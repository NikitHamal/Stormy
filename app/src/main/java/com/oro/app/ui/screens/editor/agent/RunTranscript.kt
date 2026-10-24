package com.oro.app.ui.screens.editor.agent

import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.text.TranscriptCompactor
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
    ) : Segment {
        /** This card's rendered Markdown, kept until the card changes. */
        var rendered: String? = null
    }
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
            card.body = subjectLine(call, result.output) + bodyFor(name, result.output) + writtenContent(name, call)
        } else {
            card.status = "❌"
            card.summary = (result.error ?: "Failed").lineSequence().first().take(200)
            val rest = (result.error ?: "").lines().drop(1).joinToString("\n").trim()
            card.body = subjectLine(call, result.output) +
                listOf(rest, result.output.take(600)).filter { it.isNotBlank() }.joinToString("\n")
        }
        card.rendered = null
        version++
        compactIfNeeded()
    }

    @Synchronized
    fun note(text: String) {
        closeThinking()
        segments += Note(text)
        version++
        compactIfNeeded()
    }

    /** Close any open reasoning block (end of run, or text started). */
    @Synchronized
    fun closeThinking() {
        val last = segments.lastOrNull()
        if (last is Think && last.open) {
            last.open = false
            // A closed block keeps its start and its end: the chat shows
            // reasoning collapsed, and a run of a hundred turns of a thinking
            // model was megabytes of it, stored and re-parsed with every push.
            if (last.sb.length > THINK_KEEP_HEAD + THINK_KEEP_TAIL + 64) {
                val trimmed = TranscriptCompactor.trimMiddle(last.sb.toString(), THINK_KEEP_HEAD, THINK_KEEP_TAIL)
                last.sb.setLength(0)
                last.sb.append(trimmed)
            }
            version++
            compactIfNeeded()
        }
    }

    /** Approximate rendered size, in characters. */
    @Synchronized
    fun size(): Int = segments.sumOf { segmentSize(it) }

    private fun segmentSize(segment: Segment): Int = when (segment) {
        is Text -> segment.sb.length
        is Think -> minOf(segment.sb.length, LIVE_THINK_CHARS) + 20
        is ToolCard -> segment.title.length + segment.summary.length + segment.body.length + 12
        is Note -> segment.text.length + 2
    }

    /**
     * Keeps the whole run under [MAX_RUN_CHARS] however long it goes: the
     * oldest tool cards lose their bodies first (title, status and summary
     * stay, so the step list reads the same), then the oldest closed reasoning
     * goes. Unlimited runs stay bounded; what the user reads -- the latest
     * steps and the reply -- is untouched.
     */
    private fun compactIfNeeded() {
        var total = segments.sumOf { segmentSize(it) }
        if (total <= MAX_RUN_CHARS) return
        val target = MAX_RUN_CHARS * 3 / 4
        val keepFrom = segments.size - KEEP_RECENT_SEGMENTS
        for ((i, seg) in segments.withIndex()) {
            if (total <= target || i >= keepFrom) break
            if (seg is ToolCard && seg.body.isNotEmpty() && seg.status != "⏳") {
                total -= seg.body.length
                seg.body = ""
                seg.rendered = null
            }
        }
        if (total > target) {
            val it = segments.listIterator()
            var position = 0
            var original = 0
            while (it.hasNext() && total > target) {
                val seg = it.next()
                if (original < keepFrom && seg is Think && !seg.open) {
                    total -= segmentSize(seg)
                    it.remove()
                    if (turnStart > position) turnStart--
                } else {
                    position++
                }
                original++
            }
        }
        version++
    }

    @Synchronized
    fun render(): String = buildString(segments.sumOf { segmentSize(it) } + 64) {
        for (segment in segments) {
            when (segment) {
                is Text -> append(segment.sb)
                is Think -> {
                    if (segment.sb.isBlank()) continue
                    ensureBreak()
                    append("<think>")
                    // Live reasoning shows its newest part; it is trimmed for
                    // good when the block closes (closeThinking).
                    if (segment.sb.length > LIVE_THINK_CHARS) {
                        append("…\n").append(segment.sb, segment.sb.length - LIVE_THINK_CHARS, segment.sb.length)
                    } else {
                        append(segment.sb)
                    }
                    if (!segment.open) append("</think>\n\n")
                }
                is ToolCard -> {
                    ensureBreak()
                    append(segment.rendered ?: renderCard(segment).also { segment.rendered = it })
                }
                is Note -> {
                    ensureBreak()
                    append(segment.text).append("\n\n")
                }
            }
        }
    }.trimEnd()

    private fun renderCard(card: ToolCard): String = buildString(card.body.length + card.summary.length + 48) {
        append("🔧 **").append(card.title).append("**\n")
        append(card.status).append(' ').append(card.summary.replace('\n', ' ')).append('\n')
        if (card.body.isNotBlank()) {
            // No blank lines inside a card: a blank line is what
            // ends the card for the renderer, so the reply that
            // follows is not swallowed into the tool output.
            card.body.trimEnd().lineSequence().forEach { line ->
                append(if (line.isBlank()) " " else line).append('\n')
            }
        }
        append('\n')
    }

    /** "> **You answered** · Which framework?" + the answer, as a Markdown quote. */
    private fun answeredSummary(call: ToolCallResponse, result: ToolResult): String {
        val args = JsonParserUtils.parseToJsonObject(call.function.arguments)
        val questions = args?.let { UserQuestions.parse(it) }.orEmpty()
        if (!result.success || questions.isEmpty()) return "> _Question skipped_"
        return UserQuestions.quote(questions, UserQuestions.parseAnswers(questions, result.output))
    }

    private fun StringBuilder.ensureBreak() {
        if (isEmpty()) return
        if (!endsWith("\n\n")) append(if (endsWith("\n")) "\n" else "\n\n")
    }

    /**
     * For a write, the start of the text written, so the card can show it
     * without re-reading the file, plus its true line count (`written: N
     * lines`) for the +N on the row. Whole files used to ride along -- up to
     * 12 000 characters per write, stored and re-parsed with every push.
     */
    private fun writtenContent(name: String, call: ToolCallResponse): String {
        if (name != "write_file") return ""
        val content = (JsonParserUtils.parseToJsonObject(call.function.arguments)?.get("content") as? JsonPrimitive)?.content
            ?: return ""
        if (content.contains("```")) return ""
        val text = content.trimEnd()
        val lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
        val shown = capLines(text, WRITTEN_PREVIEW_CHARS)
        return "\nwritten: $lines lines\nnew_content: ```\n$shown\n```"
    }

    /**
     * `subject: <what the call was about>` for a finished card whose output
     * names no file, so its row still reads "Studied design-system" rather
     * than a bare verb once the status line holds the result. File tools name
     * their file in a `File:` line already.
     */
    private fun subjectLine(call: ToolCallResponse, output: String): String {
        if (FILE_LINE.containsMatchIn(output)) return ""
        val subject = subjectOf(call)?.lineSequence()?.firstOrNull()?.trim()?.take(120)
        return if (subject.isNullOrEmpty()) "" else "subject: $subject\n"
    }

    private fun bodyFor(name: String, output: String): String {
        val lines = output.lines()
        return when (name) {
            // Edits carry "File: path" and a diff block; the card counts its
            // +/- lines and shows the change. Long diffs keep their start, with
            // the true counts in a `stats:` line so the row stays right.
            "edit_file", "multi_edit", "write_file", "move_file", "delete_file" ->
                capDiffBody(lines.drop(1).joinToString("\n"), EDIT_BODY_CHARS)
            "ask_user" -> ""
            // Keep the "File: a.css (124 lines)" header: the row shows the count.
            "read_file" -> output.take(1_600).let { if (output.length > 1_600) "$it\n…" else it }
            else -> {
                val rest = lines.drop(1).joinToString("\n")
                if (rest.length > 1_500) rest.take(1_500) + "\n…" else rest
            }
        }
    }

    companion object {
        /** Whole-run budget, in characters; see [compactIfNeeded]. */
        const val MAX_RUN_CHARS = 160_000

        /** The newest segments are never compacted. */
        private const val KEEP_RECENT_SEGMENTS = 24

        /** A closed reasoning block keeps this much of its start and its end. */
        const val THINK_KEEP_HEAD = TranscriptCompactor.THINK_HEAD
        const val THINK_KEEP_TAIL = TranscriptCompactor.THINK_TAIL

        /** The live (open) reasoning block shows at most its newest characters. */
        const val LIVE_THINK_CHARS = 12_000

        /** Diff bodies of edits and writes. */
        const val EDIT_BODY_CHARS = 4_000

        /** Preview of a written file on its card. */
        const val WRITTEN_PREVIEW_CHARS = 3_000

        /** The first lines of [text] that fit in [max] characters, and how many lines were left out. */
        internal fun capLines(text: String, max: Int): String {
            if (text.length <= max) return text
            val cut = text.lastIndexOf('\n', max).let { if (it <= 0) max else it }
            val rest = text.substring(cut).count { it == '\n' }
            return text.substring(0, cut) + "\n… $rest more lines"
        }

        /**
         * An edit's card body cut to [max] characters without breaking its
         * fenced diff: the fence is closed after the cut, and a `stats: +A -D`
         * line carries the counts of the whole diff.
         */
        internal fun capDiffBody(body: String, max: Int): String {
            if (body.length <= max) return body
            var adds = 0
            var dels = 0
            var inFence = false
            body.lineSequence().forEach { line ->
                val t = line.trimStart()
                when {
                    t.startsWith("```") -> inFence = !inFence
                    !inFence -> Unit
                    line.startsWith("+++") || line.startsWith("---") -> Unit
                    line.startsWith("+… ") || line.startsWith("-… ") -> Unit
                    line.startsWith("+") -> adds++
                    line.startsWith("-") -> dels++
                }
            }
            val out = StringBuilder("stats: +$adds -$dels\n")
            var open = false
            var used = 0
            var omitted = 0
            for (line in body.lineSequence()) {
                if (omitted > 0 || used + line.length + 1 > max) {
                    // Past the budget: count the rest, keep the fences balanced.
                    if (!line.trimStart().startsWith("```")) omitted++
                    continue
                }
                if (line.trimStart().startsWith("```")) open = !open
                out.append(line).append('\n')
                used += line.length + 1
            }
            if (omitted > 0) out.append("… $omitted more lines\n")
            if (open) out.append("```\n")
            return out.toString().trimEnd()
        }

        /** "multi_edit" -> "Multi Edit". */
        fun titleFor(name: String): String = name.split('_', '-', ' ').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

        fun subjectOf(call: ToolCallResponse): String? {
            val args = JsonParserUtils.parseToJsonObject(call.function.arguments) ?: return null
            // A batch read: "index.html +2".
            (args["paths"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { paths ->
                val first = (args["path"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    ?: (paths.first() as? JsonPrimitive)?.content
                val more = paths.size - if (first == (paths.first() as? JsonPrimitive)?.content) 1 else 0
                if (first != null) return if (more > 0) "$first +$more" else first
            }
            for (key in listOf("question", "path", "file_path", "pattern", "url", "source", "action", "description", "query", "command", "task", "name")) {
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

        private val FILE_LINE = Regex("(?m)^File: ")
        private val RUNNING_STATUS = Regex("""\n⏳[ \t]*([^\r\n]*)""")

        /**
         * The subject of the tool still running at the end of [content] (the
         * "⏳ index.html" status line of the last card), or null when nothing is
         * running or the card names no subject.
         */
        fun runningSubject(content: String): String? {
            val card = content.lastIndexOf("🔧")
            if (card < 0) return null
            val status = RUNNING_STATUS.find(content, card) ?: return null
            // The status line must belong to the last card: its header is one line.
            if (content.indexOf("\n\n", card).let { it in card until status.range.first }) return null
            return status.groupValues[1].trim().takeIf { it.isNotEmpty() && it != "Running" }
        }
        private val THINK = Regex("(?is)<think>.*?(</think>|$)")
        private val TOOL_BLOCK = Regex("""(?m)^🔧\s*\*\*(.+?)\*\*[ \t]*\r?\n(✅|❌|⏳)[^\n]*(?:\n[^\n]+)*""")
        private val DIFF = Regex("(?s)```diff\\n.*?```")
        private val BLANK_RUNS = Regex("\n{3,}")

        /**
         * A persisted transcript reduced to what is worth sending back to the
         * model as history: no reasoning, and tool cards collapsed to one line
         * (their real results are not in the reloaded history anyway).
         */
        fun toHistoryText(content: String): String {
            var s = content.replace(THINK, "").replace(DIFF, "")
            s = s.replace(TOOL_BLOCK) { m -> "[${m.groupValues[1].trim()}: ${if (m.groupValues[2] == "❌") "failed" else "done"}]" }
            return s.replace(BLANK_RUNS, "\n\n").trim()
        }
    }
}
