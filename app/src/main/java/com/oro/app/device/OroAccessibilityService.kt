package com.oro.app.device

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The bridge between Orobi and the phone.
 *
 * The service itself is deliberately thin: it tracks UI activity (so actions
 * can wait for the screen to settle), hosts the floating overlay, and exposes
 * itself to [DeviceController]. It never acts on its own -- every gesture
 * comes from a device task the user started in Oro, and while no task runs
 * the service only records event timestamps.
 */
class OroAccessibilityService : AccessibilityService() {

    /** Uptime of the last UI change, for settle detection. */
    @Volatile var lastEventAt: Long = 0L
        private set

    @Volatile var foregroundPackage: String = ""
        private set

    lateinit var overlay: AgentOverlay
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = AgentOverlay(this)
        _instance.value = this
        DeviceAgent.onServiceAvailable(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        lastEventAt = android.os.SystemClock.uptimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { if (it != packageName || DeviceAgent.state.value.running.not()) foregroundPackage = it }
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
        DeviceAgent.onServiceLost()
    }

    companion object {
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
