package com.oro.app.data.ai.agent

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.tools.ToolResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.random.Random

class AgentLoopTest {

    private fun call(id: String, name: String, args: String = "{}") =
        ToolCallResponse(id, "function", FunctionCall(name, args))

    private fun text(s: String) = flowOf(StreamEvent.Started, StreamEvent.ContentDelta(s), StreamEvent.Completed)

    private fun tools(vararg calls: ToolCallResponse) =
        flowOf(StreamEvent.Started, StreamEvent.ToolCalls(calls.toList()), StreamEvent.FinishReason("tool_calls"), StreamEvent.Completed)

    /** Model that replays scripted responses and records what it was sent. */
    private class ScriptedModel(vararg responses: () -> Flow<StreamEvent>) : AgentModelClient {
        private val queue = ArrayDeque(responses.toList())
        val requests = mutableListOf<AgentRequest>()
        override suspend fun stream(request: AgentRequest): Flow<StreamEvent> {
            requests += request
            return queue.removeFirst().invoke()
        }
    }

    private class Recorder : AgentLoopListener {
        val statuses = mutableListOf<AgentStatus>()
        val discarded = mutableListOf<String>()
        val finished = mutableListOf<Pair<String, ToolResult>>()
        override fun onStatus(status: AgentStatus) { statuses += status }
        override fun onAttemptDiscarded(reason: String) { discarded += reason }
        override fun onToolFinished(call: ToolCallResponse, result: ToolResult) { finished += call.function.name to result }
    }

    private val sleeps = mutableListOf<Long>()

    private fun loop(model: AgentModelClient, config: AgentLoopConfig = AgentLoopConfig(), runner: AgentToolRunner) =
        AgentLoop(model, runner, config, sleep = { sleeps += it }, random = Random(1))

    private val known = setOf("read_file", "write_file", "edit_file", "ask_user", "grep")

    private suspend fun AgentLoop.go(history: MutableList<ChatRequestMessage>, rec: AgentLoopListener = Recorder()) =
        run(history, known, rec) { h, _ -> AgentRequest(h.toList(), emptyList()) }

    @Test
    fun `loop continues after tool results and ends on a plain answer`() = runTest {
        val model = ScriptedModel(
            { tools(call("1", "read_file", """{"path":"index.html"}""")) },
            { text("Done: updated the title.") }
        )
        val ran = mutableListOf<String>()
        val history = mutableListOf(ChatRequestMessage(role = "user", content = "fix title"))
        val result = loop(model) { c -> ran += c.function.name; ToolResult(true, "<h1>x</h1>") }.go(history)

        assertThat(result).isEqualTo(AgentRunResult.Completed("Done: updated the title."))
        assertThat(ran).containsExactly("read_file")
        // Second request carries the assistant tool_calls message and the paired tool result.
        val second = model.requests[1].messages
        assertThat(second.map { it.role }).containsExactly("user", "assistant", "tool").inOrder()
        assertThat(second[1].toolCalls!!.single().id).isEqualTo("1")
        assertThat(second[2].toolCallId).isEqualTo("1")
        assertThat(history.last().content).isEqualTo("Done: updated the title.")
    }

    @Test
    fun `stream that ends without a completion marker still completes`() = runTest {
        val model = ScriptedModel({ flowOf(StreamEvent.Started, StreamEvent.ContentDelta("Hello")) })
        val result = loop(model) { ToolResult(true, "") }.go(mutableListOf())
        assertThat(result).isEqualTo(AgentRunResult.Completed("Hello"))
    }

    @Test
    fun `rate limit is retried with backoff honouring the server hint`() = runTest {
        val model = ScriptedModel(
            { flowOf(StreamEvent.Error("rate limited", httpCode = 429, retryAfterMs = 19_000, retryable = true)) },
            { text("ok") }
        )
        val rec = Recorder()
        val result = loop(model) { ToolResult(true, "") }.go(mutableListOf(), rec)
        assertThat(result).isEqualTo(AgentRunResult.Completed("ok"))
        assertThat(sleeps).containsExactly(19_000L)
        assertThat(rec.statuses.map { it.kind }).contains(AgentStatus.Kind.RETRYING)
        assertThat(rec.statuses.first { it.kind == AgentStatus.Kind.RETRYING }.label).contains("Rate limited")
    }

    @Test
    fun `non-retryable error surfaces as a failure, not a silent end`() = runTest {
        val model = ScriptedModel({ flowOf(StreamEvent.Error("Invalid API key", httpCode = 401)) })
        val result = loop(model) { ToolResult(true, "") }.go(mutableListOf())
        assertThat(result).isEqualTo(AgentRunResult.Failed("Invalid API key", retryable = false))
    }

    @Test
    fun `retries are bounded`() = runTest {
        val err = { flowOf<StreamEvent>(StreamEvent.Error("503", httpCode = 503, retryable = true)) }
        val model = ScriptedModel(err, err, err)
        val result = loop(model, AgentLoopConfig(maxRetries = 2)) { ToolResult(true, "") }.go(mutableListOf())
        assertThat(result).isInstanceOf(AgentRunResult.Failed::class.java)
        assertThat((result as AgentRunResult.Failed).retryable).isTrue()
        assertThat(sleeps).hasSize(2)
    }

    @Test
    fun `empty replies are retried then reported`() = runTest {
        val empty = { flowOf(StreamEvent.Started, StreamEvent.ReasoningDelta("hmm"), StreamEvent.Completed) }
        val model = ScriptedModel(empty, empty, empty)
        val rec = Recorder()
        val result = loop(model) { ToolResult(true, "") }.go(mutableListOf(), rec)
        assertThat((result as AgentRunResult.Failed).message).contains("empty response")
        assertThat(rec.discarded).hasSize(2)
    }

    @Test
    fun `dropped connection with no output is retried`() = runTest {
        val model = ScriptedModel({ flowOf(StreamEvent.Started) }, { text("recovered") })
        val result = loop(model) { ToolResult(true, "") }.go(mutableListOf())
        assertThat(result).isEqualTo(AgentRunResult.Completed("recovered"))
    }

    @Test
    fun `stalled stream trips the idle watchdog and is retried`() = runTest {
        val model = ScriptedModel(
            { flow { emit(StreamEvent.Started); awaitCancellation() } },
            { text("after stall") }
        )
        val result = loop(model, AgentLoopConfig(idleTimeoutMs = 5_000)) { ToolResult(true, "") }.go(mutableListOf())
        assertThat(result).isEqualTo(AgentRunResult.Completed("after stall"))
    }

    @Test
    fun `text tool calls are executed and results fed back as a user message`() = runTest {
        val model = ScriptedModel(
            { text("Let me look.\n<tool_call>{\"name\":\"read_file\",\"arguments\":{\"path\":\"a.html\"}}</tool_call>") },
            { text("It's fine.") }
        )
        var visible = ""
        val rec = object : AgentLoopListener {
            override fun onTurnComplete(visibleText: String, toolCalls: List<ToolCallResponse>) {
                if (toolCalls.isNotEmpty()) visible = visibleText
            }
        }
        val result = loop(model) { ToolResult(true, "<p>hi</p>") }.go(mutableListOf(), rec)
        assertThat(result).isEqualTo(AgentRunResult.Completed("It's fine."))
        assertThat(visible).isEqualTo("Let me look.")
        val fed = model.requests[1].messages.last()
        assertThat(fed.role).isEqualTo("user")
        assertThat(fed.content).contains("<tool_result name=\"read_file\">")
        assertThat(fed.content).contains("<p>hi</p>")
    }

    @Test
    fun `ask_user suspends the run until answered`() = runTest {
        val answer = CompletableDeferred<String>()
        val model = ScriptedModel(
            { tools(call("q", "ask_user", """{"question":"Which colour?","options":["Blue","Green"]}""")) },
            { text("Using green.") }
        )
        val rec = Recorder()
        val job = async {
            loop(model) { c ->
                if (c.function.name == "ask_user") ToolResult(true, "User answered: ${answer.await()}") else ToolResult(true, "")
            }.go(mutableListOf(), rec)
        }
        testScheduler.advanceUntilIdle()
        assertThat(job.isActive).isTrue()
        assertThat(rec.statuses.last().kind).isEqualTo(AgentStatus.Kind.WAITING_FOR_USER)
        assertThat(rec.statuses.last().label).isEqualTo("Waiting for your answer")

        answer.complete("Green")
        assertThat(job.await()).isEqualTo(AgentRunResult.Completed("Using green."))
        assertThat(model.requests[1].messages.last().content).isEqualTo("User answered: Green")
    }

    @Test
    fun `iteration guard stops runaway loops`() = runTest {
        val model = object : AgentModelClient {
            var n = 0
            override suspend fun stream(request: AgentRequest) = tools(call("c${n++}", "grep", """{"pattern":"x$n"}"""))
        }
        val result = loop(model, AgentLoopConfig(maxIterations = 3)) { ToolResult(true, "nothing") }.go(mutableListOf())
        assertThat(result).isEqualTo(AgentRunResult.IterationLimit(3))
    }

    @Test
    fun `context overflow re-prepares at higher pressure`() = runTest {
        val model = ScriptedModel(
            { flowOf(StreamEvent.Error("This model's maximum context length is 131072 tokens", httpCode = 400)) },
            { text("fits now") }
        )
        val pressures = mutableListOf<Int>()
        val result = loop(model) { ToolResult(true, "") }.run(mutableListOf(), known, Recorder()) { h, p ->
            pressures += p; AgentRequest(h, null)
        }
        assertThat(result).isEqualTo(AgentRunResult.Completed("fits now"))
        assertThat(pressures).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `consecutive read-only calls all run and keep their order`() = runTest {
        val model = ScriptedModel(
            { tools(call("a", "read_file", """{"path":"a"}"""), call("b", "grep", """{"pattern":"b"}"""), call("c", "write_file", """{"path":"c","content":""}""")) },
            { text("ok") }
        )
        val rec = Recorder()
        loop(model) { c -> ToolResult(true, "out-${c.id}") }.go(mutableListOf(), rec)
        val toolMsgs = model.requests[1].messages.filter { it.role == "tool" }
        assertThat(toolMsgs.map { it.toolCallId }).containsExactly("a", "b", "c").inOrder()
        assertThat(toolMsgs.map { it.content }).containsExactly("out-a", "out-b", "out-c").inOrder()
    }

    @Test
    fun `think blocks are not sent back as history`() {
        assertThat(AgentLoop.stripThinking("<think>secret plan</think>Answer")).isEqualTo("Answer")
    }

    // ------------------------------------------------------------- no cap, stuck detection

    @Test
    fun `runs have no step cap by default`() = runTest {
        val model = object : AgentModelClient {
            var n = 0
            override suspend fun stream(request: AgentRequest): Flow<StreamEvent> =
                if (n < 80) tools(call("c${n++}", "grep", """{"pattern":"p$n"}""")) else text("All done.")
        }
        var ran = 0
        val result = loop(model) { ran++; ToolResult(true, "match $ran") }.go(mutableListOf())
        assertThat(result).isEqualTo(AgentRunResult.Completed("All done."))
        assertThat(ran).isEqualTo(80)
    }

    @Test
    fun `a streak of failures earns a nudge and then ends the run`() = runTest {
        val model = object : AgentModelClient {
            var n = 0
            val sent = mutableListOf<AgentRequest>()
            override suspend fun stream(request: AgentRequest): Flow<StreamEvent> {
                sent += request
                return tools(call("c${n++}", "edit_file", """{"path":"a","old_string":"x$n","new_string":"y"}"""))
            }
        }
        val result = loop(model) { ToolResult(false, "", "old_string not found") }.go(mutableListOf())
        assertThat(result).isInstanceOf(AgentRunResult.Failed::class.java)
        assertThat((result as AgentRunResult.Failed).message).contains("stuck")
        assertThat(result.message).contains("old_string not found")
        // The nudge reached the model before the run gave up.
        val toolTexts = model.sent.last().messages.filter { it.role == "tool" }.mapNotNull { it.content }
        assertThat(toolTexts.any { it.contains("tool rounds all failed") }).isTrue()
        assertThat(model.n).isEqualTo(AgentLoopConfig().stuckStopAfter)
    }

    @Test
    fun `the same call with the same result over and over ends the run`() = runTest {
        val model = object : AgentModelClient {
            var n = 0
            override suspend fun stream(request: AgentRequest): Flow<StreamEvent> =
                tools(call("c${n++}", "read_file", """{"path":"index.html"}"""))
        }
        val result = loop(model) { ToolResult(true, "<h1>same</h1>") }.go(mutableListOf())
        assertThat((result as AgentRunResult.Failed).message).contains("repeating")
        assertThat(model.n).isEqualTo(AgentLoopConfig().repeatStopAfter)
    }

    // --------------------------------------------------------------- truncation and early stop

    @Test
    fun `a text call cut off by the output limit is not run and is asked for again`() = runTest {
        val cut = "<tool_call>\n<function=write_file>\n<parameter=path>index.html</parameter>\n<parameter=content>\n<html><body><main>"
        val whole = "<tool_call>\n<function=write_file>\n<parameter=path>index.html</parameter>\n" +
            "<parameter=content>\n<html></html>\n</parameter>\n</function>\n</tool_call>"
        val model = ScriptedModel(
            { flowOf(StreamEvent.Started, StreamEvent.ContentDelta(cut), StreamEvent.FinishReason("length"), StreamEvent.Completed) },
            { text(whole) },
            { text("Done.") }
        )
        val written = mutableListOf<String>()
        val result = loop(model) { c -> written += c.function.arguments; ToolResult(true, "ok") }.go(mutableListOf())
        assertThat(result).isEqualTo(AgentRunResult.Completed("Done."))
        assertThat(written).hasSize(1)
        assertThat(written.single()).contains("<html></html>")
        val nudge = model.requests[1].messages.last()
        assertThat(nudge.role).isEqualTo("user")
        assertThat(nudge.content).contains("output limit")
        assertThat(nudge.content).contains("append=true")
        // The half file is not kept in history for the model to imitate.
        assertThat(model.requests[1].messages.none { it.content?.contains("<main>") == true }).isTrue()
    }

    @Test
    fun `a native call whose arguments were cut off is not run`() = runTest {
        val truncated = call("w", "write_file", """{"path":"index.html","content":"<html><bo""")
        val model = ScriptedModel(
            { flowOf(StreamEvent.Started, StreamEvent.ToolCalls(listOf(truncated)), StreamEvent.FinishReason("length"), StreamEvent.Completed) },
            { text("Sorry, done now.") }
        )
        var ran = 0
        val result = loop(model) { ran++; ToolResult(true, "") }.go(mutableListOf())
        assertThat(result).isEqualTo(AgentRunResult.Completed("Sorry, done now."))
        assertThat(ran).isEqualTo(0)
        assertThat(model.requests[1].messages.last().content).contains("write_file")
        assertThat(model.requests[1].messages.none { it.toolCalls != null }).isTrue()
    }

    @Test
    fun `text mode stops reading when the model invents a tool result`() = runTest {
        val callText = "<tool_call>\n<function=read_file>\n<parameter=path>a.html</parameter>\n</function>\n</tool_call>"
        val model = ScriptedModel(
            {
                flowOf(
                    StreamEvent.Started,
                    StreamEvent.ContentDelta("Reading.\n$callText\n<tool_res"),
                    StreamEvent.ContentDelta("ult name=\"read_file\">invented</tool_result>\nGreat, all done!"),
                    StreamEvent.Completed
                )
            },
            { text("Real answer.") }
        )
        val result = loop(model) { ToolResult(true, "<p>real</p>") }.go(mutableListOf())
        assertThat(result).isEqualTo(AgentRunResult.Completed("Real answer."))
        val history = model.requests[1].messages
        assertThat(history.none { it.content?.contains("invented") == true }).isTrue()
        assertThat(history.last().content).contains("<p>real</p>")
    }

    @Test
    fun `tool calls that stay unreadable end the run honestly`() = runTest {
        val broken = { text("Fixing it.\n<tool_call>{\"name\": \"format_disk\", \"arguments\": {}}</tool_call>") }
        val model = ScriptedModel(broken, broken, broken)
        var ran = 0
        val result = loop(model) { ran++; ToolResult(true, "") }.go(mutableListOf())
        assertThat(result).isInstanceOf(AgentRunResult.Failed::class.java)
        assertThat((result as AgentRunResult.Failed).message).contains("could not read")
        assertThat(ran).isEqualTo(0)
        assertThat(model.requests).hasSize(3)
    }
}
