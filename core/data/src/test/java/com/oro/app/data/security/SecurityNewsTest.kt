package com.oro.app.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecurityNewsTest {

    private val rss = """
        <?xml version="1.0" encoding="utf-8"?>
        <rss version="2.0"><channel>
          <item>
            <title><![CDATA[New ransomware campaign hits edge devices]]></title>
            <link>https://example.org/ransomware</link>
            <pubDate>Fri, 30 Oct 2026 10:00:00 GMT</pubDate>
            <description><![CDATA[<p>Researchers say &amp; confirm a new campaign.</p>]]></description>
          </item>
          <item>
            <title>Patch Tuesday fixes 40 flaws</title>
            <link>https://example.org/patch</link>
            <pubDate>Thu, 29 Oct 2026 08:30:00 GMT</pubDate>
            <description>Edge &amp; Windows updates.</description>
          </item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun `rss items become clean news rows`() {
        val items = SecurityNews.parseRss(rss, "Test Desk")
        assertThat(items).hasSize(2)
        val first = items.first()
        assertThat(first.source).isEqualTo("Test Desk")
        assertThat(first.title).isEqualTo("New ransomware campaign hits edge devices")
        assertThat(first.link).isEqualTo("https://example.org/ransomware")
        assertThat(first.summary).contains("Researchers say & confirm")
        assertThat(first.summary).doesNotContain("<p>")
        assertThat(first.epochMillis).isGreaterThan(0L)
        assertThat(items[1].title).isEqualTo("Patch Tuesday fixes 40 flaws")
    }

    @Test
    fun `a feed without items is empty, not an exception`() {
        assertThat(SecurityNews.parseRss("<rss><channel/></rss>", "Empty")).isEmpty()
        assertThat(SecurityNews.parseRss("not xml", "Broken")).isEmpty()
    }
}
