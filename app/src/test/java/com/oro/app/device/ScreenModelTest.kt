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
        assertThat(el(9, "input", "Password", password = true).render()).isEqualTo("[9] input \"Password\" password empty")
        assertThat(el(9, "input", "Password", password = true, secretLength = 8).render()).isEqualTo("[9] input \"Password\" password filled")
    }

    @Test
    fun `a filled field shows what is typed even when it matches the field's name`() {
        // The old rule hid the value when it equalled the label, so right after
        // typing "cats" into a field labelled by its text the model read "empty".
        assertThat(el(3, "input", "cats", value = "cats").render()).isEqualTo("[3] input \"cats\" = \"cats\"")
    }

    @Test
    fun `typing a password changes the signature`() {
        val empty = screen(el(0, "input", "Password", password = true))
        val filled = screen(el(0, "input", "Password", password = true, secretLength = 6))
        assertThat(empty.signature).isNotEqualTo(filled.signature)
    }

    @Test
    fun `hit test finds what a finger would press, in the window on top`() {
        val s = screen(
            el(0, "button", "Buy now", top = 1000, height = 300),                                 // a card
            el(1, ScreenElement.ROLE_TEXT, "₹999", top = 1100, left = 100, right = 400, height = 60),
            el(2, "button", "OK", top = 1050, left = 300, right = 800, height = 120, window = ScreenState.WINDOW_DIALOG),
        )
        // Over the card only: the card takes the tap, the price is what is written there.
        assertThat(s.hitTest(150, 1120)?.label).isEqualTo("Buy now")
        assertThat(s.textAt(150, 1120)?.label).isEqualTo("₹999")
        // Where the dialog covers the card, the dialog wins.
        assertThat(s.hitTest(500, 1100)?.label).isEqualTo("OK")
        assertThat(s.hitTest(500, 50)).isNull()
    }

    @Test
    fun `a text inside a clickable card is received by the card`() {
        val card = el(0, "button", "Buy now", top = 1000, height = 300)
        val price = el(1, ScreenElement.ROLE_TEXT, "₹999", top = 1100, left = 100, right = 400, height = 60)
        val s = screen(card, price)
        assertThat(s.receiverFor(price).label).isEqualTo("Buy now")
        assertThat(s.receiverFor(card)).isEqualTo(card)
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

    @Test
    fun `sliders show their value compactly`() {
        fun slider(label: String, range: ValueRange) = el(4, ScreenElement.ROLE_SLIDER, label, clickable = false).copy(range = range)
        assertThat(slider("Volume", ValueRange(0f, 100f, 35f)).render()).isEqualTo("[4] slider \"Volume\" 35/100")
        assertThat(slider("Brightness", ValueRange(0f, 100f, 60f, percent = true)).render()).isEqualTo("[4] slider \"Brightness\" 60%")
        assertThat(slider("Balance", ValueRange(-10f, 10f, 3f)).render()).isEqualTo("[4] slider \"Balance\" 3 of -10..10")
        assertThat(slider("Speed", ValueRange(0.25f, 2f, 1.5f)).render()).isEqualTo("[4] slider \"Speed\" 1.5 of 0.25..2")
        val progress = el(8, ScreenElement.ROLE_PROGRESS, "", clickable = false).copy(range = ValueRange(0f, 1f, 0.333f))
        assertThat(progress.render()).isEqualTo("[8] progress 0.33/1")
    }

    @Test
    fun `a slider that moved is a changed screen, and an unnamed one says where it is`() {
        fun volume(at: Float) = screen(el(0, ScreenElement.ROLE_SLIDER, "Volume", clickable = false).copy(range = ValueRange(0f, 100f, at)))
        assertThat(volume(35f).signature).isNotEqualTo(volume(40f).signature)
        val unnamed = screen(el(0, ScreenElement.ROLE_SLIDER, "", top = 2200, clickable = false).copy(range = ValueRange(0f, 100f, 35f)))
        assertThat(unnamed.render()).contains("[0] slider (bottom) 35/100")
    }

    @Test
    fun `a playing video's seek bar moving by itself is no change`() {
        fun video(at: Float, label: String) = screen(
            el(0, ScreenElement.ROLE_SLIDER, label, clickable = false, viewId = "com.google.android.youtube:id/time_bar")
                .copy(range = ValueRange(0f, 296f, at)),
        )
        assertThat(video(83f, "1:23 of 4:56").signature).isEqualTo(video(84f, "1:24 of 4:56").signature)
    }

    @Test
    fun `a slider named like a text beside it is never tapped by words alone`() {
        val s = screen(
            el(0, ScreenElement.ROLE_TEXT, "Media volume", top = 300),
            el(1, ScreenElement.ROLE_SLIDER, "Media volume", top = 400, clickable = false).copy(range = ValueRange(0f, 15f, 5f)),
        )
        // A tap at a slider's middle would set it to half: tap_text asks which one is meant.
        assertThat(TextMatch.find("Media volume", s)).isInstanceOf(TextMatch.Result.Ambiguous::class.java)
    }
}
