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
    extraHeaders: Map<String, String> = emptyMap(),
    /** Name in error messages; custom providers carry the user's name. */
    nameOverride: String = ""
) {
    private val displayName: String = nameOverride.ifBlank { provider.displayName }

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
        return "$displayName API key is required. Add it in Settings > AI."
    }

    /**
     * `reasoning_effort` for this request: only a level the model's control on
     * this provider has. Providers that do not take the field answer 400
     * rather than ignoring it, so anything else sends nothing.
     */
    private fun effortField(model: AiModel, level: String?): String? {
        val control = model.reasoningControl ?: return null
        return level?.takeIf { control.wire == ReasoningWire.REASONING_EFFORT && control.has(it) }
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
        /** The model's level as its provider names it ([ReasoningControls.chosen]). */
        reasoningLevel: String? = null
    ): Flow<StreamEvent> {
        if (provider.requiresApiKey && apiKey.isBlank()) {
            return flowOf(StreamEvent.Error(missingKeyMessage()))
        }

        return callbackFlow {
            val request = ChatCompletionRequest(
                model = model.id,
                messages = messages,
                stream = true,
                temperature = temperature.takeUnless { ReasoningControls.omitsTemperature(model) },
                maxTokens = maxTokens,
                tools = if (model.supportsToolCalls && tools != null) tools else null,
                toolChoice = if (model.supportsToolCalls && tools != null) "auto" else null,
                reasoningEffort = effortField(model, reasoningLevel)
            )

            val requestBody = encodeChatRequest(json, request)
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

            val parser = ChatStreamParser()

            fun emitAll(events: List<StreamEvent>) {
                events.forEach { trySend(it) }
            }

            val eventSourceListener = object : EventSourceListener() {
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
                    emitAll(parser.onData(data))
                }

                override fun onClosed(eventSource: EventSource) {
                    // Many providers close after `finish_reason` without ever
                    // sending `[DONE]`. Completing here is what keeps the agent
                    // loop from waiting forever for an event that never comes
                    // (and from dropping the tool calls it already received).
                    emitAll(parser.finish())
                    channel.close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val body = runCatching { response?.body?.string() }.getOrNull()

                    // A provider that ignored stream=true answers 200 with a
                    // plain JSON completion, which the SSE reader rejects as a
                    // bad content type. Salvage it rather than failing.
                    if (response != null && response.isSuccessful && !body.isNullOrBlank()) {
                        val salvaged = parser.onData(body)
                        if (parser.hasOutput) {
                            trySend(StreamEvent.Started)
                            emitAll(salvaged)
                            emitAll(parser.finish())
                            channel.close()
                            return
                        }
                        // Or it really was SSE that the reader could not handle.
                        val lines = body.lineSequence()
                            .filter { it.startsWith("data:") }
                            .map { it.removePrefix("data:").trim() }
                            .toList()
                        if (lines.isNotEmpty()) {
                            lines.forEach { emitAll(parser.onData(it)) }
                            if (parser.hasOutput) {
                                emitAll(parser.finish())
                                channel.close()
                                return
                            }
                        }
                    }

                    trySend(errorFor(response?.code, retryHeader(response), body, t))
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
        /** The model's level as its provider names it ([ReasoningControls.chosen]). */
        reasoningLevel: String? = null
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        if (provider.requiresApiKey && apiKey.isBlank()) {
            return@withContext Result.failure(IOException(missingKeyMessage()))
        }

        try {
            val request = ChatCompletionRequest(
                model = model.id,
                messages = messages,
                stream = false,
                temperature = temperature.takeUnless { ReasoningControls.omitsTemperature(model) },
                maxTokens = maxTokens,
                tools = if (model.supportsToolCalls && tools != null) tools else null,
                toolChoice = if (model.supportsToolCalls && tools != null) "auto" else null,
                reasoningEffort = effortField(model, reasoningLevel)
            )

            val requestBody = encodeChatRequest(json, request)
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

            AiHttp.client.newCall(httpRequestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    val error = errorFor(response.code, retryHeader(response), responseBody, null)
                    return@withContext Result.failure(ProviderException(error))
                }
                if (responseBody.isNullOrBlank()) {
                    return@withContext Result.failure(IOException("Empty response body"))
                }
                val chatResponse = json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
                Result.success(chatResponse)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * `Retry-After`, or the IETF `RateLimit-Reset` seconds that OVH sends on
     * its 429s instead.
     */
    private fun retryHeader(response: Response?): String? =
        response?.header("Retry-After")
            ?: response?.header("ratelimit-reset")?.takeIf { (it.toLongOrNull() ?: Long.MAX_VALUE) < 3_600 }

    /** Map a failed request to a user-readable, retry-classified error. */
    private fun errorFor(code: Int?, retryAfterHeader: String?, body: String?, t: Throwable?): StreamEvent.Error {
        val name = displayName
        val detail = ChatStreamParser.describeErrorBody(body)
        val retryAfter = ChatStreamParser.retryAfterMs(retryAfterHeader, body)
        if (code == null) {
            val reason = when (t) {
                is java.net.SocketTimeoutException -> "$name took too long to respond"
                is java.net.UnknownHostException -> "Can't reach $name. Check your connection"
                null -> "Connection to $name closed unexpectedly"
                else -> t.message?.let { "Network error: $it" } ?: "Network error talking to $name"
            }
            return StreamEvent.Error(reason, retryable = true)
        }
        val message = when (code) {
            401, 403 -> if (provider.requiresApiKey || detail == null) {
                "$name rejected the API key ($code). Check it in Settings > AI."
            } else {
                "$name refused the request ($code): $detail"
            }
            402 -> "$name free limit reached" + (detail?.let { ": $it" } ?: "")
            404 -> "$name doesn't serve this model (404)" + (detail?.let { ": $it" } ?: "")
            429 -> "$name rate limit reached" + (detail?.let { ": $it" } ?: "")
            in 500..599 -> "$name is having trouble ($code)" + (detail?.let { ": $it" } ?: "")
            else -> detail?.let { "$name: $it ($code)" } ?: "$name request failed ($code)"
        }
        val retryable = ChatStreamParser.isRetryableStatus(code) &&
            // 402 is also "payment required" on paid gateways; only the
            // free-limit flavour clears by waiting.
            !(code == 402 && retryAfter == null && body?.contains("limit", ignoreCase = true) != true)
        return StreamEvent.Error(message, httpCode = code, retryAfterMs = retryAfter, retryable = retryable)
    }
}

/** A non-streaming request failed; carries the classified error. */
class ProviderException(val error: StreamEvent.Error) : IOException(error.message)
