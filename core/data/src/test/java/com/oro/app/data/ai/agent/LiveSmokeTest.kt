package com.oro.app.data.ai.agent

import com.oro.app.data.ai.AiModels
import com.oro.app.data.ai.AiProviderManager
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.ModelCapabilities
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.JsonParserUtils
import com.oro.app.data.ai.tools.ToolAliases
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.ai.tools.WorkspaceTools
import com.oro.app.data.repository.AiRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/**
 * Opt-in end-to-end agent run against a real provider; does nothing unless
 * LIVE_MODEL is set. Used to verify providers and pick the keyless default:
 *
 *   LIVE_MODEL=k2:IFM/K2-Horizon-375B-A23B LIVE_DIR=/tmp/site LIVE_LOG=/tmp/site.log \
 *     ./gradlew :core:data:testDebugUnitTest --tests '*LiveSmokeTest*' --rerun
 *
 * LIVE_DIR must hold a small project; LIVE_TASK overrides the task, LIVE_RAW=1
 * logs raw model output.
 */
class LiveSmokeTest {
    @Test
    fun live() = runBlocking {
        val modelId = System.getenv("LIVE_MODEL") ?: return@runBlocking
        val model = AiModels.findModelById(modelId)!!
        val root = File(System.getenv("LIVE_DIR")!!)
        val log = File(System.getenv("LIVE_LOG")!!)
        val ws = WorkspaceTools(root)
        val manager = AiProviderManager()
        val native = model.supportsToolCalls
        val tools = AgentTools.all
        val start = System.currentTimeMillis()
        var calls = 0
        val loop = AgentLoop(
            client = { req -> manager.streamChatCompletion(model, req.messages, req.tools, 0.5f) },
            tools = { call ->
                calls++
                val args = JsonParserUtils.parseToJsonObject(call.function.arguments) ?: return@AgentLoop ToolResult(false, "", "Could not parse arguments")
                val (name, a) = ToolAliases.normalise(call.function.name, args)
                val r = when (name) {
                    "read_file" -> ws.readFile(a); "edit_file" -> ws.editFile(a); "write_file" -> ws.writeFile(a)
                    "multi_edit" -> ws.multiEdit(a); "list_files" -> ws.listFiles(a); "grep" -> ws.grep(a); "glob" -> ws.glob(a)
                    else -> null
                }?.result ?: ToolResult(true, "(not available in this test)")
                log.appendText("TOOL ${call.function.name} ${call.function.arguments.take(160).replace('\n',' ')} -> ${r.success} ${(r.output + (r.error ?: "")).take(120).replace('\n',' ')}\n")
                r
            },
            config = AgentLoopConfig(maxIterations = 15)
        )
        val tree = root.walkTopDown().filter { it.isFile }.joinToString("\n") { it.relativeTo(root).path }
        val history = mutableListOf(ChatRequestMessage(role = "user", content = System.getenv("LIVE_TASK") ?: "Add a footer to index.html with the text \"© 2026 Oro\" and give it a dark background (#111) with white text in style.css."))
        val result = loop.run(history, tools.map { it.function.name }.toSet(), object : AgentLoopListener {
            val raw = StringBuilder()
            override fun onText(delta: String) { raw.append(delta) }
            override fun onModelCallStarted(iteration: Int, attempt: Int) { raw.clear() }
            override fun onTurnComplete(visibleText: String, toolCalls: List<com.oro.app.data.ai.ToolCallResponse>) {
                if (System.getenv("LIVE_RAW") == "1") log.appendText("RAW>>> ${raw.toString().take(1500)}\n<<<\n")
            }
            override fun onStatus(status: AgentStatus) { if (status.kind == AgentStatus.Kind.RETRYING) log.appendText("STATUS ${status.label}\n") }
        }) { h, pressure ->
            val system = AiRepository.systemMessage("Project: Demo\n\nProject files:\n$tree", true, native, tools)
            val window = ModelCapabilities.resolveContextLength(model.id, model.reportedContextLength)
            val budget = ((window * listOf(0.9, 0.6, 0.35)[pressure.coerceAtMost(2)]).toInt() - TokenEstimator.estimate(system)).coerceAtLeast(1500)
            AgentRequest(listOf(system) + HistoryPruner.prune(h, budget), if (native) tools else null)
        }
        log.appendText("RESULT ${result.toString().take(300)}\nTIME ${(System.currentTimeMillis() - start) / 1000}s tools=$calls\n")
        root.walkTopDown().filter { it.isFile }.forEach { log.appendText("--- ${it.name}\n${it.readText()}\n") }
    }
}
