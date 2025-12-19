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
 * Handles dynamic model fetching from DeepInfra API and model selection
 */
class AiModelsViewModel(
    private val modelRepository: AiModelRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedProvider = MutableStateFlow<AiProvider?>(null)
    private val _showStreamingOnly = MutableStateFlow(false)
    private val _showToolCallsOnly = MutableStateFlow(false)
    private val _showThinkingOnly = MutableStateFlow(false)
    private val _currentModel = MutableStateFlow<AiModel?>(null)

    val uiState: StateFlow<AiModelsUiState> = combine(
        modelRepository.observeEnabledModels(),
        _isLoading,
        _isRefreshing,
        _error,
        _searchQuery,
        _selectedProvider,
        _showStreamingOnly,
        _showToolCallsOnly,
        _showThinkingOnly,
        _currentModel
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val models = values[0] as List<AiModel>
        val isLoading = values[1] as Boolean
        val isRefreshing = values[2] as Boolean
        val error = values[3] as String?
        val searchQuery = values[4] as String
        val selectedProvider = values[5] as AiProvider?
        val showStreamingOnly = values[6] as Boolean
        val showToolCallsOnly = values[7] as Boolean
        val showThinkingOnly = values[8] as Boolean
        val currentModel = values[9] as AiModel?

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
            selectedProvider = selectedProvider,
            searchQuery = searchQuery,
            showStreamingOnly = showStreamingOnly,
            showToolCallsOnly = showToolCallsOnly,
            showThinkingOnly = showThinkingOnly,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            error = error,
            totalCount = models.size,
            enabledCount = models.size // All shown models are enabled
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

                // Load default model preference (used for new projects)
                val defaultModelId = preferencesRepository.defaultAiModel.first()
                val model = modelRepository.getModelById(defaultModelId)
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
     * Refresh models from all providers (DeepInfra, OpenRouter, Gemini)
     */
    fun refreshModels() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null

            // Refresh all providers concurrently
            // Note: Gemini requires API key, passing null for now
            val result = modelRepository.refreshAllModels(geminiApiKey = null)
            result.onFailure { error ->
                _error.value = "Failed to refresh: ${error.message}"
            }.onSuccess { count ->
                // Silently succeeded
            }

            _isRefreshing.value = false
        }
    }

    /**
     * Refresh models from a specific provider
     */
    fun refreshProvider(provider: AiProvider) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null

            val result = modelRepository.refreshModelsFromProvider(provider)
            result.onFailure { error ->
                _error.value = "Failed to refresh ${provider.displayName}: ${error.message}"
            }

            _isRefreshing.value = false
        }
    }

    /**
     * Select a model as the global default model
     * This model will be used for new projects and projects without a specific preference
     */
    fun selectModel(model: AiModel) {
        viewModelScope.launch {
            // Update both the session model and the default model
            preferencesRepository.setAiModel(model.id)
            preferencesRepository.setDefaultAiModel(model.id)
            modelRepository.recordModelUsage(model.id)
            _currentModel.value = model
        }
    }

    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Select a provider filter
     */
    fun selectProvider(provider: AiProvider?) {
        _selectedProvider.value = provider
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
        _searchQuery.value = ""
        _selectedProvider.value = null
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
