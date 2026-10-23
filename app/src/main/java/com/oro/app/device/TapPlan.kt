package com.oro.app.device

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * How a tap reaches what the model meant. Pure, so every rule is tested on
 * the JVM.
 *
 * Two ways a tap used to miss:
 *  - A point given as x,y a little off a small control (models are rough
 *    with coordinates) landed beside it, or on its very edge where the app's
 *    touch area may already have ended. A point on or just beside one small
 *    plain control now taps that control by its node ([snap]).
 *  - A part that is not clickable itself inside a bigger clickable (a menu
 *    icon in a card, a like button in a row) was "clicked" through the whole
 *    card's click, which opens the card instead. Its clickable parent's click
 *    is used only when the parent is nearly the part itself (the label of a
 *    button); otherwise a finger goes where the part is ([choose]).
 */
internal object TapPlan {

    enum class Way {
        /** The element's own ACTION_CLICK. */
        CLICK_SELF,

        /** The click of the clickable parent around it, nearly the same box. */
        CLICK_PARENT,

        /** A real tap at the element's centre, as a finger would. */
        GESTURE,
    }

    /**
     * How to tap an element with [element]'s bounds: its own click when
     * [selfClickable]; its clickable parent's ([parent]) when that is nearly
     * the element itself, or when the element's centre is [covered] by
     * another window (a finger there would press that instead); else a tap
     * at its centre. With no clickable parent at all, a tap at the centre.
     */
    fun choose(selfClickable: Boolean, element: Bounds, parent: Bounds?, covered: Boolean): Way = when {
        selfClickable -> Way.CLICK_SELF
        parent == null -> Way.GESTURE
        covered -> Way.CLICK_PARENT
        element.area <= 0L -> Way.CLICK_PARENT
        parent.area <= element.area * TIGHT_PARENT -> Way.CLICK_PARENT
        else -> Way.GESTURE
    }

    /**
     * The control a tap at ([x], [y]) on [screen] was meant for, when the
     * point is on one small plain control ([plain]) or within a fingertip
     * ([slop]) of exactly one: tapped by its node, the exact way. Null when
     * the point is on something big or positional (a map, a player, a seek
     * bar, a text field), which keeps its point, or near nothing.
     */
    fun snap(screen: ScreenState, x: Int, y: Int, hit: ScreenElement? = screen.hitTest(x, y)): ScreenElement? {
        if (hit != null) return hit.takeIf { plain(it, screen) }
        val slop = slop(screen)
        val near = screen.elements.asSequence()
            .filter { it.takesTaps && plain(it, screen) && distance(it.bounds, x, y) <= slop }
            .sortedBy { distance(it.bounds, x, y) }
            .take(2)
            .toList()
        return when {
            near.isEmpty() -> null
            near.size == 1 -> near[0]
            // Two close by: only a clearly nearer one is meant.
            distance(near[0].bounds, x, y) * 2 <= distance(near[1].bounds, x, y) -> near[0]
            else -> null
        }
    }

    /**
     * A control a tap means the same wherever it lands: small, clickable or
     * checkable, not a field, a list, a slider or anything with a value
     * range (where the point is the value).
     */
    fun plain(e: ScreenElement, screen: ScreenState): Boolean {
        if (!(e.clickable || e.checkable) || e.editable || e.scrollable || e.range != null) return false
        if (e.role == ScreenElement.ROLE_LIST || e.role in POSITIONAL) return false
        val screenArea = screen.width.toLong() * screen.height
        if (screenArea <= 0L) return false
        return e.bounds.area * 100 <= screenArea * SMALL_PERCENT
    }

    /**
     * Whether a finger at [e]'s centre would press something else: the
     * smallest tappable thing there is neither [e] nor one of its containers
     * (a floating button over the row, a popup over the app).
     */
    fun covered(screen: ScreenState, e: ScreenElement): Boolean {
        val top = screen.hitTest(e.centerX, e.centerY) ?: return false
        return top.index != e.index && !encloses(top.bounds, e.bounds)
    }

    fun encloses(outer: Bounds, inner: Bounds): Boolean =
        outer.left <= inner.left && outer.top <= inner.top && outer.right >= inner.right && outer.bottom >= inner.bottom

    /** A fingertip's reach for a near miss: 2.5% of the screen's shorter side (about 9 dp). */
    fun slop(screen: ScreenState): Int = max(8, min(screen.width, screen.height) * 25 / 1000)

    /** How far ([x], [y]) is from [b]: 0 inside. */
    fun distance(b: Bounds, x: Int, y: Int): Int {
        val dx = if (x < b.left) b.left - x else if (x >= b.right) x - b.right + 1 else 0
        val dy = if (y < b.top) b.top - y else if (y >= b.bottom) y - b.bottom + 1 else 0
        return max(abs(dx), abs(dy))
    }

    /** A clickable parent at most this many times the element's area is "the element itself". */
    private const val TIGHT_PARENT = 3L

    /** Small: at most this percentage of the screen. A list row is bigger; a button is far smaller. */
    private const val SMALL_PERCENT = 4L

    /** Roles where where-you-tap is the point. */
    private val POSITIONAL = setOf("slider", "image", "view", ScreenElement.ROLE_PROGRESS)
}
