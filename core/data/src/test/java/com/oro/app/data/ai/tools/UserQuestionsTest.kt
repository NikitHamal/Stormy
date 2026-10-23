package com.oro.app.data.ai.tools

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class UserQuestionsTest {

    private fun parse(json: String) = UserQuestions.parse(Json.parseToJsonElement(json) as JsonObject)

    @Test
    fun `batched questions keep header, rich options and multiSelect`() {
        val qs = parse(
            """{"questions":[
              {"question":"Which framework?","header":"Framework","options":[{"label":"React","description":"SPA"},{"label":"Vue"}]},
              {"question":"Which features?","options":["Auth","Dark mode"],"multiSelect":true}
            ]}"""
        )
        assertThat(qs).hasSize(2)
        assertThat(qs[0].header).isEqualTo("Framework")
        assertThat(qs[0].options.map { it.label }).containsExactly("React", "Vue").inOrder()
        assertThat(qs[0].options[0].description).isEqualTo("SPA")
        assertThat(qs[1].multiSelect).isTrue()
    }

    @Test
    fun `old single-question shape and comma options still work`() {
        val qs = parse("""{"question":"Colour?","options":"Blue, Green, Blue"}""")
        assertThat(qs).hasSize(1)
        assertThat(qs[0].options.map { it.label }).containsExactly("Blue", "Green").inOrder()
    }

    @Test
    fun `double-encoded questions array is accepted`() {
        val qs = parse("""{"questions":"[{\"question\":\"Name?\"}]"}""")
        assertThat(qs.single().question).isEqualTo("Name?")
    }

    @Test
    fun `answers format marks skipped questions`() {
        val qs = parse("""{"questions":[{"question":"A?"},{"question":"B?"}]}""")
        val text = UserQuestions.formatAnswers(qs, listOf("yes", null))
        assertThat(text).contains("answered 1 of 2")
        assertThat(text).contains("1. A?\n   → yes")
        assertThat(text).contains("(skipped")
        assertThat(UserQuestions.formatAnswers(qs.take(1), listOf("ok"))).isEqualTo("User answered: ok")
    }

    @Test
    fun `the recommended option is marked by flag, by label or by the question`() {
        val qs = parse(
            """{"questions":[
              {"question":"Which look?","options":[{"label":"Minimal"},{"label":"Cozy","recommended":true},{"label":"Bold","recommended":true}]},
              {"question":"Name?","options":["Type it","Make one up (recommended)"]},
              {"question":"Data?","recommended":"This phone","options":["This phone","Account"]},
              {"question":"Features?","multiSelect":true,"options":[{"label":"Menu","recommended":true},{"label":"Map","recommended":true}]}
            ]}"""
        )
        // A single choice keeps one suggestion; the label mark comes off the label.
        assertThat(qs[0].options.filter { it.recommended }.map { it.label }).containsExactly("Cozy")
        assertThat(qs[1].options.map { it.label }).containsExactly("Type it", "Make one up").inOrder()
        assertThat(qs[1].options[1].recommended).isTrue()
        assertThat(qs[2].options.single { it.recommended }.label).isEqualTo("This phone")
        assertThat(qs[3].options.count { it.recommended }).isEqualTo(2)
    }

    @Test
    fun `questions written as prose become a card`() {
        val reply = """
            Happy to build it! A few quick things first:

            1. **Name:** What's the cafe called?
            2. Which look fits best (warm and cozy, clean and minimal or bold)?
            3. What should visitors do first?
               - See the menu
               - Book a table
               - Order online

            Or should I just go ahead with my best guess?
        """.trimIndent()
        val qs = UserQuestions.fromProse(reply)
        assertThat(qs.map { it.question }).containsExactly(
            "What's the cafe called?", "Which look fits best?", "What should visitors do first?"
        ).inOrder()
        assertThat(qs[0].header).isEqualTo("Name")
        assertThat(qs[1].options.map { it.label }).containsExactly("Warm and cozy", "Clean and minimal", "Bold").inOrder()
        assertThat(qs[2].options.map { it.label }).containsExactly("See the menu", "Book a table", "Order online").inOrder()
    }

    @Test
    fun `prose with a colon list, and replies that are not questions`() {
        val qs = UserQuestions.fromProse("Who is it for: students or professionals?")
        assertThat(qs.single().options.map { it.label }).containsExactly("Students", "Professionals").inOrder()
        assertThat(UserQuestions.fromProse("Done! I built the page. Sound good?")).isEmpty()
        assertThat(UserQuestions.fromProse("Here it is:\n```js\nconst ok = x ? 1 : 2;\n```\nWhat next?")).isEmpty()
    }

    @Test
    fun `a text-protocol model may send the questions as plain text`() {
        val qs = parse("""{"questions":"1. What's the business called?\n2. Which colours should it use? (earthy, pastel or mono)"}""")
        assertThat(qs).hasSize(2)
        assertThat(qs[1].options).hasSize(3)
    }

    @Test
    fun `answers round-trip into the transcript quote`() {
        val qs = parse("""{"questions":[{"question":"Name?"},{"question":"Look?"}]}""")
        val text = UserQuestions.formatAnswers(qs, listOf("Brew Haven", null))
        assertThat(UserQuestions.parseAnswers(qs, text)).containsExactly("Brew Haven", null).inOrder()
        assertThat(UserQuestions.quote(qs, listOf("Brew Haven", null)))
            .isEqualTo("> **You answered 2 questions**\n> 1. Name? — **Brew Haven**\n> 2. Look? — _skipped_")
        val one = qs.take(1)
        assertThat(UserQuestions.parseAnswers(one, "User answered: Bloom")).containsExactly("Bloom")
        assertThat(UserQuestions.parseAnswers(one, UserQuestions.DECIDE_RESULT)).isNull()
        assertThat(UserQuestions.quote(qs, null)).isEqualTo("> **You let Orobi decide** · Name? (+1 more)")
    }
}
