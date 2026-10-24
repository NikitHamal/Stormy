package com.oro.app.ui.components.inspector

import com.google.common.truth.Truth.assertThat
import com.oro.app.ui.components.inspector.SourceTextEdit.Outcome
import com.oro.app.ui.components.inspector.SourceTextEdit.Target
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SourceTextEditTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun edited(outcome: Outcome): String {
        assertThat(outcome).isInstanceOf(Outcome.Edited::class.java)
        return (outcome as Outcome.Edited).html
    }

    private fun failed(outcome: Outcome): String {
        assertThat(outcome).isInstanceOf(Outcome.Failed::class.java)
        return (outcome as Outcome.Failed).reason
    }

    private val cards = """
        <!doctype html>
        <html><head><title>Learn more</title></head>
        <body>
          <main>
            <div class="card"><h3>Fast</h3><a href="/a">Learn more</a></div>
            <div class="card"><h3>Calm</h3><a href="/b">Learn more</a></div>
            <div class="card"><h3>Kind</h3><a href="/c">Learn more</a></div>
          </main>
        </body></html>
    """.trimIndent()

    @Test
    fun `edits only the occurrence that was picked`() {
        val html = edited(SourceTextEdit.replace(cards, Target("Learn more", "main>div>a", 1, 3, 3), "Read the story", "index.html"))
        assertThat(html).contains("""<a href="/a">Learn more</a>""")
        assertThat(html).contains("""<a href="/b">Read the story</a>""")
        assertThat(html).contains("""<a href="/c">Learn more</a>""")
        // The <title> in <head> was never a candidate.
        assertThat(html).contains("<title>Learn more</title>")
    }

    @Test
    fun `keeps the whitespace around the text and every other byte`() {
        val source = "<body>\n  <h1 class=\"hero\">\n    Invoices that\n    <em>pay themselves.</em>\n  </h1>\n</body>"
        val html = edited(SourceTextEdit.replace(source, Target("Invoices that", "h1", 0, 1, 1), "Bills that", "index.html"))
        assertThat(html).isEqualTo("<body>\n  <h1 class=\"hero\">\n    Bills that\n    <em>pay themselves.</em>\n  </h1>\n</body>")
    }

    @Test
    fun `matches decoded entities and escapes the new text`() {
        val source = "<body><p>Fish &amp; Chips &mdash; daily</p></body>"
        val html = edited(SourceTextEdit.replace(source, Target("Fish & Chips — daily", "p", 0, 1, 1), "Salt & <Vinegar>", "menu.html"))
        assertThat(html).isEqualTo("<body><p>Salt &amp; &lt;Vinegar&gt;</p></body>")
    }

    @Test
    fun `text a script writes fails clearly`() {
        val source = "<body><table id=\"orders\"></table><script>render('Paid')</script></body>"
        val reason = failed(SourceTextEdit.replace(source, Target("Paid", "table>tr>td", 0, 1, 1), "Settled", "index.html"))
        assertThat(reason).contains("index.html")
        assertThat(reason).contains("Orobi")
    }

    @Test
    fun `repeated text fails when the page and the source disagree`() {
        // The page has a third "Learn more" a script added, with the same path.
        val reason = failed(SourceTextEdit.replace(cards, Target("Learn more", "main>div>a", 3, 4, 4), "Read", "index.html"))
        assertThat(reason).contains("more than one place")
    }

    @Test
    fun `text unique on both sides is edited even when the paths differ`() {
        val source = "<body><section><p>Only once</p></section></body>"
        val html = edited(SourceTextEdit.replace(source, Target("Only once", "div>p", 0, 1, 1), "Just once", "index.html"))
        assertThat(html).contains("<p>Just once</p>")
    }

    @Test
    fun `comments, scripts, styles and attributes are not text`() {
        val source = """
            <body>
              <!-- Save -->
              <style>.x::after { content: "Save" }</style>
              <script>var label = "Save";</script>
              <button title="Save" data-x='a > b'><svg viewBox="0 0 24 24"><path d="M0 0"/></svg> Save</button>
            </body>
        """.trimIndent()
        val segments = SourceTextEdit.segments(source).filter { SourceTextEdit.normalize(it.text) == "Save" }
        assertThat(segments.map { it.path }).containsExactly("button")
        val html = edited(SourceTextEdit.replace(source, Target("Save", "button", 0, 1, 1), "Keep", "index.html"))
        assertThat(html).contains("</svg> Keep</button>")
        assertThat(html).contains("<!-- Save -->")
        assertThat(html).contains("var label = \"Save\";")
    }

    @Test
    fun `paths follow the end tags browsers imply`() {
        val source = """
            <body>
              <p>Intro<div>Card</div>
              <ul><li>One<li>Two</ul>
              <table><tr><td>Cell</td></tr></table>
              <h2>Title<h3>Sub</h3>
            </body>
        """.trimIndent()
        val paths = SourceTextEdit.segments(source).associate { SourceTextEdit.normalize(it.text) to it.path }
        assertThat(paths["Intro"]).isEqualTo("p")
        assertThat(paths["Card"]).isEqualTo("div")
        assertThat(paths["Two"]).isEqualTo("ul>li")
        assertThat(paths["Cell"]).isEqualTo("table>tr>td")
        assertThat(paths["Sub"]).isEqualTo("h3")
    }

    @Test
    fun `quotes inside unquoted attribute values don't swallow the page`() {
        val source = "<body><p title=don't>Hello</p><p>World</p></body>"
        assertThat(SourceTextEdit.segments(source).map { SourceTextEdit.normalize(it.text) }).containsExactly("Hello", "World").inOrder()
    }

    @Test
    fun `a page without body tags is all body`() {
        val source = "<h1>Hi</h1>\n<p>There</p>"
        assertThat(SourceTextEdit.segments(source).map { it.path }).containsExactly("h1", "p").inOrder()
    }

    @Test
    fun `entities decode, unknown ones stay as written`() {
        assertThat(SourceTextEdit.decodeEntities("&copy; 2026 &#8212; &#x2713; &eacute; &bogus; &")).isEqualTo("© 2026 — ✓ é &bogus; &")
    }

    @Test
    fun `page paths map to the file the preview server serves`() {
        val root = tmp.newFolder("site")
        File(root, "index.html").writeText("<p>home</p>")
        File(root, "post.html").writeText("<p>post</p>")
        File(root, "blog").mkdirs()
        File(root, "blog/index.html").writeText("<p>blog</p>")
        File(root, "css").mkdirs()
        File(root, "css/site.css").writeText("p{}")

        fun page(path: String) = SourceTextEdit.pageFile(root, path)?.relativeTo(root.canonicalFile)?.path
        assertThat(page("/")).isEqualTo("index.html")
        assertThat(page("/post.html")).isEqualTo("post.html")
        assertThat(page("/blog/")).isEqualTo("blog/index.html")
        assertThat(page("/blog")).isEqualTo("blog/index.html")
        assertThat(page("/pricing")).isEqualTo("index.html")
        assertThat(page("/css/site.css")).isNull()
        assertThat(page("/../../etc/passwd")).isNull()
        assertThat(page("/missing.html")).isNull()
        assertThat(page(File(root, "post.html").absolutePath)).isEqualTo("post.html")
    }
}
