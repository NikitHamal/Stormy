package com.oro.app.data.ai.community

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import org.junit.Test

class QwenCommunityTest {
    @Test
    fun `cookie encoding matches the site's SDK`() {
        // Vectors from NEB's Python implementation, which the live site accepts.
        assertThat(QwenFingerprint.encode("hello^world|123")).isEqualTo("GK3qjO0kGH01oitqGQDsG7DRDIrD")
        assertThat(QwenFingerprint.encode("abababababab")).isEqualTo("eKeLybx")
        assertThat(QwenFingerprint.encode("1-websdk-2.3.15d^1760000000000^91^zh-CN"))
            .isEqualTo("eq8xyxpx_xuxmxFwDmxB47ISDWwqBcD0=D9DDuo8f5itqzDSxGKx0iDtoD")
    }

    @Test
    fun `stream parser separates thinking from the answer and stops on finish`() {
        val p = QwenStreamParser()
        val events = listOf(
            """data: {"response.created":{"response_id":"r1"}}""",
            """data: {"choices":[{"delta":{"role":"assistant","content":"Let me think","phase":"think"}}]}""",
            """data: {"choices":[{"delta":{"content":"Hello","phase":"answer"}}]}""",
            """data: {"choices":[{"delta":{"content":" there","phase":"answer","status":"finished"}}]}"""
        ).flatMap { p.onLine(it) }
        assertThat(events.filterIsInstance<StreamEvent.ReasoningDelta>().joinToString("") { it.reasoning }).isEqualTo("Let me think")
        assertThat(events.filterIsInstance<StreamEvent.ContentDelta>().joinToString("") { it.content }).isEqualTo("Hello there")
        assertThat(p.finished).isTrue()

        val q = QwenStreamParser()
        q.onLine("""data: {"error":{"code":"RateLimited","message":"You have reached the quota"}}""")
        assertThat(q.quota).isTrue()
        assertThat(q.sawOutput).isFalse()
    }

    @Test
    fun `transcript is flattened with instructions first and the request last`() {
        val text = QwenWebClient.flatten(
            listOf(
                ChatRequestMessage(role = "system", content = "Be brief."),
                ChatRequestMessage(role = "user", content = "Hi"),
                ChatRequestMessage(role = "assistant", content = "Hello!"),
                ChatRequestMessage(role = "user", content = "Make a page")
            )
        )
        assertThat(text).startsWith("<instructions>\nBe brief.")
        assertThat(text).contains("User: Hi")
        assertThat(text).endsWith("Make a page")
        assertThat(PteroClient.buildBody("m", listOf(ChatRequestMessage(role = "user", content = "x")))!!["message"].toString()).contains("x")
    }
}
