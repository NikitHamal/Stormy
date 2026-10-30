package com.oro.app.guard

import android.content.Context
import com.oro.app.R
import com.oro.app.browser.BrowserProse
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.ReasoningControls
import com.oro.app.data.ai.agent.AgentLoop
import com.oro.app.data.ai.agent.AgentLoopConfig
import com.oro.app.data.ai.agent.AgentLoopListener
import com.oro.app.data.ai.agent.AgentModelClient
import com.oro.app.data.ai.agent.AgentRequest
import com.oro.app.data.ai.agent.AgentRunResult
import com.oro.app.data.ai.agent.AgentStatus
import com.oro.app.data.ai.agent.AgentToolRunner
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.council.CouncilResult
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.BrowserDelegationTools
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.repository.AiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * The personal cyber-defence agent: one instance app-wide, like the browser
 * agent it consults. Defensive only -- it audits (phone, vault, project),
 * detects (URLs, advisories, exploitation news), traces (public records,
 * evidence records), verifies (the council of models) and guides recovery
 * and lawful countermeasures (block, rotate, report, harden).
 *
 * Refusing to retaliate is a feature, not a limitation: the guard never
 * attacks, scans or accesses a system the user does not own. It can work the
 * user's own phone, accounts and projects, and it can take an attacker's
 * infrastructure down the only way a defender legitimately can -- through the
 * registrar, host, browser blocklists and CERTs.
 *
 * The screen is a conversation: user messages, the guard's streamed replies,
 * tool activity as inline cards, and questions as tap-to-answer cards.
 * Everything environment-touching sits behind [Guard], so the tool contract,
 * the loop and the transcript run on the JVM against scripted guards.
 */
object GuardAgent {

    enum class MsgRole { USER, GUARD, TOOL, COUNCIL, NOTE, SYSTEM }
    enum class MsgStatus { RUNNING, DONE, FAILED, WAITING }

    data class Message(
        val id: Long,
        val role: MsgRole,
        val text: String,
        val tool: String? = null,
        val detail: String = "",
        val status: MsgStatus = MsgStatus.DONE,
        val streaming: Boolean = false,
        val callId: String? = null,
        val seats: List<String> = emptyList(),
        val at: Long = System.currentTimeMillis(),
    )

    data class Question(val text: String, val options: List<String>)

    data class Result(val success: Boolean, val summary: String, val error: String? = null)

    data class State(
        val running: Boolean = false,
        val task: String = "",
        val modelName: String = "",
        val status: String = "",
        val statusKind: AgentStatus.Kind? = null,
        val messages: List<Message> = emptyList(),
        val question: Question? = null,
        val seats: List<String> = emptyList(),
        val result: Result? = null,
        val startedAt: Long = 0,
        val endedAt: Long = 0,
    )

    /** The environment, behind a seam. */
    internal interface Guard {
        fun phoneReport(): String
        fun projectFiles(): List<Pair<String, String>>
        suspend fun checkUrl(url: String, deep: Boolean): String
        suspend fun advisories(pkg: String, eco: String, version: String?): String
        suspend fun kevRecent(days: Int): String
        suspend fun vaultAudit(): String
        suspend fun council(topic: String, artifact: String?): CouncilResult
        suspend fun browserTask(task: String): ToolResult
        fun fileOp(operation: String, path: String, content: String = "", old: String = "", new: String = ""): String
        suspend fun runScript(language: String, code: String, stdin: String?): String
        suspend fun intel(kind: String, value: String): String
        suspend fun webPosture(url: String): String
        suspend fun dependencyAudit(): String
        suspend fun abuseReport(kind: String, note: String, evidence: String): String
        suspend fun delegate(task: String, focus: String?): String
        suspend fun securityNews(topic: String?, limit: Int): String
        suspend fun webSearch(query: String): String
    }

    internal val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val msgSeq = AtomicLong()
    private var job: Job? = null
    private var questionGate: CompletableDeferred<String?>? = null

    internal var followUps: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    const val FALLBACK_BRIEF =
        "You are Oro's Shield: a personal cyber-defence agent. Audit, detect, verify, recover, and counter lawfully: " +
            "preserve evidence, trace public records, block, report and harden. Never attack, scan or access a system the user does not own."

    private const val MAX_MESSAGES = 320
    private const val MAX_TEXT = 24_000

    /** Like runCatching, but Stop is never swallowed: cancellation always propagates. */
    private suspend fun <T> attempt(block: suspend () -> T): Pair<T?, Throwable?> =
        try {
            block() to null
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null to t
        }

    fun queueFollowUp(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        addMessage(MsgRole.USER, t)
        followUps += t
    }

    /** Start a run the user watches on the shield screen. */
    suspend fun start(
        context: Context,
        task: String,
        aiRepository: AiRepository,
        model: AiModel? = null,
        projectRoot: File? = null,
    ): Job? = begin(context, task, aiRepository, model, projectRoot)

    /** Start a run a caller (the coding agent) waits on. */
    suspend fun runForResult(
        context: Context,
        task: String,
        aiRepository: AiRepository,
        model: AiModel? = null,
        projectRoot: File? = null,
    ): Result {
        if (_state.value.running) {
            return Result(false, "", "The guard is busy with another task. Wait for it, or ask the user.")
        }
        val started = begin(context, task, aiRepository, model, projectRoot)
            ?: return Result(false, "", _state.value.result?.error ?: "The guard could not start.")
        try {
            started.join()
        } catch (e: CancellationException) {
            stop()
            throw e
        }
        return _state.value.result ?: Result(false, "", "The guard ended without a verdict.")
    }

    private suspend fun begin(
        context: Context,
        task: String,
        aiRepository: AiRepository,
        model: AiModel?,
        projectRoot: File?,
    ): Job? {
        if (_state.value.running) return null
        val appContext = context.applicationContext
        followUps = ConcurrentLinkedQueue()
        questionGate = null
        val councilOn = GuardSettingsStore.councilOnPrompt(appContext)
        val priorMessages = _state.value.messages
        val prior = priorMessages
            .filter { it.role == MsgRole.USER || it.role == MsgRole.GUARD || it.role == MsgRole.COUNCIL }
            .takeLast(12)
            .mapNotNull { m ->
                val text = m.text.trim().take(4_000)
                if (text.isEmpty()) null
                else ChatRequestMessage(role = if (m.role == MsgRole.USER) "user" else "assistant", content = text)
            }
        val available = aiRepository.getAvailableModels()
        val chairId = GuardSettingsStore.chairId(appContext)
        val chosen = model
            ?: available.firstOrNull { it.id == chairId }
            ?: available.firstOrNull { it.id in GuardSettingsStore.DEFAULT_MEMBERS }
            ?: available.firstOrNull()
        if (chosen == null) {
            _state.update { it.copy(running = false, result = Result(false, "", "No models are configured yet.")) }
            return null
        }
        _state.update {
            it.copy(
                running = true,
                task = task.trim(),
                modelName = chosen.name,
                status = "Standing watch",
                statusKind = null,
                question = null,
                seats = emptyList(),
                result = null,
                startedAt = System.currentTimeMillis(),
                endedAt = 0,
            )
        }
        addMessage(MsgRole.USER, task.trim())
        val guard = RealGuard(appContext, aiRepository, projectRoot)
        val stream = AgentModelClient { req ->
            aiRepository.streamChat(
                chosen, req.messages, req.tools, temperature = 0.2f,
                reasoningLevel = ReasoningControls.maxLevel(chosen),
            )
        }
        val wantsPhone = Regex("(audit|scan|check|inspect) (this|my) (phone|device)", RegexOption.IGNORE_CASE).containsMatchIn(task)
        val wantsNews = Regex("security news|latest news|what'?s happening", RegexOption.IGNORE_CASE).containsMatchIn(task)
        job = scope.launch {
            try {
                var evidence = ""
                if (wantsPhone) {
                    GuardAgent.setStatus(AgentStatus(AgentStatus.Kind.RUNNING_TOOL, "Reading this phone's posture"))
                    GuardAgent.addTool("audit_phone", "prefetch-phone")
                    val (phone, phoneError) = attempt { guard.phoneReport() }
                    val report = phone ?: "The phone audit failed: ${phoneError?.message ?: phoneError?.javaClass?.simpleName ?: "unknown error"}"
                    GuardAgent.finishTool("prefetch-phone", "audit_phone", report, phone != null)
                    evidence += "\n\nThe audit_phone tool already ran before this turn. Its exact report:\n" +
                        report.take(10_000) +
                        "\n\nExplain this report to the user. Never say you cannot access the phone while a report is above."
                }
                if (wantsNews) {
                    GuardAgent.setStatus(AgentStatus(AgentStatus.Kind.RUNNING_TOOL, "Reading the latest security news"))
                    GuardAgent.addTool("security_news", "prefetch-news")
                    val (news, newsError) = attempt { guard.securityNews(null, 10) }
                    val report = news ?: "The security-news feeds could not be read: ${newsError?.message ?: newsError?.javaClass?.simpleName ?: "unknown error"}"
                    GuardAgent.finishTool("prefetch-news", "security_news", report, news != null)
                    evidence += "\n\nThe security_news tool already ran before this turn. Its report:\n" + report.take(10_000)
                }
                if (councilOn) {
                    GuardAgent.setStatus(AgentStatus(AgentStatus.Kind.THINKING, "Council in session"))
                    val councilArtifact = buildString {
                        val recent = prior.takeLast(6)
                        if (recent.isNotEmpty()) {
                            append("Conversation so far:\n")
                            recent.forEach { m ->
                                append(m.role).append(": ").append(m.content.orEmpty().take(2_000)).append('\n')
                            }
                            append('\n')
                        }
                        append(evidence)
                    }
                    val (council, councilError) = attempt { guard.council(task, councilArtifact.ifBlank { null }) }
                    if (council != null) {
                        GuardAgent.addCouncil(council)
                        evidence += "\n\n" + GuardAgent.councilEvidence(council)
                    }
                    if (councilError != null) {
                        GuardAgent.note("The council could not convene: ${councilError.message ?: councilError.javaClass.simpleName}")
                    }
                }
                GuardRun(guard, stream, appContext, chosen.supportsToolCalls, prior).run(task.trim(), evidence)
            } catch (e: CancellationException) {
                end(Result(false, "", "Stopped."))
                throw e
            } catch (e: Throwable) {
                end(Result(false, "", e.message ?: "The guard failed."))
            }
        }
        return job
    }

    fun stop() {
        questionGate?.complete(null)
        questionGate = null
        job?.cancel()
        job = null
    }

    fun clear() {
        stop()
        _state.value = State()
        msgSeq.set(0)
    }

    fun answer(text: String) {
        questionGate?.complete(text)
    }

    internal fun setGate(gate: CompletableDeferred<String?>) { questionGate = gate }

    internal fun nextId() = msgSeq.incrementAndGet()

    internal fun addMessage(
        role: MsgRole,
        text: String,
        tool: String? = null,
        detail: String = "",
        status: MsgStatus = MsgStatus.DONE,
        callId: String? = null,
    ): Long {
        if (text.isBlank() && tool == null) return -1
        val id = nextId()
        _state.update {
            it.copy(messages = (it.messages + Message(id, role, text.take(MAX_TEXT), tool, detail, status, false, callId)).takeLast(MAX_MESSAGES))
        }
        return id
    }

    internal fun startAssistant(): Long {
        val id = nextId()
        _state.update {
            it.copy(messages = (it.messages + Message(id, MsgRole.GUARD, "", streaming = true)).takeLast(MAX_MESSAGES))
        }
        return id
    }

    internal fun appendAssistant(id: Long, delta: String) {
        if (delta.isEmpty() || id < 0) return
        _state.update { st ->
            st.copy(messages = st.messages.map { m ->
                if (m.id == id) m.copy(text = (m.text + delta).take(MAX_TEXT)) else m
            })
        }
    }

    internal fun finishAssistant(id: Long, visible: String) {
        if (id < 0) {
            if (visible.isNotBlank()) addMessage(MsgRole.GUARD, visible)
            return
        }
        val text = visible.trim()
        _state.update { st ->
            val without = st.messages.filterNot { it.id == id }
            if (text.isEmpty()) st.copy(messages = without)
            else st.copy(messages = (without + Message(id, MsgRole.GUARD, text.take(MAX_TEXT))).takeLast(MAX_MESSAGES))
        }
    }

    internal fun addTool(tool: String, callId: String?): Long = addMessage(
        MsgRole.TOOL,
        GuardTools.humanize(tool),
        tool = tool,
        status = MsgStatus.RUNNING,
        callId = callId,
    )

    internal fun finishTool(callId: String?, tool: String, output: String, success: Boolean) {
        val id = _state.value.messages.lastOrNull { it.callId == callId && it.tool == tool && it.status == MsgStatus.RUNNING }?.id
            ?: return
        _state.update { st ->
            st.copy(messages = st.messages.map { m ->
                if (m.id == id) m.copy(detail = output.take(8_000), status = if (success) MsgStatus.DONE else MsgStatus.FAILED) else m
            })
        }
    }

    internal fun note(text: String) {
        addMessage(MsgRole.NOTE, text)
    }

    internal fun askInState(text: String, options: List<String>): Long {
        _state.update { it.copy(question = Question(text, options)) }
        return -1
    }

    internal fun resolveQuestion(id: Long, answer: String?) {
        val question = _state.value.question?.text
        _state.update { st -> st.copy(question = null) }
        if (!question.isNullOrBlank()) addMessage(MsgRole.GUARD, question)
        if (!answer.isNullOrBlank()) addMessage(MsgRole.USER, answer)
    }

    internal fun setStatus(status: AgentStatus) {
        _state.update { it.copy(status = status.label, statusKind = status.kind) }
    }

    internal fun setSeats(seats: List<String>) {
        _state.update { it.copy(seats = seats) }
    }

    /**
     * The council's record as a transcript object: every seat that spoke (or
     * why it did not), the chair's verdict, merged findings and the
     * disagreements. This is the "three or more models at once" answer.
     */
    internal fun addCouncil(result: CouncilResult) {
        val seats = result.opinions.map { o ->
            "${o.modelName} · ${o.role.id.replace('_', ' ')}" + if (o.failed != null) " · absent" else ""
        }
        val detail = buildString {
            result.opinions.forEach { o ->
                append("## ").append(o.modelName).append(" (").append(o.role.id.replace('_', ' ')).append(")\n")
                if (o.failed != null) append("Absent: ").append(o.failed).append('\n')
                else append(o.text.take(6_000)).append('\n')
                append('\n')
            }
            if (result.findings.isNotEmpty()) {
                append("Findings:\n")
                result.findings.take(16).forEach { f ->
                    append("- [").append(f.severity.name).append("] ").append(f.title).append(" -> ").append(f.action).append('\n')
                }
            }
            if (result.disagreements.isNotEmpty()) {
                append("\nDisagreements:\n")
                result.disagreements.take(8).forEach { append("- ").append(it).append('\n') }
            }
        }
        val text = result.verdict.ifBlank { "The council could not hear any member." }
        val id = nextId()
        _state.update {
            it.copy(
                messages = (it.messages + Message(
                    id = id,
                    role = MsgRole.COUNCIL,
                    text = text.take(MAX_TEXT),
                    detail = detail.take(MAX_TEXT),
                    seats = seats,
                )).takeLast(MAX_MESSAGES)
            )
        }
    }

    /** The record the guard model receives after the council has spoken. */
    internal fun councilEvidence(result: CouncilResult): String = buildString {
        append("The council has already deliberated on this request, with every seated model at its maximum reasoning level. Verdict:\n")
        append(result.verdict.take(8_000)).append('\n')
        if (result.findings.isNotEmpty()) {
            append("\nCouncil findings:\n")
            result.findings.take(16).forEach { append("- [").append(it.severity.name).append("] ").append(it.title).append(" -> ").append(it.action).append('\n') }
        }
        if (result.disagreements.isNotEmpty()) {
            append("\nDisagreements to keep on the record:\n")
            result.disagreements.take(8).forEach { append("- ").append(it).append('\n') }
        }
        append("\nUse this record. Do not re-run the deliberation unless new evidence changes it; keep any disagreement that still matters.")
    }

    internal fun end(result: Result) {
        val lastGuard = _state.value.messages.lastOrNull { it.role == MsgRole.GUARD }?.text.orEmpty()
        _state.update {
            it.copy(
                running = false, question = null, result = result,
                endedAt = System.currentTimeMillis(),
                messages = it.messages.map { m -> if (m.streaming) m.copy(streaming = false) else m },
            )
        }
        if (result.summary.isNotBlank() && !lastGuard.contains(result.summary.take(60))) {
            addMessage(MsgRole.GUARD, result.summary)
        }
    }

    /** Test seam: a scripted guard drives the real tool dispatch. */
    internal suspend fun runScenarioExecute(guard: Guard, name: String, args: String): ToolResult =
        GuardToolRuntime(
            guard = guard,
            appContext = null,
            ask = { _, _ -> "No user is available." },
            onFinish = { _, _ -> },
            onNote = {},
        ).execute(name, JSONObject(args))
}

/** One guard run: the loop, the tool dispatch, the question gate. */
internal class GuardRun(
    private val guard: GuardAgent.Guard,
    private val modelStream: AgentModelClient,
    private val appContext: Context?,
    private val nativeTools: Boolean = true,
    private val prior: List<ChatRequestMessage> = emptyList(),
) {

    private var finishSummary: String? = null
    private var finishSuccess = false
    private var finishCalled = false

    suspend fun run(task: String, evidence: String = "") {
        val brief = appContext?.getString(R.string.guard_system_brief) ?: GuardAgent.FALLBACK_BRIEF
        val tools = GuardTools.all + BrowserDelegationTools.USE_BROWSER
        val taskMessage = buildString {
            append("Task: ").append(task)
            if (evidence.isNotBlank()) append("\n\n").append(evidence.take(18_000))
            append("\n\nStart with the smallest evidence-gathering step. Audit first, act second, and convene the council for anything worth a second opinion. In an active incident, contain before you investigate.")
        }
        val conversation = (prior + ChatRequestMessage(role = "user", content = taskMessage)).toMutableList()
        val runtime = GuardToolRuntime(
            guard = guard,
            appContext = appContext,
            ask = { question, options -> askQuestion(question, options) },
            onFinish = { summary, success ->
                finishSummary = summary
                finishSuccess = success
                finishCalled = true
            },
            onNote = { GuardAgent.note(it) },
        )
        val runner = AgentToolRunner { call ->
            val name = call.function.name.substringAfterLast('.').lowercase()
            val args = runCatching { JSONObject(call.function.arguments.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            runtime.execute(name, args)
        }

        var streamId = -1L
        var suppressLive = false
        val listener = object : AgentLoopListener {
            override fun onStatus(status: AgentStatus) {
                GuardAgent.setStatus(status)
            }

            override fun onText(delta: String) {
                if (suppressLive) return
                if (delta.contains("<tool_call") || delta.contains("<function=")) {
                    suppressLive = true
                    return
                }
                if (streamId < 0) streamId = GuardAgent.startAssistant()
                GuardAgent.appendAssistant(streamId, delta)
            }

            override fun onTurnComplete(visibleText: String, toolCalls: List<ToolCallResponse>) {
                val thought = AgentLoop.stripThinking(visibleText).trim()
                GuardAgent.finishAssistant(streamId, thought)
                streamId = -1
                suppressLive = false
            }

            override fun onToolStarted(call: ToolCallResponse) {
                if (call.function.name == "finish_task") return
                GuardAgent.addTool(call.function.name, call.id)
            }

            override fun onToolFinished(call: ToolCallResponse, result: ToolResult) {
                if (call.function.name == "finish_task") return
                val text = if (result.success) result.output else (result.error ?: result.output)
                GuardAgent.finishTool(call.id, call.function.name, text, result.success)
            }
        }

        val prepare: suspend (List<ChatRequestMessage>, Int) -> AgentRequest = { history, _ ->
            AgentRequest(
                listOf(ChatRequestMessage(role = "system", content = brief)) + history,
                if (nativeTools) tools else null
            )
        }

        val loop = AgentLoop(
            client = modelStream,
            tools = runner,
            config = AgentLoopConfig(
                roundNote = { _, _ ->
                    val q = GuardAgent.followUps
                    if (q.isEmpty()) null
                    else "The user sent this while you worked: " + generateSequence { q.poll() }.joinToString(" | ")
                },
                maxIterations = 80,
                maxToolResultChars = 24_000,
                idleTimeoutMs = 120_000,
                maxQualityPasses = 2,
                toolSchemas = AgentTools.schemasOf(tools),
                textResultsFooter = "Continue with the next step. When the question is answered, call finish_task; a plain reply does not end it.",
                cutRepetition = true,
            )
        )

        var asked = 0
        val outcome = loop.run(conversation, GuardTools.names + "use_browser", listener, finalCheck = {
            val said = AgentLoop.stripThinking(conversation.lastOrNull { it.role == "assistant" }?.content.orEmpty())
            when (val kind = BrowserProse.read(said)) {
                is BrowserProse.Finish -> { finishSummary = kind.summary; finishSuccess = kind.success; finishCalled = true; null }
                is BrowserProse.Ask -> {
                    if (asked >= 2) null
                    else {
                        asked++
                        askQuestion(kind.question, emptyList())
                        null
                    }
                }
                else -> null
            }
        }, prepare = prepare)

        when (outcome) {
            is AgentRunResult.Completed ->
                GuardAgent.end(GuardAgent.Result(finishSuccess || finishCalled, finishSummary ?: lastText(conversation), null))
            is AgentRunResult.IterationLimit ->
                GuardAgent.end(GuardAgent.Result(finishSuccess, finishSummary ?: "Out of turns; partial: ${lastText(conversation).take(300)}", null))
            is AgentRunResult.Failed ->
                GuardAgent.end(GuardAgent.Result(false, "", outcome.message))
        }
    }

    private fun lastText(conversation: List<ChatRequestMessage>): String =
        AgentLoop.stripThinking(conversation.lastOrNull { it.role == "assistant" }?.content.orEmpty()).trim()

    /** Blocks the run until the user answers in the chat. */
    private suspend fun askQuestion(text: String, options: List<String>): String {
        val gate = CompletableDeferred<String?>()
        GuardAgent.setGate(gate)
        val id = GuardAgent.askInState(text, options)
        val answer = try {
            gate.await()
        } finally {
            GuardAgent.setGate(CompletableDeferred())
        }
        GuardAgent.resolveQuestion(id, answer?.takeIf { it.isNotBlank() })
        return answer?.takeIf { it.isNotBlank() } ?: "The user did not answer; make the safe assumption and say so."
    }
}
