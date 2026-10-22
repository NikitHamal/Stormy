package com.oro.app.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
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
 *  - Gestures are injected in screen pixels and clamped to the display.
 */
class DeviceController(private val service: OroAccessibilityService) {

    private val width get() = service.resources.displayMetrics.widthPixels
    private val height get() = service.resources.displayMetrics.heightPixels

    // ------------------------------------------------------------ observe

    fun capture(): ScreenState = ScreenReader.capture(service)

    /** Waits until no UI events for [quietMs], at most [maxMs]. */
    suspend fun settle(quietMs: Long = 450, maxMs: Long = 3_000, minMs: Long = 250) {
        val start = SystemClock.uptimeMillis()
        delay(minMs)
        while (SystemClock.uptimeMillis() - start < maxMs) {
            val idle = SystemClock.uptimeMillis() - service.lastEventAt
            if (idle >= quietMs) return
            delay(80)
        }
    }

    // ---------------------------------------------------------- gestures

    suspend fun tap(x: Int, y: Int): Boolean {
        service.overlay.showTouch(x, y)
        return gesture(stroke(path(x, y), 0, 60))
    }

    suspend fun longPress(x: Int, y: Int, durationMs: Long = 700): Boolean {
        service.overlay.showTouch(x, y, long = true)
        return gesture(stroke(path(x, y), 0, durationMs))
    }

    suspend fun doubleTap(x: Int, y: Int): Boolean {
        service.overlay.showTouch(x, y)
        val ok = gesture(stroke(path(x, y), 0, 50))
        delay(90)
        return ok && gesture(stroke(path(x, y), 0, 50))
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 350): Boolean {
        service.overlay.showSwipe(c(x1, width), c(y1, height), c(x2, width), c(y2, height))
        val p = Path().apply { moveTo(cx(x1), cy(y1)); lineTo(cx(x2), cy(y2)) }
        return gesture(stroke(p, 0, durationMs.coerceIn(80, 3_000)))
    }

    /** Press-hold-move-release, for reordering lists, moving icons, sliders. */
    suspend fun drag(x1: Int, y1: Int, x2: Int, y2: Int, holdMs: Long = 600, moveMs: Long = 700): Boolean {
        service.overlay.showSwipe(c(x1, width), c(y1, height), c(x2, width), c(y2, height))
        if (Build.VERSION.SDK_INT >= 26) {
            val hold = GestureDescription.StrokeDescription(path(x1, y1), 0, holdMs, true)
            val okHold = gesture(GestureDescription.Builder().addStroke(hold).build())
            if (!okHold) return false
            val move = Path().apply { moveTo(cx(x1), cy(y1)); lineTo(cx(x2), cy(y2)) }
            val moveStroke = hold.continueStroke(move, 0, moveMs, false)
            return gesture(GestureDescription.Builder().addStroke(moveStroke).build())
        }
        val p = Path().apply { moveTo(cx(x1), cy(y1)); lineTo(cx(x2), cy(y2)) }
        return gesture(stroke(p, 0, holdMs + moveMs))
    }

    /** Two-finger pinch around a centre. [zoomIn] spreads the fingers. */
    suspend fun pinch(x: Int, y: Int, zoomIn: Boolean): Boolean {
        val near = 60f
        val far = (minOf(width, height) * 0.3f)
        val (from, to) = if (zoomIn) near to far else far to near
        val a = Path().apply { moveTo(cx(x) - from, cy(y)); lineTo(cx(x) - to, cy(y)) }
        val b = Path().apply { moveTo(cx(x) + from, cy(y)); lineTo(cx(x) + to, cy(y)) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(a, 0, 450))
            .addStroke(GestureDescription.StrokeDescription(b, 0, 450))
            .build()
        return gesture(g)
    }

    // ---------------------------------------------------- element actions

    suspend fun tapElement(e: ScreenElement): String {
        val node = ScreenReader.lastNodes[e.index]
        val target = node?.let { clickableSelfOrAncestor(it) }
        if (target != null && target.isEnabled && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            service.overlay.showTouch(e.centerX, e.centerY)
            return "Tapped [${e.index}] ${e.describe()}."
        }
        return if (tap(e.centerX, e.centerY)) "Tapped [${e.index}] ${e.describe()} at (${e.centerX},${e.centerY})."
        else "The tap on [${e.index}] could not be performed."
    }

    suspend fun longPressElement(e: ScreenElement): String {
        val node = ScreenReader.lastNodes[e.index]
        val target = node?.let { longClickableSelfOrAncestor(it) }
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            service.overlay.showTouch(e.centerX, e.centerY, long = true)
            return "Long-pressed [${e.index}] ${e.describe()}."
        }
        return if (longPress(e.centerX, e.centerY)) "Long-pressed [${e.index}] ${e.describe()}."
        else "The long press on [${e.index}] could not be performed."
    }

    /**
     * Types into [e] (or the focused field). Replaces the content unless
     * [append]. Falls back to tap-to-focus then paste for fields that refuse
     * ACTION_SET_TEXT.
     */
    suspend fun type(e: ScreenElement?, text: String, append: Boolean): String {
        var node = e?.let { ScreenReader.lastNodes[it.index] }
        if (node != null && !node.isEditable) node = findEditableDescendant(node) ?: node
        if (node == null) node = focusedInput()
        if (node == null) {
            return "No text field is focused. Tap the input first (or pass its index), then type."
        }
        if (!node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(150)
        }
        val existing = node.text?.toString().takeUnless { showsHint(node) }.orEmpty()
        val newText = if (append) existing + text else text
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText) }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return "Typed \"${text.take(60)}\"${if (append) " (appended)" else ""}."
        }
        // Paste fallback.
        val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("oro", text))
        if (!append) {
            val sel = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, existing.length)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        }
        return if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) "Pasted \"${text.take(60)}\" into the field."
        else "The field rejected the text. Try tapping it first, or it may not accept input."
    }

    /** Presses the keyboard's action key (search/send/go/enter). */
    suspend fun submit(): String {
        val node = focusedInput()
        if (node != null && Build.VERSION.SDK_INT >= 30) {
            val ime = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
            if (node.performAction(ime)) return "Pressed Enter on the keyboard."
        }
        return "Could not press Enter here. Look for a Send/Search/Go button on screen and tap it instead."
    }

    suspend fun scroll(direction: String, e: ScreenElement?, amount: Float = 0.6f): String {
        val dir = direction.lowercase()
        val node = e?.let { ScreenReader.lastNodes[it.index] }?.let { scrollableSelfOrAncestor(it) }
            ?: if (e == null) largestScrollable() else null
        if (node != null) {
            val action = when (dir) {
                "down", "right", "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            val directional = directionalScroll(dir)
            if ((directional != null && node.performAction(directional)) || node.performAction(action)) {
                return "Scrolled $dir${e?.let { " in [${it.index}]" } ?: ""}."
            }
        }
        // Gesture fallback within the element (or screen) bounds.
        val r = e?.bounds ?: Rect(0, (height * 0.18).toInt(), width, (height * 0.85).toInt())
        val span = (if (dir == "up" || dir == "down") r.height() else r.width()) * amount.coerceIn(0.2f, 0.9f) / 2
        val cxp = r.centerX(); val cyp = r.centerY()
        val ok = when (dir) {
            "down" -> swipe(cxp, (cyp + span).toInt(), cxp, (cyp - span).toInt(), 380)
            "up" -> swipe(cxp, (cyp - span).toInt(), cxp, (cyp + span).toInt(), 380)
            "right" -> swipe((cxp + span).toInt(), cyp, (cxp - span).toInt(), cyp, 320)
            "left" -> swipe((cxp - span).toInt(), cyp, (cxp + span).toInt(), cyp, 320)
            else -> return "Unknown direction \"$direction\"; use up, down, left or right."
        }
        return if (ok) "Scrolled $dir (swipe)." else "Scrolling failed."
    }

    fun global(key: String): String {
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

    fun openApp(name: String): String {
        val app = AppCatalog.resolve(service, name)
            ?: return "No installed app matches \"$name\". Closest: " +
                AppCatalog.suggestions(service, name).joinToString { it.label } + ". Use list_apps to see all."
        return if (AppCatalog.launch(service, app)) "Opened ${app.label}." else "${app.label} could not be launched."
    }

    fun openUrl(url: String): String =
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

    private fun c(v: Int, max: Int) = v.coerceIn(1, max - 1)
    private fun cx(x: Int) = c(x, width).toFloat()
    private fun cy(y: Int) = c(y, height).toFloat()
    private fun path(x: Int, y: Int) = Path().apply { moveTo(cx(x), cy(y)) }
    private fun stroke(p: Path, start: Long, duration: Long) =
        GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, start, duration)).build()

    private suspend fun gesture(g: GestureDescription): Boolean {
        // An injected touch that lands on the control pill must reach the app.
        service.overlay.setPassThrough(true)
        delay(16)
        return try { dispatch(g) } finally { service.overlay.setPassThrough(false) }
    }

    private suspend fun dispatch(g: GestureDescription): Boolean = suspendCancellableCoroutine { cont ->
        val dispatched = service.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!dispatched && cont.isActive) cont.resume(false)
    }
}
