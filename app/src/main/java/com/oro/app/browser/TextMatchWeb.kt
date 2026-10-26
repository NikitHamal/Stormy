package com.oro.app.browser

/**
 * Finds the element a model means when it names it by its words.
 *
 * The web port of the device agent's tiered matcher, with the same contract:
 * weak models copy labels far more reliably than numbers out of a long
 * listing, and they paraphrase ("Sign in" for "Log in"). Matching is tiered
 * -- exact, a segment of the label, prefix, a cut-short title, whole word,
 * then (offered, never acted on) part of a word, all words, small typos --
 * and never guesses between equally good candidates: an ambiguous name is
 * answered with the candidates and their numbers, so the model picks instead
 * of Oro clicking the wrong "Delete".
 *
 * Part of a word is below [ACT_THRESHOLD] on purpose: "Add" must not click a
 * lone "Address" link, nor "Go" a "Category" tab.
 */
object TextMatchWeb {

    data class Hit(val element: PageElement, val score: Int)

    sealed interface Result {
        data class Found(val element: PageElement, val score: Int) : Result
        /** Several equally good matches, or ([weak]) only poor ones. */
        data class Ambiguous(val candidates: List<PageElement>, val weak: Boolean = false) : Result
        data object NotFound : Result
    }

    /** Minimum score for click-by-text to act without asking. */
    const val ACT_THRESHOLD = 45

    /** Common web verb pairs: a model says one, the page shows the other. */
    private val SYNONYMS = listOf(
        setOf("sign in", "log in", "login", "signin"),
        setOf("sign up", "register", "create account", "signup", "join"),
        setOf("sign out", "log out", "logout", "signout"),
        setOf("submit", "send", "go", "continue", "next"),
        setOf("search", "find"),
        setOf("buy", "purchase", "order"),
        setOf("cart", "basket", "bag"),
        setOf("cancel", "close", "dismiss"),
    )

    fun normalize(s: String): String = s.lowercase()
        .replace('\u00a0', ' ')
        .replace(Regex("[\"“”‘’'`]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.', ':', '!', '?', '›', '>', '…')
        .trim()

    /** How well [label] matches [query]: 0 for no match, 100 for exact. */
    fun score(query: String, label: String): Int {
        val cutShort = label.trimEnd().endsWith("…") || label.trimEnd().endsWith("...")
        val q = normalize(query)
        val l = normalize(label)
        if (q.isEmpty() || l.isEmpty()) return 0
        if (l == q) return 100
        if (synonymous(q, l)) return 95
        val segments = (l.split(" · ", "|", " - ", " – ", "/").map { it.trim() }).filter { it.isNotEmpty() }
        if (segments.size > 1 && segments.any { it == q }) return 90
        if (l.startsWith("$q ") || segments.any { it.startsWith("$q ") }) return 75
        // The snapshot cut a long title and the model copied it whole.
        if (cutShort && l.length >= MIN_CUT_PREFIX && q.startsWith(l.trimEnd('.', '…'))) return 70
        if (wholeWord(q).containsMatchIn(l)) return 60
        if (l.contains(q)) return if (wordStart(q).containsMatchIn(l)) 30 else 20
        val words = q.split(' ').filter { it.length > 1 }
        if (words.size > 1 && words.all { l.contains(it) }) return 40
        if (q.length >= 4) {
            val bestSegment = (segments.ifEmpty { listOf(l) }).minOf { levenshtein(it, q) }
            if (bestSegment <= maxOf(1, q.length / 5)) return 35
        }
        return 0
    }

    private fun synonymous(q: String, l: String): Boolean =
        SYNONYMS.any { set -> set.any { normalize(it) == q } && set.any { normalize(it) == l } }

    /** All matches on [page], best first; enabled elements win ties. */
    fun hits(query: String, page: PageState): List<Hit> = page.elements.asSequence()
        .map { e ->
            val byLabel = score(query, e.label)
            val byValue = e.value?.takeIf { !e.password }?.let { score(query, it) - 5 } ?: 0
            val bySelected = e.selected?.let { score(query, it) - 5 } ?: 0
            Hit(e, maxOf(byLabel, byValue, bySelected))
        }
        .filter { it.score > 0 }
        .sortedWith(
            compareByDescending<Hit> { it.score }
                .thenByDescending { !it.element.disabled }
                .thenBy { it.element.index }
        )
        .toList()

    /**
     * The one element [query] names. [nth] (1-based) picks among equally good
     * matches in reading order when the model knows there are several.
     */
    fun find(query: String, page: PageState, nth: Int? = null, threshold: Int = ACT_THRESHOLD): Result {
        val all = hits(query, page)
        val best = all.firstOrNull() ?: return Result.NotFound
        if (best.score < threshold) return Result.Ambiguous(all.take(5).map { it.element }, weak = true)
        val top = all.filter { it.score == best.score }
        val enabled = top.filter { !it.element.disabled }
        val pool = (if (enabled.isNotEmpty()) enabled else top).map { it.element }.sortedBy { it.index }
        if (nth != null) {
            return pool.getOrNull(nth - 1)?.let { Result.Found(it, best.score) } ?: Result.Ambiguous(pool.take(5))
        }
        return if (pool.size == 1) Result.Found(pool.first(), best.score) else Result.Ambiguous(pool.take(5))
    }

    private fun wholeWord(q: String) = Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(q) + "($|[^\\p{L}\\p{N}])")

    private fun wordStart(q: String) = Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(q))

    /** A cut-short label shorter than this is too little to stand for a whole title. */
    private const val MIN_CUT_PREFIX = 12

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
