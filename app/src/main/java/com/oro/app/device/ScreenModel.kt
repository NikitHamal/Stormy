package com.oro.app.device

/**
 * A rectangle on screen, in pixels.
 *
 * Plain Kotlin rather than android.graphics.Rect so everything about how the
 * screen is described to the model -- rendering, matching, change detection --
 * runs in JVM unit tests, where Rect is a stub that keeps no coordinates.
 */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val area: Long get() = width.toLong().coerceAtLeast(0) * height.toLong().coerceAtLeast(0)

    fun overlaps(o: Bounds): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

/** One thing on screen the agent can see and refer to by [index]. */
data class ScreenElement(
    val index: Int,
    val role: String,
    val label: String,
    val bounds: Bounds,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val selected: Boolean = false,
    val focused: Boolean = false,
    val enabled: Boolean = true,
    val password: Boolean = false,
    val value: String? = null,
    val viewId: String? = null,
    val window: String? = null,
) {
    val centerX: Int get() = bounds.centerX
    val centerY: Int get() = bounds.centerY
    val actionable: Boolean get() = clickable || longClickable || editable || scrollable || checkable

    /**
     * The model's line for this element.
     *
     * Compact on purpose -- every observation carries the whole screen, so
     * each byte here is paid on every turn -- but still words, not codes: weak
     * models read `[4] button "Search"` reliably and misread clever
     * abbreviations. Plain texts drop the role (most of a screen is text),
     * flags are single words, and the window tag appears only off the main app.
     */
    fun render(): String = buildString {
        append('[').append(index).append("] ")
        if (role != ROLE_TEXT) append(role).append(' ')
        if (label.isNotBlank()) append('"').append(label).append('"')
        if (editable) {
            when {
                password -> append(" password")
                !value.isNullOrEmpty() && value != label -> append(" = \"").append(value.take(80)).append('"')
                else -> append(" empty")
            }
        }
        if (checkable) append(if (checked) " on" else " off")
        if (scrollable && role != ROLE_LIST) append(" scrollable")
        if (selected) append(" selected")
        if (focused && editable) append(" focused")
        if (!enabled) append(" disabled")
        if (longClickable && !clickable) append(" long-press")
        if (window != null) append(" @").append(window)
    }.trimEnd()

    companion object {
        const val ROLE_TEXT = "text"
        const val ROLE_LIST = "list"
        const val ROLE_PROGRESS = "progress"
    }
}

/** A snapshot of the device screen in a form any model can act on. */
data class ScreenState(
    val packageName: String,
    val appLabel: String,
    val width: Int,
    val height: Int,
    val keyboardVisible: Boolean,
    val elements: List<ScreenElement>,
    val truncated: Int,
    val capturedAt: Long = System.currentTimeMillis(),
) {
    /**
     * Cheap fingerprint to tell the model whether an action changed anything.
     *
     * A rolling hash over the fields that matter, rather than one long joined
     * string per capture: this runs after every action, on every element.
     * Vertical position is bucketed so a one-pixel settle does not read as a
     * change, while a real scroll does.
     */
    val signature: Int by lazy {
        var h = packageName.hashCode()
        for (e in elements) {
            h = 31 * h + e.role.hashCode()
            h = 31 * h + e.label.hashCode()
            h = 31 * h + (e.value?.hashCode() ?: 0)
            h = 31 * h + (if (e.checked) 1 else 0) + (if (e.selected) 2 else 0)
            h = 31 * h + e.bounds.top / 40
        }
        31 * h + (if (keyboardVisible) 1 else 0)
    }

    fun element(index: Int): ScreenElement? =
        elements.getOrNull(index)?.takeIf { it.index == index } ?: elements.firstOrNull { it.index == index }

    /** True when something on screen says it is still loading. */
    val loading: Boolean get() = elements.any { it.role == ScreenElement.ROLE_PROGRESS }

    /**
     * Short, factual nudges about what is on screen that small models
     * routinely miss: a consent banner blocking the page, a permission
     * dialog, a spinner. Each costs a line and saves a wasted turn.
     */
    fun hints(adsHandled: Boolean): List<String> {
        val out = mutableListOf<String>()
        val labels = elements.map { it.label.lowercase() }
        if (labels.any { CONSENT.containsMatchIn(it) }) {
            out += "A cookie/consent banner is open: decline it (\"Reject all\"/\"Only necessary\") unless the task needs it."
        }
        if (elements.any { it.window == WINDOW_SYSTEM || it.window == WINDOW_DIALOG } &&
            labels.any { PERMISSION.matches(it) }
        ) {
            out += "A permission dialog is open: allow only what the task needs (prefer \"While using the app\" / \"Only this time\")."
        }
        if (loading) out += "Something is still loading: wait_for the content (or wait 2) before acting."
        if (adsHandled && labels.any { AD.containsMatchIn(it) }) {
            out += "An ad is showing; Oro taps \"Skip\" automatically as soon as it can be skipped."
        }
        return out
    }

    /**
     * The screen as the model reads it.
     *
     * Bounded by [maxChars] with an explicit count of what was cut, so the
     * model knows to scroll or search rather than assuming the list ended.
     */
    fun render(maxChars: Int = 6_000, adsHandled: Boolean = false): String = buildString {
        append("App: ").append(appLabel.ifBlank { packageName })
        if (keyboardVisible) append(" · keyboard open")
        append(" · ").append(width).append('x').append(height).append('\n')
        hints(adsHandled).forEach { append("Hint: ").append(it).append('\n') }
        if (elements.isEmpty()) {
            append("(No readable elements. The app may draw its own UI (game, video, map, canvas) or still be loading. ")
            append("Wait, scroll, use look if you can see images, or tap by x,y.)\n")
            return@buildString
        }
        for (i in elements.indices) {
            val line = elements[i].render()
            if (length + line.length > maxChars) {
                append("… ").append(elements.size - i).append(" more not shown; scroll or use find_text.\n")
                break
            }
            append(line).append('\n')
        }
        if (truncated > 0) append("(").append(truncated).append(" minor elements omitted)\n")
    }

    companion object {
        const val WINDOW_SYSTEM = "system"
        const val WINDOW_DIALOG = "dialog"
        const val WINDOW_POPUP = "popup"

        private val CONSENT = Regex(
            "\\b(accept all|reject all|accept cookies|allow all cookies|manage cookies|cookie settings|we use cookies|only necessary|necessary cookies only)\\b"
        )
        private val PERMISSION = Regex(
            "^(allow|don.t allow|deny|while using the app|only this time|allow all the time|allow only while using the app)$"
        )
        private val AD = Regex("^(ad|ads|sponsored)( ·|$)|\\bskip ads?\\b|\\bvisit advertiser\\b|\\bad \\d+ of \\d+\\b")

        /**
         * Drops elements that add nothing for the model: a plain text whose
         * words are already part of the nearest preceding element that
         * contains it (a button labelled "Search" with a child text
         * "Search"), and exact duplicates stacked on top of each other.
         * Indexes are renumbered so they stay dense and in reading order.
         */
        fun dedupe(raw: List<ScreenElement>): List<ScreenElement> {
            val kept = ArrayList<ScreenElement>(raw.size)
            for (e in raw) {
                val redundant = kept.asReversed().asSequence().take(6).any { k ->
                    when {
                        k.role == e.role && k.label == e.label && k.bounds == e.bounds -> true
                        e.role == ScreenElement.ROLE_TEXT && e.label.isNotBlank() && k.actionable &&
                            k.bounds.overlaps(e.bounds) && containsWords(k.label, e.label) -> true
                        else -> false
                    }
                }
                if (!redundant) kept += e
            }
            return kept.mapIndexed { i, e -> if (e.index == i) e else e.copy(index = i) }
        }

        private fun containsWords(container: String, part: String): Boolean {
            if (container.isBlank()) return false
            return container.lowercase().contains(part.lowercase().trim())
        }
    }
}
