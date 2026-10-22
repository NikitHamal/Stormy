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
    fun `several look-alikes are stale, not the nearest`() {
        // After deleting one row the list shifts up: the "Delete" nearest to
        // where the chosen one was is the next row's.
        val seen = screen(el(0, "button", "Delete", top = 500), el(1, "button", "Delete", top = 900))
        val now = screen(el(0, "button", "Delete", top = 100), el(1, "button", "Delete", top = 520), el(2, "button", "Undo", top = 1500))
        val (match, stale) = ElementRemap.resolve(seen, now, 0)
        assertThat(match).isNull()
        assertThat(stale).isTrue()
    }

    @Test
    fun `a view id that only one look-alike carries settles it`() {
        val seen = screen(el(0, "button", "Share", viewId = "app:id/share_post"), el(1, "button", "Share", viewId = "app:id/share_story", top = 600))
        val now = screen(el(0, "button", "Share", viewId = "app:id/share_story", top = 50), el(1, "button", "Share", viewId = "app:id/share_post", top = 700))
        assertThat(ElementRemap.resolve(seen, now, 0).first?.index).isEqualTo(1)
    }

    @Test
    fun `an unlabelled element needs the same view id`() {
        val seen = screen(el(0, "icon", "", viewId = "app:id/menu"), el(1, "button", "Next", top = 600))
        val sameIcon = screen(el(0, ScreenElement.ROLE_TEXT, "Inbox"), el(1, "icon", "", viewId = "app:id/menu", top = 300))
        assertThat(ElementRemap.resolve(seen, sameIcon, 0).first?.index).isEqualTo(1)
        // Same slot, different control: not the same icon.
        val otherIcon = screen(el(0, ScreenElement.ROLE_TEXT, "Inbox"), el(1, "icon", "", viewId = "app:id/search", top = 0))
        assertThat(ElementRemap.resolve(seen, otherIcon, 0)).isEqualTo(null to true)
        val noId = screen(el(0, "icon", ""), el(1, "button", "Next", top = 600))
        assertThat(ElementRemap.resolve(noId, sameIcon, 0)).isEqualTo(null to true)
    }

    @Test
    fun `another app in front makes every number stale`() {
        val seen = screen(el(0, "button", "Send"), pkg = "com.whatsapp")
        val now = screen(el(0, "button", "Send"), el(1, "button", "Discard"), pkg = "com.google.android.gm")
        assertThat(ElementRemap.resolve(seen, now, 0)).isEqualTo(null to true)
    }
}
