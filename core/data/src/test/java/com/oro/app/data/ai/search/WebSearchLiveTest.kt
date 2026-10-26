package com.oro.app.data.ai.search

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live web_search against the real endpoints. Opt-in only (ORO_LIVE_EVAL=1),
 * never in CI: endpoints rate-limit, redesign and occasionally captcha, and
 * a datacenter IP sees a harsher web than a phone does. Useful exactly
 * because of that: it tells us which providers of the chain survive today.
 */
class WebSearchLiveTest {

    @Test
    fun `the chain brings back real results`() {
        assumeTrue("set ORO_LIVE_EVAL=1 to run", System.getenv("ORO_LIVE_EVAL") == "1")
        val hits = runBlocking { WebSearch.search("kotlin coroutines guide", 5) }
        println("LIVE web_search hits: " + hits.size)
        hits.forEach { println(" - ${it.title} | ${it.url}") }
        assertThat(hits).isNotEmpty()
        assertThat(hits.first().url).startsWith("http")
    }

    @Test
    fun `a second query is served too (no single-query luck)`() {
        assumeTrue("set ORO_LIVE_EVAL=1 to run", System.getenv("ORO_LIVE_EVAL") == "1")
        val hits = runBlocking { WebSearch.search("best css framework 2026", 5) }
        println("LIVE web_search hits (query 2): " + hits.size)
        assertThat(hits).isNotEmpty()
    }
}
