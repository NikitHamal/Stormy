package com.oro.app.data.ai.context

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectSnapshotTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun project(vararg files: Pair<String, String>): File {
        val root = tmp.newFolder()
        files.forEach { (path, text) -> File(root, path).apply { parentFile?.mkdirs(); writeText(text) } }
        return root
    }

    @Test
    fun `a small project is inlined whole, main page first`() {
        val root = project(
            "styles.css" to "body { color: red; }",
            "index.html" to "<h1>Hi</h1>",
            "js/app.js" to "console.log(1)",
            ".oro/usage.json" to "{}",
            "node_modules/x/index.js" to "nope",
        )
        val text = ProjectSnapshot.inline(root, 10_000)
        assertThat(text).startsWith("\n\n# Project files\n")
        assertThat(text).contains("(3 files)")
        assertThat(text.indexOf("## index.html")).isLessThan(text.indexOf("## js/app.js"))
        assertThat(text).contains("```html\n<h1>Hi</h1>\n```")
        assertThat(text).doesNotContain("usage.json")
        assertThat(text).doesNotContain("nope")
    }

    @Test
    fun `nothing is inlined when off, empty, or over budget`() {
        val root = project("index.html" to "x".repeat(5_000))
        assertThat(ProjectSnapshot.inline(root, 0)).isEmpty()
        assertThat(ProjectSnapshot.inline(root, 4_000)).isEmpty()
        assertThat(ProjectSnapshot.inline(tmp.newFolder(), 10_000)).isEmpty()
    }

    @Test
    fun `a file holding backtick fences gets a longer fence`() {
        val root = project("README.md" to "Use:\n```js\nx()\n```\n")
        val text = ProjectSnapshot.inline(root, 10_000)
        assertThat(text).contains("````markdown\n")
        assertThat(text.trimEnd()).endsWith("````")
    }

    @Test
    fun `a large db json is named, not shown, and does not block the snapshot`() {
        val root = project("index.html" to "<p>app</p>", "db.json" to "{\"items\":[" + "1,".repeat(9_000) + "1]}")
        val text = ProjectSnapshot.inline(root, 50_000)
        assertThat(text).contains("Not shown (data): db.json.")
        assertThat(text).doesNotContain("\"items\"")
        assertThat(text).contains("## index.html")
    }
}
