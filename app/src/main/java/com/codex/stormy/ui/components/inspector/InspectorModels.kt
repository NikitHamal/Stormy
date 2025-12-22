package com.codex.stormy.ui.components.inspector

/**
 * Data models for the Visual Element Inspector.
 * Centralized models used across inspector components.
 */

/**
 * Style change request for AI agent or direct editing
 */
data class StyleChangeRequest(
    val selector: String,
    val property: String,
    val oldValue: String?,
    val newValue: String,
    val elementHtml: String
)

/**
 * Text content change request
 */
data class TextChangeRequest(
    val selector: String,
    val oldText: String,
    val newText: String,
    val elementHtml: String
)

/**
 * Image source change request
 */
data class ImageChangeRequest(
    val selector: String,
    val oldSrc: String?,
    val newSrc: String,
    val elementHtml: String
)

/**
 * Inspected element data from WebView
 */
data class InspectorElementData(
    val tagName: String,
    val id: String,
    val className: String,
    val innerHTML: String,
    val outerHTML: String,
    val computedStyles: Map<String, String>,
    val attributes: Map<String, String>,
    val boundingRect: InspectorRect
)

/**
 * Element bounding rectangle
 */
data class InspectorRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

/**
 * Selected element data for agent mode
 */
data class AgentSelectedElement(
    val selector: String,
    val outerHTML: String
)

/**
 * Box shadow CSS values
 */
data class BoxShadowValues(
    val x: Int = 0,
    val y: Int = 0,
    val blur: Int = 0,
    val spread: Int = 0,
    val color: String = "rgba(0,0,0,0.2)"
) {
    fun toCssString(): String {
        return if (x == 0 && y == 0 && blur == 0 && spread == 0) {
            "none"
        } else {
            "${x}px ${y}px ${blur}px ${spread}px $color"
        }
    }

    companion object {
        /**
         * Parse box shadow CSS value into component values
         */
        fun parse(boxShadow: String?): BoxShadowValues {
            if (boxShadow == null || boxShadow == "none" || boxShadow.isBlank()) {
                return BoxShadowValues()
            }

            // Extract numeric values (px values)
            val pixelPattern = Regex("""(-?\d+)px""")
            val matches = pixelPattern.findAll(boxShadow).toList()

            // Extract color (rgba, rgb, or hex)
            val colorPattern = Regex("""(rgba?\([^)]+\)|#[0-9a-fA-F]{3,8})""")
            val colorMatch = colorPattern.find(boxShadow)?.value ?: "rgba(0,0,0,0.2)"

            return BoxShadowValues(
                x = matches.getOrNull(0)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                y = matches.getOrNull(1)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                blur = matches.getOrNull(2)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                spread = matches.getOrNull(3)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                color = colorMatch
            )
        }

        /**
         * Get a specific component value from box shadow string
         * @param index 0=x, 1=y, 2=blur, 3=spread
         */
        fun getComponent(boxShadow: String?, index: Int): Float {
            val values = parse(boxShadow)
            return when (index) {
                0 -> values.x.toFloat()
                1 -> values.y.toFloat()
                2 -> values.blur.toFloat()
                3 -> values.spread.toFloat()
                else -> 0f
            }
        }
    }
}
