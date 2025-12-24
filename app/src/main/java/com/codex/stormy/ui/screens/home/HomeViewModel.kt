package com.codex.stormy.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codex.stormy.CodeXApplication
import com.codex.stormy.data.git.GitManager
import com.codex.stormy.data.git.GitOperationResult
import com.codex.stormy.data.local.entity.ProjectTemplate
import com.codex.stormy.data.repository.ProjectRepository
import com.codex.stormy.domain.model.Project
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    // Clone state
    val isCloning: Boolean = false,
    val cloneProgress: Float? = null,
    val cloneProgressMessage: String? = null,
    val cloneError: String? = null,
    val clonedProjectId: String? = null,
    // Import state
    val isImporting: Boolean = false,
    val importProgress: Float? = null,
    val importProgressMessage: String? = null,
    val importError: String? = null,
    val importedProjectId: String? = null
)

class HomeViewModel(
    private val projectRepository: ProjectRepository,
    private val gitManager: GitManager? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    // Clone state
    private val _cloneState = MutableStateFlow(CloneState())
    val cloneState: StateFlow<CloneState> = _cloneState.asStateFlow()

    data class CloneState(
        val isCloning: Boolean = false,
        val progress: Float? = null,
        val progressMessage: String? = null,
        val error: String? = null,
        val clonedProjectId: String? = null
    )

    // Import state
    private val _importState = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    data class ImportState(
        val isImporting: Boolean = false,
        val progress: Float? = null,
        val progressMessage: String? = null,
        val error: String? = null,
        val importedProjectId: String? = null
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val projects = _searchQuery.flatMapLatest { query ->
        if (query.isEmpty()) {
            projectRepository.getAllProjects()
        } else {
            projectRepository.searchProjects(query)
        }
    }

    // Combine clone and import state for efficient flow combination
    private val operationState = combine(_cloneState, _importState) { clone, import ->
        clone to import
    }

    val uiState: StateFlow<HomeUiState> = combine(
        projects,
        _searchQuery,
        _isLoading,
        _error,
        operationState
    ) { projects, query, loading, error, (clone, import) ->
        HomeUiState(
            projects = projects,
            searchQuery = query,
            isLoading = loading,
            error = error,
            isCloning = clone.isCloning,
            cloneProgress = clone.progress,
            cloneProgressMessage = clone.progressMessage,
            cloneError = clone.error,
            clonedProjectId = clone.clonedProjectId,
            isImporting = import.isImporting,
            importProgress = import.progress,
            importProgressMessage = import.progressMessage,
            importError = import.error,
            importedProjectId = import.importedProjectId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun createProject(name: String, description: String, template: ProjectTemplate) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            projectRepository.createProject(name, description, template)
                .onFailure { throwable ->
                    _error.value = throwable.message
                }

            _isLoading.value = false
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            projectRepository.deleteProject(projectId)
                .onFailure { throwable ->
                    _error.value = throwable.message
                }

            _isLoading.value = false
        }
    }

    fun openProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.updateLastOpenedAt(projectId)
        }
    }

    /**
     * Update project name and description
     */
    fun updateProject(projectId: String, name: String, description: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            projectRepository.updateProject(projectId, name, description)
                .onFailure { throwable ->
                    _error.value = throwable.message
                }

            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Clone a Git repository and create a project from it
     */
    fun cloneRepository(url: String, projectName: String, shallow: Boolean) {
        if (gitManager == null) {
            _cloneState.update { it.copy(error = "Git is not available") }
            return
        }

        viewModelScope.launch {
            _cloneState.update {
                CloneState(
                    isCloning = true,
                    progressMessage = "Connecting to repository..."
                )
            }

            // Monitor clone progress
            launch {
                gitManager.operationProgress.collect { progress ->
                    if (progress != null) {
                        _cloneState.update {
                            it.copy(
                                progress = progress.percentage,
                                progressMessage = progress.message.ifBlank { progress.operation }
                            )
                        }
                    }
                }
            }

            // Create project directory
            val projectResult = projectRepository.createProjectForClone(projectName)

            projectResult.onSuccess { project ->
                val targetDir = File(project.rootPath)

                // Clone repository
                val result = gitManager.cloneRepository(
                    url = url,
                    directory = targetDir,
                    shallow = shallow
                )

                when (result) {
                    is GitOperationResult.Success -> {
                        _cloneState.update {
                            CloneState(
                                isCloning = false,
                                clonedProjectId = project.id
                            )
                        }
                    }
                    is GitOperationResult.Error -> {
                        // Delete the project if clone failed
                        projectRepository.deleteProject(project.id)
                        _cloneState.update {
                            CloneState(
                                isCloning = false,
                                error = result.message
                            )
                        }
                    }
                    is GitOperationResult.InProgress -> {
                        // This shouldn't happen for final result
                    }
                }
            }.onFailure { throwable ->
                _cloneState.update {
                    CloneState(
                        isCloning = false,
                        error = throwable.message ?: "Failed to create project"
                    )
                }
            }
        }
    }

    fun clearCloneState() {
        _cloneState.value = CloneState()
    }

    fun acknowledgeClonedProject() {
        _cloneState.update { it.copy(clonedProjectId = null) }
    }

    /**
     * Import a project from a folder selected via document picker
     */
    fun importFolder(name: String, description: String, folderUri: Uri) {
        viewModelScope.launch {
            _importState.update {
                ImportState(
                    isImporting = true,
                    progressMessage = "Scanning folder..."
                )
            }

            val result = projectRepository.importProjectFromFolder(
                name = name,
                description = description,
                sourceFolderUri = folderUri,
                progressCallback = { copied, total ->
                    val progress = if (total > 0) copied.toFloat() / total else 0f
                    _importState.update {
                        it.copy(
                            progress = progress,
                            progressMessage = "Copying files... ($copied/$total)"
                        )
                    }
                }
            )

            result.onSuccess { project ->
                _importState.update {
                    ImportState(
                        isImporting = false,
                        importedProjectId = project.id
                    )
                }
            }.onFailure { throwable ->
                _importState.update {
                    ImportState(
                        isImporting = false,
                        error = throwable.message ?: "Failed to import folder"
                    )
                }
            }
        }
    }

    fun clearImportState() {
        _importState.value = ImportState()
    }

    fun acknowledgeImportedProject() {
        _importState.update { it.copy(importedProjectId = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val application = CodeXApplication.getInstance()
                return HomeViewModel(
                    projectRepository = application.projectRepository,
                    gitManager = application.gitManager
                ) as T
            }
        }
    }
}
