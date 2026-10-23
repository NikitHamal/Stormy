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

    /**
     * The repaired text of a row, from its two kept ends: the head up to its
     * last block boundary (a blank line) with any code fence it left open
     * closed, then [MARKER], then the tail from its first block boundary,
     * starting after a fence closer if the cut entered a fenced block. A cut
     * at an arbitrary character used to leave half a tool card or a stray
     * fence that turned the rest of the message into a code block.
     */
    fun join(head: String, tail: String): String {
        var h = head
        val lastBreak = h.lastIndexOf("\n\n")
        if (lastBreak > 0) h = h.substring(0, lastBreak)
        if (fenceCount(h) % 2 == 1) h = h.trimEnd() + "\n```"
        var t = tail
        val firstBreak = t.indexOf("\n\n")
        // Within the first half only: a tail that is one long block keeps it all.
        if (firstBreak >= 0 && firstBreak < t.length / 2) t = t.substring(firstBreak + 2)
        if (fenceCount(t) % 2 == 1) {
            // The first fence closes a block opened before the cut: start after its line.
            val fence = t.indexOf("```")
            val lineEnd = t.indexOf('\n', fence)
            t = if (lineEnd < 0) "" else t.substring(lineEnd + 1)
        }
        return h.trimEnd() + MARKER + t.trimStart('\n')
    }

    /** Fence markers in [text]; they open mid-line too (a card's `new_content: ```). */
    internal fun fenceCount(text: String): Int {
        var n = 0
        var i = text.indexOf("```")
        while (i >= 0) {
            n++
            i = text.indexOf("```", i + 3)
        }
        return n
    }

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
