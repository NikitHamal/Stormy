package com.oro.app.device

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * The whole display in screen pixels, status and navigation bars included:
 * the space accessibility bounds, injected gestures and the overlay windows
 * all share.
 *
 * `resources.displayMetrics` is not that space. From a service it can leave
 * out the navigation bar (and on some phones the status bar), so bounds
 * clipped to it lose their bottom edge and a control near the bottom is
 * tapped -- and marked -- above its middle. Read per use: rotation changes it.
 */
internal object DisplaySize {

    fun of(context: Context): Rect {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null && Build.VERSION.SDK_INT >= 30) {
            val bounds = runCatching { wm.maximumWindowMetrics.bounds }.getOrNull()
                ?.takeIf { it.width() > 0 && it.height() > 0 }
                ?: runCatching { wm.currentWindowMetrics.bounds }.getOrNull()
                    ?.takeIf { it.width() > 0 && it.height() > 0 }
            if (bounds != null) return Rect(0, 0, bounds.width(), bounds.height())
        }
        if (wm != null) {
            @Suppress("DEPRECATION")
            val real = DisplayMetrics().also { runCatching { wm.defaultDisplay.getRealMetrics(it) } }
            if (real.widthPixels > 0 && real.heightPixels > 0) return Rect(0, 0, real.widthPixels, real.heightPixels)
        }
        val m = context.resources.displayMetrics
        return Rect(0, 0, m.widthPixels, m.heightPixels)
    }
}
