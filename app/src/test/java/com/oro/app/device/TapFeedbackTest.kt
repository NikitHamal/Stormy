package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.TypedText.Readback
import org.junit.Test

class TapFeedbackTest {

    @Test
    fun `a tap by coordinates says what took it`() {
        val ok = TapHit(at = 10, role = "button", label = "OK", bounds = Bounds(400, 1000, 680, 1120))
        assertThat(TapFeedback.line(ok)).isEqualTo("→ hit button \"OK\"")
        assertThat(TapFeedback.line(ok.copy(label = ""))).isEqualTo("→ hit button")
        assertThat(TapFeedback.line(null)).isEqualTo("→ nothing registered the tap")
    }

    @Test
    fun `clicked views get the listing's role words`() {
        assertThat(TapFeedback.roleOf("android.widget.Button")).isEqualTo("button")
        assertThat(TapFeedback.roleOf("android.widget.ImageButton")).isEqualTo("icon")
        assertThat(TapFeedback.roleOf("com.google.android.material.materialswitch.MaterialSwitch")).isEqualTo("switch")
        assertThat(TapFeedback.roleOf("android.widget.CheckBox")).isEqualTo("checkbox")
        assertThat(TapFeedback.roleOf("android.widget.EditText")).isEqualTo("input")
        assertThat(TapFeedback.roleOf("android.widget.TableRow")).isEqualTo("view")
        assertThat(TapFeedback.roleOf(null)).isEqualTo("view")
    }

    @Test
    fun `the label is the first words found, on one line and short`() {
        assertThat(TapFeedback.label(null, "  ", "Add\nto cart", "ignored")).isEqualTo("Add to cart")
        assertThat(TapFeedback.label("x".repeat(60)).length).isEqualTo(40)
        assertThat(TapFeedback.label(null, null)).isEmpty()
    }

    @Test
    fun `a click elsewhere is not this tap's`() {
        val ok = TapHit(at = 10, role = "button", label = "OK", bounds = Bounds(400, 1000, 680, 1120))
        assertThat(TapFeedback.near(ok, 540, 1060, slop = 0)).isTrue()
        assertThat(TapFeedback.near(ok, 390, 1060, slop = 24)).isTrue()
        assertThat(TapFeedback.near(ok, 100, 300, slop = 24)).isFalse()
        // The view left with the click (it opened another screen): nothing to check against.
        assertThat(TapFeedback.near(ok.copy(bounds = null), 100, 300, slop = 24)).isTrue()
    }

    @Test
    fun `a typed field counts as kept when it shows the text, formatted or completed`() {
        assertThat(TypedText.check("cats", "cats")).isEqualTo(Readback.KEPT)
        assertThat(TypedText.check("5551234567", "(555) 123-4567")).isEqualTo(Readback.KEPT)
        assertThat(TypedText.check("you", "youtube.com")).isEqualTo(Readback.KEPT)
        assertThat(TypedText.check("Hello there", "HELLO THERE")).isEqualTo(Readback.KEPT)
        // A web page put its own state back: the old text, or nothing.
        assertThat(TypedText.check("new name", "old name")).isEqualTo(Readback.LOST)
        assertThat(TypedText.check("cats", "")).isEqualTo(Readback.LOST)
        // Emptying a field means empty.
        assertThat(TypedText.check("", "")).isEqualTo(Readback.KEPT)
        assertThat(TypedText.check("", "draft")).isEqualTo(Readback.LOST)
        assertThat(TypedText.check("👍", "👍")).isEqualTo(Readback.KEPT)
        // A field that does not say what it holds cannot be judged.
        assertThat(TypedText.check("cats", null)).isEqualTo(Readback.UNKNOWN)
    }
}
