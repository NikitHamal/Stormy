package com.codex.stormy.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codex.stormy.CodeXApplication
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
    val apiKey: String = "",
    val aiModel: String = "gpt-4"
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
        preferencesRepository.apiKey,
        preferencesRepository.aiModel
    ) { values ->
        SettingsUiState(
            themeMode = values[0] as ThemeMode,
            dynamicColors = values[1] as Boolean,
            fontSize = values[2] as Float,
            lineNumbers = values[3] as Boolean,
            wordWrap = values[4] as Boolean,
            autoSave = values[5] as Boolean,
            apiKey = values[6] as String,
            aiModel = values[7] as String
        )
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

    fun setApiKey(key: String) {
        viewModelScope.launch {
            preferencesRepository.setApiKey(key)
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
