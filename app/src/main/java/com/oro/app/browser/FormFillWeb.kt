package com.oro.app.browser

import com.oro.app.data.ai.AiHttp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs

/**
 * fill_form's pure half for the web: which element on a page each requested
 * field is, and what filling it means. No Android types, so it is tested on
 * the JVM; the engine does the acting.
 *
 * A field is found by its own words (label, placeholder, aria -- all folded
 * into [PageElement.label] by the snapshot), then by the field's name/id as
 * they show in its selector ("#email", ".phone-input"), then by a text label
 * sitting just above it or just before it on its row. Each element is used
 * once, so "Email" and "Confirm email" land on two different fields. A name
 * that fits two fields equally well is reported, never guessed -- the same
 * contract the device agent's form filler kept.
 */
internal object FormFillWeb {

    data class Entry(val field: String?, val index: Int?, val value: String)

    sealed interface Match {
        data class Found(val element: PageElement) : Match
        data class Ambiguous(val candidates: List<PageElement>) : Match
        data object Missing : Match
    }

    enum class Kind { TEXT, TOGGLE, CHOICE }

    /** What filling [e] means: typing, setting a check, or picking an option. */
    fun kindOf(e: PageElement): Kind = when {
        e.editable -> Kind.TEXT
        e.checked != null && e.role != "combobox" -> Kind.TOGGLE
        else -> Kind.CHOICE
    }

    /**
     * fill_form's `fields`: a list of {field, index, value}, a
     * {"Label": "value"} object, or either written as JSON text (the text tool
     * protocol). Entries without a value or a way to find the field are dropped.
     */
    fun entries(raw: JsonElement?): List<Entry> {
        val parsed = when (raw) {
            is JsonPrimitive -> raw.contentOrNull?.trim()?.takeIf { it.startsWith("[") || it.startsWith("{") }
                ?.let { runCatching { AiHttp.json.parseToJsonElement(it) }.getOrNull() }
            else -> raw
        }
        return when (parsed) {
            is JsonArray -> parsed.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val value = o.text("value") ?: o.text("text") ?: return@mapNotNull null
                val field = o.text("field") ?: o.text("label") ?: o.text("name")
                val index = (o["index"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.trim()?.toIntOrNull() }
                if (field == null && index == null) null else Entry(field, index, value)
            }
            is JsonObject -> parsed.entries.mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.let { Entry(k.trim().takeIf(String::isNotEmpty), null, it) }
            }.filter { it.field != null }
            else -> emptyList()
        }
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    /**
     * The element [query] names on [page], leaving out [used] ones. Own labels
     * win; a name/id in the selector is a strong hint web forms almost always
     * carry ("Email" → input#email); a text label above or beside a field
     * points at it. Equal best scores on different elements are
     * [Match.Ambiguous].
     */
    fun find(query: String, page: PageState, used: Set<Int> = emptySet()): Match {
        val scores = HashMap<Int, Int>()
        val fillable = page.elements.filter { fillable(it) && it.index !in used }
        for (e in fillable) {
            val own = TextMatchWeb.score(query, e.label)
            if (own > 0) scores.merge(e.index, own, ::maxOf)
            val bySelector = selectorScore(query, e)
            if (bySelector > 0) scores.merge(e.index, bySelector, ::maxOf)
        }
        // A label rendered as its own element above/beside an unnamed box
        // (common in custom-styled forms without <label for>).
        for (t in page.elements) {
            if (t.editable || t.checked != null || t.role == "combobox") continue
            val s = TextMatchWeb.score(query, t.label)
            if (s < LABEL_MIN) continue
            labelled(t, fillable)?.let { scores.merge(it.index, s - BESIDE_PENALTY, ::maxOf) }
        }
        val ranked = scores.entries.filter { it.value >= TextMatchWeb.ACT_THRESHOLD }.sortedByDescending { it.value }
        if (ranked.isEmpty()) return Match.Missing
        val best = ranked.first().value
        val top = ranked.takeWhile { it.value == best }.mapNotNull { r -> page.elements.firstOrNull { it.index == r.key } }
        return if (top.size == 1) Match.Found(top.first()) else Match.Ambiguous(top)
    }

    /** "Email" finds input#email, input[name=email], .email-field: 65, below an exact label. */
    fun selectorScore(query: String, e: PageElement): Int {
        val sel = e.selector
        if (sel.isBlank()) return 0
        val q = TextMatchWeb.normalize(query).replace(' ', '_')
        val q2 = TextMatchWeb.normalize(query).replace(' ', '-')
        val idOrName = Regex("#([A-Za-z0-9_-]+)|\\[name=[\"']?([A-Za-z0-9_-]+)").findAll(sel)
            .map { (it.groupValues[1] + it.groupValues[2]).lowercase() }
            .filter { it.isNotEmpty() }
            .toList()
        for (token in idOrName) {
            val flat = token.replace('_', ' ').replace('-', ' ')
            val s = TextMatchWeb.score(q.replace('_', ' '), flat)
            if (s >= 60) return 65
        }
        val selFlat = sel.lowercase()
        if (q.length >= 3 && (selFlat.contains("#$q") || selFlat.contains("[name=$q") || selFlat.contains(".$q"))) return 55
        if (q2.length >= 3 && selFlat.contains(q2)) return 45
        return 0
    }

    /**
     * The element to act on for [entry]. A check whose value is not a yes/no
     * names a choice ("Gender": "Female"): the radio showing the value,
     * nearest the matched question, not the first box under it.
     */
    fun target(entry: Entry, page: PageState, used: Set<Int> = emptySet()): Match {
        entry.index?.let { i -> page.element(i)?.let { return Match.Found(it) } }
        val m = find(entry.field.orEmpty(), page, used)
        if (wantChecked(entry.value) != null) return m
        return when (m) {
            is Match.Found -> if (m.element.checked == null && m.element.role != "radio") m
            else option(entry.value, page, m.element, used)?.let { Match.Found(it) } ?: m
            Match.Missing -> option(entry.value, page, null, used)?.let { Match.Found(it) } ?: m
            is Match.Ambiguous -> m
        }
    }

    /** The field a text label names: nearest starting just below it, else the next one on its row. */
    fun labelled(label: PageElement, fields: List<PageElement>): PageElement? {
        val lb = label.bounds
        val below = fields.filter { f ->
            val b = f.bounds
            b.top >= lb.top + lb.height / 2 && b.top - lb.bottom <= maxOf(lb.height, b.height) * 2 &&
                b.left < lb.right && b.right > lb.left
        }.minByOrNull { it.bounds.top - lb.bottom }
        if (below != null) return below
        return fields.filter { f ->
            val b = f.bounds
            abs(b.centerY - lb.centerY) <= maxOf(lb.height, b.height) / 2 && b.left >= lb.right - SLACK_PX
        }.minByOrNull { it.bounds.left - lb.right }
    }

    /** Elements a form value can go into: fields, checks/radios, and pickers. */
    fun fillable(e: PageElement): Boolean =
        !e.disabled && (e.editable || e.checked != null || e.role == "combobox" || e.role == "radio")

    /** The wanted state of a check for [value], or null when it is not a yes/no. */
    fun wantChecked(value: String): Boolean? = when (value.trim().lowercase()) {
        "true", "yes", "y", "on", "checked", "check", "1", "agree", "accept", "x", "✓", "✔" -> true
        "false", "no", "n", "off", "unchecked", "uncheck", "0", "disagree", "decline" -> false
        else -> null
    }

    /**
     * For a radio group named by its question ("Gender": "Female"): the radio
     * showing [value], nearest below [near] when given, or null.
     */
    fun option(value: String, page: PageState, near: PageElement? = null, used: Set<Int> = emptySet()): PageElement? =
        page.elements
            .filter { (it.role == "radio" || it.checked != null) && it.index !in used && !it.disabled &&
                TextMatchWeb.score(value, it.label) >= OPTION_MIN }
            .sortedWith(
                compareByDescending<PageElement> { TextMatchWeb.score(value, it.label) }
                    .thenBy { e -> near?.let { n -> abs(e.bounds.top - n.bounds.bottom) } ?: 0 }
            )
            .firstOrNull()

    /**
     * The button that sends the form: the lowest enabled button whose words
     * say submit, nearest [near] when given. Never a "Continue with Google"
     * style third-party button unless nothing else exists -- the device
     * agent's rule, kept.
     */
    fun submitButton(page: PageState, near: PageElement? = null): PageElement? {
        val buttons = page.elements.filter {
            !it.disabled && (it.role == "button" || it.role == "link" || it.inputType == "submit")
        }
        fun submitScore(e: PageElement): Int {
            val l = TextMatchWeb.normalize(e.label)
            if (l.isBlank()) return 0
            if (SUBMIT_WORDS.any { l == it }) return 100
            if (SUBMIT_WORDS.any { l.startsWith(it) }) return 80
            if (SUBMIT_WORDS.any { l.contains(it) }) return 60
            return 0
        }
        fun thirdParty(e: PageElement): Boolean {
            val l = e.label.lowercase()
            return THIRD_PARTY.any { l.contains(it) }
        }
        val ranked = buttons.map { it to submitScore(it) }.filter { it.second > 0 }
            .sortedWith(
                compareBy<Pair<PageElement, Int>> { thirdParty(it.first) }
                    .thenByDescending { it.second }
                    .thenBy { (e, _) -> near?.let { n -> abs(e.bounds.centerY - n.bounds.centerY) } ?: e.bounds.top }
            )
        return ranked.firstOrNull()?.first
    }

    private val SUBMIT_WORDS = listOf(
        "submit", "sign up", "signup", "sign in", "login", "log in", "send", "continue",
        "next", "save", "apply", "register", "confirm", "subscribe", "post", "create account",
        "place order", "checkout", "check out", "pay", "book", "search", "go",
    )

    private val THIRD_PARTY = listOf("google", "facebook", "apple", "twitter", "github", "sso")

    private const val LABEL_MIN = 60
    private const val BESIDE_PENALTY = 10
    private const val OPTION_MIN = 60
    private const val SLACK_PX = 24
}
