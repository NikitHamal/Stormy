package com.oro.app.ui.screens.searchproviders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oro.app.OroApplication
import com.oro.app.data.ai.search.CustomKind
import com.oro.app.data.ai.search.CustomSearchProvider
import com.oro.app.data.ai.search.WebSearch
import com.oro.app.data.ai.search.WebSearchConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings for the web-search chain: which keyless engines are on, which
 * free-tier APIs have a saved key, and the endpoints the user wired
 * themselves. Everything lands in DataStore through
 * [com.oro.app.data.repository.PreferencesRepository]; the running agents
 * pick it up from [WebSearch.configure].
 */
class WebSearchSettingsViewModel(private val app: OroApplication) : ViewModel() {

    data class UiState(
        val config: WebSearchConfig = WebSearchConfig(),
        val views: List<WebSearch.ProviderView> = emptyList(),
        /** The provider whose editor sheet is open (preset or custom id). */
        val editing: String? = null,
        val testResult: String? = null,
        val testing: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            app.preferencesRepository.webSearchConfig.collect { cfg ->
                _ui.update { it.copy(config = cfg, views = WebSearch.views()) }
            }
        }
    }

    private fun save(transform: (WebSearchConfig) -> WebSearchConfig) {
        viewModelScope.launch { app.preferencesRepository.updateWebSearchConfig(transform) }
    }

    fun toggleBuiltIn(id: String, enabled: Boolean) = save { cfg ->
        cfg.copy(
            disabledBuiltIns = if (enabled) cfg.disabledBuiltIns - id else (cfg.disabledBuiltIns + id).distinct()
        )
    }

    fun togglePreset(id: String, enabled: Boolean) = save { cfg ->
        cfg.copy(
            disabledPresets = if (enabled) cfg.disabledPresets - id else (cfg.disabledPresets + id).distinct()
        )
    }

    fun setPresetKey(id: String, key: String) = save { cfg ->
        val keys = cfg.presetKeys.toMutableMap()
        if (key.isBlank()) keys.remove(id) else keys[id] = key.trim()
        cfg.copy(presetKeys = keys)
    }

    fun saveCustom(provider: CustomSearchProvider) = save { cfg ->
        val others = cfg.custom.filterNot { it.id == provider.id }
        cfg.copy(custom = others + provider)
    }

    fun deleteCustom(id: String) = save { cfg -> cfg.copy(custom = cfg.custom.filterNot { it.id == id }) }

    fun toggleCustom(id: String, enabled: Boolean) = save { cfg ->
        cfg.copy(custom = cfg.custom.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun edit(id: String?) = _ui.update { it.copy(editing = id, testResult = null) }

    fun dismissTest() = _ui.update { it.copy(testResult = null, testing = null) }

    /** Runs one provider against a sample query and reports what came back. */
    fun test(id: String) {
        viewModelScope.launch {
            _ui.update { it.copy(testing = id, testResult = null) }
            val hits = WebSearch.testProvider(id)
            _ui.update {
                it.copy(
                    testing = null,
                    testResult = if (hits.isEmpty()) "No results -- the endpoint refused, blocked or returned nothing readable."
                    else hits.size.toString() + " result(s):\n" + hits.take(3).joinToString("\n") { h -> "• ${h.title.take(60)}\n  ${h.url.take(70)}" },
                )
            }
        }
    }

    /** A starter custom provider for the add sheet, so nothing opens blank. */
    fun newCustom(): CustomSearchProvider = CustomSearchProvider(
        id = "custom-" + java.util.UUID.randomUUID().toString().take(8),
        label = "",
        kind = CustomKind.JSON_SNIFF,
        urlTemplate = "https://example.com/search?q={query}",
    )

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WebSearchSettingsViewModel(OroApplication.getInstance()) as T
        }
    }
}
