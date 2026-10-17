package com.oro.app.data.ai.agent

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.tools.JsonParserUtils
import com.oro.app.data.ai.tools.ToolAliases
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Recovers tool calls a model wrote as text instead of using native function
 * calling.
 *
 * Happens with models served without tool support, with some open models that
 * fall back to their chat template's own syntax, and occasionally mid-run on
 * models that otherwise call tools natively. Formats handled (all seen in
 * practice):
 *  - `<tool_call>{"name": ..., "arguments": {...}}</tool_call>` (Hermes, Qwen)
 *  - `<function=name><parameter=key>value</parameter></function>` (Qwen3-Coder)
 *  - `<function=name>{...json...}</function>`
 *  - `[TOOL_CALLS][{"name": ..., "arguments": {...}}]` (Mistral)
 *  - fenced ```json blocks holding a call object or an array of them
 *  - a reply that is nothing but one call object
 *
 * Deliberately NOT handled: `read_file(path="x")` call syntax in prose. The
 * old parser accepted it, which meant an explanation such as "I would use
 * delete_file(path='index.html')" executed the deletion.
 *
 * Only names that resolve (through [ToolAliases]) to a tool actually offered
 * are accepted, so example JSON in an answer is not mistaken for a call.
 */
object TextToolCallParser {

    data class Parsed(
        /** The reply with the tool-call markup removed. */
        val visibleText: String,
        val calls: List<ToolCallResponse>
    )

    private val TOOL_CALL_TAG = Regex("""(?is)<tool_call>\s*(.*?)\s*(?:</tool_call>|$)""")
    private val FUNCTION_TAG = Regex("""(?is)<function\s*=\s*([\w.\-]+)\s*>(.*?)</function>""")
    private val PARAMETER_TAG = Regex("""(?is)<parameter\s*=\s*([\w.\-]+)\s*>\r?\n?(.*?)\r?\n?</parameter>""")
    private val FENCE = Regex("""(?s)```(?:json|tool_call|tool|javascript|js)?[ \t]*\r?\n(.*?)\r?\n?```""")
    private const val MISTRAL_MARKER = "[TOOL_CALLS]"

    fun parse(content: String, knownTools: Set<String>): Parsed {
        if (content.isBlank()) return Parsed(content, emptyList())
        val known = knownTools.map { ToolAliases.canonicalName(it) }.toSet()
        val calls = mutableListOf<ToolCallResponse>()
        var text = content

        fun accept(name: String?, arguments: JsonElement?): Boolean {
            if (name.isNullOrBlank()) return false
            if (ToolAliases.canonicalName(name) !in known) return false
            val args = when (arguments) {
                null -> "{}"
                is JsonPrimitive -> arguments.contentOrNull?.takeIf { it.isNotBlank() } ?: "{}"
                else -> arguments.toString()
            }
            calls += ToolCallResponse(
                id = "call_text_${calls.size}_${System.nanoTime() % 1_000_000}",
                type = "function",
                function = FunctionCall(name = name.trim(), arguments = args)
            )
            return true
        }

        fun acceptObject(element: JsonElement?): Boolean = when (element) {
            is JsonObject -> {
                val fn = element["function"] as? JsonObject
                val name = str(fn?.get("name")) ?: str(element["name"]) ?: str(element["tool"])
                    ?: str(element["tool_name"])
                val args = fn?.get("arguments") ?: element["arguments"] ?: element["parameters"]
                    ?: element["args"] ?: element["input"]
                accept(name, args)
            }
            is JsonArray -> element.map { acceptObject(it) }.any { it }
            else -> false
        }

        // 1. <tool_call> blocks.
        TOOL_CALL_TAG.findAll(text).toList().forEach { m ->
            val inner = m.groupValues[1]
            val handled = FUNCTION_TAG.find(inner)?.let { acceptFunctionTag(it, ::accept) }
                ?: acceptObject(parseJson(extractJson(inner) ?: inner))
            if (handled) text = text.replace(m.value, "")
        }

        // 2. Bare <function=...> blocks.
        FUNCTION_TAG.findAll(text).toList().forEach { m ->
            if (acceptFunctionTag(m, ::accept)) text = text.replace(m.value, "")
        }

        // 3. Mistral marker.
        val marker = text.indexOf(MISTRAL_MARKER)
        if (marker >= 0) {
            val rest = text.substring(marker + MISTRAL_MARKER.length)
            extractJson(rest)?.let { json ->
                if (acceptObject(parseJson(json))) {
                    text = text.substring(0, marker) + rest.substringAfter(json)
                }
            }
        }

        // 4. Fenced JSON blocks.
        if (calls.isEmpty()) {
            FENCE.findAll(text).toList().forEach { m ->
                val body = m.groupValues[1].trim()
                if ((body.startsWith("{") || body.startsWith("[")) && acceptObject(parseJson(body))) {
                    text = text.replace(m.value, "")
                }
            }
        }

        // 5. The whole reply is one call object.
        if (calls.isEmpty()) {
            val trimmed = text.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}") && acceptObject(parseJson(trimmed))) {
                text = ""
            }
        }

        return Parsed(text.replace(Regex("\n{3,}"), "\n\n").trim(), calls)
    }

    private fun acceptFunctionTag(
        match: MatchResult,
        accept: (String?, JsonElement?) -> Boolean
    ): Boolean {
        val name = match.groupValues[1]
        val body = match.groupValues[2]
        val params = PARAMETER_TAG.findAll(body).toList()
        val args: JsonElement? = if (params.isNotEmpty()) {
            JsonObject(params.associate { p ->
                val raw = p.groupValues[2]
                // Numbers and booleans stay typed; everything else is text.
                val value: JsonElement = when {
                    raw.trim() == "true" || raw.trim() == "false" -> JsonPrimitive(raw.trim().toBoolean())
                    raw.trim().matches(Regex("-?\\d+")) -> JsonPrimitive(raw.trim().toLong())
                    raw.trim().startsWith("[") -> parseJson(raw.trim()) ?: JsonPrimitive(raw)
                    else -> JsonPrimitive(raw)
                }
                p.groupValues[1] to value
            })
        } else {
            extractJson(body)?.let { parseJson(it) }
        }
        return accept(name, args)
    }

    private fun str(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull

    private fun parseJson(text: String): JsonElement? =
        runCatching { AiHttp.json.parseToJsonElement(text) }.getOrNull()
            ?: JsonParserUtils.parseToJsonObject(text)

    /**
     * The first balanced JSON object or array in [text], honouring strings and
     * escapes so braces inside file content do not end it early.
     */
    fun extractJson(text: String): String? {
        val start = text.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
