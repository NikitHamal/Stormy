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
 */
@Serializable
enum class AiProvider(val displayName: String, val baseUrl: String) {
    DEEPINFRA("DeepInfra", "https://api.deepinfra.com/v1/openai"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1");

    companion object {
        fun fromString(value: String): AiProvider {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: DEEPINFRA
        }
    }
}

/**
 * Predefined models for DeepInfra
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
 * Predefined models for OpenRouter
 */
object OpenRouterModels {
    val CLAUDE_3_5_SONNET = AiModel(
        id = "anthropic/claude-3.5-sonnet",
        name = "Claude 3.5 Sonnet",
        provider = AiProvider.OPENROUTER,
        contextLength = 200000,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val CLAUDE_3_OPUS = AiModel(
        id = "anthropic/claude-3-opus",
        name = "Claude 3 Opus",
        provider = AiProvider.OPENROUTER,
        contextLength = 200000,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val GPT_4_TURBO = AiModel(
        id = "openai/gpt-4-turbo",
        name = "GPT-4 Turbo",
        provider = AiProvider.OPENROUTER,
        contextLength = 128000,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val GEMINI_PRO_1_5 = AiModel(
        id = "google/gemini-pro-1.5",
        name = "Gemini Pro 1.5",
        provider = AiProvider.OPENROUTER,
        contextLength = 1000000,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val LLAMA_3_3_70B = AiModel(
        id = "meta-llama/llama-3.3-70b-instruct",
        name = "Llama 3.3 70B",
        provider = AiProvider.OPENROUTER,
        contextLength = 131072,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val QWEN_2_5_CODER = AiModel(
        id = "qwen/qwen-2.5-coder-32b-instruct",
        name = "Qwen 2.5 Coder 32B",
        provider = AiProvider.OPENROUTER,
        contextLength = 32768,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val allModels = listOf(
        CLAUDE_3_5_SONNET,
        CLAUDE_3_OPUS,
        GPT_4_TURBO,
        GEMINI_PRO_1_5,
        LLAMA_3_3_70B,
        QWEN_2_5_CODER
    )

    val defaultModel = CLAUDE_3_5_SONNET
}

/**
 * Predefined models for Gemini
 */
object GeminiModels {
    val GEMINI_1_5_PRO = AiModel(
        id = "models/gemini-1.5-pro",
        name = "Gemini 1.5 Pro",
        provider = AiProvider.GEMINI,
        contextLength = 1000000,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val GEMINI_1_5_FLASH = AiModel(
        id = "models/gemini-1.5-flash",
        name = "Gemini 1.5 Flash",
        provider = AiProvider.GEMINI,
        contextLength = 1000000,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val GEMINI_PRO = AiModel(
        id = "models/gemini-pro",
        name = "Gemini Pro",
        provider = AiProvider.GEMINI,
        contextLength = 32768,
        supportsStreaming = true,
        supportsToolCalls = true
    )

    val allModels = listOf(
        GEMINI_1_5_PRO,
        GEMINI_1_5_FLASH,
        GEMINI_PRO
    )

    val defaultModel = GEMINI_1_5_FLASH
}

/**
 * Helper object for managing all models across providers
 */
object AiModels {
    /**
     * Get all available models from a specific provider
     */
    fun getModelsForProvider(provider: AiProvider): List<AiModel> {
        return when (provider) {
            AiProvider.DEEPINFRA -> DeepInfraModels.allModels
            AiProvider.OPENROUTER -> OpenRouterModels.allModels
            AiProvider.GEMINI -> GeminiModels.allModels
            AiProvider.OPENAI -> emptyList() // Can be extended later
            AiProvider.ANTHROPIC -> emptyList() // Can be extended later
        }
    }

    /**
     * Get default model for a specific provider
     */
    fun getDefaultModel(provider: AiProvider): AiModel {
        return when (provider) {
            AiProvider.DEEPINFRA -> DeepInfraModels.defaultModel
            AiProvider.OPENROUTER -> OpenRouterModels.defaultModel
            AiProvider.GEMINI -> GeminiModels.defaultModel
            AiProvider.OPENAI -> DeepInfraModels.defaultModel // Fallback
            AiProvider.ANTHROPIC -> DeepInfraModels.defaultModel // Fallback
        }
    }

    /**
     * Get all available models across all providers
     */
    fun getAllModels(): List<AiModel> {
        return DeepInfraModels.allModels +
                OpenRouterModels.allModels +
                GeminiModels.allModels
    }

    /**
     * Find a model by its ID across all providers
     */
    fun findModelById(modelId: String): AiModel? {
        return getAllModels().find { it.id == modelId }
    }

    /**
     * Get recommended models for code generation across all providers
     */
    fun getRecommendedModels(): List<AiModel> {
        return listOf(
            DeepInfraModels.QWEN_2_5_CODER_32B,
            DeepInfraModels.DEEPSEEK_V3,
            OpenRouterModels.CLAUDE_3_5_SONNET,
            OpenRouterModels.GPT_4_TURBO,
            GeminiModels.GEMINI_1_5_FLASH
        )
    }
}
