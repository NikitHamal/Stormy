package com.oro.app.data.ai.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebDocsTest {
    @Test
    fun `bundled docs load and answer common questions`() {
        assertThat(WebDocs.entries.size).isAtLeast(60)
        assertThat(WebDocs.lookup("sticky header").first().name).contains("sticky")
        assertThat(WebDocs.lookup("dark mode toggle").map { it.name }).contains("Dark mode toggle")
        assertThat(WebDocs.format(WebDocs.lookup("dialog"))).contains("showModal")
    }
}
