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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
    ) {
        /** The tool result text the coding agent reads. */
        fun forModel(): String = buildString {
            append(if (success) "Phone task done" else "Phone task not completed")
            append(" (").append(actions).append(if (actions == 1) " action, " else " actions, ").append(seconds).append(" s")
            if (finalApp.isNotBlank()) append(", ended in ").append(finalApp)
            append(").\n")
            if (summary.isNotBlank()) append("Result: ").append(summary.trim()).append('\n')
            if (error != null && error != summary) append("Problem: ").append(error).append('\n')
            if (notes.isNotEmpty()) {
                append("Notes:\n")
                notes.forEach { append("- ").append(it).append('\n') }
            }
        }.trimEnd()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val paused = MutableStateFlow(false)
    @Volatile private var pendingAnswer: CompletableDeferred<String>? = null
    @Volatile private var pendingApproval: CompletableDeferred<Boolean>? = null
    private val stepSeq = AtomicLong(0)
    @Volatile private var appContext: Context? = null

    /** Set while a run with ad skipping is live; read by the service on UI events. */
    @Volatile private var adSkipActive = false

    /** Ads skipped since the model last saw a screen. */
    private val adsSinceObservation = AtomicInteger(0)

    /** Whether the service should look for skippable ads right now. */
    val adSkipEnabled: Boolean get() = adSkipActive && !paused.value

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
        if (job?.isActive == true) {
            job?.cancel()
            _state.update { it.copy(running = false, error = "The Oro accessibility service was turned off, so the task stopped.", endedAt = System.currentTimeMillis()) }
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
        addStep(Step(nextId(), StepKind.SYSTEM, null, what, status = StepStatus.DONE, endedAt = System.currentTimeMillis()))
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
        if (job?.isActive == true) {
            return Result(false, "", emptyList(), 0, 0, "", error = "The phone is busy with another device task. Wait for it to finish, or ask the user.")
        }
        val started = begin(context, task, model, aiRepository, settings, Origin.CHAT)
            ?: return Result(false, "", emptyList(), 0, 0, "", error = _state.value.error ?: "The device agent could not start.")
        try {
            started.join()
        } catch (e: CancellationException) {
            started.cancel()
            throw e
        }
        val s = _state.value
        return Result(
            success = s.success == true,
            summary = s.summary.orEmpty(),
            notes = s.notes,
            actions = s.actions,
            seconds = ((s.endedAt - s.startedAt) / 1000).coerceAtLeast(0),
            finalApp = s.currentApp,
            error = s.error,
        )
    }

    private fun begin(
        context: Context,
        task: String,
        model: AiModel,
        aiRepository: AiRepository,
        settings: Settings,
        origin: Origin,
    ): Job? {
        if (job?.isActive == true) return null
        val service = OroAccessibilityService.instance.value
        appContext = context.applicationContext
        if (service == null) {
            _state.update { it.copy(error = "Turn on Oro Device Agent in Accessibility settings first.") }
            return null
        }
        paused.value = false
        stepSeq.set(0)
        adsSinceObservation.set(0)
        adSkipActive = settings.skipAds
        _state.value = State(
            serviceConnected = true, running = true, task = task.trim(), modelName = model.name,
            status = "Starting", maxActions = settings.maxActions, startedAt = System.currentTimeMillis(),
            origin = origin,
        )
        service.overlay.show(settings.showTouches)
        service.overlay.update("Starting…", null, paused = false, working = true)
        val run = DeviceRun(service, task.trim(), model, aiRepository, settings, origin)
        val launched = scope.launch {
            val result = try {
                run.run()
            } catch (e: CancellationException) {
                AgentRunResult.Failed("Stopped by you.", retryable = false)
            } catch (e: Exception) {
                AgentRunResult.Failed(e.message ?: "The device agent crashed.", retryable = true)
            }
            // The run may have been cancelled (Stop); the wrap-up must still
            // happen, or the floating control would stay on screen forever.
            withContext(NonCancellable) { finish(service, result, settings, origin, run.finishSummary) }
        }
        job = launched
        return launched
    }

    fun stop() {
        pendingAnswer?.cancel(); pendingApproval?.complete(false)
        job?.cancel()
    }

    fun togglePause() {
        if (!_state.value.running) return
        paused.value = !paused.value
        _state.update { it.copy(paused = paused.value, status = if (paused.value) "Paused" else it.status) }
        OroAccessibilityService.instance.value?.overlay?.update(
            if (paused.value) "Paused" else "Resuming…", null, paused.value, working = !paused.value
        )
    }

    fun answer(text: String) {
        pendingAnswer?.complete(text)
    }

    fun approve(allow: Boolean) {
        pendingApproval?.complete(allow)
    }

    fun clear() {
        if (job?.isActive == true) return
        _state.update { State(serviceConnected = it.serviceConnected) }
    }

    // ------------------------------------------------------------- one run

    /** Everything that belongs to one task: its controller, screens, notes and counters. */
    private class DeviceRun(
        val service: OroAccessibilityService,
        val task: String,
        val model: AiModel,
        val ai: AiRepository,
        val settings: Settings,
        val origin: Origin,
    ) {
        val controller = DeviceController(service)
        val vision = settings.screenshots && ScreenshotTaker.supported &&
            VisionSupport.supports(model)
        val tools = DeviceTools.forRun(vision)
        val limited = settings.maxActions > 0
        var forceText = false
        var lastScreen: ScreenState? = null
        /** The screen the model saw when it decided this turn's calls; indexes refer to it. */
        var turnScreen: ScreenState? = null
        val notes = mutableListOf<String>()
        var actions = 0
        var unchangedStreak = 0
        val visits = HashMap<Int, Int>()
        @Volatile var finishSummary: String? = null
        /** Step id of the tool call in progress, so late updates land on the right card. */
        @Volatile var currentStepId: Long = -1

        fun observe(maxLabel: Int = ScreenReader.DEFAULT_MAX_LABEL): ScreenState =
            controller.capture(maxLabel).also { s ->
                if (maxLabel == ScreenReader.DEFAULT_MAX_LABEL) lastScreen = s
                _state.update { it.copy(currentApp = s.appLabel) }
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
            val seen = (visits[s.signature] ?: 0) + 1
            visits[s.signature] = seen
            if (seen == 4 || seen == 8) {
                append("⚠ You have been on this exact screen ").append(seen).append(" times; you may be going in circles. Take a different route.\n")
            }
            if (notes.isNotEmpty()) append("Your notes: ").append(notes.joinToString(" | ")).append('\n')
            append(s.render(maxChars, adsHandled = settings.skipAds))
        }

        suspend fun run(): AgentRunResult {
            val nativeTools = model.supportsToolCalls

            // Start from the screen the task is about, not from Oro itself.
            controller.settle(quietMs = 250, maxMs = 1_200, minMs = 80)
            var first = observe()
            if (first.packageName == service.packageName) {
                controller.global("home")
                controller.settle(quietMs = 250, maxMs = 1_500)
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
                execute(call).also { r ->
                    if (call.function.name !in DeviceTools.passive && r.success) actions++
                    _state.update { it.copy(actions = actions, notes = notes.toList()) }
                }
            }

            val client = AgentModelClient { request ->
                val stream = ai.streamChat(model, request.messages, request.tools, temperature = 0.2f)
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
                    service.overlay.update(label, null, paused.value, working = true)
                }

                override fun onTurnComplete(visibleText: String, toolCalls: List<ToolCallResponse>) {
                    turnScreen = lastScreen
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
                    service.overlay.update(title, null, paused.value, working = true)
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
                        actionsLeft = if (limited) settings.maxActions - actions else null,
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
                    maxIterations = if (limited) settings.maxActions + 10 else Int.MAX_VALUE,
                    attachImages = vision,
                    maxToolResultChars = 14_000,
                    idleTimeoutMs = 90_000,
                    maxQualityPasses = 1
                )
            )
            // A "done" claim without finish_task cannot be checked against the
            // screen deterministically; instead remind once to call
            // finish_task, so the user (or the coding agent) gets a summary.
            var reminded = false
            return loop.run(conversation, DeviceTools.names, listener, finalCheck = {
                if (reminded) null else {
                    reminded = true
                    "If the task is complete, call finish_task with a summary (and the answer, if the task asked for information). " +
                        "If it is not complete, continue with the next action."
                }
            }, prepare = prepare)
        }

        // ------------------------------------------------------- execute

        private suspend fun execute(call: ToolCallResponse): ToolResult {
            val c = controller
            val name = call.function.name.substringAfterLast('.').lowercase()
            val args = JsonParserUtils.parseToJsonObject(call.function.arguments) ?: JsonObject(emptyMap())

            if (limited && name !in DeviceTools.passive && actions >= settings.maxActions) {
                return ToolResult(false, "", "The action limit (${settings.maxActions}) is used up. Call finish_task with what you achieved and what is left.")
            }

            // Pause gate: device-touching actions wait while paused.
            if (name !in DeviceTools.passive && paused.value) {
                _state.update { it.copy(status = "Paused") }
                paused.first { !it }
            }

            val screen = lastScreen ?: observe()
            val before = screen.signature
            // Indexes refer to the screen the model saw. If an earlier call in the
            // same turn changed the screen, find the same element (role + label)
            // on the new one; never act on whatever now has that number.
            var stale = false
            fun el(key: String = "index"): ScreenElement? {
                val i = args.int(key) ?: return null
                val seen = turnScreen ?: screen
                val (match, wasStale) = ElementRemap.resolve(seen, screen, i)
                if (wasStale) stale = true
                return match
            }

            suspend fun act(maxSettle: Long = 2_500, block: suspend () -> String): ToolResult {
                val outcome = block()
                // Judge the sentence, not the labels quoted in it ("Tapped “Failed orders”").
                val failed = FAILED.containsMatchIn(outcome.replace(QUOTED, ""))
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
                val text = outcome + "\n\n" + observation(before, after)
                return if (failed) ToolResult(false, text, outcome) else ToolResult(true, text)
            }

            fun missingTarget() = if (stale) ToolResult(
                false,
                observation(null, screen),
                "Skipped: an earlier action in this turn changed the screen and that element is gone. Decide again from this screen."
            ) else ToolResult(false, "", "Give an element index from the screen listing, or x and y. " +
                "Valid indexes are 0-${(screen.elements.size - 1).coerceAtLeast(0)}.")

            // Safety guard for sensitive actions.
            val sensitive = SensitiveGuard.check(name, args, screen)
            if (sensitive != null && settings.confirmSensitive) {
                val allowed = requestApproval(sensitive)
                if (!allowed) {
                    return ToolResult(true, "Blocked: the user denied \"$sensitive\". Do not retry it; choose another path or finish_task explaining what is left.")
                }
            }

            return when (name) {
                "tap" -> el()?.let { e -> act { c.tapElement(e) } }
                    ?: xy(args)?.let { (x, y) -> act { if (c.tap(x, y)) "Tapped ($x,$y)." else "The tap could not be performed." } }
                    ?: missingTarget()
                "tap_text" -> {
                    val text = args.str("text") ?: args.str("label") ?: return ToolResult(false, "", "Missing text.")
                    when (val m = TextMatch.find(text, screen, args.int("nth"))) {
                        is TextMatch.Result.Found -> act { c.tapElement(m.element) }
                        is TextMatch.Result.Ambiguous -> ToolResult(
                            false,
                            m.candidates.joinToString("\n") { it.render() },
                            if (m.weak) "Nothing clearly shows \"$text\". Closest elements below; tap one by index if it is right."
                            else "Several elements show \"$text\". Tap one by index, or pass nth (1 = first)."
                        )
                        TextMatch.Result.NotFound -> ToolResult(
                            false, "",
                            "\"$text\" is not on the screen. If it is further down use scroll_to(\"$text\"); otherwise read the listing for the exact label."
                        )
                    }
                }
                "long_press" -> el()?.let { e -> act { c.longPressElement(e) } }
                    ?: xy(args)?.let { (x, y) -> act { if (c.longPress(x, y)) "Long-pressed ($x,$y)." else "The long press failed." } }
                    ?: missingTarget()
                "double_tap" -> (el()?.let { it.centerX to it.centerY } ?: xy(args))
                    ?.let { (x, y) -> act { if (c.doubleTap(x, y)) "Double-tapped ($x,$y)." else "The double tap failed." } }
                    ?: missingTarget()
                "type_text" -> {
                    val text = args.str("text") ?: return ToolResult(false, "", "Missing text.")
                    val target = el()
                    if (args.int("index") != null && target == null && stale) return missingTarget()
                    act {
                        val typed = c.type(target, text, args.bool("append"))
                        if (args.bool("submit") && !typed.startsWith("No ")) { delay(150); typed + " " + c.submit() } else typed
                    }
                }
                "press_key" -> {
                    val key = args.str("key") ?: return ToolResult(false, "", "Missing key.")
                    act { if (key.equals("enter", true)) c.submit() else c.global(key) }
                }
                "scroll" -> act { c.scroll(args.str("direction") ?: "down", el(), (args.num("amount") ?: 0.6).toFloat()) }
                "scroll_to" -> {
                    val text = args.str("text") ?: return ToolResult(false, "", "Missing text.")
                    scrollTo(text, args.str("direction") ?: "down", el(), screen)
                }
                "swipe" -> {
                    val x1 = args.int("x1"); val y1 = args.int("y1"); val x2 = args.int("x2"); val y2 = args.int("y2")
                    if (x1 == null || y1 == null || x2 == null || y2 == null) ToolResult(false, "", "swipe needs x1, y1, x2, y2.")
                    else act { if (c.swipe(x1, y1, x2, y2, args.int("duration_ms")?.toLong() ?: 350)) "Swiped ($x1,$y1)→($x2,$y2)." else "The swipe failed." }
                }
                "drag" -> {
                    val from = el("from_index")?.let { it.centerX to it.centerY } ?: args.int("x1")?.let { x -> args.int("y1")?.let { x to it } }
                    val to = el("to_index")?.let { it.centerX to it.centerY } ?: args.int("x2")?.let { x -> args.int("y2")?.let { x to it } }
                    if (from == null || to == null) ToolResult(false, "", "drag needs from_index/to_index or x1,y1,x2,y2.")
                    else act { if (c.drag(from.first, from.second, to.first, to.second)) "Dragged from $from to $to." else "The drag failed." }
                }
                "pinch" -> {
                    val x = args.int("x") ?: screen.width / 2
                    val y = args.int("y") ?: screen.height / 2
                    act { if (c.pinch(x, y, args.str("direction") == "out")) "Pinched ${args.str("direction")} at ($x,$y)." else "The pinch failed." }
                }
                "open_app" -> {
                    val app = args.str("name") ?: args.str("app") ?: return ToolResult(false, "", "Missing app name.")
                    act(maxSettle = 4_000) { c.openApp(app).also { delay(400) } }
                }
                "open_url" -> {
                    val url = args.str("url") ?: return ToolResult(false, "", "Missing url.")
                    act(maxSettle = 4_000) { c.openUrl(url).also { delay(500) } }
                }
                "quick_action" -> {
                    val action = args.str("action") ?: return ToolResult(false, "", "Missing action. Use one of: ${QuickActions.ACTIONS.joinToString()}.")
                    val qa = QuickActions.Args(
                        action = action, text = args.str("text") ?: args.str("query"), to = args.str("to"), time = args.str("time"),
                        minutes = args.num("minutes"), page = args.str("page"), subject = args.str("subject"),
                    )
                    act(maxSettle = 3_000) { QuickActions.run(service, qa).also { delay(300) } }
                }
                "media" -> {
                    val action = args.str("action") ?: return ToolResult(false, "", "Missing action.")
                    act { c.media(action) }
                }
                "list_apps" -> {
                    val q = args.str("query")?.lowercase()
                    val apps = AppCatalog.apps(service)
                        .filter { q.isNullOrBlank() || it.label.lowercase().contains(q) || it.packageName.contains(q) }
                    ToolResult(true, if (apps.isEmpty()) "No apps match." else apps.take(120).joinToString("\n") { "${it.label} (${it.packageName})" })
                }
                "find_text" -> {
                    val q = args.str("text").orEmpty()
                    val now = observe()
                    val hits = TextMatch.hits(q, now).filter { it.score >= TextMatch.ACT_THRESHOLD }
                    ToolResult(true, if (hits.isEmpty()) "\"$q\" is not on the current screen. Try scroll_to(\"$q\")."
                    else "Found ${hits.size}:\n" + hits.take(12).joinToString("\n") { it.element.render() })
                }
                "read_screen" -> {
                    c.settle(quietMs = 250, maxMs = 1_500, minMs = 80)
                    val now = observe()
                    ToolResult(true, "Current screen.\n\n" + observation(null, now))
                }
                "read_page" -> readPage((args.int("pages") ?: 3).coerceIn(1, 8), before)
                "wait" -> {
                    val s = (args.num("seconds") ?: 2.0).coerceIn(0.5, 10.0)
                    act { delay((s * 1000).toLong()); "Waited ${"%.1f".format(s)}s." }
                }
                "wait_for" -> {
                    val text = args.str("text") ?: return ToolResult(false, "", "Missing text.")
                    waitFor(text, args.bool("gone"), ((args.num("seconds") ?: 10.0).coerceIn(1.0, 30.0) * 1000).toLong(), before)
                }
                "look" -> {
                    if (!vision) return ToolResult(false, "", "Screenshots are not available for this model or phone. Use the element listing.")
                    val now = observe()
                    val image = ScreenshotTaker.capture(service, now)
                        ?: return ToolResult(false, observation(null, now), "The screenshot failed; this app may block screenshots. Use the listing.")
                    ToolResult(true, "Screenshot attached; the numbers on it match the listing.\n\n" + observation(null, now), images = listOf(image))
                }
                "device_status" -> ToolResult(true, DeviceInfo.status(service))
                "take_note" -> {
                    val note = args.str("note")?.trim().orEmpty()
                    if (note.isEmpty()) ToolResult(false, "", "Empty note.") else {
                        notes += note.take(300)
                        while (notes.size > 16) notes.removeAt(0)
                        ToolResult(true, "Noted.")
                    }
                }
                "ask_user" -> {
                    val q = args.str("question") ?: return ToolResult(false, "", "Missing question.")
                    val options = (args["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
                    val answer = askUser(q, options)
                    updateStep(currentStepId) { it.copy(detail = "You answered: $answer", status = StepStatus.DONE, endedAt = System.currentTimeMillis()) }
                    ToolResult(true, "The user answered: $answer")
                }
                "finish_task" -> {
                    val summary = args.str("summary").orEmpty()
                    val ok = (args["success"] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() } ?: true
                    finishSummary = summary.ifBlank { null }
                    _state.update { it.copy(success = ok) }
                    ToolResult(true, summary)
                }
                else -> ToolResult(false, "", "Unknown tool \"$name\". Available: ${tools.joinToString { it.function.name }}.")
            }
        }

        private suspend fun scrollTo(text: String, direction: String, list: ScreenElement?, start: ScreenState): ToolResult {
            var cur = start
            var scrolls = 0
            var hit = TextMatch.hits(text, cur).firstOrNull { it.score >= TextMatch.ACT_THRESHOLD }
            var atEnd = false
            while (hit == null && scrolls < 10) {
                val before = cur.signature
                val target = list?.let { l -> cur.elements.firstOrNull { it.role == l.role && it.bounds == l.bounds } }
                controller.scroll(direction, target, 0.7f)
                controller.settle(quietMs = 250, maxMs = 1_500, minMs = 100)
                cur = observe()
                scrolls++
                if (cur.signature == before) { atEnd = true; break }
                hit = TextMatch.hits(text, cur).firstOrNull { it.score >= TextMatch.ACT_THRESHOLD }
            }
            val obs = observation(start.signature, cur)
            return when {
                hit != null -> ToolResult(true, "Found \"$text\" as [${hit.element.index}] after $scrolls scroll${if (scrolls == 1) "" else "s"}.\n\n$obs")
                atEnd -> ToolResult(false, obs, "Reached the end after $scrolls scroll${if (scrolls == 1) "" else "s"}; \"$text\" is not in this list. Try another direction, a search box, or another screen.")
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
                controller.scroll("down", null, 0.8f)
                controller.settle(quietMs = 250, maxMs = 1_500, minMs = 100)
                full = observe(maxLabel = 1_500)
                if (full.signature == before) break
            }
            val body = seen.joinToString("\n").let { if (it.length > 10_000) it.take(10_000) + "\n…" else it }
            val now = observe()
            return ToolResult(true, "Page text ($read screen${if (read == 1) "" else "s"}):\n$body\n\n" + observation(startSignature, now, maxChars = 2_500))
        }

        private suspend fun waitFor(text: String, gone: Boolean, timeoutMs: Long, startSignature: Int): ToolResult {
            val start = SystemClock.uptimeMillis()
            // Cheap check while waiting (the platform's own text search), full capture once at the end.
            while (controller.textVisible(text) == gone && SystemClock.uptimeMillis() - start < timeoutMs) {
                delay(400)
            }
            controller.settle(quietMs = 200, maxMs = 1_000, minMs = 50)
            val now = observe()
            val present = TextMatch.hits(text, now).any { it.score >= TextMatch.ACT_THRESHOLD } || controller.textVisible(text)
            val ok = present != gone
            val waited = "%.1f".format((SystemClock.uptimeMillis() - start) / 1000.0)
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
            _state.update { it.copy(approval = null) }
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
    private suspend fun askUser(question: String, options: List<String>): String {
        val d = CompletableDeferred<String>()
        pendingAnswer = d
        _state.update { it.copy(question = Question(question, options), status = "Waiting for your answer") }
        OroAccessibilityService.instance.value?.overlay?.update("Orobi has a question", question.take(90), paused = false, working = false)
        return try {
            d.await()
        } finally {
            pendingAnswer = null
            _state.update { it.copy(question = null) }
        }
    }

    private suspend fun finish(
        service: OroAccessibilityService,
        result: AgentRunResult,
        settings: Settings,
        origin: Origin,
        finishSummary: String?,
    ) {
        adSkipActive = false
        val now = System.currentTimeMillis()
        when (result) {
            is AgentRunResult.Completed -> _state.update {
                it.copy(running = false, paused = false, summary = finishSummary ?: result.finalText.ifBlank { "Done." },
                    success = it.success ?: true, status = "Done", endedAt = now)
            }
            is AgentRunResult.Failed -> _state.update {
                it.copy(running = false, paused = false, error = result.message, summary = finishSummary,
                    success = false, status = "Stopped", endedAt = now)
            }
            is AgentRunResult.IterationLimit -> _state.update {
                it.copy(running = false, paused = false, success = false, endedAt = now, status = "Action limit reached",
                    summary = finishSummary,
                    error = "Stopped after ${it.actions} actions (the limit). You can change the limit in settings.")
            }
        }
        _state.value.steps.lastOrNull()?.takeIf { it.status == StepStatus.RUNNING || it.status == StepStatus.WAITING }?.let { last ->
            updateStep(last.id) { st -> st.copy(status = StepStatus.FAILED, endedAt = now) }
        }
        val s = _state.value
        service.overlay.update(if (s.success == true) "Done ✓" else s.status, (s.summary ?: s.error)?.take(90), paused = false, working = false)
        delay(if (origin == Origin.CHAT) 1_800 else 2_500)
        service.overlay.hideAll()
        when {
            // The coding agent is waiting in the chat: always go back to it.
            origin == Origin.CHAT -> bringOroToFront(openDeviceScreen = false)
            settings.returnToOro -> bringOroToFront(openDeviceScreen = true)
        }
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
    private val FAILED = Regex("^(No |Unknown )|could not|failed|^\\w+ needs |not supported|rejected", RegexOption.IGNORE_CASE)
    private val QUOTED = Regex("\"[^\"]*\"|“[^”]*”")

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
        fun target(key: String = "index"): String {
            val e = a.int(key)?.let { screen?.element(it) }
            return when {
                e != null && e.label.isNotBlank() -> "“${e.label.take(40)}”"
                e != null -> "${e.role} #${e.index}"
                a.int("x") != null -> "(${a.int("x")}, ${a.int("y")})"
                else -> "the screen"
            }
        }
        fun quoted(key: String) = "“${a.str(key).orEmpty().take(40)}”"
        return when (call.function.name) {
            "tap" -> "Tap ${target()}" to StepKind.ACTION
            "tap_text" -> "Tap ${quoted("text")}" to StepKind.ACTION
            "long_press" -> "Press and hold ${target()}" to StepKind.ACTION
            "double_tap" -> "Double-tap ${target()}" to StepKind.ACTION
            "type_text" -> "Type ${quoted("text")}" + (if (a.bool("submit")) " and submit" else "") to StepKind.ACTION
            "press_key" -> "Press ${a.str("key").orEmpty().replace('_', ' ')}" to StepKind.ACTION
            "scroll" -> "Scroll ${a.str("direction") ?: "down"}" to StepKind.ACTION
            "scroll_to" -> "Scroll to ${quoted("text")}" to StepKind.ACTION
            "swipe" -> "Swipe" to StepKind.ACTION
            "drag" -> "Drag ${target("from_index")} → ${target("to_index")}" to StepKind.ACTION
            "pinch" -> "Zoom ${if (a.str("direction") == "out") "in" else "out"}" to StepKind.ACTION
            "open_app" -> "Open ${a.str("name").orEmpty()}" to StepKind.ACTION
            "open_url" -> "Open ${a.str("url").orEmpty().removePrefix("https://").removePrefix("www.").take(48)}" to StepKind.ACTION
            "quick_action" -> QuickActions.title(a.str("action"), a.str("text") ?: a.str("query"), a.str("time"), a.num("minutes"), a.str("page"), a.str("to")) to StepKind.ACTION
            "media" -> mediaTitle(a.str("action")) to StepKind.ACTION
            "read_page" -> "Read the page" to StepKind.ACTION
            "list_apps" -> "Check installed apps" to StepKind.SYSTEM
            "find_text" -> "Look for ${quoted("text")}" to StepKind.SYSTEM
            "read_screen" -> "Read the screen" to StepKind.SYSTEM
            "look" -> "Look at the screen" to StepKind.SYSTEM
            "device_status" -> "Check the phone's status" to StepKind.SYSTEM
            "wait" -> "Wait ${a.num("seconds")?.let { "%.0f".format(it) } ?: "2"}s for loading" to StepKind.SYSTEM
            "wait_for" -> (if (a.bool("gone")) "Wait for ${quoted("text")} to go" else "Wait for ${quoted("text")}") to StepKind.SYSTEM
            "take_note" -> "Note: ${a.str("note").orEmpty().take(80)}" to StepKind.NOTE
            "ask_user" -> (a.str("question") ?: "Question") to StepKind.QUESTION
            else -> call.function.name.replace('_', ' ') to StepKind.SYSTEM
        }
    }

    private fun mediaTitle(action: String?): String = when (action?.lowercase()) {
        "play" -> "Play"
        "pause" -> "Pause"
        "play_pause" -> "Play or pause"
        "next" -> "Next"
        "previous" -> "Previous"
        "forward" -> "Skip ahead 10 s"
        "rewind" -> "Back 10 s"
        "stop" -> "Stop playback"
        else -> "Media control"
    }

    private fun nextId() = stepSeq.incrementAndGet()

    private fun addStep(step: Step) = _state.update { it.copy(steps = (it.steps + step).takeLast(300)) }

    private fun updateStep(id: Long, transform: (Step) -> Step) = _state.update { st ->
        val i = st.steps.indexOfLast { it.id == id }
        if (i < 0) st else st.copy(steps = st.steps.toMutableList().also { it[i] = transform(it[i]) })
    }

    private fun xy(a: JsonObject): Pair<Int, Int>? {
        val x = a.int("x") ?: return null
        val y = a.int("y") ?: return null
        return x to y
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

// Lenient argument readers: models send numbers as strings and booleans as "true".
private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() ?: it.contentOrNull?.trim()?.toIntOrNull() }
private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "true") } ?: false
