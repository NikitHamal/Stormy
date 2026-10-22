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

    @Test
    fun `tapping Send asks first, a look-alike label does not`() {
        assertThat(SensitiveGuard.check("tap", args("index" to 1), chat)).contains("Send")
        assertThat(SensitiveGuard.check("tap", args("index" to 2), chat)).isNull()
    }

    @Test
    fun `tap_text is checked against the element it would tap`() {
        assertThat(SensitiveGuard.check("tap_text", args("text" to "send"), chat)).contains("Send")
        assertThat(SensitiveGuard.check("tap_text", args("text" to "nothing like it"), chat)).isNull()
    }

    @Test
    fun `typing with submit in a messaging app asks first`() {
        assertThat(SensitiveGuard.check("type_text", args("index" to 0, "text" to "hi", "submit" to true), chat)).isNotNull()
        assertThat(SensitiveGuard.check("type_text", args("index" to 0, "text" to "hi"), chat)).isNull()
    }

    @Test
    fun `password fields always ask`() {
        val login = screen(el(0, "input", "Password", password = true), pkg = "com.bank")
        assertThat(SensitiveGuard.check("type_text", args("index" to 0, "text" to "x"), login)).contains("password")
    }

    @Test
    fun `routine taps pass`() {
        val s = screen(el(0, "button", "Next"), el(1, "button", "Search"))
        assertThat(SensitiveGuard.check("tap", args("index" to 0), s)).isNull()
        assertThat(SensitiveGuard.check("scroll", args("direction" to "down"), s)).isNull()
    }
}
