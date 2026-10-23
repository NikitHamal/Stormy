package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.MediaControl.Candidate
import com.oro.app.device.MediaControl.Seek
import org.junit.Test

class MediaControlTest {

    @Test
    fun `positions read the way people write them`() {
        assertThat(MediaControl.parsePosition("1:30")).isEqualTo(Seek.To(90_000))
        assertThat(MediaControl.parsePosition("90")).isEqualTo(Seek.To(90_000))
        assertThat(MediaControl.parsePosition("+30")).isEqualTo(Seek.By(30_000))
        assertThat(MediaControl.parsePosition("-10")).isEqualTo(Seek.By(-10_000))
        assertThat(MediaControl.parsePosition("50%")).isEqualTo(Seek.Fraction(0.5))
        assertThat(MediaControl.parsePosition(" 1:02:03 ")).isEqualTo(Seek.To(3_723_000))
        assertThat(MediaControl.parsePosition("+1:00")).isEqualTo(Seek.By(60_000))
        assertThat(MediaControl.parsePosition("1m30s")).isEqualTo(Seek.To(90_000))
        assertThat(MediaControl.parsePosition("1 minute 30 seconds")).isEqualTo(Seek.To(90_000))
        assertThat(MediaControl.parsePosition("2.5")).isEqualTo(Seek.To(2_500))
    }

    @Test
    fun `what is not a position is refused, never guessed`() {
        for (bad in listOf("", "abc", "1:75", "1:61:00", "150%", "+", "-x", "half", "90ms")) {
            assertThat(MediaControl.parsePosition(bad)).isNull()
        }
    }

    @Test
    fun `clock times and percentages`() {
        assertThat(MediaControl.parseClock("12:05")).isEqualTo(725_000)
        assertThat(MediaControl.parseClock("90s")).isEqualTo(90_000)
        assertThat(MediaControl.parseClock("1h 2m")).isEqualTo(3_720_000)
        assertThat(MediaControl.parseClock("3 minutes 32 seconds")).isEqualTo(212_000)
        assertThat(MediaControl.percent("40%")).isEqualTo(40.0)
        assertThat(MediaControl.percent("40 %")).isEqualTo(40.0)
        assertThat(MediaControl.percent("40")).isNull()
        assertThat(MediaControl.percent("101%")).isNull()
    }

    @Test
    fun `a seek lands inside the track`() {
        val length = 296_000L
        assertThat(MediaControl.targetMs(Seek.To(90_000), 10_000, length)).isEqualTo(90_000)
        assertThat(MediaControl.targetMs(Seek.By(30_000), 83_000, length)).isEqualTo(113_000)
        assertThat(MediaControl.targetMs(Seek.By(-10_000), 4_000, length)).isEqualTo(0)
        assertThat(MediaControl.targetMs(Seek.To(400_000), 0, length)).isEqualTo(length)
        assertThat(MediaControl.targetMs(Seek.Fraction(0.5), 0, length)).isEqualTo(148_000)
        // A live stream has no length: a time still works, a share cannot.
        assertThat(MediaControl.targetMs(Seek.To(90_000), 0, null)).isEqualTo(90_000)
        assertThat(MediaControl.targetMs(Seek.Fraction(0.5), 0, null)).isNull()
        // A jump needs to know where playback is.
        assertThat(MediaControl.targetMs(Seek.By(10_000), null, length)).isNull()
    }

    @Test
    fun `the live position moves on while playing, at its speed`() {
        // Reported 60 s in, 5 s ago.
        assertThat(MediaControl.livePosition(60_000, 1_000, 1f, 6_000, playing = true, durationMs = 296_000)).isEqualTo(65_000)
        assertThat(MediaControl.livePosition(60_000, 1_000, 2f, 6_000, playing = true, durationMs = 296_000)).isEqualTo(70_000)
        assertThat(MediaControl.livePosition(60_000, 1_000, 1f, 6_000, playing = false, durationMs = 296_000)).isEqualTo(60_000)
        // Never past the end, and nothing when the app reports no position.
        assertThat(MediaControl.livePosition(290_000, 1_000, 1f, 60_000, playing = true, durationMs = 296_000)).isEqualTo(296_000)
        assertThat(MediaControl.livePosition(-1, 1_000, 1f, 6_000, playing = true, durationMs = 296_000)).isNull()
    }

    @Test
    fun `times print like a player shows them`() {
        assertThat(MediaControl.formatTime(90_000)).isEqualTo("1:30")
        assertThat(MediaControl.formatTime(0)).isEqualTo("0:00")
        assertThat(MediaControl.formatTime(3_723_000)).isEqualTo("1:02:03")
        assertThat(MediaControl.formatTime(59_600)).isEqualTo("1:00")
    }

    @Test
    fun `the session is the playing one, the app in front first`() {
        val spotify = Candidate("com.spotify.music", playing = true)
        val youtubePaused = Candidate("com.google.android.youtube", playing = false)
        val youtubePlaying = Candidate("com.google.android.youtube", playing = true)
        val podcastPaused = Candidate("com.podcasts", playing = false)

        assertThat(MediaControl.choose(listOf(spotify, youtubePlaying), "com.google.android.youtube")).isEqualTo(1)
        // Music playing behind a paused video: "pause" means the music.
        assertThat(MediaControl.choose(listOf(youtubePaused, spotify), "com.google.android.youtube")).isEqualTo(1)
        // Nothing plays: the app in front, else the most recent.
        assertThat(MediaControl.choose(listOf(podcastPaused, youtubePaused), "com.google.android.youtube")).isEqualTo(1)
        assertThat(MediaControl.choose(listOf(podcastPaused, youtubePaused), "com.android.launcher3")).isEqualTo(0)
        assertThat(MediaControl.choose(emptyList(), "com.google.android.youtube")).isNull()
    }

    @Test
    fun `seeking needs the session to offer it`() {
        assertThat(MediaControl.canSeek(256L or 4L or 2L)).isTrue()
        assertThat(MediaControl.canSeek(4L or 2L)).isFalse()
    }

    @Test
    fun `status says app, title, artist, state and where`() {
        val s = MediaControl.Status("YouTube", "Lofi radio", "Lofi Girl", "playing", 83_000, 296_000)
        assertThat(MediaControl.describe(s)).isEqualTo("YouTube: playing \"Lofi radio\" by Lofi Girl, 1:23 of 4:56.")
        val live = MediaControl.Status("YouTube", "Live", null, "playing", 3_723_000, null)
        assertThat(MediaControl.describe(live)).isEqualTo("YouTube: playing \"Live\", 1:02:03.")
        assertThat(MediaControl.describe(MediaControl.Status("Spotify", null, null, "paused", null, null))).isEqualTo("Spotify: paused.")
        assertThat(MediaControl.stateName(3)).isEqualTo("playing")
        assertThat(MediaControl.isPlaying(6)).isTrue()
        assertThat(MediaControl.isPlaying(2)).isFalse()
    }
}
