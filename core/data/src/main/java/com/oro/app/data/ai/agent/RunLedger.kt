package com.oro.app.data.ai.agent

import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.tools.JsonParserUtils
import com.oro.app.data.ai.tools.ToolAliases
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the collapsed early steps of a long run did, in a few lines.
 *
 * [HistoryPruner] folds the oldest exchanges of an unlimited run into this
 * when the run outgrows the context window. It is built from the messages
 * themselves (no model call, so it is free and deterministic): the files the
 * run changed and read, the checks it ran, what failed, what the user
 * answered, the latest plan and the model's own notes. A model that loses
 * those early turns would otherwise redo work or contradict decisions it
 * already made; a weak model on a small window loses them every few turns.
 */
object RunLedger {

    private const val MAX_CHARS = 3_000

    private val READS = setOf("read_file", "list_files", "glob", "grep", "search_project", "docs_lookup", "use_skill", "web_fetch")
    private val CHANGES = setOf("write_file", "edit_file", "multi_edit", "delete_file", "move_file", "add_ui_kit")
    private val VERBS = mapOf(
        "write_file" to "write", "edit_file" to "edit", "multi_edit" to "edit", "delete_file" to "delete",
        "move_file" to "move", "add_ui_kit" to "kit"
    )

    private data class Call(val name: String, val args: JsonObject?, val id: String?)

    fun summarize(dropped: List<ChatRequestMessage>): String {
        val changed = LinkedHashMap<String, MutableMap<String, Int>>()
        val read = LinkedHashSet<String>()
        val other = LinkedHashMap<String, Int>()
        val failures = LinkedHashMap<String, Int>()
        val answers = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var plan: String? = null
        val callsById = HashMap<String, Call>()
        var steps = 0

        fun record(call: Call) {
            val name = ToolAliases.canonicalName(call.name)
            val path = pathOf(call.args)
            when {
                name in CHANGES -> {
                    val target = if (name == "add_ui_kit") "ui kit" else path ?: "?"
                    val verbs = changed.getOrPut(target) { LinkedHashMap() }
                    val verb = VERBS[name] ?: name
                    verbs[verb] = (verbs[verb] ?: 0) + 1
                }
                name in READS -> read += (path ?: (call.args?.get("pattern") as? JsonPrimitive)?.content?.let { "\"$it\"" } ?: name)
                name == "update_plan" -> plan = planOf(call.args) ?: plan
                else -> other[name] = (other[name] ?: 0) + 1
            }
        }

        fun failure(name: String?, text: String) {
            val line = text.removePrefix("Error:").trim().lineSequence().firstOrNull { it.isNotBlank() }?.take(110) ?: return
            val key = (name?.let { ToolAliases.canonicalName(it) } ?: "tool") + " — " + line
            failures[key] = (failures[key] ?: 0) + 1
        }

        for (m in dropped) {
            when (m.role) {
                "assistant" -> {
                    steps++
                    m.toolCalls.orEmpty().forEach { c ->
                        val call = Call(c.function.name, JsonParserUtils.parseToJsonObject(c.function.arguments), c.id)
                        callsById[c.id] = call
                        record(call)
                    }
                    val content = AgentLoop.stripThinking(m.content.orEmpty())
                    textCalls(content).forEach { record(it) }
                    noteOf(content)?.let { notes += it }
                }
                "tool" -> {
                    val content = m.content.orEmpty()
                    val name = m.name ?: m.toolCallId?.let { callsById[it]?.name }
                    if (content.startsWith("Error:")) failure(name, content)
                    if (content.startsWith("User answered:")) answers += content.removePrefix("User answered:").trim().lineSequence().first().take(140)
                }
                "user" -> {
                    val content = m.content.orEmpty()
                    if (content.startsWith(HistoryPruner.TEXT_RESULTS_HEADER)) {
                        TEXT_RESULT.findAll(content).forEach { r ->
                            val body = r.groupValues[2].trim()
                            if (body.startsWith("Error:")) failure(r.groupValues[1], body)
                            if (body.startsWith("User answered:")) answers += body.removePrefix("User answered:").trim().lineSequence().first().take(140)
                        }
                    }
                }
            }
        }

        val out = StringBuilder()
        out.append("[Oro: the first ").append(steps).append(if (steps == 1) " step" else " steps")
        out.append(" of this run were collapsed to fit the context window. What they did:]")
        if (changed.isNotEmpty()) {
            out.append("\n- Changed: ")
            out.append(changed.entries.joinToString(", ") { (path, verbs) ->
                path + " (" + verbs.entries.joinToString(", ") { (v, n) -> if (n > 1) "$v ×$n" else v } + ")"
            })
        }
        if (read.isNotEmpty()) out.append("\n- Looked at: ").append(capList(read.toList(), 12))
        if (other.isNotEmpty()) out.append("\n- Also ran: ").append(other.entries.joinToString(", ") { (n, c) -> if (c > 1) "$n ×$c" else n })
        if (failures.isNotEmpty()) {
            out.append("\n- Failed (don't repeat as is): ")
            out.append(failures.entries.toList().takeLast(6).joinToString("; ") { (k, n) -> if (n > 1) "$k (×$n)" else k })
        }
        if (answers.isNotEmpty()) out.append("\n- User answered: ").append(answers.takeLast(4).joinToString("; "))
        plan?.let { out.append("\n- Plan: ").append(it) }
        if (notes.isNotEmpty()) out.append("\n- Your notes: ").append(notes.takeLast(5).joinToString(" / ") { "\"$it\"" })
        out.append("\nContinue from the newest steps below; re-read a file before editing it.")
        return if (out.length > MAX_CHARS) out.substring(0, MAX_CHARS - 1) + "…" else out.toString()
    }

    private fun capList(items: List<String>, max: Int): String =
        if (items.size <= max) items.joinToString(", ") else items.take(max).joinToString(", ") + " +${items.size - max} more"

    private fun pathOf(args: JsonObject?): String? {
        args ?: return null
        for (k in listOf("path", "file_path", "filePath", "source")) {
            (args[k] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }?.let { p ->
                val dest = (args["destination"] as? JsonPrimitive)?.content
                return if (k == "source" && dest != null) "$p → $dest" else p
            }
        }
        (args["paths"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.takeIf { it.isNotEmpty() }?.let {
            return it.joinToString(", ")
        }
        return null
    }

    private fun planOf(args: JsonObject?): String? {
        val tasks = args?.get("tasks") as? JsonArray ?: return null
        val steps = tasks.mapNotNull { t ->
            when (t) {
                is JsonPrimitive -> t.content
                is JsonObject -> listOf("title", "content", "task", "step").firstNotNullOfOrNull { (t[it] as? JsonPrimitive)?.content }?.let { title ->
                    val status = (t["status"] as? JsonPrimitive)?.content
                    if (status != null) "$title [$status]" else title
                }
                else -> null
            }
        }
        if (steps.isEmpty()) return null
        return steps.mapIndexed { i, s -> "${i + 1}. ${s.take(80)}" }.joinToString("; ").take(500)
    }

    /** The model's first sentence of prose, without tool-call markup. */
    private fun noteOf(content: String): String? {
        val prose = content.substringBefore("<tool_call").substringBefore("<function=").trim()
        val line = prose.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val sentence = line.substringBefore(". ").trimEnd('.')
        return sentence.take(140).takeIf { it.length >= 8 }
    }

    private val FUNCTION_BLOCK = Regex("""(?s)<function\s*=\s*([\w.\-]+)\s*>(.*?)(?=</function>|<function\s*=|$)""")
    private val PATH_PARAM = Regex("""<parameter\s*=\s*(?:path|file_path)\s*>\s*([^<\n]+?)\s*</parameter>""")
    private val JSON_NAME = Regex(""""name"\s*:\s*"([\w.\-]+)"""")
    private val JSON_PATH = Regex(""""(?:path|file_path)"\s*:\s*"([^"]+)"""")
    private val TOOL_CALL_JSON = Regex("""(?s)<tool_call>\s*(\{.*?)(?:</tool_call>|$)""")
    private val TEXT_RESULT = Regex("""(?s)<tool_result name="([^"]*)">\n?(.*?)\n?</tool_result>""")

    /** Calls written as text (raw-parameter or JSON); only names and paths are needed. */
    private fun textCalls(content: String): List<Call> {
        if (!content.contains("<function") && !content.contains("<tool_call")) return emptyList()
        val calls = mutableListOf<Call>()
        FUNCTION_BLOCK.findAll(content).forEach { m ->
            val path = PATH_PARAM.find(m.groupValues[2])?.groupValues?.get(1)
            calls += Call(m.groupValues[1], path?.let { JsonObject(mapOf("path" to JsonPrimitive(it))) }, null)
        }
        if (calls.isEmpty()) {
            TOOL_CALL_JSON.findAll(content).forEach { m ->
                val name = JSON_NAME.find(m.groupValues[1])?.groupValues?.get(1) ?: return@forEach
                val path = JSON_PATH.find(m.groupValues[1])?.groupValues?.get(1)
                calls += Call(name, path?.let { JsonObject(mapOf("path" to JsonPrimitive(it))) }, null)
            }
        }
        return calls
    }
}
