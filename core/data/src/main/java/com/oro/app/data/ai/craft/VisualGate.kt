package com.oro.app.data.ai.craft

/**
 * What the app learns by rendering the pages a run changed, in two strengths:
 *
 *  - [roundNote]: after a round of tool calls that changed pages, styles or
 *    scripts, a one-paragraph account of how each changed page measured at
 *    phone width, added to the tool results. Informational: soft findings
 *    (small tap targets, low contrast) are included, as the screenshot tool
 *    would report them.
 *  - [review]: the final check's visual half. Only hard defects send the
 *    agent back -- the page scrolls sideways, images do not load, elements
 *    meant to be hidden show -- because, like the static gate, a false
 *    positive costs every model a turn and teaches it to ignore the gate.
 *
 * Input is the metrics text the previewer reports for each page
 * ([com.oro.app.data.preview.PageMetrics.describe], also what
 * screenshot_preview shows), keyed by page path.
 */
object VisualGate {

    /** Pages rendered per round or per check at most: a render costs the phone a second or two. */
    const val MAX_PAGES = 2

    private val PAGE = Regex("""\.html?$""", RegexOption.IGNORE_CASE)
    private val STYLE_OR_SCRIPT = Regex("""\.(css|js|mjs)$""", RegexOption.IGNORE_CASE)

    /**
     * The pages worth rendering after [changed] project paths changed: the
     * changed HTML pages, else index.html when styles or scripts changed (they
     * show through the main page). [exists] says whether a path is a file.
     */
    fun pagesFor(changed: Collection<String>, exists: (String) -> Boolean): List<String> {
        val paths = changed.map { it.trim().removePrefix("./").trimStart('/') }.filter { it.isNotEmpty() }.distinct()
        val pages = paths.filter { PAGE.containsMatchIn(it) && exists(it) }
        if (pages.isNotEmpty()) {
            // The main page first: it is what the user opens.
            return pages.sortedBy { if (it.equals("index.html", ignoreCase = true)) 0 else 1 }.take(MAX_PAGES)
        }
        return if (paths.any { STYLE_OR_SCRIPT.containsMatchIn(it) } && exists("index.html")) listOf("index.html") else emptyList()
    }

    /**
     * The hard defects in one page's metrics text: the page scrolls sideways,
     * images do not load, hidden elements show, or text is all but invisible
     * (contrast under [INVISIBLE_RATIO]:1 -- a header button whose label took
     * the nav colour, say). Ordinary low contrast stays a note: a background
     * measured under a photo can be wrong, and a false alarm costs a turn.
     */
    fun hardDefects(metrics: String): List<String> = metrics.lineSequence().map { it.trim() }.mapNotNull { line ->
        when {
            line.startsWith("HORIZONTAL OVERFLOW") ||
                line.startsWith("Elements reaching past the right edge") ||
                IMAGES_FAILED.containsMatchIn(line) ||
                HIDDEN_SHOWN.containsMatchIn(line) -> line
            LOW_CONTRAST.containsMatchIn(line) -> invisibleText(line)
            else -> null
        }
    }.toList()

    /** Contrast below this is unreadable on any screen; WCAG's minimum for large text is 3:1. */
    const val INVISIBLE_RATIO = 2.0

    private val IMAGES_FAILED = Regex("""^\d+ images? failed to load""")
    private val HIDDEN_SHOWN = Regex("""^\d+ elements? ha(s|ve) the hidden attribute but (is|are) displayed""")
    private val LOW_CONTRAST = Regex("""^\d+ text elements? with low contrast""")
    private val RATIO = Regex("""(\d+(?:\.\d+)?):1\b""")

    /** "Text almost invisible: a.btn "Book" 1.5:1" when an example falls under [INVISIBLE_RATIO]. */
    private fun invisibleText(line: String): String? {
        val examples = line.substringAfter(", e.g. ", "").removeSuffix(".").split(", ").filter { it.isNotBlank() }
        val bad = examples.filter { ex -> RATIO.findAll(ex).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull()?.let { it < INVISIBLE_RATIO } == true }
        return if (bad.isEmpty()) null else "Text almost invisible (contrast under ${INVISIBLE_RATIO.toInt()}:1): ${bad.joinToString(", ")}. Give it a colour that contrasts with what is behind it."
    }

    /** The previewer could not measure: say nothing rather than guess. */
    private fun unmeasured(metrics: String) = metrics.isBlank() || metrics.startsWith("(could not")

    /**
     * The note added after a round: each rendered page and what it measured,
     * or null when nothing could be measured. [pages] maps page path to its
     * metrics text at phone width.
     */
    fun roundNote(pages: Map<String, String>): String? {
        val measured = pages.filterValues { !unmeasured(it) }
        if (measured.isEmpty()) return null
        return buildString {
            append("[Oro: the app rendered the changed page")
            if (measured.size > 1) append('s')
            append(" on a 390px-wide phone screen.")
            measured.forEach { (page, metrics) ->
                val findings = findings(metrics)
                append('\n').append(page).append(": ")
                if (findings.isEmpty()) append("no layout problems measured.")
                else append(findings.joinToString(" "))
            }
            if (measured.values.any { hardDefects(it).isNotEmpty() }) append("\nFix the overflow, broken images, hidden elements or invisible text first.")
            append(']')
        }
    }

    /** Everything worth saying from one page's metrics: all but the size line and the title. */
    private fun findings(metrics: String): List<String> = metrics.lineSequence().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("CSS viewport") && !it.startsWith("Title:") }
        .toList()

    /**
     * The final check's visual verdict: null when the rendered pages have no
     * hard defects, else instructions listing them.
     */
    fun review(pages: Map<String, String>): String? {
        val defects = pages.filterValues { !unmeasured(it) }.mapValues { hardDefects(it.value) }.filterValues { it.isNotEmpty() }
        if (defects.isEmpty()) return null
        return buildString {
            append("Automatic quality review (by the Oro app, not the user) rendered your pages and found layout problems:\n")
            defects.forEach { (page, list) -> list.forEach { append("- ").append(page).append(": ").append(it).append('\n') } }
            append(
                "\nFix each one now, then reply with your final summary for the user. A page that scrolls " +
                    "sideways usually has a fixed width, a 100vw box, or an element pushed past the edge; an image " +
                    "that fails to load needs a working URL (or remove it)."
            )
        }
    }
}
