package com.oro.app.browser

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.AiHttp
import org.junit.Test

class FormFillWebTest {

    private val signup = PageFixtures.page(
        PageFixtures.el(1, "label", "Full name", top = 60, height = 40, editable = false),
        PageFixtures.el(2, "textbox", "", top = 100, selector = "input#fullname"),
        PageFixtures.el(3, "textbox", "Email address", top = 200, selector = "input[name=email]"),
        PageFixtures.el(4, "textbox", "Confirm email", top = 300, selector = "input#confirm"),
        PageFixtures.el(5, "combobox", "Country", top = 400, editable = false, options = 12),
        PageFixtures.el(6, "radio", "Female", top = 500, editable = false, checked = false),
        PageFixtures.el(7, "radio", "Male", top = 560, editable = false, checked = false),
        PageFixtures.el(8, "checkbox", "Send me offers", top = 640, editable = false, checked = false),
        PageFixtures.el(9, "button", "Continue with Google", top = 740, editable = false),
        PageFixtures.el(10, "button", "Create account", top = 840, editable = false),
    )

    @Test
    fun `fields are found by their own label`() {
        val m = FormFillWeb.find("Email address", signup)
        assertThat((m as FormFillWeb.Match.Found).element.index).isEqualTo(3)
    }

    @Test
    fun `Confirm email is its own field, not Email`() {
        val m = FormFillWeb.find("Confirm email", signup)
        assertThat((m as FormFillWeb.Match.Found).element.index).isEqualTo(4)
    }

    @Test
    fun `a name in the id or the name attribute finds the field`() {
        val m = FormFillWeb.find("fullname", signup)
        assertThat((m as FormFillWeb.Match.Found).element.index).isEqualTo(2)
    }

    @Test
    fun `a label rendered above an unnamed box points at it`() {
        // "Full name" is its own element above the empty input with no label of its own.
        val m = FormFillWeb.find("Full name", signup)
        assertThat((m as FormFillWeb.Match.Found).element.index).isEqualTo(2)
    }

    @Test
    fun `a used element is never matched twice -- and a weak second choice is not guessed`() {
        val first = FormFillWeb.find("Email", signup) as FormFillWeb.Match.Found
        assertThat(first.element.index).isEqualTo(3)
        val second = FormFillWeb.find("Email", signup, used = setOf(first.element.index))
        // "Confirm email" contains "Email" as a whole word: the next-best match, not a guess
        // between equals (an equal-score tie would be Ambiguous instead).
        assertThat((second as FormFillWeb.Match.Found).element.index).isEqualTo(4)
    }

    @Test
    fun `a radio group answers by its option, not its question`() {
        val entry = FormFillWeb.Entry("Gender", null, "Female")
        val m = FormFillWeb.target(entry, signup)
        assertThat(m).isInstanceOf(FormFillWeb.Match.Found::class.java)
        assertThat((m as FormFillWeb.Match.Found).element.index).isEqualTo(6)
    }

    @Test
    fun `yes-no values mean a toggle`() {
        assertThat(FormFillWeb.wantChecked("yes")).isTrue()
        assertThat(FormFillWeb.wantChecked("FALSE")).isFalse()
        assertThat(FormFillWeb.wantChecked("priya@example.com")).isNull()
    }

    @Test
    fun `the submit button is the form's own, never Continue with Google`() {
        val b = FormFillWeb.submitButton(signup)
        assertThat(b?.index).isEqualTo(10)
    }

    @Test
    fun `entries parse from a list, an object, or JSON written as text`() {
        val list = AiHttp.json.parseToJsonElement("""[{"field":"Email","value":"a@b.c"},{"index":5,"value":"India"}]""")
        val entries = FormFillWeb.entries(list)
        assertThat(entries).hasSize(2)
        assertThat(entries[0].field).isEqualTo("Email")
        assertThat(entries[1].index).isEqualTo(5)

        val obj = AiHttp.json.parseToJsonElement("""{"Email":"a@b.c","Country":"India"}""")
        assertThat(FormFillWeb.entries(obj).map { it.field }).containsExactly("Email", "Country").inOrder()

        // The text tool protocol hands raw JSON as a string.
        val text = kotlinx.serialization.json.JsonPrimitive("""[{"field":"Email","value":"a@b.c"}]""")
        assertThat(FormFillWeb.entries(text)).hasSize(1)
    }

    @Test
    fun `entries without a value or a way to find the field are dropped`() {
        val raw = AiHttp.json.parseToJsonElement("""[{"field":"Email"},{"value":"orphan"},{"field":"Name","value":"x"}]""")
        val entries = FormFillWeb.entries(raw)
        assertThat(entries).hasSize(1)
        assertThat(entries[0].field).isEqualTo("Name")
    }

    @Test
    fun `kindOf tells typing from toggling from picking`() {
        assertThat(FormFillWeb.kindOf(signup.element(3)!!)).isEqualTo(FormFillWeb.Kind.TEXT)
        assertThat(FormFillWeb.kindOf(signup.element(8)!!)).isEqualTo(FormFillWeb.Kind.TOGGLE)
        assertThat(FormFillWeb.kindOf(signup.element(5)!!)).isEqualTo(FormFillWeb.Kind.CHOICE)
    }
}
