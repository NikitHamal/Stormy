package com.oro.app.data.ai.tools

/**
 * Exact-string editing with the safety checks coding agents rely on.
 *
 * The contract is the one Claude Code and opencode use: `old_string` must
 * occur exactly once unless `replaceAll`, so an edit can never land in the
 * wrong place silently. On a miss, two narrowly-scoped recoveries are tried
 * before failing, each only when it yields a *unique* match:
 *  1. line endings: the file uses CRLF and the model sent LF;
 *  2. line-trimmed: the same lines with different indentation/trailing space,
 *     which is the most common way a model's copy of a line drifts;
 *  3. whitespace-collapsed: the same text with whitespace runs differing
 *     anywhere (re-wrapped attributes, tabs vs spaces inside a line);
 *  4. block anchors: for blocks of 3+ lines, the first and last lines match
 *     exactly (trimmed) and the lines between are at least 85% similar --
 *     how small models typically misquote a long block (one changed
 *     word, a dropped blank line). Ambiguity always fails.
 * Model-added `read_file` line-number prefixes ("  12|") are stripped first.
 *
 * Failures return messages written for the model to act on.
 */
object TextEdits {

    sealed interface Result {
        data class Applied(val content: String, val replacements: Int) : Result
        data class Failed(val message: String) : Result
    }

    private val LINE_PREFIX = Regex("""^\s*\d+\|""")

    /** Strip "  12|" prefixes, but only if every non-blank line has one. */
    fun stripLineNumberPrefixes(text: String): String {
        val lines = text.split("\n")
        val nonBlank = lines.filter { it.isNotBlank() }
        if (nonBlank.isEmpty() || !nonBlank.all { LINE_PREFIX.containsMatchIn(it) }) return text
        return lines.joinToString("\n") { it.replaceFirst(LINE_PREFIX, "") }
    }

    fun apply(
        content: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean = false,
        path: String = "the file"
    ): Result {
        if (oldString.isEmpty()) {
            return Result.Failed(
                "old_string is empty. To create or fully replace a file use write_file; " +
                    "to insert text, set old_string to an existing nearby line and " +
                    "include it again in new_string."
            )
        }
        if (oldString == newString) {
            return Result.Failed("old_string and new_string are identical; nothing to change.")
        }

        var old = oldString
        var new = newString

        if (!content.contains(old)) {
            val stripped = stripLineNumberPrefixes(old)
            if (stripped != old && content.contains(stripped)) {
                old = stripped
                new = stripLineNumberPrefixes(new)
            }
        }

        // CRLF file, LF edit.
        if (!content.contains(old) && content.contains("\r\n") && !old.contains("\r\n")) {
            val crlfOld = old.replace("\n", "\r\n")
            if (content.contains(crlfOld)) {
                old = crlfOld
                new = new.replace("\r\n", "\n").replace("\n", "\r\n")
            }
        }

        val count = countOccurrences(content, old)
        if (count == 1 || (count > 1 && replaceAll)) {
            val updated = if (replaceAll) content.replace(old, new) else content.replaceFirst(old, new)
            return Result.Applied(updated, count)
        }
        if (count > 1) {
            val lines = occurrenceLines(content, old).take(6).joinToString(", ")
            return Result.Failed(
                "old_string matches $count places in $path (lines $lines). Include more " +
                    "surrounding lines to make it unique, or set replace_all=true to change all of them."
            )
        }

        // Line-trimmed fallback.
        lineTrimmedMatch(content, old)?.let { (start, end) ->
            val updated = content.substring(0, start) + new + content.substring(end)
            return Result.Applied(updated, 1)
        }
        whitespaceCollapsedMatch(content, old)?.let { (start, end) ->
            return Result.Applied(content.substring(0, start) + new + content.substring(end), 1)
        }
        blockAnchorMatch(content, old)?.let { (start, end) ->
            return Result.Applied(content.substring(0, start) + new + content.substring(end), 1)
        }

        return Result.Failed(notFoundMessage(content, old, path))
    }

    /** Apply edits in order; all-or-nothing. */
    fun applyAll(content: String, edits: List<Triple<String, String, Boolean>>, path: String): Result {
        var current = content
        var total = 0
        edits.forEachIndexed { i, (old, new, all) ->
            when (val r = apply(current, old, new, all, path)) {
                is Result.Applied -> { current = r.content; total += r.replacements }
                is Result.Failed -> return Result.Failed(
                    "Edit ${i + 1} of ${edits.size} failed, so no edits were applied: ${r.message}"
                )
            }
        }
        return Result.Applied(current, total)
    }

    fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var from = 0
        while (true) {
            val i = haystack.indexOf(needle, from)
            if (i < 0) return count
            count++
            from = i + needle.length
        }
    }

    private fun occurrenceLines(haystack: String, needle: String): List<Int> {
        val out = mutableListOf<Int>()
        var from = 0
        while (true) {
            val i = haystack.indexOf(needle, from)
            if (i < 0) return out
            out += haystack.substring(0, i).count { it == '\n' } + 1
            from = i + needle.length
        }
    }

    /**
     * Character range of the unique block whose lines equal [old]'s lines
     * after trimming, or null when there is none or more than one.
     */
    private fun lineTrimmedMatch(content: String, old: String): Pair<Int, Int>? {
        val oldLines = old.replace("\r\n", "\n").split("\n").let { lines ->
            // Ignore a trailing newline in old_string.
            if (lines.size > 1 && lines.last().isBlank()) lines.dropLast(1) else lines
        }.map { it.trim() }
        if (oldLines.all { it.isEmpty() }) return null

        val contentLines = content.split("\n")
        // Character offset of each line start.
        val starts = IntArray(contentLines.size)
        var offset = 0
        contentLines.forEachIndexed { i, line ->
            starts[i] = offset
            offset += line.length + 1
        }

        var found: Pair<Int, Int>? = null
        for (i in 0..contentLines.size - oldLines.size) {
            var match = true
            for (j in oldLines.indices) {
                if (contentLines[i + j].trimEnd('\r').trim() != oldLines[j]) { match = false; break }
            }
            if (match) {
                if (found != null) return null // ambiguous
                val lastIndex = i + oldLines.size - 1
                val end = starts[lastIndex] + contentLines[lastIndex].trimEnd('\r').length
                found = starts[i] to end
            }
        }
        return found
    }

    /** Unique match after collapsing every whitespace run to one space. */
    internal fun whitespaceCollapsedMatch(content: String, old: String): Pair<Int, Int>? {
        val needle = old.trim().replace(WS, " ")
        if (needle.length < 8) return null
        // Collapsed copy of content plus, for each collapsed char, its original index.
        val sb = StringBuilder(content.length)
        val map = IntArray(content.length + 1)
        var i = 0
        var inWs = false
        while (i < content.length) {
            val c = content[i]
            if (c.isWhitespace()) {
                if (!inWs) { map[sb.length] = i; sb.append(' '); inWs = true }
            } else {
                map[sb.length] = i; sb.append(c); inWs = false
            }
            i++
        }
        val hay = sb.toString()
        val first = hay.indexOf(needle)
        if (first < 0 || hay.indexOf(needle, first + 1) >= 0) return null
        val start = map[first]
        val lastCollapsed = first + needle.length - 1
        val end = map[lastCollapsed] + 1
        return start to end
    }

    /**
     * First and last lines anchor the block; the middle must be similar.
     * Returns the character range of the unique best block.
     */
    internal fun blockAnchorMatch(content: String, old: String): Pair<Int, Int>? {
        val oldLines = old.replace("\r\n", "\n").trimEnd('\n').split("\n")
        if (oldLines.size < 3) return null
        val firstT = oldLines.first().trim()
        val lastT = oldLines.last().trim()
        if (firstT.length < 3 || lastT.length < 1) return null
        val lines = content.split("\n")
        val starts = IntArray(lines.size)
        var off = 0
        lines.forEachIndexed { idx, l -> starts[idx] = off; off += l.length + 1 }
        val middleOld = oldLines.subList(1, oldLines.size - 1).joinToString("\n") { it.trim() }
        val candidates = mutableListOf<Pair<Pair<Int, Int>, Double>>()
        for (a in lines.indices) {
            if (lines[a].trimEnd('\r').trim() != firstT) continue
            val maxEnd = minOf(lines.size - 1, a + oldLines.size * 2 + 2)
            for (b in a + 1..maxEnd) {
                if (lines[b].trimEnd('\r').trim() != lastT) continue
                val middle = lines.subList(a + 1, b).joinToString("\n") { it.trimEnd('\r').trim() }
                val sim = similarity(middleOld, middle)
                if (sim >= 0.85) {
                    val end = starts[b] + lines[b].trimEnd('\r').length
                    candidates += (starts[a] to end) to sim
                }
                break // nearest closing anchor only
            }
        }
        if (candidates.size != 1) return null
        return candidates.single().first
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val longest = maxOf(a.length, b.length)
        if (longest == 0) return 1.0
        if (longest > 4000) return 0.0 // too costly; fail safe
        val d = com.oro.app.data.ai.craft.QualityAuditor.levenshtein(a, b)
        return 1.0 - d.toDouble() / longest
    }

    private val WS = Regex("\\s+")

    private fun notFoundMessage(content: String, old: String, path: String): String {
        val firstLine = old.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        val hint = firstLine?.let { needle ->
            content.lineSequence().withIndex()
                .firstOrNull { (_, line) -> line.contains(needle) || (needle.length > 12 && line.trim().startsWith(needle.take(12))) }
                ?.let { (i, _) -> " A similar line exists at line ${i + 1}; read the file around there and copy the text exactly." }
        }.orEmpty()
        return "old_string was not found in $path. It must match the current file text exactly, " +
            "including indentation and without read_file's line-number prefixes." +
            hint.ifEmpty { " Re-read the file with read_file before retrying." }
    }

    /**
     * A compact unified-style diff of one replacement, for the transcript's
     * tool card. Not a general diff: an edit already tells us exactly which
     * text went and which arrived.
     */
    fun miniDiff(old: String, new: String, maxLines: Int = 40): String = buildString {
        val removed = old.trimEnd('\n').split("\n")
        val added = new.trimEnd('\n').split("\n")
        appendLine("```diff")
        removed.take(maxLines).forEach { appendLine("-$it") }
        if (removed.size > maxLines) appendLine("-… ${removed.size - maxLines} more lines")
        added.take(maxLines).forEach { appendLine("+$it") }
        if (added.size > maxLines) appendLine("+… ${added.size - maxLines} more lines")
        append("```")
    }
}
