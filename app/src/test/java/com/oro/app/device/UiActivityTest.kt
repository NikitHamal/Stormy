package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UiActivityTest {

    private val content = UiActivity.TYPE_WINDOW_CONTENT_CHANGED
    private val text = 0x2
    private val windowState = 0x20

    @Test
    fun `a value redrawn is cosmetic, a subtree or a pane is not, an unspecified change is not`() {
        assertThat(UiActivity.cosmetic(content, text)).isTrue()
        assertThat(UiActivity.cosmetic(content, 0x40)).isTrue()
        assertThat(UiActivity.cosmetic(content, UiActivity.CHANGE_SUBTREE)).isFalse()
        assertThat(UiActivity.cosmetic(content, text or UiActivity.CHANGE_PANE_APPEARED)).isFalse()
        assertThat(UiActivity.cosmetic(content, 0)).isFalse()
        assertThat(UiActivity.cosmetic(windowState, text)).isFalse()
    }

    @Test
    fun `a ticking clock moves lastAt but not lastChangeAt`() {
        val a = UiActivity()
        a.record(1_000, windowState, 0)
        for (t in 1_100L..3_000L step 100L) a.record(t, content, text)
        assertThat(a.lastAt).isEqualTo(3_000)
        assertThat(a.lastChangeAt).isEqualTo(1_000)
    }

    @Test
    fun `events that keep coming make a busy screen, a short burst does not`() {
        val busy = UiActivity()
        for (t in 0L..1_500L step 100L) busy.record(t, content, text)
        assertThat(busy.noisy(1_550)).isTrue()
        // Gone quiet: no longer busy.
        assertThat(busy.noisy(2_000)).isFalse()

        val burst = UiActivity()
        for (t in 5_000L..5_600L step 80L) burst.record(t, content, UiActivity.CHANGE_SUBTREE)
        assertThat(burst.noisy(5_650)).isFalse()
    }

    @Test
    fun `a gap ends the run, so a new run starts counting again`() {
        val a = UiActivity()
        for (t in 0L..1_300L step 100L) a.record(t, content, text)
        a.record(2_000, content, text)
        assertThat(a.noisy(2_050)).isFalse()
    }
}
