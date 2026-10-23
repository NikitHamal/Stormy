package com.oro.app.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * One suggested answer. [description] is optional helper text; [recommended]
 * marks the option the agent suggests, which the card pre-picks so accepting
 * it is a single tap.
 */
data class QuestionOption(
    val label: String,
    val description: String = "",
    val recommended: Boolean = false
)

/**
 * One question in an `ask_user` call.
 *
 * Models can batch several questions into one call (Claude Code's
 * AskUserQuestion shape); the user answers them in one card and the whole set
 * goes back as a single tool result, so the model gets every answer in one
 * turn instead of paying a round trip per question.
 */
data class UserQuestion(
    val question: String,
    /** A 1-3 word label ("Framework"), shown above the question. */
    val header: String = "",
    val options: List<QuestionOption> = emptyList(),
    val multiSelect: Boolean = false,
    val allowFreeText: Boolean = true
)

object UserQuestions {

    const val MAX_QUESTIONS = 6
    private const val MAX_OPTIONS = 8

    /** How the result of a card the user handed back with "You decide" starts. */
    const val DECIDE_PREFIX = "The user tapped \"You decide\""

    /**
     * The tool result when the user lets the agent choose: proceed, don't ask
     * again, and say what was chosen so the user can change it afterwards.
     */
    const val DECIDE_RESULT = "$DECIDE_PREFIX: choose sensible defaults yourself (your recommended " +
        "options where you gave them), don't ask again, and list the choices you made in one line in your " +
        "final summary."

    /**
     * Questions from either shape:
     * - `{"questions":[{"question","header","options":[{"label","description","recommended"}|"text"],"multiSelect"}]}`
     * - the old `{"question","options":[...]|"a, b"}`
     * `questions` may also arrive as a JSON string (models double-encode) or,
     * from text-protocol models, as plain prose ("1. Name? 2. Style? (a, b)").
     */
    fun parse(args: JsonObject): List<UserQuestion> {
        val batch = when (val q = args["questions"]) {
            is JsonArray -> q
            is JsonPrimitive -> when (val el = runCatching { Json.parseToJsonElement(q.content) }.getOrNull()) {
                is JsonArray -> el
                is JsonObject -> return listOfNotNull(parseOne(el))
                else -> return fromProse(q.content).ifEmpty { listOfNotNull(parseOne(args)) }
            }
            else -> null
        }
        val parsed = batch?.mapNotNull { item ->
            when (item) {
                is JsonObject -> parseOne(item)
                is JsonPrimitive -> item.content.trim().takeIf { it.isNotEmpty() }?.let { UserQuestion(it) }
                else -> null
            }
        }.orEmpty()
        if (parsed.isNotEmpty()) return parsed.take(MAX_QUESTIONS)
        return listOfNotNull(parseOne(args))
    }

    private fun parseOne(obj: JsonObject): UserQuestion? {
        val text = obj.string("question") ?: obj.string("prompt") ?: obj.string("text") ?: return null
        val recommendedLabel = obj.string("recommended") ?: obj.string("default")
        val options = parseOptions(obj["options"] ?: obj["choices"]).let { opts ->
            if (recommendedLabel == null || opts.any { it.recommended }) opts
            else opts.map { if (it.label.equals(recommendedLabel, ignoreCase = true)) it.copy(recommended = true) else it }
        }
        val multi = obj.bool("multiSelect") ?: obj.bool("multi_select") ?: false
        return UserQuestion(
            question = text,
            header = obj.string("header")?.take(24).orEmpty(),
            // A single choice has one suggestion at most: the first one marked.
            options = if (multi) options else keepFirstRecommended(options),
            multiSelect = multi,
            allowFreeText = obj.bool("allowFreeText") ?: obj.bool("allow_free_text") ?: true
        )
    }

    private fun keepFirstRecommended(options: List<QuestionOption>): List<QuestionOption> {
        val first = options.indexOfFirst { it.recommended }
        return if (first < 0) options else options.mapIndexed { i, o -> if (i == first || !o.recommended) o else o.copy(recommended = false) }
    }

    private fun parseOptions(element: JsonElement?): List<QuestionOption> {
        val items: List<QuestionOption> = when (element) {
            is JsonArray -> element.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> QuestionOption(item.content)
                    is JsonObject -> (item.string("label") ?: item.string("value") ?: item.string("text"))
                        ?.let {
                            QuestionOption(
                                it, item.string("description").orEmpty(),
                                recommended = item.bool("recommended") ?: item.bool("recommend") ?: item.bool("default") ?: false
                            )
                        }
                    else -> null
                }
            }
            is JsonPrimitive -> {
                val raw = element.content.trim()
                val array = if (raw.startsWith("[")) {
                    runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull()
                } else null
                if (array != null) return parseOptions(array)
                raw.split(",").map { QuestionOption(it) }
            }
            else -> emptyList()
        }
        return items
            .map { markedRecommended(it.copy(label = it.label.trim().trim('"'), description = it.description.trim())) }
            .filter { it.label.isNotEmpty() }
            .distinctBy { it.label.lowercase() }
            .take(MAX_OPTIONS)
    }

    /** "Warm & cozy (recommended)" or "Warm & cozy ★": the mark comes off the label and onto the option. */
    private fun markedRecommended(option: QuestionOption): QuestionOption {
        val m = RECOMMENDED_MARK.find(option.label) ?: return option
        val label = option.label.removeRange(m.range).trim().trimEnd('-', '–', '—', ':').trim()
        return if (label.isEmpty()) option else option.copy(label = label, recommended = true)
    }

    private val RECOMMENDED_MARK = Regex("""\s*(\((recommended|suggested|default)\)|[-–—:]\s*(recommended|suggested)$|[★⭐]️?)\s*$""", RegexOption.IGNORE_CASE)

    /**
     * The tool result the model sees. One line per question so it can quote
     * them back; a skipped question says so, so the model does not invent an
     * answer for it.
     */
    fun formatAnswers(questions: List<UserQuestion>, answers: List<String?>): String {
        if (questions.size == 1) {
            val a = answers.firstOrNull()?.trim()
            return if (a.isNullOrEmpty()) {
                "The user skipped this question. Proceed with your best judgement and state the assumption."
            } else "User answered: $a"
        }
        return buildString {
            append("The user answered ${answers.count { !it.isNullOrBlank() }} of ${questions.size} questions:\n")
            questions.forEachIndexed { i, q ->
                val a = answers.getOrNull(i)?.trim()
                append(i + 1).append(". ").append(q.question).append("\n   → ")
                append(if (a.isNullOrEmpty()) "(skipped — use your best judgement)" else a).append('\n')
            }
        }.trimEnd()
    }

    /**
     * The answers in a result [formatAnswers] wrote, in question order (null
     * for a skipped question); null for the whole set when the user tapped
     * "You decide" (or dismissed the card in an older build).
     */
    fun parseAnswers(questions: List<UserQuestion>, output: String): List<String?>? {
        val t = output.trim()
        if (t.startsWith(DECIDE_PREFIX) || t.startsWith("The user dismissed")) return null
        if (questions.size <= 1) {
            if (!t.startsWith("User answered:")) return listOf(null)
            return listOf(t.removePrefix("User answered:").lineSequence().first().trim().ifEmpty { null })
        }
        val arrows = t.lines().filter { it.trimStart().startsWith("→") }
            .map { it.trimStart().removePrefix("→").trim() }
        return questions.indices.map { i ->
            arrows.getOrNull(i)?.takeIf { it.isNotEmpty() && !it.startsWith("(skipped") }
        }
    }

    /**
     * The answers as the chat shows them: a compact Markdown quote under
     * Orobi's text, so the transcript reads like the conversation it was.
     */
    fun quote(questions: List<UserQuestion>, answers: List<String?>?): String {
        if (questions.isEmpty()) return "> _Question skipped_"
        if (answers == null) {
            val more = if (questions.size > 1) " (+${questions.size - 1} more)" else ""
            return "> **You let Orobi decide** · ${questions[0].question.oneLine()}$more"
        }
        if (questions.size == 1) {
            val a = answers.firstOrNull()?.trim()
            return "> **You answered** · ${questions[0].question.oneLine()}\n> " +
                if (a.isNullOrEmpty()) "_Skipped_" else "**${a.take(200)}**"
        }
        return buildString {
            append("> **You answered ${questions.size} questions**")
            questions.forEachIndexed { i, q ->
                val a = answers.getOrNull(i)?.trim()
                append("\n> ").append(i + 1).append(". ").append(q.question.oneLine()).append(" — ")
                append(if (a.isNullOrEmpty()) "_skipped_" else "**${a.take(160)}**")
            }
        }
    }

    // ------------------------------------------------------------ prose

    /**
     * Questions a model wrote as prose instead of calling ask_user, so the
     * user still gets a one-tap card:
     *
     *     1. **Name:** What's the cafe called?
     *     2. Which look fits best? (warm and cozy, clean and minimal, bold)
     *     3. What should visitors do first?
     *        - See the menu
     *        - Book a table
     *
     * A line is a question when it ends with "?" -- or with "?" followed by a
     * parenthesised list or a ": a, b or c" list, which become its options, as
     * do short bullet lines right under it. Nothing else is guessed: a free
     * text answer is always available.
     */
    fun fromProse(text: String): List<UserQuestion> {
        if (text.contains("```")) return emptyList()
        val lines = text.lines()
        val out = mutableListOf<UserQuestion>()
        var i = 0
        while (i < lines.size && out.size < MAX_QUESTIONS) {
            val parsed = proseQuestion(clean(lines[i]))
            if (parsed == null) {
                i++
                continue
            }
            val options = parsed.options.toMutableList()
            var j = i + 1
            while (j < lines.size) {
                val raw = lines[j]
                if (raw.isBlank()) {
                    // A blank line may sit between a question and its bullets; not after them.
                    if (options.size > parsed.options.size) break
                    j++
                    continue
                }
                val option = optionLine(raw) ?: break
                options += QuestionOption(option)
                j++
            }
            out += parsed.copy(options = options.distinctBy { it.label.lowercase() }.take(MAX_OPTIONS).map(::markedRecommended))
            i = j
        }
        return out
    }

    private fun proseQuestion(line: String): UserQuestion? {
        val mark = line.lastIndexOf('?')
        if (mark < 0) return null
        val tail = line.substring(mark + 1).trim()
        var question = line.substring(0, mark + 1).trim()
        var options = emptyList<QuestionOption>()
        when {
            tail.isEmpty() -> Unit
            tail.startsWith("(") && tail.endsWith(")") -> options = listItems(tail.substring(1, tail.length - 1))
            tail.startsWith(":") || tail.startsWith("—") || tail.startsWith("–") -> options = listItems(tail.drop(1))
            else -> return null
        }
        // "Which look fits best (warm, minimal or bold)?"
        if (options.isEmpty()) {
            INLINE_LIST.find(question)?.let { m ->
                val items = listItems(m.groupValues[1])
                if (items.size >= 2) {
                    options = items
                    question = question.removeRange(m.range).trimEnd() + "?"
                }
            }
        }
        var header = ""
        HEADER.find(question)?.let { m ->
            if (m.groupValues[2].split(' ').size >= 3) {
                header = m.groupValues[1].trim()
                question = m.groupValues[2].trim()
            }
        }
        // "Who is it for: students or professionals?" keeps its wording and gets the list as options.
        if (options.isEmpty()) {
            COLON_LIST.find(question)?.let { m -> options = listItems(m.groupValues[2]) }
        }
        if (question.length < 6 || question.length > 240) return null
        // Rhetorical or meta lines ("Sound good?", "Should I go ahead?") are not questions for a card.
        if (question.split(' ').size < 3 || META.containsMatchIn(question)) return null
        return UserQuestion(question, header.take(24), options)
    }

    /** "a, b or c" / "a / b / c" / "a | b": 2-6 short items, else nothing. */
    private fun listItems(raw: String): List<QuestionOption> {
        val items = raw.split(Regex("""\s*(?:,|/|\||;|\bor\b)\s*"""))
            .map { it.trim().trim('.', '"', '\'', '*', '_').removePrefix("e.g.").removePrefix("eg").trim() }
            .filter { it.isNotEmpty() }
        if (items.size !in 2..6 || items.any { it.length > 40 || it.count { c -> c == ' ' } > 5 }) return emptyList()
        return items.map { QuestionOption(it.replaceFirstChar(Char::uppercase)) }
    }

    /** A short bullet line under a question ("- Book a table", "a) Minimal"); not another question. */
    private fun optionLine(raw: String): String? {
        val m = OPTION_BULLET.find(raw) ?: return null
        val body = clean(m.groupValues[1])
        // A "?" anywhere means the next question ("2. Which colours? (a, b)"), not an option.
        if (body.isEmpty() || body.contains('?') || body.length > 60) return null
        // "Minimal — clean, lots of white space": the label is the part before the dash.
        return body.split(" — ", " – ", " - ", ": ").first().trim().trim('.').takeIf { it.isNotEmpty() }
    }

    /** Markdown and numbering off a line: "1. **Name:** What…" -> "Name: What…". */
    private fun clean(line: String): String = line.trim()
        .replace(LEADING_NUMBER, "")
        .replace(Regex("""\*\*|__|`"""), "")
        .replace(Regex("""^[#>]+\s*"""), "")
        .trim()

    private val LEADING_NUMBER = Regex("""^(?:[-*•]\s+|\(?\d{1,2}[.)]\s+|[Qq]\d{1,2}[:.)]\s*)""")
    private val OPTION_BULLET = Regex("""^\s*(?:[-*•]|[a-fA-F][.)]|\([a-fA-F]\)|\d{1,2}[.)])\s+(.+)$""")
    private val INLINE_LIST = Regex("""\s*\(([^()]{3,140})\)\s*\?$""")
    /** "Name: What's it called?" -- a 1-3 word label, then a whole question. */
    private val HEADER = Regex("""^([A-Z][\w&/]*(?: [\w&/]+){0,2})\s*[:—–]\s+([A-Z].+\?)$""")
    /** "Who is it for: students or professionals?" -- the list after the colon is the options. */
    private val COLON_LIST = Regex("""^(.+?)\s*[:—–]\s*([^:?—–]+)\?$""")
    /** Lines that ask permission to start rather than ask about the work. */
    private val META = Regex(
        """\b(go ahead|proceed|get started|start building|best (guess|judgement|judgment)|my own (choices|judgement|judgment)|sound(s)? good|make(s)? sense|any questions)\b""",
        RegexOption.IGNORE_CASE
    )

    private fun String.oneLine(): String = lineSequence().first().trim().take(160)

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "true" && it.content != "false" }
            ?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let {
        it.booleanOrNull ?: it.content.lowercase().toBooleanStrictOrNull()
    }
}
