package com.oro.app.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The bridge between Orobi and the phone.
 *
 * The service itself is deliberately thin: it tracks UI activity (so actions
 * can wait for the screen to settle), hosts the floating overlay, skips video
 * ads while a task asks for it, and exposes itself to [DeviceController]. It
 * never acts on its own -- every gesture comes from a device task the user
 * started in Oro, and while no task runs the service only records event
 * timestamps.
 */
class OroAccessibilityService : AccessibilityService() {

    /** Uptime of the last UI change, for settle detection. */
    @Volatile var lastEventAt: Long = 0L
        private set

    /** What the events say about the screen settling: busy screens, cosmetic redraws ([UiActivity]). */
    internal val activity = UiActivity()

    @Volatile var foregroundPackage: String = ""
        private set

    /** The last click an app reported during a task, so a tap by coordinates can say what it hit. */
    internal val lastClick = MutableStateFlow<TapHit?>(null)

    /** Uptime when a text field last started input with the service's keyboard connection (Android 13+). */
    @Volatile var inputStartedAt: Long = 0L
        private set

    lateinit var overlay: AgentOverlay
        private set

    /** Ad checks run off the main thread, one at a time, at most every [AD_SCAN_INTERVAL_MS]. */
    private var adExecutor: ExecutorService? = null
    private val adScanQueued = AtomicBoolean(false)
    @Volatile private var lastAdScanAt = 0L

    /** Clicked views are read off the main thread too: the island draws there, and each read is a call into the app. */
    private var clickExecutor: ExecutorService? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = AgentOverlay(this)
        adExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "oro-ad-skip").apply { isDaemon = true } }
        clickExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "oro-clicks").apply { isDaemon = true } }
        _instance.value = this
        DeviceAgent.onServiceAvailable(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // While a task runs, Oro's own windows (the island redrawing its
        // status) are not the screen the agent waits on: counting them kept
        // settle() waiting and forced needless recaptures.
        val fromOro = event.packageName?.toString() == packageName
        if (!fromOro || !DeviceAgent.state.value.running) {
            val at = SystemClock.uptimeMillis()
            lastEventAt = at
            activity.record(at, event.eventType, event.contentChangeTypes)
        }
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { if (it != packageName || DeviceAgent.state.value.running.not()) foregroundPackage = it }
        }
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED && !fromOro && DeviceAgent.state.value.running) recordClick(event)
        if (DeviceAgent.adSkipEnabled &&
            (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        ) {
            scheduleAdScan()
        }
    }

    /**
     * A video player fires content changes many times a second; one check per
     * interval is plenty to catch a skip button that stays for seconds, and
     * keeps the cost on a budget phone near zero.
     */
    private fun scheduleAdScan() {
        val now = SystemClock.uptimeMillis()
        if (now - lastAdScanAt < AD_SCAN_INTERVAL_MS) return
        val executor = adExecutor ?: return
        if (!adScanQueued.compareAndSet(false, true)) return
        lastAdScanAt = now
        runCatching {
            executor.execute {
                try {
                    if (DeviceAgent.adSkipEnabled) {
                        AdSkipper.scanAndSkip(this)?.let { DeviceAgent.onAdSkipped(it) }
                    }
                } catch (_: Exception) {
                    // A node vanished mid-check; the next event tries again.
                } finally {
                    adScanQueued.set(false)
                }
            }
        }.onFailure { adScanQueued.set(false) }
    }

    /**
     * Notes a click an app reported during a task, for a tap by coordinates to
     * say what it hit. The event is copied (the platform reuses it once this
     * returns) and its view read on [clickExecutor].
     */
    private fun recordClick(event: AccessibilityEvent) {
        val at = SystemClock.uptimeMillis()
        val executor = clickExecutor ?: return
        @Suppress("DEPRECATION") val copy = AccessibilityEvent.obtain(event)
        runCatching {
            executor.execute {
                val source = runCatching { copy.source }.getOrNull()
                val r = Rect()
                source?.getBoundsInScreen(r)
                lastClick.value = TapHit(
                    at = at,
                    role = TapFeedback.roleOf((copy.className ?: source?.className)?.toString()),
                    label = TapFeedback.label(
                        copy.contentDescription?.toString(), copy.text.joinToString(" "),
                        source?.contentDescription?.toString(), source?.text?.toString(),
                        source?.viewIdResourceName?.substringAfter(":id/")?.replace('_', ' '),
                    ),
                    bounds = if (r.width() > 0 && r.height() > 0) Bounds(r.left, r.top, r.right, r.bottom) else null,
                )
            }
        }
    }

    /**
     * The service's keyboard connection (with flagInputMethodEditor): it types
     * through the field's own input connection, as a keyboard does, where
     * ACTION_SET_TEXT does not take. It only notes when a field starts input.
     */
    @RequiresApi(33)
    override fun onCreateInputMethod(): InputMethod = object : InputMethod(this) {
        override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
            super.onStartInput(attribute, restarting)
            inputStartedAt = SystemClock.uptimeMillis()
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (_instance.value === this) _instance.value = null
        if (::overlay.isInitialized) overlay.hideAll()
        adExecutor?.shutdownNow()
        adExecutor = null
        clickExecutor?.shutdownNow()
        clickExecutor = null
        DeviceAgent.onServiceLost()
    }

    companion object {
        private const val AD_SCAN_INTERVAL_MS = 400L

        private val _instance = MutableStateFlow<OroAccessibilityService?>(null)
        val instance: StateFlow<OroAccessibilityService?> = _instance.asStateFlow()

        /** True when the user has switched the service on in system settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, OroAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
