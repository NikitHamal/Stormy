package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.ScreenElement.Companion.ROLE_SLIDER
import com.oro.app.device.ScreenElement.Companion.ROLE_TEXT
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import com.oro.app.device.Sliders.Method
import com.oro.app.device.Sliders.Value
import org.junit.Test

class SlidersTest {

    private val volume = ValueRange(0f, 100f, 35f)

    @Test
    fun `values are numbers, percentages or times`() {
        assertThat(Sliders.parse("35")).isEqualTo(Value.Number(35f))
        assertThat(Sliders.parse("-5")).isEqualTo(Value.Number(-5f))
        assertThat(Sliders.parse("0.5")).isEqualTo(Value.Number(0.5f))
        assertThat(Sliders.parse("40%")).isEqualTo(Value.Percent(40f))
        assertThat(Sliders.parse("1:30")).isEqualTo(Value.Time(90_000))
        assertThat(Sliders.parse("90s")).isEqualTo(Value.Time(90_000))
        assertThat(Sliders.parse("loud")).isNull()
        assertThat(Sliders.parse("")).isNull()
    }

    @Test
    fun `a value becomes the slider's own units`() {
        assertThat(Sliders.target(Value.Number(40f), volume, null, whole = true)).isEqualTo(40f)
        assertThat(Sliders.target(Value.Percent(40f), volume, null, whole = true)).isEqualTo(40f)
        // Brightness counts 0..255: 40% is 102.
        assertThat(Sliders.target(Value.Percent(40f), ValueRange(0f, 255f, 10f), null, whole = true)).isEqualTo(102f)
        assertThat(Sliders.target(Value.Percent(25f), ValueRange(0f, 1f, 0f), null, whole = false)).isEqualTo(0.25f)
        // Out of range is kept inside it.
        assertThat(Sliders.target(Value.Number(140f), volume, null, whole = true)).isEqualTo(100f)
        // A time needs the length: 1:30 of 3:00 is halfway.
        assertThat(Sliders.target(Value.Time(90_000), volume, 180_000, whole = true)).isEqualTo(50f)
        assertThat(Sliders.target(Value.Time(90_000), volume, null, whole = true)).isNull()
    }

    @Test
    fun `where a value lies along the bar`() {
        assertThat(Sliders.fraction(Value.Number(35f), volume, null)).isEqualTo(0.35f)
        assertThat(Sliders.fraction(Value.Percent(40f), null, null)).isEqualTo(0.4f)
        assertThat(Sliders.fraction(Value.Time(74_000), null, 296_000)).isEqualTo(0.25f)
        // Unknowable: a number with no range, a time with no length.
        assertThat(Sliders.fraction(Value.Number(35f), null, null)).isNull()
        assertThat(Sliders.fraction(Value.Time(74_000), null, null)).isNull()
    }

    @Test
    fun `a value maps to x on the track, inside the thumb's inset`() {
        val bar = Bounds(0, 1000, 1000, 1048)
        // Inset: about the thumb's radius, here 16 px each end.
        assertThat(Sliders.inset(bar)).isEqualTo(16)
        assertThat(Sliders.xFor(0f, bar)).isEqualTo(16)
        assertThat(Sliders.xFor(1f, bar)).isEqualTo(984)
        assertThat(Sliders.xFor(0.5f, bar)).isEqualTo(500)
        assertThat(Sliders.xFor(1.5f, bar)).isEqualTo(984)
        // A thin, wide bar (a video's) loses little at its ends.
        assertThat(Sliders.xFor(0f, Bounds(0, 600, 1080, 630))).isEqualTo(10)
    }

    @Test
    fun `SET_PROGRESS first, then scroll steps, then a drag`() {
        assertThat(Sliders.plan(setProgress = true, scroll = true, readable = true, draggable = true))
            .containsExactly(Method.SET_PROGRESS, Method.SCROLL_STEPS, Method.DRAG).inOrder()
        assertThat(Sliders.plan(setProgress = false, scroll = true, readable = true, draggable = true))
            .containsExactly(Method.SCROLL_STEPS, Method.DRAG).inOrder()
        // A bar that reports no value (a video's): only a drag can place it.
        assertThat(Sliders.plan(setProgress = true, scroll = true, readable = false, draggable = true))
            .containsExactly(Method.DRAG)
        assertThat(Sliders.plan(setProgress = true, scroll = false, readable = true, draggable = false))
            .containsExactly(Method.SET_PROGRESS)
        assertThat(Sliders.plan(setProgress = false, scroll = false, readable = true, draggable = false)).isEmpty()
    }

    @Test
    fun `scroll steps go toward the value and stop within half a step`() {
        // The first step's size is unknown: step.
        assertThat(Sliders.nextStep(35f, 40f, null, 100f)).isTrue()
        assertThat(Sliders.nextStep(35f, 20f, null, 100f)).isFalse()
        // Steps of 5: 38 is as near 40 as they get from either side.
        assertThat(Sliders.nextStep(38f, 40f, 5f, 100f)).isNull()
        assertThat(Sliders.nextStep(30f, 40f, 5f, 100f)).isTrue()
        // Overshot by more than half a step: one back.
        assertThat(Sliders.nextStep(45f, 41f, 5f, 100f)).isFalse()
        // Halfway between two steps: either is as near, so no back and forth.
        assertThat(Sliders.nextStep(45f, 40f, 10f, 100f)).isNull()
        assertThat(Sliders.nextStep(40f, 40f, null, 100f)).isNull()
    }

    @Test
    fun `two readings steer the finger, whatever the inset or direction`() {
        val bar = Bounds(0, 1000, 1000, 1048)
        // Pressed at 100 -> 0.10, moved to 500 -> 0.50: 0.70 lies at 700.
        assertThat(Sliders.steer(100, 0.10f, 500, 0.50f, 0.70f, bar)).isEqualTo(700)
        // Readings off the guessed track: the line through them finds the real one.
        assertThat(Sliders.steer(100, 0.08f, 500, 0.52f, 0.70f, bar)).isEqualTo(664)
        // Drawn right to left (an RTL layout): the slope says so.
        assertThat(Sliders.steer(900, 0.10f, 500, 0.50f, 0.70f, bar)).isEqualTo(300)
        // The value did not move: the readings tell nothing.
        assertThat(Sliders.steer(100, 0.30f, 500, 0.30f, 0.70f, bar)).isNull()
        // Never off the slider.
        assertThat(Sliders.steer(100, 0.10f, 500, 0.20f, 0.90f, bar)).isEqualTo(998)
    }

    @Test
    fun `closeness is a share of the range`() {
        assertThat(Sliders.reached(40.5f, 40f, volume, 0.01f)).isTrue()
        assertThat(Sliders.reached(38f, 40f, volume, 0.01f)).isFalse()
    }

    @Test
    fun `a video's seek bar is told from other sliders`() {
        val yt = "com.google.android.youtube"
        val bar = el(3, ROLE_SLIDER, "", top = 700, height = 40, clickable = false, viewId = "$yt:id/time_bar")
        assertThat(Sliders.isSeekBar(bar)).isTrue()
        assertThat(Sliders.isSeekBar(bar.copy(viewId = "org.videolan.vlc:id/player_overlay_seekbar"))).isTrue()
        assertThat(Sliders.isSeekBar(bar.copy(viewId = "com.app:id/exo_progress"))).isTrue()
        assertThat(Sliders.isSeekBar(el(1, ROLE_SLIDER, "1:23 of 4:56", clickable = false))).isTrue()
        // Every SeekBar's generic id is not a player's: a volume slider must never seek the video.
        val volume = el(2, ROLE_SLIDER, "Media volume", clickable = false, viewId = "android:id/seekbar").copy(range = volume)
        assertThat(Sliders.isSeekBar(volume)).isFalse()
        assertThat(Sliders.isSeekBar(el(4, "button", "Seek"))).isFalse()
        assertThat(Sliders.seekBar(screen(volume, bar, pkg = yt))).isEqualTo(bar)
    }

    @Test
    fun `the player's clock is read off the labels by its bar`() {
        val bar = el(3, ROLE_SLIDER, "", top = 700, height = 40, clickable = false, viewId = "a:id/time_bar")
        val joined = screen(bar, el(4, ROLE_TEXT, "1:23 / 4:56", top = 640, height = 40))
        assertThat(Sliders.clock(joined, bar)).isEqualTo(83_000L to 296_000L)
        val spoken = screen(bar.copy(label = "1 minute 23 seconds of 4 minutes 56 seconds"))
        assertThat(Sliders.clock(spoken, spoken.elements[0])).isEqualTo(83_000L to 296_000L)
        val ends = screen(bar, el(4, ROLE_TEXT, "4:56", top = 650, height = 40), el(5, ROLE_TEXT, "1:23", top = 650, height = 40))
        assertThat(Sliders.clock(ends, bar)).isEqualTo(83_000L to 296_000L)
        // A count is not a clock, and far-off times (other videos' lengths) are not this one's.
        val count = screen(bar, el(4, ROLE_TEXT, "83 of 100", top = 650, height = 40), el(5, ROLE_TEXT, "12:04", top = 2000, height = 40))
        assertThat(Sliders.clock(count, bar)).isEqualTo(null to null)
    }

    @Test
    fun `values travel between set_value, the session and the bar`() {
        // set_value -> session: a share stays a share, a bare number on a bar with no range is seconds.
        assertThat(Sliders.mediaPosition(Value.Time(90_000), null)).isEqualTo("1:30")
        assertThat(Sliders.mediaPosition(Value.Percent(40f), null)).isEqualTo("40%")
        assertThat(Sliders.mediaPosition(Value.Number(90f), null)).isEqualTo("1:30")
        assertThat(Sliders.mediaPosition(Value.Number(25f), ValueRange(0f, 200f, 0f))).isEqualTo("12.5%")
        // set_value -> bar.
        assertThat(Sliders.barValue(Value.Number(90f), null)).isEqualTo("1:30")
        assertThat(Sliders.barValue(Value.Number(35f), volume)).isEqualTo("35")
        // media(seek) -> bar: a jump needs to know where playback is.
        assertThat(Sliders.barValue(MediaControl.Seek.By(30_000), 83_000L)).isEqualTo("1:53")
        assertThat(Sliders.barValue(MediaControl.Seek.By(30_000), null)).isNull()
        assertThat(Sliders.barValue(MediaControl.Seek.To(120_000), null)).isEqualTo("2:00")
        assertThat(Sliders.barValue(MediaControl.Seek.Fraction(0.5), null)).isEqualTo("50%")
    }
}
