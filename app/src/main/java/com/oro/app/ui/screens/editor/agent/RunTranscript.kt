package com.oro.app.ui.screens.editor.agent

import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.tools.JsonParserUtils
import com.oro.app.data.ai.tools.ToolAliases
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.ai.tools.UserQuestions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Builds the assistant message text for one agent run, in the format the chat
 * renderer parses: `<think>` blocks for reasoning, plain Markdown for the
 * reply, and `🔧 **Tool Name**` / `⏳|✅|❌ summary` headers for tool cards.
 *
 * Segments are kept structured and rendered on demand, so a tool card can go
 * from running to done in place, and a retried model call can discard exactly
 * its own partial output. The old code appended to one growing string with
 * `+=` on every token (quadratic, on the main thread) and could do neither.
 *
 * Mutations come from the agent loop while a throttled push renders it from
 * another coroutine, so every public method is synchronized.
 */
class RunTranscript(initial: String = "") {

    private sealed interface Segment
    private class Text(val sb: StringBuilder = StringBuilder()) : Segment
    private class Think(val sb: StringBuilder = StringBuilder(), var open: Boolean = true) : Segment
    private class ToolCard(
        val id: String,
        val title: String,
        var status: String,
        var summary: String,
        var body: String = ""
    ) : Segment
    private class Note(val text: String) : Segment

    private val segments = mutableListOf<Segment>()
    private var turnStart = 0
    private var version = 0L

    init {
        if (initial.isNotEmpty()) segments += Text(StringBuilder(initial))
    }

    /** Bumped on every change; lets the caller skip redundant UI pushes. */
    val revision: Long get() = version

    @Synchronized
    fun beginModelCall() {
        closeThinking()
        turnStart = segments.size
        version++
    }

    @Synchronized
    fun appendReasoning(delta: String) {
        val last = segments.lastOrNull()
        if (last is Think && last.open) last.sb.append(delta) else segments += Think(StringBuilder(delta))
        version++
    }

    @Synchronized
    fun appendText(delta: String) {
        closeThinking()
        val last = segments.lastOrNull()
        if (last is Text) last.sb.append(delta) else segments += Text(StringBuilder(delta))
        version++
    }

    /** Drop everything the current model call produced (a retry follows). */
    @Synchronized
    fun discardCurrentCall() {
        while (segments.size > turnStart) segments.removeAt(segments.lastIndex)
        version++
    }

    /**
     * Replace the current call's streamed text with [visibleText] -- used when
     * tool-call markup was recovered from the text and must not stay on screen.
     */
    @Synchronized
    fun setCurrentCallText(visibleText: String) {
        closeThinking()
        val texts = segments.subList(turnStart, segments.size).filterIsInstance<Text>()
        val current = texts.joinToString("") { it.sb.toString() }
        if (current == visibleText) return
        segments.subList(turnStart, segments.size).removeAll { it is Text }
        if (visibleText.isNotBlank()) segments += Text(StringBuilder(visibleText))
        version++
    }

    @Synchronized
    fun toolStarted(call: ToolCallResponse) {
        closeThinking()
        // ask_user has its own interactive card while it waits; a tool pill
        // next to it would show the same thing twice. Its answers are added as
        // a quote when it finishes.
        if (ToolAliases.canonicalName(call.function.name) == "ask_user") {
            version++
            return
        }
        segments += ToolCard(
            id = call.id,
            title = titleFor(call.function.name),
            status = "⏳",
            summary = subjectOf(call) ?: "Running"
        )
        version++
    }

    @Synchronized
    fun toolFinished(call: ToolCallResponse, result: ToolResult) {
        if (ToolAliases.canonicalName(call.function.name) == "ask_user") {
            closeThinking()
            segments += Note(answeredSummary(call, result))
            version++
            return
        }
        val card = segments.lastOrNull { it is ToolCard && it.id == call.id } as? ToolCard ?: run {
            toolStarted(call)
            segments.last() as ToolCard
        }
        val name = ToolAliases.canonicalName(call.function.name)
        if (result.success) {
            card.status = "✅"
            card.summary = when (name) {
                "ask_user" -> {
                    val q = subjectOf(call).orEmpty()
                    val a = result.output.removePrefix("User answered: ")
                    if (q.isNotEmpty()) "$q → ${a.lineSequence().first().take(120)}" else a.take(120)
                }
                else -> result.output.lineSequence().firstOrNull { it.isNotBlank() }?.take(160) ?: "Done"
            }
            card.body = bodyFor(name, result.output)
        } else {
            card.status = "❌"
            card.summary = (result.error ?: "Failed").lineSequence().first().take(200)
            val rest = (result.error ?: "").lines().drop(1).joinToString("\n").trim()
            card.body = listOf(rest, result.output.take(600)).filter { it.isNotBlank() }.joinToString("\n")
        }
        version++
    }

    @Synchronized
    fun note(text: String) {
        closeThinking()
        segments += Note(text)
        version++
    }

    /** Close any open reasoning block (end of run, or text started). */
    @Synchronized
    fun closeThinking() {
        val last = segments.lastOrNull()
        if (last is Think && last.open) {
            last.open = false
            version++
        }
    }

    @Synchronized
    fun render(): String = buildString {
        for (segment in segments) {
            when (segment) {
                is Text -> append(segment.sb)
                is Think -> {
                    if (segment.sb.isBlank()) continue
                    ensureBreak()
                    append("<think>").append(segment.sb)
                    if (!segment.open) append("</think>\n\n")
                }
                is ToolCard -> {
                    ensureBreak()
                    append("🔧 **").append(segment.title).append("**\n")
                    append(segment.status).append(' ').append(segment.summary.replace('\n', ' ')).append('\n')
                    if (segment.body.isNotBlank()) {
                        // No blank lines inside a card: a blank line is what
                        // ends the card for the renderer, so the reply that
                        // follows is not swallowed into the tool output.
                        segment.body.trimEnd().lineSequence().forEach { line ->
                            append(if (line.isBlank()) " " else line).append('\n')
                        }
                    }
                    append('\n')
                }
                is Note -> {
                    ensureBreak()
                    append(segment.text).append("\n\n")
                }
            }
        }
    }.trimEnd()

    /** "> **You answered** · Which framework?" + the answer, as a Markdown quote. */
    private fun answeredSummary(call: ToolCallResponse, result: ToolResult): String {
        val args = JsonParserUtils.parseToJsonObject(call.function.arguments)
        val questions = args?.let { UserQuestions.parse(it) }.orEmpty()
        if (!result.success || questions.isEmpty()) return "> _Question skipped_"
        val out = result.output
        if (questions.size == 1) {
            val answer = out.removePrefix("User answered: ").lineSequence().first().trim()
            val skipped = !out.startsWith("User answered: ")
            return "> **You answered** · ${questions[0].question.oneLine()}\n> " +
                if (skipped) "_Skipped_" else "**${answer.take(200)}**"
        }
        val answers = out.lines().filter { it.trimStart().startsWith("→") }
            .map { it.trimStart().removePrefix("→").trim() }
        return buildString {
            append("> **You answered ${questions.size} questions**")
            questions.forEachIndexed { i, q ->
                val a = answers.getOrNull(i).orEmpty()
                append("\n> ").append(i + 1).append(". ").append(q.question.oneLine()).append(" — ")
                append(if (a.isEmpty() || a.startsWith("(skipped")) "_skipped_" else "**${a.take(160)}**")
            }
        }
    }

    private fun String.oneLine(): String = lineSequence().first().trim().take(160)

    private fun StringBuilder.ensureBreak() {
        if (isEmpty()) return
        if (!endsWith("\n\n")) append(if (endsWith("\n")) "\n" else "\n\n")
    }

    private fun bodyFor(name: String, output: String): String {
        val lines = output.lines()
        return when (name) {
            // Edits carry "File: path" and a diff block; keep them whole so the
            // card can count additions/deletions and show the change.
            "edit_file", "multi_edit", "write_file", "move_file", "delete_file" ->
                lines.drop(1).joinToString("\n").take(6_000)
            "ask_user" -> ""
            else -> {
                val rest = lines.drop(1).joinToString("\n")
                if (rest.length > 1_500) rest.take(1_500) + "\n…" else rest
            }
        }
    }

    companion object {
        /** "multi_edit" -> "Multi Edit". */
        fun titleFor(name: String): String = name.split('_', '-', ' ').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

        fun subjectOf(call: ToolCallResponse): String? {
            val args = JsonParserUtils.parseToJsonObject(call.function.arguments) ?: return null
            for (key in listOf("question", "path", "file_path", "pattern", "url", "source", "action", "description", "query", "command")) {
                val v = (args[key] as? JsonPrimitive)?.content
                if (!v.isNullOrBlank()) {
                    val dest = (args["destination"] as? JsonPrimitive)?.content
                    return if (key == "source" && dest != null) "$v → $dest" else v.lineSequence().first().take(160)
                }
            }
            (args["tasks"] as? JsonArray)?.let { return "${it.size} steps" }
            (args["questions"] as? JsonArray)?.let { return "${it.size} questions" }
            return null
        }

        private val THINK = Regex("(?is)<think>.*?(</think>|$)")
        private val TOOL_BLOCK = Regex("""(?m)^🔧\s*\*\*(.+?)\*\*[ \t]*\r?\n(✅|❌|⏳)[^\n]*(?:\n[^\n]+)*""")
        private val DIFF = Regex("(?s)```diff\\n.*?```")

        /**
         * A persisted transcript reduced to what is worth sending back to the
         * model as history: no reasoning, and tool cards collapsed to one line
         * (their real results are not in the reloaded history anyway).
         */
        fun toHistoryText(content: String): String {
            var s = content.replace(THINK, "").replace(DIFF, "")
            s = s.replace(TOOL_BLOCK) { m -> "[${m.groupValues[1].trim()}: ${if (m.groupValues[2] == "❌") "failed" else "done"}]" }
            return s.replace(Regex("\n{3,}"), "\n\n").trim()
        }
    }
}
