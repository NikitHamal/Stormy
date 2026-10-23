package com.oro.app.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatRowRepairTest {

    private class SQLiteBlobTooBigException(message: String) : RuntimeException(message)

    @Test
    fun `recognises every form of the cursor window failure`() {
        assertThat(ChatRowRepair.isOversizedRow(SQLiteBlobTooBigException("Row too big"))).isTrue()
        assertThat(ChatRowRepair.isOversizedRow(IllegalStateException("Row too big to fit into CursorWindow requiredPos=0"))).isTrue()
        assertThat(ChatRowRepair.isOversizedRow(IllegalStateException("Couldn't read row 0, col 4 from CursorWindow."))).isTrue()
        assertThat(ChatRowRepair.isOversizedRow(RuntimeException("query failed", IllegalStateException("Row too big to fit into CursorWindow")))).isTrue()
    }

    @Test
    fun `other failures are not treated as oversized rows`() {
        assertThat(ChatRowRepair.isOversizedRow(IllegalStateException("database is locked"))).isFalse()
        assertThat(ChatRowRepair.isOversizedRow(null)).isFalse()
    }

    @Test
    fun `a repaired row is far below the limit and fits a cursor window`() {
        val repaired = ChatRowRepair.HEAD_CHARS + ChatRowRepair.MARKER.length + ChatRowRepair.TAIL_CHARS
        assertThat(repaired).isLessThan(ChatRowRepair.LIMIT_CHARS)
        // Even at four UTF-8 bytes per character, under the ~2 MB window.
        assertThat(ChatRowRepair.LIMIT_CHARS * 4).isLessThan(2 * 1024 * 1024)
    }

    @Test
    fun `the head ends at its last block and the tail starts at its next one`() {
        val head = "Intro paragraph.\n\n🔧 **Read File**\n✅ File: a.css\nhalf of a ca"
        val tail = "rd that was cut\nmore of it\n\nLater text with enough words to be most of the tail.\n\nFinal reply."
        val out = ChatRowRepair.join(head, tail)
        assertThat(out).startsWith("Intro paragraph.")
        assertThat(out).doesNotContain("half of a ca")
        assertThat(out).doesNotContain("rd that was cut")
        assertThat(out).contains(ChatRowRepair.MARKER)
        assertThat(out).endsWith("Later text with enough words to be most of the tail.\n\nFinal reply.")
    }

    @Test
    fun `a code fence the head cut open is closed`() {
        // The head's last blank line is inside the code block.
        val head = "Start.\n\n```js\nconst a = 1;\n\nconst b = 2;\nconst c"
        val out = ChatRowRepair.join(head, "\n\nDone.")
        val before = out.substringBefore(ChatRowRepair.MARKER)
        assertThat(before).isEqualTo("Start.\n\n```js\nconst a = 1;\n```")
        assertThat(ChatRowRepair.fenceCount(before) % 2).isEqualTo(0)
    }

    @Test
    fun `a tail that starts inside a fenced block starts after its closer`() {
        val head = "Start.\n\n"
        val tail = "x = 1\n\ny = 2\n```\n\nThe reply after the code."
        val out = ChatRowRepair.join(head + "more", tail)
        val after = out.substringAfter(ChatRowRepair.MARKER)
        assertThat(ChatRowRepair.fenceCount(after) % 2).isEqualTo(0)
        assertThat(after).doesNotContain("y = 2")
        assertThat(after).contains("The reply after the code.")
    }

    @Test
    fun `a tail that is one long block is kept whole`() {
        val tail = "one block " + "x".repeat(500) + "\n\nend"
        val out = ChatRowRepair.join("Head.\n\nmore", tail)
        assertThat(out).contains("one block ")
    }
}
