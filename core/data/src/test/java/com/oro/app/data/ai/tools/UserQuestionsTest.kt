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
}
