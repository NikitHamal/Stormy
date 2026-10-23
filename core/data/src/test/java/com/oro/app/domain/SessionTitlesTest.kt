package com.oro.app.domain

import com.google.common.truth.Truth.assertThat
import com.oro.app.domain.model.SessionTitles
import org.junit.Test

class SessionTitlesTest {

    @Test
    fun `a short message is its own title, capitalised and without trailing punctuation`() {
        assertThat(SessionTitles.fromFirstMessage("add a dark mode toggle?"))
            .isEqualTo("Add a dark mode toggle")
    }

    @Test
    fun `leading pleasantries are dropped`() {
        assertThat(SessionTitles.fromFirstMessage("Hey, can you fix the navbar on mobile"))
            .isEqualTo("Fix the navbar on mobile")
    }

    @Test
    fun `a message that is only a greeting keeps it`() {
        assertThat(SessionTitles.fromFirstMessage("hi")).isEqualTo("Hi")
    }

    @Test
    fun `only the first non-empty line is used`() {
        val message = "\n\nWhy does this crash\n```\njava.lang.NullPointerException\n```"
        assertThat(SessionTitles.fromFirstMessage(message)).isEqualTo("Why does this crash")
    }

    @Test
    fun `file mentions are reduced to the file name and markdown is stripped`() {
        assertThat(SessionTitles.fromFirstMessage("Refactor @src/components/Header.tsx to use **hooks**"))
            .isEqualTo("Refactor Header.tsx to use hooks")
    }

    @Test
    fun `snake_case survives`() {
        assertThat(SessionTitles.fromFirstMessage("rename user_id in models.py"))
            .isEqualTo("Rename user_id in models.py")
    }

    @Test
    fun `long messages are cut at a word boundary with an ellipsis`() {
        val title = SessionTitles.fromFirstMessage(
            "Build a landing page for a coffee shop with a hero section, a menu and a contact form"
        )
        assertThat(title).isEqualTo("Build a landing page for a coffee shop with a…")
        assertThat(title.length).isAtMost(SessionTitles.MAX_LENGTH)
    }

    @Test
    fun `a single enormous token is cut mid-word rather than emptied`() {
        val title = SessionTitles.fromFirstMessage("x".repeat(200))
        assertThat(title.length).isEqualTo(SessionTitles.MAX_LENGTH)
        assertThat(title).endsWith("…")
    }

    @Test
    fun `blank input falls back to the default title`() {
        assertThat(SessionTitles.fromFirstMessage(null)).isEqualTo(SessionTitles.DEFAULT_TITLE)
        assertThat(SessionTitles.fromFirstMessage("   \n ")).isEqualTo(SessionTitles.DEFAULT_TITLE)
        assertThat(SessionTitles.fromFirstMessage("???")).isEqualTo(SessionTitles.DEFAULT_TITLE)
    }

    @Test
    fun `user titles are one line and never blank`() {
        assertThat(SessionTitles.sanitizeUserTitle("  Auth\n  refactor ")).isEqualTo("Auth refactor")
        assertThat(SessionTitles.sanitizeUserTitle("   ")).isNull()
    }
}
