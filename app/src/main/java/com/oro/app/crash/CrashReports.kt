package com.oro.app.crash

import android.content.Context
import java.io.File

/**
 * Crash reports on disk, newest first, so the crash screen can show earlier
 * crashes too and a report survives the process that wrote it. Kept small:
 * the last [KEEP] reports.
 */
object CrashReports {

    private const val KEEP = 10

    data class Summary(
        /** "NoSuchMethodError" */
        val type: String,
        /** The exception's message, first line. */
        val message: String,
        /** The first frame in Oro's own code, if any. */
        val where: String?,
        /** The innermost "Caused by", when it differs from the top exception. */
        val rootCause: String?,
        /** A plain-language guess at what went wrong, for the person reading the screen. */
        val hint: String?
    )

    fun dir(context: Context) = File(context.filesDir, "crashes")

    fun save(context: Context, report: String) {
        runCatching {
            val d = dir(context).apply { mkdirs() }
            File(d, "crash-${System.currentTimeMillis()}.txt").writeText(report)
            d.listFiles().orEmpty().sortedByDescending { it.name }.drop(KEEP).forEach { it.delete() }
        }
    }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.startsWith("crash-") }.orEmpty().sortedByDescending { it.name }

    fun clear(context: Context) { dir(context).listFiles().orEmpty().forEach { it.delete() } }

    fun timeOf(file: File): Long = file.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: file.lastModified()

    private val EXCEPTION_LINE = Regex("""^([\w.$]+(?:Exception|Error|Throwable)[\w$]*)(?::\s*(.*))?$""")

    fun summarize(report: String): Summary {
        val lines = report.lines()
        val start = lines.indexOfFirst { it.trim() == "=== EXCEPTION ===" }.let { if (it < 0) 0 else it + 1 }
        val head = lines.drop(start).firstOrNull { EXCEPTION_LINE.matches(it.trim()) }?.trim().orEmpty()
        val m = EXCEPTION_LINE.find(head)
        val fqType = m?.groupValues?.get(1).orEmpty()
        val type = fqType.substringAfterLast('.').ifEmpty { "Crash" }
        val message = m?.groupValues?.get(2).orEmpty().lineSequence().first().take(400)
        val where = lines.firstOrNull { it.trim().startsWith("at com.oro.") }?.trim()?.removePrefix("at ")
            ?.replace(Regex("""\(r8-map-id-[^:)]*"""), "(")
        val cause = lines.lastOrNull { it.trim().startsWith("Caused by:") }?.trim()?.removePrefix("Caused by:")?.trim()
            ?.takeIf { !it.startsWith(fqType) }
        return Summary(type, message, where, cause, hint(fqType, message + " " + cause.orEmpty()))
    }

    private fun hint(type: String, text: String): String? = when {
        type.endsWith("NoSuchMethodError") || type.endsWith("NoClassDefFoundError") ->
            "A library called something this version of Android does not have. Updating Oro usually fixes it."
        type.endsWith("OutOfMemoryError") ->
            "The phone ran out of memory. Close other apps, or turn on Lite mode in Settings."
        type.endsWith("NetworkOnMainThreadException") || text.contains("UnknownHost", true) ->
            "A network call failed. Check the connection and try again."
        type.endsWith("SecurityException") ->
            "Android blocked something Oro needs. Check the app's permissions."
        type.endsWith("SQLiteException") || text.contains("database", true) ->
            "Oro could not read its local data. Restarting usually helps."
        type.endsWith("IllegalStateException") || type.endsWith("NullPointerException") ->
            "Oro reached a state it did not expect. The report shows where."
        else -> null
    }
}
