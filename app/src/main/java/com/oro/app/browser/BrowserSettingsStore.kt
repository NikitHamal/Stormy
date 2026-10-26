package com.oro.app.browser

import android.content.Context

/**
 * Where the browser agent's settings live, shared by the browser screen and
 * by `use_browser` runs started from a chat (which must honour the same
 * approvals and limits the user chose).
 */
object BrowserSettingsStore {

    private const val PREFS = "browser_agent"

    fun load(context: Context): BrowserAgent.Settings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = BrowserAgent.Settings()
        return BrowserAgent.Settings(
            confirmSensitive = p.getBoolean("confirm", defaults.confirmSensitive),
            returnToOro = p.getBoolean("return", defaults.returnToOro),
            maxActions = p.getInt("max", defaults.maxActions).coerceAtLeast(0),
            screenshots = p.getBoolean("screenshots", defaults.screenshots),
            desktopMode = p.getBoolean("desktop", defaults.desktopMode),
        )
    }

    fun save(context: Context, s: BrowserAgent.Settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("confirm", s.confirmSensitive)
            .putBoolean("return", s.returnToOro)
            .putInt("max", s.maxActions.coerceAtLeast(0))
            .putBoolean("screenshots", s.screenshots)
            .putBoolean("desktop", s.desktopMode)
            .apply()
    }
}
