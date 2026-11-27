package com.oro.app.data.preview

/**
 * A small Markdown renderer for the preview's document view (README files,
 * notes and specs the agent writes). Headings, paragraphs, emphasis, inline
 * and fenced code, links, images, block quotes, flat lists, tables and rules.
 *
 * Everything is HTML-escaped before any markup is added, and link targets
 * are limited to http(s), mailto, anchors and relative paths, so a document
 * can't run script in the preview.
 */
object MiniMarkdown {

    fun escape(text: String): String = buildString(text.length + 16) {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }

    /** A complete HTML page that follows the system's light/dark setting. */
    fun page(title: String, markdown: String): String =
        "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${escape(title)}</title><style>$CSS</style></head>" +
            "<body><main>${toHtml(markdown)}</main></body></html>"

    fun toHtml(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> i++

                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    val fence = trimmed.take(3)
                    val lang = trimmed.drop(3).trim().takeWhile { it.isLetterOrDigit() || it == '-' || it == '+' }
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                        code.append(lines[i]).append('\n')
                        i++
                    }
                    i++ // closing fence (or the end of the text)
                    val cls = if (lang.isNotEmpty()) " class=\"language-$lang\"" else ""
                    out.append("<pre><code$cls>").append(escape(code.toString().trimEnd('\n'))).append("</code></pre>\n")
                }

                HEADING.matches(trimmed) -> {
                    val m = HEADING.find(trimmed)!!
                    val level = m.groupValues[1].length
                    out.append("<h$level>").append(inline(m.groupValues[2].trimEnd('#', ' '))).append("</h$level>\n")
                    i++
                }

                RULE.matches(trimmed) -> { out.append("<hr>\n"); i++ }

                trimmed.startsWith(">") -> {
                    val quote = StringBuilder()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        quote.append(lines[i].trim().removePrefix(">").removePrefix(" ")).append('\n')
                        i++
                    }
                    out.append("<blockquote>").append(toHtml(quote.toString())).append("</blockquote>\n")
                }

                BULLET.containsMatchIn(line) || ORDERED.containsMatchIn(line) -> {
                    val ordered = ORDERED.containsMatchIn(line) && !BULLET.containsMatchIn(line)
                    val marker = if (ordered) ORDERED else BULLET
                    out.append(if (ordered) "<ol>" else "<ul>")
                    while (i < lines.size && marker.containsMatchIn(lines[i])) {
                        val item = marker.replace(lines[i], "")
                        out.append("<li>").append(inline(item.trim())).append("</li>")
                        i++
                    }
                    out.append(if (ordered) "</ol>\n" else "</ul>\n")
                }

                i + 1 < lines.size && trimmed.contains('|') && TABLE_RULE.matches(lines[i + 1].trim()) -> {
                    val header = cells(trimmed)
                    out.append("<table><thead><tr>")
                    header.forEach { out.append("<th>").append(inline(it)).append("</th>") }
                    out.append("</tr></thead><tbody>")
                    i += 2
                    while (i < lines.size && lines[i].trim().isNotEmpty() && lines[i].contains('|')) {
                        out.append("<tr>")
                        cells(lines[i].trim()).forEach { out.append("<td>").append(inline(it)).append("</td>") }
                        out.append("</tr>")
                        i++
                    }
                    out.append("</tbody></table>\n")
                }

                else -> {
                    val para = StringBuilder()
                    while (i < lines.size && lines[i].trim().isNotEmpty() && !startsBlock(lines[i], lines.getOrNull(i + 1))) {
                        if (para.isNotEmpty()) para.append('\n')
                        para.append(lines[i].trim())
                        i++
                    }
                    // A line that opened no block but was not consumed above would loop forever.
                    if (para.isEmpty()) { para.append(trimmed); i++ }
                    out.append("<p>").append(inline(para.toString()).replace("\n", "<br>")).append("</p>\n")
                }
            }
        }
        return out.toString()
    }

    private fun startsBlock(line: String, next: String?): Boolean {
        val t = line.trim()
        return t.startsWith("```") || t.startsWith("~~~") || HEADING.matches(t) || RULE.matches(t) ||
            t.startsWith(">") || BULLET.containsMatchIn(line) || ORDERED.containsMatchIn(line) ||
            (t.contains('|') && next != null && TABLE_RULE.matches(next.trim()))
    }

    private fun cells(row: String): List<String> =
        row.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    private fun inline(raw: String): String {
        val codes = ArrayList<String>()
        // Code spans are lifted out first so their contents are never formatted.
        var text = CODE_SPAN.replace(raw) { m ->
            codes += "<code>${escape(m.groupValues[1])}</code>"
            "\u0000${codes.size - 1}\u0000"
        }
        text = escape(text)
        text = IMAGE.replace(text) { m ->
            if (safeUrl(m.groupValues[2])) "<img alt=\"${m.groupValues[1]}\" src=\"${m.groupValues[2]}\">" else m.value
        }
        text = LINK.replace(text) { m ->
            if (safeUrl(m.groupValues[2])) "<a href=\"${m.groupValues[2]}\">${m.groupValues[1]}</a>" else m.groupValues[1]
        }
        text = BOLD.replace(text) { "<strong>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</strong>" }
        text = ITALIC.replace(text) { "<em>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</em>" }
        text = STRIKE.replace(text) { "<del>${it.groupValues[1]}</del>" }
        return PLACEHOLDER.replace(text) { codes[it.groupValues[1].toInt()] }
    }

    /** http(s), mailto, in-page anchors and relative paths. Anything with another scheme is refused. */
    private fun safeUrl(url: String): Boolean {
        val u = url.trim().lowercase()
        if (u.isEmpty()) return false
        val scheme = SCHEME.find(u)?.groupValues?.get(1) ?: return true
        return scheme == "http" || scheme == "https" || scheme == "mailto"
    }

    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val RULE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
    private val BULLET = Regex("""^\s*[-*+]\s+""")
    private val ORDERED = Regex("""^\s*\d+[.)]\s+""")
    private val TABLE_RULE = Regex("""^\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?$""")
    private val CODE_SPAN = Regex("""`([^`\n]+)`""")
    private val IMAGE = Regex("""!\[([^\]]*)]\(([^)\s]+)\)""")
    private val LINK = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")
    private val BOLD = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val ITALIC = Regex("""(?<![*\w])\*(?!\s)(.+?)(?<!\s)\*(?!\*)|(?<![_\w])_(?!\s)(.+?)(?<!\s)_(?![_\w])""")
    private val STRIKE = Regex("""~~(.+?)~~""")
    private val PLACEHOLDER = Regex("""\u0000(\d+)\u0000""")
    private val SCHEME = Regex("""^([a-z][a-z0-9+.-]*):""")

    private const val CSS =
        ":root{color-scheme:light dark}" +
            "body{margin:0;font:16px/1.65 system-ui,-apple-system,Roboto,sans-serif;background:#fff;color:#1c1b1f}" +
            "main{max-width:760px;margin:0 auto;padding:20px 18px 64px}" +
            "h1,h2,h3,h4{line-height:1.25;margin:1.6em 0 .5em}h1{font-size:1.9em}h2{font-size:1.5em;border-bottom:1px solid #8884;padding-bottom:.25em}" +
            "a{color:#0b6bcb}img{max-width:100%;height:auto}hr{border:0;border-top:1px solid #8884;margin:2em 0}" +
            "code{font:.9em ui-monospace,Menlo,Consolas,monospace;background:#8882;padding:.15em .35em;border-radius:5px}" +
            "pre{background:#8882;padding:14px;border-radius:10px;overflow-x:auto}pre code{background:none;padding:0}" +
            "blockquote{margin:1em 0;padding:.1em 1em;border-left:4px solid #8886;opacity:.85}" +
            "table{border-collapse:collapse;display:block;overflow-x:auto}th,td{border:1px solid #8886;padding:6px 12px;text-align:left}" +
            "@media(prefers-color-scheme:dark){body{background:#121212;color:#e6e1e5}a{color:#8ab4f8}}"
}
