package com.oro.app.data.ai.agent

import com.oro.app.data.ai.Tool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The tool instructions for models that take no tools through the API: web
 * chat bridges (K2 Horizon) and providers serving a model without function
 * calling.
 *
 * Written from live K2 runs. What made those models fail was not the call
 * format -- they emit `<tool_call>` JSON or their own template reliably, and
 * [TextToolCallParser] reads both -- but a catalogue that gave them nothing to
 * go on: parameter names without types or required markers, nested shapes
 * (multi_edit's `edits`, ask_user's `questions`) left unspecified, and
 * descriptions cut at "e.g." mid-sentence. Here every tool is one signature
 * line with types, `?` for optional, nested object shapes spelled out and a
 * complete first sentence, plus one worked example of a full round trip.
 */
object TextToolProtocol {

    fun render(tools: List<Tool>): String = buildString {
        appendLine("# Calling tools")
        appendLine("You have no built-in tool API here. The Oro app on the user's device runs tools for you:")
        appendLine("write a <tool_call> block and the app executes it on the user's real project files and")
        appendLine("replies with a <tool_result>. Rules:")
        appendLine("- One block per call. Several independent read-only calls may go in one reply.")
        appendLine("- Inside the block: one JSON object {\"name\": ..., \"arguments\": {...}}. Escape quotes and")
        appendLine("  newlines inside strings (\\\" and \\n). File contents go in full, never \"...\".")
        appendLine("- After your last block, stop and wait for the results. Never write a <tool_result> yourself")
        appendLine("  and never claim a tool ran before you have its result.")
        appendLine("- Act, don't narrate: if you say you will change a file, the same reply must contain the call.")
        appendLine("- When the task is done, reply with a short summary and no <tool_call> block.")
        appendLine()
        appendLine("Example:")
        appendLine("<tool_call>")
        appendLine("{\"name\": \"edit_file\", \"arguments\": {\"path\": \"index.html\", \"old_string\": \"<h1>Hi</h1>\", \"new_string\": \"<h1>Hello</h1>\"}}")
        appendLine("</tool_call>")
        appendLine()
        appendLine("Tools (`?` = optional):")
        tools.forEach { tool ->
            append("- ").append(signature(tool)).append(" — ").append(firstSentence(tool.function.description))
            appendLine()
        }
    }.trimEnd()

    /** `edit_file(path: string, old_string: string, replace_all?: boolean)`. */
    fun signature(tool: Tool): String {
        val schema = tool.function.parameters as? JsonObject
        return tool.function.name + "(" + fields(schema) + ")"
    }

    private fun fields(schema: JsonObject?): String {
        val props = schema?.get("properties") as? JsonObject ?: return ""
        val required = (schema["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty().toSet()
        return props.entries.joinToString(", ") { (name, spec) ->
            name + (if (name in required) "" else "?") + ": " + type(spec)
        }
    }

    private fun type(spec: JsonElement): String {
        val obj = spec as? JsonObject ?: return "any"
        (obj["enum"] as? JsonArray)?.let { values ->
            return values.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString("|") { "\"$it\"" }
        }
        return when ((obj["type"] as? JsonPrimitive)?.content) {
            "array" -> {
                val items = obj["items"]
                "[" + (items?.let { type(it) } ?: "any") + ", ...]"
            }
            "object" -> if (obj["properties"] is JsonObject) "{" + fields(obj) + "}" else "object"
            "integer" -> "int"
            null -> "any"
            else -> (obj["type"] as JsonPrimitive).content
        }
    }

    private val SENTENCE_END = Regex("""(?<!\be\.g)(?<!\bi\.e)(?<!\betc)\.\s+(?=[A-Z])""")

    /** The first full sentence; never cut inside "e.g." or "i.e.". */
    fun firstSentence(description: String): String {
        val flat = description.replace(Regex("\\s+"), " ").trim()
        val end = SENTENCE_END.find(flat)?.range?.first ?: flat.length
        val sentence = flat.substring(0, end).trim().removeSuffix(".")
        return (if (sentence.length > 220) sentence.take(217).trimEnd() + "…" else sentence) + "."
    }
}
