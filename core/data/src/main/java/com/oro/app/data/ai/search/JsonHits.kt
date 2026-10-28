package com.oro.app.data.ai.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Finds search results in any reasonable JSON search-API response, without a
 * per-provider parser.
 *
 * The market has seventeen search APIs and none of them agree on field
 * names: Brave says `web.results[].description`, Serper says
 * `organic[].snippet`, Google CSE says `items[].link`, Tavily says
 * `results[].content`, TinyFish and the next startup say something new.
 * Writing a parser per provider meant every new one (and every user's own
 * API) needed a code change. Instead: walk the JSON, find the arrays of
 * objects, and in each array look for the three things a search result is --
 * a URL-ish field, a title-ish field, a snippet-ish field -- and keep the
 * array that scores best. Known shapes all fall out of this; so do the ones
 * that do not exist yet, which is what lets users wire their own provider
 * from the settings screen with nothing but a URL template.
 *
 * Pure, and tested against fixtures in every shape above.
 */
object JsonHits {

    private val URL_KEYS = listOf("url", "link", "href", "uri", "permalink", "target_url", "page_url")
    private val URL_KEY_PARTS = listOf("url", "link", "href")
    private val TITLE_KEYS = listOf("title", "name", "heading", "headline", "question")
    private val SNIPPET_KEYS = listOf(
        "snippet", "description", "content", "text", "summary", "abstract", "excerpt",
        "body", "highlight", "answer", "snippet_text", "clean_content", "htmlsnippet",
    )

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** The results hidden in [raw], best array first; empty when it is not a results document. */
    fun sniff(raw: String): List<WebSearch.Hit> {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val candidates = ArrayList<JsonArray>()
        collectArrays(root, candidates, 0)
        var best: List<WebSearch.Hit> = emptyList()
        var bestScore = 0
        for (arr in candidates) {
            val hits = ArrayList<WebSearch.Hit>(arr.size)
            var score = 0
            for (el in arr) {
                val obj = el as? JsonObject ?: continue
                val url = field(obj, ::isUrlKey)?.takeIf { it.startsWith("http") } ?: continue
                val title = field(obj, ::isTitleKey).orEmpty()
                if (title.isBlank()) continue
                val snippet = field(obj, ::isSnippetKey).orEmpty()
                hits += WebSearch.Hit(
                    title = clean(title),
                    url = url,
                    snippet = clean(snippet),
                )
                score += 2 + (if (snippet.isNotBlank()) 1 else 0)
            }
            if (hits.isNotEmpty() && score > bestScore) {
                bestScore = score
                best = hits
            }
        }
        return best.distinctBy { it.url }
    }

    /** Depth-capped walk: results live near the surface in every real API. */
    private fun collectArrays(el: JsonElement, out: MutableList<JsonArray>, depth: Int) {
        if (depth > 6) return
        when (el) {
            is JsonArray -> {
                if (el.size in 1..200 && el.count { it is JsonObject } * 2 >= el.size) out += el
                el.forEach { collectArrays(it, out, depth + 1) }
            }
            is JsonObject -> el.values.forEach { collectArrays(it, out, depth + 1) }
            else -> Unit
        }
    }

    private fun isUrlKey(key: String): Boolean =
        key.lowercase() in URL_KEYS || URL_KEY_PARTS.any { key.lowercase().contains(it) }

    private fun isTitleKey(key: String): Boolean = key.lowercase() in TITLE_KEYS

    private fun isSnippetKey(key: String): Boolean = key.lowercase() in SNIPPET_KEYS

    /** The first matching field's string value, trying exact keys before substring keys. */
    private fun field(obj: JsonObject, match: (String) -> Boolean): String? {
        var fuzzy: String? = null
        for ((key, value) in obj) {
            if (value !is JsonPrimitive || !value.isString) continue
            val k = key.lowercase()
            if (match(k)) {
                // Exact names win over substring matches ("link_score" must not beat "link").
                if (k in URL_KEYS || k in TITLE_KEYS || k in SNIPPET_KEYS) return value.content
                if (fuzzy == null) fuzzy = value.content
            }
        }
        return fuzzy
    }

    /** Entities decoded, tags and whitespace flattened. */
    private fun clean(s: String): String =
        com.oro.app.data.ai.tools.HtmlText.unescape(s.replace(Regex("<[^>]*>"), " "))
            .replace(Regex("\\s+"), " ").trim()
}
