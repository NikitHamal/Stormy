package com.oro.app.ui.components.inspector

import java.io.File
import java.net.URLDecoder

/**
 * Writes a text edit made in the live preview back to the one place in the
 * page's HTML source that text came from.
 *
 * The page reports the picked text node as a [Target]: its text (whitespace
 * collapsed), the tag path from `<body>` down to its element, and which of
 * the nodes with that same text and path it is. Here the source is scanned
 * for runs of text between tags (comments, scripts, styles and other raw
 * text skipped), each with the same kind of path, and the run that lines up
 * is rewritten. Its surrounding whitespace, and every other byte of the
 * file, stay as they were.
 *
 * Anything that can't be lined up exactly fails with a reason instead of
 * guessing: text a script wrote isn't in the source at all, and repeated
 * text where the page and the source disagree on the count can't be told
 * apart. The editor this replaces swapped every match in the file (and fell
 * back to a fuzzy match), so fixing one "Learn more" rewrote all of them.
 */
object SourceTextEdit {

    data class Target(
        /** The node's text, whitespace collapsed and trimmed. */
        val text: String,
        /** Tag names from below `<body>` to the node's element, joined by `>`, tbody left out. */
        val path: String,
        /** Which of the page's nodes with this text and path it is, from 0. */
        val occurrence: Int,
        /** How many of the page's nodes have this text and path. */
        val matches: Int,
        /** How many of the page's nodes have this text at all. */
        val total: Int,
    )

    sealed interface Outcome {
        data class Edited(val html: String) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    /** A run of text in the source, [start] until [end], with its decoded [text] and element [path]. */
    internal data class Segment(val start: Int, val end: Int, val text: String, val path: String)

    /** Rewrites the text [target] points at in [html] to [newText], or says why it can't. */
    fun replace(html: String, target: Target, newText: String, fileName: String): Outcome {
        if (target.text.isEmpty()) return Outcome.Failed("There's no text here to edit.")
        val sameText = segments(html).filter { normalize(it.text) == target.text }
        if (sameText.isEmpty()) {
            return Outcome.Failed("This text isn't in $fileName as written; a script may add it. Ask Orobi to change it.")
        }
        val samePath = sameText.filter { it.path == target.path }
        val segment = when {
            samePath.size == target.matches && target.occurrence in samePath.indices -> samePath[target.occurrence]
            sameText.size == 1 && target.total == 1 -> sameText[0]
            else -> return Outcome.Failed(
                "“${clip(target.text)}” is in more than one place and Oro can't tell which you picked. Ask Orobi to change it."
            )
        }
        val raw = html.substring(segment.start, segment.end)
        val lead = raw.takeWhile { it in HTML_SPACE }
        val trail = raw.substring(lead.length).takeLastWhile { it in HTML_SPACE }
        return Outcome.Edited(html.substring(0, segment.start) + lead + escape(newText) + trail + html.substring(segment.end))
    }

    /**
     * The HTML file behind [pagePath] (the page's `location.pathname`), mapped
     * the way the preview server maps it: "/" and folders are their
     * index.html, a path with no extension and no file is the single-page
     * fallback index.html, and a file:// page reports its absolute path. Null
     * when that lands outside [root] or on anything but an HTML file.
     */
    fun pageFile(root: File, pagePath: String): File? {
        val path = runCatching { URLDecoder.decode(pagePath.replace("+", "%2B"), "UTF-8") }.getOrDefault(pagePath)
        val base = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val relative = listOf(base.path, root.absolutePath)
            .firstOrNull { path.startsWith("$it/") }
            ?.let { path.removePrefix("$it/") }
            ?: path.trimStart('/')
        var file = runCatching { File(base, relative).canonicalFile }.getOrNull() ?: return null
        // Like the server, refuse anything outside the project before any fallback.
        if (file != base && !file.path.startsWith(base.path + File.separator)) return null
        if (file.isDirectory) {
            file = File(file, "index.html")
        } else if (!file.exists() && '.' !in relative.substringAfterLast('/')) {
            file = File(base, "index.html")
        }
        return file.takeIf { it.isFile && it.extension.lowercase() in HTML_EXTENSIONS }
    }

    /** Whitespace collapsed the way the inspector script does it, then trimmed. */
    fun normalize(text: String): String = text.replace(SPACE_RUN, " ").trim()

    /** Text as it goes between tags: markup characters escaped, a no-break space kept visible. */
    fun escape(text: String): String = buildString(text.length + 8) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                ' ' -> append("&nbsp;")
                else -> append(c)
            }
        }
    }

    /** Character references decoded: numeric ones, and the named ones pages actually use. */
    fun decodeEntities(raw: String): String {
        if (raw.indexOf('&') < 0) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            val semi = if (c == '&') raw.indexOf(';', i + 1) else -1
            val value = if (semi in (i + 2)..(i + 33)) entityValue(raw.substring(i + 1, semi)) else null
            if (value != null) {
                sb.append(value)
                i = semi + 1
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /**
     * Every run of page text in [html], in document order, with the path of
     * open elements it sits in. A small tag-soup reader: it honours quoted
     * attributes, skips comments and raw-text elements, and applies the
     * implied end tags browsers apply (a block closes an open `<p>`, an
     * `<li>` closes the previous one, and so on), so its paths agree with the
     * page's DOM for ordinary hand-written and generated pages.
     */
    internal fun segments(html: String): List<Segment> {
        val out = ArrayList<Segment>()
        val stack = ArrayList<String>()
        // Without a <body> tag, everything outside <head> is body content.
        var inBody = !BODY_START.containsMatchIn(html)
        var inHead = false
        var textStart = 0
        var i = 0
        val n = html.length

        fun emit(end: Int) {
            if (!inBody || inHead || end <= textStart) return
            val decoded = decodeEntities(html.substring(textStart, end))
            if (normalize(decoded).isEmpty()) return
            out.add(Segment(textStart, end, decoded, stack.filter { it != "tbody" }.joinToString(">")))
        }

        while (i < n) {
            if (html[i] != '<') {
                i++
                continue
            }
            val next = if (i + 1 < n) html[i + 1] else ' '
            when {
                html.startsWith("<!--", i) -> {
                    emit(i)
                    val close = html.indexOf("-->", i + 4)
                    i = if (close < 0) n else close + 3
                    textStart = i
                }
                next == '!' || next == '?' -> {
                    emit(i)
                    val close = html.indexOf('>', i + 2)
                    i = if (close < 0) n else close + 1
                    textStart = i
                }
                next == '/' && i + 2 < n && html[i + 2].isLetter() -> {
                    val nameEnd = nameEnd(html, i + 2)
                    val name = html.substring(i + 2, nameEnd).lowercase()
                    val gt = html.indexOf('>', nameEnd)
                    emit(i)
                    when (name) {
                        "head" -> inHead = false
                        "body", "html" -> Unit
                        else -> {
                            val at = stack.lastIndexOf(name)
                            if (at >= 0) truncate(stack, at)
                        }
                    }
                    i = if (gt < 0) n else gt + 1
                    textStart = i
                }
                next.isLetter() -> {
                    val nameEnd = nameEnd(html, i + 1)
                    val name = html.substring(i + 1, nameEnd).lowercase()
                    val gt = tagEnd(html, nameEnd)
                    if (gt < 0) {
                        // An unterminated tag runs to the end: nothing after it is text.
                        emit(i)
                        textStart = n
                        i = n
                        continue
                    }
                    emit(i)
                    i = gt + 1
                    textStart = i
                    when {
                        name == "html" -> Unit
                        name == "head" -> inHead = true
                        name == "body" -> {
                            inBody = true
                            inHead = false
                            stack.clear()
                        }
                        name in RAW_TEXT -> {
                            // Nothing inside is page text; carry on after its end tag.
                            val close = endTagAt(html, name, i)
                            i = if (close < 0) n else (html.indexOf('>', close).takeIf { it >= 0 }?.plus(1) ?: n)
                            textStart = i
                        }
                        else -> {
                            if (inHead && name !in HEAD_CONTENT) inHead = false
                            if (!inHead) {
                                closeImplied(stack, name)
                                val foreign = "svg" in stack || "math" in stack || name == "svg" || name == "math"
                                val selfClosing = html[gt - 1] == '/' && foreign
                                if (name !in VOID && !selfClosing) stack.add(name)
                            }
                        }
                    }
                }
                else -> i++ // A bare '<' is text.
            }
        }
        emit(n)
        return out
    }

    private const val HTML_SPACE = " \t\n\r\u000C"
    private val SPACE_RUN = Regex("[ \\t\\n\\r\\u000C]+")
    private val BODY_START = Regex("<body[\\s/>]", RegexOption.IGNORE_CASE)
    private val HTML_EXTENSIONS = setOf("html", "htm")

    private val RAW_TEXT = setOf(
        "script", "style", "textarea", "title", "noscript", "template", "iframe", "noembed", "noframes", "xmp"
    )
    private val VOID = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "keygen", "link", "meta", "param",
        "source", "track", "wbr"
    )
    private val HEAD_CONTENT = setOf("base", "link", "meta", "script", "style", "title", "noscript", "template")
    private val CLOSES_P = setOf(
        "address", "article", "aside", "blockquote", "center", "dd", "details", "dialog", "dir", "div", "dl",
        "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6",
        "header", "hgroup", "hr", "li", "listing", "main", "menu", "nav", "ol", "p", "pre", "search",
        "section", "summary", "table", "ul"
    )
    private val SCOPE = setOf("applet", "button", "caption", "html", "marquee", "object", "table", "td", "th", "template", "svg", "math")
    private val TABLE_SCOPE = setOf("table", "html", "template")
    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

    /** The end tags browsers imply when [name] starts. */
    private fun closeImplied(stack: MutableList<String>, name: String) {
        if (name in CLOSES_P) closeInScope(stack, setOf("p"), SCOPE)
        when (name) {
            "li" -> closeInScope(stack, setOf("li"), SCOPE + setOf("ol", "ul"))
            "dd", "dt" -> closeInScope(stack, setOf("dd", "dt"), SCOPE + "dl")
            "tr" -> closeInScope(stack, setOf("tr"), TABLE_SCOPE)
            "td", "th" -> closeInScope(stack, setOf("td", "th"), TABLE_SCOPE + "tr")
            "thead", "tbody", "tfoot" -> closeInScope(stack, setOf("thead", "tbody", "tfoot"), TABLE_SCOPE)
            "option" -> if (stack.lastOrNull() == "option") truncate(stack, stack.size - 1)
            "optgroup" -> {
                if (stack.lastOrNull() == "option") truncate(stack, stack.size - 1)
                if (stack.lastOrNull() == "optgroup") truncate(stack, stack.size - 1)
            }
            in HEADINGS -> if (stack.lastOrNull() in HEADINGS) truncate(stack, stack.size - 1)
        }
    }

    /** Closes the nearest open element in [names], unless a [boundary] element is open above it. */
    private fun closeInScope(stack: MutableList<String>, names: Set<String>, boundary: Set<String>) {
        for (k in stack.indices.reversed()) {
            val open = stack[k]
            if (open in names) {
                truncate(stack, k)
                return
            }
            if (open in boundary) return
        }
    }

    private fun truncate(stack: MutableList<String>, size: Int) {
        while (stack.size > size) stack.removeAt(stack.size - 1)
    }

    private fun nameEnd(html: String, from: Int): Int {
        var i = from
        while (i < html.length && html[i] !in HTML_SPACE && html[i] != '/' && html[i] != '>') i++
        return i
    }

    /** The '>' closing a start tag; quotes only count where they open an attribute value. */
    private fun tagEnd(html: String, from: Int): Int {
        var i = from
        val n = html.length
        while (i < n) {
            when (html[i]) {
                '>' -> return i
                '=' -> {
                    i++
                    while (i < n && html[i] in HTML_SPACE) i++
                    if (i < n && (html[i] == '"' || html[i] == '\'')) {
                        val close = html.indexOf(html[i], i + 1)
                        if (close < 0) return -1
                        i = close + 1
                    }
                }
                else -> i++
            }
        }
        return -1
    }

    /** Where `</name` starts at or after [from], case-insensitively, or -1. */
    private fun endTagAt(html: String, name: String, from: Int): Int {
        var i = html.indexOf("</", from)
        while (i >= 0) {
            val after = i + 2 + name.length
            if (html.regionMatches(i + 2, name, 0, name.length, ignoreCase = true) &&
                (after >= html.length || html[after] in HTML_SPACE || html[after] == '>' || html[after] == '/')
            ) return i
            i = html.indexOf("</", i + 2)
        }
        return -1
    }

    private fun entityValue(body: String): String? = when {
        body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let(::codePoint)
        body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePoint)
        else -> NAMED[body]
    }

    private fun codePoint(cp: Int): String =
        if (cp in 1..0x10FFFF && cp !in 0xD800..0xDFFF) String(Character.toChars(cp)) else "�"

    private fun clip(text: String): String = if (text.length <= 40) text else text.take(39).trimEnd() + "…"

    /** Latin-1 names in code point order from 160, then the rest pages commonly use. */
    private val NAMED: Map<String, String> = buildMap {
        val latin1 = "nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr deg " +
            "plusmn sup2 sup3 acute micro para middot cedil sup1 ordm raquo frac14 frac12 frac34 iquest " +
            "Agrave Aacute Acirc Atilde Auml Aring AElig Ccedil Egrave Eacute Ecirc Euml Igrave Iacute Icirc " +
            "Iuml ETH Ntilde Ograve Oacute Ocirc Otilde Ouml times Oslash Ugrave Uacute Ucirc Uuml Yacute " +
            "THORN szlig agrave aacute acirc atilde auml aring aelig ccedil egrave eacute ecirc euml igrave " +
            "iacute icirc iuml eth ntilde ograve oacute ocirc otilde ouml divide oslash ugrave uacute ucirc " +
            "uuml yacute thorn yuml"
        latin1.split(' ').forEachIndexed { k, name -> put(name, (160 + k).toChar().toString()) }
        mapOf(
            "quot" to 34, "amp" to 38, "apos" to 39, "lt" to 60, "gt" to 62, "OElig" to 338, "oelig" to 339,
            "Scaron" to 352, "scaron" to 353, "Yuml" to 376, "fnof" to 402, "circ" to 710, "tilde" to 732,
            "ensp" to 8194, "emsp" to 8195, "thinsp" to 8201, "zwnj" to 8204, "zwj" to 8205, "lrm" to 8206,
            "rlm" to 8207, "ndash" to 8211, "mdash" to 8212, "lsquo" to 8216, "rsquo" to 8217, "sbquo" to 8218,
            "ldquo" to 8220, "rdquo" to 8221, "bdquo" to 8222, "dagger" to 8224, "Dagger" to 8225,
            "bull" to 8226, "hellip" to 8230, "permil" to 8240, "prime" to 8242, "Prime" to 8243,
            "lsaquo" to 8249, "rsaquo" to 8250, "oline" to 8254, "frasl" to 8260, "euro" to 8364,
            "trade" to 8482, "larr" to 8592, "uarr" to 8593, "rarr" to 8594, "darr" to 8595, "harr" to 8596,
            "crarr" to 8629, "lArr" to 8656, "uArr" to 8657, "rArr" to 8658, "dArr" to 8659, "hArr" to 8660,
            "minus" to 8722, "infin" to 8734, "sim" to 8764, "asymp" to 8776, "ne" to 8800, "le" to 8804,
            "ge" to 8805, "loz" to 9674, "spades" to 9824, "clubs" to 9827, "hearts" to 9829,
            "diams" to 9830, "starf" to 9733, "star" to 9734, "check" to 10003, "cross" to 10007
        ).forEach { (name, cp) -> put(name, String(Character.toChars(cp))) }
    }
}
