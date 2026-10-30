package com.oro.app.guard

import android.content.Context
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.security.SecretScan
import com.oro.app.data.security.ThreatTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The tool contract: every guard capability lands here, and the environment
 * behind it is [GuardAgent.Guard]. Shared by the main run and by investigator
 * subagents, so the same rules (masked secrets, no retaliation, no plaintext
 * passwords) hold everywhere.
 */
internal class GuardToolRuntime(
    private val guard: GuardAgent.Guard,
    private val appContext: Context?,
    private val ask: suspend (String, List<String>) -> String,
    private val onFinish: (String, Boolean) -> Unit,
    private val onNote: (String) -> Unit,
) {

    suspend fun execute(name: String, args: JSONObject): ToolResult {
        if (name == "use_browser") {
            val task = args.optString("task").trim()
            if (task.isEmpty()) return ToolResult(false, "", "Provide task: what the browser agent should do and what counts as done.")
            return guard.browserTask(task)
        }
        if (name !in GuardTools.names) return ToolResult(false, "", "Unknown guard tool: $name")
        return try {
            when (name) {
                "audit_phone" -> {
                    val report = guard.phoneReport()
                    persistFindings(report, "phone audit")
                    ToolResult(true, report)
                }

                "check_url" -> {
                    val url = args.optString("url")
                    if (url.isBlank()) ToolResult(false, "", "Provide url.")
                    else ToolResult(true, guard.checkUrl(url, args.optBoolean("deep", true)))
                }

                "lookup_advisory" -> {
                    val pkg = args.optString("package")
                    val eco = args.optString("ecosystem")
                    if (pkg.isBlank() || eco.isBlank()) ToolResult(false, "", "Provide package and ecosystem.")
                    else ToolResult(true, guard.advisories(pkg, eco, args.optString("version").ifBlank { null }))
                }

                "kev_recent" -> {
                    val days = args.optInt("days", 14).coerceIn(1, 90)
                    val report = guard.kevRecent(days)
                    persistFindings(report, "KEV")
                    ToolResult(true, report)
                }

                "security_news" -> {
                    val topic = args.optString("topic").ifBlank { null }
                    val limit = args.optInt("limit", 10).coerceIn(1, 20)
                    ToolResult(true, guard.securityNews(topic, limit))
                }

                "web_search" -> {
                    val query = args.optString("query").trim()
                    if (query.isBlank()) return ToolResult(false, "", "Provide a query.")
                    ToolResult(true, guard.webSearch(query))
                }

                "scan_project_secrets" -> {
                    val files = guard.projectFiles()
                    if (files.isEmpty()) return ToolResult(true, "No scannable files in the project.")
                    val hits = ArrayList<SecretScan.Hit>()
                    for ((path, text) in files) hits += SecretScan.scanFile(path, text)
                    if (hits.isEmpty()) ToolResult(true, "Scanned ${files.size} files: no credential shapes found.")
                    else ToolResult(true, buildString {
                        append("Found ").append(hits.size).append(" escaped credential(s) across ").append(files.size).append(" files (values masked):\n")
                        hits.take(40).forEach { append("- ").append(it.file).append(':').append(it.line).append(' ').append(it.kind).append(' ').append(it.masked).append('\n') }
                        append("\nRotate every one of these now: a committed secret is a leaked secret, git history included. Then remove it from the file and load it from an environment variable or Oro's vault instead.")
                    })
                }

                "check_vault" -> ToolResult(true, guard.vaultAudit())

                "trace_incident" -> {
                    val evidence = args.optString("evidence")
                    if (evidence.isBlank()) return ToolResult(false, "", "Provide the evidence text.")
                    val subjects = args.optJSONArray("subjects").toStringList()
                    val dossier = ThreatTrace.analyze(evidence, subjects)
                    val output = StringBuilder(dossier.render())
                    if (args.optBoolean("enrich", true)) {
                        val targets = dossier.indicators
                            .filter { it.kind == ThreatTrace.Kind.URL || it.kind == ThreatTrace.Kind.DOMAIN || it.kind == ThreatTrace.Kind.IPV4 }
                            .distinctBy { it.kind to it.value.lowercase() }
                            .take(3)
                        if (targets.isNotEmpty()) output.append("\nEnrichment (passive public records):\n")
                        targets.forEach { indicator ->
                            output.append("\n## ").append(indicator.kind.label).append(' ').append(indicator.value).append('\n')
                            output.append(guard.intel(indicator.kind.name.lowercase(), indicator.value)).append('\n')
                        }
                    }
                    output.append("\nCase fingerprint: ").append(ThreatTrace.fingerprint(dossier.indicators))
                    ToolResult(true, output.toString())
                }

                "enrich_indicator" -> {
                    val indicator = args.optString("indicator").trim()
                    if (indicator.isBlank()) return ToolResult(false, "", "Provide an indicator.")
                    ToolResult(true, guard.intel(args.optString("kind"), indicator))
                }

                "audit_web_posture" -> {
                    val url = args.optString("url").trim()
                    if (url.isBlank()) return ToolResult(false, "", "Provide the site's url.")
                    val report = guard.webPosture(url)
                    persistFindings(report, "web posture")
                    ToolResult(true, report)
                }

                "audit_dependencies" -> ToolResult(true, guard.dependencyAudit())

                "draft_abuse_report" -> {
                    val kind = args.optString("kind").ifBlank { "abuse" }
                    val evidence = args.optString("evidence")
                    if (evidence.isBlank()) return ToolResult(false, "", "Provide the evidence to quote.")
                    ToolResult(true, guard.abuseReport(kind, args.optString("note"), evidence))
                }

                "read_file" -> {
                    val path = args.optString("path")
                    if (path.isBlank()) return ToolResult(false, "", "Provide path.")
                    ToolResult(true, guard.fileOp("read", path))
                }

                "list_files" -> ToolResult(true, guard.fileOp("list", args.optString("path")))

                "grep" -> {
                    val pattern = args.optString("pattern")
                    if (pattern.isBlank()) return ToolResult(false, "", "Provide a pattern.")
                    ToolResult(true, guard.fileOp("grep", args.optString("path"), content = pattern))
                }

                "write_file" -> {
                    val path = args.optString("path")
                    if (path.isBlank()) return ToolResult(false, "", "Provide path.")
                    ToolResult(true, guard.fileOp(
                        operation = if (args.optBoolean("append", false)) "append" else "write",
                        path = path,
                        content = args.optString("content"),
                    ))
                }

                "edit_file" -> {
                    val path = args.optString("path")
                    val old = args.optString("old")
                    val new = args.optString("new")
                    if (path.isBlank() || old.isBlank()) return ToolResult(false, "", "Provide path and the exact old text.")
                    ToolResult(true, guard.fileOp("edit", path, content = "", old = old, new = new))
                }

                "run_script" -> {
                    val language = args.optString("language")
                    val code = args.optString("code")
                    if (language.isBlank() || code.isBlank()) return ToolResult(false, "", "Provide language and code.")
                    ToolResult(true, guard.runScript(language, code, args.optString("stdin").ifBlank { null }))
                }

                "delegate_investigation" -> {
                    val task = args.optString("task").trim()
                    if (task.isBlank()) return ToolResult(false, "", "Provide the task for the investigators.")
                    ToolResult(true, guard.delegate(task, args.optString("focus").ifBlank { null }))
                }

                "add_finding" -> {
                    val title = args.optString("title").trim()
                    if (title.isBlank()) return ToolResult(false, "", "Provide a title.")
                    val severity = normalizeSeverity(args.optString("severity"))
                    val action = args.optString("action").trim()
                    persistFinding(severity, title, action, "guard")
                    ToolResult(true, "Finding recorded: [$severity] $title -> $action")
                }

                "convene_council" -> {
                    val topic = args.optString("topic")
                    if (topic.isBlank()) return ToolResult(false, "", "Provide topic.")
                    val artifact = args.optString("artifact").ifBlank { null }
                    GuardAgent._state.update { it.copy(status = "Council in session", seats = emptyList()) }
                    val result = guard.council(topic, artifact)
                    GuardAgent.setSeats(result.opinions.map { o -> "${o.modelName}: ${o.failed ?: "${o.findings.size} findings"}" })
                    ToolResult(true, buildString {
                        append("Council verdict (chair: ").append(result.synthesizer).append("):\n").append(result.verdict).append("\n\nFindings:\n")
                        if (result.findings.isEmpty()) append("(none evidenced)\n")
                        result.findings.take(24).forEach { append("- [").append(it.severity.name).append("] ").append(it.title).append(" -> ").append(it.action).append('\n') }
                        if (result.disagreements.isNotEmpty()) {
                            append("\nDisagreements on the record:\n")
                            result.disagreements.forEach { append("- ").append(it).append('\n') }
                        }
                    })
                }

                "ask_user" -> {
                    val question = args.optString("question")
                    if (question.isBlank()) return ToolResult(false, "", "Provide question.")
                    val options = args.optJSONArray("options").toStringList().take(4)
                    val answer = ask(question, options)
                    ToolResult(true, "The user answered: $answer")
                }

                "take_note" -> {
                    val note = args.optString("note").trim()
                    if (note.isNotEmpty()) onNote(note.take(240))
                    ToolResult(true, "Noted.")
                }

                "finish_task" -> {
                    val summary = args.optString("summary").trim()
                    if (summary.isBlank()) return ToolResult(false, "", "Provide summary.")
                    onFinish(summary, args.optBoolean("success", true))
                    ToolResult(true, "Task finished.")
                }

                else -> ToolResult(false, "", "Unknown tool.")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            ToolResult(false, "", "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun normalizeSeverity(raw: String): String = when (raw.trim().lowercase()) {
        "critical", "crit", "p0" -> "CRITICAL"
        "high", "p1" -> "HIGH"
        "medium", "med", "p2" -> "MEDIUM"
        "low", "p3" -> "LOW"
        else -> "INFO"
    }

    private fun persistFindings(report: String, source: String) {
        val ctx = appContext ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                report.lineSequence().forEach { line ->
                    val m = Regex("^\\[(\\w+)]\\s+(.+?)\\s*->\\s*(.+)$").find(line.trim()) ?: return@forEach
                    GuardSettingsStore.addFinding(ctx, m.groupValues[1], m.groupValues[2].take(140), m.groupValues[3].take(240), source)
                }
            }
        }
    }

    private fun persistFinding(severity: String, title: String, action: String, source: String) {
        val ctx = appContext ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { GuardSettingsStore.addFinding(ctx, severity, title.take(140), action.take(240), source) }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        val arr = this ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optString(i)?.takeIf { it.isNotBlank() } }
    }
}
