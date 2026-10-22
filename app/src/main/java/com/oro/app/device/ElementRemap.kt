package com.oro.app.device

import kotlin.math.abs

/**
 * Keeps element numbers honest when several actions run in one turn.
 *
 * The model picks numbers from the screen it saw. If an earlier action in the
 * same turn changed the screen, number 7 may now be a different button; acting
 * on it would tap something the model never chose. So the element is looked up
 * again by what it is (role and label), nearest to where it was, and when it is
 * gone the action is skipped and reported as stale.
 */
object ElementRemap {

    /**
     * The element the model meant by [index] on [seen], found on [now].
     * Returns the element (or null) and whether the index went stale.
     */
    fun resolve(seen: ScreenState, now: ScreenState, index: Int): Pair<ScreenElement?, Boolean> {
        if (seen === now || seen.signature == now.signature) return now.element(index) to false
        val original = seen.element(index) ?: return null to false
        if (original.label.isBlank()) {
            // No identity to look up by: only the same place on the same kind of element will do.
            val same = now.elements.firstOrNull { it.role == original.role && it.bounds == original.bounds }
            return same to (same == null)
        }
        val match = now.elements
            .filter { it.role == original.role && it.label == original.label }
            .minByOrNull { abs(it.centerY - original.centerY) + abs(it.centerX - original.centerX) }
        return match to (match == null)
    }
}
