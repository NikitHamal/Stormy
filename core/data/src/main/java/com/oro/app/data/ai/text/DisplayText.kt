package com.oro.app.data.ai.text

/**
 * Repairs reply text that arrives escaped, so the chat shows characters
 * instead of codes. Some providers (and models that answer "in JSON") send
 * `\n`, `—` or `&#39;` literally; rendered as-is they read as `'n`,
 * `—` or `&#39;` in the middle of sentences.
 *
 * Only prose is touched: text inside `inline code` keeps its backslashes,
 * since there they are usually meant (`"\n"` in a JS snippet). Literal `\n`
 * is decoded only when the text has clearly lost its real line breaks.
 */
object DisplayText {

    private val UNICODE = Regex("""\\u([0-9a-fA-F]{4})""")
    private val NUMERIC = Regex("""&#(x[0-9a-fA-F]+|\d+);""")
    private val NAMED = mapOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&#39;" to "'",
        "&nbsp;" to " ", "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…", "&rarr;" to "→",
        "&larr;" to "←", "&times;" to "×", "&bull;" to "•", "&copy;" to "©", "&trade;" to "™",
        "&lsquo;" to "‘", "&rsquo;" to "’", "&ldquo;" to "“", "&rdquo;" to "”"
    )

    fun clean(text: String): String {
        if (text.indexOf('\\') < 0 && text.indexOf('&') < 0) return text
        val decodeNewlines = lostLineBreaks(text)
        // Split on backticks: even parts are prose, odd parts are inline code.
        val parts = text.split('`')
        return parts.mapIndexed { i, part -> if (i % 2 == 1) part else prose(part, decodeNewlines) }.joinToString("`")
    }

    private fun lostLineBreaks(text: String): Boolean {
        val literal = Regex("""\\n""").findAll(text).count()
        if (literal < 2) return false
        val real = text.count { it == '\n' }
        return real == 0 || literal > real * 2
    }

    private fun prose(s: String, decodeNewlines: Boolean): String {
        var out = s
        if (decodeNewlines) out = out.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "\t")
        if (out.contains("\\u")) out = UNICODE.replace(out) { m -> m.groupValues[1].toInt(16).toChar().toString() }
        if (out.contains("\\\"") && decodeNewlines) out = out.replace("\\\"", "\"")
        if (out.indexOf('&') >= 0) {
            out = NUMERIC.replace(out) { m ->
                val v = m.groupValues[1]
                val cp = if (v.startsWith("x")) v.drop(1).toIntOrNull(16) else v.toIntOrNull()
                if (cp != null && Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else m.value
            }
            NAMED.forEach { (k, v) -> if (out.contains(k)) out = out.replace(k, v) }
        }
        return out
    }
}
