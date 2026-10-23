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
 * ### Raw values are opaque
 *
 * A raw value is a file the model is writing, and files mention markup: a
 * README that shows an example call, a script holding the string
 * `'</parameter>'`, docs about `<tool_result>`. So the raw format is read by
 * a scanner, not by splitting on tags. Tags mark structure only outside open
 * values; inside one, markup the value itself opens and closes (an example
 * `<function=...>` call with its parameters) stays content, and a
 * `</parameter>` closes the value only where structure follows it (another
 * parameter, `</function>`, `</tool_call>`, the end) or where it ends its
 * line. A `<function=` inside a value never becomes a call. JSON inside a
 * `<tool_call>` ends at its balanced brace, never at a `</tool_call>` inside
 * one of its strings.
 *
 * Weak models drop closing tags; a missing `</parameter>` before the next
 * parameter, `</function>` or `</tool_call>` on its own line is tolerated.
 * What is never accepted is a call that may have been cut off -- a value
 * still open at the end of the text, typically because the output limit hit
 * in the middle of a file. Those land in [Parsed.incomplete] and are not run:
 * a truncated write_file would replace a good file with half of one.
 *
 * Deliberately NOT handled: `read_file(path="x")` call syntax in prose. The
 * old parser accepted it, which meant an explanation such as "I would use
 * delete_file(path='index.html')" executed the deletion.
 *
 * Only names that resolve (through [ToolAliases]) to a tool actually offered
 * are accepted, so example JSON in an answer is not mistaken for a call.
 * When the offered tools' JSON schemas are known, raw values are typed by the
 * schema (an MCP `page` integer stays an integer, a zip code "02134" stays
 * text); otherwise by a conservative guess.
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
        val openEnded: Set<String> = emptySet(),
        /**
         * The cut-off calls of [incomplete] with whatever arguments arrived. Never
         * run as they are, except a read-only call after an explicit stop (a
         * reply that ends on `<parameter=path>a.html` without its closers).
         */
        val incompleteCalls: List<ToolCallResponse> = emptyList()
    )

    private const val TOOL_CALL_OPEN = "<tool_call>"
    private const val TOOL_CALL_CLOSE = "</tool_call>"
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
    private val BARE_NAME = Regex("[A-Za-z_.][\\w.\\-]*")

    /**
     * Arguments that are always text when no schema says otherwise. Key-value
     * formats carry raw values, and guessing a type from the value turned a
     * JSON file's `content` into an object (write_file then failed) and "404"
     * into a number.
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
        "<tool_call", "<tool_calls", "<function=", "<function name=", "<function>", "[TOOL_CALLS]", "<|tool_call", "<|tool_calls",
        "<｜tool▁call", "<ifm|tool_call"
    )

    // Template drift: the taught format with a model's own chat template mixed in
    // (seen live with Tencent Hy3). Rewritten to the canonical markup when the
    // line starts with it and names a real tool, so file bodies that merely
    // mention such tags are left as written.

    /** `<tool_call:6124c78e>use_skill>`: an id on the tag, then the bare tool name. */
    private val ID_TAG_CALL = Regex("""(?m)^([ \t]*)<tool_call:[\w-]+>[ \t]*([A-Za-z_][\w.\-]*)>[ \t]*$""")

    /** `<tool_call:6124c78e>` or `</tool_calls:6124c78e>` around otherwise canonical markup. */
    private val ID_TAG = Regex("""(?m)^([ \t]*)<(/?)(tool_calls?):[\w-]+>""")

    /** `<function>list_files>` or `<function list_files>`: the opener with its `=` lost. */
    private val BARE_FUNCTION = Regex("""(?m)^([ \t]*)<function(?:>|[ \t]+)([A-Za-z_][\w.\-]*)>[ \t]*$""")

    /** `<parameter>content>value`: the same lost `=` on a parameter, the value often on the same line. */
    private val BARE_PARAMETER = Regex("""(?m)^([ \t]*)<parameter(?:>|[ \t]+)([A-Za-z_][\w\-]*)>""")

    private fun normalizeDrift(text: String, known: Set<String>): String {
        if ("<tool_call:" !in text && "</tool_call" !in text && "<function>" !in text && "<function " !in text &&
            "<parameter>" !in text && "<parameter " !in text) return text
        fun tool(name: String) = ToolAliases.canonicalName(name) in known
        var t = ID_TAG_CALL.replace(text) { m ->
            val (indent, name) = m.destructured
            if (tool(name)) "$indent<tool_call>\n$indent<function=$name>" else m.value
        }
        t = BARE_FUNCTION.replace(t) { m ->
            val (indent, name) = m.destructured
            if (tool(name)) "$indent<function=$name>" else m.value
        }
        t = BARE_PARAMETER.replace(t) { m ->
            val (indent, name) = m.destructured
            "$indent<parameter=$name>"
        }
        return ID_TAG.replace(t) { m ->
            val (indent, slash, tag) = m.destructured
            "$indent<$slash$tag>"
        }
    }

    /** The first line of [content] holding call markup, for the resend nudge (null when none). */
    fun firstMarkupLine(content: String): String? =
        content.lineSequence().firstOrNull { line -> OPENERS.any { line.contains(it) } }?.trim()?.take(140)

    /** Ends of special-token calls the raw scanner does not read; a fabricated result may follow them. */
    private val SPECIAL_CLOSERS = listOf("<|tool_call_end|>", "<｜tool▁call▁end｜>", "</ifm|tool_call>")

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
     * Only counts after a completed call and outside every call block, open
     * values included, so a file that merely mentions `<tool_result>` or a
     * line starting "User:" is never cut. Every occurrence of every marker is
     * considered, not just the first: in a reply that arrives in one piece, the
     * first marker may sit inside a file and the invented result come later.
     * [from] skips text already scanned; markers are short, so callers back it
     * off a little.
     */
    fun fakeResultStart(content: CharSequence, from: Int = 0): Int {
        val start = from.coerceIn(0, content.length)
        // Cheap first: almost every chunk holds no marker at all.
        if (FAKE_RESULT_MARKERS.none { content.indexOf(it, start) >= 0 }) return -1
        val text = content.toString()
        val blocks = RawScanner(text) { null }.scan()
        var firstEnd = Int.MAX_VALUE
        for (b in blocks) if (b.endsCleanly) firstEnd = minOf(firstEnd, b.end)
        for (closer in SPECIAL_CLOSERS) {
            val i = text.indexOf(closer)
            if (i >= 0) firstEnd = minOf(firstEnd, i + closer.length)
        }
        if (firstEnd == Int.MAX_VALUE) return -1
        var best = -1
        for (marker in FAKE_RESULT_MARKERS) {
            var i = text.indexOf(marker, maxOf(start, firstEnd))
            while (i >= 0 && (best < 0 || i < best)) {
                if (blocks.none { i >= it.start && i < it.end }) {
                    best = i
                    break
                }
                i = text.indexOf(marker, i + 1)
            }
        }
        return best
    }

    /**
     * @param schemas the offered tools' JSON parameter schemas by name, used to
     *   type raw values. Tools without one get the conservative guess.
     */
    fun parse(content: String, knownTools: Set<String>, schemas: Map<String, JsonObject> = emptyMap()): Parsed {
        if (content.isBlank()) return Parsed(content, emptyList())
        val known = knownTools.map { ToolAliases.canonicalName(it) }.toSet()
        val calls = mutableListOf<ToolCallResponse>()
        val incomplete = mutableListOf<String>()
        val incompleteCalls = mutableListOf<ToolCallResponse>()
        val openEnded = mutableSetOf<String>()
        var text = if (content.contains("|tool_call>")) content.replace(NAMESPACED_TAG, "<$1") else content
        text = normalizeDrift(text, known)
        // Invented results after the calls are not part of the reply.
        fakeResultStart(text).takeIf { it >= 0 }?.let { text = text.substring(0, it) }

        fun schemaOf(name: String): JsonObject? = schemas[name] ?: schemas[ToolAliases.canonicalName(name)]

        fun newCall(name: String, args: String): ToolCallResponse = ToolCallResponse(
            id = "call_text_${calls.size + incompleteCalls.size}_${System.nanoTime() % 1_000_000}",
            type = "function",
            function = FunctionCall(name = name.trim(), arguments = args)
        )

        fun argsText(arguments: JsonElement?): String = when (arguments) {
            null -> "{}"
            is JsonPrimitive -> arguments.contentOrNull?.takeIf { it.isNotBlank() } ?: "{}"
            else -> arguments.toString()
        }

        fun accept(name: String?, arguments: JsonElement?, closed: Boolean = true): Boolean {
            if (name.isNullOrBlank()) return false
            if (ToolAliases.canonicalName(name) !in known) return false
            val call = newCall(name, argsText(arguments))
            calls += call
            if (!closed) openEnded += call.id
            return true
        }

        /** Records a call that was cut off. True when the name is a known tool. */
        fun cutOff(name: String?, arguments: JsonElement? = null): Boolean {
            if (name.isNullOrBlank() || ToolAliases.canonicalName(name) !in known) return false
            incomplete += name.trim()
            incompleteCalls += newCall(name, argsText(arguments))
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

        /** One raw-format call: run it, record it as cut off, or (unknown name) leave it alone. */
        fun handle(call: RawCall, wrapperClosed: Boolean): Boolean {
            val props = schemaOf(call.name)?.get("properties") as? JsonObject
            val args: JsonElement? = when {
                call.json != null -> parseJson(call.json)
                else -> JsonObject(LinkedHashMap<String, JsonElement>().also { map ->
                    call.values.forEach { (k, v) -> map[k] = typed(k, v, props?.get(k) as? JsonObject) }
                })
            }
            val complete = call.complete || wrapperClosed
            return if (!complete && !call.lastClosed) cutOff(call.name, args) else accept(call.name, args, complete)
        }

        // 1. The raw format and <tool_call> blocks, in text order.
        val handledSpans = mutableListOf<IntRange>()
        for (block in RawScanner(text) { name -> (schemaOf(name)?.get("properties") as? JsonObject)?.keys }.scan()) {
            val handled = when (block) {
                is RawCall -> handle(block, wrapperClosed = false)
                is Wrapper -> if (block.calls.isNotEmpty()) {
                    // Every call in the block counts; don't stop at the first.
                    block.calls.map { handle(it, wrapperClosed = block.closed) }.any { it }
                } else {
                    val inner = block.inner
                    when {
                        inner.contains("<arg_key>") || inner.contains("<arg_value>") -> {
                            if (!block.closed && !inner.trimEnd().endsWith("</arg_value>")) cutOff(inner.substringBefore('<').trim())
                            else acceptKeyValue(inner, { name -> schemaOf(name) }, ::accept)
                        }
                        !inner.trimStart().startsWith("{") && !inner.trimStart().startsWith("[") &&
                            inner.trim().matches(BARE_NAME) ->
                            accept(inner.trim(), null) // a bare name: a call with no arguments
                        else -> {
                            val json = extractJson(inner)
                            if (json == null && !block.closed) cutOff(JSON_NAME.find(inner)?.groupValues?.get(1))
                            else acceptObject(parseJson(json ?: inner))
                        }
                    }
                }
            }
            if (handled) handledSpans += block.start until block.end
        }
        if (handledSpans.isNotEmpty()) text = removeSpans(text, handledSpans)

        // 2. Kimi K2 and DeepSeek special-token sections.
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
        return Parsed(text.replace(Regex("\n{3,}"), "\n\n").trim(), calls, incomplete, openEnded, incompleteCalls)
    }

    private fun removeSpans(text: String, spans: List<IntRange>): String = buildString {
        var at = 0
        spans.sortedBy { it.first }.forEach { r ->
            if (r.first > at) append(text, at, r.first)
            at = maxOf(at, r.last + 1)
        }
        if (at < text.length) append(text, at, text.length)
    }

    // ------------------------------------------------------------------ raw-format scanner

    /** Something the scanner found, spanning [start] until [end]. */
    private sealed interface Block {
        val start: Int
        val end: Int
        /** A complete call ends here: a fabricated result may follow it. */
        val endsCleanly: Boolean
    }

    /** One `<function=…>` block. */
    private class RawCall(
        override val start: Int,
        override val end: Int,
        val name: String,
        val values: LinkedHashMap<String, String>,
        /** A JSON body (`<function=name>{...}</function>`) instead of parameters. */
        val json: String?,
        /** Ended by `</function>`, by a `</tool_call>`, or by another call starting. */
        val complete: Boolean,
        /** False when the last value (or a JSON body) ran to the end of the text: cut off. */
        val lastClosed: Boolean,
    ) : Block {
        override val endsCleanly: Boolean get() = complete || lastClosed
    }

    /** A `<tool_call>` block: raw-format [calls], or another format's [inner] text. */
    private class Wrapper(
        override val start: Int,
        override val end: Int,
        val closed: Boolean,
        val calls: List<RawCall>,
        val inner: String,
    ) : Block {
        override val endsCleanly: Boolean get() = closed || (calls.isNotEmpty() && calls.all { it.endsCleanly })
    }

    private class Tag(val name: String, val end: Int)

    private class Value(val text: String, val closed: Boolean, val next: Int)

    /**
     * Reads the raw format left to right. [keysOf] gives a tool's parameter
     * names when its schema is known: a `<parameter=x>` line inside a value
     * only ends that value (the model dropped its `</parameter>`) when `x` is
     * a parameter of the tool not set yet.
     */
    private class RawScanner(private val t: String, private val keysOf: (String) -> Set<String>?) {

        fun scan(): List<Block> {
            val out = mutableListOf<Block>()
            var i = 0
            while (i < t.length) {
                val lt = t.indexOf('<', i)
                if (lt < 0) break
                if (isToolCallOpen(lt)) {
                    val w = wrapper(lt)
                    out += w
                    i = maxOf(w.end, lt + 1)
                    continue
                }
                val fn = openTag(lt, "function")
                if (fn != null) {
                    val c = function(lt, fn, inWrapper = false)
                    out += c
                    i = maxOf(c.end, lt + 1)
                    continue
                }
                i = lt + 1
            }
            return out
        }

        private fun wrapper(start: Int): Wrapper {
            val k = skipWs(start + TOOL_CALL_OPEN.length)
            if (openTag(k, "function") != null) {
                val calls = mutableListOf<RawCall>()
                var p = k
                while (true) {
                    val tag = openTag(p, "function") ?: break
                    val c = function(p, tag, inWrapper = true)
                    calls += c
                    p = skipWs(c.end)
                    if (p >= t.length) return Wrapper(start, t.length, false, calls, "")
                    if (isToolCallClose(p)) return Wrapper(start, p + TOOL_CALL_CLOSE.length, true, calls, "")
                    if (openTag(p, "function") != null) continue
                    // A stray fence or word before the closer: skip to it, unless another call starts first.
                    val close = t.indexOf(TOOL_CALL_CLOSE, p, ignoreCase = true)
                    val next = nextOpener(p)
                    return if (close >= 0 && (next < 0 || close < next)) Wrapper(start, close + TOOL_CALL_CLOSE.length, true, calls, "")
                    else Wrapper(start, p, false, calls, "")
                }
                return Wrapper(start, p, false, calls, "")
            }
            if (k < t.length && (t[k] == '{' || t[k] == '[')) {
                // JSON ends at its balanced brace, never at a closer inside one of its strings.
                val jsonEnd = jsonEnd(t, k)
                if (jsonEnd > 0) {
                    val after = skipWs(jsonEnd)
                    val close = t.indexOf(TOOL_CALL_CLOSE, after, ignoreCase = true)
                    val next = nextOpener(after)
                    val end = if (close >= 0 && (next < 0 || close < next)) close + TOOL_CALL_CLOSE.length else jsonEnd
                    return Wrapper(start, end, true, emptyList(), t.substring(k, jsonEnd))
                }
            }
            // Key-value, a bare name, or JSON the model left unbalanced: up to the first closer.
            val close = t.indexOf(TOOL_CALL_CLOSE, k, ignoreCase = true)
            return if (close >= 0) Wrapper(start, close + TOOL_CALL_CLOSE.length, true, emptyList(), t.substring(k, close).trim())
            else Wrapper(start, t.length, false, emptyList(), t.substring(minOf(k, t.length)).trim())
        }

        private fun function(start: Int, tag: Tag, inWrapper: Boolean): RawCall {
            val values = LinkedHashMap<String, String>()
            val keys = keysOf(tag.name)
            var json: String? = null
            var lastClosed = true
            var p = tag.end
            while (true) {
                p = skipWs(p)
                if (p >= t.length) return RawCall(start, t.length, tag.name, values, json, false, lastClosed)
                if (isFunctionClose(p)) return RawCall(start, p + FUNCTION_CLOSE.length, tag.name, values, json, true, true)
                if (isToolCallClose(p)) {
                    // Its wrapper's closer (kept for the wrapper), or a stray one after a bare call.
                    val end = if (inWrapper) p else p + TOOL_CALL_CLOSE.length
                    return RawCall(start, end, tag.name, values, json, true, lastClosed)
                }
                if (isToolCallOpen(p) || openTag(p, "function") != null) {
                    // The model moved on to the next call: this one is complete.
                    return RawCall(start, p, tag.name, values, json, true, lastClosed)
                }
                val param = openTag(p, "parameter")
                if (param != null) {
                    val v = value(param.end, param.name, values.keys, keys)
                    values[param.name] = v.text
                    lastClosed = v.closed
                    p = v.next
                    continue
                }
                if (values.isEmpty() && json == null && (t[p] == '{' || t[p] == '[')) {
                    val end = jsonEnd(t, p)
                    if (end < 0) return RawCall(start, t.length, tag.name, values, null, false, false)
                    json = t.substring(p, end)
                    p = end
                    continue
                }
                // A stray line (a fence, a note) between values is skipped; prose after the call ends it.
                val resume = strayLines(p, values.keys, keys)
                if (resume > p) {
                    p = resume
                    continue
                }
                return RawCall(start, p, tag.name, values, json, false, lastClosed)
            }
        }

        /** Where scanning resumes when up to three junk lines are followed by a parameter or `</function>`; else [p]. */
        private fun strayLines(p: Int, assigned: Set<String>, keys: Set<String>?): Int {
            var line = p
            repeat(3) {
                val nl = t.indexOf('\n', line)
                if (nl < 0) return p
                val q = skipInline(nl + 1)
                val param = openTag(q, "parameter")
                if (isFunctionClose(q) || (param != null && param.name !in assigned && (keys == null || param.name in keys))) return q
                if (q < t.length && t[q] == '<') return p
                line = q
            }
            return p
        }

        /**
         * One raw value, starting right after its `<parameter=…>` tag.
         *
         * Markup the value opens itself is counted, so it closes as content:
         * `<function…>` / `</function>`, `<parameter…>` / `</parameter>` and
         * `<tool_call>` / `</tool_call>` pairs (an example call in a README).
         * At depth 0 a `</parameter>` closes the value only where structure
         * follows or its line ends; otherwise it is literal text. A value the
         * model left open ends at a line that starts the next parameter of the
         * tool, or that holds the call's own `</function>` / `</tool_call>`.
         */
        private fun value(start: Int, key: String, assigned: Set<String>, keys: Set<String>?): Value {
            var nestFunc = 0
            var nestParam = 0
            var nestCall = 0
            var k = start
            while (true) {
                val lt = t.indexOf('<', k)
                if (lt < 0) break
                k = lt
                if (isParameterClose(k)) {
                    val after = k + PARAMETER_CLOSE.length
                    if (nestParam > 0) { nestParam--; k = after; continue }
                    if (structuralAfter(after) || restOfLineBlank(after)) return Value(clean(start, k, closed = true), true, after)
                    k = after
                    continue
                }
                if (opensLike(k, "<parameter")) {
                    val tag = openTag(k, "parameter")
                    if (tag != null && nestFunc == 0 && nestCall == 0 && atLineStart(k, start) &&
                        tag.name != key && tag.name !in assigned && (keys == null || tag.name in keys)
                    ) {
                        // The model left this value open and went on to the next parameter.
                        return Value(clean(start, k, closed = false), true, k)
                    }
                    if (!selfClosing(k)) nestParam++
                    k = tag?.end ?: (k + 1)
                    continue
                }
                if (opensLike(k, "<function")) {
                    if (!selfClosing(k)) nestFunc++
                    k = openTag(k, "function")?.end ?: (k + 1)
                    continue
                }
                if (isFunctionClose(k)) {
                    val after = k + FUNCTION_CLOSE.length
                    if (nestFunc > 0) { nestFunc--; k = after; continue }
                    if (nestCall == 0 && atLineStart(k, start) && structuralAfter(after)) {
                        return Value(clean(start, k, closed = false), true, k)
                    }
                    k = after
                    continue
                }
                if (isToolCallOpen(k)) {
                    nestCall++
                    k += TOOL_CALL_OPEN.length
                    continue
                }
                if (isToolCallClose(k)) {
                    val after = k + TOOL_CALL_CLOSE.length
                    if (nestCall > 0) { nestCall--; k = after; continue }
                    if (nestFunc == 0 && atLineStart(k, start) && structuralAfter(after)) {
                        return Value(clean(start, k, closed = false), true, k)
                    }
                    k = after
                    continue
                }
                k++
            }
            // Ran to the end of the text: cut off.
            return Value(clean(start, t.length, closed = false), false, t.length)
        }

        /** One leading and one trailing newline belong to the tags; an unclosed value also loses trailing blanks. */
        private fun clean(from: Int, to: Int, closed: Boolean): String {
            val v = trimOneNewline(t.substring(from, to))
            return if (closed) v else v.trimEnd()
        }

        private fun structuralAfter(p: Int): Boolean {
            val q = skipWs(p)
            return q >= t.length || isFunctionClose(q) || isToolCallClose(q) || isToolCallOpen(q) ||
                openTag(q, "parameter") != null || openTag(q, "function") != null
        }

        private fun restOfLineBlank(p: Int): Boolean {
            var q = p
            while (q < t.length && t[q] != '\n') {
                if (t[q] != ' ' && t[q] != '\t' && t[q] != '\r') return false
                q++
            }
            return true
        }

        /** Only blanks between the line's start (or the value's start) and [k]. */
        private fun atLineStart(k: Int, floor: Int): Boolean {
            var j = k - 1
            while (j >= floor && (t[j] == ' ' || t[j] == '\t' || t[j] == '\r')) j--
            return j < floor || t[j] == '\n'
        }

        private fun nextOpener(p: Int): Int {
            var i = p
            while (true) {
                val lt = t.indexOf('<', i)
                if (lt < 0) return -1
                if (isToolCallOpen(lt) || openTag(lt, "function") != null) return lt
                i = lt + 1
            }
        }

        private fun skipWs(p: Int): Int {
            var q = p
            while (q < t.length && t[q].isWhitespace()) q++
            return q
        }

        private fun skipInline(p: Int): Int {
            var q = p
            while (q < t.length && (t[q] == ' ' || t[q] == '\t' || t[q] == '\r')) q++
            return q
        }

        private fun at(i: Int, token: String): Boolean = t.regionMatches(i, token, 0, token.length, ignoreCase = true)
        private fun isToolCallOpen(i: Int) = at(i, TOOL_CALL_OPEN)
        private fun isToolCallClose(i: Int) = at(i, TOOL_CALL_CLOSE)
        private fun isFunctionClose(i: Int) = at(i, FUNCTION_CLOSE)
        private fun isParameterClose(i: Int) = at(i, PARAMETER_CLOSE)

        /** `<parameter` / `<function` starting a tag of that name (`=`, a space or `>` next). */
        private fun opensLike(i: Int, prefix: String): Boolean {
            if (!at(i, prefix)) return false
            val c = t.getOrNull(i + prefix.length) ?: return false
            return c == '=' || c == '>' || c == ' ' || c == '\t' || c == '\n' || c == '\r'
        }

        private fun selfClosing(i: Int): Boolean {
            val gt = t.indexOf('>', i)
            return gt > 0 && t[gt - 1] == '/'
        }

        /** `<tag=name>`, `<tag=“name”>` or `<tag name="name">` at [i]. */
        fun openTag(i: Int, tag: String): Tag? {
            if (i < 0 || i >= t.length || !at(i, "<$tag")) return null
            var j = i + tag.length + 1
            if (j >= t.length) return null
            when (t[j]) {
                '=' -> j++
                ' ', '\t' -> {
                    while (j < t.length && (t[j] == ' ' || t[j] == '\t')) j++
                    if (!at(j, "name")) return null
                    j += 4
                    while (j < t.length && t[j] == ' ') j++
                    if (j >= t.length || t[j] != '=') return null
                    j++
                }
                else -> return null
            }
            while (j < t.length && t[j] == ' ') j++
            val quote = if (j < t.length && (t[j] == '"' || t[j] == '\'')) t[j++] else null
            val nameStart = j
            while (j < t.length && (t[j].isLetterOrDigit() || t[j] == '_' || t[j] == '.' || t[j] == '-')) j++
            if (j == nameStart) return null
            val name = t.substring(nameStart, j)
            if (quote != null && j < t.length && t[j] == quote) j++
            while (j < t.length && t[j] == ' ') j++
            if (j >= t.length || t[j] != '>') return null
            return Tag(name, j + 1)
        }
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

    // ------------------------------------------------------------------ typing

    /**
     * A raw value as JSON: by the parameter's schema type when known, else
     * text keys stay text and plain numbers, booleans and JSON are typed.
     */
    private fun typed(key: String, raw: String, spec: JsonObject?): JsonElement {
        val t = raw.trim()
        return when (schemaType(spec)) {
            "string" -> JsonPrimitive(raw)
            "integer" -> t.toLongOrNull()?.let { JsonPrimitive(it) }
                ?: t.toDoubleOrNull()?.takeIf { !it.isInfinite() && it == Math.floor(it) && kotlin.math.abs(it) < 9.0E15 }
                    ?.let { JsonPrimitive(it.toLong()) }
                ?: JsonPrimitive(raw)
            "number" -> t.toLongOrNull()?.let { JsonPrimitive(it) } ?: t.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
            "boolean" -> when (t.lowercase()) {
                "true" -> JsonPrimitive(true)
                "false" -> JsonPrimitive(false)
                else -> JsonPrimitive(raw)
            }
            "array", "object" -> parseJson(t) ?: JsonPrimitive(raw)
            else -> guessed(key, raw)
        }
    }

    private fun schemaType(spec: JsonObject?): String? {
        if (spec == null) return null
        val enum = spec["enum"] as? JsonArray
        if (enum != null && enum.isNotEmpty()) {
            val first = enum.first() as? JsonPrimitive
            if (first != null && first.isString) return "string"
        }
        return when (val type = spec["type"]) {
            is JsonPrimitive -> type.contentOrNull
            is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.firstOrNull { it != "null" }
            else -> null
        }
    }

    /** No schema: text keys stay text; plain numbers (not "02134"), booleans and JSON are typed. */
    private fun guessed(key: String, raw: String): JsonElement {
        if (key in TEXT_KEYS) return JsonPrimitive(raw)
        val t = raw.trim()
        val leadingZero = t.length > 1 && (t.startsWith("0") || t.startsWith("-0")) && !t.startsWith("0.") && !t.startsWith("-0.")
        return when {
            t == "true" || t == "false" -> JsonPrimitive(t.toBoolean())
            !leadingZero && t.matches(INTEGER) -> t.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
            !leadingZero && t.matches(DECIMAL) -> t.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
            t.startsWith("[") || t.startsWith("{") -> parseJson(t) ?: JsonPrimitive(raw)
            else -> JsonPrimitive(raw)
        }
    }

    // ------------------------------------------------------------------ other formats

    /** GLM/K2 `name <arg_key>k</arg_key><arg_value>v</arg_value>...` blocks. */
    private fun acceptKeyValue(
        inner: String,
        schemaOf: (String) -> JsonObject?,
        accept: (String?, JsonElement?, Boolean) -> Boolean
    ): Boolean {
        val name = inner.substringBefore('<').trim()
        val props = schemaOf(name)?.get("properties") as? JsonObject
        val args = LinkedHashMap<String, JsonElement>()
        KV_PAIR.findAll(inner).forEach { m ->
            // Keys arrive with template debris such as `path":`; keep the identifier.
            val key = Regex("[A-Za-z_][\\w]*").find(m.groupValues[1])?.value ?: return@forEach
            args[key] = typed(key, m.groupValues[2].trim('\n'), props?.get(key) as? JsonObject)
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
        val end = jsonEnd(text, start)
        return if (end < 0) null else text.substring(start, end)
    }

    /** End (exclusive) of the balanced JSON value opening at [start], or -1 when it never closes. */
    private fun jsonEnd(text: String, start: Int): Int {
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
                    if (depth == 0) return i + 1
                }
            }
        }
        return -1
    }
}
