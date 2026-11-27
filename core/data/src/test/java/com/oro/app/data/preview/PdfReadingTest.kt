package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfReadingTest {

    @Test
    fun `dark mode turns white paper near black and black text near white`() {
        val paper = PdfReadingMode.DARK.apply(255, 255, 255)
        val ink = PdfReadingMode.DARK.apply(0, 0, 0)
        assertThat(paper.first).isIn(10..30)
        assertThat(paper.second).isIn(10..30)
        assertThat(ink.first).isIn(210..235)
        assertThat(ink.third).isIn(210..235)
    }

    @Test
    fun `sepia warms white and keeps black black`() {
        val paper = PdfReadingMode.SEPIA.apply(255, 255, 255)
        assertThat(paper.first).isGreaterThan(paper.second)
        assertThat(paper.second).isGreaterThan(paper.third)
        assertThat(PdfReadingMode.SEPIA.apply(0, 0, 0)).isEqualTo(Triple(0, 0, 0))
    }

    @Test
    fun `original has no matrix and modes cycle`() {
        assertThat(PdfReadingMode.ORIGINAL.matrix).isNull()
        assertThat(PdfReadingMode.ORIGINAL.apply(1, 2, 3)).isEqualTo(Triple(1, 2, 3))
        assertThat(PdfReadingMode.DARK.matrix!!.size).isEqualTo(20)
        assertThat(PdfReadingMode.ORIGINAL.next()).isEqualTo(PdfReadingMode.DARK)
        assertThat(PdfReadingMode.SEPIA.next()).isEqualTo(PdfReadingMode.ORIGINAL)
    }

    @Test
    fun `keyboard shortcuts`() {
        assertThat(PdfShortcuts.resolve("ArrowLeft")).isEqualTo(PdfCommand.PREVIOUS_PAGE)
        assertThat(PdfShortcuts.resolve("PageUp")).isEqualTo(PdfCommand.PREVIOUS_PAGE)
        assertThat(PdfShortcuts.resolve("ArrowRight")).isEqualTo(PdfCommand.NEXT_PAGE)
        assertThat(PdfShortcuts.resolve("PageDown")).isEqualTo(PdfCommand.NEXT_PAGE)
        assertThat(PdfShortcuts.resolve("+")).isEqualTo(PdfCommand.ZOOM_IN)
        assertThat(PdfShortcuts.resolve("=", ctrl = true)).isEqualTo(PdfCommand.ZOOM_IN)
        assertThat(PdfShortcuts.resolve("-", ctrl = true)).isEqualTo(PdfCommand.ZOOM_OUT)
        assertThat(PdfShortcuts.resolve("0")).isEqualTo(PdfCommand.ZOOM_RESET)
        assertThat(PdfShortcuts.resolve("f")).isEqualTo(PdfCommand.FULLSCREEN)
        assertThat(PdfShortcuts.resolve("m")).isEqualTo(PdfCommand.CYCLE_READING_MODE)
        assertThat(PdfShortcuts.resolve("Home")).isEqualTo(PdfCommand.FIRST_PAGE)
        assertThat(PdfShortcuts.resolve("End")).isEqualTo(PdfCommand.LAST_PAGE)
    }

    @Test
    fun `shortcuts leave browser and system chords alone`() {
        assertThat(PdfShortcuts.resolve("f", ctrl = true)).isNull()
        assertThat(PdfShortcuts.resolve("ArrowLeft", alt = true)).isNull()
        assertThat(PdfShortcuts.resolve("ArrowRight", ctrl = true)).isNull()
        assertThat(PdfShortcuts.resolve("q")).isNull()
    }

    @Test
    fun `file size and summary`() {
        assertThat(PdfInfo.formatBytes(412)).isEqualTo("412 B")
        assertThat(PdfInfo.formatBytes(830 * 1024L)).isEqualTo("830 KB")
        assertThat(PdfInfo.formatBytes((1.2 * 1024 * 1024).toLong())).isEqualTo("1.1 MB")
        assertThat(PdfInfo.summary(1, 2048)).isEqualTo("1 page · 2 KB")
        assertThat(PdfInfo.summary(12, 3L * 1024 * 1024)).isEqualTo("12 pages · 3.0 MB")
    }

    @Test
    fun `sources normalise to one shape`() {
        val b64 = "JVBERi0xLjQKJcfsj6IKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwo+PgplbmRvYmoK"
        assertThat(PdfSources.normalize("  $b64 \n")).isEqualTo("data:application/pdf;base64,$b64")
        assertThat(PdfSources.classify(PdfSources.normalize(b64))).isEqualTo(PdfSourceType.DATA_URI)
        val wrapped = b64.chunked(20).joinToString("\n")
        assertThat(PdfSources.normalize(wrapped)).isEqualTo("data:application/pdf;base64,$b64")
        assertThat(PdfSources.normalize("data:application/pdf;base64,AAAA")).isEqualTo("data:application/pdf;base64,AAAA")
        assertThat(PdfSources.normalize(" /sdcard/a.pdf ")).isEqualTo("/sdcard/a.pdf")
        assertThat(PdfSources.normalize("JVBERi0")).isEqualTo("JVBERi0")
    }

    @Test
    fun `every source kind is recognised`() {
        assertThat(PdfSources.classify("blob:http://localhost/1234")).isEqualTo(PdfSourceType.BLOB_URL)
        assertThat(PdfSources.classify("https://x.dev/a.pdf")).isEqualTo(PdfSourceType.HTTP_URL)
        assertThat(PdfSources.classify("file:///a/b.pdf")).isEqualTo(PdfSourceType.FILE_URI)
        assertThat(PdfSources.classify("content://x/1")).isEqualTo(PdfSourceType.CONTENT_URI)
        assertThat(PdfSources.classify("/data/a.pdf")).isEqualTo(PdfSourceType.FILE_PATH)
        assertThat(PdfSources.displayName("blob:http://localhost/1234")).isEqualTo("Document.pdf")
    }
}
