package com.oro.app.data.ai.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The sniffer is what lets any JSON search API -- today's presets and
 * tomorrow's, plus whatever a user wires -- work without a parser of its
 * own. Fixtures in every real shape the chain meets.
 */
class JsonHitsTest {

    @Test
    fun `brave's nested web results are found`() {
        val json = """
            {"query":{"parsed":{"q":"kotlin"}},"mixed":{"type":"search"},
             "web":{"type":"search","results":[
               {"title":"Kotlin Programming Language","url":"https://kotlinlang.org/","description":"Official site"},
               {"title":"Kotlin - Wikipedia","url":"https://en.wikipedia.org/wiki/Kotlin","description":"Cross-platform language"}
             ]}}
        """.trimIndent()
        val hits = JsonHits.sniff(json)
        assertThat(hits).hasSize(2)
        assertThat(hits[0].url).isEqualTo("https://kotlinlang.org/")
        assertThat(hits[0].snippet).isEqualTo("Official site")
    }

    @Test
    fun `serper's organic links and snippets are found`() {
        val json = """
            {"searchParameters":{"q":"x"},"organic":[
              {"title":"A","link":"https://a.example/1","snippet":"one"},
              {"title":"B","link":"https://b.example/2","snippet":"two"}
            ],"peopleAlsoAsk":[{"question":"Q","link":"https://q.example"}]}
        """.trimIndent()
        val hits = JsonHits.sniff(json)
        // The organic array scores higher (snippets present) than peopleAlsoAsk.
        assertThat(hits.map { it.url }).containsAtLeast("https://a.example/1", "https://b.example/2").inOrder()
    }

    @Test
    fun `google cse items are found`() {
        val json = """
            {"queries":{"request":[{"searchTerms":"x"}]},"items":[
              {"kind":"customsearch#result","title":"Doc","link":"https://docs.example/a","snippet":"how to"},
              {"title":"Doc2","link":"https://docs.example/b","htmlSnippet":"<b>how</b> to"}
            ]}
        """.trimIndent()
        val hits = JsonHits.sniff(json)
        assertThat(hits).hasSize(2)
        assertThat(hits[1].snippet).isEqualTo("how to")
    }

    @Test
    fun `tavily and exa shapes are found`() {
        val tavily = """{"answer":null,"results":[{"title":"T","url":"https://t.example","content":"body text"}]}"""
        assertThat(JsonHits.sniff(tavily).single().snippet).isEqualTo("body text")
        val exa = """{"results":[{"title":"E","url":"https://e.example","text":"neighbourhood text","score":0.9}],"requestId":"x"}"""
        assertThat(JsonHits.sniff(exa).single().url).isEqualTo("https://e.example")
    }

    @Test
    fun `markdown tags inside titles and snippets are stripped`() {
        val json = """{"results":[{"title":"<b>Kotlin</b> docs","url":"https://k.example","description":"&amp; more <i>info</i>"}]}"""
        val hit = JsonHits.sniff(json).single()
        assertThat(hit.title).isEqualTo("Kotlin docs")
        assertThat(hit.snippet).isEqualTo("& more info")
    }

    @Test
    fun `documents without results yield nothing`() {
        assertThat(JsonHits.sniff("""{"error":"rate limited"}""")).isEmpty()
        assertThat(JsonHits.sniff("not json at all")).isEmpty()
        assertThat(JsonHits.sniff("""{"results":[{"title":"no url here"}]}""")).isEmpty()
    }

    @Test
    fun `url-ish fields without http values are not results`() {
        val json = """{"data":[{"title":"A","url":"/relative/path"},{"title":"B","url":"https://ok.example"}]}"""
        val hits = JsonHits.sniff(json)
        assertThat(hits).hasSize(1)
        assertThat(hits[0].url).isEqualTo("https://ok.example")
    }

    @Test
    fun `deeply nested arrays are still found within the depth cap`() {
        val json = """{"a":{"b":{"c":{"d":{"results":[{"title":"Deep","url":"https://d.example","snippet":"s"}]}}}}}"""
        assertThat(JsonHits.sniff(json)).hasSize(1)
    }
}

class WebSearchConfigTest {

    @Test
    fun `config round-trips through its stored JSON`() {
        val cfg = WebSearchConfig(
            disabledBuiltIns = listOf("mojeek"),
            presetKeys = mapOf("tinyfish" to "tf_key", "brave" to "BSAkey"),
            disabledPresets = listOf("brave"),
            custom = listOf(
                CustomSearchProvider(
                    id = "custom-1", label = "Home SearXNG", kind = CustomKind.SEARXNG_JSON,
                    urlTemplate = "https://searx.lan/search?q={query}&format=json", enabled = true,
                ),
                CustomSearchProvider(
                    id = "custom-2", label = "Work API", kind = CustomKind.JSON_SNIFF,
                    method = "POST", urlTemplate = "https://api.work.dev/search",
                    keyHeader = "X-API-Key", key = "sekrit",
                    bodyTemplate = """{"q":"{query}","n":{limit}}""",
                ),
            ),
        )
        val raw = com.oro.app.data.ai.AiHttp.json.encodeToString(WebSearchConfig.serializer(), cfg)
        val back = com.oro.app.data.ai.AiHttp.json.decodeFromString(WebSearchConfig.serializer(), raw)
        assertThat(back).isEqualTo(cfg)
    }

    @Test
    fun `defaults keep every keyless engine on and every preset off`() {
        val cfg = WebSearchConfig()
        assertThat(BuiltInSearch.ids.all { cfg.builtInEnabled(it) }).isTrue()
        assertThat(cfg.presetEnabled("brave")).isTrue()
        assertThat(cfg.presetKeys).isEmpty()
        assertThat(cfg.custom).isEmpty()
    }

    @Test
    fun `the chain puts the user's sources first, then keyed presets, then keyless`() {
        val cfg = WebSearchConfig(
            presetKeys = mapOf("exa" to "k"),
            disabledBuiltIns = listOf("mojeek", "searx"),
            custom = listOf(CustomSearchProvider(id = "custom-1", label = "Mine", urlTemplate = "https://x/{query}")),
        )
        val ids = WebSearch.chain(cfg).map { it.id }
        assertThat(ids.first()).isEqualTo("custom-1")
        assertThat(ids).contains("exa")
        assertThat(ids.indexOf("custom-1")).isLessThan(ids.indexOf("exa"))
        assertThat(ids.indexOf("exa")).isLessThan(ids.indexOf(BuiltInSearch.DDG_HTML))
        assertThat(ids).containsNoneOf("mojeek", "searx")
        // Keyless engines that are still on, in their order.
        assertThat(ids.filter { it in BuiltInSearch.ids })
            .containsExactly("ddg_html", "ddg_lite", "bing", "wikipedia").inOrder()
    }

    @Test
    fun `a preset without a saved key is not in the chain`() {
        val ids = WebSearch.chain(WebSearchConfig()).map { it.id }
        assertThat(ids).containsNoneOf("tinyfish", "brave", "exa", "tavily")
        assertThat(ids).containsAtLeast("ddg_html", "bing", "wikipedia").inOrder()
    }

    @Test
    fun `views report what the settings screen needs`() {
        WebSearch.configure(
            WebSearchConfig(presetKeys = mapOf("brave" to "k"), disabledBuiltIns = listOf("wikipedia"))
        )
        val views = WebSearch.views()
        val brave = views.first { it.id == "brave" }
        assertThat(brave.hasKey).isTrue()
        assertThat(brave.enabled).isTrue()
        val tinyfish = views.first { it.id == "tinyfish" }
        assertThat(tinyfish.hasKey).isFalse()
        assertThat(tinyfish.enabled).isFalse()
        val wiki = views.first { it.id == "wikipedia" }
        assertThat(wiki.enabled).isFalse()
        val ddg = views.first { it.id == "ddg_html" }
        assertThat(ddg.enabled).isTrue()
        assertThat(ddg.needsKey).isFalse()
        WebSearch.configure(WebSearchConfig())
    }
}
