package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.ScreenElement.Companion.ROLE_TEXT
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import org.junit.Test

class HumanCheckTest {

    @Test
    fun `an unticked robot check is a human check, a ticked one is passed`() {
        assertThat(screen(el(0, "checkbox", "I'm not a robot", checkable = true)).humanCheck).isTrue()
        assertThat(screen(el(0, "checkbox", "I'm not a robot", checkable = true, checked = true)).humanCheck).isFalse()
        assertThat(screen(el(0, "checkbox", "I am human", checkable = true)).humanCheck).isTrue()
    }

    @Test
    fun `challenge prompts are human checks`() {
        assertThat(screen(el(0, ROLE_TEXT, "Select all images with traffic lights")).humanCheck).isTrue()
        assertThat(screen(el(0, ROLE_TEXT, "Enter the characters you see")).humanCheck).isTrue()
        assertThat(screen(el(0, ROLE_TEXT, "Verify you are human")).humanCheck).isTrue()
    }

    @Test
    fun `slider puzzles are human checks too, a slide to unlock is not`() {
        assertThat(screen(el(0, ROLE_TEXT, "Slide to complete the puzzle")).humanCheck).isTrue()
        assertThat(screen(el(0, "slider", "Swipe to verify", clickable = false)).humanCheck).isTrue()
        assertThat(screen(el(0, ROLE_TEXT, "Slide to unlock")).humanCheck).isFalse()
    }

    @Test
    fun `the reCAPTCHA badge and text about captchas are not`() {
        assertThat(screen(el(0, ROLE_TEXT, "protected by reCAPTCHA")).humanCheck).isFalse()
        assertThat(screen(el(0, ROLE_TEXT, "reCAPTCHA · Privacy - Terms")).humanCheck).isFalse()
        val article = "Why websites ask you to solve a captcha, and how the checks decide that you are a person"
        assertThat(screen(el(0, ROLE_TEXT, article)).humanCheck).isFalse()
    }
}
