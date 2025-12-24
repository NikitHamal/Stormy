package com.codex.stormy.data.ai

import kotlinx.coroutines.flow.Flow

/**
 * Unified manager for all AI providers
 * Handles provider selection, API key management, and dynamic model fetching
 *
 * DeepInfra works without an API key using the free inference endpoint.
 * OpenRouter and Gemini require API keys.
 */
class AiProviderManager(
    private val deepInfraApiKey: String = "",
    private val openRouterApiKey: String = "",
    private val geminiApiKey: String = ""
) {

    // Provider instances (lazy initialization)
    // DeepInfra works without API key - it uses the free inference endpoint
    private val deepInfraProvider by lazy { DeepInfraProvider(deepInfraApiKey) }
    private val openRouterProvider by lazy { OpenRouterProvider(openRouterApiKey) }
    private val geminiProvider by lazy { GeminiProvider(geminiApiKey) }

    // Model service instances
    private val deepInfraModelService = DeepInfraModelService()
    private val openRouterModelService = OpenRouterModelService()
    private val geminiModelService = GeminiModelService()

    /**
     * Get the appropriate provider instance based on the model's provider
     * DeepInfra is always available (free API), others require API keys
     */
    fun getProvider(model: AiModel): Any? {
        return when (model.provider) {
            // DeepInfra works without API key - always return provider
            AiProvider.DEEPINFRA -> deepInfraProvider
            AiProvider.OPENROUTER -> if (openRouterApiKey.isNotBlank()) openRouterProvider else null
            AiProvider.GEMINI -> if (geminiApiKey.isNotBlank()) geminiProvider else null
            else -> null
        }
    }

    /**
     * Stream chat completion using the appropriate provider
     * DeepInfra works without API key (free inference endpoint)
     */
    fun streamChatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): Flow<StreamEvent>? {
        return when (model.provider) {
            // DeepInfra works without API key - always allow
            AiProvider.DEEPINFRA -> {
                deepInfraProvider.streamChatCompletion(model, messages, tools, temperature, maxTokens)
            }
            AiProvider.OPENROUTER -> {
                if (openRouterApiKey.isBlank()) return null
                openRouterProvider.streamChatCompletion(model, messages, tools, temperature, maxTokens)
            }
            AiProvider.GEMINI -> {
                if (geminiApiKey.isBlank()) return null
                geminiProvider.streamChatCompletion(model, messages, tools, temperature, maxTokens)
            }
            else -> null
        }
    }

    /**
     * Non-streaming chat completion using the appropriate provider
     * DeepInfra works without API key (free inference endpoint)
     */
    suspend fun chatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): Result<ChatCompletionResponse> {
        return when (model.provider) {
            // DeepInfra works without API key - always allow
            AiProvider.DEEPINFRA -> {
                deepInfraProvider.chatCompletion(model, messages, tools, temperature, maxTokens)
            }
            AiProvider.OPENROUTER -> {
                if (openRouterApiKey.isBlank()) {
                    return Result.failure(Exception("OpenRouter API key not configured. Please add your API key in Settings."))
                }
                openRouterProvider.chatCompletion(model, messages, tools, temperature, maxTokens)
            }
            AiProvider.GEMINI -> {
                if (geminiApiKey.isBlank()) {
                    return Result.failure(Exception("Gemini API key not configured. Please add your API key in Settings."))
                }
                geminiProvider.chatCompletion(model, messages, tools, temperature, maxTokens)
            }
            else -> Result.failure(Exception("Unsupported provider: ${model.provider}"))
        }
    }

    /**
     * Fetch available models for a specific provider
     */
    suspend fun fetchModelsForProvider(provider: AiProvider): Result<List<AiModel>> {
        return when (provider) {
            AiProvider.DEEPINFRA -> deepInfraModelService.fetchAvailableModels()
            AiProvider.OPENROUTER -> {
                // OpenRouter can work without API key but provides more info with it
                openRouterModelService.fetchAvailableModels(
                    apiKey = openRouterApiKey.takeIf { it.isNotBlank() }
                )
            }
            AiProvider.GEMINI -> {
                if (geminiApiKey.isBlank()) {
                    return Result.failure(Exception("Gemini API key required to fetch models"))
                }
                geminiModelService.fetchAvailableModels(geminiApiKey)
            }
            else -> Result.failure(Exception("Model fetching not supported for ${provider.displayName}"))
        }
    }

    /**
     * Get all available models across all configured providers
     * Combines static predefined models with dynamically fetched models
     */
    suspend fun getAllAvailableModels(): Result<List<AiModel>> {
        val allModels = mutableListOf<AiModel>()
        val errors = mutableListOf<String>()

        // Add predefined models
        allModels.addAll(AiModels.getAllModels())

        // Try to fetch dynamic models from each provider
        if (deepInfraApiKey.isNotBlank()) {
            fetchModelsForProvider(AiProvider.DEEPINFRA)
                .onSuccess { models -> allModels.addAll(models) }
                .onFailure { errors.add("DeepInfra: ${it.message}") }
        }

        if (openRouterApiKey.isNotBlank()) {
            fetchModelsForProvider(AiProvider.OPENROUTER)
                .onSuccess { models -> allModels.addAll(models) }
                .onFailure { errors.add("OpenRouter: ${it.message}") }
        }

        if (geminiApiKey.isNotBlank()) {
            fetchModelsForProvider(AiProvider.GEMINI)
                .onSuccess { models -> allModels.addAll(models) }
                .onFailure { errors.add("Gemini: ${it.message}") }
        }

        // Remove duplicates by ID
        val uniqueModels = allModels.distinctBy { it.id }

        return if (uniqueModels.isNotEmpty()) {
            Result.success(uniqueModels)
        } else {
            Result.failure(Exception("Failed to fetch models: ${errors.joinToString(", ")}"))
        }
    }

    /**
     * Check if a provider is configured and ready to use
     * DeepInfra is always available (free API), others require API keys
     */
    fun isProviderConfigured(provider: AiProvider): Boolean {
        return when (provider) {
            // DeepInfra always works - free API
            AiProvider.DEEPINFRA -> true
            AiProvider.OPENROUTER -> openRouterApiKey.isNotBlank()
            AiProvider.GEMINI -> geminiApiKey.isNotBlank()
            else -> false
        }
    }

    /**
     * Get a list of all configured providers
     */
    fun getConfiguredProviders(): List<AiProvider> {
        return AiProvider.entries.filter { isProviderConfigured(it) }
    }

    /**
     * Get the recommended model for a provider
     */
    fun getRecommendedModelForProvider(provider: AiProvider): AiModel? {
        if (!isProviderConfigured(provider)) return null

        return when (provider) {
            AiProvider.DEEPINFRA -> DeepInfraModels.defaultModel
            AiProvider.OPENROUTER -> OpenRouterModels.defaultModel
            AiProvider.GEMINI -> GeminiModels.defaultModel
            else -> null
        }
    }

    companion object {
        /**
         * Create an AiProviderManager from a map of API keys
         */
        fun fromApiKeys(apiKeys: Map<AiProvider, String>): AiProviderManager {
            return AiProviderManager(
                deepInfraApiKey = apiKeys[AiProvider.DEEPINFRA] ?: "",
                openRouterApiKey = apiKeys[AiProvider.OPENROUTER] ?: "",
                geminiApiKey = apiKeys[AiProvider.GEMINI] ?: ""
            )
        }

        /**
         * Get fallback models when API is not available
         * These are curated, high-quality models that work well for code generation
         */
        fun getFallbackModels(): List<AiModel> {
            return listOf(
                // DeepInfra models (free tier available)
                DeepInfraModels.QWEN_2_5_CODER_32B,
                DeepInfraModels.DEEPSEEK_V3,
                DeepInfraModels.LLAMA_3_3_70B,

                // OpenRouter models (pay-as-you-go)
                OpenRouterModels.CLAUDE_3_5_SONNET,
                OpenRouterModels.GPT_4_TURBO,

                // Gemini models (free tier available)
                GeminiModels.GEMINI_1_5_FLASH,
                GeminiModels.GEMINI_1_5_PRO
            )
        }
    }
}

/**
 * Builder class for AiProviderManager
 */
class AiProviderManagerBuilder {
    private var deepInfraApiKey: String = ""
    private var openRouterApiKey: String = ""
    private var geminiApiKey: String = ""

    fun setDeepInfraApiKey(key: String) = apply { this.deepInfraApiKey = key }
    fun setOpenRouterApiKey(key: String) = apply { this.openRouterApiKey = key }
    fun setGeminiApiKey(key: String) = apply { this.geminiApiKey = key }

    fun build(): AiProviderManager {
        return AiProviderManager(
            deepInfraApiKey = deepInfraApiKey,
            openRouterApiKey = openRouterApiKey,
            geminiApiKey = geminiApiKey
        )
    }
}
