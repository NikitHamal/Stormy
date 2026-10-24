package com.oro.app.data.preview

import java.util.concurrent.ConcurrentHashMap

/**
 * Problems the live preview reported, per project, for the agent to read.
 *
 * The preview runs in its own Activity; the agent runs in the editor's view
 * model. This in-memory bus is the bridge: the preview records console
 * errors, uncaught exceptions and failed resource loads; the agent sees a
 * summary in its context on the next turn and can ask for the details with
 * the `get_preview_errors` tool. Bounded per project and deduplicated, so a
 * page that throws inside requestAnimationFrame cannot flood either side.
 */
object PreviewDiagnostics {

    enum class Level { ERROR, WARNING }

    data class Problem(
        val level: Level,
        val message: String,
        val source: String,
        val line: Int,
        val firstAt: Long,
        val lastAt: Long,
        val count: Int = 1
    )

    private const val MAX_PER_PROJECT = 40
    private val byProject = ConcurrentHashMap<String, ArrayDeque<Problem>>()
    /** When the agent last consumed a project's problems (context summary). */
    private val seenAt = ConcurrentHashMap<String, Long>()

    fun record(projectId: String, level: Level, message: String, source: String = "", line: Int = 0, now: Long = System.currentTimeMillis()) {
        if (projectId.isBlank() || message.isBlank()) return
        val list = byProject.getOrPut(projectId) { ArrayDeque() }
        synchronized(list) {
            val msg = message.trim().take(600)
            val src = source.substringAfterLast('/').substringBefore('?').take(120)
            val i = list.indexOfFirst { it.message == msg && it.source == src && it.line == line }
            if (i >= 0) {
                val p = list.removeAt(i)
                list.addLast(p.copy(lastAt = now, count = p.count + 1))
            } else {
                list.addLast(Problem(level, msg, src, line, now, now))
                while (list.size > MAX_PER_PROJECT) list.removeFirst()
            }
        }
    }

    fun recent(projectId: String, since: Long = 0L): List<Problem> {
        val list = byProject[projectId] ?: return emptyList()
        return synchronized(list) { list.filter { it.lastAt > since }.toList() }
    }

    fun clear(projectId: String) {
        byProject.remove(projectId)
        seenAt.remove(projectId)
    }

    /** The full report, for the tool. */
    fun report(projectId: String, includeWarnings: Boolean = true): String {
        val all = recent(projectId).filter { includeWarnings || it.level == Level.ERROR }
        if (all.isEmpty()) return "The preview has reported no errors" + if (includeWarnings) " or warnings." else "."
        return buildString {
            append("Preview problems, most recent last (").append(all.size).append("):\n")
            all.forEach { p -> append("- ").append(line(p)).append('\n') }
        }.trimEnd()
    }

    /**
     * New errors since the agent last looked, as a short context section, or
     * "" when there are none. Marks them seen.
     */
    fun takeContextSummary(projectId: String, max: Int = 6, now: Long = System.currentTimeMillis()): String {
        val since = seenAt[projectId] ?: 0L
        val fresh = recent(projectId, since).filter { it.level == Level.ERROR }
        seenAt[projectId] = now
        if (fresh.isEmpty()) return ""
        return buildString {
            append("\n\n# Live preview errors\nThe user's live preview reported these errors since your last turn. ")
            append("If they relate to the task or to files you changed, fix them:\n")
            fresh.takeLast(max).forEach { append("- ").append(line(it)).append('\n') }
            if (fresh.size > max) append("(${fresh.size - max} more; call get_preview_errors for all)\n")
        }.trimEnd()
    }

    private fun line(p: Problem): String = buildString {
        append(if (p.level == Level.ERROR) "error" else "warning").append(": ").append(p.message)
        if (p.source.isNotBlank()) append(" (").append(p.source).append(if (p.line > 0) ":${p.line}" else "").append(')')
        if (p.count > 1) append(" ×").append(p.count)
    }
}
