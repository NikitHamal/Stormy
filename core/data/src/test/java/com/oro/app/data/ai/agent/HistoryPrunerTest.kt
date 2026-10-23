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

    @Test
    fun `old text-protocol traffic is shrunk too`() {
        val bigFile = "<p>".repeat(3_000)
        val call = "Writing.\n<tool_call>\n<function=write_file>\n<parameter=path>a.html</parameter>\n" +
            "<parameter=content>\n$bigFile\n</parameter>\n</function>\n</tool_call>"
        val results = "Tool results:\n<tool_result name=\"read_file\">\n${"y".repeat(20_000)}\n</tool_result>\nContinue."
        val msgs = listOf(user("build")) +
            (1..6).flatMap { listOf(ChatRequestMessage(role = "assistant", content = call), user(results)) } +
            listOf(user("latest"))
        val pruned = HistoryPruner.prune(msgs, 30_000)
        assertThat(pruned.size).isEqualTo(msgs.size)
        assertThat(pruned.any { it.content?.contains("characters omitted") == true }).isTrue()
        assertThat(pruned.any { it.content?.contains(HistoryPruner.PRUNED_TOOL_OUTPUT) == true }).isTrue()
        // The call structure survives, so the model still sees what it did.
        assertThat(pruned[1].content).contains("<parameter=path>a.html</parameter>")
        assertThat(TokenEstimator.estimate(pruned)).isAtMost(30_000)
    }

    private fun writeCall(id: String, path: String, size: Int) = ChatRequestMessage(
        role = "assistant", content = "Updating $path for step $id.",
        toolCalls = listOf(ToolCallRequest(id, "function", FunctionCall("write_file", "{\"path\":\"$path\",\"content\":\"${"c".repeat(size)}\"}")))
    )

    /** Every tool message directly follows its assistant call (or another result of it). */
    private fun assertPaired(msgs: List<ChatRequestMessage>) {
        msgs.forEachIndexed { i, m ->
            if (m.role == "tool") {
                val owner = msgs.subList(0, i).lastOrNull { it.role != "tool" }
                assertThat(owner?.toolCalls?.any { it.id == m.toolCallId }).isTrue()
            }
        }
    }

    @Test
    fun `a long run collapses its oldest steps into a ledger on the task message`() {
        val task = user("Build a recipe site with search and favourites")
        val run = (1..200).flatMap { i ->
            val path = listOf("index.html", "css/app.css", "js/app.js")[i % 3]
            listOf(writeCall("c$i", path, 2_000), tool("c$i", 600))
        }
        val msgs = listOf(task) + run
        val pruned = HistoryPruner.prune(msgs, 12_000)

        assertThat(TokenEstimator.estimate(pruned)).isAtMost(12_000)
        // The task survives, with the ledger of what the collapsed steps did.
        assertThat(pruned.first().role).isEqualTo("user")
        assertThat(pruned.first().content).startsWith("Build a recipe site")
        assertThat(pruned.first().content).contains("collapsed to fit the context window")
        assertThat(pruned.first().content).contains("index.html (write ×")
        assertThat(pruned.first().content).contains("js/app.js")
        // The run resumes on the model's own turn, calls stay paired, the newest step is intact.
        assertThat(pruned[1].role).isEqualTo("assistant")
        assertPaired(pruned)
        assertThat(pruned.last()).isEqualTo(msgs.last())
    }

    @Test
    fun `the collapse boundary holds for a while so the prompt prefix stays cacheable`() {
        val task = user("Make the dashboard")
        fun runOf(n: Int) = listOf(task) + (1..n).flatMap { i -> listOf(writeCall("c$i", "app.js", 1_500), tool("c$i", 400)) }
        // Over 32 consecutive turns the task + ledger prefix changes only a few times.
        val prefixes = (100 until 132).map { HistoryPruner.prune(runOf(it), 10_000).first().content }
        val changes = prefixes.zipWithNext().count { (a, b) -> a != b }
        assertThat(changes).isAtMost(8)
        assertThat(prefixes.distinct().size).isAtLeast(2)
    }

    @Test
    fun `text-protocol results and harness notes never displace the task`() {
        val task = user("Add a contact form to index.html")
        val turns = (1..120).flatMap { i ->
            listOf(
                ChatRequestMessage(
                    role = "assistant",
                    content = "Step $i.\n<tool_call>\n<function=edit_file>\n<parameter=path>index.html</parameter>\n" +
                        "<parameter=old_string>${"o".repeat(800)}</parameter>\n<parameter=new_string>${"n".repeat(800)}</parameter>\n</function>\n</tool_call>"
                ),
                user("Tool results:\n<tool_result name=\"edit_file\">\n" + (if (i % 10 == 0) "Error: old_string not found in index.html" else "Edited index.html") + "\n</tool_result>\nContinue.")
            )
        }
        val pruned = HistoryPruner.prune(listOf(task) + turns, 9_000)
        assertThat(TokenEstimator.estimate(pruned)).isAtMost(9_000)
        assertThat(pruned.first().content).startsWith("Add a contact form")
        assertThat(pruned.first().content).contains("index.html (edit ×")
        assertThat(pruned.first().content).contains("Failed (don't repeat as is): edit_file — old_string not found")
        assertThat(pruned[1].role).isEqualTo("assistant")
        assertThat(pruned.none { it.content == HistoryPruner.OMITTED_NOTE }).isTrue()
    }

    @Test
    fun `harness notes are recognised so the user's last message anchors the run`() {
        val msgs = listOf(
            user("first request"), ChatRequestMessage(role = "assistant", content = "ok"),
            user("second request"),
            user("Tool results:\n<tool_result name=\"read_file\">\nx\n</tool_result>"),
            user("Automatic quality review (by the Oro app, not the user) found 2 problems")
        )
        assertThat(HistoryPruner.taskIndex(msgs)).isEqualTo(2)
    }

    @Test
    fun `a retry note or a deploy note never becomes the task`() {
        val task = user("Build a booking page with a calendar")
        val run = (1..60).flatMap { i -> listOf(writeCall("c$i", "index.html", 2_000), tool("c$i", 300)) }
        val retry = user(HistoryPruner.appNote("continue from where you stopped. Check the current state of the files first if unsure."))
        val deployed = user(HistoryPruner.appNote("the user published this project. It is live at https://x.netlify.app"))
        val msgs = listOf(task) + run + listOf(deployed, retry)
        assertThat(HistoryPruner.taskIndex(msgs)).isEqualTo(0)

        val pruned = HistoryPruner.prune(msgs, 8_000)
        assertThat(pruned.first().content).startsWith("Build a booking page")
        assertThat(pruned.last().content).startsWith("[Oro: continue from where you stopped")
        assertThat(TokenEstimator.estimate(pruned)).isAtMost(8_000)
        // Notes written by older versions are recognised too.
        assertThat(HistoryPruner.isHarnessMessage(user("Continue from where you stopped. Check the files."))).isTrue()
    }

    @Test
    fun `the newest step of a text-protocol run is never folded away from its results`() {
        val task = user("Write the whole site")
        fun call(i: Int, size: Int) = ChatRequestMessage(
            role = "assistant",
            content = "Step $i.\n<tool_call>\n<function=write_file>\n<parameter=path>p$i.html</parameter>\n" +
                "<parameter=content>\n${"x".repeat(size)}\n</parameter>\n</function>\n</tool_call>"
        )
        fun results(i: Int) = user("Tool results:\n<tool_result name=\"write_file\">\nWrote p$i.html\n</tool_result>\nContinue.")
        val msgs = listOf(task) + (1..30).flatMap { listOf(call(it, 600), results(it)) } + listOf(call(31, 40_000), results(31))

        val pruned = HistoryPruner.prune(msgs, 6_000)

        // The last call survives (shrunk) right before its results.
        assertThat(pruned.last().content).contains("Wrote p31.html")
        val lastCall = pruned[pruned.lastIndex - 1]
        assertThat(lastCall.role).isEqualTo("assistant")
        assertThat(lastCall.content).contains("<parameter=path>p31.html</parameter>")
        assertThat(lastCall.content).contains("characters omitted")
        assertThat(TokenEstimator.estimate(pruned)).isAtMost(6_000)
    }

    @Test
    fun `a note after the newest native step keeps that step`() {
        val task = user("Polish the header")
        val run = (1..40).flatMap { i -> listOf(writeCall("c$i", "index.html", 1_500), tool("c$i", 400)) }
        val note = user("Automatic quality review (by the Oro app, not the user) found 1 problem: index.html")
        val pruned = HistoryPruner.prune(listOf(task) + run + listOf(note), 4_000)

        assertThat(pruned.last()).isEqualTo(note)
        assertThat(pruned.any { m -> m.toolCalls?.any { it.id == "c40" } == true }).isTrue()
        assertPaired(pruned)
    }

    @Test
    fun `a very long run prunes in one pass`() {
        val task = user("Build the app")
        val run = (1..1_500).flatMap { i -> listOf(writeCall("c$i", "src/f${i % 40}.js", 1_200), tool("c$i", 300)) }
        val started = System.nanoTime()
        val pruned = HistoryPruner.prune(listOf(task) + run, 16_000)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertThat(TokenEstimator.estimate(pruned)).isAtMost(16_000)
        assertThat(pruned.first().content).contains("collapsed to fit the context window")
        assertThat(ms).isLessThan(5_000)
    }
}
