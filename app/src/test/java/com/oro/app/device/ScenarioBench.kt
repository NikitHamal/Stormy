package com.oro.app.device

import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.agent.AgentModelClient
import com.oro.app.data.ai.agent.AgentRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The scenario bench: a phone that follows a script and a model that follows
 * a script, so the device agent's real run loop -- observation, target
 * resolution, the sensitive-action guard, stuck signals, endings -- runs in a
 * JVM test, start to finish, with no Android device.
 *
 * A scenario says which screen the phone starts on, how each action changes
 * the screen ([FakePhone.rules]), and what the model calls each turn. The
 * test then checks what reached the phone ([FakePhone.log]), what the model
 * was shown ([ScriptedModel.seen]) and how the run ended.
 */
internal class FakePhone(
    private val clock: () -> Long,
    start: ScreenState,
    private val installed: List<AppCatalog.App> = listOf(
        AppCatalog.App("YouTube", "com.google.android.youtube"),
        AppCatalog.App("WhatsApp", "com.whatsapp"),
    ),
) : Phone {

    /** Something the agent did to the phone. */
    sealed interface Act {
        data class Tap(val label: String, val index: Int) : Act
        data class TapAt(val x: Int, val y: Int) : Act
        data class Type(val field: String?, val text: String) : Act
        data object Submit : Act
        data class Key(val key: String) : Act
        /** [list] is the index of the element scrolled, null for "whatever the phone picks". */
        data class Scroll(val direction: String, val list: Int? = null) : Act
        data class Url(val url: String) : Act
        data class App(val name: String) : Act
        data class Quick(val args: QuickActions.Args) : Act
        data class Media(val action: String) : Act
        /** set_value on a slider; [durationMs] is the length the run worked out for a time. */
        data class SetValue(val label: String, val index: Int, val value: String, val durationMs: Long? = null) : Act
        /** A seek the media session took (Notification access on). */
        data class Seek(val position: String, val pkg: String?) : Act
        /** One tap on the video player to bring back its hidden controls. */
        data object ShowControls : Act
    }

    var screen: ScreenState = start
        private set

    /** What each action turns the screen into; null leaves it as it is. */
    var rules: (Act) -> ScreenState? = { null }

    /** What open_app says back (after [rules] ran for it). */
    var appResult: (String) -> String = { "Opened $it." }

    /** What quick_action says back (after [rules] ran for it). */
    var quickResult: (QuickActions.Args) -> String = { "Did ${it.action}." }

    /** What a seek through the media session does; by default Notification access is off. */
    var seekResult: (position: String, pkg: String?) -> MediaControl.SeekResult = { _, _ -> MediaControl.SeekResult.NoAccess }

    /** What set_value says back (after [rules] ran for it). */
    var setValueResult: (ScreenElement, String) -> String = { e, v -> "Set [${e.index}] ${e.role} \"${e.label}\" to $v." }

    /** The click the last tap by coordinates registered: whatever takes taps under it. */
    private var lastHit: TapHit? = null

    /** Every action that reached the phone, in order. */
    val log = mutableListOf<Act>()
    val scrolls: Int get() = log.count { it is Act.Scroll }

    private var latest: ScreenState? = null

    /** The screen changes on its own (a list refreshed, a dialog popped up). */
    fun change(next: ScreenState) {
        screen = next
        lastEventAt = clock() + 1
    }

    private fun act(a: Act) {
        log += a
        rules(a)?.let { change(it) }
    }

    override val ownPackage: String = "com.oro.app"
    override var lastEventAt: Long = 0L
        private set

    override fun uptime(): Long = clock()
    override fun capture(maxLabel: Int): ScreenState = screen.copy(capturedAt = clock()).also { latest = it }
    override fun isLatest(state: ScreenState): Boolean = state === latest
    override fun textVisible(text: String): Boolean = screen.elements.any { it.label.contains(text, ignoreCase = true) }
    override suspend fun settle(quietMs: Long, maxMs: Long, minMs: Long) = delay(minMs)

    override suspend fun tap(x: Int, y: Int): Boolean {
        lastHit = screen.hitTest(x, y)?.let { TapHit(clock(), it.role, it.label, it.bounds) }
        act(Act.TapAt(x, y))
        return true
    }
    override suspend fun longPress(x: Int, y: Int, durationMs: Long): Boolean { act(Act.TapAt(x, y)); return true }
    override suspend fun doubleTap(x: Int, y: Int): Boolean { act(Act.TapAt(x, y)); return true }
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean = true
    override suspend fun drag(x1: Int, y1: Int, x2: Int, y2: Int, holdMs: Long, moveMs: Long): Boolean = true
    override suspend fun pinch(x: Int, y: Int, zoomIn: Boolean): Boolean = true

    override suspend fun tapElement(e: ScreenElement, screen: ScreenState): String {
        act(Act.Tap(e.label, e.index))
        return "Tapped [${e.index}] ${e.role} \"${e.label}\"."
    }

    override suspend fun longPressElement(e: ScreenElement, screen: ScreenState): String {
        act(Act.Tap(e.label, e.index))
        return "Long-pressed [${e.index}]."
    }

    override suspend fun doubleTapElement(e: ScreenElement, screen: ScreenState): String {
        act(Act.Tap(e.label, e.index))
        return "Double-tapped [${e.index}]."
    }

    override suspend fun type(e: ScreenElement?, screen: ScreenState, text: String, append: Boolean): String {
        act(Act.Type(e?.label, text))
        return "Typed \"$text\"."
    }

    override suspend fun submit(): String { act(Act.Submit); return "Pressed Enter on the keyboard." }
    override suspend fun media(action: String): String { act(Act.Media(action)); return "Sent media $action to the current player." }

    override suspend fun scroll(direction: String, e: ScreenElement?, screen: ScreenState?, amount: Float): String {
        act(Act.Scroll(direction, e?.index))
        return "Scrolled $direction."
    }

    override suspend fun setValue(e: ScreenElement, screen: ScreenState, value: String, durationMs: Long?): String {
        act(Act.SetValue(e.label, e.index, value, durationMs))
        return setValueResult(e, value)
    }

    override suspend fun seekMedia(position: String, pkg: String?): MediaControl.SeekResult =
        seekResult(position, pkg).also { if (it is MediaControl.SeekResult.Done) act(Act.Seek(position, pkg)) }

    /** Like the phone: only a screen with a video player has one to tap. */
    override suspend fun showPlayerControls(): Boolean {
        if (screen.elements.none { it.viewId.orEmpty().contains("player") }) return false
        act(Act.ShowControls)
        return true
    }

    override suspend fun clickAfter(since: Long, x: Int, y: Int, waitMs: Long): TapHit? =
        lastHit?.takeIf { it.at >= since && TapFeedback.near(it, x, y, slop = 0) }

    override fun global(key: String): String { act(Act.Key(key)); return "Pressed $key." }
    override suspend fun openApp(name: String): String { act(Act.App(name)); return appResult(name) }
    override fun openUrl(url: String): String { act(Act.Url(url)); return "Opened $url." }
    override fun bringToFront(packageName: String): Boolean = false
    override fun oroInFront(): Boolean = false
    override fun quickAction(args: QuickActions.Args): String { act(Act.Quick(args)); return quickResult(args) }
    override fun apps(): List<AppCatalog.App> = installed
    override fun status(): String = "Thursday 22 October, 21:40 · battery 80% · Wi-Fi"
    override suspend fun screenshot(state: ScreenState): String? = null
}

/** A model that answers each turn from a script and remembers what it was shown. */
internal class ScriptedModel(private vararg val turns: (AgentRequest) -> Flow<StreamEvent>) : AgentModelClient {

    /** Per turn: everything the model received since its own last message (task or tool results). */
    val seen = mutableListOf<String>()
    private var next = 0

    override suspend fun stream(request: AgentRequest): Flow<StreamEvent> {
        seen += request.messages.takeLastWhile { it.role != "assistant" }.joinToString("\n") { it.content.orEmpty() }
        val turn = turns.getOrNull(next++) ?: return flowOf(StreamEvent.Started, StreamEvent.ContentDelta("Done."), StreamEvent.Completed)
        return turn(request)
    }

    companion object {
        /** One turn that calls these tools, as `name to jsonArguments`. */
        fun calls(vararg calls: Pair<String, String>): (AgentRequest) -> Flow<StreamEvent> = {
            flowOf(
                StreamEvent.Started,
                StreamEvent.ToolCalls(calls.mapIndexed { i, (name, args) -> ToolCallResponse("call_$i", "function", FunctionCall(name, args)) }),
                StreamEvent.FinishReason("tool_calls"),
                StreamEvent.Completed,
            )
        }

        /** One turn that answers in plain text, with no tool call. */
        fun text(reply: String): (AgentRequest) -> Flow<StreamEvent> = {
            flowOf(StreamEvent.Started, StreamEvent.ContentDelta(reply), StreamEvent.FinishReason("stop"), StreamEvent.Completed)
        }

        val model = AiModel(id = "bench", name = "Bench", provider = AiProvider.CUSTOM, supportsToolCalls = true)
    }
}
