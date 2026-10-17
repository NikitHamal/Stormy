package com.oro.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * Entry point for every AI call in the app.
 *
 * This used to carry one constructor parameter, one lazy field, one builder
 * setter and one branch in each of five exhaustive `when` blocks per provider.
 * Adding a provider meant eight coordinated edits, and the `when` blocks drifted
 * out of sync (model fetching special-cased three providers differently for no
 * reason). Everything is keyed off [AiProvider] now, so a new provider is one
 * enum constant and nothing here changes at all.
 */
class AiProviderManager(
    private val apiKeys: Map<AiProvider, String> = emptyMap(),
    /** Base URL for [AiProvider.CUSTOM]; ignored for the rest. */
    private val customBaseUrl: String = ""
) {

    private val clients = mutableMapOf<AiProvider, OpenAiCompatibleProvider>()

    private fun clientFor(provider: AiProvider): OpenAiCompatibleProvider =
        synchronized(clients) {
            clients.getOrPut(provider) {
                OpenAiCompatibleProvider(
                    provider = provider,
                    apiKey = keyFor(provider),
                    baseUrlOverride = if (provider == AiProvider.CUSTOM) customBaseUrl else ""
                )
            }
        }

    private fun keyFor(provider: AiProvider): String = apiKeys[provider]?.trim().orEmpty()

    private val modelService = OpenAiCompatibleModelService()
    private val geminiModelService = GeminiModelService()

    fun getProvider(model: AiModel): OpenAiCompatibleProvider = clientFor(model.provider)

    fun streamChatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null,
        reasoningEffort: ReasoningEffort? = null
    ): Flow<StreamEvent> {
        configurationError(model.provider)?.let { return flowOf(StreamEvent.Error(it)) }
        // Community bridges speak their own protocols and take no tools.
        if (model.provider.isCommunity) {
            return com.oro.app.data.ai.community.CommunityClients.stream(model, messages)
        }
        return clientFor(model.provider)
            .streamChatCompletion(model, messages, tools, temperature, maxTokens, reasoningEffort)
    }

    suspend fun chatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null,
        reasoningEffort: ReasoningEffort? = null
    ): Result<ChatCompletionResponse> {
        configurationError(model.provider)?.let {
            return Result.failure(IllegalStateException(it))
        }
        if (model.provider.isCommunity) return collectCommunity(model, messages)
        return clientFor(model.provider)
            .chatCompletion(model, messages, tools, temperature, maxTokens, reasoningEffort)
    }

    /**
     * Human-readable reason this provider cannot be used right now, or null if
     * it can. Centralised so the stream and non-stream paths cannot disagree,
     * which they previously did.
     */
    fun configurationError(provider: AiProvider): String? = when {
        provider.requiresApiKey && keyFor(provider).isBlank() ->
            "${provider.displayName} needs an API key. Add one in Settings > AI Providers."
        provider == AiProvider.CUSTOM && customBaseUrl.isBlank() ->
            "Set a base URL for the custom provider in Settings > AI Providers."
        else -> null
    }

    /** Non-streaming call on a community bridge: collect its stream. */
    private suspend fun collectCommunity(
        model: AiModel,
        messages: List<ChatRequestMessage>
    ): Result<ChatCompletionResponse> {
        val text = StringBuilder()
        var error: String? = null
        com.oro.app.data.ai.community.CommunityClients.stream(model, messages).collect { event ->
            when (event) {
                is StreamEvent.ContentDelta -> text.append(event.content)
                is StreamEvent.Error -> error = event.message
                else -> Unit
            }
        }
        error?.takeIf { text.isEmpty() }?.let { return Result.failure(java.io.IOException(it)) }
        return Result.success(
            ChatCompletionResponse(
                model = model.id,
                choices = listOf(Choice(message = ResponseMessage(role = "assistant", content = text.toString())))
            )
        )
    }

    suspend fun fetchModelsForProvider(provider: AiProvider): Result<List<AiModel>> {
        configurationError(provider)?.let { return Result.failure(IllegalStateException(it)) }
        // Community bridges have no model listing; their catalogue is the seed.
        if (provider.isCommunity) return Result.success(AiModels.getModelsForProvider(provider))

        val viaOpenAi = modelService.fetchAvailableModels(
            provider = provider,
            apiKey = keyFor(provider),
            extraHeaders = provider.extraHeaders,
            baseUrlOverride = if (provider == AiProvider.CUSTOM) customBaseUrl else ""
        )

        val models = viaOpenAi.getOrNull()
        if (!models.isNullOrEmpty()) return Result.success(models)

        // Gemini's OpenAI-compatibility layer does not list models, so fall back
        // to its native endpoint. Only Gemini needs this.
        if (provider == AiProvider.GEMINI) {
            return geminiModelService.fetchAvailableModels(keyFor(provider))
        }
        return viaOpenAi
    }

    /**
     * Every model the user can actually reach, seeds plus live catalogues.
     *
     * Providers are queried concurrently. Serially this was one round trip per
     * configured provider before the picker could open -- with ten providers
     * that is ten sequential network waits on a phone.
     */
    suspend fun getAllAvailableModels(): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        val configured = getConfiguredProviders()
        val fetched = coroutineScope {
            configured
                .map { provider -> async { fetchModelsForProvider(provider).getOrNull().orEmpty() } }
                .flatMap { it.await() }
        }

        // Seeds first so a live entry with the same id replaces the seed rather
        // than the other way round.
        val merged = (AiModels.getAllModels() + fetched)
            .filter { it.provider in configured || !it.provider.requiresApiKey }
            .associateBy { it.provider to it.id }
            .values
            .toList()

        if (merged.isEmpty()) {
            Result.failure(IllegalStateException("No models available from the configured providers"))
        } else {
            Result.success(merged)
        }
    }

    fun isProviderConfigured(provider: AiProvider): Boolean = configurationError(provider) == null

    fun getConfiguredProviders(): List<AiProvider> =
        AiProvider.entries.filter(::isProviderConfigured)

    fun getRecommendedModelForProvider(provider: AiProvider): AiModel? =
        if (isProviderConfigured(provider)) AiModels.getModelsForProvider(provider).firstOrNull()
        else null

    companion object {
        fun fromApiKeys(
            apiKeys: Map<AiProvider, String>,
            customBaseUrl: String = ""
        ): AiProviderManager = AiProviderManager(apiKeys, customBaseUrl)

        fun getFallbackModels(): List<AiModel> = AiModels.getRecommendedModels()
    }
}
