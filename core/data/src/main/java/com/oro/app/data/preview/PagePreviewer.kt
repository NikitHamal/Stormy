package com.oro.app.data.preview

import java.io.File

/** One rendering of a page at one viewport. */
data class PageShot(
    val device: String,
    val width: Int,
    val height: Int,
    /** Base64 JPEG, or null when the renderer could not capture pixels. */
    val jpegBase64: String?,
    /** Layout facts measured in the page (overflow, tap targets, fonts), as text. */
    val metrics: String
)

/**
 * Renders project pages off screen so the agent can look at its own work.
 * Implemented in the app with a WebView ([PagePreviewers.instance]); the
 * data layer only sees this interface.
 */
interface PagePreviewer {
    suspend fun capture(projectRoot: File, page: String, devices: List<Device>): List<PageShot>
    /** A Lighthouse-style audit of the page, as a readable report. */
    suspend fun audit(projectRoot: File, page: String): String
}

enum class Device(val label: String, val width: Int, val height: Int) {
    PHONE("phone", 390, 844), TABLET("tablet", 820, 1180), DESKTOP("desktop", 1280, 800);

    companion object {
        fun parse(v: String?): List<Device> = when (v?.trim()?.lowercase()) {
            null, "", "phone", "mobile" -> listOf(PHONE)
            "tablet" -> listOf(TABLET)
            "desktop" -> listOf(DESKTOP)
            "all", "responsive" -> entries.toList()
            else -> listOf(PHONE)
        }
    }
}

object PagePreviewers {
    @Volatile
    var instance: PagePreviewer? = null
}
