package com.oro.app.device.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import androidx.compose.ui.graphics.toArgb
import com.oro.app.ui.brand.OroBrand
import kotlin.math.max

/**
 * Where Orobi touches the screen, drawn on a full-screen untouchable window:
 * a ring that opens out from each tap, a ring that fills while a long press
 * holds, and a trail behind each swipe. Every action is visible, so nothing
 * happens to the phone "invisibly".
 *
 * Plain Canvas on purpose: it runs over whatever app is in front, on budget
 * phones too, and only animates while a mark is alive (a few hundred ms).
 * Colours are Orobi's own -- magenta markings on a peach core -- so the touch
 * reads as Orobi's hand on every background.
 */
internal class TouchFeedbackView(context: Context) : View(context) {

    private class Mark(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val born: Long, val kind: Int,
    )

    private val marks = ArrayList<Mark>(8)
    private val density = resources.displayMetrics.density

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = MAGENTA }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = MAGENTA }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = PEACH }
    private val coreEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = MAGENTA }
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = MAGENTA
    }

    init {
        // Never part of what the agent reads, never a touch target.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
    }

    fun tap(x: Float, y: Float, long: Boolean) = add(Mark(x, y, x, y, now(), if (long) LONG else TAP))

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) = add(Mark(x1, y1, x2, y2, now(), SWIPE))

    private fun add(mark: Mark) {
        if (marks.size >= MAX_MARKS) marks.removeAt(0)
        marks += mark
        postInvalidateOnAnimation()
    }

    private fun now() = SystemClock.uptimeMillis()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = now()
        marks.removeAll { t - it.born > life(it.kind) }
        for (m in marks) {
            val p = ((t - m.born) / life(m.kind).toFloat()).coerceIn(0f, 1f)
            when (m.kind) {
                SWIPE -> drawSwipe(canvas, m, p)
                LONG -> drawLongPress(canvas, m, p)
                else -> drawTap(canvas, m, p)
            }
        }
        if (marks.isNotEmpty()) postInvalidateOnAnimation()
    }

    private fun drawTap(canvas: Canvas, m: Mark, p: Float) {
        val open = easeOut(p)
        val fade = 1f - p
        ring.strokeWidth = 2.5f * density
        ring.alpha = (fade * 255).toInt()
        canvas.drawCircle(m.x1, m.y1, (14f + 26f * open) * density, ring)
        halo.alpha = (fade * 56).toInt()
        canvas.drawCircle(m.x1, m.y1, (14f + 26f * open) * density, halo)
        drawCore(canvas, m.x1, m.y1, scale = 1f - 0.3f * p, alpha = fadeLate(p))
    }

    private fun drawLongPress(canvas: Canvas, m: Mark, p: Float) {
        // The hold: a second ring closes in while the first breathes out.
        val hold = easeOut((p / 0.7f).coerceAtMost(1f))
        val fade = if (p < 0.7f) 1f else 1f - (p - 0.7f) / 0.3f
        ring.strokeWidth = 3f * density
        ring.alpha = (fade * 255).toInt()
        canvas.drawCircle(m.x1, m.y1, (30f + 8f * hold) * density, ring)
        ring.alpha = (fade * 140).toInt()
        canvas.drawCircle(m.x1, m.y1, (30f - 14f * hold) * density, ring)
        halo.alpha = (fade * 70 * hold).toInt()
        canvas.drawCircle(m.x1, m.y1, (30f + 8f * hold) * density, halo)
        drawCore(canvas, m.x1, m.y1, scale = 1f + 0.25f * hold, alpha = fade)
    }

    private fun drawSwipe(canvas: Canvas, m: Mark, p: Float) {
        val head = easeOut((p / 0.75f).coerceAtMost(1f))
        val fade = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f
        val hx = m.x1 + (m.x2 - m.x1) * head
        val hy = m.y1 + (m.y2 - m.y1) * head
        // The tail lags the head, so the trail shortens as it lands.
        val tail = max(0f, head - 0.45f) / 0.55f
        val tx = m.x1 + (m.x2 - m.x1) * tail
        val ty = m.y1 + (m.y2 - m.y1) * tail
        trail.strokeWidth = (10f - 5f * p) * density
        trail.alpha = (fade * 150).toInt()
        canvas.drawLine(tx, ty, hx, hy, trail)
        drawCore(canvas, hx, hy, scale = 1f, alpha = fade)
    }

    private fun drawCore(canvas: Canvas, x: Float, y: Float, scale: Float, alpha: Float) {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        val r = 9f * density * scale
        core.alpha = a
        coreEdge.alpha = a
        coreEdge.strokeWidth = 2.5f * density
        canvas.drawCircle(x, y, r, core)
        canvas.drawCircle(x, y, r, coreEdge)
    }

    private fun easeOut(p: Float): Float = 1f - (1f - p) * (1f - p) * (1f - p)

    /** Holds full strength, then fades over the last 40%. */
    private fun fadeLate(p: Float): Float = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f

    private fun life(kind: Int): Long = when (kind) {
        LONG -> 900L
        SWIPE -> 700L
        else -> 560L
    }

    private companion object {
        const val TAP = 0
        const val LONG = 1
        const val SWIPE = 2
        const val MAX_MARKS = 12
        /** Orobi's markings and skin, as ARGB ints for Paint. */
        val MAGENTA = OroBrand.Magenta.toArgb()
        val PEACH = OroBrand.Peach.toArgb()
    }
}
