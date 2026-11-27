package com.oro.app.data.preview

/** How the PDF viewer paints pages: as they are, softened for a dark UI, or warm for long reading. */
enum class PdfReadingMode(val label: String) {
    ORIGINAL("Original"), DARK("Dark"), SEPIA("Sepia");

    fun next(): PdfReadingMode = entries[(ordinal + 1) % entries.size]

    /**
     * A 4x5 colour matrix in Android's `ColorMatrix` order (rows R, G, B, A;
     * the fifth column is an offset in 0..255), or null for [ORIGINAL].
     * Dark inverts brightness but keeps hues (invert plus a 180-degree hue
     * turn), so white paper becomes near-black and black text near-white
     * while photos keep their colours. Sepia maps luminance onto warm paper.
     */
    val matrix: FloatArray?
        get() = when (this) {
            ORIGINAL -> null
            DARK -> {
                val k = (DARK_TEXT - DARK_PAPER) / 255f
                val hue = floatArrayOf(
                    -0.574f, 1.430f, 0.144f,
                    0.426f, 0.430f, 0.144f,
                    0.426f, 1.430f, -0.856f
                )
                val offset = DARK_PAPER + 255f * k
                floatArrayOf(
                    -k * hue[0], -k * hue[1], -k * hue[2], 0f, offset,
                    -k * hue[3], -k * hue[4], -k * hue[5], 0f, offset,
                    -k * hue[6], -k * hue[7], -k * hue[8], 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
            }
            SEPIA -> {
                val lr = 0.299f; val lg = 0.587f; val lb = 0.114f
                val tr = 244f / 255f; val tg = 236f / 255f; val tb = 216f / 255f
                floatArrayOf(
                    lr * tr, lg * tr, lb * tr, 0f, 0f,
                    lr * tg, lg * tg, lb * tg, 0f, 0f,
                    lr * tb, lg * tb, lb * tb, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            }
        }

    /** Applies [matrix] to one RGB colour (0..255 per channel), clamped; for tests and swatches. */
    fun apply(r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        val m = matrix ?: return Triple(r, g, b)
        fun row(i: Int) = (m[i * 5] * r + m[i * 5 + 1] * g + m[i * 5 + 2] * b + m[i * 5 + 4]).toInt().coerceIn(0, 255)
        return Triple(row(0), row(1), row(2))
    }

    private companion object {
        const val DARK_PAPER = 18f
        const val DARK_TEXT = 224f
    }
}

/** What a key press does in the PDF viewer. */
enum class PdfCommand {
    PREVIOUS_PAGE, NEXT_PAGE, FIRST_PAGE, LAST_PAGE, ZOOM_IN, ZOOM_OUT, ZOOM_RESET, FULLSCREEN, CYCLE_READING_MODE
}

object PdfShortcuts {

    /**
     * Maps a key (by name: `ArrowLeft`, `PageDown`, `+`, `-`, `=`, `0`, `f`, `m`, ...)
     * and its modifiers to a command. Plain arrows and Page keys turn pages;
     * `+`/`=` and `-` zoom with or without Ctrl/Cmd; `0` resets zoom; `f` toggles
     * full screen; `m` cycles the reading mode. Anything with Alt is left alone.
     */
    fun resolve(key: String, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false): PdfCommand? {
        if (alt) return null
        return when (key) {
            "ArrowLeft", "PageUp" -> if (ctrl) null else PdfCommand.PREVIOUS_PAGE
            "ArrowRight", "PageDown" -> if (ctrl) null else PdfCommand.NEXT_PAGE
            "Home" -> PdfCommand.FIRST_PAGE
            "End" -> PdfCommand.LAST_PAGE
            "+", "=" -> PdfCommand.ZOOM_IN
            "-", "_" -> PdfCommand.ZOOM_OUT
            "0" -> PdfCommand.ZOOM_RESET
            "f", "F" -> if (ctrl) null else PdfCommand.FULLSCREEN
            "m", "M" -> if (ctrl) null else PdfCommand.CYCLE_READING_MODE
            else -> null
        }
    }
}

object PdfInfo {
    /** `12.4 MB`, `830 KB`, `412 B`. */
    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> {
            val tenths = bytes * 10 / (1024 * 1024)
            "${tenths / 10}.${tenths % 10} MB"
        }
    }

    /** `12 pages · 1.2 MB`. */
    fun summary(pageCount: Int, bytes: Long): String =
        "$pageCount ${if (pageCount == 1) "page" else "pages"} · ${formatBytes(bytes)}"
}
