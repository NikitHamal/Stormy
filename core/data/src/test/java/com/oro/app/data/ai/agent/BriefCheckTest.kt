package com.oro.app.data.ai.agent

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.oro.app.data.ai.agent.BriefCheck.Facet
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.JsonParserUtils
import com.oro.app.data.ai.tools.UserQuestions
import org.junit.Test

class BriefCheckTest {

    private fun vague(request: String, fresh: Boolean = true) = BriefCheck.check(request, fresh)

    @Test
    fun `short requests for something new with no facts are vague`() {
        listOf(
            "make me a website for my cafe",
            "build a portfolio",
            "I want an app",
            "website for my bakery",
            "make a game",
            "Create a landing page for my startup",
            "can you make me a website for my gym?",
            "make a modern website for my law firm",
            "need a site for my wedding",
        ).forEach { assertWithMessage(it).that(vague(it).vague).isTrue() }
    }

    @Test
    fun `specific requests say enough`() {
        listOf(
            // The live eval's landing brief: long and full of facts.
            "Build a polished one-page website for \"Himalayan Brew\", a specialty coffee shop and small-batch roastery " +
                "in Thamel, Kathmandu (Jyatha Marg, open 7am-9pm every day). Include a hero, the story of their single-origin " +
                "Nepali beans, a menu with real drinks and pastries priced in NPR, opening hours and location, a few customer " +
                "reviews, and a table-reservation form. Make it feel premium and modern, mobile-first.",
            "Build a website for Brew Haven, a cozy cafe, with a menu, gallery and table bookings",
            "Make a dashboard for my team to track sales with charts, filters and a login",
            "Create a dark, minimal portfolio for a photographer called Mira with a gallery, about and contact page",
        ).forEach { assertWithMessage(it).that(vague(it).vague).isFalse() }
    }

    @Test
    fun `self-explanatory apps, edits, questions and opt-outs get nothing`() {
        mapOf(
            "make a snake game" to "self-explanatory",
            "make a todo app" to "self-explanatory",
            "build a calculator" to "self-explanatory",
            "fix the navbar on mobile" to "edit",
            "add a contact page to my website" to "edit",
            "I want to add a contact page to my website" to "edit",
            "how do I make a website?" to "question",
            "just build me a website, surprise me" to "asked not to be asked",
            "make a website for my cafe, you decide the rest" to "asked not to be asked",
            "hello" to "not a request",
            "I saw a nice site yesterday with a big hero" to "not a request",
        ).forEach { (request, why) ->
            val v = vague(request)
            assertWithMessage(request).that(v.vague).isFalse()
            assertWithMessage(request).that(v.reason).contains(why)
        }
    }

    @Test
    fun `a project with work in it is never interrupted`() {
        val v = vague("make me a website for my cafe", fresh = false)
        assertThat(v.vague).isFalse()
        assertThat(v.reason).contains("already has work")
    }

    @Test
    fun `missing facts are ordered by what matters for the kind of build`() {
        val site = vague("make me a website for my cafe")
        assertThat(site.kind).isEqualTo(BriefCheck.Kind.SITE)
        assertThat(site.missing).containsExactly(Facet.NAME, Facet.PURPOSE, Facet.LOOK).inOrder()
        val app = vague("make a habit tracker")
        assertThat(app.kind).isEqualTo(BriefCheck.Kind.APP)
        assertThat(app.missing).containsExactly(Facet.PURPOSE, Facet.SCOPE, Facet.DATA).inOrder()
    }

    @Test
    fun `a capitalised place is not the name, a capitalised name is`() {
        assertThat(vague("make a website for my cafe in Kathmandu").missing.first()).isEqualTo(Facet.NAME)
        val named = vague("Make a website for Himalayan Brew")
        assertThat(named.vague).isTrue()
        assertThat(named.missing).doesNotContain(Facet.NAME)
    }

    @Test
    fun `the note asks for one card about the open facts`() {
        val note = BriefCheck.note(vague("make me a website for my cafe"), ask = true, textProtocol = false)!!
        assertThat(note).startsWith("[Oro:")
        assertThat(note).contains("ONE ask_user call")
        assertThat(note).contains("its name or brand, what it is mainly for and the look and feel")
        assertThat(note).contains("recommended: true")
        assertThat(note).doesNotContain("<tool_call>")
    }

    @Test
    fun `the text-protocol note carries a call that parses into a card`() {
        val note = BriefCheck.note(vague("make me a website for my cafe"), ask = true, textProtocol = true)!!
        val parsed = TextToolCallParser.parse(note, setOf("ask_user"), AgentTools.parameterSchemas)
        val call = parsed.calls.single()
        assertThat(call.function.name).isEqualTo("ask_user")
        val questions = UserQuestions.parse(JsonParserUtils.parseToJsonObject(call.function.arguments)!!)
        assertThat(questions).hasSize(3)
        assertThat(questions.all { q -> q.options.count { it.recommended } == 1 }).isTrue()
    }

    @Test
    fun `with asking off a vague request is told to decide, a specific one gets nothing`() {
        val off = BriefCheck.note(vague("build a portfolio"), ask = false, textProtocol = false)!!
        assertThat(off).contains("prefers not to be asked")
        assertThat(off).doesNotContain("ask_user")
        assertThat(BriefCheck.note(vague("make a snake game"), ask = true, textProtocol = true)).isNull()
    }
}
