package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.context.ProjectInstructions
import org.junit.Test
import java.nio.file.Files

class PreviewDiagnosticsTest {
    @Test
    fun `repeats are counted, summary only shows new errors once`() {
        val p = "proj-test"
        PreviewDiagnostics.clear(p)
        repeat(3) { PreviewDiagnostics.record(p, PreviewDiagnostics.Level.ERROR, "Uncaught TypeError: x is null", "http://127.0.0.1/app.js?v=1", 12, now = 100L + it) }
        PreviewDiagnostics.record(p, PreviewDiagnostics.Level.WARNING, "deprecated", "a.js", 1, now = 110L)
        val summary = PreviewDiagnostics.takeContextSummary(p, now = 200L)
        assertThat(summary).contains("Uncaught TypeError: x is null (app.js:12) ×3")
        assertThat(summary).doesNotContain("deprecated")
        assertThat(PreviewDiagnostics.takeContextSummary(p, now = 300L)).isEmpty()
        assertThat(PreviewDiagnostics.report(p)).contains("warning: deprecated")
    }

    @Test
    fun `project instructions read ORO and AGENTS files in order`() {
        val dir = Files.createTempDirectory("oro").toFile()
        java.io.File(dir, "AGENTS.md").writeText("Use tabs.")
        java.io.File(dir, "ORO.md").writeText("Brand is Bean There.")
        val loaded = ProjectInstructions.load(dir)
        assertThat(loaded.files).containsExactly("ORO.md", "AGENTS.md").inOrder()
        assertThat(ProjectInstructions.section(loaded)).contains("Brand is Bean There.")
        assertThat(ProjectInstructions.section(ProjectInstructions.load(Files.createTempDirectory("e").toFile()))).isEmpty()
    }
}
