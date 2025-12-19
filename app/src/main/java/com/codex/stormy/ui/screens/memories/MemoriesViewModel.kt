package com.codex.stormy.ui.screens.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codex.stormy.CodeXApplication
import com.codex.stormy.data.ai.tools.MemoryStorage
import com.codex.stormy.data.repository.ProjectRepository
import com.codex.stormy.domain.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Memories screen
 */
data class MemoriesUiState(
    val projectMemories: List<ProjectMemoryState> = emptyList(),
    val selectedProject: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val editingMemory: EditingMemoryState? = null
)

/**
 * State representing a project's memories
 */
data class ProjectMemoryState(
    val projectId: String,
    val projectName: String,
    val memories: List<MemoryState> = emptyList()
)

/**
 * State representing a single memory entry
 */
data class MemoryState(
    val key: String,
    val value: String,
    val timestamp: Long
)

/**
 * State for editing a memory
 */
data class EditingMemoryState(
    val projectId: String,
    val key: String,
    val originalValue: String,
    val editedValue: String
)

/**
 * ViewModel for the Memories management screen
 * Handles loading, creating, editing, and deleting AI memories
 */
class MemoriesViewModel(
    private val memoryStorage: MemoryStorage,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoriesUiState())
    val uiState: StateFlow<MemoriesUiState> = _uiState.asStateFlow()

    init {
        loadMemories()
    }

    /**
     * Load all memories from all projects
     */
    private fun loadMemories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Get all projects
                val projects = projectRepository.getAllProjects().first()

                // Load memories for each project
                val projectMemories = projects.map { project: Project ->
                    loadProjectMemories(project)
                }.filter { it.memories.isNotEmpty() || it.projectId == _uiState.value.selectedProject }
                 .sortedByDescending { projectMemory: ProjectMemoryState ->
                     projectMemory.memories.maxOfOrNull { it.timestamp } ?: 0L
                 }

                _uiState.update { state ->
                    state.copy(
                        projectMemories = projectMemories,
                        isLoading = false,
                        error = null
                    )
                }

            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = "Failed to load memories: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Load memories for a specific project
     */
    private suspend fun loadProjectMemories(project: Project): ProjectMemoryState {
        val memoriesMap = memoryStorage.list(project.id)

        // Convert to MemoryState list
        val memories = memoriesMap.map { (key, value) ->
            // Try to get timestamp from storage (stored internally)
            MemoryState(
                key = key,
                value = value,
                timestamp = getMemoryTimestamp(project.id, key)
            )
        }.sortedByDescending { it.timestamp }

        return ProjectMemoryState(
            projectId = project.id,
            projectName = project.name,
            memories = memories
        )
    }

    /**
     * Get timestamp for a memory (approximate based on storage)
     */
    private suspend fun getMemoryTimestamp(projectId: String, key: String): Long {
        // For now, return current time as we don't store timestamps externally
        // In production, this could be enhanced to store timestamps
        return System.currentTimeMillis() - (key.hashCode() % 86400000).toLong().coerceAtLeast(0)
    }

    /**
     * Select a project to expand/collapse
     */
    fun selectProject(projectId: String?) {
        _uiState.update { it.copy(selectedProject = projectId) }
    }

    /**
     * Start editing a memory
     */
    fun startEditingMemory(projectId: String, memory: MemoryState) {
        _uiState.update { state ->
            state.copy(
                editingMemory = EditingMemoryState(
                    projectId = projectId,
                    key = memory.key,
                    originalValue = memory.value,
                    editedValue = memory.value
                )
            )
        }
    }

    /**
     * Cancel editing
     */
    fun cancelEditing() {
        _uiState.update { it.copy(editingMemory = null) }
    }

    /**
     * Save edited memory
     */
    fun saveMemoryEdit(key: String, newValue: String) {
        val editingMemory = _uiState.value.editingMemory ?: return

        viewModelScope.launch {
            try {
                memoryStorage.save(editingMemory.projectId, key, newValue)
                _uiState.update { it.copy(editingMemory = null) }
                loadMemories()
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(error = "Failed to save memory: ${e.message}")
                }
            }
        }
    }

    /**
     * Add a new memory to the selected project
     */
    fun addMemory(key: String, value: String) {
        val projectId = _uiState.value.selectedProject ?: return

        viewModelScope.launch {
            try {
                memoryStorage.save(projectId, key, value)
                loadMemories()
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(error = "Failed to add memory: ${e.message}")
                }
            }
        }
    }

    /**
     * Delete a memory
     */
    fun deleteMemory(projectId: String, key: String) {
        viewModelScope.launch {
            try {
                memoryStorage.delete(projectId, key)
                loadMemories()
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(error = "Failed to delete memory: ${e.message}")
                }
            }
        }
    }

    /**
     * Clear all memories for a project
     */
    fun clearProjectMemories(projectId: String) {
        viewModelScope.launch {
            try {
                memoryStorage.clearProject(projectId)
                loadMemories()
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(error = "Failed to clear memories: ${e.message}")
                }
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val application = CodeXApplication.getInstance()
                return MemoriesViewModel(
                    memoryStorage = application.memoryStorage,
                    projectRepository = application.projectRepository
                ) as T
            }
        }
    }
}
