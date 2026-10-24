package com.oro.app.data.ai.community

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.AiModels
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.KeylessFallback
import com.oro.app.data.ai.OpenAiCompatibleModelService
import com.oro.app.data.ai.SeedModels
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.ToolCallRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Test

/** Mercury (Inception guest chat), its AI SDK stream, and UniKey's anonymous chat. */
class MercuryUnikeyTest {

    // ---------------------------------------------------------- AI SDK stream

    @Test
    fun `ai sdk deltas are appended and reasoning is kept apart`() {
        // Frames recorded from an AI SDK v5 useChat backend (2026-10-22).
        val p = AiSdkStreamParser()
        val events = listOf(
            """{"type":"start","messageMetadata":{"modelName":"Mercury 3"}}""",
            """{"type":"start-step"}""",
            """{"type":"reasoning-start","id":"r1"}""",
            """{"type":"reasoning-delta","id":"r1","delta":"Plan the page."}""",
            """{"type":"reasoning-end","id":"r1"}""",
            """{"type":"text-start","id":"t1"}""",
            """{"type":"text-delta","id":"t1","delta":"Hello"}""",
            """{"type":"text-delta","id":"t1","delta":" there"}""",
            """{"type":"text-end","id":"t1"}""",
            """{"type":"finish-step"}""",
            """{"type":"finish","finishReason":"stop","messageMetadata":{"inputTokens":1626,"outputTokens":18}}""",
            "[DONE]"
        ).flatMap { p.onData(it) }
        assertThat(events.filterIsInstance<StreamEvent.ContentDelta>().joinToString("") { it.content }).isEqualTo("Hello there")
        assertThat(events.filterIsInstance<StreamEvent.ReasoningDelta>().single().reasoning).isEqualTo("Plan the page.")
        assertThat(events.filterIsInstance<StreamEvent.Usage>().single()).isEqualTo(StreamEvent.Usage(1626, 18))
        assertThat(events.filterIsInstance<StreamEvent.FinishReason>().single().reason).isEqualTo("stop")
        assertThat(p.finished).isTrue()
        assertThat(p.sawOutput).isTrue()
        // Finish already reported: only completion remains.
        assertThat(p.finish()).containsExactly(StreamEvent.Completed)
    }

    @Test
    fun `ai sdk errors unwrap json error text and finish reasons use openai spelling`() {
        val p = AiSdkStreamParser()
        p.onData("""{"type":"error","errorText":"{\"error\":\"This site has run out of API credit for the moment.\"}"}""")
        assertThat(p.error).isEqualTo("This site has run out of API credit for the moment.")
        assertThat(p.sawOutput).isFalse()

        val q = AiSdkStreamParser()
        q.onData("""{"type":"error","errorText":"Rate limited"}""")
        assertThat(q.error).isEqualTo("Rate limited")

        assertThat(AiSdkStreamParser.finishReason("length")).isEqualTo("length")
        assertThat(AiSdkStreamParser.finishReason("tool-calls")).isEqualTo("tool_calls")
        assertThat(AiSdkStreamParser.finishReason("content-filter")).isEqualTo("content_filter")
        assertThat(AiSdkStreamParser.finishReason(null)).isEqualTo("stop")

        // No finish frame (Mercury never sends one): the end reason stays unknown, so the
        // agent loop does not take a file write left open at the end for a complete one.
        val r = AiSdkStreamParser()
        r.onData("""{"type":"text-delta","delta":"x"}""")
        assertThat(r.finish()).containsExactly(StreamEvent.Completed)
    }

    @Test
    fun `ui messages carry instructions in the first user turn and alternate roles`() {
        val turns = listOf(
            Transcript.Turn("system", "Use tools."),
            Transcript.Turn("user", "Hi"),
            Transcript.Turn("assistant", "Hello"),
            Transcript.Turn("user", "Result A"),
            Transcript.Turn("user", "Make a page")
        )
        val messages = AiSdkMessages.build(turns)
        assertThat(messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
            .containsExactly("user", "assistant", "user").inOrder()
        val first = text(messages[0].jsonObject)
        assertThat(first).startsWith("<instructions>\nUse tools.\n</instructions>")
        assertThat(first).endsWith("Hi")
        assertThat(text(messages[2].jsonObject)).isEqualTo("Result A\n\nMake a page")
        assertThat(messages[0].jsonObject["id"]!!.jsonPrimitive.content).matches("[0-9a-f]{16}")
    }

    // ---------------------------------------------------------------- Mercury

    @Test
    fun `mercury body is a useChat request without a model field`() {
        val body = MercuryWebClient.buildBody(
            "high",
            listOf(
                ChatRequestMessage(role = "system", content = "Be brief."),
                ChatRequestMessage(role = "user", content = "Hi")
            ),
            timezone = "Asia/Kathmandu"
        )!!
        assertThat(body.containsKey("model")).isFalse()
        assertThat(body["reasoningEffort"]!!.jsonPrimitive.content).isEqualTo("high")
        assertThat(body["trigger"]!!.jsonPrimitive.content).isEqualTo("submit-message")
        assertThat(body["timezone"]!!.jsonPrimitive.content).isEqualTo("Asia/Kathmandu")
        assertThat(body["webSearchEnabled"]!!.jsonPrimitive.content).isEqualTo("false")
        val messages = body["messages"]!!.jsonArray
        assertThat(messages).hasSize(1)
        assertThat(text(messages[0].jsonObject)).contains("Be brief.")

        assertThat(MercuryWebClient.buildBody("high", listOf(ChatRequestMessage(role = "system", content = "x")))).isNull()
    }

    @Test
    fun `mercury tool traffic is replayed as text`() {
        val body = MercuryWebClient.buildBody(
            "high",
            listOf(
                ChatRequestMessage(role = "user", content = "Edit it"),
                ChatRequestMessage(
                    role = "assistant", content = null,
                    toolCalls = listOf(ToolCallRequest(id = "c1", type = "function", function = FunctionCall("read_file", """{"path":"a.html"}""")))
                ),
                ChatRequestMessage(role = "tool", content = "<h1>Hi</h1>", toolCallId = "c1", name = "read_file")
            )
        )!!
        val messages = body["messages"]!!.jsonArray
        assertThat(messages.map { it.jsonObject["role"]!!.jsonPrimitive.content }).containsExactly("user", "assistant", "user").inOrder()
        assertThat(text(messages[1].jsonObject)).contains("read_file")
        assertThat(text(messages[2].jsonObject)).contains("<h1>Hi</h1>")
    }

    @Test
    fun `mercury effort comes from the seed id and the checkpoint is recognised`() {
        assertThat(MercuryWebClient.effortFor("mercury-3")).isEqualTo("high")
        assertThat(MercuryWebClient.effortFor("mercury-3-instant")).isEqualTo("instant")
        assertThat(MercuryWebClient.parseToken("""{"token":"abc.def","expiresIn":3600}""")).isEqualTo("abc.def")
        assertThat(MercuryWebClient.parseToken("""{"token":""}""")).isNull()
        assertThat(MercuryWebClient.parseToken("<!DOCTYPE html>")).isNull()

        // Vercel's checkpoint is a 429 flagged as a mitigation, not a rate limit.
        assertThat(MercuryWebClient.isChallenge(response(429, "x-vercel-mitigated" to "challenge"))).isTrue()
        assertThat(MercuryWebClient.isChallenge(response(429, "Content-Type" to "text/html; charset=utf-8"))).isTrue()
        assertThat(MercuryWebClient.isChallenge(response(429, "Content-Type" to "application/json"))).isFalse()
        assertThat(MercuryWebClient.isChallenge(response(200, "Content-Type" to "text/event-stream"))).isFalse()

        assertThat(MercuryWebClient.describeBrowserFailure(429, "<title>Vercel Security Checkpoint</title>"))
            .contains("browser check")
        assertThat(MercuryWebClient.describeBrowserFailure(0, "\nTypeError: Failed to fetch\n")).isEqualTo("TypeError: Failed to fetch")
    }

    // ----------------------------------------------------------------- UniKey

    @Test
    fun `unikey body keeps roles, merges runs and never sends tools`() {
        val body = UnikeyClient.buildBody(
            "claude-opus-4-8",
            listOf(
                ChatRequestMessage(role = "system", content = "Use tools."),
                ChatRequestMessage(role = "user", content = "Hi"),
                ChatRequestMessage(role = "user", content = "Make a page")
            )
        )!!
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("claude-opus-4-8")
        assertThat(body["stream"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(body.containsKey("tools")).isFalse()
        val messages = body["messages"] as JsonArray
        assertThat(messages.map { it.jsonObject["role"]!!.jsonPrimitive.content }).containsExactly("system", "user").inOrder()
        assertThat(messages[1].jsonObject["content"]!!.jsonPrimitive.content).isEqualTo("Hi\n\nMake a page")
        assertThat(UnikeyClient.buildBody("m", listOf(ChatRequestMessage(role = "system", content = "x")))).isNull()
    }

    @Test
    fun `unikey visitor ids are fresh and fingerprint shaped`() {
        val a = UnikeyClient.fingerprint()
        val b = UnikeyClient.fingerprint()
        assertThat(a).matches("rnd[0-9a-f]{32}")
        assertThat(a).isNotEqualTo(b)
    }

    // ------------------------------------------------------------- catalogue

    @Test
    fun `community seeds are keyless, namespaced and text-tool only`() {
        for (provider in listOf(AiProvider.MERCURY, AiProvider.UNIKEY)) {
            assertThat(provider.worksWithoutKey).isTrue()
            assertThat(provider.isCommunity).isTrue()
            assertThat(CommunityClients.forProvider(provider)).isNotNull()
            val seeds = SeedModels[provider]
            assertThat(seeds).isNotEmpty()
            seeds.forEach { m ->
                assertThat(m.supportsToolCalls).isFalse()
                assertThat(m.id).contains(":")
            }
        }
        assertThat(CommunityClients.upstreamId(SeedModels.unikey.first())).isEqualTo("claude-opus-4-8")
        // One Mercury 3: Instant…High is its reasoning setting, not another model.
        assertThat(SeedModels.mercury).hasSize(1)
        assertThat(CommunityClients.upstreamId(SeedModels.mercury.single())).isEqualTo("mercury-3")
    }

    @Test
    fun `official inception needs a key and mercury 3 takes an effort`() {
        assertThat(AiProvider.INCEPTION.requiresApiKey).isTrue()
        assertThat(AiProvider.INCEPTION.isCommunity).isFalse()
        val m3 = SeedModels.inception.first { it.id == "mercury-3" }
        assertThat(m3.supportsToolCalls).isTrue()
        assertThat(m3.supportsReasoningEffort).isTrue()
        // Inception documents the same four levels on every Mercury model.
        assertThat(SeedModels.inception.first { it.id == "mercury-2" }.reasoningControl!!.levels.map { it.wire })
            .containsExactly("instant", "low", "medium", "high").inOrder()
    }

    @Test
    fun `inception listing drops models chat cannot use`() {
        // Trimmed from https://api.inceptionlabs.ai/v1/models (public, 2026-10-22).
        val body = """{"data":[
            {"id":"mercury-2","name":"Inception: Mercury 2","context_length":128000,"supported_endpoints":["chat.completions"]},
            {"id":"mercury-3","name":"Inception: Mercury 3","context_length":1000000,"supported_endpoints":["chat.completions"]},
            {"id":"mercury-decide","name":"Inception: Mercury Decide","context_length":65536,"supported_endpoints":["decisions"]}
        ]}"""
        val models = OpenAiCompatibleModelService().parseModels(AiProvider.INCEPTION, body, hasKey = true)
        assertThat(models.map { it.id }).containsExactly("mercury-2", "mercury-3")
        assertThat(models.first { it.id == "mercury-3" }.contextLength).isEqualTo(1_000_000)
        assertThat(models.first { it.id == "mercury-3" }.supportsReasoningEffort).isTrue()
    }

    @Test
    fun `keyless fallback offers unikey claude for text-mode requests only`() {
        val from = AiModels.findModelById("k2:IFM/K2-Horizon-375B-A23B")!!
        val textMode = KeylessFallback.candidates(from, needsNativeTools = false) { AiModels.findModelById(it) }.map { it.id }
        assertThat(textMode).contains("unikey:claude-opus-4-8")
        assertThat(textMode).contains("mercury:mercury-3")
        val native = KeylessFallback.candidates(from, needsNativeTools = true) { AiModels.findModelById(it) }.map { it.id }
        assertThat(native).doesNotContain("unikey:claude-opus-4-8")
    }

    // ---------------------------------------------------------------- helpers

    private fun text(message: JsonObject): String =
        ((message["parts"] as JsonArray)[0].jsonObject["text"] as JsonPrimitive).content

    private fun response(code: Int, vararg headers: Pair<String, String>): Response {
        val b = Response.Builder()
            .request(Request.Builder().url("https://chat.inceptionlabs.ai/api/session").build())
            .protocol(Protocol.HTTP_2)
            .code(code)
            .message("")
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }


    @Test
    fun `an ai sdk reply that errors after some text is void`() {
        val p = AiSdkStreamParser()
        p.onData("""{"type":"text-delta","delta":"<tool_call>\n<function=write_file>"}""")
        p.onData("""{"type":"error","errorText":"upstream reset"}""")
        assertThat(p.sawOutput).isTrue()
        assertThat(p.failed).isTrue()

        val q = AiSdkStreamParser()
        q.onData("""{"type":"text-delta","delta":"Half an answer"}""")
        val events = q.onData("""{"type":"finish","finishReason":"error"}""")
        assertThat(q.failed).isTrue()
        assertThat(events.none { it is StreamEvent.FinishReason }).isTrue()

        val ok = AiSdkStreamParser()
        ok.onData("""{"type":"text-delta","delta":"Fine"}""")
        ok.onData("""{"type":"finish","finishReason":"stop"}""")
        assertThat(ok.failed).isFalse()
    }

    @Test
    fun `cancelling a blocked community call ends it at once`() = kotlinx.coroutines.runBlocking {
        // A server that accepts and then says nothing: the call blocks reading the response.
        val server = java.net.ServerSocket(0)
        kotlin.concurrent.thread(isDaemon = true) {
            runCatching { server.accept() }.getOrNull()?.let { socket ->
                runCatching { Thread.sleep(15_000) }
                socket.close()
            }
        }
        val client = okhttp3.OkHttpClient.Builder().readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
        val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()
        val job = launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { CommunityHttp.execute(request, client) { it.code } }
        }
        kotlinx.coroutines.delay(500)
        val started = System.nanoTime()
        job.cancel()
        job.join()
        val ms = (System.nanoTime() - started) / 1_000_000
        server.close()
        assertThat(ms).isLessThan(3_000L)
    }
}
