package com.oro.app.data.ai.agent

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.craft.QualityGate
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.CraftTools
import com.oro.app.data.ai.tools.JsonParserUtils
import com.oro.app.data.ai.tools.ProjectTools
import com.oro.app.data.ai.tools.ToolAliases
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.ai.tools.WorkspaceTools
import com.oro.app.data.repository.AiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/**
 * Golden tasks: whole agent runs, end to end, the way a weak model performs
 * them -- a scripted model, the real [AgentLoop], the real file tools
 * ([ProjectTools], the code ToolExecutor runs) on a temp project, the real
 * [HistoryPruner] and the real quality gate. Each task is a failure mode seen
 * with real models; each asserts on the files the user ends up with.
 */
class GoldenTasksTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ------------------------------------------------------------- harness

    /** A model step: sees the request, answers with a stream. */
    private class Model(vararg steps: (AgentRequest) -> Flow<StreamEvent>) : AgentModelClient {
        private val queue = ArrayDeque(steps.toList())
        val requests = mutableListOf<AgentRequest>()
        override suspend fun stream(request: AgentRequest): Flow<StreamEvent> {
            requests += request
            return queue.removeFirstOrNull()?.invoke(request)
                ?: flowOf(StreamEvent.Started, StreamEvent.ContentDelta("(script ended)"), StreamEvent.Completed)
        }
    }

    private class Project(val root: File) {
        val ws = WorkspaceTools(root)
        val touched = linkedSetOf<String>()
        val ran = mutableListOf<String>()

        /** Every write as it happened, to prove a cut-off file never landed. */
        val writes = mutableListOf<Pair<String, String>>()

        val runner = AgentToolRunner { call ->
            val name = ToolAliases.canonicalName(call.function.name)
            ran += name
            if (name == "write_file") {
                val args = JsonParserUtils.parseToJsonObject(call.function.arguments)
                val path = (args?.get("path") as? JsonPrimitive)?.content
                val content = (args?.get("content") as? JsonPrimitive)?.content
                if (path != null && content != null) writes += path to content
            }
            val result = ProjectTools.execute(root, ws, call) ?: when (name) {
                "check_syntax" -> {
                    val path = (JsonParserUtils.parseToJsonObject(call.function.arguments)?.get("path") as? JsonPrimitive)
                        ?.content?.takeIf { it.isNotBlank() }
                    CraftTools.check(root, path) { null }
                }
                else -> ToolResult(false, "", "$name is not available in this test.")
            }
            if (result.success) touched += QualityGate.touchedPaths(call)
            result
        }

        fun read(path: String) = File(root, path).readText()
    }

    private val known = AgentTools.forMainAgent().map { it.function.name }.toSet()

    private fun textRequest(budget: Int = 200_000): suspend (List<ChatRequestMessage>, Int) -> AgentRequest {
        val system = AiRepository.systemMessage("Project: golden", agentMode = true, nativeTools = false, toolList = AgentTools.forMainAgent())
        return { h, _ -> AgentRequest(listOf(system) + HistoryPruner.prune(h, budget), null) }
    }

    private fun nativeRequest(budget: Int = 200_000): suspend (List<ChatRequestMessage>, Int) -> AgentRequest {
        val system = AiRepository.systemMessage("Project: golden", agentMode = true, nativeTools = true)
        return { h, _ -> AgentRequest(listOf(system) + HistoryPruner.prune(h, budget), AgentTools.forMainAgent()) }
    }

    private fun loop(model: AgentModelClient, project: Project) =
        AgentLoop(model, project.runner, AgentLoopConfig(), sleep = {}, random = Random(7))

    private suspend fun Project.run(model: Model, task: String, request: suspend (List<ChatRequestMessage>, Int) -> AgentRequest): AgentRunResult {
        val history = mutableListOf(ChatRequestMessage(role = "user", content = task))
        return loop(model, this).run(history, known, object : AgentLoopListener {}, { QualityGate.review(root, touched) }, request)
    }

    // ---------------------------------------------------- model utterances

    /** A text-protocol call, raw values, the way the protocol teaches it. */
    private fun call(name: String, vararg params: Pair<String, String>) = buildString {
        append("<tool_call>\n<function=").append(name).append(">\n")
        params.forEach { (k, v) ->
            append("<parameter=").append(k).append(">")
            if ('\n' in v) append('\n').append(v).append('\n') else append(v)
            append("</parameter>\n")
        }
        append("</function>\n</tool_call>")
    }

    private fun say(text: String, finish: String = "stop"): (AgentRequest) -> Flow<StreamEvent> = {
        flowOf(StreamEvent.Started, StreamEvent.ContentDelta(text), StreamEvent.FinishReason(finish), StreamEvent.Completed)
    }

    private fun native(vararg calls: Pair<String, String>): (AgentRequest) -> Flow<StreamEvent> = {
        flowOf(
            StreamEvent.Started,
            StreamEvent.ToolCalls(calls.mapIndexed { i, (n, a) -> ToolCallResponse("n${System.nanoTime()}_$i", "function", FunctionCall(n, a)) }),
            StreamEvent.FinishReason("tool_calls"),
            StreamEvent.Completed
        )
    }

    private val goodHtml = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Recipes</title>
          <link rel="stylesheet" href="styles.css">
        </head>
        <body>
          <main>
            <h1>Recipes</h1>
            <input id="search" aria-label="Search recipes">
            <ul id="list"></ul>
          </main>
          <script src="app.js"></script>
        </body>
        </html>
    """.trimIndent()

    private val goodCss = """
        :root { --accent: #6c5ce7; }
        body { font-family: system-ui, sans-serif; margin: 0; }
        main { max-width: 40rem; margin: 0 auto; padding: 1rem; }
        input:focus-visible { outline: 2px solid var(--accent); }
        @media (prefers-reduced-motion: reduce) { * { transition: none !important; } }
    """.trimIndent()

    private val jsPart1 = """
        const recipes = [
          { id: 1, name: 'Dal bhat' },
          { id: 2, name: 'Momo' }
        ];
    """.trimIndent()

    private val jsPart2 = """
        function render(items) {
          const list = document.getElementById('list');
          list.innerHTML = items.map(r => `<li>${'$'}{r.name}</li>`).join('');
        }
        document.getElementById('search').addEventListener('input', e => {
          const q = e.target.value.toLowerCase();
          render(recipes.filter(r => r.name.toLowerCase().includes(q)));
        });
        render(recipes);
    """.trimIndent()

    // --------------------------------------------------------------- tasks

    @Test
    fun `text model builds a three-file site, writing the script in parts`() = runTest {
        val p = Project(tmp.newFolder("site"))
        val model = Model(
            say("I'll write the page and the styles.\n" + call("write_file", "path" to "index.html", "content" to goodHtml) + "\n" +
                call("write_file", "path" to "styles.css", "content" to goodCss)),
            say("Now the script, in two parts.\n" + call("write_file", "path" to "app.js", "content" to jsPart1)),
            say(call("write_file", "path" to "app.js", "append" to "true", "content" to jsPart2)),
            say(call("check_syntax")),
            say("Built the recipe page: index.html, styles.css and app.js with live search.")
        )
        val result = p.run(model, "Build a recipe page with live search", textRequest())

        assertThat(result).isInstanceOf(AgentRunResult.Completed::class.java)
        assertThat(p.read("index.html")).isEqualTo(goodHtml)
        assertThat(p.read("app.js")).contains(jsPart1)
        assertThat(p.read("app.js")).contains("render(recipes);")
        assertThat(p.read("app.js").indexOf(jsPart1)).isLessThan(p.read("app.js").indexOf("function render"))
    }

    @Test
    fun `a write cut off by the output limit never lands and is resent whole`() = runTest {
        val p = Project(tmp.newFolder("cut"))
        val cut = "<tool_call>\n<function=write_file>\n<parameter=path>app.js</parameter>\n<parameter=content>\n" +
            jsPart1.substring(0, jsPart1.length / 2)
        val model = Model(
            say(cut, finish = "length"),
            say(call("write_file", "path" to "app.js", "content" to jsPart1)),
            say("Wrote app.js.")
        )
        val result = p.run(model, "Create app.js with the recipes array", textRequest())

        assertThat(result).isInstanceOf(AgentRunResult.Completed::class.java)
        assertThat(p.writes.map { it.second }).doesNotContain(jsPart1.substring(0, jsPart1.length / 2))
        assertThat(p.read("app.js")).isEqualTo(jsPart1)
        // The model was told why nothing happened.
        assertThat(model.requests[1].messages.last().content).contains("app.js")
    }

    @Test
    fun `an example tool call inside a written README is content, not a call`() = runTest {
        val p = Project(tmp.newFolder("readme"))
        File(p.root, "index.html").writeText(goodHtml)
        val readme = "# Agent format\n\nCalls look like this:\n\n```\n" +
            "<tool_call>\n<function=delete_file>\n<parameter=path>index.html</parameter>\n</function>\n</tool_call>\n```\n\nThat's all."
        val model = Model(
            say(call("write_file", "path" to "README.md", "content" to readme)),
            say("Added the README.")
        )
        p.run(model, "Document the tool-call format in README.md", textRequest())

        assertThat(p.ran).doesNotContain("delete_file")
        assertThat(File(p.root, "index.html").exists()).isTrue()
        assertThat(p.read("README.md")).isEqualTo(readme)
    }

    @Test
    fun `an unreadable call is nudged and the resend runs`() = runTest {
        val p = Project(tmp.newFolder("nudge"))
        val broken = "<tool_call>\n{\"name\": \"write_file\", \"arguments\": {\"path\": \"note.txt\", \"content\": \"say \"hi\" \n</tool_call>"
        val model = Model(
            say(broken),
            say(call("write_file", "path" to "note.txt", "content" to "say \"hi\"")),
            say("Saved the note.")
        )
        val result = p.run(model, "Save a note that says hi", textRequest())

        assertThat(result).isInstanceOf(AgentRunResult.Completed::class.java)
        assertThat(p.read("note.txt")).isEqualTo("say \"hi\"")
    }

    @Test
    fun `the quality gate sends a missing script back and the fix completes the run`() = runTest {
        val p = Project(tmp.newFolder("gate"))
        File(p.root, "styles.css").writeText(goodCss)
        val model = Model(
            say(call("write_file", "path" to "index.html", "content" to goodHtml)),
            say("Done: the page is ready."),
            // The gate reported app.js as missing; the model fixes it.
            { req ->
                val feedback = req.messages.last().content.orEmpty()
                assertThat(feedback).contains("app.js")
                say(call("write_file", "path" to "app.js", "content" to jsPart1 + "\n" + jsPart2))(req)
            },
            say("Added the missing app.js.")
        )
        val result = p.run(model, "Make the recipe page", textRequest())

        assertThat(result).isInstanceOf(AgentRunResult.Completed::class.java)
        assertThat(File(p.root, "app.js").exists()).isTrue()
    }

    @Test
    fun `native model batches reads and edits with a misquoted indent`() = runTest {
        val p = Project(tmp.newFolder("native"))
        File(p.root, "index.html").writeText(goodHtml)
        File(p.root, "styles.css").writeText(goodCss)
        File(p.root, "app.js").writeText(jsPart1)
        val model = Model(
            native("read_file" to """{"path":"index.html","paths":["styles.css","app.js"]}""", "grep" to """{"pattern":"recipes"}"""),
            // Quotes the line with 2 spaces less indentation than the file has.
            native("edit_file" to """{"path":"index.html","old_string":"<h1>Recipes</h1>","new_string":"<h1>Family recipes</h1>"}"""),
            say("Renamed the heading.")
        )
        val result = p.run(model, "Rename the heading to Family recipes", nativeRequest())

        assertThat(result).isInstanceOf(AgentRunResult.Completed::class.java)
        assertThat(p.read("index.html")).contains("<h1>Family recipes</h1>")
        // The batch read returned all three files in one result.
        val readResult = model.requests[1].messages.first { it.role == "tool" }.content.orEmpty()
        assertThat(readResult).contains("styles.css")
        assertThat(readResult).contains("app.js")
    }

    @Test
    fun `a long run on a small window keeps the task and stays inside the budget`() = runTest {
        val p = Project(tmp.newFolder("long"))
        File(p.root, "app.js").writeText(jsPart1)
        val budget = 8_000
        val steps = (1..90).map { i ->
            native("write_file" to """{"path":"part$i.js","content":"${"// filler line for step $i\\n".repeat(60)}"}""")
        }
        val model = Model(*(steps + say("Wrote all 90 parts.")).toTypedArray())
        val result = p.run(model, "Split the app into 90 modules", nativeRequest(budget))

        assertThat(result).isInstanceOf(AgentRunResult.Completed::class.java)
        assertThat(File(p.root, "part90.js").exists()).isTrue()
        for (req in model.requests) {
            val history = req.messages.drop(1)
            assertThat(TokenEstimator.estimate(history)).isAtMost(budget)
            // The task is always there, first.
            assertThat(history.first().content).startsWith("Split the app into 90 modules")
            // Calls and results stay paired.
            history.forEachIndexed { i, m ->
                if (m.role == "tool") {
                    val owner = history.subList(0, i).lastOrNull { it.role != "tool" }
                    assertThat(owner?.toolCalls?.any { it.id == m.toolCallId }).isTrue()
                }
            }
        }
        // Late requests carry the ledger of what the folded steps did.
        assertThat(model.requests.last().messages[1].content).contains("part1.js (write)")
    }
}
