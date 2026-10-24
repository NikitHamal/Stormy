package com.oro.app.data.ai.agent

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.Tool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The tool instructions for models that take no tools through the API: web
 * chat bridges (K2 Horizon, Qwen, Ptero, Mercury) and providers serving a
 * model without function calling.
 *
 * ### Why raw parameter values
 *
 * The protocol used to ask for `<tool_call>{"name": ..., "arguments": {...}}`.
 * Live runs showed where that breaks: not on small calls, but on the call that
 * matters -- write_file with a 300-line page, where every quote and newline of
 * the file has to be JSON-escaped by a model that is busy writing HTML. One
 * missed `\"` and the whole call is lost. The canonical format here takes each
 * argument as raw text between tags, exactly as it should land on disk, which
 * is also the template Qwen3-Coder and several open models were trained on.
 * Lists and objects stay JSON (they are short); every older format the parser
 * understands is still accepted.
 *
 * The rendered text is static per tool list, so it sits in the cacheable
 * prefix of the system prompt.
 */
object TextToolProtocol {

    /**
     * Tools left out of the text-mode catalogue (still executed if called):
     * multi_edit needs a JSON array of strings -- the escaping this protocol
     * exists to avoid -- and several edit_file calls in one reply do the same
     * job, in order.
     */
    val TEXT_MODE_HIDDEN: Set<String> = setOf("multi_edit")

    /**
     * @param wholeFiles the model's output limit is large (see ModelProfile.wholeFiles):
     *   files are written whole in one call instead of first part plus appends.
     */
    fun render(tools: List<Tool>, wholeFiles: Boolean = false): String = buildString {
        appendLine("# Calling tools")
        appendLine("You have no built-in tool API here. The Oro app on the user's device runs tools for you:")
        appendLine("write tool calls, stop, and the app replies with their results. Use exactly this format:")
        appendLine()
        appendLine("<tool_call>")
        appendLine("<function=TOOL_NAME>")
        appendLine("<parameter=NAME>value</parameter>")
        appendLine("</function>")
        appendLine("</tool_call>")
        appendLine()
        appendLine("Rules:")
        appendLine("- Values are raw text, exactly as they should be saved: never escape quotes or newlines and never")
        appendLine("  wrap a value in JSON or quotes. File contents go in full -- never \"...\" or \"rest unchanged\".")
        appendLine("- Numbers and true/false are written plainly. Lists and objects are JSON, e.g.")
        appendLine("  <parameter=questions>[{\"question\": \"Which colour?\"}]</parameter>.")
        appendLine("- Several calls may go in one reply; they run in order. Several edit_file calls on one file are fine.")
        appendLine("- After your last call, STOP. Never write a <tool_result> yourself and never claim a tool ran")
        appendLine("  before you have its result.")
        appendLine("- Act, don't narrate: if you say you will change a file, the same reply must contain the call.")
        if (wholeFiles) {
            appendLine("- Write each file whole in one write_file call, however long. Never stop halfway through a call.")
        } else {
            appendLine("- A very long file: write the first part with write_file, then add the rest with further")
            appendLine("  write_file calls with append=true. Never stop halfway through a call.")
        }
        appendLine("- When the task is done, reply with a short summary and no tool call.")
        appendLine()
        appendLine("Example:")
        appendLine(EXAMPLE)
        appendLine()
        appendLine("Tools (`?` = optional):")
        tools.filter { it.function.name !in TEXT_MODE_HIDDEN }.forEach { tool ->
            append("- ").append(signature(tool)).append(" — ").append(firstSentence(tool.function.description))
            appendLine()
        }
    }.trimEnd()

    private val EXAMPLE = """
        <tool_call>
        <function=edit_file>
        <parameter=path>index.html</parameter>
        <parameter=old_string><h1 class="title">Hi</h1></parameter>
        <parameter=new_string><h1 class="title">Hello, "friend"</h1></parameter>
        </function>
        </tool_call>
    """.trimIndent()

    /**
     * One call in the canonical format, from a name and JSON arguments. Used
     * to render earlier native tool calls into a text transcript, so a model
     * switched to text mode sees its history in the format it must use (models
     * imitate their history more than their instructions).
     */
    fun formatCall(name: String, argumentsJson: String): String = buildString {
        append("<tool_call>\n<function=").append(name).append(">\n")
        val args = runCatching { AiHttp.json.parseToJsonElement(argumentsJson.ifBlank { "{}" }) as? JsonObject }.getOrNull()
        args?.forEach { (key, value) ->
            append("<parameter=").append(key).append('>')
            val text = rawValue(value)
            // Multi-line values (file bodies) start on their own line, as taught.
            if (text.contains('\n')) append('\n').append(text).append('\n') else append(text)
            append("</parameter>\n")
        }
        append("</function>\n</tool_call>")
    }

    /** A JSON argument as the raw text the protocol carries. */
    private fun rawValue(value: JsonElement): String = when (value) {
        is JsonNull -> ""
        is JsonPrimitive -> value.content
        else -> value.toString()
    }

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
