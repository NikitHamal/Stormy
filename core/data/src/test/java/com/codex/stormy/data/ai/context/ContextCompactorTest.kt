package com.codex.stormy.data.ai.context

import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.AiProvider
import com.codex.stormy.data.ai.ChatRequestMessage
import com.codex.stormy.data.ai.Tool
import com.codex.stormy.data.ai.FunctionDefinition
import com.codex.stormy.data.local.entity.MessageRole
import com.codex.stormy.data.local.entity.MessageStatus
import com.codex.stormy.domain.model.ChatMessage
import io.mockk.mockk
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test

class ContextCompactorTest {

    // ContextWindowManager takes an AiRepository it does not use for the
    // estimation paths exercised here; mocked so the policy stays testable
    // without a provider.
    private val manager = ContextWindowManager(mockk(relaxed = true))
    private val compactor = ContextCompactor(manager)

    private fun message(text: String, user: Boolean = true) = ChatMessage(
        id = text.hashCode().toString(),
        projectId = "p",
        role = if (user) MessageRole.USER else MessageRole.ASSISTANT,
        content = text,
        timestamp = 0L,
        status = MessageStatus.SENT,
        codeChanges = null
    )

    private fun conversation(count: Int, charsEach: Int = 100) =
        (1..count).map { message("m$it " + "x".repeat(charsEach), user = it % 2 == 1) }

    @Test
    fun `a short conversation has nothing to compact`() {
        val partition = compactor.partition(conversation(3))
        assertThat(partition.canCompact).isFalse()
        assertThat(partition.recent).hasSize(3)
    }

    @Test
    fun `a long conversation splits into older and recent`() {
        val partition = compactor.partition(conversation(200, charsEach = 400))
        assertThat(partition.canCompact).isTrue()
        assertThat(partition.older).isNotEmpty()
        assertThat(partition.recent).isNotEmpty()
        // Nothing is lost in the split.
        assertThat(partition.older.size + partition.recent.size).isEqualTo(200)
    }

    @Test
    fun `recent is selected by token budget, not message count`() {
        // The same number of messages, very different sizes. A fixed count
        // would keep the same number of each; a token budget keeps far fewer of
        // the large ones, which is the point.
        val small = compactor.partition(conversation(100, charsEach = 50)).recent.size
        val large = compactor.partition(conversation(100, charsEach = 4_000)).recent.size
        assertThat(large).isLessThan(small)
    }

    @Test
    fun `a minimum number of recent turns survives one enormous message`() {
        val messages = conversation(3) + message("y".repeat(200_000), user = false) +
            conversation(3)
        val partition = compactor.partition(messages)
        // Without the floor, one huge message eats the whole budget and the
        // model is left with no recent exchange at all.
        assertThat(partition.recent.size).isAtLeast(ContextCompactor.MIN_RECENT_MESSAGES)
    }

    @Test
    fun `the summary request carries OpenCode's system prompt verbatim`() {
        val request = compactor.buildSummaryRequest(conversation(50))
        val system = request.first { it.role == "system" }.content
        assertThat(system).isEqualTo(ContextCompactor.SYSTEM_PROMPT)
        assertThat(system).contains("anchored context summarization assistant")
    }

    @Test
    fun `the user prompt carries the exact output template`() {
        val user = compactor.buildSummaryRequest(conversation(50)).last().content.orEmpty()
        // The fixed skeleton is what makes the result usable as stable context.
        assertThat(user).contains("## Goal")
        assertThat(user).contains("## Constraints & Preferences")
        assertThat(user).contains("## Relevant Files")
        assertThat(user).contains("Keep every section, even when empty.")
    }

    @Test
    fun `a previous summary is passed back for anchoring`() {
        val user = compactor
            .buildSummaryRequest(conversation(50), previousSummary = "## Goal\n- Build a site")
            .last().content.orEmpty()
        // Anchoring is what stops repeated compaction from summarising a
        // summary and losing the early constraints.
        assertThat(user).contains("<previous-summary>")
        assertThat(user).contains("Build a site")
    }

    @Test
    fun `no previous-summary block when there is no previous summary`() {
        val user = compactor.buildSummaryRequest(conversation(50)).last().content.orEmpty()
        assertThat(user).doesNotContain("<previous-summary>")
    }

    @Test
    fun `individual messages are capped before reaching the summariser`() {
        val huge = listOf(message("z".repeat(100_000)))
        val user = compactor.buildSummaryRequest(huge + conversation(40)).last().content.orEmpty()
        // Otherwise a file dump overflows the very window being compacted.
        assertThat(user.count { it == 'z' }).isAtMost(ContextCompactor.TOOL_OUTPUT_MAX_CHARS)
    }

    @Test
    fun `applying a summary reduces tokens and keeps the recent turns`() {
        val messages = conversation(120, charsEach = 500)
        val applied = compactor.applySummary(messages, "## Goal\n- Ship the thing")
        assertThat(applied).isNotNull()

        val (compacted, result) = applied!!
        assertThat(result.tokensAfter).isLessThan(result.tokensBefore)
        assertThat(result.tokensSaved).isGreaterThan(0)
        assertThat(result.compactedCount).isGreaterThan(0)
        // The summary is a system message, so the model treats it as
        // established context rather than something it said.
        assertThat(compacted.first().role).isEqualTo("system")
        assertThat(compacted.first().content).contains("Ship the thing")
    }

    @Test
    fun `an empty summary is refused rather than applied`() {
        // A half-applied compaction that dropped messages without producing a
        // summary would lose work irrecoverably.
        assertThat(compactor.applySummary(conversation(120), "   ")).isNull()
    }

    @Test
    fun `nothing to compact yields null rather than an empty result`() {
        assertThat(compactor.applySummary(conversation(2), "summary")).isNull()
    }

    @Test
    fun `a prior summary can be recovered for the next anchoring pass`() {
        val applied = compactor.applySummary(conversation(120, charsEach = 500), "## Goal\n- Ship")
        val (compacted, _) = applied!!
        assertThat(compactor.extractPreviousSummary(compacted)).contains("Ship")
    }

    @Test
    fun `extracting a summary from an uncompacted conversation yields null`() {
        val plain = listOf(ChatRequestMessage(role = "system", content = "You are helpful"))
        assertThat(compactor.extractPreviousSummary(plain)).isNull()
    }
}

class ContextBreakdownTest {

    private val manager = ContextWindowManager(mockk(relaxed = true))
    private val builder = ContextBreakdownBuilder(manager)

    private val model = AiModel(
        id = "test-model",
        name = "Test",
        provider = AiProvider.POLLINATIONS,
        contextLength = 32_000
    )

    private fun tool(name: String) = Tool(
        function = FunctionDefinition(
            name = name,
            description = "A tool that does $name",
            parameters = buildJsonObject { put("type", JsonPrimitive("object")) }
        )
    )

    @Test
    fun `segments add up to the used total`() {
        val breakdown = builder.build(
            systemPrompt = "You are a coding assistant.".repeat(20),
            tools = listOf(tool("read_file"), tool("write_file")),
            messages = listOf(ChatRequestMessage(role = "user", content = "hello".repeat(100))),
            maxTokens = 32_000
        )
        assertThat(breakdown.segments.sumOf { it.tokens }).isEqualTo(breakdown.usedTokens)
    }

    @Test
    fun `the system message is not counted twice`() {
        val system = "You are a coding assistant.".repeat(50)
        // The system prompt also appears in the message list, so counting both
        // would double it and overstate usage.
        val breakdown = builder.build(
            systemPrompt = system,
            tools = null,
            messages = listOf(
                ChatRequestMessage(role = "system", content = system),
                ChatRequestMessage(role = "user", content = "hi")
            ),
            maxTokens = 32_000
        )
        val systemSegment = breakdown.segments.first { it.label == "System prompt" }
        assertThat(breakdown.usedTokens).isLessThan(systemSegment.tokens * 2)
    }

    @Test
    fun `only the conversation is marked compactable`() {
        val breakdown = builder.build(
            systemPrompt = "sys",
            tools = listOf(tool("a")),
            messages = listOf(ChatRequestMessage(role = "user", content = "x".repeat(20_000))),
            maxTokens = 32_000
        )
        // Tool definitions and the system prompt cannot be summarised away;
        // offering to compact them would be a button that does nothing.
        assertThat(breakdown.segments.filter { it.compactable }.map { it.label })
            .containsExactly("Conversation")
    }

    @Test
    fun `compaction is not offered when there is too little to reclaim`() {
        val breakdown = builder.build(
            systemPrompt = "sys",
            tools = null,
            messages = listOf(ChatRequestMessage(role = "user", content = "short")),
            maxTokens = 32_000
        )
        assertThat(breakdown.canCompact).isFalse()
    }

    @Test
    fun `usage level escalates with fill`() {
        fun levelAt(chars: Int) = builder.build(
            systemPrompt = null,
            tools = null,
            messages = listOf(ChatRequestMessage(role = "user", content = "x".repeat(chars))),
            maxTokens = 1_000
        ).level

        assertThat(levelAt(400)).isEqualTo(ContextUsageLevel.LOW)
        assertThat(levelAt(4_000)).isEqualTo(ContextUsageLevel.CRITICAL)
    }

    @Test
    fun `fraction is clamped when the window is overflowed`() {
        val breakdown = builder.build(
            systemPrompt = null,
            tools = null,
            messages = listOf(ChatRequestMessage(role = "user", content = "x".repeat(100_000))),
            maxTokens = 1_000
        )
        assertThat(breakdown.fraction).isAtMost(1f)
        assertThat(breakdown.percent).isAtMost(100)
    }

    @Test
    fun `an empty breakdown reports zero rather than dividing by zero`() {
        assertThat(ContextBreakdown.Empty.fraction).isEqualTo(0f)
        assertThat(ContextBreakdown.Empty.percent).isEqualTo(0)
        assertThat(ContextBreakdown.Empty.canCompact).isFalse()
    }
}
