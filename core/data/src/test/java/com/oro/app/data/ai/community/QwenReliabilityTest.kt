package com.oro.app.data.ai.community

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.AiModels
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.KeylessFallback
import com.oro.app.data.ai.StreamEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** chat.qwen.ai as of web 0.3.22 (2026-10-23): payloads recorded from the live site. */
class QwenReliabilityTest {

    // What the WAF answers instead of a stream (recorded from two datacenter networks).
    private val punish = """{"ret":["FAIL_SYS_USER_VALIDATE","RGV587_ERROR::SM::哎哟喂,被挤爆啦,请稍后重试"],""" +
        """"data":{"url":"https://chat.qwen.ai:443//api/v2/chat/completions/_____tmd_____/punish?x5secdata=xg1c86&x5step=2&action=captcha"}}"""

    private fun text(events: List<StreamEvent>) = events.filterIsInstance<StreamEvent.ContentDelta>().joinToString("") { it.content }
    private fun reasoning(events: List<StreamEvent>) = events.filterIsInstance<StreamEvent.ReasoningDelta>().joinToString("") { it.reasoning }

    @Test
    fun `browser reply streams once the chat exists`() {
        val parser = QwenStreamParser()
        val reply = QwenWebClient.BrowserReply(parser)
        val events = listOf(
            "CHAT be9a0a19-f21c-4553-9c14-a3e5e17d3d35",
            "HTTP 200 text/event-stream;charset=UTF-8",
            """data: {"response.created":{"chat_id":"be9a","response_id":"r1"}}""",
            """data: {"choices":[{"delta":{"role":"assistant","content":"Checking","phase":"think","status":"typing"}}]}""",
            """data: {"choices":[{"delta":{"content":"hello from qwen","phase":"answer","status":"typing"}}]}""",
            """data: {"choices":[{"delta":{"content":"","phase":"answer","status":"finished"},"finish_reason":"stop"}]}"""
        ).flatMap { reply.onLine(it) }
        assertThat(reply.chatId).isEqualTo("be9a0a19-f21c-4553-9c14-a3e5e17d3d35")
        // The chat existing is proof of life; the reply starts when its stream opens, before any token.
        assertThat(events.first()).isInstanceOf(StreamEvent.RawSSE::class.java)
        assertThat(events[1]).isEqualTo(StreamEvent.Started)
        assertThat(events.filterIsInstance<StreamEvent.Started>()).hasSize(1)
        assertThat(reasoning(events)).isEqualTo("Checking")
        assertThat(text(events)).isEqualTo("hello from qwen")
        assertThat(parser.finished).isTrue()
        assertThat(reply.challenge).isNull()
    }

    @Test
    fun `a failure after the reply started is a broken reply, not an answer`() {
        val parser = QwenStreamParser()
        val reply = QwenWebClient.BrowserReply(parser)
        reply.onLine("CHAT c1")
        reply.onLine("HTTP 200 text/event-stream")
        reply.onLine("""data: {"choices":[{"delta":{"content":"half of a fi","phase":"answer","status":"typing"}}]}""")
        assertThat(reply.onLine("FAIL the reply stalled")).isEmpty()
        assertThat(reply.brokeOff).isTrue()
        assertThat(reply.failure()).contains("stalled")
        assertThat(parser.sawOutput).isTrue()
        // Nothing after the break counts.
        assertThat(reply.onLine("""data: {"choices":[{"delta":{"content":"le","phase":"answer"}}]}""")).isEmpty()
    }

    @Test
    fun `a captcha before or after the chat is a challenge, never a hang or an answer`() {
        val early = QwenWebClient.BrowserReply(QwenStreamParser())
        early.onLine("CHALLENGE the site asked for a human check")
        assertThat(early.challenge).isEqualTo("the site asked for a human check")

        val late = QwenWebClient.BrowserReply(QwenStreamParser())
        late.onLine("CHAT c1")
        late.onLine("CHALLENGE")
        assertThat(late.challenge).isNotEmpty()
        // Nothing after a challenge counts.
        assertThat(late.onLine("""data: {"choices":[{"delta":{"content":"x","phase":"answer"}}]}""")).isEmpty()
    }

    @Test
    fun `a punish body in place of the stream is a challenge`() {
        val reply = QwenWebClient.BrowserReply(QwenStreamParser())
        reply.onLine("CHAT c1")
        reply.onLine("HTTP 200 application/json;charset=UTF-8")
        reply.onLine(punish)
        assertThat(reply.challenge).isNotNull()
        assertThat(QwenWebClient.isChallenge(punish)).isTrue()
    }

    @Test
    fun `failures explain themselves`() {
        val noChat = QwenWebClient.BrowserReply(QwenStreamParser())
        noChat.onLine("FAIL could not start a chat: {\"success\":false}")
        assertThat(noChat.failure()).contains("could not start a chat")

        val quota = QwenWebClient.BrowserReply(QwenStreamParser())
        quota.onLine("CHAT c1")
        quota.onLine("HTTP 200 application/json")
        quota.onLine("""{"success":false,"data":{"code":"RateLimited","details":"You have reached the quota"}}""")
        assertThat(quota.challenge).isNull()
        assertThat(quota.failure()).contains("quota")
    }

    @Test
    fun `parser reads alternative reasoning keys and whole-message frames`() {
        val p = QwenStreamParser()
        val events = listOf(
            """data: {"choices":[{"delta":{"reasoning":"step one"}}]}""",
            """data: {"choices":[{"message":{"content":"Done."},"finish_reason":"stop"}]}"""
        ).flatMap { p.onLine(it) }
        assertThat(reasoning(events)).isEqualTo("step one")
        assertThat(text(events)).isEqualTo("Done.")
        assertThat(p.finished).isTrue()
    }

    @Test
    fun `request bodies match the site's current shape`() {
        val client = QwenWebClient()
        val chat = client.newChatBody("qwen3.8-max")
        assertThat(chat["chat_mode"]!!.jsonPrimitive.content).isEqualTo("normal")
        assertThat((chat["models"] as JsonArray).first()).isEqualTo(JsonPrimitive("qwen3.8-max"))

        val body = client.completionBody("", "qwen3.7-plus", "Hi", thinking = false)
        assertThat(body["stream"]!!.jsonPrimitive.boolean).isTrue()
        assertThat(body["chat_id"]!!.jsonPrimitive.content).isEmpty() // the page fills it in
        val message = (body["messages"] as JsonArray).first() as JsonObject
        val features = message["feature_config"] as JsonObject
        assertThat(features["thinking_enabled"]!!.jsonPrimitive.boolean).isFalse()
        assertThat(features["output_schema"]!!.jsonPrimitive.content).isEqualTo("phase")
        assertThat(message["content"]!!.jsonPrimitive.content).isEqualTo("Hi")
    }

    @Test
    fun `timezone is sent the way the page sends it`() {
        assertThat(QwenWebClient.timezone()).matches("""[A-Z][a-z]{2} [A-Z][a-z]{2} \d{2} \d{4} \d{2}:\d{2}:\d{2} GMT[+-]\d{4}""")
        assertThat(QwenWebClient.WEB_VERSION).isEqualTo("0.3.22")
    }

    @Test
    fun `a challenge hands over and parks the whole site`() {
        val error = QwenWebClient.challengeError("the site asked for a human check")
        assertThat(error.message).contains("browser check")
        assertThat(KeylessFallback.shouldFallBack(error)).isTrue()
        try {
            KeylessFallback.markBusy("qwen:qwen3.8-max", error)
            assertThat(KeylessFallback.isBusy("qwen:qwen3.8-max")).isTrue()
            // Same site, same check: its other models wait too.
            assertThat(KeylessFallback.isBusy("qwen:qwen3.7-plus")).isTrue()
            // Another host of the same family is unaffected.
            assertThat(KeylessFallback.isBusy("qwen3.8-27b")).isFalse()
        } finally {
            KeylessFallback.markHealthy("qwen:qwen3.8-max")
        }
        assertThat(KeylessFallback.isBusy("qwen:qwen3.7-plus")).isFalse()
    }

    @Test
    fun `a qwen request is answered by another qwen first`() {
        // Other tests share KeylessFallback's state; start from healthy models.
        (KeylessFallback.order + KeylessFallback.sameFamily(AiModels.findModelById("qwen:qwen3.8-max")!!)).forEach { KeylessFallback.markHealthy(it) }
        val max = AiModels.findModelById("qwen:qwen3.8-max")!!
        val chain = KeylessFallback.candidates(max, needsNativeTools = false) { AiModels.findModelById(it) }.map { it.id }
        assertThat(chain.take(4)).containsExactly("qwen3.8-27b", "Qwen3.8-27B", "Qwen3.5-397B-A17B", "ptero:qwen3.7-flash:free").inOrder()
        // chat.qwen.ai itself refused: none of its models are retried.
        assertThat(chain.none { AiModels.findModelById(it)?.provider == AiProvider.QWEN_WEB }).isTrue()

        // With native tools, only real tool callers stand in (Ptero is text-only).
        val vlm = AiModels.findModelById("qwen3.8-27b")!!
        val tools = KeylessFallback.candidates(vlm, needsNativeTools = true) { AiModels.findModelById(it) }.map { it.id }
        assertThat(tools.take(2)).containsExactly("Qwen3.8-27B", "Qwen3.5-397B-A17B").inOrder()
        assertThat(tools).doesNotContain("ptero:qwen3.7-flash:free")
    }

    @Test
    fun `other families keep the global order`() {
        val mercury = AiModels.findModelById("mercury:mercury-3")!!
        assertThat(KeylessFallback.sameFamily(mercury)).isEmpty()
    }
}
