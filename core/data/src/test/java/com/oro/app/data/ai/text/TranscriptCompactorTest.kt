package com.oro.app.data.ai.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscriptCompactorTest {

    private fun card(i: Int, bodyLines: Int = 20) = buildString {
        append("🔧 **Edit File**\n✅ Edited file$i.css (1 replacement).\n")
        repeat(bodyLines) { append("+line $it of card $i with some width to it\n") }
        append('\n')
    }

    @Test
    fun `short transcripts are left alone`() {
        val t = "<think>plan</think>\n\nLet me look.\n\n" + card(1) + "Done."
        assertThat(TranscriptCompactor.compact(t)).isEqualTo(t)
    }

    @Test
    fun `long reasoning keeps its start and its end`() {
        val body = "a".repeat(2_000) + "MIDDLE" + "z".repeat(5_000)
        val out = TranscriptCompactor.capReasoning("<think>$body</think>\n\nReply.")
        assertThat(out).startsWith("<think>aaa")
        assertThat(out).contains("zzz</think>")
        assertThat(out).doesNotContain("MIDDLE")
        assertThat(out).endsWith("Reply.")
        assertThat(out.length).isLessThan(body.length)
    }

    @Test
    fun `oldest card bodies go first and every header stays`() {
        val t = (1..50).joinToString("") { card(it) } + "Final reply."
        val out = TranscriptCompactor.dropOldCardBodies(t, t.length / 2)
        assertThat(out.length).isAtMost(t.length / 2)
        (1..50).forEach { assertThat(out).contains("✅ Edited file$it.css") }
        assertThat(out).contains("+line 0 of card 50")
        assertThat(out).doesNotContain("+line 0 of card 1 ")
        assertThat(out).endsWith("Final reply.")
    }

    @Test
    fun `compact always fits and keeps the end of the run`() {
        val t = "<think>" + "r".repeat(50_000) + "</think>\n\n" + (1..3_000).joinToString("") { card(it, 2) } + "The site is ready."
        val out = TranscriptCompactor.compact(t, 60_000)
        assertThat(out.length).isAtMost(60_000)
        assertThat(out).endsWith("The site is ready.")
        assertThat(out).contains("Edited file3000.css")
    }

    @Test
    fun `a cut inside reasoning is rebalanced on both sides`() {
        // What the SQL repair leaves: a head ending inside reasoning, then a tail starting inside it.
        val repaired = "Intro\n\n<think>first half of a long thought" + TranscriptCompactor.CUT_MARKER +
            "second half</think>\n\nReply."
        val out = TranscriptCompactor.balance(repaired)
        val (head, tail) = out.split(TranscriptCompactor.CUT_MARKER)
        assertThat(head).endsWith("</think>\n\n")
        assertThat(tail).startsWith("<think>second half</think>")
        assertThat(tail).endsWith("Reply.")
    }

    @Test
    fun `an unclosed block at the end is closed`() {
        assertThat(TranscriptCompactor.balance("Text <think>never closed")).isEqualTo("Text <think>never closed</think>\n\n")
    }

    @Test
    fun `trimMiddle marks the cut`() {
        assertThat(TranscriptCompactor.trimMiddle("abcdefghij", 2, 3)).isEqualTo("ab\n…\nhij")
        assertThat(TranscriptCompactor.trimMiddle("short", 10, 10)).isEqualTo("short")
    }
}
