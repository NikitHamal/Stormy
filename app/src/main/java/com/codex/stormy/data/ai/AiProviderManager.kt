package com.codex.stormy.data.ai

import kotlinx.coroutines.flow.Flow

/**
 * Unified manager for all AI providers.
 */
class AiProviderManager(
    private val deepInfraApiKey: String = "",
    private val geminiApiKey: String = "",
    private val nvidiaApiKey: String = "",
    private val openRouterApiKey: String = ""
) {
    private val deepInfraProvider by lazy { DeepInfraProvider(deepInfraApiKey) }
    private val geminiProvider by lazy {
        OpenAiCompatibleProvider(
            provider = AiProvider.GEMINI,
            apiKey = geminiApiKey
        )
    }
    private val nvidiaProvider by lazy {
        OpenAiCompatibleProvider(
            provider = AiProvider.NVIDIA,
            apiKey = nvidiaApiKey
        )
    }
    private val openRouterProvider by lazy {
        OpenAiCompatibleProvider(
            provider = AiProvider.OPENROUTER,
            apiKey = openRouterApiKey,
            extraHeaders = OPENROUTER_HEADERS
        )
    }

    private val deepInfraModelService = DeepInfraModelService()
    private val geminiModelService = GeminiModelService()
    private val openAiCompatibleModelService = OpenAiCompatibleModelService()

    fun getProvider(model: AiModel): Any? {
        return when (model.provider) {
            AiProvider.DEEPINFRA -> deepInfraProvider
            AiProvider.GEMINI -> geminiProvider
            AiProvider.NVIDIA -> nvidiaProvider
            AiProvider.OPENROUTER -> openRouterProvider
        }
    }

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

            AiProvider.GEMINI -> {
                geminiProvider.streamChatCompletion(model, messages, tools, temperature, maxTokens)
            }

            AiProvider.NVIDIA -> {
                nvidiaProvider.streamChatCompletion(model, messages, tools, temperature, maxTokens)
            }

            AiProvider.OPENROUTER -> {
                openRouterProvider.streamChatCompletion(model, messages, tools, temperature, maxTokens)
            }
        }
    }

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

            AiProvider.GEMINI -> {
                geminiProvider.chatCompletion(model, messages, tools, temperature, maxTokens)
            }

            AiProvider.NVIDIA -> {
                nvidiaProvider.chatCompletion(model, messages, tools, temperature, maxTokens)
            }

            AiProvider.OPENROUTER -> {
                openRouterProvider.chatCompletion(model, messages, tools, temperature, maxTokens)
            }
        }
    }

    suspend fun fetchModelsForProvider(provider: AiProvider): Result<List<AiModel>> {
        return when (provider) {
            AiProvider.DEEPINFRA -> deepInfraModelService.fetchAvailableModels()
            AiProvider.GEMINI -> {
                if (geminiApiKey.isBlank()) {
                    Result.failure(IllegalStateException("Gemini API key is not configured"))
                } else {
                    val openAiCompat = openAiCompatibleModelService.fetchAvailableModels(
                        provider = AiProvider.GEMINI,
                        apiKey = geminiApiKey
                    )
                    openAiCompat.getOrNull()?.takeIf { it.isNotEmpty() }?.let { models ->
                        Result.success(models)
                    } ?: geminiModelService.fetchAvailableModels(geminiApiKey)
                }
            }

            AiProvider.NVIDIA -> {
                if (nvidiaApiKey.isBlank()) {
                    Result.failure(IllegalStateException("NVIDIA API key is not configured"))
                } else {
                    openAiCompatibleModelService.fetchAvailableModels(
                        provider = AiProvider.NVIDIA,
                        apiKey = nvidiaApiKey
                    )
                }
            }

            AiProvider.OPENROUTER -> {
                if (openRouterApiKey.isBlank()) {
                    Result.failure(IllegalStateException("OpenRouter API key is not configured"))
                } else {
                    openAiCompatibleModelService.fetchAvailableModels(
                        provider = AiProvider.OPENROUTER,
                        apiKey = openRouterApiKey,
                        extraHeaders = OPENROUTER_HEADERS
                    )
                }
            }
        }
    }

    suspend fun getAllAvailableModels(): Result<List<AiModel>> {
        val allModels = mutableListOf<AiModel>()
        allModels.addAll(AiModels.getAllModels())

        AiProvider.entries.forEach { provider ->
            if (isProviderConfigured(provider)) {
                fetchModelsForProvider(provider)
                    .onSuccess { models -> allModels.addAll(models) }
            }
        }

        val uniqueModels = allModels.distinctBy { it.id }
        return if (uniqueModels.isNotEmpty()) {
            Result.success(uniqueModels)
        } else {
            Result.failure(Exception("Failed to fetch models from configured providers"))
        }
    }

    fun isProviderConfigured(provider: AiProvider): Boolean {
        return when (provider) {
            AiProvider.DEEPINFRA -> true
            AiProvider.GEMINI -> geminiApiKey.isNotBlank()
            AiProvider.NVIDIA -> nvidiaApiKey.isNotBlank()
            AiProvider.OPENROUTER -> openRouterApiKey.isNotBlank()
        }
    }

    fun getConfiguredProviders(): List<AiProvider> {
        return AiProvider.entries.filter { isProviderConfigured(it) }
    }

    fun getRecommendedModelForProvider(provider: AiProvider): AiModel? {
        if (!isProviderConfigured(provider)) return null
        return AiModels.getModelsForProvider(provider).firstOrNull()
    }

    companion object {
        private val OPENROUTER_HEADERS = mapOf(
            "HTTP-Referer" to "https://github.com/codex/stormy",
            "X-Title" to "Stormy"
        )

        fun fromApiKeys(apiKeys: Map<AiProvider, String>): AiProviderManager {
            return AiProviderManager(
                deepInfraApiKey = apiKeys[AiProvider.DEEPINFRA] ?: "",
                geminiApiKey = apiKeys[AiProvider.GEMINI] ?: "",
                nvidiaApiKey = apiKeys[AiProvider.NVIDIA] ?: "",
                openRouterApiKey = apiKeys[AiProvider.OPENROUTER] ?: ""
            )
        }

        fun getFallbackModels(): List<AiModel> {
            return AiModels.getRecommendedModels()
        }
    }
}

/**
 * Builder class for AiProviderManager.
 */
class AiProviderManagerBuilder {
    private var deepInfraApiKey: String = ""
    private var geminiApiKey: String = ""
    private var nvidiaApiKey: String = ""
    private var openRouterApiKey: String = ""

    fun setDeepInfraApiKey(key: String) = apply { this.deepInfraApiKey = key }
    fun setGeminiApiKey(key: String) = apply { this.geminiApiKey = key }
    fun setNvidiaApiKey(key: String) = apply { this.nvidiaApiKey = key }
    fun setOpenRouterApiKey(key: String) = apply { this.openRouterApiKey = key }

    fun build(): AiProviderManager {
        return AiProviderManager(
            deepInfraApiKey = deepInfraApiKey,
            geminiApiKey = geminiApiKey,
            nvidiaApiKey = nvidiaApiKey,
            openRouterApiKey = openRouterApiKey
        )
    }
}
