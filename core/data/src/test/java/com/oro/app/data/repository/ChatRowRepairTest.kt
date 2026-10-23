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
}
