package com.codex.stormy.data.git

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-level Git manager that combines GitRepository with credentials management
 * Provides a unified API for Git operations with automatic credential handling
 */
class GitManager(
    private val credentialsManager: GitCredentialsManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cache of open repositories
    private val repositories = mutableMapOf<String, GitRepository>()

    // Current active repository
    private var _currentRepository: GitRepository? = null
    val currentRepository: GitRepository? get() = _currentRepository

    // Repository status flow
    private val _status = MutableStateFlow<GitRepositoryStatus?>(null)
    val status: StateFlow<GitRepositoryStatus?> = _status.asStateFlow()

    // Changed files flow
    private val _changedFiles = MutableStateFlow<List<GitChangedFile>>(emptyList())
    val changedFiles: StateFlow<List<GitChangedFile>> = _changedFiles.asStateFlow()

    // Branches flow
    private val _branches = MutableStateFlow<List<GitBranch>>(emptyList())
    val branches: StateFlow<List<GitBranch>> = _branches.asStateFlow()

    // Commit history flow
    private val _commits = MutableStateFlow<List<GitCommit>>(emptyList())
    val commits: StateFlow<List<GitCommit>> = _commits.asStateFlow()

    // Operation state
    private val _isOperationInProgress = MutableStateFlow(false)
    val isOperationInProgress: StateFlow<Boolean> = _isOperationInProgress.asStateFlow()

    private val _operationProgress = MutableStateFlow<GitOperationProgress?>(null)
    val operationProgress: StateFlow<GitOperationProgress?> = _operationProgress.asStateFlow()

    /**
     * Open or get repository for a directory
     */
    suspend fun openRepository(directory: File): GitOperationResult<GitRepository> {
        return withContext(Dispatchers.IO) {
            try {
                val path = directory.absolutePath

                // Check cache first
                repositories[path]?.let { repo ->
                    _currentRepository = repo
                    refreshStatus()
                    return@withContext GitOperationResult.Success(repo)
                }

                // Create and open new repository
                val repo = GitRepository(directory)
                val result = repo.open()

                when (result) {
                    is GitOperationResult.Success -> {
                        if (result.data) {
                            repositories[path] = repo
                            _currentRepository = repo
                            refreshStatus()
                            GitOperationResult.Success(repo, "Repository opened")
                        } else {
                            GitOperationResult.Success(repo, "Not a Git repository")
                        }
                    }
                    is GitOperationResult.Error -> result
                    is GitOperationResult.InProgress -> GitOperationResult.InProgress
                }
            } catch (e: Exception) {
                GitOperationResult.Error("Failed to open repository: ${e.message}", e)
            }
        }
    }

    /**
     * Initialize a new repository
     */
    suspend fun initRepository(directory: File): GitOperationResult<GitRepository> {
        return withContext(Dispatchers.IO) {
            try {
                val repo = GitRepository(directory)
                val result = repo.init()

                when (result) {
                    is GitOperationResult.Success -> {
                        val path = directory.absolutePath
                        repositories[path] = repo
                        _currentRepository = repo
                        refreshStatus()
                        GitOperationResult.Success(repo, "Repository initialized")
                    }
                    is GitOperationResult.Error -> result
                    is GitOperationResult.InProgress -> GitOperationResult.InProgress
                }
            } catch (e: Exception) {
                GitOperationResult.Error("Failed to initialize repository: ${e.message}", e)
            }
        }
    }

    /**
     * Clone a repository
     */
    suspend fun cloneRepository(
        url: String,
        directory: File,
        branch: String? = null,
        shallow: Boolean = false,
        progressCallback: GitProgressCallback? = null
    ): GitOperationResult<GitRepository> {
        return withContext(Dispatchers.IO) {
            _isOperationInProgress.value = true
            try {
                // Get credentials for the URL
                val credentials = credentialsManager.getCredentialsForUrl(url)

                val options = GitCloneOptions(
                    url = url,
                    directory = directory.absolutePath,
                    branch = branch,
                    depth = if (shallow) 1 else null,
                    credentials = credentials
                )

                val repo = GitRepository(directory)
                val result = repo.clone(options) { current, total, message ->
                    _operationProgress.value = GitOperationProgress(
                        operation = "Cloning",
                        current = current,
                        total = total,
                        message = message
                    )
                    progressCallback?.invoke(current, total, message)
                }

                when (result) {
                    is GitOperationResult.Success -> {
                        val path = directory.absolutePath
                        repositories[path] = repo
                        _currentRepository = repo
                        refreshStatus()
                        GitOperationResult.Success(repo, "Repository cloned")
                    }
                    is GitOperationResult.Error -> result
                    is GitOperationResult.InProgress -> GitOperationResult.InProgress
                }
            } catch (e: Exception) {
                GitOperationResult.Error("Failed to clone repository: ${e.message}", e)
            } finally {
                _isOperationInProgress.value = false
                _operationProgress.value = null
            }
        }
    }

    /**
     * Refresh repository status and data
     */
    suspend fun refreshStatus() {
        val repo = _currentRepository ?: return

        scope.launch {
            // Get status
            when (val result = repo.getStatus()) {
                is GitOperationResult.Success -> _status.value = result.data
                else -> {}
            }

            // Get changed files
            when (val result = repo.getChangedFiles()) {
                is GitOperationResult.Success -> _changedFiles.value = result.data
                else -> {}
            }

            // Get branches
            when (val result = repo.getBranches()) {
                is GitOperationResult.Success -> _branches.value = result.data
                else -> {}
            }

            // Get recent commits
            when (val result = repo.getCommitHistory(20)) {
                is GitOperationResult.Success -> _commits.value = result.data
                else -> {}
            }
        }
    }

    // Stage operations

    suspend fun stageFiles(paths: List<String>): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        val result = repo.stage(paths)
        if (result is GitOperationResult.Success) refreshStatus()
        return result
    }

    suspend fun stageAll(): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        val result = repo.stageAll()
        if (result is GitOperationResult.Success) refreshStatus()
        return result
    }

    suspend fun unstageFiles(paths: List<String>): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        val result = repo.unstage(paths)
        if (result is GitOperationResult.Success) refreshStatus()
        return result
    }

    suspend fun discardChanges(paths: List<String>): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        val result = repo.discardChanges(paths)
        if (result is GitOperationResult.Success) refreshStatus()
        return result
    }

    // Commit operations

    suspend fun commit(
        message: String,
        amend: Boolean = false
    ): GitOperationResult<GitCommit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")

        // Get user identity
        val (name, email) = credentialsManager.getGitIdentity()

        val options = GitCommitOptions(
            message = message,
            authorName = name.takeIf { it.isNotEmpty() },
            authorEmail = email.takeIf { it.isNotEmpty() },
            amend = amend
        )

        val result = repo.commit(options)
        if (result is GitOperationResult.Success) refreshStatus()
        return result
    }

    // Remote operations

    suspend fun push(
        remote: String = "origin",
        branch: String? = null,
        setUpstream: Boolean = false,
        force: Boolean = false,
        progressCallback: GitProgressCallback? = null
    ): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")

        _isOperationInProgress.value = true
        return try {
            // Get remote URL to look up credentials
            val remotes = repo.getRemotes()
            val remoteUrl = when (remotes) {
                is GitOperationResult.Success -> {
                    remotes.data.find { it.name == remote }?.pushUrl
                }
                else -> null
            }

            val credentials = remoteUrl?.let { credentialsManager.getCredentialsForUrl(it) }

            val options = GitPushOptions(
                remote = remote,
                branch = branch,
                setUpstream = setUpstream,
                force = force,
                credentials = credentials
            )

            val result = repo.push(options) { current, total, message ->
                _operationProgress.value = GitOperationProgress(
                    operation = "Pushing",
                    current = current,
                    total = total,
                    message = message
                )
                progressCallback?.invoke(current, total, message)
            }

            if (result is GitOperationResult.Success) refreshStatus()
            result
        } finally {
            _isOperationInProgress.value = false
            _operationProgress.value = null
        }
    }

    suspend fun pull(
        remote: String = "origin",
        branch: String? = null,
        rebase: Boolean? = null,
        progressCallback: GitProgressCallback? = null
    ): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")

        _isOperationInProgress.value = true
        return try {
            // Get remote URL to look up credentials
            val remotes = repo.getRemotes()
            val remoteUrl = when (remotes) {
                is GitOperationResult.Success -> {
                    remotes.data.find { it.name == remote }?.fetchUrl
                }
                else -> null
            }

            val credentials = remoteUrl?.let { credentialsManager.getCredentialsForUrl(it) }

            // Use provided rebase setting or fall back to preference
            val useRebase = rebase ?: credentialsManager.pullRebase.let { flow ->
                var value = false
                scope.launch { value = flow.let { false } }
                value
            }

            val options = GitPullOptions(
                remote = remote,
                branch = branch,
                rebase = useRebase,
                credentials = credentials
            )

            val result = repo.pull(options) { current, total, message ->
                _operationProgress.value = GitOperationProgress(
                    operation = "Pulling",
                    current = current,
                    total = total,
                    message = message
                )
                progressCallback?.invoke(current, total, message)
            }

            if (result is GitOperationResult.Success) refreshStatus()
            result
        } finally {
            _isOperationInProgress.value = false
            _operationProgress.value = null
        }
    }

    suspend fun fetch(
        remote: String = "origin",
        prune: Boolean = true,
        progressCallback: GitProgressCallback? = null
    ): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")

        _isOperationInProgress.value = true
        return try {
            // Get remote URL to look up credentials
            val remotes = repo.getRemotes()
            val remoteUrl = when (remotes) {
                is GitOperationResult.Success -> {
                    remotes.data.find { it.name == remote }?.fetchUrl
                }
                else -> null
            }

            val credentials = remoteUrl?.let { credentialsManager.getCredentialsForUrl(it) }

            val options = GitFetchOptions(
                remote = remote,
                prune = prune,
                credentials = credentials
            )

            val result = repo.fetch(options) { current, total, message ->
                _operationProgress.value = GitOperationProgress(
                    operation = "Fetching",
                    current = current,
                    total = total,
                    message = message
                )
                progressCallback?.invoke(current, total, message)
            }

            if (result is GitOperationResult.Success) refreshStatus()
            result
        } finally {
            _isOperationInProgress.value = false
            _operationProgress.value = null
        }
    }

    // Branch operations

    suspend fun createBranch(
        name: String,
        checkout: Boolean = false
    ): GitOperationResult<GitBranch> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        val result = repo.createBranch(name, checkout)
        if (result is GitOperationResult.Success) refreshStatus()
        return result
    }

    suspend fun checkout(branchName: String): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        val result = repo.checkout(branchName)
        if (result is GitOperationResult.Success) refreshStatus()
        return result
    }

    suspend fun deleteBranch(
        name: String,
        force: Boolean = false
    ): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        val result = repo.deleteBranch(name, force)
        if (result is GitOperationResult.Success) refreshStatus()
        return result
    }

    suspend fun merge(branchName: String): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        val result = repo.merge(branchName)
        if (result is GitOperationResult.Success) refreshStatus()
        return result
    }

    // Remote management

    suspend fun getRemotes(): GitOperationResult<List<GitRemote>> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        return repo.getRemotes()
    }

    suspend fun addRemote(name: String, url: String): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        return repo.addRemote(name, url)
    }

    suspend fun removeRemote(name: String): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        return repo.removeRemote(name)
    }

    // Diff operations

    suspend fun getFileDiff(path: String, staged: Boolean = false): GitOperationResult<GitFileDiff> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        return repo.getFileDiff(path, staged)
    }

    // Stash operations

    suspend fun stash(message: String? = null): GitOperationResult<String> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        val result = repo.stash(message)
        if (result is GitOperationResult.Success) refreshStatus()
        return result
    }

    suspend fun stashApply(
        stashRef: String? = null,
        drop: Boolean = false
    ): GitOperationResult<Unit> {
        val repo = _currentRepository
            ?: return GitOperationResult.Error("No repository open")
        val result = repo.stashApply(stashRef, drop)
        if (result is GitOperationResult.Success) refreshStatus()
        return result
    }

    /**
     * Close a repository
     */
    fun closeRepository(directory: File) {
        val path = directory.absolutePath
        repositories[path]?.close()
        repositories.remove(path)

        if (_currentRepository?.let { path == directory.absolutePath } == true) {
            _currentRepository = null
            _status.value = null
            _changedFiles.value = emptyList()
            _branches.value = emptyList()
            _commits.value = emptyList()
        }
    }

    /**
     * Close all repositories
     */
    fun closeAll() {
        repositories.values.forEach { it.close() }
        repositories.clear()
        _currentRepository = null
        _status.value = null
        _changedFiles.value = emptyList()
        _branches.value = emptyList()
        _commits.value = emptyList()
    }
}

/**
 * Progress information for Git operations
 */
data class GitOperationProgress(
    val operation: String,
    val current: Int,
    val total: Int,
    val message: String
) {
    val percentage: Float get() = if (total > 0) current.toFloat() / total else 0f
}
