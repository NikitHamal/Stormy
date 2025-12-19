package com.codex.stormy.ui.screens.aimodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codex.stormy.CodeXApplication
import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.AiProvider
import com.codex.stormy.data.ai.DeepInfraModels
import com.codex.stormy.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * UI state for the AI Models screen
 */
data class AiModelsUiState(
    val allModels: List<AiModel> = emptyList(),
    val filteredModels: List<AiModel> = emptyList(),
    val currentModel: AiModel? = null,
    val selectedProvider: AiProvider? = null,
    val showStreamingOnly: Boolean = false,
    val showToolCallsOnly: Boolean = false,
    val showThinkingOnly: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Response models for DeepInfra API
 */
@Serializable
private data class DeepInfraModelsResponse(
    val data: List<DeepInfraModelData>? = null
)

@Serializable
private data class DeepInfraModelData(
    val id: String,
    val owned_by: String? = null,
    val object_type: String? = null,
    val created: Long? = null,
    val description: String? = null,
    val context_length: Int? = null,
    val max_tokens: Int? = null,
    val supports_streaming: Boolean? = null,
    val supports_tool_calls: Boolean? = null
)

/**
 * ViewModel for the AI Models management screen
 * Handles dynamic model fetching from DeepInfra API and model selection
 */
class AiModelsViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiModelsUiState())
    val uiState: StateFlow<AiModelsUiState> = _uiState.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // Load current model preference
            val currentModelId = preferencesRepository.aiModel.first()

            // Start with predefined models
            val initialModels = DeepInfraModels.allModels
            val currentModel = initialModels.find { it.id == currentModelId }
                ?: DeepInfraModels.defaultModel

            _uiState.update { state ->
                state.copy(
                    allModels = initialModels,
                    filteredModels = initialModels,
                    currentModel = currentModel
                )
            }

            // Fetch dynamic models from API
            refreshModels()
        }
    }

    /**
     * Refresh models from the DeepInfra API
     */
    fun refreshModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val fetchedModels = fetchModelsFromApi()
                val mergedModels = mergeModels(DeepInfraModels.allModels, fetchedModels)

                // Update current model reference if needed
                val currentModelId = preferencesRepository.aiModel.first()
                val currentModel = mergedModels.find { it.id == currentModelId }
                    ?: mergedModels.firstOrNull()
                    ?: DeepInfraModels.defaultModel

                _uiState.update { state ->
                    state.copy(
                        allModels = mergedModels,
                        isLoading = false
                    )
                }

                // Re-apply filters
                applyFilters()

            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = "Failed to fetch models: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Fetch models from DeepInfra API
     */
    private suspend fun fetchModelsFromApi(): List<AiModel> {
        return try {
            val request = Request.Builder()
                .url("https://api.deepinfra.com/v1/openai/models")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return emptyList()
            }

            val responseBody = response.body?.string() ?: return emptyList()
            val modelsResponse = json.decodeFromString<DeepInfraModelsResponse>(responseBody)

            modelsResponse.data?.mapNotNull { data ->
                // Filter for chat models only
                if (!data.id.contains("chat", ignoreCase = true) &&
                    !data.id.contains("instruct", ignoreCase = true) &&
                    !data.id.contains("llama", ignoreCase = true) &&
                    !data.id.contains("qwen", ignoreCase = true) &&
                    !data.id.contains("mistral", ignoreCase = true) &&
                    !data.id.contains("deepseek", ignoreCase = true)
                ) {
                    return@mapNotNull null
                }

                AiModel(
                    id = data.id,
                    name = formatModelName(data.id),
                    provider = AiProvider.DEEPINFRA,
                    contextLength = data.context_length ?: 4096,
                    supportsStreaming = data.supports_streaming ?: true,
                    supportsToolCalls = data.supports_tool_calls ?: false,
                    isThinkingModel = data.id.contains("R1", ignoreCase = true) ||
                            data.id.contains("reasoning", ignoreCase = true)
                )
            } ?: emptyList()

        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Merge predefined models with fetched models, preferring predefined metadata
     */
    private fun mergeModels(
        predefined: List<AiModel>,
        fetched: List<AiModel>
    ): List<AiModel> {
        val predefinedIds = predefined.map { it.id }.toSet()
        val newModels = fetched.filter { it.id !in predefinedIds }
        return (predefined + newModels).sortedBy { it.name }
    }

    /**
     * Select a provider filter
     */
    fun selectProvider(provider: AiProvider?) {
        _uiState.update { it.copy(selectedProvider = provider) }
        applyFilters()
    }

    /**
     * Toggle streaming filter
     */
    fun toggleStreamingFilter() {
        _uiState.update { it.copy(showStreamingOnly = !it.showStreamingOnly) }
        applyFilters()
    }

    /**
     * Toggle tool calls filter
     */
    fun toggleToolCallsFilter() {
        _uiState.update { it.copy(showToolCallsOnly = !it.showToolCallsOnly) }
        applyFilters()
    }

    /**
     * Toggle thinking/reasoning filter
     */
    fun toggleThinkingFilter() {
        _uiState.update { it.copy(showThinkingOnly = !it.showThinkingOnly) }
        applyFilters()
    }

    /**
     * Apply all active filters to the models list
     */
    private fun applyFilters() {
        _uiState.update { state ->
            var filtered = state.allModels

            // Provider filter
            state.selectedProvider?.let { provider ->
                filtered = filtered.filter { it.provider == provider }
            }

            // Feature filters
            if (state.showStreamingOnly) {
                filtered = filtered.filter { it.supportsStreaming }
            }
            if (state.showToolCallsOnly) {
                filtered = filtered.filter { it.supportsToolCalls }
            }
            if (state.showThinkingOnly) {
                filtered = filtered.filter { it.isThinkingModel }
            }

            state.copy(filteredModels = filtered)
        }
    }

    /**
     * Select a model as the active model
     */
    fun selectModel(model: AiModel) {
        viewModelScope.launch {
            preferencesRepository.setAiModel(model.id)
            _uiState.update { it.copy(currentModel = model) }
        }
    }

    /**
     * Clear the error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Format model ID into a readable name
     */
    private fun formatModelName(modelId: String): String {
        // Extract the model name from the ID
        val parts = modelId.split("/")
        val name = parts.lastOrNull() ?: modelId

        // Clean up common suffixes and format
        return name
            .replace("-Instruct", " Instruct")
            .replace("-instruct", " Instruct")
            .replace("-Chat", " Chat")
            .replace("-chat", " Chat")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { word ->
                if (word.length <= 2) word.uppercase()
                else word.replaceFirstChar { it.uppercase() }
            }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val application = CodeXApplication.getInstance()
                return AiModelsViewModel(
                    preferencesRepository = application.preferencesRepository
                ) as T
            }
        }
    }
}
