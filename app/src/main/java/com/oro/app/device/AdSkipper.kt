package com.oro.app.device

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Taps "Skip ad" the moment a video ad becomes skippable, while a device task
 * runs (setting: skip ads).
 *
 * Done by the service, not the model: the button appears for a few seconds at
 * an unpredictable time, usually while the agent is waiting, and a model
 * round trip is slower than the window. Matching is deliberately strict -- a
 * bare "Skip" only counts inside YouTube apps, where it is the ad button;
 * anywhere else the label must say "ad". A countdown ("Skip ad in 5") is not
 * clickable yet and never matches.
 */
object AdSkipper {

    private val YOUTUBE_PACKAGES = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.youtube.kids",
        "com.google.android.youtube.tv",
    )

    private val EXPLICIT = Regex("^(skip ads?|skip this ad|skip video ad|skip ad now)$")
    private val YOUTUBE_LABEL = Regex("^(skip|skip ads?|skip ad now)$")

    fun isYouTube(pkg: String): Boolean = pkg in YOUTUBE_PACKAGES || pkg.contains("youtube")

    /** Whether a node with these properties is a skippable-ad button. Pure, for tests. */
    fun isSkipButton(pkg: String, text: String?, description: String?, viewId: String?): Boolean {
        val id = viewId.orEmpty().lowercase()
        val labels = listOfNotNull(text, description).map(::normalize).filter { it.isNotEmpty() }
        if (labels.any { it.any(Char::isDigit) }) return false
        if (isYouTube(pkg)) {
            if ("skip_ad" in id || "ad_skip" in id || "skip_button" in id) return true
            if (labels.any { YOUTUBE_LABEL.matches(it) }) return true
        }
        return labels.any { EXPLICIT.matches(it) }
    }

    private fun normalize(s: String): String = s.lowercase()
        .replace(' ', ' ')
        .replace(Regex("[›>»→\\s]+$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Looks for a skippable ad in the active window and taps it. Returns a
     * short description of what was skipped, or null. Uses the platform's
     * own search (done in the app's process) instead of walking the tree.
     */
    fun scanAndSkip(service: AccessibilityService): String? {
        val root = service.rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString().orEmpty()
        val candidates = LinkedHashSet<AccessibilityNodeInfo>()
        if (isYouTube(pkg)) {
            for (id in listOf("skip_ad_button", "skip_ad_button_text", "modern_skip_ad_text")) {
                runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }.getOrNull()?.let { candidates += it }
            }
        }
        runCatching { root.findAccessibilityNodeInfosByText("Skip") }.getOrNull()?.let { candidates += it }
        for (node in candidates) {
            if (!node.isVisibleToUser || !node.isEnabled) continue
            if (!isSkipButton(pkg, node.text?.toString(), node.contentDescription?.toString(), node.viewIdResourceName)) continue
            val target = clickable(node) ?: continue
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                val r = Rect().also { target.getBoundsInScreen(it) }
                (service as? OroAccessibilityService)?.overlay?.showTouch(r.centerX(), r.centerY())
                return if (isYouTube(pkg)) "Skipped a YouTube ad" else "Skipped an ad"
            }
        }
        return null
    }

    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 4) {
            if (n.isClickable && n.isEnabled) return n
            n = n.parent
            depth++
        }
        return null
    }
}
