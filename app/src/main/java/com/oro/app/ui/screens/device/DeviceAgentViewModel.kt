package com.oro.app.ui.screens.device

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oro.app.OroApplication
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.SeedModels
import com.oro.app.device.DeviceAgent
import com.oro.app.device.OroAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Screen state for composing and supervising device tasks. */
class DeviceAgentViewModel(private val app: OroApplication) : ViewModel() {

    data class UiState(
        val serviceEnabled: Boolean = false,
        val task: String = "",
        val models: List<AiModel> = emptyList(),
        val model: AiModel? = null,
        val settings: DeviceAgent.Settings = DeviceAgent.Settings(),
        val history: List<String> = emptyList(),
    )

    private val prefs = app.getSharedPreferences("device_agent", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(UiState(settings = loadSettings(), history = loadHistory()))
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    val run: StateFlow<DeviceAgent.State> = DeviceAgent.state

    init {
        refreshService()
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

    fun refreshService() {
        DeviceAgent.refreshServiceState(app)
        _ui.update { it.copy(serviceEnabled = OroAccessibilityService.isEnabled(app) && OroAccessibilityService.instance.value != null) }
    }

    fun setTask(text: String) = _ui.update { it.copy(task = text) }

    fun selectModel(model: AiModel) {
        prefs.edit().putString("model", model.id).apply()
        _ui.update { it.copy(model = model) }
    }

    fun updateSettings(transform: (DeviceAgent.Settings) -> DeviceAgent.Settings) {
        val next = transform(_ui.value.settings)
        prefs.edit()
            .putBoolean("confirm", next.confirmSensitive)
            .putBoolean("touches", next.showTouches)
            .putBoolean("return", next.returnToOro)
            .putInt("max", next.maxActions)
            .apply()
        _ui.update { it.copy(settings = next) }
    }

    fun start(task: String = _ui.value.task) {
        val model = _ui.value.model ?: return
        if (task.isBlank()) return
        rememberTask(task)
        DeviceAgent.start(app, task, model, app.aiRepository, _ui.value.settings)
    }

    fun stop() = DeviceAgent.stop()
    fun togglePause() = DeviceAgent.togglePause()
    fun answer(text: String) = DeviceAgent.answer(text)
    fun approve(allow: Boolean) = DeviceAgent.approve(allow)
    fun newTask() { DeviceAgent.clear(); _ui.update { it.copy(task = "") } }

    private fun rememberTask(task: String) {
        val list = (listOf(task.trim()) + _ui.value.history.filter { it != task.trim() }).take(8)
        prefs.edit().putString("history", list.joinToString("\u001F")).apply()
        _ui.update { it.copy(history = list) }
    }

    private fun loadHistory(): List<String> =
        prefs.getString("history", null)?.split("\u001F")?.filter { it.isNotBlank() } ?: emptyList()

    private fun loadSettings() = DeviceAgent.Settings(
        confirmSensitive = prefs.getBoolean("confirm", true),
        showTouches = prefs.getBoolean("touches", true),
        returnToOro = prefs.getBoolean("return", true),
        maxActions = prefs.getInt("max", 60),
    )

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DeviceAgentViewModel(OroApplication.getInstance()) as T
        }
    }
}
