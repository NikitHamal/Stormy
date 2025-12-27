package com.codex.stormy.ui.screens.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codex.stormy.data.git.CIStatusSummary
import com.codex.stormy.data.git.GitBranch
import com.codex.stormy.data.git.GitChangedFile
import com.codex.stormy.data.git.GitCommit
import com.codex.stormy.data.git.GitCredentials
import com.codex.stormy.data.git.GitCredentialsManager
import com.codex.stormy.data.git.GitFileDiff
import com.codex.stormy.data.git.GitHubActionsService
import com.codex.stormy.data.git.GitManager
import com.codex.stormy.data.git.GitOperationProgress
import com.codex.stormy.data.git.GitOperationResult
import com.codex.stormy.data.git.GitRemote
import com.codex.stormy.data.git.GitRepositoryStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for Git operations and UI state management
 */
class GitViewModel(
    private val gitManager: GitManager,
    private val credentialsManager: GitCredentialsManager,
    private val gitHubActionsService: GitHubActionsService? = null
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(GitUiState())
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    // Events for one-time UI actions
    private val _events = MutableSharedFlow<GitUiEvent>()
    val events: SharedFlow<GitUiEvent> = _events.asSharedFlow()

    // Current project directory
    private var projectDirectory: File? = null

    init {
        // Observe Git manager state
        viewModelScope.launch {
            gitManager.status.collect { status ->
                _uiState.update { it.copy(status = status) }
            }
        }

        viewModelScope.launch {
            gitManager.changedFiles.collect { files ->
                _uiState.update { it.copy(changedFiles = files) }
            }
        }

        viewModelScope.launch {
            gitManager.branches.collect { branches ->
                _uiState.update { it.copy(branches = branches) }
            }
        }

        viewModelScope.launch {
            gitManager.commits.collect { commits ->
                _uiState.update { it.copy(commits = commits) }
            }
        }

        viewModelScope.launch {
            gitManager.isOperationInProgress.collect { inProgress ->
                _uiState.update { it.copy(isLoading = inProgress) }
            }
        }

        viewModelScope.launch {
            gitManager.operationProgress.collect { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
            }
        }

        // Load Git identity
        viewModelScope.launch {
            credentialsManager.gitUserName.collect { name ->
                _uiState.update { it.copy(userName = name) }
            }
        }

        viewModelScope.launch {
            credentialsManager.gitUserEmail.collect { email ->
                _uiState.update { it.copy(userEmail = email) }
            }
        }
    }

    /**
     * Open repository for a project directory
     */
    fun openRepository(directory: File) {
        projectDirectory = directory
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = gitManager.openRepository(directory)) {
                is GitOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isGitRepo = result.data.let { true }
                        )
                    }
                }
                is GitOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message,
                            isGitRepo = false
                        )
                    }
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Initialize a new repository
     */
    fun initRepository() {
        val directory = projectDirectory ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = gitManager.initRepository(directory)) {
                is GitOperationResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isGitRepo = true) }
                    _events.emit(GitUiEvent.ShowMessage("Repository initialized"))
                }
                is GitOperationResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Clone a repository
     */
    fun cloneRepository(
        url: String,
        directory: File,
        branch: String? = null,
        shallow: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = gitManager.cloneRepository(url, directory, branch, shallow)) {
                is GitOperationResult.Success -> {
                    projectDirectory = directory
                    _uiState.update { it.copy(isLoading = false, isGitRepo = true) }
                    _events.emit(GitUiEvent.ShowMessage("Repository cloned successfully"))
                    _events.emit(GitUiEvent.CloneComplete(directory))
                }
                is GitOperationResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Stage files
     */
    fun stageFiles(paths: List<String>) {
        viewModelScope.launch {
            when (val result = gitManager.stageFiles(paths)) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("${paths.size} file(s) staged"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Stage all files
     */
    fun stageAll() {
        viewModelScope.launch {
            when (val result = gitManager.stageAll()) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("All files staged"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Unstage files
     */
    fun unstageFiles(paths: List<String>) {
        viewModelScope.launch {
            when (val result = gitManager.unstageFiles(paths)) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("${paths.size} file(s) unstaged"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Discard changes
     */
    fun discardChanges(paths: List<String>) {
        viewModelScope.launch {
            when (val result = gitManager.discardChanges(paths)) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("Changes discarded"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Create commit
     */
    fun commit(message: String, amend: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = gitManager.commit(message, amend)) {
                is GitOperationResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.emit(GitUiEvent.ShowMessage("Commit created: ${result.data.shortId}"))
                }
                is GitOperationResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Push to remote
     */
    fun push(
        remote: String = "origin",
        setUpstream: Boolean = false,
        force: Boolean = false
    ) {
        viewModelScope.launch {
            when (val result = gitManager.push(remote, null, setUpstream, force)) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("Pushed successfully"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Pull from remote
     */
    fun pull(remote: String = "origin", rebase: Boolean = false) {
        viewModelScope.launch {
            when (val result = gitManager.pull(remote, null, rebase)) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("Pulled successfully"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Fetch from remote
     */
    fun fetch(remote: String = "origin") {
        viewModelScope.launch {
            when (val result = gitManager.fetch(remote)) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("Fetched successfully"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Create branch
     */
    fun createBranch(name: String, checkout: Boolean = false) {
        viewModelScope.launch {
            when (val result = gitManager.createBranch(name, checkout)) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("Branch '$name' created"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Checkout branch
     */
    fun checkout(branchName: String) {
        viewModelScope.launch {
            when (val result = gitManager.checkout(branchName)) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("Checked out '$branchName'"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Delete branch
     */
    fun deleteBranch(name: String, force: Boolean = false) {
        viewModelScope.launch {
            when (val result = gitManager.deleteBranch(name, force)) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("Branch '$name' deleted"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Merge branch
     */
    fun merge(branchName: String) {
        viewModelScope.launch {
            when (val result = gitManager.merge(branchName)) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("Merged '$branchName'"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Reset to a specific commit
     * @param commitId The commit hash to reset to
     * @param hard If true, performs a hard reset (discards all changes). If false, performs a soft reset (keeps changes staged).
     */
    fun resetToCommit(commitId: String, hard: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = gitManager.resetToCommit(commitId, hard)) {
                is GitOperationResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    val resetType = if (hard) "Hard" else "Soft"
                    _events.emit(GitUiEvent.ShowMessage("$resetType reset to ${commitId.take(7)}"))
                }
                is GitOperationResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Get file diff
     */
    fun getFileDiff(path: String, staged: Boolean = false) {
        viewModelScope.launch {
            when (val result = gitManager.getFileDiff(path, staged)) {
                is GitOperationResult.Success -> {
                    _uiState.update { it.copy(currentDiff = result.data) }
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    fun clearDiff() {
        _uiState.update { it.copy(currentDiff = null) }
    }

    /**
     * Load remotes
     */
    fun loadRemotes() {
        viewModelScope.launch {
            when (val result = gitManager.getRemotes()) {
                is GitOperationResult.Success -> {
                    _uiState.update { it.copy(remotes = result.data) }
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Add remote
     */
    fun addRemote(name: String, url: String) {
        viewModelScope.launch {
            when (val result = gitManager.addRemote(name, url)) {
                is GitOperationResult.Success -> {
                    loadRemotes()
                    _events.emit(GitUiEvent.ShowMessage("Remote '$name' added"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Remove remote
     */
    fun removeRemote(name: String) {
        viewModelScope.launch {
            when (val result = gitManager.removeRemote(name)) {
                is GitOperationResult.Success -> {
                    loadRemotes()
                    _events.emit(GitUiEvent.ShowMessage("Remote '$name' removed"))
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Save Git identity
     */
    fun setGitIdentity(name: String, email: String) {
        viewModelScope.launch {
            credentialsManager.setGitIdentity(name, email)
            _events.emit(GitUiEvent.ShowMessage("Git identity saved"))
        }
    }

    /**
     * Save credentials for a host
     */
    fun saveCredentials(host: String, username: String, password: String) {
        viewModelScope.launch {
            val credentials = GitCredentials(username = username, password = password)
            if (host.isEmpty()) {
                credentialsManager.saveDefaultCredentials(credentials)
            } else {
                credentialsManager.saveHostCredentials(host, credentials)
            }
            _events.emit(GitUiEvent.ShowMessage("Credentials saved"))
        }
    }

    /**
     * Refresh repository status
     */
    fun refresh() {
        viewModelScope.launch {
            gitManager.refreshStatus()
            refreshCIStatus()
        }
    }

    /**
     * Refresh CI/CD status from GitHub Actions
     */
    fun refreshCIStatus() {
        if (gitHubActionsService == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCILoading = true) }

            val remoteUrl = _uiState.value.status?.remoteUrl
            val parsed = gitHubActionsService.parseGitHubUrl(remoteUrl)

            if (parsed == null) {
                _uiState.update {
                    it.copy(
                        isCILoading = false,
                        ciStatus = CIStatusSummary.NOT_GITHUB
                    )
                }
                return@launch
            }

            val (owner, repo) = parsed
            val currentBranch = _uiState.value.status?.currentBranch

            // Fetch workflows
            val workflowsResult = gitHubActionsService.getWorkflows(owner, repo)
            val workflows = when (workflowsResult) {
                is GitOperationResult.Success -> workflowsResult.data
                else -> emptyList()
            }

            // Fetch recent runs
            val runsResult = gitHubActionsService.getWorkflowRuns(owner, repo, perPage = 10)
            val recentRuns = when (runsResult) {
                is GitOperationResult.Success -> runsResult.data
                else -> emptyList()
            }

            // Fetch runs for current branch
            val branchRunsResult = currentBranch?.let {
                gitHubActionsService.getWorkflowRuns(owner, repo, branch = it, perPage = 1)
            }
            val currentBranchRun = when (branchRunsResult) {
                is GitOperationResult.Success -> branchRunsResult.data.firstOrNull()
                else -> null
            }

            _uiState.update {
                it.copy(
                    isCILoading = false,
                    ciStatus = CIStatusSummary(
                        isGitHubRepo = true,
                        owner = owner,
                        repo = repo,
                        latestRun = recentRuns.firstOrNull(),
                        recentRuns = recentRuns,
                        workflows = workflows,
                        currentBranchStatus = currentBranchRun?.displayStatus,
                        errorMessage = null
                    )
                )
            }
        }
    }

    /**
     * Re-run a workflow
     */
    fun rerunWorkflow(runId: Long) {
        if (gitHubActionsService == null) return

        val ciStatus = _uiState.value.ciStatus ?: return
        val owner = ciStatus.owner ?: return
        val repo = ciStatus.repo ?: return

        viewModelScope.launch {
            when (val result = gitHubActionsService.rerunWorkflow(owner, repo, runId)) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("Workflow re-run triggered"))
                    refreshCIStatus()
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    /**
     * Cancel a workflow run
     */
    fun cancelWorkflow(runId: Long) {
        if (gitHubActionsService == null) return

        val ciStatus = _uiState.value.ciStatus ?: return
        val owner = ciStatus.owner ?: return
        val repo = ciStatus.repo ?: return

        viewModelScope.launch {
            when (val result = gitHubActionsService.cancelWorkflowRun(owner, repo, runId)) {
                is GitOperationResult.Success -> {
                    _events.emit(GitUiEvent.ShowMessage("Workflow run cancelled"))
                    refreshCIStatus()
                }
                is GitOperationResult.Error -> {
                    _events.emit(GitUiEvent.ShowError(result.message))
                }
                is GitOperationResult.InProgress -> {}
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        projectDirectory?.let { gitManager.closeRepository(it) }
    }

    class Factory(
        private val gitManager: GitManager,
        private val credentialsManager: GitCredentialsManager,
        private val gitHubActionsService: GitHubActionsService? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GitViewModel::class.java)) {
                return GitViewModel(gitManager, credentialsManager, gitHubActionsService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * UI State for Git screen
 */
data class GitUiState(
    val isLoading: Boolean = false,
    val isGitRepo: Boolean = false,
    val error: String? = null,
    val status: GitRepositoryStatus? = null,
    val changedFiles: List<GitChangedFile> = emptyList(),
    val branches: List<GitBranch> = emptyList(),
    val commits: List<GitCommit> = emptyList(),
    val remotes: List<GitRemote> = emptyList(),
    val currentDiff: GitFileDiff? = null,
    val operationProgress: GitOperationProgress? = null,
    val userName: String = "",
    val userEmail: String = "",
    // CI/CD status
    val isCILoading: Boolean = false,
    val ciStatus: CIStatusSummary? = null
) {
    val stagedFiles: List<GitChangedFile>
        get() = changedFiles.filter { it.isStaged }

    val unstagedFiles: List<GitChangedFile>
        get() = changedFiles.filter { !it.isStaged }

    val currentBranch: GitBranch?
        get() = branches.find { it.isCurrent }

    val localBranches: List<GitBranch>
        get() = branches.filter { it.isLocal }

    val remoteBranches: List<GitBranch>
        get() = branches.filter { it.isRemote }
}

/**
 * One-time UI events
 */
sealed class GitUiEvent {
    data class ShowMessage(val message: String) : GitUiEvent()
    data class ShowError(val message: String) : GitUiEvent()
    data class CloneComplete(val directory: File) : GitUiEvent()
}
