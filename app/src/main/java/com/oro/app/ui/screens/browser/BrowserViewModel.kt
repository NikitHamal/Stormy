package com.oro.app.ui.screens.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.oro.app.OroApplication
import com.oro.app.browser.BrowserAgent
import com.oro.app.browser.BrowserEngine
import com.oro.app.browser.BrowserSettingsStore
import com.oro.app.browser.WebVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Screen state for the browser: composing tasks, supervising runs, credentials. */
class BrowserViewModel(private val app: OroApplication) : ViewModel() {

    data class UiState(
        val settings: BrowserAgent.Settings = BrowserAgent.Settings(),
        val credentials: List<WebVault.Visible> = emptyList(),
    )

    private val _ui = MutableStateFlow(
        UiState(settings = BrowserSettingsStore.load(app).also { BrowserEngine.setDesktopMode(it.desktopMode) })
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    val run: StateFlow<BrowserAgent.State> = BrowserAgent.state

    /** The engine's live URL/title/tabs, for the URL bar. */
    val engine: StateFlow<BrowserEngine.Ui> = BrowserEngine.ui

    init {
        BrowserEngine.init(app)
        WebVault.init(app)
        refreshCredentials()
    }

    fun updateSettings(transform: (BrowserAgent.Settings) -> BrowserAgent.Settings) {
        val next = transform(_ui.value.settings)
        BrowserSettingsStore.save(app, next)
        BrowserEngine.setDesktopMode(next.desktopMode)
        _ui.update { it.copy(settings = next) }
    }

    fun stop() = BrowserAgent.stop()
    fun togglePause() = BrowserAgent.togglePause()
    fun answer(text: String) = BrowserAgent.answer(text)
    fun approve(allow: Boolean) = BrowserAgent.approve(allow)
    fun provideSecret(secret: String) = BrowserAgent.provideSecret(secret)
    fun secretEnteredDirectly() = BrowserAgent.secretEnteredDirectly()
    fun declineSecret() = BrowserAgent.declineSecret()

    /** Dismisses a finished run; the screen returns to its idle state. */
    fun newTask() {
        BrowserAgent.clear()
    }

    // ------------------------------------------------------- credentials

    fun refreshCredentials() = _ui.update { it.copy(credentials = WebVault.list()) }

    fun saveCredential(label: String, domain: String, username: String, password: String) {
        if (label.isBlank() || domain.isBlank() || password.isEmpty()) return
        WebVault.save(label, domain, username, password)
        refreshCredentials()
    }

    fun deleteCredential(id: String) {
        WebVault.delete(id)
        refreshCredentials()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BrowserViewModel(OroApplication.getInstance()) as T
        }
    }
}
