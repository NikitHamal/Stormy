package com.oro.app.data.ai.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextEditsTest {

    private val html = "<body>\n  <h1>Hi</h1>\n  <p>One</p>\n  <p>One</p>\n</body>\n"

    private fun applied(r: TextEdits.Result) = (r as TextEdits.Result.Applied)
    private fun failed(r: TextEdits.Result) = (r as TextEdits.Result.Failed).message

    @Test
    fun `unique exact match is replaced`() {
        val r = applied(TextEdits.apply(html, "<h1>Hi</h1>", "<h1>Hello</h1>"))
        assertThat(r.content).contains("<h1>Hello</h1>")
        assertThat(r.replacements).isEqualTo(1)
    }

    @Test
    fun `ambiguous match is refused with line numbers`() {
        val msg = failed(TextEdits.apply(html, "<p>One</p>", "<p>Two</p>", path = "index.html"))
        assertThat(msg).contains("matches 2 places")
        assertThat(msg).contains("lines 3, 4")
        assertThat(msg).contains("replace_all")
    }

    @Test
    fun `replace_all changes every occurrence`() {
        val r = applied(TextEdits.apply(html, "<p>One</p>", "<p>Two</p>", replaceAll = true))
        assertThat(r.content).doesNotContain("One")
        assertThat(r.replacements).isEqualTo(2)
    }

    @Test
    fun `missing text fails with a pointer to a similar line`() {
        val msg = failed(TextEdits.apply(html, "<h1>Hi there</h1>", "x", path = "index.html"))
        assertThat(msg).contains("was not found in index.html")
    }

    @Test
    fun `indentation drift is tolerated when the match is unique`() {
        val r = applied(TextEdits.apply(html, "<h1>Hi</h1>\n<p>One</p>", "  <h1>Hey</h1>\n  <p>Zero</p>"))
        assertThat(r.content).isEqualTo("<body>\n  <h1>Hey</h1>\n  <p>Zero</p>\n  <p>One</p>\n</body>\n")
    }

    @Test
    fun `line number prefixes copied from read_file are stripped`() {
        val r = applied(TextEdits.apply(html, "2|  <h1>Hi</h1>", "2|  <h1>Yo</h1>"))
        assertThat(r.content).contains("  <h1>Yo</h1>")
        assertThat(r.content).doesNotContain("2|")
    }

    @Test
    fun `crlf files accept lf edits`() {
        val crlf = "a\r\nb\r\nc\r\n"
        val r = applied(TextEdits.apply(crlf, "a\nb", "x\ny"))
        assertThat(r.content).isEqualTo("x\r\ny\r\nc\r\n")
    }

    @Test
    fun `identical and empty old strings are rejected`() {
        assertThat(failed(TextEdits.apply(html, "<p>", "<p>"))).contains("identical")
        assertThat(failed(TextEdits.apply(html, "", "x"))).contains("empty")
    }

    @Test
    fun `multi edit is all or nothing`() {
        val r = TextEdits.applyAll(
            html,
            listOf(Triple("<h1>Hi</h1>", "<h1>A</h1>", false), Triple("missing", "y", false)),
            "index.html"
        )
        assertThat(failed(r)).startsWith("Edit 2 of 2 failed, so no edits were applied")
        val ok = applied(TextEdits.applyAll(html, listOf(Triple("Hi", "A", false), Triple("A", "B", false)), "x"))
        assertThat(ok.content).contains("<h1>B</h1>")
    }
}

class ToolAliasesTest {
    @Test
    fun `namespaced and foreign names map to canonical tools`() {
        assertThat(ToolAliases.canonicalName("repo_browser.print_tree")).isEqualTo("list_files")
        assertThat(ToolAliases.canonicalName("functions.read_file")).isEqualTo("read_file")
        assertThat(ToolAliases.canonicalName("str_replace")).isEqualTo("edit_file")
        assertThat(ToolAliases.canonicalName("todowrite")).isEqualTo("update_plan")
    }

    @Test
    fun `legacy argument names are renamed`() {
        val (name, args) = ToolAliases.normalise(
            "patch_file",
            kotlinx.serialization.json.buildJsonObject {
                put("path", kotlinx.serialization.json.JsonPrimitive("a.css"))
                put("old_content", kotlinx.serialization.json.JsonPrimitive("x"))
                put("new_content", kotlinx.serialization.json.JsonPrimitive("y"))
            }
        )
        assertThat(name).isEqualTo("edit_file")
        assertThat(args.keys).containsExactly("path", "old_string", "new_string")
    }

    @Test
    fun `read_file line_end becomes a limit`() {
        val (_, args) = ToolAliases.normalise(
            "read_file",
            kotlinx.serialization.json.buildJsonObject {
                put("path", kotlinx.serialization.json.JsonPrimitive("a"))
                put("line_start", kotlinx.serialization.json.JsonPrimitive(10))
                put("line_end", kotlinx.serialization.json.JsonPrimitive(19))
            }
        )
        assertThat(args["offset"].toString()).isEqualTo("10")
        assertThat(args["limit"].toString()).isEqualTo("10")
        assertThat(args.containsKey("line_end")).isFalse()
    }
}

class TextEditsFuzzyTest {
    @org.junit.Test
    fun `whitespace drift inside a line still applies uniquely`() {
        val content = "<div class=\"card\"   id=\"x\">\n  <p>Hello</p>\n</div>\n"
        val r = TextEdits.apply(content, "<div class=\"card\" id=\"x\">", "<section id=\"x\">")
        com.google.common.truth.Truth.assertThat(r).isInstanceOf(TextEdits.Result.Applied::class.java)
        com.google.common.truth.Truth.assertThat((r as TextEdits.Result.Applied).content).startsWith("<section id=\"x\">\n  <p>")
    }

    @org.junit.Test
    fun `block anchors tolerate a misquoted middle line`() {
        val content = "function greet(name) {\n  const msg = 'Hello, ' + name;\n  console.log(msg);\n  return msg;\n}\nother();\n"
        val old = "function greet(name) {\n  const msg = 'Hello ' + name;\n  console.log(msg);\n  return msg;\n}"
        val r = TextEdits.apply(content, old, "function greet(n) { return n; }")
        com.google.common.truth.Truth.assertThat(r).isInstanceOf(TextEdits.Result.Applied::class.java)
        com.google.common.truth.Truth.assertThat((r as TextEdits.Result.Applied).content).isEqualTo("function greet(n) { return n; }\nother();\n")
    }

    @org.junit.Test
    fun `ambiguous fuzzy matches still fail`() {
        val content = "a {\n  color: red;\n}\nb {\n  color: red;\n}\n"
        val r = TextEdits.apply(content, "color:  red;", "color: blue;")
        com.google.common.truth.Truth.assertThat(r).isInstanceOf(TextEdits.Result.Failed::class.java)
    }

    @org.junit.Test
    fun `very different blocks are not matched`() {
        val content = "if (a) {\n  doOne();\n  doTwo();\n}\n"
        val r = TextEdits.apply(content, "if (a) {\n  somethingElseEntirely(42, 'x');\n}", "x")
        com.google.common.truth.Truth.assertThat(r).isInstanceOf(TextEdits.Result.Failed::class.java)
    }
}
