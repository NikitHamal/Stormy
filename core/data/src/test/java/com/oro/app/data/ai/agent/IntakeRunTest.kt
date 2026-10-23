package com.oro.app.data.ai.agent

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.craft.QualityGate
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.JsonParserUtils
import com.oro.app.data.ai.tools.ProjectTools
import com.oro.app.data.ai.tools.ToolAliases
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.ai.tools.UserQuestion
import com.oro.app.data.ai.tools.UserQuestions
import com.oro.app.data.ai.tools.WorkspaceTools
import com.oro.app.data.repository.AiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/**
 * The intake end to end, the way EditorViewModel runs it: the brief check adds
 * its note to a vague first turn, a scripted text-protocol model asks one
 * card, a scripted user answers (or taps "You decide"), and the model builds
 * with what it was told -- through the real AgentLoop, parser and file tools.
 */
class IntakeRunTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class Model(vararg steps: (AgentRequest) -> Flow<StreamEvent>) : AgentModelClient {
        private val queue = ArrayDeque(steps.toList())
        val requests = mutableListOf<AgentRequest>()
        override suspend fun stream(request: AgentRequest): Flow<StreamEvent> {
            requests += request
            return queue.removeFirstOrNull()?.invoke(request)
                ?: flowOf(StreamEvent.Started, StreamEvent.ContentDelta("(script ended)"), StreamEvent.Completed)
        }
    }

    private fun say(text: String): (AgentRequest) -> Flow<StreamEvent> = {
        flowOf(StreamEvent.Started, StreamEvent.ContentDelta(text), StreamEvent.FinishReason("stop"), StreamEvent.Completed)
    }

    private fun call(name: String, vararg params: Pair<String, String>) = buildString {
        append("<tool_call>\n<function=").append(name).append(">\n")
        params.forEach { (k, v) ->
            append("<parameter=").append(k).append(">")
            if ('\n' in v) append('\n').append(v).append('\n') else append(v)
            append("</parameter>\n")
        }
        append("</function>\n</tool_call>")
    }

    private val known = AgentTools.forMainAgent().map { it.function.name }.toSet()

    private val request = textRequest()

    private fun textRequest(): suspend (List<ChatRequestMessage>, Int) -> AgentRequest {
        val system = AiRepository.systemMessage("Project: cafe", agentMode = true, nativeTools = false, toolList = AgentTools.forMainAgent())
        return { h, _ -> AgentRequest(listOf(system) + HistoryPruner.prune(h, 200_000), null) }
    }

    /** The user's turn as EditorViewModel sends it: the request, plus the brief check's note when vague. */
    private fun firstTurn(task: String): MutableList<ChatRequestMessage> {
        val note = BriefCheck.note(BriefCheck.check(task, projectFresh = true), ask = true, textProtocol = true)
        return mutableListOf(ChatRequestMessage(role = "user", content = if (note == null) task else "$task\n\n$note"))
    }

    private class Project(val root: File, val answer: (List<UserQuestion>) -> List<String?>?) {
        val ws = WorkspaceTools(root)
        val touched = linkedSetOf<String>()
        val asked = mutableListOf<List<UserQuestion>>()
        val runner = AgentToolRunner { c ->
            val name = ToolAliases.canonicalName(c.function.name)
            val result = if (name == "ask_user") {
                // As ToolExecutor.executeAskUser does with the card's answers.
                val questions = UserQuestions.parse(JsonParserUtils.parseToJsonObject(c.function.arguments)!!)
                asked += questions
                val answers = answer(questions)
                ToolResult(true, if (answers == null) UserQuestions.DECIDE_RESULT else UserQuestions.formatAnswers(questions, answers))
            } else {
                ProjectTools.execute(root, ws, c) ?: ToolResult(false, "", "$name is not available in this test.")
            }
            if (result.success) touched += QualityGate.touchedPaths(c)
            result
        }
    }

    private val cardCall = call(
        "ask_user",
        "questions" to """[{"header":"Name","question":"What's the cafe called?","options":[{"label":"I'll type it"},{"label":"Make one up","recommended":true}]},""" +
            """{"header":"Focus","question":"What should visitors do first?","options":[{"label":"See the menu","recommended":true},{"label":"Book a table"}]},""" +
            """{"header":"Look","question":"Which feel fits?","options":[{"label":"Warm and cozy","recommended":true},{"label":"Clean and minimal"}]}]"""
    )

    private fun page(name: String, focus: String) =
        "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
            "<title>$name</title>\n</head>\n<body>\n<h1>$name</h1>\n<p>$focus</p>\n</body>\n</html>"

    @Test
    fun `a vague request asks one card, then builds with the answers`() = runTest {
        val project = Project(tmp.newFolder("cafe")) { listOf("Brew Haven", "Book a table", "Warm and cozy") }
        val model = Model(
            say("Three quick things so it feels like your cafe.\n$cardCall"),
            { req ->
                // The answers are in the turn the model builds from.
                val last = req.messages.last().content.orEmpty()
                check("Brew Haven" in last && "Book a table" in last)
                say(call("write_file", "path" to "index.html", "content" to page("Brew Haven", "Book a table")))(req)
            },
            say("Built index.html for Brew Haven with table booking up front, in a warm and cozy style.")
        )
        val history = firstTurn("make me a website for my cafe")
        val result = AgentLoop(model, project.runner, AgentLoopConfig(), sleep = {}, random = Random(1))
            .run(history, known, object : AgentLoopListener {}, { QualityGate.review(project.root, project.touched) }, request)

        assertThat(result).isInstanceOf(AgentRunResult.Completed::class.java)
        // The note reached the model on the first turn.
        assertThat(model.requests[0].messages.last().content).contains("ONE ask_user call")
        assertThat(project.asked.single().map { it.header }).containsExactly("Name", "Focus", "Look").inOrder()
        assertThat(project.asked.single().all { q -> q.options.count { it.recommended } == 1 }).isTrue()
        assertThat(File(project.root, "index.html").readText()).contains("<h1>Brew Haven</h1>")
    }

    @Test
    fun `you decide hands the choices back and the model builds without asking again`() = runTest {
        val project = Project(tmp.newFolder("decide")) { null }
        val model = Model(
            say(cardCall),
            { req ->
                check(UserQuestions.DECIDE_PREFIX in req.messages.last().content.orEmpty())
                say(call("write_file", "path" to "index.html", "content" to page("Ember and Oak", "See the menu")))(req)
            },
            say("Built index.html. I chose: name Ember and Oak, menu first, warm and cozy.")
        )
        val result = AgentLoop(model, project.runner, AgentLoopConfig(), sleep = {}, random = Random(2))
            .run(firstTurn("make me a website for my cafe"), known, object : AgentLoopListener {}, null, request)
        assertThat(result).isInstanceOf(AgentRunResult.Completed::class.java)
        assertThat(project.asked).hasSize(1)
        assertThat(File(project.root, "index.html").readText()).contains("Ember and Oak")
    }

    @Test
    fun `questions asked as prose become the card through the final check`() = runTest {
        val project = Project(tmp.newFolder("prose")) { error("not used: the card comes from the final check") }
        var shown: List<UserQuestion>? = null
        val model = Model(
            say("Before I build:\n1. What's the cafe called?\n2. Which look fits best (warm and cozy, clean and minimal)?"),
            { req ->
                check("Brew Haven" in req.messages.last().content.orEmpty())
                say(call("write_file", "path" to "index.html", "content" to page("Brew Haven", "Warm and cozy")))(req)
            },
            say("Built index.html for Brew Haven.")
        )
        val history = firstTurn("build a website for my cafe")
        var armed = true
        // What EditorViewModel's final check does when the intake is armed and nothing was written yet.
        val finalCheck: suspend () -> String? = {
            val asked = if (armed && project.touched.isEmpty()) {
                val questions = UserQuestions.fromProse(history.last { it.role == "assistant" }.content.orEmpty())
                if (questions.isEmpty()) null else {
                    shown = questions
                    UserQuestions.formatAnswers(questions, listOf("Brew Haven", "Warm and cozy")) +
                        "\nBuild it now with these answers; don't ask again."
                }
            } else null
            armed = false
            asked ?: QualityGate.review(project.root, project.touched)
        }
        val result = AgentLoop(model, project.runner, AgentLoopConfig(maxQualityPasses = 3), sleep = {}, random = Random(3))
            .run(history, known, object : AgentLoopListener {}, finalCheck, request)
        assertThat(result).isInstanceOf(AgentRunResult.Completed::class.java)
        assertThat(shown!!.map { it.question }).containsExactly("What's the cafe called?", "Which look fits best?").inOrder()
        assertThat(shown!![1].options).hasSize(2)
        assertThat(File(project.root, "index.html").readText()).contains("Brew Haven")
    }

    @Test
    fun `a specific request goes straight to building with no note`() {
        val task = "Build a website for Brew Haven, a cozy cafe, with a menu, gallery and table bookings"
        assertThat(firstTurn(task).single().content).isEqualTo(task)
    }
}
