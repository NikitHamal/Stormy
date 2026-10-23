package com.oro.app.data.ai.agent

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.ToolCallRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Token estimate shared by the meter, the pruner and the rate limiter.
 *
 * Four characters per token, as everywhere else in the app (there is no
 * tokenizer on device) -- but counting tool-call arguments too. The old
 * estimate counted only `content`, so a turn that wrote a 30 KB file through
 * write_file was metered as nearly free and the window overflowed with the
 * meter reading half full.
 */
object TokenEstimator {
    private const val CHARS_PER_TOKEN = 4
    private const val PER_MESSAGE_OVERHEAD = 4

    fun estimate(text: String?): Int = ((text?.length ?: 0) + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

    fun estimate(message: ChatRequestMessage): Int =
        PER_MESSAGE_OVERHEAD + estimate(message.content) +
            (message.toolCalls?.sumOf { estimate(it.function.name) + estimate(it.function.arguments) + 4 } ?: 0)

    fun estimate(messages: List<ChatRequestMessage>): Int = messages.sumOf { estimate(it) }
}

/**
 * Keeps a conversation inside the context window without a model call.
 *
 * The same order of operations opencode uses for its automatic prune, because
 * it removes the least valuable tokens first:
 *  1. old tool outputs (file dumps, search results) are replaced with a stub --
 *     the model can re-run the tool if it needs them again;
 *  2. old, large tool-call arguments (whole files sent to write_file) are
 *     reduced to their short fields;
 *  3. only then are the oldest exchanges dropped, a whole exchange at a time.
 *
 * Exchanges are dropped as units because providers reject a `tool` message
 * whose `assistant` tool_calls message is gone (and vice versa) with a 400 --
 * the old optimiser cut message-by-message and could produce exactly that.
 * The most recent user message and everything after it are never touched.
 */
object HistoryPruner {

    const val PRUNED_TOOL_OUTPUT =
        "[Earlier tool output removed to save context. Run the tool again if you need it.]"
    const val OMITTED_NOTE =
        "[Earlier parts of this conversation were omitted to fit the context window.]"

    /** Recent tokens protected from step 1 and 2. */
    private const val PROTECT_RECENT_TOKENS = 24_000
    private const val LARGE_ARGUMENT_CHARS = 1_500

    fun prune(messages: List<ChatRequestMessage>, budgetTokens: Int): List<ChatRequestMessage> {
        if (TokenEstimator.estimate(messages) <= budgetTokens) return messages

        val protectFrom = protectedStart(messages)

        // 1 + 2: shrink old tool traffic -- native messages, and the same
        // traffic written as text by models on the text tool protocol.
        val shrunk = messages.mapIndexed { i, m ->
            if (i >= protectFrom) return@mapIndexed m
            val content = m.content
            when {
                m.role == "tool" && (content?.length ?: 0) > PRUNED_TOOL_OUTPUT.length ->
                    m.copy(content = PRUNED_TOOL_OUTPUT)
                m.role == "assistant" && m.toolCalls != null ->
                    m.copy(toolCalls = m.toolCalls.map { shrinkCall(it) })
                m.role == "user" && content != null && content.startsWith(TEXT_RESULTS_HEADER) ->
                    m.copy(content = shrinkTextResults(content))
                m.role == "assistant" && content != null && content.length > LARGE_ARGUMENT_CHARS &&
                    content.contains("<parameter=") -> m.copy(content = shrinkTextCalls(content))
                else -> m
            }
        }
        if (TokenEstimator.estimate(shrunk) <= budgetTokens) return shrunk

        // Exchanges as index ranges; the same indexes address the originals,
        // which the run ledger reads (their tool outputs are still whole).
        val units = unitRanges(shrunk)
        val task = taskIndex(shrunk)
        val anchorUnit = units.indexOfFirst { task in it }.let { if (it < 0) units.lastIndex else it }
        val runUnits = units.size - anchorUnit - 1

        var pre = 0      // exchanges dropped before the task message
        var collapsed = 0 // oldest exchanges of the current run folded into the ledger
        fun assemble(): List<ChatRequestMessage> {
            val out = ArrayList<ChatRequestMessage>(shrunk.size)
            if (pre > 0) out += ChatRequestMessage(role = "user", content = OMITTED_NOTE)
            for (u in pre until anchorUnit) units[u].forEach { out += shrunk[it] }
            units[anchorUnit].forEach { i ->
                val m = shrunk[i]
                if (i == task && collapsed > 0) {
                    val dropped = units.subList(anchorUnit + 1, anchorUnit + 1 + collapsed).flatMap { r -> r.map { messages[it] } }
                    out += m.copy(content = (m.content ?: "").trimEnd() + "\n\n" + RunLedger.summarize(dropped))
                } else {
                    out += m
                }
            }
            for (u in anchorUnit + 1 + collapsed until units.size) units[u].forEach { out += shrunk[it] }
            return out
        }

        // 3: drop whole exchanges from before the task, oldest first.
        while (pre < anchorUnit && TokenEstimator.estimate(assemble()) > budgetTokens) pre++

        // 4: an unlimited run can outgrow any window on its own. Fold its
        // oldest exchanges into a ledger appended to the task message --
        // whole exchanges, so tool calls and results stay paired, and never
        // the newest one. The fold grows [COLLAPSE_CHUNK] exchanges at a time,
        // so as the run goes on the boundary (and with it the prompt prefix:
        // task + ledger) stays put for several turns and providers' prompt
        // caches keep hitting.
        if (TokenEstimator.estimate(assemble()) > budgetTokens && runUnits > 1) {
            while (collapsed < runUnits - 1 && TokenEstimator.estimate(assemble()) > budgetTokens) {
                collapsed = (collapsed + COLLAPSE_CHUNK).coerceAtMost(runUnits - 1)
            }
            // Never leave a results or harness message whose call was folded
            // away: the run resumes on the model's own turn.
            while (collapsed < runUnits - 1 && shrunk[units[anchorUnit + 1 + collapsed].first].role != "assistant") collapsed++
        }
        var result = assemble()

        // Still over with only the current exchange left: shrink its oldest
        // tool outputs too, newest kept intact as long as possible.
        if (TokenEstimator.estimate(result) > budgetTokens) {
            val mutable = result.toMutableList()
            for (i in mutable.indices) {
                if (TokenEstimator.estimate(mutable) <= budgetTokens) break
                val m = mutable[i]
                if (m.role == "tool" && (m.content?.length ?: 0) > PRUNED_TOOL_OUTPUT.length) {
                    mutable[i] = m.copy(content = PRUNED_TOOL_OUTPUT)
                }
            }
            result = mutable
        }
        return result
    }

    /** Exchanges folded into the run ledger at a time (see step 4). */
    private const val COLLAPSE_CHUNK = 8

    /**
     * The message that states the task: the last user turn the user wrote.
     * Text-protocol results and the harness's own notes are user messages too,
     * and must not count -- otherwise the task itself is pruned first.
     */
    internal fun taskIndex(messages: List<ChatRequestMessage>): Int {
        val genuine = messages.indexOfLast { it.role == "user" && !isHarnessMessage(it) }
        if (genuine >= 0) return genuine
        return messages.indexOfLast { it.role == "user" }.coerceAtLeast(0)
    }

    internal fun isHarnessMessage(m: ChatRequestMessage): Boolean {
        val c = m.content?.trimStart() ?: return false
        return HARNESS_PREFIXES.any { c.startsWith(it) }
    }

    /** How the agent loop, the quality gate and this pruner open the user messages they add. */
    private val HARNESS_PREFIXES = listOf(
        "Tool results:", "Your previous reply was cut off", "Your reply ended in the middle of",
        "Your last reply contained a tool call", "The screenshots your last tool call captured",
        "Automatic quality review (by the Oro app", "[Oro:", "[Earlier parts of this conversation",
        "If the task is complete, call finish_task"
    )

    /**
     * Give every assistant tool call a result.
     *
     * A run stopped mid-tool (the user pressed Stop, the app was killed) leaves
     * an assistant message whose tool_calls have no `tool` replies, and every
     * provider rejects the next request with a 400 until that is fixed. Missing
     * results are filled in, in place, right after their call.
     */
    fun closeDanglingToolCalls(history: MutableList<ChatRequestMessage>) {
        var i = 0
        while (i < history.size) {
            val m = history[i]
            val calls = m.toolCalls
            if (m.role == "assistant" && !calls.isNullOrEmpty()) {
                var j = i + 1
                val answered = mutableSetOf<String>()
                while (j < history.size && history[j].role == "tool") {
                    history[j].toolCallId?.let { answered += it }
                    j++
                }
                val missing = calls.filter { it.id !in answered }
                missing.forEachIndexed { k, call ->
                    history.add(
                        j + k,
                        ChatRequestMessage(
                            role = "tool",
                            content = "Not run: the previous run was stopped before this tool call executed.",
                            toolCallId = call.id,
                            name = call.function.name
                        )
                    )
                }
                i = j + missing.size
            } else {
                i++
            }
        }
    }

    /**
     * Index from which messages are kept verbatim: roughly the last
     * [PROTECT_RECENT_TOKENS]. Not "everything after the last user message":
     * in an agent run the whole run comes after it, and that is exactly the
     * traffic that overflows the window.
     */
    private fun protectedStart(messages: List<ChatRequestMessage>): Int {
        var tokens = 0
        var i = messages.size
        while (i > 0) {
            val next = tokens + TokenEstimator.estimate(messages[i - 1])
            if (next > PROTECT_RECENT_TOKENS) break
            tokens = next
            i--
        }
        return i
    }

    /** Index ranges of: user | assistant(+ its tool results) | other single messages. */
    private fun unitRanges(messages: List<ChatRequestMessage>): List<IntRange> {
        val units = mutableListOf<IntRange>()
        for ((i, m) in messages.withIndex()) {
            val last = units.lastOrNull()
            if (m.role == "tool" && last != null && messages[last.first].role == "assistant" && last.last == i - 1) {
                units[units.lastIndex] = last.first..i
            } else {
                units += i..i
            }
        }
        return units
    }

    /** How the agent loop opens a text-protocol results message. */
    internal const val TEXT_RESULTS_HEADER = "Tool results:"
    private val TEXT_RESULT = Regex("""(?s)(<tool_result name="[^"]*">\n?)(.*?)(\n?</tool_result>)""")
    private val TEXT_PARAMETER = Regex("""(?s)(<parameter=[\w.\-]+>)(.*?)(</parameter>)""")

    private fun shrinkTextResults(content: String): String = TEXT_RESULT.replace(content) { m ->
        if (m.groupValues[2].length > PRUNED_TOOL_OUTPUT.length) m.groupValues[1] + PRUNED_TOOL_OUTPUT + m.groupValues[3]
        else m.value
    }

    /** Large raw parameter values (whole files) in an old text-protocol call. */
    private fun shrinkTextCalls(content: String): String = TEXT_PARAMETER.replace(content) { m ->
        val value = m.groupValues[2]
        if (value.length > 200) m.groupValues[1] + "[${value.length} characters omitted]" + m.groupValues[3] else m.value
    }

    private fun shrinkCall(call: ToolCallRequest): ToolCallRequest {
        val args = call.function.arguments
        if (args.length <= LARGE_ARGUMENT_CHARS) return call
        val obj = runCatching { AiHttp.json.parseToJsonElement(args) as? JsonObject }.getOrNull()
        val shrunk = if (obj != null) {
            JsonObject(obj.mapValues { (_, v) ->
                val s = (v as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (s != null && s.length > 200) JsonPrimitive("[${s.length} characters omitted]") else v
            }).toString()
        } else {
            "{}"
        }
        return call.copy(function = FunctionCall(call.function.name, shrunk))
    }
}
