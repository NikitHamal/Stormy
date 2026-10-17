package com.oro.app.data.ai.tools

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspaceToolsTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var root: File
    private lateinit var tools: WorkspaceTools

    private fun args(vararg pairs: Pair<String, Any>) = JsonObject(pairs.associate { (k, v) ->
        k to when (v) {
            is String -> JsonPrimitive(v)
            is Int -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            is JsonArray -> v
            else -> error("unsupported")
        }
    })

    @Before
    fun setUp() {
        root = tmp.newFolder("site")
        File(root, "index.html").writeText((1..30).joinToString("\n") { "line $it" } + "\n")
        File(root, "css").mkdirs()
        File(root, "css/style.css").writeText("body { color: red; }\n")
        File(root, "js").mkdirs()
        File(root, "js/app.js").writeText("const x = 1;\nconsole.log(x);\n")
        File(root, ".oro").mkdirs()
        File(root, ".oro/secret.log").writeText("hidden")
        tools = WorkspaceTools(root)
    }

    @Test
    fun `read_file returns numbered lines and honours offset and limit`() {
        val out = tools.readFile(args("path" to "index.html", "offset" to 10, "limit" to 3)).result
        assertThat(out.success).isTrue()
        assertThat(out.output).contains("lines 10-12 of 30")
        assertThat(out.output).contains("10|line 10")
        assertThat(out.output).contains("12|line 12")
        assertThat(out.output).doesNotContain("line 13")
        assertThat(out.output).contains("offset=13")
    }

    @Test
    fun `read_file on a missing file suggests the right path`() {
        val out = tools.readFile(args("path" to "style.css")).result
        assertThat(out.success).isFalse()
        assertThat(out.error).contains("css/style.css")
    }

    @Test
    fun `paths outside the project and reserved folders are refused`() {
        assertThat(tools.readFile(args("path" to "../../etc/passwd")).result.success).isFalse()
        val reserved = tools.readFile(args("path" to ".oro/secret.log")).result
        assertThat(reserved.success).isFalse()
        assertThat(reserved.error).contains("reserved")
    }

    @Test
    fun `write then edit records changes for undo`() {
        val write = tools.writeFile(args("path" to "pages/about.html", "content" to "<h1>About</h1>\n"))
        assertThat(write.result.success).isTrue()
        assertThat(write.changes.single().type).isEqualTo(FileChangeType.CREATED)
        assertThat(File(root, "pages/about.html").readText()).isEqualTo("<h1>About</h1>\n")

        val edit = tools.editFile(args("path" to "pages/about.html", "old_string" to "About", "new_string" to "About us"))
        assertThat(edit.result.success).isTrue()
        assertThat(edit.result.output).contains("```diff")
        assertThat(edit.changes.single().oldContent).isEqualTo("<h1>About</h1>\n")
        assertThat(File(root, "pages/about.html").readText()).isEqualTo("<h1>About us</h1>\n")
    }

    @Test
    fun `failed edit leaves the file untouched and explains why`() {
        val r = tools.editFile(args("path" to "js/app.js", "old_string" to "let y", "new_string" to "let z")).result
        assertThat(r.success).isFalse()
        assertThat(r.error).contains("not found")
        assertThat(File(root, "js/app.js").readText()).isEqualTo("const x = 1;\nconsole.log(x);\n")
    }

    @Test
    fun `multi_edit applies in order`() {
        val edits = JsonArray(listOf(
            args("old_string" to "const x = 1;", "new_string" to "const x = 2;"),
            args("old_string" to "console.log(x);", "new_string" to "console.info(x);")
        ))
        val r = tools.multiEdit(args("path" to "js/app.js", "edits" to edits)).result
        assertThat(r.success).isTrue()
        assertThat(File(root, "js/app.js").readText()).isEqualTo("const x = 2;\nconsole.info(x);\n")
    }

    @Test
    fun `list_files hides reserved folders`() {
        val out = tools.listFiles(args()).result.output
        assertThat(out).contains("css/")
        assertThat(out).contains("  style.css")
        assertThat(out).doesNotContain(".oro")
    }

    @Test
    fun `glob matches by name at any depth and by path`() {
        assertThat(tools.glob(args("pattern" to "*.css")).result.output).isEqualTo("css/style.css")
        assertThat(tools.glob(args("pattern" to "**/*.{js,css}")).result.output.lines())
            .containsExactly("css/style.css", "js/app.js")
        assertThat(tools.glob(args("pattern" to "*.secret")).result.output).startsWith("No files")
    }

    @Test
    fun `grep finds regex matches with include filter and survives bad regex`() {
        val r = tools.grep(args("pattern" to "console\\.\\w+", "include" to "*.js")).result
        assertThat(r.output).isEqualTo("js/app.js:2: console.log(x);")
        val literal = tools.grep(args("pattern" to "color: red;(")).result
        assertThat(literal.output).contains("plain text")
        assertThat(tools.grep(args("pattern" to "hidden")).result.output).startsWith("No matches")
    }

    @Test
    fun `move and delete`() {
        val moved = tools.moveFile(args("source" to "js/app.js", "destination" to "scripts/main.js"))
        assertThat(moved.result.success).isTrue()
        assertThat(File(root, "scripts/main.js").exists()).isTrue()
        assertThat(tools.moveFile(args("source" to "css/style.css", "destination" to "index.html")).result.error)
            .contains("already exists")
        val deleted = tools.deleteFile(args("path" to "css"))
        assertThat(deleted.result.success).isTrue()
        assertThat(deleted.changes.single().oldContent).isEqualTo("body { color: red; }\n")
        assertThat(tools.deleteFile(args("path" to ".")).result.success).isFalse()
    }

    @Test
    fun `html to text keeps content and drops scripts`() {
        val text = HtmlText.toText("<html><head><title>x</title></head><body><script>bad()</script><h1>Title</h1><p>A &amp; B</p><ul><li>one</li></ul></body></html>")
        assertThat(text).contains("# Title")
        assertThat(text).contains("A & B")
        assertThat(text).contains("- one")
        assertThat(text).doesNotContain("bad()")
    }
}
