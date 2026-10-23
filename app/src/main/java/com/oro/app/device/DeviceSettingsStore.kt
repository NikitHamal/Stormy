package com.oro.app.device

import android.content.Context

/**
 * Where the device agent's settings live, shared by the device screen and by
 * `use_phone` runs started from the coding chat (which must honour the same
 * approvals, touch display and ad skipping the user chose).
 */
object DeviceSettingsStore {

    private const val PREFS = "device_agent"
    private const val VERSION = 2

    fun load(context: Context): DeviceAgent.Settings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = DeviceAgent.Settings()
        // v1 saved its old default (60 actions) whenever any setting changed;
        // that number was a default, not a choice, so it becomes "no limit".
        val storedMax = p.getInt("max", defaults.maxActions)
        val max = if (p.getInt("v", 1) < VERSION && storedMax == 60) defaults.maxActions else storedMax
        return DeviceAgent.Settings(
            confirmSensitive = p.getBoolean("confirm", defaults.confirmSensitive),
            showTouches = p.getBoolean("touches", defaults.showTouches),
            returnToOro = p.getBoolean("return", defaults.returnToOro),
            maxActions = max.coerceAtLeast(0),
            skipAds = p.getBoolean("skipAds", defaults.skipAds),
            screenshots = p.getBoolean("screenshots", defaults.screenshots),
        )
    }

    fun save(context: Context, s: DeviceAgent.Settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("v", VERSION)
            .putBoolean("confirm", s.confirmSensitive)
            .putBoolean("touches", s.showTouches)
            .putBoolean("return", s.returnToOro)
            .putInt("max", s.maxActions.coerceAtLeast(0))
            .putBoolean("skipAds", s.skipAds)
            .putBoolean("screenshots", s.screenshots)
            .apply()
    }
}
