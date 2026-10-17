package com.oro.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oro.app.OroApplication
import com.oro.app.data.repository.PreferencesRepository
import com.oro.app.data.repository.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** Applied immediately: the onboarding itself re-themes as the user picks. */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    /**
     * Returns the write's job so the caller can wait for it before navigating
     * away: leaving the screen clears this ViewModel, and with it any write
     * still in flight -- which would show the onboarding again next launch.
     */
    fun completeOnboarding(): Job =
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
        }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val application = OroApplication.getInstance()
                return OnboardingViewModel(
                    preferencesRepository = application.preferencesRepository
                ) as T
            }
        }
    }
}
