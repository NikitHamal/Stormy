package com.oro.app.device

import com.oro.app.data.ai.AiHttp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs

/**
 * fill_form's pure half: which element on a screen each requested field is,
 * and what filling it means. No Android types, so it is tested on the JVM;
 * the run does the acting.
 *
 * A field is found by its own name (hint, description, the label that names
 * it), else by the text label just above it or just before it on its row --
 * how most web and app forms lay out "Email ____". Each element is used once,
 * so "Email" and "Confirm email" land on two different fields. A name that
 * fits two fields equally well is reported, never guessed.
 */
internal object FormFill {

    data class Entry(val field: String?, val index: Int?, val value: String)

    sealed interface Match {
        data class Found(val element: ScreenElement) : Match
        data class Ambiguous(val candidates: List<ScreenElement>) : Match
        data object Missing : Match
    }

    enum class Kind { TEXT, TOGGLE, CHOICE }

    /** What filling [e] means: typing, setting a check, or opening it and picking an option. */
    fun kindOf(e: ScreenElement): Kind = when {
        e.editable -> Kind.TEXT
        e.checkable -> Kind.TOGGLE
        else -> Kind.CHOICE
    }

    /**
     * fill_form's `fields`: a list of {field (or label/name), index, value}, a
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

    /**
     * The element [query] names on [screen], leaving out [used] ones. Own
     * names win over labels beside a field; equal best scores on different
     * elements are [Match.Ambiguous].
     */
    fun find(query: String, screen: ScreenState, used: Set<Int> = emptySet()): Match {
        val scores = HashMap<Int, Int>()
        val fillable = screen.elements.filter { fillable(it) && it.index !in used }
        for (e in fillable) {
            val own = TextMatch.score(query, e.label)
            if (own > 0) scores.merge(e.index, own, ::maxOf)
        }
        // "Email" written above or before its box: the box nearest that label.
        for (t in screen.elements) {
            if (t.role != ScreenElement.ROLE_TEXT) continue
            val s = TextMatch.score(query, t.label)
            if (s < LABEL_MIN) continue
            labelled(t, fillable)?.let { scores.merge(it.index, s - BESIDE_PENALTY, ::maxOf) }
        }
        val ranked = scores.entries.filter { it.value >= TextMatch.ACT_THRESHOLD }.sortedByDescending { it.value }
        if (ranked.isEmpty()) return Match.Missing
        val best = ranked.first().value
        val top = ranked.takeWhile { it.value == best }.mapNotNull { r -> screen.elements.firstOrNull { it.index == r.key } }
        return if (top.size == 1) Match.Found(top.first()) else Match.Ambiguous(top)
    }

    /**
     * The element to act on for [entry]. A check whose value is not a yes/no
     * names a choice ("Gender": "Female"): the option showing the value,
     * nearest the matched question, not the first box under it.
     */
    fun target(entry: Entry, screen: ScreenState, used: Set<Int> = emptySet()): Match {
        val m = find(entry.field.orEmpty(), screen, used)
        if (wantChecked(entry.value) != null) return m
        return when (m) {
            is Match.Found -> if (!m.element.checkable) m else option(entry.value, screen, m.element, used)?.let { Match.Found(it) } ?: m
            Match.Missing -> option(entry.value, screen, null, used)?.let { Match.Found(it) } ?: m
            is Match.Ambiguous -> m
        }
    }

    /** The field a text label names: the nearest one starting just below it, else the next one on its row. */
    fun labelled(label: ScreenElement, fields: List<ScreenElement>): ScreenElement? {
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

    /** Elements a form value can go into: text fields, checks, and pickers (dropdowns, date buttons). */
    fun fillable(e: ScreenElement): Boolean =
        e.enabled && (e.editable || e.checkable || (e.clickable && e.role in PICKER_ROLES))

    /** The wanted state of a check for [value], or null when it is not a yes/no. */
    fun wantChecked(value: String): Boolean? = when (value.trim().lowercase()) {
        "true", "yes", "y", "on", "checked", "check", "1", "agree", "accept", "x", "✓", "✔" -> true
        "false", "no", "n", "off", "unchecked", "uncheck", "0", "disagree", "decline" -> false
        else -> null
    }

    /**
     * For a radio group named by its question ("Gender": "Female"): the option
     * showing [value], nearest below [near] when given, or null.
     */
    fun option(value: String, screen: ScreenState, near: ScreenElement? = null, used: Set<Int> = emptySet()): ScreenElement? =
        screen.elements
            .filter { it.checkable && it.index !in used && TextMatch.score(value, it.label) >= OPTION_MIN }
            .sortedWith(
                compareByDescending<ScreenElement> { TextMatch.score(value, it.label) }
                    .thenBy { e -> near?.let { n -> abs(e.bounds.top - n.bounds.bottom) } ?: 0 }
            )
            .firstOrNull()

    /**
     * The button that sends the form: the lowest enabled button whose words
     * say submit (the keyboard's keys are never in the capture).
     */
    fun submitButton(screen: ScreenState): ScreenElement? = screen.elements
        .filter { it.clickable && it.enabled && !it.editable }
        .filter { TextMatch.normalize(it.label).let { l -> SUBMIT.containsMatchIn(l) && !THIRD_PARTY.containsMatchIn(l) } && it.label.length <= MAX_BUTTON_LABEL }
        .maxByOrNull { it.bounds.top }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** A label beside its field scores a little under the field's own name. */
    private const val BESIDE_PENALTY = 5
    private const val LABEL_MIN = 60
    /** How well an option must show the value to be picked. */
    const val OPTION_MIN = 60
    private const val SLACK_PX = 24
    private const val MAX_BUTTON_LABEL = 40
    private val PICKER_ROLES = setOf("button", "view", "spinner", "dropdown")
    /** "Continue with Google": another way in, not this form's button. */
    private val THIRD_PARTY = Regex("\\bwith (google|apple|facebook|microsoft|github|x|twitter|email|phone|passkey)\\b")
    private val SUBMIT = Regex(
        "^(submit|continue|next|save|done|apply|confirm|sign up|signup|register|create account|create|join|" +
            "sign in|log in|login|send|book|reserve|search|get started|proceed|update|ok)\\b"
    )
}
