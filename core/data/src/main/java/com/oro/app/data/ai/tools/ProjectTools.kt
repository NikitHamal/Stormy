package com.oro.app.data.ai.tools

import com.oro.app.data.ai.ToolCallResponse
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * The project-file tools on a project root, with no repository, Android or
 * network dependency.
 *
 * [ToolExecutor] runs the canonical file tools through here, and the
 * golden-task tests drive the same code end to end (fake model, real files on
 * a temp project), so what the tests prove is what the app does.
 */
object ProjectTools {

    /** Tools [dispatch] handles. web_fetch shares WorkspaceTools but needs the network. */
    val NAMES: Set<String> = setOf(
        "read_file", "write_file", "edit_file", "multi_edit", "list_files", "glob", "grep", "delete_file", "move_file"
    )

    /** Writes whose result carries a quick check of the file just written. */
    val QUICK_CHECKED: Set<String> = setOf("write_file", "edit_file", "multi_edit")

    /** Runs a canonical file tool; null when [name] is not one. */
    fun dispatch(ws: WorkspaceTools, name: String, args: JsonObject): ToolOutcome? = when (name) {
        "read_file" -> ws.readFile(args)
        "write_file" -> ws.writeFile(args)
        "edit_file" -> ws.editFile(args)
        "multi_edit" -> ws.multiEdit(args)
        "list_files" -> ws.listFiles(args)
        "glob" -> ws.glob(args)
        "grep" -> ws.grep(args)
        "delete_file" -> ws.deleteFile(args)
        "move_file" -> ws.moveFile(args)
        else -> null
    }

    /**
     * [outcome]'s result with the quick check of the files a write changed
     * appended: self-contained defects (cut off, unbalanced brackets, invalid
     * JSON, duplicate ids) come back with the write, saving a check_syntax turn.
     */
    fun withQuickCheck(root: File, name: String, outcome: ToolOutcome): ToolResult {
        if (name !in QUICK_CHECKED || !outcome.result.success || outcome.changes.isEmpty()) return outcome.result
        val note = runCatching { CraftTools.quickCheckNote(root, outcome.changes) }.getOrNull() ?: return outcome.result
        return outcome.result.copy(output = outcome.result.output + "\n\n" + note)
    }

    /**
     * One call the way ToolExecutor runs it: arguments parsed leniently,
     * aliases mapped onto the canonical tools, the path policy enforced, then
     * dispatched. Null when the tool is not a project-file tool.
     */
    fun execute(root: File, ws: WorkspaceTools, call: ToolCallResponse): ToolResult? {
        val parsed = JsonParserUtils.parseToJsonObject(call.function.arguments)
            ?: return ToolResult(
                false, "",
                "Could not parse the arguments as a JSON object. Send the arguments as valid JSON " +
                    "matching the tool's schema (strings quoted and escaped, no trailing commas)."
            )
        val (name, args) = ToolAliases.normalise(call.function.name, parsed)
        if (name !in NAMES) return null
        AgentPathPolicy.denyIfAnyPathBlocked(args, root)?.let { return ToolResult(false, "", it) }
        val outcome = dispatch(ws, name, args) ?: return null
        return withQuickCheck(root, name, outcome)
    }
}
