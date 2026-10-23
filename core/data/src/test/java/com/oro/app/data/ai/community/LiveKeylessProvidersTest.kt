package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiModels
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.Tool
import com.oro.app.data.ai.agent.AgentLoop
import com.oro.app.data.ai.agent.AgentLoopConfig
import com.oro.app.data.ai.agent.AgentLoopListener
import com.oro.app.data.ai.agent.AgentRequest
import com.oro.app.data.ai.agent.AgentRunResult
import com.oro.app.data.ai.agent.TextToolCallParser
import com.oro.app.data.ai.agent.TextToolProtocol
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.JsonParserUtils
import com.oro.app.data.ai.tools.ToolAliases
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.repository.AiRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live calls to the keyless sites added on 2026-10-23, through the real
 * clients. Skipped unless `ORO_LIVE_PROVIDERS=1` is set (CI never sets it):
 *
 *     ORO_LIVE_PROVIDERS=1 ./gradlew :core:data:testDebugUnitTest --tests '*LiveKeylessProvidersTest*'
 *
 * Each case prints what came back and fails if a site gives no usable reply.
 */
class LiveKeylessProvidersTest {

    private val live = System.getenv("ORO_LIVE_PROVIDERS") == "1"

    private fun run(id: String, messages: List<ChatRequestMessage>, level: String? = null, tools: List<Tool>? = null): List<StreamEvent> {
        assumeTrue(live)
        val model = AiModels.findModelById(id) ?: error("no seed $id")
        val started = System.currentTimeMillis()
        val events = runBlocking { withTimeout(240_000) { CommunityClients.stream(model, messages, level, tools).toList() } }
        val text = events.filterIsInstance<StreamEvent.ContentDelta>().joinToString("") { it.content }
        val calls = events.filterIsInstance<StreamEvent.ToolCalls>().flatMap { it.toolCalls }
        val error = events.filterIsInstance<StreamEvent.Error>().firstOrNull()
        println("LIVE $id (${System.currentTimeMillis() - started} ms): text=${text.take(160)} calls=${calls.map { it.function.name + it.function.arguments.take(80) }} error=${error?.message}")
        return events
    }

    private fun text(events: List<StreamEvent>) = events.filterIsInstance<StreamEvent.ContentDelta>().joinToString("") { it.content }

    private fun pong(id: String, level: String? = null) {
        val events = run(id, listOf(ChatRequestMessage("system", "Answer briefly."), ChatRequestMessage("user", "Reply with exactly: PONG")), level)
        check(text(events).contains("PONG")) { "$id did not answer PONG" }
    }

    /** About 95 characters a line: 1,300 lines is ~32k tokens. */
    private fun haystack(lines: Int): String {
        val filler = (0 until lines).joinToString("\n") { "Line $it: the quick brown fox jumps over the lazy dog while counting ${it * 7} pebbles." }
        val half = filler.length / 2
        return filler.substring(0, half) + "\nThe secret code is MAGENTA-4417.\n" + filler.substring(half)
    }

    private fun needle(id: String, lines: Int, level: String? = null) {
        val prompt = haystack(lines) + "\n\nWhat is the secret code? Reply with the code only."
        val events = run(id, listOf(ChatRequestMessage("user", prompt)), level)
        check(text(events).contains("MAGENTA-4417")) { "$id lost the needle in a ${prompt.length}-character prompt" }
    }

    private val writeFileTool = Tool(
        function = FunctionDefinition(
            "write_file", "Write a file in the project.",
            AiHttp.json.parseToJsonElement(
                """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}"""
            )
        )
    )

    /** Oro's text tool protocol, as text-mode models get it: the reply must hold a readable write_file call. */
    private fun textProtocol(id: String, level: String? = null) {
        val system = "You are Orobi, a coding agent. Change files with tools, never by pasting code.\n\n" +
            TextToolProtocol.render(listOf(writeFileTool))
        val events = run(id, listOf(ChatRequestMessage("system", system), ChatRequestMessage("user", "Create index.html containing just <h1>Hi</h1>.")), level)
        val reply = text(events)
        println("LIVE $id full reply <<<${reply.take(1500)}>>>")
        val call = TextToolCallParser.parse(reply, setOf("write_file")).calls.firstOrNull()
        check(call?.function?.name == "write_file" && call.function.arguments.contains("index.html")) { "$id wrote no readable write_file call" }
    }

    /**
     * A whole agent run on a long prompt, the way the editor drives it: Oro's
     * real system prompt and tool catalogue, a ~32k-token log with a code in
     * its middle, and the agent loop running the calls. The code has to land
     * in answer.txt and the run has to end with a reply.
     */
    private fun agentRoundTrip(id: String, lines: Int, level: String? = null) {
        assumeTrue(live)
        val model = AiModels.findModelById(id) ?: error("no seed $id")
        val native = model.supportsToolCalls
        val tools = AgentTools.all
        val files = mutableMapOf("index.html" to "<h1>Demo</h1>\n")
        val ran = mutableListOf<String>()
        val loop = AgentLoop(
            client = { req -> CommunityClients.stream(model, req.messages, level, req.tools) },
            tools = { call ->
                ran += call.function.name
                val parsed = JsonParserUtils.parseToJsonObject(call.function.arguments)
                    ?: return@AgentLoop ToolResult(false, "", "Could not parse arguments")
                val (name, args) = ToolAliases.normalise(call.function.name, parsed)
                val path = args["path"]?.jsonPrimitive?.contentOrNull
                when (name) {
                    "write_file" -> if (path == null) ToolResult(false, "", "path is required") else {
                        files[path] = args["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        ToolResult(true, "Wrote $path")
                    }
                    "read_file" -> files[path]?.let { ToolResult(true, it) } ?: ToolResult(false, "", "No such file: $path")
                    "list_files", "glob" -> ToolResult(true, files.keys.joinToString("\n"))
                    else -> ToolResult(true, "(not available in this test)")
                }
            },
            config = AgentLoopConfig(maxIterations = 8)
        )
        val task = haystack(lines) +
            "\n\nFind the secret code in the log above and save it to answer.txt (the code only, nothing else). " +
            "Then reply with one short sentence."
        val history = mutableListOf(ChatRequestMessage("user", task))
        val started = System.currentTimeMillis()
        val result = runBlocking {
            withTimeout(600_000) {
                loop.run(history, tools.map { it.function.name }.toSet(), object : AgentLoopListener {}) { h, _ ->
                    val system = AiRepository.systemMessage("Project: Demo\n\nProject files:\nindex.html", true, native, tools)
                    AgentRequest(listOf(system) + h, if (native) tools else null)
                }
            }
        }
        println(
            "LIVE $id agent round trip (${System.currentTimeMillis() - started} ms, ${task.length}-character task): " +
                "tools=$ran answer=${files["answer.txt"]} result=${result.toString().take(240)}"
        )
        check(files["answer.txt"]?.contains("MAGENTA-4417") == true) { "$id did not save the code" }
        check(result is AgentRunResult.Completed) { "$id did not finish: $result" }
    }

    // ------------------------------------------------------------------ Hy3

    @Test fun hy3Pong() = pong("hy3:hy3", "no_think")

    @Test fun hy3NativeToolCall() {
        val events = run(
            "hy3:hy3",
            listOf(
                ChatRequestMessage("system", "You are a coding agent. Use tools to change files."),
                ChatRequestMessage("user", "Create index.html containing just <h1>Hi</h1>. Use the tool.")
            ),
            "low", listOf(writeFileTool)
        )
        val call = events.filterIsInstance<StreamEvent.ToolCalls>().flatMap { it.toolCalls }.firstOrNull()
        check(call?.function?.name == "write_file" && call.function.arguments.contains("index.html")) { "Hy3 made no write_file call" }
    }

    @Test fun hy3LongPrompt() = needle("hy3:hy3", 2_000, "no_think")

    @Test fun hy3AgentRoundTrip() = agentRoundTrip("hy3:hy3", 1_300, "low")

    // -------------------------------------------------------------- MiniCPM

    @Test fun minicpmPong() = pong("minicpm:MiniCPM-V-4.7-35B-A3B", "off")

    @Test fun minicpmNativeToolCall() {
        val events = run(
            "minicpm:MiniCPM-V-4.7-35B-A3B",
            listOf(
                ChatRequestMessage("system", "You are a coding agent. Use tools to change files."),
                ChatRequestMessage("user", "Create index.html containing just <h1>Hi</h1>. Use the tool.")
            ),
            "off", listOf(writeFileTool)
        )
        val call = events.filterIsInstance<StreamEvent.ToolCalls>().flatMap { it.toolCalls }.firstOrNull()
        check(call?.function?.name == "write_file" && call.function.arguments.contains("index.html")) { "MiniCPM made no write_file call" }
    }

    @Test fun minicpmLongPrompt() = needle("minicpm:MiniCPM-V-4.7-35B-A3B", 1_300, "off")

    @Test fun minicpmAgentRoundTrip() = agentRoundTrip("minicpm:MiniCPM-V-4.7-35B-A3B", 1_300, "off")

    // --------------------------------------------------------------- Laguna

    @Test fun lagunaPong() = pong("laguna:laguna-s-2.1", "off")

    /** Laguna's calls come back as GLM blocks in its text, which the agent loop reads: at least 4 of 5. */
    @Test fun lagunaToolCallRate() {
        assumeTrue(live)
        var ok = 0
        repeat(5) {
            val events = runCatching {
                run(
                    "laguna:laguna-s-2.1",
                    listOf(
                        ChatRequestMessage("system", "You are Orobi, a coding agent. Change files with tools, never by pasting code."),
                        ChatRequestMessage("user", "Create index.html containing just <h1>Hi</h1>.")
                    ),
                    "off", listOf(writeFileTool)
                )
            }.getOrNull()
            val call = events?.let { TextToolCallParser.parse(text(it), setOf("write_file")).calls.firstOrNull() }
            if (call?.function?.name == "write_file" && call.function.arguments.contains("index.html")) ok++
        }
        println("LIVE laguna tool-call rate: $ok/5")
        check(ok >= 4) { "Laguna wrote a readable call only $ok of 5 times" }
    }

    @Test fun lagunaLongPrompt() = needle("laguna:laguna-s-2.1", 1_300, "off")

    @Test fun lagunaAgentRoundTrip() = agentRoundTrip("laguna:laguna-s-2.1", 1_300, "off")

    // ----------------------------------------------------------- Cloudflare

    @Test fun cloudflarePong() = pong("cfai:@cf/zai-org/glm-4.7-flash")

    @Test fun gemmaTextProtocol() = textProtocol("cfai:@cf/google/gemma-4-26b-a4b-it")

    @Test fun cloudflareLongFinalTurnIsSplit() = needle("cfai:@cf/google/gemma-4-26b-a4b-it", 600)

    @Test fun cloudflareLongPrompt() = needle("cfai:@cf/google/gemma-4-26b-a4b-it", 1_300)

    @Test fun gemmaAgentRoundTrip() = agentRoundTrip("cfai:@cf/google/gemma-4-26b-a4b-it", 1_300)
}
