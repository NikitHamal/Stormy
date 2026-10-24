package com.oro.app.ui.agent

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.ui.components.message.MessageContentBlock
import com.oro.app.ui.components.message.parseAiMessageContent
import com.oro.app.ui.screens.editor.agent.RunTranscript
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/** Unlimited runs must stay small enough to store, reload and render. */
class RunTranscriptLimitsTest {

    private fun call(id: String, name: String, args: String) = ToolCallResponse(id, "function", FunctionCall(name, args))

    private fun writeArgs(path: String, content: String) =
        buildJsonObject { put("path", path); put("content", content) }.toString()

    @Test
    fun `a very long run stays under its budget and keeps its newest steps`() {
        val t = RunTranscript()
        val file = (1..400).joinToString("\n") { "<div class=\"row-$it\">Some markup line $it</div>" }
        repeat(1_500) { i ->
            t.beginModelCall()
            t.appendReasoning("thinking ".repeat(2_000))
            t.appendText("Step $i.")
            val c = call("w$i", "write_file", writeArgs("page$i.html", file))
            t.toolStarted(c)
            t.toolFinished(c, ToolResult(true, "Created page$i.html (400 lines).\nFile: page$i.html"))
        }
        t.beginModelCall()
        t.appendText("All pages are done.")
        val rendered = t.render()
        assertThat(rendered.length).isAtMost(RunTranscript.MAX_RUN_CHARS + 20_000)
        assertThat(rendered).contains("page1499.html")
        assertThat(rendered).endsWith("All pages are done.")
        // Every step is still listed, even the oldest.
        assertThat(rendered).contains("Created page0.html")
    }

    @Test
    fun `closed reasoning keeps its two ends`() {
        val t = RunTranscript()
        t.beginModelCall()
        t.appendReasoning("START " + "x".repeat(20_000) + " FINISH")
        t.appendText("Reply.")
        val r = t.render()
        assertThat(r).contains("<think>START")
        assertThat(r).contains("FINISH</think>")
        assertThat(r.length).isLessThan(RunTranscript.THINK_KEEP_HEAD + RunTranscript.THINK_KEEP_TAIL + 200)
    }

    @Test
    fun `live reasoning shows only its newest part`() {
        val t = RunTranscript()
        t.beginModelCall()
        t.appendReasoning("OLDEST " + "y".repeat(40_000) + " NEWEST")
        val r = t.render()
        assertThat(r).contains("NEWEST")
        assertThat(r).doesNotContain("OLDEST")
        assertThat(r.length).isAtMost(RunTranscript.LIVE_THINK_CHARS + 64)
    }

    @Test
    fun `a write card carries a short preview and the true line count`() {
        val t = RunTranscript()
        val content = (1..900).joinToString("\n") { "line $it of a big file" }
        val c = call("w", "write_file", writeArgs("big.js", content))
        t.toolStarted(c)
        t.toolFinished(c, ToolResult(true, "Created big.js (900 lines).\nFile: big.js"))
        val r = t.render()
        assertThat(r).contains("written: 900 lines")
        assertThat(r).doesNotContain("line 900 of a big file")
        assertThat(r.length).isLessThan(RunTranscript.WRITTEN_PREVIEW_CHARS + 400)
        val block = parseAiMessageContent(r).filterIsInstance<MessageContentBlock.ToolCallBlock>().single()
        assertThat(block.additions).isEqualTo(900)
    }

    @Test
    fun `a shortened diff keeps its fence closed and its real counts`() {
        val removed = (1..300).joinToString("\n") { "-old line $it with some text" }
        val added = (1..250).joinToString("\n") { "+new line $it with some text" }
        val body = "File: a.css\n```diff\n$removed\n$added\n```"
        val capped = RunTranscript.capDiffBody(body, RunTranscript.EDIT_BODY_CHARS)
        assertThat(capped.length).isAtMost(RunTranscript.EDIT_BODY_CHARS + 80)
        assertThat(capped).startsWith("stats: +250 -300")
        assertThat(capped.lines().count { it.trim() == "```" || it.startsWith("```diff") } % 2).isEqualTo(0)

        val t = RunTranscript()
        val c = call("e", "edit_file", """{"path":"a.css"}""")
        t.toolStarted(c)
        t.toolFinished(c, ToolResult(true, "Edited a.css (1 replacement).\n$body"))
        val block = parseAiMessageContent(t.render()).filterIsInstance<MessageContentBlock.ToolCallBlock>().single()
        assertThat(block.additions).isEqualTo(250)
        assertThat(block.deletions).isEqualTo(300)
    }

    @Test
    fun `parsing a huge transcript is linear, not quadratic`() {
        // 4 000 cards and no reasoning after them: the old per-card tag search
        // scanned to the end of the message for every card.
        val content = buildString {
            append("<think>plan</think>\n\n")
            repeat(4_000) { i -> append("🔧 **Read File**\n✅ File: f$i.css (10 lines)\n1|a { color: red }\n\n") }
            append("Done.")
        }
        val start = System.nanoTime()
        val blocks = parseAiMessageContent(content)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertThat(blocks.filterIsInstance<MessageContentBlock.ToolCallBlock>()).hasSize(4_000)
        assertThat(ms).isLessThan(4_000L)
    }
}
