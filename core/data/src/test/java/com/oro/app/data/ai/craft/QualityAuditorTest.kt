package com.oro.app.data.ai.craft

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QualityAuditorTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun write(path: String, text: String): File =
        File(tmp.root, path).apply { parentFile?.mkdirs(); writeText(text.trimIndent()) }

    private val goodHtml = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Shop</title>
          <link rel="stylesheet" href="css/style.css">
        </head>
        <body>
          <button id="buy" class="btn">Buy</button>
          <img src="https://picsum.photos/seed/a/400/300" alt="A product">
          <script src="js/app.js"></script>
        </body>
        </html>
    """

    @Test
    fun `a clean project has no blocking issues`() {
        write("index.html", goodHtml)
        write("css/style.css", ".btn{transition:transform .2s}.btn:hover{transform:scale(1.02)}.btn:focus-visible{outline:2px solid}@media (prefers-reduced-motion: reduce){*{transition:none}}")
        write("js/app.js", "document.getElementById('buy').addEventListener('click', () => { console.log(`ok ${'$'}{1 + 1}`); });")
        val report = QualityAuditor(tmp.root).audit()
        assertThat(report.blocking).isEmpty()
    }

    @Test
    fun `missing linked file is an error with a suggestion`() {
        write("index.html", goodHtml.replace("css/style.css", "css/styles.css"))
        write("css/style.css", "body{color:red}")
        write("js/app.js", "")
        val issues = QualityAuditor(tmp.root).audit().issues
        val issue = issues.single { it.message.contains("styles.css") }
        assertThat(issue.severity).isEqualTo(QualityAuditor.Severity.ERROR)
        assertThat(issue.message).contains("css/style.css")
    }

    @Test
    fun `script looking up a missing id is caught with the near miss`() {
        write("index.html", goodHtml)
        write("css/style.css", "")
        write("js/app.js", "const b = document.querySelector('#buyBtn'); b.onclick = () => {};")
        val issue = QualityAuditor(tmp.root).audit().issues.single { it.message.contains("#buyBtn") }
        assertThat(issue.severity).isEqualTo(QualityAuditor.Severity.ERROR)
    }

    @Test
    fun `ids created from script are not reported`() {
        write("index.html", goodHtml)
        write("css/style.css", "")
        write("js/app.js", "const el = document.createElement('div'); el.id = 'toast'; document.body.append(el); document.getElementById('toast');")
        assertThat(QualityAuditor(tmp.root).audit().issues.none { it.message.contains("#toast") }).isTrue()
    }

    @Test
    fun `truncated html and unbalanced js are errors`() {
        write("index.html", goodHtml.substringBefore("</body>"))
        write("css/style.css", "")
        write("js/app.js", "function a(){ if (x) { return 1; }\n")
        val issues = QualityAuditor(tmp.root).audit().issues
        assertThat(issues.any { it.path == "index.html" && it.message.contains("cut off") }).isTrue()
        assertThat(issues.any { it.path == "js/app.js" && it.message.contains("unclosed") }).isTrue()
    }

    @Test
    fun `js balance understands strings, templates, regex and comments`() {
        val js = """
            const re = /[({]+/g; // a regex with brackets
            const s = "}" + '{' + `${'$'}{ {a:1}.a } ) ]`;
            /* ( unclosed in comment */
            function f(a) { return a.replace(/\)/g, ''); }
            const t = x / 2; const u = (y) / 3;
        """.trimIndent()
        assertThat(JsBalance.check(js)).isNull()
    }

    @Test
    fun `focus limits the report to touched files`() {
        write("index.html", goodHtml.replace("<title>Shop</title>", ""))
        write("about.html", goodHtml.replace("<title>Shop</title>", "").replace("js/app.js", "js/missing.js"))
        write("css/style.css", "")
        write("js/app.js", "")
        val report = QualityAuditor(tmp.root).audit(setOf("about.html"))
        assertThat(report.issues.map { it.path }.toSet()).containsExactly("about.html")
    }

    @Test
    fun `missing viewport is an error and lorem ipsum a warning`() {
        write("index.html", goodHtml.replace(Regex("<meta name=\"viewport\"[^>]*>"), "").replace("Buy</button>", "Buy</button><p>Lorem ipsum dolor</p>"))
        write("css/style.css", "")
        write("js/app.js", "")
        val issues = QualityAuditor(tmp.root).audit().issues
        assertThat(issues.single { it.message.contains("viewport") }.severity).isEqualTo(QualityAuditor.Severity.ERROR)
        assertThat(issues.single { it.message.contains("lorem") }.severity).isEqualTo(QualityAuditor.Severity.WARNING)
    }

    @Test
    fun `quality gate returns null when clean and instructions when not`() {
        write("index.html", goodHtml)
        write("css/style.css", "")
        write("js/app.js", "document.getElementById('nope')")
        assertThat(QualityGate.review(tmp.root, emptySet())).isNull()
        val msg = QualityGate.review(tmp.root, setOf("js/app.js"))
        assertThat(msg).contains("#nope")
        assertThat(msg).contains("check_syntax")
        assertThat(QualityGate.review(tmp.root, setOf("css/style.css"))).isNull()
    }
}
