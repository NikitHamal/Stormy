package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdSkipperTest {

    private val yt = "com.google.android.youtube"

    @Test
    fun `youtube skip buttons match by id or label`() {
        assertThat(AdSkipper.isSkipButton(yt, null, null, "$yt:id/skip_ad_button")).isTrue()
        assertThat(AdSkipper.isSkipButton(yt, "Skip", null, null)).isTrue()
        assertThat(AdSkipper.isSkipButton(yt, "Skip ads", null, null)).isTrue()
        assertThat(AdSkipper.isSkipButton(yt, null, "Skip ad ›", null)).isTrue()
        assertThat(AdSkipper.isSkipButton("com.google.android.apps.youtube.music", "Skip", null, null)).isTrue()
    }

    @Test
    fun `countdowns are not clickable yet`() {
        assertThat(AdSkipper.isSkipButton(yt, "Skip ad in 5", null, null)).isFalse()
        assertThat(AdSkipper.isSkipButton(yt, "5", "Skip ad in 5 seconds", null)).isFalse()
    }

    @Test
    fun `outside youtube a bare Skip is not an ad`() {
        assertThat(AdSkipper.isSkipButton("com.example.onboarding", "Skip", null, null)).isFalse()
        assertThat(AdSkipper.isSkipButton("com.example.player", "Skip ad", null, null)).isTrue()
        assertThat(AdSkipper.isSkipButton("com.example.player", "Skip intro", null, null)).isFalse()
        assertThat(AdSkipper.isSkipButton("com.example.player", null, null, "com.example:id/skip_ad_button")).isFalse()
    }
}
