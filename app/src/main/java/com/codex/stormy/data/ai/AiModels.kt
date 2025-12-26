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
 * Currently only DeepInfra is supported (free, no API key required)
 */
@Serializable
enum class AiProvider(val displayName: String, val baseUrl: String) {
    DEEPINFRA("DeepInfra", "https://api.deepinfra.com/v1/openai");

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
 * Helper object for managing all models
 */
object AiModels {
    /**
     * Get all available models from a specific provider
     */
    fun getModelsForProvider(provider: AiProvider): List<AiModel> {
        return when (provider) {
            AiProvider.DEEPINFRA -> DeepInfraModels.allModels
        }
    }

    /**
     * Get default model for a specific provider
     */
    fun getDefaultModel(provider: AiProvider): AiModel {
        return when (provider) {
            AiProvider.DEEPINFRA -> DeepInfraModels.defaultModel
        }
    }

    /**
     * Get all available models across all providers
     */
    fun getAllModels(): List<AiModel> {
        return DeepInfraModels.allModels
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
