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
import kotlinx.serialization.json.JsonObject
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
    enum class Kind { THINKING, WRITING, RUNNING_TOOL, WAITING_FOR_USER, RETRYING, COMPACTING, VERIFYING }

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
    /**
     * Model calls per run before stopping (each tool round trip is one).
     * Unlimited by default: a healthy run is never paused for its length.
     * Runaway runs are caught by stuck detection instead ([stuckNudgeAfter]).
     */
    val maxIterations: Int = UNLIMITED,
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
    val maxLengthContinuations: Int = 2,
    /** Times the final check may send the agent back to fix what it found. */
    val maxQualityPasses: Int = 2,
    /** Times a run asks the model to resend a call cut off by the output limit. */
    val maxTruncatedCallRetries: Int = 3,
    /**
     * Consecutive tool rounds in which every call failed before the model is
     * told to step back and change approach; the nudge repeats at multiples.
     */
    val stuckNudgeAfter: Int = 3,
    /** Consecutive all-failed tool rounds after which the run gives up. */
    val stuckStopAfter: Int = 8,
    /**
     * Times the same call with the same result may recur within the recent
     * window ([REPEAT_WINDOW] calls) before the run gives up.
     */
    val repeatStopAfter: Int = 6,
    /**
     * JSON parameter schemas of tools beyond the built-ins (MCP servers, the
     * device agent's tools), by name. Text-protocol values are typed by these;
     * the built-in tools' schemas are always known.
     */
    val toolSchemas: Map<String, JsonObject> = emptyMap()
) {
    companion object {
        /** No iteration cap. */
        const val UNLIMITED: Int = Int.MAX_VALUE

        /** Calls remembered for cycle detection. */
        const val REPEAT_WINDOW: Int = 30
    }
}

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

    /** Schemas that type text-protocol values: the built-ins plus whatever the caller offers. */
    private val schemas: Map<String, JsonObject> by lazy { AgentTools.parameterSchemas + config.toolSchemas }

    /**
     * Run until the model stops calling tools.
     *
     * @param conversation history without the system message; appended to in
     *   place as the run proceeds, so the caller keeps it afterwards.
     * @param prepare builds the request for the current history. `pressure` is
     *   0 normally and rises after a context-length error, asking for harder
     *   pruning.
     * @param knownTools names the model may call, for text-call recovery.
     * @param finalCheck runs when the model says it is done. A non-null result
     *   is a list of defects; it goes back as a user message and the run
     *   continues, at most [AgentLoopConfig.maxQualityPasses] times. This is
     *   the quality gate that gives every model, weak ones especially, a
     *   careful engineer's second pass (see QualityGate).
     */
    suspend fun run(
        conversation: MutableList<ChatRequestMessage>,
        knownTools: Set<String>,
        listener: AgentLoopListener,
        finalCheck: (suspend () -> String?)? = null,
        prepare: suspend (history: List<ChatRequestMessage>, pressure: Int) -> AgentRequest
    ): AgentRunResult {
        var iteration = 0
        var lengthContinuations = 0
        var qualityPasses = 0
        var malformedNudges = 0
        var truncatedRetries = 0
        var reasoningNudged = false
        val stuck = StuckDetector(config)

        while (true) {
            if (iteration >= config.maxIterations) return AgentRunResult.IterationLimit(iteration)
            iteration++

            val turn = when (val outcome = callModel(conversation, iteration, listener, prepare, knownTools.isNotEmpty())) {
                is CallOutcome.Failure -> return AgentRunResult.Failed(outcome.message, outcome.retryable)
                is CallOutcome.OnlyReasoning -> {
                    // Thinking models sometimes spend the whole reply reasoning. One
                    // plain instruction usually gets the answer; a second time, stop.
                    if (reasoningNudged) return AgentRunResult.Failed(outcome.message, retryable = true)
                    reasoningNudged = true
                    conversation += ChatRequestMessage(role = "user", content = REASONING_ONLY_NUDGE)
                    continue
                }
                is CallOutcome.Success -> outcome.turn.also { if (!it.isEmpty) reasoningNudged = false }
            }

            var calls = turn.toolCalls
            var visible = turn.content
            var fromText = false
            // Calls the output limit cut off: never run, the model resends them.
            var cutOff = emptyList<String>()
            // The same calls named with their target ("write_file app.js"), for the nudge.
            var cutLabels = emptyList<String>()
            if (calls.isEmpty() && knownTools.isNotEmpty()) {
                val parsed = TextToolCallParser.parse(turn.content, knownTools, schemas)
                var parsedCalls = parsed.calls
                val cut = parsed.incomplete.toMutableList()
                // A block left open is suspect when the reply hit the limit, and
                // a file write left open is suspect when the provider could not
                // say why the reply ended: the rest of the call may be missing.
                val suspect = parsedCalls.filter { c ->
                    c.id in parsed.openEnded && (turn.finishReason == "length" ||
                        (turn.finishReason == null && ToolAliases.canonicalName(c.function.name) in FILE_WRITES))
                }
                if (suspect.isNotEmpty()) {
                    parsedCalls = parsedCalls - suspect.toSet()
                    cut += suspect.map { it.function.name }
                }
                // A read-only call missing only its closers after an explicit
                // stop is complete enough to run: nothing is written.
                if (turn.finishReason == "stop") {
                    val salvage = parsed.incompleteCalls.filter { isReadOnly(it) }
                    if (salvage.isNotEmpty()) {
                        parsedCalls = parsedCalls + salvage
                        salvage.forEach { cut.remove(it.function.name) }
                    }
                }
                cutOff = cut
                val cutCalls = (parsed.incompleteCalls + suspect).toMutableList()
                cutLabels = cut.map { name ->
                    val c = cutCalls.firstOrNull { it.function.name == name }?.also { cutCalls.remove(it) }
                    label(name, c?.function?.arguments)
                }
                if (parsedCalls.isNotEmpty() || cutOff.isNotEmpty()) {
                    calls = parsedCalls
                    visible = parsed.visibleText
                    fromText = true
                }
            } else if (calls.isNotEmpty() && turn.finishReason == "length") {
                // Native calls stream in order, so only the last can be cut mid-arguments.
                val last = calls.last()
                if (!isCompleteJson(last.function.arguments)) {
                    calls = calls.dropLast(1)
                    cutOff = listOf(last.function.name)
                    cutLabels = listOf(label(last.function.name, last.function.arguments))
                }
            }
            // Tool-call markup that did not parse (bad JSON, unknown tool, a
            // template the model half-remembered): nothing ran. Say so and ask
            // again, rather than ending the run on a reply full of markup.
            val malformed = calls.isEmpty() && cutOff.isEmpty() && knownTools.isNotEmpty() &&
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
            if (malformed) {
                // Still unreadable after the nudges: say so, rather than
                // presenting the prose before the broken call as a finished answer.
                listener.onTurnComplete(visible, emptyList())
                conversation += ChatRequestMessage(role = "assistant", content = stripThinking(visible).ifBlank { "(unreadable tool call)" })
                return AgentRunResult.Failed(
                    "The model kept writing tool calls the app could not read, so nothing ran. " +
                        "Retry, or pick a model with native tool calling.",
                    retryable = true
                )
            }
            calls = calls.mapIndexed { i, c ->
                if (c.id.isBlank()) c.copy(id = "call_${iteration}_$i") else c
            }
            listener.onTurnComplete(visible, calls)

            val historyText = stripThinking(turn.content)

            // Only cut-off calls: nothing ran. Ask for them again, smaller.
            if (calls.isEmpty() && cutOff.isNotEmpty()) {
                if (truncatedRetries >= config.maxTruncatedCallRetries) {
                    return AgentRunResult.Failed(
                        "The model's replies keep getting cut off by its output limit before a " +
                            "${cutOff.first()} call is complete. Try a model with a larger output limit, " +
                            "or ask for a smaller change.",
                        retryable = false
                    )
                }
                truncatedRetries++
                // The cut-off markup is not kept: resending a half file teaches nothing.
                val prose = if (fromText) stripThinking(visible) else historyText
                conversation += ChatRequestMessage(role = "assistant", content = prose.ifBlank { "(reply cut off)" })
                conversation += ChatRequestMessage(role = "user", content = truncatedCallNudge(cutLabels.ifEmpty { cutOff }, fromText))
                continue
            }

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
                if (finalCheck != null && qualityPasses < config.maxQualityPasses && iteration < config.maxIterations) {
                    listener.onStatus(AgentStatus(AgentStatus.Kind.VERIFYING, "Reviewing quality"))
                    val feedback = try { finalCheck() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                    if (!feedback.isNullOrBlank()) {
                        qualityPasses++
                        conversation += ChatRequestMessage(role = "user", content = feedback)
                        continue
                    }
                }
                return AgentRunResult.Completed(stripThinking(visible))
            }

            if (fromText) {
                // Keep only what ran: a cut-off tail must not be imitated next turn.
                val kept = if (cutOff.isEmpty()) historyText else {
                    stripThinking(visible) + "\n" + calls.joinToString("\n") {
                        TextToolProtocol.formatCall(it.function.name, it.function.arguments)
                    }
                }
                conversation += ChatRequestMessage(role = "assistant", content = kept.trim())
            } else {
                conversation += AssistantMessageWithToolCalls(
                    content = historyText.ifBlank { null },
                    toolCalls = calls
                ).toChatRequestMessage()
            }

            val results = executeCalls(calls, listener)
            // Calls ran: the run is moving. Retry budgets are for consecutive
            // trouble, not a lifetime allowance -- runs have no step cap now.
            malformedNudges = 0
            truncatedRetries = 0
            lengthContinuations = 0

            // Stuck detection: repeated calls with unchanged results and
            // streaks of failures earn a corrective note; a loop that
            // outlasts the notes ends the run.
            val verdict = stuck.record(calls, results)
            val finalResults = if (verdict.note == null) results else results.mapIndexed { i, r ->
                if (i == results.lastIndex) r.copy(output = r.output + "\n\n" + verdict.note) else r
            }
            val cutNote = if (cutOff.isEmpty()) null else truncatedCallNudge(cutLabels.ifEmpty { cutOff }, fromText)

            if (fromText) {
                conversation += ChatRequestMessage(
                    role = "user",
                    content = buildString {
                        append("Tool results:\n")
                        calls.forEachIndexed { i, call ->
                            append("<tool_result name=\"").append(call.function.name).append("\">\n")
                            append(resultText(finalResults[i])).append("\n</tool_result>\n")
                        }
                        if (cutNote != null) append(cutNote).append('\n')
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
                if (cutNote != null) conversation += ChatRequestMessage(role = "user", content = cutNote)
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
                    content = "The screenshots your last tool call captured. Look at them before you continue.",
                    images = images.take(3)
                )
            }

            if (verdict.stop != null) return AgentRunResult.Failed(verdict.stop, retryable = false)

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

    /**
     * "write_file app.js": a cut-off call named with the file it targeted, read
     * from whatever arguments arrived (raw JSON may be unfinished).
     */
    private fun label(name: String, arguments: String?): String {
        val path = arguments?.let { PATH_IN_ARGS.find(it)?.groupValues?.get(1) }?.takeIf { it.isNotBlank() && it.length < 200 }
        return if (path != null) "$name $path" else name
    }

    /** What to tell the model about calls the output limit cut off. */
    private fun truncatedCallNudge(names: List<String>, textMode: Boolean): String = buildString {
        val what = names.distinct().joinToString(", ")
        append("Your reply ended in the middle of ")
        append(if (textMode) "a <tool_call> for $what" else "the $what call")
        append(" (the output limit, or the call was never closed), so that call was NOT run (a cut-off file ")
        append("would be broken). Close every call with </parameter>, </function> and </tool_call>. ")
        append("Send it again in smaller pieces: ")
        append("create a long file with write_file holding only its first part, then add the rest with further ")
        append("write_file calls with append=true, a few hundred lines each. Keep edits short.")
    }

    private fun malformedCallNudge(knownTools: Set<String>): String = buildString {
        append("Your last reply contained a tool call that could not be read, so nothing ran. ")
        append("Send it again in exactly this format (values are raw text, no JSON escaping):\n")
        append("<tool_call>\n<function=TOOL_NAME>\n<parameter=NAME>value</parameter>\n</function>\n</tool_call>\n")
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
        /** Retries came back empty but the model did reason: worth one explicit nudge. */
        data class OnlyReasoning(val message: String) : CallOutcome
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
                if (turn.reasoning.isNotBlank()) {
                    return CallOutcome.OnlyReasoning("The model returned an empty response (it reasoned but wrote no answer).")
                }
                return CallOutcome.Failure("The model returned an empty response.", retryable = true)
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
        // Text mode: the model may run on past its calls and invent their
        // results. Stop reading there -- it saves the wait, and the invented
        // results never reach the history.
        val textMode = hideToolMarkup && request.tools.isNullOrEmpty()
        var scanned = 0

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
                            if (textMode) {
                                val fake = TextToolCallParser.fakeResultStart(content, (scanned - FAKE_MARKER_OVERLAP).coerceAtLeast(0))
                                scanned = content.length
                                if (fake >= 0) {
                                    content.setLength(fake)
                                    if (finishReason == null) finishReason = "stop"
                                    completed = true
                                    break
                                }
                            }
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
        /** Longest fabricated-result marker, so a marker split across deltas is still found. */
        private const val FAKE_MARKER_OVERLAP = 24

        /** Calls whose missing tail could lose file content. */
        private val FILE_WRITES = setOf("write_file", "edit_file", "multi_edit")

        /**
         * Whether native call arguments are a complete JSON object, not cut off
         * mid-stream. The plain parser only (no repairs): repairing a cut-off
         * argument string is exactly how half a file would get written.
         */
        internal fun isCompleteJson(arguments: String): Boolean =
            arguments.isBlank() || runCatching {
                com.oro.app.data.ai.AiHttp.json.parseToJsonElement(arguments) is kotlinx.serialization.json.JsonObject
            }.getOrDefault(false)

        private val CONTEXT_OVERFLOW = Regex(
            "context[ _-]?length|maximum context|context window|too many tokens|" +
                "reduce the length|prompt is too long|input is too long|token limit|exceeds the (model|max)",
            RegexOption.IGNORE_CASE
        )

        fun isContextOverflow(error: StreamEvent.Error): Boolean =
            error.httpCode == 413 || (error.httpCode in setOf(400, 422, null) && CONTEXT_OVERFLOW.containsMatchIn(error.message))

        /** Sent once when a model keeps replying with reasoning only. */
        const val REASONING_ONLY_NUDGE =
            "[Oro: your last replies contained only reasoning -- no answer and no tool call, so nothing " +
                "happened. Reply now with the next tool call, or with your final answer if the task is done. " +
                "Keep any further reasoning short.]"

        private val PATH_IN_ARGS = Regex(""""(?:path|file_path)"\s*:\s*"([^"\n]+)"""")

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
