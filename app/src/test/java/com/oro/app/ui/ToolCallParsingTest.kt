package com.oro.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The tool-call header pattern, which silently matched nothing.
 *
 * The original used `(✅|❌|⏳)\b` -- a word boundary immediately after an
 * emoji. `\b` requires a word character on one side, an emoji is not one, and
 * the following character is a space, so the assertion could never hold. Every
 * tool call therefore fell through to the markdown renderer and the transcript
 * read as literal "🔧 **List Files** ✅ Directory is empty" text.
 *
 * Mirrors the production pattern; if that changes, these should change with it.
 */
class ToolCallParsingTest {

    private val pattern = Regex(
        """(?m)^\s*🔧\s*(?:\*\*)?([^*✅❌⏳\r\n]+?)(?:\*\*)?\s*(?:\r?\n|[ \t]+)(✅|❌|⏳)[^\r\n]*\r?\n?"""
    )

    @Test
    fun `the exact shape the view model emits is matched`() {
        val emitted = "\n\n🔧 **List Files**\n✅ Directory is empty\n"
        val match = pattern.find(emitted)
        assertThat(match).isNotNull()
        assertThat(match!!.groupValues[1].trim()).isEqualTo("List Files")
        assertThat(match.groupValues[2]).isEqualTo("✅")
    }

    @Test
    fun `failure and running states are matched too`() {
        assertThat(pattern.find("🔧 **Write File**\n❌ Permission denied")).isNotNull()
        assertThat(pattern.find("🔧 **Read File**\n⏳ Reading")).isNotNull()
    }

    @Test
    fun `the plain non-bold form is matched`() {
        val match = pattern.find("🔧 Read File\n✅ done")
        assertThat(match).isNotNull()
        assertThat(match!!.groupValues[1].trim()).isEqualTo("Read File")
    }

    @Test
    fun `header and status on one line is matched`() {
        assertThat(pattern.find("🔧 **Run Command** ✅ exit 0")).isNotNull()
    }

    @Test
    fun `several calls in one message are all matched`() {
        val transcript = buildString {
            append("Here is what I did.\n\n")
            append("🔧 **List Files**\n✅ 3 files\n\n")
            append("🔧 **Read File**\n✅ ok\n\n")
            append("🔧 **Write File**\n✅ written\n\n")
            append("All done.")
        }
        assertThat(pattern.findAll(transcript).count()).isEqualTo(3)
    }

    @Test
    fun `ordinary prose is not mistaken for a tool call`() {
        val prose = "I will read the file and then write it back.\nDone."
        assertThat(pattern.find(prose)).isNull()
    }

    @Test
    fun `a wrench mentioned mid-sentence is not a header`() {
        // The pattern is anchored to the start of a line, so prose that
        // happens to contain the glyph does not become a card.
        assertThat(pattern.find("the 🔧 icon means a tool ✅ ran")).isNull()
    }
}
