package com.oro.app.data.preview

/** What shows behind a page that has no background of its own. */
enum class CanvasBackdrop(val label: String) {
    AUTO("Auto"), LIGHT("Light"), DARK("Dark"), GRID("Grid");

    fun next(): CanvasBackdrop = entries[(ordinal + 1) % entries.size]

    companion object {
        const val LIGHT_COLOR = 0xFFFFFFFFL
        const val DARK_COLOR = 0xFF09090BL
    }
}

/** Width rules for the preview's drag-to-resize viewport. Widths are CSS pixels. */
object PreviewViewport {
    const val MIN_WIDTH = 240
    const val MAX_WIDTH = 1920

    /** Common device and breakpoint widths a drag snaps to when it lands near one. */
    val BREAKPOINTS = intArrayOf(320, 360, 375, 390, 414, 480, 600, 768, 820, 1024, 1280, 1440, 1920)

    private const val SNAP_DISTANCE = 8

    fun clamp(width: Int): Int = width.coerceIn(MIN_WIDTH, MAX_WIDTH)

    /** [width] clamped, and pulled onto the nearest breakpoint within a few pixels. */
    fun snap(width: Int): Int {
        val w = clamp(width)
        val near = BREAKPOINTS.minByOrNull { kotlin.math.abs(it - w) } ?: return w
        return if (kotlin.math.abs(near - w) <= SNAP_DISTANCE) near else w
    }

    /**
     * The page's visible height in CSS pixels when it is laid out [layoutWidth]
     * wide and scaled to fill a view of [viewWidthPx] x [viewHeightPx].
     */
    fun cssHeight(layoutWidth: Int, viewWidthPx: Int, viewHeightPx: Int): Int =
        if (viewWidthPx <= 0 || viewHeightPx <= 0) 0
        else (viewHeightPx.toDouble() * layoutWidth / viewWidthPx).toInt()

    /** `375 × 667`, or just the width when the height is not known. */
    fun label(width: Int, height: Int): String = if (height > 0) "$width × $height" else "$width"
}
