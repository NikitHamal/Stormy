package com.oro.app.device

/**
 * Everything one device run needs from the phone.
 *
 * [DeviceController] is the real phone (accessibility service, gestures,
 * intents). The run loop talks only to this interface, so the whole decision
 * path -- observe, resolve, guard, act, report -- runs in JVM tests against a
 * scripted fake phone (see the scenario bench in app/src/test).
 */
internal interface Phone {
    /** Oro's own package: the run never operates Oro itself. */
    val ownPackage: String

    /** Uptime (ms) of the last UI event the service saw. */
    val lastEventAt: Long

    /** Uptime (ms) of the last event that changed what is on screen, not just a value redrawn ([UiActivity]). */
    val lastChangeAt: Long get() = lastEventAt

    /** The clock [lastEventAt] is on. */
    fun uptime(): Long

    fun capture(maxLabel: Int = ScreenReader.DEFAULT_MAX_LABEL): ScreenState

    /** Whether [state] is the newest capture, i.e. the one element actions use. */
    fun isLatest(state: ScreenState): Boolean

    /** Cheap check whether [text] shows in the active window (no full capture). */
    fun textVisible(text: String): Boolean

    /** Waits until the UI has been quiet for [quietMs], after at least [minMs] and at most [maxMs]. */
    suspend fun settle(quietMs: Long = 300, maxMs: Long = 2_500, minMs: Long = 120)

    suspend fun tap(x: Int, y: Int): Boolean
    suspend fun longPress(x: Int, y: Int, durationMs: Long = 700): Boolean
    suspend fun doubleTap(x: Int, y: Int): Boolean
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 350): Boolean
    suspend fun drag(x1: Int, y1: Int, x2: Int, y2: Int, holdMs: Long = 600, moveMs: Long = 700): Boolean
    suspend fun pinch(x: Int, y: Int, zoomIn: Boolean): Boolean

    suspend fun tapElement(e: ScreenElement, screen: ScreenState): String
    suspend fun longPressElement(e: ScreenElement, screen: ScreenState): String
    suspend fun doubleTapElement(e: ScreenElement, screen: ScreenState): String
    suspend fun type(e: ScreenElement?, screen: ScreenState, text: String, append: Boolean): String
    suspend fun submit(): String
    suspend fun media(action: String): String
    suspend fun scroll(direction: String, e: ScreenElement?, screen: ScreenState?, amount: Float = 0.6f): String

    /**
     * Sets slider [e] to [value] ("35", "40%", "1:30") and reads it back. A
     * time needs the length: [durationMs], else the playing app's session.
     */
    suspend fun setValue(e: ScreenElement, screen: ScreenState, value: String, durationMs: Long? = null): String

    /** Seeks what is playing through its media session (only [pkg]'s when given): exact, or why not. */
    suspend fun seekMedia(position: String, pkg: String? = null): MediaControl.SeekResult

    /** One tap on the video player, away from its buttons, to bring back hidden controls. False with no player. */
    suspend fun showPlayerControls(): Boolean

    /** The click the app reported for a tap at ([x], [y]) that started at [since] (uptime), if one comes within [waitMs]. */
    suspend fun clickAfter(since: Long, x: Int, y: Int, waitMs: Long = 250): TapHit?

    fun global(key: String): String

    /** Launches an app by name and returns once it is in front (or after a short timeout). */
    suspend fun openApp(name: String): String
    fun openUrl(url: String): String

    /** Brings [packageName]'s task back to the front where it was. */
    fun bringToFront(packageName: String): Boolean

    /** Whether Oro's own screen is the app in front. */
    fun oroInFront(): Boolean

    fun quickAction(args: QuickActions.Args): String
    fun apps(): List<AppCatalog.App>
    fun status(): String

    /** A numbered screenshot of [state] as base64 JPEG, or null when the phone or app cannot give one. */
    suspend fun screenshot(state: ScreenState): String?
}
