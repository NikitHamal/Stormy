package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreviewViewportTest {
    @Test
    fun `width is clamped and snaps to near breakpoints only`() {
        assertThat(PreviewViewport.clamp(10)).isEqualTo(PreviewViewport.MIN_WIDTH)
        assertThat(PreviewViewport.clamp(99999)).isEqualTo(PreviewViewport.MAX_WIDTH)
        assertThat(PreviewViewport.snap(372)).isEqualTo(375)
        assertThat(PreviewViewport.snap(1030)).isEqualTo(1024)
        assertThat(PreviewViewport.snap(700)).isEqualTo(700)
    }

    @Test
    fun `css height follows the scale of the layout`() {
        assertThat(PreviewViewport.cssHeight(375, 1080, 2000)).isEqualTo(694)
        assertThat(PreviewViewport.cssHeight(375, 0, 2000)).isEqualTo(0)
        assertThat(PreviewViewport.label(375, 667)).isEqualTo("375 × 667")
        assertThat(PreviewViewport.label(375, 0)).isEqualTo("375")
    }

    @Test
    fun `backdrops cycle`() {
        assertThat(CanvasBackdrop.AUTO.next()).isEqualTo(CanvasBackdrop.LIGHT)
        assertThat(CanvasBackdrop.GRID.next()).isEqualTo(CanvasBackdrop.AUTO)
    }
}
