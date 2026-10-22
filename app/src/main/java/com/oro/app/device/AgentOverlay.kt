package com.oro.app.device

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * What the user sees while Orobi drives the phone:
 *  - a floating control pill (status, Pause/Resume, Stop), draggable, always
 *    on top of every app -- the user is never more than one tap from taking
 *    control back;
 *  - an approval card for sensitive actions (Allow / Deny);
 *  - touch feedback: a ripple where Orobi taps and a trail where it swipes,
 *    so every action is visible and nothing happens "invisibly".
 *
 * Plain Views on TYPE_ACCESSIBILITY_OVERLAY windows: they need no extra
 * permission, draw above everything, and are excluded from what the agent
 * reads. All calls are marshalled to the main thread.
 */
class AgentOverlay(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var pill: LinearLayout? = null
    private var pillParams: WindowManager.LayoutParams? = null
    private var statusText: TextView? = null
    private var stepText: TextView? = null
    private var dot: View? = null
    private var pauseBtn: TextView? = null
    private var approvalRow: LinearLayout? = null
    private var approvalText: TextView? = null
    private var touchLayer: TouchLayer? = null
    private var dotAnimator: ValueAnimator? = null

    var onPauseToggle: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onApproval: ((Boolean) -> Unit)? = null
    var onOpenApp: (() -> Unit)? = null

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)
    private fun ui(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block) }

    // ----------------------------------------------------------- the pill

    fun show(showTouches: Boolean) = ui {
        if (pill == null) createPill()
        if (showTouches && touchLayer == null) createTouchLayer()
        if (!showTouches) removeTouchLayer()
    }

    fun update(status: String, step: String?, paused: Boolean, working: Boolean) = ui {
        statusText?.text = status
        stepText?.text = step.orEmpty()
        stepText?.visibility = if (step.isNullOrBlank()) View.GONE else View.VISIBLE
        pauseBtn?.text = if (paused) "Resume" else "Pause"
        (dot?.background as? GradientDrawable)?.setColor(
            when {
                paused -> Color.parseColor("#F59E0B")
                working -> Color.parseColor("#E8508E")
                else -> Color.parseColor("#10B981")
            }
        )
        if (working && !paused) startPulse() else stopPulse()
    }

    fun askApproval(question: String) = ui {
        if (pill == null) createPill()
        approvalText?.text = question
        approvalRow?.visibility = View.VISIBLE
        pill?.let { p -> pillParams?.let { lp -> lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv(); wm.updateViewLayout(p, lp) } }
    }

    fun clearApproval() = ui { approvalRow?.visibility = View.GONE }

    fun hideAll() = ui {
        stopPulse()
        pill?.let { runCatching { wm.removeView(it) } }
        pill = null
        removeTouchLayer()
    }

    /**
     * Lets an injected gesture pass through the pill if it lands on it, then
     * restores touch so the user can still press Stop.
     */
    fun setPassThrough(pass: Boolean) = ui {
        val p = pill ?: return@ui
        val lp = pillParams ?: return@ui
        lp.flags = if (pass) lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { wm.updateViewLayout(p, lp) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createPill() {
        val bg = GradientDrawable().apply {
            cornerRadius = dp(22f)
            setColor(Color.parseColor("#F2111322"))
            setStroke(dp(1f).toInt(), Color.parseColor("#33FFFFFF"))
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            elevation = dp(12f)
            setPadding(dp(14f).toInt(), dp(10f).toInt(), dp(10f).toInt(), dp(10f).toInt())
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val d = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#E8508E")) }
            layoutParams = LinearLayout.LayoutParams(dp(10f).toInt(), dp(10f).toInt()).apply { marginEnd = dp(10f).toInt() }
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 13f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            paint.isFakeBoldText = true; text = "Orobi is working"
        }
        val sub = TextView(context).apply {
            setTextColor(Color.parseColor("#B3FFFFFF")); textSize = 11.5f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        texts.addView(title); texts.addView(sub)
        val pause = chip("Pause", "#26FFFFFF") { onPauseToggle?.invoke() }
        val stop = chip("Stop", "#E8508E") { onStop?.invoke() }
        row.addView(d); row.addView(texts); row.addView(pause); row.addView(stop)
        root.addView(row)

        // Approval strip.
        val approval = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(10f).toInt(), 0, 0)
        }
        val q = TextView(context).apply { setTextColor(Color.WHITE); textSize = 13f }
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, dp(8f).toInt(), 0, 0) }
        buttons.addView(chip("Deny", "#26FFFFFF") { onApproval?.invoke(false) })
        buttons.addView(chip("Allow", "#10B981") { onApproval?.invoke(true) })
        approval.addView(q); approval.addView(buttons)
        root.addView(approval)

        val width = minOf(context.resources.displayMetrics.widthPixels - dp(24f).toInt(), dp(420f).toInt())
        val lp = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(36f).toInt()
        }

        // Drag vertically to move out of the way; a tap on the body opens Oro.
        var downY = 0f; var startY = 0; var moved = false
        root.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downY = e.rawY; startY = lp.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - downY
                    if (abs(dy) > dp(6f)) moved = true
                    if (moved) {
                        lp.y = (startY + dy).toInt().coerceIn(0, context.resources.displayMetrics.heightPixels - root.height)
                        runCatching { wm.updateViewLayout(root, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) onOpenApp?.invoke(); true }
                else -> false
            }
        }

        root.alpha = 0f; root.translationY = -dp(16f)
        runCatching { wm.addView(root, lp) }
        root.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(PathInterpolator(0.16f, 1f, 0.3f, 1f)).start()

        pill = root; pillParams = lp; statusText = title; stepText = sub; dot = d; pauseBtn = pause
        approvalRow = approval; approvalText = q
    }

    private fun chip(label: String, color: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 12.5f
        paint.isFakeBoldText = true
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = dp(16f); setColor(Color.parseColor(color)) }
        setPadding(dp(14f).toInt(), dp(8f).toInt(), dp(14f).toInt(), dp(8f).toInt())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(6f).toInt() }
        minHeight = dp(36f).toInt()
        setOnClickListener {
            animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(120).start() }.start()
            onClick()
        }
    }

    private fun startPulse() {
        val d = dot ?: return
        if (dotAnimator?.isRunning == true) return
        dotAnimator = ValueAnimator.ofFloat(1f, 0.35f).apply {
            duration = 700; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { d.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopPulse() { dotAnimator?.cancel(); dotAnimator = null; dot?.alpha = 1f }

    // ---------------------------------------------------- touch feedback

    fun showTouch(x: Int, y: Int, long: Boolean = false) = ui { touchLayer?.ripple(x.toFloat(), y.toFloat(), long) }

    fun showSwipe(x1: Int, y1: Int, x2: Int, y2: Int) = ui { touchLayer?.trail(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat()) }

    private fun createTouchLayer() {
        val layer = TouchLayer(context)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching { wm.addView(layer, lp) }
        touchLayer = layer
    }

    private fun removeTouchLayer() {
        touchLayer?.let { runCatching { wm.removeView(it) } }
        touchLayer = null
    }

    /** Full-screen, untouchable canvas that draws ripples and swipe trails. */
    private class TouchLayer(context: Context) : FrameLayout(context) {
        private data class Mark(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val born: Long, val long: Boolean, val swipe: Boolean)
        private val marks = mutableListOf<Mark>()
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#E8508E") }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#E8508E") }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#E8508E") }
        private val density = resources.displayMetrics.density

        init { setWillNotDraw(false) }

        fun ripple(x: Float, y: Float, long: Boolean) { marks += Mark(x, y, x, y, now(), long, false); invalidate() }
        fun trail(x1: Float, y1: Float, x2: Float, y2: Float) { marks += Mark(x1, y1, x2, y2, now(), false, true); invalidate() }
        private fun now() = android.os.SystemClock.uptimeMillis()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val t = now()
            marks.removeAll { t - it.born > LIFE }
            for (m in marks) {
                val p = (t - m.born) / LIFE.toFloat()
                val ease = 1f - (1f - p) * (1f - p) * (1f - p)
                val alpha = ((1f - p) * 255).toInt().coerceIn(0, 255)
                if (m.swipe) {
                    line.strokeWidth = 10f * density * (1f - p * 0.6f)
                    line.alpha = (alpha * 0.7f).toInt()
                    canvas.drawLine(m.x1, m.y1, m.x1 + (m.x2 - m.x1) * ease, m.y1 + (m.y2 - m.y1) * ease, line)
                    fill.alpha = alpha
                    canvas.drawCircle(m.x1 + (m.x2 - m.x1) * ease, m.y1 + (m.y2 - m.y1) * ease, 9f * density, fill)
                } else {
                    val r = (14f + 34f * ease) * density * (if (m.long) 1.4f else 1f)
                    ring.strokeWidth = 3.5f * density
                    ring.alpha = alpha
                    canvas.drawCircle(m.x1, m.y1, r, ring)
                    fill.alpha = (alpha * 0.45f).toInt()
                    canvas.drawCircle(m.x1, m.y1, 12f * density * (1f - p * 0.5f), fill)
                }
            }
            if (marks.isNotEmpty()) postInvalidateOnAnimation()
        }

        companion object { private const val LIFE = 650L }
    }
}
