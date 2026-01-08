package com.codex.stormy.utils

import kotlinx.serialization.Serializable

/**
 * Comprehensive error types for the CodeX application.
 * Provides structured error handling with proper categorization,
 * user-friendly messages, and recovery suggestions.
 */
sealed class AppError(
    override val message: String,
    override val cause: Throwable? = null,
    val errorCode: String,
    val userMessage: String,
    val recoverySuggestion: String? = null,
    val isRetryable: Boolean = false,
    val category: ErrorCategory = ErrorCategory.UNKNOWN
) : Exception(message, cause) {

    /**
     * Error categories for grouping and metrics.
     */
    enum class ErrorCategory {
        NETWORK,
        AI,
        FILE,
        DATABASE,
        GIT,
        VALIDATION,
        PERMISSION,
        RESOURCE,
        UNKNOWN
    }

    // ==================== Network Errors ====================

    /**
     * Network-related errors.
     */
    sealed class NetworkError(
        message: String,
        cause: Throwable? = null,
        errorCode: String,
        userMessage: String,
        recoverySuggestion: String? = null,
        isRetryable: Boolean = true
    ) : AppError(message, cause, errorCode, userMessage, recoverySuggestion, isRetryable, ErrorCategory.NETWORK) {

        class NoInternetConnection(cause: Throwable? = null) : NetworkError(
            message = "No internet connection available",
            cause = cause,
            errorCode = "NET_001",
            userMessage = "No internet connection",
            recoverySuggestion = "Please check your network connection and try again.",
            isRetryable = true
        )

        class ConnectionTimeout(timeoutMs: Long, cause: Throwable? = null) : NetworkError(
            message = "Connection timed out after ${timeoutMs}ms",
            cause = cause,
            errorCode = "NET_002",
            userMessage = "Connection timed out",
            recoverySuggestion = "The server is taking too long to respond. Please try again later.",
            isRetryable = true
        )

        class ServerError(statusCode: Int, body: String? = null, cause: Throwable? = null) : NetworkError(
            message = "Server returned error $statusCode: $body",
            cause = cause,
            errorCode = "NET_003",
            userMessage = "Server error ($statusCode)",
            recoverySuggestion = "The server encountered an error. Please try again later.",
            isRetryable = statusCode in 500..599
        )

        class RateLimited(retryAfterMs: Long? = null, cause: Throwable? = null) : NetworkError(
            message = "Rate limited. Retry after ${retryAfterMs ?: "unknown"}ms",
            cause = cause,
            errorCode = "NET_004",
            userMessage = "Too many requests",
            recoverySuggestion = retryAfterMs?.let {
                "Please wait ${it / 1000} seconds before trying again."
            } ?: "Please wait a moment before trying again.",
            isRetryable = true
        )

        class SSLError(cause: Throwable? = null) : NetworkError(
            message = "SSL/TLS connection error: ${cause?.message}",
            cause = cause,
            errorCode = "NET_005",
            userMessage = "Secure connection failed",
            recoverySuggestion = "There's a problem with the secure connection. Please check your network.",
            isRetryable = false
        )

        class UnknownHost(host: String, cause: Throwable? = null) : NetworkError(
            message = "Unknown host: $host",
            cause = cause,
            errorCode = "NET_006",
            userMessage = "Cannot reach server",
            recoverySuggestion = "Unable to connect to the server. Please check your connection.",
            isRetryable = true
        )
    }

    // ==================== AI Errors ====================

    /**
     * AI/LLM-related errors.
     */
    sealed class AiError(
        message: String,
        cause: Throwable? = null,
        errorCode: String,
        userMessage: String,
        recoverySuggestion: String? = null,
        isRetryable: Boolean = false
    ) : AppError(message, cause, errorCode, userMessage, recoverySuggestion, isRetryable, ErrorCategory.AI) {

        class StreamingError(details: String, cause: Throwable? = null) : AiError(
            message = "Streaming error: $details",
            cause = cause,
            errorCode = "AI_001",
            userMessage = "Response streaming failed",
            recoverySuggestion = "There was a problem receiving the AI response. Please try again.",
            isRetryable = true
        )

        class ContextLimitExceeded(currentTokens: Int, maxTokens: Int) : AiError(
            message = "Context limit exceeded: $currentTokens tokens (max: $maxTokens)",
            errorCode = "AI_002",
            userMessage = "Context limit exceeded",
            recoverySuggestion = "The conversation is too long. Please start a new chat or clear history.",
            isRetryable = false
        )

        class InvalidResponse(details: String, cause: Throwable? = null) : AiError(
            message = "Invalid AI response: $details",
            cause = cause,
            errorCode = "AI_003",
            userMessage = "Invalid response from AI",
            recoverySuggestion = "The AI returned an unexpected response. Please try rephrasing your request.",
            isRetryable = true
        )

        class ToolExecutionFailed(toolName: String, details: String, cause: Throwable? = null) : AiError(
            message = "Tool '$toolName' execution failed: $details",
            cause = cause,
            errorCode = "AI_004",
            userMessage = "Action failed: $toolName",
            recoverySuggestion = "The AI couldn't complete this action. You may need to do it manually.",
            isRetryable = false
        )

        class AgentIterationLimitReached(iterations: Int, maxIterations: Int) : AiError(
            message = "Agent reached iteration limit: $iterations/$maxIterations",
            errorCode = "AI_005",
            userMessage = "Task too complex",
            recoverySuggestion = "This task requires too many steps. Try breaking it into smaller parts.",
            isRetryable = false
        )

        class ModelNotAvailable(modelId: String, cause: Throwable? = null) : AiError(
            message = "Model not available: $modelId",
            cause = cause,
            errorCode = "AI_006",
            userMessage = "AI model unavailable",
            recoverySuggestion = "This AI model is currently unavailable. Try selecting a different model.",
            isRetryable = true
        )

        class ParseError(details: String, cause: Throwable? = null) : AiError(
            message = "Failed to parse AI response: $details",
            cause = cause,
            errorCode = "AI_007",
            userMessage = "Response parsing error",
            recoverySuggestion = "Couldn't understand the AI response. Please try again.",
            isRetryable = true
        )

        class CircuitOpen(remainingMs: Long) : AiError(
            message = "AI service circuit breaker is open. Retry in ${remainingMs}ms",
            errorCode = "AI_008",
            userMessage = "AI service temporarily unavailable",
            recoverySuggestion = "The AI service is recovering. Please wait ${remainingMs / 1000} seconds.",
            isRetryable = true
        )
    }

    // ==================== File Errors ====================

    /**
     * File operation errors.
     */
    sealed class FileError(
        message: String,
        cause: Throwable? = null,
        errorCode: String,
        userMessage: String,
        recoverySuggestion: String? = null,
        isRetryable: Boolean = false
    ) : AppError(message, cause, errorCode, userMessage, recoverySuggestion, isRetryable, ErrorCategory.FILE) {

        class NotFound(path: String) : FileError(
            message = "File not found: $path",
            errorCode = "FILE_001",
            userMessage = "File not found",
            recoverySuggestion = "The file may have been moved or deleted. Refresh the file tree.",
            isRetryable = false
        )

        class ReadError(path: String, cause: Throwable? = null) : FileError(
            message = "Cannot read file: $path - ${cause?.message}",
            cause = cause,
            errorCode = "FILE_002",
            userMessage = "Cannot read file",
            recoverySuggestion = "Unable to read this file. It may be in use by another app.",
            isRetryable = true
        )

        class WriteError(path: String, cause: Throwable? = null) : FileError(
            message = "Cannot write file: $path - ${cause?.message}",
            cause = cause,
            errorCode = "FILE_003",
            userMessage = "Cannot save file",
            recoverySuggestion = "Unable to save this file. Check storage permissions and space.",
            isRetryable = true
        )

        class FileTooLarge(path: String, sizeBytes: Long, maxBytes: Long) : FileError(
            message = "File too large: $path ($sizeBytes bytes, max $maxBytes)",
            errorCode = "FILE_004",
            userMessage = "File is too large",
            recoverySuggestion = "This file exceeds the maximum size of ${maxBytes / 1024 / 1024}MB.",
            isRetryable = false
        )

        class InsufficientStorage(requiredBytes: Long, availableBytes: Long) : FileError(
            message = "Insufficient storage: need ${requiredBytes}B, have ${availableBytes}B",
            errorCode = "FILE_005",
            userMessage = "Not enough storage space",
            recoverySuggestion = "Free up ${(requiredBytes - availableBytes) / 1024 / 1024}MB to continue.",
            isRetryable = false
        )

        class InvalidPath(path: String, reason: String) : FileError(
            message = "Invalid path: $path - $reason",
            errorCode = "FILE_006",
            userMessage = "Invalid file path",
            recoverySuggestion = "The file path contains invalid characters. Please rename the file.",
            isRetryable = false
        )

        class BinaryFile(path: String) : FileError(
            message = "Cannot edit binary file: $path",
            errorCode = "FILE_007",
            userMessage = "Cannot edit binary file",
            recoverySuggestion = "This is a binary file and cannot be edited as text.",
            isRetryable = false
        )

        class ProjectLimitExceeded(currentFiles: Int, maxFiles: Int) : FileError(
            message = "Project file limit exceeded: $currentFiles (max $maxFiles)",
            errorCode = "FILE_008",
            userMessage = "Too many files",
            recoverySuggestion = "This project has too many files. Remove some files to continue.",
            isRetryable = false
        )
    }

    // ==================== Database Errors ====================

    /**
     * Database-related errors.
     */
    sealed class DatabaseError(
        message: String,
        cause: Throwable? = null,
        errorCode: String,
        userMessage: String,
        recoverySuggestion: String? = null,
        isRetryable: Boolean = false
    ) : AppError(message, cause, errorCode, userMessage, recoverySuggestion, isRetryable, ErrorCategory.DATABASE) {

        class CorruptionError(cause: Throwable? = null) : DatabaseError(
            message = "Database corruption detected: ${cause?.message}",
            cause = cause,
            errorCode = "DB_001",
            userMessage = "Database error",
            recoverySuggestion = "The app data may be corrupted. Try clearing app data in settings.",
            isRetryable = false
        )

        class MigrationFailed(version: Int, cause: Throwable? = null) : DatabaseError(
            message = "Database migration to version $version failed: ${cause?.message}",
            cause = cause,
            errorCode = "DB_002",
            userMessage = "Update error",
            recoverySuggestion = "Failed to update app data. Please reinstall the app.",
            isRetryable = false
        )

        class QueryFailed(query: String, cause: Throwable? = null) : DatabaseError(
            message = "Database query failed: ${cause?.message}",
            cause = cause,
            errorCode = "DB_003",
            userMessage = "Data access error",
            recoverySuggestion = "Couldn't access app data. Please restart the app.",
            isRetryable = true
        )

        class StorageFull(cause: Throwable? = null) : DatabaseError(
            message = "Database storage full: ${cause?.message}",
            cause = cause,
            errorCode = "DB_004",
            userMessage = "Storage full",
            recoverySuggestion = "App data storage is full. Clear chat history or delete old projects.",
            isRetryable = false
        )
    }

    // ==================== Git Errors ====================

    /**
     * Git operation errors.
     */
    sealed class GitError(
        message: String,
        cause: Throwable? = null,
        errorCode: String,
        userMessage: String,
        recoverySuggestion: String? = null,
        isRetryable: Boolean = false
    ) : AppError(message, cause, errorCode, userMessage, recoverySuggestion, isRetryable, ErrorCategory.GIT) {

        class NotARepository(path: String) : GitError(
            message = "Not a git repository: $path",
            errorCode = "GIT_001",
            userMessage = "Not a Git repository",
            recoverySuggestion = "Initialize a Git repository first using 'Init Repo'.",
            isRetryable = false
        )

        class CloneFailed(url: String, cause: Throwable? = null) : GitError(
            message = "Clone failed for $url: ${cause?.message}",
            cause = cause,
            errorCode = "GIT_002",
            userMessage = "Clone failed",
            recoverySuggestion = "Could not clone the repository. Check the URL and your credentials.",
            isRetryable = true
        )

        class PushFailed(remote: String, cause: Throwable? = null) : GitError(
            message = "Push to $remote failed: ${cause?.message}",
            cause = cause,
            errorCode = "GIT_003",
            userMessage = "Push failed",
            recoverySuggestion = "Could not push changes. Check your credentials and network.",
            isRetryable = true
        )

        class PullFailed(remote: String, cause: Throwable? = null) : GitError(
            message = "Pull from $remote failed: ${cause?.message}",
            cause = cause,
            errorCode = "GIT_004",
            userMessage = "Pull failed",
            recoverySuggestion = "Could not pull changes. Check your network connection.",
            isRetryable = true
        )

        class MergeConflict(files: List<String>) : GitError(
            message = "Merge conflict in ${files.size} file(s): ${files.take(3).joinToString()}",
            errorCode = "GIT_005",
            userMessage = "Merge conflict",
            recoverySuggestion = "There are conflicts that need to be resolved manually.",
            isRetryable = false
        )

        class AuthenticationFailed(remote: String, cause: Throwable? = null) : GitError(
            message = "Authentication failed for $remote: ${cause?.message}",
            cause = cause,
            errorCode = "GIT_006",
            userMessage = "Authentication failed",
            recoverySuggestion = "Your credentials are invalid. Update them in Git settings.",
            isRetryable = false
        )

        class CommitFailed(reason: String, cause: Throwable? = null) : GitError(
            message = "Commit failed: $reason",
            cause = cause,
            errorCode = "GIT_007",
            userMessage = "Commit failed",
            recoverySuggestion = "Could not create commit. Check that you have staged changes.",
            isRetryable = false
        )
    }

    // ==================== Validation Errors ====================

    /**
     * Input validation errors.
     */
    sealed class ValidationError(
        message: String,
        errorCode: String,
        userMessage: String,
        recoverySuggestion: String? = null
    ) : AppError(message, null, errorCode, userMessage, recoverySuggestion, false, ErrorCategory.VALIDATION) {

        class InvalidInput(field: String, reason: String) : ValidationError(
            message = "Invalid input for $field: $reason",
            errorCode = "VAL_001",
            userMessage = "Invalid $field",
            recoverySuggestion = reason
        )

        class RequiredFieldMissing(field: String) : ValidationError(
            message = "Required field missing: $field",
            errorCode = "VAL_002",
            userMessage = "$field is required",
            recoverySuggestion = "Please provide a value for $field."
        )

        class ValueTooLong(field: String, maxLength: Int) : ValidationError(
            message = "$field exceeds maximum length of $maxLength",
            errorCode = "VAL_003",
            userMessage = "$field is too long",
            recoverySuggestion = "Please shorten $field to $maxLength characters or less."
        )

        class InvalidFormat(field: String, expectedFormat: String) : ValidationError(
            message = "$field has invalid format. Expected: $expectedFormat",
            errorCode = "VAL_004",
            userMessage = "Invalid format for $field",
            recoverySuggestion = "Expected format: $expectedFormat"
        )
    }

    // ==================== Permission Errors ====================

    /**
     * Permission-related errors.
     */
    sealed class PermissionError(
        message: String,
        errorCode: String,
        userMessage: String,
        recoverySuggestion: String? = null
    ) : AppError(message, null, errorCode, userMessage, recoverySuggestion, false, ErrorCategory.PERMISSION) {

        class StoragePermissionDenied : PermissionError(
            message = "Storage permission denied",
            errorCode = "PERM_001",
            userMessage = "Storage permission required",
            recoverySuggestion = "Please grant storage permission in app settings."
        )

        class NetworkPermissionDenied : PermissionError(
            message = "Network permission denied",
            errorCode = "PERM_002",
            userMessage = "Network permission required",
            recoverySuggestion = "Please grant network permission in app settings."
        )
    }

    // ==================== Resource Errors ====================

    /**
     * Resource exhaustion errors.
     */
    sealed class ResourceError(
        message: String,
        errorCode: String,
        userMessage: String,
        recoverySuggestion: String? = null,
        isRetryable: Boolean = false
    ) : AppError(message, null, errorCode, userMessage, recoverySuggestion, isRetryable, ErrorCategory.RESOURCE) {

        class OutOfMemory(details: String) : ResourceError(
            message = "Out of memory: $details",
            errorCode = "RES_001",
            userMessage = "Out of memory",
            recoverySuggestion = "Close other apps and try again.",
            isRetryable = true
        )

        class TooManyOpenFiles(count: Int) : ResourceError(
            message = "Too many open files: $count",
            errorCode = "RES_002",
            userMessage = "Too many open files",
            recoverySuggestion = "Close some tabs and try again.",
            isRetryable = true
        )

        class CpuOverload(details: String) : ResourceError(
            message = "CPU overload: $details",
            errorCode = "RES_003",
            userMessage = "App is overloaded",
            recoverySuggestion = "Wait a moment for the app to catch up.",
            isRetryable = true
        )
    }

    /**
     * Convert to a user-presentable error info.
     */
    fun toErrorInfo(): ErrorInfo = ErrorInfo(
        code = errorCode,
        message = userMessage,
        suggestion = recoverySuggestion,
        isRetryable = isRetryable,
        category = category.name,
        technicalDetails = message
    )

    override fun toString(): String = "[$errorCode] $message"
}

/**
 * User-presentable error information.
 */
@Serializable
data class ErrorInfo(
    val code: String,
    val message: String,
    val suggestion: String?,
    val isRetryable: Boolean,
    val category: String,
    val technicalDetails: String? = null
)

/**
 * Extension function to convert exceptions to AppErrors.
 */
fun Throwable.toAppError(): AppError {
    return when (this) {
        is AppError -> this
        is java.net.UnknownHostException -> AppError.NetworkError.UnknownHost(
            message ?: "unknown",
            this
        )
        is java.net.SocketTimeoutException -> AppError.NetworkError.ConnectionTimeout(
            30000,
            this
        )
        is java.io.IOException -> AppError.NetworkError.NoInternetConnection(this)
        is OutOfMemoryError -> AppError.ResourceError.OutOfMemory(message ?: "")
        else -> object : AppError(
            message = this.message ?: "Unknown error",
            cause = this,
            errorCode = "UNK_001",
            userMessage = "An unexpected error occurred",
            recoverySuggestion = "Please try again. If the problem persists, restart the app.",
            isRetryable = true,
            category = ErrorCategory.UNKNOWN
        ) {}
    }
}
