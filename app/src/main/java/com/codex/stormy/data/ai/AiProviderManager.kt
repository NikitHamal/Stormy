package com.codex.stormy.data.ai

import kotlinx.coroutines.flow.Flow

/**
 * Unified manager for AI providers
 * Currently only DeepInfra is supported (free, no API key required)
 */
class AiProviderManager(
    private val deepInfraApiKey: String = ""
) {
    // DeepInfra works without API key - it uses the free inference endpoint
    private val deepInfraProvider by lazy { DeepInfraProvider(deepInfraApiKey) }

    // Model service instance
    private val deepInfraModelService = DeepInfraModelService()

    /**
     * Get the appropriate provider instance based on the model's provider
     * DeepInfra is always available (free API)
     */
    fun getProvider(model: AiModel): Any? {
        return when (model.provider) {
            AiProvider.DEEPINFRA -> deepInfraProvider
        }
    }

    /**
     * Stream chat completion using DeepInfra provider
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
            AiProvider.DEEPINFRA -> {
                deepInfraProvider.streamChatCompletion(model, messages, tools, temperature, maxTokens)
            }
        }
    }

    /**
     * Non-streaming chat completion using DeepInfra provider
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
            AiProvider.DEEPINFRA -> {
                deepInfraProvider.chatCompletion(model, messages, tools, temperature, maxTokens)
            }
        }
    }

    /**
     * Fetch available models for DeepInfra
     */
    suspend fun fetchModelsForProvider(provider: AiProvider): Result<List<AiModel>> {
        return when (provider) {
            AiProvider.DEEPINFRA -> deepInfraModelService.fetchAvailableModels()
        }
    }

    /**
     * Get all available models (DeepInfra only)
     */
    suspend fun getAllAvailableModels(): Result<List<AiModel>> {
        val allModels = mutableListOf<AiModel>()

        // Add predefined models
        allModels.addAll(AiModels.getAllModels())

        // Try to fetch dynamic models from DeepInfra
        fetchModelsForProvider(AiProvider.DEEPINFRA)
            .onSuccess { models -> allModels.addAll(models) }

        // Remove duplicates by ID
        val uniqueModels = allModels.distinctBy { it.id }

        return if (uniqueModels.isNotEmpty()) {
            Result.success(uniqueModels)
        } else {
            Result.failure(Exception("Failed to fetch models"))
        }
    }

    /**
     * Check if a provider is configured and ready to use
     * DeepInfra is always available (free API)
     */
    fun isProviderConfigured(provider: AiProvider): Boolean {
        return when (provider) {
            AiProvider.DEEPINFRA -> true
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
        }
    }

    companion object {
        /**
         * Create an AiProviderManager from a map of API keys
         */
        fun fromApiKeys(apiKeys: Map<AiProvider, String>): AiProviderManager {
            return AiProviderManager(
                deepInfraApiKey = apiKeys[AiProvider.DEEPINFRA] ?: ""
            )
        }

        /**
         * Get fallback models when API is not available
         * These are curated, high-quality models that work well for code generation
         */
        fun getFallbackModels(): List<AiModel> {
            return listOf(
                DeepInfraModels.QWEN_2_5_CODER_32B,
                DeepInfraModels.DEEPSEEK_V3,
                DeepInfraModels.LLAMA_3_3_70B
            )
        }
    }
}

/**
 * Builder class for AiProviderManager
 */
class AiProviderManagerBuilder {
    private var deepInfraApiKey: String = ""

    fun setDeepInfraApiKey(key: String) = apply { this.deepInfraApiKey = key }

    fun build(): AiProviderManager {
        return AiProviderManager(
            deepInfraApiKey = deepInfraApiKey
        )
    }
}
