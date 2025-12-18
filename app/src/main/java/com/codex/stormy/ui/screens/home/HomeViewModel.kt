package com.codex.stormy.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codex.stormy.CodeXApplication
import com.codex.stormy.data.local.entity.ProjectTemplate
import com.codex.stormy.data.repository.ProjectRepository
import com.codex.stormy.domain.model.Project
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val projects = _searchQuery.flatMapLatest { query ->
        if (query.isEmpty()) {
            projectRepository.getAllProjects()
        } else {
            projectRepository.searchProjects(query)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        projects,
        _searchQuery,
        _isLoading,
        _error
    ) { projects, query, loading, error ->
        HomeUiState(
            projects = projects,
            searchQuery = query,
            isLoading = loading,
            error = error
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

    fun clearError() {
        _error.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val application = CodeXApplication.getInstance()
                return HomeViewModel(
                    projectRepository = application.projectRepository
                ) as T
            }
        }
    }
}
