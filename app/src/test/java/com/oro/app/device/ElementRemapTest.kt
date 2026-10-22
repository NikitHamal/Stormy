package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import org.junit.Test

class ElementRemapTest {

    @Test
    fun `same screen keeps the number`() {
        val s = screen(el(0, "button", "A"), el(1, "button", "B"))
        assertThat(ElementRemap.resolve(s, s, 1)).isEqualTo(s.element(1) to false)
    }

    @Test
    fun `a moved element is found again by role and label`() {
        val seen = screen(el(0, "input", "Search"), el(1, "button", "Go", top = 100))
        val now = screen(el(0, ScreenElement.ROLE_TEXT, "Suggestions"), el(1, "input", "Search"), el(2, "button", "Go", top = 600))
        val (match, stale) = ElementRemap.resolve(seen, now, 1)
        assertThat(match?.index).isEqualTo(2)
        assertThat(stale).isFalse()
    }

    @Test
    fun `a vanished element is stale, never whatever now has its number`() {
        val seen = screen(el(0, "button", "Delete"), el(1, "button", "Keep"))
        val now = screen(el(0, "button", "Undo"), el(1, "button", "Done"))
        val (match, stale) = ElementRemap.resolve(seen, now, 0)
        assertThat(match).isNull()
        assertThat(stale).isTrue()
    }

    @Test
    fun `the nearest of several look-alikes wins`() {
        val seen = screen(el(0, "button", "Like", top = 500))
        val now = screen(el(0, "button", "Like", top = 100), el(1, "button", "Like", top = 520), el(2, "button", "Like", top = 900))
        assertThat(ElementRemap.resolve(seen, now, 0).first?.index).isEqualTo(1)
    }
}
