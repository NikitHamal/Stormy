package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtifactViewsTest {

    @Test
    fun `new kinds are recognised and open the preview`() {
        assertThat(PreviewArtifacts.kindOf("data/people.csv")).isEqualTo(ArtifactKind.CSV)
        assertThat(PreviewArtifacts.kindOf("a.TSV")).isEqualTo(ArtifactKind.CSV)
        assertThat(PreviewArtifacts.kindOf("db.json")).isEqualTo(ArtifactKind.JSON)
        assertThat(PreviewArtifacts.kindOf("notes.txt")).isEqualTo(ArtifactKind.TEXT)
        listOf("a.csv", "a.json", "a.txt").forEach {
            assertThat(PreviewArtifacts.actionFor(it)).isEqualTo(ArtifactAction.LIVE_PREVIEW)
        }
    }

    @Test
    fun `viewer pages route through the view prefix and back`() {
        val base = "http://127.0.0.1:8080"
        val svg = PreviewArtifacts.urlFor(base, "img/my logo.svg")
        assertThat(svg).isEqualTo("$base/__oro/view/img/my%20logo.svg")
        assertThat(PreviewArtifacts.relativeOf(base, svg)).isEqualTo("img/my logo.svg")
        assertThat(PreviewArtifacts.urlFor(base, "data.csv")).isEqualTo("$base/__oro/view/data.csv")
        assertThat(PreviewArtifacts.urlFor(base, "style.css")).isEqualTo("$base/style.css")
        assertThat(PreviewArtifacts.urlFor(base, "README.md")).isEqualTo("$base/__oro/md/README.md")
        assertThat(PreviewArtifacts.relativeOf(base, "$base/__oro/md/README.md")).isEqualTo("README.md")
    }

    @Test
    fun `svg studio shows the image from the raw url and the escaped code`() {
        val page = ArtifactViews.page(ArtifactKind.SVG, "logo.svg", "/logo.svg", "<svg><script>alert(1)</script></svg>")!!
        assertThat(page).contains("src=\"/logo.svg\"")
        assertThat(page).contains("&lt;svg&gt;&lt;script&gt;alert(1)&lt;/script&gt;&lt;/svg&gt;")
        assertThat(page).doesNotContain("<svg><script>")
        assertThat(page).contains("checker")
    }

    @Test
    fun `source text cannot break out of the page`() {
        val evil = "</pre><script>steal()</script>"
        listOf(ArtifactKind.CSV, ArtifactKind.JSON, ArtifactKind.TEXT).forEach { kind ->
            val page = ArtifactViews.page(kind, "x", "/x", evil)!!
            assertThat(page).doesNotContain(evil)
            assertThat(page).contains("&lt;/pre&gt;&lt;script&gt;steal()&lt;/script&gt;")
        }
    }

    @Test
    fun `file names are escaped and unrelated kinds have no page`() {
        val page = ArtifactViews.page(ArtifactKind.TEXT, "<b>.txt", "/x", "hi")!!
        assertThat(page).contains("&lt;b&gt;.txt")
        assertThat(ArtifactViews.page(ArtifactKind.WEB_PAGE, "a.html", "/a.html", "x")).isNull()
        assertThat(ArtifactViews.page(ArtifactKind.CSV, "a.csv", "/a.csv", "a,b")).contains("filter")
    }
}
