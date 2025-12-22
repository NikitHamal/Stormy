package com.codex.stormy.ui.components.inspector

import androidx.compose.ui.graphics.Color

/**
 * Utility functions for the Visual Element Inspector.
 * Provides CSS parsing and selector building helpers.
 */
object InspectorUtils {

    /**
     * Build a CSS selector from element data
     * Prioritizes ID, then classes (max 2), with tag name prefix
     */
    fun buildSelector(element: InspectorElementData): String {
        val sb = StringBuilder(element.tagName.lowercase())
        if (element.id.isNotEmpty()) {
            sb.append("#${element.id}")
        }
        if (element.className.isNotEmpty()) {
            element.className.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .forEach { className ->
                    sb.append(".$className")
                }
        }
        return sb.toString()
    }

    /**
     * Build a CSS selector from basic element properties
     */
    fun buildSelector(tagName: String, id: String, className: String): String {
        val sb = StringBuilder(tagName.lowercase())
        if (id.isNotEmpty()) {
            sb.append("#$id")
        }
        if (className.isNotEmpty()) {
            className.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .forEach { cn ->
                    sb.append(".$cn")
                }
        }
        return sb.toString()
    }

    /**
     * Parse a CSS pixel value to Float
     * @param value CSS value like "16px", "auto", etc.
     * @return The numeric value or 0f if not parseable
     */
    fun parsePixelValue(value: String?): Float {
        if (value == null) return 0f
        return value.replace("px", "").toFloatOrNull() ?: 0f
    }

    /**
     * Parse line height value (can be unitless, px, or em)
     */
    fun parseLineHeight(value: String?): Float {
        if (value == null || value == "normal") return 1.5f
        return value.replace("px", "").replace("em", "").toFloatOrNull() ?: 1.5f
    }

    /**
     * Parse a CSS color string to Compose Color
     * Supports hex (#RGB, #RRGGBB, #RRGGBBAA) and rgb/rgba formats
     */
    fun parseColor(colorString: String): Color {
        return try {
            when {
                colorString.startsWith("#") -> parseHexColor(colorString)
                colorString.startsWith("rgb") -> parseRgbColor(colorString)
                colorString == "transparent" -> Color.Transparent
                else -> Color.Transparent
            }
        } catch (e: Exception) {
            Color.Transparent
        }
    }

    /**
     * Parse hex color string
     */
    private fun parseHexColor(hex: String): Color {
        val cleanHex = hex.removePrefix("#")
        return when (cleanHex.length) {
            3 -> {
                val r = cleanHex[0].toString().repeat(2).toInt(16)
                val g = cleanHex[1].toString().repeat(2).toInt(16)
                val b = cleanHex[2].toString().repeat(2).toInt(16)
                Color(r, g, b)
            }
            6 -> Color(android.graphics.Color.parseColor(hex))
            8 -> Color(android.graphics.Color.parseColor(hex))
            else -> Color.Transparent
        }
    }

    /**
     * Parse rgb/rgba color string
     */
    private fun parseRgbColor(rgb: String): Color {
        val values = rgb
            .replace(Regex("rgba?\\("), "")
            .replace(")", "")
            .split(",")
            .map { it.trim() }

        if (values.size >= 3) {
            val r = values[0].toIntOrNull() ?: 0
            val g = values[1].toIntOrNull() ?: 0
            val b = values[2].toIntOrNull() ?: 0
            val a = if (values.size >= 4) {
                (values[3].toFloatOrNull() ?: 1f).coerceIn(0f, 1f)
            } else 1f

            return Color(
                red = r.coerceIn(0, 255),
                green = g.coerceIn(0, 255),
                blue = b.coerceIn(0, 255),
                alpha = (a * 255).toInt().coerceIn(0, 255)
            )
        }
        return Color.Transparent
    }

    /**
     * Convert Compose Color to hex string
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
     * Format pixel value for CSS
     */
    fun formatPixelValue(value: Number): String = "${value.toInt()}px"

    /**
     * Check if element is an image
     */
    fun isImageElement(element: InspectorElementData): Boolean {
        return element.tagName.equals("IMG", ignoreCase = true)
    }

    /**
     * Extract text content from innerHTML (strips HTML tags)
     */
    fun extractTextContent(innerHTML: String, maxLength: Int = 100): String {
        return innerHTML
            .replace(Regex("<[^>]*>"), "")
            .trim()
            .take(maxLength)
    }
}
