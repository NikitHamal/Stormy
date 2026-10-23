package com.oro.app.data.ai.eval

import com.oro.app.data.preview.Device
import com.oro.app.data.preview.PageMetrics
import com.oro.app.data.preview.PageShot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Headless Chromium through `browse.py` (Playwright): the eval's stand-in for
 * the app's WebView previewer, plus the final screenshots and smoke tests.
 * Pages load from Oro's own dev server, so `/api` (MockApi) works as it does
 * in the live preview.
 */
internal class EvalBrowser(private val python: String, workDir: File) {

    private val script: File = File(workDir, "browse.py").also { f ->
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            f.writeText(javaClass.classLoader!!.getResource("oro-eval/browse.py")!!.readText())
        }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Runs the probe; null when it failed or timed out. */
    fun run(vararg args: String, timeoutSec: Long = 120): JsonObject? {
        val out = File.createTempFile("browse", ".json")
        val err = File.createTempFile("browse", ".err")
        return try {
            val p = ProcessBuilder(listOf(python, script.path) + args)
                .redirectOutput(out)
                .redirectError(err)
                .start()
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly(); return null
            }
            val text = out.readText().trim().lineSequence().lastOrNull { it.startsWith("{") } ?: return null
            runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        } finally {
            out.delete()
            err.delete()
        }
    }

    /** What screenshot_preview reports to the model, worded like the app's previewer. */
    fun capture(baseUrl: String, page: String, devices: List<Device>): List<PageShot> = devices.map { d ->
        val o = run("shot", baseUrl + page, d.width.toString(), d.height.toString(), "-", "0")
        PageShot(d.label, d.width, d.height, null, o?.let { formatMetrics(it) } ?: "(could not measure)")
    }

    fun audit(baseUrl: String, page: String): String {
        val o = run("audit", baseUrl + page) ?: return "Could not render $page."
        val issues = (o["issues"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        if (issues.isEmpty()) return "Audit of $page: no issues found."
        val score = (100 - issues.size * 4).coerceIn(0, 100)
        return buildString {
            append("Audit of ").append(page).append(" — ").append(issues.size).append(" issues (rough score ").append(score).append("/100)\n")
            issues.groupBy { it.str("cat") }.forEach { (cat, list) ->
                append("\n").append(cat.uppercase()).append('\n')
                list.forEach { i ->
                    append("- ").append(i.str("msg"))
                    i.str("sel").takeIf { it.isNotBlank() }?.let { append("  [").append(it).append(']') }
                    append('\n')
                }
            }
        }.trimEnd()
    }

    /** Runtime errors of the page, like the app's preview diagnostics. */
    fun errors(baseUrl: String, page: String): String {
        val o = run("shot", baseUrl + page, "390", "844", "-", "0") ?: return "Could not load $page."
        val lines = (o.list("pageErrors").map { "Uncaught: $it" } + o.list("console").map { "Console error: $it" } +
            o.list("failed").map { "Failed to load: $it" }).distinct()
        return if (lines.isEmpty()) "No errors or warnings in the preview." else lines.take(20).joinToString("\n")
    }

    private fun formatMetrics(o: JsonObject): String =
        (o["metrics"] as? JsonObject)?.let { PageMetrics.from(it).describe() } ?: "(could not measure)"

    companion object {
        fun JsonObject.str(k: String): String = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        fun JsonObject.list(k: String): List<String> =
            (this[k] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
    }
}
