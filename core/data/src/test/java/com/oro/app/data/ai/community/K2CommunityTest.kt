package com.oro.app.data.ai.community

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.AiModels
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.ModelGroup
import com.oro.app.data.ai.SeedModels
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.ToolCallRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** chat.ifm.ai guest protocol, frames recorded live on 2026-10-17. */
class K2CommunityTest {

    @Test
    fun `cumulative frames become reasoning and answer deltas`() {
        val p = K2FrameParser()
        val events = listOf(
            """{"task_id": "6e3c32db"}""",
            """{"content": "<details type=\"reasoning\" done=\"false\">\n<summary>Thinking…</summary>\n> The user asks me to say pong.\n</details>"}""",
            """{"content": "<details type=\"reasoning\" done=\"true\" duration=\"0\">\n<summary>Thought for 0 seconds</summary>\n> The user asks me to say pong. Easy.\n</details>\nPo"}""",
            """{"content": "<details type=\"reasoning\" done=\"true\" duration=\"0\">\n<summary>Thought for 0 seconds</summary>\n> The user asks me to say pong. Easy.\n</details>\nPong!", "title": null}""",
            """{"usage": {"total_tokens": 428, "completion_tokens": 74, "prompt_tokens": 354}}""",
            """{"done": true}"""
        ).flatMap { p.onData(it) }

        assertThat(events.filterIsInstance<StreamEvent.ContentDelta>().joinToString("") { it.content }).isEqualTo("Pong!")
        assertThat(events.filterIsInstance<StreamEvent.ReasoningDelta>().joinToString("") { it.reasoning })
            .isEqualTo("The user asks me to say pong. Easy.")
        assertThat(events).contains(StreamEvent.Usage(354, 74))
        assertThat(events.last()).isEqualTo(StreamEvent.Completed)
        assertThat(p.onData("""{"content":"late"}""")).isEmpty()
    }

    @Test
    fun `detail frames are errors`() {
        val e = K2FrameParser().onData("""{"detail":"400: extra_body: property 'extra_body' is unsupported"}""").single()
        assertThat((e as StreamEvent.Error).message).contains("extra_body")
    }

    @Test
    fun `a details element inside the answer does not swallow the tool call`() {
        val content = "<details type=\"reasoning\" done=\"true\">\n<summary>Thought</summary>\n" +
            "> I should add an FAQ with </details> tags.\n</details>\n" +
            "<ifm|tool_call>write_file\n<ifm|arg_key>path</ifm|arg_key>\n<ifm|arg_value>faq.html</ifm|arg_value>\n" +
            "<ifm|arg_key>content</ifm|arg_key>\n<ifm|arg_value><details>\n<summary>Q</summary>\nA\n</details>\n</ifm|arg_value>\n</ifm|tool_call>"
        val (reasoning, answer) = K2FrameParser.split(content)
        assertThat(reasoning).isEqualTo("I should add an FAQ with </details> tags.")
        assertThat(answer).startsWith("<ifm|tool_call>write_file")
        assertThat(answer).endsWith("</ifm|tool_call>")
    }

    @Test
    fun `answer without reasoning block passes through`() {
        assertThat(K2FrameParser.split("plain answer")).isEqualTo("" to "plain answer")
    }

    @Test
    fun `request body strips the namespace, adds reasoning to assistant turns and renders tool traffic as text`() {
        val messages = listOf(
            ChatRequestMessage(role = "system", content = "Be brief."),
            ChatRequestMessage(role = "user", content = "fix it"),
            ChatRequestMessage(
                role = "assistant", content = null,
                toolCalls = listOf(ToolCallRequest("c1", "function", FunctionCall("read_file", """{"path":"a.html"}""")))
            ),
            ChatRequestMessage(role = "tool", content = "<p>x</p>", toolCallId = "c1", name = "read_file")
        )
        val model = SeedModels.k2Horizon.single()
        val body = K2GuestClient.buildBody(CommunityClients.upstreamId(model), messages)
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("IFM/K2-Horizon-375B-A23B")
        val sent = (body["messages"] as JsonArray).map { it as JsonObject }
        assertThat(sent.map { it["role"]!!.jsonPrimitive.content })
            .containsExactly("system", "user", "assistant", "user").inOrder()
        assertThat(sent[2]["reasoning"]!!.jsonPrimitive.content).isEmpty()
        assertThat(sent[2]["content"]!!.jsonPrimitive.content).contains("<tool_call>")
        assertThat(sent[3]["content"]!!.jsonPrimitive.content).contains("<tool_result name=\"read_file\">")
        // The upstream rejects extra_body (live 400), so it must not be sent.
        assertThat(body.containsKey("extra_body")).isFalse()
    }

    @Test
    fun `community models are text-tool, namespaced and grouped as unofficial`() {
        val model = SeedModels.k2Horizon.single()
        assertThat(model.provider.isCommunity).isTrue()
        assertThat(model.supportsToolCalls).isFalse()
        assertThat(model.id).startsWith("k2:")
        assertThat(ModelGroup.caption(model)).contains("unofficial")
        // It is the keyless default, so it sits in Recommended; other community
        // models land in the Community group.
        assertThat(ModelGroup.of(model)).isEqualTo(ModelGroup.RECOMMENDED)
        assertThat(ModelGroup.of(model.copy(id = "k2:other"))).isEqualTo(ModelGroup.COMMUNITY)
        assertThat(AiProvider.DEFAULT).isEqualTo(AiProvider.K2_HORIZON)
        assertThat(AiModels.getRecommendedModels().first()).isEqualTo(model)
    }

    @Test
    fun `picker groups keep recommended order and omit empty groups`() {
        val groups = ModelGroup.grouped(AiModels.getAllModels())
        assertThat(groups.first().first).isEqualTo(ModelGroup.RECOMMENDED)
        assertThat(groups.first().second.map { it.id })
            .containsExactlyElementsIn(AiModels.getRecommendedModels().filter { it.provider.worksWithoutKey }.map { it.id })
            .inOrder()
        assertThat(groups.map { it.first }).isInOrder()
    }
}
