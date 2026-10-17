package com.oro.app.ui.theme

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the UI should run in Lite mode: no backdrop blur, no decorative
 * infinite loops, brand animations at 30fps and the calmer standard motion
 * scheme. Provided by [OroTheme]; resolved by the activity from the user's
 * setting and [DevicePerformance.isLowEndDevice].
 *
 * Static: it changes only when the user flips the setting, and then the whole
 * tree should re-read it anyway.
 */
val LocalLiteMode = staticCompositionLocalOf { false }

object DevicePerformance {

    @Volatile
    private var cachedLowEnd: Boolean? = null

    /**
     * True on phones where decorative effects cost visible frames.
     *
     * Android's own low-RAM flag is only set on Go-edition devices, which
     * misses most budget phones, so the per-app heap class and total RAM are
     * consulted too. Computed once per process: none of these change at
     * runtime.
     */
    fun isLowEndDevice(context: Context): Boolean {
        cachedLowEnd?.let { return it }
        val am = context.applicationContext
            .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val result = if (am == null) {
            false
        } else {
            val memoryInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memoryInfo)
            am.isLowRamDevice ||
                am.memoryClass <= LOW_END_MEMORY_CLASS_MB ||
                memoryInfo.totalMem in 1..LOW_END_TOTAL_RAM_BYTES
        }
        cachedLowEnd = result
        return result
    }

    /** Heap limit (MB) at or below which the device is treated as low-end. */
    private const val LOW_END_MEMORY_CLASS_MB = 192

    /** Devices with 3 GB of RAM or less. */
    private const val LOW_END_TOTAL_RAM_BYTES = 3L * 1024 * 1024 * 1024
}
