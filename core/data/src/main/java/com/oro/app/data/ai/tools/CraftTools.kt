package com.oro.app.data.ai.tools

import com.oro.app.data.ai.craft.QualityAuditor
import com.oro.app.data.ai.craft.UiKit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File

/** Execution of the craft tools: check_syntax (project review) and add_ui_kit. */
internal object CraftTools {

    val UI_KIT_PATHS = listOf("${UiKit.DIR}/oro.css", "${UiKit.DIR}/oro.js")

    /**
     * With a path: the per-file syntax check plus the review of that file.
     * Without: the review of the whole project.
     */
    suspend fun check(root: File, path: String?, perFile: suspend () -> ToolResult?): ToolResult {
        if (path == null) return ToolResult(true, QualityAuditor(root).audit().render())
        if (!File(root, path).exists()) return ToolResult(false, "", "$path does not exist in the project.")
        val syntax = runCatching { perFile() }.getOrNull()
        val review = QualityAuditor(root).audit(setOf(path))
        val parts = buildList {
            syntax?.let { add(if (it.success) it.output.trim() else "Error: ${it.error ?: it.output}") }
            if (review.issues.isNotEmpty() || syntax == null) add(review.render())
        }.filter { it.isNotBlank() }
        return ToolResult(true, parts.joinToString("\n\n").ifBlank { "No issues found in $path." })
    }

    /**
     * A few lines for a write's result when the file just written has a
     * self-contained defect (cut off, unbalanced brackets, invalid JSON), so
     * the model fixes it in its next step instead of finding out at the end --
     * one round trip, not a check_syntax call plus a fix. Null when clean.
     */
    fun quickCheckNote(root: File, changes: List<FileChange>): String? {
        val auditor = QualityAuditor(root)
        val issues = changes
            .filter { it.type == FileChangeType.CREATED || it.type == FileChangeType.MODIFIED }
            .flatMap { change -> change.newContent?.let { auditor.quickCheck(change.path, it) }.orEmpty() }
        if (issues.isEmpty()) return null
        return buildString {
            append("Quick check of the written file found ")
            append(if (issues.size == 1) "a problem:" else "${issues.size} problems:")
            issues.take(MAX_QUICK_ISSUES).forEach { append("\n- ").append(it.render()) }
            if (issues.size > MAX_QUICK_ISSUES) append("\n- … and ${issues.size - MAX_QUICK_ISSUES} more")
            append("\nFix ").append(if (issues.size == 1) "it" else "them")
            append(" now, unless you are still writing this file in parts.")
        }
    }

    private const val MAX_QUICK_ISSUES = 4

    fun addUiKit(root: File, args: JsonObject): ToolOutcome {
        val hue = (args["brand_hue"] as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toIntOrNull() }
        val written = UiKit.install(root, hue)
        val changes = written.map { (rel, old, new) ->
            FileChange(rel, if (old == null) FileChangeType.CREATED else FileChangeType.MODIFIED, old, new)
        }
        return ToolOutcome(ToolResult(true, UiKit.usage()), changes)
    }
}
