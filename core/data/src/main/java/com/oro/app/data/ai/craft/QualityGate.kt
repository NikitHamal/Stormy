package com.oro.app.data.ai.craft

import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.tools.JsonParserUtils
import com.oro.app.data.ai.tools.ToolAliases
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * Turns a [QualityAuditor] report into the message that sends the agent back
 * for a fix pass when it declares itself done.
 */
object QualityGate {

    /** Most issues listed per pass; more than this overwhelms small models. */
    private const val MAX_LISTED = 15

    /** Project-relative paths a successful write-type call changed. */
    fun touchedPaths(call: ToolCallResponse): List<String> {
        val name = ToolAliases.canonicalName(call.function.name)
        if (name == "add_ui_kit") return emptyList() // vetted
        val args = JsonParserUtils.parseToJsonObject(call.function.arguments) ?: return emptyList()
        val (_, norm) = ToolAliases.normalise(name, args)
        return listOf("path", "destination")
            .mapNotNull { (norm[it] as? JsonPrimitive)?.content }
            .map { it.trim().removePrefix("./").trimStart('/') }
            .filter { it.isNotEmpty() }
    }

    /**
     * Null when the touched files are clean (or nothing was touched);
     * otherwise instructions listing the defects.
     */
    fun review(root: File, touched: Set<String>): String? {
        if (touched.isEmpty() || !root.isDirectory) return null
        val report = QualityAuditor(root).audit(touched + ".")
        val blocking = report.blocking
        if (blocking.isEmpty()) return null
        return buildString {
            append("Automatic quality review (by the Oro app, not the user) found ")
            append("${blocking.size} problem${if (blocking.size == 1) "" else "s"} in the files you changed:\n")
            blocking.sortedBy { it.severity.ordinal }.take(MAX_LISTED).forEach { append("- ").append(it.render()).append('\n') }
            if (blocking.size > MAX_LISTED) append("- … and ${blocking.size - MAX_LISTED} more (run check_syntax with no path to see all).\n")
            append(
                "\nFix each one now: read the relevant part of the file, make the edit, then run " +
                    "check_syntax (no path) to confirm. If an item is a deliberate choice, leave it and say why. " +
                    "When everything is fixed, reply with your final summary for the user."
            )
        }
    }
}
