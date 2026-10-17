package com.oro.app.data.ai.agent

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.ToolCallRequest
import org.junit.Test

class HistoryPrunerTest {

    private fun user(text: String) = ChatRequestMessage(role = "user", content = text)
    private fun assistantCall(id: String, args: String = "{\"path\":\"a\"}") = ChatRequestMessage(
        role = "assistant", content = null,
        toolCalls = listOf(ToolCallRequest(id, "function", FunctionCall("read_file", args)))
    )
    private fun tool(id: String, size: Int) = ChatRequestMessage(role = "tool", content = "x".repeat(size), toolCallId = id)

    @Test
    fun `under budget is untouched`() {
        val msgs = listOf(user("hi"), ChatRequestMessage(role = "assistant", content = "hello"))
        assertThat(HistoryPruner.prune(msgs, 10_000)).isSameInstanceAs(msgs)
    }

    @Test
    fun `old tool outputs are stubbed before anything is dropped`() {
        val msgs = listOf(user("build it")) +
            (1..10).flatMap { listOf(assistantCall("c$it"), tool("c$it", 40_000)) } +
            listOf(user("now fix the header"))
        val pruned = HistoryPruner.prune(msgs, 60_000)
        assertThat(pruned.size).isEqualTo(msgs.size)
        assertThat(pruned.count { it.content == HistoryPruner.PRUNED_TOOL_OUTPUT }).isAtLeast(8)
        assertThat(TokenEstimator.estimate(pruned)).isAtMost(60_000)
        assertThat(pruned.last().content).isEqualTo("now fix the header")
    }

    @Test
    fun `dropping keeps tool calls paired with their results`() {
        val msgs = (1..30).flatMap {
            listOf(user("q$it ${"y".repeat(4_000)}"), assistantCall("c$it", "{\"path\":\"a\",\"content\":\"${"z".repeat(3_000)}\"}"), tool("c$it", 100))
        } + listOf(user("latest"))
        val pruned = HistoryPruner.prune(msgs, 5_000)
        assertThat(pruned.first().content).isEqualTo(HistoryPruner.OMITTED_NOTE)
        assertThat(pruned.last().content).isEqualTo("latest")
        // Every tool message is preceded (somewhere earlier) by its call.
        pruned.forEachIndexed { i, m ->
            if (m.role == "tool") {
                assertThat(pruned.take(i).any { a -> a.toolCalls?.any { it.id == m.toolCallId } == true }).isTrue()
            }
        }
        assertThat(TokenEstimator.estimate(pruned)).isAtMost(5_000)
    }

    @Test
    fun `token estimate counts tool call arguments`() {
        val bigCall = assistantCall("c", "{\"content\":\"${"a".repeat(4_000)}\"}")
        assertThat(TokenEstimator.estimate(bigCall)).isAtLeast(1_000)
    }
}
