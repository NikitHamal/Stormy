package com.oro.app.utils

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
     * Upper bound on the LCS table, in cells (4 bytes each): 2M cells is 8 MB.
     * The table is (old lines x new lines) of the *changed middle* only, but a
     * wholesale rewrite of a 3,000-line file used to allocate ~36 MB here --
     * on the main thread, inside composition -- which is an OOM on a budget
     * phone. Past this bound the middle is shown as a block replacement:
     * still a correct diff, just not a minimal one.
     */
    private const val MAX_LCS_CELLS = 2_000_000L

    /**
     * Compute diff using Longest Common Subsequence (LCS) algorithm.
     *
     * The common prefix and suffix are stripped first, so the quadratic table
     * only covers the region that actually changed -- for a typical agent
     * edit that is a handful of lines instead of the whole file.
     */
    private fun computeLCSDiff(
        oldLines: List<String>,
        newLines: List<String>
    ): List<DiffLine> {
        val oldSize = oldLines.size
        val newSize = newLines.size

        var prefix = 0
        while (prefix < oldSize && prefix < newSize && oldLines[prefix] == newLines[prefix]) {
            prefix++
        }
        var suffix = 0
        while (suffix < oldSize - prefix && suffix < newSize - prefix &&
            oldLines[oldSize - 1 - suffix] == newLines[newSize - 1 - suffix]
        ) {
            suffix++
        }

        val m = oldSize - prefix - suffix
        val n = newSize - prefix - suffix
        val result = ArrayList<DiffLine>(prefix + suffix + m + n)

        for (k in 0 until prefix) {
            result.add(DiffLine.Unchanged(oldLines[k], k + 1, k + 1))
        }

        if (m.toLong() * n.toLong() > MAX_LCS_CELLS) {
            for (k in 0 until m) {
                result.add(DiffLine.Deletion(oldLines[prefix + k], prefix + k + 1))
            }
            for (k in 0 until n) {
                result.add(DiffLine.Addition(newLines[prefix + k], prefix + k + 1))
            }
        } else if (m > 0 || n > 0) {
            // LCS length matrix over the changed middle only.
            val dp = Array(m + 1) { IntArray(n + 1) }
            for (i in 1..m) {
                val oldLine = oldLines[prefix + i - 1]
                for (j in 1..n) {
                    if (oldLine == newLines[prefix + j - 1]) {
                        dp[i][j] = dp[i - 1][j - 1] + 1
                    } else {
                        dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                    }
                }
            }

            // Backtrack, collecting in reverse and flipping once at the end
            // (inserting at index 0 each step was quadratic on its own).
            val middle = ArrayList<DiffLine>(m + n)
            var i = m
            var j = n
            while (i > 0 || j > 0) {
                when {
                    i > 0 && j > 0 && oldLines[prefix + i - 1] == newLines[prefix + j - 1] -> {
                        middle.add(DiffLine.Unchanged(oldLines[prefix + i - 1], prefix + i, prefix + j))
                        i--
                        j--
                    }
                    j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                        middle.add(DiffLine.Addition(newLines[prefix + j - 1], prefix + j))
                        j--
                    }
                    else -> {
                        middle.add(DiffLine.Deletion(oldLines[prefix + i - 1], prefix + i))
                        i--
                    }
                }
            }
            middle.reverse()
            result.addAll(middle)
        }

        for (k in 0 until suffix) {
            val oldIndex = oldSize - suffix + k
            val newIndex = newSize - suffix + k
            result.add(DiffLine.Unchanged(oldLines[oldIndex], oldIndex + 1, newIndex + 1))
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

        // Mark indices that should be shown. A flag array rather than a
        // boxed Set<Int> + sort: this runs over every line of the file.
        val show = BooleanArray(lines.size)
        var anyShown = false

        lines.forEachIndexed { index, line ->
            if (line is DiffLine.Addition || line is DiffLine.Deletion) {
                // Add this line and surrounding context
                val from = maxOf(0, index - contextLines)
                val to = minOf(lines.lastIndex, index + contextLines)
                for (contextIndex in from..to) {
                    show[contextIndex] = true
                }
                anyShown = true
            }
        }

        // If no changes, return empty (or show message)
        if (!anyShown) {
            return emptyList()
        }

        // Build filtered list with optional ellipsis markers
        val result = mutableListOf<DiffLine>()
        var lastShownIndex = -1

        for (index in lines.indices) {
            if (!show[index]) continue
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
