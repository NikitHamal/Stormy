package com.codex.stormy.utils

import androidx.compose.ui.graphics.Color

/**
 * Centralized CSS utility functions for parsing and formatting CSS values.
 * This eliminates duplicate code across ElementInspector, PreviewActivity, and other UI components.
 */
object CssUtils {

    // Pre-compiled regex patterns for better performance
    private val PIXEL_PATTERN = Regex("""(-?\d+(?:\.\d+)?)px""")
    private val RGB_PATTERN = Regex("""rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""")
    private val RGBA_PATTERN = Regex("""rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)""")
    private val HEX_COLOR_PATTERN = Regex("""#([0-9a-fA-F]{3,8})""")
    private val BOX_SHADOW_PATTERN = Regex("""(-?\d+)px\s+(-?\d+)px\s+(-?\d+)px\s+(-?\d+)px\s+(rgba?\([^)]+\)|#[0-9a-fA-F]{3,8})""")
    private val COLOR_IN_VALUE_PATTERN = Regex("""(rgba?\([^)]+\)|#[0-9a-fA-F]{3,8})""")

    /**
     * Data class representing parsed box shadow values
     */
    data class BoxShadowValues(
        val offsetX: Int = 0,
        val offsetY: Int = 4,
        val blurRadius: Int = 8,
        val spreadRadius: Int = 0,
        val color: String = "rgba(0,0,0,0.1)"
    ) {
        fun toCssString(): String = "${offsetX}px ${offsetY}px ${blurRadius}px ${spreadRadius}px $color"
    }

    /**
     * Parse a pixel value string to Float
     * @param value CSS value like "16px", "10.5px", etc.
     * @return The numeric value or null if not parseable
     */
    fun parsePixelValue(value: String): Float? {
        val match = PIXEL_PATTERN.find(value)
        return match?.groupValues?.get(1)?.toFloatOrNull()
    }

    /**
     * Parse a pixel value with a default fallback
     */
    fun parsePixelValue(value: String, default: Float): Float {
        return parsePixelValue(value) ?: default
    }

    /**
     * Format a numeric value to CSS pixel string
     */
    fun formatPixelValue(value: Number): String = "${value.toInt()}px"

    /**
     * Parse a color string (hex, rgb, rgba) to Compose Color
     * @param colorString CSS color value
     * @return Compose Color or Color.Transparent if parsing fails
     */
    fun parseColor(colorString: String): Color {
        val trimmed = colorString.trim().lowercase()

        return try {
            when {
                trimmed.startsWith("#") -> parseHexColor(trimmed)
                trimmed.startsWith("rgba") -> parseRgbaColor(trimmed)
                trimmed.startsWith("rgb") -> parseRgbColor(trimmed)
                trimmed == "transparent" -> Color.Transparent
                else -> Color.Transparent
            }
        } catch (e: Exception) {
            Color.Transparent
        }
    }

    /**
     * Parse hex color string (#RGB, #RRGGBB, #RRGGBBAA)
     */
    private fun parseHexColor(hex: String): Color {
        val cleanHex = hex.removePrefix("#")
        return when (cleanHex.length) {
            3 -> {
                // #RGB -> #RRGGBB
                val r = cleanHex[0].toString().repeat(2).toInt(16)
                val g = cleanHex[1].toString().repeat(2).toInt(16)
                val b = cleanHex[2].toString().repeat(2).toInt(16)
                Color(r, g, b)
            }
            6 -> {
                // #RRGGBB
                val colorLong = cleanHex.toLong(16)
                Color(
                    red = ((colorLong shr 16) and 0xFF).toInt(),
                    green = ((colorLong shr 8) and 0xFF).toInt(),
                    blue = (colorLong and 0xFF).toInt()
                )
            }
            8 -> {
                // #RRGGBBAA
                val colorLong = cleanHex.toLong(16)
                Color(
                    red = ((colorLong shr 24) and 0xFF).toInt(),
                    green = ((colorLong shr 16) and 0xFF).toInt(),
                    blue = ((colorLong shr 8) and 0xFF).toInt(),
                    alpha = (colorLong and 0xFF).toInt()
                )
            }
            else -> Color.Transparent
        }
    }

    /**
     * Parse rgb() color string
     */
    private fun parseRgbColor(rgb: String): Color {
        val match = RGB_PATTERN.find(rgb) ?: return Color.Transparent
        val (r, g, b) = match.destructured
        return Color(
            r.toInt().coerceIn(0, 255),
            g.toInt().coerceIn(0, 255),
            b.toInt().coerceIn(0, 255)
        )
    }

    /**
     * Parse rgba() color string
     */
    private fun parseRgbaColor(rgba: String): Color {
        val match = RGBA_PATTERN.find(rgba) ?: return Color.Transparent
        val (r, g, b, a) = match.destructured
        return Color(
            r.toInt().coerceIn(0, 255),
            g.toInt().coerceIn(0, 255),
            b.toInt().coerceIn(0, 255),
            (a.toFloat().coerceIn(0f, 1f) * 255).toInt()
        )
    }

    /**
     * Convert Compose Color to hex string (#RRGGBB or #RRGGBBAA)
     */
    fun colorToHex(color: Color, includeAlpha: Boolean = false): String {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)

        return if (includeAlpha && color.alpha < 1f) {
            val a = (color.alpha * 255).toInt().coerceIn(0, 255)
            String.format("#%02X%02X%02X%02X", r, g, b, a)
        } else {
            String.format("#%02X%02X%02X", r, g, b)
        }
    }

    /**
     * Convert Compose Color to rgba() CSS string
     */
    fun colorToRgba(color: Color): String {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        val a = color.alpha.coerceIn(0f, 1f)

        return if (a >= 1f) {
            "rgb($r, $g, $b)"
        } else {
            "rgba($r, $g, $b, ${"%.2f".format(a)})"
        }
    }

    /**
     * Parse box-shadow CSS value
     */
    fun parseBoxShadow(value: String): BoxShadowValues {
        if (value.isBlank() || value == "none") {
            return BoxShadowValues()
        }

        val match = BOX_SHADOW_PATTERN.find(value) ?: return BoxShadowValues()

        return try {
            BoxShadowValues(
                offsetX = match.groupValues[1].toIntOrNull() ?: 0,
                offsetY = match.groupValues[2].toIntOrNull() ?: 4,
                blurRadius = match.groupValues[3].toIntOrNull() ?: 8,
                spreadRadius = match.groupValues[4].toIntOrNull() ?: 0,
                color = match.groupValues[5].ifBlank { "rgba(0,0,0,0.1)" }
            )
        } catch (e: Exception) {
            BoxShadowValues()
        }
    }

    /**
     * Build box-shadow CSS value from components
     */
    fun buildBoxShadow(
        offsetX: Int = 0,
        offsetY: Int = 4,
        blurRadius: Int = 8,
        spreadRadius: Int = 0,
        color: String = "rgba(0,0,0,0.1)"
    ): String {
        return "${offsetX}px ${offsetY}px ${blurRadius}px ${spreadRadius}px $color"
    }

    /**
     * Extract color from a CSS value string (e.g., box-shadow, border)
     */
    fun extractColor(value: String): String? {
        return COLOR_IN_VALUE_PATTERN.find(value)?.value
    }

    /**
     * Parse CSS shorthand padding/margin value
     * @return List of values [top, right, bottom, left]
     */
    fun parseSpacingShorthand(value: String): List<Int> {
        val values = value.split(Regex("\\s+"))
            .mapNotNull { parsePixelValue(it)?.toInt() }

        return when (values.size) {
            1 -> listOf(values[0], values[0], values[0], values[0]) // all sides
            2 -> listOf(values[0], values[1], values[0], values[1]) // vertical, horizontal
            3 -> listOf(values[0], values[1], values[2], values[1]) // top, horizontal, bottom
            4 -> values // top, right, bottom, left
            else -> listOf(0, 0, 0, 0)
        }
    }

    /**
     * Build CSS shorthand spacing value from individual values
     */
    fun buildSpacingShorthand(top: Int, right: Int, bottom: Int, left: Int): String {
        return when {
            top == right && right == bottom && bottom == left -> "${top}px"
            top == bottom && left == right -> "${top}px ${right}px"
            left == right -> "${top}px ${right}px ${bottom}px"
            else -> "${top}px ${right}px ${bottom}px ${left}px"
        }
    }

    /**
     * Parse border shorthand value
     * @return Triple of (width, style, color) or null
     */
    fun parseBorder(value: String): Triple<String, String, String>? {
        if (value.isBlank() || value == "none") return null

        val parts = value.split(Regex("\\s+"))
        if (parts.size < 2) return null

        val width = parts.firstOrNull { it.contains("px") } ?: "1px"
        val style = parts.firstOrNull { it in BORDER_STYLES } ?: "solid"
        val color = parts.firstOrNull { it.startsWith("#") || it.startsWith("rgb") } ?: "#000000"

        return Triple(width, style, color)
    }

    /**
     * Build border CSS shorthand
     */
    fun buildBorder(width: String, style: String, color: String): String {
        return "$width $style $color"
    }

    /**
     * List of valid CSS border styles
     */
    val BORDER_STYLES = listOf(
        "none", "solid", "dashed", "dotted", "double",
        "groove", "ridge", "inset", "outset"
    )

    /**
     * Common CSS display values
     */
    val DISPLAY_VALUES = listOf(
        "block", "inline", "inline-block", "flex", "inline-flex",
        "grid", "inline-grid", "none", "contents"
    )

    /**
     * Common CSS position values
     */
    val POSITION_VALUES = listOf(
        "static", "relative", "absolute", "fixed", "sticky"
    )

    /**
     * Common CSS text-align values
     */
    val TEXT_ALIGN_VALUES = listOf(
        "left", "center", "right", "justify"
    )

    /**
     * Common CSS font-weight values
     */
    val FONT_WEIGHT_VALUES = listOf(
        "normal", "bold", "lighter", "bolder",
        "100", "200", "300", "400", "500", "600", "700", "800", "900"
    )

    /**
     * Important CSS properties for element inspection
     */
    val IMPORTANT_STYLE_PROPERTIES = listOf(
        "display", "position", "width", "height", "margin", "padding",
        "background", "color", "font-size", "font-weight", "border",
        "border-radius", "box-shadow", "opacity", "z-index", "flex",
        "grid", "transform", "transition"
    )

    /**
     * Filter styles map to only include important/relevant properties
     */
    fun filterImportantStyles(styles: Map<String, String>): Map<String, String> {
        return styles.filter { (key, value) ->
            IMPORTANT_STYLE_PROPERTIES.any { key.contains(it, ignoreCase = true) } &&
                    value.isNotBlank() &&
                    value != "none" &&
                    value != "0px" &&
                    value != "normal" &&
                    value != "auto"
        }
    }
}
