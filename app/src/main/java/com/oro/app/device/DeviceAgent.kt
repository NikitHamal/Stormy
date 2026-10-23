package com.oro.app.device

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.oro.app.MainActivity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Orobi operating the phone: an observe → think → act loop over the
 * accessibility service, built on the same [AgentLoop] as the coding agent
 * (retries, text-tool fallback for models without function calling,
 * cancellation, repetition guard).
 *
 * ### What makes it work with weak models
 *  - The screen is a short numbered list, not a raw tree or an image, so any
 *    text model can read it and act by number -- or by the words on a button
 *    (tap_text). Vision models can also look at a numbered screenshot.
 *  - Every action returns its outcome *and the new screen*, plus an explicit
 *    "the screen did not change" when it didn't, escalating after repeats --
 *    the single most useful signal for noticing a failed tap instead of
 *    repeating it forever.
 *  - Shortcuts beat navigation: intents for alarms/timers/directions/settings
 *    (quick_action), app links for websites and YouTube, media keys.
 *  - Only the latest screen stays in context; older observations collapse to
 *    their one-line outcome. Notes (take_note) persist facts across screens.
 *  - Ads are skipped by the service itself the moment they become skippable.
 *
 * ### What keeps the user in control
 *  - A floating control with Pause and Stop is on screen for the whole run.
 *  - Sensitive actions (send, pay, buy, delete, post, install, password
 *    fields...) wait for an explicit Allow, by default.
 *  - Questions wait for the user, answered from the floating control or Oro.
 *  - Every action is resolved once against a fresh screen ([Targets]); the
 *    guard approves exactly what will be acted on, and a target that moved
 *    while the user paused or decided is skipped, never guessed.
 *  - While Oro itself is in front (the user opened it mid-run), actions wait.
 *  - The run lives in this process-wide object, not in a screen, so it keeps
 *    going while other apps are in front; the bound accessibility service
 *    keeps the process alive.
 *
 * ### Two ways in
 *  - [start]: a task the user typed on the device agent screen.
 *  - [runForResult]: a task the coding agent hands over mid-run (`use_phone`);
 *    it suspends until the phone work is done and returns what was found.
 */
object DeviceAgent {

    enum class StepKind { THOUGHT, ACTION, QUESTION, APPROVAL, NOTE, SYSTEM }
    enum class StepStatus { RUNNING, DONE, FAILED, BLOCKED, WAITING }

    /** Who started a run: the user on the device screen, or the coding agent in a chat. */
    enum class Origin { USER, CHAT }

    /**
     * Why a run ended, so screens never parse [State.error] to find out.
     * DONE: finish_task(success=true). UNCONFIRMED: the model stopped without
     * finish_task. FAILED: finish_task(success=false). STOPPED_BY_USER: Stop.
     * ACTION_LIMIT: the user's action limit. SERVICE_LOST: accessibility turned
     * off. ERROR: the model or the run broke.
     */
    enum class EndReason { DONE, UNCONFIRMED, FAILED, STOPPED_BY_USER, ACTION_LIMIT, SERVICE_LOST, ERROR }

    data class Step(
        val id: Long,
        val kind: StepKind,
        val tool: String?,
        val title: String,
        val detail: String = "",
        val status: StepStatus = StepStatus.RUNNING,
        val app: String? = null,
        val screenChanged: Boolean? = null,
        val startedAt: Long = System.currentTimeMillis(),
        val endedAt: Long? = null,
    )

    data class Question(val text: String, val options: List<String>)

    data class State(
        val serviceConnected: Boolean = false,
        val running: Boolean = false,
        val paused: Boolean = false,
        val task: String = "",
        val modelName: String = "",
        val status: String = "",
        val statusKind: AgentStatus.Kind? = null,
        val steps: List<Step> = emptyList(),
        val currentApp: String = "",
        val actions: Int = 0,
        /** The action limit for this run; 0 = no limit. */
        val maxActions: Int = 0,
        val notes: List<String> = emptyList(),
        val question: Question? = null,
        val approval: String? = null,
        val summary: String? = null,
        val success: Boolean? = null,
        val error: String? = null,
        val startedAt: Long = 0,
        val endedAt: Long = 0,
        val origin: Origin = Origin.USER,
        /** Ads the service skipped during this run. */
        val adsSkipped: Int = 0,
        /** Set when the run ends; null while it runs or before the first one. */
        val endReason: EndReason? = null,
    ) {
        val finished: Boolean get() = !running && startedAt > 0
    }

    data class Settings(
        val confirmSensitive: Boolean = true,
        val showTouches: Boolean = true,
        val returnToOro: Boolean = true,
        /** Stop after this many actions; 0 = no limit (the default -- Stop is always on screen). */
        val maxActions: Int = 0,
        /** Tap "Skip ad" automatically while a task runs. */
        val skipAds: Boolean = true,
        /** Let vision models see numbered screenshots (Android 11+). */
        val screenshots: Boolean = true,
    )

    /** What a [runForResult] run produced, for the model that asked for it. */
    data class Result(
        val success: Boolean,
        val summary: String,
        val notes: List<String>,
        val actions: Int,
        val seconds: Long,
        val finalApp: String,
        /** Set when the run never started or ended in an error. */
        val error: String? = null,
        /** Why the run ended; null when it never started. */
        val endReason: EndReason? = null,
    ) {
        /** The user pressed Stop: the coding agent must not take the phone again on its own. */
        val stoppedByUser: Boolean get() = endReason == EndReason.STOPPED_BY_USER

        /** The tool result text the coding agent reads. */
        fun forModel(): String = buildString {
            append(
                when (endReason) {
                    EndReason.DONE -> "Phone task done"
                    EndReason.UNCONFIRMED -> "Phone task ended without confirming it was done"
                    EndReason.STOPPED_BY_USER -> "The user stopped the phone task"
                    EndReason.ACTION_LIMIT -> "Phone task hit the user's action limit"
                    EndReason.SERVICE_LOST -> "Phone task stopped: the accessibility service was turned off"
                    EndReason.FAILED, EndReason.ERROR -> "Phone task not completed"
                    null -> if (success) "Phone task done" else "Phone task not completed"
                }
            )
            append(" (").append(actions).append(if (actions == 1) " action, " else " actions, ").append(seconds).append(" s")
            if (finalApp.isNotBlank()) append(", ended in ").append(finalApp)
            append(").\n")
            if (stoppedByUser) append("Do not call use_phone again unless the user asks you to.\n")
            if (summary.isNotBlank()) append("Result: ").append(summary.trim()).append('\n')
            if (error != null && error != summary && !stoppedByUser) append("Problem: ").append(error).append('\n')
            if (notes.isNotEmpty()) {
                append("Notes:\n")
                notes.forEach { append("- ").append(it).append('\n') }
            }
        }.trimEnd()
    }

    /** Who cancelled a run, recorded before cancelling so [finish] can say why it ended. */
    internal enum class CancelReason { USER, SERVICE_LOST }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var job: Job? = null
    /** The pause after a run (hide the island, come back to Oro); a new run cancels it. */
    @Volatile private var wrapUpJob: Job? = null
    private val paused = MutableStateFlow(false)
    @Volatile private var pendingAnswer: CompletableDeferred<String>? = null
    @Volatile private var pendingApproval: CompletableDeferred<Boolean>? = null
    private val stepSeq = AtomicLong(0)
    @Volatile private var appContext: Context? = null
    @Volatile private var cancelReason: CancelReason? = null

    /** When the user last answered or approved, so a run can return to its app after an answer given in Oro. */
    @Volatile private var lastAnswerAt = 0L

    /** Set while a run with ad skipping is live; read by the service on UI events. */
    @Volatile private var adSkipActive = false

    /** Ads skipped since the model last saw a screen. */
    private val adsSinceObservation = AtomicInteger(0)

    /** Whether the service should look for skippable ads right now: only during a running, unpaused task. */
    val adSkipEnabled: Boolean get() = adSkipActive && !paused.value && _state.value.running

    // ------------------------------------------------------------- service

    internal fun onServiceAvailable(service: OroAccessibilityService) {
        appContext = service.applicationContext
        _state.update { it.copy(serviceConnected = true) }
        service.overlay.onStop = { stop() }
        service.overlay.onPauseToggle = { togglePause() }
        service.overlay.onApproval = { approve(it) }
        service.overlay.onOpenApp = { bringOroToFront(openDeviceScreen = _state.value.origin != Origin.CHAT) }
    }

    internal fun onServiceLost() {
        _state.update { it.copy(serviceConnected = false) }
        adSkipActive = false
        if (_state.value.running) {
            // finish() reports it: the run's wrap-up always runs.
            cancelReason = CancelReason.SERVICE_LOST
            pendingAnswer?.cancel(); pendingApproval?.complete(false)
            job?.cancel()
        }
    }

    fun refreshServiceState(context: Context) {
        appContext = context.applicationContext
        val connected = OroAccessibilityService.instance.value != null
        _state.update { it.copy(serviceConnected = connected) }
    }

    /** Called by the service after it tapped "Skip ad". */
    internal fun onAdSkipped(what: String) {
        if (!_state.value.running) return
        adsSinceObservation.incrementAndGet()
        _state.update { it.copy(adsSkipped = it.adsSkipped + 1) }
        addStep(Step(nextId(), StepKind.SYSTEM, "skip_ad", what, status = StepStatus.DONE, endedAt = System.currentTimeMillis()))
    }

    // ------------------------------------------------------------ controls

    fun start(context: Context, task: String, model: AiModel, aiRepository: AiRepository, settings: Settings) {
        begin(context, task, model, aiRepository, settings, Origin.USER)
    }

    /**
     * Runs a task handed over by the coding agent and suspends until it ends.
     * Cancelling the caller (the user stopped the chat run) stops the phone
     * task too. Never throws for an ordinary failure: the [Result] says what
     * happened, so the coding agent can adjust.
     */
    suspend fun runForResult(
        context: Context,
        task: String,
        model: AiModel,
        aiRepository: AiRepository,
        settings: Settings,
    ): Result {
        if (_state.value.running) {
            return Result(false, "", emptyList(), 0, 0, "", error = "The phone is busy with another device task. Wait for it to finish, or ask the user.")
        }
        val started = begin(context, task, model, aiRepository, settings, Origin.CHAT)
            ?: return Result(false, "", emptyList(), 0, 0, "", error = _state.value.error ?: "The device agent could not start.")
        try {
            started.join()
        } catch (e: CancellationException) {
            // The user stopped the chat run: the phone task goes with it.
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
            finalApp = s.currentApp,
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
    ): Job? {
        if (_state.value.running) return null
        val service = OroAccessibilityService.instance.value
        appContext = context.applicationContext
        if (service == null) {
            _state.update { it.copy(error = "Turn on Oro Device Agent in Accessibility settings first.") }
            return null
        }
        // The previous run's wrap-up must not hide this run's island or pull Oro forward over it.
        wrapUpJob?.cancel()
        wrapUpJob = null
        paused.value = false
        cancelReason = null
        lastAnswerAt = 0L
        stepSeq.set(0)
        adsSinceObservation.set(0)
        adSkipActive = settings.skipAds
        _state.value = State(
            serviceConnected = true, running = true, task = task.trim(), modelName = model.name,
            status = "Starting", maxActions = settings.maxActions, startedAt = System.currentTimeMillis(),
            origin = origin,
        )
        service.overlay.show(settings.showTouches)
        val run = DeviceRun(
            phone = DeviceController(service), task = task.trim(), model = model,
            modelStream = AgentModelClient { req -> aiRepository.streamChat(model, req.messages, req.tools, temperature = 0.2f) },
            settings = settings, origin = origin,
            vision = settings.screenshots && ScreenshotTaker.supported && VisionSupport.supports(model),
        )
        // ATOMIC: the body runs even if Stop lands before it is first
        // dispatched, so the wrap-up in finally always happens -- otherwise the
        // state would stay "running", the island up and the ad skipper armed.
        val launched = scope.launch(start = CoroutineStart.ATOMIC) {
            var result: AgentRunResult = AgentRunResult.Failed("The device agent stopped unexpectedly.", retryable = true)
            try {
                result = run.run()
            } catch (e: CancellationException) {
                result = AgentRunResult.Failed("Stopped.", retryable = false)
            } catch (e: Exception) {
                result = AgentRunResult.Failed(e.message ?: "The device agent crashed.", retryable = true)
            } finally {
                finish(service, result, settings, origin, run)
            }
        }
        job = launched
        return launched
    }

    fun stop() {
        if (_state.value.running) cancelReason = CancelReason.USER
        pendingAnswer?.cancel(); pendingApproval?.complete(false)
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

    fun clear() {
        if (_state.value.running) return
        _state.update { State(serviceConnected = it.serviceConnected) }
    }

    // ------------------------------------------------------------- one run

    /**
     * Everything that belongs to one task: its phone, screens, notes and
     * counters. It talks to the phone only through [Phone] and to the model
     * only through [modelStream], so the whole run executes in JVM tests too.
     */
    private class DeviceRun(
        val phone: Phone,
        val task: String,
        val model: AiModel,
        val modelStream: AgentModelClient,
        val settings: Settings,
        val origin: Origin,
        /** Whether this model sees numbered screenshots (look). */
        val vision: Boolean,
    ) {
        val tools = DeviceTools.forRun(vision)
        val limited = settings.maxActions > 0
        var forceText = false
        var lastScreen: ScreenState? = null
        /** Uptime when [lastScreen] was captured: UI events after it mean the screen may have moved. */
        var lastCaptureUptime = 0L
        /** Set after any wait for the user: the next action starts from a new capture. */
        var mustRecapture = false
        /** The screen the model saw when it decided this turn's calls; indexes refer to it. */
        var turnScreen: ScreenState? = null
        /** The run has been in another app; from then on Oro in front means the user opened it. */
        var leftOro = false
        /** The last app the run worked in, to return to after the user answers in Oro. */
        var workApp: String? = null
        /** Uptime when the last call passed [gate]; an answer after it is "since the last action". */
        var lastGateAt = 0L
        val notes = mutableListOf<String>()
        /** The task picks among items, so a list's unseen part matters. */
        val comparing = DevicePrompt.comparesItems(task)
        /** The run has scrolled or read a page, or a pick from the first screenful was held once already. */
        var browsed = false
        /** The action being observed submitted a search (or a form). */
        var submitting = false
        /** What the last submitted search showed, apart from what was typed. */
        var lastResults: Int? = null
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
         * remaining actions (and a finish_task) were planned for a screen that
         * never came, so they are not run.
         */
        var turnBroken: String? = null
        /** Said before the next result: the user passed a human check first. */
        var handOffNote: String? = null

        fun observe(maxLabel: Int = ScreenReader.DEFAULT_MAX_LABEL): ScreenState {
            val startedAt = phone.uptime()
            val s = phone.capture(maxLabel)
            if (maxLabel == ScreenReader.DEFAULT_MAX_LABEL) {
                lastScreen = s
                lastCaptureUptime = startedAt
            }
            if (s.packageName.isNotEmpty() && s.packageName != phone.ownPackage) {
                leftOro = true
                workApp = s.packageName
            }
            _state.update { it.copy(currentApp = s.appLabel) }
            return s
        }

        /**
         * The screen as it is right before an action: the last capture if
         * nothing has happened on screen since (cheap: the service stamps every
         * UI event), else a new one. Acting on a snapshot taken before a pause,
         * an approval or a long think is how taps used to land on the wrong
         * thing.
         */
        fun freshScreen(): ScreenState {
            val s = lastScreen
            val reuse = s != null && !mustRecapture && phone.isLatest(s) &&
                phone.lastEventAt <= lastCaptureUptime &&
                phone.uptime() - lastCaptureUptime <= MAX_SNAPSHOT_AGE_MS
            if (reuse && s != null) return s
            mustRecapture = false
            return observe()
        }

        /**
         * Holds actions while the user has the run paused, or has Oro itself in
         * front: the agent never drives Oro's own screens. If the user just
         * answered a question in Oro, the run goes back to the app it was in.
         */
        suspend fun gate() {
            val since = lastGateAt
            var waited = false
            while (true) {
                if (paused.value) {
                    waited = true
                    _state.update { it.copy(status = "Paused") }
                    paused.first { !it }
                    continue
                }
                if (leftOro && phone.oroInFront()) {
                    waited = true
                    // The user answered (or approved) since the last action, most
                    // likely right here in Oro: take them back to the task's app.
                    val back = workApp
                    if (back != null && lastAnswerAt > since) {
                        lastAnswerAt = 0L
                        if (phone.bringToFront(back)) {
                            phone.settle(quietMs = 250, maxMs = 2_000)
                            continue
                        }
                    }
                    _state.update { it.copy(status = "Waiting while Oro is open") }
                    while (!paused.value && phone.oroInFront()) delay(500)
                    continue
                }
                break
            }
            if (waited) {
                mustRecapture = true
                _state.update { it.copy(status = "Continuing") }
            }
            lastGateAt = phone.uptime()
        }

        fun observation(before: Int?, s: ScreenState, maxChars: Int = 6_000): String = buildString {
            append(SCREEN_MARKER).append('\n')
            val skipped = adsSinceObservation.getAndSet(0)
            if (skipped > 0) append("(Oro skipped ").append(if (skipped == 1) "an ad" else "$skipped ads").append(" automatically.)\n")
            if (before != null) {
                if (before == s.signature) {
                    unchangedStreak++
                    append("⚠ The screen did not change after this action.")
                    if (unchangedStreak >= 2) {
                        append(" That is ").append(unchangedStreak).append(" in a row: stop repeating it. Try tap_text, the row ")
                        append("around it, scroll_to, wait_for, or press_key(\"back\").")
                    }
                    append('\n')
                } else {
                    unchangedStreak = 0
                    append("Screen changed.\n")
                }
            }
            // Only an action can bring the run back to a screen; reading or
            // waiting on the same screen is not going in circles.
            val seen = if (before != null) ((visits[s.signature] ?: 0) + 1).also { visits[s.signature] = it } else 0
            if (seen == 4 || seen == 8) {
                append("⚠ You have been on this exact screen ").append(seen).append(" times; you may be going in circles. Take a different route.\n")
            }
            for ((name, hits) in DevicePrompt.ambiguousNames(task, s)) {
                append("Hint: \"").append(name).append("\" matches ").append(hits.size).append(" items here (")
                    .append(hits.joinToString(", ")).append("). If the task does not say which, ask_user; don't guess.\n")
            }
            // A choice among items ("the cheapest", "the best rated") made from the
            // first screenful missed cheaper items below the fold in the live eval.
            if (comparing && s.elements.any { it.role == ScreenElement.ROLE_LIST && it.canScrollForward && !it.horizontal }) {
                append("Hint: this list has more below and your task compares items: read_page (or scroll) before choosing.\n")
            }
            // A new search that brought back the same results (only the query
            // changed): in the live eval a model searched five ways and never
            // looked below the fold, where the right video was.
            if (submitting) {
                submitting = false
                val results = s.elements.filter { !it.editable }.map { it.role + it.label }.hashCode()
                if (results == lastResults && s.elements.any { it.role == ScreenElement.ROLE_LIST && it.canScrollForward && !it.horizontal }) {
                    append("Hint: the same results as your last search. This list has more below: scroll (or read_page) to see the rest before searching again.\n")
                }
                lastResults = results
            }
            // Here rather than in the system prompt, which stays the same all run (prompt caching).
            if (limited) append("Actions left: ").append((settings.maxActions - actions).coerceAtLeast(0)).append('\n')
            if (notes.isNotEmpty()) append("Your notes: ").append(notes.joinToString(" | ")).append('\n')
            append(s.render(maxChars, adsHandled = settings.skipAds))
        }

        suspend fun run(): AgentRunResult {
            val nativeTools = model.supportsToolCalls

            // Start from the screen the task is about, not from Oro itself.
            phone.settle(quietMs = 250, maxMs = 1_200, minMs = 80)
            var first = observe()
            if (first.packageName == phone.ownPackage) {
                phone.global("home")
                phone.settle(quietMs = 250, maxMs = 1_500)
                first = observe()
            }
            turnScreen = first
            val conversation = mutableListOf(
                ChatRequestMessage(
                    role = "user",
                    content = "Task: $task\n\n${observation(null, first)}\n\nDecide the first step."
                )
            )

            val runner = AgentToolRunner { call ->
                val name = call.function.name.substringAfterLast('.').lowercase()
                val done = execute(call)
                if (name !in DeviceTools.passive && turnBroken == null) {
                    val text = if (done.success) done.output else (done.error ?: done.output)
                    if (!done.success || text.startsWith("Blocked")) turnBroken = text.substringBefore('\n').take(120)
                }
                val r = handOffNote?.let { note -> handOffNote = null; done.copy(output = note + "\n" + done.output) } ?: done
                if (name !in DeviceTools.passive && r.success) actions++
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
                        AgentStatus.Kind.THINKING -> "Looking at the screen"
                        AgentStatus.Kind.WRITING -> "Deciding"
                        else -> status.label
                    }
                    _state.update { it.copy(status = label, statusKind = status.kind) }
                }

                override fun onTurnComplete(visibleText: String, toolCalls: List<ToolCallResponse>) {
                    turnScreen = lastScreen
                    turnBroken = null
                    val thought = AgentLoop.stripThinking(visibleText).trim()
                    if (thought.isNotEmpty()) addStep(Step(nextId(), StepKind.THOUGHT, null, thought.take(400), status = StepStatus.DONE, endedAt = System.currentTimeMillis()))
                }

                override fun onToolStarted(call: ToolCallResponse) {
                    val name = call.function.name
                    if (name == "finish_task") return
                    val (title, kind) = describe(call, lastScreen)
                    val id = nextId()
                    currentStepId = id
                    addStep(Step(id, kind, name, title, app = lastScreen?.appLabel, status = if (kind == StepKind.QUESTION) StepStatus.WAITING else StepStatus.RUNNING))
                }

                override fun onToolFinished(call: ToolCallResponse, result: ToolResult) {
                    if (call.function.name == "finish_task") return
                    val text = if (result.success) result.output else (result.error ?: result.output)
                    val outcome = text.substringBefore(SCREEN_MARKER).trim().lineSequence().firstOrNull().orEmpty()
                    val unchanged = result.output.contains("did not change")
                    val blocked = text.startsWith("Blocked") || text.contains("user denied", true)
                    updateStep(currentStepId) { st ->
                        st.copy(
                            detail = if (st.kind == StepKind.QUESTION && st.status == StepStatus.DONE) st.detail else outcome.take(240),
                            status = when {
                                blocked -> StepStatus.BLOCKED
                                !result.success -> StepStatus.FAILED
                                else -> StepStatus.DONE
                            },
                            screenChanged = if (call.function.name in DeviceTools.passive) null else !unchanged,
                            endedAt = System.currentTimeMillis()
                        )
                    }
                }
            }

            val prepare: suspend (List<ChatRequestMessage>, Int) -> AgentRequest = { history, pressure ->
                val textMode = !nativeTools || forceText
                val system = ChatRequestMessage(
                    role = "system",
                    content = DevicePrompt.system(
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
                    // The device tools' own schemas win over the coding tools' (ask_user differs).
                    toolSchemas = com.oro.app.data.ai.tools.AgentTools.schemasOf(DeviceTools.all),
                    textResultsFooter = "Continue with the next step. When the task is done (or impossible), call finish_task; " +
                        "a plain reply does not end it.",
                    cutRepetition = true,
                )
            )
            // A reply with no tool call. finish_task written as text is a
            // finish; a question for the user written as text becomes a real
            // question card (nobody reads a plain reply as one); anything else
            // gets a reminder to call finish_task, so the user (or the
            // coding agent) gets a summary. A "done" claim alone is never
            // taken as done: it cannot be checked against the screen.
            var reminded = 0
            var asked = 0
            return loop.run(conversation, DeviceTools.names, listener, finalCheck = {
                val said = AgentLoop.stripThinking(conversation.lastOrNull { it.role == "assistant" }?.content.orEmpty())
                when (val kind = ProseTurn.read(said)) {
                    is ProseTurn.Finish -> {
                        finishSummary = kind.summary.ifBlank { null }
                        finishCalled = true
                        finishSuccess = kind.success
                        null
                    }
                    is ProseTurn.Ask -> if (asked >= 2) null else {
                        asked++
                        val repeated = repeatedQuestion(kind.question)
                        if (repeated != null) repeated.error + "\nContinue from the current screen, or call finish_task." else {
                            val id = nextId()
                            addStep(Step(id, StepKind.QUESTION, "ask_user", kind.question.take(400), app = lastScreen?.appLabel, status = StepStatus.WAITING))
                            val answer = askUser(kind.question, emptyList())
                            questions += kind.question to answer
                            mustRecapture = true
                            updateStep(id) { it.copy(detail = "You answered: $answer", status = StepStatus.DONE, endedAt = System.currentTimeMillis()) }
                            "The user answered: $answer\nContinue the task from the current screen. When it is done (or impossible), call finish_task."
                        }
                    }
                    // The first worked 8 times in 9 in the live eval; a model that
                    // answers it with another "done" in prose gets one plainer ask.
                    ProseTurn.Statement -> when (reminded++) {
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
         * the screen as it is now, guard exactly that target, and act on
         * exactly that target. A target that changed while the user decided
         * is skipped and the new screen returned, never acted on.
         */
        private suspend fun execute(call: ToolCallResponse): ToolResult {
            val c = phone
            val name = call.function.name.substringAfterLast('.').lowercase()
            val args = JsonParserUtils.parseToJsonObject(call.function.arguments) ?: JsonObject(emptyMap())

            if (limited && name !in DeviceTools.passive && actions >= settings.maxActions) {
                limitHit = true
                return ToolResult(false, "", "The action limit (${settings.maxActions}) is used up. Call finish_task with what you achieved and what is left.")
            }

            if (name !in DeviceTools.allowedWhilePaused) gate()

            // An earlier action in this turn did not work: the rest of the
            // turn was planned for a screen that never came.
            turnBroken?.let { why ->
                if (name !in DeviceTools.passive || name == "finish_task") {
                    return skipped("an earlier action in this turn did not work ($why), so this was not tried. Decide again from this screen.", freshScreen())
                }
            }

            readOnly(name, args)?.let { return it }
            if (name == "scroll" || name == "scroll_to" || name == "read_page") browsed = true
            if (name == "set_value" && DeviceArgs.str(args, "value")?.let(Sliders::parse) == null) {
                return ToolResult(false, "", "set_value needs value: a number in the slider's range (35), a percentage (\"40%\"), or a time on a media seek bar (\"1:30\").")
            }

            var screen = freshScreen()
            val seen = turnScreen

            // A human check is the user's to pass, never the agent's: wait for
            // them, then act on the screen that follows.
            if (name in HANDS_ON && screen.humanCheck) {
                handOff(null)
                gate()
                screen = freshScreen()
                if (screen.humanCheck) return skipped("the human check is still on screen.", screen)
            }

            if (name == "fill_form") return fillForm(args, seen, screen)

            var target = resolve(name, args, seen, screen)
            // A video's seek bar hides with the player's controls a few seconds
            // after the model saw it: set_value still reaches it (seekBarValue).
            val seekBar = if (name == "set_value") meantSeekBar(args, seen, target) else null
            if (seekBar == null) problem(target, screen)?.let { return it }
            unseenChoice(name, target, screen)?.let { return it }
            var dragTo = if (name == "drag") resolveDragEnd(args, seen, screen) else null
            if (name == "drag") problem(dragTo, screen)?.let { return it }

            // Safety guard on exactly what is about to be acted on.
            val sensitive = guard(name, args, target, screen)
            if (sensitive != null && settings.confirmSensitive) {
                if (!requestApproval(sensitive)) {
                    return ToolResult(true, "Blocked: the user denied \"$sensitive\". Do not retry it; choose another path or finish_task explaining what is left.")
                }
                // The user took a moment (maybe in Oro, maybe pausing first): act
                // only if the screen still shows what they approved.
                gate()
                mustRecapture = true
                val now = freshScreen()
                val approved = target
                if (approved != null) {
                    val again = resolve(name, args, seen, now)
                    if (again == null || !Targets.same(approved, again)) {
                        return skipped("the screen changed while you were deciding, so nothing was done. Decide again from this screen.", now)
                    }
                    target = again
                    if (name == "drag") {
                        dragTo = resolveDragEnd(args, seen, now)
                        problem(dragTo, now)?.let { return it }
                    }
                } else if (now.packageName != screen.packageName) {
                    return skipped("the app changed while you were deciding (now ${now.appLabel}), so nothing was done.", now)
                }
                screen = now
            }

            val before = screen.signature
            val actScreen = screen

            suspend fun act(maxSettle: Long = 2_500, expectChange: Boolean = true, block: suspend () -> String): ToolResult {
                val outcome = block()
                val didFail = failed(outcome)
                c.settle(maxMs = maxSettle)
                var after = observe()
                // Content still loading (spinner, empty window): give it a moment
                // here rather than spend a model turn on "wait".
                var extra = 0
                while ((after.loading || after.elements.isEmpty()) && extra < 3) {
                    delay(450)
                    after = observe()
                    extra++
                }
                // Media keys, intents and links often change nothing on screen by
                // design; "did not change" would only invite a duplicate alarm.
                val text = outcome + "\n\n" + observation(if (expectChange) before else null, after)
                return if (didFail) ToolResult(false, text, outcome) else ToolResult(true, text)
            }

            fun noTarget() = ToolResult(false, "", "Give an element index from the screen listing, or x and y.")

            // A point behind the open keyboard: a tap there presses a key (Send,
            // Enter), whatever the app shows underneath. Same rule as element taps.
            fun underKeyboard(x: Int, y: Int): ToolResult? = if (actScreen.underKeyboard(x, y)) {
                skipped("($x,$y) is under the keyboard. press_key(\"back\") closes the keyboard; then act there.", actScreen)
            } else null

            return when (name) {
                "tap", "tap_text" -> when (val t = target) {
                    is Target.OnElement -> act { c.tapElement(t.element, actScreen) }
                    // A finger lands wherever it lands: the app's own click event says what took it.
                    is Target.AtPoint -> underKeyboard(t.x, t.y) ?: act {
                        val at = c.uptime()
                        if (c.tap(t.x, t.y)) "Tapped (${t.x},${t.y}) ${TapFeedback.line(c.clickAfter(at, t.x, t.y))}."
                        else "The tap could not be performed."
                    }
                    else -> noTarget()
                }
                "long_press" -> when (val t = target) {
                    is Target.OnElement -> act { c.longPressElement(t.element, actScreen) }
                    is Target.AtPoint -> underKeyboard(t.x, t.y)
                        ?: act { if (c.longPress(t.x, t.y)) "Long-pressed (${t.x},${t.y})." else "The long press failed." }
                    else -> noTarget()
                }
                "double_tap" -> when (val t = target) {
                    is Target.OnElement -> act { c.doubleTapElement(t.element, actScreen) }
                    is Target.AtPoint -> underKeyboard(t.x, t.y)
                        ?: act { if (c.doubleTap(t.x, t.y)) "Double-tapped (${t.x},${t.y})." else "The double tap failed." }
                    else -> noTarget()
                }
                "type_text" -> {
                    val text = DeviceArgs.str(args, "text") ?: return ToolResult(false, "", "Missing text.")
                    val field = (target as? Target.OnElement)?.element
                    submitting = DeviceArgs.bool(args, "submit")
                    act {
                        val typed = c.type(field, actScreen, text, DeviceArgs.bool(args, "append"))
                        // Enter only after the text really went in: never send an old draft.
                        if (DeviceArgs.bool(args, "submit") && !failed(typed)) { delay(150); typed + " " + c.submit() } else typed
                    }
                }
                "press_key" -> {
                    val key = DeviceArgs.str(args, "key") ?: return ToolResult(false, "", "Missing key.")
                    submitting = key.equals("enter", true)
                    act { if (key.equals("enter", true)) c.submit() else c.global(key) }
                }
                "scroll" -> {
                    val dir = DeviceArgs.str(args, "direction") ?: "down"
                    // The list named, else the biggest one on the app's screen that
                    // scrolls this way (a carousel for left/right). The same element
                    // is checked and scrolled, so the answer is about the list moved.
                    val list = (target as? Target.OnElement)?.element ?: actScreen.listFor(dir)
                    // The list already says there is nothing more that way: say so
                    // now instead of swiping, settling and reporting "no change".
                    if (list?.canScroll(dir) == false) {
                        ToolResult(false, observation(null, actScreen), "Nothing more $dir: that list is at its end. Use what is on screen, or another list or screen.")
                    } else act {
                        c.scroll(dir, list, actScreen, (DeviceArgs.num(args, "amount") ?: 0.6).toFloat())
                    }
                }
                "scroll_to" -> {
                    val text = DeviceArgs.str(args, "text") ?: return ToolResult(false, "", "Missing text.")
                    scrollTo(text, DeviceArgs.str(args, "direction") ?: "down", (target as? Target.OnElement)?.element, actScreen)
                }
                "swipe" -> {
                    val x1 = DeviceArgs.int(args, "x1"); val y1 = DeviceArgs.int(args, "y1")
                    val x2 = DeviceArgs.int(args, "x2"); val y2 = DeviceArgs.int(args, "y2")
                    if (x1 == null || y1 == null || x2 == null || y2 == null) ToolResult(false, "", "swipe needs x1, y1, x2, y2.")
                    else underKeyboard(x1, y1)
                        ?: act { if (c.swipe(x1, y1, x2, y2, DeviceArgs.int(args, "duration_ms")?.toLong() ?: 350)) "Swiped ($x1,$y1)→($x2,$y2)." else "The swipe failed." }
                }
                "drag" -> {
                    val from = pointOf(target)
                    val to = pointOf(dragTo)
                    if (from == null || to == null) ToolResult(false, "", "drag needs from_index/to_index or x1,y1,x2,y2.")
                    else underKeyboard(from.first, from.second)
                        ?: act { if (c.drag(from.first, from.second, to.first, to.second)) "Dragged from $from to $to." else "The drag failed." }
                }
                "set_value" -> {
                    val raw = DeviceArgs.str(args, "value").orEmpty().trim()
                    val value = Sliders.parse(raw) ?: return ToolResult(false, "", "Missing value.")
                    val bar = seekBar
                    // A seek through the media session changes nothing on screen by design.
                    if (bar != null) act(expectChange = false) { seekBarValue(bar, target, value, actScreen) }
                    else when (val t = target) {
                        is Target.OnElement -> act { c.setValue(t.element, actScreen, raw) }
                        else -> ToolResult(false, "", "Give the slider's element number from the screen listing.")
                    }
                }
                "pinch" -> {
                    val x = DeviceArgs.int(args, "x") ?: screen.width / 2
                    val y = DeviceArgs.int(args, "y") ?: screen.height / 2
                    val dir = DeviceArgs.str(args, "direction")
                    act { if (c.pinch(x, y, dir == "out")) "Pinched $dir at ($x,$y)." else "The pinch failed." }
                }
                "open_app" -> {
                    val app = DeviceArgs.str(args, "name") ?: DeviceArgs.str(args, "app") ?: return ToolResult(false, "", "Missing app name.")
                    act(maxSettle = 4_000) { c.openApp(app) }
                }
                "open_url" -> {
                    val url = DeviceArgs.str(args, "url") ?: return ToolResult(false, "", "Missing url.")
                    act(maxSettle = 4_000, expectChange = false) { c.openUrl(url).also { delay(500) } }
                }
                "quick_action" -> {
                    val action = DeviceArgs.str(args, "action") ?: return ToolResult(false, "", "Missing action. Use one of: ${QuickActions.ACTIONS.joinToString()}.")
                    val qa = QuickActions.Args(
                        action = action, text = DeviceArgs.str(args, "text") ?: DeviceArgs.str(args, "query"), to = DeviceArgs.str(args, "to"),
                        time = DeviceArgs.str(args, "time"), minutes = DeviceArgs.num(args, "minutes"), page = DeviceArgs.str(args, "page"),
                        subject = DeviceArgs.str(args, "subject"),
                    )
                    act(maxSettle = 3_000, expectChange = false) { phone.quickAction(qa).also { delay(300) } }
                }
                "media" -> {
                    val action = DeviceArgs.str(args, "action") ?: return ToolResult(false, "", "Missing action.")
                    if (action.trim().equals("seek", ignoreCase = true)) {
                        val position = (DeviceArgs.str(args, "position") ?: DeviceArgs.str(args, "value")).orEmpty().trim()
                        val seek = MediaControl.parsePosition(position) ?: return ToolResult(
                            false, "",
                            "seek needs position: \"1:30\" or \"90\" (from the start), \"+30\" or \"-10\" (a jump), or \"50%\" (of the length)."
                        )
                        act(expectChange = false) { seekPlayer(position, seek, actScreen) }
                    } else act(expectChange = false) { c.media(action) }
                }
                "read_page" -> readPage((DeviceArgs.int(args, "pages") ?: 3).coerceIn(1, 8), before)
                "wait" -> {
                    val s = (DeviceArgs.num(args, "seconds") ?: 2.0).coerceIn(0.5, 10.0)
                    act(expectChange = false) { delay((s * 1000).toLong()); "Waited ${"%.1f".format(s)}s." }
                }
                else -> ToolResult(false, "", "Unknown tool \"$name\". Available: ${tools.joinToString { it.function.name }}.")
            }
        }

        /** Tools that only read, note or talk: no target, no guard. Null for everything else. */
        private suspend fun readOnly(name: String, args: JsonObject): ToolResult? = when (name) {
            "list_apps" -> {
                val q = DeviceArgs.str(args, "query")?.lowercase()
                val apps = phone.apps()
                    .filter { q.isNullOrBlank() || it.label.lowercase().contains(q) || it.packageName.contains(q) }
                ToolResult(true, if (apps.isEmpty()) "No apps match." else apps.take(120).joinToString("\n") { "${it.label} (${it.packageName})" })
            }
            "device_status" -> ToolResult(true, phone.status())
            "take_note" -> {
                val note = DeviceArgs.str(args, "note")?.trim().orEmpty()
                if (note.isEmpty()) ToolResult(false, "", "Empty note.") else {
                    notes += note.take(300)
                    while (notes.size > 16) notes.removeAt(0)
                    ToolResult(true, "Noted.")
                }
            }
            "ask_user" -> {
                val q = DeviceArgs.str(args, "question")
                if (q == null) ToolResult(false, "", "Missing question.")
                else if (lastScreen?.humanCheck == true) handOff(currentStepId)
                else repeatedQuestion(q) ?: run {
                    val options = (args["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
                    val answer = askUser(q, options)
                    questions += q to answer
                    mustRecapture = true
                    updateStep(currentStepId) { it.copy(detail = "You answered: $answer", status = StepStatus.DONE, endedAt = System.currentTimeMillis()) }
                    ToolResult(true, "The user answered: $answer")
                }
            }
            "finish_task" -> {
                val summary = DeviceArgs.str(args, "summary").orEmpty()
                val ok = (args["success"] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.trim()?.toBooleanStrictOrNull() } ?: true
                finishSummary = summary.ifBlank { null }
                finishCalled = true
                finishSuccess = ok
                ToolResult(true, summary)
            }
            "find_text" -> {
                val q = DeviceArgs.str(args, "text").orEmpty()
                val now = observe()
                val hits = TextMatch.hits(q, now).filter { it.score >= TextMatch.ACT_THRESHOLD }
                ToolResult(true, if (hits.isEmpty()) "\"$q\" is not on the current screen. Try scroll_to(\"$q\")."
                else "Found ${hits.size}:\n" + hits.take(12).joinToString("\n") { it.element.render() })
            }
            "read_screen" -> {
                phone.settle(quietMs = 250, maxMs = 1_500, minMs = 80)
                val now = observe()
                ToolResult(true, "Current screen.\n\n" + observation(null, now))
            }
            "look" -> {
                if (!vision) ToolResult(false, "", "Screenshots are not available for this model or phone. Use the element listing.")
                else {
                    val now = observe()
                    val image = phone.screenshot(now)
                    if (image == null) ToolResult(false, observation(null, now), "The screenshot failed; this app may block screenshots. Use the listing.")
                    else ToolResult(true, "Screenshot attached; the numbers on it match the listing.\n\n" + observation(null, now), images = listOf(image))
                }
            }
            "wait_for" -> {
                val text = DeviceArgs.str(args, "text")
                if (text == null) ToolResult(false, "", "Missing text.")
                else waitFor(
                    text, DeviceArgs.bool(args, "gone"),
                    ((DeviceArgs.num(args, "seconds") ?: 10.0).coerceIn(1.0, 30.0) * 1000).toLong(),
                    lastScreen?.signature ?: 0,
                )
            }
            else -> null
        }

        /**
         * Hands a human check to the user: the island asks them to pass it and
         * the run carries on by itself once it is gone (or when they answer).
         * The step and a note record that the user passed it. [stepId] is the
         * question card already on screen, if the model asked; else one is added.
         */
        private suspend fun handOff(stepId: Long?): ToolResult {
            val question = "Pass the check on screen"
            val id = stepId ?: nextId().also {
                addStep(Step(it, StepKind.QUESTION, "ask_user", "Human check", app = lastScreen?.appLabel, status = StepStatus.WAITING))
            }
            val answer = askUntil(question, listOf("Done")) { !phone.capture().humanCheck }
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
         * fill_form: every field from one capture -- typed, checked or picked --
         * then (submit) the form's own button. One approval covers the whole
         * form when anything in it is sensitive. Fields not on screen are
         * looked for further down, a few scrolls at most. A field whose name
         * fits two boxes is reported, never guessed.
         */
        private suspend fun fillForm(args: JsonObject, seen: ScreenState?, start: ScreenState): ToolResult {
            val entries = FormFill.entries(args["fields"])
            if (entries.isEmpty()) {
                return ToolResult(false, "", "fill_form needs fields: [{\"field\": \"Email\", \"value\": \"...\"}, ...] (field = the label or hint; or index).")
            }
            val before = start.signature
            var screen = start
            val report = arrayOfNulls<String>(entries.size)
            val done = BooleanArray(entries.size)
            var approved = false
            var scrolls = 0
            /** Filled already: never matched again after the screen is read anew. */
            val filled = ArrayList<ScreenElement>()
            fun label(i: Int) = entries[i].field ?: "[${entries[i].index}]"

            while (true) {
                val used = screen.elements
                    .filter { e -> filled.any { f -> f.role == e.role && f.label == e.label && f.bounds == e.bounds } }
                    .mapTo(HashSet()) { it.index }
                val plan = ArrayList<Pair<Int, ScreenElement>>()
                for (i in entries.indices) {
                    if (done[i]) continue
                    val entry = entries[i]
                    val element: ScreenElement? = if (entry.index != null) {
                        val (match, stale) = if (seen != null && screen !== seen) ElementRemap.resolve(seen, screen, entry.index) else screen.element(entry.index) to false
                        if (stale) { report[i] = "${label(i)}: changed since you saw it, not filled"; done[i] = true }
                        match
                    } else when (val m = FormFill.target(entry, screen, used)) {
                        is FormFill.Match.Found -> m.element
                        is FormFill.Match.Ambiguous -> {
                            report[i] = "${label(i)}: fits ${m.candidates.size} fields (" + m.candidates.joinToString { "[${it.index}]" } + "); give its index"
                            done[i] = true
                            null
                        }
                        FormFill.Match.Missing -> null
                    }
                    if (element != null && !done[i]) { used += element.index; plan += i to element }
                }

                // One OK for the whole form when anything in it is sensitive.
                if (!approved && settings.confirmSensitive) {
                    val reasons = plan.mapNotNull { (i, e) ->
                        if (FormFill.kindOf(e) == FormFill.Kind.TEXT) SensitiveGuard.type(e, entries[i].value, false, screen) else null
                    }.distinct()
                    if (reasons.isNotEmpty()) {
                        if (!requestApproval("Fill the form: " + reasons.joinToString("; "))) {
                            return ToolResult(true, "Blocked: the user denied filling this form. Do not retry it; choose another path or finish_task explaining what is left.")
                        }
                        gate()
                        mustRecapture = true
                        approved = true
                        val now = freshScreen()
                        if (now.signature != screen.signature) { screen = now; continue }
                    }
                    approved = true
                }

                var moved = false
                for ((i, e) in plan) {
                    val entry = entries[i]
                    val outcome: String = when (FormFill.kindOf(e)) {
                        FormFill.Kind.TEXT -> {
                            val typed = phone.type(e, screen, entry.value, append = false)
                            if (failed(typed)) "not filled ($typed)" else if (e.password) "filled (hidden)" else "filled"
                        }
                        FormFill.Kind.TOGGLE -> {
                            val want = FormFill.wantChecked(entry.value) ?: true
                            if (e.checked == want) (if (want) "already checked" else "already off")
                            else {
                                val tapped = phone.tapElement(e, screen)
                                if (failed(tapped)) "not changed ($tapped)" else if (want) "checked" else "unchecked"
                            }
                        }
                        FormFill.Kind.CHOICE -> {
                            // A picker: open it, take the option, and read the screen again.
                            val opened = phone.tapElement(e, screen)
                            moved = true
                            if (failed(opened)) "could not open ($opened)" else {
                                phone.settle(maxMs = 1_500)
                                var list = observe()
                                var pick = TextMatch.hits(entry.value, list).firstOrNull { it.score >= FormFill.OPTION_MIN && it.element.index != e.index }
                                var tries = 0
                                while (pick == null && tries < 3) {
                                    val scroller = list.listFor("down") ?: break
                                    if (scroller.canScroll("down") == false) break
                                    phone.scroll("down", scroller, list)
                                    phone.settle(maxMs = 1_000)
                                    list = observe()
                                    pick = TextMatch.hits(entry.value, list).firstOrNull { it.score >= FormFill.OPTION_MIN && it.element.index != e.index }
                                    tries++
                                }
                                val option = pick?.element
                                if (option == null) {
                                    phone.global("back")
                                    "no option \"${entry.value.take(40)}\""
                                } else {
                                    val chose = phone.tapElement(option, list)
                                    if (failed(chose)) "could not choose \"${entry.value.take(40)}\"" else "chose \"${option.label.take(40)}\""
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
                    // A picker changed the screen: the rest are found again on the new one.
                    phone.settle(maxMs = 1_500)
                    screen = observe()
                    continue
                }
                if (done.all { it }) break
                // Fields not on screen yet: further down the form, a few scrolls at most.
                val list = screen.listFor("down")
                if (scrolls >= MAX_FORM_SCROLLS || list == null || list.canScroll("down") == false) break
                phone.scroll("down", list, screen)
                scrolls++
                phone.settle(maxMs = 1_200)
                screen = observe()
            }
            for (i in entries.indices) if (report[i] == null) report[i] = "${label(i)}: not on screen"

            var submitted = ""
            val allFilled = report.all { r -> r != null && FILLED.containsMatchIn(r.substringAfter(": ")) }
            if (DeviceArgs.bool(args, "submit")) {
                phone.settle(maxMs = 1_000)
                val now = observe()
                val button = FormFill.submitButton(now)
                submitted = when {
                    !allFilled -> "\nNot submitted: some fields were not filled."
                    button == null -> "\nNot submitted: no submit button on screen; tap it yourself."
                    else -> {
                        val target = Target.OnElement(button, button)
                        val sensitive = SensitiveGuard.tap(target, now)
                        if (sensitive != null && settings.confirmSensitive && !requestApproval(sensitive)) {
                            "\nNot submitted: the user denied \"$sensitive\"."
                        } else {
                            if (sensitive != null) { gate(); mustRecapture = true }
                            val pressed = if (sensitive != null) {
                                val again = freshScreen()
                                val b = FormFill.submitButton(again)
                                if (b == null || !Targets.same(target, Target.OnElement(b, b))) null else phone.tapElement(b, again)
                            } else phone.tapElement(button, now)
                            if (pressed == null) "\nNot submitted: the screen changed while you decided." else "\nSubmitted: $pressed"
                        }
                    }
                }
            }
            phone.settle(maxMs = 2_000)
            val after = observe()
            val text = "Form: " + report.joinToString("; ") + submitted + "\n\n" + observation(before, after)
            return if (allFilled) ToolResult(true, text) else ToolResult(false, text, "Form: " + report.joinToString("; "))
        }

        /** The target of a targeted tool, resolved against [now]; null for tools without one. */
        private fun resolve(name: String, args: JsonObject, seen: ScreenState?, now: ScreenState): Target? = when (name) {
            "tap", "double_tap" -> Targets.tap(args, seen, now)
            "long_press" -> Targets.tap(args, seen, now, long = true)
            "tap_text" -> Targets.text(args, seen, now)
            "type_text" -> Targets.field(args, seen, now)
            "scroll", "scroll_to" -> Targets.optional(args, "index", seen, now)
            "swipe" -> Targets.start(args, "x1", "y1", seen, now)
            "drag" -> Targets.optional(args, "from_index", seen, now) ?: Targets.start(args, "x1", "y1", seen, now)
            "set_value" -> Targets.slider(args, seen, now)
            else -> null
        }

        private fun resolveDragEnd(args: JsonObject, seen: ScreenState?, now: ScreenState): Target? =
            Targets.optional(args, "to_index", seen, now) ?: Targets.start(args, "x2", "y2", seen, now)

        /** What the guard judges: the resolved target, or the call itself for keys and links. */
        private fun guard(name: String, args: JsonObject, target: Target?, screen: ScreenState): String? = when (name) {
            "tap", "tap_text" -> SensitiveGuard.tap(target, screen)
            "double_tap" -> SensitiveGuard.tap(target, screen, verb = "double-tap")
            "long_press" -> SensitiveGuard.tap(target, screen, verb = "press and hold")
            "type_text" -> {
                val named = (target as? Target.OnElement)?.element ?: (target as? Target.Focused)?.field
                // An index on the field's container still types into the field inside it.
                val field = named?.takeIf { it.editable }
                    ?: named?.let { box -> screen.elements.firstOrNull { it.editable && box.bounds.contains(it.centerX, it.centerY) } }
                    ?: named
                SensitiveGuard.type(field, DeviceArgs.str(args, "text"), DeviceArgs.bool(args, "submit"), screen)
            }
            "press_key" -> SensitiveGuard.key(DeviceArgs.str(args, "key"), screen)
            "open_url" -> SensitiveGuard.url(DeviceArgs.str(args, "url"))
            // "Slide to pay" moved by its value is still a slide.
            "swipe", "drag", "set_value" -> SensitiveGuard.slide(target, screen)
            else -> null
        }

        /** The answer for a target nothing can be done with, or null. */
        private fun problem(t: Target?, now: ScreenState): ToolResult? = when (t) {
            is Target.Stale -> skipped(
                if (t.appChanged) "the app changed since you chose that (now ${now.appLabel}). Decide again from this screen."
                else "that element changed or left the screen since you chose it. Decide again from this screen.",
                now
            )
            is Target.Missing -> ToolResult(false, "", t.message)
            is Target.Choices -> ToolResult(
                false,
                t.candidates.joinToString("\n") { it.render() },
                if (t.weak) "Nothing clearly shows \"${t.query}\". Closest elements below; tap one by index if it is right."
                else "Several elements show \"${t.query}\". Tap one by index, or pass nth (1 = first)."
            )
            is Target.NotFound -> ToolResult(
                false, "",
                "\"${t.query}\" is not on the screen. If it is further down use scroll_to(\"${t.query}\"); otherwise read the listing for the exact label." +
                    (lastScreen?.let { settingsShortcut(it) }?.let { " $it" } ?: "")
            )
            else -> null
        }

        private fun skipped(why: String, now: ScreenState) = ToolResult(false, observation(null, now), "Skipped: $why")

        /**
         * The same question a third time: a weak model asked for a phone
         * number eight times in the live eval, the user answering each time.
         * The answers so far come back instead of another card.
         */
        fun repeatedQuestion(q: String): ToolResult? {
            val before = questions.filter { DevicePrompt.sameQuestion(it.first, q) }
            if (before.size < 2) return null
            return ToolResult(
                false, "",
                "Not asked again: you asked this ${before.size} times and the user answered " +
                    before.joinToString(", then ") { "\"${it.second}\"" } + ". Work with that: the app may have what you " +
                    "need (open it and look), or call finish_task(success=false) saying what is missing."
            )
        }

        /**
         * A pick among items ("the cheapest") tapped from the first screenful
         * of a list that goes on below: held once, so the rest gets read. Two
         * models in the live eval ignored the hint and added the cheapest
         * *visible* charger to the cart. Tapping again goes through.
         */
        private fun unseenChoice(name: String, target: Target?, screen: ScreenState): ToolResult? {
            if (!comparing || browsed || (name != "tap" && name != "tap_text")) return null
            val e = (target as? Target.OnElement)?.element ?: return null
            if (e.editable || e.role == ScreenElement.ROLE_LIST || CONTROLS.containsMatchIn(e.label)) return null
            screen.elements.firstOrNull {
                it.role == ScreenElement.ROLE_LIST && !it.horizontal && it.canScrollForward && it.bounds.contains(e.centerX, e.centerY)
            } ?: return null
            browsed = true
            return ToolResult(
                false, observation(null, screen),
                "Not tapped yet: your task compares items and this list goes on below, unseen. read_page (or scroll down) " +
                    "first, then tap the one you chose."
            )
        }

        private fun pointOf(t: Target?): Pair<Int, Int>? = when (t) {
            is Target.OnElement -> t.element.centerX to t.element.centerY
            is Target.AtPoint -> t.x to t.y
            else -> null
        }

        /**
         * The media seek bar a set_value names, if it names one: the element
         * it resolved to, or -- gone stale because the player's controls hid
         * -- the one the model saw. A slider given a time ("1:30") is one.
         * Null for any other slider.
         */
        private fun meantSeekBar(args: JsonObject, seen: ScreenState?, target: Target?): ScreenElement? {
            val e = (target as? Target.OnElement)?.element
                ?: if (target is Target.Stale && !target.appChanged) DeviceArgs.int(args, "index")?.let { seen?.element(it) } else null
            val time = DeviceArgs.str(args, "value")?.let(Sliders::parse) is Sliders.Value.Time
            return e?.takeIf { Sliders.isSeekBar(it) || (time && it.role == ScreenElement.ROLE_SLIDER) }
        }

        /**
         * set_value on a media seek bar. The app's media session seeks exactly
         * and needs no bar, so it goes first. Else the bar itself: the one named
         * if it is still there, else found again after one tap on the player
         * -- all in this one call, with no turn in between.
         */
        private suspend fun seekBarValue(meant: ScreenElement, target: Target?, value: Sliders.Value, screen: ScreenState): String {
            val exact = phone.seekMedia(Sliders.mediaPosition(value, meant.range), screen.packageName)
            if (exact is MediaControl.SeekResult.Done) return exact.text
            val hint = if (exact is MediaControl.SeekResult.NoAccess) " " + MediaControl.ACCESS_HINT else ""
            val named = (target as? Target.OnElement)?.element
            val (bar, shown) = (if (named != null) named to screen else revealSeekBar(screen))
                ?: return "Skipped: the seek bar is hidden, and one tap on the player did not bring it back.$hint"
            return phone.setValue(bar, shown, Sliders.barValue(value, bar.range), Sliders.clock(shown, bar).second) + hint
        }

        /**
         * media(seek): the session's own seekTo where Notification access
         * allows (exact, nothing on screen needed); else the player's seek bar,
         * shown again first if its controls hid; else, for a jump of about ten
         * seconds, the player's own double tap.
         */
        private suspend fun seekPlayer(position: String, seek: MediaControl.Seek, screen: ScreenState): String {
            val exact = phone.seekMedia(position, null)
            if (exact is MediaControl.SeekResult.Done) return exact.text
            val hint = if (exact is MediaControl.SeekResult.NoAccess) " " + MediaControl.ACCESS_HINT else ""
            revealSeekBar(screen)?.let { (bar, shown) ->
                val (now, length) = Sliders.clock(shown, bar)
                Sliders.barValue(seek, now)?.let { value -> return phone.setValue(bar, shown, value, length) + hint }
            }
            if (seek is MediaControl.Seek.By && abs(seek.ms) in 5_000L..15_000L) {
                return phone.media(if (seek.ms > 0) "forward" else "rewind")
            }
            val why = (exact as? MediaControl.SeekResult.Unavailable)?.why ?: "Media control is off"
            return "Could not seek: $why, and no seek bar on screen shows where it is.$hint"
        }

        /**
         * The player's seek bar, its controls shown: a player hides them a few
         * seconds after the last touch, so when the bar is not on [screen] one
         * tap on the player brings them back, and after a moment to fade in the
         * bar is found on a new capture. The pause still holds in between.
         */
        private suspend fun revealSeekBar(screen: ScreenState): Pair<ScreenElement, ScreenState>? {
            Sliders.seekBar(screen)?.let { return it to screen }
            if (!phone.showPlayerControls()) return null
            phone.settle(quietMs = 150, maxMs = 1_000, minMs = 300)
            gate()
            val shown = observe()
            return Sliders.seekBar(shown)?.let { it to shown }
        }

        private suspend fun scrollTo(text: String, direction: String, list: ScreenElement?, start: ScreenState): ToolResult {
            var cur = start
            var scrolls = 0
            var hit = TextMatch.hits(text, cur).firstOrNull { it.score >= TextMatch.ACT_THRESHOLD }
            var atEnd = false
            while (hit == null && scrolls < 10) {
                val before = cur.signature
                val target = list?.let { l -> cur.elements.firstOrNull { it.role == l.role && it.bounds == l.bounds } }
                    ?: cur.listFor(direction)
                // The list says there is nothing more this way: done, without a wasted scroll.
                if (target?.canScroll(direction) == false) { atEnd = true; break }
                phone.scroll(direction, target, cur, 0.7f)
                phone.settle(quietMs = 250, maxMs = 1_500, minMs = 100)
                cur = observe()
                scrolls++
                if (cur.signature == before) { atEnd = true; break }
                hit = TextMatch.hits(text, cur).firstOrNull { it.score >= TextMatch.ACT_THRESHOLD }
            }
            val obs = observation(start.signature, cur)
            return when {
                hit != null -> ToolResult(true, "Found \"$text\" as [${hit.element.index}] after $scrolls scroll${if (scrolls == 1) "" else "s"}.\n\n$obs")
                atEnd -> ToolResult(false, obs, "Reached the end after $scrolls scroll${if (scrolls == 1) "" else "s"}; \"$text\" is not in this list. " +
                    (settingsShortcut(cur) ?: "Try another direction, a search box, or another screen."))
                else -> ToolResult(false, obs, "Scrolled 10 times without finding \"$text\". It may be elsewhere; consider searching for it.")
            }
        }

        private suspend fun readPage(pages: Int, startSignature: Int): ToolResult {
            val seen = LinkedHashSet<String>()
            var read = 0
            var full = observe(maxLabel = 1_500)
            while (true) {
                full.elements.forEach { e -> if (e.label.isNotBlank() && !e.editable && e.role != ScreenElement.ROLE_PROGRESS) seen += e.label }
                read++
                if (read >= pages) break
                val before = full.signature
                phone.scroll("down", null, null, 0.8f)
                phone.settle(quietMs = 250, maxMs = 1_500, minMs = 100)
                full = observe(maxLabel = 1_500)
                if (full.signature == before) break
            }
            val body = seen.joinToString("\n").let { if (it.length > 10_000) it.take(10_000) + "\n…" else it }
            val now = observe()
            return ToolResult(true, "Page text ($read screen${if (read == 1) "" else "s"}):\n$body\n\n" + observation(startSignature, now, maxChars = 2_500))
        }

        private suspend fun waitFor(text: String, gone: Boolean, timeoutMs: Long, startSignature: Int): ToolResult {
            val start = phone.uptime()
            // Cheap check while waiting (the platform's own text search), full capture once at the end.
            while (phone.textVisible(text) == gone && phone.uptime() - start < timeoutMs) {
                delay(400)
            }
            phone.settle(quietMs = 200, maxMs = 1_000, minMs = 50)
            val now = observe()
            val present = TextMatch.hits(text, now).any { it.score >= TextMatch.ACT_THRESHOLD } || phone.textVisible(text)
            val ok = present != gone
            val waited = "%.1f".format((phone.uptime() - start) / 1000.0)
            val obs = observation(startSignature, now)
            return if (ok) ToolResult(true, (if (gone) "\"$text\" is gone after ${waited}s." else "\"$text\" appeared after ${waited}s.") + "\n\n" + obs)
            else ToolResult(false, obs, "Waited ${timeoutMs / 1000}s; \"$text\" ${if (gone) "is still there" else "did not appear"}.")
        }
    }

    // ------------------------------------------------------ interactions

    private suspend fun requestApproval(what: String): Boolean {
        val d = CompletableDeferred<Boolean>()
        pendingApproval = d
        val stepId = nextId()
        addStep(Step(stepId, StepKind.APPROVAL, null, "Needs your OK: $what", status = StepStatus.WAITING))
        _state.update { it.copy(approval = what, status = "Waiting for your approval") }
        val overlay = OroAccessibilityService.instance.value?.overlay
        overlay?.askApproval("Allow Orobi to $what?")
        val allowed = try {
            withTimeoutOrNull(180_000) { d.await() } ?: false
        } finally {
            overlay?.clearApproval()
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

    /**
     * Waits for the user's answer. The floating control shows the question
     * and takes the answer in place, so the app the agent is working in
     * stays on screen; Oro is opened only if the user taps through to it.
     */
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

    private suspend fun askUser(question: String, options: List<String>): String {
        val d = CompletableDeferred<String>()
        pendingAnswer = d
        _state.update { it.copy(question = Question(question, options), status = "Waiting for your answer") }
        return try {
            d.await()
        } finally {
            pendingAnswer = null
            _state.update { it.copy(question = null, status = "Continuing") }
        }
    }

    /**
     * Ends a run, whatever ended it: one state update (so a new run can start
     * the moment it is published), the ad skipper off, and a separate
     * wrap-up -- hide the island after a beat, come back to Oro -- that a new
     * run cancels, so a stale wrap-up never hides the next run's island.
     */
    private fun finish(service: OroAccessibilityService, result: AgentRunResult, settings: Settings, origin: Origin, run: DeviceRun) {
        val wrapUp = scope.launch(start = CoroutineStart.LAZY) {
            delay(if (origin == Origin.CHAT) 1_800 else 2_500)
            service.overlay.hideAll()
            when {
                // The coding agent is waiting in the chat: always go back to it.
                origin == Origin.CHAT -> bringOroToFront(openDeviceScreen = false)
                settings.returnToOro -> bringOroToFront(openDeviceScreen = true)
            }
        }
        wrapUpJob = wrapUp
        publishEnd(result, run)
        wrapUp.start()
    }

    /** The one state update that ends a run: why it ended, its summary or error, and the last step closed. */
    private fun publishEnd(result: AgentRunResult, run: DeviceRun) {
        adSkipActive = false
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
                question = null, approval = null,
                success = reason == EndReason.DONE,
                summary = when (reason) {
                    EndReason.DONE -> run.finishSummary ?: finalText.ifBlank { "Done." }
                    EndReason.UNCONFIRMED -> finalText.ifBlank { null }
                    else -> run.finishSummary
                },
                error = when (reason) {
                    EndReason.DONE, EndReason.UNCONFIRMED, EndReason.FAILED -> null
                    EndReason.STOPPED_BY_USER -> "Stopped by you."
                    EndReason.SERVICE_LOST -> "The Oro accessibility service was turned off, so the task stopped."
                    EndReason.ACTION_LIMIT -> "Stopped after ${s.actions} actions (the limit). You can change the limit in settings."
                    EndReason.ERROR -> (result as? AgentRunResult.Failed)?.message ?: "The device agent stopped."
                },
                status = when (reason) {
                    EndReason.DONE -> "Done"
                    EndReason.UNCONFIRMED -> "Ended"
                    EndReason.FAILED -> "Couldn't finish"
                    EndReason.ACTION_LIMIT -> "Action limit reached"
                    EndReason.STOPPED_BY_USER, EndReason.SERVICE_LOST, EndReason.ERROR -> "Stopped"
                },
            )
        }
    }

    /**
     * Runs one task on [phone], with [client] standing in for the model, in
     * the caller's coroutine. The scenario bench (app/src/test) drives the
     * real run loop -- observation, target resolution, the guard, stuck
     * signals, endings -- through this; there is no overlay and no wrap-up.
     */
    internal suspend fun runScenario(
        phone: Phone,
        task: String,
        model: AiModel,
        client: AgentModelClient,
        settings: Settings = Settings(),
        vision: Boolean = false,
    ): State {
        check(!_state.value.running) { "a run is already live" }
        paused.value = false
        cancelReason = null
        lastAnswerAt = 0L
        stepSeq.set(0)
        adsSinceObservation.set(0)
        adSkipActive = settings.skipAds
        _state.value = State(
            serviceConnected = true, running = true, task = task.trim(), modelName = model.name,
            status = "Starting", maxActions = settings.maxActions, startedAt = System.currentTimeMillis(),
        )
        val run = DeviceRun(phone, task.trim(), model, client, settings, Origin.USER, vision)
        var result: AgentRunResult = AgentRunResult.Failed("The device agent stopped unexpectedly.", retryable = true)
        try {
            result = run.run()
        } catch (e: CancellationException) {
            result = AgentRunResult.Failed("Stopped.", retryable = false)
            throw e
        } catch (e: Exception) {
            result = AgentRunResult.Failed(e.message ?: "The device agent crashed.", retryable = true)
        } finally {
            publishEnd(result, run)
        }
        return _state.value
    }

    /**
     * Why a run ended. Pure, for tests. Who cancelled wins (Stop, service
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
        cancelled == CancelReason.SERVICE_LOST -> EndReason.SERVICE_LOST
        cancelled == CancelReason.USER -> EndReason.STOPPED_BY_USER
        result is AgentRunResult.IterationLimit -> EndReason.ACTION_LIMIT
        result is AgentRunResult.Failed -> if (limitHit) EndReason.ACTION_LIMIT else EndReason.ERROR
        finishCalled && finishSuccess != false -> EndReason.DONE
        limitHit -> EndReason.ACTION_LIMIT
        finishCalled -> EndReason.FAILED
        else -> EndReason.UNCONFIRMED
    }

    private fun bringOroToFront(openDeviceScreen: Boolean) {
        val ctx = appContext ?: return
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (openDeviceScreen) intent.putExtra(EXTRA_OPEN_DEVICE_AGENT, true)
        runCatching { ctx.startActivity(intent) }
    }

    const val EXTRA_OPEN_DEVICE_AGENT = "com.oro.app.OPEN_DEVICE_AGENT"

    // ------------------------------------------------------------ helpers

    internal const val SCREEN_MARKER = "=== SCREEN ==="

    /** Outcome sentences that mean the action did not happen. */
    private val FAILED = Regex("^(No |Unknown |Skipped|Refused)|could not|failed|^\\w+ needs |not supported|rejected|refused", RegexOption.IGNORE_CASE)
    private val QUOTED = Regex("\"[^\"]*\"|“[^”]*”")

    /** Judges an outcome sentence, not the labels quoted in it ("Tapped “Failed orders”" succeeded). */
    /** fill_form outcomes that count as done. */
    private val FILLED = Regex("^(filled|checked|unchecked|already|chose)")
    private const val MAX_FORM_SCROLLS = 3
    /** The answer [askUntil] gives itself when the reason for asking is gone. */
    private const val CLEARED = "\u0000cleared"
    /** Actions on the screen's content: they wait while a human check is up (navigation does not). */
    private val HANDS_ON = setOf(
        "tap", "tap_text", "type_text", "fill_form", "long_press", "double_tap", "swipe", "drag", "pinch", "scroll", "scroll_to",
        "set_value",
    )

    private fun failed(outcome: String): Boolean = FAILED.containsMatchIn(outcome.replace(QUOTED, ""))

    /** Oldest capture an action may reuse even when no UI event arrived since (some views never send any). */
    private const val MAX_SNAPSHOT_AGE_MS = 4_000L

    /** Labels of list controls rather than items: tapping these is how a list gets compared. */
    private val CONTROLS = Regex("""\b(sort|filter|filters|refine|search|show more|see all|view all|load more)\b""", RegexOption.IGNORE_CASE)


    /**
     * Older observations collapse to their outcome line; only the newest
     * screen is kept whole. Long outcomes (read_page text) are trimmed once
     * they are no longer the latest: the facts worth keeping are in notes.
     * Under context pressure, keep fewer turns.
     */
    internal fun compact(history: List<ChatRequestMessage>, pressure: Int): List<ChatRequestMessage> {
        val lastScreenIdx = history.indexOfLast { it.content?.contains(SCREEN_MARKER) == true }
        val keepTail = when (pressure) { 0 -> 48; 1 -> 24; else -> 12 }
        val compacted = history.mapIndexed { i, m ->
            val c = m.content
            if (i != lastScreenIdx && c != null && c.contains(SCREEN_MARKER)) {
                val head = c.substringBefore(SCREEN_MARKER).trimEnd().let { if (it.length > 1_200) it.take(1_200) + " …" else it }
                val unchanged = c.substringAfter(SCREEN_MARKER).lineSequence().any { it.contains("did not change") }
                m.copy(content = head.ifBlank { "(observation)" } + (if (unchanged) " [screen did not change]" else "") + " [older screen omitted]")
            } else m
        }
        if (compacted.size <= keepTail + 1) return compacted
        // Keep the task message, then the newest turns starting at a clean boundary.
        var start = compacted.size - keepTail
        while (start < compacted.size && compacted[start].role == "tool") start++
        return listOf(compacted.first()) + compacted.subList(start, compacted.size)
    }

    private fun describe(call: ToolCallResponse, screen: ScreenState?): Pair<String, StepKind> {
        val a = JsonParserUtils.parseToJsonObject(call.function.arguments) ?: JsonObject(emptyMap())
        fun target(key: String = "index", xKey: String = "x", yKey: String = "y"): String {
            val e = DeviceArgs.int(a, key)?.let { screen?.element(it) }
            val x = DeviceArgs.int(a, xKey)
            return when {
                e != null && e.label.isNotBlank() -> "“${e.label.take(40)}”"
                e != null -> "${e.role} #${e.index}"
                x != null -> "($x, ${DeviceArgs.int(a, yKey)})"
                else -> "the screen"
            }
        }
        fun quoted(vararg keys: String) = "“${keys.firstNotNullOfOrNull { DeviceArgs.str(a, it) }.orEmpty().take(40)}”"
        return when (call.function.name) {
            "tap" -> "Tap ${target()}" to StepKind.ACTION
            "tap_text" -> "Tap ${quoted("text", "label")}" to StepKind.ACTION
            "long_press" -> "Press and hold ${target()}" to StepKind.ACTION
            "double_tap" -> "Double-tap ${target()}" to StepKind.ACTION
            "type_text" -> "Type ${quoted("text")}" + (if (DeviceArgs.bool(a, "submit")) " and submit" else "") to StepKind.ACTION
            "fill_form" -> FormFill.entries(a["fields"]).size.let { n -> "Fill $n field${if (n == 1) "" else "s"}" + (if (DeviceArgs.bool(a, "submit")) " and submit" else "") } to StepKind.ACTION
            "press_key" -> "Press ${DeviceArgs.str(a, "key").orEmpty().replace('_', ' ')}" to StepKind.ACTION
            "scroll" -> "Scroll ${DeviceArgs.str(a, "direction") ?: "down"}" to StepKind.ACTION
            "scroll_to" -> "Scroll to ${quoted("text")}" to StepKind.ACTION
            "swipe" -> "Swipe" to StepKind.ACTION
            "drag" -> "Drag ${target("from_index", "x1", "y1")} → ${target("to_index", "x2", "y2")}" to StepKind.ACTION
            "set_value" -> "Set ${target()} to ${quoted("value")}" to StepKind.ACTION
            "pinch" -> "Zoom ${if (DeviceArgs.str(a, "direction") == "out") "in" else "out"}" to StepKind.ACTION
            "open_app" -> "Open ${DeviceArgs.str(a, "name") ?: DeviceArgs.str(a, "app").orEmpty()}" to StepKind.ACTION
            "open_url" -> "Open ${DeviceArgs.str(a, "url").orEmpty().removePrefix("https://").removePrefix("www.").take(48)}" to StepKind.ACTION
            "quick_action" -> QuickActions.title(
                DeviceArgs.str(a, "action"), DeviceArgs.str(a, "text") ?: DeviceArgs.str(a, "query"), DeviceArgs.str(a, "time"),
                DeviceArgs.num(a, "minutes"), DeviceArgs.str(a, "page"), DeviceArgs.str(a, "to"),
            ) to StepKind.ACTION
            "media" -> mediaTitle(DeviceArgs.str(a, "action"), DeviceArgs.str(a, "position")) to StepKind.ACTION
            "read_page" -> "Read the page" to StepKind.ACTION
            "list_apps" -> "Check installed apps" to StepKind.SYSTEM
            "find_text" -> "Look for ${quoted("text")}" to StepKind.SYSTEM
            "read_screen" -> "Read the screen" to StepKind.SYSTEM
            "look" -> "Look at the screen" to StepKind.SYSTEM
            "device_status" -> "Check the phone's status" to StepKind.SYSTEM
            "wait" -> "Wait ${DeviceArgs.num(a, "seconds")?.let { "%.0f".format(it) } ?: "2"}s for loading" to StepKind.SYSTEM
            "wait_for" -> (if (DeviceArgs.bool(a, "gone")) "Wait for ${quoted("text")} to go" else "Wait for ${quoted("text")}") to StepKind.SYSTEM
            "take_note" -> "Note: ${DeviceArgs.str(a, "note").orEmpty().take(80)}" to StepKind.NOTE
            "ask_user" -> (DeviceArgs.str(a, "question") ?: "Question") to StepKind.QUESTION
            else -> call.function.name.replace('_', ' ') to StepKind.SYSTEM
        }
    }

    /** Step titles that name the action, so screens can pick its icon (play, pause, next...). */
    internal fun mediaTitle(action: String?, position: String? = null): String = when (action?.lowercase()?.replace(' ', '_')) {
        "play" -> "Play"
        "pause" -> "Pause"
        "play_pause", "toggle" -> "Play or pause"
        "next", "skip" -> "Next"
        "previous", "prev", "back" -> "Previous"
        "forward", "fast_forward" -> "Forward 10 s"
        "rewind" -> "Rewind 10 s"
        "stop" -> "Stop playback"
        "seek" -> when (val s = position?.let(MediaControl::parsePosition)) {
            is MediaControl.Seek.By -> if (s.ms >= 0) "Forward ${s.ms / 1000} s" else "Rewind ${-s.ms / 1000} s"
            is MediaControl.Seek.To -> "Jump to ${MediaControl.formatTime(s.ms)}"
            is MediaControl.Seek.Fraction -> "Jump to ${Math.round(s.f * 100)}%"
            null -> "Seek"
        }
        "status" -> "Check what is playing"
        else -> "Media control"
    }

    /**
     * In the Settings app, the way to any page in one step. The live eval saw
     * models scroll and back out through Settings for a page quick_action
     * opens directly.
     */
    internal fun settingsShortcut(screen: ScreenState): String? =
        if (screen.packageName == "com.android.settings" || screen.appLabel.equals("Settings", ignoreCase = true)) {
            "In Settings, quick_action(settings, page=...) opens a page directly (wifi, bluetooth, display, sound, dnd, " +
                "notifications, apps, battery, location…), or type in \"Search settings\"."
        } else null

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
}

