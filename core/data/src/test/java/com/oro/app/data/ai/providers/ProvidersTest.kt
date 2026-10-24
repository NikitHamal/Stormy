package com.oro.app.data.ai.providers

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.ToolCallRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test

class KeyPoolTest {
    private var now = 1_000_000L
    private val keys = listOf(ApiKeyEntry("a", "key-a"), ApiKeyEntry("b", "key-b"), ApiKeyEntry("c", "key-c"))

    @Before fun setUp() { KeyPool.clock = { now }; keys.forEach { KeyPool.reset(it.id) } }
    @After fun tearDown() { KeyPool.clock = { System.currentTimeMillis() } }

    @Test
    fun `failures are classified by what they say about the key`() {
        assertThat(KeyPool.classify(401, "bad key")).isEqualTo(KeyHealth.INVALID)
        assertThat(KeyPool.classify(429, "Rate limit reached for requests")).isEqualTo(KeyHealth.RATE_LIMITED)
        assertThat(KeyPool.classify(429, "You exceeded your current quota (insufficient_quota)")).isEqualTo(KeyHealth.EXHAUSTED)
        assertThat(KeyPool.classify(402, "Payment required")).isEqualTo(KeyHealth.EXHAUSTED)
        assertThat(KeyPool.classify(400, "Your credit balance is too low to access the Anthropic API")).isEqualTo(KeyHealth.EXHAUSTED)
        // Not the key's fault: same on every key, so no rotation.
        assertThat(KeyPool.classify(400, "messages: field required")).isNull()
        assertThat(KeyPool.classify(503, "overloaded")).isNull()
    }

    @Test
    fun `a rate-limited key goes to the back until its cooldown ends`() {
        KeyPool.reportFailure("a", StreamEvent.Error("rate limit", httpCode = 429, retryAfterMs = 30_000))
        assertThat(KeyPool.candidates("p", keys, KeyRotation.FAILOVER).map { it.id }).containsExactly("b", "c", "a").inOrder()
        now += 31_000
        assertThat(KeyPool.candidates("p", keys, KeyRotation.FAILOVER).map { it.id }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `disabled and blank keys are never candidates, and round robin rotates`() {
        val list = keys + ApiKeyEntry("d", "  ") + ApiKeyEntry("e", "key-e", enabled = false)
        val first = KeyPool.candidates("rr", list, KeyRotation.ROUND_ROBIN).map { it.id }
        val second = KeyPool.candidates("rr", list, KeyRotation.ROUND_ROBIN).map { it.id }
        assertThat(first).containsExactly("a", "b", "c")
        assertThat(first.first()).isNotEqualTo(second.first())
    }
}

class ProviderSettingsMigrationTest {
    @Test
    fun `legacy single keys and custom url are folded in once`() {
        val migrated = ProviderSettingsMigration.withLegacy(
            ProviderSettings(),
            legacyKeys = mapOf("GROQ" to "gsk_1", "GEMINI" to "", "CUSTOM" to "local-key"),
            legacyCustomBaseUrl = "http://10.0.0.2:11434/v1"
        )
        assertThat(migrated.builtInKeys["GROQ"]!!.single().key).isEqualTo("gsk_1")
        assertThat(migrated.builtInKeys).doesNotContainKey("GEMINI")
        assertThat(migrated.builtInKeys).doesNotContainKey("CUSTOM")
        val legacy = migrated.custom.first { it.id == ProviderSettingsMigration.LEGACY_CUSTOM_ID }
        // Well-known vendors are shipped ready-made, once.
        assertThat(migrated.custom.map { it.presetId }).containsAtLeast("deepseek", "moonshot", "anthropic")
        assertThat(migrated.seededPresets).isTrue()
        assertThat(legacy.id).isEqualTo(ProviderSettingsMigration.LEGACY_CUSTOM_ID)
        assertThat(legacy.keys.single().key).isEqualTo("local-key")
        // Idempotent: a second pass changes nothing.
        assertThat(ProviderSettingsMigration.withLegacy(migrated, mapOf("GROQ" to "gsk_1"), "http://10.0.0.2:11434/v1"))
            .isEqualTo(migrated)
    }

    @Test
    fun `an emptied key list is not re-imported`() {
        val s = ProviderSettings(builtInKeys = mapOf("GROQ" to emptyList()), seededPresets = true)
        assertThat(ProviderSettingsMigration.withLegacy(s, mapOf("GROQ" to "old"), "").builtInKeys["GROQ"]).isEmpty()
        // A removed preset is not re-added.
        assertThat(ProviderSettingsMigration.withLegacy(s, emptyMap(), "").custom).isEmpty()
    }

    @Test
    fun `model ids round-trip their provider and upstream id`() {
        val id = ModelIds.of("ab12", "anthropic/claude:beta")
        assertThat(ModelIds.providerId(id)).isEqualTo("ab12")
        assertThat(ModelIds.upstream(id)).isEqualTo("anthropic/claude:beta")
        assertThat(ModelIds.providerId("gpt-4o")).isNull()
        assertThat(ModelIds.upstream("gpt-4o")).isEqualTo("gpt-4o")
    }
}

class AnthropicTest {
    @Test
    fun `stream with text and a tool call maps to the same events as OpenAI`() {
        val p = AnthropicStreamParser("Claude")
        val events = listOf(
            """{"type":"message_start","message":{"usage":{"input_tokens":120,"output_tokens":1}}}""",
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Reading it."}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"read_file","input":{}}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"path\": \"a"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":".html\"}"}}""",
            """{"type":"content_block_stop","index":1}""",
            """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":42}}""",
            """{"type":"message_stop"}"""
        ).flatMap { p.onData(it) }
        assertThat(events.filterIsInstance<StreamEvent.ContentDelta>().single().content).isEqualTo("Reading it.")
        val call = events.filterIsInstance<StreamEvent.ToolCalls>().single().toolCalls.single()
        assertThat(call.id).isEqualTo("toolu_1")
        assertThat(call.function.arguments).isEqualTo("{\"path\": \"a.html\"}")
        assertThat(events).contains(StreamEvent.FinishReason("tool_calls"))
        assertThat(events).contains(StreamEvent.Usage(120, 42))
        assertThat(events.last()).isEqualTo(StreamEvent.Completed)
    }

    @Test
    fun `overloaded stream errors are retryable`() {
        val e = AnthropicStreamParser().onData("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""").single()
        assertThat((e as StreamEvent.Error).retryable).isTrue()
    }

    @Test
    fun `request maps system, tool calls and merged tool results`() {
        val messages = listOf(
            ChatRequestMessage(role = "system", content = "Be brief."),
            ChatRequestMessage(role = "user", content = "fix it"),
            ChatRequestMessage(
                role = "assistant", content = "Looking.",
                toolCalls = listOf(
                    ToolCallRequest("t1", "function", FunctionCall("read_file", """{"path":"a.html"}""")),
                    ToolCallRequest("t2", "function", FunctionCall("read_file", """{"path":"b.css"}"""))
                )
            ),
            ChatRequestMessage(role = "tool", content = "<p>a</p>", toolCallId = "t1"),
            ChatRequestMessage(role = "tool", content = "", toolCallId = "t2")
        )
        val body = AnthropicClient.buildBody("claude-x", messages, null, 0.7f, 1024)
        val system = (body["system"] as JsonArray).single() as JsonObject
        assertThat(system["text"]!!.jsonPrimitive.content).isEqualTo("Be brief.")
        assertThat(system).containsKey("cache_control")
        val turns = (body["messages"] as JsonArray).map { it as JsonObject }
        assertThat(turns.map { it["role"]!!.jsonPrimitive.content }).containsExactly("user", "assistant", "user").inOrder()
        val assistant = turns[1]["content"] as JsonArray
        assertThat(assistant).hasSize(3)
        assertThat((assistant[1] as JsonObject)["type"]!!.jsonPrimitive.content).isEqualTo("tool_use")
        assertThat(((assistant[1] as JsonObject)["input"] as JsonObject)["path"]!!.jsonPrimitive.content).isEqualTo("a.html")
        val results = turns[2]["content"] as JsonArray
        assertThat(results).hasSize(2)
        assertThat((results[1] as JsonObject)["content"]!!.jsonPrimitive.content).isEqualTo("(no output)")
        // The newest user turn carries the rolling cache breakpoint.
        assertThat(results[1] as JsonObject).containsKey("cache_control")
    }

    @Test
    fun `anthropic's model list carries each model's window and output cap`() {
        val page = AnthropicClient.parseModelPage(
            """{"data":[
                {"type":"model","id":"claude-opus-5-5","display_name":"Claude Opus 5.5","max_input_tokens":1000000,"max_tokens":128000},
                {"type":"model","id":"vendor-model","display_name":"Vendor"}
              ],"has_more":true,"first_id":"claude-opus-5-5","last_id":"vendor-model"}"""
        )
        assertThat(page.models.map { it.id }).containsExactly("claude-opus-5-5", "vendor-model").inOrder()
        assertThat(page.models[0].maxInputTokens).isEqualTo(1_000_000)
        assertThat(page.models[0].maxOutputTokens).isEqualTo(128_000)
        // Another Messages-API server may not say: the table decides then.
        assertThat(page.models[1].maxInputTokens).isNull()
        assertThat(page.nextAfter).isEqualTo("vendor-model")
        assertThat(AnthropicClient.parseModelPage("""{"data":[],"has_more":false}""").nextAfter).isNull()
    }
}
