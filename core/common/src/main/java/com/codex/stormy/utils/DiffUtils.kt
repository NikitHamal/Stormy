package com.codex.stormy.utils

/**
 * Professional diff computation utilities for file change visualization.
 * Implements Myers' diff algorithm for efficient line-level comparison.
 */
object DiffUtils {

    /**
     * Represents a single line in a diff output
     */
    sealed class DiffLine {
        abstract val lineNumber: Int?
        abstract val content: String

        data class Addition(
            override val content: String,
            override val lineNumber: Int
        ) : DiffLine()

        data class Deletion(
            override val content: String,
            override val lineNumber: Int
        ) : DiffLine()

        data class Unchanged(
            override val content: String,
            override val lineNumber: Int,
            val newLineNumber: Int
        ) : DiffLine()

        data class Context(
            override val content: String,
            override val lineNumber: Int,
            val newLineNumber: Int
        ) : DiffLine()
    }

    /**
     * Represents the overall diff result with statistics
     */
    data class DiffResult(
        val lines: List<DiffLine>,
        val additions: Int,
        val deletions: Int,
        val oldLineCount: Int,
        val newLineCount: Int,
        val hasChanges: Boolean
    ) {
        val summary: String
            get() = buildString {
                if (additions > 0) append("+$additions")
                if (additions > 0 && deletions > 0) append(" ")
                if (deletions > 0) append("-$deletions")
                if (additions == 0 && deletions == 0) append("No changes")
            }
    }

    /**
     * Compute a diff between two strings, treating them as line-based content.
     * Uses a simplified Myers algorithm for efficiency.
     *
     * @param oldContent The original content (can be empty for new files)
     * @param newContent The new content
     * @param contextLines Number of unchanged context lines to show around changes
     * @return DiffResult containing the diff lines and statistics
     */
    fun computeDiff(
        oldContent: String,
        newContent: String,
        contextLines: Int = 3
    ): DiffResult {
        // Handle edge cases
        if (oldContent.isEmpty() && newContent.isEmpty()) {
            return DiffResult(
                lines = emptyList(),
                additions = 0,
                deletions = 0,
                oldLineCount = 0,
                newLineCount = 0,
                hasChanges = false
            )
        }

        val oldLines = if (oldContent.isEmpty()) emptyList() else oldContent.lines()
        val newLines = if (newContent.isEmpty()) emptyList() else newContent.lines()

        // If creating new file
        if (oldContent.isEmpty()) {
            return DiffResult(
                lines = newLines.mapIndexed { index, line ->
                    DiffLine.Addition(line, index + 1)
                },
                additions = newLines.size,
                deletions = 0,
                oldLineCount = 0,
                newLineCount = newLines.size,
                hasChanges = true
            )
        }

        // If deleting file
        if (newContent.isEmpty()) {
            return DiffResult(
                lines = oldLines.mapIndexed { index, line ->
                    DiffLine.Deletion(line, index + 1)
                },
                additions = 0,
                deletions = oldLines.size,
                oldLineCount = oldLines.size,
                newLineCount = 0,
                hasChanges = true
            )
        }

        // Compute LCS-based diff
        val diffLines = computeLCSDiff(oldLines, newLines)

        // Add context filtering
        val filteredLines = addContextFiltering(diffLines, contextLines)

        val additions = filteredLines.count { it is DiffLine.Addition }
        val deletions = filteredLines.count { it is DiffLine.Deletion }

        return DiffResult(
            lines = filteredLines,
            additions = additions,
            deletions = deletions,
            oldLineCount = oldLines.size,
            newLineCount = newLines.size,
            hasChanges = additions > 0 || deletions > 0
        )
    }

    /**
     * Compute diff using Longest Common Subsequence (LCS) algorithm
     */
    private fun computeLCSDiff(
        oldLines: List<String>,
        newLines: List<String>
    ): List<DiffLine> {
        val m = oldLines.size
        val n = newLines.size

        // Compute LCS length matrix
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) {
            for (j in 1..n) {
                if (oldLines[i - 1] == newLines[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        // Backtrack to find the diff
        val result = mutableListOf<DiffLine>()
        var i = m
        var j = n

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1] -> {
                    // Lines are the same
                    result.add(0, DiffLine.Unchanged(oldLines[i - 1], i, j))
                    i--
                    j--
                }
                j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                    // Addition
                    result.add(0, DiffLine.Addition(newLines[j - 1], j))
                    j--
                }
                i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j]) -> {
                    // Deletion
                    result.add(0, DiffLine.Deletion(oldLines[i - 1], i))
                    i--
                }
            }
        }

        return result
    }

    /**
     * Filter diff lines to show only changed lines with surrounding context
     */
    private fun addContextFiltering(
        lines: List<DiffLine>,
        contextLines: Int
    ): List<DiffLine> {
        if (contextLines < 0 || lines.isEmpty()) return lines

        // Mark indices that should be shown
        val showIndices = mutableSetOf<Int>()

        lines.forEachIndexed { index, line ->
            if (line is DiffLine.Addition || line is DiffLine.Deletion) {
                // Add this line and surrounding context
                for (offset in -contextLines..contextLines) {
                    val contextIndex = index + offset
                    if (contextIndex in lines.indices) {
                        showIndices.add(contextIndex)
                    }
                }
            }
        }

        // If no changes, return empty (or show message)
        if (showIndices.isEmpty()) {
            return emptyList()
        }

        // Build filtered list with optional ellipsis markers
        val result = mutableListOf<DiffLine>()
        var lastShownIndex = -1

        for (index in showIndices.sorted()) {
            // Add ellipsis if there's a gap
            if (lastShownIndex >= 0 && index - lastShownIndex > 1) {
                // Gap exists - could add a separator here if needed
            }

            val line = lines[index]
            when (line) {
                is DiffLine.Unchanged -> {
                    // Convert unchanged to context when filtering
                    result.add(DiffLine.Context(line.content, line.lineNumber, line.newLineNumber))
                }
                else -> result.add(line)
            }
            lastShownIndex = index
        }

        return result
    }

    /**
     * Compute a simple word-level diff for inline highlighting
     */
    fun computeWordDiff(oldLine: String, newLine: String): List<WordDiff> {
        val oldWords = tokenizeLine(oldLine)
        val newWords = tokenizeLine(newLine)

        val result = mutableListOf<WordDiff>()

        // Simple implementation - can be enhanced with LCS for words
        val oldSet = oldWords.toSet()
        val newSet = newWords.toSet()

        for (word in newWords) {
            result.add(
                when {
                    word !in oldSet -> WordDiff.Added(word)
                    else -> WordDiff.Unchanged(word)
                }
            )
        }

        return result
    }

    /**
     * Tokenize a line into words and whitespace
     */
    private fun tokenizeLine(line: String): List<String> {
        return line.split(Regex("(?<=\\s)|(?=\\s)"))
            .filter { it.isNotEmpty() }
    }

    /**
     * Represents a word-level diff element
     */
    sealed class WordDiff {
        abstract val text: String

        data class Added(override val text: String) : WordDiff()
        data class Removed(override val text: String) : WordDiff()
        data class Unchanged(override val text: String) : WordDiff()
    }

    /**
     * Generate a unified diff format string (for debugging/export)
     */
    fun toUnifiedDiff(
        diff: DiffResult,
        oldFileName: String = "a",
        newFileName: String = "b"
    ): String {
        return buildString {
            appendLine("--- $oldFileName")
            appendLine("+++ $newFileName")
            appendLine("@@ -1,${diff.oldLineCount} +1,${diff.newLineCount} @@")

            for (line in diff.lines) {
                when (line) {
                    is DiffLine.Addition -> appendLine("+${line.content}")
                    is DiffLine.Deletion -> appendLine("-${line.content}")
                    is DiffLine.Unchanged -> appendLine(" ${line.content}")
                    is DiffLine.Context -> appendLine(" ${line.content}")
                }
            }
        }
    }
}
