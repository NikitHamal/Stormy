package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.ScreenElement.Companion.ROLE_LIST
import com.oro.app.device.ScreenElement.Companion.ROLE_TEXT
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import org.junit.Test

/** What the screen listing tells the model beyond the elements themselves. */
class ScreenCuesTest {

    @Test
    fun `lists say whether there is more to see`() {
        val list = el(0, ROLE_LIST, "", height = 1800)
        assertThat(list.copy(canScrollForward = true).render()).isEqualTo("[0] list more below")
        assertThat(list.copy(canScrollBack = true, canScrollForward = true).render()).isEqualTo("[0] list more above and below")
        assertThat(list.copy(canScrollBack = true).render()).isEqualTo("[0] list more above")
        assertThat(list.copy(canScrollForward = true, horizontal = true).render()).isEqualTo("[0] list more to the right")
        // Nothing known: a plain list, as before.
        assertThat(list.render()).isEqualTo("[0] list")
        assertThat(list.copy(canScrollBack = true).canScroll("down")).isFalse()
        assertThat(list.copy(canScrollBack = true).canScroll("up")).isTrue()
        assertThat(list.canScroll("down")).isNull()
    }

    @Test
    fun `the end of a list changes the screen's signature`() {
        val more = screen(el(0, ROLE_LIST, "").copy(canScrollForward = true))
        val end = screen(el(0, ROLE_LIST, "").copy(canScrollBack = true))
        assertThat(more.signature).isNotEqualTo(end.signature)
    }

    @Test
    fun `an icon with no name says where it sits`() {
        val s = screen(
            el(0, "icon", "", top = 60, left = 960, right = 1060, height = 100),
            el(1, "icon", "", top = 60, left = 20, right = 120, height = 100),
            el(2, "icon", "Search", top = 60, left = 800, right = 900, height = 100),
            el(3, ROLE_TEXT, "Title", top = 1200),
            el(4, "button", "", top = 2250, left = 440, right = 640, height = 100),
        )
        val text = s.render()
        assertThat(text).contains("[0] icon (top right)")
        assertThat(text).contains("[1] icon (top left)")
        assertThat(text).contains("[2] icon \"Search\"")
        assertThat(text).contains("[4] button (bottom)")
    }

    @Test
    fun `the main list is the big vertical one in the app, not a carousel or a dialog`() {
        val s = screen(
            el(0, ROLE_LIST, "", top = 300, height = 300).copy(horizontal = true, canScrollForward = true),
            el(1, ROLE_LIST, "", top = 700, height = 1500).copy(canScrollForward = true),
            el(2, ROLE_LIST, "", top = 900, height = 1600, window = ScreenState.WINDOW_DIALOG).copy(canScrollForward = true),
        )
        assertThat(s.mainList()?.index).isEqualTo(1)
    }

    @Test
    fun `sign-in walls are named, a newsletter box is not`() {
        val password = screen(el(0, "input", "Email"), el(1, "input", "Password", password = true), el(2, "button", "Log in"))
        assertThat(password.hints(false).joinToString()).contains("asks to sign in")
        val emailFirst = screen(el(0, "input", "Email or phone"), el(1, "button", "Sign in"))
        assertThat(emailFirst.hints(false).joinToString()).contains("asks to sign in")
        val sso = screen(el(0, ROLE_TEXT, "Sign in to save your playlist"), el(1, "button", "Continue with Google"))
        assertThat(sso.hints(false).joinToString()).contains("asks to sign in")
        val newsletter = screen(el(0, ROLE_TEXT, "Get our weekly news"), el(1, "input", "Your email"), el(2, "button", "Sign up"))
        assertThat(newsletter.hints(false).joinToString()).doesNotContain("sign in")
    }

    @Test
    fun `captchas, errors and nags are named, ordinary screens are quiet`() {
        val captcha = screen(el(0, "checkbox", "I'm not a robot", checkable = true))
        assertThat(captcha.hints(false).joinToString()).contains("human check")
        val offline = screen(el(0, ROLE_TEXT, "You're offline"), el(1, "button", "Retry"))
        assertThat(offline.hints(false).joinToString()).contains("shows an error (\"You're offline\")")
        val rating = screen(el(0, ROLE_TEXT, "Enjoying Shopsy?"), el(1, "button", "Not now"), el(2, "button", "Rate"))
        assertThat(rating.hints(false).joinToString()).contains("dismiss it with \"Not now\"")
        // "Update" with nothing to dismiss is a normal button, e.g. in Settings.
        val settings = screen(el(0, "button", "Update now"), el(1, ROLE_TEXT, "Version 4.2"))
        assertThat(settings.hints(false)).isEmpty()
        val plain = screen(el(0, "button", "Next"), el(1, "input", "Name"), el(2, ROLE_TEXT, "Something to read"))
        assertThat(plain.hints(false)).isEmpty()
    }
}

class KeyboardCoverTest {
    @Test
    fun `what the open keyboard covers is marked, dialogs above it are not`() {
        val s = ScreenState(
            "com.chat", "Chat", 1080, 2400, keyboardVisible = true,
            elements = listOf(
                el(0, "input", "Message", top = 1300, height = 120),
                el(1, "button", "Send", top = 1300, left = 950, right = 1060, height = 120),
                el(2, ScreenElement.ROLE_TEXT, "Older message", top = 1900),
                el(3, "button", "OK", top = 1900, window = ScreenState.WINDOW_DIALOG),
            ),
            truncated = 0, keyboardTop = 1500,
        )
        val text = s.render()
        assertThat(text).contains("[1] button \"Send\"\n")
        assertThat(text).contains("[2] \"Older message\" under keyboard")
        assertThat(text).doesNotContain("\"OK\" under keyboard")
        assertThat(s.underKeyboard(s.elements[2])).isTrue()
        assertThat(screen(el(0, "button", "Send", top = 1900)).underKeyboard(el(0, "button", "Send", top = 1900))).isFalse()
    }
}
