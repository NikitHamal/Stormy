package com.oro.app.data.ai.agent

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.ToolCallRequest
import org.junit.Test

class RunLedgerTest {

    private fun call(id: String, name: String, args: String) = ChatRequestMessage(
        role = "assistant", content = null,
        toolCalls = listOf(ToolCallRequest(id, "function", FunctionCall(name, args)))
    )
    private fun result(id: String, name: String, text: String) = ChatRequestMessage(role = "tool", content = text, toolCallId = id, name = name)

    @Test
    fun `batched answers and skipped ones are kept with their questions`() {
        val batch = "The user answered 2 of 3 questions:\n" +
            "1. What is the brand name?\n   → Bloom & Co\n" +
            "2. Which pages?\n   → Home, Menu, Contact\n" +
            "3. Any colours?\n   → (skipped — use your best judgement)"
        val ledger = RunLedger.summarize(
            listOf(
                call("a", "ask_user", """{"questions":[{"question":"What is the brand name?"}]}"""),
                result("a", "ask_user", batch),
                call("b", "ask_user", """{"question":"Dark mode too?"}"""),
                result("b", "ask_user", "User answered: Yes, both"),
                call("c", "ask_user", """{"question":"Logo?"}"""),
                result("c", "ask_user", "The user skipped this question. Proceed with your best judgement and state the assumption."),
            )
        )
        assertThat(ledger).contains("What is the brand name? → Bloom & Co")
        assertThat(ledger).contains("Which pages? → Home, Menu, Contact")
        assertThat(ledger).contains("Any colours? → skipped")
        assertThat(ledger).contains("Dark mode too? → Yes, both")
        assertThat(ledger).contains("Logo? → skipped")
    }

    @Test
    fun `answers and the plan come first, changes are capped, the closing line always survives`() {
        val many = (1..80).flatMap { i ->
            listOf(call("w$i", "write_file", """{"path":"src/components/very/long/folder/name/Component$i.jsx","content":"x"}"""), result("w$i", "write_file", "Wrote"))
        }
        val ledger = RunLedger.summarize(
            listOf(
                call("p", "update_plan", """{"tasks":[{"title":"Hero","status":"done"},{"title":"Menu"}]}"""),
                result("p", "update_plan", "Plan updated"),
                call("q", "ask_user", """{"question":"Brand?"}"""),
                result("q", "ask_user", "User answered: Bloom"),
            ) + many
        )
        assertThat(ledger.length).isAtMost(RunLedger.MAX_CHARS)
        assertThat(ledger.indexOf("User answered: Brand? → Bloom")).isLessThan(ledger.indexOf("- Changed:"))
        assertThat(ledger.indexOf("- Plan:")).isLessThan(ledger.indexOf("- Changed:"))
        assertThat(ledger).contains("+65 more")
        assertThat(ledger).endsWith("re-read a file before editing it.")
    }
}
