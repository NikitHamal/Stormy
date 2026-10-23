package com.oro.app.device.overlay

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The edge glow's falloff: how strongly each point inside the screen is lit,
 * from 1 on the display's edge to 0 at [reach] inward, along the real
 * rounded corners. Computed once per size into a small mask that the GPU
 * stretches smoothly over the screen, so the bloom has no bands (stacked
 * strokes left visible rings) and costs one textured fill per frame.
 *
 * Plain Kotlin so the shape is tested on the JVM.
 */
internal object GlowMask {

    /**
     * Distance (px) from ([x], [y]) to the edge of a [w]x[h] rounded
     * rectangle at the origin, positive inside; [radii] is top-left,
     * top-right, bottom-right, bottom-left.
     */
    fun insideDistance(x: Float, y: Float, w: Float, h: Float, radii: FloatArray): Float {
        val cx = w / 2f
        val cy = h / 2f
        val r = when {
            x < cx && y < cy -> radii[0]
            x >= cx && y < cy -> radii[1]
            x >= cx -> radii[2]
            else -> radii[3]
        }.coerceIn(0f, min(cx, cy))
        // Signed distance to a rounded box (negative inside), flipped.
        val qx = abs(x - cx) - (cx - r)
        val qy = abs(y - cy) - (cy - r)
        val outside = sqrt(max(qx, 0f).let { it * it } + max(qy, 0f).let { it * it })
        val sdf = outside + min(max(qx, qy), 0f) - r
        return -sdf
    }

    /** Light at [distance] px in from the edge: full on the edge, gone at [reach], soft in between. */
    fun falloff(distance: Float, reach: Float): Float {
        if (reach <= 0f || distance >= reach) return 0f
        if (distance <= 0f) return 1f
        val t = distance / reach
        return (1f - t).pow(FALLOFF_POWER)
    }

    /**
     * The mask as ARGB pixels (white, alpha = light), [mw]x[mh] cells each
     * covering [cell] screen px of a [w]x[h] screen.
     */
    fun build(w: Int, h: Int, cell: Float, radii: FloatArray, reach: Float): IntArray {
        val mw = cells(w, cell)
        val mh = cells(h, cell)
        val out = IntArray(mw * mh)
        val fw = w.toFloat()
        val fh = h.toFloat()
        // Inside a rounded corner the curve is nearer than the straight edges,
        // by at most (sqrt(2) - 1) * radius; past that margin nothing is lit.
        val skipBeyond = reach + CORNER_GAP * (radii.maxOrNull() ?: 0f)
        for (j in 0 until mh) {
            val y = (j + 0.5f) * cell
            for (i in 0 until mw) {
                val x = (i + 0.5f) * cell
                // Most of the screen is far from every edge: skip the maths there.
                val nearest = min(min(x, fw - x), min(y, fh - y))
                if (nearest > skipBeyond) continue
                val a = falloff(insideDistance(x, y, fw, fh, radii), reach)
                if (a > 0f) out[j * mw + i] = ((a * 255f + 0.5f).toInt() shl 24) or 0xFFFFFF
            }
        }
        return out
    }

    fun cells(px: Int, cell: Float): Int = max(1, ceil(px / cell).toInt())

    /** 2.2: most of the light sits in the first third, like light off a glass edge. */
    private const val FALLOFF_POWER = 2.2f

    /** sqrt(2) - 1, rounded up: how much nearer a corner's curve can be than its straight edges, per radius. */
    private const val CORNER_GAP = 0.415f
}
