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
 *  - `<tool_call><function=name><parameter=key>raw value</parameter></function></tool_call>`,
 *    the format [TextToolProtocol] teaches (Qwen3-Coder's template), also bare
 *    and with `<function name="x">` / `<parameter name="x">` spellings
 *  - `<tool_call>{"name": ..., "arguments": {...}}</tool_call>` (Hermes, Qwen)
 *  - GLM / K2-style key-value blocks:
 *    `<tool_call>name<arg_key>path</arg_key><arg_value>a.html</arg_value></tool_call>`,
 *    including namespaced special tokens (`<ifm|tool_call>`, K2 Horizon)
 *  - `[TOOL_CALLS][{"name": ..., "arguments": {...}}]` (Mistral)
 *  - Kimi K2 sections: `<|tool_call_begin|>functions.read_file:0<|tool_call_argument_begin|>{...}<|tool_call_end|>`
 *  - DeepSeek: `<｜tool▁call▁begin｜>function<｜tool▁sep｜>read_file` + a JSON body
 *  - fenced ```json blocks holding a call object or an array of them
 *  - a reply that is nothing but one call object
 *
 * Weak models drop closing tags; a missing `</parameter>` or `</function>`
 * inside a closed `<tool_call>` block is tolerated. What is never accepted is
 * a call that may have been cut off -- an unclosed block whose last value is
 * still open, typically because the output limit hit in the middle of a file.
 * Those land in [Parsed.incomplete] and are not run: a truncated write_file
 * would replace a good file with half of one.
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
        val calls: List<ToolCallResponse>,
        /** Tools whose call was cut off before it was complete; not in [calls]. */
        val incomplete: List<String> = emptyList(),
        /**
         * Ids of accepted calls whose block was never explicitly closed. Fine
         * after a normal stop (the model just forgot the closer); suspect when
         * the reply ended on the output limit.
         */
        val openEnded: Set<String> = emptySet()
    )

    private val TOOL_CALL_TAG = Regex("""(?is)<tool_call>\s*(.*?)\s*(?:</tool_call>|$)""")
    private val FUNCTION_OPEN = Regex("""(?i)<function\s*(?:=\s*|\s+name\s*=\s*)["']?([\w.\-]+)["']?\s*>""")
    private val PARAMETER_OPEN = Regex("""(?i)<parameter\s*(?:=\s*|\s+name\s*=\s*)["']?([\w.\-]+)["']?\s*>""")
    private const val PARAMETER_CLOSE = "</parameter>"
    private const val FUNCTION_CLOSE = "</function>"
    private val FENCE = Regex("""(?s)```(?:json|tool_call|tool|javascript|js)?[ \t]*\r?\n(.*?)\r?\n?```""")
    private const val MISTRAL_MARKER = "[TOOL_CALLS]"
    /** `<ifm|tool_call>` -> `<tool_call>`: chat-template special tokens with a namespace. */
    private val NAMESPACED_TAG = Regex("""<(/?)[A-Za-z0-9_]+\|""")
    private val KV_PAIR = Regex("""(?s)<arg_key>(.*?)(?:</arg_key>|(?=<arg_value>))\s*<arg_value>(.*?)(?:</arg_value>|(?=<arg_key>)|$)""")
    private val WRAPPER = Regex("""</?tool_calls>""")
    private val KIMI_CALL = Regex(
        """(?s)<\|tool_call_begin\|>\s*([\w.\-]+?)(?::\d+)?\s*<\|tool_call_argument_begin\|>(.*?)(?:<\|tool_call_end\|>|$)"""
    )
    private val KIMI_SECTION = Regex("""<\|tool_calls_section_(?:begin|end)\|>""")
    private val DEEPSEEK_CALL = Regex(
        """(?s)<｜tool▁call▁begin｜>\s*(?:function)?\s*<｜tool▁sep｜>\s*([\w.\-]+)\s*(.*?)(?:<｜tool▁call▁end｜>|$)"""
    )
    private val DEEPSEEK_SECTION = Regex("""<｜tool▁calls▁(?:begin|end)｜>""")
    private val INTEGER = Regex("-?\\d+")
    private val DECIMAL = Regex("-?\\d+\\.\\d+")
    private val JSON_NAME = Regex(""""name"\s*:\s*"([\w.\-]+)"""")

    /**
     * Arguments that are always text. Key-value formats carry raw values, and
     * guessing a type from the value turned a JSON file's `content` into an
     * object (write_file then failed) and "404" into a number.
     */
    private val TEXT_KEYS = setOf(
        "path", "file_path", "content", "old_string", "new_string", "pattern", "query", "url",
        "question", "command", "text", "source", "destination", "description", "summary",
        "key", "value", "include", "header", "label", "name", "title", "body", "repo", "branch",
        "base", "page", "prompt", "task", "note", "action", "detail", "reason", "message", "patch", "input"
    )

    private val VERBATIM_KEYS = setOf("content", "old_string", "new_string", "patch")

    /** Opening tokens of every format above; [visiblePrefixLength] hides from the first one. */
    private val OPENERS = listOf(
        "<tool_call", "<tool_calls", "<function=", "<function name=", "[TOOL_CALLS]", "<|tool_call", "<|tool_calls",
        "<｜tool▁call", "<ifm|tool_call"
    )

    /** Closing tokens: a fabricated result only counts after one of these. */
    private val CLOSERS = listOf("</tool_call>", FUNCTION_CLOSE, "<|tool_call_end|>", "<｜tool▁call▁end｜>", "</ifm|tool_call>")

    /**
     * What a text-mode model writes when, instead of stopping after its calls,
     * it starts inventing their results or the next user turn.
     */
    private val FAKE_RESULT_MARKERS = listOf(
        "<tool_result", "<tool_response", "<|im_start|>", "<|user|>", "<start_of_turn>", "<|eot_id|>",
        "\nTool results:", "\nTool result:", "\nUser:", "\nHuman:", "\nObservation:"
    )

    /** A code fence left empty once the call inside it was taken out. */
    private val EMPTY_FENCE = Regex("```[\\w-]*\\s*```")

    /**
     * How much of a partial, still-streaming reply is safe to show: everything
     * before the first tool-call opener, held back by a few characters while
     * the tail could still be the start of one. Lets the UI stream prose while
     * the call itself (often a whole file) stays off screen.
     */
    fun visiblePrefixLength(streamed: CharSequence, from: Int = 0): Int {
        // Text before [from] was already shown, so it held no opener; an
        // opener can only start in the last few characters before it.
        val scanFrom = (from - 16).coerceAtLeast(0)
        var cut = streamed.length
        for (opener in OPENERS) {
            val i = streamed.indexOf(opener, scanFrom)
            if (i in 0 until cut) cut = i
        }
        if (cut < streamed.length) return cut
        // Hold back a trailing fragment that could still grow into an opener.
        val tailStart = (streamed.length - 16).coerceAtLeast(0)
        for (i in tailStart until streamed.length) {
            val c = streamed[i]
            if (c != '<' && c != '[') continue
            val tail = streamed.subSequence(i, streamed.length).toString()
            if (OPENERS.any { it.startsWith(tail) }) return i
        }
        return streamed.length
    }

    /** True when [content] contains a tool-call opener; parsing it may still have failed. */
    fun looksLikeToolCall(content: String): Boolean = OPENERS.any { content.contains(it) }

    /**
     * Where a fabricated tool result (or an invented next turn) starts, or -1.
     *
     * Only counts after a completed call and outside any open one, so a file
     * that merely mentions `<tool_result>` is not cut. [from] skips text
     * already scanned; markers are short, so callers back it off a little.
     */
    fun fakeResultStart(content: CharSequence, from: Int = 0): Int {
        val start = from.coerceIn(0, content.length)
        var best = -1
        for (marker in FAKE_RESULT_MARKERS) {
            val i = content.indexOf(marker, start)
            if (i >= 0 && (best < 0 || i < best)) best = i
        }
        if (best < 0) return -1
        val before = content.subSequence(0, best).toString()
        val lastClose = CLOSERS.maxOf { before.lastIndexOf(it) }
        if (lastClose < 0) return -1
        val tail = before.substring(lastClose)
        if (OPENERS.any { tail.contains(it) }) return -1
        return best
    }

    fun parse(content: String, knownTools: Set<String>): Parsed {
        if (content.isBlank()) return Parsed(content, emptyList())
        val known = knownTools.map { ToolAliases.canonicalName(it) }.toSet()
        val calls = mutableListOf<ToolCallResponse>()
        val incomplete = mutableListOf<String>()
        val openEnded = mutableSetOf<String>()
        var text = if (content.contains("|tool_call>")) content.replace(NAMESPACED_TAG, "<$1") else content

        fun accept(name: String?, arguments: JsonElement?, closed: Boolean = true): Boolean {
            if (name.isNullOrBlank()) return false
            if (ToolAliases.canonicalName(name) !in known) return false
            val args = when (arguments) {
                null -> "{}"
                is JsonPrimitive -> arguments.contentOrNull?.takeIf { it.isNotBlank() } ?: "{}"
                else -> arguments.toString()
            }
            val id = "call_text_${calls.size}_${System.nanoTime() % 1_000_000}"
            calls += ToolCallResponse(
                id = id,
                type = "function",
                function = FunctionCall(name = name.trim(), arguments = args)
            )
            if (!closed) openEnded += id
            return true
        }

        /** Records a call that was cut off. True when the name is a known tool. */
        fun cutOff(name: String?): Boolean {
            if (name.isNullOrBlank() || ToolAliases.canonicalName(name) !in known) return false
            incomplete += name.trim()
            return true
        }

        fun acceptObject(element: JsonElement?, closed: Boolean = true): Boolean = when (element) {
            is JsonObject -> {
                val fn = element["function"] as? JsonObject
                val name = str(fn?.get("name")) ?: str(element["name"]) ?: str(element["tool"])
                    ?: str(element["tool_name"])
                val args = fn?.get("arguments") ?: element["arguments"] ?: element["parameters"]
                    ?: element["args"] ?: element["input"]
                accept(name, args, closed)
            }
            is JsonArray -> element.map { acceptObject(it, closed) }.any { it }
            else -> false
        }

        // 1. <tool_call> blocks.
        TOOL_CALL_TAG.findAll(text).toList().forEach { m ->
            val inner = m.groupValues[1]
            val closed = m.value.trimEnd().endsWith("</tool_call>", ignoreCase = true)
            val handled = when {
                FUNCTION_OPEN.containsMatchIn(inner) -> acceptFunctions(inner, closed, ::accept, ::cutOff)
                inner.contains("<arg_key>") || inner.contains("<arg_value>") -> {
                    if (!closed && !inner.trimEnd().endsWith("</arg_value>")) cutOff(inner.substringBefore('<').trim())
                    else acceptKeyValue(inner, ::accept)
                }
                !inner.trimStart().startsWith("{") && !inner.trimStart().startsWith("[") &&
                    inner.trim().matches(Regex("[A-Za-z_.][\\w.\\-]*")) ->
                    accept(inner.trim(), null) // a bare name: a call with no arguments
                else -> {
                    val json = extractJson(inner)
                    if (json == null && !closed) cutOff(JSON_NAME.find(inner)?.groupValues?.get(1))
                    else acceptObject(parseJson(json ?: inner))
                }
            }
            if (handled) text = text.replace(m.value, "")
        }

        // 2. Bare <function=...> blocks.
        var searchFrom = 0
        var guard = 0
        while (guard++ < 64) {
            val open = FUNCTION_OPEN.find(text, searchFrom) ?: break
            val end = functionEnd(text, open)
            val segment = text.substring(open.range.first, end)
            // Followed by more text (another call): the model moved on, so it is complete.
            val handled = acceptFunctions(segment, closed = end < text.length, accept = ::accept, cutOff = ::cutOff)
            // Unknown names stay in the text; parsing carries on after them.
            if (handled) text = text.removeRange(open.range.first, end) else searchFrom = end
        }

        // 2b. Kimi K2 and DeepSeek special-token sections.
        if (text.contains("<|tool_call_begin|>")) {
            KIMI_CALL.findAll(text).toList().forEach { m ->
                val raw = m.groupValues[2]
                val json = extractJson(raw)
                val handled = if (json == null && !m.value.endsWith("<|tool_call_end|>")) {
                    cutOff(m.groupValues[1].substringAfterLast('.'))
                } else {
                    accept(m.groupValues[1].substringAfterLast('.'), parseJson(json ?: raw.trim()))
                }
                if (handled) text = text.replace(m.value, "")
            }
            text = text.replace(KIMI_SECTION, "")
        }
        if (text.contains("<｜tool▁call▁begin｜>")) {
            DEEPSEEK_CALL.findAll(text).toList().forEach { m ->
                val json = extractJson(m.groupValues[2])
                val handled = if (json == null && !m.value.endsWith("<｜tool▁call▁end｜>") && m.groupValues[2].contains('{')) {
                    cutOff(m.groupValues[1])
                } else {
                    accept(m.groupValues[1], json?.let { parseJson(it) })
                }
                if (handled) text = text.replace(m.value, "")
            }
            text = text.replace(DEEPSEEK_SECTION, "")
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
        if (calls.isEmpty() && incomplete.isEmpty()) {
            FENCE.findAll(text).toList().forEach { m ->
                val body = m.groupValues[1].trim()
                if ((body.startsWith("{") || body.startsWith("[")) && acceptObject(parseJson(body))) {
                    text = text.replace(m.value, "")
                }
            }
        }

        // 5. The whole reply is one call object.
        if (calls.isEmpty() && incomplete.isEmpty()) {
            val trimmed = text.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}") && acceptObject(parseJson(trimmed))) {
                text = ""
            }
        }

        if (calls.isNotEmpty() || incomplete.isNotEmpty()) text = text.replace(WRAPPER, "").replace(EMPTY_FENCE, "")
        return Parsed(text.replace(Regex("\n{3,}"), "\n\n").trim(), calls, incomplete, openEnded)
    }

    /**
     * Where a bare `<function=…>` block that starts at [open] ends: after its
     * `</function>`, else at the next `<function=`, else at the end.
     */
    private fun functionEnd(text: String, open: MatchResult): Int {
        val bodyStart = open.range.last + 1
        val close = text.indexOf(FUNCTION_CLOSE, bodyStart, ignoreCase = true)
        val next = FUNCTION_OPEN.find(text, bodyStart)?.range?.first ?: -1
        return when {
            close >= 0 && (next < 0 || close < next) -> close + FUNCTION_CLOSE.length
            next >= 0 -> next
            else -> text.length
        }
    }

    /**
     * Every `<function=…>` call in [container]. A call counts as complete when
     * it has `</function>`, when the enclosing `<tool_call>` block is [closed],
     * or when another call follows it; otherwise it is accepted only if its
     * last parameter was closed, and is then marked open-ended.
     */
    private fun acceptFunctions(
        container: String,
        closed: Boolean,
        accept: (String?, JsonElement?, Boolean) -> Boolean,
        cutOff: (String?) -> Boolean
    ): Boolean {
        var any = false
        var from = 0
        while (true) {
            val open = FUNCTION_OPEN.find(container, from) ?: break
            val name = open.groupValues[1]
            val bodyStart = open.range.last + 1
            val close = container.indexOf(FUNCTION_CLOSE, bodyStart, ignoreCase = true)
            val next = FUNCTION_OPEN.find(container, bodyStart)?.range?.first ?: -1
            val bodyEnd = when {
                close >= 0 && (next < 0 || close < next) -> close
                next >= 0 -> next
                else -> container.length
            }
            val explicitEnd = (close >= 0 && (next < 0 || close < next)) || next >= 0 || closed
            val (args, lastClosed) = parameters(container.substring(bodyStart, bodyEnd))
            any = if (!explicitEnd && !lastClosed) {
                cutOff(name) || any
            } else {
                accept(name, args, explicitEnd) || any
            }
            from = if (close >= 0 && close == bodyEnd) close + FUNCTION_CLOSE.length else bodyEnd
            if (from >= container.length) break
        }
        return any
    }

    /**
     * The arguments of one function body and whether its last parameter was
     * explicitly closed. A body with no `<parameter=` tags may be JSON
     * (`<function=name>{...}</function>`).
     */
    private fun parameters(body: String): Pair<JsonElement?, Boolean> {
        val opens = PARAMETER_OPEN.findAll(body).toList()
        if (opens.isEmpty()) {
            val json = extractJson(body)
            return (json?.let { parseJson(it) }) to (json != null || body.isBlank())
        }
        val args = LinkedHashMap<String, JsonElement>()
        var lastClosed = true
        opens.forEachIndexed { i, p ->
            val valueStart = p.range.last + 1
            val limit = if (i + 1 < opens.size) opens[i + 1].range.first else body.length
            val close = body.indexOf(PARAMETER_CLOSE, valueStart, ignoreCase = true)
            val closedHere = close in valueStart until limit
            val raw = trimOneNewline(body.substring(valueStart, if (closedHere) close else limit))
            if (i == opens.lastIndex) lastClosed = closedHere
            args[p.groupValues[1]] = typed(p.groupValues[1], if (closedHere) raw else raw.trimEnd())
        }
        return JsonObject(args) to lastClosed
    }

    /** One leading and one trailing newline belong to the tags, not the value. */
    private fun trimOneNewline(s: String): String {
        var t = s
        t = when {
            t.startsWith("\r\n") -> t.substring(2)
            t.startsWith("\n") -> t.substring(1)
            else -> t
        }
        t = when {
            t.endsWith("\r\n") -> t.dropLast(2)
            t.endsWith("\n") -> t.dropLast(1)
            else -> t
        }
        return t
    }

    /** Raw text to a JSON value: text keys stay text; numbers, booleans and JSON are typed. */
    private fun typed(key: String, raw: String): JsonElement {
        if (key in TEXT_KEYS) return JsonPrimitive(raw)
        val t = raw.trim()
        return when {
            t == "true" || t == "false" -> JsonPrimitive(t.toBoolean())
            t.matches(INTEGER) -> t.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
            t.matches(DECIMAL) -> t.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
            t.startsWith("[") || t.startsWith("{") -> parseJson(t) ?: JsonPrimitive(raw)
            else -> JsonPrimitive(raw)
        }
    }

    /** GLM/K2 `name <arg_key>k</arg_key><arg_value>v</arg_value>...` blocks. */
    private fun acceptKeyValue(inner: String, accept: (String?, JsonElement?, Boolean) -> Boolean): Boolean {
        val name = inner.substringBefore('<').trim()
        val args = LinkedHashMap<String, JsonElement>()
        KV_PAIR.findAll(inner).forEach { m ->
            // Keys arrive with template debris such as `path":`; keep the identifier.
            val key = Regex("[A-Za-z_][\\w]*").find(m.groupValues[1])?.value ?: return@forEach
            val raw = m.groupValues[2].trim('\n')
            args[key] = when {
                key in TEXT_KEYS -> JsonPrimitive(raw)
                raw.trim() == "true" || raw.trim() == "false" -> JsonPrimitive(raw.trim().toBoolean())
                raw.trim().matches(INTEGER) -> JsonPrimitive(raw.trim().toLong())
                raw.trim().startsWith("[") || raw.trim().startsWith("{") -> parseJson(raw.trim()) ?: JsonPrimitive(raw)
                else -> JsonPrimitive(raw)
            }
        }
        val debris = Regex("\"[}\\]]+\\s*$")
        // File bodies are taken verbatim: a JS file may well start with a quote.
        val kvClean = args.isNotEmpty() && args.filterKeys { it !in VERBATIM_KEYS }.values.none {
            it is JsonPrimitive && it.isString && (debris.containsMatchIn(it.content) || it.content.trimStart().startsWith("\""))
        }
        if (kvClean) return accept(name, JsonObject(args), true)
        // K2 Horizon often mixes its key-value tokens with JSON, e.g.
        // `<arg_key>path": "index.html"}}` or `<arg_key>pattern</arg_key> ".*", "path": "a"}}`.
        // Rebuild the JSON object those fragments spell out.
        val body = inner.substring(name.length.coerceAtMost(inner.length))
            .replace(Regex("\"?\\s*:?\\s*</arg_key>"), "\":")
            .replace("<arg_key>", "")
            .replace(Regex("</?arg_value>"), "")
            .trim()
        val repaired = (extractJson("{\"" + body)?.let { parseJson(it) } as? JsonObject)
            ?: lenientClose(body)
        if (repaired != null && repaired.isNotEmpty()) return accept(name, repaired, true)
        return if (args.isNotEmpty()) accept(name, JsonObject(args), true) else false
    }

    /** `path": "index.html` -> `{"path": "index.html"}`: close what the model left open. */
    private fun lenientClose(body: String): JsonObject? {
        var t = "{\"" + body.trimEnd().trimEnd('}').trimEnd()
        var quotes = 0
        var escaped = false
        for (c in t) {
            if (escaped) { escaped = false; continue }
            if (c == '\\') escaped = true else if (c == '"') quotes++
        }
        if (quotes % 2 == 1) t += "\""
        val obj = parseJson("$t}") as? JsonObject ?: return null
        // Lenient parsing turns a bare word into an unquoted literal; tools want strings.
        return JsonObject(obj.mapValues { (_, v) ->
            if (v is JsonPrimitive && !v.isString && v.content != "true" && v.content != "false" &&
                v.content.toDoubleOrNull() == null && v.content != "null"
            ) JsonPrimitive(v.content.trim()) else v
        })
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
