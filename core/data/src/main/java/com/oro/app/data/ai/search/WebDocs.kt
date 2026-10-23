package com.oro.app.data.ai.search

import com.oro.app.data.ai.AiHttp
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * An offline pocket reference for HTML, CSS and JavaScript, bundled with the
 * app (resources/oro/webdocs.json): the patterns a web build needs most,
 * each with a working example and the mobile / accessibility notes that
 * matter. `docs_lookup` searches it with the same tokenizer as project search,
 * so it answers with no network at all.
 */
object WebDocs {
    @Serializable
    data class Entry(val name: String, val kind: String, val summary: String, val example: String, val notes: String = "")

    val entries: List<Entry> by lazy {
        val text = WebDocs::class.java.getResourceAsStream("/oro/webdocs.json")?.bufferedReader()?.readText() ?: "[]"
        AiHttp.json.decodeFromString(ListSerializer(Entry.serializer()), text)
    }

    fun lookup(query: String, limit: Int = 3): List<Entry> {
        val q = ProjectSearch.expand(ProjectSearch.tokens(query))
        if (q.isEmpty()) return emptyList()
        return entries.map { e ->
            val name = ProjectSearch.tokens(e.name).toSet()
            val body = ProjectSearch.tokens(e.summary + " " + e.notes + " " + e.example).groupingBy { it }.eachCount()
            e to q.sumOf { (t, w) -> w * ((if (t in name) 4.0 else 0.0) + (body[t] ?: 0).coerceAtMost(3)) }
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    fun format(list: List<Entry>): String = if (list.isEmpty()) "No match in the offline docs. Try web_fetch on developer.mozilla.org." else
        list.joinToString("\n\n") { e -> "## ${e.name} (${e.kind})\n${e.summary}\n```\n${e.example}\n```" + if (e.notes.isNotBlank()) "\nNote: ${e.notes}" else "" }
}
