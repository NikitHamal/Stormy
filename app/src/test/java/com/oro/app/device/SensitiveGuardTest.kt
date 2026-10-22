package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class SensitiveGuardTest {

    private fun args(vararg pairs: Pair<String, Any>) = JsonObject(pairs.associate { (k, v) ->
        k to when (v) {
            is Int -> JsonPrimitive(v)
            is Double -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            else -> JsonPrimitive(v.toString())
        }
    })

    private val chat = screen(
        el(0, "input", "Message", value = "See you at 6"),
        el(1, "button", "Send"),
        el(2, "button", "Sender info"),
        pkg = "com.whatsapp", label = "WhatsApp",
    )

    /** The guard as the executor uses it: on the resolved target. */
    private fun tap(a: JsonObject, now: ScreenState, seen: ScreenState? = null) =
        SensitiveGuard.tap(Targets.tap(a, seen, now), now)

    @Test
    fun `tapping Send asks first, a look-alike label does not`() {
        assertThat(tap(args("index" to 1), chat)).contains("Send")
        assertThat(tap(args("index" to 2), chat)).isNull()
    }

    @Test
    fun `tap_text is checked against the element it would tap, under either key`() {
        assertThat(SensitiveGuard.tap(Targets.text(args("text" to "send"), null, chat), chat)).contains("Send")
        // Models also write label; the guard used to read only text and waved it through.
        assertThat(SensitiveGuard.tap(Targets.text(args("label" to "Send"), null, chat), chat)).contains("Send")
        assertThat(SensitiveGuard.tap(Targets.text(args("text" to "nothing like it"), null, chat), chat)).isNull()
    }

    @Test
    fun `an index written as a float is the same index for the guard`() {
        assertThat(tap(args("index" to 1.0), chat)).contains("Send")
        assertThat(tap(args("index" to " 1"), chat)).contains("Send")
    }

    @Test
    fun `coordinates are judged by what is under the finger`() {
        val sendAt = chat.element(1)!!
        assertThat(tap(args("x" to sendAt.centerX, "y" to sendAt.centerY), chat)).contains("Send")
        assertThat(tap(args("x" to 10, "y" to 2_300), chat)).isNull()
    }

    @Test
    fun `a plain text inside a buy card asks, because the card takes the tap`() {
        val shop = screen(
            el(0, "button", "Buy now", top = 1000, height = 300),
            el(1, ScreenElement.ROLE_TEXT, "₹999", top = 1100, left = 100, right = 400, height = 60),
            pkg = "in.amazon.mShop.android.shopping", label = "Amazon",
        )
        assertThat(tap(args("index" to 1), shop)).contains("Buy now")
    }

    @Test
    fun `a row that shifted under a multi-call turn is judged as what will really be tapped`() {
        // The model saw "Place order" at [2]; applying a coupon added a row,
        // so [2] is now "Total". The executor remaps to the real "Place order"
        // and the guard must judge that same element.
        val seen = screen(el(0, "button", "Apply coupon"), el(1, ScreenElement.ROLE_TEXT, "Total ₹450"), el(2, "button", "Place order"), pkg = "shop")
        val now = screen(
            el(0, "button", "Apply coupon"), el(1, ScreenElement.ROLE_TEXT, "Coupon SAVE10 applied"),
            el(2, ScreenElement.ROLE_TEXT, "Total ₹405"), el(3, "button", "Place order"), pkg = "shop",
        )
        val target = Targets.tap(args("index" to 2), seen, now)
        assertThat((target as Target.OnElement).element.index).isEqualTo(3)
        assertThat(SensitiveGuard.tap(target, now)).contains("Place order")
    }

    @Test
    fun `typing with submit in a messaging app asks first`() {
        assertThat(SensitiveGuard.type(chat.element(0), "hi", submit = true, screen = chat)).isNotNull()
        assertThat(SensitiveGuard.type(chat.element(0), "hi", submit = false, screen = chat)).isNull()
    }

    @Test
    fun `password fields always ask`() {
        val login = screen(el(0, "input", "Password", password = true), pkg = "com.bank")
        assertThat(SensitiveGuard.type(login.element(0), "x", submit = false, screen = login)).contains("password")
    }

    @Test
    fun `slide to pay asks, links that call or text ask`() {
        val pay = screen(el(0, "button", "Slide to pay ₹1,200", top = 2000, height = 160), pkg = "upi")
        val start = Targets.start(args("x1" to 100, "y1" to 2_080, "x2" to 1_000, "y2" to 2_080), "x1", "y1", null, pay)
        assertThat(SensitiveGuard.slide(start, pay)).contains("Slide to pay")
        assertThat(SensitiveGuard.url("tel:+15550100")).isNotNull()
        assertThat(SensitiveGuard.url("https://example.com")).isNull()
    }

    @Test
    fun `routine taps pass`() {
        val s = screen(el(0, "button", "Next"), el(1, "button", "Search"))
        assertThat(tap(args("index" to 0), s)).isNull()
    }
}
