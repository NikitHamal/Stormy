package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreviewTelemetryTest {

    private val origin = "http://127.0.0.1:8080"

    @Test
    fun `parses an uncaught error report with position and stack`() {
        val issue = PreviewTelemetry.parse(
            """{"kind":"error","message":"Uncaught TypeError: x is undefined","source":"http://127.0.0.1:8080/js/app.js","line":12,"column":5,"stack":"TypeError: x\n    at go (app.js:12:5)"}"""
        )!!
        assertThat(issue.kind).isEqualTo("error")
        assertThat(issue.line).isEqualTo(12)
        assertThat(issue.column).isEqualTo(5)
        assertThat(issue.stack).contains("at go")
        assertThat(PreviewTelemetry.fileOf(issue, origin)).isEqualTo("js/app.js")
    }

    @Test
    fun `parses numbers sent as strings or floats and tolerates missing fields`() {
        val issue = PreviewTelemetry.parse("""{"message":"boom","line":"7","column":3.0}""")!!
        assertThat(issue.kind).isEqualTo("error")
        assertThat(issue.line).isEqualTo(7)
        assertThat(issue.column).isEqualTo(3)
        assertThat(issue.source).isEmpty()
    }

    @Test
    fun `rejects garbage and empty reports`() {
        assertThat(PreviewTelemetry.parse("not json")).isNull()
        assertThat(PreviewTelemetry.parse("[1,2]")).isNull()
        assertThat(PreviewTelemetry.parse("""{"kind":"error"}""")).isNull()
        assertThat(PreviewTelemetry.parse("""{"message":"   "}""")).isNull()
    }

    @Test
    fun `clamps oversized fields`() {
        val huge = "x".repeat(10_000)
        val issue = PreviewTelemetry.parse("""{"message":"$huge","stack":"$huge","line":-4}""")!!
        assertThat(issue.message.length).isAtMost(600)
        assertThat(issue.stack.length).isAtMost(1500)
        assertThat(issue.line).isEqualTo(0)
    }

    @Test
    fun `the console line and the bridge report of one error become one entry with the stack`() {
        val fromConsole = PreviewTelemetry.fromConsole("Uncaught TypeError: x is undefined", "http://127.0.0.1:8080/app.js", 12)
        val fromBridge = RuntimeIssue("error", "Uncaught TypeError: x is undefined", "http://127.0.0.1:8080/app.js", 12, 5, "TypeError: x\n at go")
        val merged = PreviewTelemetry.merge(PreviewTelemetry.merge(emptyList(), fromConsole), fromBridge)
        assertThat(merged).hasSize(1)
        assertThat(merged.single().column).isEqualTo(5)

        val reversed = PreviewTelemetry.merge(PreviewTelemetry.merge(emptyList(), fromBridge), fromConsole)
        assertThat(reversed).hasSize(1)
        assertThat(reversed.single().isRich).isTrue()
    }

    @Test
    fun `different errors stay separate and the list is bounded`() {
        var list = emptyList<RuntimeIssue>()
        for (i in 1..40) list = PreviewTelemetry.merge(list, RuntimeIssue("error", "failure number $i", "", i))
        assertThat(list).hasSize(25)
        assertThat(list.last().message).isEqualTo("failure number 40")
    }

    @Test
    fun `fix prompt names the file, position, message and stack`() {
        val issue = RuntimeIssue("error", "Uncaught ReferenceError: foo is not defined", "$origin/js/app.js", 3, 9, "ReferenceError: foo\n    at app.js:3:9")
        val prompt = PreviewTelemetry.fixPrompt(listOf(issue), "index.html", origin)
        assertThat(prompt).contains("Page: index.html")
        assertThat(prompt).contains("File: js/app.js (line 3, column 9)")
        assertThat(prompt).contains("Error: Uncaught ReferenceError: foo is not defined")
        assertThat(prompt).contains("at app.js:3:9")
        assertThat(prompt).contains("get_preview_errors")
    }

    @Test
    fun `copy context has the facts but no instruction`() {
        val issue = RuntimeIssue("error", "boom", "$origin/a.js", 1)
        val text = PreviewTelemetry.copyContext(listOf(issue), null, origin)
        assertThat(text).contains("File: a.js (line 1)")
        assertThat(text).doesNotContain("get_preview_errors")
    }

    @Test
    fun `headline counts the rest`() {
        val a = RuntimeIssue("error", "Uncaught Error: one", "", 0)
        val b = RuntimeIssue("error", "Uncaught Error: two\nmore", "", 0)
        assertThat(PreviewTelemetry.headline(listOf(a, b))).isEqualTo("Error: two (+1 more)")
        assertThat(PreviewTelemetry.headline(emptyList())).isEmpty()
    }

    @Test
    fun `the script installs the bridge hooks`() {
        assertThat(PreviewTelemetry.SCRIPT).contains("unhandledrejection")
        assertThat(PreviewTelemetry.SCRIPT).contains(PreviewTelemetry.BRIDGE)
    }
}
