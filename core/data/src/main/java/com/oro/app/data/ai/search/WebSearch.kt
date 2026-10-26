package com.oro.app.data.ai.search

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.tools.HtmlText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Keyless web search for every agent (coding agent, subagents, browser agent).
 *
 * A chain of free HTML endpoints, tried in order until one brings back
 * results: DuckDuckGo HTML, DuckDuckGo Lite, Mojeek, then public SearXNG
 * instances. No API key, no account, no telemetry deal -- the same endpoints a
 * browser would hit. Each provider's parsing is a pure function over the
 * returned HTML, so the whole chain is unit-tested against fixtures; the
 * network part is one OkHttp call per provider with a hard timeout.
 *
 * Endpoints like these come and go (rate limits, redesigns, captchas), so
 * nothing here is fatal: an empty or broken provider is skipped, and a search
 * that finds nothing anywhere says so plainly instead of inventing results.
 */
object WebSearch {

    data class Hit(val title: String, val url: String, val snippet: String)

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /** Small query cache: an agent retrying a search must not hammer the endpoint. */
    private val cache = object : LinkedHashMap<String, Pair<Long, List<Hit>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<Hit>>>?) = size > 32
    }
    private const val CACHE_TTL_MS = 5 * 60_000L

    /** SearXNG instances rotate; a dead one costs one timeout and the chain moves on. */
    private val searxInstances = listOf(
        "https://searx.be",
        "https://search.bus-hit.me",
        "https://paulgo.io",
        "https://opnxng.com",
    )

    /**
     * Search [query]; at most [limit] results, providers tried in order.
     * Never throws: an unreachable web is an empty list.
     */
    suspend fun search(query: String, limit: Int = 6): List<Hit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val n = limit.coerceIn(1, 10)
        synchronized(cache) {
            cache[q.lowercase()]?.let { (at, hits) ->
                if (System.currentTimeMillis() - at < CACHE_TTL_MS && hits.isNotEmpty()) return hits.take(n)
            }
        }
        return withContext(Dispatchers.IO) {
            val hits = firstNonEmpty(
                { ddgHtml(q) },
                { ddgLite(q) },
                { mojeek(q) },
                { searx(q) },
            ).map { normalise(it) }.filter { it.url.isNotBlank() && it.title.isNotBlank() }
                .distinctBy { it.url }
                .take(n)
            synchronized(cache) { cache[q.lowercase()] = System.currentTimeMillis() to hits }
            hits
        }
    }

    /** Render results the way every agent reads them. */
    fun format(query: String, hits: List<Hit>): String =
        if (hits.isEmpty()) "No web results for \"$query\". The search endpoints may be rate-limiting; try again later, rephrase, or web_fetch a URL you already know."
        else hits.mapIndexed { i, h ->
            "${i + 1}. ${h.title}\n   ${h.url}" + (h.snippet.takeIf { it.isNotBlank() }?.let { "\n   $it" } ?: "")
        }.joinToString("\n\n")

    // ---------------------------------------------------------- providers

    private fun firstNonEmpty(vararg providers: () -> List<Hit>): List<Hit> {
        for (p in providers) {
            val hits = runCatching { p() }.getOrDefault(emptyList())
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }

    private fun get(url: String, referer: String? = null): String? = call(
        Request.Builder().url(url).get()
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .apply { referer?.let { header("Referer", it) } }
            .build()
    )

    private fun postForm(url: String, form: FormBody, referer: String? = null): String? = call(
        Request.Builder().url(url).post(form)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .apply { referer?.let { header("Referer", it) } }
            .build()
    )

    private fun call(request: Request): String? {
        val client = AiHttp.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return null
            return r.body?.string()?.takeIf { it.isNotBlank() }
        }
    }

    /** html.duckduckgo.com: POST q; results are `.result__a` links + `.result__snippet`. */
    private fun ddgHtml(q: String): List<Hit> {
        val html = postForm(
            "https://html.duckduckgo.com/html/",
            FormBody.Builder().add("q", q).add("b", "").build(),
            referer = "https://html.duckduckgo.com/",
        ) ?: return emptyList()
        return parseDdgHtml(html)
    }

    /** lite.duckduckgo.com: the no-JS table layout; `result-link` / `result-snippet`. */
    private fun ddgLite(q: String): List<Hit> {
        val html = postForm(
            "https://lite.duckduckgo.com/lite/",
            FormBody.Builder().add("q", q).build(),
            referer = "https://lite.duckduckgo.com/",
        ) ?: return emptyList()
        return parseDdgLite(html)
    }

    private fun mojeek(q: String): List<Hit> {
        val html = get("https://www.mojeek.com/search?q=" + enc(q)) ?: return emptyList()
        return parseMojeek(html)
    }

    private fun searx(q: String): List<Hit> {
        for (base in searxInstances) {
            val html = get("$base/search?q=" + enc(q), referer = base) ?: continue
            val hits = parseSearx(html)
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // ---------------------------------------------------- pure parsers

    /** DuckDuckGo HTML endpoint. Ads (`result--ad`) are skipped. */
    fun parseDdgHtml(html: String): List<Hit> {
        val out = ArrayList<Hit>()
        // Split on result blocks, keeping each block's own opening tag in its
        // chunk so `result--ad` blocks are visible and skipped.
        val blocks = html.split(Regex("(?=<div[^>]+class=\"[^\"]*\\bresult\\b)")).drop(1)
        for (block in blocks) {
            if (block.contains("result--ad")) continue
            val href = Regex("class=\"result__a\"[^>]*?href=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)
                ?: Regex("href=\"([^\"]+)\"[^>]*?class=\"result__a\"", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1)
                ?: continue
            val title = Regex("class=\"result__a\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)?.let { stripTags(it) } ?: continue
            val snippet = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)?.let { stripTags(it) }.orEmpty()
            out += Hit(title, decodeDdgRedirect(href), snippet)
        }
        return out
    }

    /** DuckDuckGo Lite: `result-link` anchors, `result-snippet` cells. */
    fun parseDdgLite(html: String): List<Hit> {
        val out = ArrayList<Hit>()
        val links = Regex("class='result-link'[^>]*href='([^']+)'[^>]*>(.*?)</a>|href='([^']+)'[^>]*class='result-link'[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val snippets = Regex("class='result-snippet'[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).map { stripTags(it.groupValues[1]) }.toList()
        links.findAll(html).forEachIndexed { i, m ->
            val href = m.groupValues[1].ifEmpty { m.groupValues[3] }
            val title = stripTags(m.groupValues[2].ifEmpty { m.groupValues[4] })
            if (href.isBlank() || title.isBlank()) return@forEachIndexed
            out += Hit(title, decodeDdgRedirect(href), snippets.getOrNull(i).orEmpty())
        }
        return out
    }

    /** Mojeek: `<a class="title" href=...>` inside `li.results-with-links`-style blocks. */
    fun parseMojeek(html: String): List<Hit> {
        val out = ArrayList<Hit>()
        val blocks = html.split(Regex("<li[^>]*class=\"[^\"]*\\bob\\b")).drop(1)
        for (block in blocks) {
            val href = Regex("<a[^>]*class=\"[^\"]*title[^\"]*\"[^>]*href=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)
                ?: Regex("<a[^>]*href=\"([^\"]+)\"[^>]*class=\"[^\"]*title[^\"]*\"", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1)
                ?: continue
            val title = Regex("<a[^>]*class=\"[^\"]*title[^\"]*\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)?.let { stripTags(it) } ?: continue
            val snippet = Regex("<p[^>]*class=\"[^\"]*\\bs\\b[^\"]*\"[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)?.let { stripTags(it) }.orEmpty()
            if (href.startsWith("http")) out += Hit(title, href, snippet)
        }
        return out
    }

    /** SearXNG default theme: `<h3><a href=...>title</a></h3>` + `<p class="content">`. */
    fun parseSearx(html: String): List<Hit> {
        val out = ArrayList<Hit>()
        val blocks = html.split(Regex("<(?:div|article)[^>]*class=\"[^\"]*\\bresult\\b")).drop(1)
        for (block in blocks) {
            val m = Regex("<h3[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                .find(block) ?: continue
            val title = stripTags(m.groupValues[2])
            val snippet = Regex("<p[^>]*class=\"[^\"]*content[^\"]*\"[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)?.let { stripTags(it) }.orEmpty()
            if (m.groupValues[1].startsWith("http") && title.isNotBlank()) {
                out += Hit(title, m.groupValues[1], snippet)
            }
        }
        return out
    }

    /** DuckDuckGo wraps result URLs in a redirect (`/l/?uddg=...`): unwrap it. */
    fun decodeDdgRedirect(url: String): String {
        val u = if (url.startsWith("//")) "https:$url" else url
        if (!u.contains("duckduckgo.com/l/")) return u
        val uddg = Regex("[?&]uddg=([^&]+)").find(u)?.groupValues?.get(1) ?: return u
        return runCatching { URLDecoder.decode(uddg, "UTF-8") }.getOrDefault(u)
    }

    private fun normalise(hit: Hit) = hit.copy(
        title = hit.title.trim().take(160),
        url = hit.url.trim(),
        snippet = hit.snippet.trim().take(400),
    )

    /** Tags out, entities decoded, whitespace collapsed. */
    private fun stripTags(raw: String): String {
        val noTags = raw.replace(Regex("<[^>]*>"), " ")
        return HtmlText.unescape(noTags).replace(Regex("\\s+"), " ").trim()
    }
}
