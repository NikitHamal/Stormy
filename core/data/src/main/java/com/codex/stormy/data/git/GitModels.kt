package com.codex.stormy.data.git

import kotlinx.serialization.Serializable

/**
 * Represents the status of a Git repository
 */
data class GitRepositoryStatus(
    val isGitRepo: Boolean = false,
    val currentBranch: String = "",
    val hasRemote: Boolean = false,
    val remoteUrl: String? = null,
    val isClean: Boolean = true,
    val hasUncommittedChanges: Boolean = false,
    val hasUnstagedChanges: Boolean = false,
    val hasStagedChanges: Boolean = false,
    val aheadCount: Int = 0,
    val behindCount: Int = 0
)

/**
 * Represents a file's Git status
 */
enum class GitFileStatus {
    UNTRACKED,
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    IGNORED,
    CONFLICTING,
    UNCHANGED
}

/**
 * Represents a changed file in the working directory
 */
data class GitChangedFile(
    val path: String,
    val status: GitFileStatus,
    val isStaged: Boolean = false,
    val oldPath: String? = null // For renames
)

/**
 * Represents a Git commit
 */
data class GitCommit(
    val id: String,
    val shortId: String,
    val message: String,
    val fullMessage: String,
    val authorName: String,
    val authorEmail: String,
    val timestamp: Long,
    val parentIds: List<String> = emptyList()
)

/**
 * Represents a Git branch
 */
data class GitBranch(
    val name: String,
    val isLocal: Boolean = true,
    val isRemote: Boolean = false,
    val isCurrent: Boolean = false,
    val trackingBranch: String? = null,
    val lastCommitId: String? = null,
    val aheadCount: Int = 0,
    val behindCount: Int = 0
)

/**
 * Represents a Git remote
 */
data class GitRemote(
    val name: String,
    val fetchUrl: String,
    val pushUrl: String
)

/**
 * Represents Git credentials
 */
@Serializable
data class GitCredentials(
    val username: String = "",
    val password: String = "", // Can be password or personal access token
    val sshKeyPath: String? = null,
    val sshPassphrase: String? = null
)

/**
 * Result of a Git operation
 */
sealed class GitOperationResult<out T> {
    data class Success<T>(val data: T, val message: String = "") : GitOperationResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : GitOperationResult<Nothing>()
    data object InProgress : GitOperationResult<Nothing>()
}

/**
 * Progress callback for long-running Git operations
 */
typealias GitProgressCallback = (current: Int, total: Int, message: String) -> Unit

/**
 * Clone options
 */
data class GitCloneOptions(
    val url: String,
    val directory: String,
    val branch: String? = null,
    val depth: Int? = null, // For shallow clones
    val credentials: GitCredentials? = null
)

/**
 * Commit options
 */
data class GitCommitOptions(
    val message: String,
    val authorName: String? = null,
    val authorEmail: String? = null,
    val amend: Boolean = false,
    val allowEmpty: Boolean = false
)

/**
 * Push options
 */
data class GitPushOptions(
    val remote: String = "origin",
    val branch: String? = null,
    val setUpstream: Boolean = false,
    val force: Boolean = false,
    val credentials: GitCredentials? = null
)

/**
 * Pull options
 */
data class GitPullOptions(
    val remote: String = "origin",
    val branch: String? = null,
    val rebase: Boolean = false,
    val credentials: GitCredentials? = null
)

/**
 * Fetch options
 */
data class GitFetchOptions(
    val remote: String = "origin",
    val prune: Boolean = true,
    val credentials: GitCredentials? = null
)

/**
 * Diff hunk
 */
data class GitDiffHunk(
    val oldStartLine: Int,
    val oldLineCount: Int,
    val newStartLine: Int,
    val newLineCount: Int,
    val lines: List<GitDiffLine>
)

/**
 * Single line in a diff
 */
data class GitDiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null
)

/**
 * Type of diff line
 */
enum class DiffLineType {
    CONTEXT,
    ADDITION,
    DELETION,
    HEADER
}

/**
 * File diff
 */
data class GitFileDiff(
    val path: String,
    val oldPath: String? = null,
    val status: GitFileStatus,
    val hunks: List<GitDiffHunk>,
    val isBinary: Boolean = false
)
