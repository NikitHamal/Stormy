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
 * Response model for DeepInfra models API
 */
@Serializable
data class DeepInfraModelsResponse(
    val data: List<DeepInfraModelInfo>
)

@Serializable
data class DeepInfraModelInfo(
    val id: String,
    @SerialName("object")
    val objectType: String? = null,
    val owned_by: String? = null,
    val created: Long? = null,
    // Extended info that might be available
    val max_context_length: Int? = null,
    val max_tokens: Int? = null
)

/**
 * Service for fetching available models from DeepInfra API
 * DeepInfra provides a free tier with access to many open-source models
 */
class DeepInfraModelService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch available models from DeepInfra API
     * Uses the OpenAI-compatible /models endpoint
     */
    suspend fun fetchAvailableModels(): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.deepinfra.com/v1/openai/models")
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

                val modelsResponse = json.decodeFromString<DeepInfraModelsResponse>(body)

                // Filter and map to our AiModel format
                val models = modelsResponse.data
                    .filter { isValidChatModel(it.id) }
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
     * Filters out embedding models, image models, audio models, etc.
     * Accepts all text generation models for maximum model availability
     */
    private fun isValidChatModel(modelId: String): Boolean {
        val lowerId = modelId.lowercase()

        // Exclude patterns for non-text-generation models
        val excludePatterns = listOf(
            "embed", "embedding",          // Embedding models
            "whisper", "audio", "speech",  // Audio/speech models
            "flux", "sdxl", "stable-diffusion", "image", "vision-only", // Image generation
            "rerank", "classifier",        // Ranking/classification
            "vae", "encoder", "decoder"    // Component models
        )

        // Exclude models matching these patterns
        if (excludePatterns.any { lowerId.contains(it) }) {
            return false
        }

        // Accept all remaining models - they are likely text generation capable
        // This ensures we show all available chat/instruct models from DeepInfra
        return true
    }

    /**
     * Map DeepInfra model info to our AiModel format
     */
    private fun mapToAiModel(info: DeepInfraModelInfo): AiModel {
        val modelId = info.id
        val lowerId = modelId.lowercase()

        // Determine capabilities based on model name/type
        val isThinkingModel = lowerId.contains("r1") ||
                lowerId.contains("reasoning") ||
                lowerId.contains("think")

        val supportsToolCalls = !isThinkingModel && (
                lowerId.contains("instruct") ||
                        lowerId.contains("chat") ||
                        lowerId.contains("coder") ||
                        lowerId.contains("qwen") ||
                        lowerId.contains("llama-3")
                )

        // Estimate context length based on model name patterns
        val contextLength = when {
            info.max_context_length != null -> info.max_context_length
            lowerId.contains("128k") -> 131072
            lowerId.contains("64k") -> 65536
            lowerId.contains("32k") || lowerId.contains("32b") -> 32768
            lowerId.contains("qwen") -> 32768
            lowerId.contains("llama-3.3") || lowerId.contains("llama-3.1") -> 131072
            lowerId.contains("deepseek-v3") -> 65536
            lowerId.contains("mixtral") -> 65536
            else -> 8192
        }

        // Generate friendly name from model ID
        val displayName = generateDisplayName(modelId)

        return AiModel(
            id = modelId,
            name = displayName,
            provider = AiProvider.DEEPINFRA,
            contextLength = contextLength,
            supportsStreaming = true,
            supportsToolCalls = supportsToolCalls,
            isThinkingModel = isThinkingModel
        )
    }

    /**
     * Generate a user-friendly display name from model ID
     */
    private fun generateDisplayName(modelId: String): String {
        // Common transformations
        val name = modelId
            .substringAfterLast("/") // Remove organization prefix
            .replace("-Instruct", "")
            .replace("-instruct", "")
            .replace("-v0.1", "")
            .replace("_", " ")

        // Title case transformation
        return name.split("-", " ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                when {
                    word.all { it.isDigit() || it == '.' } -> word
                    word.length <= 3 -> word.uppercase()
                    word.lowercase() in listOf("qwen", "llama", "mistral", "deepseek") ->
                        word.replaceFirstChar { it.uppercase() }
                    else -> word.replaceFirstChar { it.uppercase() }
                }
            }
    }

    companion object {
        /**
         * Curated list of recommended models that are known to work well
         * This is used as a fallback if dynamic fetching fails
         */
        val RECOMMENDED_MODELS = listOf(
            "Qwen/Qwen2.5-Coder-32B-Instruct",
            "Qwen/Qwen2.5-72B-Instruct",
            "deepseek-ai/DeepSeek-V3",
            "meta-llama/Llama-3.3-70B-Instruct",
            "meta-llama/Meta-Llama-3.1-405B-Instruct",
            "mistralai/Mixtral-8x22B-Instruct-v0.1",
            "deepseek-ai/DeepSeek-R1",
            "Qwen/QwQ-32B-Preview",
            "google/gemma-2-27b-it",
            "microsoft/phi-4"
        )
    }
}
