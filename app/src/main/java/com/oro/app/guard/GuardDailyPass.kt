package com.oro.app.guard

import android.content.Context
import com.oro.app.data.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The "daily guard pass" without a background scheduler: on the first Shield
 * visit of a new day, the keyless checks run and land on the findings board.
 * No model call, no notification, no battery cost while the app is closed --
 * the toggle promises exactly what happens, when it happens.
 */
internal object GuardDailyPass {

    private const val DUE_AFTER_MS = 20 * 60 * 60 * 1000L

    suspend fun runIfDue(context: Context, aiRepository: AiRepository): Int = withContext(Dispatchers.IO) {
        if (!GuardSettingsStore.dailyPassOn(context)) return@withContext 0
        val last = GuardSettingsStore.lastDailyPass(context)
        if (last > 0 && System.currentTimeMillis() - last < DUE_AFTER_MS) return@withContext 0

        val guard = RealGuard(context.applicationContext, aiRepository, null)
        var added = 0
        val reports = ArrayList<Pair<String, String>>()
        runCatching { guard.phoneReport() }.getOrNull()?.let { reports += it to "daily posture" }
        runCatching { guard.kevRecent(7) }.getOrNull()?.let { reports += it to "daily KEV" }
        runCatching { guard.vaultAudit() }.getOrNull()?.let { reports += it to "daily vault" }
        for ((report, source) in reports) {
            report.lineSequence().forEach { line ->
                val match = Regex("^\\[(\\w+)]\\s+(.+?)\\s*->\\s*(.+)$").find(line.trim()) ?: return@forEach
                GuardSettingsStore.addFinding(
                    context,
                    match.groupValues[1],
                    match.groupValues[2].take(140),
                    match.groupValues[3].take(240),
                    source,
                )
                added++
            }
        }
        GuardSettingsStore.markDailyPass(context)
        added
    }
}
