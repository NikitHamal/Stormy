package com.oro.app.device.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import androidx.compose.ui.graphics.toArgb
import com.oro.app.ui.brand.OroBrand
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The light around the screen's edge while Orobi works the phone: a thin,
 * bright line hugging the display's own corners, a soft bloom falling inward,
 * and Orobi's colours flowing slowly around the frame. It says "the agent has
 * the phone" from anywhere on screen, with nothing in the middle in the way.
 *
 * Drawn on a full-screen untouchable window, like [TouchFeedbackView]. The
 * bloom is a small falloff mask ([GlowMask], built once per size) that the GPU
 * stretches over the screen and colours with a turning sweep gradient, so the
 * light fades without bands and costs one textured fill of the edge band per
 * frame. The line on top is a crisp stroke with its own gradient turning the
 * other way, so the colour never sits still. Each action brightens the glow
 * for a moment ([pulse]).
 *
 * Frames run only while the glow is visible or fading; once it is off,
 * nothing is drawn or scheduled. Lite mode keeps one still frame.
 */
internal class EdgeGlowView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val edge = Path()
    private val band = Path()
    private val box = RectF()
    private val inner = RectF()
    private val radii = FloatArray(8)
    private val innerRadii = FloatArray(8)
    private val cornerRadii = FloatArray(4)
    private val spin = Matrix()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { style = Paint.Style.FILL }

    private var lineShader: SweepGradient? = null
    private var bloomColors: SweepGradient? = null
    private var bloomShader: Shader? = null
    private var mask: Bitmap? = null
    private var builtW = -1
    private var builtH = -1

    /** 1 while the agent works, 0 otherwise; [level] eases towards it. */
    private var target = 0f
    private var from = 0f
    private var level = 0f
    private var changedAt = 0L
    private var startedAt = 0L
    private var pulseAt = NEVER

    /** One still frame instead of the animation, for low-end phones and Lite mode. */
    var lite = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        // Never part of what the agent reads, never a touch target.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
    }

    /** Fades the glow in or out; off, it stops drawing once the fade ends. */
    fun setActive(active: Boolean) {
        val next = if (active) 1f else 0f
        if (next == target) return
        val t = now()
        from = currentLevel(t)
        target = next
        changedAt = t
        if (active && startedAt == 0L) startedAt = t
        postInvalidateOnAnimation()
    }

    /** A brief brightening, for an action the agent just took. */
    fun pulse() {
        if (target <= 0f) return
        pulseAt = now()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        builtW = -1
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The display's corner radii arrive with the window's insets.
        builtW = -1
        invalidate()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        // The display's rounded corners come with the insets, often after the first frame.
        builtW = -1
        invalidate()
        return super.onApplyWindowInsets(insets)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseMask()
        builtW = -1
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = now()
        level = currentLevel(t)
        if (level <= 0.002f && target == 0f) {
            // Faded out: no frame scheduled until setActive(true).
            startedAt = 0L
            return
        }
        if (width <= 0 || height <= 0) return
        if (builtW != width || builtH != height) build()

        val elapsed = (t - startedAt).coerceAtLeast(0L).toFloat()
        val pulse = pulseStrength(t)
        val breath = if (lite) 1f else BREATH_LOW + (1f - BREATH_LOW) * (0.5f + 0.5f * sin(elapsed / BREATH_MS * TWO_PI))
        val strength = (level * (breath + PULSE_BOOST * pulse)).coerceIn(0f, 1f)
        val cx = width / 2f
        val cy = height / 2f
        if (!lite) {
            // Android 8+ picks up a child shader's new matrix inside the
            // ComposeShader; on 7.x the bloom keeps its colours and only the line turns.
            spin.setRotate(BLOOM_PHASE - elapsed / BLOOM_SPIN_MS * 360f, cx, cy)
            bloomColors?.setLocalMatrix(spin)
            spin.setRotate(elapsed / SPIN_MS * 360f, cx, cy)
            lineShader?.setLocalMatrix(spin)
        }

        canvas.save()
        canvas.clipPath(edge)
        // The bloom: only the band near the edge is filled; the mask fades it.
        bloomShader?.let {
            fill.shader = it
            fill.alpha = alpha(BLOOM_ALPHA * strength)
            canvas.drawPath(band, fill)
        }
        // The line, centred on the edge and clipped, so its inner half shows;
        // then a hot thin core along it.
        lineShader?.let {
            stroke.shader = it
            stroke.strokeWidth = LINE_DP * 2f * density
            stroke.alpha = alpha(LINE_ALPHA * strength)
            canvas.drawPath(edge, stroke)
        }
        stroke.shader = null
        stroke.color = CORE
        stroke.strokeWidth = CORE_DP * 2f * density
        stroke.alpha = alpha(CORE_ALPHA * strength)
        canvas.drawPath(edge, stroke)
        canvas.restore()

        val settling = t - changedAt < FADE_MS || pulse > 0f
        if (!lite || settling) postInvalidateOnAnimation()
    }

    private fun currentLevel(t: Long): Float {
        val p = ((t - changedAt) / FADE_MS.toFloat()).coerceIn(0f, 1f)
        val eased = p * p * (3f - 2f * p)
        return from + (target - from) * eased
    }

    private fun pulseStrength(t: Long): Float {
        val p = (t - pulseAt) / PULSE_MS.toFloat()
        if (p < 0f || p >= 1f) return 0f
        // Quick rise, slow fall.
        return if (p < 0.2f) p / 0.2f else (1f - (p - 0.2f) / 0.8f).let { it * it }
    }

    /** Edge path, band path, gradients and the falloff mask for the current size. */
    private fun build() {
        val w = width
        val h = height
        builtW = w
        builtH = h
        val fallback = FALLBACK_CORNER_DP * density
        cornerRadii[0] = corner(TOP_LEFT, fallback)
        cornerRadii[1] = corner(TOP_RIGHT, fallback)
        cornerRadii[2] = corner(BOTTOM_RIGHT, fallback)
        cornerRadii[3] = corner(BOTTOM_LEFT, fallback)
        for (i in 0 until 4) {
            radii[i * 2] = cornerRadii[i]
            radii[i * 2 + 1] = cornerRadii[i]
        }
        box.set(0f, 0f, w.toFloat(), h.toFloat())
        edge.reset()
        edge.addRoundRect(box, radii, Path.Direction.CW)

        // The band the bloom can reach: the screen minus an inner rounded box.
        val reach = REACH_DP * density
        band.reset()
        band.fillType = Path.FillType.EVEN_ODD
        band.addRoundRect(box, radii, Path.Direction.CW)
        val inset = reach + 1f
        if (w > inset * 2 && h > inset * 2) {
            inner.set(inset, inset, w - inset, h - inset)
            for (i in 0 until 8) innerRadii[i] = max(0f, radii[i] - inset)
            band.addRoundRect(inner, innerRadii, Path.Direction.CW)
        }

        val cx = w / 2f
        val cy = h / 2f
        lineShader = SweepGradient(cx, cy, LINE_COLORS, null)
        val colors = SweepGradient(cx, cy, BLOOM_COLORS, null)
        bloomColors = colors

        releaseMask()
        // One mask cell per ~1.5 dp: the falloff is smooth, so the GPU's
        // filtering hides the low resolution and the mask stays small.
        val cell = max(1f, MASK_CELL_DP * density)
        val bitmap = runCatching {
            val pixels = GlowMask.build(w, h, cell, cornerRadii, reach)
            Bitmap.createBitmap(pixels, GlowMask.cells(w, cell), GlowMask.cells(h, cell), Bitmap.Config.ARGB_8888)
        }.getOrNull()
        mask = bitmap
        bloomShader = bitmap?.let { b ->
            val shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(Matrix().apply { setScale(w / b.width.toFloat(), h / b.height.toFloat()) })
            // The gradient's colour wherever the mask is lit: src (colours) in dst (mask).
            ComposeShader(shader, colors, PorterDuff.Mode.SRC_IN)
        }
    }

    /**
     * Drops the mask without recycle(): the render thread may still be
     * drawing the last frame recorded with it, and a recycled bitmap there is
     * a crash. It is small; the collector takes it.
     */
    private fun releaseMask() {
        fill.shader = null
        bloomShader = null
        mask = null
    }

    /** The display's own corner radius where Android reports it (12+), else a typical one. */
    private fun corner(position: Int, fallback: Float): Float {
        if (Build.VERSION.SDK_INT >= 31) {
            val r = runCatching { rootWindowInsets?.getRoundedCorner(position)?.radius }.getOrNull()
            if (r != null && r > 0) return min(r.toFloat(), min(width, height) / 2f)
        }
        return fallback
    }

    private fun alpha(v: Float): Int = (v.coerceIn(0f, 1f) * 255f).toInt()

    private fun now() = SystemClock.uptimeMillis()

    private companion object {
        const val NEVER = -100_000L
        const val TWO_PI = 6.2831855f

        const val FADE_MS = 420L
        const val SPIN_MS = 9_000f
        const val BLOOM_SPIN_MS = 13_000f
        const val BLOOM_PHASE = 140f
        const val BREATH_MS = 3_200f
        const val BREATH_LOW = 0.74f
        const val PULSE_MS = 650L
        const val PULSE_BOOST = 0.3f

        /** How far the bloom reaches in from the edge, and its mask's cell size. */
        const val REACH_DP = 30f
        const val MASK_CELL_DP = 1.5f

        /** Visible widths (dp) of the line and its core, inward from the edge. */
        const val LINE_DP = 2f
        const val CORE_DP = 0.6f
        const val BLOOM_ALPHA = 1f
        const val LINE_ALPHA = 1f
        const val CORE_ALPHA = 0.6f
        const val FALLBACK_CORNER_DP = 28f

        // RoundedCorner positions (API 31); the values are fixed by the platform.
        const val TOP_LEFT = 0
        const val TOP_RIGHT = 1
        const val BOTTOM_RIGHT = 2
        const val BOTTOM_LEFT = 3

        val CORE: Int = Color.WHITE

        /** Orobi's hues around the frame; the last repeats the first so the turn has no seam. */
        val LINE_COLORS = intArrayOf(
            OroBrand.Magenta.toArgb(),
            OroBrand.Violet.toArgb(),
            OroBrand.Blue.toArgb(),
            OroBrand.Apricot.toArgb(),
            OroBrand.Coral.toArgb(),
            OroBrand.Magenta.toArgb(),
        )

        /** The bloom carries brighter and dimmer stretches, so light gathers and moves along the edge. */
        val BLOOM_COLORS = intArrayOf(
            OroBrand.Blue.toArgb(),
            withAlpha(OroBrand.Magenta.toArgb(), 0xFF),
            withAlpha(OroBrand.Violet.toArgb(), 0xC0),
            withAlpha(OroBrand.Coral.toArgb(), 0xE6),
            withAlpha(OroBrand.Magenta.toArgb(), 0xB0),
            OroBrand.Blue.toArgb(),
        )

        fun withAlpha(argb: Int, a: Int): Int = (argb and 0x00FFFFFF) or (a shl 24)
    }
}
