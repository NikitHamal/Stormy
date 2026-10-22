package com.oro.app.device

import android.animation.ValueAnimator
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.oro.app.MainActivity
import com.oro.app.OroApplication
import com.oro.app.data.repository.LiteMode
import com.oro.app.device.overlay.AgentIsland
import com.oro.app.device.overlay.DeviceAgentUi
import com.oro.app.device.overlay.DragFrame
import com.oro.app.device.overlay.IslandActions
import com.oro.app.device.overlay.OverlayLifecycleOwner
import com.oro.app.device.overlay.TouchFeedbackView
import com.oro.app.ui.theme.DevicePerformance
import com.oro.app.ui.theme.OroTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What the user sees while Orobi drives the phone:
 *  - the island: a small floating pill that says what Orobi is doing, with
 *    Stop always one tap away, that opens into a card with Pause, Stop and
 *    Open Oro; permission requests and questions open it by themselves and
 *    can be answered right there. It is draggable and settles to the nearest
 *    edge or the centre;
 *  - touch feedback: a ring where Orobi taps, a trail where it swipes, so
 *    nothing happens to the phone invisibly.
 *
 * Both live on TYPE_ACCESSIBILITY_OVERLAY windows: no extra permission, drawn
 * above every app, and excluded from what the agent reads. The island is
 * Compose (Oro's theme, Poppins, Orobi's loader) rendering [DeviceAgent.state]
 * directly; the touch layer is a plain Canvas. All window work happens on the
 * main thread.
 */
class AgentOverlay(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    var onPauseToggle: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onApproval: ((Boolean) -> Unit)? = null
    var onOpenApp: (() -> Unit)? = null

    /** Answers typed or picked on the island. Unset, they go straight to [DeviceAgent.answer]. */
    var onAnswer: ((String) -> Unit)? = null

    private var island: IslandWindow? = null
    private var touches: TouchFeedbackView? = null

    /** Whether the island should be on screen; it animates out before its window is removed. */
    private val visible = MutableStateFlow(false)
    private var pendingRemoval: Runnable? = null

    /** The resolved Lite flag (setting or low-end device), for the window work outside composition. */
    @Volatile private var liteNow = false

    /** Bumped when Back or a touch outside should take the keyboard away from the reply field. */
    private val focusResets = MutableStateFlow(0)

    /** The island sits on the bottom half: its card opens upwards. */
    private val anchoredBottom = MutableStateFlow(false)

    /** The reply field has the keyboard: the card keeps to the space above it. */
    private val typing = MutableStateFlow(false)

    private fun dp(v: Float): Int = (v * context.resources.displayMetrics.density).toInt()
    private fun ui(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    // ------------------------------------------------------------ island

    fun show(showTouches: Boolean) = ui {
        pendingRemoval?.let { main.removeCallbacks(it) }
        pendingRemoval = null
        // A window the system refused is not kept: the next show() tries again,
        // so a run is never left without Stop on screen.
        if (island == null) island = IslandWindow().takeIf { it.attach() }
        visible.value = true
        if (showTouches) addTouchLayer() else removeTouchLayer()
    }

    /**
     * Kept for callers from before the island read the run itself: status,
     * step, pause and progress all come from [DeviceAgent.state] now.
     */
    @Suppress("UNUSED_PARAMETER")
    fun update(status: String, step: String?, paused: Boolean, working: Boolean) = Unit

    /** The island opens itself for approvals; this only makes sure it is there. */
    @Suppress("UNUSED_PARAMETER")
    fun askApproval(question: String) = ui {
        if (island == null) show(showTouches = touches != null)
    }

    fun clearApproval() = Unit

    fun hideAll() = ui {
        visible.value = false
        removeTouchLayer()
        val window = island ?: return@ui
        val removal = Runnable {
            if (!visible.value) {
                window.detach()
                if (island === window) island = null
            }
        }
        pendingRemoval = removal
        main.postDelayed(removal, EXIT_MS)
    }

    /**
     * Lets every touch pass through the island, or restores it. Prefer
     * [clearPathFor], which only steps aside when a gesture would land on the
     * island and waits until it has.
     */
    fun setPassThrough(pass: Boolean) = ui { island?.setTouchable(!pass) }

    /**
     * Gets the island out of an injected gesture's way: if [region] (screen
     * pixels, the gesture's bounding box) overlaps the island, the island stops
     * taking touches and this returns only once that change is on screen.
     * Returns true when it changed anything; call [restoreTouch] after the
     * gesture either way. Elsewhere the island stays touchable, so Stop always
     * works.
     */
    suspend fun clearPathFor(region: Rect): Boolean {
        val changed = withContext(Dispatchers.Main.immediate) {
            val window = island ?: return@withContext false
            if (!window.overlaps(region, slop = dp(GESTURE_SLOP_DP))) return@withContext false
            window.setTouchable(false)
            true
        }
        if (changed) {
            // updateViewLayout takes effect on the next traversal; the input
            // system learns about it a frame or so after that.
            awaitFrames(2)
            delay(16)
        }
        return changed
    }

    /** Undoes [clearPathFor]. */
    fun restoreTouch() = ui { island?.setTouchable(true) }

    private suspend fun awaitFrames(count: Int) = withContext(Dispatchers.Main.immediate) {
        repeat(count) {
            suspendCancellableCoroutine<Unit> { cont ->
                val callback = Choreographer.FrameCallback { if (cont.isActive) cont.resume(Unit) }
                Choreographer.getInstance().postFrameCallback(callback)
                cont.invokeOnCancellation { main.post { Choreographer.getInstance().removeFrameCallback(callback) } }
            }
        }
    }

    // ---------------------------------------------------- touch feedback

    fun showTouch(x: Int, y: Int, long: Boolean = false) = ui { touches?.tap(x.toFloat(), y.toFloat(), long) }

    fun showSwipe(x1: Int, y1: Int, x2: Int, y2: Int) = ui {
        touches?.swipe(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())
    }

    private fun addTouchLayer() {
        if (touches != null) return
        val layer = TouchFeedbackView(context)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Orobi touches"
            // Screen coordinates all the way to the edges, so a ring lands
            // exactly where the gesture did, nav bar or not.
            if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
        }
        if (runCatching { wm.addView(layer, lp) }.isSuccess) touches = layer
    }

    private fun removeTouchLayer() {
        touches?.let { runCatching { wm.removeView(it) } }
        touches = null
    }

    // ------------------------------------------------------------- Oro

    private fun openOro() {
        onOpenApp?.let { it(); return }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(DeviceAgent.EXTRA_OPEN_DEVICE_AGENT, true)
        runCatching { context.startActivity(intent) }
    }

    private val actions = IslandActions(
        onPauseToggle = { onPauseToggle?.invoke() ?: DeviceAgent.togglePause() },
        onStop = { onStop?.invoke() ?: DeviceAgent.stop() },
        onApprove = { allow -> onApproval?.invoke(allow) ?: DeviceAgent.approve(allow) },
        onAnswer = { text -> onAnswer?.invoke(text) ?: DeviceAgent.answer(text) },
        onOpenOro = { openOro() },
        onTyping = { on -> ui { island?.setTyping(on) } },
    )

    private val liteChoice = (context.applicationContext as? OroApplication)?.preferencesRepository?.liteMode
        ?: flowOf(LiteMode.AUTO)

    @Composable
    private fun IslandContent() {
        val state by DeviceAgent.state.collectAsState()
        val screenVisible by DeviceAgentUi.screenVisible.collectAsState()
        val shown by visible.collectAsState()
        val choice by liteChoice.collectAsState(initial = LiteMode.AUTO)
        val bottom by anchoredBottom.collectAsState()
        val keyboard by typing.collectAsState()
        val resets by focusResets.collectAsState()
        val lowEnd = remember { DevicePerformance.isLowEndDevice(context) }
        val lite = choice.isEnabled(lowEnd)
        SideEffect { liteNow = lite }
        val config = LocalConfiguration.current
        // The whole height between the bars, or the part above the keyboard
        // while the reply field has it; the card's middle scrolls past that.
        val maxCard = if (keyboard) config.screenHeightDp * 0.42f else config.screenHeightDp - CARD_MARGIN_DP
        // Over any app, legible on any wallpaper: always the dark island.
        OroTheme(darkTheme = true, liteMode = lite) {
            AgentIsland(
                state = state,
                visible = shown,
                screenVisible = screenVisible,
                lite = lite,
                cardWidth = min(config.screenWidthDp - 28, 360).coerceAtLeast(240).dp,
                actions = actions,
                maxCardHeight = maxCard.coerceAtLeast(MIN_CARD_DP).dp,
                anchoredBottom = bottom,
                focusResets = resets,
            )
        }
    }

    // ------------------------------------------------------ the window

    private enum class HAnchor { START, CENTER, END }
    private enum class VAnchor { TOP, BOTTOM }

    /**
     * The island's window. Wraps its content tightly (touches beside it reach
     * the app below) and is anchored to a corner or the top/bottom centre, so
     * the card grows away from the edge it sits on. It keeps itself on screen
     * when it grows, shrinks or the phone rotates.
     */
    private inner class IslandWindow {
        private val owner = OverlayLifecycleOwner()
        private val frame = DragFrame(
            context, ::dragStart, ::dragTo, ::dragEnd,
            onDismissKeyboard = ::dismissKeyboard,
            onConfigChanged = { main.post(::afterRotation) },
        )
        private val compose = ComposeView(context)
        private val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            title = "Orobi"
            // Positions are screen coordinates: without this, bottom and end
            // anchors are measured from the navigation bar's edge and the
            // island jumps by its height after every snap.
            if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
        }
        private var hAnchor = HAnchor.CENTER
        private var vAnchor = VAnchor.TOP
        private var offsetY = 0

        /** Where the island sits between its top and bottom limits, 0..1, so a rotation keeps it in proportion. */
        private var yFraction = 0f
        private var dragX = 0
        private var dragY = 0
        private var snap: ValueAnimator? = null
        private var typingNow = false
        private var touchable = true
        private var beforeTyping: Pair<VAnchor, Int>? = null
        private val marginX get() = dp(10f)

        private val rotation = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                main.post(::afterRotation)
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        }

        /** Adds the window; false (with everything released) when the system refuses it. */
        fun attach(): Boolean {
            owner.attachTo(frame)
            compose.setContent { IslandContent() }
            frame.addView(
                compose,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            )
            // A card that grows or shrinks must stay on screen.
            frame.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
                if (r - l != oldR - oldL || b - t != oldB - oldT) main.post(::keepOnScreen)
            }
            offsetY = topInset()
            applyAnchor()
            if (runCatching { wm.addView(frame, lp) }.isFailure) {
                owner.destroy()
                return false
            }
            runCatching { context.registerComponentCallbacks(rotation) }
            owner.resume()
            return true
        }

        fun detach() {
            snap?.cancel()
            runCatching { context.unregisterComponentCallbacks(rotation) }
            runCatching { wm.removeView(frame) }
            owner.destroy()
            if (typingNow) typing.value = false
        }

        /** Whether [region] (screen pixels), widened by [slop], touches the island's window. */
        fun overlaps(region: Rect, slop: Int): Boolean {
            if (!frame.isAttachedToWindow || frame.width == 0 || frame.height == 0) return false
            val loc = IntArray(2)
            frame.getLocationOnScreen(loc)
            val mine = Rect(loc[0], loc[1], loc[0] + frame.width, loc[1] + frame.height)
            val theirs = Rect(region).apply { inset(-slop, -slop) }
            return Rect.intersects(mine, theirs)
        }

        fun setTouchable(on: Boolean) {
            if (touchable == on) return
            touchable = on
            lp.flags = if (on) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            relayout()
        }

        /**
         * The reply field needs keyboard focus, which a NOT_FOCUSABLE window
         * never gets. Focusable only from the user's touch on the field until
         * they send, press Back or touch outside; touches beside the island
         * still reach the app (NOT_TOUCH_MODAL). A card low on the screen
         * moves to the top first, so the keyboard does not cover it.
         */
        fun setTyping(on: Boolean) {
            if (typingNow == on) return
            typingNow = on
            typing.value = on
            if (on) {
                lp.flags = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                if (vAnchor == VAnchor.BOTTOM) {
                    beforeTyping = vAnchor to offsetY
                    vAnchor = VAnchor.TOP
                    offsetY = topInset()
                }
            } else {
                lp.flags = (lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) and
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
                beforeTyping?.let { (anchor, y) ->
                    vAnchor = anchor
                    offsetY = y
                }
                beforeTyping = null
            }
            applyAnchor()
            relayout()
        }

        /** Back or a touch outside: the field lets go of the keyboard and the app gets its focus back. */
        private fun dismissKeyboard() {
            if (!typingNow) return
            focusResets.value = focusResets.value + 1
            setTyping(false)
        }

        private fun applyAnchor() {
            val h = when (hAnchor) {
                HAnchor.START -> Gravity.START
                HAnchor.CENTER -> Gravity.CENTER_HORIZONTAL
                HAnchor.END -> Gravity.END
            }
            lp.gravity = h or if (vAnchor == VAnchor.TOP) Gravity.TOP else Gravity.BOTTOM
            lp.x = if (hAnchor == HAnchor.CENTER) 0 else marginX
            lp.y = offsetY
            anchoredBottom.value = vAnchor == VAnchor.BOTTOM
        }

        private fun relayout() {
            if (frame.isAttachedToWindow) runCatching { wm.updateViewLayout(frame, lp) }
        }

        /** Top edge between the bars for the current size, as a top-anchored y. */
        private fun limits(height: Int): Pair<Int, Int> {
            val (_, sh) = screenSize()
            val minY = topInset()
            return minY to max(minY, sh - bottomInset() - height)
        }

        private fun currentTop(height: Int): Int {
            val (_, sh) = screenSize()
            return if (vAnchor == VAnchor.TOP) offsetY else sh - offsetY - height
        }

        private fun setTop(top: Int, height: Int) {
            val (_, sh) = screenSize()
            offsetY = if (vAnchor == VAnchor.TOP) top else sh - top - height
            applyAnchor()
            relayout()
        }

        /** After a size change: back inside the bars if it grew past one. */
        private fun keepOnScreen() {
            if (frame.isDragging || snap?.isRunning == true || !frame.isAttachedToWindow) return
            val h = frame.height
            if (h == 0) return
            val (minY, maxY) = limits(h)
            val top = currentTop(h)
            val clamped = top.coerceIn(minY, maxY)
            if (clamped != top) setTop(clamped, h)
        }

        /** After a rotation: the same place in proportion, inside the new bars. */
        private fun afterRotation() {
            if (frame.isDragging || !frame.isAttachedToWindow) return
            snap?.cancel()
            val h = frame.height
            val (minY, maxY) = limits(h)
            setTop((minY + yFraction * (maxY - minY)).toInt().coerceIn(minY, maxY), h)
        }

        // Dragging works in absolute top-left coordinates, then re-anchors.

        private fun dragStart() {
            snap?.cancel()
            // Where the window really is on screen, whatever it is anchored to.
            val loc = IntArray(2)
            frame.getLocationOnScreen(loc)
            dragX = loc[0]
            dragY = loc[1]
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = dragX
            lp.y = dragY
            relayout()
        }

        private fun dragTo(dx: Float, dy: Float) {
            val (sw, sh) = screenSize()
            val w = frame.width
            val h = frame.height
            lp.x = (dragX + dx).toInt().coerceIn(-w / 3, max(-w / 3, sw - w * 2 / 3))
            lp.y = (dragY + dy).toInt().coerceIn(0, max(0, sh - h))
            relayout()
        }

        private fun dragEnd() {
            val (sw, sh) = screenSize()
            val w = frame.width
            val h = frame.height
            val cx = lp.x + w / 2f
            val cy = lp.y + h / 2f
            hAnchor = when {
                abs(cx - sw / 2f) < sw / 6f -> HAnchor.CENTER
                cx < sw / 2f -> HAnchor.START
                else -> HAnchor.END
            }
            vAnchor = if (cy < sh / 2f) VAnchor.TOP else VAnchor.BOTTOM
            val targetX = when (hAnchor) {
                HAnchor.START -> marginX
                HAnchor.END -> sw - marginX - w
                HAnchor.CENTER -> (sw - w) / 2
            }
            val (minY, maxY) = limits(h)
            val targetY = lp.y.coerceIn(minY, maxY)
            yFraction = if (maxY > minY) (targetY - minY).toFloat() / (maxY - minY) else 0f
            offsetY = if (vAnchor == VAnchor.TOP) targetY else sh - targetY - h
            beforeTyping = null

            val fromX = lp.x
            val fromY = lp.y
            val settle = {
                applyAnchor()
                relayout()
            }
            if (liteNow) {
                settle()
                return
            }
            snap = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = SNAP_MS
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener { a ->
                    val p = a.animatedValue as Float
                    lp.x = (fromX + (targetX - fromX) * p).toInt()
                    lp.y = (fromY + (targetY - fromY) * p).toInt()
                    relayout()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                    override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) settle() }
                })
                start()
            }
        }

        private fun screenSize(): Pair<Int, Int> {
            if (Build.VERSION.SDK_INT >= 30) {
                runCatching { wm.currentWindowMetrics.bounds }.getOrNull()
                    ?.takeIf { it.width() > 0 && it.height() > 0 }
                    ?.let { return it.width() to it.height() }
            }
            @Suppress("DEPRECATION")
            val real = android.util.DisplayMetrics().also { runCatching { wm.defaultDisplay.getRealMetrics(it) } }
            if (real.widthPixels > 0 && real.heightPixels > 0) return real.widthPixels to real.heightPixels
            val m = context.resources.displayMetrics
            return m.widthPixels to m.heightPixels
        }

        /** Below the status bar (and any cutout); never less than a bar, even where a service context reports none. */
        private fun topInset(): Int {
            val reported = if (Build.VERSION.SDK_INT >= 30) {
                runCatching {
                    wm.currentWindowMetrics.windowInsets
                        .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
                        .top
                }.getOrDefault(0)
            } else systemDimen("status_bar_height")
            return max(reported, dp(24f)) + dp(4f)
        }

        private fun bottomInset(): Int {
            val reported = if (Build.VERSION.SDK_INT >= 30) {
                runCatching {
                    wm.currentWindowMetrics.windowInsets
                        .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                        .bottom
                }.getOrDefault(0)
            } else systemDimen("navigation_bar_height")
            return max(reported, dp(48f)) + dp(4f)
        }

        private fun systemDimen(name: String): Int {
            val res = context.resources
            val id = res.getIdentifier(name, "dimen", "android")
            return if (id != 0) runCatching { res.getDimensionPixelSize(id) }.getOrDefault(0) else 0
        }
    }

    private companion object {
        /** Out of the way of the app below: no focus, touches beside the island pass through. */
        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        /** Long enough for the island's exit animation to finish. */
        const val EXIT_MS = 320L
        const val SNAP_MS = 280L

        /** A gesture this close to the island counts as landing on it. */
        const val GESTURE_SLOP_DP = 8f

        /** Room the card leaves for both bars and its own shadow. */
        const val CARD_MARGIN_DP = 112f
        const val MIN_CARD_DP = 220f
    }
}
