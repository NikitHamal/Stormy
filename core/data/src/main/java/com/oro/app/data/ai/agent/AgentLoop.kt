package com.oro.app.data.ai.agent

import com.oro.app.data.ai.AssistantMessageWithToolCalls
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.Tool
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.ToolAliases
import com.oro.app.data.ai.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.random.Random

/** One request to the model. */
data class AgentRequest(
    val messages: List<ChatRequestMessage>,
    /** Null when tools must not be sent (chat mode, or a model without native tools). */
    val tools: List<Tool>?
)

/** Streams one completion. */
fun interface AgentModelClient {
    suspend fun stream(request: AgentRequest): Flow<StreamEvent>
}

/** Executes one tool call. May suspend for as long as it needs (ask_user). */
fun interface AgentToolRunner {
    suspend fun run(call: ToolCallResponse): ToolResult
}

/** What the run is doing right now, for the working indicator. */
data class AgentStatus(val kind: Kind, val label: String) {
    enum class Kind { THINKING, WRITING, RUNNING_TOOL, WAITING_FOR_USER, RETRYING, COMPACTING }

    companion object {
        val Thinking = AgentStatus(Kind.THINKING, "Thinking")
        val Writing = AgentStatus(Kind.WRITING, "Writing")
    }
}

/** Callbacks from a run. All optional. Called on the loop's coroutine. */
interface AgentLoopListener {
    fun onStatus(status: AgentStatus) {}
    /** A model call is starting. [attempt] > 1 means a retry. */
    fun onModelCallStarted(iteration: Int, attempt: Int) {}
    fun onReasoning(delta: String) {}
    fun onText(delta: String) {}
    /** The partial output of the current attempt is void; a retry follows. */
    fun onAttemptDiscarded(reason: String) {}
    /**
     * The model's turn finished. [visibleText] is what should remain on screen
     * for this turn: the streamed text minus any tool-call markup recovered
     * from it.
     */
    fun onTurnComplete(visibleText: String, toolCalls: List<ToolCallResponse>) {}
    fun onToolStarted(call: ToolCallResponse) {}
    fun onToolFinished(call: ToolCallResponse, result: ToolResult) {}
    fun onUsage(promptTokens: Int, completionTokens: Int) {}
    /** Same report with the cache hits; defaults to [onUsage]. */
    fun onUsage(promptTokens: Int, completionTokens: Int, cachedTokens: Int) = onUsage(promptTokens, completionTokens)
}

sealed interface AgentRunResult {
    /** The model answered without requesting more tools. */
    data class Completed(val finalText: String) : AgentRunResult
    /** A failure that retries could not fix. [retryable] if trying again later may work. */
    data class Failed(val message: String, val retryable: Boolean) : AgentRunResult
    /** Stopped by the iteration guard. */
    data class IterationLimit(val iterations: Int) : AgentRunResult
}

data class AgentLoopConfig(
    /** Model calls per run before stopping (each tool round trip is one). */
    val maxIterations: Int = 40,
    /** Whether tool-produced screenshots are shown to the model (vision models only). */
    val attachImages: Boolean = false,
    /** Times a run asks the model to resend a tool call that did not parse. */
    val maxMalformedCallRetries: Int = 2,
    /** Retries per model call for transient failures. */
    val maxRetries: Int = 4,
    /** Retries for an empty or reasoning-only reply. */
    val emptyResponseRetries: Int = 2,
    val baseBackoffMs: Long = 2_000,
    val maxBackoffMs: Long = 45_000,
    /** Longest silence tolerated on an open stream before it is abandoned. */
    val idleTimeoutMs: Long = 150_000,
    /** Cap on one tool result kept in history, in characters. */
    val maxToolResultChars: Int = 60_000,
    /** Times a reply cut off by the output limit is continued. */
    val maxLengthContinuations: Int = 2
)

/**
 * The agent loop: call the model, run the tools it asks for, feed results
 * back, repeat until it answers.
 *
 * ### Why it is its own class
 *
 * This lived inside `EditorViewModel.sendAiRequest`, recursing into itself from
 * inside the stream collector. Several exits did nothing at all: a stream that
 * closed without `[DONE]` never reached the completion branch, so the run
 * ended with no reply, no error and the indicator gone -- the "Thinking
 * appears once then disappears" report. Here every path ends in exactly one
 * [AgentRunResult], and the whole loop runs under unit tests with a fake model.
 *
 * ### Guarantees
 *  - Transient failures (429/402-limit/5xx, dropped or stalled streams, empty
 *    replies) are retried with exponential backoff and jitter, honouring the
 *    server's retry hint, up to [AgentLoopConfig.maxRetries].
 *  - A stream silent for [AgentLoopConfig.idleTimeoutMs] is abandoned and retried.
 *  - A context-length error re-prepares the request at higher pressure, so
 *    the caller can prune harder, before giving up.
 *  - Native tool calls are used when present; otherwise tool calls written as
 *    text are recovered ([TextToolCallParser]) and their results fed back as a
 *    user message, which every provider accepts.
 *  - Consecutive read-only calls run concurrently; anything that writes, and
 *    anything interactive, runs alone and in order.
 *  - Cancellation propagates: cancelling the calling coroutine stops the
 *    stream, any running tool, and any pending question.
 */
class AgentLoop(
    private val client: AgentModelClient,
    private val tools: AgentToolRunner,
    private val config: AgentLoopConfig = AgentLoopConfig(),
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val random: Random = Random.Default
) {

    /**
     * Run until the model stops calling tools.
     *
     * @param conversation history without the system message; appended to in
     *   place as the run proceeds, so the caller keeps it afterwards.
     * @param prepare builds the request for the current history. `pressure` is
     *   0 normally and rises after a context-length error, asking for harder
     *   pruning.
     * @param knownTools names the model may call, for text-call recovery.
     */
    suspend fun run(
        conversation: MutableList<ChatRequestMessage>,
        knownTools: Set<String>,
        listener: AgentLoopListener,
        prepare: suspend (history: List<ChatRequestMessage>, pressure: Int) -> AgentRequest
    ): AgentRunResult {
        var iteration = 0
        var lengthContinuations = 0
        var malformedNudges = 0
        val recentCalls = ArrayDeque<String>()

        while (true) {
            if (iteration >= config.maxIterations) return AgentRunResult.IterationLimit(iteration)
            iteration++

            val turn = when (val outcome = callModel(conversation, iteration, listener, prepare, knownTools.isNotEmpty())) {
                is CallOutcome.Failure -> return AgentRunResult.Failed(outcome.message, outcome.retryable)
                is CallOutcome.Success -> outcome.turn
            }

            var calls = turn.toolCalls
            var visible = turn.content
            var fromText = false
            if (calls.isEmpty() && knownTools.isNotEmpty()) {
                val parsed = TextToolCallParser.parse(turn.content, knownTools)
                if (parsed.calls.isNotEmpty()) {
                    calls = parsed.calls
                    visible = parsed.visibleText
                    fromText = true
                }
            }
            // Tool-call markup that did not parse (bad JSON, unknown tool, a
            // template the model half-remembered): nothing ran. Say so and ask
            // again, rather than ending the run on a reply full of markup.
            val malformed = calls.isEmpty() && knownTools.isNotEmpty() &&
                TextToolCallParser.looksLikeToolCall(turn.content)
            if (malformed) {
                visible = turn.content.substring(0, TextToolCallParser.visiblePrefixLength(turn.content)).trimEnd()
            }
            if (malformed && malformedNudges < config.maxMalformedCallRetries) {
                malformedNudges++
                listener.onTurnComplete(visible, emptyList())
                conversation += ChatRequestMessage(role = "assistant", content = stripThinking(turn.content))
                conversation += ChatRequestMessage(role = "user", content = malformedCallNudge(knownTools))
                continue
            }
            calls = calls.mapIndexed { i, c ->
                if (c.id.isBlank()) c.copy(id = "call_${iteration}_$i") else c
            }
            listener.onTurnComplete(visible, calls)

            val historyText = stripThinking(turn.content)

            if (calls.isEmpty()) {
                if (turn.finishReason == "length" && lengthContinuations < config.maxLengthContinuations) {
                    lengthContinuations++
                    conversation += ChatRequestMessage(role = "assistant", content = historyText)
                    conversation += ChatRequestMessage(
                        role = "user",
                        content = "Your previous reply was cut off by the output limit. " +
                            "Continue exactly where it stopped, without repeating anything."
                    )
                    continue
                }
                conversation += ChatRequestMessage(role = "assistant", content = historyText)
                return AgentRunResult.Completed(stripThinking(visible))
            }

            if (fromText) {
                conversation += ChatRequestMessage(role = "assistant", content = historyText)
            } else {
                conversation += AssistantMessageWithToolCalls(
                    content = historyText.ifBlank { null },
                    toolCalls = calls
                ).toChatRequestMessage()
            }

            val results = executeCalls(calls, listener)

            // Repetition guard: the same call three times running is a loop.
            val finalResults = results.mapIndexed { i, result ->
                val key = calls[i].function.name + "\u0000" + calls[i].function.arguments
                recentCalls.addLast(key)
                while (recentCalls.size > 3) recentCalls.removeFirst()
                if (recentCalls.size == 3 && recentCalls.all { it == key }) {
                    result.copy(
                        output = result.output + "\n\n[Note: this exact call has now been made three " +
                            "times in a row. Its result will not change; try a different approach.]"
                    )
                } else result
            }

            if (fromText) {
                conversation += ChatRequestMessage(
                    role = "user",
                    content = buildString {
                        append("Tool results:\n")
                        calls.forEachIndexed { i, call ->
                            append("<tool_result name=\"").append(call.function.name).append("\">\n")
                            append(resultText(finalResults[i])).append("\n</tool_result>\n")
                        }
                        append("Continue. When the task is done, reply with a short summary and no tool call.")
                    }
                )
            } else {
                calls.forEachIndexed { i, call ->
                    conversation += ChatRequestMessage(
                        role = "tool",
                        content = resultText(finalResults[i]),
                        toolCallId = call.id,
                        name = call.function.name
                    )
                }
            }

            // Screenshots go to the model as an image turn (tool messages cannot
            // carry images on most APIs). Only the newest set is kept: images
            // are the most expensive thing in a context window.
            val images = finalResults.flatMap { it.images }
            if (images.isNotEmpty() && config.attachImages) {
                for (i in conversation.indices) {
                    if (conversation[i].images.isNotEmpty()) conversation[i] = conversation[i].copy(images = emptyList())
                }
                conversation += ChatRequestMessage(
                    role = "user",
                    content = "Screenshots of the live preview, as your tool requested. Look at them before you continue.",
                    images = images.take(3)
                )
            }

            // Legacy explicit finish: the summary is the answer.
            val finish = calls.indexOfFirst { ToolAliases.canonicalName(it.function.name) == "finish_task" }
            if (finish >= 0) {
                val summary = runCatching {
                    com.oro.app.data.ai.tools.JsonParserUtils.parseToJsonObject(calls[finish].function.arguments)
                        ?.get("summary")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                }.getOrNull().orEmpty()
                return AgentRunResult.Completed(listOf(stripThinking(visible), summary).filter { it.isNotBlank() }.joinToString("\n\n"))
            }
        }
    }

    private fun malformedCallNudge(knownTools: Set<String>): String = buildString {
        append("Your last reply contained a tool call that could not be read, so nothing ran. ")
        append("Send it again in exactly this format, with valid JSON (escape quotes and newlines inside strings):\n")
        append("<tool_call>\n{\"name\": \"TOOL_NAME\", \"arguments\": {...}}\n</tool_call>\n")
        append("TOOL_NAME must be one of: ").append(knownTools.sorted().joinToString(", ")).append('.')
    }

    // ------------------------------------------------------------ model call

    private data class Turn(
        val content: String,
        val reasoning: String,
        val toolCalls: List<ToolCallResponse>,
        val finishReason: String?,
        val error: StreamEvent.Error?
    ) {
        val isEmpty: Boolean get() = content.isBlank() && toolCalls.isEmpty()
    }

    private sealed interface CallOutcome {
        data class Success(val turn: Turn) : CallOutcome
        data class Failure(val message: String, val retryable: Boolean) : CallOutcome
    }

    private suspend fun callModel(
        conversation: List<ChatRequestMessage>,
        iteration: Int,
        listener: AgentLoopListener,
        prepare: suspend (List<ChatRequestMessage>, Int) -> AgentRequest,
        hideToolMarkup: Boolean = false
    ): CallOutcome {
        var attempt = 0
        var emptyRetries = 0
        var pressure = 0
        while (true) {
            attempt++
            listener.onStatus(AgentStatus.Thinking)
            listener.onModelCallStarted(iteration, attempt)
            val request = prepare(conversation, pressure)
            val turn = collectTurn(request, listener, hideToolMarkup)

            val error = turn.error
            if (error != null) {
                if (isContextOverflow(error) && pressure < 2) {
                    pressure++
                    listener.onAttemptDiscarded("context too long")
                    listener.onStatus(AgentStatus(AgentStatus.Kind.COMPACTING, "Trimming context"))
                    continue
                }
                if (error.retryable && attempt <= config.maxRetries) {
                    val wait = backoff(attempt, error.retryAfterMs)
                    listener.onAttemptDiscarded(error.message)
                    listener.onStatus(
                        AgentStatus(
                            AgentStatus.Kind.RETRYING,
                            "${shortReason(error)}, retrying in ${(wait + 999) / 1000}s"
                        )
                    )
                    sleep(wait)
                    continue
                }
                return CallOutcome.Failure(error.message, error.retryable)
            }

            if (turn.isEmpty) {
                if (emptyRetries < config.emptyResponseRetries) {
                    emptyRetries++
                    attempt-- // empty replies have their own budget
                    listener.onAttemptDiscarded("empty response")
                    listener.onStatus(AgentStatus(AgentStatus.Kind.RETRYING, "Empty reply, retrying"))
                    sleep(backoff(emptyRetries, null) / 2)
                    continue
                }
                return CallOutcome.Failure(
                    "The model returned an empty response" +
                        if (turn.reasoning.isNotBlank()) " (it reasoned but wrote no answer)." else ".",
                    retryable = true
                )
            }
            return CallOutcome.Success(turn)
        }
    }

    /** Collect one streamed completion, with an idle watchdog. */
    private suspend fun collectTurn(
        request: AgentRequest,
        listener: AgentLoopListener,
        hideToolMarkup: Boolean = false
    ): Turn {
        val content = StringBuilder()
        // Text-protocol tool calls (often a whole file) stream as markup; only
        // the prose before them is shown live. onTurnComplete sets the final
        // visible text either way.
        var shown = 0
        var markupFound = false
        val reasoning = StringBuilder()
        var toolCalls = emptyList<ToolCallResponse>()
        var finishReason: String? = null
        var error: StreamEvent.Error? = null
        var completed = false
        var writing = false

        val flow = try {
            client.stream(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Turn("", "", emptyList(), null, StreamEvent.Error(e.message ?: "Request failed", retryable = true))
        }

        coroutineScope {
            val channel = flow.buffer(Channel.UNLIMITED).produceIn(this)
            try {
                while (true) {
                    val received = withTimeoutOrNull(config.idleTimeoutMs) { channel.receiveCatching() }
                    if (received == null) {
                        error = StreamEvent.Error(
                            "No response from the model for ${config.idleTimeoutMs / 1000}s",
                            retryable = true
                        )
                        break
                    }
                    if (received.isClosed) {
                        received.exceptionOrNull()?.let { t ->
                            if (t is CancellationException) throw t
                            error = StreamEvent.Error(t.message ?: "Stream failed", retryable = true)
                        }
                        break
                    }
                    when (val event = received.getOrThrow()) {
                        is StreamEvent.ContentDelta -> {
                            content.append(event.content)
                            if (!writing && content.isNotBlank()) {
                                writing = true
                                listener.onStatus(AgentStatus.Writing)
                            }
                            if (!hideToolMarkup) {
                                listener.onText(event.content)
                            } else if (!markupFound) {
                                val visible = TextToolCallParser.visiblePrefixLength(content, shown)
                                if (visible > shown) {
                                    listener.onText(content.substring(shown, visible))
                                    shown = visible
                                }
                                if (visible < content.length &&
                                    TextToolCallParser.looksLikeToolCall(content.substring(visible))
                                ) {
                                    markupFound = true
                                    listener.onStatus(AgentStatus(AgentStatus.Kind.WRITING, "Preparing tool call"))
                                }
                            }
                        }
                        is StreamEvent.ReasoningDelta -> {
                            reasoning.append(event.reasoning)
                            listener.onReasoning(event.reasoning)
                        }
                        is StreamEvent.ToolCalls -> toolCalls = toolCalls + event.toolCalls
                        is StreamEvent.FinishReason -> finishReason = event.reason
                        is StreamEvent.Usage -> listener.onUsage(event.promptTokens, event.completionTokens, event.cachedTokens)
                        is StreamEvent.Error -> { error = event; break }
                        is StreamEvent.Completed -> { completed = true; break }
                        is StreamEvent.Started, is StreamEvent.RawSSE -> Unit
                    }
                }
            } finally {
                channel.cancel()
            }
        }

        // A stream that ended without a completion marker and without output
        // is a dropped connection, not an answer.
        if (error == null && !completed && content.isEmpty() && toolCalls.isEmpty()) {
            error = StreamEvent.Error("The connection closed before the model replied", retryable = true)
        }
        return Turn(content.toString(), reasoning.toString(), toolCalls, finishReason, error)
    }

    // ----------------------------------------------------------------- tools

    private suspend fun executeCalls(
        calls: List<ToolCallResponse>,
        listener: AgentLoopListener
    ): List<ToolResult> {
        val results = arrayOfNulls<ToolResult>(calls.size)
        var i = 0
        while (i < calls.size) {
            // Gather a run of consecutive read-only calls.
            var j = i
            while (j < calls.size && isReadOnly(calls[j])) j++
            if (j - i >= 2) {
                val group = (i until j).toList()
                group.forEach { listener.onToolStarted(calls[it]) }
                listener.onStatus(AgentStatus(AgentStatus.Kind.RUNNING_TOOL, "Running ${group.size} tools"))
                coroutineScope {
                    group.map { idx ->
                        async {
                            val r = runOne(calls[idx])
                            results[idx] = r
                            listener.onToolFinished(calls[idx], r)
                        }
                    }.awaitAll()
                }
                i = j
            } else {
                val call = calls[i]
                listener.onToolStarted(call)
                val name = ToolAliases.canonicalName(call.function.name)
                val kind = when (name) {
                    "ask_user", "request_approval" -> AgentStatus.Kind.WAITING_FOR_USER
                    else -> AgentStatus.Kind.RUNNING_TOOL
                }
                listener.onStatus(AgentStatus(kind, AgentTools.activityLabel(name, subjectOf(call))))
                val r = runOne(call)
                results[i] = r
                listener.onToolFinished(call, r)
                i++
            }
        }
        return results.map { it ?: ToolResult(false, "", "Tool did not run") }
    }

    private suspend fun runOne(call: ToolCallResponse): ToolResult = try {
        tools.run(call)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ToolResult(false, "", "Tool crashed: ${e.message ?: e.javaClass.simpleName}")
    }

    /** Read-only tools, and subagents (which run in their own context). */
    private fun isReadOnly(call: ToolCallResponse): Boolean =
        ToolAliases.canonicalName(call.function.name).let { it in AgentTools.readOnly || it == "spawn_subagent" }

    private fun resultText(result: ToolResult): String {
        val raw = if (result.success) result.output.ifBlank { "(no output)" }
        else "Error: ${result.error ?: "failed"}" + if (result.output.isNotBlank()) "\n${result.output}" else ""
        return if (raw.length > config.maxToolResultChars) {
            raw.take(config.maxToolResultChars) +
                "\n[Output truncated at ${config.maxToolResultChars} characters. Narrow the request " +
                "(e.g. read_file with offset/limit) to see the rest.]"
        } else raw
    }

    // --------------------------------------------------------------- helpers

    private fun backoff(attempt: Int, hintMs: Long?): Long {
        val exp = config.baseBackoffMs * (1L shl (attempt - 1).coerceIn(0, 10))
        val jitter = (exp * 0.25 * random.nextDouble()).toLong()
        val computed = min(config.maxBackoffMs, exp + jitter)
        // A server hint wins when it asks for longer, within reason.
        return if (hintMs != null && hintMs > 0) hintMs.coerceAtMost(120_000).coerceAtLeast(computed / 2) else computed
    }

    private fun shortReason(error: StreamEvent.Error): String = when (error.httpCode) {
        429, 402 -> "Rate limited"
        null -> if (error.message.contains("No response", true)) "No response" else "Connection problem"
        in 500..599 -> "Provider error"
        else -> "Request failed"
    }

    companion object {
        private val CONTEXT_OVERFLOW = Regex(
            "context[ _-]?length|maximum context|context window|too many tokens|" +
                "reduce the length|prompt is too long|input is too long|token limit|exceeds the (model|max)",
            RegexOption.IGNORE_CASE
        )

        fun isContextOverflow(error: StreamEvent.Error): Boolean =
            error.httpCode == 413 || (error.httpCode in setOf(400, 422, null) && CONTEXT_OVERFLOW.containsMatchIn(error.message))

        private val THINK_BLOCK = Regex("(?is)<(think|thinking|reasoning)>.*?</\\1>")

        /** Reasoning shown in the UI is not sent back as conversation. */
        fun stripThinking(text: String): String = text.replace(THINK_BLOCK, "").trim()

        private fun subjectOf(call: ToolCallResponse): String? {
            val args = com.oro.app.data.ai.tools.JsonParserUtils.parseToJsonObject(call.function.arguments) ?: return null
            for (key in listOf("path", "file_path", "pattern", "url", "source", "command", "question", "query")) {
                val v = (args[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (!v.isNullOrBlank()) return v
            }
            return null
        }
    }
}
