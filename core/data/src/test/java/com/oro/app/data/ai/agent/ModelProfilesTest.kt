package com.oro.app.data.ai.agent

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.providers.ModelIds
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.repository.AiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ModelProfilesTest {

    // ------------------------------------------------------------ selection

    @Test
    fun `mercury gets its profile on every route that really serves it`() {
        assertThat(ModelProfiles.forIds(AiProvider.MERCURY, "mercury:mercury-3")).isEqualTo(ModelProfiles.MERCURY)
        assertThat(ModelProfiles.forIds(AiProvider.INCEPTION, "mercury-2.5")).isEqualTo(ModelProfiles.MERCURY)
        assertThat(ModelProfiles.forIds(AiProvider.KILOCODE, "inception/mercury-3")).isEqualTo(ModelProfiles.MERCURY)
        assertThat(ModelProfiles.forIds(AiProvider.CUSTOM, ModelIds.of("p1", "mercury-3"))).isEqualTo(ModelProfiles.MERCURY)
        // Ptero answers "mercury" ids with another model: not Mercury's profile.
        assertThat(ModelProfiles.forIds(AiProvider.PTERO, "ptero:mercury-2.5:free")).isEqualTo(ModelProfiles.COMMUNITY)
    }

    @Test
    fun `fast hosts, frontier families, guest bridges and the rest`() {
        assertThat(ModelProfiles.forIds(AiProvider.GROQ, "openai/gpt-oss-120b")).isEqualTo(ModelProfiles.FAST)
        assertThat(ModelProfiles.forIds(AiProvider.CEREBRAS, "gpt-oss-120b")).isEqualTo(ModelProfiles.FAST)
        assertThat(ModelProfiles.forIds(AiProvider.CUSTOM, ModelIds.of("anth", "claude-opus-5-5"))).isEqualTo(ModelProfiles.FRONTIER)
        assertThat(ModelProfiles.forIds(AiProvider.OPENROUTER, "openai/gpt-5.5")).isEqualTo(ModelProfiles.FRONTIER)
        assertThat(ModelProfiles.forIds(AiProvider.GEMINI, "models/gemini-3-pro")).isEqualTo(ModelProfiles.FRONTIER)
        assertThat(ModelProfiles.forIds(AiProvider.OPENROUTER, "deepseek/deepseek-v4-pro")).isEqualTo(ModelProfiles.FRONTIER)
        // A frontier model behind a guest bridge lives by the bridge's limits.
        assertThat(ModelProfiles.forIds(AiProvider.UNIKEY, "unikey:claude-opus-4-8")).isEqualTo(ModelProfiles.COMMUNITY)
        assertThat(ModelProfiles.forIds(AiProvider.K2_HORIZON, "k2:IFM/K2-Horizon-375B-A23B")).isEqualTo(ModelProfiles.COMMUNITY)
        assertThat(ModelProfiles.forIds(AiProvider.KILOCODE, "nvidia/nemotron-3-ultra-550b-a55b:free")).isEqualTo(ModelProfiles.DEFAULT)
        assertThat(ModelProfiles.forIds(AiProvider.OPENROUTER, "openai/gpt-4o-mini")).isEqualTo(ModelProfiles.DEFAULT)
    }

    @Test
    fun `the default profile is the harness as it was`() {
        val d = ModelProfiles.DEFAULT
        assertThat(d.wholeFiles).isFalse()
        assertThat(d.qualityPasses).isEqualTo(AgentLoopConfig().maxQualityPasses)
        assertThat(d.idleTimeoutMs).isEqualTo(AgentLoopConfig().idleTimeoutMs)
        assertThat(d.previewEachRound).isFalse()
        assertThat(d.maxOutputTokens).isNull()
        assertThat(d.contextBudgetTokens).isNull()
        assertThat(d.inlineProjectChars).isEqualTo(0)
        assertThat(d.promptSection).isEmpty()
    }

    @Test
    fun `mercury works in whole files with the app checking between replies`() {
        val m = ModelProfiles.MERCURY
        assertThat(m.wholeFiles).isTrue()
        assertThat(m.previewEachRound).isTrue()
        assertThat(m.qualityPasses).isGreaterThan(ModelProfiles.DEFAULT.qualityPasses)
        assertThat(m.idleTimeoutMs).isLessThan(ModelProfiles.DEFAULT.idleTimeoutMs)
        assertThat(m.contextBudgetTokens).isNotNull()
        assertThat(m.inlineProjectChars).isGreaterThan(0)
        assertThat(m.promptSection).contains("ONE write_file call")
    }

    // ------------------------------------------------------------ the prompt

    @Test
    fun `a profile's section and whole-file rule reach the system prompt, and only then`() {
        val tools = AgentTools.forMainAgent()
        val plain = AiRepository.systemMessage("ctx", nativeTools = false, toolList = tools).content!!
        val mercury = AiRepository.systemMessage("ctx", nativeTools = false, toolList = tools, profile = ModelProfiles.MERCURY).content!!
        assertThat(plain).contains("write_file calls with append=true")
        assertThat(plain).doesNotContain("# Working at your speed")
        assertThat(mercury).contains("# Working at your speed")
        assertThat(mercury).contains("Write each file whole in one write_file call, however long.")
        assertThat(mercury).doesNotContain("add the rest with further")
        // The section sits ahead of the per-run context, and the prompt is stable call to call.
        assertThat(mercury.indexOf("# Working at your speed")).isLessThan(mercury.indexOf("# Project context"))
        assertThat(AiRepository.systemMessage("ctx", nativeTools = false, toolList = tools, profile = ModelProfiles.MERCURY).content)
            .isEqualTo(mercury)
        // Chat mode carries no agent section.
        assertThat(AiRepository.systemMessage("ctx", agentMode = false, profile = ModelProfiles.MERCURY).content)
            .doesNotContain("# Working at your speed")
    }

    // ------------------------------------------------------------ the round hook

    private fun call(id: String, name: String, args: String = "{}") = ToolCallResponse(id, "function", FunctionCall(name, args))

    private class Scripted(vararg responses: () -> Flow<StreamEvent>) : AgentModelClient {
        private val queue = ArrayDeque(responses.toList())
        val requests = mutableListOf<AgentRequest>()
        override suspend fun stream(request: AgentRequest): Flow<StreamEvent> {
            requests += request
            return queue.removeFirst().invoke()
        }
    }

    private fun text(s: String) = flowOf(StreamEvent.Started, StreamEvent.ContentDelta(s), StreamEvent.Completed)

    @Test
    fun `a round note follows native tool results as one message`() = runTest {
        val model = Scripted(
            { flowOf(StreamEvent.Started, StreamEvent.ToolCalls(listOf(call("1", "write_file", """{"path":"index.html","content":"<p>"}"""))), StreamEvent.Completed) },
            { text("Done.") }
        )
        val seen = mutableListOf<List<String>>()
        val loop = AgentLoop(model, { ToolResult(true, "written") }, AgentLoopConfig(roundNote = { calls, results ->
            seen += calls.map { it.function.name }
            if (results.all { it.success }) "[Oro: index.html: no layout problems measured.]" else null
        }), sleep = {})
        val history = mutableListOf(ChatRequestMessage(role = "user", content = "make a page"))
        loop.run(history, setOf("write_file"), object : AgentLoopListener {}) { h, _ -> AgentRequest(h.toList(), emptyList()) }
        assertThat(seen).containsExactly(listOf("write_file"))
        val second = model.requests[1].messages
        assertThat(second.map { it.role }).containsExactly("user", "assistant", "tool", "user").inOrder()
        assertThat(second.last().content).isEqualTo("[Oro: index.html: no layout problems measured.]")
    }

    @Test
    fun `a round note rides inside text-protocol results, and a failing hook is ignored`() = runTest {
        val reply = "<tool_call>\n<function=write_file>\n<parameter=path>a.html</parameter>\n<parameter=content>\n<p>Hi</p>\n</parameter>\n</function>\n</tool_call>"
        val model = Scripted({ text(reply) }, { text(reply) }, { text("Done.") })
        var calls = 0
        val loop = AgentLoop(model, { ToolResult(true, "written") }, AgentLoopConfig(roundNote = { _, _ ->
            calls++
            if (calls == 1) "[Oro: a.html: no layout problems measured.]" else error("renderer gone")
        }), sleep = {})
        val history = mutableListOf(ChatRequestMessage(role = "user", content = "make a page"))
        val result = loop.run(history, setOf("write_file"), object : AgentLoopListener {}) { h, _ -> AgentRequest(h.toList(), null) }
        assertThat(result).isInstanceOf(AgentRunResult.Completed::class.java)
        val firstResults = model.requests[1].messages.last().content!!
        assertThat(firstResults).startsWith("Tool results:")
        assertThat(firstResults).contains("[Oro: a.html: no layout problems measured.]")
        val secondResults = model.requests[2].messages.last().content!!
        assertThat(secondResults).doesNotContain("[Oro:")
    }
}
