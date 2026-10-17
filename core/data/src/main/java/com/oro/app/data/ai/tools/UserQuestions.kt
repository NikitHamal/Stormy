package com.oro.app.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** One suggested answer. [description] is optional helper text under the label. */
data class QuestionOption(val label: String, val description: String = "")

/**
 * One question in an `ask_user` call.
 *
 * Models can batch several questions into one call (Claude Code's
 * AskUserQuestion shape); the user answers them one after another and the
 * whole set goes back as a single tool result, so the model gets every answer
 * in one turn instead of paying a round trip per question.
 */
data class UserQuestion(
    val question: String,
    /** A 1-3 word chip label ("Framework"), shown in the stepper. */
    val header: String = "",
    val options: List<QuestionOption> = emptyList(),
    val multiSelect: Boolean = false,
    val allowFreeText: Boolean = true
)

object UserQuestions {

    const val MAX_QUESTIONS = 6
    private const val MAX_OPTIONS = 8

    /**
     * Questions from either shape:
     * - `{"questions":[{"question","header","options":[{"label","description"}|"text"],"multiSelect"}]}`
     * - the old `{"question","options":[...]|"a, b"}`
     * `questions` may also arrive as a JSON string (models double-encode).
     */
    fun parse(args: JsonObject): List<UserQuestion> {
        val batch = when (val q = args["questions"]) {
            is JsonArray -> q
            is JsonPrimitive -> runCatching { Json.parseToJsonElement(q.content) as? JsonArray }.getOrNull()
            else -> null
        }
        val parsed = batch?.mapNotNull { (it as? JsonObject)?.let(::parseOne) }.orEmpty()
        if (parsed.isNotEmpty()) return parsed.take(MAX_QUESTIONS)
        return listOfNotNull(parseOne(args))
    }

    private fun parseOne(obj: JsonObject): UserQuestion? {
        val text = obj.string("question") ?: obj.string("prompt") ?: obj.string("text") ?: return null
        return UserQuestion(
            question = text,
            header = obj.string("header")?.take(24).orEmpty(),
            options = parseOptions(obj["options"] ?: obj["choices"]),
            multiSelect = obj.bool("multiSelect") ?: obj.bool("multi_select") ?: false,
            allowFreeText = obj.bool("allowFreeText") ?: obj.bool("allow_free_text") ?: true
        )
    }

    private fun parseOptions(element: JsonElement?): List<QuestionOption> {
        val items: List<QuestionOption> = when (element) {
            is JsonArray -> element.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> QuestionOption(item.content)
                    is JsonObject -> (item.string("label") ?: item.string("value") ?: item.string("text"))
                        ?.let { QuestionOption(it, item.string("description").orEmpty()) }
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
            .map { it.copy(label = it.label.trim().trim('"'), description = it.description.trim()) }
            .filter { it.label.isNotEmpty() }
            .distinctBy { it.label.lowercase() }
            .take(MAX_OPTIONS)
    }

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

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let {
        it.booleanOrNull ?: it.content.lowercase().toBooleanStrictOrNull()
    }
}
