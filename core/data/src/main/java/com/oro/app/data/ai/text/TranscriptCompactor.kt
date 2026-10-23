package com.oro.app.data.ai.text

/**
 * Keeps a stored agent transcript (the assistant message of one run) small
 * enough to load, rebuild into history and render quickly, without losing what
 * the user actually reads.
 *
 * The format is RunTranscript's: `<think>` reasoning blocks, Markdown text, and
 * tool cards -- a `🔧 **Title**` line, a status line starting with ✅/❌/⏳,
 * then body lines up to the next blank line. Applied in order:
 *  1. reasoning blocks keep their beginning and their end (the chat shows
 *     reasoning collapsed; the conclusion is at the end);
 *  2. while still too long, the bodies of the oldest tool cards go (title and
 *     status line stay, so the step list reads the same);
 *  3. as a last resort the oldest part of the run is cut and its end kept --
 *     the latest steps and the final reply are what matter after a run.
 * Reasoning tags are rebalanced after every cut, so a cut can never turn the
 * rest of a message into raw text.
 */
object TranscriptCompactor {

    /** Upper bound for one stored assistant message, in characters. */
    const val MAX_CHARS = 200_000

    /** Reasoning kept per block: its start and its end. */
    const val THINK_HEAD = 600
    const val THINK_TAIL = 2_400

    /** Marks where something was cut. Plain Markdown, rendered as an italic line. */
    const val CUT_MARKER = "\n\n_… earlier steps of this run were trimmed to keep the chat fast._\n\n"

    private val THINK_BLOCK = Regex("(?s)<think>(.*?)</think>")

    fun compact(content: String, maxChars: Int = MAX_CHARS): String {
        var s = balance(content)
        s = capReasoning(s)
        if (s.length <= maxChars) return s
        s = dropOldCardBodies(s, maxChars)
        if (s.length <= maxChars) return s
        // Keep the end, starting at a block boundary so no card loses its header.
        val keep = (maxChars - CUT_MARKER.length - 16).coerceAtLeast(0)
        var tail = s.takeLast(keep)
        val boundary = tail.indexOf("\n\n")
        if (boundary in 0 until 4_000) tail = tail.substring(boundary + 2)
        return CUT_MARKER.trimStart('\n') + balanceSegment(tail)
    }

    /** Shortens every reasoning block longer than [THINK_HEAD] + [THINK_TAIL] to its two ends. */
    fun capReasoning(content: String): String {
        if (!content.contains("<think>")) return content
        return THINK_BLOCK.replace(content) { m ->
            val body = m.groupValues[1]
            if (body.length <= THINK_HEAD + THINK_TAIL + 64) m.value
            else "<think>" + trimMiddle(body, THINK_HEAD, THINK_TAIL) + "</think>"
        }
    }

    /** [text] with its middle replaced by an ellipsis line, keeping [head] and [tail] characters. */
    fun trimMiddle(text: String, head: Int, tail: Int): String =
        if (text.length <= head + tail) text
        else text.take(head).trimEnd() + "\n…\n" + text.takeLast(tail).trimStart()

    /**
     * Closes reasoning that a cut left open and reopens reasoning a cut entered
     * mid-way, segment by segment around [CUT_MARKER], so each side parses on
     * its own.
     */
    fun balance(content: String): String {
        if (!content.contains("<think>") && !content.contains("</think>")) return content
        val parts = content.split(CUT_MARKER)
        return parts.joinToString(CUT_MARKER) { balanceSegment(it) }
    }

    private fun balanceSegment(segment: String): String {
        var open = false
        var startsInside = false
        var sawTag = false
        var i = 0
        while (true) {
            val o = segment.indexOf("<think>", i)
            val c = segment.indexOf("</think>", i)
            if (o < 0 && c < 0) break
            if (c >= 0 && (o < 0 || c < o)) {
                // A close before any open: the segment starts inside a reasoning block.
                if (!sawTag) startsInside = true
                open = false
                i = c + "</think>".length
            } else {
                open = true
                i = o + "<think>".length
            }
            sawTag = true
        }
        var out = segment
        if (startsInside) out = "<think>$out"
        if (open) out = out.trimEnd() + "</think>\n\n"
        return out
    }

    /**
     * Drops the bodies of the oldest tool cards until [content] fits in
     * [maxChars] or no card has a body left. A card's header and status line
     * always stay.
     */
    fun dropOldCardBodies(content: String, maxChars: Int): String {
        val lines = content.split('\n')
        // Body line ranges, oldest card first.
        val bodies = mutableListOf<IntRange>()
        var i = 0
        while (i < lines.size) {
            if (isCardHeader(lines[i]) && i + 1 < lines.size && isStatusLine(lines[i + 1])) {
                val start = i + 2
                var end = start
                while (end < lines.size && lines[end].isNotBlank() && !isCardHeader(lines[end])) end++
                if (end > start) bodies += start until end
                i = end
            } else {
                i++
            }
        }
        if (bodies.isEmpty()) return content
        var excess = content.length - maxChars
        val dropped = BooleanArray(lines.size)
        for (range in bodies) {
            if (excess <= 0) break
            for (k in range) {
                dropped[k] = true
                excess -= lines[k].length + 1
            }
        }
        val sb = StringBuilder(content.length)
        var first = true
        for (k in lines.indices) {
            if (dropped[k]) continue
            if (!first) sb.append('\n')
            sb.append(lines[k])
            first = false
        }
        return sb.toString()
    }

    private fun isCardHeader(line: String): Boolean = line.trimStart().startsWith("🔧")

    private fun isStatusLine(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("✅") || t.startsWith("❌") || t.startsWith("⏳")
    }
}
