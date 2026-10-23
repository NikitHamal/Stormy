package com.oro.app.device

/**
 * Finds the element a model means when it names it by its words.
 *
 * Weak models are far better at copying a label ("Add to cart") than at
 * copying the right number out of a 120-line listing, and they paraphrase
 * ("Add to basket"), change case, or include the punctuation. Matching is
 * tiered -- exact, a whole segment of a row label, prefix, the full title of
 * a label cut short with "…", whole word, then (offered, never acted on) part
 * of a word, all words, small typos -- and never guesses between equally good
 * candidates: an ambiguous name is answered with the candidates and their
 * numbers, so the model picks instead of Oro tapping the wrong "Delete".
 *
 * Part of a word is below [ACT_THRESHOLD] on purpose: "Add" must not tap a
 * lone "Address" field, nor "Go" a "Category" button.
 */
object TextMatch {

    data class Hit(val element: ScreenElement, val score: Int)

    sealed interface Result {
        data class Found(val element: ScreenElement, val score: Int) : Result
        /** Several equally good matches, or ([weak]) only poor ones. */
        data class Ambiguous(val candidates: List<ScreenElement>, val weak: Boolean = false) : Result
        data object NotFound : Result
    }

    /** Minimum score for tap_text to act without asking. */
    const val ACT_THRESHOLD = 45

    fun normalize(s: String): String = s.lowercase()
        .replace(' ', ' ')
        .replace(Regex("[\"“”‘’'`]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.', ':', '!', '?', '›', '>', '…')
        .trim()

    /** How well [label] matches [query]: 0 for no match, 100 for exact. */
    fun score(query: String, label: String): Int {
        val cutShort = label.trimEnd().endsWith("…")
        val q = normalize(query)
        val l = normalize(label)
        if (q.isEmpty() || l.isEmpty()) return 0
        if (l == q) return 100
        val segments = l.split(" · ").map { it.trim() }
        if (segments.size > 1 && segments.any { it == q }) return 90
        if (l.startsWith("$q ") || segments.any { it.startsWith("$q ") }) return 75
        // The listing cut a long title ("lofi hip hop radio - beats to relax/stu…")
        // and the model copied it whole: the visible part is the match.
        if (cutShort && l.length >= MIN_CUT_PREFIX && q.startsWith(l)) return 70
        if (wholeWord(q).containsMatchIn(l)) return 60
        if (l.contains(q)) return if (wordStart(q).containsMatchIn(l)) 30 else 20
        val words = q.split(' ').filter { it.length > 1 }
        if (words.size > 1 && words.all { l.contains(it) }) return 40
        if (q.length >= 4) {
            val bestSegment = segments.minOf { levenshtein(it, q) }
            if (bestSegment <= maxOf(1, q.length / 5)) return 35
        }
        return 0
    }

    /** All matches on [screen], best first; actionable elements win ties over plain text. */
    fun hits(query: String, screen: ScreenState): List<Hit> = screen.elements.asSequence()
        .map { e -> Hit(e, maxOf(score(query, e.label), e.value?.let { score(query, it) - 5 } ?: 0)) }
        .filter { it.score > 0 }
        .sortedWith(
            compareByDescending<Hit> { it.score }
                .thenByDescending { it.element.actionable && it.element.enabled }
                .thenBy { it.element.index }
        )
        .toList()

    /**
     * The one element [query] names. [nth] (1-based) picks among equally good
     * matches in reading order when the model knows there are several.
     */
    fun find(query: String, screen: ScreenState, nth: Int? = null, threshold: Int = ACT_THRESHOLD): Result {
        val all = hits(query, screen)
        val best = all.firstOrNull() ?: return Result.NotFound
        if (best.score < threshold) return Result.Ambiguous(all.take(5).map { it.element }, weak = true)
        val top = all.filter { it.score == best.score }
        // A plain text and the button that contains the same words are one
        // target, not two: prefer the actionable one.
        val actionableTop = top.filter { it.element.actionable }
        val pool = (if (actionableTop.isNotEmpty()) actionableTop else top).map { it.element }.sortedBy { it.index }
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
