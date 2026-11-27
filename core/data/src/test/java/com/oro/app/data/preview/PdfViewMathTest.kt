package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfViewMathTest {

    @Test
    fun `zoom steps walk the stops and stop at the limits`() {
        assertThat(PdfViewMath.zoomIn(1f)).isEqualTo(1.25f)
        assertThat(PdfViewMath.zoomIn(1.1f)).isEqualTo(1.25f)
        assertThat(PdfViewMath.zoomOut(1f)).isEqualTo(0.75f)
        assertThat(PdfViewMath.zoomOut(1.1f)).isEqualTo(1f)
        assertThat(PdfViewMath.zoomIn(4f)).isEqualTo(PdfViewMath.MAX_ZOOM)
        assertThat(PdfViewMath.zoomIn(PdfViewMath.MAX_ZOOM)).isEqualTo(PdfViewMath.MAX_ZOOM)
        assertThat(PdfViewMath.zoomOut(0.5f)).isEqualTo(PdfViewMath.MIN_ZOOM)
        assertThat(PdfViewMath.zoomOut(PdfViewMath.MIN_ZOOM)).isEqualTo(PdfViewMath.MIN_ZOOM)
        assertThat(PdfViewMath.clampZoom(99f)).isEqualTo(PdfViewMath.MAX_ZOOM)
        assertThat(PdfViewMath.clampZoom(0f)).isEqualTo(PdfViewMath.MIN_ZOOM)
    }

    @Test
    fun `percent rounds to whole numbers`() {
        assertThat(PdfViewMath.percent(1f)).isEqualTo(100)
        assertThat(PdfViewMath.percent(0.755f)).isEqualTo(76)
        assertThat(PdfViewMath.percent(1.25f)).isEqualTo(125)
    }

    @Test
    fun `rotation wraps in quarter turns both ways`() {
        assertThat(PdfViewMath.rotate(0, true)).isEqualTo(90)
        assertThat(PdfViewMath.rotate(270, true)).isEqualTo(0)
        assertThat(PdfViewMath.rotate(0, false)).isEqualTo(270)
        assertThat(PdfViewMath.rotate(90, false)).isEqualTo(0)
        assertThat(PdfViewMath.rotatedSize(600f, 800f, 0)).isEqualTo(600f to 800f)
        assertThat(PdfViewMath.rotatedSize(600f, 800f, 90)).isEqualTo(800f to 600f)
        assertThat(PdfViewMath.rotatedSize(600f, 800f, 180)).isEqualTo(600f to 800f)
    }

    @Test
    fun `fit width is 100 percent and fit page shrinks tall pages to the screen`() {
        // Portrait page on a phone: 1080 x 2000 viewport, 612 x 792 page: width is the limit.
        assertThat(PdfViewMath.zoomFor(PdfFit.PAGE, 1f, 612f, 792f, 1080f, 2000f)).isEqualTo(1f)
        // Same page on a short viewport: height is the limit, so the page is shown smaller.
        val short = PdfViewMath.zoomFor(PdfFit.PAGE, 1f, 612f, 792f, 1080f, 900f)
        assertThat(short).isWithin(0.001f).of((900f / 792f) / (1080f / 612f))
        assertThat(PdfViewMath.zoomFor(PdfFit.WIDTH, 3f, 612f, 792f, 1080f, 900f)).isEqualTo(1f)
        assertThat(PdfViewMath.zoomFor(PdfFit.CUSTOM, 1.5f, 612f, 792f, 1080f, 900f)).isEqualTo(1.5f)
        assertThat(PdfViewMath.zoomFor(PdfFit.CUSTOM, 50f, 612f, 792f, 1080f, 900f)).isEqualTo(PdfViewMath.MAX_ZOOM)
        // Degenerate sizes fall back to fit width rather than dividing by zero.
        assertThat(PdfViewMath.zoomFor(PdfFit.PAGE, 1f, 0f, 0f, 0f, 0f)).isEqualTo(1f)
    }

    @Test
    fun `render size follows the viewport and is capped in pixels`() {
        assertThat(PdfViewMath.renderSize(612f, 792f, 1000, 1f)).isEqualTo(1000 to 1294)
        val (w, h) = PdfViewMath.renderSize(612f, 792f, 1080, 5f)
        assertThat(w.toLong() * h).isAtMost(12_000_000L)
        assertThat(w.toFloat() / h).isWithin(0.01f).of(612f / 792f)
        assertThat(PdfViewMath.renderSize(0f, 0f, 1080, 1f)).isEqualTo(1 to 1)
    }

    @Test
    fun `page input is clamped and bad input is ignored`() {
        assertThat(PdfViewMath.parsePageInput("5", 10)).isEqualTo(5)
        assertThat(PdfViewMath.parsePageInput(" 7 ", 10)).isEqualTo(7)
        assertThat(PdfViewMath.parsePageInput("99", 10)).isEqualTo(10)
        assertThat(PdfViewMath.parsePageInput("0", 10)).isEqualTo(1)
        assertThat(PdfViewMath.parsePageInput("-3", 10)).isEqualTo(1)
        assertThat(PdfViewMath.parsePageInput("abc", 10)).isNull()
        assertThat(PdfViewMath.parsePageInput("", 10)).isNull()
        assertThat(PdfViewMath.parsePageInput("1", 0)).isNull()
    }

    @Test
    fun `current page is the one under the middle of the viewport`() {
        val tops = listOf(0f, 1000f, 2000f)
        val heights = listOf(900f, 900f, 900f)
        assertThat(PdfViewMath.currentPage(tops, heights, 0f, 800f)).isEqualTo(0)
        assertThat(PdfViewMath.currentPage(tops, heights, 900f, 800f)).isEqualTo(1)
        assertThat(PdfViewMath.currentPage(tops, heights, 1950f, 800f)).isEqualTo(2)
        // The middle falls in the gap between pages: the nearer page wins.
        assertThat(PdfViewMath.currentPage(tops, heights, 550f, 800f)).isEqualTo(0)
        assertThat(PdfViewMath.currentPage(emptyList(), emptyList(), 0f, 800f)).isEqualTo(0)
    }

    @Test
    fun `sources are told apart`() {
        assertThat(PdfSources.classify("data:application/pdf;base64,AAAA")).isEqualTo(PdfSourceType.DATA_URI)
        assertThat(PdfSources.classify("HTTPS://x.test/a.pdf")).isEqualTo(PdfSourceType.HTTP_URL)
        assertThat(PdfSources.classify("content://docs/1")).isEqualTo(PdfSourceType.CONTENT_URI)
        assertThat(PdfSources.classify("file:///sdcard/a.pdf")).isEqualTo(PdfSourceType.FILE_URI)
        assertThat(PdfSources.classify("/data/user/0/app/files/a.pdf")).isEqualTo(PdfSourceType.FILE_PATH)
    }

    @Test
    fun `data uris are split into type and payload`() {
        val b64 = PdfSources.parseDataUri(" data:application/pdf;base64,JVBERi0= ")!!
        assertThat(b64.mimeType).isEqualTo("application/pdf")
        assertThat(b64.isBase64).isTrue()
        assertThat(b64.payload).isEqualTo("JVBERi0=")
        val plain = PdfSources.parseDataUri("data:,hello")!!
        assertThat(plain.mimeType).isEqualTo("text/plain")
        assertThat(plain.isBase64).isFalse()
        assertThat(PdfSources.parseDataUri("data:application/pdf")).isNull()
        assertThat(PdfSources.parseDataUri("https://x.test/a.pdf")).isNull()
    }

    @Test
    fun `pdf magic is found at the start or within the first kilobyte`() {
        assertThat(PdfSources.looksLikePdf("%PDF-1.7\n".toByteArray())).isTrue()
        assertThat(PdfSources.looksLikePdf(("junk".repeat(50) + "%PDF-1.4").toByteArray())).isTrue()
        assertThat(PdfSources.looksLikePdf(("x".repeat(2000) + "%PDF-1.4").toByteArray())).isFalse()
        assertThat(PdfSources.looksLikePdf("<html>".toByteArray())).isFalse()
        assertThat(PdfSources.looksLikePdf(ByteArray(0))).isFalse()
        assertThat(PdfSources.looksLikePdf("%PDF".toByteArray())).isFalse()
    }

    @Test
    fun `display names come from the last path segment`() {
        assertThat(PdfSources.displayName("/files/projects/Annual%20Report.pdf")).isEqualTo("Annual Report.pdf")
        assertThat(PdfSources.displayName("https://x.test/docs/a.pdf?dl=1#p2")).isEqualTo("a.pdf")
        assertThat(PdfSources.displayName("data:application/pdf;base64,AAAA")).isEqualTo("Document.pdf")
        assertThat(PdfSources.displayName("https://x.test/", "Fallback.pdf")).isEqualTo("x.test")
    }
}
