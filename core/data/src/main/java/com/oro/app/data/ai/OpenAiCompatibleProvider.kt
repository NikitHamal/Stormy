package com.oro.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException

/**
 * Generic OpenAI-compatible provider client used for providers that expose
 * `/chat/completions` and SSE streaming in OpenAI format.
 */
class OpenAiCompatibleProvider(
    private val provider: AiProvider,
    private val apiKey: String,
    /** Only used by [AiProvider.CUSTOM], where the URL is user-supplied. */
    baseUrlOverride: String = "",
    extraHeaders: Map<String, String> = emptyMap()
) {
    /**
     * Headers the provider always wants (attribution, origin), merged with
     * anything the caller adds. OpenRouter and Kilo Code want attribution
     * headers -- which live on the enum, so this class serves every provider.
     */
    private val extraHeaders: Map<String, String> = provider.extraHeaders + extraHeaders

    private val json get() = AiHttp.json

    private val baseUrl =
        (baseUrlOverride.takeIf { it.isNotBlank() } ?: provider.baseUrl).trimEnd('/')

    private fun missingKeyMessage(): String {
        return "${provider.displayName} API key is required. Add it in Settings > AI."
    }

    /**
     * Send a streaming chat completion request.
     */
    fun streamChatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null,
        reasoningEffort: ReasoningEffort? = null
    ): Flow<StreamEvent> {
        if (provider.requiresApiKey && apiKey.isBlank()) {
            return flowOf(StreamEvent.Error(missingKeyMessage()))
        }

        return callbackFlow {
            val request = ChatCompletionRequest(
                model = model.id,
                messages = messages,
                stream = true,
                temperature = temperature,
                maxTokens = maxTokens,
                tools = if (model.supportsToolCalls && tools != null) tools else null,
                toolChoice = if (model.supportsToolCalls && tools != null) "auto" else null,
                // Sent only when the model advertises support: providers that
                // do not accept the field answer 400 rather than ignoring it.
                reasoningEffort = reasoningEffort
                    ?.takeIf { model.supportsReasoningEffort }
                    ?.wireValue
            )

            val requestBody = json.encodeToString(ChatCompletionRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val httpRequestBuilder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(requestBody)

            for ((header, value) in extraHeaders) {
                httpRequestBuilder.addHeader(header, value)
            }

            if (apiKey.isNotBlank()) {
                httpRequestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val httpRequest = httpRequestBuilder.build()

            val eventSourceListener = object : EventSourceListener() {
                private val toolCallsMap = mutableMapOf<Int, ToolCallAccumulator>()

                override fun onOpen(eventSource: EventSource, response: Response) {
                    trySend(StreamEvent.Started)
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    trySend(StreamEvent.RawSSE(data))

                    if (data == "[DONE]") {
                        if (toolCallsMap.isNotEmpty()) {
                            val toolCalls = toolCallsMap.values.map { acc ->
                                ToolCallResponse(
                                    id = acc.id,
                                    type = "function",
                                    function = FunctionCall(
                                        name = acc.name,
                                        arguments = acc.arguments.toString()
                                    )
                                )
                            }
                            trySend(StreamEvent.ToolCalls(toolCalls))
                        }
                        trySend(StreamEvent.Completed)
                        return
                    }

                    try {
                        val chunk = json.decodeFromString(StreamChunk.serializer(), data)
                        val delta = chunk.choices.firstOrNull()?.delta

                        delta?.content?.let { content ->
                            trySend(StreamEvent.ContentDelta(content))
                        }

                        delta?.reasoningContent?.let { reasoning ->
                            trySend(StreamEvent.ReasoningDelta(reasoning))
                        }

                        delta?.toolCalls?.forEach { toolCallDelta ->
                            val index = toolCallDelta.index
                            val accumulator = toolCallsMap.getOrPut(index) {
                                ToolCallAccumulator()
                            }

                            toolCallDelta.id?.let { accumulator.id = it }
                            toolCallDelta.function?.name?.let { accumulator.name = it }
                            toolCallDelta.function?.arguments?.let {
                                accumulator.arguments.append(it)
                            }
                        }

                        chunk.choices.firstOrNull()?.finishReason?.let { reason ->
                            when (reason) {
                                "stop", "tool_calls", "length" -> {
                                    trySend(StreamEvent.FinishReason(reason))
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore malformed chunks and keep stream alive.
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    channel.close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val errorMessage = when {
                        response != null && response.code == 401 -> "Invalid ${provider.displayName} API key"
                        response != null && response.code == 429 -> "${provider.displayName} rate limit exceeded"
                        response != null && response.code == 503 -> "${provider.displayName} is temporarily unavailable"
                        response != null -> {
                            try {
                                val errorBody = response.body?.string()
                                val apiError = json.decodeFromString(ApiErrorResponse.serializer(), errorBody ?: "")
                                apiError.error?.message ?: "${provider.displayName} request failed (${response.code})"
                            } catch (_: Exception) {
                                "${provider.displayName} request failed (${response.code})"
                            }
                        }
                        t != null -> t.message ?: "Network error"
                        else -> "Unknown error"
                    }
                    trySend(StreamEvent.Error(errorMessage))
                    channel.close()
                }
            }

            val eventSource = EventSources.createFactory(AiHttp.streaming)
                .newEventSource(httpRequest, eventSourceListener)

            awaitClose {
                eventSource.cancel()
            }
        }
    }

    /**
     * Send a non-streaming chat completion request.
     */
    suspend fun chatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null,
        reasoningEffort: ReasoningEffort? = null
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        if (provider.requiresApiKey && apiKey.isBlank()) {
            return@withContext Result.failure(IOException(missingKeyMessage()))
        }

        try {
            val request = ChatCompletionRequest(
                model = model.id,
                messages = messages,
                stream = false,
                temperature = temperature,
                maxTokens = maxTokens,
                tools = if (model.supportsToolCalls && tools != null) tools else null,
                toolChoice = if (model.supportsToolCalls && tools != null) "auto" else null,
                // Sent only when the model advertises support: providers that
                // do not accept the field answer 400 rather than ignoring it.
                reasoningEffort = reasoningEffort
                    ?.takeIf { model.supportsReasoningEffort }
                    ?.wireValue
            )

            val requestBody = json.encodeToString(ChatCompletionRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val httpRequestBuilder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)

            for ((header, value) in extraHeaders) {
                httpRequestBuilder.addHeader(header, value)
            }

            if (apiKey.isNotBlank()) {
                httpRequestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val response = AiHttp.client.newCall(httpRequestBuilder.build()).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val apiError = try {
                    json.decodeFromString(ApiErrorResponse.serializer(), errorBody ?: "")
                } catch (_: Exception) {
                    null
                }
                return@withContext Result.failure(
                    IOException(
                        apiError?.error?.message
                            ?: "${provider.displayName} request failed: ${response.code}"
                    )
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response body"))

            val chatResponse = json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
            Result.success(chatResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class ToolCallAccumulator(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    )
}
