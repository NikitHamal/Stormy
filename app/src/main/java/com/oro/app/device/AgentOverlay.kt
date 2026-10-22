package com.oro.app.device

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What the user sees while Orobi drives the phone:
 *  - the island: a small floating pill that says what Orobi is doing and
 *    grows into a card with Pause, Stop and Open Oro; permission requests and
 *    questions open it by themselves and can be answered right there. It is
 *    draggable and settles to the nearest edge or the centre;
 *  - touch feedback: a ring where Orobi taps, a trail where it swipes, so
 *    nothing happens to the phone invisibly.
 *
 * Both live on TYPE_ACCESSIBILITY_OVERLAY windows: no extra permission, drawn
 * above every app, and excluded from what the agent reads. The island is
 * Compose (Oro's theme, Poppins, Orobi's loader) rendering [DeviceAgent.state]
 * directly; the touch layer is a plain Canvas. All calls hop to the main
 * thread.
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

    private fun dp(v: Float): Int = (v * context.resources.displayMetrics.density).toInt()
    private fun ui(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    // ------------------------------------------------------------ island

    fun show(showTouches: Boolean) = ui {
        pendingRemoval?.let { main.removeCallbacks(it) }
        pendingRemoval = null
        if (island == null) island = IslandWindow().also { it.attach() }
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
     * Lets an injected gesture pass through the island if it lands on it,
     * then restores touch so the user can still press Stop.
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
    suspend fun clearPathFor(region: android.graphics.Rect): Boolean {
        setPassThrough(true)
        kotlinx.coroutines.delay(48)
        return true
    }

    /** Undoes [clearPathFor]. */
    fun restoreTouch() = setPassThrough(false)

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
        ).apply { gravity = Gravity.TOP or Gravity.START }
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
        onTyping = { typing -> ui { island?.setTyping(typing) } },
    )

    private val liteChoice = (context.applicationContext as? OroApplication)?.preferencesRepository?.liteMode
        ?: flowOf(LiteMode.AUTO)

    @Composable
    private fun IslandContent() {
        val state by DeviceAgent.state.collectAsState()
        val screenVisible by DeviceAgentUi.screenVisible.collectAsState()
        val shown by visible.collectAsState()
        val choice by liteChoice.collectAsState(initial = LiteMode.AUTO)
        val lowEnd = remember { DevicePerformance.isLowEndDevice(context) }
        val lite = choice.isEnabled(lowEnd)
        val screenWidth = LocalConfiguration.current.screenWidthDp
        // Over any app, legible on any wallpaper: always the dark island.
        OroTheme(darkTheme = true, liteMode = lite) {
            AgentIsland(
                state = state,
                visible = shown,
                screenVisible = screenVisible,
                lite = lite,
                cardWidth = min(screenWidth - 28, 360).coerceAtLeast(240).dp,
                actions = actions,
            )
        }
    }

    // ------------------------------------------------------ the window

    private enum class HAnchor { START, CENTER, END }
    private enum class VAnchor { TOP, BOTTOM }

    /**
     * The island's window. Wraps its content tightly (touches beside it reach
     * the app below) and is anchored to a corner or the top/bottom centre, so
     * the card grows away from the edge it sits on.
     */
    private inner class IslandWindow {
        private val owner = OverlayLifecycleOwner()
        private val frame = DragFrame(context, ::dragStart, ::dragTo, ::dragEnd)
        private val compose = ComposeView(context)
        private val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT
        )
        private var hAnchor = HAnchor.CENTER
        private var vAnchor = VAnchor.TOP
        private var offsetY = 0
        private var dragX = 0
        private var dragY = 0
        private var snap: ValueAnimator? = null
        private var typing = false
        private var beforeTyping: Pair<VAnchor, Int>? = null
        private val marginX get() = dp(10f)

        fun attach() {
            owner.attachTo(frame)
            compose.setContent { IslandContent() }
            frame.addView(
                compose,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            )
            offsetY = topInset()
            applyAnchor()
            runCatching { wm.addView(frame, lp) }
            owner.resume()
        }

        fun detach() {
            snap?.cancel()
            runCatching { wm.removeView(frame) }
            owner.destroy()
        }

        fun setTouchable(touchable: Boolean) {
            lp.flags = if (touchable) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            relayout()
        }

        /**
         * The reply field needs keyboard focus, which a NOT_FOCUSABLE window
         * never gets. Focusable only while a question is open; touches beside
         * the island still reach the app (NOT_TOUCH_MODAL). A card low on the
         * screen moves to the top first, so the keyboard does not cover it.
         */
        fun setTyping(on: Boolean) {
            if (typing == on) return
            typing = on
            if (on) {
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                if (vAnchor == VAnchor.BOTTOM) {
                    beforeTyping = vAnchor to offsetY
                    vAnchor = VAnchor.TOP
                    offsetY = topInset()
                }
            } else {
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                beforeTyping?.let { (anchor, y) ->
                    vAnchor = anchor
                    offsetY = y
                }
                beforeTyping = null
            }
            applyAnchor()
            relayout()
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
        }

        private fun relayout() {
            runCatching { wm.updateViewLayout(frame, lp) }
        }

        // Dragging works in absolute top-left coordinates, then re-anchors.

        private fun dragStart() {
            snap?.cancel()
            val (sw, sh) = screenSize()
            val w = frame.width
            val h = frame.height
            dragX = when (hAnchor) {
                HAnchor.START -> lp.x
                HAnchor.END -> sw - lp.x - w
                HAnchor.CENTER -> (sw - w) / 2 + lp.x
            }
            dragY = if (vAnchor == VAnchor.TOP) lp.y else sh - lp.y - h
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
            val minY = topInset()
            val maxY = max(minY, sh - bottomInset() - h)
            val targetY = lp.y.coerceIn(minY, maxY)
            offsetY = if (vAnchor == VAnchor.TOP) targetY else sh - targetY - h
            beforeTyping = null

            val fromX = lp.x
            val fromY = lp.y
            val settle = {
                applyAnchor()
                relayout()
            }
            if (DevicePerformance.isLowEndDevice(context)) {
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
            val m = context.resources.displayMetrics
            return m.widthPixels to m.heightPixels
        }

        /** Below the status bar (and any cutout); a service context may report no insets, so never less than a bar. */
        private fun topInset(): Int {
            val reported = if (Build.VERSION.SDK_INT >= 30) {
                runCatching {
                    wm.currentWindowMetrics.windowInsets
                        .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
                        .top
                }.getOrDefault(0)
            } else 0
            return max(reported, dp(24f)) + dp(4f)
        }

        private fun bottomInset(): Int {
            val reported = if (Build.VERSION.SDK_INT >= 30) {
                runCatching {
                    wm.currentWindowMetrics.windowInsets
                        .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                        .bottom
                }.getOrDefault(0)
            } else 0
            return max(reported, dp(48f)) + dp(4f)
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
    }
}
