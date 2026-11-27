package com.oro.app.data.preview

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** One saved state of a file the agent worked on. [number] counts from 1 and never repeats. */
data class Revision(val number: Int, val content: String, val at: Long)

/** How a line of a diff relates to the two versions. */
enum class DiffKind { SAME, ADDED, REMOVED, SKIPPED }

data class DiffLine(val kind: DiffKind, val text: String, val oldNumber: Int = 0, val newNumber: Int = 0)

/**
 * A line diff between two texts: what the Diff tab of the preview shows
 * between the previous version of a file and the latest.
 */
object LineDiff {

    /** Above this many cells the exact diff is skipped for a cheaper one. */
    private const val MAX_CELLS = 4_000_000

    fun diff(old: String, new: String): List<DiffLine> {
        val a = lines(old)
        val b = lines(new)
        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start]) start++
        var endA = a.size
        var endB = b.size
        while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) { endA--; endB-- }

        val out = ArrayList<DiffLine>(a.size + b.size)
        for (i in 0 until start) out += DiffLine(DiffKind.SAME, a[i], i + 1, i + 1)

        val n = endA - start
        val m = endB - start
        if (n.toLong() * m.toLong() > MAX_CELLS) {
            for (i in start until endA) out += DiffLine(DiffKind.REMOVED, a[i], oldNumber = i + 1)
            for (j in start until endB) out += DiffLine(DiffKind.ADDED, b[j], newNumber = j + 1)
        } else {
            // Longest common subsequence of the differing middle.
            val table = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
                table[i][j] = if (a[start + i] == b[start + j]) table[i + 1][j + 1] + 1
                else maxOf(table[i + 1][j], table[i][j + 1])
            }
            var i = 0
            var j = 0
            while (i < n && j < m) {
                when {
                    a[start + i] == b[start + j] -> {
                        out += DiffLine(DiffKind.SAME, a[start + i], start + i + 1, start + j + 1); i++; j++
                    }
                    table[i + 1][j] >= table[i][j + 1] -> {
                        out += DiffLine(DiffKind.REMOVED, a[start + i], oldNumber = start + i + 1); i++
                    }
                    else -> {
                        out += DiffLine(DiffKind.ADDED, b[start + j], newNumber = start + j + 1); j++
                    }
                }
            }
            while (i < n) { out += DiffLine(DiffKind.REMOVED, a[start + i], oldNumber = start + i + 1); i++ }
            while (j < m) { out += DiffLine(DiffKind.ADDED, b[start + j], newNumber = start + j + 1); j++ }
        }

        for (k in 0 until a.size - endA) out += DiffLine(DiffKind.SAME, a[endA + k], endA + k + 1, endB + k + 1)
        return out
    }

    fun added(diff: List<DiffLine>): Int = diff.count { it.kind == DiffKind.ADDED }
    fun removed(diff: List<DiffLine>): Int = diff.count { it.kind == DiffKind.REMOVED }

    /**
     * Keeps [context] unchanged lines around each change and folds the rest
     * into one SKIPPED line saying how many were left out.
     */
    fun withContext(diff: List<DiffLine>, context: Int = 3): List<DiffLine> {
        if (diff.none { it.kind != DiffKind.SAME }) return diff
        val keep = BooleanArray(diff.size)
        diff.forEachIndexed { index, line ->
            if (line.kind != DiffKind.SAME) {
                for (k in (index - context).coerceAtLeast(0)..(index + context).coerceAtMost(diff.lastIndex)) keep[k] = true
            }
        }
        val out = ArrayList<DiffLine>()
        var skipped = 0
        fun flush() {
            if (skipped > 0) {
                out += DiffLine(DiffKind.SKIPPED, "$skipped unchanged line${if (skipped == 1) "" else "s"}")
                skipped = 0
            }
        }
        diff.forEachIndexed { index, line ->
            if (keep[index]) { flush(); out += line } else skipped++
        }
        flush()
        return out
    }

    private fun lines(text: String): List<String> =
        if (text.isEmpty()) emptyList() else text.replace("\r\n", "\n").removeSuffix("\n").split('\n')
}

/**
 * The revisions of every file one project's preview has seen, oldest first.
 * Consecutive identical contents collapse into one revision, and each file
 * keeps a bounded number so a long session cannot grow it without limit.
 */
class ArtifactHistory(private val maxPerFile: Int = 20) {

    private val files = HashMap<String, ArrayList<Revision>>()
    private val counters = HashMap<String, Int>()

    /** Saves [content] as a new revision of [path] unless it is what the file already holds. */
    @Synchronized
    fun record(path: String, content: String, at: Long = System.currentTimeMillis()): Revision? {
        val key = key(path)
        val list = files.getOrPut(key) { ArrayList() }
        if (list.lastOrNull()?.content == content) return null
        val number = (counters[key] ?: 0) + 1
        counters[key] = number
        val revision = Revision(number, content, at)
        list += revision
        while (list.size > maxPerFile) list.removeAt(0)
        return revision
    }

    @Synchronized
    fun revisions(path: String): List<Revision> = files[key(path)]?.toList().orEmpty()

    /** True once a file has changed at least once: the version selector and Diff tab appear. */
    @Synchronized
    fun hasHistory(path: String): Boolean = (files[key(path)]?.size ?: 0) > 1

    /** The text of revision [index] (0 = oldest kept), clamped into range. */
    @Synchronized
    fun contentAt(path: String, index: Int): String? {
        val list = files[key(path)] ?: return null
        if (list.isEmpty()) return null
        return list[index.coerceIn(0, list.lastIndex)].content
    }

    /** Diff from revision [fromIndex] to [toIndex] (indexes into [revisions]). */
    fun diff(path: String, fromIndex: Int, toIndex: Int): List<DiffLine> {
        val old = contentAt(path, fromIndex) ?: return emptyList()
        val new = contentAt(path, toIndex) ?: return emptyList()
        return LineDiff.diff(old, new)
    }

    private fun key(path: String) = path.replace('\\', '/').trimStart('/')

    companion object {
        /** `v<number>`, or `Latest` for the newest of [count] revisions. */
        fun label(index: Int, count: Int, number: Int): String =
            if (count > 0 && index == count - 1) "Latest" else "v$number"
    }
}

/**
 * Process-wide histories, one per project, fed by the file tools and the
 * preview. Only files the preview can show are kept, and only small ones.
 */
object ArtifactHistoryStore {

    private const val MAX_BYTES = 400_000L
    private val byProject = ConcurrentHashMap<String, ArtifactHistory>()

    fun forProject(projectId: String): ArtifactHistory = byProject.getOrPut(projectId) { ArtifactHistory() }

    fun isTracked(path: String): Boolean = when (PreviewArtifacts.kindOf(path)) {
        ArtifactKind.WEB_PAGE, ArtifactKind.STYLE, ArtifactKind.SCRIPT, ArtifactKind.SVG,
        ArtifactKind.MARKDOWN, ArtifactKind.CSV, ArtifactKind.JSON, ArtifactKind.TEXT -> true
        else -> false
    }

    /** Records what [root]/[relativePath] holds now; silently ignores anything else. */
    fun capture(projectId: String, root: File, relativePath: String) {
        if (projectId.isBlank() || !isTracked(relativePath)) return
        runCatching {
            val clean = relativePath.replace('\\', '/').trimStart('/')
            if (clean.split('/').any { it.startsWith(".") || it == "node_modules" }) return
            val file = File(root, clean)
            val inside = file.canonicalPath.startsWith(root.canonicalPath + File.separator)
            if (inside && file.isFile && file.length() <= MAX_BYTES) {
                forProject(projectId).record(clean, file.readText())
            }
        }
    }

    fun clear(projectId: String) { byProject.remove(projectId) }
}
