package com.oro.app.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.oro.app.data.ai.craft.QualityAuditor

/** Launchable apps, resolved by the names people actually use for them. */
object AppCatalog {

    data class App(val label: String, val packageName: String)

    @Volatile private var cache: List<App>? = null
    @Volatile private var cachedAt = 0L
    private val labels = HashMap<String, String>()

    fun apps(context: Context): List<App> {
        val now = System.currentTimeMillis()
        cache?.let { if (now - cachedAt < 60_000) return it }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
        synchronized(labels) { list.forEach { labels[it.packageName] = it.label } }
        cache = list
        cachedAt = now
        return list
    }

    fun labelFor(context: Context, pkg: String): String? {
        if (pkg.isEmpty()) return null
        synchronized(labels) { labels[pkg]?.let { return it } }
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()
        if (label != null) synchronized(labels) { labels[pkg] = label }
        return label
    }

    /** Common names that don't match the launcher label. */
    private val SYNONYMS = mapOf(
        "browser" to listOf("chrome", "firefox", "samsung internet", "brave", "edge", "opera", "browser"),
        "web" to listOf("chrome", "firefox", "samsung internet", "brave", "browser"),
        "camera" to listOf("camera"),
        "photos" to listOf("photos", "gallery"),
        "gallery" to listOf("gallery", "photos"),
        "messages" to listOf("messages", "messaging", "sms"),
        "sms" to listOf("messages", "messaging"),
        "phone" to listOf("phone", "dialer", "call"),
        "dialer" to listOf("phone"),
        "mail" to listOf("gmail", "email", "outlook", "mail"),
        "email" to listOf("gmail", "email", "outlook"),
        "settings" to listOf("settings"),
        "store" to listOf("play store", "galaxy store"),
        "play store" to listOf("play store", "google play"),
        "maps" to listOf("maps", "google maps"),
        "music" to listOf("spotify", "youtube music", "music"),
        "video" to listOf("youtube"),
        "files" to listOf("files", "my files", "file manager"),
        "calculator" to listOf("calculator"),
        "clock" to listOf("clock", "alarm"),
        "alarm" to listOf("clock"),
        "calendar" to listOf("calendar"),
        "notes" to listOf("keep", "notes", "samsung notes"),
        "x" to listOf("x", "twitter"),
        "twitter" to listOf("x", "twitter"),
    )

    /** Best match for [query], or null with suggestions computed by the caller. */
    fun resolve(context: Context, query: String): App? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val all = apps(context)
        all.firstOrNull { it.packageName.equals(q, true) }?.let { return it }
        all.firstOrNull { it.label.equals(q, true) }?.let { return it }
        SYNONYMS[q]?.forEach { syn -> all.firstOrNull { it.label.equals(syn, true) }?.let { return it } }
        SYNONYMS[q]?.forEach { syn -> all.firstOrNull { it.label.lowercase().contains(syn) }?.let { return it } }
        all.filter { it.label.lowercase().startsWith(q) }.minByOrNull { it.label.length }?.let { return it }
        all.filter { it.label.lowercase().contains(q) || it.packageName.contains(q.replace(" ", "")) }
            .minByOrNull { it.label.length }?.let { return it }
        return all.minByOrNull { QualityAuditor.levenshtein(it.label.lowercase(), q) }
            ?.takeIf { QualityAuditor.levenshtein(it.label.lowercase(), q) <= maxOf(1, q.length / 4) }
    }

    fun suggestions(context: Context, query: String, n: Int = 6): List<App> {
        val q = query.lowercase()
        return apps(context).sortedBy { QualityAuditor.levenshtein(it.label.lowercase().take(q.length + 3), q) }.take(n)
    }

    fun launch(context: Context, app: App): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun openUri(context: Context, uri: String): Boolean {
        val fixed = when {
            uri.contains("://") || uri.startsWith("tel:") || uri.startsWith("mailto:") || uri.startsWith("geo:") ||
                uri.startsWith("sms:") || uri.startsWith("intent:") || uri.startsWith("market:") -> uri
            else -> "https://$uri"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fixed)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
