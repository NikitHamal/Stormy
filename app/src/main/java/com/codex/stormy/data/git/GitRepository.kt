package com.codex.stormy.data.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.errors.LockFailedException
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Repository class for Git operations using JGit
 */
class GitRepository(
    private val workingDirectory: File
) {
    private var repository: Repository? = null
    private var git: Git? = null

    /**
     * Initialize or open an existing Git repository
     */
    suspend fun open(): GitOperationResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val gitDir = File(workingDirectory, ".git")
            if (gitDir.exists()) {
                repository = FileRepositoryBuilder()
                    .setGitDir(gitDir)
                    .readEnvironment()
                    .findGitDir()
                    .build()
                git = Git(repository)
                GitOperationResult.Success(true, "Repository opened successfully")
            } else {
                GitOperationResult.Success(false, "Not a Git repository")
            }
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to open repository: ${e.message}", e)
        }
    }

    /**
     * Initialize a new Git repository
     */
    suspend fun init(): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            git = Git.init()
                .setDirectory(workingDirectory)
                .call()
            repository = git?.repository
            GitOperationResult.Success(Unit, "Repository initialized")
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to initialize repository: ${e.message}", e)
        }
    }

    /**
     * Clone a remote repository
     */
    suspend fun clone(
        options: GitCloneOptions,
        progressCallback: GitProgressCallback? = null
    ): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val cloneCommand = Git.cloneRepository()
                .setURI(options.url)
                .setDirectory(File(options.directory))

            options.branch?.let { cloneCommand.setBranch(it) }
            options.credentials?.let {
                cloneCommand.setCredentialsProvider(createCredentialsProvider(it))
            }

            cloneCommand.setProgressMonitor(JGitProgressMonitor(progressCallback))

            git = cloneCommand.call()
            repository = git?.repository
            GitOperationResult.Success(Unit, "Repository cloned successfully")
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to clone repository: ${e.message}", e)
        }
    }

    /**
     * Get repository status
     */
    suspend fun getStatus(): GitOperationResult<GitRepositoryStatus> = withContext(Dispatchers.IO) {
        val repo = repository ?: return@withContext GitOperationResult.Success(
            GitRepositoryStatus(isGitRepo = false)
        )

        try {
            val status = git?.status()?.call()
                ?: return@withContext GitOperationResult.Error("Failed to get status")

            val branch = repo.branch ?: ""
            val hasRemote = repo.remoteNames.isNotEmpty()
            val remoteUrl = if (hasRemote) {
                repo.config.getString("remote", "origin", "url")
            } else null

            // Get ahead/behind counts
            var aheadCount = 0
            var behindCount = 0
            if (branch.isNotEmpty() && hasRemote) {
                try {
                    val trackingStatus = BranchTrackingStatus.of(repo, branch)
                    aheadCount = trackingStatus?.aheadCount ?: 0
                    behindCount = trackingStatus?.behindCount ?: 0
                } catch (e: Exception) {
                    // Ignore tracking status errors
                }
            }

            val repoStatus = GitRepositoryStatus(
                isGitRepo = true,
                currentBranch = branch,
                hasRemote = hasRemote,
                remoteUrl = remoteUrl,
                isClean = status.isClean,
                hasUncommittedChanges = !status.isClean,
                hasUnstagedChanges = status.modified.isNotEmpty() || status.missing.isNotEmpty(),
                hasStagedChanges = status.added.isNotEmpty() || status.changed.isNotEmpty() || status.removed.isNotEmpty(),
                aheadCount = aheadCount,
                behindCount = behindCount
            )

            GitOperationResult.Success(repoStatus)
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to get status: ${e.message}", e)
        }
    }

    /**
     * Get list of changed files
     */
    suspend fun getChangedFiles(): GitOperationResult<List<GitChangedFile>> = withContext(Dispatchers.IO) {
        try {
            val status = git?.status()?.call()
                ?: return@withContext GitOperationResult.Error("Failed to get status")

            val changedFiles = mutableListOf<GitChangedFile>()

            // Staged files
            status.added.forEach {
                changedFiles.add(GitChangedFile(it, GitFileStatus.ADDED, isStaged = true))
            }
            status.changed.forEach {
                changedFiles.add(GitChangedFile(it, GitFileStatus.MODIFIED, isStaged = true))
            }
            status.removed.forEach {
                changedFiles.add(GitChangedFile(it, GitFileStatus.DELETED, isStaged = true))
            }

            // Unstaged files
            status.modified.forEach { path ->
                if (!status.changed.contains(path)) {
                    changedFiles.add(GitChangedFile(path, GitFileStatus.MODIFIED, isStaged = false))
                }
            }
            status.missing.forEach { path ->
                if (!status.removed.contains(path)) {
                    changedFiles.add(GitChangedFile(path, GitFileStatus.DELETED, isStaged = false))
                }
            }

            // Untracked files
            status.untracked.forEach {
                changedFiles.add(GitChangedFile(it, GitFileStatus.UNTRACKED, isStaged = false))
            }

            // Conflicting files
            status.conflicting.forEach {
                changedFiles.add(GitChangedFile(it, GitFileStatus.CONFLICTING, isStaged = false))
            }

            GitOperationResult.Success(changedFiles)
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to get changed files: ${e.message}", e)
        }
    }

    /**
     * Stage files for commit
     * Handles both added/modified files and deleted files properly
     */
    suspend fun stage(paths: List<String>): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val gitInstance = git ?: return@withContext GitOperationResult.Error("Repository not opened")

            // Get current status to identify deleted files
            val status = gitInstance.status().call()
            val missingFiles = status.missing

            // Separate paths into existing files and deleted files
            val existingFiles = paths.filter { it !in missingFiles }
            val deletedFiles = paths.filter { it in missingFiles }

            // Stage existing files (added/modified) using add command
            if (existingFiles.isNotEmpty()) {
                val addCommand = gitInstance.add()
                existingFiles.forEach { addCommand.addFilepattern(it) }
                addCommand.call()
            }

            // Stage deleted files using rm command (removes from index)
            if (deletedFiles.isNotEmpty()) {
                val rmCommand = gitInstance.rm().setCached(true)
                deletedFiles.forEach { rmCommand.addFilepattern(it) }
                rmCommand.call()
            }

            GitOperationResult.Success(Unit, "Files staged")
        } catch (e: JGitInternalException) {
            // Handle lock file errors
            if (e.cause is LockFailedException) {
                handleLockFileError()
                GitOperationResult.Error("Repository is locked. Please try again.", e)
            } else {
                GitOperationResult.Error("Failed to stage files: ${e.message}", e)
            }
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to stage files: ${e.message}", e)
        }
    }

    /**
     * Stage all files including deletions
     */
    suspend fun stageAll(): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val gitInstance = git ?: return@withContext GitOperationResult.Error("Repository not opened")

            // Stage all new and modified files
            gitInstance.add()
                .addFilepattern(".")
                .call()

            // Stage all deleted files by using add with setUpdate(true)
            // This updates the index for tracked files (including deletions)
            gitInstance.add()
                .addFilepattern(".")
                .setUpdate(true)
                .call()

            GitOperationResult.Success(Unit, "All files staged")
        } catch (e: JGitInternalException) {
            // Handle lock file errors
            if (e.cause is LockFailedException) {
                handleLockFileError()
                GitOperationResult.Error("Repository is locked. Please try again.", e)
            } else {
                GitOperationResult.Error("Failed to stage all files: ${e.message}", e)
            }
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to stage all files: ${e.message}", e)
        }
    }

    /**
     * Unstage files
     * Handles LockFailedException by cleaning up stale lock files and retrying
     */
    suspend fun unstage(paths: List<String>): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val resetCommand = git?.reset() ?: return@withContext GitOperationResult.Error("Repository not opened")
            paths.forEach { resetCommand.addPath(it) }
            resetCommand.call()
            GitOperationResult.Success(Unit, "Files unstaged")
        } catch (e: JGitInternalException) {
            if (e.cause is LockFailedException) {
                handleLockFileError()
                // Retry once after cleaning up lock files
                try {
                    val retryCommand = git?.reset() ?: return@withContext GitOperationResult.Error("Repository not opened")
                    paths.forEach { retryCommand.addPath(it) }
                    retryCommand.call()
                    GitOperationResult.Success(Unit, "Files unstaged")
                } catch (retryError: Exception) {
                    GitOperationResult.Error("Failed to unstage files after lock cleanup: ${retryError.message}", retryError)
                }
            } else {
                GitOperationResult.Error("Failed to unstage files: ${e.message}", e)
            }
        } catch (e: GitAPIException) {
            if (e.cause is LockFailedException) {
                handleLockFileError()
                // Retry once after cleaning up lock files
                try {
                    val retryCommand = git?.reset() ?: return@withContext GitOperationResult.Error("Repository not opened")
                    paths.forEach { retryCommand.addPath(it) }
                    retryCommand.call()
                    GitOperationResult.Success(Unit, "Files unstaged")
                } catch (retryError: Exception) {
                    GitOperationResult.Error("Failed to unstage files after lock cleanup: ${retryError.message}", retryError)
                }
            } else {
                GitOperationResult.Error("Failed to unstage files: ${e.message}", e)
            }
        }
    }

    /**
     * Discard changes to files
     * Handles LockFailedException by cleaning up stale lock files and retrying
     */
    suspend fun discardChanges(paths: List<String>): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val checkoutCommand = git?.checkout() ?: return@withContext GitOperationResult.Error("Repository not opened")
            paths.forEach { checkoutCommand.addPath(it) }
            checkoutCommand.call()
            GitOperationResult.Success(Unit, "Changes discarded")
        } catch (e: JGitInternalException) {
            if (e.cause is LockFailedException) {
                handleLockFileError()
                try {
                    val retryCommand = git?.checkout() ?: return@withContext GitOperationResult.Error("Repository not opened")
                    paths.forEach { retryCommand.addPath(it) }
                    retryCommand.call()
                    GitOperationResult.Success(Unit, "Changes discarded")
                } catch (retryError: Exception) {
                    GitOperationResult.Error("Failed to discard changes after lock cleanup: ${retryError.message}", retryError)
                }
            } else {
                GitOperationResult.Error("Failed to discard changes: ${e.message}", e)
            }
        } catch (e: GitAPIException) {
            if (e.cause is LockFailedException) {
                handleLockFileError()
                try {
                    val retryCommand = git?.checkout() ?: return@withContext GitOperationResult.Error("Repository not opened")
                    paths.forEach { retryCommand.addPath(it) }
                    retryCommand.call()
                    GitOperationResult.Success(Unit, "Changes discarded")
                } catch (retryError: Exception) {
                    GitOperationResult.Error("Failed to discard changes after lock cleanup: ${retryError.message}", retryError)
                }
            } else {
                GitOperationResult.Error("Failed to discard changes: ${e.message}", e)
            }
        }
    }

    /**
     * Create a commit
     * Handles LockFailedException by cleaning up stale lock files and retrying
     */
    suspend fun commit(options: GitCommitOptions): GitOperationResult<GitCommit> = withContext(Dispatchers.IO) {
        fun createCommitCommand() = git?.commit()
            ?.setMessage(options.message)
            ?.setAmend(options.amend)
            ?.setAllowEmpty(options.allowEmpty)
            ?.also { cmd ->
                options.authorName?.let { name ->
                    options.authorEmail?.let { email ->
                        cmd.setAuthor(name, email)
                    }
                }
            }

        try {
            val commitCommand = createCommitCommand()
                ?: return@withContext GitOperationResult.Error("Repository not opened")

            val commit = commitCommand.call()
            GitOperationResult.Success(
                commit.toGitCommit(),
                "Committed: ${commit.shortMessage}"
            )
        } catch (e: JGitInternalException) {
            if (e.cause is LockFailedException) {
                handleLockFileError()
                try {
                    val retryCommand = createCommitCommand()
                        ?: return@withContext GitOperationResult.Error("Repository not opened")
                    val commit = retryCommand.call()
                    GitOperationResult.Success(
                        commit.toGitCommit(),
                        "Committed: ${commit.shortMessage}"
                    )
                } catch (retryError: Exception) {
                    GitOperationResult.Error("Failed to commit after lock cleanup: ${retryError.message}", retryError)
                }
            } else {
                GitOperationResult.Error("Failed to commit: ${e.message}", e)
            }
        } catch (e: GitAPIException) {
            if (e.cause is LockFailedException) {
                handleLockFileError()
                try {
                    val retryCommand = createCommitCommand()
                        ?: return@withContext GitOperationResult.Error("Repository not opened")
                    val commit = retryCommand.call()
                    GitOperationResult.Success(
                        commit.toGitCommit(),
                        "Committed: ${commit.shortMessage}"
                    )
                } catch (retryError: Exception) {
                    GitOperationResult.Error("Failed to commit after lock cleanup: ${retryError.message}", retryError)
                }
            } else {
                GitOperationResult.Error("Failed to commit: ${e.message}", e)
            }
        }
    }

    /**
     * Push to remote
     * Validates push results to ensure refs were actually updated
     */
    suspend fun push(
        options: GitPushOptions,
        progressCallback: GitProgressCallback? = null
    ): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val gitInstance = git ?: return@withContext GitOperationResult.Error("Repository not opened")
            val repo = repository ?: return@withContext GitOperationResult.Error("Repository not opened")

            val pushCommand = gitInstance.push()
                .setRemote(options.remote)
                .setForce(options.force)

            // If branch is specified, add it as a refspec
            if (options.branch != null) {
                pushCommand.add(options.branch)
            } else {
                // Push current branch by default
                val currentBranch = repo.branch
                if (currentBranch != null) {
                    pushCommand.add("refs/heads/$currentBranch:refs/heads/$currentBranch")
                }
            }

            // Set credentials if provided
            options.credentials?.let {
                pushCommand.setCredentialsProvider(createCredentialsProvider(it))
            }

            pushCommand.setProgressMonitor(JGitProgressMonitor(progressCallback))

            val results = pushCommand.call()

            // Validate push results - check if any refs were actually updated
            var pushedSuccessfully = false
            var rejectedMessage: String? = null

            for (result in results) {
                for (update in result.remoteUpdates) {
                    when (update.status) {
                        org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK,
                        org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE -> {
                            pushedSuccessfully = true
                        }
                        org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD -> {
                            rejectedMessage = "Push rejected: remote has changes you don't have locally. Pull first."
                        }
                        org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_NODELETE -> {
                            rejectedMessage = "Push rejected: cannot delete remote ref"
                        }
                        org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED -> {
                            rejectedMessage = "Push rejected: remote ref was updated by another push"
                        }
                        org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON -> {
                            rejectedMessage = "Push rejected: ${update.message ?: "unknown reason"}"
                        }
                        org.eclipse.jgit.transport.RemoteRefUpdate.Status.NON_EXISTING -> {
                            rejectedMessage = "Push failed: remote ref does not exist"
                        }
                        org.eclipse.jgit.transport.RemoteRefUpdate.Status.NOT_ATTEMPTED -> {
                            // This can happen if credentials are missing
                            if (options.credentials == null) {
                                rejectedMessage = "Push not attempted: credentials may be required"
                            }
                        }
                        else -> {
                            // Handle any other status
                        }
                    }
                }
            }

            if (rejectedMessage != null) {
                return@withContext GitOperationResult.Error(rejectedMessage)
            }

            if (!pushedSuccessfully && results.isNotEmpty()) {
                // Check for authentication issues
                val firstResult = results.firstOrNull()
                val messages = firstResult?.messages
                if (messages != null && messages.contains("authentication", ignoreCase = true)) {
                    return@withContext GitOperationResult.Error("Push failed: authentication required. Please configure Git credentials in Settings.")
                }
                return@withContext GitOperationResult.Error("Push may not have completed. Please verify your remote repository.")
            }

            GitOperationResult.Success(Unit, "Pushed successfully")
        } catch (e: org.eclipse.jgit.api.errors.TransportException) {
            // Handle transport/authentication errors specifically
            val message = when {
                e.message?.contains("not authorized", ignoreCase = true) == true ||
                e.message?.contains("authentication", ignoreCase = true) == true ||
                e.message?.contains("401", ignoreCase = true) == true -> {
                    "Push failed: authentication required. Please configure Git credentials in Settings."
                }
                e.message?.contains("not found", ignoreCase = true) == true ||
                e.message?.contains("404", ignoreCase = true) == true -> {
                    "Push failed: remote repository not found. Please verify the remote URL."
                }
                e.message?.contains("timeout", ignoreCase = true) == true -> {
                    "Push failed: connection timed out. Please check your network connection."
                }
                else -> "Failed to push: ${e.message}"
            }
            GitOperationResult.Error(message, e)
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to push: ${e.message}", e)
        }
    }

    /**
     * Pull from remote
     */
    suspend fun pull(
        options: GitPullOptions,
        progressCallback: GitProgressCallback? = null
    ): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val pullCommand = git?.pull()
                ?.setRemote(options.remote)
                ?.setRebase(options.rebase)
                ?: return@withContext GitOperationResult.Error("Repository not opened")

            options.branch?.let { pullCommand.setRemoteBranchName(it) }
            options.credentials?.let {
                pullCommand.setCredentialsProvider(createCredentialsProvider(it))
            }

            pullCommand.setProgressMonitor(JGitProgressMonitor(progressCallback))
            pullCommand.call()

            GitOperationResult.Success(Unit, "Pulled successfully")
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to pull: ${e.message}", e)
        }
    }

    /**
     * Fetch from remote
     */
    suspend fun fetch(
        options: GitFetchOptions,
        progressCallback: GitProgressCallback? = null
    ): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val fetchCommand = git?.fetch()
                ?.setRemote(options.remote)
                ?.setRemoveDeletedRefs(options.prune)
                ?: return@withContext GitOperationResult.Error("Repository not opened")

            options.credentials?.let {
                fetchCommand.setCredentialsProvider(createCredentialsProvider(it))
            }

            fetchCommand.setProgressMonitor(JGitProgressMonitor(progressCallback))
            fetchCommand.call()

            GitOperationResult.Success(Unit, "Fetched successfully")
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to fetch: ${e.message}", e)
        }
    }

    /**
     * Get commit history for the current branch
     */
    suspend fun getCommitHistory(maxCount: Int = 50): GitOperationResult<List<GitCommit>> = withContext(Dispatchers.IO) {
        try {
            val repo = repository ?: return@withContext GitOperationResult.Error("Repository not opened")
            val gitInstance = git ?: return@withContext GitOperationResult.Error("Repository not opened")

            // Get the HEAD reference for the current branch
            val head = repo.resolve("HEAD")
                ?: return@withContext GitOperationResult.Success(emptyList())

            // Use log command starting from HEAD to get commits for current branch only
            val logCommand = gitInstance.log()
                .add(head)
                .setMaxCount(maxCount)

            val commits = logCommand.call().map { it.toGitCommit() }
            GitOperationResult.Success(commits)
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to get commit history: ${e.message}", e)
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to get commit history: ${e.message}", e)
        }
    }

    /**
     * Get all branches
     */
    suspend fun getBranches(): GitOperationResult<List<GitBranch>> = withContext(Dispatchers.IO) {
        try {
            val repo = repository ?: return@withContext GitOperationResult.Error("Repository not opened")
            val gitInstance = git ?: return@withContext GitOperationResult.Error("Git not initialized")
            val currentBranch = repo.branch

            val branches = mutableListOf<GitBranch>()

            // Get all branches using ListMode.ALL to ensure we get everything
            val allRefs = gitInstance.branchList()
                .setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.ALL)
                .call()

            allRefs.forEach { ref ->
                val refName = ref.name

                when {
                    // Local branches (refs/heads/*)
                    refName.startsWith("refs/heads/") -> {
                        val branchName = refName.removePrefix("refs/heads/")
                        val isCurrent = branchName == currentBranch

                        var aheadCount = 0
                        var behindCount = 0
                        var trackingBranch: String? = null

                        try {
                            val trackingStatus = BranchTrackingStatus.of(repo, branchName)
                            if (trackingStatus != null) {
                                aheadCount = trackingStatus.aheadCount
                                behindCount = trackingStatus.behindCount
                                trackingBranch = trackingStatus.remoteTrackingBranch?.removePrefix("refs/remotes/")
                            }
                        } catch (e: Exception) {
                            // Ignore tracking status errors
                        }

                        branches.add(
                            GitBranch(
                                name = branchName,
                                isLocal = true,
                                isRemote = false,
                                isCurrent = isCurrent,
                                trackingBranch = trackingBranch,
                                lastCommitId = ref.objectId?.name,
                                aheadCount = aheadCount,
                                behindCount = behindCount
                            )
                        )
                    }
                    // Remote branches (refs/remotes/*)
                    refName.startsWith("refs/remotes/") -> {
                        val branchName = refName.removePrefix("refs/remotes/")
                        // Skip HEAD references like origin/HEAD
                        if (!branchName.endsWith("/HEAD")) {
                            branches.add(
                                GitBranch(
                                    name = branchName,
                                    isLocal = false,
                                    isRemote = true,
                                    isCurrent = false,
                                    lastCommitId = ref.objectId?.name
                                )
                            )
                        }
                    }
                }
            }

            GitOperationResult.Success(branches)
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to get branches: ${e.message}", e)
        }
    }

    /**
     * Create a new branch
     */
    suspend fun createBranch(name: String, checkout: Boolean = false): GitOperationResult<GitBranch> = withContext(Dispatchers.IO) {
        try {
            val ref = git?.branchCreate()
                ?.setName(name)
                ?.call()
                ?: return@withContext GitOperationResult.Error("Repository not opened")

            if (checkout) {
                git?.checkout()?.setName(name)?.call()
            }

            GitOperationResult.Success(
                GitBranch(
                    name = name,
                    isLocal = true,
                    isCurrent = checkout,
                    lastCommitId = ref.objectId?.name
                ),
                "Branch created: $name"
            )
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to create branch: ${e.message}", e)
        }
    }

    /**
     * Checkout a branch
     */
    suspend fun checkout(branchName: String): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            git?.checkout()?.setName(branchName)?.call()
                ?: return@withContext GitOperationResult.Error("Repository not opened")
            GitOperationResult.Success(Unit, "Checked out: $branchName")
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to checkout: ${e.message}", e)
        }
    }

    /**
     * Delete a branch
     */
    suspend fun deleteBranch(name: String, force: Boolean = false): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            git?.branchDelete()
                ?.setBranchNames(name)
                ?.setForce(force)
                ?.call()
                ?: return@withContext GitOperationResult.Error("Repository not opened")
            GitOperationResult.Success(Unit, "Branch deleted: $name")
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to delete branch: ${e.message}", e)
        }
    }

    /**
     * Merge a branch
     */
    suspend fun merge(branchName: String): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val repo = repository ?: return@withContext GitOperationResult.Error("Repository not opened")
            val branchRef = repo.findRef(branchName)
                ?: return@withContext GitOperationResult.Error("Branch not found: $branchName")

            val result = git?.merge()?.include(branchRef)?.call()
                ?: return@withContext GitOperationResult.Error("Merge failed")

            if (result.mergeStatus.isSuccessful) {
                GitOperationResult.Success(Unit, "Merged successfully")
            } else {
                GitOperationResult.Error("Merge failed: ${result.mergeStatus}")
            }
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to merge: ${e.message}", e)
        }
    }

    /**
     * Get remotes
     */
    suspend fun getRemotes(): GitOperationResult<List<GitRemote>> = withContext(Dispatchers.IO) {
        try {
            val repo = repository ?: return@withContext GitOperationResult.Error("Repository not opened")
            val remotes = repo.remoteNames.map { name ->
                val config = repo.config
                GitRemote(
                    name = name,
                    fetchUrl = config.getString("remote", name, "url") ?: "",
                    pushUrl = config.getString("remote", name, "pushurl")
                        ?: config.getString("remote", name, "url") ?: ""
                )
            }
            GitOperationResult.Success(remotes)
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to get remotes: ${e.message}", e)
        }
    }

    /**
     * Add a remote
     */
    suspend fun addRemote(name: String, url: String): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            git?.remoteAdd()
                ?.setName(name)
                ?.setUri(URIish(url))
                ?.call()
                ?: return@withContext GitOperationResult.Error("Repository not opened")
            GitOperationResult.Success(Unit, "Remote added: $name")
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to add remote: ${e.message}", e)
        }
    }

    /**
     * Remove a remote
     */
    suspend fun removeRemote(name: String): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            git?.remoteRemove()?.setRemoteName(name)?.call()
                ?: return@withContext GitOperationResult.Error("Repository not opened")
            GitOperationResult.Success(Unit, "Remote removed: $name")
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to remove remote: ${e.message}", e)
        }
    }

    /**
     * Get diff for a file
     */
    suspend fun getFileDiff(path: String, staged: Boolean = false): GitOperationResult<GitFileDiff> = withContext(Dispatchers.IO) {
        try {
            val repo = repository ?: return@withContext GitOperationResult.Error("Repository not opened")

            val outputStream = ByteArrayOutputStream()
            DiffFormatter(outputStream).use { formatter ->
                formatter.setRepository(repo)

                val head = repo.resolve("HEAD^{tree}")
                val reader = repo.newObjectReader()

                val oldTreeIter = if (head != null) {
                    CanonicalTreeParser().apply {
                        reset(reader, head)
                    }
                } else {
                    EmptyTreeIterator()
                }

                val newTreeIter = if (staged) {
                    // Compare HEAD with index
                    CanonicalTreeParser().apply {
                        val indexTreeId = repo.resolve("HEAD^{tree}")
                        if (indexTreeId != null) {
                            reset(reader, indexTreeId)
                        }
                    }
                } else {
                    // Working directory - use null for comparison
                    null
                }

                val diffs = git?.diff()
                    ?.setOldTree(oldTreeIter)
                    ?.setNewTree(newTreeIter)
                    ?.setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(path))
                    ?.call()
                    ?: return@withContext GitOperationResult.Error("Failed to get diff")

                if (diffs.isEmpty()) {
                    return@withContext GitOperationResult.Success(
                        GitFileDiff(
                            path = path,
                            status = GitFileStatus.UNCHANGED,
                            hunks = emptyList()
                        )
                    )
                }

                val diff = diffs.first()
                formatter.format(diff)
                val diffOutput = outputStream.toString()

                // Parse the diff output
                val hunks = parseDiffHunks(diffOutput)

                GitOperationResult.Success(
                    GitFileDiff(
                        path = path,
                        oldPath = diff.oldPath.takeIf { it != diff.newPath },
                        status = diff.changeType.toGitFileStatus(),
                        hunks = hunks,
                        isBinary = diff.oldMode?.bits == 0 || diff.newMode?.bits == 0
                    )
                )
            }
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to get diff: ${e.message}", e)
        }
    }

    /**
     * Reset to a specific commit
     */
    suspend fun reset(commitId: String, mode: ResetCommand.ResetType = ResetCommand.ResetType.MIXED): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            git?.reset()
                ?.setMode(mode)
                ?.setRef(commitId)
                ?.call()
                ?: return@withContext GitOperationResult.Error("Repository not opened")
            GitOperationResult.Success(Unit, "Reset to $commitId")
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to reset: ${e.message}", e)
        }
    }

    /**
     * Stash changes
     */
    suspend fun stash(message: String? = null): GitOperationResult<String> = withContext(Dispatchers.IO) {
        try {
            val stashCommand = git?.stashCreate()
                ?: return@withContext GitOperationResult.Error("Repository not opened")

            message?.let { stashCommand.setWorkingDirectoryMessage(it) }
            val stashRef = stashCommand.call()

            if (stashRef != null) {
                GitOperationResult.Success(stashRef.name, "Changes stashed")
            } else {
                GitOperationResult.Error("No changes to stash")
            }
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to stash: ${e.message}", e)
        }
    }

    /**
     * Apply stash
     */
    suspend fun stashApply(stashRef: String? = null, drop: Boolean = false): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val applyCommand = git?.stashApply()
                ?: return@withContext GitOperationResult.Error("Repository not opened")

            stashRef?.let { applyCommand.setStashRef(it) }
            applyCommand.call()

            if (drop) {
                git?.stashDrop()?.call()
            }

            GitOperationResult.Success(Unit, "Stash applied")
        } catch (e: GitAPIException) {
            GitOperationResult.Error("Failed to apply stash: ${e.message}", e)
        }
    }

    /**
     * Close the repository
     */
    fun close() {
        git?.close()
        repository?.close()
        git = null
        repository = null
    }

    // Helper functions

    /**
     * Handle lock file errors by cleaning up stale lock files
     * This can happen when a previous git operation was interrupted
     */
    private fun handleLockFileError() {
        try {
            val gitDir = File(workingDirectory, ".git")
            val lockFiles = listOf(
                File(gitDir, "index.lock"),
                File(gitDir, "HEAD.lock"),
                File(gitDir, "config.lock")
            )

            lockFiles.forEach { lockFile ->
                if (lockFile.exists()) {
                    lockFile.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors - the main error will be reported
        }
    }

    private fun createCredentialsProvider(credentials: GitCredentials): CredentialsProvider {
        return UsernamePasswordCredentialsProvider(
            credentials.username,
            credentials.password
        )
    }

    private fun RevCommit.toGitCommit(): GitCommit {
        return GitCommit(
            id = name,
            shortId = abbreviate(7).name(),
            message = shortMessage,
            fullMessage = fullMessage,
            authorName = authorIdent.name,
            authorEmail = authorIdent.emailAddress,
            timestamp = commitTime.toLong() * 1000,
            parentIds = parents.map { it.name }
        )
    }

    private fun DiffEntry.ChangeType.toGitFileStatus(): GitFileStatus {
        return when (this) {
            DiffEntry.ChangeType.ADD -> GitFileStatus.ADDED
            DiffEntry.ChangeType.MODIFY -> GitFileStatus.MODIFIED
            DiffEntry.ChangeType.DELETE -> GitFileStatus.DELETED
            DiffEntry.ChangeType.RENAME -> GitFileStatus.RENAMED
            DiffEntry.ChangeType.COPY -> GitFileStatus.COPIED
        }
    }

    private fun parseDiffHunks(diffOutput: String): List<GitDiffHunk> {
        val hunks = mutableListOf<GitDiffHunk>()
        val lines = diffOutput.lines()

        var currentHunk: MutableList<GitDiffLine>? = null
        var oldStart = 0
        var oldCount = 0
        var newStart = 0
        var newCount = 0
        var oldLine = 0
        var newLine = 0

        val hunkHeaderRegex = Regex("""@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*""")

        for (line in lines) {
            val headerMatch = hunkHeaderRegex.matchEntire(line)
            if (headerMatch != null) {
                // Save previous hunk
                currentHunk?.let {
                    hunks.add(GitDiffHunk(oldStart, oldCount, newStart, newCount, it))
                }

                // Parse new hunk header
                oldStart = headerMatch.groupValues[1].toInt()
                oldCount = headerMatch.groupValues[2].toIntOrNull() ?: 1
                newStart = headerMatch.groupValues[3].toInt()
                newCount = headerMatch.groupValues[4].toIntOrNull() ?: 1
                oldLine = oldStart
                newLine = newStart

                currentHunk = mutableListOf()
                currentHunk.add(GitDiffLine(DiffLineType.HEADER, line))
            } else if (currentHunk != null) {
                when {
                    line.startsWith("+") -> {
                        currentHunk.add(GitDiffLine(DiffLineType.ADDITION, line.drop(1), newLineNumber = newLine))
                        newLine++
                    }
                    line.startsWith("-") -> {
                        currentHunk.add(GitDiffLine(DiffLineType.DELETION, line.drop(1), oldLineNumber = oldLine))
                        oldLine++
                    }
                    line.startsWith(" ") -> {
                        currentHunk.add(GitDiffLine(DiffLineType.CONTEXT, line.drop(1), oldLine, newLine))
                        oldLine++
                        newLine++
                    }
                }
            }
        }

        // Add last hunk
        currentHunk?.let {
            hunks.add(GitDiffHunk(oldStart, oldCount, newStart, newCount, it))
        }

        return hunks
    }
}

/**
 * JGit progress monitor adapter
 */
private class JGitProgressMonitor(
    private val callback: GitProgressCallback?
) : org.eclipse.jgit.lib.ProgressMonitor {
    private var totalWork = 0
    private var completed = 0
    private var taskName = ""

    override fun start(totalTasks: Int) {
        // Total tasks starting
    }

    override fun beginTask(title: String?, totalWork: Int) {
        this.taskName = title ?: ""
        this.totalWork = totalWork
        this.completed = 0
        callback?.invoke(0, totalWork, taskName)
    }

    override fun update(completed: Int) {
        this.completed += completed
        callback?.invoke(this.completed, totalWork, taskName)
    }

    override fun endTask() {
        callback?.invoke(totalWork, totalWork, taskName)
    }

    override fun isCancelled(): Boolean = false
}
