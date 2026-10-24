package com.oro.app.device

/**
 * What a device-agent reply with no tool call meant.
 *
 * The live eval (docs/research/device-live-eval.md) showed two habits that
 * cost whole runs. Models that call tools as text often end with
 * `finish_task(summary="…", success=true)` written out as plain text -- the
 * parser rightly ignores call syntax in prose -- and the run then ended
 * unconfirmed although the task was done. Others ask the user something
 * ("Could you give me your username?") in a plain reply, which nobody sees as
 * a question: the run ended and the user was never asked. Read here, the first
 * is a finish and the second becomes an ask_user card. Pure, for tests.
 */
internal object ProseTurn {

    sealed interface Kind

    /** finish_task written as text. */
    data class Finish(val summary: String, val success: Boolean) : Kind

    /** A question for the user written as text (or ask_user written as text). */
    data class Ask(val question: String) : Kind

    /** Anything else: a statement, a summary, a refusal. */
    data object Statement : Kind

    fun read(reply: String): Kind {
        val text = reply.trim()
        if (text.isEmpty()) return Statement
        callArgs(text, "finish_task")?.let { args ->
            val summary = named(args, "summary") ?: firstQuoted(args) ?: ""
            val success = Regex("""success\s*["']?\s*[=:]\s*["']?(true|false)""", RegexOption.IGNORE_CASE)
                .find(args)?.groupValues?.get(1)?.lowercase()?.toBooleanStrictOrNull() ?: true
            return Finish(summary.trim(), success)
        }
        callArgs(text, "ask_user")?.let { args ->
            val q = named(args, "question") ?: firstQuoted(args)
            if (!q.isNullOrBlank()) return Ask(q.trim())
        }
        question(text)?.let { return Ask(it) }
        return Statement
    }

    /**
     * The question at the end of a reply that asks the user something: its
     * last sentence ends with "?" and is about the user ("you", "your") or
     * opens like a request ("Could…", "Which…"). Null otherwise -- a rhetorical
     * "Done. Anything else?" stays a statement.
     */
    internal fun question(text: String): String? {
        val t = text.trim().trimEnd('*', '_', ' ')
        if (!t.endsWith("?")) return request(t)
        // From the last sentence end before the final question (keeping a short lead-in).
        val body = t.dropLast(1)
        // Options as a list ("Would you like me to:\n1. …\n2. …?"): the lead-in and the list together.
        val lines = t.lines()
        val listStart = lines.indexOfLast { !LIST_ITEM.matches(it.trim()) && it.isNotBlank() }
        if (listStart in 0 until lines.lastIndex && LIST_ITEM.matches(lines.last().trim()) && lines[listStart].trimEnd().endsWith(":")) {
            val block = lines.subList(listStart, lines.size).joinToString("\n").trim()
            val lead = lines[listStart].trim().lowercase()
            if (Regex("""\b(you|your|would|should|which|shall|can)\b""").containsMatchIn(lead)) return block.take(400)
        }
        val cut = maxOf(body.lastIndexOf(". "), body.lastIndexOf("! "), body.lastIndexOf('\n'))
        var q = (if (cut >= 0) t.substring(cut + 1) else t).trim()
        if (q.length < 12 && cut > 0) q = t.substring(maxOf(0, cut - 160)).trim()
        val lower = q.lowercase()
        // An offer of more help after the work is done is not a question the run waits on.
        if (OFFER.containsMatchIn(lower)) return null
        val aboutUser = Regex("""\b(you|your|yours)\b""").containsMatchIn(lower)
        val request = Regex("""^(could|would|should|do|does|did|can|which|what|where|when|who|shall|may|is|are|will)\b""").containsMatchIn(lower)
        if (!aboutUser && !request) return null
        return q.take(300)
    }

    private val LIST_ITEM = Regex("""^(\d+[.)]|[-*•])\s+.+""")

    /**
     * A request for something only the user has, made without a question
     * mark: "Please provide your username and password so I can log in."
     * (dots-3 in the live eval). An offer ("Let me know if…") is not one.
     */
    private fun request(t: String): String? {
        val tail = t.takeLast(400)
        val m = REQUEST.find(tail) ?: return null
        val from = maxOf(tail.lastIndexOf(". ", m.range.first), tail.lastIndexOf('\n', m.range.first)) + 1
        val q = tail.substring(from).trim()
        if (OFFER.containsMatchIn(q.lowercase())) return null
        return q.take(300)
    }

    private val REQUEST = Regex(
        """\b(please\s+(provide|share|give|tell|send|enter|type|confirm)|i\s+(still\s+)?need\s+(you\s+to|your))\b""",
        RegexOption.IGNORE_CASE
    )

    private val OFFER = Regex("""anything else|something else|else (i|we) can|help with anything|need anything""")

    /** The argument text of `name(…)` written in a reply, honouring quotes; null when absent. */
    private fun callArgs(text: String, name: String): String? {
        val m = Regex("""\b""" + name + """\s*\(""").find(text) ?: return null
        var i = m.range.last + 1
        var depth = 1
        var quote: Char? = null
        val start = i
        while (i < text.length) {
            val c = text[i]
            if (quote != null) {
                if (c == '\\') { i += 2; continue }
                if (c == quote) quote = null
            } else when (c) {
                '"', '\'' -> quote = c
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return text.substring(start, i) }
            }
            i++
        }
        return text.substring(start)
    }

    private fun named(args: String, key: String): String? =
        Regex("""\b""" + key + """\s*["']?\s*[=:]\s*(["'])((?:\\.|(?!\1).)*)\1""", RegexOption.DOT_MATCHES_ALL)
            .find(args)?.groupValues?.get(2)?.let(::unescape)

    private fun firstQuoted(args: String): String? =
        Regex("""(["'])((?:\\.|(?!\1).)*)\1""", RegexOption.DOT_MATCHES_ALL).find(args)?.groupValues?.get(2)?.let(::unescape)

    private fun unescape(s: String) = s.replace("\\\"", "\"").replace("\\'", "'").replace("\\n", "\n")
}
