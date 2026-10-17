package com.codex.stormy.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codex.stormy.CodeXApplication
import com.codex.stormy.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isLoading: Boolean = false,
    val saveFailed: Boolean = false
)

class OnboardingViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun completeOnboarding(onComplete: () -> Unit) {
        if (_uiState.value.isLoading) return
        _uiState.value = OnboardingUiState(isLoading = true)
        viewModelScope.launch {
            try {
                preferencesRepository.setOnboardingCompleted(true)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = OnboardingUiState(saveFailed = true)
                return@launch
            }
            onComplete()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val application = CodeXApplication.getInstance()
                return OnboardingViewModel(
                    preferencesRepository = application.preferencesRepository
                ) as T
            }
        }
    }
}
