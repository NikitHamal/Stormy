package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.FakePhone.Act
import com.oro.app.device.ScenarioBench.play
import com.oro.app.device.ScreenElement.Companion.ROLE_TEXT
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import com.oro.app.device.ScriptedModel.Companion.calls
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** fill_form, human checks and queued actions through the real run loop, on scripted screens. */
class FormScenarioTest {

    @Test
    fun `fill_form fills a whole sign-up form in one call and submits it`() = runTest {
        val form = screen(
            el(0, ROLE_TEXT, "Create your account", top = 40, height = 60),
            el(1, ROLE_TEXT, "Full name", top = 150, height = 40),
            el(2, "input", "", top = 200),
            el(3, "input", "Email", top = 320),
            el(4, "input", "Confirm email", top = 440),
            el(5, "checkbox", "I agree to the Terms", top = 560, checkable = true),
            el(6, "button", "Continue with Google", top = 680),
            el(7, "button", "Create account", top = 800),
            pkg = "com.shop", label = "Shop",
        )
        val welcome = screen(el(0, ROLE_TEXT, "Welcome, Priya"), pkg = "com.shop", label = "Shop")
        val phone = ScenarioBench.phone(this, form)
        phone.rules = { a -> if (a is Act.Tap && a.label == "Create account") welcome else null }
        ScenarioBench.answerApprovals(this, allow = true)
        val fields = """[{"field":"Full name","value":"Priya Rao"},{"field":"Email","value":"priya@example.com"},""" +
            """{"field":"Confirm email","value":"priya@example.com"},{"field":"I agree to the Terms","value":"yes"}]"""
        val model = ScriptedModel(
            calls("fill_form" to """{"fields":$fields,"submit":true}"""),
            calls("finish_task" to """{"summary":"Account created.","success":true}"""),
        )

        play(phone, model, "Sign me up on Shop as Priya Rao, priya@example.com")

        assertThat(phone.log).containsAtLeast(
            Act.Type("", "Priya Rao"),
            Act.Type("Email", "priya@example.com"),
            Act.Type("Confirm email", "priya@example.com"),
            Act.Tap("I agree to the Terms", 5),
            Act.Tap("Create account", 7),
        ).inOrder()
        assertThat(phone.log).doesNotContain(Act.Tap("Continue with Google", 6))
        assertThat(model.seen[1]).contains("Form: Full name: filled; Email: filled; Confirm email: filled; I agree to the Terms: checked")
        assertThat(model.seen[1]).contains("Submitted:")
    }

    @Test
    fun `a field whose name fits two boxes is reported and the form is not submitted`() = runTest {
        val form = screen(
            el(0, "input", "Name", top = 100),
            el(1, "input", "Name", top = 220),
            el(2, "input", "Email", top = 340),
            el(3, "button", "Submit", top = 460),
            pkg = "com.site", label = "Site",
        )
        val phone = ScenarioBench.phone(this, form)
        val model = ScriptedModel(
            calls("fill_form" to """{"fields":[{"field":"Name","value":"Priya"},{"field":"Email","value":"p@x.io"}],"submit":true}"""),
            calls("finish_task" to """{"summary":"Stopped.","success":false}"""),
        )

        play(phone, model, "Fill in the form")

        assertThat(phone.log).containsExactly(Act.Type("Email", "p@x.io"))
        assertThat(model.seen[1]).contains("Name: fits 2 fields ([0], [1]); give its index")
        assertThat(model.seen[1]).contains("Not submitted: some fields were not filled.")
    }

    @Test
    fun `a radio group, a dropdown and a switch are set in the same call`() = runTest {
        val form = screen(
            el(0, ROLE_TEXT, "Size", top = 100, height = 40),
            el(1, "radio", "Small", top = 150, checkable = true),
            el(2, "radio", "Large", top = 250, checkable = true),
            el(3, "button", "Country", top = 400),
            el(4, "switch", "Gift wrap", top = 520, checkable = true),
            pkg = "com.shop", label = "Shop",
        )
        val options = screen(
            el(0, ROLE_TEXT, "Choose a country"),
            el(1, "button", "India"),
            el(2, "button", "Nepal"),
            pkg = "com.shop", label = "Shop",
        )
        val phone = ScenarioBench.phone(this, form)
        phone.rules = { a ->
            when {
                a is Act.Tap && a.label == "Country" -> options
                a is Act.Tap && a.label == "Nepal" -> form
                else -> null
            }
        }
        val model = ScriptedModel(
            calls("fill_form" to """{"fields":[{"field":"Size","value":"Large"},{"field":"Country","value":"Nepal"},{"field":"Gift wrap","value":"on"}]}"""),
            calls("finish_task" to """{"summary":"Set.","success":true}"""),
        )

        play(phone, model, "Large, to Nepal, gift wrapped")

        assertThat(phone.log).containsExactly(
            Act.Tap("Large", 2), Act.Tap("Country", 3), Act.Tap("Nepal", 2), Act.Tap("Gift wrap", 4),
        ).inOrder()
        assertThat(model.seen[1]).contains("Size: checked; Country: chose \"Nepal\"; Gift wrap: checked")
    }

    @Test
    fun `a human check is handed to the user and the run carries on once it is passed`() = runTest {
        val gate = screen(
            el(0, ROLE_TEXT, "Before you continue"),
            el(1, "checkbox", "I'm not a robot", checkable = true),
            el(2, "button", "Continue"),
            pkg = "com.site", label = "Site",
        )
        val passed = screen(
            el(0, ROLE_TEXT, "Before you continue"),
            el(1, "checkbox", "I'm not a robot", checkable = true, checked = true),
            el(2, "button", "Continue"),
            pkg = "com.site", label = "Site",
        )
        val phone = ScenarioBench.phone(this, gate)
        // The user ticks the box while the island waits; nobody types an answer.
        backgroundScope.launch {
            DeviceAgent.state.collect { s -> if (s.question != null && phone.screen === gate) phone.change(passed) }
        }
        val model = ScriptedModel(
            calls("tap_text" to """{"text":"Continue"}"""),
            calls("finish_task" to """{"summary":"Continued.","success":true}"""),
        )

        play(phone, model, "Open the article")

        assertThat(phone.log).containsExactly(Act.Tap("Continue", 2))
        assertThat(model.seen[1]).contains("The user passed the human check.")
    }

    @Test
    fun `the rest of a turn does not run after an action that did not work, finish_task included`() = runTest {
        val shop = screen(el(0, "input", "Search"), el(1, "button", "Go"), pkg = "com.shop", label = "Shop")
        val phone = ScenarioBench.phone(this, shop)
        val model = ScriptedModel(
            calls(
                "tap_text" to """{"text":"Checkout"}""",
                "tap_text" to """{"text":"Go"}""",
                "finish_task" to """{"summary":"Bought it.","success":true}""",
            ),
            calls("finish_task" to """{"summary":"No checkout on this screen.","success":false}"""),
        )

        play(phone, model, "Buy the item")

        assertThat(phone.log).isEmpty()
        assertThat(model.seen[1]).contains("an earlier action in this turn did not work")
        assertThat(DeviceAgent.state.value.summary).isEqualTo("No checkout on this screen.")
    }
}
