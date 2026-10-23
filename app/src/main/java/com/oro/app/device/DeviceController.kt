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
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Every physical action Orobi can take on the device, each returning a short,
 * factual outcome for the model.
 *
 * ### Reliability rules
 *  - Prefer the node's own accessibility action (ACTION_CLICK, SET_TEXT,
 *    SCROLL_FORWARD...) when it exists -- it works even when something
 *    overlaps the view -- and fall back to a real gesture at the element's
 *    centre, which works for Compose, WebViews, games and custom views.
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

    private val width get() = service.resources.displayMetrics.widthPixels
    private val height get() = service.resources.displayMetrics.heightPixels

    // ------------------------------------------------------------ observe

    override val ownPackage: String get() = service.packageName

    override val lastEventAt: Long get() = service.lastEventAt

    override fun uptime(): Long = SystemClock.uptimeMillis()

    override fun capture(maxLabel: Int): ScreenState = ScreenReader.capture(service, maxLabel)

    override fun isLatest(state: ScreenState): Boolean = ScreenReader.isLatest(state)

    override fun quickAction(args: QuickActions.Args): String = QuickActions.run(service, args)

    override fun apps(): List<AppCatalog.App> = AppCatalog.apps(service)

    override fun status(): String = DeviceInfo.status(service)

    override suspend fun screenshot(state: ScreenState): String? = ScreenshotTaker.capture(service, state)

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
    override suspend fun settle(quietMs: Long, maxMs: Long, minMs: Long) {
        val start = SystemClock.uptimeMillis()
        delay(minMs)
        while (SystemClock.uptimeMillis() - start < maxMs) {
            val idle = SystemClock.uptimeMillis() - service.lastEventAt
            if (idle >= quietMs) return
            delay(50)
        }
    }

    // ---------------------------------------------------------- gestures

    override suspend fun tap(x: Int, y: Int): Boolean {
        service.overlay.showTouch(x, y)
        return gestures(region(cx(x), cy(y))) { dispatch(stroke(path(x, y), 0, 60)) }
    }

    override suspend fun longPress(x: Int, y: Int, durationMs: Long): Boolean {
        service.overlay.showTouch(x, y, long = true)
        return gestures(region(cx(x), cy(y))) { dispatch(stroke(path(x, y), 0, durationMs)) }
    }

    override suspend fun doubleTap(x: Int, y: Int): Boolean {
        service.overlay.showTouch(x, y)
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
        if (target != null && target.isEnabled && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            service.overlay.showTouch(e.centerX, e.centerY)
            return "Tapped [${e.index}] ${e.describe()}."
        }
        if (screen.underKeyboard(e)) return underKeyboard(e)
        val (x, y) = centreOf(node, e)
        return if (tap(x, y)) "Tapped [${e.index}] ${e.describe()} at ($x,$y)."
        else "The tap on [${e.index}] could not be performed."
    }

    override suspend fun longPressElement(e: ScreenElement, screen: ScreenState): String {
        val node = liveNode(e, screen) ?: return gone(e)
        val target = longClickableSelfOrAncestor(node)
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            service.overlay.showTouch(e.centerX, e.centerY, long = true)
            return "Long-pressed [${e.index}] ${e.describe()}."
        }
        if (screen.underKeyboard(e)) return underKeyboard(e)
        val (x, y) = centreOf(node, e)
        return if (longPress(x, y)) "Long-pressed [${e.index}] ${e.describe()}."
        else "The long press on [${e.index}] could not be performed."
    }

    /** Double-taps [e] where its node is now (zoom, like), or reports it gone. */
    override suspend fun doubleTapElement(e: ScreenElement, screen: ScreenState): String {
        val node = liveNode(e, screen) ?: return gone(e)
        if (screen.underKeyboard(e)) return underKeyboard(e)
        val (x, y) = centreOf(node, e)
        return if (doubleTap(x, y)) "Double-tapped [${e.index}] ${e.describe()}." else "The double tap failed."
    }

    /**
     * Types into [e] (an element of [screen]) or, without one, the focused
     * field. Replaces the content unless [append]. Fields that refuse
     * ACTION_SET_TEXT get the text pasted -- never a password field, and the
     * clip is marked sensitive and removed again right after.
     */
    override suspend fun type(e: ScreenElement?, screen: ScreenState, text: String, append: Boolean): String {
        var node: AccessibilityNodeInfo? = null
        if (e != null) {
            node = liveNode(e, screen) ?: return "Skipped: that field is no longer on screen."
            if (!node.isEditable) node = findEditableDescendant(node) ?: node
        }
        if (node == null) node = focusedInput()
        if (node == null) {
            return "No text field is focused. Tap the input first (or pass its index), then type."
        }
        if (!node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(150)
        }
        val existing = node.text?.toString().takeUnless { showsHint(node) || node.isPassword }.orEmpty()
        val newText = if (append) existing + text else text
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText) }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return if (node.isPassword) "Typed the password." else "Typed \"${text.take(60)}\"${if (append) " (appended)" else ""}."
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
        cm.setPrimaryClip(clip)
        try {
            if (!append) {
                val sel = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, existing.length)
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
        val r = Rect().also { node.getBoundsInScreen(it) }
        return if (r.width() > 1 && r.height() > 1) r.centerX() to r.centerY() else e.centerX to e.centerY
    }

    /**
     * Presses the keyboard's action key (search/send/go/enter): the IME
     * action on Android 11+, else the keyboard's own action key, found by
     * its label in the keyboard window.
     */
    override suspend fun submit(): String {
        val node = focusedInput()
        if (node != null && Build.VERSION.SDK_INT >= 30) {
            val ime = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
            if (node.performAction(ime)) return "Pressed Enter on the keyboard."
        }
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
     * Media control for whatever is playing: system media keys, which every
     * player honours, except seeking in YouTube-style players, which ignore
     * fast-forward keys but seek 10 s on a double tap at the player's side.
     */
    override suspend fun media(action: String): String {
        val a = action.lowercase().replace(' ', '_')
        // Double-tap seeking is YouTube's gesture only: in Instagram, X or any
        // reels feed a double tap means Like.
        val pkg = service.rootInActiveWindow?.packageName?.toString().orEmpty()
        if ((a == "forward" || a == "rewind") && AdSkipper.isYouTube(pkg)) {
            val player = playerBounds()
            if (player != null) {
                val x = if (a == "forward") player.left + player.width * 4 / 5 else player.left + player.width / 5
                val ok = doubleTap(x, player.centerY)
                return if (ok) (if (a == "forward") "Skipped forward 10 s in the player." else "Went back 10 s in the player.")
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
            else -> return "Unknown media action \"$action\". Use play, pause, play_pause, next, previous, forward, rewind or stop."
        }
        val am = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "No audio service."
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now + 40, KeyEvent.ACTION_UP, code, 0))
        return "Sent media ${a.replace('_', ' ')} to the current player."
    }

    /** The video player's area on screen, found by its view id (YouTube and most native players name it so). */
    private fun playerBounds(): Bounds? {
        val root = service.rootInActiveWindow ?: return null
        var best: Bounds? = null
        val r = Rect()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 30 || !n.isVisibleToUser) return
            val id = n.viewIdResourceName?.lowercase()
            if (id != null && ("player" in id || "video" in id) && "button" !in id && "control" !in id) {
                n.getBoundsInScreen(r)
                val b = Bounds(r.left, r.top, r.right, r.bottom)
                if (b.width >= width * 0.6 && b.height >= 120 && (best == null || b.area > best!!.area)) best = b
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return best
    }

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
