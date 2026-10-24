package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.ScreenElement.Companion.ROLE_TEXT
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.Test

class FormFillTest {

    private fun entries(json: String) = FormFill.entries(Json.parseToJsonElement(json))

    @Test
    fun `fields come as a list, a label-to-value object, or either as JSON text`() {
        val list = entries("""[{"field":"Email","value":"a@b.c"},{"index":4,"value":"x"},{"label":"Name","value":"Priya"}]""")
        assertThat(list).containsExactly(
            FormFill.Entry("Email", null, "a@b.c"), FormFill.Entry(null, 4, "x"), FormFill.Entry("Name", null, "Priya"),
        ).inOrder()
        assertThat(entries("""{"Email":"a@b.c","City":"Pokhara"}""").map { it.field }).containsExactly("Email", "City").inOrder()
        val asText = FormFill.entries(JsonPrimitive("""[{"field":"Email","value":"a@b.c"}]"""))
        assertThat(asText).containsExactly(FormFill.Entry("Email", null, "a@b.c"))
    }

    @Test
    fun `entries with no value or no way to find the field are dropped`() {
        assertThat(entries("""[{"field":"Email"},{"value":"x"},{"field":"City","value":"Pokhara"}]""").map { it.field })
            .containsExactly("City")
    }

    @Test
    fun `a field is found by its own name, and each field is used once`() {
        val s = screen(el(0, "input", "Email"), el(1, "input", "Confirm email"))
        assertThat((FormFill.find("Email", s) as FormFill.Match.Found).element.index).isEqualTo(0)
        assertThat((FormFill.find("Confirm email", s) as FormFill.Match.Found).element.index).isEqualTo(1)
        assertThat((FormFill.find("Email", s, used = setOf(0)) as FormFill.Match.Found).element.index).isEqualTo(1)
    }

    @Test
    fun `a nameless box is found by the label above it`() {
        val s = screen(
            el(0, ROLE_TEXT, "Full name", top = 150, height = 40),
            el(1, "input", "", top = 200),
            el(2, ROLE_TEXT, "Phone", top = 320, height = 40),
            el(3, "input", "", top = 370),
        )
        assertThat((FormFill.find("Full name", s) as FormFill.Match.Found).element.index).isEqualTo(1)
        assertThat((FormFill.find("Phone", s) as FormFill.Match.Found).element.index).isEqualTo(3)
    }

    @Test
    fun `a label beside its box finds the box on its row, not the one below`() {
        val s = screen(
            el(0, ROLE_TEXT, "City", top = 200, right = 300, height = 60),
            el(1, "input", "", top = 195, left = 320, height = 70),
            el(2, ROLE_TEXT, "Postcode", top = 300, right = 300, height = 60),
            el(3, "input", "", top = 295, left = 320, height = 70),
        )
        assertThat((FormFill.find("City", s) as FormFill.Match.Found).element.index).isEqualTo(1)
        assertThat((FormFill.find("Postcode", s) as FormFill.Match.Found).element.index).isEqualTo(3)
    }

    @Test
    fun `two fields with the same name are reported, never guessed`() {
        val s = screen(el(0, "input", "Name"), el(1, "input", "Name"))
        val m = FormFill.find("Name", s)
        assertThat(m).isInstanceOf(FormFill.Match.Ambiguous::class.java)
        assertThat((m as FormFill.Match.Ambiguous).candidates.map { it.index }).containsExactly(0, 1)
    }

    @Test
    fun `nothing close enough is missing`() {
        assertThat(FormFill.find("Passport number", screen(el(0, "input", "Email")))).isEqualTo(FormFill.Match.Missing)
    }

    @Test
    fun `a radio group takes the option showing the value, not the first box under the question`() {
        val s = screen(
            el(0, ROLE_TEXT, "Gender", top = 100, height = 40),
            el(1, "radio", "Female", top = 150, checkable = true),
            el(2, "radio", "Male", top = 250, checkable = true),
        )
        val male = FormFill.target(FormFill.Entry("Gender", null, "Male"), s)
        assertThat((male as FormFill.Match.Found).element.index).isEqualTo(2)
        val female = FormFill.target(FormFill.Entry("Gender", null, "Female"), s)
        assertThat((female as FormFill.Match.Found).element.index).isEqualTo(1)
    }

    @Test
    fun `a checkbox keeps its own match for a yes or no`() {
        val s = screen(el(0, "checkbox", "I agree to the Terms", checkable = true), el(1, "checkbox", "Send me offers", checkable = true))
        val agree = FormFill.target(FormFill.Entry("I agree to the Terms", null, "yes"), s)
        assertThat((agree as FormFill.Match.Found).element.index).isEqualTo(0)
        assertThat(FormFill.wantChecked("yes")).isTrue()
        assertThat(FormFill.wantChecked("Off")).isFalse()
        assertThat(FormFill.wantChecked("Nepal")).isNull()
    }

    @Test
    fun `the submit button is the lowest one that says so`() {
        val s = screen(
            el(0, "button", "Sign in with Google", top = 100),
            el(1, "input", "Email", top = 300),
            el(2, "button", "Continue", top = 900),
            el(3, "button", "Help", top = 1000),
        )
        assertThat(FormFill.submitButton(s)?.index).isEqualTo(2)
        assertThat(FormFill.submitButton(screen(el(0, "button", "Help")))).isNull()
    }

    @Test
    fun `kinds follow what the element is`() {
        assertThat(FormFill.kindOf(el(0, "input", "Email"))).isEqualTo(FormFill.Kind.TEXT)
        assertThat(FormFill.kindOf(el(0, "switch", "Wi-Fi", checkable = true))).isEqualTo(FormFill.Kind.TOGGLE)
        assertThat(FormFill.kindOf(el(0, "button", "Country"))).isEqualTo(FormFill.Kind.CHOICE)
    }
}
