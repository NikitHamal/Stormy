package com.codex.stormy.data.ai.tools

import android.util.Log
import com.codex.stormy.data.repository.ProjectRepository
import com.codex.stormy.domain.model.FileTreeNode
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Production-grade code diff and comparison tools.
 *
 * Provides sophisticated diffing capabilities:
 * - Line-by-line diff with context
 * - Unified diff format output
 * - Semantic diff for code (function/class level)
 * - Side-by-side comparison
 * - Multi-file diff generation
 * - Patch application and reversal
 */
class CodeDiffTools(
    private val projectRepository: ProjectRepository
) {
    companion object {
        private const val TAG = "CodeDiffTools"
        private const val DEFAULT_CONTEXT_LINES = 3
        private const val MAX_DIFF_SIZE = 100_000
    }

    /**
     * Generate a unified diff between two strings.
     */
    fun generateUnifiedDiff(
        original: String,
        modified: String,
        originalLabel: String = "original",
        modifiedLabel: String = "modified",
        contextLines: Int = DEFAULT_CONTEXT_LINES
    ): String {
        val originalLines = original.lines()
        val modifiedLines = modified.lines()

        if (originalLines == modifiedLines) {
            return "No differences found."
        }

        val lcs = computeLCS(originalLines, modifiedLines)
        val hunks = generateHunks(originalLines, modifiedLines, lcs, contextLines)

        return buildString {
            appendLine("--- $originalLabel")
            appendLine("+++ $modifiedLabel")

            hunks.forEach { hunk ->
                appendLine("@@ -${hunk.originalStart + 1},${hunk.originalLength} +${hunk.modifiedStart + 1},${hunk.modifiedLength} @@")
                hunk.lines.forEach { line ->
                    appendLine(line)
                }
            }
        }
    }

    /**
     * Generate side-by-side comparison view.
     */
    fun generateSideBySideDiff(
        original: String,
        modified: String,
        maxWidth: Int = 80
    ): String {
        val originalLines = original.lines()
        val modifiedLines = modified.lines()
        val lcs = computeLCS(originalLines, modifiedLines)

        val columnWidth = (maxWidth - 3) / 2
        val separator = " | "

        return buildString {
            val aligned = alignLines(originalLines, modifiedLines, lcs)

            aligned.forEach { (origLine, modLine, changeType) ->
                val leftPadded = (origLine ?: "").take(columnWidth).padEnd(columnWidth)
                val rightPadded = (modLine ?: "").take(columnWidth).padEnd(columnWidth)

                val marker = when (changeType) {
                    ChangeType.UNCHANGED -> " "
                    ChangeType.ADDED -> "+"
                    ChangeType.REMOVED -> "-"
                    ChangeType.MODIFIED -> "~"
                }

                appendLine("$marker$leftPadded$separator$rightPadded")
            }
        }
    }

    /**
     * Compare two files and return diff result.
     */
    suspend fun compareFiles(
        projectId: String,
        path1: String,
        path2: String,
        format: DiffFormat = DiffFormat.UNIFIED
    ): ToolResult {
        return try {
            val content1 = projectRepository.readFile(projectId, path1).getOrNull()
                ?: return ToolResult(false, "", "File not found: $path1")
            val content2 = projectRepository.readFile(projectId, path2).getOrNull()
                ?: return ToolResult(false, "", "File not found: $path2")

            if (content1.length + content2.length > MAX_DIFF_SIZE) {
                return ToolResult(false, "", "Files too large for diff comparison (max ${MAX_DIFF_SIZE / 1000}KB combined)")
            }

            val diff = when (format) {
                DiffFormat.UNIFIED -> generateUnifiedDiff(content1, content2, path1, path2)
                DiffFormat.SIDE_BY_SIDE -> generateSideBySideDiff(content1, content2)
                DiffFormat.STATS_ONLY -> generateDiffStats(content1, content2)
            }

            ToolResult(true, diff)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compare files", e)
            ToolResult(false, "", "Failed to compare files: ${e.message}")
        }
    }

    /**
     * Generate diff statistics without full diff content.
     */
    fun generateDiffStats(original: String, modified: String): String {
        val originalLines = original.lines()
        val modifiedLines = modified.lines()
        val lcs = computeLCS(originalLines, modifiedLines)

        var additions = 0
        var deletions = 0
        var unchanged = 0

        val originalSet = lcs.map { it.first }.toSet()
        val modifiedSet = lcs.map { it.second }.toSet()

        originalLines.indices.forEach { i ->
            if (i !in originalSet) deletions++
        }
        modifiedLines.indices.forEach { i ->
            if (i !in modifiedSet) additions++
        }
        unchanged = lcs.size

        return buildString {
            appendLine("Diff Statistics:")
            appendLine("  Original lines: ${originalLines.size}")
            appendLine("  Modified lines: ${modifiedLines.size}")
            appendLine("  Added:          +$additions")
            appendLine("  Removed:        -$deletions")
            appendLine("  Unchanged:      $unchanged")
            appendLine("  Change ratio:   ${String.format("%.1f", (additions + deletions).toFloat() / max(1, originalLines.size) * 100)}%")
        }
    }

    /**
     * Apply a unified diff patch to content.
     */
    fun applyPatch(original: String, patch: String): Result<String> {
        return try {
            val lines = original.lines().toMutableList()
            val patchLines = patch.lines()
            var lineOffset = 0

            val hunkPattern = Regex("""^@@ -(\d+),?(\d+)? \+(\d+),?(\d+)? @@""")

            var i = 0
            while (i < patchLines.size) {
                val line = patchLines[i]

                if (line.startsWith("@@")) {
                    val match = hunkPattern.find(line)
                    if (match != null) {
                        val originalStart = match.groupValues[1].toInt() - 1 + lineOffset

                        i++
                        while (i < patchLines.size && !patchLines[i].startsWith("@@")) {
                            val patchLine = patchLines[i]
                            when {
                                patchLine.startsWith("-") -> {
                                    if (originalStart + lineOffset < lines.size) {
                                        lines.removeAt(originalStart)
                                        lineOffset--
                                    }
                                }
                                patchLine.startsWith("+") -> {
                                    val content = patchLine.substring(1)
                                    lines.add(minOf(originalStart + lineOffset + 1, lines.size), content)
                                    lineOffset++
                                }
                            }
                            i++
                        }
                        continue
                    }
                }
                i++
            }

            Result.success(lines.joinToString("\n"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply patch", e)
            Result.failure(e)
        }
    }

    /**
     * Generate a semantic diff that groups changes by code structure.
     * Identifies function/class level changes.
     */
    fun generateSemanticDiff(
        original: String,
        modified: String,
        fileExtension: String
    ): String {
        val originalBlocks = extractCodeBlocks(original, fileExtension)
        val modifiedBlocks = extractCodeBlocks(modified, fileExtension)

        return buildString {
            appendLine("Semantic Diff Analysis:")
            appendLine()

            val originalNames = originalBlocks.map { it.name }.toSet()
            val modifiedNames = modifiedBlocks.map { it.name }.toSet()

            val added = modifiedNames - originalNames
            val removed = originalNames - modifiedNames
            val common = originalNames.intersect(modifiedNames)

            if (added.isNotEmpty()) {
                appendLine("Added:")
                added.forEach { appendLine("  + $it") }
                appendLine()
            }

            if (removed.isNotEmpty()) {
                appendLine("Removed:")
                removed.forEach { appendLine("  - $it") }
                appendLine()
            }

            if (common.isNotEmpty()) {
                appendLine("Modified:")
                common.forEach { name ->
                    val origBlock = originalBlocks.find { it.name == name }
                    val modBlock = modifiedBlocks.find { it.name == name }

                    if (origBlock != null && modBlock != null && origBlock.content != modBlock.content) {
                        appendLine("  ~ $name (${origBlock.type})")
                        val stats = generateDiffStats(origBlock.content, modBlock.content)
                        stats.lines().forEach { line ->
                            appendLine("      $line")
                        }
                    }
                }
            }
        }
    }

    /**
     * Find changed files in a directory (comparing with previous state).
     */
    suspend fun findChangedFiles(
        projectId: String,
        basePath: String = "",
        previousState: Map<String, String>
    ): List<FileChange> {
        val changes = mutableListOf<FileChange>()
        val fileTree = projectRepository.getFileTree(projectId)

        fun traverse(nodes: List<FileTreeNode>, currentPath: String) {
            for (node in nodes) {
                when (node) {
                    is FileTreeNode.FileNode -> {
                        val fullPath = if (currentPath.isEmpty()) node.name else "$currentPath/${node.name}"

                        if (basePath.isEmpty() || fullPath.startsWith(basePath)) {
                            val previousContent = previousState[fullPath]
                            val currentContent = runCatching {
                                kotlinx.coroutines.runBlocking {
                                    projectRepository.readFile(projectId, fullPath).getOrNull()
                                }
                            }.getOrNull()

                            when {
                                previousContent == null && currentContent != null -> {
                                    changes.add(FileChange(fullPath, FileChangeType.ADDED))
                                }
                                previousContent != null && currentContent == null -> {
                                    changes.add(FileChange(fullPath, FileChangeType.DELETED))
                                }
                                previousContent != null && currentContent != null && previousContent != currentContent -> {
                                    changes.add(FileChange(fullPath, FileChangeType.MODIFIED))
                                }
                            }
                        }
                    }
                    is FileTreeNode.FolderNode -> {
                        val newPath = if (currentPath.isEmpty()) node.name else "$currentPath/${node.name}"
                        traverse(node.children, newPath)
                    }
                }
            }
        }

        traverse(fileTree, "")
        return changes
    }

    // ==================== Private Helper Methods ====================

    /**
     * Compute Longest Common Subsequence using dynamic programming.
     * Returns list of pairs (originalIndex, modifiedIndex) for matching lines.
     */
    private fun computeLCS(original: List<String>, modified: List<String>): List<Pair<Int, Int>> {
        val m = original.size
        val n = modified.size

        if (m == 0 || n == 0) return emptyList()

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (original[i - 1] == modified[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    max(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        val result = mutableListOf<Pair<Int, Int>>()
        var i = m
        var j = n

        while (i > 0 && j > 0) {
            when {
                original[i - 1] == modified[j - 1] -> {
                    result.add(0, Pair(i - 1, j - 1))
                    i--
                    j--
                }
                dp[i - 1][j] > dp[i][j - 1] -> i--
                else -> j--
            }
        }

        return result
    }

    /**
     * Generate diff hunks with context.
     */
    private fun generateHunks(
        original: List<String>,
        modified: List<String>,
        lcs: List<Pair<Int, Int>>,
        contextLines: Int
    ): List<DiffHunk> {
        val hunks = mutableListOf<DiffHunk>()
        val changes = mutableListOf<DiffChange>()

        val lcsOriginalSet = lcs.map { it.first }.toSet()
        val lcsModifiedSet = lcs.map { it.second }.toSet()

        var origIdx = 0
        var modIdx = 0
        var lcsIdx = 0

        while (origIdx < original.size || modIdx < modified.size) {
            when {
                lcsIdx < lcs.size && origIdx == lcs[lcsIdx].first && modIdx == lcs[lcsIdx].second -> {
                    changes.add(DiffChange(ChangeType.UNCHANGED, original[origIdx], origIdx, modIdx))
                    origIdx++
                    modIdx++
                    lcsIdx++
                }
                origIdx < original.size && origIdx !in lcsOriginalSet -> {
                    changes.add(DiffChange(ChangeType.REMOVED, original[origIdx], origIdx, -1))
                    origIdx++
                }
                modIdx < modified.size && modIdx !in lcsModifiedSet -> {
                    changes.add(DiffChange(ChangeType.ADDED, modified[modIdx], -1, modIdx))
                    modIdx++
                }
                else -> {
                    if (origIdx < original.size) origIdx++
                    if (modIdx < modified.size) modIdx++
                }
            }
        }

        if (changes.isEmpty()) return emptyList()

        var hunkStart = -1
        var hunkChanges = mutableListOf<DiffChange>()

        changes.forEachIndexed { idx, change ->
            val isSignificant = change.type != ChangeType.UNCHANGED

            if (isSignificant) {
                if (hunkStart == -1) {
                    val contextStart = max(0, idx - contextLines)
                    hunkStart = contextStart
                    for (i in contextStart until idx) {
                        hunkChanges.add(changes[i])
                    }
                }
                hunkChanges.add(change)
            } else if (hunkStart != -1) {
                hunkChanges.add(change)

                val nextSignificant = changes.drop(idx + 1).take(contextLines * 2).any { it.type != ChangeType.UNCHANGED }
                if (!nextSignificant) {
                    val contextEnd = min(changes.size - 1, idx + contextLines)
                    for (i in idx + 1..contextEnd) {
                        if (i < changes.size) hunkChanges.add(changes[i])
                    }

                    hunks.add(createHunk(hunkChanges))
                    hunkStart = -1
                    hunkChanges = mutableListOf()
                }
            }
        }

        if (hunkChanges.isNotEmpty()) {
            hunks.add(createHunk(hunkChanges))
        }

        return hunks
    }

    private fun createHunk(changes: List<DiffChange>): DiffHunk {
        val originalStart = changes.filter { it.originalLine >= 0 }.minOfOrNull { it.originalLine } ?: 0
        val modifiedStart = changes.filter { it.modifiedLine >= 0 }.minOfOrNull { it.modifiedLine } ?: 0

        val originalLength = changes.count { it.type == ChangeType.UNCHANGED || it.type == ChangeType.REMOVED }
        val modifiedLength = changes.count { it.type == ChangeType.UNCHANGED || it.type == ChangeType.ADDED }

        val lines = changes.map { change ->
            when (change.type) {
                ChangeType.UNCHANGED -> " ${change.content}"
                ChangeType.ADDED -> "+${change.content}"
                ChangeType.REMOVED -> "-${change.content}"
                ChangeType.MODIFIED -> "~${change.content}"
            }
        }

        return DiffHunk(originalStart, originalLength, modifiedStart, modifiedLength, lines)
    }

    private fun alignLines(
        original: List<String>,
        modified: List<String>,
        lcs: List<Pair<Int, Int>>
    ): List<Triple<String?, String?, ChangeType>> {
        val result = mutableListOf<Triple<String?, String?, ChangeType>>()

        val lcsOriginalSet = lcs.map { it.first }.toSet()
        val lcsModifiedSet = lcs.map { it.second }.toSet()

        var origIdx = 0
        var modIdx = 0
        var lcsIdx = 0

        while (origIdx < original.size || modIdx < modified.size) {
            when {
                lcsIdx < lcs.size && origIdx == lcs[lcsIdx].first && modIdx == lcs[lcsIdx].second -> {
                    result.add(Triple(original[origIdx], modified[modIdx], ChangeType.UNCHANGED))
                    origIdx++
                    modIdx++
                    lcsIdx++
                }
                origIdx < original.size && origIdx !in lcsOriginalSet -> {
                    result.add(Triple(original[origIdx], null, ChangeType.REMOVED))
                    origIdx++
                }
                modIdx < modified.size && modIdx !in lcsModifiedSet -> {
                    result.add(Triple(null, modified[modIdx], ChangeType.ADDED))
                    modIdx++
                }
                else -> {
                    if (origIdx < original.size) origIdx++
                    if (modIdx < modified.size) modIdx++
                }
            }
        }

        return result
    }

    private fun extractCodeBlocks(content: String, extension: String): List<CodeBlock> {
        val blocks = mutableListOf<CodeBlock>()

        val patterns = when (extension.lowercase()) {
            "js", "jsx", "ts", "tsx" -> listOf(
                Regex("""(function\s+(\w+)\s*\([^)]*\)\s*\{[\s\S]*?\n\})""", RegexOption.MULTILINE),
                Regex("""(const\s+(\w+)\s*=\s*(?:async\s+)?\([^)]*\)\s*=>\s*\{[\s\S]*?\n\})""", RegexOption.MULTILINE),
                Regex("""(class\s+(\w+)[\s\S]*?\n\})""", RegexOption.MULTILINE)
            )
            "kt" -> listOf(
                Regex("""(fun\s+(\w+)\s*\([^)]*\)[\s\S]*?\n\})""", RegexOption.MULTILINE),
                Regex("""(class\s+(\w+)[\s\S]*?\n\})""", RegexOption.MULTILINE),
                Regex("""(object\s+(\w+)[\s\S]*?\n\})""", RegexOption.MULTILINE)
            )
            "java" -> listOf(
                Regex("""((?:public|private|protected)?\s*(?:static)?\s*\w+\s+(\w+)\s*\([^)]*\)\s*\{[\s\S]*?\n\s*\})""", RegexOption.MULTILINE),
                Regex("""(class\s+(\w+)[\s\S]*?\n\})""", RegexOption.MULTILINE)
            )
            "py" -> listOf(
                Regex("""(def\s+(\w+)\s*\([^)]*\):[\s\S]*?(?=\ndef|\nclass|\Z))""", RegexOption.MULTILINE),
                Regex("""(class\s+(\w+)[\s\S]*?(?=\nclass|\ndef|\Z))""", RegexOption.MULTILINE)
            )
            else -> emptyList()
        }

        for (pattern in patterns) {
            pattern.findAll(content).forEach { match ->
                val fullMatch = match.groupValues[1]
                val name = match.groupValues[2]
                val type = when {
                    fullMatch.contains("class ") -> "class"
                    fullMatch.contains("object ") -> "object"
                    fullMatch.contains("function ") || fullMatch.contains("fun ") || fullMatch.contains("def ") -> "function"
                    fullMatch.contains("const ") -> "const"
                    else -> "block"
                }
                blocks.add(CodeBlock(name, type, fullMatch))
            }
        }

        return blocks
    }

    // ==================== Data Classes ====================

    enum class ChangeType { UNCHANGED, ADDED, REMOVED, MODIFIED }

    enum class DiffFormat { UNIFIED, SIDE_BY_SIDE, STATS_ONLY }

    enum class FileChangeType { ADDED, MODIFIED, DELETED }

    data class FileChange(val path: String, val type: FileChangeType)

    private data class DiffChange(
        val type: ChangeType,
        val content: String,
        val originalLine: Int,
        val modifiedLine: Int
    )

    private data class DiffHunk(
        val originalStart: Int,
        val originalLength: Int,
        val modifiedStart: Int,
        val modifiedLength: Int,
        val lines: List<String>
    )

    private data class CodeBlock(
        val name: String,
        val type: String,
        val content: String
    )
}
