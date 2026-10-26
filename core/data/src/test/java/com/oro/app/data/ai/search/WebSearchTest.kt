package com.oro.app.data.ai.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The parsers are the part that breaks when an endpoint redesigns, so each
 * runs against a fixture shaped like the real page (attribute order included:
 * the regexes accept both `class ... href` and `href ... class`).
 */
class WebSearchTest {

    @Test
    fun `ddg html results parse with redirect unwrapped and ads skipped`() {
        val html = """
            <div class="result results_links results_links_deep web-result">
              <div class="links_main links_deep result__body">
                <h2 class="result__title">
                  <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2Fdocs%2Fcoroutines.html&amp;rut=abc123">Kotlin <b>Coroutines</b> guide</a>
                </h2>
                <a class="result__snippet" href="//duckduckgo.com/l/?uddg=x">Official <b>documentation</b> for structured concurrency &amp; flow.</a>
              </div>
            </div>
            <div class="result result--ad results_links">
              <div class="result__body">
                <a rel="nofollow" class="result__a" href="https://ads.example.com/buy">BUY COROUTINES</a>
                <a class="result__snippet" href="x">An ad nobody asked for</a>
              </div>
            </div>
            <div class="result results_links web-result">
              <div class="result__body">
                <a rel="nofollow" class="result__a" href="https://developer.android.com/kotlin/coroutines">Android coroutines</a>
                <a class="result__snippet" href="y">Use coroutines on Android for <b>background</b> work.</a>
              </div>
            </div>
        """.trimIndent()
        val hits = WebSearch.parseDdgHtml(html)
        assertThat(hits).hasSize(2)
        assertThat(hits[0].url).isEqualTo("https://kotlinlang.org/docs/coroutines.html")
        assertThat(hits[0].title).isEqualTo("Kotlin Coroutines guide")
        assertThat(hits[0].snippet).contains("structured concurrency & flow")
        assertThat(hits[1].url).isEqualTo("https://developer.android.com/kotlin/coroutines")
        assertThat(hits.none { it.title.contains("BUY") }).isTrue()
    }

    @Test
    fun `ddg lite parses both attribute orders`() {
        val html = """
            <table>
            <tr><td><a rel="nofollow" href='https://a.example/one' class='result-link'>First &amp; best</a></td></tr>
            <tr><td class='result-snippet'>Snippet for the first one</td></tr>
            <tr><td><a rel="nofollow" class='result-link' href='https://b.example/two'>Second result</a></td></tr>
            <tr><td class='result-snippet'>Snippet for the second</td></tr>
            </table>
        """.trimIndent()
        val hits = WebSearch.parseDdgLite(html)
        assertThat(hits).hasSize(2)
        assertThat(hits[0].url).isEqualTo("https://a.example/one")
        assertThat(hits[0].title).isEqualTo("First & best")
        assertThat(hits[0].snippet).isEqualTo("Snippet for the first one")
        assertThat(hits[1].url).isEqualTo("https://b.example/two")
    }

    @Test
    fun `mojeek results parse`() {
        val html = """
            <ul class="results-standard">
            <li class="ob"><div class="title"><a class="title" href="https://example.com/page">Example &mdash; a page</a></div>
            <p class="s">The snippet of the example.</p></li>
            <li class="ob"><div class="title"><a href="https://second.example" class="title">Second</a></div>
            <p class="s">Another snippet.</p></li>
            </ul>
        """.trimIndent()
        val hits = WebSearch.parseMojeek(html)
        assertThat(hits).hasSize(2)
        assertThat(hits[0].url).isEqualTo("https://example.com/page")
        assertThat(hits[0].title).isEqualTo("Example \u2014 a page")
        assertThat(hits[0].snippet).isEqualTo("The snippet of the example.")
        assertThat(hits[1].url).isEqualTo("https://second.example")
    }

    @Test
    fun `searxng default theme parses`() {
        val html = """
            <div id="main_results">
            <div class="result default">
              <h3><a href="https://example.org/doc" rel="noreferrer">Example Doc</a></h3>
              <p class="content">Content snippet with <strong>markup</strong> stripped.</p>
            </div>
            <article class="result paper">
              <h3 class="title"><a href="https://papers.example/1">A paper</a></h3>
              <p class="content">Paper abstract.</p>
            </article>
            </div>
        """.trimIndent()
        val hits = WebSearch.parseSearx(html)
        assertThat(hits).hasSize(2)
        assertThat(hits[0].url).isEqualTo("https://example.org/doc")
        assertThat(hits[0].snippet).isEqualTo("Content snippet with markup stripped.")
        assertThat(hits[1].title).isEqualTo("A paper")
    }

    @Test
    fun `redirect unwrapping handles every shape`() {
        assertThat(WebSearch.decodeDdgRedirect("//duckduckgo.com/l/?uddg=https%3A%2F%2Fx.dev%2Fa%3Fb%3D1&rut=1"))
            .isEqualTo("https://x.dev/a?b=1")
        assertThat(WebSearch.decodeDdgRedirect("https://duckduckgo.com/l/?uddg=http%3A%2F%2Fplain.dev"))
            .isEqualTo("http://plain.dev")
        assertThat(WebSearch.decodeDdgRedirect("https://example.com/normal")).isEqualTo("https://example.com/normal")
    }

    @Test
    fun `empty or broken pages yield no hits instead of garbage`() {
        assertThat(WebSearch.parseDdgHtml("<html><body>Anomaly detected, please enable JavaScript</body></html>")).isEmpty()
        assertThat(WebSearch.parseDdgLite("")).isEmpty()
        assertThat(WebSearch.parseMojeek("<div>nothing here</div>")).isEmpty()
        assertThat(WebSearch.parseSearx("<html></html>")).isEmpty()
    }

    @Test
    fun `format renders numbered hits and says so when empty`() {
        val text = WebSearch.format(
            "kotlin coroutines",
            listOf(
                WebSearch.Hit("Kotlin Coroutines", "https://kotlinlang.org/docs/coroutines.html", "Official docs"),
                WebSearch.Hit("Android", "https://developer.android.com/kotlin/coroutines", ""),
            )
        )
        assertThat(text).contains("1. Kotlin Coroutines")
        assertThat(text).contains("https://kotlinlang.org/docs/coroutines.html")
        assertThat(text).contains("2. Android")
        assertThat(WebSearch.format("nothing", emptyList())).contains("No web results")
    }
}
