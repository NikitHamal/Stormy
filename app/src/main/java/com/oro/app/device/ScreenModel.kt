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

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
}

/**
 * A slider's or progress bar's value as the app reports it (RangeInfo), in
 * the app's own units: 35 on 0..100, 0.5 on 0..1, or a percentage.
 */
data class ValueRange(val min: Float, val max: Float, val current: Float, val percent: Boolean = false) {

    /** Where [current] sits between [min] and [max], 0..1. */
    val fraction: Float get() = if (max > min) ((current - min) / (max - min)).coerceIn(0f, 1f) else 0f

    /** `35/100`, `35%`, or `3 of -10..10` when the range does not start at 0. */
    fun render(): String = when {
        percent -> num(current) + "%"
        min == 0f -> num(current) + "/" + num(max)
        else -> num(current) + " of " + num(min) + ".." + num(max)
    }

    companion object {
        /** 35, 0.5, 33.33: no trailing zeros, so a whole number reads as one. */
        fun num(v: Float): String {
            val rounded = Math.round(v)
            if (kotlin.math.abs(v - rounded) < 0.005f) return rounded.toString()
            return String.format(java.util.Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.')
        }
    }
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
    /**
     * Characters in a password field (the platform reports them masked). Never
     * shown to the model beyond filled/empty; it is in the signature so typing
     * a password registers as a change.
     */
    val secretLength: Int = 0,
    /** A scrollable that can still move back (up, or left when [horizontal]). */
    val canScrollBack: Boolean = false,
    /** A scrollable that can still move forward (down, or right when [horizontal]). */
    val canScrollForward: Boolean = false,
    /** Scrolls sideways (carousel, tab strip, pager) rather than up and down. */
    val horizontal: Boolean = false,
    /** A slider's or progress bar's value, when the app reports one. */
    val range: ValueRange? = null,
) {
    val centerX: Int get() = bounds.centerX
    val centerY: Int get() = bounds.centerY
    val actionable: Boolean get() = clickable || longClickable || editable || scrollable || checkable

    /** Whether a tap here does something by itself (as opposed to reaching a container). */
    val takesTaps: Boolean get() = clickable || longClickable || editable || checkable

    /**
     * The model's line for this element.
     *
     * Compact on purpose -- every observation carries the whole screen, so
     * each byte here is paid on every turn -- but still words, not codes: weak
     * models read `[4] button "Search"` reliably and misread clever
     * abbreviations. Plain texts drop the role (most of a screen is text),
     * flags are single words, and the window tag appears only off the main app.
     */
    fun render(where: String? = null, underKeyboard: Boolean = false): String = buildString {
        append('[').append(index).append(']')
        if (role != ROLE_TEXT) append(' ').append(role)
        if (label.isNotBlank()) append(" \"").append(label).append('"')
        // An icon with no name at all: where it sits is what the model can go
        // on ("the ⋮ menu top right"), better than a bare number.
        else if (where != null) append(" (").append(where).append(')')
        if (editable) {
            // The label is the field's name (hint, description); what is typed
            // in it is always shown, so the model never reads a filled field
            // as empty and types into it again.
            when {
                password -> append(if (secretLength > 0) " password filled" else " password empty")
                !value.isNullOrEmpty() -> append(" = \"").append(value.take(80)).append('"')
                else -> append(" empty")
            }
        }
        if (checkable) append(if (checked) " on" else " off")
        range?.let { append(' ').append(it.render()) }
        // Whether there is more to see, so "the end of the list" is read off
        // the screen instead of found by a wasted scroll.
        val more = scrollWords()
        if (more != null) append(' ').append(more)
        else if (scrollable && role != ROLE_LIST) append(" scrollable")
        if (selected) append(" selected")
        if (focused && editable) append(" focused")
        if (!enabled) append(" disabled")
        if (underKeyboard) append(" under keyboard")
        if (longClickable && !clickable) append(" long-press")
        if (window != null) append(" @").append(window)
    }.trimEnd()

    /** "more below", "more above and below", "more to the right"…; null when nothing is known. */
    fun scrollWords(): String? {
        if (!scrollable || !(canScrollBack || canScrollForward)) return null
        val (back, fwd) = if (horizontal) "to the left" to "to the right" else "above" to "below"
        return when {
            canScrollBack && canScrollForward -> if (horizontal) "more to the left and right" else "more above and below"
            canScrollForward -> "more $fwd"
            else -> "more $back"
        }
    }

    /**
     * Whether scrolling this way can show anything new: down/right is forward,
     * up/left is back. Null when unknown -- including a direction across this
     * element's axis (left on a vertical feed, down on a carousel): its
     * capacity says nothing about that.
     */
    fun canScroll(direction: String): Boolean? {
        if (!scrollable || !(canScrollBack || canScrollForward)) return null
        return when (direction.lowercase()) {
            "down" -> if (horizontal) null else canScrollForward
            "up" -> if (horizontal) null else canScrollBack
            "right" -> if (horizontal) canScrollForward else null
            "left" -> if (horizontal) canScrollBack else null
            "forward" -> canScrollForward
            "back", "backward" -> canScrollBack
            else -> null
        }
    }

    companion object {
        const val ROLE_TEXT = "text"
        const val ROLE_LIST = "list"
        const val ROLE_PROGRESS = "progress"
        const val ROLE_SLIDER = "slider"
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
    /** Top edge of the on-screen keyboard while it is open; what lies below it cannot be tapped. */
    val keyboardTop: Int? = null,
) {
    /** Whether [e] sits behind the open keyboard (a tap there would press a key). */
    fun underKeyboard(e: ScreenElement): Boolean = keyboardTop != null && e.window == null && e.centerY >= keyboardTop

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
            // A playing video's seek bar moves by itself every second: that is not a change.
            val ticking = Sliders.isSeekBar(e)
            h = 31 * h + e.role.hashCode()
            h = 31 * h + (if (ticking) 0 else e.label.hashCode())
            h = 31 * h + (e.value?.hashCode() ?: 0)
            h = 31 * h + (if (e.checked) 1 else 0) + (if (e.selected) 2 else 0)
            h = 31 * h + e.secretLength
            h = 31 * h + (if (e.canScrollBack) 4 else 0) + (if (e.canScrollForward) 8 else 0)
            h = 31 * h + (if (ticking) 0 else e.range?.current?.toBits() ?: 0)
            h = 31 * h + e.bounds.top / 40
        }
        31 * h + (if (keyboardVisible) 1 else 0)
    }

    fun element(index: Int): ScreenElement? =
        elements.getOrNull(index)?.takeIf { it.index == index } ?: elements.firstOrNull { it.index == index }

    /**
     * What a finger at (x, y) would press: the smallest element there that
     * takes taps, in the top-most window that has anything at that point
     * (a dialog covers the app under it). Null over empty space.
     */
    fun hitTest(x: Int, y: Int): ScreenElement? =
        topLayerAt(x, y).filter { it.takesTaps }.minWithOrNull(SMALLEST_LAST)

    /** The text under (x, y) in the top-most window, if any: what the user would read there. */
    fun textAt(x: Int, y: Int): ScreenElement? =
        topLayerAt(x, y).filter { it.label.isNotBlank() }.minWithOrNull(SMALLEST_LAST)

    private fun topLayerAt(x: Int, y: Int): List<ScreenElement> {
        val here = elements.filter { it.bounds.contains(x, y) }
        if (here.isEmpty()) return here
        // Windows are listed bottom to top, so the last element here is in the top one.
        val top = here.last().window
        return here.filter { it.window == top }
    }

    /**
     * The element that really receives a tap on [e]: [e] itself when it takes
     * taps, else the smallest clickable element of the same window around its
     * centre (a price text inside a "Buy now" card is a tap on the card).
     */
    fun receiverFor(e: ScreenElement, long: Boolean = false): ScreenElement {
        val takes = if (long) e.longClickable || e.clickable else e.takesTaps
        if (takes) return e
        return elements.asSequence()
            .filter { it !== e && it.window == e.window && it.bounds.contains(e.centerX, e.centerY) }
            .filter { if (long) it.longClickable || it.clickable else it.clickable }
            .minWithOrNull(SMALLEST_LAST)
            ?: e
    }

    /**
     * The list a bare "scroll" moves: the largest vertically scrolling element
     * of the app's own window (not a dialog or popup over it).
     */
    fun mainList(): ScreenElement? = listFor("down")

    /**
     * The list a scroll [direction] with no index moves: the largest element
     * of the app's own window that scrolls on that axis -- a carousel for
     * left/right, the feed for up/down.
     */
    fun listFor(direction: String): ScreenElement? {
        val sideways = direction.lowercase().let { it == "left" || it == "right" }
        return elements.asSequence()
            .filter { it.scrollable && it.horizontal == sideways && it.window == null }
            .maxByOrNull { it.bounds.area }
    }

    /** Whether (x, y) lies behind the open keyboard, where a tap presses a key. */
    fun underKeyboard(x: Int, y: Int): Boolean = keyboardTop != null && y >= keyboardTop

    /** Where [e] sits, in words: "top right", "bottom", "middle"… */
    fun whereIs(e: ScreenElement): String {
        val col = when {
            e.centerX < width / 3 -> "left"
            e.centerX > width * 2 / 3 -> "right"
            else -> null
        }
        val row = when {
            e.centerY < height / 4 -> "top"
            e.centerY > height * 3 / 4 -> "bottom"
            else -> null
        }
        return when {
            row != null && col != null -> "$row $col"
            row != null -> row
            col != null -> col
            else -> "middle"
        }
    }

    /**
     * Whether the elements of [window] form something laid over the app: a
     * dialog or system window, or a window whose content covers well under the
     * whole screen (an in-app dialog or bottom sheet is the active window too).
     */
    private fun overlayLike(window: String?): Boolean {
        if (window == WINDOW_DIALOG || window == WINDOW_SYSTEM) return true
        val inWindow = elements.filter { it.window == window }
        if (inWindow.isEmpty() || height <= 0) return false
        val top = inWindow.minOf { it.bounds.top }
        val bottom = inWindow.maxOf { it.bounds.bottom }
        return bottom - top < height * 3 / 4
    }

    private fun isCredentialField(e: ScreenElement): Boolean {
        val id = e.viewId.orEmpty().lowercase()
        if (URL_BAR_ID.containsMatchIn(id)) return false
        val name = e.label.lowercase()
        return CREDENTIAL.containsMatchIn(name) || CREDENTIAL.containsMatchIn(id.substringAfter(":id/").replace('_', ' '))
    }

    /**
     * A human check (captcha) waiting for the user. It is a widget (a
     * checkbox, a button, a short label), not any text that mentions one: an
     * email about a "security check" or an article about captchas is not a
     * human check. reCAPTCHA's always-on "protected by" badge is not one
     * either, and a check box already ticked has been passed.
     */
    val humanCheck: Boolean
        get() = elements.any { e ->
            val label = e.label.lowercase()
            (e.checkable || e.label.length <= MAX_WIDGET_LABEL) && HUMAN_CHECK.containsMatchIn(label) &&
                !CAPTCHA_BADGE.containsMatchIn(label) && !(e.checkable && e.checked)
        }

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
        if (humanCheck) {
            out += "A human check (captcha) is on screen. Never try to solve it: it is handed to the user, and the run " +
                "carries on by itself once they pass it. Do not reload the page for an easier one."
        }
        // A sign-in wall has a field for credentials, not just an editable box
        // (every browser page has the address bar) and a "Sign in" link.
        val credentials = elements.any { e -> e.password || (e.editable && e.window == null && isCredentialField(e)) }
        if (elements.any { it.password } || (credentials && labels.any { SIGN_IN.matches(it) }) ||
            (labels.any { SSO.containsMatchIn(it) } && labels.any { SIGN_IN.matches(it) || SIGN_IN_PROMPT.containsMatchIn(it) })
        ) {
            // Name the way past it only when there is one: told to "look for
            // Not now/Skip", a model in the live eval hunted for them for eight
            // turns and then tapped "Create new account".
            val skip = elements.firstOrNull { it.actionable && DISMISS.matches(it.label.lowercase().trim()) }
            out += "This screen asks to sign in. Use only credentials the user gave you (never invent them, never create an account). " +
                if (skip != null) "If the task does not need an account, \"${skip.label.take(30)}\" skips it."
                else "Nothing here skips it: if the task needs the account, ask_user, or finish_task(success=false) saying so."
        }
        elements.firstOrNull { ERROR_STATE.matches(it.label.lowercase().trim()) }?.let { e ->
            out += "The app shows an error (\"${e.label.take(60)}\"): try its Retry/Try again once; device_status shows whether the phone is online."
        }
        // A nag is a dialog or sheet over the app: the same words on a full
        // screen (a store page's "Update" header) are the app itself.
        elements.firstOrNull { NAG.matches(it.label.lowercase().trim()) && overlayLike(it.window) }?.let { nag ->
            elements.firstOrNull {
                it.window == nag.window && it.actionable && DISMISS.matches(it.label.lowercase().trim())
            }?.let { d ->
                out += "A prompt (rating, update, notifications, tips) is in the way: dismiss it with \"${d.label.take(30)}\" unless the task needs it."
            }
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
            val e = elements[i]
            val line = e.render(
                where = if (e.label.isBlank() && (e.takesTaps || e.role == ScreenElement.ROLE_SLIDER) && e.role != ScreenElement.ROLE_LIST) whereIs(e) else null,
                underKeyboard = underKeyboard(e),
            )
            if (length + line.length > maxChars) {
                append("… ").append(elements.size - i).append(" more not shown; scroll or use find_text.\n")
                break
            }
            append(line).append('\n')
        }
        if (truncated > 0) append("(").append(truncated).append(" minor elements omitted)\n")
    }

    companion object {
        /** Smallest area first; among equals the later one, which is drawn on top. */
        private val SMALLEST_LAST = compareBy<ScreenElement> { it.bounds.area }.thenByDescending { it.index }

        const val WINDOW_SYSTEM = "system"
        const val WINDOW_DIALOG = "dialog"
        const val WINDOW_POPUP = "popup"

        private val CONSENT = Regex(
            "\\b(accept all|reject all|accept cookies|allow all cookies|manage cookies|cookie settings|we use cookies|only necessary|necessary cookies only)\\b"
        )
        private val PERMISSION = Regex(
            "^(allow|don.t allow|deny|while using the app|only this time|allow all the time|allow only while using the app)$"
        )
        // Slider puzzles too: set_value could move them, and they are the user's to pass.
        private val HUMAN_CHECK = Regex(
            "\\b(i.m not a robot|i am human|verify (that )?you are (a )?human|are you a robot|captcha|human verification|" +
                "unusual traffic|enter the characters you see|type the characters|select all (images|squares)|" +
                "slide to verify|swipe to verify|slide to complete the puzzle|drag the puzzle piece)\\b"
        )
        private val CAPTCHA_BADGE = Regex("protected by recaptcha|recaptcha.{0,3}(privacy|terms)|privacy\\s*-\\s*terms")
        /** Longest label read as a widget's own (a checkbox's, a button's) rather than content. */
        private const val MAX_WIDGET_LABEL = 50
        private val CREDENTIAL = Regex("\\b(e-?mail|user ?name|login|phone|mobile|password|passcode)\\b")
        private val URL_BAR_ID = Regex("url|omnibox|location_bar|address_bar|search_src|search_box")
        private val SIGN_IN = Regex("^(sign in|log in|login)$")
        private val SIGN_IN_PROMPT = Regex("\\b(sign in to|log in to|sign in or|log in or)\\b")
        private val SSO = Regex("\\bcontinue with (google|facebook|apple|microsoft)\\b")
        private val ERROR_STATE = Regex(
            "^(no internet( connection)?|you.re offline|you are offline|no (network )?connection|connection (lost|failed|error)|" +
                "something went wrong.*|couldn.t (load|connect|refresh).*|unable to (load|connect).*|check your (internet|network|connection).*|" +
                "network error.*|server error.*|page not found|this site can.t be reached.*)$"
        )
        private val NAG = Regex(
            "^(rate (us|this app|the app).*|enjoying .{1,40}\\?|update (now|available|the app)|a new version is available.*|" +
                "turn on notifications\\??|allow notifications\\??|get notified.*|get started|let.s get started|take a tour)$"
        )
        // Not "cancel": on a download or an order it undoes what is in progress.
        private val DISMISS = Regex("^(not now|no thanks|no, thanks|later|maybe later|skip|skip for now|dismiss|close|remind me later)$")
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
