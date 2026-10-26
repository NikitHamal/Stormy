package com.oro.app.ui.screens.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oro.app.OroApplication
import com.oro.app.browser.BrowserAgent
import com.oro.app.browser.BrowserEngine
import com.oro.app.browser.BrowserSettingsStore
import com.oro.app.browser.WebVault
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.SeedModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Screen state for the browser: composing tasks, supervising runs, credentials. */
class BrowserViewModel(private val app: OroApplication) : ViewModel() {

    data class UiState(
        val task: String = "",
        val models: List<AiModel> = emptyList(),
        val model: AiModel? = null,
        val settings: BrowserAgent.Settings = BrowserAgent.Settings(),
        val history: List<String> = emptyList(),
        val credentials: List<WebVault.Visible> = emptyList(),
    )

    private val prefs = app.getSharedPreferences("browser_agent", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(
        UiState(
            settings = BrowserSettingsStore.load(app).also { BrowserEngine.setDesktopMode(it.desktopMode) },
            history = loadHistory(),
        )
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    val run: StateFlow<BrowserAgent.State> = BrowserAgent.state

    /** The engine's live URL/title/tabs, for the URL bar. */
    val engine: StateFlow<BrowserEngine.Ui> = BrowserEngine.ui

    init {
        BrowserEngine.init(app)
        WebVault.init(app)
        refreshCredentials()
        viewModelScope.launch {
            app.aiModelRepository.observeEnabledModels().collect { models ->
                _ui.update { s ->
                    val keep = s.model?.let { m -> models.firstOrNull { it.id == m.id } }
                    s.copy(models = models, model = keep ?: s.model ?: pickDefault(models))
                }
            }
        }
    }

    private suspend fun pickDefault(models: List<AiModel>): AiModel? {
        val saved = prefs.getString("model", null)
        models.firstOrNull { it.id == saved }?.let { return it }
        val globalDefault = app.preferencesRepository.defaultModelId.first()
        return models.firstOrNull { it.id == globalDefault }
            ?: models.firstOrNull { it.id == SeedModels.default.id }
            ?: models.firstOrNull()
    }

    fun setTask(text: String) = _ui.update { it.copy(task = text) }

    fun selectModel(model: AiModel) {
        prefs.edit().putString("model", model.id).apply()
        _ui.update { it.copy(model = model) }
    }

    fun updateSettings(transform: (BrowserAgent.Settings) -> BrowserAgent.Settings) {
        val next = transform(_ui.value.settings)
        BrowserSettingsStore.save(app, next)
        BrowserEngine.setDesktopMode(next.desktopMode)
        _ui.update { it.copy(settings = next) }
    }

    fun start(task: String = _ui.value.task) {
        val model = _ui.value.model ?: return
        if (task.isBlank()) return
        rememberTask(task)
        BrowserAgent.start(app, task, model, app.aiRepository, _ui.value.settings)
    }

    fun startAt(url: String) {
        val model = _ui.value.model ?: return
        if (url.isBlank()) return
        BrowserAgent.start(app, "Open $url", model, app.aiRepository, _ui.value.settings, startUrl = url)
    }

    fun openUrl(url: String) = BrowserEngine.openByUser(url)

    fun stop() = BrowserAgent.stop()
    fun togglePause() = BrowserAgent.togglePause()
    fun answer(text: String) = BrowserAgent.answer(text)
    fun approve(allow: Boolean) = BrowserAgent.approve(allow)
    fun provideSecret(secret: String) = BrowserAgent.provideSecret(secret)
    fun secretEnteredDirectly() = BrowserAgent.secretEnteredDirectly()
    fun declineSecret() = BrowserAgent.declineSecret()

    fun newTask() {
        BrowserAgent.clear()
        _ui.update { it.copy(task = "") }
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

    private fun rememberTask(task: String) {
        val list = (listOf(task.trim()) + _ui.value.history.filter { it != task.trim() }).take(8)
        prefs.edit().putString("history", list.joinToString("\u001F")).apply()
        _ui.update { it.copy(history = list) }
    }

    private fun loadHistory(): List<String> =
        prefs.getString("history", null)?.split("\u001F")?.filter { it.isNotBlank() } ?: emptyList()

    /** Send from the composer: start, and clear the field once the run really began. */
    fun startFromComposer() {
        start()
        if (BrowserAgent.state.value.running) _ui.update { it.copy(task = "") }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BrowserViewModel(OroApplication.getInstance()) as T
        }
    }
}
