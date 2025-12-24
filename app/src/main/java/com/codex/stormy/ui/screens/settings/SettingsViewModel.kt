package com.codex.stormy.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codex.stormy.CodeXApplication
import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.DeepInfraModels
import com.codex.stormy.data.repository.PreferencesRepository
import com.codex.stormy.data.repository.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = false,
    val fontSize: Float = 14f,
    val lineNumbers: Boolean = true,
    val wordWrap: Boolean = true,
    val autoSave: Boolean = true,
    val codeAutocompletion: Boolean = true,
    val apiKey: String = "",
    val deepInfraApiKey: String = "",
    val openRouterApiKey: String = "",
    val geminiApiKey: String = "",
    val aiModel: String = DeepInfraModels.defaultModel.id,
    val availableModels: List<AiModel> = DeepInfraModels.allModels
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.themeMode,
        preferencesRepository.dynamicColors,
        preferencesRepository.fontSize,
        preferencesRepository.lineNumbers,
        preferencesRepository.wordWrap,
        preferencesRepository.autoSave,
        preferencesRepository.codeAutocompletion,
        preferencesRepository.deepInfraApiKey,
        preferencesRepository.openRouterApiKey,
        preferencesRepository.geminiApiKey
    ) { values ->
        SettingsUiState(
            themeMode = values[0] as ThemeMode,
            dynamicColors = values[1] as Boolean,
            fontSize = values[2] as Float,
            lineNumbers = values[3] as Boolean,
            wordWrap = values[4] as Boolean,
            autoSave = values[5] as Boolean,
            codeAutocompletion = values[6] as Boolean,
            deepInfraApiKey = values[7] as String,
            openRouterApiKey = values[8] as String,
            geminiApiKey = values[9] as String
        )
    }.combine(preferencesRepository.aiModel) { state, aiModel ->
        state.copy(aiModel = aiModel)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

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

    fun setCodeAutocompletion(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCodeAutocompletion(enabled)
        }
    }

    fun setDeepInfraApiKey(key: String) {
        viewModelScope.launch {
            preferencesRepository.setDeepInfraApiKey(key)
        }
    }

    fun setOpenRouterApiKey(key: String) {
        viewModelScope.launch {
            preferencesRepository.setOpenRouterApiKey(key)
        }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch {
            preferencesRepository.setGeminiApiKey(key)
        }
    }

    fun setAiModel(model: String) {
        viewModelScope.launch {
            preferencesRepository.setAiModel(model)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val application = CodeXApplication.getInstance()
                return SettingsViewModel(
                    preferencesRepository = application.preferencesRepository
                ) as T
            }
        }
    }
}
