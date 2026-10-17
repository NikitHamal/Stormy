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

        val lastUser = messages.indexOfLast { it.role == "user" }.coerceAtLeast(0)
        val protectFrom = protectedStart(messages)

        // 1 + 2: shrink old tool traffic.
        val shrunk = messages.mapIndexed { i, m ->
            if (i >= protectFrom) return@mapIndexed m
            when {
                m.role == "tool" && (m.content?.length ?: 0) > PRUNED_TOOL_OUTPUT.length ->
                    m.copy(content = PRUNED_TOOL_OUTPUT)
                m.role == "assistant" && m.toolCalls != null ->
                    m.copy(toolCalls = m.toolCalls.map { shrinkCall(it) })
                else -> m
            }
        }
        if (TokenEstimator.estimate(shrunk) <= budgetTokens) return shrunk

        // 3: drop whole exchanges from the front.
        val units = splitIntoUnits(shrunk)
        val keepFromUnit = units.indexOfFirst { unit -> unit.any { it === shrunk.getOrNull(lastUser) } }
            .let { if (it < 0) units.lastIndex else it }
        var start = 0
        fun assembled(from: Int): List<ChatRequestMessage> {
            val kept = units.drop(from).flatten()
            return if (from > 0) listOf(ChatRequestMessage(role = "user", content = OMITTED_NOTE)) + kept else kept
        }
        while (start < keepFromUnit && TokenEstimator.estimate(assembled(start)) > budgetTokens) start++
        var result = assembled(start)

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

    /** user | assistant(+ its tool results) | other single messages. */
    private fun splitIntoUnits(messages: List<ChatRequestMessage>): List<List<ChatRequestMessage>> {
        val units = mutableListOf<MutableList<ChatRequestMessage>>()
        for (m in messages) {
            if (m.role == "tool" && units.isNotEmpty() && units.last().first().role == "assistant") {
                units.last().add(m)
            } else {
                units.add(mutableListOf(m))
            }
        }
        return units
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
