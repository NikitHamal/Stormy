package com.oro.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import com.oro.app.data.ai.providers.AnthropicClient
import com.oro.app.data.ai.providers.ApiKeyEntry
import com.oro.app.data.ai.providers.CustomProviderConfig
import com.oro.app.data.ai.providers.KeyPool
import com.oro.app.data.ai.providers.KeyRotation
import com.oro.app.data.ai.providers.ModelIds
import com.oro.app.data.ai.providers.ProviderSettings
import com.oro.app.data.ai.providers.ProviderSettingsMigration
import com.oro.app.data.ai.providers.WireProtocol

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
    private val customBaseUrl: String = "",
    /** Custom providers and multi-key lists; empty means single keys from [apiKeys]. */
    private val settings: ProviderSettings = ProviderSettings()
) {

    /** Every key this built-in provider can use, in the user's order. */
    private fun keysFor(provider: AiProvider): List<ApiKeyEntry> =
        settings.builtInKeys[provider.name]
            ?: apiKeys[provider]?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { listOf(ApiKeyEntry(id = "single-${provider.name}", key = it)) }
            ?: emptyList()

    private fun rotationFor(provider: AiProvider): KeyRotation =
        settings.builtInRotation[provider.name] ?: KeyRotation.FAILOVER

    /** The key a one-off call (model listing) should use. */
    private fun keyFor(provider: AiProvider): String =
        KeyPool.candidates(provider.name, keysFor(provider), rotationFor(provider)).firstOrNull()?.key
            ?: apiKeys[provider]?.trim().orEmpty()

    /** The user-added provider a model belongs to, if any. */
    private fun customFor(model: AiModel): CustomProviderConfig? {
        if (model.provider != AiProvider.CUSTOM) return null
        val namespaced = ModelIds.providerId(model.id)
        settings.customById(namespaced ?: ProviderSettingsMigration.LEGACY_CUSTOM_ID)?.let { return it }
        // A pre-multi-provider custom model, with settings not loaded: the old single server.
        if (namespaced == null && customBaseUrl.isNotBlank()) {
            return ProviderSettingsMigration.withLegacy(
                ProviderSettings(),
                mapOf(ProviderSettingsMigration.CUSTOM_PROVIDER_NAME to apiKeys[AiProvider.CUSTOM].orEmpty()),
                customBaseUrl
            ).custom.firstOrNull()
        }
        return null
    }

    private val modelService = OpenAiCompatibleModelService()
    private val geminiModelService = GeminiModelService()

    fun getProvider(model: AiModel): OpenAiCompatibleProvider {
        val custom = customFor(model)
        return if (custom != null) {
            OpenAiCompatibleProvider(
                AiProvider.CUSTOM, custom.keys.firstOrNull { it.enabled }?.key.orEmpty(),
                custom.normalizedBaseUrl, custom.headers, custom.name
            )
        } else {
            OpenAiCompatibleProvider(model.provider, keyFor(model.provider))
        }
    }

    fun streamChatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null,
        reasoningLevels: Map<String, String> = emptyMap()
    ): Flow<StreamEvent> {
        if (!usesKeylessly(model)) return streamOnce(model, messages, tools, temperature, maxTokens, reasoningLevels)
        // Keyless: a busy free model hands the request to the next one (see KeylessFallback).
        val first = if (KeylessFallback.isBusy(model.id)) emptyList() else listOf(model)
        val chain = first + KeylessFallback.candidates(model, needsNativeTools = tools != null) { id ->
            AiModels.findModelById(id)?.takeIf { usesKeylessly(it) }
        }
        if (chain.isEmpty()) return streamOnce(model, messages, tools, temperature, maxTokens, reasoningLevels)
        return flow {
            for ((index, candidate) in chain.withIndex()) {
                val last = index == chain.lastIndex
                var output = false
                var failure: StreamEvent.Error? = null
                val attempt = streamOnce(candidate, messages, tools, temperature, maxTokens, reasoningLevels)
                // A site that sends nothing at all hands over in seconds, not at the idle watchdog.
                (if (last) attempt else KeylessFallback.guardSilence(candidate, attempt)).collect { event ->
                    if (failure != null) return@collect
                    when (event) {
                        is StreamEvent.Error ->
                            if (!output && !last && KeylessFallback.shouldFallBack(event)) failure = event else emit(event)
                        is StreamEvent.ContentDelta, is StreamEvent.ReasoningDelta, is StreamEvent.ToolCalls -> {
                            if (!output && candidate.id != model.id) {
                                emit(StreamEvent.ReasoningDelta("${model.name} was busy, so ${candidate.name} answered this step.\n\n"))
                            }
                            output = true
                            emit(event)
                        }
                        is StreamEvent.Completed -> {
                            KeylessFallback.markHealthy(candidate.id)
                            emit(event)
                        }
                        else -> emit(event)
                    }
                }
                val failed = failure ?: return@flow
                KeylessFallback.markBusy(candidate.id, failed)
            }
        }
    }

    /** True when this model is being used without a key, so switching to another free model is safe. */
    private fun usesKeylessly(model: AiModel): Boolean {
        val p = model.provider
        if (p == AiProvider.CUSTOM || p.requiresApiKey) return false
        return keysFor(p).none { it.enabled && it.key.isNotBlank() }
    }

    private fun streamOnce(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>?,
        temperature: Float,
        maxTokens: Int?,
        reasoningLevels: Map<String, String>
    ): Flow<StreamEvent> {
        configurationError(model)?.let { return flowOf(StreamEvent.Error(it)) }
        // Each model gets its own level: a keyless stand-in has a different scale.
        val level = ReasoningControls.chosen(model, reasoningLevels)
        // Community bridges speak their own protocols and take no tools.
        if (model.provider.isCommunity) {
            return com.oro.app.data.ai.community.CommunityClients.stream(model, messages, level)
        }
        val custom = customFor(model)
        if (custom != null) return streamCustom(custom, model, messages, tools, temperature, maxTokens, reasoningLevels)
        val provider = model.provider
        return rotating(provider.name, keysFor(provider), rotationFor(provider)) { key ->
            OpenAiCompatibleProvider(provider, key)
                .streamChatCompletion(model, messages, tools, temperature, maxTokens, level)
        }
    }

    private fun streamCustom(
        config: CustomProviderConfig,
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>?,
        temperature: Float,
        maxTokens: Int?,
        reasoningLevels: Map<String, String>
    ): Flow<StreamEvent> {
        // The namespace is ours; the server knows the model by its own id.
        val upstream = model.copy(id = ModelIds.upstream(model.id))
        return rotating("custom:${config.id}", config.keys, config.rotation) { key ->
            when (config.protocol) {
                WireProtocol.OPENAI -> OpenAiCompatibleProvider(
                    AiProvider.CUSTOM, key, config.normalizedBaseUrl, config.headers, config.name
                ).streamChatCompletion(
                    upstream, messages, tools, temperature, maxTokens ?: config.maxOutputTokens,
                    ReasoningControls.chosen(model, reasoningLevels)
                )
                WireProtocol.ANTHROPIC -> AnthropicClient(config.name, config.normalizedBaseUrl, key, config.headers).stream(
                    model = upstream.id,
                    messages = messages,
                    tools = tools?.takeIf { model.supportsToolCalls },
                    temperature = temperature,
                    maxTokens = maxTokens ?: config.maxOutputTokens ?: DEFAULT_ANTHROPIC_MAX_TOKENS,
                    effort = ReasoningControls.chosen(ReasoningControls.forAnthropicCustom(model.id), model.id, reasoningLevels)
                )
            }
        }
    }

    /**
     * Run [attempt] with each candidate key until one is not turned away.
     *
     * A key-shaped failure (rate limit, no credits, rejected key) that arrives
     * before any output moves the same request to the next key; the caller
     * only ever sees the last key's failure. Health is recorded either way so
     * the next request starts on a good key.
     */
    private fun rotating(
        poolId: String,
        keys: List<ApiKeyEntry>,
        rotation: KeyRotation,
        attempt: (String) -> Flow<StreamEvent>
    ): Flow<StreamEvent> {
        val candidates = KeyPool.candidates(poolId, keys, rotation)
        if (candidates.size <= 1) {
            val entry = candidates.firstOrNull() ?: return attempt("")
            return attempt(entry.key).onEach { event ->
                when (event) {
                    is StreamEvent.Error -> KeyPool.reportFailure(entry.id, event)
                    is StreamEvent.Completed -> KeyPool.reportSuccess(entry.id)
                    else -> Unit
                }
            }
        }
        return flow {
            for ((index, entry) in candidates.withIndex()) {
                val last = index == candidates.lastIndex
                var output = false
                var rotate = false
                attempt(entry.key).collect { event ->
                    if (rotate) return@collect
                    when (event) {
                        is StreamEvent.Error -> {
                            val health = KeyPool.reportFailure(entry.id, event)
                            if (health != null && !output && !last) rotate = true else emit(event)
                        }
                        is StreamEvent.ContentDelta, is StreamEvent.ReasoningDelta, is StreamEvent.ToolCalls -> {
                            output = true
                            emit(event)
                        }
                        is StreamEvent.Completed -> {
                            KeyPool.reportSuccess(entry.id)
                            emit(event)
                        }
                        else -> emit(event)
                    }
                }
                if (!rotate) return@flow
            }
        }
    }

    suspend fun chatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null,
        reasoningLevels: Map<String, String> = emptyMap()
    ): Result<ChatCompletionResponse> {
        configurationError(model)?.let {
            return Result.failure(IllegalStateException(it))
        }
        if (model.provider.isCommunity) return collectCommunity(model, messages)
        // Collected from the stream so custom protocols and key rotation
        // behave exactly as they do for streaming requests.
        if (customFor(model) != null || keysFor(model.provider).size > 1) {
            return collect(model, streamChatCompletion(model, messages, tools, temperature, maxTokens, reasoningLevels))
        }
        return OpenAiCompatibleProvider(model.provider, keyFor(model.provider))
            .chatCompletion(model, messages, tools, temperature, maxTokens, ReasoningControls.chosen(model, reasoningLevels))
    }

    /** Model-level check: custom models need their provider to still exist. */
    fun configurationError(model: AiModel): String? {
        if (model.provider == AiProvider.CUSTOM) {
            val config = customFor(model)
                ?: return "This model's provider was removed. Pick another model, or add the provider again in Settings > AI Providers."
            return when {
                !config.enabled -> "${config.name} is turned off in Settings > AI Providers."
                config.normalizedBaseUrl.isBlank() -> "Set a base URL for ${config.name} in Settings > AI Providers."
                else -> null
            }
        }
        return configurationError(model.provider)
    }

    /**
     * Human-readable reason this provider cannot be used right now, or null if
     * it can. Centralised so the stream and non-stream paths cannot disagree,
     * which they previously did.
     */
    fun configurationError(provider: AiProvider): String? = when {
        provider.requiresApiKey && keysFor(provider).none { it.enabled && it.key.isNotBlank() } ->
            "${provider.displayName} needs an API key. Add one in Settings > AI Providers."
        provider == AiProvider.CUSTOM && settings.custom.none { it.enabled && it.normalizedBaseUrl.isNotBlank() } &&
            customBaseUrl.isBlank() ->
            "Add a custom provider in Settings > AI Providers."
        else -> null
    }

    /** Collect a whole stream into one response: text, reasoning and tool calls. */
    private suspend fun collect(model: AiModel, stream: Flow<StreamEvent>): Result<ChatCompletionResponse> {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        val calls = mutableListOf<ToolCallResponse>()
        var error: String? = null
        var finish: String? = null
        stream.collect { event ->
            when (event) {
                is StreamEvent.ContentDelta -> text.append(event.content)
                is StreamEvent.ReasoningDelta -> reasoning.append(event.reasoning)
                is StreamEvent.ToolCalls -> calls += event.toolCalls
                is StreamEvent.FinishReason -> finish = event.reason
                is StreamEvent.Error -> error = event.message
                else -> Unit
            }
        }
        error?.takeIf { text.isEmpty() && calls.isEmpty() }?.let { return Result.failure(java.io.IOException(it)) }
        return Result.success(
            ChatCompletionResponse(
                model = model.id,
                choices = listOf(
                    Choice(
                        message = ResponseMessage(
                            role = "assistant",
                            content = text.toString(),
                            reasoningContent = reasoning.toString().ifEmpty { null },
                            toolCalls = calls.ifEmpty { null }
                        ),
                        finishReason = finish
                    )
                )
            )
        )
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
        if (provider == AiProvider.CUSTOM && settings.custom.isNotEmpty()) {
            val all = settings.custom.filter { it.enabled }.map { fetchModelsForCustom(it) }
            all.firstOrNull { it.isFailure }?.takeIf { all.none { r -> r.isSuccess } }?.let { return it }
            return Result.success(all.flatMap { it.getOrNull().orEmpty() })
        }

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
     * A custom provider's live catalogue, as namespaced CUSTOM models. Tries
     * each key in order, so one dead key does not hide the catalogue.
     */
    suspend fun fetchModelsForCustom(config: CustomProviderConfig): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        if (config.normalizedBaseUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Set a base URL for ${config.name} first."))
        }
        val keys = KeyPool.candidates("custom:${config.id}", config.keys, KeyRotation.FAILOVER).map { it.key }
            .ifEmpty { listOf("") }
        var last: Result<List<AiModel>> = Result.failure(IllegalStateException("No models"))
        for (key in keys) {
            last = when (config.protocol) {
                WireProtocol.OPENAI -> modelService.fetchAvailableModels(
                    provider = AiProvider.CUSTOM,
                    apiKey = key,
                    extraHeaders = config.headers,
                    baseUrlOverride = config.normalizedBaseUrl
                ).map { models ->
                    models.map { m ->
                        m.copy(
                            id = ModelIds.of(config.id, m.id),
                            provider = AiProvider.CUSTOM,
                            supportsToolCalls = config.supportsTools
                        )
                    }
                }
                WireProtocol.ANTHROPIC -> AnthropicClient(config.name, config.normalizedBaseUrl, key, config.headers)
                    .listModels()
                    .map { list ->
                        list.map { (id, name) ->
                            AiModel(
                                id = ModelIds.of(config.id, id),
                                name = name,
                                provider = AiProvider.CUSTOM,
                                contextLength = ModelCapabilities.resolveContextLength(id, null),
                                supportsToolCalls = config.supportsTools
                            )
                        }
                    }
            }
            if (last.isSuccess) return@withContext last
        }
        last
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
        /** Anthropic requires max_tokens; used when neither caller nor provider sets one. */
        const val DEFAULT_ANTHROPIC_MAX_TOKENS = 8_192

        fun fromApiKeys(
            apiKeys: Map<AiProvider, String>,
            customBaseUrl: String = "",
            settings: ProviderSettings = ProviderSettings()
        ): AiProviderManager = AiProviderManager(apiKeys, customBaseUrl, settings)

        fun getFallbackModels(): List<AiModel> = AiModels.getRecommendedModels()
    }
}
