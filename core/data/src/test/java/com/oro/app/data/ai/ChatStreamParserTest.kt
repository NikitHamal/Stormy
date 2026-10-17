package com.oro.app.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Provider stream quirks recorded from live endpoints on 2026-10-17. */
class ChatStreamParserTest {

    private fun ChatStreamParser.feed(vararg lines: String): List<StreamEvent> =
        lines.flatMap { onData(it) }

    @Test
    fun `tool call split across deltas is assembled, first delta without arguments`() {
        // OVH Llama sends the first tool delta with no "arguments" key at all.
        val p = ChatStreamParser()
        val events = p.feed(
            """{"choices":[{"index":0,"delta":{"role":"assistant","name":"assistant","tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"read_file"}}]}}]}""",
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"path\":"}}]}}]}""",
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"index.html\"}"}}]}}]}""",
            """{"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""",
            "[DONE]"
        )
        val calls = events.filterIsInstance<StreamEvent.ToolCalls>().single().toolCalls
        assertThat(calls).hasSize(1)
        assertThat(calls[0].id).isEqualTo("call_1")
        assertThat(calls[0].function.name).isEqualTo("read_file")
        assertThat(calls[0].function.arguments).isEqualTo("""{"path":"index.html"}""")
        assertThat(events.last()).isEqualTo(StreamEvent.Completed)
        assertThat(events.filterIsInstance<StreamEvent.FinishReason>().single().reason).isEqualTo("tool_calls")
    }

    @Test
    fun `stream that closes without DONE still yields its tool calls on finish`() {
        val p = ChatStreamParser()
        p.feed(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"a","function":{"name":"glob","arguments":"{\"pattern\":\"*.css\"}"}}]},"finish_reason":"tool_calls"}]}"""
        )
        val end = p.finish()
        assertThat(end.filterIsInstance<StreamEvent.ToolCalls>().single().toolCalls.single().function.name)
            .isEqualTo("glob")
        assertThat(end.last()).isEqualTo(StreamEvent.Completed)
        // Idempotent: a second finish (e.g. [DONE] then close) emits nothing.
        assertThat(p.finish()).isEmpty()
    }

    @Test
    fun `reasoning is read from both reasoning and reasoning_content`() {
        val p = ChatStreamParser()
        val events = p.feed(
            """{"choices":[{"delta":{"reasoning":"plan "}}]}""",
            """{"choices":[{"delta":{"reasoning_content":"more"}}]}""",
            """{"choices":[{"delta":{"content":"Hi"}}]}"""
        )
        assertThat(events.filterIsInstance<StreamEvent.ReasoningDelta>().map { it.reasoning })
            .containsExactly("plan ", "more").inOrder()
        assertThat(events.filterIsInstance<StreamEvent.ContentDelta>().single().content).isEqualTo("Hi")
    }

    @Test
    fun `usage-only chunk and gateway metadata chunks are tolerated`() {
        val p = ChatStreamParser()
        val events = p.feed(
            """{"choices":[{"index":0,"delta":{"provider_metadata":{"gateway":{"routing":{}}}}}]}""",
            """{"choices":[],"usage":{"prompt_tokens":120,"completion_tokens":7,"total_tokens":127}}""",
            "not json at all"
        )
        assertThat(events).containsExactly(StreamEvent.Usage(120, 7))
    }

    @Test
    fun `tool calls without index are separated by id`() {
        val p = ChatStreamParser()
        p.feed(
            """{"choices":[{"delta":{"tool_calls":[{"id":"x","function":{"name":"read_file","arguments":"{\"path\":\"a\"}"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"id":"y","function":{"name":"read_file","arguments":"{\"path\":\"b\"}"}}]}}]}"""
        )
        val calls = p.finish().filterIsInstance<StreamEvent.ToolCalls>().single().toolCalls
        assertThat(calls.map { it.id }).containsExactly("x", "y").inOrder()
        assertThat(calls[1].function.arguments).contains("\"b\"")
    }

    @Test
    fun `non-streamed completion body is accepted`() {
        val p = ChatStreamParser()
        val events = p.onData(
            """{"choices":[{"index":0,"message":{"role":"assistant","content":"pong"},"finish_reason":"stop"}]}"""
        )
        assertThat(events.filterIsInstance<StreamEvent.ContentDelta>().single().content).isEqualTo("pong")
        assertThat(p.hasOutput).isTrue()
    }

    @Test
    fun `mid-stream error object becomes a retryable error`() {
        val events = ChatStreamParser().onData("""{"error":{"message":"upstream overloaded","code":502}}""")
        val error = events.single() as StreamEvent.Error
        assertThat(error.message).isEqualTo("upstream overloaded")
        assertThat(error.retryable).isTrue()
    }

    @Test
    fun `retry hints are read from header, field and message text`() {
        assertThat(ChatStreamParser.retryAfterMs("7", null)).isEqualTo(7_000)
        assertThat(ChatStreamParser.retryAfterMs(null, """{"retry_after": 60, "code":"quota_exceeded"}""")).isEqualTo(60_000)
        // Pollinations 402 FREE_LIMIT wording.
        assertThat(
            ChatStreamParser.retryAfterMs(null, """{"status":402,"error":{"code":"FREE_LIMIT","message":"Free tier limit. Retry after 19s"}}""")
        ).isEqualTo(19_000)
        assertThat(ChatStreamParser.retryAfterMs(null, "nothing here")).isNull()
    }

    @Test
    fun `error bodies of different shapes are described`() {
        assertThat(ChatStreamParser.describeErrorBody("""{"error":{"message":"bad key"}}""")).isEqualTo("bad key")
        assertThat(ChatStreamParser.describeErrorBody("""{"message":"API rate limit exceeded","request_id":"1"}"""))
            .isEqualTo("API rate limit exceeded")
        assertThat(ChatStreamParser.describeErrorBody("""{"error":"Missing Authorization"}""")).isEqualTo("Missing Authorization")
        assertThat(ChatStreamParser.describeErrorBody("<html>502</html>")).isNull()
    }

    @Test
    fun `retryable statuses`() {
        listOf(408, 429, 402, 500, 502, 503).forEach { assertThat(ChatStreamParser.isRetryableStatus(it)).isTrue() }
        listOf(400, 401, 403, 404).forEach { assertThat(ChatStreamParser.isRetryableStatus(it)).isFalse() }
    }
}
