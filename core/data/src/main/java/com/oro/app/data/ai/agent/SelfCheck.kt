package com.oro.app.data.ai.agent

import com.oro.app.data.preview.Device
import com.oro.app.data.preview.PagePreviewers
import com.oro.app.data.preview.PreviewDiagnostics
import java.io.File

/**
 * The check Orobi runs on its own work before it says "done".
 *
 * For the files a run changed: render the affected pages at phone width and
 * read the layout facts (sideways scrolling, broken images), collect the
 * preview's new errors, and run the syntax check on changed HTML/CSS/JS. If
 * anything is wrong the run gets one more turn with the findings, so the user
 * receives working pages instead of a list of problems.
 */
object SelfCheck {

    /** A readable report, or null when everything checked out. */
    suspend fun run(
        projectId: String,
        root: File,
        changed: List<String>,
        /** Only preview errors seen since this time (the run's start) count. */
        since: Long = 0L,
        syntaxCheck: suspend (path: String) -> String?
    ): String? {
        val web = changed.filter { it.substringAfterLast('.').lowercase() in setOf("html", "htm", "css", "js", "mjs") }
            .filter { File(root, it).isFile }
        if (web.isEmpty()) return null
        val problems = mutableListOf<String>()

        web.take(8).forEach { path -> syntaxCheck(path)?.let { problems += "$path: $it" } }

        // Pages to look at: changed HTML, or index.html when only CSS/JS changed.
        val pages = web.filter { it.endsWith(".html") || it.endsWith(".htm") }.ifEmpty {
            listOf("index.html").filter { File(root, it).isFile }
        }.take(3)
        val previewer = PagePreviewers.instance
        if (previewer != null) {
            for (page in pages) {
                val shot = runCatching { previewer.capture(root, page, listOf(Device.PHONE)).firstOrNull() }.getOrNull() ?: continue
                val m = shot.metrics
                if ("HORIZONTAL OVERFLOW" in m) problems += "$page scrolls sideways on a phone. " + m.lineSequence().firstOrNull { it.startsWith("Elements wider") }.orEmpty()
                Regex("(\\d+) images failed to load").find(m)?.let { problems += "$page: ${it.value} (check paths)." }
            }
        }
        val errors = PreviewDiagnostics.recent(projectId, since).filter { it.level == PreviewDiagnostics.Level.ERROR }.takeLast(5)
        errors.forEach { problems += "Preview error: ${it.message}" + if (it.source.isNotBlank()) " (${it.source}:${it.line})" else "" }

        if (problems.isEmpty()) return null
        return buildString {
            append("Self-check of your changes found ${problems.size} problem${if (problems.size == 1) "" else "s"}:\n")
            problems.distinct().take(10).forEach { append("- ").append(it).append('\n') }
            append("Fix them now (re-read files before editing), then reply with a short summary. ")
            append("If one is not caused by your change or cannot be fixed, say so briefly.")
        }
    }

    /** "ok"/"no issues" style outputs from check_syntax mean clean. */
    fun syntaxProblem(output: String, success: Boolean): String? {
        if (!success) return output.lineSequence().firstOrNull()?.take(200)
        val t = output.lowercase()
        return if (t.contains("error") || t.contains("unclosed") || t.contains("unbalanced") || t.contains("mismatch")) output.lines().take(3).joinToString(" ").take(240) else null
    }
}
