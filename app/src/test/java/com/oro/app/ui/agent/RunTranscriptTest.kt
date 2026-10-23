package com.oro.app.ui.agent

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.ui.components.message.MessageContentBlock
import com.oro.app.ui.components.message.ToolStatus
import com.oro.app.ui.components.message.parseAiMessageContent
import com.oro.app.ui.screens.editor.agent.RunTranscript
import org.junit.Test

class RunTranscriptTest {

    private fun call(id: String, name: String, args: String) = ToolCallResponse(id, "function", FunctionCall(name, args))

    @Test
    fun `tool card goes from running to done in place and the reply after it stays text`() {
        val t = RunTranscript()
        t.beginModelCall()
        t.appendReasoning("Need to read the file")
        t.appendText("Let me check.")
        val read = call("1", "read_file", """{"path":"index.html"}""")
        t.toolStarted(read)
        assertThat(t.render()).contains("⏳ index.html")

        t.toolFinished(read, ToolResult(true, "File: index.html (3 lines)\n1|<html>\n2|\n3|</html>"))
        t.beginModelCall()
        t.appendText("The page is fine.\n\nNothing to change.")

        val rendered = t.render()
        assertThat(rendered).contains("<think>Need to read the file</think>")
        assertThat(rendered).contains("🔧 **Read File**\n✅ File: index.html (3 lines)")
        assertThat(rendered).doesNotContain("⏳")

        // The renderer sees one card and keeps the final reply out of it.
        val blocks = parseAiMessageContent(rendered)
        val tool = blocks.filterIsInstance<MessageContentBlock.ToolCallBlock>().single()
        assertThat(tool.status).isEqualTo(ToolStatus.SUCCESS)
        assertThat(tool.output.orEmpty()).doesNotContain("The page is fine")
        val text = blocks.filterIsInstance<MessageContentBlock.TextBlock>().joinToString("\n") { it.content }
        assertThat(text).contains("The page is fine.")
        assertThat(text).contains("Nothing to change.")
    }

    @Test
    fun `a retried model call discards only its own output`() {
        val t = RunTranscript()
        t.beginModelCall()
        t.appendText("First answer part")
        val c = call("1", "grep", """{"pattern":"x"}""")
        t.toolStarted(c)
        t.toolFinished(c, ToolResult(true, "No matches for x."))
        t.beginModelCall()
        t.appendText("half a repl")
        t.discardCurrentCall()
        t.appendText("Full reply.")
        val r = t.render()
        assertThat(r).contains("First answer part")
        assertThat(r).doesNotContain("half a repl")
        assertThat(r).endsWith("Full reply.")
    }

    @Test
    fun `recovered text tool call markup is removed from the visible text`() {
        val t = RunTranscript()
        t.beginModelCall()
        t.appendText("Reading.\n<tool_call>{\"name\":\"read_file\"}</tool_call>")
        t.setCurrentCallText("Reading.")
        assertThat(t.render()).isEqualTo("Reading.")
    }

    @Test
    fun `ask_user renders as an answered quote, not a tool card`() {
        val t = RunTranscript()
        val ask = call("q", "ask_user", """{"question":"Which colour?","options":["Blue","Green"]}""")
        t.toolStarted(ask)
        t.toolFinished(ask, ToolResult(true, "User answered: Green"))
        val edit = call("e", "edit_file", """{"path":"a.css","old_string":"x","new_string":"y"}""")
        t.toolStarted(edit)
        t.toolFinished(edit, ToolResult(false, "", "old_string was not found in a.css."))
        val r = t.render()
        assertThat(r).contains("> **You answered** · Which colour?\n> **Green**")
        assertThat(r).doesNotContain("Ask User")
        assertThat(r).contains("❌ old_string was not found in a.css.")
    }

    @Test
    fun `a card handed back with You decide reads as such`() {
        val t = RunTranscript()
        val ask = call(
            "q", "ask_user",
            """{"questions":[{"question":"What's the cafe called?"},{"question":"Which look fits?"}]}"""
        )
        t.toolStarted(ask)
        t.toolFinished(ask, ToolResult(true, com.oro.app.data.ai.tools.UserQuestions.DECIDE_RESULT))
        assertThat(t.render()).isEqualTo("> **You let Orobi decide** · What's the cafe called? (+1 more)")
    }

    @Test
    fun `edit output keeps the diff without blank lines inside the card`() {
        val t = RunTranscript()
        val edit = call("e", "edit_file", """{"path":"a.css"}""")
        t.toolStarted(edit)
        t.toolFinished(edit, ToolResult(true, "Edited a.css (1 replacement).\nFile: a.css\n```diff\n-a\n\n+b\n```"))
        val card = t.render()
        assertThat(card).contains("File: a.css\n```diff\n-a\n \n+b\n```")
        val block = parseAiMessageContent(card).filterIsInstance<MessageContentBlock.ToolCallBlock>().single()
        assertThat(block.additions).isEqualTo(1)
        assertThat(block.deletions).isEqualTo(1)
    }

    @Test
    fun `history text drops reasoning and collapses tool cards`() {
        val content = "<think>plan</think>\n\nLet me look.\n\n🔧 **Read File**\n✅ File: a.html\n1|x\n\n🔧 **Edit File**\n❌ not found\n\nDone."
        assertThat(RunTranscript.toHistoryText(content))
            .isEqualTo("Let me look.\n\n[Read File: done]\n\n[Edit File: failed]\n\nDone.")
    }

    @Test
    fun `batched ask_user lists every answer and marks skips`() {
        val t = RunTranscript()
        val ask = call(
            "q", "ask_user",
            """{"questions":[{"question":"Framework?","options":[{"label":"React"}]},{"question":"Name?"}]}"""
        )
        t.toolStarted(ask)
        assertThat(t.render()).isEmpty()
        val output = com.oro.app.data.ai.tools.UserQuestions.formatAnswers(
            com.oro.app.data.ai.tools.UserQuestions.parse(
                kotlinx.serialization.json.Json.parseToJsonElement(ask.function.arguments)
                    as kotlinx.serialization.json.JsonObject
            ),
            listOf("React", null)
        )
        t.toolFinished(ask, ToolResult(true, output))
        val r = t.render()
        assertThat(r).contains("1. Framework? — **React**")
        assertThat(r).contains("2. Name? — _skipped_")
    }

    @Test
    fun `rows keep their subject when the output names no file`() {
        val t = RunTranscript()
        val skill = call("s", "use_skill", """{"name":"design-system"}""")
        t.toolStarted(skill)
        t.toolFinished(skill, ToolResult(true, "Skill design-system (97 lines)\nTokens and motion."))
        val write = call("w", "write_file", """{"path":"index.html","content":"<p>hi</p>"}""")
        t.toolStarted(write)
        val blocks = parseAiMessageContent(t.render(), isStreaming = true)
            .filterIsInstance<MessageContentBlock.ToolCallBlock>()
        assertThat(blocks.map { it.filePath }).containsExactly("design-system", "index.html").inOrder()
        // The subject line is for the row; the detail sheet shows the real output.
        assertThat(blocks.first().output.orEmpty()).doesNotContain("subject:")
    }

    @Test
    fun `file tools are not given a second subject`() {
        val t = RunTranscript()
        val read = call("r", "read_file", """{"path":"a.css"}""")
        t.toolStarted(read)
        t.toolFinished(read, ToolResult(true, "File: a.css (3 lines)\n1|a"))
        assertThat(t.render()).doesNotContain("subject:")
    }

    @Test
    fun `the running tool's subject is read from the last card`() {
        val t = RunTranscript()
        val read = call("r", "read_file", """{"path":"a.css"}""")
        t.toolStarted(read)
        t.toolFinished(read, ToolResult(true, "File: a.css (3 lines)\n1|a"))
        assertThat(RunTranscript.runningSubject(t.render())).isNull()
        val write = call("w", "write_file", """{"path":"index.html","content":"x"}""")
        t.toolStarted(write)
        assertThat(RunTranscript.runningSubject(t.render())).isEqualTo("index.html")
        t.toolFinished(write, ToolResult(true, "Created index.html (1 lines).\nFile: index.html"))
        assertThat(RunTranscript.runningSubject(t.render())).isNull()
    }
}
