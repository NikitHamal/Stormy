package com.oro.app.ui.screens.providers

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oro.app.OroApplication
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.AiProviderManager
import com.oro.app.data.ai.OpenAiCompatibleModelService
import com.oro.app.data.ai.providers.ApiKeyEntry
import com.oro.app.data.ai.providers.CustomProviderConfig
import com.oro.app.data.ai.providers.KeyPool
import com.oro.app.data.ai.providers.KeyRotation
import com.oro.app.data.ai.providers.KeyState
import com.oro.app.data.ai.providers.ProviderPreset
import com.oro.app.data.ai.providers.ProviderSettings
import com.oro.app.data.repository.AiModelRepository
import com.oro.app.data.repository.PreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** A key with its live health. */
@Immutable
data class KeyUi(val entry: ApiKeyEntry, val state: KeyState)

/** Which provider a sheet is about: a built-in by enum, or a custom one by id. */
sealed interface ProviderRef {
    /** Pool id in [KeyPool]; matches the manager's. */
    val poolId: String

    data class BuiltIn(val provider: AiProvider) : ProviderRef {
        override val poolId: String get() = provider.name
    }

    data class Custom(val id: String) : ProviderRef {
        override val poolId: String get() = "custom:$id"
    }
}

@Immutable
data class ProvidersUiState(
    val settings: ProviderSettings = ProviderSettings(),
    /** Key health by key id; refreshed on a slow tick while the screen is visible. */
    val health: Map<String, KeyState> = emptyMap(),
    val busy: Set<String> = emptySet()
) {
    fun keysOf(ref: ProviderRef): List<KeyUi> {
        val entries = when (ref) {
            is ProviderRef.BuiltIn -> settings.builtInKeys[ref.provider.name].orEmpty()
            is ProviderRef.Custom -> settings.customById(ref.id)?.keys.orEmpty()
        }
        return entries.map { KeyUi(it, health[it.id] ?: KeyState()) }
    }

    fun rotationOf(ref: ProviderRef): KeyRotation = when (ref) {
        is ProviderRef.BuiltIn -> settings.builtInRotation[ref.provider.name] ?: KeyRotation.FAILOVER
        is ProviderRef.Custom -> settings.customById(ref.id)?.rotation ?: KeyRotation.FAILOVER
    }
}

/**
 * Providers, keys and custom-provider models.
 *
 * Everything persistent goes through [PreferencesRepository.updateProviderSettings],
 * one atomic edit of one JSON document, so a key added while another screen
 * reads settings can never be half-written. Key health lives in [KeyPool] (in
 * memory, process-wide) and is sampled every few seconds only while the screen
 * is collected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProvidersViewModel(
    private val preferences: PreferencesRepository,
    private val models: AiModelRepository
) : ViewModel() {

    private val busy = MutableStateFlow<Set<String>>(emptySet())
    private val refresh = MutableStateFlow(0)
    private val tick = flow {
        while (true) {
            emit(Unit)
            delay(4_000)
        }
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val uiState: StateFlow<ProvidersUiState> =
        combine(preferences.providerSettings, busy, tick, refresh) { settings, busySet, _, _ ->
            val ids = settings.builtInKeys.values.flatten().map { it.id } + settings.custom.flatMap { c -> c.keys.map { it.id } }
            ProvidersUiState(settings, ids.associateWith { KeyPool.state(it) }, busySet)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProvidersUiState())

    /** Models of the custom provider whose sheet is open. */
    private val openCustom = MutableStateFlow<String?>(null)
    val customModels: StateFlow<List<AiModel>> = openCustom
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else models.observeCustomProviderModels(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Model counts per custom provider, for the list rows. */
    val customModelCounts: StateFlow<Map<String, Int>> = models.observeAllModels()
        .map { all ->
            all.mapNotNull { m -> com.oro.app.data.ai.providers.ModelIds.providerId(m.id) }
                .groupingBy { it }.eachCount()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun openCustom(id: String?) {
        openCustom.value = id
    }

    // ---------------------------------------------------------------- keys

    fun addKey(ref: ProviderRef, key: String, label: String = "") {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        val entry = ApiKeyEntry(id = UUID.randomUUID().toString(), key = trimmed, label = label.trim())
        editKeys(ref) { list ->
            if (list.any { it.key == trimmed }) {
                _messages.tryEmit("That key is already saved")
                list
            } else list + entry
        }
    }

    fun removeKey(ref: ProviderRef, keyId: String) {
        KeyPool.reset(keyId)
        editKeys(ref) { list -> list.filterNot { it.id == keyId } }
    }

    fun setKeyEnabled(ref: ProviderRef, keyId: String, enabled: Boolean) =
        editKeys(ref) { list -> list.map { if (it.id == keyId) it.copy(enabled = enabled) else it } }

    fun moveKey(ref: ProviderRef, keyId: String, delta: Int) = editKeys(ref) { list ->
        val from = list.indexOfFirst { it.id == keyId }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) list else list.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun resetKeyHealth(keyId: String) {
        KeyPool.reset(keyId)
        refresh.update { it + 1 }
    }

    fun setRotation(ref: ProviderRef, rotation: KeyRotation) = viewModelScope.launch {
        preferences.updateProviderSettings { s ->
            when (ref) {
                is ProviderRef.BuiltIn -> s.copy(builtInRotation = s.builtInRotation + (ref.provider.name to rotation))
                is ProviderRef.Custom -> s.copy(custom = s.custom.map { if (it.id == ref.id) it.copy(rotation = rotation) else it })
            }
        }
    }

    private fun editKeys(ref: ProviderRef, transform: (List<ApiKeyEntry>) -> List<ApiKeyEntry>) {
        viewModelScope.launch {
            preferences.updateProviderSettings { s ->
                when (ref) {
                    is ProviderRef.BuiltIn -> s.copy(
                        builtInKeys = s.builtInKeys + (ref.provider.name to transform(s.builtInKeys[ref.provider.name].orEmpty()))
                    )
                    is ProviderRef.Custom -> s.copy(
                        custom = s.custom.map { if (it.id == ref.id) it.copy(keys = transform(it.keys)) else it }
                    )
                }
            }
        }
    }

    /** Check one key by listing models with it; the cheapest authenticated call every API has. */
    fun testKey(ref: ProviderRef, keyId: String) {
        val tag = "test:$keyId"
        if (tag in busy.value) return
        busy.update { it + tag }
        viewModelScope.launch {
            try {
                val state = uiState.value
                val entry = state.keysOf(ref).firstOrNull { it.entry.id == keyId }?.entry ?: return@launch
                val result = when (ref) {
                    is ProviderRef.BuiltIn ->
                        OpenAiCompatibleModelService().fetchAvailableModels(ref.provider, entry.key, ref.provider.extraHeaders)
                    is ProviderRef.Custom -> {
                        val config = state.settings.customById(ref.id) ?: return@launch
                        AiProviderManager().fetchModelsForCustom(config.copy(keys = listOf(entry.copy(enabled = true))))
                    }
                }
                result.onSuccess {
                    KeyPool.reset(keyId)
                    refresh.update { n -> n + 1 }
                    _messages.tryEmit("Key works · ${it.size} models available")
                }.onFailure {
                    _messages.tryEmit("Key check failed: ${it.message ?: "unknown error"}")
                }
            } finally {
                busy.update { it - tag }
            }
        }
    }

    // ------------------------------------------------------ custom providers

    /** Create from the Add sheet, then fetch its catalogue straight away. */
    fun addCustomProvider(
        preset: ProviderPreset?,
        name: String,
        protocol: com.oro.app.data.ai.providers.WireProtocol,
        baseUrl: String,
        apiKey: String,
        headers: Map<String, String>,
        supportsTools: Boolean,
        maxOutputTokens: Int?
    ) {
        val id = UUID.randomUUID().toString().take(8)
        val config = CustomProviderConfig(
            id = id,
            name = name.trim().ifEmpty { preset?.name ?: "Custom provider" },
            protocol = protocol,
            baseUrl = baseUrl.trim(),
            headers = headers,
            keys = apiKey.trim().takeIf { it.isNotEmpty() }
                ?.let { listOf(ApiKeyEntry(UUID.randomUUID().toString(), it)) }.orEmpty(),
            supportsTools = supportsTools,
            maxOutputTokens = maxOutputTokens,
            presetId = preset?.id
        )
        viewModelScope.launch {
            preferences.updateProviderSettings { it.copy(custom = it.custom + config) }
            refreshModels(id)
        }
    }

    fun updateCustomProvider(config: CustomProviderConfig) = viewModelScope.launch {
        preferences.updateProviderSettings { s ->
            s.copy(custom = s.custom.map { if (it.id == config.id) config else it })
        }
    }

    fun deleteCustomProvider(id: String) = viewModelScope.launch {
        val config = uiState.value.settings.customById(id)
        config?.keys?.forEach { KeyPool.reset(it.id) }
        preferences.updateProviderSettings { s -> s.copy(custom = s.custom.filterNot { it.id == id }) }
        models.deleteCustomProviderModels(id)
        _messages.tryEmit("Removed ${config?.name ?: "provider"}")
    }

    fun refreshModels(id: String) {
        val tag = "models:$id"
        if (tag in busy.value) return
        busy.update { it + tag }
        viewModelScope.launch {
            try {
                val config = preferences.providerSettings.first().customById(id) ?: return@launch
                AiProviderManager().fetchModelsForCustom(config)
                    .onSuccess { fetched ->
                        val n = models.replaceCustomProviderModels(id, fetched)
                        _messages.tryEmit(if (n == 0) "${config.name} listed no models; add one by hand" else "${config.name}: $n models")
                    }
                    .onFailure { _messages.tryEmit("Couldn't load models: ${it.message ?: "unknown error"}") }
            } finally {
                busy.update { it - tag }
            }
        }
    }

    fun addManualModel(providerId: String, upstreamId: String, name: String, contextLength: Int, tools: Boolean, thinking: Boolean) {
        if (upstreamId.isBlank()) return
        viewModelScope.launch {
            models.addCustomProviderModel(providerId, upstreamId, name, contextLength, tools, thinking)
                .onSuccess { _messages.tryEmit("Added ${name.ifBlank { upstreamId }}") }
                .onFailure { _messages.tryEmit("Couldn't add model: ${it.message}") }
        }
    }

    fun setModelEnabled(model: AiModel, enabled: Boolean) = viewModelScope.launch {
        models.setModelEnabled(model.id, enabled)
    }

    fun deleteModel(model: AiModel) = viewModelScope.launch {
        models.deleteModel(model.id)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = OroApplication.getInstance()
                return ProvidersViewModel(app.preferencesRepository, app.aiModelRepository) as T
            }
        }
    }
}
