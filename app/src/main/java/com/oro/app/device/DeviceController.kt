package com.oro.app.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Every physical action Orobi can take on the device, each returning a short,
 * factual outcome for the model.
 *
 * ### Reliability rules
 *  - Prefer the node's own accessibility action (ACTION_CLICK, SET_TEXT,
 *    SCROLL_FORWARD...) when it exists -- it works even when something
 *    overlaps the view -- and fall back to a real gesture at the element's
 *    centre, which works for Compose, WebViews, games and custom views.
 *  - The same for everything else that has an API: the media session for
 *    playback, SET_PROGRESS for sliders, the keyboard connection for text a
 *    field would not take. A tap by coordinates reports what took it.
 *  - After every action wait for the UI to settle (no accessibility events
 *    for a short quiet period, capped), so the next observation shows the
 *    result rather than a half-drawn transition.
 *  - Gestures are injected in screen pixels and clamped to the display. The
 *    floating island steps out of the way only when a gesture would land on
 *    it ([AgentOverlay.clearPathFor]), so the user's Stop keeps working.
 *  - An element is acted on only through the live node of the capture it
 *    came from, refreshed first: a node that left the screen is reported, never
 *    replaced by a blind tap at its old coordinates.
 */
class DeviceController(private val service: OroAccessibilityService) : Phone {

    // The space gestures are injected in: the whole display, bars included
    // (see [DisplaySize]), the same one the screen's bounds are measured in.
    // Read many times per action, so kept for a moment; a rotation outlives it.
    @Volatile private var display = Rect()
    @Volatile private var displayAt = 0L
    private val displayRect: Rect
        get() {
            val now = SystemClock.uptimeMillis()
            if (display.isEmpty || now - displayAt > DISPLAY_TTL_MS) {
                display = DisplaySize.of(service)
                displayAt = now
            }
            return display
        }
    private val width get() = displayRect.width()
    private val height get() = displayRect.height()

    // ------------------------------------------------------------ observe

    override val ownPackage: String get() = service.packageName

    override val lastEventAt: Long get() = service.lastEventAt

    override val lastChangeAt: Long get() = service.activity.lastChangeAt

    override fun uptime(): Long = SystemClock.uptimeMillis()

    override fun capture(maxLabel: Int): ScreenState = ScreenReader.capture(service, maxLabel)

    override fun isLatest(state: ScreenState): Boolean = ScreenReader.isLatest(state)

    override fun quickAction(args: QuickActions.Args): String = QuickActions.run(service, args)

    override fun apps(): List<AppCatalog.App> = AppCatalog.apps(service)

    override fun status(): String = DeviceInfo.status(service)

    override suspend fun screenshot(state: ScreenState): String? {
        service.overlay.clearMarksForCapture()
        return ScreenshotTaker.capture(service, state)
    }

    /**
     * Whether [text] is visible in the active window, using the platform's
     * own text search (run in the app's process). Much cheaper than a full
     * capture, so wait_for can poll with it.
     */
    override fun textVisible(text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val hits = runCatching { root.findAccessibilityNodeInfosByText(text) }.getOrNull().orEmpty()
        return hits.any { it.isVisibleToUser }
    }

    /**
     * Waits until the UI has been quiet (no accessibility events) for
     * [quietMs], after at least [minMs] and at most [maxMs].
     *
     * Event-driven rather than a fixed sleep: a tap on a static list settles
     * in ~150 ms, an app launch takes the full budget. The service stamps
     * every event, so polling the stamp costs nothing.
     */
    /**
     * Waits for the screen to settle after an action: [quietMs] without
     * events, at most [maxMs]. A screen that was busy before (a playing
     * video, an animation) never goes quiet, so there the wait ends once
     * nothing but values has changed for [quietMs], and within [BUSY_CAP_MS]:
     * it used to sit out the whole cap after every action.
     */
    override suspend fun settle(quietMs: Long, maxMs: Long, minMs: Long) {
        val start = SystemClock.uptimeMillis()
        val activity = service.activity
        val busy = activity.noisy(start)
        val cap = if (busy) minOf(maxMs, BUSY_CAP_MS) else maxMs
        delay(minMs)
        while (true) {
            val now = SystemClock.uptimeMillis()
            if (now - start >= cap) return
            if (now - activity.lastAt >= quietMs) return
            if (busy && now - activity.lastChangeAt >= quietMs) return
            delay(30)
        }
    }

    // ---------------------------------------------------------- gestures

    override suspend fun tap(x: Int, y: Int): Boolean = tapAt(x, y, frame = null)

    override suspend fun longPress(x: Int, y: Int, durationMs: Long): Boolean = longPressAt(x, y, durationMs, frame = null)

    override suspend fun doubleTap(x: Int, y: Int): Boolean = doubleTapAt(x, y, frame = null)

    // The mark goes where the finger does: the clamped point, not the asked-for one.
    private suspend fun tapAt(x: Int, y: Int, frame: Rect?): Boolean {
        service.overlay.showTouch(c(x, width), c(y, height), bounds = frame)
        return gestures(region(cx(x), cy(y))) { dispatch(stroke(path(x, y), 0, 60)) }
    }

    private suspend fun longPressAt(x: Int, y: Int, durationMs: Long, frame: Rect?): Boolean {
        service.overlay.showTouch(c(x, width), c(y, height), long = true, bounds = frame)
        return gestures(region(cx(x), cy(y))) { dispatch(stroke(path(x, y), 0, durationMs)) }
    }

    private suspend fun doubleTapAt(x: Int, y: Int, frame: Rect?): Boolean {
        service.overlay.showTouch(c(x, width), c(y, height), bounds = frame)
        return gestures(region(cx(x), cy(y))) {
            val ok = dispatch(stroke(path(x, y), 0, 50))
            delay(90)
            ok && dispatch(stroke(path(x, y), 0, 50))
        }
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean {
        service.overlay.showSwipe(c(x1, width), c(y1, height), c(x2, width), c(y2, height))
        val p = Path().apply { moveTo(cx(x1), cy(y1)); lineTo(cx(x2), cy(y2)) }
        return gestures(region(cx(x1), cy(y1), cx(x2), cy(y2))) { dispatch(stroke(p, 0, durationMs.coerceIn(80, 3_000))) }
    }

    /** Press-hold-move-release, for reordering lists, moving icons, sliders. */
    override suspend fun drag(x1: Int, y1: Int, x2: Int, y2: Int, holdMs: Long, moveMs: Long): Boolean {
        service.overlay.showSwipe(c(x1, width), c(y1, height), c(x2, width), c(y2, height))
        return gestures(region(cx(x1), cy(y1), cx(x2), cy(y2))) {
            if (Build.VERSION.SDK_INT >= 26) {
                val hold = GestureDescription.StrokeDescription(path(x1, y1), 0, holdMs, true)
                if (!dispatch(GestureDescription.Builder().addStroke(hold).build())) return@gestures false
                val move = Path().apply { moveTo(cx(x1), cy(y1)); lineTo(cx(x2), cy(y2)) }
                val moveStroke = hold.continueStroke(move, 0, moveMs, false)
                dispatch(GestureDescription.Builder().addStroke(moveStroke).build())
            } else {
                val p = Path().apply { moveTo(cx(x1), cy(y1)); lineTo(cx(x2), cy(y2)) }
                dispatch(stroke(p, 0, holdMs + moveMs))
            }
        }
    }

    /**
     * Two-finger pinch around a centre. [zoomIn] spreads the fingers. The
     * centre moves inward when needed so neither finger leaves the screen (a
     * stroke with negative coordinates is rejected outright).
     */
    override suspend fun pinch(x: Int, y: Int, zoomIn: Boolean): Boolean {
        val near = 60f
        val far = (minOf(width, height) * 0.3f)
        val (from, to) = if (zoomIn) near to far else far to near
        val maxX = (width - 1).toFloat()
        val centre = cx(x).coerceIn(far + 1f, (maxX - far).coerceAtLeast(far + 1f))
        val y0 = cy(y)
        fun fx(v: Float) = v.coerceIn(1f, maxX)
        val a = Path().apply { moveTo(fx(centre - from), y0); lineTo(fx(centre - to), y0) }
        val b = Path().apply { moveTo(fx(centre + from), y0); lineTo(fx(centre + to), y0) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(a, 0, 450))
            .addStroke(GestureDescription.StrokeDescription(b, 0, 450))
            .build()
        return gestures(region(fx(centre - far), y0, fx(centre + far), y0)) { dispatch(g) }
    }

    // ---------------------------------------------------- element actions

    /**
     * Taps [e], an element of [screen]: the node's own click action when it
     * (or its clickable parent) has one, else a real tap at where the node is
     * now. If the node has left the screen nothing is tapped.
     */
    override suspend fun tapElement(e: ScreenElement, screen: ScreenState): String {
        val node = liveNode(e, screen) ?: return gone(e)
        val target = clickableSelfOrAncestor(node)
        // Where the node is now, not where the capture saw it: a list that
        // settled after the capture moved it, and the mark has to follow.
        val live = liveBounds(node)
        // A part inside a bigger clickable (a menu icon in a card) is tapped
        // where it is, not through the card's own click ([TapPlan.choose]).
        val way = if (target == null) TapPlan.Way.GESTURE else {
            val own = live ?: Rect(e.bounds.left, e.bounds.top, e.bounds.right, e.bounds.bottom)
            val parent = if (target === node) null else liveBounds(target)
            TapPlan.choose(
                selfClickable = target === node,
                element = Bounds(own.left, own.top, own.right, own.bottom),
                parent = parent?.let { Bounds(it.left, it.top, it.right, it.bottom) } ?: if (target === node) null else e.bounds,
                covered = TapPlan.covered(screen, e),
            )
        }
        if (target != null && way != TapPlan.Way.GESTURE && target.isEnabled && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            val at = live ?: Rect(e.bounds.left, e.bounds.top, e.bounds.right, e.bounds.bottom)
            service.overlay.showTouch(at.centerX(), at.centerY(), bounds = at)
            return "Tapped [${e.index}] ${e.describe()}."
        }
        if (screen.underKeyboard(e)) return underKeyboard(e)
        val (x, y) = centreOf(node, e)
        val at = SystemClock.uptimeMillis()
        return if (tapAt(x, y, live)) "Tapped [${e.index}] ${e.describe()} at ($x,$y) ${TapFeedback.line(clickAfter(at, x, y))}."
        else "The tap on [${e.index}] could not be performed."
    }

    override suspend fun longPressElement(e: ScreenElement, screen: ScreenState): String {
        val node = liveNode(e, screen) ?: return gone(e)
        val target = longClickableSelfOrAncestor(node)
        val live = liveBounds(node)
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            val at = live ?: Rect(e.bounds.left, e.bounds.top, e.bounds.right, e.bounds.bottom)
            service.overlay.showTouch(at.centerX(), at.centerY(), long = true, bounds = at)
            return "Long-pressed [${e.index}] ${e.describe()}."
        }
        if (screen.underKeyboard(e)) return underKeyboard(e)
        val (x, y) = centreOf(node, e)
        return if (longPressAt(x, y, LONG_PRESS_MS, live)) "Long-pressed [${e.index}] ${e.describe()}."
        else "The long press on [${e.index}] could not be performed."
    }

    /** Double-taps [e] where its node is now (zoom, like), or reports it gone. */
    override suspend fun doubleTapElement(e: ScreenElement, screen: ScreenState): String {
        val node = liveNode(e, screen) ?: return gone(e)
        if (screen.underKeyboard(e)) return underKeyboard(e)
        val (x, y) = centreOf(node, e)
        return if (doubleTapAt(x, y, liveBounds(node))) "Double-tapped [${e.index}] ${e.describe()}." else "The double tap failed."
    }

    /**
     * Types into [e] (an element of [screen]) or, without one, the focused
     * field. Replaces the content unless [append]. ACTION_SET_TEXT first, then
     * the field is read back: a web or Flutter field can take it and put its
     * own state back. Where it did not take, Android 13+ types through the
     * service's keyboard connection, as a keyboard does; else the text is
     * pasted -- never into a password field, and the clip is marked sensitive
     * and removed again right after.
     */
    override suspend fun type(e: ScreenElement?, screen: ScreenState, text: String, append: Boolean): String {
        typedThroughIme = false
        var node: AccessibilityNodeInfo? = null
        if (e != null) {
            node = liveNode(e, screen) ?: return "Skipped: that field is no longer on screen."
            if (!node.isEditable) node = findEditableDescendant(node) ?: node
        }
        if (node == null) node = focusedInput()
        if (node == null) {
            return "No text field is focused. Tap the input first (or pass its index), then type."
        }
        var focusedAt = 0L
        if (!node.isFocused) {
            focusedAt = SystemClock.uptimeMillis()
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(150)
        }
        liveBounds(node)?.let { service.overlay.showField(it) }
        val existing = node.text?.toString().takeUnless { showsHint(node) || node.isPassword }.orEmpty()
        val newText = if (append) existing + text else text
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText) }
        val set = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        // A password cannot be read back: one that took SET_TEXT is trusted, as before.
        if (set && (node.isPassword || readBack(node, newText) != TypedText.Readback.LOST)) return typed(node, text, append)
        // The keyboard connection reaches a password field only when it refused SET_TEXT outright.
        if (Build.VERSION.SDK_INT >= 33 && (!node.isPassword || !set) && imeType(node, text, append, focusedAt)) {
            typedThroughIme = true
            if (node.isPassword || readBack(node, newText) != TypedText.Readback.LOST) return typed(node, text, append)
            return "The text could not be typed: the field shows \"${shown(node).take(60)}\"."
        }
        if (node.isPassword) {
            return "The password field refused the text. Tap it and try again, or ask the user to type it themselves."
        }
        // Paste fallback: the clip is marked sensitive (keyboards leave it out
        // of their history) and removed once the paste is done.
        val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val previous = runCatching { cm.primaryClip }.getOrNull()
        val clip = ClipData.newPlainText("oro", text)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        // What the field holds now: a SET_TEXT that did not stick may still have changed it.
        val current = if (set) shown(node) else existing
        cm.setPrimaryClip(clip)
        try {
            if (!append) {
                val sel = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, current.length)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
            }
            val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            delay(120) // let the app read the clip before it goes
            return if (pasted) "Pasted \"${text.take(60)}\" into the field."
            else "The field rejected the text. Try tapping it first, or it may not accept input."
        } finally {
            // Not suspending: it must run even when the run is being stopped.
            runCatching {
                when {
                    previous != null -> cm.setPrimaryClip(previous)
                    Build.VERSION.SDK_INT >= 28 -> cm.clearPrimaryClip()
                    else -> cm.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }

    /** The node behind [e] if [screen] is the latest capture and the node is still on screen. */
    private fun liveNode(e: ScreenElement, screen: ScreenState): AccessibilityNodeInfo? {
        val node = ScreenReader.nodeFor(screen, e.index) ?: return null
        return if (runCatching { node.refresh() }.getOrDefault(false) && node.isVisibleToUser) node else null
    }

    private fun gone(e: ScreenElement) = "Skipped: [${e.index}] ${e.describe()} is no longer on screen."

    /** A gesture there would press a key: say how to get to it instead. */
    private fun underKeyboard(e: ScreenElement) =
        "Skipped: [${e.index}] ${e.describe()} is under the keyboard. press_key(\"back\") closes the keyboard; then tap it."

    /** Where [node] is now (it may have moved since the capture), else where [e] was. */
    private fun centreOf(node: AccessibilityNodeInfo, e: ScreenElement): Pair<Int, Int> {
        val r = liveBounds(node) ?: return e.centerX to e.centerY
        return r.centerX() to r.centerY()
    }

    /** [node]'s bounds on screen now, cut to the display; null when it has no real size. */
    private fun liveBounds(node: AccessibilityNodeInfo): Rect? {
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (!r.intersect(0, 0, width, height)) return null
        return r.takeIf { it.width() > 1 && it.height() > 1 }
    }

    // ------------------------------------------------------------- typing

    /** The last type() went through the keyboard connection, so submit() goes that way too. */
    @Volatile private var typedThroughIme = false

    private fun typed(node: AccessibilityNodeInfo, text: String, append: Boolean): String =
        if (node.isPassword) "Typed the password." else "Typed \"${text.take(60)}\"${if (append) " (appended)" else ""}."

    /** What [node] shows now, its hint and a password's dots aside. */
    private fun shown(node: AccessibilityNodeInfo): String {
        runCatching { node.refresh() }
        return if (showsHint(node) || node.isPassword) "" else node.text?.toString().orEmpty()
    }

    /**
     * Whether [node] kept [expected], read after a beat: a web page puts its
     * own state back on its next frame. Read twice before calling it lost: a
     * keyboard commit is applied, and a web page's tree updated, a moment later.
     */
    private suspend fun readBack(node: AccessibilityNodeInfo, expected: String): TypedText.Readback {
        var result = TypedText.Readback.UNKNOWN
        for (wait in READ_BACK_MS) {
            delay(wait)
            if (!runCatching { node.refresh() }.getOrDefault(false)) return TypedText.Readback.UNKNOWN
            result = TypedText.check(expected, if (showsHint(node)) "" else node.text?.toString())
            if (result != TypedText.Readback.LOST) return result
        }
        return result
    }

    /**
     * Types through the service's keyboard connection: select all (or go to
     * the end), then commit the text as a keyboard does, so a web or Flutter
     * field sees real input. False when no connection is open on [node]'s
     * app -- or, for a field focused at [focusedAt], not yet on that field:
     * text committed then would go into the field before it.
     */
    @RequiresApi(33)
    private suspend fun imeType(node: AccessibilityNodeInfo, text: String, append: Boolean, focusedAt: Long): Boolean {
        val ime = service.inputMethod ?: return false
        val pkg = node.packageName?.toString()
        val start = SystemClock.uptimeMillis()
        while (!(ime.currentInputStarted && ime.currentInputEditorInfo?.packageName == pkg && service.inputStartedAt >= focusedAt)) {
            if (SystemClock.uptimeMillis() - start > IME_WAIT_MS) return false
            delay(50)
        }
        val ic = ime.currentInputConnection ?: return false
        if (append) {
            val around = runCatching { ic.getSurroundingText(MAX_FIELD_CHARS, MAX_FIELD_CHARS, 0) }.getOrNull()
            val end = around?.let { it.offset + it.text.length } ?: shown(node).length
            ic.setSelection(end, end)
        } else {
            ic.performContextMenuAction(android.R.id.selectAll)
        }
        ic.commitText(text, 1, null)
        return true
    }

    /** Enter through the keyboard connection: the field's own action (Search, Send, Go), else Enter on a one-line field. */
    @RequiresApi(33)
    private fun imeEnter(): String? {
        val ime = service.inputMethod ?: return null
        if (!ime.currentInputStarted) return null
        val ic = ime.currentInputConnection ?: return null
        val info = ime.currentInputEditorInfo ?: return null
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val name = when (action) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            EditorInfo.IME_ACTION_PREVIOUS -> "Previous"
            else -> null
        }
        if (name != null) {
            ic.performEditorAction(action)
            return "Pressed the keyboard's $name key."
        }
        // No action of its own: Enter, unless Enter starts a new line here.
        if (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) return null
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
        ic.sendKeyEvent(KeyEvent(now, now + 40, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
        return "Pressed Enter on the keyboard."
    }

    /**
     * Presses the keyboard's action key (search/send/go/enter): the IME
     * action on Android 11+, the field's editor action through the keyboard
     * connection on 13+ (first, for a field that was typed that way: such
     * fields often ignore the IME action), else the keyboard's own action
     * key, found by its label in the keyboard window.
     */
    override suspend fun submit(): String {
        if (Build.VERSION.SDK_INT >= 33 && typedThroughIme) imeEnter()?.let { return it }
        val node = focusedInput()
        if (node != null && Build.VERSION.SDK_INT >= 30) {
            val ime = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
            if (node.performAction(ime)) return "Pressed Enter on the keyboard."
        }
        if (Build.VERSION.SDK_INT >= 33 && !typedThroughIme) imeEnter()?.let { return it }
        val key = keyboardActionKey()
        if (key != null) {
            val r = Rect().also { key.getBoundsInScreen(it) }
            if (key.performAction(AccessibilityNodeInfo.ACTION_CLICK) || tap(r.centerX(), r.centerY())) {
                return "Pressed the keyboard's action key."
            }
        }
        return "Could not press Enter here. Look for a Send/Search/Go button on screen and tap it instead."
    }

    private fun keyboardActionKey(): AccessibilityNodeInfo? {
        val ime = runCatching { service.windows }.getOrNull().orEmpty()
            .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.root ?: return null
        var found: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (found != null || depth > 25) return
            val label = (n.contentDescription ?: n.text)?.toString()?.trim()?.lowercase()
            if (label != null && label in KEYBOARD_ACTION_KEYS && n.isVisibleToUser) { found = n; return }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(ime, 0)
        return found
    }

    // ------------------------------------------------------------- media

    /**
     * Media control for whatever is playing. With Notification access it goes
     * to the playing app's own media session: its transport controls, and
     * exact 10 s seeks for forward and rewind. Without it, system media keys,
     * which every player honours, except seeking in YouTube-style players,
     * which ignore fast-forward keys but seek 10 s on a double tap at the
     * player's side; the result then says how to turn access on.
     */
    override suspend fun media(action: String): String {
        val a = action.lowercase().replace(' ', '_')
        val front = frontPackage()
        if (a == "status") return MediaControl.status(service, front)
        val sessions = MediaControl.sessions(service)
        if (sessions != null) {
            if (a == "forward" || a == "fast_forward" || a == "rewind") {
                val jump = MediaControl.Seek.By(if (a == "rewind") -SEEK_STEP_MS else SEEK_STEP_MS)
                (MediaControl.seek(service, jump, front, null) as? MediaControl.SeekResult.Done)?.let { return it.text }
            }
            MediaControl.pick(sessions, front)?.let { c -> MediaControl.transport(service, c, a)?.let { return it } }
        }
        val hint = if (sessions == null) " " + MediaControl.ACCESS_HINT else ""
        // Double-tap seeking is YouTube's gesture only: in Instagram, X or any
        // reels feed a double tap means Like.
        if ((a == "forward" || a == "rewind") && AdSkipper.isYouTube(front)) {
            val player = playerBounds()
            if (player != null) {
                val x = if (a == "forward") player.left + player.width * 4 / 5 else player.left + player.width / 5
                val ok = doubleTap(x, player.centerY)
                return if (ok) (if (a == "forward") "Skipped forward 10 s in the player." else "Went back 10 s in the player.") + hint
                else "The seek gesture failed."
            }
        }
        val code = when (a) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play_pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "forward", "fast_forward" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "rewind" -> KeyEvent.KEYCODE_MEDIA_REWIND
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return "Unknown media action \"$action\". Use play, pause, play_pause, next, previous, forward, rewind, stop, seek or status."
        }
        val am = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "No audio service."
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now + 40, KeyEvent.ACTION_UP, code, 0))
        return "Sent media ${a.replace('_', ' ')} to the current player.$hint"
    }

    override suspend fun seekMedia(position: String, pkg: String?): MediaControl.SeekResult {
        val seek = MediaControl.parsePosition(position)
            ?: return MediaControl.SeekResult.Unavailable("\"$position\" is not a position")
        return MediaControl.seek(service, seek, frontPackage(), pkg)
    }

    /**
     * A quarter of the way down the player, mid-width: clear of play/pause in
     * the middle and of the bar at the bottom, should the controls be up
     * after all (a tap there then only hides them).
     */
    override suspend fun showPlayerControls(): Boolean {
        val player = playerBounds() ?: return false
        return tap(player.centerX, player.top + player.height / 4)
    }

    /** The video player's area on screen, found by its view id (YouTube and most native players name it so). */
    private fun playerBounds(): Bounds? {
        val root = service.rootInActiveWindow ?: return null
        var best: Bounds? = null
        val r = Rect()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 30 || !n.isVisibleToUser) return
            val id = n.viewIdResourceName?.lowercase()
            // Not a Shorts or reels player: there a tap pauses and a double tap likes.
            if (id != null && ("player" in id || "video" in id) && "button" !in id && "control" !in id &&
                "reel" !in id && "short" !in id
            ) {
                n.getBoundsInScreen(r)
                val b = Bounds(r.left, r.top, r.right, r.bottom)
                if (b.width >= width * 0.6 && b.height >= 120 && (best == null || b.area > best!!.area)) best = b
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return best
    }

    // ----------------------------------------------------------- sliders

    /**
     * set_value: the slider's own ACTION_SET_PROGRESS (exact), else its
     * scroll steps toward the value, read back after each, else one
     * press-hold-move from the thumb ([slide]). Each way is checked by reading
     * the value back; one that falls short hands over to the next.
     */
    override suspend fun setValue(e: ScreenElement, screen: ScreenState, value: String, durationMs: Long?): String {
        val node = liveNode(e, screen) ?: return gone(e)
        val asked = Sliders.parse(value)
            ?: return "Could not read \"$value\" as a value: give a number (35), a percentage (\"40%\") or a time (\"1:30\")."
        val length = durationMs ?: if (asked is Sliders.Value.Time) sessionLength(screen.packageName) else null
        val range = ScreenReader.rangeOf(node)
        val box = liveBounds(node)?.let { Bounds(it.left, it.top, it.right, it.bottom) } ?: e.bounds
        val fraction = Sliders.fraction(asked, range, length) ?: return "Could not place \"$value\" on [${e.index}] ${e.describe()}: " +
            (if (asked is Sliders.Value.Time) "its length is unknown." else "it does not report its range.") + " Give a percentage like \"40%\"."
        val whole = runCatching { node.rangeInfo?.type }.getOrNull() == AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT
        val target = range?.let { Sliders.target(asked, it, length, whole) }
        val ids = runCatching { node.actionList.map { it.id } }.getOrNull().orEmpty()
        val plan = Sliders.plan(
            setProgress = AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id in ids,
            scroll = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD in ids || AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD in ids,
            readable = target != null,
            // A press under the keyboard would type instead.
            draggable = box.width >= MIN_SLIDER_DP * service.resources.displayMetrics.density && !screen.underKeyboard(e),
        )
        if (plan.isEmpty()) {
            return if (screen.underKeyboard(e)) underKeyboard(e) else "Could not set [${e.index}] ${e.describe()}: it is not a slider that can be moved."
        }
        var step: Float? = null
        var used: Sliders.Method? = null
        for (method in plan) {
            if (method != Sliders.Method.DRAG) {
                val at = Rect(box.left, box.top, box.right, box.bottom)
                service.overlay.showTouch(Sliders.xFor(fraction, box), box.centerY, bounds = at)
            }
            val moved = when (method) {
                Sliders.Method.SET_PROGRESS -> node.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                    Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, target ?: 0f) },
                )
                Sliders.Method.SCROLL_STEPS -> stepTo(node, target ?: 0f, range ?: break).also { step = it } != null
                Sliders.Method.DRAG -> slide(node, box, fraction, range)
            }
            if (!moved) continue
            used = method
            delay(SLIDER_SETTLE_MS)
            val now = readRange(node) ?: break
            if (target == null || Sliders.reached(now.current, target, now, tolerance(now, step, whole))) break
        }
        if (used == null) return "Could not move [${e.index}] ${e.describe()}: it took none of its own actions or a drag."
        val now = readRange(node)
        return when {
            now == null || target == null ->
                "Moved [${e.index}] ${e.describe()} to \"$value\" (${Math.round(fraction * 100)}% along it); it does not report its value."
            Sliders.reached(now.current, target, now, SLIDER_TOLERANCE) -> "Set [${e.index}] ${e.describe()} to ${now.render()}."
            Sliders.reached(now.current, target, now, tolerance(now, step, whole)) ->
                "Set [${e.index}] ${e.describe()} to ${now.render()}, the nearest step to \"$value\"."
            else -> "[${e.index}] ${e.describe()} is at ${now.render()}: it could not be set to \"$value\"."
        }
    }

    /** How close counts as there: 1% of the range, half a step when it moves in steps, a whole unit when it counts in them. */
    private fun tolerance(range: ValueRange, step: Float?, whole: Boolean): Float {
        val span = (range.max - range.min).coerceAtLeast(1e-6f)
        return maxOf(SLIDER_TOLERANCE, (step ?: 0f) / 2 / span + 1e-4f, if (whole) 0.5f / span else 0f)
    }

    /** [node]'s value now, or null when it is gone or reports none. */
    private fun readRange(node: AccessibilityNodeInfo): ValueRange? =
        if (runCatching { node.refresh() }.getOrDefault(false)) ScreenReader.rangeOf(node) else null

    /** The length the app in front reports for what it plays, for a time on its seek bar. */
    private fun sessionLength(pkg: String): Long? =
        MediaControl.sessions(service)?.filter { it.packageName == pkg }?.let { MediaControl.pick(it, pkg) }?.let(MediaControl::durationOf)

    /**
     * Scroll steps toward [target], reading the value after each. The last
     * step's size (how near steps can get), or null when they never moved it.
     */
    private suspend fun stepTo(node: AccessibilityNodeInfo, target: Float, start: ValueRange): Float? {
        var cur = start
        var step: Float? = null
        var last: Boolean? = null
        var turns = 0
        repeat(MAX_STEPS) {
            val forward = Sliders.nextStep(cur.current, target, step, start.max - start.min) ?: return step ?: 0f
            // Uneven steps can straddle the value: back and forth twice is as near as they get.
            if (last != null && forward != last && ++turns > 1) return step
            last = forward
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (!node.performAction(action)) return step
            delay(STEP_SETTLE_MS)
            val next = readRange(node) ?: return step
            if (next.current == cur.current) return step
            step = abs(next.current - cur.current)
            cur = next
        }
        return step
    }

    /**
     * One press-hold-move: press on the thumb, hold, move to [fraction] of the
     * track, and -- where the slider reports its value while the finger is
     * down -- steer onto it from two readings ([Sliders.steer]), then let go.
     * It stays one touch throughout (continueStroke), so the app sees one
     * drag. Without a value to start from it presses where the value goes.
     */
    private suspend fun slide(node: AccessibilityNodeInfo, b: Bounds, fraction: Float, start: ValueRange?): Boolean {
        val y = b.centerY
        val x0 = Sliders.xFor(start?.fraction ?: fraction, b)
        val x1 = Sliders.xFor(fraction, b)
        service.overlay.showSwipe(c(x0, width), c(y, height), c(x1, width), c(y, height))
        return gestures(region(b.left.toFloat(), cy(y), b.right.toFloat(), cy(y))) {
            if (Build.VERSION.SDK_INT >= 26) steeredDrag(node, b, y, x0, x1, fraction)
            else dispatch(stroke(Path().apply { moveTo(cx(x0), cy(y)); lineTo(cx(x1), cy(y)) }, 0, SLIDE_HOLD_MS + SLIDE_MOVE_MS))
        }
    }

    @RequiresApi(26)
    private suspend fun steeredDrag(node: AccessibilityNodeInfo, b: Bounds, y: Int, x0: Int, x1: Int, fraction: Float): Boolean {
        var stroke = GestureDescription.StrokeDescription(path(x0, y), 0, SLIDE_HOLD_MS, true)
        if (!dispatch(GestureDescription.Builder().addStroke(stroke).build())) return false
        var x = x0
        // Each part starts where the last one ended, as continueStroke requires.
        suspend fun glide(to: Int, ms: Long, lift: Boolean): Boolean {
            val p = Path().apply { moveTo(cx(x), cy(y)); if (to != x) lineTo(cx(to), cy(y)) }
            stroke = stroke.continueStroke(p, 0, ms, !lift)
            x = to
            return dispatch(GestureDescription.Builder().addStroke(stroke).build())
        }
        var before = readRange(node)?.fraction
        var from = x0
        if (!glide(x1, SLIDE_MOVE_MS, lift = false)) return false
        var steers = 0
        while (steers++ < MAX_STEERS) {
            val f = readRange(node)?.fraction ?: break
            val f0 = before
            val next = if (f0 != null) Sliders.steer(from, f0, x, f, fraction, b) else null
            before = f
            from = x
            if (next == null || abs(next - x) < 2) break
            if (!glide(next, SLIDE_STEER_MS, lift = false)) return false
        }
        return glide(x, SLIDE_RELEASE_MS, lift = true)
    }

    override suspend fun clickAfter(since: Long, x: Int, y: Int, waitMs: Long): TapHit? {
        val slop = (FINGER_DP * service.resources.displayMetrics.density).toInt()
        return withTimeoutOrNull(waitMs) {
            service.lastClick.first { it != null && it.at >= since && TapFeedback.near(it, x, y, slop) }
        }
    }

    /** The app in front: the active window's, else the last one that came to the front. */
    private fun frontPackage(): String =
        runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: service.foregroundPackage

    override suspend fun scroll(direction: String, e: ScreenElement?, screen: ScreenState?, amount: Float): String {
        val dir = direction.lowercase()
        val sideways = dir == "left" || dir == "right"
        // With no element, only an up/down scroll goes to the biggest list: the
        // biggest list is almost always the vertical feed, and "left" must not
        // page it backwards.
        val node = e?.let { el -> screen?.let { ScreenReader.nodeFor(it, el.index) } }?.let { scrollableSelfOrAncestor(it) }
            ?: if (e == null && !sideways) largestScrollable() else null
        if (node != null) {
            val directional = directionalScroll(dir)
            if (directional != null && node.performAction(directional)) {
                return "Scrolled $dir${e?.let { " in [${it.index}]" } ?: ""}."
            }
            // Forward/backward move a list along its own axis, so they stand in
            // for a direction only when that axis is the one asked for.
            val alongAxis = dir == "forward" || dir == "back" || dir == "backward" ||
                (e == null && !sideways) || (e != null && (!e.scrollable || e.horizontal == sideways))
            val action = when (dir) {
                "down", "right", "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            if (alongAxis && node.performAction(action)) {
                return "Scrolled $dir${e?.let { " in [${it.index}]" } ?: ""}."
            }
        }
        // Gesture fallback within the element (or screen) bounds.
        val r = e?.bounds ?: Bounds(0, (height * 0.18).toInt(), width, (height * 0.85).toInt())
        val span = (if (dir == "up" || dir == "down") r.height else r.width) * amount.coerceIn(0.2f, 0.9f) / 2
        val cxp = r.centerX; val cyp = r.centerY
        val ok = when (dir) {
            "down" -> swipe(cxp, (cyp + span).toInt(), cxp, (cyp - span).toInt(), 380)
            "up" -> swipe(cxp, (cyp - span).toInt(), cxp, (cyp + span).toInt(), 380)
            "right" -> swipe((cxp + span).toInt(), cyp, (cxp - span).toInt(), cyp, 320)
            "left" -> swipe((cxp - span).toInt(), cyp, (cxp + span).toInt(), cyp, 320)
            else -> return "Unknown direction \"$direction\"; use up, down, left or right."
        }
        return if (ok) "Scrolled $dir (swipe)." else "Scrolling failed."
    }

    override fun global(key: String): String {
        val k = key.lowercase().replace(' ', '_')
        val action = when (k) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents", "recent_apps", "overview", "app_switch" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "power_dialog", "power" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            "lock", "lock_screen" -> if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN else return "Locking needs Android 9+."
            "screenshot" -> if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT else return "Screenshots need Android 9+."
            "dismiss_notifications" -> if (Build.VERSION.SDK_INT >= 31) AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE else return "Not supported on this Android version."
            else -> return "Unknown key \"$key\". Use back, home, recents, notifications, quick_settings, lock, screenshot, enter."
        }
        return if (service.performGlobalAction(action)) "Pressed ${k.replace('_', ' ')}." else "The ${k.replace('_', ' ')} action failed."
    }

    override suspend fun openApp(name: String): String {
        val app = AppCatalog.resolve(service, name)
            ?: return "No installed app matches \"$name\". Closest: " +
                AppCatalog.suggestions(service, name).joinToString { it.label } + ". Use list_apps to see all."
        if (app.packageName == service.packageName) {
            return "Refused: Oro runs this task and does not operate itself. Use the other apps."
        }
        if (!AppCatalog.launch(service, app)) return "${app.label} could not be launched."
        // Return as soon as the app is in front instead of after a fixed pause:
        // a warm app is there in ~150 ms, a cold one can take seconds.
        val start = SystemClock.uptimeMillis()
        while (service.foregroundPackage != app.packageName && SystemClock.uptimeMillis() - start < 3_000) delay(60)
        return if (service.foregroundPackage == app.packageName) "Opened ${app.label}." else "Opened ${app.label} (still loading)."
    }

    /**
     * Brings [packageName]'s existing task back to the front, where it was
     * (its launcher intent with NEW_TASK resumes the task instead of
     * restarting it). Used to return to the app the task was in after the user
     * answered a question in Oro.
     */
    override fun bringToFront(packageName: String): Boolean {
        if (packageName.isEmpty() || packageName == service.packageName) return false
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { service.startActivity(intent); true }.getOrDefault(false)
    }

    /**
     * Whether Oro's own screen is the app in front. While it is, the user is
     * looking at Oro (the device screen, the chat) and the agent must not
     * drive Oro's UI, so actions wait.
     */
    override fun oroInFront(): Boolean {
        val windows = runCatching { service.windows }.getOrNull() ?: return false
        val top = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.maxByOrNull { it.layer } ?: return false
        return runCatching { top.root?.packageName?.toString() }.getOrNull() == service.packageName
    }

    override fun openUrl(url: String): String =
        if (AppCatalog.openUri(service, url)) "Opened $url." else "No app can open $url."

    // ------------------------------------------------------------ helpers

    private fun ScreenElement.describe(): String = buildString {
        append(role)
        if (label.isNotBlank()) append(" \"").append(label.take(50)).append('"')
    }

    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 8) {
            if (n.isClickable) return n
            n = n.parent; depth++
        }
        return null
    }

    private fun longClickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 8) {
            if (n.isLongClickable) return n
            n = n.parent; depth++
        }
        return null
    }

    private fun scrollableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 12) {
            if (n.isScrollable) return n
            n = n.parent; depth++
        }
        return null
    }

    private fun findEditableDescendant(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        if (depth > 5) return null
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findEditableDescendant(c, depth + 1)?.let { return it }
        }
        return null
    }

    private fun focusedInput(): AccessibilityNodeInfo? =
        service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: service.rootInActiveWindow?.let { findFocusedEditable(it, 0) }

    private fun findFocusedEditable(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) return node
        if (depth > 40) return null
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findFocusedEditable(c, depth + 1)?.let { return it }
        }
        return null
    }

    private fun largestScrollable(): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || !n.isVisibleToUser) return
            if (n.isScrollable) {
                val r = Rect(); n.getBoundsInScreen(r)
                val area = r.width() * r.height()
                if (area > bestArea) { bestArea = area; best = n }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        service.rootInActiveWindow?.let { walk(it, 0) }
        return best
    }

    private fun directionalScroll(dir: String): Int? {
        if (Build.VERSION.SDK_INT < 29) return null
        return when (dir) {
            "down" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
            "up" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
            "left" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
            "right" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
            else -> null
        }
    }

    private fun showsHint(node: AccessibilityNodeInfo): Boolean =
        Build.VERSION.SDK_INT >= 26 && node.isShowingHintText

    private companion object {
        val KEYBOARD_ACTION_KEYS = setOf("enter", "search", "go", "send", "done", "next", "return", "↵", "⏎")

        /** Half a fingertip, around every injected touch, for the island's way-clearing. */
        const val FINGER_DP = 24f

        /** [Phone.longPress]'s default hold, for the element path that calls the private helper. */
        const val LONG_PRESS_MS = 700L

        const val DISPLAY_TTL_MS = 500L

        /** The longest a busy screen is waited on after an action. */
        const val BUSY_CAP_MS = 1_000L

        /** media forward/rewind through the session: the player's own double-tap step. */
        const val SEEK_STEP_MS = 10_000L

        /** When a typed field is read back: a web page restores its own state a frame later. */
        val READ_BACK_MS = longArrayOf(150L, 250L)
        /** How long a field just focused gets to start input on the keyboard connection. */
        const val IME_WAIT_MS = 600L
        const val MAX_FIELD_CHARS = 10_000

        const val MAX_STEPS = 40
        const val STEP_SETTLE_MS = 40L
        const val SLIDER_SETTLE_MS = 120L
        /** A slider within 1% of its range is where it was asked to be. */
        const val SLIDER_TOLERANCE = 0.01f
        /** Narrower than this, a drag along it has no room to be accurate. */
        const val MIN_SLIDER_DP = 24f
        const val SLIDE_HOLD_MS = 150L
        const val SLIDE_MOVE_MS = 350L
        const val SLIDE_STEER_MS = 150L
        const val SLIDE_RELEASE_MS = 40L
        const val MAX_STEERS = 2
    }

    private fun c(v: Int, max: Int) = v.coerceIn(1, max - 1)
    private fun cx(x: Int) = c(x, width).toFloat()
    private fun cy(y: Int) = c(y, height).toFloat()
    private fun path(x: Int, y: Int) = Path().apply { moveTo(cx(x), cy(y)) }
    private fun stroke(p: Path, start: Long, duration: Long) =
        GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, start, duration)).build()

    /**
     * The screen box a gesture through these points covers, padded by a
     * fingertip, for [AgentOverlay.clearPathFor].
     */
    private fun region(vararg xy: Float): Rect {
        val pad = (FINGER_DP * service.resources.displayMetrics.density).toInt()
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        var i = 0
        while (i + 1 < xy.size) {
            l = minOf(l, xy[i]); r = maxOf(r, xy[i])
            t = minOf(t, xy[i + 1]); b = maxOf(b, xy[i + 1])
            i += 2
        }
        return Rect(l.toInt() - pad, t.toInt() - pad, r.toInt() + pad, b.toInt() + pad)
    }

    /**
     * Runs injected gestures over [region]: an injected touch that would land
     * on the floating island must reach the app, so the island stops taking
     * touches for exactly as long as the gesture runs, and only if it is in the
     * way.
     */
    private suspend fun gestures(region: Rect, block: suspend () -> Boolean): Boolean {
        service.overlay.clearPathFor(region)
        return try { block() } finally { service.overlay.restoreTouch() }
    }

    private suspend fun dispatch(g: GestureDescription): Boolean = suspendCancellableCoroutine { cont ->
        val dispatched = service.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!dispatched && cont.isActive) cont.resume(false)
    }
}
