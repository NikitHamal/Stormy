package com.codex.stormy.data.ai

import kotlinx.serialization.Serializable

/**
 * Represents an AI model available for use
 */
@Serializable
data class AiModel(
    val id: String,
    val name: String,
    val provider: AiProvider,
    val contextLength: Int = 4096,
    val supportsStreaming: Boolean = true,
    val supportsToolCalls: Boolean = true,
    val isThinkingModel: Boolean = false
)

/**
 * Supported AI providers
 * DeepInfra is available without API key. Other providers require API keys.
 */
@Serializable
enum class AiProvider(
    val displayName: String,
    val baseUrl: String,
    val requiresApiKey: Boolean
) {
    DEEPINFRA("DeepInfra", "https://api.deepinfra.com/v1/openai", false),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", true),
    NVIDIA("NVIDIA", "https://integrate.api.nvidia.com/v1", true),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", true);

    companion object {
        fun fromString(value: String): AiProvider {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: DEEPINFRA
        }
    }
}

/**
 * Predefined models for DeepInfra
 * DeepInfra provides free inference without requiring an API key
 */
object DeepInfraModels {
    val QWEN_2_5_72B_INSTRUCT = AiModel(
        id = "Qwen/Qwen2.5-72B-Instruct",
        name = "Qwen 2.5 72B Instruct",
        provider = AiProvider.DEEPINFRA,
        contextLength = 32768,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val QWEN_2_5_CODER_32B = AiModel(
        id = "Qwen/Qwen2.5-Coder-32B-Instruct",
        name = "Qwen 2.5 Coder 32B",
        provider = AiProvider.DEEPINFRA,
        contextLength = 32768,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val DEEPSEEK_V3 = AiModel(
        id = "deepseek-ai/DeepSeek-V3",
        name = "DeepSeek V3",
        provider = AiProvider.DEEPINFRA,
        contextLength = 65536,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val LLAMA_3_3_70B = AiModel(
        id = "meta-llama/Llama-3.3-70B-Instruct",
        name = "Llama 3.3 70B",
        provider = AiProvider.DEEPINFRA,
        contextLength = 131072,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val LLAMA_3_1_405B = AiModel(
        id = "meta-llama/Meta-Llama-3.1-405B-Instruct",
        name = "Llama 3.1 405B",
        provider = AiProvider.DEEPINFRA,
        contextLength = 32768,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val MIXTRAL_8X22B = AiModel(
        id = "mistralai/Mixtral-8x22B-Instruct-v0.1",
        name = "Mixtral 8x22B",
        provider = AiProvider.DEEPINFRA,
        contextLength = 65536,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val DEEPSEEK_R1 = AiModel(
        id = "deepseek-ai/DeepSeek-R1",
        name = "DeepSeek R1 (Thinking)",
        provider = AiProvider.DEEPINFRA,
        contextLength = 65536,
        supportsStreaming = true,
        supportsToolCalls = false,
        isThinkingModel = true
    )

    val allModels = listOf(
        QWEN_2_5_CODER_32B,
        QWEN_2_5_72B_INSTRUCT,
        DEEPSEEK_V3,
        LLAMA_3_3_70B,
        LLAMA_3_1_405B,
        MIXTRAL_8X22B,
        DEEPSEEK_R1
    )

    val defaultModel = QWEN_2_5_CODER_32B
}

/**
 * Gemini models are fetched dynamically from the official API.
 * We do not ship static IDs here to avoid stale model references.
 */
object GeminiModels {
    val allModels: List<AiModel> = emptyList()
    val defaultModel: AiModel? = null
}

/**
 * NVIDIA models are fetched dynamically from the official API.
 */
object NvidiaModels {
    val allModels: List<AiModel> = emptyList()
    val defaultModel: AiModel? = null
}

/**
 * OpenRouter models are fetched dynamically from the official API.
 */
object OpenRouterModels {
    val allModels: List<AiModel> = emptyList()
    val defaultModel: AiModel? = null
}

/**
 * Helper object for managing all models
 */
object AiModels {
    /**
     * Get all available models from a specific provider
     */
    fun getModelsForProvider(provider: AiProvider): List<AiModel> {
        return when (provider) {
            AiProvider.DEEPINFRA -> DeepInfraModels.allModels
            AiProvider.GEMINI -> GeminiModels.allModels
            AiProvider.NVIDIA -> NvidiaModels.allModels
            AiProvider.OPENROUTER -> OpenRouterModels.allModels
        }
    }

    /**
     * Get default model for a specific provider
     */
    fun getDefaultModel(provider: AiProvider): AiModel {
        return when (provider) {
            AiProvider.DEEPINFRA -> DeepInfraModels.defaultModel
            AiProvider.GEMINI -> GeminiModels.defaultModel ?: DeepInfraModels.defaultModel
            AiProvider.NVIDIA -> NvidiaModels.defaultModel ?: DeepInfraModels.defaultModel
            AiProvider.OPENROUTER -> OpenRouterModels.defaultModel ?: DeepInfraModels.defaultModel
        }
    }

    /**
     * Get all available models across all providers
     */
    fun getAllModels(): List<AiModel> {
        return buildList {
            addAll(DeepInfraModels.allModels)
            addAll(GeminiModels.allModels)
            addAll(NvidiaModels.allModels)
            addAll(OpenRouterModels.allModels)
        }
    }

    /**
     * Find a model by its ID across all providers
     */
    fun findModelById(modelId: String): AiModel? {
        return getAllModels().find { it.id == modelId }
    }

    /**
     * Get recommended models for code generation
     */
    fun getRecommendedModels(): List<AiModel> {
        return listOf(
            DeepInfraModels.QWEN_2_5_CODER_32B,
            DeepInfraModels.DEEPSEEK_V3,
            DeepInfraModels.LLAMA_3_3_70B
        )
    }
}
