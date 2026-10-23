package com.oro.app.data.ai.eval

import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProviderManager
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.ProviderRateLimiter
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.VisionSupport
import com.oro.app.data.ai.agent.AgentLoop
import com.oro.app.data.ai.agent.AgentLoopConfig
import com.oro.app.data.ai.agent.AgentLoopListener
import com.oro.app.data.ai.agent.AgentModelClient
import com.oro.app.data.ai.agent.AgentRequest
import com.oro.app.data.ai.agent.AgentRunResult
import com.oro.app.data.ai.agent.AgentStatus
import com.oro.app.data.ai.agent.AgentToolRunner
import com.oro.app.data.ai.agent.HistoryPruner
import com.oro.app.data.ai.agent.TokenEstimator
import com.oro.app.data.ai.craft.QualityAuditor
import com.oro.app.data.ai.craft.QualityGate
import com.oro.app.data.ai.genui.GenUi
import com.oro.app.data.ai.search.ProjectSearch
import com.oro.app.data.ai.search.WebDocs
import com.oro.app.data.ai.skills.Skills
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.CraftTools
import com.oro.app.data.ai.tools.JsonParserUtils
import com.oro.app.data.ai.tools.ProjectTools
import com.oro.app.data.ai.tools.ToolAliases
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.ai.tools.WorkspaceTools
import com.oro.app.data.preview.Device
import com.oro.app.data.preview.EmbeddedDevServer
import com.oro.app.data.repository.AiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** One model on one task: the label names the model and the call protocol. */
internal data class EvalSpec(val modelId: String, val textMode: Boolean, val task: EvalTask) {
    val label: String get() = modelId.substringAfterLast('/').replace(':', '_') + if (textMode) "@text" else "@native"
}

/**
 * One live agent run, the way EditorViewModel runs it: the real system prompt
 * (project context snapshotted once, skills catalogue, generative-UI note),
 * the main agent's tool set, HistoryPruner on the model's window, the quality
 * gate as the final check, and tools dispatched like ToolExecutor -- file tools
 * through [ProjectTools], previews through headless Chromium on Oro's own dev
 * server. Only what needs a person is simulated (ask_user, approvals).
 */
internal class EvalRun(
    private val spec: EvalSpec,
    private val model: AiModel,
    private val dir: File,
    private val browser: EvalBrowser,
    private val maxTurns: Int,
) {
    private val project = File(dir, "project").apply { deleteRecursively(); mkdirs() }
    private val ws = WorkspaceTools(project)
    private val skillsDir = File(dir, ".skills").apply { mkdirs() }
    private val touched = ConcurrentHashMap.newKeySet<String>()
    private val transcript = StringBuilder()
    private val raw = StringBuilder()
    private var baseUrl = ""
    @Volatile private var forceText = spec.textMode

    // Measurements.
    private var turns = 0
    private var retries = 0
    private var toolCalls = 0
    private var toolErrors = 0
    private val errorSamples = mutableListOf<String>()
    private val toolCounts = linkedMapOf<String, Int>()
    private var asked = 0
    private var promptTokens = 0L
    private var completionTokens = 0L
    private val discards = mutableListOf<String>()

    suspend fun run(): JsonObject {
        val server = EmbeddedDevServer(project)
        val port = server.start()
        baseUrl = "http://127.0.0.1:$port/"
        val started = System.currentTimeMillis()
        try {
            val result = agentRun()
            val seconds = (System.currentTimeMillis() - started) / 1000
            File(dir, "transcript.md").writeText(transcript.toString())
            File(dir, "raw.md").writeText(raw.toString())
            return measure(result, seconds)
        } finally {
            server.stop()
        }
    }

    // ------------------------------------------------------------- the run

    private suspend fun agentRun(): Pair<AgentRunResult, List<ChatRequestMessage>> {
        val offered = AgentTools.forMainAgent()
        val known = offered.map { it.function.name }.toSet()
        val context = "Project: ${spec.task.projectName}\n\nThe project is empty." + GenUi.PROMPT +
            Skills.catalogue(Skills.all(skillsDir, project))
        val total = model.contextLength.takeIf { it > 0 } ?: 128_000
        val window = (total - (total / 8).coerceIn(2_000, 32_000)).coerceAtLeast(2_048)
        val manager = AiProviderManager()

        val prepare: suspend (List<ChatRequestMessage>, Int) -> AgentRequest = { history, pressure ->
            val textMode = forceText || !model.supportsToolCalls
            val system = AiRepository.systemMessage(context, agentMode = true, nativeTools = !textMode, toolList = offered)
            val tools = if (textMode) null else offered
            val fixed = TokenEstimator.estimate(system) + (tools?.sumOf {
                TokenEstimator.estimate(it.function.name) + TokenEstimator.estimate(it.function.description) +
                    TokenEstimator.estimate(it.function.parameters.toString())
            } ?: 0)
            val share = when (pressure) { 0 -> 0.9; 1 -> 0.6; else -> 0.35 }
            val budget = ((window * share).toInt() - fixed).coerceAtLeast(2_000)
            AgentRequest(listOf(system) + HistoryPruner.prune(history, budget), tools)
        }

        val client = AgentModelClient { request ->
            ProviderRateLimiter.shared.acquire(model.provider, TokenEstimator.estimate(request.messages))
            val stream = tap(manager.streamChatCompletion(model, request.messages, request.tools, temperature = 0.5f))
            if (request.tools == null) stream else detectToolRejection(stream)
        }

        val runner = AgentToolRunner { call -> runTool(call) }
        val listener = object : AgentLoopListener {
            private val text = StringBuilder()
            override fun onModelCallStarted(iteration: Int, attempt: Int) {
                turns = iteration
                if (attempt > 1) retries++
                text.setLength(0)
            }
            override fun onText(delta: String) { text.append(delta) }
            override fun onAttemptDiscarded(reason: String) {
                discards += reason.take(160)
                transcript.append("_discarded (").append(text.length).append(" chars streamed): ").append(reason.take(300)).append("_\n")
                flush()
            }
            override fun onStatus(status: AgentStatus) {
                if (status.kind == AgentStatus.Kind.RETRYING) {
                    transcript.append("_status: ").append(status.label).append("_\n")
                    flush()
                }
            }
            override fun onTurnComplete(visibleText: String, toolCalls: List<ToolCallResponse>) {
                transcript.append("\n### Turn ").append(turns).append('\n')
                if (visibleText.isNotBlank()) transcript.append(visibleText.take(2_500)).append('\n')
                toolCalls.forEach { c ->
                    transcript.append("- CALL ").append(c.function.name).append(' ')
                        .append(c.function.arguments.take(260).replace('\n', ' ')).append('\n')
                }
                raw.append("\n===== turn ").append(turns).append(" (").append(rawTurn.length).append(" chars streamed)\n")
                    .append(if (rawTurn.length > 9_000) rawTurn.substring(0, 4_500) + "\n…\n" + rawTurn.substring(rawTurn.length - 4_500) else rawTurn)
                    .append('\n')
                flush()
            }
            override fun onToolFinished(call: ToolCallResponse, result: ToolResult) {
                val name = ToolAliases.canonicalName(call.function.name)
                this@EvalRun.toolCalls++
                toolCounts[name] = (toolCounts[name] ?: 0) + 1
                val body = if (result.success) result.output else "ERROR " + (result.error ?: "") + " " + result.output
                transcript.append("  → ").append(name).append(": ").append(body.take(420).replace('\n', ' ')).append('\n')
                flush()
                if (!result.success) {
                    toolErrors++
                    if (errorSamples.size < 12) errorSamples += "$name: ${(result.error ?: result.output).take(200)}"
                } else if (name !in AgentTools.readOnly) {
                    touched += QualityGate.touchedPaths(call)
                }
            }
            override fun onUsage(promptTokens: Int, completionTokens: Int) {
                this@EvalRun.promptTokens += promptTokens
                this@EvalRun.completionTokens += completionTokens
            }
        }

        val history = mutableListOf(ChatRequestMessage(role = "user", content = spec.task.prompt))
        val loop = AgentLoop(
            client, runner,
            AgentLoopConfig(
                maxIterations = maxTurns,
                attachImages = VisionSupport.supports(model),
                toolSchemas = AgentTools.schemasOf(offered)
            )
        )
        val result = try {
            loop.run(history, known, listener, { QualityGate.review(project, touched) }, prepare)
        } catch (e: Exception) {
            AgentRunResult.Failed("crashed: ${e.javaClass.simpleName}: ${e.message}", retryable = false)
        }
        transcript.append("\n## Result\n").append(result.toString().take(2_000)).append('\n')
        return result to history.toList()
    }

    /** Everything the model streamed, markup included (the loop shows only the prose before a call). */
    private val rawTurn = StringBuilder()

    private fun tap(stream: Flow<StreamEvent>): Flow<StreamEvent> = flow {
        rawTurn.setLength(0)
        stream.collect { event ->
            when (event) {
                is StreamEvent.ContentDelta -> rawTurn.append(event.content)
                is StreamEvent.ToolCalls -> event.toolCalls.forEach { c ->
                    rawTurn.append("\n[native call] ").append(c.function.name).append(' ').append(c.function.arguments.take(400))
                }
                is StreamEvent.FinishReason -> rawTurn.append("\n[finish: ").append(event.reason).append(']')
                is StreamEvent.Error -> rawTurn.append("\n[error: ").append(event.message.take(300)).append(']')
                else -> Unit
            }
            emit(event)
        }
    }

    /** As EditorViewModel: a 400 about tools switches the run to the text protocol. */
    private fun detectToolRejection(stream: Flow<StreamEvent>): Flow<StreamEvent> = flow {
        stream.collect { event ->
            if (event is StreamEvent.Error && !forceText && event.httpCode in setOf(400, 404, 422) &&
                Regex("tool|function", RegexOption.IGNORE_CASE).containsMatchIn(event.message)
            ) {
                forceText = true
                emit(StreamEvent.Error("Model rejected native tools; switching to text tool calls", retryAfterMs = 500, retryable = true))
            } else emit(event)
        }
    }

    private fun flush() {
        runCatching {
            File(dir, "transcript.md").writeText(transcript.toString())
            File(dir, "raw.md").writeText(raw.toString())
        }
    }

    // ------------------------------------------------------------- tools

    private suspend fun runTool(call: ToolCallResponse): ToolResult {
        ProjectTools.execute(project, ws, call)?.let { return it }
        val name = ToolAliases.canonicalName(call.function.name)
        val args = JsonParserUtils.parseToJsonObject(call.function.arguments) ?: JsonObject(emptyMap())
        fun s(k: String) = (args[k] as? JsonPrimitive)?.contentOrNull
        fun page() = s("page")?.trim()?.trimStart('/')?.ifEmpty { null } ?: "index.html"
        return when (name) {
            "check_syntax" -> CraftTools.check(
                project, s("path")?.trim()?.removePrefix("./")?.trimStart('/')?.takeIf { it.isNotEmpty() && it != "." }
            ) { null }
            "add_ui_kit" -> CraftTools.addUiKit(project, args).result
            "use_skill" -> {
                val wanted = Skills.slug(s("name").orEmpty())
                val all = Skills.all(skillsDir, project)
                val skill = all.firstOrNull { it.name == wanted }
                    ?: return ToolResult(false, "", "No skill named '$wanted'. Available: ${all.joinToString(", ") { it.name }}")
                val starter = if (skill.files.isEmpty()) "" else buildString {
                    append("\n\n# Starter files\nWrite each with write_file (skip or merge any that already exist in the project), then adapt them to the request.\n")
                    skill.files.forEach { (path, content) -> append("\n## ").append(path).append("\n```\n").append(content.trimEnd()).append("\n```\n") }
                }
                ToolResult(true, "Skill ${skill.name}: ${skill.description}\n\n${skill.body}$starter")
            }
            "docs_lookup" -> ToolResult(true, WebDocs.format(WebDocs.lookup(s("query").orEmpty())))
            "search_project" -> {
                val hits = ProjectSearch(project).search(s("query").orEmpty(), s("limit")?.toIntOrNull()?.coerceIn(1, 20) ?: 8)
                ToolResult(true, if (hits.isEmpty()) "No matches." else hits.joinToString("\n\n") { "${it.path}:${it.startLine}-${it.endLine}\n${it.snippet}" })
            }
            "web_fetch" -> ws.webFetch(args).result
            "screenshot_preview" -> {
                val page = page()
                if (!File(project, page).isFile) return ToolResult(false, "", "$page does not exist in the project.")
                val shots = browser.capture(baseUrl, page, Device.parse(s("device")))
                ToolResult(true, shots.joinToString("\n\n") { "## ${it.device} (${it.width}x${it.height})\n${it.metrics}" })
            }
            "audit_page" -> {
                val page = page()
                if (!File(project, page).isFile) return ToolResult(false, "", "$page does not exist in the project.")
                ToolResult(true, browser.audit(baseUrl, page))
            }
            "get_preview_errors" -> ToolResult(true, browser.errors(baseUrl, "index.html"))
            "ask_user" -> {
                asked++
                ToolResult(true, "User answered: I'm away for now -- make the sensible choices yourself and keep going.")
            }
            "request_approval" -> ToolResult(true, "The user approved: ${s("action").orEmpty()}")
            "update_plan" -> ToolResult(true, "Plan updated.")
            "save_memory" -> ToolResult(true, "Saved.")
            "spawn_subagent" -> ToolResult(false, "", "Subagents are not available in this run; do the work yourself.")
            else -> ToolResult(false, "", "$name is not available here.")
        }
    }

    // ------------------------------------------------------------- scoring

    private fun measure(run: Pair<AgentRunResult, List<ChatRequestMessage>>, seconds: Long): JsonObject {
        val (result, history) = run
        val users = history.filter { it.role == "user" }.mapNotNull { it.content }
        fun count(prefix: String) = users.count { it.startsWith(prefix) }
        val files = project.walkTopDown().filter { it.isFile && !it.path.contains("/.oro/") }.toList()
        val audit = QualityAuditor(project).audit(null)
        val entry = if (File(project, "index.html").isFile) "index.html"
            else files.firstOrNull { it.extension == "html" }?.relativeTo(project)?.path
        val url = entry?.let { baseUrl + it }
        val shots = File(dir, "shots").apply { mkdirs() }
        val phone = url?.let { browser.run("shot", it, "390", "844", File(shots, "phone.jpg").path, "1") }
        val desktopTop = url?.let { browser.run("shot", it, "1280", "800", File(shots, "desktop_top.jpg").path, "0") }
        url?.let { browser.run("shot", it, "1280", "800", File(shots, "desktop_full.jpg").path, "1") }
        val pageAudit = url?.let { browser.run("audit", it) }
        val smoke = url?.let {
            when (spec.task) {
                EvalTask.APP -> browser.run("interact", it, "Eval habit 7")
                EvalTask.GAME -> browser.run("play", it)
                EvalTask.LANDING -> null
            }
        }
        val phoneMetrics = phone?.get("metrics") as? JsonObject
        fun mi(k: String) = (phoneMetrics?.get(k) as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        val runtimeErrors = (phone?.let { with(EvalBrowser) { it.list("pageErrors") + it.list("console") } } ?: emptyList()) +
            (desktopTop?.let { with(EvalBrowser) { it.list("pageErrors") } } ?: emptyList())
        return buildJsonObject {
            put("label", spec.label)
            put("model", spec.modelId)
            put("task", spec.task.name.lowercase())
            put("outcome", when (result) {
                is AgentRunResult.Completed -> "done"
                is AgentRunResult.Failed -> "failed"
                is AgentRunResult.IterationLimit -> "turn cap"
            })
            put("detail", when (result) {
                is AgentRunResult.Completed -> result.finalText.take(400)
                is AgentRunResult.Failed -> result.message.take(400)
                is AgentRunResult.IterationLimit -> "${result.iterations} turns"
            })
            put("seconds", seconds)
            put("turns", turns)
            put("retries", retries)
            put("textProtocol", forceText || !model.supportsToolCalls)
            put("toolCalls", toolCalls)
            put("toolErrors", toolErrors)
            put("toolErrorSamples", buildJsonArray { errorSamples.forEach { add(JsonPrimitive(it)) } })
            put("tools", buildJsonObject { toolCounts.forEach { (k, v) -> put(k, v) } })
            put("malformedNudges", count("Your last reply contained a tool call that could not be read"))
            put("truncatedNudges", count("Your reply ended in the middle of"))
            put("lengthContinuations", count("Your previous reply was cut off by the output limit"))
            put("reasoningOnlyNudges", count("[Oro: your last replies contained only reasoning"))
            put("qualityGatePasses", count("Automatic quality review"))
            put("askedUser", asked)
            put("discards", buildJsonArray { discards.take(10).forEach { add(JsonPrimitive(it)) } })
            put("promptTokens", promptTokens)
            put("completionTokens", completionTokens)
            put("files", buildJsonArray { files.forEach { add(JsonPrimitive("${it.relativeTo(project).path} ${it.length()}")) } })
            put("auditErrors", audit.issues.count { it.severity == QualityAuditor.Severity.ERROR })
            put("auditWarnings", audit.issues.count { it.severity == QualityAuditor.Severity.WARNING })
            put("auditTips", audit.issues.count { it.severity == QualityAuditor.Severity.TIP })
            put("auditSample", buildJsonArray { audit.blocking.take(8).forEach { add(JsonPrimitive("${it.severity} ${it.path}: ${it.message}".take(200))) } })
            put("pageIssues", (pageAudit?.get("issues") as? kotlinx.serialization.json.JsonArray)?.size ?: -1)
            put("runtimeErrors", buildJsonArray { runtimeErrors.distinct().take(10).forEach { add(JsonPrimitive(it.take(200))) } })
            put("phoneOverflow", mi("sw") > mi("vw") + 1)
            put("phoneHeight", mi("sh"))
            put("brokenImages", mi("brokenImages"))
            put("hiddenShown", mi("hiddenShown"))
            put("textChars", mi("textChars"))
            smoke?.let { put("smoke", it) }
        }
    }
}
