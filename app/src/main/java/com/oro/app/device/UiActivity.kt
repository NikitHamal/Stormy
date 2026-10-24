package com.oro.app.device

/**
 * What the screen's accessibility events say about whether it has settled,
 * for [DeviceController.settle] and for reusing a capture.
 *
 * Waiting for silence works on a still screen and never ends on a busy one:
 * a playing video, a progress bar or an animation sends events all the time,
 * so every action used to wait out the whole cap (2.5 s) before the model
 * could look again. Two things fix that. A content change that only redraws
 * a value -- a clock's text, a description, a state -- is [cosmetic]: it
 * does not change what is on screen to act on. And a screen that was already
 * busy before the action ([noisy]) is settled once its structure stops
 * changing, not once it falls silent.
 *
 * Plain Kotlin (the event fields are passed in), so it is tested on the JVM.
 * Written from the main thread, read from the run's.
 */
internal class UiActivity(
    /** Events closer together than this belong to one run of activity. */
    private val gapMs: Long = NOISE_GAP_MS,
    /** A run of activity this long, still going, is a busy screen rather than a transition. */
    private val busyMs: Long = BUSY_RUN_MS,
) {
    /** Uptime of the last event of any kind. */
    @Volatile var lastAt: Long = 0L
        private set

    /** Uptime of the last event that changed what is on screen (not [cosmetic]). */
    @Volatile var lastChangeAt: Long = 0L
        private set

    @Volatile private var runStart: Long = 0L

    fun record(at: Long, eventType: Int, contentChangeTypes: Int) {
        if (at - lastAt > gapMs) runStart = at
        lastAt = at
        if (!cosmetic(eventType, contentChangeTypes)) lastChangeAt = at
    }

    /**
     * Events have kept coming, never more than [gapMs] apart, for at least
     * [busyMs] up to [now]: the screen was busy before whatever just happened.
     */
    fun noisy(now: Long): Boolean = now - lastAt <= gapMs && lastAt - runStart >= busyMs

    companion object {
        const val NOISE_GAP_MS = 250L
        const val BUSY_RUN_MS = 1_200L

        // AccessibilityEvent's values, fixed by the platform.
        const val TYPE_WINDOW_CONTENT_CHANGED = 0x800
        const val CHANGE_SUBTREE = 0x1
        const val CHANGE_PANE_TITLE = 0x8
        const val CHANGE_PANE_APPEARED = 0x10
        const val CHANGE_PANE_DISAPPEARED = 0x20

        /** Views appearing, leaving or being replaced: what makes a screen a different screen. */
        private const val STRUCTURAL = CHANGE_SUBTREE or CHANGE_PANE_TITLE or CHANGE_PANE_APPEARED or CHANGE_PANE_DISAPPEARED

        /**
         * A content change that only redraws a value (text, description,
         * state, an error, enabled). An unspecified change (0) is not
         * cosmetic: older views report every change that way.
         */
        fun cosmetic(eventType: Int, contentChangeTypes: Int): Boolean =
            eventType == TYPE_WINDOW_CONTENT_CHANGED && contentChangeTypes != 0 && contentChangeTypes and STRUCTURAL == 0
    }
}
