package com.oro.app.data.ai.search

import kotlinx.serialization.Serializable

/** What a user-added endpoint speaks. */
@Serializable
enum class CustomKind {
    /** Any JSON search API; results found by [JsonHits]. */
    JSON_SNIFF,

    /** A SearXNG instance with `format=json` enabled. */
    SEARXNG_JSON,

    /** An HTML page; results pulled by regex (URL pattern, optional snippet pattern). */
    HTML_GENERIC,
}

/**
 * A search endpoint the user wired themselves from Settings → Web search.
 *
 * `{query}`, `{limit}` and `{key}` are substituted in the URL template (and
 * in the body template, raw). The key can travel as a header, a query param
 * or a bearer token; it lives in the app's DataStore, never in a project.
 */
@Serializable
data class CustomSearchProvider(
    val id: String,
    val label: String,
    val kind: CustomKind = CustomKind.JSON_SNIFF,
    val method: String = "GET",
    val urlTemplate: String,
    val key: String = "",
    /** Header name to send the key in, e.g. `X-API-Key`. */
    val keyHeader: String? = null,
    /** Query param to send the key in, e.g. `api_key`. */
    val keyParam: String? = null,
    val bearer: Boolean = false,
    /** POST body; omitted for GET. */
    val bodyTemplate: String? = null,
    /** HTML_GENERIC: regex whose group 1 is the URL and group 2 the title. */
    val htmlUrlPattern: String? = null,
    /** HTML_GENERIC: regex whose group 1 is the snippet. */
    val htmlSnippetPattern: String? = null,
    val enabled: Boolean = true,
)

/**
 * The user's web-search setup: which sources are on, which keys are saved,
 * and what they wired themselves. Stored as JSON in DataStore
 * ([com.oro.app.data.repository.PreferencesRepository.webSearchConfig]),
 * additive with defaults like every other settings blob here.
 */
@Serializable
data class WebSearchConfig(
    /** Built-in keyless scrapers the user turned off. */
    val disabledBuiltIns: List<String> = emptyList(),
    /** Keys for the free-tier presets, by preset id. */
    val presetKeys: Map<String, String> = emptyMap(),
    /** Presets the user turned off (a saved key does not mean "always use"). */
    val disabledPresets: List<String> = emptyList(),
    /** The user's own endpoints, tried first, in this order. */
    val custom: List<CustomSearchProvider> = emptyList(),
) {
    fun builtInEnabled(id: String): Boolean = id !in disabledBuiltIns
    fun presetEnabled(id: String): Boolean = id !in disabledPresets
}

/** The built-in keyless sources, in default order. */
object BuiltInSearch {
    const val DDG_HTML = "ddg_html"
    const val DDG_LITE = "ddg_lite"
    const val BING = "bing"
    const val MOJEEK = "mojeek"
    const val SEARX = "searx"
    const val WIKIPEDIA = "wikipedia"

    val ids = listOf(DDG_HTML, DDG_LITE, BING, MOJEEK, SEARX, WIKIPEDIA)

    val notes = mapOf(
        DDG_HTML to "Keyless · DuckDuckGo HTML · residential IPs fare best",
        DDG_LITE to "Keyless · DuckDuckGo Lite (no-JS table layout)",
        BING to "Keyless · Bing SERP · answers even from datacenter IPs",
        MOJEEK to "Keyless · independent index · strict rate limits",
        SEARX to "Keyless · public SearXNG instances, rotated",
        WIKIPEDIA to "Keyless · Wikipedia API · entities and facts",
    )
}
