package com.oro.app.data.preview

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** How the PDF viewer sizes its pages. */
enum class PdfFit { WIDTH, PAGE, CUSTOM }

/**
 * The viewer's arithmetic, kept free of Android so it is tested on the JVM.
 *
 * Zoom is relative to fit-width: 1.0 (100%) makes a page exactly as wide as
 * the screen, which is what a phone reader expects, rather than the PDF's
 * 72-dpi "actual size", which would be a postage stamp.
 */
object PdfViewMath {
    const val MIN_ZOOM = 0.25f
    const val MAX_ZOOM = 5f

    /** The stops the percentage menu and the +/- buttons walk through. */
    val ZOOM_STOPS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)

    fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /** The next stop above [zoom]; the top stop's successor is [MAX_ZOOM]. */
    fun zoomIn(zoom: Float): Float =
        clampZoom(ZOOM_STOPS.firstOrNull { it > zoom + EPS } ?: MAX_ZOOM)

    /** The next stop below [zoom]. */
    fun zoomOut(zoom: Float): Float =
        clampZoom(ZOOM_STOPS.lastOrNull { it < zoom - EPS } ?: MIN_ZOOM)

    fun percent(zoom: Float): Int = (zoom * 100f + 0.5f).toInt()

    /** `n` quarter turns clockwise, as a degree count in 0, 90, 180, 270. */
    fun rotate(degrees: Int, clockwise: Boolean): Int =
        ((degrees + if (clockwise) 90 else -90) % 360 + 360) % 360

    /** A page's size once rotated: a quarter turn swaps width and height. */
    fun rotatedSize(width: Float, height: Float, degrees: Int): Pair<Float, Float> =
        if (degrees % 180 == 0) width to height else height to width

    /**
     * The zoom (relative to fit-width) that [fit] asks for on a page of
     * [pageWidth] x [pageHeight] (already rotated) shown in a viewport.
     * Fit page never exceeds what fits both ways, so a landscape page is
     * shown whole rather than cropped by the screen's height.
     */
    fun zoomFor(
        fit: PdfFit,
        custom: Float,
        pageWidth: Float,
        pageHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): Float = when (fit) {
        PdfFit.WIDTH -> 1f
        PdfFit.CUSTOM -> clampZoom(custom)
        PdfFit.PAGE -> {
            if (pageWidth <= 0f || pageHeight <= 0f || viewportWidth <= 0f || viewportHeight <= 0f) 1f
            else {
                val byWidth = viewportWidth / pageWidth
                val byHeight = viewportHeight / pageHeight
                clampZoom(min(byWidth, byHeight) / byWidth)
            }
        }
    }

    /**
     * Pixel size to render a page at: its width is [viewportWidthPx] x zoom, capped
     * so one bitmap never exceeds [maxPixels] (a 5x zoom of a big page would
     * otherwise be hundreds of megabytes).
     */
    fun renderSize(
        pageWidth: Float,
        pageHeight: Float,
        viewportWidthPx: Int,
        zoom: Float,
        maxPixels: Long = 12_000_000L
    ): Pair<Int, Int> {
        if (pageWidth <= 0f || pageHeight <= 0f || viewportWidthPx <= 0) return 1 to 1
        var w = max(1f, viewportWidthPx * zoom)
        var h = w * pageHeight / pageWidth
        val pixels = w.toDouble() * h.toDouble()
        if (pixels > maxPixels) {
            val shrink = Math.sqrt(maxPixels / pixels).toFloat()
            w *= shrink
            h *= shrink
        }
        return max(1, w.toInt()) to max(1, h.toInt())
    }

    /** A 1-based page number typed by the user, clamped into range; null if not a number. */
    fun parsePageInput(text: String, pageCount: Int): Int? {
        if (pageCount <= 0) return null
        val n = text.trim().toIntOrNull() ?: return null
        return n.coerceIn(1, pageCount)
    }

    /** The 0-based page occupying most of the viewport, given each page's top and height. */
    fun currentPage(tops: List<Float>, heights: List<Float>, scrollY: Float, viewportHeight: Float): Int {
        if (tops.isEmpty()) return 0
        val mid = scrollY + viewportHeight / 2f
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in tops.indices) {
            val center = tops[i] + heights.getOrElse(i) { 0f } / 2f
            val d = abs(center - mid)
            if (mid in tops[i]..(tops[i] + heights.getOrElse(i) { 0f })) return i
            if (d < bestDistance) { bestDistance = d; best = i }
        }
        return best
    }

    private const val EPS = 0.001f
}

/** Where a PDF comes from, decided from the string it was handed. */
enum class PdfSourceType { DATA_URI, HTTP_URL, CONTENT_URI, FILE_URI, BLOB_URL, FILE_PATH }

/** A `data:` URI split into its type and (still encoded) payload. */
data class DataUri(val mimeType: String, val isBase64: Boolean, val payload: String)

object PdfSources {

    fun classify(source: String): PdfSourceType {
        val s = source.trim()
        return when {
            s.startsWith("data:", ignoreCase = true) -> PdfSourceType.DATA_URI
            s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true) -> PdfSourceType.HTTP_URL
            s.startsWith("content://", ignoreCase = true) -> PdfSourceType.CONTENT_URI
            s.startsWith("file://", ignoreCase = true) -> PdfSourceType.FILE_URI
            s.startsWith("blob:", ignoreCase = true) -> PdfSourceType.BLOB_URL
            else -> PdfSourceType.FILE_PATH
        }
    }

    /**
     * Brings the forms an agent or a page hands over to one shape: a bare
     * base64 string (`JVBERi0x...`, which is what `%PDF-1` encodes to, with
     * or without line breaks) becomes a `data:application/pdf;base64,` URI;
     * everything else is only trimmed.
     */
    fun normalize(source: String): String {
        val s = source.trim()
        if (s.startsWith("JVBERi", ignoreCase = false) && s.length >= 16) {
            val compact = s.filterNot { it.isWhitespace() }
            if (compact.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '-' || it == '_' || it == '=' }) {
                return "data:application/pdf;base64,$compact"
            }
        }
        return s
    }

    /** Splits `data:application/pdf;base64,....`; null if it is not a data URI. */
    fun parseDataUri(source: String): DataUri? {
        val s = source.trim()
        if (!s.startsWith("data:", ignoreCase = true)) return null
        val comma = s.indexOf(',')
        if (comma < 0) return null
        val header = s.substring(5, comma)
        val parts = header.split(';')
        val mime = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: "text/plain"
        val base64 = parts.drop(1).any { it.equals("base64", ignoreCase = true) }
        return DataUri(mime.lowercase(), base64, s.substring(comma + 1))
    }

    /** PDF files start with `%PDF-`, possibly after a little junk (the spec allows 1 KB). */
    fun looksLikePdf(head: ByteArray): Boolean {
        val magic = "%PDF-".toByteArray(Charsets.US_ASCII)
        val limit = min(head.size, 1024) - magic.size
        for (start in 0..limit) {
            var match = true
            for (j in magic.indices) if (head[start + j] != magic[j]) { match = false; break }
            if (match) return true
        }
        return false
    }

    /** A readable file name for the viewer's title bar, whatever the source. */
    fun displayName(source: String, fallback: String = "Document.pdf"): String {
        if (classify(source) == PdfSourceType.DATA_URI || classify(source) == PdfSourceType.BLOB_URL) return fallback
        val last = source.trim().substringBefore('#').substringBefore('?').trimEnd('/')
            .substringAfterLast('/').substringAfterLast('\\')
        val decoded = runCatching { java.net.URLDecoder.decode(last.replace("+", "%2B"), "UTF-8") }.getOrDefault(last)
        return decoded.ifBlank { fallback }
    }
}
