package com.codex.stormy.utils

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException

/**
 * Centralized edge case handling utilities.
 *
 * This class provides comprehensive handling for various edge cases that can occur
 * in the application, including:
 * - File system edge cases (permissions, space, encoding)
 * - Memory and resource limits
 * - Network edge cases
 * - AI/streaming edge cases
 * - Input validation edge cases
 *
 * Design principles:
 * - Fail safely with informative error messages
 * - Provide recovery suggestions where possible
 * - Log edge cases for debugging without exposing sensitive data
 * - Prevent crashes from unhandled edge cases
 */
object EdgeCaseHandler {

    private const val TAG = "EdgeCaseHandler"

    // ==================== File System Edge Cases ====================

    /**
     * Result of a file operation edge case check.
     */
    data class FileCheckResult(
        val canProceed: Boolean,
        val warning: String? = null,
        val error: String? = null,
        val suggestion: String? = null
    )

    /**
     * Check if a file operation is safe to perform.
     */
    fun checkFileOperation(
        file: File,
        operation: FileOperation,
        requiredBytes: Long = 0
    ): FileCheckResult {
        return try {
            when (operation) {
                FileOperation.READ -> checkReadOperation(file)
                FileOperation.WRITE -> checkWriteOperation(file, requiredBytes)
                FileOperation.DELETE -> checkDeleteOperation(file)
                FileOperation.CREATE -> checkCreateOperation(file)
            }
        } catch (e: SecurityException) {
            FileCheckResult(
                canProceed = false,
                error = "Permission denied: ${e.message}",
                suggestion = "Check app permissions in device settings"
            )
        } catch (e: Exception) {
            FileCheckResult(
                canProceed = false,
                error = "Unexpected error: ${e.message}",
                suggestion = "Try again or contact support"
            )
        }
    }

    private fun checkReadOperation(file: File): FileCheckResult {
        if (!file.exists()) {
            return FileCheckResult(
                canProceed = false,
                error = "File not found: ${file.name}",
                suggestion = "Check if the file was moved or deleted"
            )
        }

        if (!file.canRead()) {
            return FileCheckResult(
                canProceed = false,
                error = "Cannot read file: ${file.name}",
                suggestion = "Check file permissions"
            )
        }

        if (file.isDirectory) {
            return FileCheckResult(
                canProceed = false,
                error = "Cannot read directory as file",
                suggestion = "Use list_files for directories"
            )
        }

        val sizeBytes = file.length()

        // Check for very large files
        if (sizeBytes > Constants.FileSize.MAX_EDIT_SIZE_BYTES) {
            return FileCheckResult(
                canProceed = false,
                error = "File too large to read (${formatBytes(sizeBytes)})",
                suggestion = "Maximum file size is ${formatBytes(Constants.FileSize.MAX_EDIT_SIZE_BYTES)}"
            )
        }

        // Warn about large files
        if (sizeBytes > Constants.FileSize.LARGE_FILE_THRESHOLD_BYTES) {
            return FileCheckResult(
                canProceed = true,
                warning = "Large file (${formatBytes(sizeBytes)}) - may be slow"
            )
        }

        // Check if file is binary
        if (Constants.isBinaryFile(file.name)) {
            return FileCheckResult(
                canProceed = false,
                error = "Cannot read binary file as text",
                suggestion = "Use asset manager for binary files"
            )
        }

        return FileCheckResult(canProceed = true)
    }

    private fun checkWriteOperation(file: File, requiredBytes: Long): FileCheckResult {
        // Check parent directory
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            return FileCheckResult(
                canProceed = false,
                error = "Parent directory does not exist",
                suggestion = "Create the directory first"
            )
        }

        if (parent != null && !parent.canWrite()) {
            return FileCheckResult(
                canProceed = false,
                error = "Cannot write to directory: ${parent.name}",
                suggestion = "Check directory permissions"
            )
        }

        // Check file if it exists
        if (file.exists()) {
            if (!file.canWrite()) {
                return FileCheckResult(
                    canProceed = false,
                    error = "File is read-only",
                    suggestion = "Change file permissions or save with a different name"
                )
            }

            if (file.isDirectory) {
                return FileCheckResult(
                    canProceed = false,
                    error = "Cannot write to a directory",
                    suggestion = "Specify a file path, not a directory"
                )
            }
        }

        // Check storage space
        if (requiredBytes > 0 && parent != null) {
            val stat = StatFs(parent.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong

            if (availableBytes < requiredBytes + Constants.FileSize.MIN_FREE_SPACE_BYTES) {
                return FileCheckResult(
                    canProceed = false,
                    error = "Insufficient storage space",
                    suggestion = "Free up ${formatBytes(requiredBytes - availableBytes + Constants.FileSize.MIN_FREE_SPACE_BYTES)}"
                )
            }

            if (availableBytes < requiredBytes * 2) {
                return FileCheckResult(
                    canProceed = true,
                    warning = "Low storage space (${formatBytes(availableBytes)} available)"
                )
            }
        }

        // Check content size
        if (requiredBytes > Constants.FileSize.MAX_EDIT_SIZE_BYTES) {
            return FileCheckResult(
                canProceed = false,
                error = "Content too large to write (${formatBytes(requiredBytes)})",
                suggestion = "Split content into multiple files"
            )
        }

        return FileCheckResult(canProceed = true)
    }

    private fun checkDeleteOperation(file: File): FileCheckResult {
        if (!file.exists()) {
            return FileCheckResult(
                canProceed = true,
                warning = "File does not exist (nothing to delete)"
            )
        }

        // Check for protected paths
        val protectedNames = setOf(
            "index.html", "package.json", ".git", ".gitignore",
            "src", "app", "build", "node_modules"
        )
        if (file.name in protectedNames) {
            return FileCheckResult(
                canProceed = false,
                error = "Cannot delete protected file: ${file.name}",
                suggestion = "This file is important for the project"
            )
        }

        // Check if directory has contents
        if (file.isDirectory) {
            val contents = file.listFiles()
            if (contents != null && contents.isNotEmpty()) {
                return FileCheckResult(
                    canProceed = true,
                    warning = "Directory contains ${contents.size} items that will be deleted"
                )
            }
        }

        return FileCheckResult(canProceed = true)
    }

    private fun checkCreateOperation(file: File): FileCheckResult {
        if (file.exists()) {
            return FileCheckResult(
                canProceed = true,
                warning = "File already exists and will be overwritten"
            )
        }

        // Check for invalid filename
        val validationResult = validateFilename(file.name)
        if (validationResult != null) {
            return FileCheckResult(
                canProceed = false,
                error = validationResult,
                suggestion = "Use a valid filename"
            )
        }

        return checkWriteOperation(file, 0)
    }

    /**
     * Validate a filename for various edge cases.
     */
    fun validateFilename(filename: String): String? {
        if (filename.isBlank()) {
            return "Filename cannot be empty"
        }

        if (filename.length > Constants.Security.MAX_FILENAME_LENGTH) {
            return "Filename too long (max ${Constants.Security.MAX_FILENAME_LENGTH} characters)"
        }

        // Check for forbidden characters
        Constants.Security.FORBIDDEN_CHARS.forEach { char ->
            if (filename.contains(char)) {
                return "Filename contains forbidden character: $char"
            }
        }

        // Check for reserved names (Windows compatibility)
        val reservedNames = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )
        val baseName = filename.substringBeforeLast('.').uppercase()
        if (baseName in reservedNames) {
            return "Filename uses a reserved name: $baseName"
        }

        // Check for names starting/ending with problematic characters
        if (filename.startsWith(".") && filename.length == 1) {
            return "Filename cannot be just a dot"
        }

        if (filename.startsWith(" ") || filename.endsWith(" ")) {
            return "Filename cannot start or end with spaces"
        }

        if (filename.endsWith(".")) {
            return "Filename cannot end with a dot"
        }

        return null
    }

    /**
     * Types of file operations.
     */
    enum class FileOperation {
        READ, WRITE, DELETE, CREATE
    }

    // ==================== Memory Edge Cases ====================

    /**
     * Check memory availability before allocating.
     */
    fun checkMemoryAvailability(requiredBytes: Long): MemoryCheckResult {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val availableMemory = maxMemory - usedMemory

        val usagePercent = (usedMemory.toDouble() / maxMemory) * 100

        return when {
            availableMemory < requiredBytes -> MemoryCheckResult(
                canProceed = false,
                availableBytes = availableMemory,
                usagePercent = usagePercent,
                message = "Insufficient memory available"
            )
            usagePercent > 90 -> MemoryCheckResult(
                canProceed = true,
                availableBytes = availableMemory,
                usagePercent = usagePercent,
                message = "Memory usage is critical (${usagePercent.toInt()}%)",
                shouldGc = true
            )
            usagePercent > 75 -> MemoryCheckResult(
                canProceed = true,
                availableBytes = availableMemory,
                usagePercent = usagePercent,
                message = "Memory usage is high (${usagePercent.toInt()}%)"
            )
            else -> MemoryCheckResult(
                canProceed = true,
                availableBytes = availableMemory,
                usagePercent = usagePercent
            )
        }
    }

    /**
     * Result of a memory availability check.
     */
    data class MemoryCheckResult(
        val canProceed: Boolean,
        val availableBytes: Long,
        val usagePercent: Double,
        val message: String? = null,
        val shouldGc: Boolean = false
    )

    /**
     * Suggest garbage collection if memory is low.
     */
    fun suggestGcIfNeeded(): Boolean {
        val result = checkMemoryAvailability(0)
        if (result.shouldGc || result.usagePercent > 85) {
            System.gc()
            return true
        }
        return false
    }

    // ==================== String Edge Cases ====================

    /**
     * Safely truncate a string to a maximum length with suffix.
     */
    fun safeTruncate(
        text: String,
        maxLength: Int,
        suffix: String = "..."
    ): String {
        if (maxLength <= 0) return ""
        if (text.length <= maxLength) return text
        if (suffix.length >= maxLength) return text.take(maxLength)

        val truncateAt = maxLength - suffix.length

        // Try to truncate at a natural boundary
        val boundaryChars = listOf(' ', '\n', '.', ',', ';', ':', '-', '_')
        for (i in truncateAt downTo (truncateAt * 0.8).toInt()) {
            if (text[i] in boundaryChars) {
                return text.substring(0, i) + suffix
            }
        }

        return text.take(truncateAt) + suffix
    }

    /**
     * Safely read lines from a string, handling various line endings.
     */
    fun safeReadLines(text: String): List<String> {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
    }

    /**
     * Normalize line endings to Unix style (LF).
     */
    fun normalizeLineEndings(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
    }

    /**
     * Check if text is likely binary content.
     */
    fun isBinaryContent(text: String): Boolean {
        // Check first 8KB for null bytes or high percentage of non-printable characters
        val sample = text.take(8192)
        if (sample.contains('\u0000')) return true

        val nonPrintable = sample.count { char ->
            char.code < 32 && char !in listOf('\n', '\r', '\t')
        }

        return nonPrintable.toDouble() / sample.length > 0.3
    }

    // ==================== Timeout Edge Cases ====================

    /**
     * Execute a block with timeout and proper error handling.
     */
    suspend fun <T> withSafeTimeout(
        timeoutMs: Long,
        operationName: String,
        block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(withTimeout(timeoutMs) { block() })
        } catch (e: TimeoutCancellationException) {
            Result.failure(
                AppError.NetworkError.ConnectionTimeout(timeoutMs, e)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Encoding Edge Cases ====================

    /**
     * Try to detect and handle text encoding.
     */
    fun detectAndNormalizeEncoding(bytes: ByteArray): String {
        // Check for BOM markers
        return when {
            // UTF-8 BOM
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> {
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
            // UTF-16 LE BOM
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            // UTF-16 BE BOM
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
            // Default to UTF-8
            else -> {
                try {
                    String(bytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    // Fallback to ISO-8859-1 which can decode any byte sequence
                    String(bytes, Charsets.ISO_8859_1)
                }
            }
        }
    }

    // ==================== Input Validation Edge Cases ====================

    /**
     * Validate and sanitize user input.
     */
    fun sanitizeUserInput(
        input: String,
        maxLength: Int = 10000,
        allowNewlines: Boolean = true,
        allowHtml: Boolean = false
    ): String {
        var sanitized = input.trim()

        // Limit length
        if (sanitized.length > maxLength) {
            sanitized = sanitized.take(maxLength)
        }

        // Remove null bytes
        sanitized = sanitized.replace("\u0000", "")

        // Handle newlines
        if (!allowNewlines) {
            sanitized = sanitized.replace("\n", " ").replace("\r", " ")
        }

        // Escape HTML if not allowed
        if (!allowHtml) {
            sanitized = sanitized
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
        }

        return sanitized
    }

    // ==================== Utility Functions ====================

    /**
     * Format bytes to human-readable string.
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * Get available storage space for a path.
     */
    fun getAvailableStorage(path: File): Long {
        return try {
            val stat = StatFs(path.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Check if a string looks like a valid file path.
     */
    fun looksLikeFilePath(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.length > Constants.Security.MAX_PATH_LENGTH) return false

        // Basic path patterns
        val pathPatterns = listOf(
            Regex("""^[a-zA-Z0-9_\-./\\]+\.[a-zA-Z0-9]+$"""),  // file.ext
            Regex("""^[a-zA-Z0-9_\-./\\]+$"""),                  // path/to/file
        )

        return pathPatterns.any { it.matches(text) }
    }
}
