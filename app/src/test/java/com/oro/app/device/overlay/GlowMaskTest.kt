package com.oro.app.device.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlowMaskTest {

    private val radii = floatArrayOf(120f, 120f, 90f, 90f)

    @Test
    fun `distance is zero on a straight edge and grows inward`() {
        assertThat(GlowMask.insideDistance(540f, 0f, 1080f, 2400f, radii)).isWithin(0.01f).of(0f)
        assertThat(GlowMask.insideDistance(0f, 1200f, 1080f, 2400f, radii)).isWithin(0.01f).of(0f)
        assertThat(GlowMask.insideDistance(540f, 30f, 1080f, 2400f, radii)).isWithin(0.01f).of(30f)
        assertThat(GlowMask.insideDistance(540f, 1200f, 1080f, 2400f, radii)).isWithin(0.01f).of(540f)
    }

    @Test
    fun `a corner follows its own curve`() {
        // On the top-left arc's diagonal: the arc centre is (120, 120), radius 120.
        val onArc = 120f - 120f / Math.sqrt(2.0).toFloat()
        assertThat(GlowMask.insideDistance(onArc, onArc, 1080f, 2400f, radii)).isWithin(0.05f).of(0f)
        // The screen's very corner lies outside the rounded edge.
        assertThat(GlowMask.insideDistance(1f, 1f, 1080f, 2400f, radii)).isLessThan(0f)
        // The bottom corners use their own, smaller radius.
        val onBottomArc = 90f - 90f / Math.sqrt(2.0).toFloat()
        assertThat(GlowMask.insideDistance(onBottomArc, 2400f - onBottomArc, 1080f, 2400f, radii)).isWithin(0.05f).of(0f)
    }

    @Test
    fun `falloff is full on the edge, gone at the reach, and only ever falls`() {
        assertThat(GlowMask.falloff(0f, 90f)).isEqualTo(1f)
        assertThat(GlowMask.falloff(-4f, 90f)).isEqualTo(1f)
        assertThat(GlowMask.falloff(90f, 90f)).isEqualTo(0f)
        assertThat(GlowMask.falloff(200f, 90f)).isEqualTo(0f)
        var last = 1f
        for (d in 1..89) {
            val a = GlowMask.falloff(d.toFloat(), 90f)
            assertThat(a).isAtMost(last)
            last = a
        }
        assertThat(GlowMask.falloff(10f, 0f)).isEqualTo(0f)
    }

    @Test
    fun `the mask lights the edge band and leaves the middle dark`() {
        val w = 300
        val h = 600
        val cell = 4f
        val reach = 40f
        val mask = GlowMask.build(w, h, cell, floatArrayOf(60f, 60f, 60f, 60f), reach)
        val mw = GlowMask.cells(w, cell)
        val mh = GlowMask.cells(h, cell)
        assertThat(mask.size).isEqualTo(mw * mh)
        fun alphaAt(x: Int, y: Int) = mask[(y / cell.toInt()) * mw + x / cell.toInt()] ushr 24
        assertThat(alphaAt(150, 1)).isGreaterThan(200)       // top edge, middle (cell centre is 2 px in)
        assertThat(alphaAt(1, 300)).isGreaterThan(200)       // left edge, middle
        assertThat(alphaAt(150, 300)).isEqualTo(0)           // centre
        assertThat(alphaAt(150, 60)).isEqualTo(0)            // past the reach
        assertThat(alphaAt(150, 20)).isIn(1..229)            // inside the falloff
        // Every lit cell is white: the colour comes from the gradient.
        assertThat(mask.filter { it != 0 }.all { it and 0xFFFFFF == 0xFFFFFF }).isTrue()
    }

    @Test
    fun `corner cells inside the curve but far from the straight edges are still lit`() {
        val w = 300
        val h = 600
        val cell = 2f
        val reach = 20f
        val r = 100f
        val mask = GlowMask.build(w, h, cell, floatArrayOf(r, r, r, r), reach)
        val mw = GlowMask.cells(w, cell)
        // On the top-left arc's diagonal, 10 px inside the curve: ~36 px from
        // both straight edges (past the reach), yet lit by the curve.
        val inset = r - (r - 10f) / Math.sqrt(2.0).toFloat()
        val i = (inset / cell).toInt()
        val a = mask[i * mw + i] ushr 24
        assertThat(inset).isGreaterThan(reach)
        assertThat(a).isGreaterThan(0)
    }
}
