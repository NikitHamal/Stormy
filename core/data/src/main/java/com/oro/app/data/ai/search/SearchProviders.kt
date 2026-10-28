package com.oro.app.data.ai.search

import com.oro.app.data.ai.AiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * One source of web search results.
 *
 * Implementations are thin: build a request, parse the response. All the
 * judgement (ordering, health, caching) lives in [WebSearch]. A provider that
 * cannot answer -- blocked, rate-limited, redesigned -- returns an empty list
 * and lets the next one try; that is the whole reliability story.
 */
interface SearchProvider {
    /** Stable id, used in settings and health tracking. */
    val id: String

    /** Shown in the settings screen. */
    val label: String

    /** One line for the settings screen: what it costs, what it needs. */
    val note: String

    /** False when the provider cannot answer without a key the user has not given. */
    fun ready(config: WebSearchConfig): Boolean

    suspend fun search(query: String, limit: Int, config: WebSearchConfig): List<WebSearch.Hit>
}

/** Where a provider's API key goes on the request. */
sealed interface KeyPlacement {
    data object None : KeyPlacement
    data class Header(val name: String) : KeyPlacement
    data class Query(val param: String) : KeyPlacement
    data object Bearer : KeyPlacement
    /** A field inside the JSON body (the body template carries `{key}`). */
    data object Body : KeyPlacement
}

/**
 * A JSON search API: the request is described, the response is read by
 * [JsonHits] -- so any API with URL-ish/title-ish fields works, including
 * ones nobody has written a parser for (which is the point: users add their
 * own from the settings screen).
 */
class JsonSearchProvider(
    override val id: String,
    override val label: String,
    override val note: String,
    private val method: String = "GET",
    /** `{query}`, `{limit}`, `{key}` are substituted. */
    private val urlTemplate: String,
    private val keyPlacement: KeyPlacement = KeyPlacement.None,
    /** POST body; `{query}`, `{limit}`, `{key}` substituted. Null for GET. */
    private val bodyTemplate: String? = null,
    private val extraHeaders: Map<String, String> = emptyMap(),
) : SearchProvider {

    override fun ready(config: WebSearchConfig): Boolean =
        keyPlacement == KeyPlacement.None || config.presetKeys[id]?.isNotBlank() == true ||
            config.custom.firstOrNull { it.id == id }?.key?.isNotBlank() == true

    fun keyFor(config: WebSearchConfig): String =
        config.presetKeys[id] ?: config.custom.firstOrNull { it.id == id }?.key.orEmpty()

    override suspend fun search(query: String, limit: Int, config: WebSearchConfig): List<WebSearch.Hit> {
        val key = keyFor(config)
        if (keyPlacement != KeyPlacement.None && key.isBlank()) return emptyList()
        val url = fill(urlTemplate, query, limit, key, keyPlacement as? KeyPlacement.Query)
        val body = bodyTemplate?.let { fill(it, query, limit, key, null) }
        val raw = http(url, method, body, key, config) ?: return emptyList()
        return JsonHits.sniff(raw).take(limit)
    }

    private fun fill(template: String, query: String, limit: Int, key: String, keyAsQuery: KeyPlacement.Query?): String {
        var out = template
            .replace("{query}", enc(query))
            .replace("{limit}", limit.toString())
        out = when (keyAsQuery) {
            null -> out.replace("{key}", enc(key))
            else -> out // the query param is appended below
        }
        return out
    }

    private suspend fun http(urlIn: String, method: String, body: String?, key: String, config: WebSearchConfig): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = when (val kp = keyPlacement) {
                    is KeyPlacement.Query -> urlIn + (if (urlIn.contains('?')) "&" else "?") + kp.param + "=" + enc(key)
                    else -> urlIn
                }
                val builder = Request.Builder().url(url)
                    .header("User-Agent", WebSearch.UA)
                    .header("Accept", "application/json, text/html;q=0.9")
                when (val kp = keyPlacement) {
                    is KeyPlacement.Header -> builder.header(kp.name, key)
                    KeyPlacement.Bearer -> builder.header("Authorization", "Bearer $key")
                    else -> Unit
                }
                extraHeaders.forEach { (k, v) -> builder.header(k, v) }
                if (method == "POST") {
                    val b = body.orEmpty()
                    builder.post(b.toRequestBody("application/json".toMediaType()))
                } else {
                    builder.get()
                }
                val client = AiHttp.client.newBuilder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .callTimeout(20, TimeUnit.SECONDS)
                    .build()
                client.newCall(builder.build()).execute().use { r ->
                    if (!r.isSuccessful) null else r.body?.string()?.takeIf { it.isNotBlank() }
                }
            }.getOrNull()
        }

    companion object {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }
}

/**
 * An HTML endpoint with a hand-written parser: the keyless scrapers, where
 * the markup is the API and a fixture-tested parser is the contract.
 */
class HtmlSearchProvider(
    override val id: String,
    override val label: String,
    override val note: String,
    private val fetch: suspend (query: String, limit: Int) -> String?,
    private val parse: (String) -> List<WebSearch.Hit>,
) : SearchProvider {
    override fun ready(config: WebSearchConfig): Boolean = true
    override suspend fun search(query: String, limit: Int, config: WebSearchConfig): List<WebSearch.Hit> =
        (fetch(query, limit)?.let { parse(it) } ?: emptyList()).take(limit)
}

/** A user's own endpoint: JSON sniffed, SearXNG JSON, or HTML by regex trio. */
class CustomSearchProviderRunner(private val spec: CustomSearchProvider) : SearchProvider {
    override val id: String get() = spec.id
    override val label: String get() = spec.label
    override val note: String get() = "Your provider"

    override fun ready(config: WebSearchConfig): Boolean =
        config.custom.any { it.id == spec.id && it.enabled }

    override suspend fun search(query: String, limit: Int, config: WebSearchConfig): List<WebSearch.Hit> {
        val s = config.custom.firstOrNull { it.id == spec.id } ?: spec
        if (!s.enabled) return emptyList()
        val url = s.urlTemplate
            .replace("{query}", JsonSearchProvider.enc(query))
            .replace("{limit}", limit.toString())
            .replace("{key}", JsonSearchProvider.enc(s.key))
        return withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder().url(url)
                    .header("User-Agent", WebSearch.UA)
                if (s.bearer && s.key.isNotBlank()) builder.header("Authorization", "Bearer " + s.key)
                s.keyHeader?.takeIf { s.key.isNotBlank() }?.let { builder.header(it, s.key) }
                if (s.method.equals("POST", true)) {
                    // Raw JSON string: the user controls escaping in their template.
                    val body = s.bodyTemplate
                        ?.replace("{query}", query)
                        ?.replace("{limit}", limit.toString())
                        ?.replace("{key}", s.key)
                        ?: "{\"query\":\"$query\"}"
                    builder.post(body.toRequestBody("application/json".toMediaType()))
                }
                val client = AiHttp.client.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
                (client.newCall(builder.build()).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val raw = r.body?.string() ?: return@use null
                    when (s.kind) {
                        CustomKind.JSON_SNIFF -> JsonHits.sniff(raw)
                        CustomKind.SEARXNG_JSON -> parseSearxngJson(raw)
                        CustomKind.HTML_GENERIC -> parseHtmlGeneric(raw, s)
                    }.take(limit)
                }) ?: emptyList()
            }.getOrDefault(emptyList())
        }
    }

    private fun parseSearxngJson(raw: String): List<WebSearch.Hit> = JsonHits.sniff(raw)

    private fun parseHtmlGeneric(raw: String, s: CustomSearchProvider): List<WebSearch.Hit> {
        val urlPattern = s.htmlUrlPattern?.let { Regex(it, RegexOption.DOT_MATCHES_ALL) }
            ?: Regex("<a[^>]+href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val snippetPattern = s.htmlSnippetPattern?.let { Regex(it, RegexOption.DOT_MATCHES_ALL) }
        val out = ArrayList<WebSearch.Hit>()
        val snippets = snippetPattern?.findAll(raw)?.map { it.groupValues.getOrNull(1).orEmpty() }?.toList().orEmpty()
        urlPattern.findAll(raw).forEachIndexed { i, m ->
            val url = m.groupValues[1]
            val title = m.groupValues.getOrNull(2)?.let { it.replace(Regex("<[^>]*>"), " ").trim() }.orEmpty()
            if (title.isBlank()) return@forEachIndexed
            out += WebSearch.Hit(title, url, snippets.getOrNull(i)?.let { it.replace(Regex("<[^>]*>"), " ").trim() }.orEmpty())
        }
        return out
    }
}

/**
 * The keyed presets: free tiers that answer structured JSON, wired with the
 * right verb, key placement and body shape. The response is read by
 * [JsonHits], so a renamed field next year does not break anything.
 * Free-tier notes as of 2026-10 (docs/research/web-search.md).
 */
object SearchPresets {

    val all: List<JsonSearchProvider> = listOf(
        JsonSearchProvider(
            id = "tinyfish", label = "TinyFish Search",
            note = "Free on every plan · 30 queries/min · key from agent.tinyfish.ai",
            urlTemplate = "https://api.search.tinyfish.ai?query={query}",
            keyPlacement = KeyPlacement.Header("X-API-Key"),
        ),
        JsonSearchProvider(
            id = "brave", label = "Brave Search",
            note = "Own index · 2,000 queries/mo free (non-commercial)",
            urlTemplate = "https://api.search.brave.com/res/v1/web/search?q={query}&count={limit}",
            keyPlacement = KeyPlacement.Header("X-Subscription-Token"),
        ),
        JsonSearchProvider(
            id = "exa", label = "Exa",
            note = "Neural/semantic index · 1,000 searches/mo free",
            method = "POST",
            urlTemplate = "https://api.exa.ai/search",
            keyPlacement = KeyPlacement.Header("x-api-key"),
            bodyTemplate = "{\"query\":\"{query}\",\"numResults\":{limit},\"contents\":{\"text\":true}}",
        ),
        JsonSearchProvider(
            id = "tavily", label = "Tavily",
            note = "1,000 credits/mo free · no card",
            method = "POST",
            urlTemplate = "https://api.tavily.com/search",
            keyPlacement = KeyPlacement.Bearer,
            bodyTemplate = "{\"query\":\"{query}\",\"max_results\":{limit}}",
        ),
        JsonSearchProvider(
            id = "serper", label = "Serper (Google SERP)",
            note = "Google results · 2,500 queries free, one-time",
            method = "POST",
            urlTemplate = "https://google.serper.dev/search",
            keyPlacement = KeyPlacement.Header("X-API-KEY"),
            bodyTemplate = "{\"q\":\"{query}\",\"num\":{limit}}",
        ),
        JsonSearchProvider(
            id = "parallel", label = "Parallel Search",
            note = "Own index · free starter credits",
            method = "POST",
            urlTemplate = "https://api.parallel.ai/v1beta/search",
            keyPlacement = KeyPlacement.Bearer,
            bodyTemplate = "{\"query\":\"{query}\",\"mode\":\"fast\",\"size\":{limit}}",
        ),
        JsonSearchProvider(
            id = "firecrawl", label = "Firecrawl Search",
            note = "Search + extraction · free credits",
            method = "POST",
            urlTemplate = "https://api.firecrawl.dev/v2/search",
            keyPlacement = KeyPlacement.Bearer,
            bodyTemplate = "{\"query\":\"{query}\",\"limit\":{limit}}",
        ),
        JsonSearchProvider(
            id = "linkup", label = "Linkup",
            note = "Premium/paywalled sources · €5/mo free credits",
            urlTemplate = "https://api.linkup.so/v1/search?q={query}&depth=standard",
            keyPlacement = KeyPlacement.Bearer,
        ),
        JsonSearchProvider(
            id = "searchapi", label = "SearchAPI.io",
            note = "Google/Bing SERP · 100 searches free",
            urlTemplate = "https://www.searchapi.io/api/v1/search?engine=google&q={query}",
            keyPlacement = KeyPlacement.Query("api_key"),
        ),
        JsonSearchProvider(
            id = "you", label = "You.com",
            note = "Own index · free developer tier",
            urlTemplate = "https://api.you.com/smart?query={query}",
            keyPlacement = KeyPlacement.Header("x-api-key"),
        ),
        JsonSearchProvider(
            id = "jina", label = "Jina Search",
            note = "s.jina.ai · free tier with key",
            urlTemplate = "https://s.jina.ai/{query}",
            keyPlacement = KeyPlacement.Bearer,
            extraHeaders = mapOf("Accept" to "application/json"),
        ),
    )

    fun byId(id: String): JsonSearchProvider? = all.firstOrNull { it.id == id }
}
