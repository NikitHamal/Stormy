package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.ScreenElement.Companion.ROLE_TEXT
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import org.junit.Test

class TapPlanTest {

    private val button = Bounds(100, 100, 400, 220)

    @Test
    fun `a clickable element uses its own click`() {
        assertThat(TapPlan.choose(true, button, null, covered = false)).isEqualTo(TapPlan.Way.CLICK_SELF)
    }

    @Test
    fun `a label inside its button goes through the button's click`() {
        val label = Bounds(130, 130, 370, 190)
        assertThat(TapPlan.choose(false, label, button, covered = false)).isEqualTo(TapPlan.Way.CLICK_PARENT)
    }

    @Test
    fun `a part inside a big card is tapped where it is, not through the card`() {
        val menuIcon = Bounds(960, 420, 1040, 500)
        val card = Bounds(0, 300, 1080, 900)
        assertThat(TapPlan.choose(false, menuIcon, card, covered = false)).isEqualTo(TapPlan.Way.GESTURE)
    }

    @Test
    fun `a covered part goes through its parent's click, which still reaches it`() {
        val menuIcon = Bounds(960, 420, 1040, 500)
        val card = Bounds(0, 300, 1080, 900)
        assertThat(TapPlan.choose(false, menuIcon, card, covered = true)).isEqualTo(TapPlan.Way.CLICK_PARENT)
    }

    @Test
    fun `nothing clickable around it means a finger`() {
        assertThat(TapPlan.choose(false, button, null, covered = false)).isEqualTo(TapPlan.Way.GESTURE)
    }

    @Test
    fun `a point on a small button taps the button by its node`() {
        val s = screen(el(0, "button", "Send", top = 2000, left = 900, right = 1040, height = 120))
        assertThat(TapPlan.snap(s, 905, 2002)?.label).isEqualTo("Send")
    }

    @Test
    fun `a point just beside one small control is moved onto it`() {
        val s = screen(el(0, "checkbox", "Remember me", top = 800, left = 40, right = 120, height = 80, checkable = true))
        // 20 px under the box: inside a fingertip on a 1080-wide screen (27 px).
        assertThat(TapPlan.snap(s, 80, 899)?.label).isEqualTo("Remember me")
        assertThat(TapPlan.snap(s, 80, 960)).isNull()
    }

    @Test
    fun `between two close controls only a clearly nearer one is taken`() {
        val s = screen(
            el(0, "button", "−", top = 500, left = 100, right = 200, height = 100),
            el(1, "button", "+", top = 500, left = 230, right = 330, height = 100),
        )
        assertThat(TapPlan.snap(s, 215, 550)).isNull()
        assertThat(TapPlan.snap(s, 203, 550)?.label).isEqualTo("−")
    }

    @Test
    fun `big, positional and value elements keep the point`() {
        val s = screen(
            el(0, "button", "Video player", top = 0, height = 700),
            el(1, "slider", "Seek", top = 700, height = 40),
            el(2, "input", "Message", top = 2000, height = 120),
        )
        assertThat(TapPlan.snap(s, 300, 300)).isNull()
        assertThat(TapPlan.snap(s, 300, 720)).isNull()
        assertThat(TapPlan.snap(s, 300, 2050)).isNull()
    }

    @Test
    fun `a floating button over a row covers the row's text`() {
        val s = screen(
            el(0, "button", "Inbox row", top = 1800, height = 200),
            el(1, ROLE_TEXT, "Meeting notes", top = 1850, left = 40, right = 1040, height = 80),
            el(2, "button", "Compose", top = 1820, left = 400, right = 700, height = 150),
        )
        assertThat(TapPlan.covered(s, s.elements[1])).isTrue()
        val plain = screen(el(0, "button", "Inbox row", top = 1800, height = 200), el(1, ROLE_TEXT, "Meeting notes", top = 1850, height = 80))
        assertThat(TapPlan.covered(plain, plain.elements[1])).isFalse()
    }

    @Test
    fun `distance is zero inside and grows outside`() {
        assertThat(TapPlan.distance(button, 200, 150)).isEqualTo(0)
        assertThat(TapPlan.distance(button, 90, 150)).isEqualTo(10)
        assertThat(TapPlan.distance(button, 200, 230)).isEqualTo(11)
    }
}
