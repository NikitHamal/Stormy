package com.oro.app.device

/**
 * Keeps element numbers honest when the screen moved between the model's
 * decision and the action (an earlier call in the same turn, a list that
 * updated while the model was thinking, the user scrolling during a pause).
 *
 * The model picks numbers from the screen it saw; number 7 may now be a
 * different button. So the element is looked up again by what it is, and when
 * that is not certain the action is skipped and reported as stale -- a wrong
 * tap costs far more than one more turn:
 *  - another app in front: stale;
 *  - labelled: the one element with the same role, label and window. Several
 *    look-alikes ("Delete" on every row) are stale unless the view id singles
 *    one out -- after the first delete the list shifts, and "the nearest
 *    Delete" is the next row's;
 *  - unlabelled: only the one element of the same role and view id will do; an
 *    icon in the same toolbar slot on another screen is not the same icon.
 */
object ElementRemap {

    /**
     * The element the model meant by [index] on [seen], found on [now].
     * Returns the element (or null) and whether the index went stale.
     */
    fun resolve(seen: ScreenState, now: ScreenState, index: Int): Pair<ScreenElement?, Boolean> {
        if (seen === now || seen.signature == now.signature) return now.element(index) to false
        val original = seen.element(index) ?: return null to false
        if (seen.packageName != now.packageName) return null to true
        val candidates = if (original.label.isBlank()) {
            val id = original.viewId ?: return null to true
            now.elements.filter {
                it.label.isBlank() && it.role == original.role && it.viewId == id && it.window == original.window
            }
        } else {
            now.elements.filter { it.role == original.role && it.label == original.label && it.window == original.window }
        }
        if (candidates.size == 1) return candidates[0] to false
        if (candidates.isEmpty()) return null to true
        val id = original.viewId ?: return null to true
        val byId = candidates.filter { it.viewId == id }
        return if (byId.size == 1) byId[0] to false else null to true
    }
}
