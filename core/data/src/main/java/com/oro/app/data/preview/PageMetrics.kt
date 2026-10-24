package com.oro.app.data.preview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Layout facts measured inside a rendered page, and the text the agent reads
 * under each screenshot. Shared by the WebView previewer and the live eval so
 * both say exactly the same thing.
 */
data class PageMetrics(
    val viewportWidth: Int = 0,
    val scrollWidth: Int = 0,
    val scrollHeight: Int = 0,
    /** The outermost boxes that stick out past the right edge without being clipped. */
    val wide: List<String> = emptyList(),
    val smallTargets: Int = 0,
    /** A few of the small targets, as `selector "text" WxH`. */
    val smallTargetExamples: List<String> = emptyList(),
    val smallText: Int = 0,
    val smallTextExamples: List<String> = emptyList(),
    /** Text below WCAG AA contrast against its measured background. */
    val lowContrast: Int = 0,
    val lowContrastExamples: List<String> = emptyList(),
    val brokenImages: Int = 0,
    val hiddenShown: Int = 0,
    val title: String = "",
) {
    fun describe(): String = buildString {
        append("CSS viewport ").append(viewportWidth).append("px, page ").append(scrollWidth).append("x").append(scrollHeight).append("px.")
        if (scrollWidth > viewportWidth + 1) {
            append("\nHORIZONTAL OVERFLOW: the page is wider than the screen.")
            if (wide.isEmpty()) append(" No element box reaches past the edge, so look for ::before/::after decorations, negative margins or 100vw widths.")
        }
        // The outermost unclipped boxes past the right edge: what actually makes the page scroll sideways.
        if (wide.isNotEmpty()) append("\nElements reaching past the right edge: ").append(wide.joinToString(", "))
        // Name the elements: a bare count sent models resizing icons that were never the problem.
        // Measured at touch widths only; a mouse needs no 40px target.
        if (smallTargets > 0) {
            append("\n").append(smallTargets).append(" tap targets too small for a finger (aim for 40x40px, never under 32px)")
            examples(smallTargetExamples)
            append('.')
        }
        if (smallText > 0) {
            append("\n").append(smallText).append(" text elements under 12px")
            examples(smallTextExamples)
            append('.')
        }
        // Often a header button whose text took the nav link colour: invisible in a description, obvious on screen.
        if (lowContrast > 0) {
            append("\n").append(lowContrast).append(" text elements with low contrast")
            examples(lowContrastExamples)
            append('.')
        }
        if (brokenImages > 0) append("\n").append(brokenImages).append(" images failed to load.")
        if (hiddenShown > 0) append("\n").append(hiddenShown)
            .append(" elements have the hidden attribute but are displayed: a CSS display rule overrides it ([hidden]{display:none!important} fixes it).")
        if (title.isNotBlank()) append("\nTitle: ").append(title)
    }

    private fun StringBuilder.examples(list: List<String>) {
        if (list.isNotEmpty()) append(", e.g. ").append(list.joinToString(", "))
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads the object the measuring script returns; null when it is not one. */
        fun parse(raw: String): PageMetrics? = runCatching { from(json.parseToJsonElement(raw).jsonObject) }.getOrNull()

        fun from(o: JsonObject): PageMetrics {
            fun i(k: String) = (o[k] as? JsonPrimitive)?.intOrNull ?: 0
            fun l(k: String) = (o[k] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            return PageMetrics(
                viewportWidth = i("vw"), scrollWidth = i("sw"), scrollHeight = i("sh"), wide = l("wide"),
                smallTargets = i("smallTargets"), smallTargetExamples = l("smallTargetsAt"),
                smallText = i("smallText"), smallTextExamples = l("smallTextAt"),
                lowContrast = i("lowContrast"), lowContrastExamples = l("lowContrastAt"),
                brokenImages = i("brokenImages"), hiddenShown = i("hiddenShown"),
                title = (o["title"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            )
        }
    }
}
