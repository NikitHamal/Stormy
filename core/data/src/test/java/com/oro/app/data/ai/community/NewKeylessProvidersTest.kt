package com.oro.app.data.ai.community

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModels
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.KeylessFallback
import com.oro.app.data.ai.KeylessSeeds
import com.oro.app.data.ai.ReasoningControls
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.Tool
import com.oro.app.data.ai.ToolCallRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** Hy3, MiniCPM and Laguna (Hugging Face Spaces) and Cloudflare's AI Playground, with frames recorded 2026-10-23. */
class NewKeylessProvidersTest {

    private fun json(s: String) = AiHttp.json.parseToJsonElement(s)

    // ------------------------------------------------------------ Gradio diffs

    @Test
    fun `gradio diffs append text, add items and edit nested values`() {
        val start = json("""["1", "", [], [{"role":"user","content":"q"},{"role":"assistant","content":"1"}]]""")
        val ops = json(
            """[["append",[0],". Use flex"],["append",[3,1,"content"],". Use flex"],["add",[2,0],{"id":"c1"}],["replace",[1],"think"]]"""
        ) as JsonArray
        val out = GradioDiff.apply(start, ops) as JsonArray
        assertThat(out[0].jsonPrimitive.content).isEqualTo("1. Use flex")
        assertThat(out[1].jsonPrimitive.content).isEqualTo("think")
        assertThat(out[2].jsonArray).hasSize(1)
        assertThat(out[3].jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content).isEqualTo("1. Use flex")
    }

    @Test
    fun `gradio diffs replace the whole value and delete keys`() {
        val replaced = GradioDiff.apply(json("""{"a":1}"""), json("""[["replace",[],{"b":2}]]""") as JsonArray)
        assertThat((replaced as JsonObject).keys).containsExactly("b")
        val deleted = GradioDiff.apply(json("""{"a":1,"b":2}"""), json("""[["delete",["a"],null]]""") as JsonArray)
        assertThat((deleted as JsonObject).keys).containsExactly("b")
    }

    // ------------------------------------------------------- queue parser (Hy3)

    @Test
    fun `hy3 queue frames stream text, reasoning and tool calls once complete`() {
        val p = HfSpaceClient.QueueParser(HfSpaces.Hy3)
        val frames = listOf(
            """{"msg":"estimation","event_id":"e1","rank":0,"queue_size":1,"rank_eta":27.8}""",
            """{"msg":"process_starts","event_id":"e1","eta":27.8}""",
            """{"msg":"process_generating","event_id":"e1","output":{"data":[["", "We", [], [{"role":"user","content":"Create index.html"},{"role":"assistant","content":"","reasoning_content":"We"}]]],"is_generating":true},"success":true}""",
            """{"msg":"process_generating","event_id":"e1","output":{"data":[[["append",[1]," need a file."],["append",[3,1,"reasoning_content"]," need a file."]]],"is_generating":true},"success":true}""",
            """{"msg":"process_generating","event_id":"e1","output":{"data":[[["append",[0],"Writing it."]]],"is_generating":true},"success":true}""",
            """{"msg":"process_generating","event_id":"e1","output":{"data":[[]],"is_generating":true},"success":true}""",
            """{"msg":"process_completed","event_id":"e1","output":{"data":[["Writing it.", "We need a file.", [{"id":"chatcmpl-tool-a3b","type":"function","function":{"name":"write_file","arguments":"{\"path\": \"index.html\", \"content\": \"<h1>Hi</h1>\"}"}}], []]]},"success":true}"""
        )
        val events = frames.flatMap { p.onMessage(it) }
        assertThat(events.filterIsInstance<StreamEvent.ReasoningDelta>().joinToString("") { it.reasoning }).isEqualTo("We need a file.")
        assertThat(events.filterIsInstance<StreamEvent.ContentDelta>().joinToString("") { it.content }).isEqualTo("Writing it.")
        val call = events.filterIsInstance<StreamEvent.ToolCalls>().single().toolCalls.single()
        assertThat(call.id).isEqualTo("chatcmpl-tool-a3b")
        assertThat(call.function.name).isEqualTo("write_file")
        assertThat(call.function.arguments).contains("index.html")
        assertThat(events.filterIsInstance<StreamEvent.FinishReason>().single().reason).isEqualTo("tool_calls")
        assertThat(events.last()).isEqualTo(StreamEvent.Completed)
        assertThat(p.done).isTrue()
    }

    @Test
    fun `a failed space event is a retryable error, and an empty end is not an answer`() {
        val p = HfSpaceClient.QueueParser(HfSpaces.Hy3)
        val events = p.onMessage("""{"msg":"process_completed","event_id":"e1","output":{"error":"Upstream rate limit"},"success":false}""")
        val error = events.single() as StreamEvent.Error
        assertThat(error.message).contains("rate limit")
        assertThat(error.retryable).isTrue()

        val empty = HfSpaceClient.QueueParser(HfSpaces.Hy3)
        assertThat(empty.onMessage("""{"msg":"close_stream","event_id":null}""").single()).isInstanceOf(StreamEvent.Error::class.java)
    }

    @Test
    fun `queue waits and silent progress keep the stream alive`() {
        // A long queue or a Space that answers in one piece must not look like silence.
        val p = HfSpaceClient.QueueParser(HfSpaces.Laguna)
        listOf(
            """{"msg":"estimation","event_id":"e1","rank":3,"queue_size":4,"rank_eta":40.0}""",
            """{"msg":"heartbeat"}""",
            """{"msg":"process_starts","event_id":"e1"}""",
            """{"msg":"process_generating","output":{"data":[{"event":"delta","text":"thinking...\n"}]},"success":true}"""
        ).forEach { frame ->
            assertThat(p.onMessage(frame).single()).isInstanceOf(StreamEvent.RawSSE::class.java)
        }
        assertThat(p.done).isFalse()
        // Nothing after the end, not even a keep-alive.
        p.onMessage("""{"msg":"close_stream"}""")
        assertThat(p.onMessage("""{"msg":"heartbeat"}""")).isEmpty()
    }

    @Test
    fun `a reply cut off by the space's output cap says so`() {
        val p = HfSpaceClient.QueueParser(HfSpaces.MiniCpm)
        val events = p.onMessage(
            """{"msg":"process_completed","output":{"data":[{"content":"<!doctype html><html>","reasoning":"","finish_reason":"length"}]},"success":true}"""
        )
        assertThat(events.filterIsInstance<StreamEvent.FinishReason>().single().reason).isEqualTo("length")
    }

    @Test
    fun `fn index is read from the space config`() {
        val config = """{"protocol":"sse_v3","dependencies":[{"id":3,"api_name":"init_state"},{"id":8,"api_name":"chat"}]}"""
        assertThat(HfSpaceClient.fnIndexIn(config, "chat")).isEqualTo(8)
        assertThat(HfSpaceClient.fnIndexIn(config, "missing")).isNull()
    }

    // ---------------------------------------------------------------- inputs

    private val writeFile = Tool(
        function = FunctionDefinition(
            "write_file", "Write a file.",
            json("""{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}""")
        )
    )

    @Test
    fun `hy3 sends the last user turn as the message with history, tools and think level`() {
        val data = HfSpaces.Hy3.inputs(
            "hy3",
            listOf(
                ChatRequestMessage("system", "You are Orobi."),
                ChatRequestMessage("user", "Hi"),
                ChatRequestMessage("assistant", "Hello"),
                ChatRequestMessage("user", "Make a page")
            ),
            listOf(writeFile), "low"
        )!!
        assertThat(data[0].jsonPrimitive.content).isEqualTo("Make a page")
        assertThat(data[1].jsonPrimitive.content).isEqualTo("You are Orobi.")
        assertThat(data[2].jsonArray).hasSize(2)
        assertThat(data[3].jsonPrimitive.content).isEqualTo("low")
        assertThat(data[4]).isEqualTo(JsonNull)
        val tools = json(data[8].jsonPrimitive.content).jsonArray
        assertThat(tools.single().jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content).isEqualTo("write_file")
    }

    @Test
    fun `after tool results hy3 continues with the results in history`() {
        val data = HfSpaces.Hy3.inputs(
            "hy3",
            listOf(
                ChatRequestMessage("user", "Make a page"),
                ChatRequestMessage("assistant", null, toolCalls = listOf(ToolCallRequest("t1", function = FunctionCall("write_file", "{\"path\":\"a\"}")))),
                ChatRequestMessage("tool", "Wrote a", toolCallId = "t1", name = "write_file")
            ),
            null, "unknown-level"
        )!!
        assertThat(data[0].jsonPrimitive.content).isEqualTo(HfSpaces.Hy3.CONTINUE)
        val history = data[2].jsonArray
        assertThat(history).hasSize(3)
        assertThat(history[1].jsonObject["tool_calls"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content).isEqualTo("t1")
        assertThat(history[2].jsonObject["tool_call_id"]!!.jsonPrimitive.content).isEqualTo("t1")
        assertThat(data[3].jsonPrimitive.content).isEqualTo("high")
        assertThat(data[8].jsonPrimitive.content).isEmpty()
    }

    @Test
    fun `minicpm gets paired turns with instructions in the first one`() {
        val data = HfSpaces.MiniCpm.inputs(
            "MiniCPM-V-4.7-35B-A3B",
            listOf(
                ChatRequestMessage("system", "Be brief."),
                ChatRequestMessage("user", "One"),
                ChatRequestMessage("assistant", "Uno"),
                ChatRequestMessage("user", "Two")
            ),
            null, "off"
        )!!
        assertThat(data).hasSize(18)
        assertThat(data[0].jsonPrimitive.content).isEqualTo("Two")
        val turn = data[1].jsonArray.single().jsonArray
        assertThat(turn[0].jsonPrimitive.content).startsWith("<instructions>\nBe brief.")
        assertThat(turn[1].jsonPrimitive.content).isEqualTo("Uno")
        assertThat(turn[3].jsonArray).isEmpty()
        assertThat(data[3].jsonPrimitive.content).isEqualTo("false")
        assertThat(data[14]).isEqualTo(JsonNull)
        assertThat(data[16]).isEqualTo(JsonNull)
        assertThat(data[17].jsonPrimitive.content).isEqualTo("MiniCPM-V-4.7-35B-A3B")
    }

    @Test
    fun `minicpm places tool exchanges where the space expects them`() {
        val call1 = ToolCallRequest("t1", function = FunctionCall("write_file", "{\"path\":\"a\"}"))
        val call2 = ToolCallRequest("t2", function = FunctionCall("read_file", "{\"path\":\"a\"}"))
        val dangling = ToolCallRequest("t9", function = FunctionCall("read_file", "{}"))
        val data = HfSpaces.MiniCpm.inputs(
            "MiniCPM-V-4.7-35B-A3B",
            listOf(
                ChatRequestMessage("system", "Rules"),
                ChatRequestMessage("user", "Make a"),
                ChatRequestMessage("assistant", null, toolCalls = listOf(call1)),
                ChatRequestMessage("tool", "Wrote a", toolCallId = "t1", name = "write_file"),
                ChatRequestMessage("assistant", "Done."),
                ChatRequestMessage("user", "Check it"),
                ChatRequestMessage("assistant", null, toolCalls = listOf(dangling)),
                ChatRequestMessage("assistant", "Reading.", toolCalls = listOf(call2)),
                ChatRequestMessage("tool", "<h1>a</h1>", toolCallId = "t2", name = "read_file")
            ),
            listOf(writeFile, Tool(function = FunctionDefinition("bad name!", "x", json("{\"type\":\"object\"}")))), null
        )!!
        assertThat(data[0].jsonPrimitive.content).isEqualTo("Check it")
        val turn = data[1].jsonArray.single().jsonArray
        assertThat(turn[0].jsonPrimitive.content).startsWith("<instructions>\nRules")
        assertThat(turn[1].jsonPrimitive.content).isEqualTo("Done.")
        val past = turn[3].jsonArray
        assertThat(past.map { it.jsonObject["role"]!!.jsonPrimitive.content }).containsExactly("assistant", "tool").inOrder()
        // The current exchange: the unanswered call is dropped, the answered one kept with its result.
        val current = data[16].jsonArray
        assertThat(current).hasSize(2)
        assertThat(current[0].jsonObject["tool_calls"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content).isEqualTo("t2")
        assertThat(current[1].jsonObject["tool_call_id"]!!.jsonPrimitive.content).isEqualTo("t2")
        // Only names the Space accepts are offered.
        val tools = data[14].jsonArray
        assertThat(tools.map { it.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }).containsExactly("write_file")
        assertThat(data[3].jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `minicpm reads native tool calls from its frames`() {
        val p = HfSpaceClient.QueueParser(HfSpaces.MiniCpm)
        val events = p.onMessage(
            """{"msg":"process_completed","output":{"data":[{"content":"","reasoning":"","finish_reason":"tool_calls","tool_calls":[{"id":"chatcmpl-tool-984a","type":"function","function":{"name":"write_file","arguments":"{\"path\": \"index.html\", \"content\": \"<h1>Hi</h1>\"}"}}]}]},"success":true}"""
        )
        val call = events.filterIsInstance<StreamEvent.ToolCalls>().single().toolCalls.single()
        assertThat(call.id).isEqualTo("chatcmpl-tool-984a")
        assertThat(call.function.arguments).contains("index.html")
        assertThat(events.filterIsInstance<StreamEvent.FinishReason>().single().reason).isEqualTo("tool_calls")
    }

    @Test
    fun `minicpm frames are cumulative content and reasoning`() {
        val p = HfSpaceClient.QueueParser(HfSpaces.MiniCpm)
        val events = listOf(
            """{"msg":"process_generating","output":{"data":[{"content":"P","reasoning":"","finish_reason":null}]},"success":true}""",
            """{"msg":"process_generating","output":{"data":[[["append",["content"],"ONG"]]]},"success":true}""",
            """{"msg":"process_completed","output":{"data":[{"content":"PONG","reasoning":"","finish_reason":"stop"}]},"success":true}"""
        ).flatMap { p.onMessage(it) }
        assertThat(events.filterIsInstance<StreamEvent.ContentDelta>().joinToString("") { it.content }).isEqualTo("PONG")
        assertThat(events.last()).isEqualTo(StreamEvent.Completed)
    }

    @Test
    fun `laguna reads the clean answer from its done event and errors from its terminal`() {
        val p = HfSpaceClient.QueueParser(HfSpaces.Laguna)
        val events = listOf(
            """{"msg":"process_generating","output":{"data":[{"event":"delta","text":"\u001b[90m$ \u001b[0m\u001b[32muser\u001b[0m Reply\n"}]},"success":true}""",
            """{"msg":"process_generating","output":{"data":[{"event":"done","text":"","assistant":{"role":"assistant","content":"PONG","reasoning_details":[{"type":"reasoning.text","text":"Short."}]}}]},"success":true}""",
            """{"msg":"process_completed","output":{"data":[{"event":"done","text":"","assistant":{"role":"assistant","content":"PONG","reasoning_details":[{"type":"reasoning.text","text":"Short."}]}}]},"success":true}"""
        ).flatMap { p.onMessage(it) }
        assertThat(events.filterIsInstance<StreamEvent.ContentDelta>().single().content).isEqualTo("PONG")
        assertThat(events.filterIsInstance<StreamEvent.ReasoningDelta>().single().reasoning).isEqualTo("Short.")

        val failed = HfSpaceClient.QueueParser(HfSpaces.Laguna).onMessage(
            """{"msg":"process_generating","output":{"data":[{"event":"delta","text":"\n\u001b[1m\u001b[31merror\u001b[0m \u001b[2m»\u001b[0m 429 Too Many Requests\n"}]},"success":true}"""
        )
        assertThat((failed.single() as StreamEvent.Error).message).contains("429")
    }

    @Test
    fun `laguna sends merged messages and the reasoning switch`() {
        val data = HfSpaces.Laguna.inputs(
            "laguna-s-2.1",
            listOf(ChatRequestMessage("system", "S"), ChatRequestMessage("user", "A"), ChatRequestMessage("user", "B")),
            null, null
        )!!
        val msgs = data[0].jsonArray
        assertThat(msgs.map { it.jsonObject["role"]!!.jsonPrimitive.content }).containsExactly("system", "user").inOrder()
        assertThat(msgs[1].jsonObject["content"]!!.jsonPrimitive.content).isEqualTo("A\n\nB")
        assertThat(data[1].jsonPrimitive.content).isEqualTo("true")
    }

    private val writeFull = Tool(
        function = FunctionDefinition(
            "write_file", "Write a file.",
            json("""{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"},"append":{"type":"boolean"}},"required":["path","content"]}""")
        )
    )

    @Test
    fun `laguna carries tools and earlier calls in GLM's template`() {
        val call = ToolCallRequest(id = "c1", function = FunctionCall("write_file", """{"path":"index.html","content":"<h1 class=\"t\">Hi</h1>\n<p>x</p>","append":false}"""))
        val data = HfSpaces.Laguna.inputs(
            "laguna-s-2.1",
            listOf(
                ChatRequestMessage("system", "Be Orobi."),
                ChatRequestMessage("user", "Make a page."),
                ChatRequestMessage("assistant", "On it.", toolCalls = listOf(call)),
                ChatRequestMessage("tool", "Wrote index.html", toolCallId = "c1", name = "write_file")
            ),
            listOf(writeFull), "off"
        )!!
        val msgs = data[0].jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content to it.jsonObject["content"]!!.jsonPrimitive.content }
        assertThat(msgs.map { it.first }).containsExactly("system", "user", "assistant", "user").inOrder()
        val system = msgs[0].second
        assertThat(system).startsWith("Be Orobi.\n\n# Tools")
        assertThat(system).contains("<tools>\n{\"type\":\"function\",\"function\":{\"name\":\"write_file\"")
        assertThat(system).endsWith("<arg_key>{arg-key-2}</arg_key>\n<arg_value>{arg-value-2}</arg_value>\n...\n</tool_call>")
        assertThat(msgs[2].second).isEqualTo(
            "On it.\n<tool_call>write_file\n<arg_key>path</arg_key>\n<arg_value>index.html</arg_value>\n" +
                "<arg_key>content</arg_key>\n<arg_value><h1 class=\"t\">Hi</h1>\n<p>x</p></arg_value>\n" +
                "<arg_key>append</arg_key>\n<arg_value>false</arg_value>\n</tool_call>"
        )
        assertThat(msgs[3].second).isEqualTo("<tool_result name=\"write_file\">\nWrote index.html\n</tool_result>")
        assertThat(data[1].jsonPrimitive.content).isEqualTo("false")

        // What the agent loop reads back from that template: the same call, typed by the schema.
        val schemas = mapOf("write_file" to writeFull.function.parameters as JsonObject)
        val parsed = com.oro.app.data.ai.agent.TextToolCallParser.parse(msgs[2].second, setOf("write_file"), schemas)
        val args = json(parsed.calls.single().function.arguments).jsonObject
        assertThat(args["content"]!!.jsonPrimitive.content).isEqualTo("<h1 class=\"t\">Hi</h1>\n<p>x</p>")
        assertThat(args["append"]).isEqualTo(JsonPrimitive(false))
        assertThat(parsed.visibleText).isEqualTo("On it.")
    }

    @Test
    fun `laguna's own call blocks parse, and a reply it ended on gets a Continue turn`() {
        // Recorded 2026-10-23, reasoning off.
        val reply = "<tool_call>write_file<arg_key>path</arg_key><arg_value>index.html</arg_value>" +
            "<arg_key>content</arg_key><arg_value><h1>Hi</h1></arg_value></tool_call>"
        val parsed = com.oro.app.data.ai.agent.TextToolCallParser.parse(reply, setOf("write_file"))
        val args = json(parsed.calls.single().function.arguments).jsonObject
        assertThat(args["path"]!!.jsonPrimitive.content).isEqualTo("index.html")
        assertThat(args["content"]!!.jsonPrimitive.content).isEqualTo("<h1>Hi</h1>")

        val data = HfSpaces.Laguna.inputs(
            "laguna-s-2.1",
            listOf(ChatRequestMessage("assistant", "Hello."), ChatRequestMessage("user", "Hi"), ChatRequestMessage("assistant", "Done.")),
            null, null
        )!!
        val msgs = data[0].jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content to it.jsonObject["content"]!!.jsonPrimitive.content }
        assertThat(msgs).containsExactly("user" to "Hi", "assistant" to "Done.", "user" to HfSpaces.Hy3.CONTINUE).inOrder()
        assertThat(HfSpaces.Laguna.inputs("laguna-s-2.1", listOf(ChatRequestMessage("system", "S")), null, null)).isNull()
    }

    // ------------------------------------------------------------ Cloudflare

    @Test
    fun `a long final turn is split so the newest message fits the playground cap`() {
        val long = (1..900).joinToString("\n") { "Line $it of a long tool result with some content." }
        val built = CloudflarePlaygroundClient.Messages.build(
            listOf(Transcript.Turn("system", "Rules"), Transcript.Turn("user", "Task"), Transcript.Turn("assistant", "Working"), Transcript.Turn("user", long))
        )!!
        val roles = built.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertThat(roles.first()).isEqualTo("user")
        assertThat(roles.last()).isEqualTo("user")
        roles.zipWithNext().forEach { (a, b) -> assertThat(a).isNotEqualTo(b) }
        val texts = built.map { it.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content }
        assertThat(texts.last().length).isAtMost(CloudflarePlaygroundClient.Messages.LAST_TURN_LIMIT)
        assertThat(texts.joinToString("") { it }).doesNotContain("Rules")
        // Nothing lost: the user parts rejoin into the original text.
        val userParts = built.drop(2).filter { it.jsonObject["role"]!!.jsonPrimitive.content == "user" }
            .map { it.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content }
        assertThat(userParts.joinToString("")).isEqualTo(long)
    }

    @Test
    fun `splitting cuts at line breaks and keeps every part under its limit`() {
        val text = (1..400).joinToString("\n") { "row $it" }
        val parts = CloudflarePlaygroundClient.Messages.split(text, 500, 1_000)
        assertThat(parts.last().length).isAtMost(500)
        parts.dropLast(1).forEach { assertThat(it.length).isAtMost(1_000) }
        assertThat(parts.joinToString("")).isEqualTo(text)
        assertThat(CloudflarePlaygroundClient.Messages.split("short", 500, 1_000)).containsExactly("short")
    }

    @Test
    fun `playground error bodies are recognised, chunks are not`() {
        val rate = CloudflarePlaygroundClient.bodyError(
            """{"message":"The model is currently rate limited. Please wait a moment and try again.","details":"3021: rate limiting"}""", false
        )!!
        assertThat(rate.second).isEqualTo(429)
        assertThat(CloudflarePlaygroundClient.bodyError("""{"message":"Safety check unavailable. Please try again shortly."}""", false)!!.second)
            .isEqualTo(503)
        assertThat(CloudflarePlaygroundClient.bodyError("""{"type":"text-delta","id":"t","delta":"Hi"}""", false)).isNull()
        assertThat(CloudflarePlaygroundClient.bodyError("", false)).isNull()
        assertThat(CloudflarePlaygroundClient.bodyError("boom", true)!!.first).isEqualTo("boom")
    }

    @Test
    fun `playground config keeps the agent's state and sets model and instructions`() {
        val initial = json("""{"model":"@cf/zai-org/glm-4.7-flash","temperature":1,"stream":true,"system":"theirs","webSearchEnabled":false}""") as JsonObject
        val config = CloudflarePlaygroundClient.config(initial, "@cf/google/gemma-4-26b-a4b-it", "Ours")
        assertThat(config["model"]!!.jsonPrimitive.content).isEqualTo("@cf/google/gemma-4-26b-a4b-it")
        assertThat(config["system"]!!.jsonPrimitive.content).isEqualTo("Ours")
        assertThat(config["webSearchEnabled"]).isEqualTo(JsonPrimitive(false))
        val request = CloudflarePlaygroundClient.chatRequest("r1", JsonArray(emptyList()))
        assertThat(request["type"]!!.jsonPrimitive.content).isEqualTo("cf_agent_use_chat_request")
        assertThat(json(request["init"]!!.jsonObject["body"]!!.jsonPrimitive.content).jsonObject["trigger"]!!.jsonPrimitive.content)
            .isEqualTo("submit-message")
    }

    // ------------------------------------------------------ seeds and fallback

    @Test
    fun `new providers are seeded keyless with the right tool support`() {
        val hy3 = AiModels.findModelById("hy3:hy3")!!
        assertThat(hy3.provider).isEqualTo(AiProvider.HY3)
        assertThat(hy3.supportsToolCalls).isTrue()
        assertThat(hy3.contextLength).isEqualTo(262_144)
        assertThat(AiModels.findModelById("minicpm:MiniCPM-V-4.7-35B-A3B")!!.supportsToolCalls).isTrue()
        assertThat(AiModels.findModelById("laguna:laguna-s-2.1")!!.supportsToolCalls).isTrue()
        listOf("cfai:@cf/zai-org/glm-4.7-flash", "cfai:@cf/google/gemma-4-26b-a4b-it").forEach { id ->
            val m = AiModels.findModelById(id)!!
            assertThat(m.supportsToolCalls).isFalse()
            assertThat(m.provider.worksWithoutKey).isTrue()
            assertThat(m.provider.isCommunity).isTrue()
        }
        assertThat(KeylessSeeds[AiProvider.CLOUDFLARE_PLAYGROUND].map { it.id }).contains("cfai:@cf/google/gemma-4-26b-a4b-it")
    }

    @Test
    fun `tool-taking demos stand in for native tool requests, text-only bridges cannot`() {
        // One Space client, several sites: each adapter says whether its site takes tools.
        listOf(AiProvider.HY3, AiProvider.MINICPM, AiProvider.LAGUNA).forEach {
            assertThat(CommunityClients.takesTools(it)).isTrue()
        }
        assertThat(CommunityClients.takesTools(AiProvider.CLOUDFLARE_PLAYGROUND)).isFalse()
        val anchor = AiModels.findModelById("cfai:@cf/zai-org/glm-4.7-flash")!!
        val native = KeylessFallback.candidates(anchor, needsNativeTools = true, enabled = null) { AiModels.findModelById(it) }.map { it.id }
        assertThat(native).containsAtLeast("hy3:hy3", "laguna:laguna-s-2.1").inOrder()
        assertThat(native).doesNotContain("cfai:@cf/google/gemma-4-26b-a4b-it")
        // MiniCPM's demo logs every request: only used when the user picks it.
        assertThat(native).doesNotContain("minicpm:MiniCPM-V-4.7-35B-A3B")
        val text = KeylessFallback.candidates(anchor, needsNativeTools = false, enabled = null) { AiModels.findModelById(it) }.map { it.id }
        assertThat(text).containsAtLeast("laguna:laguna-s-2.1", "cfai:@cf/google/gemma-4-26b-a4b-it")
        // Unreliable text tool calls: selectable, never a stand-in.
        assertThat(KeylessFallback.order).doesNotContain("cfai:@cf/zai-org/glm-4.7-flash")
    }

    @Test
    fun `reasoning controls use each demo's own scale`() {
        val hy3 = ReasoningControls.forModel(AiModels.findModelById("hy3:hy3")!!)!!
        assertThat(hy3.levels.map { it.wire }).containsExactly("no_think", "low", "high").inOrder()
        assertThat(hy3.initial).isEqualTo("high")
        assertThat(ReasoningControls.forModel(AiModels.findModelById("laguna:laguna-s-2.1")!!)!!.levels.map { it.wire })
            .containsExactly("off", "on").inOrder()
        assertThat(ReasoningControls.forModel(AiModels.findModelById("cfai:@cf/zai-org/glm-4.7-flash")!!)).isNull()
    }
}
