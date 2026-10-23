package com.oro.app.device.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TouchGeometryTest {

    @Test
    fun `a window at the screen's corner draws at the screen point`() {
        assertThat(TouchGeometry.toView(540f, 0)).isEqualTo(540f)
    }

    @Test
    fun `a window pushed below the status bar draws the mark that much higher`() {
        // A tap at y=300 on screen, in a window whose top sits at y=96.
        assertThat(TouchGeometry.toView(300f, 96)).isEqualTo(204f)
    }

    @Test
    fun `a window that starts above the screen draws the mark lower`() {
        assertThat(TouchGeometry.toView(300f, -40)).isEqualTo(340f)
    }

    @Test
    fun `an outline sits just outside the target`() {
        val o = TouchGeometry.outline(100f, 200f, 300f, 260f, pad = 6f)
        assertThat(o?.toList()).containsExactly(94f, 194f, 306f, 266f).inOrder()
    }

    @Test
    fun `degenerate bounds get no outline`() {
        assertThat(TouchGeometry.outline(100f, 200f, 101f, 260f, pad = 6f)).isNull()
        assertThat(TouchGeometry.outline(100f, 260f, 300f, 200f, pad = 6f)).isNull()
    }

    @Test
    fun `controls and full-width rows are framed`() {
        val checkbox = floatArrayOf(40f, 900f, 120f, 980f)
        val row = floatArrayOf(0f, 600f, 1080f, 760f)
        assertThat(TouchGeometry.worthOutlining(checkbox, 1080, 2400)).isTrue()
        assertThat(TouchGeometry.worthOutlining(row, 1080, 2400)).isTrue()
    }

    @Test
    fun `page-sized containers are not framed`() {
        val page = floatArrayOf(0f, 0f, 1080f, 2400f)
        val tallList = floatArrayOf(0f, 200f, 1080f, 1500f)
        val bigCard = floatArrayOf(0f, 300f, 1080f, 1350f) // under the height cap, over the area cap
        assertThat(TouchGeometry.worthOutlining(page, 1080, 2400)).isFalse()
        assertThat(TouchGeometry.worthOutlining(tallList, 1080, 2400)).isFalse()
        assertThat(TouchGeometry.worthOutlining(bigCard, 1080, 2400)).isFalse()
    }

    @Test
    fun `no screen size, no frame`() {
        assertThat(TouchGeometry.worthOutlining(floatArrayOf(0f, 0f, 10f, 10f), 0, 2400)).isFalse()
        assertThat(TouchGeometry.worthOutlining(floatArrayOf(0f, 0f), 1080, 2400)).isFalse()
    }
}
