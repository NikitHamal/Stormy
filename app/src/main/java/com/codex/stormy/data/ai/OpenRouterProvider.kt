package com.codex.stormy.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Response model for OpenRouter models API
 */
@Serializable
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelInfo>
)

@Serializable
data class OpenRouterModelInfo(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val pricing: OpenRouterPricing? = null,
    @SerialName("context_length")
    val contextLength: Int? = null,
    val architecture: OpenRouterArchitecture? = null,
    @SerialName("top_provider")
    val topProvider: OpenRouterTopProvider? = null
)

@Serializable
data class OpenRouterPricing(
    val prompt: String? = null,
    val completion: String? = null
)

@Serializable
data class OpenRouterArchitecture(
    val modality: String? = null,
    val tokenizer: String? = null,
    @SerialName("instruct_type")
    val instructType: String? = null
)

@Serializable
data class OpenRouterTopProvider(
    @SerialName("context_length")
    val contextLength: Int? = null,
    @SerialName("is_moderated")
    val isModerated: Boolean? = null
)

/**
 * AI provider implementation for OpenRouter API
 * OpenRouter provides access to multiple AI models through a unified API
 *
 * Important: OpenRouter requires:
 * - Valid API key in Authorization header
 * - HTTP-Referer header for tracking
 * - X-Title header for identification
 * - For free models, append :free suffix or use nousresearch/nous-hermes-llama2-13b:free format
 */
class OpenRouterProvider(
    private val apiKey: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)  // Increased for slower free models
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = AiProvider.OPENROUTER.baseUrl

    companion object {
        private const val USER_AGENT = "CodeX-Android/1.0"
        private const val APP_NAME = "CodeX"
        private const val APP_SITE_URL = "https://codex.app"  // Site URL for OpenRouter attribution
    }

    /**
     * Check if this is a free model that needs special handling
     * Free models on OpenRouter typically have ":free" suffix or are in free tier list
     */
    private fun isFreeModel(modelId: String): Boolean {
        val lowerModelId = modelId.lowercase()
        return lowerModelId.endsWith(":free") ||
                lowerModelId.contains("nous-hermes") ||
                lowerModelId.contains("mythomist") ||
                lowerModelId.contains("toppy-m-7b")
    }

    /**
     * Send a chat completion request with streaming response
     */
    fun streamChatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): Flow<StreamEvent> = callbackFlow {
        val request = ChatCompletionRequest(
            model = model.id,
            messages = messages,
            stream = true,
            temperature = temperature,
            maxTokens = maxTokens,
            tools = if (model.supportsToolCalls && tools != null) tools else null,
            toolChoice = if (model.supportsToolCalls && tools != null) "auto" else null
        )

        val requestBody = json.encodeToString(ChatCompletionRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("HTTP-Referer", APP_SITE_URL)
            .addHeader("X-Title", APP_NAME)
            .post(requestBody)
            .build()

        val eventSourceListener = object : EventSourceListener() {
            private var accumulatedContent = StringBuilder()
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
                if (data == "[DONE]") {
                    // Emit final tool calls if any
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
                        accumulatedContent.append(content)
                        trySend(StreamEvent.ContentDelta(content))
                    }

                    // Handle tool calls
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

                    // Check for finish reason
                    chunk.choices.firstOrNull()?.finishReason?.let { reason ->
                        when (reason) {
                            "stop" -> trySend(StreamEvent.FinishReason(reason))
                            "tool_calls" -> trySend(StreamEvent.FinishReason(reason))
                            "length" -> trySend(StreamEvent.FinishReason(reason))
                        }
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors for malformed chunks
                }
            }

            override fun onClosed(eventSource: EventSource) {
                channel.close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorMessage = when {
                    response != null && response.code == 401 -> "Invalid OpenRouter API key. Please check your API key in Settings."
                    response != null && response.code == 402 -> "Insufficient credits. Please add credits to your OpenRouter account."
                    response != null && response.code == 429 -> "Rate limit exceeded. Please try again later."
                    response != null && response.code == 503 -> "Service temporarily unavailable. Please try again later."
                    response != null -> {
                        try {
                            val errorBody = response.body?.string()
                            val apiError = json.decodeFromString(ApiErrorResponse.serializer(), errorBody ?: "")
                            val rawMessage = apiError.error?.message ?: "Request failed with status ${response.code}"
                            // Provide user-friendly error messages
                            when {
                                rawMessage.contains("does not exist", ignoreCase = true) ||
                                rawMessage.contains("not found", ignoreCase = true) ->
                                    "Model not found. The selected model may not be available. Please try a different model."
                                rawMessage.contains("API key", ignoreCase = true) ->
                                    "Invalid API key. Please check your OpenRouter API key in Settings."
                                else -> rawMessage
                            }
                        } catch (e: Exception) {
                            "Request failed with status ${response.code}"
                        }
                    }
                    t != null -> t.message ?: "Network error"
                    else -> "Unknown error"
                }
                trySend(StreamEvent.Error(errorMessage))
                channel.close()
            }
        }

        val eventSource = EventSources.createFactory(client)
            .newEventSource(httpRequest, eventSourceListener)

        awaitClose {
            eventSource.cancel()
        }
    }

    /**
     * Send a non-streaming chat completion request
     */
    suspend fun chatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        try {
            val request = ChatCompletionRequest(
                model = model.id,
                messages = messages,
                stream = false,
                temperature = temperature,
                maxTokens = maxTokens,
                tools = if (model.supportsToolCalls && tools != null) tools else null,
                toolChoice = if (model.supportsToolCalls && tools != null) "auto" else null
            )

            val requestBody = json.encodeToString(ChatCompletionRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("HTTP-Referer", APP_SITE_URL)
                .addHeader("X-Title", APP_NAME)
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val apiError = try {
                    json.decodeFromString(ApiErrorResponse.serializer(), errorBody ?: "")
                } catch (e: Exception) {
                    null
                }
                return@withContext Result.failure(
                    IOException(apiError?.error?.message ?: "Request failed: ${response.code}")
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

/**
 * Service for fetching available models from OpenRouter API
 */
class OpenRouterModelService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch available models from OpenRouter API
     */
    suspend fun fetchAvailableModels(apiKey: String? = null): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .get()

            // Add API key if provided for more detailed information
            if (!apiKey.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch models: ${response.code} ${response.message}")
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val modelsResponse = json.decodeFromString<OpenRouterModelsResponse>(body)

                // Filter and map to our AiModel format
                val models = modelsResponse.data
                    .filter { isValidChatModel(it) }
                    .map { info -> mapToAiModel(info) }
                    .sortedBy { it.name }

                Result.success(models)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if a model is suitable for chat completion
     */
    private fun isValidChatModel(model: OpenRouterModelInfo): Boolean {
        val modality = model.architecture?.modality?.lowercase() ?: ""
        val instructType = model.architecture?.instructType?.lowercase() ?: ""

        // Prefer chat/instruct models
        if (modality.contains("text") || instructType.isNotEmpty()) {
            return true
        }

        // Filter out non-text models
        val excludePatterns = listOf("image", "audio", "video", "embedding")
        return !excludePatterns.any { modality.contains(it) }
    }

    /**
     * Map OpenRouter model info to our AiModel format
     */
    private fun mapToAiModel(info: OpenRouterModelInfo): AiModel {
        val modelId = info.id
        val lowerId = modelId.lowercase()

        // Determine if it supports tool calls based on architecture
        val supportsToolCalls = info.architecture?.instructType != null ||
                lowerId.contains("instruct") ||
                lowerId.contains("chat") ||
                lowerId.contains("gpt") ||
                lowerId.contains("claude") ||
                lowerId.contains("gemini")

        // Get context length
        val contextLength = info.contextLength
            ?: info.topProvider?.contextLength
            ?: estimateContextLength(lowerId)

        // Use provided name or generate from ID
        val displayName = info.name ?: generateDisplayName(modelId)

        return AiModel(
            id = modelId,
            name = displayName,
            provider = AiProvider.OPENROUTER,
            contextLength = contextLength,
            supportsStreaming = true,
            supportsToolCalls = supportsToolCalls,
            isThinkingModel = false
        )
    }

    /**
     * Estimate context length based on model name patterns
     */
    private fun estimateContextLength(lowerId: String): Int {
        return when {
            lowerId.contains("128k") -> 131072
            lowerId.contains("100k") -> 102400
            lowerId.contains("64k") -> 65536
            lowerId.contains("32k") -> 32768
            lowerId.contains("16k") -> 16384
            lowerId.contains("claude") -> 200000  // Claude typically has large context
            lowerId.contains("gemini") -> 1000000  // Gemini can have very large context
            lowerId.contains("gpt-4") -> 128000
            else -> 8192
        }
    }

    /**
     * Generate a user-friendly display name from model ID
     */
    private fun generateDisplayName(modelId: String): String {
        // OpenRouter format: provider/model-name
        val parts = modelId.split("/")
        val modelName = parts.lastOrNull() ?: modelId

        return modelName
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }

    companion object {
        /**
         * Recommended OpenRouter models for code generation
         */
        val RECOMMENDED_MODELS = listOf(
            "anthropic/claude-3.5-sonnet",
            "anthropic/claude-3-opus",
            "openai/gpt-4-turbo",
            "openai/gpt-4",
            "google/gemini-pro-1.5",
            "meta-llama/llama-3.1-405b-instruct",
            "meta-llama/llama-3.3-70b-instruct",
            "deepseek/deepseek-coder",
            "qwen/qwen-2.5-coder-32b-instruct"
        )
    }
}
