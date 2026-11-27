package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreviewArtifactsTest {

    @Test
    fun `files are classified by extension, case-insensitively and across separators`() {
        assertThat(PreviewArtifacts.kindOf("index.html")).isEqualTo(ArtifactKind.WEB_PAGE)
        assertThat(PreviewArtifacts.kindOf("pages/About.HTM")).isEqualTo(ArtifactKind.WEB_PAGE)
        assertThat(PreviewArtifacts.kindOf("css\\site.css")).isEqualTo(ArtifactKind.STYLE)
        assertThat(PreviewArtifacts.kindOf("src/App.tsx")).isEqualTo(ArtifactKind.SCRIPT)
        assertThat(PreviewArtifacts.kindOf("logo.svg")).isEqualTo(ArtifactKind.SVG)
        assertThat(PreviewArtifacts.kindOf("hero.WEBP")).isEqualTo(ArtifactKind.IMAGE)
        assertThat(PreviewArtifacts.kindOf("docs/Report.PDF")).isEqualTo(ArtifactKind.PDF)
        assertThat(PreviewArtifacts.kindOf("README.md")).isEqualTo(ArtifactKind.MARKDOWN)
        assertThat(PreviewArtifacts.kindOf("Makefile")).isEqualTo(ArtifactKind.OTHER)
        assertThat(PreviewArtifacts.kindOf("folder.d/file")).isEqualTo(ArtifactKind.OTHER)
    }

    @Test
    fun `each kind earns the right button`() {
        assertThat(PreviewArtifacts.actionFor("a.pdf")).isEqualTo(ArtifactAction.PDF_VIEWER)
        assertThat(PreviewArtifacts.actionFor("a.html")).isEqualTo(ArtifactAction.LIVE_PREVIEW)
        assertThat(PreviewArtifacts.actionFor("a.css")).isEqualTo(ArtifactAction.LIVE_PREVIEW)
        assertThat(PreviewArtifacts.actionFor("a.jsx")).isEqualTo(ArtifactAction.LIVE_PREVIEW)
        assertThat(PreviewArtifacts.actionFor("a.svg")).isEqualTo(ArtifactAction.LIVE_PREVIEW)
        assertThat(PreviewArtifacts.actionFor("a.md")).isEqualTo(ArtifactAction.LIVE_PREVIEW)
        assertThat(PreviewArtifacts.actionFor("a.png")).isEqualTo(ArtifactAction.NONE)
        assertThat(PreviewArtifacts.actionFor("a.kt")).isEqualTo(ArtifactAction.NONE)
    }

    @Test
    fun `pages lists index first, then shallow before deep, and skips hidden and build folders`() {
        val files = listOf(
            "pricing.html", "docs/guide/intro.html", "blog/index.html", "index.html", "About.html",
            "node_modules/pkg/readme.html", "dist/index.html", ".oro/cache/x.html", "style.css", "a.pdf",
            "index.html"
        )
        assertThat(PreviewArtifacts.pages(files)).containsExactly(
            "index.html", "About.html", "pricing.html", "blog/index.html", "docs/guide/intro.html"
        ).inOrder()
    }

    @Test
    fun `entry point is index, else the first page, else nothing`() {
        assertThat(PreviewArtifacts.entryPoint(listOf("z.html", "index.html"))).isEqualTo("index.html")
        assertThat(PreviewArtifacts.entryPoint(listOf("pricing.html", "about.html"))).isEqualTo("about.html")
        assertThat(PreviewArtifacts.entryPoint(listOf("style.css"))).isNull()
        assertThat(PreviewArtifacts.entryPoint(emptyList())).isNull()
    }

    @Test
    fun `urls encode each segment and send markdown through its document route`() {
        val base = "http://localhost:8123/"
        assertThat(PreviewArtifacts.urlFor(base, "index.html")).isEqualTo("http://localhost:8123/index.html")
        assertThat(PreviewArtifacts.urlFor(base, "/my pages/a&b.html")).isEqualTo("http://localhost:8123/my%20pages/a%26b.html")
        assertThat(PreviewArtifacts.urlFor("http://localhost:8123", "docs/README.md"))
            .isEqualTo("http://localhost:8123/__oro/md/docs/README.md")
    }

    @Test
    fun `a url maps back to its project path`() {
        val base = "http://localhost:8123"
        assertThat(PreviewArtifacts.relativeOf(base, "http://localhost:8123/pricing.html?x=1#top")).isEqualTo("pricing.html")
        assertThat(PreviewArtifacts.relativeOf(base, "http://localhost:8123/")).isEqualTo("index.html")
        assertThat(PreviewArtifacts.relativeOf(base, "http://localhost:8123")).isEqualTo("index.html")
        assertThat(PreviewArtifacts.relativeOf(base, "http://localhost:8123/blog/")).isEqualTo("blog/index.html")
        assertThat(PreviewArtifacts.relativeOf(base, "http://localhost:8123/my%20pages/a%26b.html")).isEqualTo("my pages/a&b.html")
        assertThat(PreviewArtifacts.relativeOf(base, "http://localhost:8123/__oro/md/README.md")).isEqualTo("README.md")
        assertThat(PreviewArtifacts.relativeOf(base, "http://localhost:81230/x.html")).isNull()
        assertThat(PreviewArtifacts.relativeOf(base, "https://example.com/")).isNull()
        assertThat(PreviewArtifacts.relativeOf(base, null)).isNull()
    }

    @Test
    fun `url and relativeOf round trip`() {
        val base = "http://localhost:5000"
        for (path in listOf("index.html", "a b/c d.html", "ünï/页.html", "docs/README.md", "x+y.html")) {
            assertThat(PreviewArtifacts.relativeOf(base, PreviewArtifacts.urlFor(base, path))).isEqualTo(path)
        }
    }

    private fun valid(input: String) = (PreviewArtifacts.parseLocalUrl(input) as LocalUrl.Valid).url

    @Test
    fun `the address bar accepts ports and localhost shapes`() {
        assertThat(valid("5173")).isEqualTo("http://localhost:5173")
        assertThat(valid(" 3000/admin ")).isEqualTo("http://localhost:3000/admin")
        assertThat(valid(":8000")).isEqualTo("http://localhost:8000")
        assertThat(valid("localhost:4000/x?y=1")).isEqualTo("http://localhost:4000/x?y=1")
        assertThat(valid("127.0.0.1:8080")).isEqualTo("http://127.0.0.1:8080")
        assertThat(valid("https://localhost:8443/")).isEqualTo("https://localhost:8443/")
        assertThat(valid("http://[::1]:3000")).isEqualTo("http://[::1]:3000")
        assertThat(valid("localhost")).isEqualTo("http://localhost")
    }

    @Test
    fun `the address bar refuses other sites, other schemes and bad ports`() {
        listOf(
            "", "   ", "example.com", "http://evil.test:3000", "https://localhost.evil.com",
            "javascript:alert(1)", "file:///sdcard/x.html", "ftp://localhost", "99999", "0",
            "http://localhost:99999", "http://user@localhost:3000", "local host:3000",
            "http://localhost:abc", "http://127.0.0.1.evil.com"
        ).forEach { bad ->
            assertThat(PreviewArtifacts.parseLocalUrl(bad)).isInstanceOf(LocalUrl.Invalid::class.java)
        }
    }

    @Test
    fun `loopback hosts`() {
        assertThat(PreviewArtifacts.isLoopbackHost("localhost")).isTrue()
        assertThat(PreviewArtifacts.isLoopbackHost("127.0.0.5")).isTrue()
        assertThat(PreviewArtifacts.isLoopbackHost("[::1]")).isTrue()
        assertThat(PreviewArtifacts.isLoopbackHost("192.168.1.5")).isFalse()
        assertThat(PreviewArtifacts.isLoopbackHost("127.0.0.1.nip.io")).isFalse()
    }
}
