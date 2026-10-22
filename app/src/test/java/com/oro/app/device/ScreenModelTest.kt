package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import org.junit.Test

class ScreenModelTest {

    @Test
    fun `lines are compact but still words`() {
        assertThat(el(3, "button", "Search").render()).isEqualTo("[3] button \"Search\"")
        // Plain text drops its role: most of a screen is text.
        assertThat(el(4, ScreenElement.ROLE_TEXT, "Total: ₹420").render()).isEqualTo("[4] \"Total: ₹420\"")
        assertThat(el(5, "input", "Search YouTube").render()).isEqualTo("[5] input \"Search YouTube\" empty")
        assertThat(el(6, "input", "To", value = "Alice").render()).isEqualTo("[6] input \"To\" = \"Alice\"")
        assertThat(el(7, "switch", "Wi-Fi", checkable = true, checked = true).render()).isEqualTo("[7] switch \"Wi-Fi\" on")
        assertThat(el(8, "button", "Allow", window = ScreenState.WINDOW_DIALOG).render()).isEqualTo("[8] button \"Allow\" @dialog")
        assertThat(el(9, "input", "Password", password = true).render()).isEqualTo("[9] input \"Password\" password")
    }

    @Test
    fun `render lists every element once and counts what it cut`() {
        val many = (0 until 200).map { el(it, ScreenElement.ROLE_TEXT, "Row number $it with some words") }
        val s = ScreenState("p", "App", 1080, 2400, false, many, truncated = 3)
        val out = s.render(maxChars = 1_000)
        assertThat(out.length).isAtMost(1_200)
        assertThat(out).contains("more not shown")
        assertThat(out).contains("[0] \"Row number 0")
        assertThat(out).doesNotContain("[199]")
    }

    @Test
    fun `header mentions the keyboard only when it is open`() {
        assertThat(screen(el(0, "button", "Go")).render()).doesNotContain("keyboard")
        assertThat(screen(el(0, "button", "Go"), keyboard = true).render()).contains("keyboard open")
    }

    @Test
    fun `text repeated inside its own button is dropped and indexes stay dense`() {
        val raw = listOf(
            el(0, "button", "Search", top = 0),
            el(1, ScreenElement.ROLE_TEXT, "Search", top = 10, height = 40),
            el(2, ScreenElement.ROLE_TEXT, "Recent searches", top = 300),
            el(3, "button", "Clear", top = 400),
            el(4, "button", "Clear", top = 400), // exact duplicate stacked on top
        )
        val out = ScreenState.dedupe(raw)
        assertThat(out.map { it.label }).containsExactly("Search", "Recent searches", "Clear").inOrder()
        assertThat(out.map { it.index }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `signature ignores tiny shifts but sees real changes`() {
        val a = screen(el(0, "button", "Play", top = 100))
        val nudged = screen(el(0, "button", "Play", top = 105))
        val scrolled = screen(el(0, "button", "Play", top = 700))
        val toggled = screen(el(0, "button", "Pause", top = 100))
        assertThat(a.signature).isEqualTo(nudged.signature)
        assertThat(a.signature).isNotEqualTo(scrolled.signature)
        assertThat(a.signature).isNotEqualTo(toggled.signature)
    }

    @Test
    fun `hints flag consent banners, permission dialogs, spinners and ads`() {
        val consent = screen(el(0, "button", "Accept all"), el(1, "button", "Reject all"))
        assertThat(consent.hints(adsHandled = false).joinToString()).contains("consent")

        val permission = screen(
            el(0, ScreenElement.ROLE_TEXT, "Allow Maps to access this device's location?", window = ScreenState.WINDOW_DIALOG),
            el(1, "button", "While using the app", window = ScreenState.WINDOW_DIALOG),
            el(2, "button", "Don't allow", window = ScreenState.WINDOW_DIALOG),
        )
        assertThat(permission.hints(adsHandled = false).joinToString()).contains("permission")

        val loading = screen(el(0, ScreenElement.ROLE_PROGRESS, "", clickable = false))
        assertThat(loading.loading).isTrue()
        assertThat(loading.hints(adsHandled = false).joinToString()).contains("loading")

        val ad = screen(el(0, ScreenElement.ROLE_TEXT, "Sponsored · 0:05"), pkg = "com.google.android.youtube")
        assertThat(ad.hints(adsHandled = true).joinToString()).contains("skip")
        assertThat(ad.hints(adsHandled = false)).isEmpty()
    }

    @Test
    fun `an empty screen says how to proceed`() {
        assertThat(screen().render()).contains("No readable elements")
    }
}
