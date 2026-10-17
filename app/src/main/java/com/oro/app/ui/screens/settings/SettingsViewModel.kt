package com.oro.app.ui.screens.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oro.app.data.ai.AiProvider
import com.oro.app.OroApplication
import com.oro.app.data.ai.AiModel
import com.oro.app.data.repository.AiModelRepository
import com.oro.app.data.repository.PreferencesRepository
import com.oro.app.data.repository.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Marked @Immutable because it holds List fields. Compose cannot prove a
 * kotlin.collections.List is immutable -- the interface permits a mutable
 * implementation -- so without this the whole state object counts as unstable
 * and every composable reading it recomposes on each emission, even when the
 * value it reads is unchanged. These lists are only ever replaced wholesale,
 * never mutated, so the annotation is true.
 */
@Immutable
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = false,
    val fontSize: Float = 14f,
    val lineNumbers: Boolean = true,
    val wordWrap: Boolean = true,
    val autoSave: Boolean = true,
    val debugLogsEnabled: Boolean = false,
    val aiModel: String = "",
    val availableModels: List<AiModel> = emptyList(),
    /**
     * Key per provider. A map rather than one field each, so the settings
     * screen can render the provider list from AiProvider.entries and adding a
     * provider needs no change to this state class or the combine() below.
     */
    val apiKeys: Map<AiProvider, String> = emptyMap(),
    val customBaseUrl: String = ""
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val aiModelRepository: AiModelRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.themeMode,
        preferencesRepository.dynamicColors,
        preferencesRepository.fontSize,
        preferencesRepository.lineNumbers,
        preferencesRepository.wordWrap,
        preferencesRepository.autoSave,
        preferencesRepository.debugLogsEnabled,
        preferencesRepository.aiModel,
        preferencesRepository.apiKeys,
        preferencesRepository.customBaseUrl,
        aiModelRepository.observeEnabledModels()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val keys = values[8] as Map<AiProvider, String>
        @Suppress("UNCHECKED_CAST")
        val models = values[10] as List<AiModel>
        SettingsUiState(
            themeMode = values[0] as ThemeMode,
            dynamicColors = values[1] as Boolean,
            fontSize = values[2] as Float,
            lineNumbers = values[3] as Boolean,
            wordWrap = values[4] as Boolean,
            autoSave = values[5] as Boolean,
            debugLogsEnabled = values[6] as Boolean,
            aiModel = values[7] as String,
            apiKeys = keys,
            customBaseUrl = values[9] as String,
            availableModels = models
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setDebugLogsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDebugLogsEnabled(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDynamicColors(enabled)
        }
    }

    fun setFontSize(size: Float) {
        viewModelScope.launch {
            preferencesRepository.setFontSize(size)
        }
    }

    fun setLineNumbers(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setLineNumbers(enabled)
        }
    }

    fun setWordWrap(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setWordWrap(enabled)
        }
    }

    fun setAutoSave(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoSave(enabled)
        }
    }

    fun setAiModel(model: String) {
        viewModelScope.launch {
            preferencesRepository.setAiModel(model)
        }
    }

    /** One setter for every provider, keyed off the enum. */
    fun setApiKey(provider: AiProvider, key: String) {
        viewModelScope.launch {
            preferencesRepository.setApiKeyForProvider(provider, key)
        }
    }

    fun setCustomBaseUrl(url: String) {
        viewModelScope.launch {
            preferencesRepository.setCustomBaseUrl(url)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val application = OroApplication.getInstance()
                return SettingsViewModel(
                    preferencesRepository = application.preferencesRepository,
                    aiModelRepository = application.aiModelRepository
                ) as T
            }
        }
    }
}
