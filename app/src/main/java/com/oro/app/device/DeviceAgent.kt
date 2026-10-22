package com.oro.app.device

import android.content.Context
import android.content.Intent
import com.oro.app.MainActivity
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.ToolCallResponse
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

/**
 * Orobi operating the phone: an observe → think → act loop over the
 * accessibility service, built on the same [AgentLoop] as the coding agent
 * (retries, text-tool fallback for models without function calling,
 * cancellation, repetition guard).
 *
 * ### What makes it work with weak models
 *  - The screen is a short numbered list, not a raw tree or an image, so any
 *    text model can read it and act by number.
 *  - Every action returns its outcome *and the new screen*, plus an explicit
 *    "the screen did not change" when it didn't -- the single most useful
 *    signal for noticing a failed tap instead of repeating it forever.
 *  - Only the latest screen stays in context; older observations collapse to
 *    their one-line outcome. Context stays small and the model never reasons
 *    about a stale screen.
 *  - Notes (take_note) persist facts across screens.
 *
 * ### What keeps the user in control
 *  - A floating pill with Pause and Stop is on screen for the whole run.
 *  - Sensitive actions (send, pay, buy, delete, post, install, password
 *    fields...) wait for an explicit Allow, by default.
 *  - Questions bring Oro to the front and wait for the user.
 *  - The run lives in this process-wide object, not in a screen, so it keeps
 *    going while other apps are in front; the bound accessibility service
 *    keeps the process alive.
 */
object DeviceAgent {

    enum class StepKind { THOUGHT, ACTION, QUESTION, APPROVAL, NOTE, SYSTEM }
    enum class StepStatus { RUNNING, DONE, FAILED, BLOCKED, WAITING }

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
        val maxActions: Int = 0,
        val notes: List<String> = emptyList(),
        val question: Question? = null,
        val approval: String? = null,
        val summary: String? = null,
        val success: Boolean? = null,
        val error: String? = null,
        val startedAt: Long = 0,
        val endedAt: Long = 0,
    ) {
        val finished: Boolean get() = !running && startedAt > 0
    }

    data class Settings(
        val confirmSensitive: Boolean = true,
        val showTouches: Boolean = true,
        val returnToOro: Boolean = true,
        val maxActions: Int = 60,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val paused = MutableStateFlow(false)
    private var pendingAnswer: CompletableDeferred<String>? = null
    private var pendingApproval: CompletableDeferred<Boolean>? = null
    private var stepSeq = 0L
    private var appContext: Context? = null

    // ------------------------------------------------------------- service

    internal fun onServiceAvailable(service: OroAccessibilityService) {
        appContext = service.applicationContext
        _state.update { it.copy(serviceConnected = true) }
        service.overlay.onStop = { stop() }
        service.overlay.onPauseToggle = { togglePause() }
        service.overlay.onApproval = { approve(it) }
        service.overlay.onOpenApp = { bringOroToFront() }
    }

    internal fun onServiceLost() {
        _state.update { it.copy(serviceConnected = false) }
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

    // ------------------------------------------------------------ controls

    fun start(context: Context, task: String, model: AiModel, aiRepository: AiRepository, settings: Settings) {
        if (job?.isActive == true) return
        val service = OroAccessibilityService.instance.value
        appContext = context.applicationContext
        if (service == null) {
            _state.update { it.copy(error = "Turn on Oro Device Agent in Accessibility settings first.") }
            return
        }
        paused.value = false
        stepSeq = 0
        _state.value = State(
            serviceConnected = true, running = true, task = task.trim(), modelName = model.name,
            status = "Starting", maxActions = settings.maxActions, startedAt = System.currentTimeMillis()
        )
        service.overlay.show(settings.showTouches)
        service.overlay.update("Starting…", null, paused = false, working = true)
        job = scope.launch {
            val result = try {
                run(service, task.trim(), model, aiRepository, settings)
            } catch (e: CancellationException) {
                AgentRunResult.Failed("Stopped by you.", retryable = false)
            } catch (e: Exception) {
                AgentRunResult.Failed(e.message ?: "The device agent crashed.", retryable = true)
            }
            finish(service, result, settings)
        }
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

    // ---------------------------------------------------------------- run

    private suspend fun run(
        service: OroAccessibilityService,
        task: String,
        model: AiModel,
        ai: AiRepository,
        settings: Settings
    ): AgentRunResult {
        val controller = DeviceController(service)
        val nativeTools = model.supportsToolCalls
        var forceText = false
        var lastScreen: ScreenState? = null
        val notes = mutableListOf<String>()
        var actions = 0

        fun observe(): ScreenState = controller.capture().also { s ->
            lastScreen = s
            _state.update { it.copy(currentApp = s.appLabel) }
        }

        fun observation(before: Int?, s: ScreenState): String = buildString {
            append(SCREEN_MARKER).append('\n')
            if (before != null) {
                append(if (before == s.signature) "⚠ The screen did not change after this action.\n" else "Screen changed.\n")
            }
            if (notes.isNotEmpty()) append("Your notes: ").append(notes.joinToString(" | ")).append('\n')
            append(s.render())
        }

        // Initial observation.
        controller.settle(quietMs = 300, maxMs = 1_500, minMs = 100)
        val first = observe()
        val conversation = mutableListOf(
            ChatRequestMessage(
                role = "user",
                content = "Task: $task\n\n${observation(null, first)}\n\nDecide the first step."
            )
        )

        // The screen the model saw when it decided this turn's calls; indexes refer to it.
        var turnScreen: ScreenState? = first
        val runner = AgentToolRunner { call -> execute(call, controller, settings, notes, ::observe, ::observation, lastScreen, turnScreen).also { r ->
            if (call.function.name !in DeviceTools.passive && r.success) actions++
            _state.update { it.copy(actions = actions, notes = notes.toList()) }
        } }

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
                addStep(Step(nextId(), kind, name, title, app = lastScreen?.appLabel, status = if (kind == StepKind.QUESTION) StepStatus.WAITING else StepStatus.RUNNING))
                service.overlay.update(title, null, paused.value, working = true)
            }

            override fun onToolFinished(call: ToolCallResponse, result: ToolResult) {
                if (call.function.name == "finish_task") return
                val text = (if (result.success) result.output else result.error ?: result.output)
                val outcome = text.substringBefore(SCREEN_MARKER).trim().lineSequence().firstOrNull().orEmpty()
                val unchanged = text.contains("did not change")
                val blocked = text.startsWith("Blocked") || text.contains("user denied", true)
                updateLastStep { st ->
                    st.copy(
                        detail = outcome.take(240),
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
            val system = ChatRequestMessage(role = "system", content = DevicePrompt.system(textMode, settings, actionsLeft = settings.maxActions - actions))
            AgentRequest(listOf(system) + compact(history, pressure), if (textMode) null else DeviceTools.all)
        }

        val loop = AgentLoop(
            client = client,
            tools = runner,
            config = AgentLoopConfig(
                maxIterations = settings.maxActions + 10,
                maxToolResultChars = 12_000,
                idleTimeoutMs = 90_000,
                maxQualityPasses = 1
            )
        )
        // A final check that catches "done" claims without finish_task on a
        // screen that clearly still needs work is not possible deterministically;
        // instead remind once to call finish_task so the user gets a summary.
        var reminded = false
        return loop.run(conversation, DeviceTools.names, listener, finalCheck = {
            if (reminded) null else {
                reminded = true
                "If the task is complete, call finish_task with a summary (and the answer, if the task asked for information). " +
                    "If it is not complete, continue with the next action."
            }
        }, prepare = prepare)
    }

    private suspend fun execute(
        call: ToolCallResponse,
        c: DeviceController,
        settings: Settings,
        notes: MutableList<String>,
        observe: () -> ScreenState,
        observation: (Int?, ScreenState) -> String,
        screenAtCall: ScreenState?,
        turnScreen: ScreenState?,
    ): ToolResult {
        val name = call.function.name.substringAfterLast('.').lowercase()
        val args = JsonParserUtils.parseToJsonObject(call.function.arguments) ?: JsonObject(emptyMap())

        // Pause gate: device-touching actions wait while paused.
        if (name !in DeviceTools.passive && paused.value) {
            _state.update { it.copy(status = "Paused") }
            paused.first { !it }
        }

        val screen = screenAtCall ?: observe()
        val before = screen.signature
        // Indexes refer to the screen the model saw. If an earlier call in the
        // same turn changed the screen, find the same element (role + label)
        // on the new one; never act on whatever now has that number.
        var stale = false
        fun el(key: String = "index"): ScreenElement? {
            val i = args.int(key) ?: return null
            val seen = turnScreen ?: screen
            if (seen === screen || seen.signature == screen.signature) return screen.element(i)
            val original = seen.element(i) ?: return null
            val matches = screen.elements.filter { it.role == original.role && it.label == original.label && it.label.isNotBlank() }
            val match = matches.minByOrNull { kotlin.math.abs(it.centerY - original.centerY) + kotlin.math.abs(it.centerX - original.centerX) }
            if (match == null) stale = true
            return match
        }

        suspend fun act(block: suspend () -> String): ToolResult {
            val outcome = block()
            val failed = outcome.startsWith("No ") || outcome.contains("could not") || outcome.contains("failed") || outcome.startsWith("Unknown")
            c.settle()
            val after = observe()
            return ToolResult(!failed, outcome + "\n\n" + observation(before, after))
        }

        fun missingTarget() = if (stale) ToolResult(
            false, "",
            "Skipped: an earlier action in this turn changed the screen and that element is gone. Decide again from this screen.\n\n" +
                observation(null, screen)
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
            "long_press" -> el()?.let { e -> act { c.longPressElement(e) } }
                ?: xy(args)?.let { (x, y) -> act { if (c.longPress(x, y)) "Long-pressed ($x,$y)." else "The long press failed." } }
                ?: missingTarget()
            "double_tap" -> (el()?.let { it.centerX to it.centerY } ?: xy(args))
                ?.let { (x, y) -> act { if (c.doubleTap(x, y)) "Double-tapped ($x,$y)." else "The double tap failed." } }
                ?: missingTarget()
            "type_text" -> {
                val text = args.str("text") ?: return ToolResult(false, "", "Missing text.")
                act {
                    val typed = c.type(el(), text, args.bool("append"))
                    if (args.bool("submit") && !typed.startsWith("No ")) { delay(200); typed + " " + c.submit() } else typed
                }
            }
            "press_key" -> {
                val key = args.str("key") ?: return ToolResult(false, "", "Missing key.")
                act { if (key.equals("enter", true)) c.submit() else c.global(key) }
            }
            "scroll" -> act { c.scroll(args.str("direction") ?: "down", el(), (args.num("amount") ?: 0.6).toFloat()) }
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
                act { c.openApp(app).also { delay(600) } }
            }
            "open_url" -> {
                val url = args.str("url") ?: return ToolResult(false, "", "Missing url.")
                act { c.openUrl(url).also { delay(800) } }
            }
            "list_apps" -> {
                val q = args.str("query")?.lowercase()
                val apps = AppCatalog.apps(appContext ?: return ToolResult(false, "", "No context"))
                    .filter { q.isNullOrBlank() || it.label.lowercase().contains(q) || it.packageName.contains(q) }
                ToolResult(true, if (apps.isEmpty()) "No apps match." else apps.take(120).joinToString("\n") { "${it.label} (${it.packageName})" })
            }
            "find_text" -> {
                val q = args.str("text")?.lowercase().orEmpty()
                val now = observe()
                val hits = now.elements.filter { it.label.lowercase().contains(q) || it.value?.lowercase()?.contains(q) == true }
                ToolResult(true, if (hits.isEmpty()) "\"$q\" is not on the current screen. Try scrolling or another screen."
                else "Found ${hits.size}:\n" + hits.joinToString("\n") { it.render() })
            }
            "read_screen" -> {
                c.settle(quietMs = 300, maxMs = 1_500, minMs = 100)
                val now = observe()
                ToolResult(true, "Current screen.\n\n" + observation(null, now))
            }
            "wait" -> {
                val s = (args.num("seconds") ?: 2.0).coerceIn(0.5, 10.0)
                act { delay((s * 1000).toLong()); "Waited ${"%.1f".format(s)}s." }
            }
            "take_note" -> {
                val note = args.str("note")?.trim().orEmpty()
                if (note.isEmpty()) ToolResult(false, "", "Empty note.") else {
                    notes += note.take(300)
                    while (notes.size > 12) notes.removeAt(0)
                    ToolResult(true, "Noted.")
                }
            }
            "ask_user" -> {
                val q = args.str("question") ?: return ToolResult(false, "", "Missing question.")
                val options = (args["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
                val answer = askUser(q, options)
                updateLastStep { it.copy(detail = "You answered: $answer", status = StepStatus.DONE, endedAt = System.currentTimeMillis()) }
                ToolResult(true, "The user answered: $answer")
            }
            "finish_task" -> {
                val summary = args.str("summary").orEmpty()
                val ok = (args["success"] as? JsonPrimitive)?.booleanOrNull ?: true
                _state.update { it.copy(success = ok) }
                ToolResult(true, summary)
            }
            else -> ToolResult(false, "", "Unknown tool \"$name\". Available: ${DeviceTools.names.joinToString()}.")
        }
    }

    // ------------------------------------------------------ interactions

    private suspend fun requestApproval(what: String): Boolean {
        val d = CompletableDeferred<Boolean>()
        pendingApproval = d
        addStep(Step(nextId(), StepKind.APPROVAL, null, "Needs your OK: $what", status = StepStatus.WAITING))
        _state.update { it.copy(approval = what, status = "Waiting for your approval") }
        val overlay = OroAccessibilityService.instance.value?.overlay
        overlay?.askApproval("Allow Orobi to $what?")
        val allowed = withTimeoutOrNull(180_000) { d.await() } ?: false
        overlay?.clearApproval()
        pendingApproval = null
        _state.update { it.copy(approval = null) }
        updateLastStep {
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
        OroAccessibilityService.instance.value?.overlay?.update("Orobi has a question", question.take(90), paused = false, working = false)
        bringOroToFront()
        val answer = try { d.await() } finally {
            pendingAnswer = null
            _state.update { it.copy(question = null) }
        }
        return answer
    }

    private suspend fun finish(service: OroAccessibilityService, result: AgentRunResult, settings: Settings) {
        val now = System.currentTimeMillis()
        when (result) {
            is AgentRunResult.Completed -> _state.update {
                it.copy(running = false, paused = false, summary = result.finalText.ifBlank { "Done." }, success = it.success ?: true,
                    status = "Done", endedAt = now)
            }
            is AgentRunResult.Failed -> _state.update {
                it.copy(running = false, paused = false, error = result.message, success = false, status = "Stopped", endedAt = now)
            }
            is AgentRunResult.IterationLimit -> _state.update {
                it.copy(running = false, paused = false, success = false, endedAt = now, status = "Step limit reached",
                    error = "Stopped after ${it.actions} actions (the limit). You can raise the limit in settings.")
            }
        }
        _state.value.steps.lastOrNull()?.takeIf { it.status == StepStatus.RUNNING || it.status == StepStatus.WAITING }?.let {
            updateLastStep { st -> st.copy(status = StepStatus.FAILED, endedAt = now) }
        }
        val s = _state.value
        service.overlay.update(if (s.success == true) "Done ✓" else s.status, (s.summary ?: s.error)?.take(90), paused = false, working = false)
        delay(2_500)
        service.overlay.hideAll()
        if (settings.returnToOro) bringOroToFront()
    }

    private fun bringOroToFront() {
        val ctx = appContext ?: return
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_DEVICE_AGENT, true)
        runCatching { ctx.startActivity(intent) }
    }

    const val EXTRA_OPEN_DEVICE_AGENT = "com.oro.app.OPEN_DEVICE_AGENT"

    // ------------------------------------------------------------ helpers

    internal const val SCREEN_MARKER = "=== SCREEN ==="

    /**
     * Older observations collapse to their outcome line; only the newest
     * screen is kept whole. Under context pressure, keep fewer turns.
     */
    internal fun compact(history: List<ChatRequestMessage>, pressure: Int): List<ChatRequestMessage> {
        val lastScreenIdx = history.indexOfLast { it.content?.contains(SCREEN_MARKER) == true }
        val keepTail = when (pressure) { 0 -> 48; 1 -> 24; else -> 12 }
        val compacted = history.mapIndexed { i, m ->
            val c = m.content
            if (i != lastScreenIdx && c != null && c.contains(SCREEN_MARKER)) {
                val head = c.substringBefore(SCREEN_MARKER).trimEnd()
                val tail = c.substringAfter(SCREEN_MARKER).lineSequence().firstOrNull { it.contains("did not change") }
                m.copy(content = (head.ifBlank { "(observation)" }) + (if (tail != null) " [screen did not change]" else "") + " [older screen omitted]")
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
        return when (call.function.name) {
            "tap" -> "Tap ${target()}" to StepKind.ACTION
            "long_press" -> "Press and hold ${target()}" to StepKind.ACTION
            "double_tap" -> "Double-tap ${target()}" to StepKind.ACTION
            "type_text" -> "Type “${a.str("text")?.take(40).orEmpty()}”" + (if (a.bool("submit")) " and submit" else "") to StepKind.ACTION
            "press_key" -> "Press ${a.str("key").orEmpty().replace('_', ' ')}" to StepKind.ACTION
            "scroll" -> "Scroll ${a.str("direction") ?: "down"}" to StepKind.ACTION
            "swipe" -> "Swipe" to StepKind.ACTION
            "drag" -> "Drag ${target("from_index")} → ${target("to_index")}" to StepKind.ACTION
            "pinch" -> "Zoom ${if (a.str("direction") == "out") "in" else "out"}" to StepKind.ACTION
            "open_app" -> "Open ${a.str("name").orEmpty()}" to StepKind.ACTION
            "open_url" -> "Open ${a.str("url").orEmpty().removePrefix("https://").take(48)}" to StepKind.ACTION
            "list_apps" -> "Check installed apps" to StepKind.SYSTEM
            "find_text" -> "Look for “${a.str("text").orEmpty().take(40)}”" to StepKind.SYSTEM
            "read_screen" -> "Read the screen" to StepKind.SYSTEM
            "wait" -> "Wait ${a.num("seconds")?.let { "%.0f".format(it) } ?: "2"}s for loading" to StepKind.SYSTEM
            "take_note" -> "Note: ${a.str("note").orEmpty().take(80)}" to StepKind.NOTE
            "ask_user" -> (a.str("question") ?: "Question") to StepKind.QUESTION
            else -> call.function.name.replace('_', ' ') to StepKind.SYSTEM
        }
    }

    private fun nextId() = ++stepSeq

    private fun addStep(step: Step) = _state.update { it.copy(steps = (it.steps + step).takeLast(300)) }

    private fun updateLastStep(transform: (Step) -> Step) = _state.update { st ->
        if (st.steps.isEmpty()) st else st.copy(steps = st.steps.dropLast(1) + transform(st.steps.last()))
    }

    private fun xy(a: JsonObject): Pair<Int, Int>? {
        val x = a.int("x") ?: return null
        val y = a.int("y") ?: return null
        return x to y
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() ?: it.contentOrNull?.trim()?.toIntOrNull() }
    private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
    private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "true") } ?: false

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
