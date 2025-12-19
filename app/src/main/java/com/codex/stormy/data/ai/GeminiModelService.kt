package com.codex.stormy.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Response model for Gemini models API
 */
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
    val version: String? = null,
    @SerialName("inputTokenLimit")
    val inputTokenLimit: Int? = null,
    @SerialName("outputTokenLimit")
    val outputTokenLimit: Int? = null,
    @SerialName("supportedGenerationMethods")
    val supportedGenerationMethods: List<String>? = null
)

/**
 * Service for fetching available models from Google Gemini API
 * Gemini provides powerful multimodal AI models
 */
class GeminiModelService(private val apiKey: String? = null) {

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
     * Note: API key is required for Gemini API access
     */
    suspend fun fetchAvailableModels(key: String? = apiKey): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        val effectiveKey = key ?: return@withContext Result.failure(
            Exception("Gemini API key is required")
        )

        try {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$effectiveKey")
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
    private fun isValidChatModel(info: GeminiModelInfo): Boolean {
        val modelName = info.name.lowercase()
        val methods = info.supportedGenerationMethods ?: emptyList()

        // Must support generateContent for chat
        if (!methods.contains("generateContent")) {
            return false
        }

        // Exclude embedding and other non-chat models
        val excludePatterns = listOf(
            "embed", "embedding",
            "aqa", "retrieval",
            "vision" // Vision-only models
        )

        if (excludePatterns.any { modelName.contains(it) }) {
            return false
        }

        // Include gemini models
        return modelName.contains("gemini")
    }

    /**
     * Map Gemini model info to our AiModel format
     */
    private fun mapToAiModel(info: GeminiModelInfo): AiModel {
        // Model name format: "models/gemini-1.5-pro-latest"
        val modelId = info.name.removePrefix("models/")
        val lowerId = modelId.lowercase()

        // Determine capabilities
        val isThinkingModel = lowerId.contains("thinking") ||
                lowerId.contains("reason")

        // Gemini models generally support function calling
        val supportsToolCalls = !isThinkingModel && (
                lowerId.contains("pro") ||
                        lowerId.contains("flash") ||
                        lowerId.contains("ultra")
                )

        // Get context length from API response
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
            isThinkingModel = isThinkingModel
        )
    }

    /**
     * Estimate context length based on model name patterns
     */
    private fun estimateContextLength(modelId: String): Int {
        return when {
            modelId.contains("1.5") -> 1000000 // 1M tokens for Gemini 1.5
            modelId.contains("2.0") -> 1000000 // Gemini 2.0 also has large context
            modelId.contains("ultra") -> 128000
            modelId.contains("pro") -> 128000
            modelId.contains("flash") -> 1000000
            else -> 32768
        }
    }

    /**
     * Generate a user-friendly display name from model ID
     */
    private fun generateDisplayName(modelId: String): String {
        val name = modelId
            .replace("-", " ")
            .replace("_", " ")

        return name.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                when {
                    word.all { it.isDigit() || it == '.' } -> word
                    word.equals("pro", ignoreCase = true) -> "Pro"
                    word.equals("flash", ignoreCase = true) -> "Flash"
                    word.equals("ultra", ignoreCase = true) -> "Ultra"
                    word.equals("gemini", ignoreCase = true) -> "Gemini"
                    word.length <= 3 -> word.uppercase()
                    else -> word.replaceFirstChar { it.uppercase() }
                }
            }
    }

    companion object {
        /**
         * Curated list of recommended Gemini models
         * These are available without API key via AI Studio for limited use
         */
        val RECOMMENDED_MODELS = listOf(
            "gemini-2.0-flash-exp",
            "gemini-1.5-pro-latest",
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash-8b-latest",
            "gemini-1.0-pro"
        )
    }
}
