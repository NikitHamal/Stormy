package com.oro.app.device.overlay

/**
 * Screen-to-window arithmetic for the overlay layers, kept free of Android
 * types so it is tested on the JVM.
 *
 * Gestures and accessibility bounds are in screen pixels. An overlay window
 * does not always start at the screen's corner (a cutout the window avoids,
 * insets an OEM applies to overlays, a rotation in flight), so a mark drawn at
 * raw screen coordinates lands that offset away from the target: above it
 * under a status-bar-sized offset, below it under a negative one. Every mark is
 * stored in screen pixels and moved into the view's space at draw time.
 */
internal object TouchGeometry {

    /** [screen] (x or y, screen pixels) in a view whose top-left sits at [viewOrigin] on screen. */
    fun toView(screen: Float, viewOrigin: Int): Float = screen - viewOrigin

    /**
     * A target's outline, grown by [pad] on every side so the stroke sits just
     * outside the control rather than over its edge; null for bounds too
     * small or inverted to be a real target.
     */
    fun outline(left: Float, top: Float, right: Float, bottom: Float, pad: Float): FloatArray? {
        if (right - left < 2f || bottom - top < 2f) return null
        return floatArrayOf(left - pad, top - pad, right + pad, bottom + pad)
    }

    /**
     * Whether [bounds] (l, t, r, b) is worth framing. A full-width row is (it
     * is what was tapped); a page-sized container says nothing about where the
     * touch landed, so anything taller than [maxHeightShare] of the screen or
     * covering more than [maxAreaShare] of it gets no frame.
     */
    fun worthOutlining(
        bounds: FloatArray,
        screenW: Int,
        screenH: Int,
        maxHeightShare: Float = 0.45f,
        maxAreaShare: Float = 0.4f,
    ): Boolean {
        if (bounds.size < 4 || screenW <= 0 || screenH <= 0) return false
        val w = bounds[2] - bounds[0]
        val h = bounds[3] - bounds[1]
        if (w <= 0f || h <= 0f) return false
        return h < screenH * maxHeightShare && w * h < screenW.toFloat() * screenH * maxAreaShare
    }
}
