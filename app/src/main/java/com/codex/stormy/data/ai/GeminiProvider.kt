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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Gemini-specific request/response models
 */
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("generationConfig")
    val generationConfig: GeminiGenerationConfig? = null,
    val tools: List<GeminiTool>? = null
)

@Serializable
data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("functionCall")
    val functionCall: GeminiFunctionCall? = null,
    @SerialName("functionResponse")
    val functionResponse: GeminiFunctionResponse? = null
)

@Serializable
data class GeminiFunctionCall(
    val name: String,
    val args: Map<String, String>
)

@Serializable
data class GeminiFunctionResponse(
    val name: String,
    val response: Map<String, String>
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float? = null,
    @SerialName("maxOutputTokens")
    val maxOutputTokens: Int? = null,
    @SerialName("topP")
    val topP: Float? = null,
    @SerialName("topK")
    val topK: Int? = null
)

@Serializable
data class GeminiTool(
    @SerialName("functionDeclarations")
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: GeminiParameters
)

@Serializable
data class GeminiParameters(
    val type: String = "object",
    val properties: Map<String, GeminiProperty>,
    val required: List<String> = emptyList()
)

@Serializable
data class GeminiProperty(
    val type: String,
    val description: String
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    @SerialName("usageMetadata")
    val usageMetadata: GeminiUsageMetadata? = null,
    @SerialName("modelVersion")
    val modelVersion: String? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerialName("finishReason")
    val finishReason: String? = null,
    val index: Int? = null
)

@Serializable
data class GeminiUsageMetadata(
    @SerialName("promptTokenCount")
    val promptTokenCount: Int? = null,
    @SerialName("candidatesTokenCount")
    val candidatesTokenCount: Int? = null,
    @SerialName("totalTokenCount")
    val totalTokenCount: Int? = null
)

/**
 * Response model for Gemini models API
 */
/**
 * Error response model for Gemini API
 */
@Serializable
data class GeminiErrorResponse(
    val error: GeminiErrorDetail? = null
)

@Serializable
data class GeminiErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

@Serializable
data class GeminiModelsResponse(
    val models: List<GeminiModelInfo>
)

@Serializable
data class GeminiModelInfo(
    val name: String,
    @SerialName("displayName")
    val displayName: String? = null,
    val description: String? = null,
    @SerialName("inputTokenLimit")
    val inputTokenLimit: Int? = null,
    @SerialName("outputTokenLimit")
    val outputTokenLimit: Int? = null,
    @SerialName("supportedGenerationMethods")
    val supportedGenerationMethods: List<String>? = null
)

/**
 * AI provider implementation for Google Gemini API
 */
class GeminiProvider(
    private val apiKey: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = AiProvider.GEMINI.baseUrl

    companion object {
        private const val USER_AGENT = "CodeX-Android/1.0"
    }

    /**
     * Normalize model ID to ensure correct format for Gemini API
     * Gemini API expects: models/gemini-1.5-pro
     */
    private fun normalizeModelId(modelId: String): String {
        // If already has models/ prefix, use as-is
        if (modelId.startsWith("models/")) {
            return modelId
        }
        // Otherwise add the prefix
        return "models/$modelId"
    }

    /**
     * Parse Gemini API error response to provide user-friendly error messages
     */
    private fun parseGeminiError(statusCode: Int, errorBody: String?): String {
        return try {
            if (errorBody.isNullOrBlank()) {
                return getDefaultErrorMessage(statusCode)
            }

            // Try to parse the error JSON
            val errorResponse = json.decodeFromString<GeminiErrorResponse>(errorBody)
            val errorMessage = errorResponse.error?.message ?: errorResponse.error?.status

            when {
                errorMessage?.contains("not found", ignoreCase = true) == true ->
                    "Model not found. Please check that the model is available and try again."
                errorMessage?.contains("API key", ignoreCase = true) == true || statusCode == 401 ->
                    "Invalid API key. Please check your Gemini API key in Settings."
                errorMessage?.contains("quota", ignoreCase = true) == true || statusCode == 429 ->
                    "API quota exceeded. Please try again later or check your usage limits."
                errorMessage?.contains("permission", ignoreCase = true) == true || statusCode == 403 ->
                    "Access denied. Your API key may not have permission to use this model."
                statusCode == 400 ->
                    "Invalid request: ${errorMessage ?: "Please check your input"}"
                statusCode == 503 ->
                    "Service temporarily unavailable. Please try again later."
                else -> errorMessage ?: getDefaultErrorMessage(statusCode)
            }
        } catch (e: Exception) {
            getDefaultErrorMessage(statusCode)
        }
    }

    private fun getDefaultErrorMessage(statusCode: Int): String {
        return when (statusCode) {
            400 -> "Bad request. Please try again."
            401 -> "Invalid API key. Please check your Gemini API key in Settings."
            403 -> "Access denied. Please check your API key permissions."
            404 -> "Model not found. Please select a different model."
            429 -> "Rate limit exceeded. Please wait a moment and try again."
            500 -> "Server error. Please try again later."
            503 -> "Service temporarily unavailable. Please try again later."
            else -> "Request failed with status $statusCode"
        }
    }

    /**
     * Send a chat completion request with streaming response
     * Uses x-goog-api-key header for authentication (recommended method)
     */
    fun streamChatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): Flow<StreamEvent> = callbackFlow {
        try {
            // Convert messages to Gemini format
            val geminiContents = convertMessagesToGemini(messages)

            val request = GeminiRequest(
                contents = geminiContents,
                generationConfig = GeminiGenerationConfig(
                    temperature = temperature,
                    maxOutputTokens = maxTokens
                ),
                tools = if (model.supportsToolCalls && tools != null) {
                    listOf(GeminiTool(functionDeclarations = convertToolsToGemini(tools)))
                } else null
            )

            val requestBody = json.encodeToString(GeminiRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val normalizedModelId = normalizeModelId(model.id)
            // Use x-goog-api-key header for authentication (recommended by Google)
            val httpRequest = Request.Builder()
                .url("$baseUrl/$normalizedModelId:streamGenerateContent?alt=sse")
                .addHeader("Content-Type", "application/json")
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("User-Agent", USER_AGENT)
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val errorMessage = parseGeminiError(response.code, errorBody)
                trySend(StreamEvent.Error(errorMessage))
                channel.close()
                return@callbackFlow
            }

            trySend(StreamEvent.Started)

            val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
            var line: String?
            val accumulatedContent = StringBuilder()
            val toolCallsAccumulator = mutableListOf<ToolCallResponse>()

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue

                // Parse SSE format: data: {...}
                if (currentLine.startsWith("data: ")) {
                    val jsonData = currentLine.substring(6)

                    if (jsonData == "[DONE]") {
                        break
                    }

                    try {
                        val geminiResponse = json.decodeFromString(GeminiResponse.serializer(), jsonData)
                        val candidate = geminiResponse.candidates?.firstOrNull()
                        val content = candidate?.content

                        // Handle text content
                        content?.parts?.forEach { part ->
                            part.text?.let { text ->
                                accumulatedContent.append(text)
                                trySend(StreamEvent.ContentDelta(text))
                            }

                            // Handle function calls
                            part.functionCall?.let { functionCall ->
                                val toolCall = ToolCallResponse(
                                    id = "call_${System.currentTimeMillis()}",
                                    type = "function",
                                    function = FunctionCall(
                                        name = functionCall.name,
                                        arguments = json.encodeToString(
                                            kotlinx.serialization.serializer(),
                                            functionCall.args
                                        )
                                    )
                                )
                                toolCallsAccumulator.add(toolCall)
                            }
                        }

                        // Check finish reason
                        candidate?.finishReason?.let { reason ->
                            when (reason.lowercase()) {
                                "stop" -> trySend(StreamEvent.FinishReason("stop"))
                                "max_tokens", "length" -> trySend(StreamEvent.FinishReason("length"))
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore parsing errors for malformed chunks
                    }
                }
            }

            // Emit tool calls if any
            if (toolCallsAccumulator.isNotEmpty()) {
                trySend(StreamEvent.ToolCalls(toolCallsAccumulator))
            }

            trySend(StreamEvent.Completed)
            channel.close()
            response.close()
        } catch (e: Exception) {
            trySend(StreamEvent.Error(e.message ?: "Unknown error"))
            channel.close()
        }

        awaitClose {
            // Cleanup if needed
        }
    }

    /**
     * Send a non-streaming chat completion request
     * Uses x-goog-api-key header for authentication (recommended method)
     */
    suspend fun chatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        try {
            // Convert messages to Gemini format
            val geminiContents = convertMessagesToGemini(messages)

            val request = GeminiRequest(
                contents = geminiContents,
                generationConfig = GeminiGenerationConfig(
                    temperature = temperature,
                    maxOutputTokens = maxTokens
                ),
                tools = if (model.supportsToolCalls && tools != null) {
                    listOf(GeminiTool(functionDeclarations = convertToolsToGemini(tools)))
                } else null
            )

            val requestBody = json.encodeToString(GeminiRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val normalizedModelId = normalizeModelId(model.id)
            // Use x-goog-api-key header for authentication (recommended by Google)
            val httpRequest = Request.Builder()
                .url("$baseUrl/$normalizedModelId:generateContent")
                .addHeader("Content-Type", "application/json")
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("User-Agent", USER_AGENT)
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val errorMessage = parseGeminiError(response.code, errorBody)
                return@withContext Result.failure(IOException(errorMessage))
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response body"))

            val geminiResponse = json.decodeFromString(GeminiResponse.serializer(), responseBody)

            // Convert Gemini response to our standard format
            val chatResponse = convertGeminiResponse(geminiResponse, model.id)
            Result.success(chatResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert OpenAI-style messages to Gemini format
     */
    private fun convertMessagesToGemini(messages: List<ChatRequestMessage>): List<GeminiContent> {
        return messages.mapNotNull { message ->
            when (message.role) {
                "system" -> {
                    // Gemini doesn't have system role, prepend to first user message
                    // This will be handled by combining with the next user message
                    null
                }
                "user", "assistant" -> {
                    val role = if (message.role == "user") "user" else "model"
                    val parts = listOfNotNull(
                        message.content?.let { GeminiPart(text = it) }
                    )

                    if (parts.isNotEmpty()) {
                        GeminiContent(role = role, parts = parts)
                    } else null
                }
                "tool" -> {
                    // Handle tool response
                    message.content?.let { content ->
                        GeminiContent(
                            role = "function",
                            parts = listOf(
                                GeminiPart(
                                    functionResponse = GeminiFunctionResponse(
                                        name = message.name ?: "unknown",
                                        response = mapOf("result" to content)
                                    )
                                )
                            )
                        )
                    }
                }
                else -> null
            }
        }.let { contents ->
            // Prepend system message to first user message if exists
            val systemMessage = messages.firstOrNull { it.role == "system" }
            if (systemMessage != null && contents.isNotEmpty()) {
                val firstUserIndex = contents.indexOfFirst { it.role == "user" }
                if (firstUserIndex >= 0) {
                    val firstUser = contents[firstUserIndex]
                    val combinedParts = listOf(
                        GeminiPart(text = "System: ${systemMessage.content}\n\n")
                    ) + firstUser.parts

                    contents.toMutableList().apply {
                        this[firstUserIndex] = firstUser.copy(parts = combinedParts)
                    }
                } else contents
            } else contents
        }
    }

    /**
     * Convert OpenAI-style tools to Gemini format
     */
    private fun convertToolsToGemini(tools: List<Tool>): List<GeminiFunctionDeclaration> {
        return tools.map { tool ->
            val func = tool.function

            // Parse parameters from JsonElement
            val paramsJson = func.parameters.toString()
            val paramsMap = try {
                json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(paramsJson)
            } catch (e: Exception) {
                emptyMap()
            }

            val properties = try {
                val propsElement = paramsMap["properties"]
                if (propsElement != null) {
                    val propsObj = propsElement as? kotlinx.serialization.json.JsonObject ?: kotlinx.serialization.json.JsonObject(emptyMap())
                    propsObj.mapValues { (_, propValue) ->
                        val propObj = propValue as? kotlinx.serialization.json.JsonObject
                        GeminiProperty(
                            type = propObj?.get("type")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "string",
                            description = propObj?.get("description")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
                        )
                    }
                } else {
                    emptyMap()
                }
            } catch (e: Exception) {
                emptyMap()
            }

            val required = try {
                val reqElement = paramsMap["required"]
                if (reqElement != null) {
                    val reqArray = reqElement as? kotlinx.serialization.json.JsonArray
                    reqArray?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }

            GeminiFunctionDeclaration(
                name = func.name,
                description = func.description,
                parameters = GeminiParameters(
                    type = "object",
                    properties = properties,
                    required = required
                )
            )
        }
    }

    /**
     * Convert Gemini response to our standard ChatCompletionResponse format
     */
    private fun convertGeminiResponse(
        geminiResponse: GeminiResponse,
        modelId: String
    ): ChatCompletionResponse {
        val candidate = geminiResponse.candidates?.firstOrNull()
        val content = candidate?.content

        val text = content?.parts
            ?.mapNotNull { it.text }
            ?.joinToString("")

        val toolCalls = content?.parts
            ?.mapNotNull { part ->
                part.functionCall?.let { functionCall ->
                    ToolCallResponse(
                        id = "call_${System.currentTimeMillis()}",
                        type = "function",
                        function = FunctionCall(
                            name = functionCall.name,
                            arguments = json.encodeToString(
                                kotlinx.serialization.serializer(),
                                functionCall.args
                            )
                        )
                    )
                }
            }

        return ChatCompletionResponse(
            id = "gemini_${System.currentTimeMillis()}",
            model = modelId,
            choices = listOf(
                Choice(
                    index = 0,
                    message = ResponseMessage(
                        role = "assistant",
                        content = text,
                        toolCalls = toolCalls?.takeIf { it.isNotEmpty() }
                    ),
                    finishReason = candidate?.finishReason?.lowercase()
                )
            ),
            usage = geminiResponse.usageMetadata?.let { usage ->
                Usage(
                    promptTokens = usage.promptTokenCount ?: 0,
                    completionTokens = usage.candidatesTokenCount ?: 0,
                    totalTokens = usage.totalTokenCount ?: 0
                )
            }
        )
    }
}

/**
 * Service for fetching available models from Gemini API
 */
class GeminiModelService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch available models from Gemini API
     * Uses x-goog-api-key header for authentication
     */
    suspend fun fetchAvailableModels(apiKey: String): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models")
                .addHeader("x-goog-api-key", apiKey)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch models: ${response.code} ${response.message}")
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val modelsResponse = json.decodeFromString<GeminiModelsResponse>(body)

                // Filter and map to our AiModel format
                val models = modelsResponse.models
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
    private fun isValidChatModel(model: GeminiModelInfo): Boolean {
        val methods = model.supportedGenerationMethods ?: return false
        return methods.contains("generateContent")
    }

    /**
     * Map Gemini model info to our AiModel format
     */
    private fun mapToAiModel(info: GeminiModelInfo): AiModel {
        val modelId = info.name
        val lowerId = modelId.lowercase()

        // Gemini models support tool calls
        val supportsToolCalls = true

        // Get context length
        val contextLength = info.inputTokenLimit ?: estimateContextLength(lowerId)

        // Use provided display name or generate from ID
        val displayName = info.displayName ?: generateDisplayName(modelId)

        return AiModel(
            id = modelId,
            name = displayName,
            provider = AiProvider.GEMINI,
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
            lowerId.contains("1.5") -> 1000000  // Gemini 1.5 models have 1M+ context
            lowerId.contains("pro") -> 1000000
            lowerId.contains("flash") -> 1000000
            else -> 32768
        }
    }

    /**
     * Generate a user-friendly display name from model ID
     */
    private fun generateDisplayName(modelId: String): String {
        // Format: models/gemini-1.5-pro -> Gemini 1.5 Pro
        return modelId
            .removePrefix("models/")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }

    companion object {
        /**
         * Recommended Gemini models for code generation
         */
        val RECOMMENDED_MODELS = listOf(
            "models/gemini-1.5-pro",
            "models/gemini-1.5-flash",
            "models/gemini-pro"
        )
    }
}
