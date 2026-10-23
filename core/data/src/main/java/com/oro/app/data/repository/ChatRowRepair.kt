package com.oro.app.data.repository

import com.oro.app.data.ai.text.TranscriptCompactor

/**
 * The safety net for chat rows too big to read.
 *
 * Android hands SQLite rows to the app through a CursorWindow of about 2 MB.
 * A `content` larger than that cannot be read at all -- the query throws
 * (SQLiteBlobTooBigException, or "Row too big to fit into CursorWindow" /
 * "Couldn't read row" on older releases) -- and because a chat loads every
 * message of its session, one oversized agent transcript made the whole chat
 * crash the app on open. Transcripts are bounded where they are built now
 * (RunTranscript, TranscriptCompactor); this repairs rows written before that,
 * in SQL, so the row never has to fit in a cursor.
 */
object ChatRowRepair {

    /** Rows longer than this many characters are shrunk; far under the window even at 4 bytes a character. */
    const val LIMIT_CHARS = 400_000

    /** Kept from the start of a run: the first steps. */
    const val HEAD_CHARS = 20_000

    /** Kept from the end: the latest steps and the final reply. */
    const val TAIL_CHARS = 160_000

    /** Between the two, the compactor's own marker, so a later compaction recognises the cut. */
    const val MARKER = TranscriptCompactor.CUT_MARKER

    /** Whether [error], or any cause, is SQLite refusing a row too big for a cursor window. */
    fun isOversizedRow(error: Throwable?): Boolean {
        var e = error
        var depth = 0
        while (e != null && depth < 8) {
            if (e.javaClass.simpleName == "SQLiteBlobTooBigException") return true
            val m = e.message.orEmpty()
            if (m.contains("too big to fit into CursorWindow", ignoreCase = true) ||
                m.contains("Couldn't read row", ignoreCase = true) ||
                m.contains("CursorWindow", ignoreCase = true) && m.contains("row", ignoreCase = true)
            ) return true
            e = e.cause
            depth++
        }
        return false
    }
}
