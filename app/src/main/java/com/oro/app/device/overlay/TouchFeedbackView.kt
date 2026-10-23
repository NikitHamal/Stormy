package com.oro.app.device.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import androidx.compose.ui.graphics.toArgb
import com.oro.app.ui.brand.OroBrand
import kotlin.math.max

/**
 * Where Orobi touches the screen, drawn on a full-screen untouchable window:
 * a ring that opens out from each tap, a ring that fills while a long press
 * holds, and a trail behind each swipe. A tap on an element also frames the
 * element itself, and a field being typed into gets the same frame, so it is
 * plain which control the touch was for. Every action is visible, so nothing
 * happens to the phone "invisibly".
 *
 * Marks are kept in screen pixels and moved into this view's space when they
 * are drawn ([TouchGeometry]): the window is not always at the screen's corner,
 * and a mark drawn at raw screen coordinates sat above or below its target.
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
        /** The target's bounds in screen pixels (l, t, r, b), framed while the mark lives. */
        val frame: FloatArray? = null,
    )

    private val marks = ArrayList<Mark>(8)
    private val density = resources.displayMetrics.density
    private val origin = IntArray(2)
    private val box = RectF()

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = MAGENTA }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = MAGENTA }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = PEACH }
    private val coreEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = MAGENTA }
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = MAGENTA
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = MAGENTA }
    private val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = MAGENTA }

    init {
        // Never part of what the agent reads, never a touch target.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
    }

    /** A tap at ([x], [y]) in screen pixels; [bounds] (screen pixels) frames the element it was for. */
    fun tap(x: Float, y: Float, long: Boolean, bounds: RectF? = null) =
        add(Mark(x, y, x, y, now(), if (long) LONG else TAP, frameOf(bounds)))

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) = add(Mark(x1, y1, x2, y2, now(), SWIPE))

    /** Frames a field while text goes into it: no ring, nothing was touched. */
    fun field(bounds: RectF) {
        val frame = frameOf(bounds) ?: return
        add(Mark(bounds.centerX(), bounds.centerY(), bounds.centerX(), bounds.centerY(), now(), FIELD, frame))
    }

    /** Drops every mark at once; true when there was one to drop. */
    fun clear(): Boolean {
        if (marks.isEmpty()) return false
        marks.clear()
        invalidate()
        return true
    }

    private fun frameOf(bounds: RectF?): FloatArray? {
        bounds ?: return null
        val pad = 3f * density
        val frame = TouchGeometry.outline(bounds.left, bounds.top, bounds.right, bounds.bottom, pad) ?: return null
        val screen = resources.displayMetrics
        val w = max(width, screen.widthPixels)
        val h = max(height, screen.heightPixels)
        return frame.takeIf { TouchGeometry.worthOutlining(bounds.toArray(), w, h) }
    }

    private fun RectF.toArray() = floatArrayOf(left, top, right, bottom)

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
        if (marks.isEmpty()) return
        // Screen pixels to this window's pixels, read every frame: the window
        // can move under a rotation or a cutout change between two marks.
        getLocationOnScreen(origin)
        canvas.save()
        canvas.translate(TouchGeometry.toView(0f, origin[0]), TouchGeometry.toView(0f, origin[1]))
        for (m in marks) {
            val p = ((t - m.born) / life(m.kind).toFloat()).coerceIn(0f, 1f)
            m.frame?.let { drawFrame(canvas, it, p, filled = m.kind == FIELD) }
            when (m.kind) {
                SWIPE -> drawSwipe(canvas, m, p)
                LONG -> drawLongPress(canvas, m, p)
                FIELD -> Unit
                else -> drawTap(canvas, m, p)
            }
        }
        canvas.restore()
        postInvalidateOnAnimation()
    }

    /** The target's own outline: it settles onto the control, holds, then fades with the mark. */
    private fun drawFrame(canvas: Canvas, f: FloatArray, p: Float, filled: Boolean) {
        val settle = easeOut((p / 0.35f).coerceAtMost(1f))
        val grow = (1f - settle) * 4f * density
        box.set(f[0] - grow, f[1] - grow, f[2] + grow, f[3] + grow)
        val radius = minOf(FRAME_RADIUS_DP * density, minOf(box.width(), box.height()) / 2f)
        val fade = fadeLate(p)
        if (filled) {
            wash.alpha = (fade * 28).toInt()
            canvas.drawRoundRect(box, radius, radius, wash)
        }
        outline.strokeWidth = 2f * density
        outline.alpha = (fade * settle * 230).toInt()
        canvas.drawRoundRect(box, radius, radius, outline)
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
        FIELD -> 900L
        else -> 640L
    }

    private companion object {
        const val TAP = 0
        const val LONG = 1
        const val SWIPE = 2
        const val FIELD = 3
        const val MAX_MARKS = 12
        const val FRAME_RADIUS_DP = 12f
        /** Orobi's markings and skin, as ARGB ints for Paint. */
        val MAGENTA = OroBrand.Magenta.toArgb()
        val PEACH = OroBrand.Peach.toArgb()
    }
}
