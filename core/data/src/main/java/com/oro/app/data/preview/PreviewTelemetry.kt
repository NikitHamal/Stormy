package com.oro.app.data.preview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** One uncaught problem the previewed page reported. */
data class RuntimeIssue(
    /** `error`, `unhandledrejection`, `console.error`, `resource` or `console` (seen only by the console). */
    val kind: String,
    val message: String,
    /** The script URL or project file the problem came from; empty when unknown. */
    val source: String = "",
    val line: Int = 0,
    val column: Int = 0,
    val stack: String = ""
) {
    /** True when this carries more than a console line would (a column or a stack). */
    val isRich: Boolean get() = column > 0 || stack.isNotBlank()
}

/**
 * The page-side half of "Fix with Stormy": a script that reports uncaught
 * errors, unhandled promise rejections, failed resource loads and
 * `console.error` calls to the app (through the `OroTelemetry` bridge), and
 * the pure rules that turn those reports into a deduplicated list and into
 * a prompt the agent can act on. No Android types, so it is unit-tested.
 */
object PreviewTelemetry {

    /** The name the script's bridge is registered under in the WebView. */
    const val BRIDGE = "OroTelemetry"

    private const val MAX_ISSUES = 25
    private const val MAX_STACK_CHARS = 1500
    private const val MAX_MESSAGE_CHARS = 600

    /** Installs once per document; safe to inject again after load. */
    val SCRIPT: String = """
        (function () {
          if (window.__oroTelemetry || !window.$BRIDGE) return;
          window.__oroTelemetry = true;
          function send(o) { try { window.$BRIDGE.report(JSON.stringify(o)); } catch (e) {} }
          function text(a) {
            try {
              if (a && a.stack) return String(a.stack);
              if (typeof a === 'object') return JSON.stringify(a);
              return String(a);
            } catch (e) { return String(a); }
          }
          window.addEventListener('error', function (e) {
            var t = e.target;
            if (t && t !== window && (t.src || t.href)) {
              send({ kind: 'resource', message: 'Failed to load ' + (t.src || t.href), source: location.href });
              return;
            }
            send({
              kind: 'error', message: e.message || 'Script error', source: e.filename || location.href,
              line: e.lineno || 0, column: e.colno || 0,
              stack: e.error && e.error.stack ? String(e.error.stack) : ''
            });
          }, true);
          window.addEventListener('unhandledrejection', function (e) {
            var r = e.reason;
            send({
              kind: 'unhandledrejection',
              message: 'Unhandled promise rejection: ' + (r && r.message ? r.message : text(r)),
              source: location.href, stack: r && r.stack ? String(r.stack) : ''
            });
          });
          var original = console.error;
          console.error = function () {
            try { send({ kind: 'console.error', message: Array.prototype.map.call(arguments, text).join(' '), source: location.href }); } catch (e) {}
            return original.apply(console, arguments);
          };
        })();
    """.trimIndent()

    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Reads one report from the page. Null for anything that is not a usable report. */
    fun parse(json: String): RuntimeIssue? {
        val obj = runCatching { lenient.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        val message = obj.str("message")?.trim().orEmpty()
        if (message.isEmpty()) return null
        return RuntimeIssue(
            kind = obj.str("kind")?.take(32)?.ifBlank { null } ?: "error",
            message = message.take(MAX_MESSAGE_CHARS),
            source = obj.str("source").orEmpty().take(300),
            line = obj.int("line").coerceAtLeast(0),
            column = obj.int("column").coerceAtLeast(0),
            stack = obj.str("stack").orEmpty().trim().take(MAX_STACK_CHARS)
        )
    }

    /** What the WebView console reports for an error, as an issue. */
    fun fromConsole(message: String, sourceId: String, line: Int): RuntimeIssue =
        RuntimeIssue(
            kind = "console",
            message = message.trim().take(MAX_MESSAGE_CHARS),
            source = sourceId.take(300),
            line = line.coerceAtLeast(0)
        )

    /**
     * Adds [incoming] to [issues] (newest last, bounded). The same problem
     * reported twice (the console and the bridge both see an uncaught error)
     * becomes one entry, keeping whichever report has the column and stack.
     */
    fun merge(issues: List<RuntimeIssue>, incoming: RuntimeIssue): List<RuntimeIssue> {
        if (incoming.message.isBlank()) return issues
        val i = issues.indexOfFirst { same(it, incoming) }
        val next = if (i >= 0) {
            val kept = if (incoming.isRich && !issues[i].isRich) incoming else issues[i]
            issues.toMutableList().also { it.removeAt(i); it.add(kept) }
        } else {
            issues + incoming
        }
        return if (next.size > MAX_ISSUES) next.takeLast(MAX_ISSUES) else next
    }

    private fun same(a: RuntimeIssue, b: RuntimeIssue): Boolean {
        val x = core(a.message)
        val y = core(b.message)
        if (x.isEmpty() || y.isEmpty()) return false
        val messagesMatch = x == y || x.contains(y) || y.contains(x)
        val linesMatch = a.line == b.line || a.line == 0 || b.line == 0
        return messagesMatch && linesMatch
    }

    private fun core(message: String): String =
        message.trim().removePrefix("Uncaught ").lineSequence().firstOrNull().orEmpty().trim()

    /**
     * The project file an issue points at: its source mapped back through the
     * dev server at [serverOrigin], or null for inline scripts, other origins
     * and sources that are not project files.
     */
    fun fileOf(issue: RuntimeIssue, serverOrigin: String?): String? {
        if (serverOrigin == null || issue.source.isBlank()) return null
        val rel = PreviewArtifacts.relativeOf(serverOrigin, issue.source) ?: return null
        return rel.takeIf { it.substringAfterLast('/').contains('.') }
    }

    /** The headline shown on the banner. */
    fun headline(issues: List<RuntimeIssue>): String {
        val last = issues.lastOrNull() ?: return ""
        val first = last.message.lineSequence().firstOrNull().orEmpty().removePrefix("Uncaught ").trim()
        val more = issues.size - 1
        return if (more > 0) "$first (+$more more)" else first
    }

    /** The prompt that fills the chat: the file, position, message and stack of each issue. */
    fun fixPrompt(issues: List<RuntimeIssue>, page: String?, serverOrigin: String?): String {
        val list = issues.takeLast(5)
        return buildString {
            append(if (list.size == 1) "Fix this runtime error in the live preview.\n" else "Fix these runtime errors in the live preview.\n")
            if (!page.isNullOrBlank()) append("Page: ").append(page).append('\n')
            list.forEachIndexed { index, issue ->
                append('\n')
                if (list.size > 1) append("Error ").append(index + 1).append('\n')
                append(describe(issue, serverOrigin))
            }
            append("\n\nRead the file, find the root cause, fix it, and check the preview again with get_preview_errors.")
        }
    }

    /** The same facts without the instruction, for the clipboard. */
    fun copyContext(issues: List<RuntimeIssue>, page: String?, serverOrigin: String?): String =
        buildString {
            if (!page.isNullOrBlank()) append("Page: ").append(page).append("\n\n")
            issues.takeLast(10).forEachIndexed { index, issue ->
                if (index > 0) append("\n\n")
                append(describe(issue, serverOrigin))
            }
        }.trimEnd()

    private fun describe(issue: RuntimeIssue, serverOrigin: String?): String = buildString {
        val file = fileOf(issue, serverOrigin)
        val shown = file ?: issue.source.substringAfterLast('/').substringBefore('?').ifBlank { null }
        if (shown != null) {
            append("File: ").append(shown)
            if (issue.line > 0) {
                append(" (line ").append(issue.line)
                if (issue.column > 0) append(", column ").append(issue.column)
                append(')')
            }
            append('\n')
        }
        append("Error: ").append(issue.message.trim()).append('\n')
        if (issue.stack.isNotBlank()) {
            append("Stack:\n")
            issue.stack.lineSequence().take(12).forEach { append("  ").append(it.trim()).append('\n') }
        }
    }.trimEnd()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() } ?: 0
}
