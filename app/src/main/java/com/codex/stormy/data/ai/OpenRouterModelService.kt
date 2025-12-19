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
    @SerialName("context_length")
    val contextLength: Int? = null,
    val pricing: OpenRouterPricing? = null,
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
    @SerialName("max_completion_tokens")
    val maxCompletionTokens: Int? = null,
    @SerialName("is_moderated")
    val isModerated: Boolean? = null
)

/**
 * Service for fetching available models from OpenRouter API
 * OpenRouter provides access to many models from various providers
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
    suspend fun fetchAvailableModels(): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
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
    private fun isValidChatModel(info: OpenRouterModelInfo): Boolean {
        val modelId = info.id.lowercase()
        val modality = info.architecture?.modality?.lowercase() ?: ""

        // Exclude non-text models
        if (modality.contains("image") || modality.contains("audio")) {
            return false
        }

        // Exclude embedding and other non-chat models
        val excludePatterns = listOf(
            "embed", "embedding",
            "whisper", "audio",
            "flux", "sdxl", "stable-diffusion",
            "dall-e", "midjourney",
            "rerank", "moderation"
        )

        if (excludePatterns.any { modelId.contains(it) }) {
            return false
        }

        // Include text-based chat models
        return modality.contains("text") || modality.isEmpty()
    }

    /**
     * Map OpenRouter model info to our AiModel format
     */
    private fun mapToAiModel(info: OpenRouterModelInfo): AiModel {
        val modelId = info.id
        val lowerId = modelId.lowercase()

        // Determine capabilities based on model name/type
        val isThinkingModel = lowerId.contains("r1") ||
                lowerId.contains("reasoning") ||
                lowerId.contains("think") ||
                lowerId.contains("qwq")

        val supportsToolCalls = !isThinkingModel && (
                lowerId.contains("gpt-4") ||
                        lowerId.contains("gpt-3.5") ||
                        lowerId.contains("claude") ||
                        lowerId.contains("instruct") ||
                        lowerId.contains("chat") ||
                        lowerId.contains("qwen") ||
                        lowerId.contains("llama-3") ||
                        lowerId.contains("gemini")
                )

        // Get context length from API response
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
            isThinkingModel = isThinkingModel
        )
    }

    /**
     * Estimate context length based on model name patterns
     */
    private fun estimateContextLength(modelId: String): Int {
        return when {
            modelId.contains("128k") -> 131072
            modelId.contains("64k") -> 65536
            modelId.contains("32k") -> 32768
            modelId.contains("16k") -> 16384
            modelId.contains("gpt-4") -> 128000
            modelId.contains("claude-3") -> 200000
            modelId.contains("claude-2") -> 100000
            modelId.contains("gemini") -> 1000000
            modelId.contains("llama-3") -> 131072
            modelId.contains("qwen") -> 32768
            else -> 8192
        }
    }

    /**
     * Generate a user-friendly display name from model ID
     */
    private fun generateDisplayName(modelId: String): String {
        val name = modelId
            .substringAfterLast("/")
            .replace("-", " ")
            .replace("_", " ")

        return name.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                when {
                    word.all { it.isDigit() || it == '.' } -> word
                    word.length <= 3 -> word.uppercase()
                    else -> word.replaceFirstChar { it.uppercase() }
                }
            }
    }

    companion object {
        /**
         * Curated list of recommended models from OpenRouter
         */
        val RECOMMENDED_MODELS = listOf(
            "openai/gpt-4o",
            "openai/gpt-4o-mini",
            "anthropic/claude-3.5-sonnet",
            "anthropic/claude-3-opus",
            "google/gemini-pro-1.5",
            "meta-llama/llama-3.3-70b-instruct",
            "qwen/qwen-2.5-coder-32b-instruct",
            "deepseek/deepseek-chat",
            "deepseek/deepseek-r1"
        )
    }
}
