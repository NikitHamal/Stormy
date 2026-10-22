package com.oro.app.data.ai.agent

import com.oro.app.data.ai.tools.JsonParserUtils
import com.oro.app.data.ai.tools.ToolAliases
import com.oro.app.data.ai.mcp.McpNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File

/**
 * How much Orobi may do without asking.
 *
 * Leashed: every change to the project (write, edit, delete, move, an MCP
 * action) waits for the user's OK, with the change shown. Unleashed: fully
 * autonomous, except for the few actions that are hard to undo or sensitive,
 * which still ask ([AutonomyPolicy.dangerReason]). Ordinary deletes of single
 * files are not "dangerous": checkpoints can restore them.
 */
enum class AutonomyMode(val label: String, val summary: String) {
    LEASHED("Leashed", "Asks before every change to your files"),
    UNLEASHED("Unleashed", "Works on its own; still asks before risky steps");

    companion object {
        val DEFAULT = UNLEASHED
        fun fromName(v: String?): AutonomyMode = entries.firstOrNull { it.name == v } ?: DEFAULT
    }
}

object AutonomyPolicy {

    private val MUTATING = setOf("write_file", "edit_file", "multi_edit", "delete_file", "move_file", "add_ui_kit")
    private val SENSITIVE_FILE = Regex("""(^|/)(\.env(\..*)?|.*\.(pem|key|p12|jks|keystore)|id_rsa.*|\.npmrc|\.git/.*)$""", RegexOption.IGNORE_CASE)
    private val RISKY_MCP = Regex("delete|drop|remove|destroy|merge|push|deploy|execute|sql|transfer|revoke|archive", RegexOption.IGNORE_CASE)

    /** Whether a call changes something (and so needs a Leashed OK). */
    fun isChange(toolName: String): Boolean {
        val n = ToolAliases.canonicalName(toolName)
        return n in MUTATING || n == "open_pull_request" || McpNames.isMcp(n)
    }

    /** Why this call needs the user even when Unleashed, or null. */
    fun dangerReason(toolName: String, arguments: String, projectRoot: File?): String? {
        val n = ToolAliases.canonicalName(toolName)
        val args = JsonParserUtils.parseToJsonObject(arguments) ?: JsonObject(emptyMap())
        fun str(k: String) = (args[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val path = str("path").ifEmpty { str("source") }
        when (n) {
            "delete_file" -> {
                if (path.isEmpty() || path == "." || path == "/") return "Delete the whole project"
                val f = projectRoot?.let { File(it, path) }
                if (f != null && f.isDirectory) {
                    val count = f.walkTopDown().filter { it.isFile }.take(1000).count()
                    if (count > 3) return "Delete the folder $path ($count files)"
                }
            }
            "open_pull_request" -> return "Open a pull request on GitHub" + str("repo").let { if (it.isEmpty()) "" else " ($it)" }
            "move_file" -> if ((args["overwrite"] as? JsonPrimitive)?.contentOrNull == "true") {
                return "Move $path over an existing ${str("destination")}"
            }
        }
        if (n in MUTATING && (SENSITIVE_FILE.containsMatchIn(path) || SENSITIVE_FILE.containsMatchIn(str("destination")))) {
            return "Change a sensitive file ($path)"
        }
        if (McpNames.isMcp(n) && RISKY_MCP.containsMatchIn(n.substringAfterLast("__"))) {
            return "Run ${n.substringAfterLast("__")} on ${n.removePrefix(McpNames.PREFIX).substringBefore("__")}"
        }
        return null
    }

    /** One-line action and a short detail for the approval sheet. */
    fun describe(toolName: String, arguments: String): Pair<String, String?> {
        val n = ToolAliases.canonicalName(toolName)
        val args = JsonParserUtils.parseToJsonObject(arguments) ?: JsonObject(emptyMap())
        fun str(k: String) = (args[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val path = str("path")
        return when (n) {
            "write_file" -> (if (str("append") == "true") "Add to $path" else "Write $path") to
                str("content").lines().take(14).joinToString("\n").take(900)
            "edit_file" -> "Edit $path" to ("- " + str("old_string").take(400).replace("\n", "\n- ") + "\n+ " + str("new_string").take(400).replace("\n", "\n+ "))
            "multi_edit" -> "Edit $path" to "Several replacements in one file"
            "delete_file" -> "Delete $path" to null
            "move_file" -> "Move ${str("source")} to ${str("destination")}" to null
            "open_pull_request" -> "Open pull request: ${str("title")}" to str("body").take(900)
            else -> if (McpNames.isMcp(n)) "Use ${n.removePrefix(McpNames.PREFIX).replace("__", ": ")}" to args.toString().take(600)
            else n to null
        }
    }
}
