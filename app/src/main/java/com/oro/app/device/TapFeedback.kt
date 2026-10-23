package com.oro.app.device

/** A click the app itself reported (TYPE_VIEW_CLICKED): when (uptime), on what, and where. */
data class TapHit(val at: Long, val role: String, val label: String, val bounds: Bounds?)

/**
 * What a tap by coordinates hit, in words for the model. A gesture lands
 * wherever the finger lands; the app's own click event says whether a control
 * took it. Pure, for tests.
 */
internal object TapFeedback {

    /** `→ hit button "OK"`, or that nothing took the tap. */
    fun line(hit: TapHit?): String = when {
        hit == null -> "→ nothing registered the tap"
        hit.label.isBlank() -> "→ hit ${hit.role}"
        else -> "→ hit ${hit.role} \"${hit.label}\""
    }

    /** The listing's role words for a clicked view's class. */
    fun roleOf(className: String?): String {
        val c = className.orEmpty()
        return when {
            c.contains("EditText") -> "input"
            c.contains("CheckBox") -> "checkbox"
            c.contains("Switch") || c.contains("ToggleButton") -> "switch"
            c.contains("RadioButton") -> "radio"
            c.contains("Image") -> "icon"
            c.contains("Button") -> "button"
            c.contains("Tab") && !c.contains("Table") -> "tab"
            c.contains("TextView") -> "text"
            else -> "view"
        }
    }

    /** The first of [texts] with words in it, on one line and short. */
    fun label(vararg texts: String?): String {
        val t = texts.firstOrNull { !it.isNullOrBlank() }.orEmpty().replace(Regex("\\s+"), " ").trim()
        return if (t.length <= MAX_LABEL) t else t.take(MAX_LABEL - 1).trimEnd() + "…"
    }

    /**
     * Whether [hit] can be the tap at ([x], [y]): its view holds the point,
     * give or take [slop]. A view that left with the click (it opened another
     * screen) has no bounds left to check, and counts.
     */
    fun near(hit: TapHit, x: Int, y: Int, slop: Int): Boolean {
        val b = hit.bounds ?: return true
        return x in b.left - slop until b.right + slop && y in b.top - slop until b.bottom + slop
    }

    private const val MAX_LABEL = 40
}
