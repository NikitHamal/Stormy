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
 * Keyless-first web search for every agent (coding agent, subagents, browser
 * agent), now a provider chain the user extends.
 *
 * Three kinds of source, tried in order until one brings back results:
 *  1. the user's own endpoints ([CustomSearchProviderRunner]), in their order;
 *  2. free-tier APIs whose key the user saved ([SearchPresets]: TinyFish,
 *     Brave, Exa, Tavily, Serper, Parallel, Firecrawl, Linkup, SearchAPI,
 *     You.com, Jina -- responses read by [JsonHits], so field names do not
 *     matter);
 *  3. the built-in keyless scrapers (DuckDuckGo HTML and Lite, Bing, Mojeek,
 *     public SearXNG, Wikipedia).
 *
 * A source that errors, rate-limits or comes back empty is skipped and put on
 * a short cooldown, so a blocked endpoint costs one timeout, never the
 * answer. Endpoints come and go (redesigns, captchas, limits), so nothing
 * here is fatal: a search that finds nothing anywhere says so plainly
 * instead of inventing results.
 *
 * Each provider's parsing is a pure function over the returned document, so
 * the whole chain is unit-tested against fixtures; the network part is one
 * OkHttp call per provider with hard timeouts.
 */
object WebSearch {

    data class Hit(val title: String, val url: String, val snippet: String)

    const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /** The user's setup; pushed by the app from DataStore. Empty = built-ins only. */
    @Volatile private var config: WebSearchConfig = WebSearchConfig()

    fun configure(config: WebSearchConfig) {
        this.config = config
    }

    fun currentConfig(): WebSearchConfig = config

    /** Small query cache: an agent retrying a search must not hammer endpoints. */
    private val cache = object : LinkedHashMap<String, Pair<Long, List<Hit>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<Hit>>>?) = size > 32
    }
    private const val CACHE_TTL_MS = 5 * 60_000L

    /** Providers on cooldown after a failure; id → uptime-ish wall clock. */
    private val cooldownUntil = HashMap<String, Long>()
    private const val COOLDOWN_MS = 10 * 60_000L

    /** What the settings screen shows about one source. */
    data class ProviderView(
        val id: String,
        val label: String,
        val note: String,
        val kind: Kind,
        val needsKey: Boolean,
        val hasKey: Boolean,
        val enabled: Boolean,
        val coolingDown: Boolean,
    ) {
        enum class Kind { BUILT_IN, PRESET, CUSTOM }
    }

    fun views(): List<ProviderView> = buildList {
        config.custom.forEach { c ->
            add(
                ProviderView(
                    c.id, c.label, c.urlTemplate.take(60), ProviderView.Kind.CUSTOM,
                    needsKey = c.bearer || c.keyHeader != null || c.keyParam != null || c.urlTemplate.contains("{key}") || c.bodyTemplate?.contains("{key}") == true,
                    hasKey = c.key.isNotBlank(), enabled = c.enabled, coolingDown = cooling(c.id),
                )
            )
        }
        SearchPresets.all.forEach { p ->
            val key = config.presetKeys[p.id].orEmpty()
            add(
                ProviderView(
                    p.id, p.label, p.note, ProviderView.Kind.PRESET,
                    needsKey = true, hasKey = key.isNotBlank(),
                    enabled = key.isNotBlank() && config.presetEnabled(p.id), coolingDown = cooling(p.id),
                )
            )
        }
        BuiltInSearch.ids.forEach { id ->
            add(
                ProviderView(
                    id, labelOfBuiltIn(id), BuiltInSearch.notes[id].orEmpty(), ProviderView.Kind.BUILT_IN,
                    needsKey = false, hasKey = true, enabled = config.builtInEnabled(id), coolingDown = cooling(id),
                )
            )
        }
    }

    private fun labelOfBuiltIn(id: String): String = when (id) {
        BuiltInSearch.DDG_HTML -> "DuckDuckGo (HTML)"
        BuiltInSearch.DDG_LITE -> "DuckDuckGo (Lite)"
        BuiltInSearch.BING -> "Bing"
        BuiltInSearch.MOJEEK -> "Mojeek"
        BuiltInSearch.SEARX -> "SearXNG (public)"
        BuiltInSearch.WIKIPEDIA -> "Wikipedia"
        else -> id
    }

    private fun cooling(id: String): Boolean = (cooldownUntil[id] ?: 0L) > System.currentTimeMillis()

    /** The chain for this run, in try-order. */
    fun chain(cfg: WebSearchConfig = config): List<SearchProvider> = buildList {
        cfg.custom.filter { it.enabled }.forEach { add(CustomSearchProviderRunner(it)) }
        SearchPresets.all.forEach { p ->
            if (cfg.presetEnabled(p.id) && cfg.presetKeys[p.id]?.isNotBlank() == true) add(p)
        }
        if (cfg.builtInEnabled(BuiltInSearch.DDG_HTML)) add(builtInDdgHtml)
        if (cfg.builtInEnabled(BuiltInSearch.DDG_LITE)) add(builtInDdgLite)
        if (cfg.builtInEnabled(BuiltInSearch.BING)) add(builtInBing)
        if (cfg.builtInEnabled(BuiltInSearch.MOJEEK)) add(builtInMojeek)
        if (cfg.builtInEnabled(BuiltInSearch.SEARX)) add(builtInSearx)
        if (cfg.builtInEnabled(BuiltInSearch.WIKIPEDIA)) add(builtInWikipedia)
    }

    /**
     * Search [query]; at most [limit] results. Never throws: an unreachable
     * web is an empty list.
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
        val hits = withContext(Dispatchers.IO) {
            var result = emptyList<Hit>()
            for (p in chain()) {
                if (cooling(p.id)) continue
                if (!p.ready(config)) continue
                val found = runCatching { p.search(q, n, config) }.getOrDefault(emptyList())
                if (found.isNotEmpty()) {
                    result = found
                    cooldownUntil.remove(p.id)
                    break
                }
                cooldownUntil[p.id] = System.currentTimeMillis() + COOLDOWN_MS
            }
            // Every healthy source failed: one last pass ignoring cooldowns,
            // so a flaky-but-only source still gets its say.
            if (result.isEmpty()) {
                for (p in chain()) {
                    if (!p.ready(config)) continue
                    val found = runCatching { p.search(q, n, config) }.getOrDefault(emptyList())
                    if (found.isNotEmpty()) { result = found; break }
                }
            }
            result.map { normalise(it) }.filter { it.url.isNotBlank() && it.title.isNotBlank() }
                .distinctBy { it.url }.take(n)
        }
        synchronized(cache) { cache[q.lowercase()] = System.currentTimeMillis() to hits }
        return hits
    }

    /** One provider, ignoring health: the settings screen's Test button. */
    suspend fun testProvider(id: String, query: String = "kotlin coroutines"): List<Hit> {
        val cfg = config
        val provider = chain(cfg).firstOrNull { it.id == id }
            ?: cfg.custom.firstOrNull { it.id == id }?.let { CustomSearchProviderRunner(it) }
            ?: SearchPresets.byId(id)
            ?: return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { provider.search(query, 5, cfg) }.getOrDefault(emptyList())
        }
    }

    /** Render results the way every agent reads them. */
    fun format(query: String, hits: List<Hit>): String =
        if (hits.isEmpty()) "No web results for \"$query\". Every configured search source came back empty or blocked this network; try again later, rephrase, or add a provider with a key in Settings → Web search. You can also web_fetch a URL you already know."
        else hits.mapIndexed { i, h ->
            "${i + 1}. ${h.title}\n   ${h.url}" + (h.snippet.takeIf { it.isNotBlank() }?.let { "\n   $it" } ?: "")
        }.joinToString("\n\n")

    // ---------------------------------------------------- built-ins

    private val builtInDdgHtml = HtmlSearchProvider(
        id = BuiltInSearch.DDG_HTML, label = labelOfBuiltIn(BuiltInSearch.DDG_HTML),
        note = BuiltInSearch.notes.getValue(BuiltInSearch.DDG_HTML),
        fetch = { q, _ ->
            postForm(
                "https://html.duckduckgo.com/html/",
                FormBody.Builder().add("q", q).add("b", "").build(),
                referer = "https://html.duckduckgo.com/",
            )
        },
        parse = ::parseDdgHtml,
    )

    private val builtInDdgLite = HtmlSearchProvider(
        id = BuiltInSearch.DDG_LITE, label = labelOfBuiltIn(BuiltInSearch.DDG_LITE),
        note = BuiltInSearch.notes.getValue(BuiltInSearch.DDG_LITE),
        fetch = { q, _ ->
            postForm("https://lite.duckduckgo.com/lite/", FormBody.Builder().add("q", q).build(), referer = "https://lite.duckduckgo.com/")
        },
        parse = ::parseDdgLite,
    )

    private val builtInBing = HtmlSearchProvider(
        id = BuiltInSearch.BING, label = labelOfBuiltIn(BuiltInSearch.BING),
        note = BuiltInSearch.notes.getValue(BuiltInSearch.BING),
        fetch = { q, _ -> get("https://www.bing.com/search?q=" + enc(q) + "&count=10") },
        parse = ::parseBing,
    )

    private val builtInMojeek = HtmlSearchProvider(
        id = BuiltInSearch.MOJEEK, label = labelOfBuiltIn(BuiltInSearch.MOJEEK),
        note = BuiltInSearch.notes.getValue(BuiltInSearch.MOJEEK),
        fetch = { q, _ -> get("https://www.mojeek.com/search?q=" + enc(q)) },
        parse = ::parseMojeek,
    )

    /** SearXNG instances rotate; a dead one costs one timeout and the chain moves on. */
    private val searxInstances = listOf(
        "https://searx.be",
        "https://search.bus-hit.me",
        "https://paulgo.io",
        "https://opnxng.com",
    )

    private val builtInSearx = HtmlSearchProvider(
        id = BuiltInSearch.SEARX, label = labelOfBuiltIn(BuiltInSearch.SEARX),
        note = BuiltInSearch.notes.getValue(BuiltInSearch.SEARX),
        fetch = { q, _ ->
            var html: String? = null
            for (base in searxInstances) {
                html = get("$base/search?q=" + enc(q), referer = base)
                if (html != null && parseSearx(html).isNotEmpty()) break
                html = null
            }
            html
        },
        parse = ::parseSearx,
    )

    private val builtInWikipedia = HtmlSearchProvider(
        id = BuiltInSearch.WIKIPEDIA, label = labelOfBuiltIn(BuiltInSearch.WIKIPEDIA),
        note = BuiltInSearch.notes.getValue(BuiltInSearch.WIKIPEDIA),
        fetch = { q, n ->
            get("https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=" +
                n.coerceIn(1, 10) + "&srsearch=" + enc(q))
        },
        parse = ::parseWikipedia,
    )

    // ------------------------------------------------------------ http

    private fun get(url: String, referer: String? = null): String? = call(
        Request.Builder().url(url).get()
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/json")
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

    private fun call(request: Request): String? = runCatching {
        val client = AiHttp.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.string()?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

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

    /** Mojeek: `<a class="title" href=...>` inside `li.ob` blocks. */
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

    /** Bing SERP: `<li class="b_algo">` blocks, title in `b_algoheader`, snippet in `b_lineclamp*`. */
    fun parseBing(html: String): List<Hit> {
        val out = ArrayList<Hit>()
        val blocks = html.split("<li class=\"b_algo\"").drop(1)
        for (block in blocks) {
            val head = Regex("<div class=\"b_algoheader\"[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                .find(block)
                ?: Regex("<a[^>]*href=\"(https?://[^\"]+)\"[^>]*>\\s*<h2>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL).find(block)
                ?: continue
            val title = stripTags(head.groupValues[2])
            val snippet = Regex("<p class=\"b_lineclamp[^\"]*\"[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)?.let { stripTags(it) }
                ?.replace(Regex("^[A-Z][a-z]{2} \\d{1,2}, \\d{4}\\s*[\u00b7\u2022-]?\\s*"), "")
                .orEmpty()
            if (title.isNotBlank()) out += Hit(title, head.groupValues[1], snippet)
        }
        return out
    }

    /** Wikipedia search API JSON (query.search[]). */
    fun parseWikipedia(json: String): List<Hit> = runCatching {
        val root = AiHttp.json.parseToJsonElement(json)
            .let { it as? kotlinx.serialization.json.JsonObject } ?: return emptyList()
        val arr = (root["query"] as? kotlinx.serialization.json.JsonObject)
            ?.get("search") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        arr.mapNotNull { el ->
            val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val title = (o["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val snippet = (o["snippet"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?.let { stripTags(it) }.orEmpty()
            val url = runCatching {
                java.net.URI("https", "en.wikipedia.org", "/wiki/" + title.replace(' ', '_'), null).toASCIIString()
            }.getOrDefault("https://en.wikipedia.org/wiki/" + title.replace(' ', '_'))
            Hit(title, url, snippet)
        }
    }.getOrDefault(emptyList())

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
