package com.oro.app.browser

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.oro.app.MainActivity
import com.oro.app.OroApplication
import com.oro.app.agent.AgentNotifications
import com.oro.app.agent.BackgroundRuns
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.VisionSupport
import com.oro.app.data.ai.agent.AgentLoop
import com.oro.app.data.ai.agent.AgentLoopConfig
import com.oro.app.data.ai.agent.AgentLoopListener
import com.oro.app.data.ai.agent.AgentModelClient
import com.oro.app.data.ai.agent.AgentRequest
import com.oro.app.data.ai.agent.AgentRunResult
import com.oro.app.data.ai.agent.AgentStatus
import com.oro.app.data.ai.agent.AgentToolRunner
import com.oro.app.data.ai.tools.JsonParserUtils
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.repository.AiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Orobi driving the in-app browser: an observe → think → act loop over
 * [BrowserEngine], built on the same [AgentLoop] as the coding agent
 * (retries, text-tool fallback for models without function calling,
 * cancellation, repetition guard).
 *
 * ### What makes it work with weak models
 *  - The page is a short numbered list of its controls plus a readable
 *    digest, not a DOM dump or an image, so any text model can read it and
 *    act by number -- or by the words on a button (click_text). Vision models
 *    can also look at a screenshot.
 *  - Every action returns its outcome *and the new page*, plus an explicit
 *    "the page did not change" when it didn't, escalating after repeats --
 *    the single most useful signal for noticing a click that did nothing.
 *  - Fast paths beat navigation: web_search before wandering, open_url over
 *    menus, read_page over click-click-read, fill_form over N type_text.
 *  - Only the latest page stays in context; older observations collapse to
 *    their one-line outcome. Notes (take_note) persist facts across pages.
 *
 * ### What keeps the user in control
 *  - The browser screen shows the run live (the very WebView being driven);
 *    anywhere else in Oro a pill shows the step and opens the screen.
 *  - Pause and Stop are on screen for the whole run.
 *  - Sensitive actions (pay, send, post, delete, publish, plaintext password
 *    entry) wait for an explicit Allow, by default.
 *  - Human checks (captchas) are the user's to pass -- never the agent's:
 *    the run waits, the banner says so, and it resumes when the check clears.
 *  - Secrets never travel through the model: saved credentials are filled by
 *    the engine (use_credential), one-time secrets are typed by the user
 *    (ask_user_secret), HTTP-auth prompts are answered in the UI.
 *  - The run lives in this process-wide object, not in a screen: minimise,
 *    switch chats, edit code, even leave the app -- the work continues (a
 *    foreground [BackgroundRuns] run keeps the process warm).
 *
 * ### Two ways in
 *  - [start]: a task the user typed on the browser screen.
 *  - [runForResult]: a task the coding agent (or a subagent) hands over
 *    mid-run (`use_browser`); it suspends until the work is done and returns
 *    what was found.
 */
object BrowserAgent {

    enum class StepKind { THOUGHT, ACTION, QUESTION, APPROVAL, SECRET, NOTE, SYSTEM }
    enum class StepStatus { RUNNING, DONE, FAILED, BLOCKED, WAITING }

    /** Who started a run: the user on the browser screen, or an agent in a chat. */
    enum class Origin { USER, CHAT }

    /**
     * Why a run ended, so screens never parse [State.error] to find out.
     * DONE: finish_task(success=true). UNCONFIRMED: the model stopped without
     * finish_task. FAILED: finish_task(success=false). STOPPED_BY_USER: Stop.
     * ACTION_LIMIT: the user's action limit. ENGINE_LOST: the WebView engine
     * broke beyond recovery. ERROR: the model or the run broke.
     */
    enum class EndReason { DONE, UNCONFIRMED, FAILED, STOPPED_BY_USER, ACTION_LIMIT, ENGINE_LOST, ERROR }

    data class Step(
        val id: Long,
        val kind: StepKind,
        val tool: String?,
        val title: String,
        val detail: String = "",
        val status: StepStatus = StepStatus.RUNNING,
        val url: String? = null,
        val pageChanged: Boolean? = null,
        val startedAt: Long = System.currentTimeMillis(),
        val endedAt: Long? = null,
    )

    data class Question(val text: String, val options: List<String>)

    /** A secret the user is being asked to provide; the value never lands here. */
    data class SecretRequest(val purpose: String, val fieldIndex: Int?)

    /** How a secret request was answered. */
    sealed interface SecretOutcome {
        /** The user typed it into the card; [value] goes straight to the engine. */
        data class Entered(val value: String) : SecretOutcome
        /** The user typed it straight into the live page. */
        data object Direct : SecretOutcome
        data object Declined : SecretOutcome
    }

    data class State(
        val running: Boolean = false,
        val paused: Boolean = false,
        val task: String = "",
        val modelName: String = "",
        val status: String = "",
        val statusKind: AgentStatus.Kind? = null,
        val steps: List<Step> = emptyList(),
        val currentUrl: String = "",
        val currentTitle: String = "",
        val actions: Int = 0,
        /** The action limit for this run; 0 = no limit. */
        val maxActions: Int = 0,
        val notes: List<String> = emptyList(),
        val question: Question? = null,
        val approval: String? = null,
        val secretRequest: SecretRequest? = null,
        val summary: String? = null,
        val success: Boolean? = null,
        val error: String? = null,
        val startedAt: Long = 0,
        val endedAt: Long = 0,
        val origin: Origin = Origin.USER,
        /** Set when the run ends; null while it runs or before the first one. */
        val endReason: EndReason? = null,
    ) {
        val finished: Boolean get() = !running && startedAt > 0
        /** The run is waiting on the user in some way: screens show it loudly. */
        val needsUser: Boolean get() = question != null || approval != null || secretRequest != null
    }

    data class Settings(
        val confirmSensitive: Boolean = true,
        val returnToOro: Boolean = true,
        /** Stop after this many actions; 0 = no limit (the default -- Stop is always on screen). */
        val maxActions: Int = 0,
        /** Let vision models take screenshots. */
        val screenshots: Boolean = true,
        /** Desktop user agent and layout. */
        val desktopMode: Boolean = false,
    )

    /** What a [runForResult] run produced, for the model that asked for it. */
    data class Result(
        val success: Boolean,
        val summary: String,
        val notes: List<String>,
        val actions: Int,
        val seconds: Long,
        val finalUrl: String,
        /** Set when the run never started or ended in an error. */
        val error: String? = null,
        /** Why the run ended; null when it never started. */
        val endReason: EndReason? = null,
    ) {
        /** The user pressed Stop: the coding agent must not take the browser again on its own. */
        val stoppedByUser: Boolean get() = endReason == EndReason.STOPPED_BY_USER

        /** The tool result text the coding agent reads. */
        fun forModel(): String = buildString {
            append(
                when (endReason) {
                    EndReason.DONE -> "Browser task done"
                    EndReason.UNCONFIRMED -> "Browser task ended without confirming it was done"
                    EndReason.STOPPED_BY_USER -> "The user stopped the browser task"
                    EndReason.ACTION_LIMIT -> "Browser task hit the user's action limit"
                    EndReason.ENGINE_LOST -> "Browser task stopped: the browser engine broke"
                    EndReason.FAILED, EndReason.ERROR -> "Browser task not completed"
                    null -> if (success) "Browser task done" else "Browser task not completed"
                }
            )
            append(" (").append(actions).append(if (actions == 1) " action, " else " actions, ").append(seconds).append(" s")
            if (finalUrl.isNotBlank()) append(", ended on ").append(finalUrl)
            append(").\n")
            if (stoppedByUser) append("Do not call use_browser again unless the user asks you to.\n")
            if (summary.isNotBlank()) append("Result: ").append(summary.trim()).append('\n')
            if (error != null && error != summary && !stoppedByUser) append("Problem: ").append(error).append('\n')
            if (notes.isNotEmpty()) {
                append("Notes:\n")
                notes.forEach { append("- ").append(it).append('\n') }
            }
        }.trimEnd()
    }

    /** Who cancelled a run, recorded before cancelling so [finish] can say why it ended. */
    internal enum class CancelReason { USER, ENGINE_LOST }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var job: Job? = null
    /** The pause after a run (notification, come back to Oro); a new run cancels it. */
    @Volatile private var wrapUpJob: Job? = null
    private val paused = MutableStateFlow(false)
    @Volatile private var pendingAnswer: CompletableDeferred<String>? = null
    @Volatile private var pendingApproval: CompletableDeferred<Boolean>? = null
    @Volatile private var pendingSecret: CompletableDeferred<SecretOutcome>? = null
    private val stepSeq = AtomicLong(0)
    @Volatile private var appContext: Context? = null
    @Volatile private var cancelReason: CancelReason? = null

    /** The project folder uploads come from, when a run was started from a project. */
    @Volatile private var projectRootPath: String? = null

    /** When the user last answered or approved, for notifications and wrap-up. */
    @Volatile private var lastAnswerAt = 0L

    // ------------------------------------------------------------ controls

    fun start(
        context: Context, task: String, model: AiModel, aiRepository: AiRepository, settings: Settings,
        startUrl: String? = null, projectRoot: String? = null,
    ) {
        begin(context, task, model, aiRepository, settings, Origin.USER, startUrl, projectRoot)
    }

    /**
     * Runs a task handed over by an agent and suspends until it ends.
     * Cancelling the caller (the user stopped the chat run) stops the browser
     * task too. Never throws for an ordinary failure: the [Result] says what
     * happened, so the caller can adjust.
     */
    suspend fun runForResult(
        context: Context,
        task: String,
        model: AiModel,
        aiRepository: AiRepository,
        settings: Settings,
        startUrl: String? = null,
        projectRoot: String? = null,
    ): Result {
        if (_state.value.running) {
            return Result(false, "", emptyList(), 0, 0, "", error = "The browser is busy with another task. Wait for it to finish, or ask the user.")
        }
        val started = begin(context, task, model, aiRepository, settings, Origin.CHAT, startUrl, projectRoot)
            ?: return Result(false, "", emptyList(), 0, 0, "", error = _state.value.error ?: "The browser agent could not start.")
        try {
            started.join()
        } catch (e: CancellationException) {
            // The user stopped the chat run: the browser task goes with it.
            cancelReason = CancelReason.USER
            started.cancel()
            throw e
        }
        val s = _state.value
        return Result(
            success = s.endReason == EndReason.DONE,
            summary = s.summary.orEmpty(),
            notes = s.notes,
            actions = s.actions,
            seconds = ((s.endedAt - s.startedAt) / 1000).coerceAtLeast(0),
            finalUrl = s.currentUrl,
            error = s.error,
            endReason = s.endReason,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Synchronized
    private fun begin(
        context: Context,
        task: String,
        model: AiModel,
        aiRepository: AiRepository,
        settings: Settings,
        origin: Origin,
        startUrl: String?,
        projectRoot: String?,
    ): Job? {
        if (_state.value.running) return null
        appContext = context.applicationContext
        BrowserEngine.init(context.applicationContext)
        if (!BrowserEngine.isReady()) {
            _state.update { it.copy(error = "The browser engine could not start.") }
            return null
        }
        // The previous run's wrap-up must not stomp this run's state.
        wrapUpJob?.cancel()
        wrapUpJob = null
        paused.value = false
        cancelReason = null
        lastAnswerAt = 0L
        stepSeq.set(0)
        projectRootPath = projectRoot
        BrowserEngine.setDesktopMode(settings.desktopMode)
        _state.value = State(
            running = true, task = task.trim(), modelName = model.name,
            status = "Starting", maxActions = settings.maxActions, startedAt = System.currentTimeMillis(),
            origin = origin, currentUrl = startUrl.orEmpty(),
        )
        val run = BrowserRun(
            browser = BrowserEngine, task = task.trim(), model = model,
            // Many short decisions: the model's quick level, not the chat's deep one.
            modelStream = AgentModelClient { req ->
                aiRepository.streamChat(
                    model, req.messages, req.tools, temperature = 0.2f,
                    reasoningLevel = com.oro.app.data.ai.ReasoningControls.quickLevel(model),
                )
            },
            settings = settings, origin = origin, startUrl = startUrl,
            vision = settings.screenshots && VisionSupport.supports(model),
        )
        // ATOMIC: the body runs even if Stop lands before it is first
        // dispatched, so the wrap-up in finally always happens -- otherwise
        // the state would stay "running" and the foreground service up.
        val launched = scope.launch(start = CoroutineStart.ATOMIC) {
            keepAlive(context.applicationContext, coroutineContext[Job]!!)
            var result: AgentRunResult = AgentRunResult.Failed("The browser agent stopped unexpectedly.", retryable = true)
            try {
                result = run.run()
            } catch (e: CancellationException) {
                result = AgentRunResult.Failed("Stopped.", retryable = false)
            } catch (e: Exception) {
                result = AgentRunResult.Failed(e.message ?: "The browser agent crashed.", retryable = true)
            } finally {
                finish(context.applicationContext, result, settings, origin, run)
            }
        }
        job = launched
        return launched
    }

    /** Foreground keep-alive so a backgrounded app does not lose the run. */
    private fun keepAlive(context: Context, job: Job) {
        runCatching {
            BackgroundRuns.begin(context, BackgroundRuns.Run(BROWSER_RUN_ID, "", "a browser task", job))
        }
    }

    private fun endKeepAlive(context: Context) {
        runCatching { BackgroundRuns.end(context, BROWSER_RUN_ID) }
    }

    fun stop() {
        if (_state.value.running) cancelReason = CancelReason.USER
        pendingAnswer?.cancel(); pendingApproval?.complete(false); pendingSecret?.complete(SecretOutcome.Declined)
        job?.cancel()
    }

    fun togglePause() {
        if (!_state.value.running) return
        paused.value = !paused.value
        _state.update { it.copy(paused = paused.value, status = if (paused.value) "Paused" else "Continuing") }
    }

    fun answer(text: String) {
        lastAnswerAt = SystemClock.uptimeMillis()
        pendingAnswer?.complete(text)
    }

    fun approve(allow: Boolean) {
        lastAnswerAt = SystemClock.uptimeMillis()
        pendingApproval?.complete(allow)
    }

    /** The user typed a secret into the card; it goes to the engine, never to state. */
    fun provideSecret(secret: String) {
        lastAnswerAt = SystemClock.uptimeMillis()
        pendingSecret?.complete(SecretOutcome.Entered(secret))
    }

    /** The user typed the secret straight into the live page. */
    fun secretEnteredDirectly() {
        lastAnswerAt = SystemClock.uptimeMillis()
        pendingSecret?.complete(SecretOutcome.Direct)
    }

    fun declineSecret() {
        lastAnswerAt = SystemClock.uptimeMillis()
        pendingSecret?.complete(SecretOutcome.Declined)
    }

    fun clear() {
        if (_state.value.running) return
        _state.update { State() }
    }

    // ------------------------------------------------------------- one run

    /**
     * Everything that belongs to one task: its browser, pages, notes and
     * counters. It talks to the web only through [Browser] and to the model
     * only through [modelStream], so the whole run executes in JVM tests too.
     */
    private class BrowserRun(
        val browser: Browser,
        val task: String,
        val model: AiModel,
        val modelStream: AgentModelClient,
        val settings: Settings,
        val origin: Origin,
        val startUrl: String?,
        /** Whether this model sees screenshots. */
        val vision: Boolean,
    ) {
        val tools = BrowserTools.forRun(vision)
        val limited = settings.maxActions > 0
        var forceText = false
        var lastPage: PageState? = null
        /** Uptime when [lastPage] was captured: page changes after it mean it may be stale. */
        var lastCaptureUptime = 0L
        /** Set after any wait for the user: the next action starts from a new snapshot. */
        var mustRecapture = false
        /** The page the model saw when it decided this turn's calls; indexes refer to it. */
        var turnPage: PageState? = null
        /** Uptime when the last call passed [gate]; an answer after it is "since the last action". */
        var lastGateAt = 0L
        val notes = mutableListOf<String>()
        /** The task picks among items, so results beyond the fold matter. */
        val comparing = BrowserPrompt.comparesItems(task)
        /** The run has read or scrolled a page, or a pick from the first screenful was held once. */
        var browsed = false
        /** A pick from the first screenful was held once: the next one gets the list read for it. */
        var pickHeld = false
        /** Questions put to the user and their answers, in order. */
        val questions = mutableListOf<Pair<String, String>>()
        var actions = 0
        var unchangedStreak = 0
        val visits = HashMap<Int, Int>()
        @Volatile var finishSummary: String? = null
        @Volatile var finishCalled = false
        @Volatile var finishSuccess: Boolean? = null
        @Volatile var limitHit = false
        /** Step id of the tool call in progress, so late updates land on the right card. */
        @Volatile var currentStepId: Long = -1
        /**
         * Why an action in this turn did not work, if one did not: the turn's
         * remaining actions (and a finish_task) were planned for a page that
         * never came, so they are not run.
         */
        var turnBroken: String? = null
        /** Said before the next result: the user passed a human check first. */
        var handOffNote: String? = null

        suspend fun observe(): PageState {
            val startedAt = browser.uptime()
            val s = browser.snapshot()
            lastPage = s
            lastCaptureUptime = startedAt
            _state.update { it.copy(currentUrl = s.url, currentTitle = s.title) }
            return s
        }

        /**
         * The page as it is right before an action: the last capture if
         * nothing has changed since -- value-only redraws (a caret, a clock)
         * do not count -- else a new one. Acting on a snapshot taken before a
         * pause, an approval or a long think is how clicks used to land on the
         * wrong thing.
         */
        suspend fun freshPage(): PageState {
            val s = lastPage
            val reuse = s != null && !mustRecapture && browser.isLatest(s) &&
                browser.lastChangeAt <= lastCaptureUptime &&
                browser.uptime() - lastCaptureUptime <= MAX_SNAPSHOT_AGE_MS
            if (reuse && s != null) return s
            mustRecapture = false
            return observe()
        }

        /** Holds actions while the user has the run paused. */
        suspend fun gate() {
            if (paused.value) {
                _state.update { it.copy(status = "Paused") }
                paused.first { !it }
                mustRecapture = true
                _state.update { it.copy(status = "Continuing") }
            }
            lastGateAt = browser.uptime()
        }

        fun observation(before: Int?, s: PageState, maxChars: Int = 6_000): String = buildString {
            append(PAGE_MARKER).append('\n')
            if (before != null) {
                if (before == s.signature) {
                    unchangedStreak++
                    append("⚠ The page did not change after this action.")
                    if (unchangedStreak >= 2) {
                        append(" That is ").append(unchangedStreak).append(" in a row: stop repeating it. Try click_text, ")
                        append("a selector, scroll_to, hover first, wait_for, or back.")
                    }
                    append('\n')
                } else {
                    unchangedStreak = 0
                    append("Page changed.\n")
                }
                val seen = (visits[s.signature] ?: 0) + 1
                visits[s.signature] = seen
                if (seen == 4 || seen == 8) {
                    append("⚠ You have been on this exact page ").append(seen).append(" times; you may be going in circles. Take a different route.\n")
                }
            }
            // A choice among results ("the cheapest") made from the first
            // screenful misses better ones below the fold (the device agent's
            // live-eval lesson, kept).
            if (comparing && !browsed && s.canScrollDown) {
                append("Hint: your task compares items and this page has more below: read_page (or scroll) before choosing.\n")
            }
            if (limited) append("Actions left: ").append((settings.maxActions - actions).coerceAtLeast(0)).append('\n')
            if (notes.isNotEmpty()) append("Your notes: ").append(notes.joinToString(" | ")).append('\n')
            append(s.render(maxChars))
        }

        suspend fun run(): AgentRunResult {
            val nativeTools = model.supportsToolCalls

            // Land on the task's page first when one was named.
            var first = observe()
            if (!startUrl.isNullOrBlank()) {
                val opened = browser.navigate(startUrl)
                addStep(Step(nextId(), StepKind.ACTION, "open_url", "Open ${startUrl.take(60)}", opened, StepStatus.DONE, endedAt = System.currentTimeMillis()))
                browser.settle(quietMs = 250, maxMs = 2_000, minMs = 100)
                first = observe()
            }
            turnPage = first
            val conversation = mutableListOf(
                ChatRequestMessage(
                    role = "user",
                    content = "Task: $task\n\n${observation(null, first)}\n\nDecide the first step."
                )
            )

            val runner = AgentToolRunner { call ->
                val name = call.function.name.substringAfterLast('.').lowercase()
                val done = execute(call)
                if (name !in BrowserTools.passive && turnBroken == null) {
                    val text = if (done.success) done.output else (done.error ?: done.output)
                    if (!done.success || text.startsWith("Blocked")) turnBroken = text.substringBefore('\n').take(120)
                }
                val r = handOffNote?.let { note -> handOffNote = null; done.copy(output = note + "\n" + done.output) } ?: done
                if (name !in BrowserTools.passive && r.success) actions++
                _state.update { it.copy(actions = actions, notes = notes.toList()) }
                r
            }

            val client = AgentModelClient { request ->
                val stream = modelStream.stream(request)
                if (request.tools == null) stream else detectToolRejection(stream) { forceText = true }
            }

            val listener = object : AgentLoopListener {
                override fun onStatus(status: AgentStatus) {
                    val label = when (status.kind) {
                        AgentStatus.Kind.THINKING -> "Looking at the page"
                        AgentStatus.Kind.WRITING -> "Deciding"
                        else -> status.label
                    }
                    _state.update { it.copy(status = label, statusKind = status.kind) }
                }

                override fun onTurnComplete(visibleText: String, toolCalls: List<ToolCallResponse>) {
                    turnPage = lastPage
                    turnBroken = null
                    val thought = AgentLoop.stripThinking(visibleText).trim()
                    if (thought.isNotEmpty()) addStep(Step(nextId(), StepKind.THOUGHT, null, thought.take(400), status = StepStatus.DONE, endedAt = System.currentTimeMillis()))
                }

                override fun onToolStarted(call: ToolCallResponse) {
                    val name = call.function.name
                    if (name == "finish_task") return
                    val (title, kind) = describe(call, lastPage)
                    val id = nextId()
                    currentStepId = id
                    addStep(Step(id, kind, name, title, url = lastPage?.url, status = if (kind == StepKind.QUESTION || kind == StepKind.SECRET) StepStatus.WAITING else StepStatus.RUNNING))
                }

                override fun onToolFinished(call: ToolCallResponse, result: ToolResult) {
                    if (call.function.name == "finish_task") return
                    val text = if (result.success) result.output else (result.error ?: result.output)
                    val outcome = text.substringBefore(PAGE_MARKER).trim().lineSequence().firstOrNull().orEmpty()
                    val unchanged = result.output.contains("did not change")
                    val blocked = text.startsWith("Blocked") || text.contains("user denied", true)
                    updateStep(currentStepId) { st ->
                        st.copy(
                            detail = if ((st.kind == StepKind.QUESTION || st.kind == StepKind.SECRET) && st.status == StepStatus.DONE) st.detail else outcome.take(240),
                            status = when {
                                blocked -> StepStatus.BLOCKED
                                !result.success -> StepStatus.FAILED
                                else -> StepStatus.DONE
                            },
                            pageChanged = if (call.function.name in BrowserTools.passive) null else !unchanged,
                            endedAt = System.currentTimeMillis()
                        )
                    }
                }
            }

            val prepare: suspend (List<ChatRequestMessage>, Int) -> AgentRequest = { history, pressure ->
                val textMode = !nativeTools || forceText
                val system = ChatRequestMessage(
                    role = "system",
                    content = BrowserPrompt.system(
                        textTools = textMode,
                        settings = settings,
                        tools = tools,
                        vision = vision,
                        fromChat = origin == Origin.CHAT,
                    )
                )
                AgentRequest(listOf(system) + compact(history, pressure), if (textMode) null else tools)
            }

            val loop = AgentLoop(
                client = client,
                tools = runner,
                config = AgentLoopConfig(
                    maxIterations = if (limited) settings.maxActions + 10 else AgentLoopConfig.UNLIMITED,
                    attachImages = vision,
                    maxToolResultChars = 14_000,
                    idleTimeoutMs = 90_000,
                    // A reminder, plus questions asked in prose turned into real ones.
                    maxQualityPasses = 4,
                    // The browser tools' own schemas win over the coding tools' (ask_user differs).
                    toolSchemas = com.oro.app.data.ai.tools.AgentTools.schemasOf(BrowserTools.all),
                    textResultsFooter = "Continue with the next step. When the task is done (or impossible), call finish_task; " +
                        "a plain reply does not end it.",
                    cutRepetition = true,
                )
            )
            // A reply with no tool call. finish_task written as text is a
            // finish; a question for the user written as text becomes a real
            // question card; anything else gets a reminder to call
            // finish_task. A "done" claim alone is never taken as done: it
            // cannot be checked against the page.
            var reminded = 0
            var asked = 0
            return loop.run(conversation, BrowserTools.names, listener, finalCheck = {
                val said = AgentLoop.stripThinking(conversation.lastOrNull { it.role == "assistant" }?.content.orEmpty())
                when (val kind = BrowserProse.read(said)) {
                    is BrowserProse.Finish -> {
                        finishSummary = kind.summary.ifBlank { null }
                        finishCalled = true
                        finishSuccess = kind.success
                        null
                    }
                    is BrowserProse.Ask -> if (asked >= 2) null else {
                        asked++
                        val repeated = repeatedQuestion(kind.question)
                        if (repeated != null) repeated.error + "\nContinue from the current page, or call finish_task." else {
                            val id = nextId()
                            addStep(Step(id, StepKind.QUESTION, "ask_user", kind.question.take(400), url = lastPage?.url, status = StepStatus.WAITING))
                            val answer = askUser(kind.question, emptyList())
                            questions += kind.question to answer
                            mustRecapture = true
                            updateStep(id) { it.copy(detail = "You answered: $answer", status = StepStatus.DONE, endedAt = System.currentTimeMillis()) }
                            "The user answered: $answer\nContinue the task from the current page. When it is done (or impossible), call finish_task."
                        }
                    }
                    BrowserProse.Statement -> when (reminded++) {
                        0 -> "If the task is complete, call finish_task with a summary (and the answer, if the task asked for information). " +
                            "If it is not complete, continue with the next action."
                        1 -> "Call the finish_task tool now to end the task: finish_task(summary, success=true), or success=false " +
                            "if it could not be done. A text reply does not end it."
                        else -> null
                    }
                }
            }, prepare = prepare)
        }

        // ------------------------------------------------------- execute

        /**
         * Runs one call: hold while paused, resolve the target once against
         * the page as it is now, guard exactly that target, and act on
         * exactly that target.
         */
        private suspend fun execute(call: ToolCallResponse): ToolResult {
            val b = browser
            val name = call.function.name.substringAfterLast('.').lowercase()
            val args = JsonParserUtils.parseToJsonObject(call.function.arguments) ?: JsonObject(emptyMap())

            if (limited && name !in BrowserTools.passive && actions >= settings.maxActions) {
                limitHit = true
                return ToolResult(false, "", "The action limit (${settings.maxActions}) is used up. Call finish_task with what you achieved and what is left.")
            }

            if (name !in BrowserTools.allowedWhilePaused) gate()

            // An earlier action in this turn did not work: the rest of the
            // turn was planned for a page that never came.
            turnBroken?.let { why ->
                if (name !in BrowserTools.passive || name == "finish_task") {
                    return skipped("an earlier action in this turn did not work ($why), so this was not tried. Decide again from this page.", freshPage())
                }
            }

            readOnly(name, args)?.let { return it }
            if (name == "scroll" || name == "scroll_to" || name == "read_page") browsed = true

            var page = freshPage()
            val seen = turnPage

            // A human check is the user's to pass, never the agent's: wait
            // for them, then act on the page that follows. Interstitials
            // ("checking your browser") get a wait first: they often clear.
            if (name in HANDS_ON) {
                if (CaptchaWatch.isInterstitial(page)) {
                    _state.update { it.copy(status = "Waiting for the site's browser check") }
                    var waited = 0
                    while (waited < 15_000 && CaptchaWatch.isInterstitial(page)) {
                        delay(1_000); waited += 1_000
                        page = observe()
                    }
                }
                if (page.needsHuman) {
                    handOff(null)
                    gate()
                    page = freshPage()
                    if (page.needsHuman) return skipped("the human check is still on the page.", page)
                }
            }

            if (name == "fill_form") return fillForm(args, page)

            // A javascript: URL is a navigation dressed as code; evaluate runs
            // the code itself, without the scheme.
            if (name == "evaluate" && BrowserArgs.str(args, "js")?.trim()?.startsWith("javascript:", true) == true) {
                return ToolResult(false, "", "Refused: drop the javascript: prefix; the code runs in the page as-is.")
            }

            val target = resolve(name, args, seen, page)
            problem(target, page)?.let { return it }
            val element = (target as? Target.Resolved)?.element

            // A plaintext password never goes through the model's hands: the
            // vault or the user types it. Refused before any guard or action.
            if (name == "type_text" && element?.password == true) {
                return ToolResult(
                    false, observation(null, page),
                    "Refused to type a plaintext password. Use use_credential (a saved login) or ask_user_secret (the user types it)."
                )
            }

            // Safety guard on exactly what is about to be acted on.
            val sensitive = guard(name, args, element, page)
            if (sensitive != null && settings.confirmSensitive) {
                if (!requestApproval(sensitive)) {
                    return ToolResult(true, "Blocked: the user denied \"$sensitive\". Do not retry it; choose another path or finish_task explaining what is left.")
                }
                // The user took a moment: act only if the page still shows
                // what they approved.
                gate()
                mustRecapture = true
                val now = freshPage()
                if (element != null) {
                    val again = resolve(name, args, seen, now)
                    val againEl = (again as? Target.Resolved)?.element
                    if (againEl == null || !sameTarget(element, againEl)) {
                        return skipped("the page changed while you were deciding, so nothing was done. Decide again from this page.", now)
                    }
                } else if (now.url != page.url) {
                    return skipped("the page navigated while you were deciding (now ${now.url}), so nothing was done.", now)
                }
                page = now
            }

            val before = page.signature

            suspend fun act(maxSettle: Long = 2_000, expectChange: Boolean = true, block: suspend () -> String): ToolResult {
                val outcome = block()
                val didFail = failed(outcome)
                b.settle(maxMs = maxSettle)
                var after = observe()
                // Still loading right after the action: give it a moment here
                // rather than spend a model turn on "wait".
                var extra = 0
                while (after.loading && extra < 3) {
                    delay(400)
                    after = observe()
                    extra++
                }
                val text = outcome + "\n\n" + observation(if (expectChange) before else null, after)
                return if (didFail) ToolResult(false, text, outcome) else ToolResult(true, text)
            }

            val el = (target as? Target.Resolved)?.element

            return when (name) {
                "click", "click_text" -> when {
                    el != null -> act { b.click(el) }
                    else -> ToolResult(false, "", "Give an element index, its text, or a selector.")
                }
                "type_text" -> {
                    val text = BrowserArgs.str(args, "text") ?: return ToolResult(false, "", "Missing text.")
                    if (el?.password == true) {
                        return ToolResult(
                            false, observation(null, page),
                            "Refused to type a plaintext password. Use use_credential (a saved login) or ask_user_secret (the user types it)."
                        )
                    }
                    val submitting = BrowserArgs.bool(args, "submit")
                    act {
                        val typed = b.type(el, text, BrowserArgs.bool(args, "append"))
                        if (submitting && !failed(typed)) { delay(150); typed + " " + b.submit() } else typed
                    }
                }
                "select" -> when {
                    el != null -> {
                        val option = BrowserArgs.str(args, "option") ?: return ToolResult(false, "", "Missing option.")
                        act { b.select(el, option) }
                    }
                    else -> ToolResult(false, "", "Give the dropdown's element index.")
                }
                "check" -> when {
                    el != null -> {
                        val want = (args["value"] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() } ?: true
                        act { b.setChecked(el, want) }
                    }
                    else -> ToolResult(false, "", "Give the element's index.")
                }
                "press_key" -> {
                    val key = BrowserArgs.str(args, "key") ?: return ToolResult(false, "", "Missing key.")
                    act(expectChange = false) { b.pressKey(key) }
                }
                "hover" -> when {
                    el != null -> act(expectChange = false) { b.hover(el) }
                    else -> ToolResult(false, "", "Give the element's index.")
                }
                "scroll" -> {
                    val dir = BrowserArgs.str(args, "direction") ?: "down"
                    act(maxSettle = 1_200) { b.scroll(dir, el, (BrowserArgs.num(args, "amount") ?: 0.7).toFloat().coerceIn(0.2f, 1.2f)) }
                }
                "scroll_to" -> {
                    val text = BrowserArgs.str(args, "text") ?: return ToolResult(false, "", "Missing text.")
                    act(maxSettle = 1_200) { b.scrollToText(text) }
                }
                "upload_file" -> when {
                    el != null -> {
                        val path = BrowserArgs.str(args, "path") ?: return ToolResult(false, "", "Missing path.")
                        val absolute = projectFile(path)
                            ?: return ToolResult(false, "", "No such project file: $path (paths are relative to the project root).")
                        act(maxSettle = 3_000, expectChange = false) { b.uploadFile(el, absolute) }
                    }
                    else -> ToolResult(false, "", "Give the file input's element index.")
                }
                "evaluate" -> {
                    val js = BrowserArgs.str(args, "js") ?: return ToolResult(false, "", "Missing js.")
                    act(expectChange = false) { b.evaluate(js) }
                }
                "open_url" -> {
                    val url = BrowserArgs.str(args, "url") ?: return ToolResult(false, "", "Missing url.")
                    val newTab = BrowserArgs.bool(args, "new_tab")
                    act(maxSettle = 3_000) {
                        if (newTab) b.newTab(url) else b.navigate(url)
                    }
                }
                "back" -> act(maxSettle = 2_500) { b.back() }
                "forward" -> act(maxSettle = 2_500) { b.forward() }
                "reload" -> act(maxSettle = 2_500) { b.reload() }
                "new_tab" -> act(maxSettle = 2_500) { b.newTab(BrowserArgs.str(args, "url")) }
                "switch_tab" -> {
                    val id = BrowserArgs.int(args, "id") ?: return ToolResult(false, "", "Missing tab id.")
                    act(maxSettle = 2_000, expectChange = false) { b.switchTab(id) }
                }
                "close_tab" -> {
                    val id = BrowserArgs.int(args, "id") ?: return ToolResult(false, "", "Missing tab id.")
                    act(expectChange = false) { b.closeTab(id) }
                }
                else -> ToolResult(false, "", "Unknown tool \"$name\". Available: ${tools.joinToString { it.function.name }}.")
            }
        }

        /** A project file's absolute path, for upload_file; null when outside the project. */
        private fun projectFile(path: String): String? {
            val root = projectRootPath?.let { File(it) } ?: return null
            if (!root.isDirectory) return null
            val f = File(root, path)
            return if (f.exists() && f.isFile && f.canonicalPath.startsWith(root.canonicalPath)) f.absolutePath else null
        }

        /** Tools that only read, note or talk: no target, no guard. Null for everything else. */
        private suspend fun readOnly(name: String, args: JsonObject): ToolResult? = when (name) {
            "browser_status" -> ToolResult(true, browser.status())
            "list_tabs" -> {
                val t = browser.tabs()
                ToolResult(true, if (t.isEmpty()) "No tabs." else t.joinToString("\n") { "[${it.id}]${if (it.active) "*" else " "} ${it.title.ifBlank { "(no title)" }} — ${it.url}" })
            }
            "list_downloads" -> {
                val d = browser.downloads()
                ToolResult(true, if (d.isEmpty()) "No downloads yet." else d.joinToString("\n") { "${it.name} (${it.mime}, ${it.bytes} bytes)" })
            }
            "read_download" -> {
                val n = BrowserArgs.str(args, "name") ?: return ToolResult(false, "", "Missing name.")
                ToolResult(true, browser.readDownload(n, (BrowserArgs.int(args, "max_chars") ?: 20_000).coerceIn(1_000, 60_000)))
            }
            "web_search" -> {
                val q = BrowserArgs.str(args, "query") ?: return ToolResult(false, "", "Missing query.")
                val hits = com.oro.app.data.ai.search.WebSearch.search(q, (BrowserArgs.int(args, "limit") ?: 6).coerceIn(1, 10))
                ToolResult(true, com.oro.app.data.ai.search.WebSearch.format(q, hits))
            }
            "take_note" -> {
                val note = BrowserArgs.str(args, "note")?.trim().orEmpty()
                if (note.isEmpty()) ToolResult(false, "", "Empty note.") else {
                    notes += note.take(300)
                    while (notes.size > 16) notes.removeAt(0)
                    ToolResult(true, "Noted.")
                }
            }
            "ask_user" -> {
                val q = BrowserArgs.str(args, "question")
                if (q == null) ToolResult(false, "", "Missing question.")
                else if (lastPage?.needsHuman == true) handOff(currentStepId)
                else repeatedQuestion(q) ?: run {
                    val options = (args["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
                    val answer = askUser(q, options)
                    questions += q to answer
                    mustRecapture = true
                    updateStep(currentStepId) { it.copy(detail = "You answered: $answer", status = StepStatus.DONE, endedAt = System.currentTimeMillis()) }
                    ToolResult(true, "The user answered: $answer")
                }
            }
            "ask_user_secret" -> askSecret(args)
            "list_credentials" -> {
                val list = vault()?.list().orEmpty()
                ToolResult(true, if (list.isEmpty()) {
                    "No saved credentials. The user can add them in the browser screen's settings, or use ask_user_secret for a one-time secret."
                } else list.joinToString("\n") { "[${it.id}] ${it.label} — ${it.domain} — user: ${it.username}" })
            }
            "use_credential" -> useCredential(args)
            "finish_task" -> {
                val summary = BrowserArgs.str(args, "summary").orEmpty()
                val ok = (args["success"] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.trim()?.toBooleanStrictOrNull() } ?: true
                finishSummary = summary.ifBlank { null }
                finishCalled = true
                finishSuccess = ok
                ToolResult(true, summary)
            }
            "find_text" -> {
                val q = BrowserArgs.str(args, "text").orEmpty()
                val now = observe()
                val hits = TextMatchWeb.hits(q, now).filter { it.score >= TextMatchWeb.ACT_THRESHOLD }
                ToolResult(true, if (hits.isEmpty()) "\"$q\" is not on the current page. Try scroll_to(\"$q\") or read_page."
                else "Found ${hits.size}:\n" + hits.take(12).joinToString("\n") { it.element.render() })
            }
            "extract" -> {
                val fieldsEl = args["fields"]
                val fieldsJson = fieldsEl?.toString() ?: "[]"
                val sel = BrowserArgs.str(args, "selector")
                val limit = (BrowserArgs.int(args, "limit") ?: 40).coerceIn(1, 200)
                browsed = true
                ToolResult(true, browser.extract(sel, fieldsJson, limit))
            }
            "read_page" -> {
                val mode = BrowserArgs.str(args, "mode") ?: "markdown"
                val selector = BrowserArgs.str(args, "selector")
                val max = (BrowserArgs.int(args, "max_chars") ?: 12_000).coerceIn(1_000, 40_000)
                browsed = true
                val text = browser.readPage(mode, selector, max)
                val now = observe()
                ToolResult(true, "Page content ($mode):\n$text\n\n" + observation(null, now, maxChars = 2_500))
            }
            "screenshot" -> {
                if (!vision) ToolResult(false, "", "Screenshots are not available for this model. Use the element listing.")
                else {
                    val now = observe()
                    val image = browser.screenshot(BrowserArgs.bool(args, "full_page"))
                    if (image == null) ToolResult(false, observation(null, now), "The screenshot failed. Use the listing.")
                    else ToolResult(true, "Screenshot attached.\n\n" + observation(null, now), images = listOf(image))
                }
            }
            "wait" -> {
                val s = (BrowserArgs.num(args, "seconds") ?: 2.0).coerceIn(0.5, 10.0)
                delay((s * 1000).toLong())
                val now = observe()
                ToolResult(true, "Waited ${"%.1f".format(s)}s.\n\n" + observation(null, now))
            }
            "wait_for" -> waitFor(args)
            else -> null
        }

        /**
         * ask_user_secret: the user types the secret -- into a masked card
         * (the engine then fills the field) or straight into the live page.
         * The value never enters the model's history, the transcript or the
         * state: only the fact that it was entered.
         */
        private suspend fun askSecret(args: JsonObject): ToolResult {
            val purpose = BrowserArgs.str(args, "purpose") ?: "a secret"
            val fieldIndex = BrowserArgs.int(args, "index")
            val d = CompletableDeferred<SecretOutcome>()
            pendingSecret = d
            val id = currentStepId
            addStep(Step(if (id >= 0) id else nextId(), StepKind.SECRET, "ask_user_secret", purpose.take(200), status = StepStatus.WAITING))
            _state.update { it.copy(secretRequest = SecretRequest(purpose, fieldIndex), status = "Waiting for your secret") }
            if (fieldIndex != null) {
                val page = freshPage()
                page.element(fieldIndex)?.let { browser.focusForUser(it) }
            } else browser.focusForUser(null)
            notifyUser("Orobi needs a secret", purpose)
            val outcome = try {
                withTimeoutOrNull(300_000) { d.await() } ?: SecretOutcome.Declined
            } finally {
                pendingSecret = null
                _state.update { it.copy(secretRequest = null, status = "Continuing") }
            }
            mustRecapture = true
            return when (outcome) {
                is SecretOutcome.Entered -> {
                    val page = freshPage()
                    val el = fieldIndex?.let { page.element(it) }
                    if (el != null) {
                        val typed = browser.typeSecret(el, outcome.value)
                        updateStep(id) { it.copy(detail = "You provided it; it was entered.", status = StepStatus.DONE, endedAt = System.currentTimeMillis()) }
                        if (typed.startsWith("Could not")) {
                            ToolResult(false, "", "$typed The secret was discarded; ask again with the right field.")
                        } else {
                            ToolResult(true, "The user provided the secret and it was entered into the field.\n\n" + observation(null, observe()))
                        }
                    } else {
                        updateStep(id) { it.copy(detail = "You provided it.", status = StepStatus.DONE, endedAt = System.currentTimeMillis()) }
                        ToolResult(false, "", "The field is gone; the secret was not entered. Look at the page again and ask with the right field index.")
                    }
                }
                SecretOutcome.Direct -> {
                    updateStep(id) { it.copy(detail = "You typed it into the page yourself.", status = StepStatus.DONE, endedAt = System.currentTimeMillis()) }
                    ToolResult(true, "The user typed the secret straight into the page. Continue (verify the field is filled before submitting).\n\n" + observation(null, observe()))
                }
                SecretOutcome.Declined -> {
                    updateStep(id) { it.copy(detail = "Declined.", status = StepStatus.BLOCKED, endedAt = System.currentTimeMillis()) }
                    ToolResult(true, "The user declined to provide the secret. Do not retry; continue another way or finish_task explaining what is blocked.")
                }
            }
        }

        /**
         * use_credential: the engine fills a saved login. The password goes
         * vault → engine → page; the model sees only which fields were filled.
         * Sensitive by nature: always asks first when confirmations are on.
         */
        private suspend fun useCredential(args: JsonObject): ToolResult {
            val id = BrowserArgs.str(args, "id") ?: return ToolResult(false, "", "Missing credential id (see list_credentials).")
            val vault = vault() ?: return ToolResult(false, "", "The credential store is not available.")
            val entry = vault.get(id) ?: return ToolResult(false, "", "No saved credential with id \"$id\".")
            if (settings.confirmSensitive &&
                !requestApproval("sign in to ${entry.domain} as ${entry.username} (saved credential)")
            ) {
                return ToolResult(true, "Blocked: the user denied signing in with the saved credential. Do not retry it.")
            }
            val page = freshPage()
            val passField = BrowserArgs.int(args, "pass_index")?.let { page.element(it) }
                ?: page.passwordFields.firstOrNull { !it.disabled }
                ?: return ToolResult(false, observation(null, page), "No password field on this page. Navigate to the login form first.")
            val userField = BrowserArgs.int(args, "user_index")?.let { page.element(it) }
                ?: page.editable.filter { !it.password && !it.disabled }
                    .filter { it.bounds.centerY <= passField.bounds.centerY }
                    .minByOrNull { kotlin.math.abs(passField.bounds.centerY - it.bounds.centerY) }
            var report = ""
            if (userField != null) {
                browser.typeSecret(userField, entry.username)
                report += "username into ${describe(userField)}"
            }
            browser.typeSecret(passField, entry.password)
            report += if (report.isEmpty()) "password into ${describe(passField)}" else ", password into ${describe(passField)}"
            mustRecapture = true
            return ToolResult(true, "Signed-in details filled from the vault: $report (values are never shown). Submit the form when ready.\n\n" + observation(null, observe()))
        }

        private fun describe(e: PageElement): String =
            if (e.label.isNotBlank()) "\"${e.label.take(30)}\"" else "${e.role} [${e.index}]"

        /**
         * Hands a human check to the user: the screen asks them to pass it and
         * the run carries on by itself once it is gone (or when they answer).
         */
        private suspend fun handOff(stepId: Long?): ToolResult {
            val kind = lastPage?.humanCheck ?: HumanCheckKind.TEXT
            val question = CaptchaWatch.waitingMessage(kind)
            val id = stepId ?: nextId().also {
                addStep(Step(it, StepKind.QUESTION, "ask_user", "Human check", url = lastPage?.url, status = StepStatus.WAITING))
            }
            notifyUser("Orobi is waiting for you", question)
            bringOroToFront()
            val answer = askUntil(question, listOf("Done")) {
                val p = browser.snapshot()
                !p.needsHuman && !CaptchaWatch.isInterstitial(p)
            }
            mustRecapture = true
            updateStep(id) {
                it.copy(detail = answer?.let { a -> "You answered: $a" } ?: "You passed it", status = StepStatus.DONE, endedAt = System.currentTimeMillis())
            }
            questions += question to (answer ?: "(passed the check)")
            notes += "The user passed a human check."
            val head = answer?.let { "The user answered: $it" } ?: "The user passed the human check."
            if (stepId == null) handOffNote = head
            return ToolResult(true, head + "\n\n" + observation(null, observe()))
        }

        /**
         * fill_form: every field from one snapshot -- typed, checked or
         * picked -- then (submit) the form's own button. One approval covers
         * the whole form when anything in it is sensitive. Fields not on
         * screen are looked for further down, a few scrolls at most. A field
         * whose name fits two boxes is reported, never guessed. Password
         * fields refuse plaintext: the vault or the user fills those.
         */
        private suspend fun fillForm(args: JsonObject, start: PageState): ToolResult {
            val entries = FormFillWeb.entries(args["fields"])
            if (entries.isEmpty()) {
                return ToolResult(false, "", "fill_form needs fields: [{\"field\": \"Email\", \"value\": \"...\"}, ...] (field = the label or placeholder; or index).")
            }
            val before = start.signature
            var page = start
            val report = arrayOfNulls<String>(entries.size)
            val done = BooleanArray(entries.size)
            var scrolls = 0
            val filled = ArrayList<PageElement>()
            fun label(i: Int) = entries[i].field ?: "[${entries[i].index}]"

            while (true) {
                val used = filled.mapTo(HashSet()) { it.index }
                val plan = ArrayList<Pair<Int, PageElement>>()
                for (i in entries.indices) {
                    if (done[i]) continue
                    val entry = entries[i]
                    val element: PageElement? = if (entry.index != null) page.element(entry.index)
                    else when (val m = FormFillWeb.target(entry, page, used)) {
                        is FormFillWeb.Match.Found -> m.element
                        is FormFillWeb.Match.Ambiguous -> {
                            report[i] = "${label(i)}: fits ${m.candidates.size} fields (" + m.candidates.joinToString { "[${it.index}]" } + "); give its index"
                            done[i] = true
                            null
                        }
                        FormFillWeb.Match.Missing -> null
                    }
                    if (element != null && !done[i]) {
                        // Passwords never take plaintext from the model.
                        if (element.password) {
                            report[i] = "${label(i)}: password field -- use use_credential or ask_user_secret for it"
                            done[i] = true
                            continue
                        }
                        used += element.index
                        plan += i to element
                    }
                }

                // Filling fields is not sensitive by itself; the submit that
                // follows is judged on its own (payment pages, send buttons,
                // destructive dialogs), so the user approves at most once.

                var moved = false
                for ((i, e) in plan) {
                    val entry = entries[i]
                    val outcome: String = when (FormFillWeb.kindOf(e)) {
                        FormFillWeb.Kind.TEXT -> {
                            val typed = browser.type(e, entry.value, append = false)
                            if (failed(typed)) "not filled ($typed)" else "filled"
                        }
                        FormFillWeb.Kind.TOGGLE -> {
                            val want = FormFillWeb.wantChecked(entry.value) ?: true
                            if (e.checked == want) (if (want) "already checked" else "already off")
                            else {
                                val set = browser.setChecked(e, want)
                                if (failed(set)) "not changed ($set)" else if (want) "checked" else "unchecked"
                            }
                        }
                        FormFillWeb.Kind.CHOICE -> {
                            if (e.role == "combobox" && e.options > 0) {
                                val chose = browser.select(e, entry.value)
                                if (failed(chose)) "no option \"${entry.value.take(30)}\"" else "chose in ${describe(e)}"
                            } else {
                                // A custom dropdown or a radio group by label: open, then pick.
                                val opened = browser.click(e)
                                moved = true
                                if (failed(opened)) "could not open ($opened)" else {
                                    browser.settle(maxMs = 1_200)
                                    var list = observe()
                                    var pick = TextMatchWeb.hits(entry.value, list).firstOrNull { it.score >= 60 && it.element.index != e.index }
                                    if (pick == null) {
                                        browser.scroll("down", null, 0.5f)
                                        browser.settle(maxMs = 1_000)
                                        list = observe()
                                        pick = TextMatchWeb.hits(entry.value, list).firstOrNull { it.score >= 60 && it.element.index != e.index }
                                    }
                                    val option = pick?.element
                                    if (option == null) "no option \"${entry.value.take(30)}\""
                                    else {
                                        val chose = browser.click(option)
                                        if (failed(chose)) "could not choose \"${option.label.take(30)}\"" else "chose \"${option.label.take(30)}\""
                                    }
                                }
                            }
                        }
                    }
                    report[i] = "${label(i)}: $outcome"
                    done[i] = true
                    filled += e
                    if (moved) break
                }
                if (moved) {
                    browser.settle(maxMs = 1_200)
                    page = observe()
                    continue
                }
                if (done.all { it }) break
                // Fields not on screen yet: further down the form.
                if (scrolls >= MAX_FORM_SCROLLS || !page.canScrollDown) break
                browser.scroll("down", null, 0.8f)
                scrolls++
                browser.settle(maxMs = 1_000)
                page = observe()
            }
            for (i in entries.indices) if (report[i] == null) report[i] = "${label(i)}: not on this page"

            var submitted = ""
            val allFilled = report.all { r -> r != null && FILLED.containsMatchIn(r.substringAfter(": ")) }
            if (BrowserArgs.bool(args, "submit")) {
                browser.settle(maxMs = 1_000)
                val now = observe()
                val button = FormFillWeb.submitButton(now)
                submitted = when {
                    !allFilled -> "\nNot submitted: some fields were not filled."
                    button == null -> "\nNot submitted: no submit button found; click it yourself."
                    else -> {
                        val sensitive = WebGuard.click(button, now)
                        if (sensitive != null && settings.confirmSensitive && !requestApproval(sensitive)) {
                            "\nNot submitted: the user denied \"$sensitive\"."
                        } else {
                            if (sensitive != null) { gate(); mustRecapture = true }
                            val target = if (sensitive != null) {
                                val again = freshPage()
                                FormFillWeb.submitButton(again)?.takeIf { sameTarget(button, it) }
                            } else button
                            if (target == null) "\nNot submitted: the page changed while you decided."
                            else "\nSubmitted: ${browser.click(target)}"
                        }
                    }
                }
            }
            browser.settle(maxMs = 2_000)
            val after = observe()
            val text = "Form: " + report.joinToString("; ") + submitted + "\n\n" + observation(before, after)
            return if (allFilled) ToolResult(true, text) else ToolResult(false, text, "Form: " + report.joinToString("; "))
        }

        /** The element a call targets, resolved against [now] (index/text/selector). */
        private suspend fun resolve(name: String, args: JsonObject, seen: PageState?, now: PageState): Target? {
            if (name !in TARGETED) return null
            BrowserArgs.int(args, "index")?.let { i ->
                seen?.element(i)?.let { return Target.Resolved(it) }
                now.element(i)?.let { return Target.Resolved(it) }
                return Target.Missing("[$i] is not on the page anymore")
            }
            BrowserArgs.str(args, "selector")?.let { sel ->
                now.elements.firstOrNull { it.selector == sel }?.let { return Target.Resolved(it) }
                browser.bySelector(sel)?.let { return Target.Resolved(it) }
                return Target.Missing("nothing matches $sel")
            }
            val text = BrowserArgs.str(args, "text") ?: return Target.Missing("no index, text or selector given")
            val nth = BrowserArgs.int(args, "nth")
            return when (val m = TextMatchWeb.find(text, now, nth)) {
                is TextMatchWeb.Result.Found -> Target.Resolved(m.element)
                is TextMatchWeb.Result.Ambiguous -> Target.Ambiguous(
                    "\"$text\" matches ${m.candidates.size} elements" +
                        (if (m.weak) " (weakly)" else "") + ": " +
                        m.candidates.joinToString("; ") { "[${it.index}] ${it.role} \"${it.label.take(30)}\"" } +
                        (if (!m.weak && nth == null) " -- give its index or nth" else "")
                )
                TextMatchWeb.Result.NotFound -> {
                    // The page moved between the model's look and now: try the page it saw.
                    if (seen != null && seen.signature != now.signature) {
                        when (val m2 = TextMatchWeb.find(text, seen, nth)) {
                            is TextMatchWeb.Result.Found -> {
                                browser.bySelector(m2.element.selector)?.let { return Target.Resolved(it) }
                                return Target.Missing("\"$text\" was on the page you saw, but the page changed; look again")
                            }
                            else -> Target.Missing("\"$text\" is not on the page")
                        }
                    }
                    Target.Missing("\"$text\" is not on the page")
                }
            }
        }

        private fun problem(target: Target?, page: PageState): ToolResult? = when (target) {
            is Target.Missing -> skipped(target.why + ".", page)
            is Target.Ambiguous -> ToolResult(false, observation(null, page), target.why)
            else -> null
        }

        /** The same control on a re-snapshot: oid, else selector+role+label. */
        private fun sameTarget(a: PageElement, b: PageElement): Boolean =
            (a.oid.isNotBlank() && a.oid == b.oid) ||
                (a.selector.isNotBlank() && a.selector == b.selector && a.role == b.role) ||
                (a.label.isNotBlank() && a.label == b.label && a.role == b.role &&
                    kotlin.math.abs(a.bounds.centerY - b.bounds.centerY) < 24)

        /** What the guard asks about for this call, or null when it is not sensitive. */
        private fun guard(name: String, args: JsonObject, el: PageElement?, page: PageState): String? = when (name) {
            "click", "click_text" -> WebGuard.click(el, page)
            "type_text" -> WebGuard.type(el, page, BrowserArgs.bool(args, "submit"))
            "press_key" -> if (BrowserArgs.str(args, "key")?.equals("enter", true) == true) WebGuard.type(el, page, submit = true) else null
            else -> null
        }

        private suspend fun waitFor(args: JsonObject): ToolResult {
            val timeout = ((BrowserArgs.num(args, "seconds") ?: 10.0).coerceIn(1.0, 30.0) * 1000).toLong()
            val gone = BrowserArgs.bool(args, "gone")
            val startSig = lastPage?.signature ?: 0
            val text = BrowserArgs.str(args, "text")
            val selector = BrowserArgs.str(args, "selector")
            val url = BrowserArgs.str(args, "url_contains")
            val outcome = when {
                text != null -> browser.waitForText(text, gone, timeout)
                selector != null -> browser.waitForSelector(selector, gone, timeout)
                url != null -> browser.waitForUrl(url, timeout)
                else -> return ToolResult(false, "", "wait_for needs text, selector or url_contains.")
            }
            val now = observe()
            val ok = !outcome.startsWith("Waited")
            return if (ok) ToolResult(true, outcome + "\n\n" + observation(startSig, now))
            else ToolResult(false, observation(startSig, now), outcome)
        }

        private fun skipped(why: String, page: PageState): ToolResult =
            ToolResult(false, observation(null, page), "Skipped: $why")

        /** A question already asked and answered in this run: answered with the same answer, no new card. */
        private fun repeatedQuestion(q: String): ToolResult? {
            val asked = questions.lastOrNull { (old, _) -> BrowserPrompt.sameQuestion(old, q) } ?: return null
            return ToolResult(true, "You already asked this and the user answered: ${asked.second}. Continue with that answer.")
        }
    }

    // ------------------------------------------------------ interactions

    private suspend fun requestApproval(what: String): Boolean {
        val d = CompletableDeferred<Boolean>()
        pendingApproval = d
        val stepId = nextId()
        addStep(Step(stepId, StepKind.APPROVAL, null, "Needs your OK: $what", status = StepStatus.WAITING))
        _state.update { it.copy(approval = what, status = "Waiting for your approval") }
        notifyUser("Orobi asks permission", what)
        val allowed = try {
            withTimeoutOrNull(180_000) { d.await() } ?: false
        } finally {
            pendingApproval = null
            _state.update { it.copy(approval = null, status = "Continuing") }
        }
        updateStep(stepId) {
            it.copy(
                title = if (allowed) "You allowed: $what" else "You denied: $what",
                status = if (allowed) StepStatus.DONE else StepStatus.BLOCKED,
                endedAt = System.currentTimeMillis()
            )
        }
        return allowed
    }

    private suspend fun askUser(question: String, options: List<String>): String {
        val d = CompletableDeferred<String>()
        pendingAnswer = d
        _state.update { it.copy(question = Question(question, options), status = "Waiting for your answer") }
        notifyUser("Orobi has a question", question)
        bringOroToFront()
        return try {
            d.await()
        } finally {
            pendingAnswer = null
            _state.update { it.copy(question = null, status = "Continuing") }
        }
    }

    /**
     * [askUser], except the wait also ends, with null, once [cleared] says the
     * reason for asking is gone (checked every [everyMs] while not paused):
     * the user passes a human check and the run carries on by itself.
     */
    private suspend fun askUntil(question: String, options: List<String>, everyMs: Long = 900, cleared: suspend () -> Boolean): String? {
        val d = CompletableDeferred<String>()
        pendingAnswer = d
        _state.update { it.copy(question = Question(question, options), status = "Your turn") }
        return try {
            coroutineScope {
                val watcher = launch {
                    while (isActive) {
                        delay(everyMs)
                        if (!paused.value && runCatching { cleared() }.getOrDefault(false)) {
                            d.complete(CLEARED)
                            break
                        }
                    }
                }
                val answer = d.await()
                watcher.cancel()
                answer.takeUnless { it == CLEARED }
            }
        } finally {
            pendingAnswer = null
            _state.update { it.copy(question = null, status = "Continuing") }
        }
    }

    /**
     * Ends a run, whatever ended it: one state update (so a new run can start
     * the moment it is published), the keep-alive off, and a separate wrap-up
     * (notification, come back to Oro) that a new run cancels.
     */
    private fun finish(context: Context, result: AgentRunResult, settings: Settings, origin: Origin, run: BrowserRun) {
        publishEnd(result, run)
        endKeepAlive(context)
        BrowserEngine.flushCookies()
        val wrapUp = scope.launch(start = CoroutineStart.LAZY) {
            delay(if (origin == Origin.CHAT) 1_200 else 2_000)
            val s = _state.value
            val title = when (s.endReason) {
                EndReason.DONE -> "Browser task done"
                EndReason.STOPPED_BY_USER -> "Browser task stopped"
                EndReason.ACTION_LIMIT -> "Action limit reached"
                else -> "Browser task ended"
            }
            notifyUpdate(title, s.summary ?: s.error ?: s.status)
            if (origin == Origin.USER && settings.returnToOro && s.needsUser) bringOroToFrontActivity()
        }
        wrapUpJob = wrapUp
        wrapUp.start()
    }

    /** The one state update that ends a run: why it ended, its summary or error, and the last step closed. */
    private fun publishEnd(result: AgentRunResult, run: BrowserRun) {
        val now = System.currentTimeMillis()
        val reason = endReason(result, run.finishCalled, run.finishSuccess, cancelReason, run.limitHit)
        val finalText = (result as? AgentRunResult.Completed)?.finalText?.trim().orEmpty()
        _state.update { s ->
            val last = s.steps.lastOrNull()
            val steps = if (last != null && (last.status == StepStatus.RUNNING || last.status == StepStatus.WAITING)) {
                s.steps.dropLast(1) + last.copy(status = StepStatus.FAILED, endedAt = now)
            } else s.steps
            s.copy(
                running = false, paused = false, steps = steps, endedAt = now, endReason = reason,
                question = null, approval = null, secretRequest = null,
                success = reason == EndReason.DONE,
                summary = when (reason) {
                    EndReason.DONE -> run.finishSummary ?: finalText.ifBlank { "Done." }
                    EndReason.UNCONFIRMED -> finalText.ifBlank { null }
                    else -> run.finishSummary
                },
                error = when (reason) {
                    EndReason.DONE, EndReason.UNCONFIRMED, EndReason.FAILED -> null
                    EndReason.STOPPED_BY_USER -> "Stopped by you."
                    EndReason.ENGINE_LOST -> "The browser engine stopped, so the task stopped."
                    EndReason.ACTION_LIMIT -> "Stopped after ${s.actions} actions (the limit). You can change the limit in the browser settings."
                    EndReason.ERROR -> (result as? AgentRunResult.Failed)?.message ?: "The browser agent stopped."
                },
                status = when (reason) {
                    EndReason.DONE -> "Done"
                    EndReason.UNCONFIRMED -> "Ended"
                    EndReason.FAILED -> "Couldn't finish"
                    EndReason.ACTION_LIMIT -> "Action limit reached"
                    EndReason.STOPPED_BY_USER, EndReason.ENGINE_LOST, EndReason.ERROR -> "Stopped"
                },
            )
        }
    }

    /**
     * Runs one task on [browser], with [client] standing in for the model, in
     * the caller's coroutine. The scenario bench (app/src/test) drives the
     * real run loop -- observation, target resolution, the guard, stuck
     * signals, endings -- through this; there is no UI and no wrap-up.
     */
    internal suspend fun runScenario(
        browser: Browser,
        task: String,
        model: AiModel,
        client: AgentModelClient,
        settings: Settings = Settings(),
        vision: Boolean = false,
        startUrl: String? = null,
    ): State {
        check(!_state.value.running) { "a run is already live" }
        paused.value = false
        cancelReason = null
        lastAnswerAt = 0L
        stepSeq.set(0)
        _state.value = State(
            running = true, task = task.trim(), modelName = model.name,
            status = "Starting", maxActions = settings.maxActions, startedAt = System.currentTimeMillis(),
            currentUrl = startUrl.orEmpty(),
        )
        val run = BrowserRun(browser, task.trim(), model, client, settings, Origin.USER, startUrl, vision)
        var result: AgentRunResult = AgentRunResult.Failed("The browser agent stopped unexpectedly.", retryable = true)
        try {
            result = run.run()
        } catch (e: CancellationException) {
            result = AgentRunResult.Failed("Stopped.", retryable = false)
            throw e
        } catch (e: Exception) {
            result = AgentRunResult.Failed(e.message ?: "The browser agent crashed.", retryable = true)
        } finally {
            publishEnd(result, run)
        }
        return _state.value
    }

    /**
     * Why a run ended. Pure, for tests. Who cancelled wins (Stop, engine
     * lost); then the loop's own limit or failure; then whether the model
     * called finish_task, and with what.
     */
    internal fun endReason(
        result: AgentRunResult,
        finishCalled: Boolean,
        finishSuccess: Boolean?,
        cancelled: CancelReason?,
        limitHit: Boolean,
    ): EndReason = when {
        cancelled == CancelReason.ENGINE_LOST -> EndReason.ENGINE_LOST
        cancelled == CancelReason.USER -> EndReason.STOPPED_BY_USER
        result is AgentRunResult.IterationLimit -> EndReason.ACTION_LIMIT
        result is AgentRunResult.Failed -> if (limitHit) EndReason.ACTION_LIMIT else EndReason.ERROR
        finishCalled && finishSuccess != false -> EndReason.DONE
        limitHit -> EndReason.ACTION_LIMIT
        finishCalled -> EndReason.FAILED
        else -> EndReason.UNCONFIRMED
    }

    /** Best effort: Android 10+ blocks background activity starts; the notification is the reliable path. */
    private fun bringOroToFront() {
        if (appContext == null) return
        val inForeground = runCatching { OroApplication.getInstance().isInForeground }.getOrDefault(true)
        if (inForeground) return
        bringOroToFrontActivity()
    }

    private fun bringOroToFrontActivity() {
        val ctx = appContext ?: return
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_BROWSER_AGENT, true)
        runCatching { ctx.startActivity(intent) }
    }

    /** Post a notification when the app is not on screen. */
    private fun notifyUser(title: String, text: String) = notifyUpdate(title, text)

    private fun notifyUpdate(title: String, text: String) {
        val ctx = appContext ?: return
        runCatching { AgentNotifications.update(ctx, BROWSER_RUN_ID, title, text.take(200)) }
    }

    private fun vault(): WebVault? = appContext?.let { WebVault.also { v -> v.init(it) } }

    const val EXTRA_OPEN_BROWSER_AGENT = "com.oro.app.OPEN_BROWSER_AGENT"
    private const val BROWSER_RUN_ID = "__browser__"

    // ------------------------------------------------------------ helpers

    internal const val PAGE_MARKER = PageState.PAGE_MARKER

    /** Outcome sentences that mean the action did not happen. */
    private val FAILED = Regex("^(No |Unknown |Skipped|Refused|Could not|Cannot|Blocked)|could not|failed|^\\w+ needs |not supported|rejected|refused", RegexOption.IGNORE_CASE)
    private val QUOTED = Regex("\"[^\"]*\"|“[^”]*”")

    /** Judges an outcome sentence, not the labels quoted in it ("Clicked “Failed orders”" succeeded). */
    internal fun failed(outcome: String): Boolean = FAILED.containsMatchIn(outcome.replace(QUOTED, ""))

    /** fill_form outcomes that count as done. */
    private val FILLED = Regex("^(filled|checked|unchecked|already|chose)")
    private const val MAX_FORM_SCROLLS = 3
    private const val MAX_SNAPSHOT_AGE_MS = 4_000L
    /** The answer [askUntil] gives itself when the reason for asking is gone. */
    private const val CLEARED = "\u0000cleared"
    /** Actions on the page's content: they wait while a human check is up (navigation does not). */
    private val HANDS_ON = setOf(
        "click", "click_text", "type_text", "fill_form", "select", "check", "press_key",
        "hover", "scroll", "scroll_to", "upload_file", "evaluate",
    )
    /** Tools whose target is resolved against the page. */
    private val TARGETED = setOf("click", "click_text", "type_text", "select", "check", "hover", "scroll", "upload_file")

    private fun nextId() = stepSeq.incrementAndGet()

    private fun addStep(step: Step) = _state.update { it.copy(steps = (it.steps + step).takeLast(300)) }

    private fun updateStep(id: Long, transform: (Step) -> Step) = _state.update { st ->
        val i = st.steps.indexOfLast { it.id == id }
        if (i < 0) st else st.copy(steps = st.steps.toMutableList().also { it[i] = transform(it[i]) })
    }

    private fun detectToolRejection(stream: Flow<StreamEvent>, onReject: () -> Unit): Flow<StreamEvent> = kotlinx.coroutines.flow.flow {
        stream.collect { event ->
            if (event is StreamEvent.Error && event.httpCode in setOf(400, 404, 422) &&
                Regex("tool|function", RegexOption.IGNORE_CASE).containsMatchIn(event.message)
            ) {
                onReject()
                emit(event.copy(retryable = true))
            } else emit(event)
        }
    }

    /**
     * Older observations collapse to their outcome line; only the newest page
     * is kept whole. Long outcomes (read_page text) are trimmed once they are
     * no longer the latest: the facts worth keeping are in notes. Under
     * context pressure, keep fewer turns.
     */
    internal fun compact(history: List<ChatRequestMessage>, pressure: Int): List<ChatRequestMessage> {
        val lastPageIdx = history.indexOfLast { it.content?.contains(PAGE_MARKER) == true }
        val keepTail = when (pressure) { 0 -> 48; 1 -> 24; else -> 12 }
        val compacted = history.mapIndexed { i, m ->
            val c = m.content
            if (i != lastPageIdx && c != null && c.contains(PAGE_MARKER)) {
                val head = c.substringBefore(PAGE_MARKER).trimEnd().let { if (it.length > 1_200) it.take(1_200) + " …" else it }
                val unchanged = c.substringAfter(PAGE_MARKER).lineSequence().any { it.contains("did not change") }
                m.copy(content = head.ifBlank { "(observation)" } + (if (unchanged) " [page did not change]" else "") + " [older page omitted]")
            } else m
        }
        if (compacted.size <= keepTail + 1) return compacted
        // Keep the task message, then the newest turns starting at a clean boundary.
        var start = compacted.size - keepTail
        while (start < compacted.size && compacted[start].role == "tool") start++
        return listOf(compacted.first()) + compacted.subList(start, compacted.size)
    }

    private fun describe(call: ToolCallResponse, page: PageState?): Pair<String, StepKind> {
        val a = JsonParserUtils.parseToJsonObject(call.function.arguments) ?: JsonObject(emptyMap())
        fun target(): String {
            val e = BrowserArgs.int(a, "index")?.let { page?.element(it) }
            val text = BrowserArgs.str(a, "text")
            val sel = BrowserArgs.str(a, "selector")
            return when {
                e != null && e.label.isNotBlank() -> "\"${e.label.take(40)}\""
                e != null -> "${e.role} [${e.index}]"
                text != null -> "\"${text.take(40)}\""
                sel != null -> sel.take(40)
                else -> "the page"
            }
        }
        fun quoted(vararg keys: String) = "\"${keys.firstNotNullOfOrNull { BrowserArgs.str(a, it) }.orEmpty().take(40)}\""
        return when (call.function.name) {
            "open_url" -> "Open ${BrowserArgs.str(a, "url").orEmpty().removePrefix("https://").removePrefix("http://").removePrefix("www.").take(48)}" to StepKind.ACTION
            "click" -> "Click ${target()}" to StepKind.ACTION
            "click_text" -> "Click ${quoted("text")}" to StepKind.ACTION
            "type_text" -> "Type ${quoted("text")}" + (if (BrowserArgs.bool(a, "submit")) " and submit" else "") to StepKind.ACTION
            "fill_form" -> FormFillWeb.entries(a["fields"]).size.let { n -> "Fill $n field${if (n == 1) "" else "s"}" + (if (BrowserArgs.bool(a, "submit")) " and submit" else "") } to StepKind.ACTION
            "select" -> "Choose ${quoted("option")}" to StepKind.ACTION
            "check" -> "Toggle ${target()}" to StepKind.ACTION
            "press_key" -> "Press ${BrowserArgs.str(a, "key").orEmpty()}" to StepKind.ACTION
            "hover" -> "Hover ${target()}" to StepKind.ACTION
            "scroll" -> "Scroll ${BrowserArgs.str(a, "direction") ?: "down"}" to StepKind.ACTION
            "scroll_to" -> "Scroll to ${quoted("text")}" to StepKind.ACTION
            "back" -> "Go back" to StepKind.ACTION
            "forward" -> "Go forward" to StepKind.ACTION
            "reload" -> "Reload" to StepKind.ACTION
            "new_tab" -> "New tab" to StepKind.ACTION
            "switch_tab" -> "Switch tab" to StepKind.ACTION
            "close_tab" -> "Close tab" to StepKind.ACTION
            "upload_file" -> "Upload ${quoted("path")}" to StepKind.ACTION
            "evaluate" -> "Run JavaScript" to StepKind.ACTION
            "read_page" -> "Read the page" + (BrowserArgs.str(a, "selector")?.let { " ($it)" } ?: "") to StepKind.SYSTEM
            "extract" -> "Extract rows (${BrowserArgs.str(a, "selector") ?: "table tr"})" to StepKind.SYSTEM
            "find_text" -> "Look for ${quoted("text")}" to StepKind.SYSTEM
            "web_search" -> "Search the web for ${quoted("query")}" to StepKind.SYSTEM
            "screenshot" -> "Look at the page" to StepKind.SYSTEM
            "browser_status" -> "Check the browser" to StepKind.SYSTEM
            "list_tabs" -> "List tabs" to StepKind.SYSTEM
            "list_downloads" -> "List downloads" to StepKind.SYSTEM
            "read_download" -> "Read ${quoted("name")}" to StepKind.SYSTEM
            "list_credentials" -> "Check saved logins" to StepKind.SYSTEM
            "use_credential" -> "Use a saved login" to StepKind.ACTION
            "wait" -> "Wait ${BrowserArgs.num(a, "seconds")?.let { "%.0f".format(it) } ?: "2"}s" to StepKind.SYSTEM
            "wait_for" -> ((BrowserArgs.str(a, "text") ?: BrowserArgs.str(a, "selector") ?: BrowserArgs.str(a, "url_contains"))
                ?.let { "Wait for \"$it\"" } ?: "Wait") to StepKind.SYSTEM
            "take_note" -> "Note: ${BrowserArgs.str(a, "note").orEmpty().take(80)}" to StepKind.NOTE
            "ask_user" -> (BrowserArgs.str(a, "question") ?: "Question") to StepKind.QUESTION
            "ask_user_secret" -> (BrowserArgs.str(a, "purpose") ?: "Needs a secret from you") to StepKind.SECRET
            else -> call.function.name.replace('_', ' ') to StepKind.SYSTEM
        }
    }

    /** Where a targeted call will act. */
    internal sealed interface Target {
        data class Resolved(val element: PageElement) : Target
        /** Several candidates; the model must pick (its listing is in the message). */
        data class Ambiguous(val why: String) : Target
        /** Not on the page (anymore). */
        data class Missing(val why: String) : Target
    }
}

/** Tolerant argument readers for the browser tools' JSON blobs. */
internal object BrowserArgs {
    fun str(args: JsonObject, key: String): String? =
        (args[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    fun int(args: JsonObject, key: String): Int? {
        val p = args[key] as? JsonPrimitive ?: return null
        return p.contentOrNull?.trim()?.removeSurrounding("\"")?.toIntOrNull()
            ?: runCatching { p.content.trim().toDouble().toInt() }.getOrNull()
    }

    fun num(args: JsonObject, key: String): Double? {
        val p = args[key] as? JsonPrimitive ?: return null
        return p.contentOrNull?.trim()?.removeSurrounding("\"")?.toDoubleOrNull()
    }

    fun bool(args: JsonObject, key: String): Boolean {
        val p = args[key] as? JsonPrimitive ?: return false
        return p.booleanOrNull ?: p.contentOrNull?.trim()?.toBooleanStrictOrNull() ?: false
    }
}
