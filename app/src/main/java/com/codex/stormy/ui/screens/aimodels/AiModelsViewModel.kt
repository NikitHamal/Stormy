package com.codex.stormy.ui.screens.aimodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codex.stormy.CodeXApplication
import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.AiProvider
import com.codex.stormy.data.ai.DeepInfraModels
import com.codex.stormy.data.repository.AiModelRepository
import com.codex.stormy.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the AI Models management screen
 */
data class AiModelsUiState(
    val allModels: List<AiModel> = emptyList(),
    val filteredModels: List<AiModel> = emptyList(),
    val currentModel: AiModel? = null,
    val defaultModelId: String = "",
    val selectedProvider: AiProvider? = null,
    val searchQuery: String = "",
    val showStreamingOnly: Boolean = false,
    val showToolCallsOnly: Boolean = false,
    val showThinkingOnly: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val totalCount: Int = 0,
    val enabledCount: Int = 0
)

/**
 * ViewModel for the AI Models management screen
 * Handles model management for DeepInfra (free, no API key required)
 */
class AiModelsViewModel(
    private val modelRepository: AiModelRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _selectedProvider = MutableStateFlow<AiProvider?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _showStreamingOnly = MutableStateFlow(false)
    private val _showToolCallsOnly = MutableStateFlow(false)
    private val _showThinkingOnly = MutableStateFlow(false)
    private val _currentModel = MutableStateFlow<AiModel?>(null)
    private val _defaultModelId = MutableStateFlow("")

    val uiState: StateFlow<AiModelsUiState> = combine(
        modelRepository.observeEnabledModels(),
        _isLoading,
        _isRefreshing,
        _error,
        _selectedProvider,
        _searchQuery,
        _showStreamingOnly,
        _showToolCallsOnly,
        combine(_showThinkingOnly, _currentModel, _defaultModelId) { thinking, current, default ->
            Triple(thinking, current, default)
        }
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val models = values[0] as List<AiModel>
        val isLoading = values[1] as Boolean
        val isRefreshing = values[2] as Boolean
        val error = values[3] as String?
        val selectedProvider = values[4] as AiProvider?
        val searchQuery = values[5] as String
        val showStreamingOnly = values[6] as Boolean
        val showToolCallsOnly = values[7] as Boolean
        val (showThinkingOnly, currentModel, defaultModelId) = values[8] as Triple<Boolean, AiModel?, String>

        // Apply filters
        val filteredModels = models.filter { model ->
            // Provider filter
            val providerMatch = selectedProvider == null || model.provider == selectedProvider

            // Search filter
            val searchMatch = searchQuery.isBlank() ||
                    model.name.contains(searchQuery, ignoreCase = true) ||
                    model.id.contains(searchQuery, ignoreCase = true)

            // Feature filters
            val streamingMatch = !showStreamingOnly || model.supportsStreaming
            val toolCallsMatch = !showToolCallsOnly || model.supportsToolCalls
            val thinkingMatch = !showThinkingOnly || model.isThinkingModel

            providerMatch && searchMatch && streamingMatch && toolCallsMatch && thinkingMatch
        }

        AiModelsUiState(
            allModels = models,
            filteredModels = filteredModels,
            currentModel = currentModel,
            defaultModelId = defaultModelId,
            selectedProvider = selectedProvider,
            searchQuery = searchQuery,
            showStreamingOnly = showStreamingOnly,
            showToolCallsOnly = showToolCallsOnly,
            showThinkingOnly = showThinkingOnly,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            error = error,
            totalCount = models.size,
            enabledCount = models.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AiModelsUiState(isLoading = true)
    )

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Initialize models if database is empty
                modelRepository.initializeModelsIfEmpty()

                // Load default model ID
                _defaultModelId.value = preferencesRepository.defaultModelId.first()

                // Load current model preference
                val currentModelId = preferencesRepository.aiModel.first()
                val model = modelRepository.getModelById(currentModelId)
                    ?: modelRepository.getEnabledModels().firstOrNull()
                    ?: DeepInfraModels.defaultModel

                _currentModel.value = model
            } catch (e: Exception) {
                _error.value = "Failed to load models: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refresh models from DeepInfra
     * DeepInfra is free and doesn't require an API key
     */
    fun refreshModels() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null

            modelRepository.refreshModelsFromDeepInfra().onFailure { error ->
                _error.value = "Failed to refresh: ${error.message}"
            }

            _isRefreshing.value = false
        }
    }

    /**
     * Select a model as the active model
     */
    fun selectModel(model: AiModel) {
        viewModelScope.launch {
            preferencesRepository.setAiModel(model.id)
            modelRepository.recordModelUsage(model.id)
            _currentModel.value = model
        }
    }

    /**
     * Set a model as the global default model
     * This model will be used for new projects or when no project-specific model is set
     */
    fun setAsDefaultModel(model: AiModel) {
        viewModelScope.launch {
            preferencesRepository.setDefaultModelId(model.id)
            _defaultModelId.value = model.id
        }
    }

    /**
     * Clear the global default model
     */
    fun clearDefaultModel() {
        viewModelScope.launch {
            preferencesRepository.clearDefaultModel()
            _defaultModelId.value = ""
        }
    }

    /**
     * Select a provider to filter models by
     * Pass null to show all providers
     */
    fun selectProvider(provider: AiProvider?) {
        _selectedProvider.value = provider
    }

    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Toggle streaming filter
     */
    fun toggleStreamingFilter() {
        _showStreamingOnly.value = !_showStreamingOnly.value
    }

    /**
     * Toggle tool calls filter
     */
    fun toggleToolCallsFilter() {
        _showToolCallsOnly.value = !_showToolCallsOnly.value
    }

    /**
     * Toggle thinking/reasoning filter
     */
    fun toggleThinkingFilter() {
        _showThinkingOnly.value = !_showThinkingOnly.value
    }

    /**
     * Clear all filters
     */
    fun clearFilters() {
        _selectedProvider.value = null
        _searchQuery.value = ""
        _showStreamingOnly.value = false
        _showToolCallsOnly.value = false
        _showThinkingOnly.value = false
    }

    /**
     * Clear the error message
     */
    fun clearError() {
        _error.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val application = CodeXApplication.getInstance()
                return AiModelsViewModel(
                    modelRepository = application.aiModelRepository,
                    preferencesRepository = application.preferencesRepository
                ) as T
            }
        }
    }
}
