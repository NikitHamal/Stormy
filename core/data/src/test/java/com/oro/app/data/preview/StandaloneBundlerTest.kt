package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StandaloneBundlerTest {

    private fun bundle(page: String, files: Map<String, String>, bytes: Map<String, ByteArray> = emptyMap()) =
        StandaloneBundler.bundle(page, { files[it] }, { bytes[it] ?: files[it]?.toByteArray() })

    @Test
    fun `inlines stylesheets and scripts written three ways`() {
        val files = mapOf(
            "index.html" to """<html><head>
                <link rel="stylesheet" href="styles.css">
                <link href='./theme.css' rel='stylesheet' media="print">
                <link rel="stylesheet" href="/css/root.css">
                </head><body><script src="script.js"></script>
                <script type="module" src="./mod.js"></script></body></html>""",
            "styles.css" to "body{color:red}",
            "theme.css" to "h1{margin:0}",
            "css/root.css" to ".r{top:0}",
            "script.js" to "console.log('a')",
            "mod.js" to "export const x = 1;"
        )
        val r = bundle("index.html", files)
        assertThat(r.html).contains("<style>\nbody{color:red}\n</style>")
        assertThat(r.html).contains("<style media=\"print\">\nh1{margin:0}\n</style>")
        assertThat(r.html).contains(".r{top:0}")
        assertThat(r.html).contains("<script>\nconsole.log('a')\n</script>")
        assertThat(r.html).contains("<script type=\"module\">")
        assertThat(r.html).doesNotContain("href=\"styles.css\"")
        assertThat(r.html).doesNotContain("src=\"script.js\"")
        assertThat(r.inlined).containsExactly("styles.css", "theme.css", "css/root.css", "script.js", "mod.js")
        assertThat(r.missing).isEmpty()
    }

    @Test
    fun `resolves relative to a page in a subfolder`() {
        val files = mapOf(
            "pages/about.html" to """<link rel="stylesheet" href="../styles.css"><script src="a.js"></script>""",
            "styles.css" to "p{}",
            "pages/a.js" to "var a=1;"
        )
        val r = bundle("pages/about.html", files)
        assertThat(r.html).contains("p{}")
        assertThat(r.html).contains("var a=1;")
    }

    @Test
    fun `leaves remote links and reports missing files`() {
        val files = mapOf(
            "index.html" to """<link rel="stylesheet" href="https://cdn.example.com/a.css">
                <script src="//cdn.example.com/x.js"></script><link rel="stylesheet" href="gone.css">"""
        )
        val r = bundle("index.html", files)
        assertThat(r.html).contains("https://cdn.example.com/a.css")
        assertThat(r.html).contains("//cdn.example.com/x.js")
        assertThat(r.missing).containsExactly("gone.css")
        assertThat(r.inlined).isEmpty()
    }

    @Test
    fun `refuses to climb out of the project`() {
        assertThat(StandaloneBundler.resolve("index.html", "../secret.txt")).isNull()
        assertThat(StandaloneBundler.resolve("a/b.html", "../../x")).isNull()
        assertThat(StandaloneBundler.resolve("a/b.html", "../x.css")).isEqualTo("x.css")
        assertThat(StandaloneBundler.resolve("a/b.html", "/x/y.css?v=2#h")).isEqualTo("x/y.css")
        assertThat(StandaloneBundler.resolve("a.html", "mailto:me@x.dev")).isNull()
        assertThat(StandaloneBundler.resolve("a.html", "#top")).isNull()
    }

    @Test
    fun `a script cannot close its own tag and css url images become data uris`() {
        val files = mapOf(
            "index.html" to """<link rel="stylesheet" href="s.css"><script src="j.js"></script><img src="img/dot.png" alt="d">""",
            "s.css" to """@import "base.css"; .a{background:url('img/dot.png')} .b{background:url(https://x.dev/a.png)}""",
            "base.css" to "html{margin:0}",
            "j.js" to "var s='</script><b>';"
        )
        val png = byteArrayOf(1, 2, 3, 4)
        val r = bundle("index.html", files, mapOf("img/dot.png" to png))
        assertThat(r.html).contains("<\\/script><b>")
        assertThat(r.html).contains("html{margin:0}")
        assertThat(r.html).contains("data:image/png;base64,AQIDBA==")
        assertThat(r.html).contains("url(https://x.dev/a.png)")
        assertThat(r.html).doesNotContain("@import")
        assertThat(r.html).contains("<img src=\"data:image/png;base64,AQIDBA==\" alt=\"d\">")
    }

    @Test
    fun `base64 matches the standard encoding including padding`() {
        assertThat(StandaloneBundler.base64(byteArrayOf())).isEmpty()
        assertThat(StandaloneBundler.base64("f".toByteArray())).isEqualTo("Zg==")
        assertThat(StandaloneBundler.base64("fo".toByteArray())).isEqualTo("Zm8=")
        assertThat(StandaloneBundler.base64("foo".toByteArray())).isEqualTo("Zm9v")
        assertThat(StandaloneBundler.base64("foobar".toByteArray())).isEqualTo("Zm9vYmFy")
    }

    @Test
    fun `data-src is not src and a missing page reports itself`() {
        val r = bundle("index.html", mapOf("index.html" to """<img data-src="lazy.png" src="a.png">"""), mapOf("a.png" to byteArrayOf(0)))
        assertThat(r.html).contains("data-src=\"lazy.png\"")
        assertThat(r.html).contains("src=\"data:image/png;base64,AA==\"")
        assertThat(bundle("nope.html", emptyMap()).missing).containsExactly("nope.html")
    }

    @Test
    fun `closure follows linked pages and their assets`() {
        val files = mapOf(
            "index.html" to """<a href="about.html">About</a><link rel="stylesheet" href="s.css"><a href="https://x.dev">x</a>""",
            "about.html" to """<script src="js/a.js"></script><a href="index.html">Home</a>""",
            "s.css" to "a{background:url(bg.png)}",
            "js/a.js" to "1", "bg.png" to "x", "unrelated.txt" to "y"
        )
        val all = StandaloneBundler.closure("index.html", { files[it] }, { it in files })
        assertThat(all).containsExactly("index.html", "about.html", "s.css", "js/a.js", "bg.png")
        assertThat(all.first()).isEqualTo("index.html")
    }
}
