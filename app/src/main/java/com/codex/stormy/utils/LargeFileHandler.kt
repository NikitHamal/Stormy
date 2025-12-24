package com.codex.stormy.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * File size thresholds for different handling strategies
 */
object FileSizeThresholds {
    const val SMALL_FILE = 100 * 1024L           // 100 KB - Load fully without warning
    const val MEDIUM_FILE = 500 * 1024L          // 500 KB - Load fully with slight delay
    const val LARGE_FILE = 1 * 1024 * 1024L      // 1 MB - Show warning, offer chunked loading
    const val VERY_LARGE_FILE = 5 * 1024 * 1024L // 5 MB - Strong warning, chunked loading required
    const val HUGE_FILE = 10 * 1024 * 1024L      // 10 MB - Read-only mode recommended

    const val DEFAULT_CHUNK_SIZE = 50 * 1024     // 50 KB per chunk
    const val LINES_PER_PAGE = 1000              // Lines to load per page
    const val PREVIEW_LINES = 100                // Lines for preview
}

/**
 * File loading strategy based on file size
 */
enum class FileLoadingStrategy {
    FULL_LOAD,           // Load entire file at once
    CHUNKED_LOAD,        // Load in chunks, merge for editing
    PAGINATED_LOAD,      // Load page by page on demand
    READ_ONLY_PREVIEW,   // Show preview, read-only mode
    UNSUPPORTED          // File too large to handle
}

/**
 * Result of file size analysis
 */
data class FileSizeAnalysis(
    val sizeBytes: Long,
    val sizeFormatted: String,
    val lineCount: Long,
    val strategy: FileLoadingStrategy,
    val warningMessage: String?,
    val estimatedLoadTime: String
)

/**
 * Chunked file content for paginated editing
 */
data class FileChunk(
    val startLine: Int,
    val endLine: Int,
    val content: String,
    val startByte: Long,
    val endByte: Long
)

/**
 * Large file handler with lazy loading and virtual scrolling support
 */
object LargeFileHandler {

    /**
     * Analyze file and determine best loading strategy
     */
    suspend fun analyzeFile(file: File): FileSizeAnalysis = withContext(Dispatchers.IO) {
        val size = file.length()
        val lineCount = estimateLineCount(file)
        val strategy = determineStrategy(size)
        val warning = getWarningMessage(size, strategy)
        val loadTime = estimateLoadTime(size)

        FileSizeAnalysis(
            sizeBytes = size,
            sizeFormatted = FileUtils.formatFileSize(size),
            lineCount = lineCount,
            strategy = strategy,
            warningMessage = warning,
            estimatedLoadTime = loadTime
        )
    }

    /**
     * Determine loading strategy based on file size
     */
    fun determineStrategy(sizeBytes: Long): FileLoadingStrategy {
        return when {
            sizeBytes <= FileSizeThresholds.MEDIUM_FILE -> FileLoadingStrategy.FULL_LOAD
            sizeBytes <= FileSizeThresholds.LARGE_FILE -> FileLoadingStrategy.CHUNKED_LOAD
            sizeBytes <= FileSizeThresholds.VERY_LARGE_FILE -> FileLoadingStrategy.PAGINATED_LOAD
            sizeBytes <= FileSizeThresholds.HUGE_FILE -> FileLoadingStrategy.READ_ONLY_PREVIEW
            else -> FileLoadingStrategy.UNSUPPORTED
        }
    }

    /**
     * Get warning message for file size
     */
    private fun getWarningMessage(size: Long, strategy: FileLoadingStrategy): String? {
        return when (strategy) {
            FileLoadingStrategy.FULL_LOAD -> null
            FileLoadingStrategy.CHUNKED_LOAD ->
                "This file is fairly large (${FileUtils.formatFileSize(size)}). Loading may take a moment."
            FileLoadingStrategy.PAGINATED_LOAD ->
                "This file is large (${FileUtils.formatFileSize(size)}). It will be loaded in pages for better performance."
            FileLoadingStrategy.READ_ONLY_PREVIEW ->
                "This file is very large (${FileUtils.formatFileSize(size)}). Opening in read-only preview mode."
            FileLoadingStrategy.UNSUPPORTED ->
                "This file is too large to open (${FileUtils.formatFileSize(size)}). Maximum supported size is 10 MB."
        }
    }

    /**
     * Estimate load time based on file size
     */
    private fun estimateLoadTime(size: Long): String {
        // Rough estimate: ~10 MB/s on average mobile device
        val seconds = size / (10 * 1024 * 1024.0)
        return when {
            seconds < 0.5 -> "instant"
            seconds < 2 -> "~1 second"
            seconds < 5 -> "~${seconds.toInt()} seconds"
            seconds < 30 -> "~${(seconds / 10).toInt() * 10} seconds"
            else -> ">30 seconds"
        }
    }

    /**
     * Estimate line count without reading entire file
     */
    private suspend fun estimateLineCount(file: File): Long = withContext(Dispatchers.IO) {
        try {
            // Sample first and last 10KB to estimate average line length
            val sampleSize = minOf(file.length(), 10 * 1024L)
            val buffer = ByteArray(sampleSize.toInt())

            RandomAccessFile(file, "r").use { raf ->
                raf.readFully(buffer, 0, buffer.size)
            }

            val sampleContent = String(buffer, StandardCharsets.UTF_8)
            val sampleLines = sampleContent.count { it == '\n' }

            if (sampleLines == 0) return@withContext 1L

            val avgLineLength = sampleSize / sampleLines
            (file.length() / avgLineLength)
        } catch (e: Exception) {
            // Fallback: assume average line length of 50 characters
            file.length() / 50
        }
    }

    /**
     * Read file with progress callback
     */
    suspend fun readFileWithProgress(
        file: File,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val totalSize = file.length()
            val builder = StringBuilder()
            var bytesRead = 0L

            file.bufferedReader().use { reader ->
                val buffer = CharArray(FileSizeThresholds.DEFAULT_CHUNK_SIZE)
                var count: Int

                while (reader.read(buffer).also { count = it } != -1) {
                    builder.append(buffer, 0, count)
                    bytesRead += count
                    onProgress(bytesRead.toFloat() / totalSize)
                }
            }

            Result.success(builder.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Read specific line range from file
     */
    suspend fun readLineRange(
        file: File,
        startLine: Int,
        endLine: Int
    ): Result<FileChunk> = withContext(Dispatchers.IO) {
        try {
            val lines = mutableListOf<String>()
            var currentLine = 0
            var startByte = 0L
            var endByte = 0L
            var foundStart = false

            file.bufferedReader().use { reader ->
                var line: String?
                var bytePosition = 0L

                while (reader.readLine().also { line = it } != null) {
                    if (currentLine == startLine) {
                        startByte = bytePosition
                        foundStart = true
                    }

                    if (currentLine >= startLine && currentLine < endLine) {
                        lines.add(line!!)
                    }

                    bytePosition += (line!!.length + 1) // +1 for newline
                    currentLine++

                    if (currentLine >= endLine) {
                        endByte = bytePosition
                        break
                    }
                }
            }

            if (!foundStart) {
                return@withContext Result.failure(IllegalArgumentException("Start line $startLine not found"))
            }

            Result.success(
                FileChunk(
                    startLine = startLine,
                    endLine = minOf(endLine, currentLine),
                    content = lines.joinToString("\n"),
                    startByte = startByte,
                    endByte = endByte
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Read file preview (first N lines)
     */
    suspend fun readPreview(
        file: File,
        maxLines: Int = FileSizeThresholds.PREVIEW_LINES
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val lines = mutableListOf<String>()

            file.bufferedReader().use { reader ->
                var line: String?
                var count = 0

                while (reader.readLine().also { line = it } != null && count < maxLines) {
                    lines.add(line!!)
                    count++
                }
            }

            val content = lines.joinToString("\n")
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search in large file without loading entire content
     */
    suspend fun searchInFile(
        file: File,
        query: String,
        caseSensitive: Boolean = false,
        maxResults: Int = 100
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()
        val searchQuery = if (caseSensitive) query else query.lowercase()

        try {
            file.bufferedReader().use { reader ->
                var line: String?
                var lineNumber = 0

                while (reader.readLine().also { line = it } != null && results.size < maxResults) {
                    val searchLine = if (caseSensitive) line!! else line!!.lowercase()

                    if (searchLine.contains(searchQuery)) {
                        val column = searchLine.indexOf(searchQuery)
                        results.add(
                            SearchResult(
                                lineNumber = lineNumber,
                                column = column,
                                lineContent = line!!,
                                matchLength = query.length
                            )
                        )
                    }

                    lineNumber++
                }
            }
        } catch (e: Exception) {
            // Return empty results on error
        }

        results
    }

    /**
     * Write file in chunks to prevent memory issues
     */
    suspend fun writeFileChunked(
        file: File,
        content: String,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val totalSize = content.length
            var written = 0

            file.bufferedWriter().use { writer ->
                var start = 0
                while (start < content.length) {
                    val end = minOf(start + FileSizeThresholds.DEFAULT_CHUNK_SIZE, content.length)
                    writer.write(content.substring(start, end))
                    written += (end - start)
                    onProgress(written.toFloat() / totalSize)
                    start = end
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Count total lines in file efficiently
     */
    suspend fun countLines(file: File): Long = withContext(Dispatchers.IO) {
        try {
            var count = 0L
            file.bufferedReader().use { reader ->
                while (reader.readLine() != null) {
                    count++
                }
            }
            count
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get file encoding
     */
    suspend fun detectEncoding(file: File): String = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(minOf(file.length().toInt(), 4096))
            file.inputStream().use { it.read(buffer) }

            // Check for BOM
            when {
                buffer.size >= 3 &&
                    buffer[0] == 0xEF.toByte() &&
                    buffer[1] == 0xBB.toByte() &&
                    buffer[2] == 0xBF.toByte() -> "UTF-8 with BOM"
                buffer.size >= 2 &&
                    buffer[0] == 0xFE.toByte() &&
                    buffer[1] == 0xFF.toByte() -> "UTF-16 BE"
                buffer.size >= 2 &&
                    buffer[0] == 0xFF.toByte() &&
                    buffer[1] == 0xFE.toByte() -> "UTF-16 LE"
                else -> {
                    // Heuristic check for ASCII/UTF-8
                    val highByteCount = buffer.count { it < 0 }
                    if (highByteCount == 0) "ASCII" else "UTF-8"
                }
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}

/**
 * Search result in a file
 */
data class SearchResult(
    val lineNumber: Int,
    val column: Int,
    val lineContent: String,
    val matchLength: Int
)

/**
 * Extension function for checking if file needs special handling
 */
fun File.needsLargeFileHandling(): Boolean {
    return length() > FileSizeThresholds.SMALL_FILE
}

/**
 * Extension function for checking if file is too large to edit
 */
fun File.isTooLargeToEdit(): Boolean {
    return length() > FileSizeThresholds.VERY_LARGE_FILE
}
