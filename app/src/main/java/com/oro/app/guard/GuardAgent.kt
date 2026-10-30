package com.oro.app.guard

import android.content.Context
import com.oro.app.OroApplication
import com.oro.app.R
import com.oro.app.browser.BrowserAgent
import com.oro.app.browser.BrowserProse
import com.oro.app.browser.WebVault
import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.ReasoningControls
import com.oro.app.data.ai.agent.AgentLoop
import com.oro.app.data.ai.agent.AgentLoopConfig
import com.oro.app.data.ai.agent.AgentLoopListener
import com.oro.app.data.ai.agent.AgentModelClient
import com.oro.app.data.ai.agent.AgentRequest
import com.oro.app.data.ai.agent.AgentStatus
import com.oro.app.data.ai.agent.AgentRunResult
import com.oro.app.data.ai.agent.AgentToolRunner
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.council.CouncilMember
import com.oro.app.data.ai.council.CouncilResult
import com.oro.app.data.ai.council.CouncilRole
import com.oro.app.data.ai.council.SecurityCouncil
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.BrowserDelegationTools
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.repository.AiRepository
import com.oro.app.data.security.Advisories
import com.oro.app.data.security.Pwned
import com.oro.app.data.security.SecretScan
import com.oro.app.data.security.UrlGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.YearMonth
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * The personal cyber-defence agent: one instance app-wide, like the browser
 * agent it consults. Defensive only -- it audits (phone, vault, project),
 * detects (URLs, advisories, exploitation news), verifies (the council of
 * models) and guides recovery (incident work, with the browser agent on hand
 * to walk the user's own security checkups). It never touches a system the
 * user does not own.
 *
 * Everything environment-touching sits behind the [Guard] interface, so the
 * tool contract and the loop run on the JVM against scripted guards.
 */
object GuardAgent {

    enum class StepKind { THOUGHT, ACTION, QUESTION, NOTE, SYSTEM }
    enum class StepStatus { RUNNING, DONE, FAILED, WAITING }

    data class Step(
        val id: Long,
        val kind: StepKind,
        val tool: String?,
        val title: String,
        val detail: String = "",
        val status: StepStatus = StepStatus.DONE,
        val endedAt: Long? = null,
    )

    data class Question(val text: String, val options: List<String>)

    data class Result(val success: Boolean, val summary: String, val error: String? = null)

    data class State(
        val running: Boolean = false,
        val task: String = "",
        val modelName: String = "",
        val status: String = "",
        val statusKind: AgentStatus.Kind? = null,
        val steps: List<Step> = emptyList(),
        val question: Question? = null,
        val seats: List<String> = emptyList(),
        val result: Result? = null,
        val startedAt: Long = 0,
        val endedAt: Long = 0,
    )

    /** The environment, behind a seam. */
    internal interface Guard {
        fun phoneReport(): String
        fun projectFiles(): List<Pair<String, String>>
        suspend fun checkUrl(url: String, deep: Boolean): String
        suspend fun advisories(pkg: String, eco: String, version: String?): String
        suspend fun kevRecent(days: Int): String
        suspend fun vaultAudit(): String
        suspend fun council(topic: String, artifact: String?): CouncilResult
        suspend fun browserTask(task: String): ToolResult
    }

    internal val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stepSeq = AtomicLong()
    private var job: Job? = null
    private var questionGate: CompletableDeferred<String?>? = null

    internal fun setGate(gate: CompletableDeferred<String?>) { questionGate = gate }
    private val notes = mutableListOf<String>()
    internal var followUps: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    fun queueFollowUp(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) followUps += t
    }

    /** Start a run the user watches on the shield screen. */
    fun start(
        context: Context,
        task: String,
        aiRepository: AiRepository,
        model: AiModel? = null,
        projectRoot: File? = null,
    ): Job? = begin(context, task, aiRepository, model, projectRoot)

    /** Start a run a caller (the coding agent) waits on. */
    suspend fun runForResult(
        context: Context,
        task: String,
        aiRepository: AiRepository,
        model: AiModel? = null,
        projectRoot: File? = null,
    ): Result {
        if (_state.value.running) {
            return Result(false, "", "The guard is busy with another task. Wait for it, or ask the user.")
        }
        val started = begin(context, task, aiRepository, model, projectRoot)
            ?: return Result(false, "", _state.value.result?.error ?: "The guard could not start.")
        try {
            started.join()
        } catch (e: CancellationException) {
            stop()
            throw e
        }
        return _state.value.result ?: Result(false, "", "The guard ended without a verdict.")
    }

    private fun begin(
        context: Context,
        task: String,
        aiRepository: AiRepository,
        model: AiModel?,
        projectRoot: File?,
    ): Job? {
        if (_state.value.running) return null
        val appContext = context.applicationContext
        stepSeq.set(0)
        notes.clear()
        followUps = ConcurrentLinkedQueue()
        questionGate = null
        val chosen = model ?: aiRepository.getAvailableModels().firstOrNull { it.id in GuardSettingsStore.DEFAULT_MEMBERS }
            ?: aiRepository.getAvailableModels().firstOrNull()
        if (chosen == null) {
            _state.value = State(result = Result(false, "", "No models are configured yet."))
            return null
        }
        _state.value = State(
            running = true, task = task.trim(), modelName = chosen.name,
            status = "Standing watch", startedAt = System.currentTimeMillis(),
        )
        val guard = RealGuard(appContext, aiRepository, projectRoot)
        val stream = AgentModelClient { req ->
            aiRepository.streamChat(
                chosen, req.messages, req.tools, temperature = 0.2f,
                // Few, consequential decisions: the model's deepest level.
                reasoningLevel = ReasoningControls.maxLevel(chosen),
            )
        }
        job = scope.launch {
            try {
                GuardRun(guard, stream, appContext, chosen.supportsToolCalls).run(task.trim())
            } catch (e: CancellationException) {
                end(Result(false, "", "Stopped."))
                throw e
            } catch (e: Throwable) {
                end(Result(false, "", e.message ?: "The guard failed."))
            }
        }
        return job
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun clear() {
        stop()
        _state.value = State()
    }

    fun answer(text: String) {
        questionGate?.complete(text)
    }

    internal fun addStep(step: Step) {
        _state.update { it.copy(steps = (it.steps + step).takeLast(400)) }
    }

    internal fun nextId() = stepSeq.incrementAndGet()

    internal fun end(result: Result) {
        _state.update {
            it.copy(
                running = false, question = null, result = result,
                endedAt = System.currentTimeMillis(),
                steps = it.steps.map { s -> if (s.status == StepStatus.RUNNING || s.status == StepStatus.WAITING) s.copy(status = StepStatus.DONE) else s },
            )
        }
    }

    /** Test seam: a scripted guard drives the real tool dispatch. */
    internal suspend fun runScenarioExecute(guard: Guard, name: String, args: String): ToolResult =
        GuardRun(guard, AgentModelClient { throw IllegalStateException("scripted") }, null).execute(name, JSONObject(args))
}

/** One guard run: the loop, the tool dispatch, the question gate. */
internal class GuardRun(
    private val guard: GuardAgent.Guard,
    private val modelStream: AgentModelClient,
    private val appContext: Context?,
    private val nativeTools: Boolean = true,
) {

    private var finishSummary: String? = null
    private var finishSuccess = false
    private var finishCalled = false

    suspend fun run(task: String) {
        val brief = appContext?.getString(R.string.guard_system_brief) ?: FALLBACK_BRIEF
        val tools = GuardTools.all + BrowserDelegationTools.USE_BROWSER
        val conversation = mutableListOf(
            ChatRequestMessage(role = "user", content = "Task: $task\n\nDecide the first step. Audit first, act second, and convene the council for anything worth a second opinion.")
        )

        val runner = AgentToolRunner { call ->
            val name = call.function.name.substringAfterLast('.').lowercase()
            val args = runCatching { JSONObject(call.function.arguments.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            execute(name, args)
        }

        var currentStepId = -1L
        val listener = object : AgentLoopListener {
            override fun onStatus(status: AgentStatus) {
                GuardAgent._state.update { it.copy(status = status.label, statusKind = status.kind) }
            }

            override fun onTurnComplete(visibleText: String, toolCalls: List<ToolCallResponse>) {
                val thought = AgentLoop.stripThinking(visibleText).trim()
                if (thought.isNotEmpty()) {
                    GuardAgent.addStep(GuardAgent.Step(GuardAgent.nextId(), GuardAgent.StepKind.THOUGHT, null, thought.take(400)))
                }
            }

            override fun onToolStarted(call: ToolCallResponse) {
                if (call.function.name == "finish_task") return
                val id = GuardAgent.nextId()
                currentStepId = id
                GuardAgent.addStep(
                    GuardAgent.Step(id, GuardAgent.StepKind.ACTION, call.function.name, humanize(call.function.name),
                        status = GuardAgent.StepStatus.RUNNING)
                )
            }

            override fun onToolFinished(call: ToolCallResponse, result: ToolResult) {
                if (call.function.name == "finish_task") return
                val text = if (result.success) result.output else (result.error ?: result.output)
                val id = currentStepId
                GuardAgent._state.update { st ->
                    st.copy(steps = st.steps.map { s ->
                        if (s.id == id) s.copy(detail = text.take(240), status = if (result.success) GuardAgent.StepStatus.DONE else GuardAgent.StepStatus.FAILED, endedAt = System.currentTimeMillis())
                        else s
                    })
                }
            }
        }

        val prepare: suspend (List<ChatRequestMessage>, Int) -> AgentRequest = { history, _ ->
            AgentRequest(
                listOf(ChatRequestMessage(role = "system", content = brief)) + history,
                if (nativeTools) tools else null
            )
        }

        val loop = AgentLoop(
            client = modelStream,
            tools = runner,
            config = AgentLoopConfig(
                roundNote = { _, _ ->
                    val q = GuardAgent.followUps
                    if (q.isEmpty()) null
                    else "The user sent this while you worked: " + generateSequence { q.poll() }.joinToString(" | ")
                },
                maxIterations = 60,
                maxToolResultChars = 20_000,
                idleTimeoutMs = 120_000,
                maxQualityPasses = 2,
                toolSchemas = AgentTools.schemasOf(tools),
                textResultsFooter = "Continue with the next step. When the question is answered, call finish_task; a plain reply does not end it.",
                cutRepetition = true,
            )
        )

        var asked = 0
        val outcome = loop.run(conversation, GuardTools.names + "use_browser", listener, finalCheck = {
            val said = AgentLoop.stripThinking(conversation.lastOrNull { it.role == "assistant" }?.content.orEmpty())
            when (val kind = BrowserProse.read(said)) {
                is BrowserProse.Finish -> { finishSummary = kind.summary; finishSuccess = kind.success; finishCalled = true; null }
                is BrowserProse.Ask -> {
                    if (asked >= 2) null
                    else {
                        asked++
                        askQuestion(kind.question, emptyList())
                        null
                    }
                }
                else -> null
            }
        }, prepare = prepare)

        when (outcome) {
            is AgentRunResult.Completed ->
                GuardAgent.end(GuardAgent.Result(finishSuccess || finishCalled, finishSummary ?: lastText(conversation), null))
            is AgentRunResult.IterationLimit ->
                GuardAgent.end(GuardAgent.Result(finishSuccess, finishSummary ?: "Out of turns; partial: ${lastText(conversation).take(300)}", null))
            is AgentRunResult.Failed ->
                GuardAgent.end(GuardAgent.Result(false, "", outcome.message))
        }
    }

    private fun lastText(conversation: List<ChatRequestMessage>): String =
        AgentLoop.stripThinking(conversation.lastOrNull { it.role == "assistant" }?.content.orEmpty()).trim()

    /** The tool contract; everything a guard can do lands here. */
    internal suspend fun execute(name: String, args: JSONObject): ToolResult {
        if (name == "use_browser") return guard.browserTask(args.optString("task"))
        if (!GuardTools.names.contains(name)) return ToolResult(false, "", "Unknown guard tool: $name")
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

                "convene_council" -> {
                    val topic = args.optString("topic")
                    if (topic.isBlank()) return ToolResult(false, "", "Provide topic.")
                    val artifact = args.optString("artifact").ifBlank { null }
                    GuardAgent._state.update { it.copy(status = "Council in session", seats = emptyList()) }
                    val result = guard.council(topic, artifact)
                    GuardAgent._state.update { it.copy(seats = result.opinions.map { o -> "${o.modelName}: ${o.failed ?: "${o.findings.size} findings"}" }) }
                    ToolResult(true, buildString {
                        append("Council verdict (chair: ").append(result.synthesizer).append("):\n").append(result.verdict).append("\n\nFindings:\n")
                        if (result.findings.isEmpty()) append("(none evidenced)\n")
                        result.findings.take(12).forEach { append("- [").append(it.severity.name).append("] ").append(it.title).append(" -> ").append(it.action).append('\n') }
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
                    val answer = askQuestion(question, options)
                    ToolResult(true, "The user answered: $answer")
                }

                "take_note" -> {
                    val note = args.optString("note").trim()
                    if (note.isNotEmpty()) {
                        GuardAgent.addStep(GuardAgent.Step(GuardAgent.nextId(), GuardAgent.StepKind.NOTE, null, note.take(240)))
                    }
                    ToolResult(true, "Noted.")
                }

                "finish_task" -> {
                    val summary = args.optString("summary").trim()
                    if (summary.isBlank()) return ToolResult(false, "", "Provide summary.")
                    finishSummary = summary
                    finishSuccess = args.optBoolean("success", true)
                    finishCalled = true
                    ToolResult(true, "Task finished.")
                }

                else -> ToolResult(false, "", "Unknown tool.")
            }
        } catch (t: Throwable) {
            ToolResult(false, "", "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun persistFindings(report: String, source: String) {
        val ctx = appContext ?: return
        scope().launch(Dispatchers.IO) {
            runCatching {
                report.lineSequence().forEach { line ->
                    val m = Regex("^\\[(\\w+)]\\s+(.+?)\\s*->\\s*(.+)$").find(line.trim()) ?: return@forEach
                    GuardSettingsStore.addFinding(ctx, m.groupValues[1], m.groupValues[2].take(140), m.groupValues[3].take(240), source)
                }
            }
        }
    }

    private fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Blocks the run until the user answers on the shield screen. */
    private suspend fun askQuestion(text: String, options: List<String>): String {
        val gate = CompletableDeferred<String?>()
        GuardAgent.setGate(gate)
        val id = GuardAgent.nextId()
        GuardAgent.addStep(GuardAgent.Step(id, GuardAgent.StepKind.QUESTION, "ask_user", text, status = GuardAgent.StepStatus.WAITING))
        GuardAgent._state.update { it.copy(question = GuardAgent.Question(text, options)) }
        val answer = gate.await()
        GuardAgent._state.update { it.copy(question = null) }
        GuardAgent._state.update { st ->
            st.copy(steps = st.steps.map { s -> if (s.id == id) s.copy(status = GuardAgent.StepStatus.DONE, detail = (answer ?: "(no answer)").take(200)) else s })
        }
        return answer?.takeIf { it.isNotBlank() } ?: "The user did not answer; make the safe assumption and say so."
    }

    private fun JSONArray?.toStringList(): List<String> {
        val arr = this ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optString(i)?.takeIf { it.isNotBlank() } }
    }

    private fun humanize(name: String): String = when (name) {
        "audit_phone" -> "Auditing this phone's posture"
        "check_url" -> "Judging a link"
        "lookup_advisory" -> "Checking advisories"
        "kev_recent" -> "Reading the exploitation news"
        "scan_project_secrets" -> "Scanning the project for escaped secrets"
        "check_vault" -> "Auditing saved logins"
        "convene_council" -> "Convening the council"
        "use_browser" -> "Working in the browser"
        "ask_user" -> "Asking you"
        else -> name
    }

    companion object {
        const val FALLBACK_BRIEF = "You are Oro's personal cyber-defence agent: audit, detect, verify, recover. Defensive only."
    }
}

/**
 * The environment-touching half of the guard: the phone, the network, the
 * vault, the models -- behind [GuardAgent.Guard] so the other half stays
 * testable.
 */
internal class RealGuard(
    private val context: Context,
    private val aiRepository: AiRepository,
    private val projectRoot: File?,
) : GuardAgent.Guard {

    override fun phoneReport(): String {
        val facts = PhoneAudit.collect(context)
        val now = YearMonth.now()
        val findings = PhoneAudit.report(facts, now.year, now.monthValue)
        return buildString {
            append("Device posture (Android ").append(facts.sdkInt).append(", patch ").append(facts.securityPatch).append("):\n")
            findings.forEach { (sev, title, action) ->
                append("[").append(sev.name).append("] ").append(title).append(" -> ").append(action).append('\n')
            }
        }
    }

    override fun projectFiles(): List<Pair<String, String>> {
        val root = projectRoot ?: return emptyList()
        if (!root.isDirectory) return emptyList()
        val skipDirs = setOf(".git", "node_modules", "build", ".gradle", "dist", "out", ".next", "__pycache__", ".venv")
        val binaryExt = setOf("png", "jpg", "jpeg", "gif", "webp", "ico", "pdf", "zip", "gz", "jar", "apk", "mp3", "mp4", "woff", "woff2", "ttf", "eot", "bin", "so", "exe", "class")
        val out = ArrayList<Pair<String, String>>()
        fun walk(dir: File, depth: Int) {
            if (depth > 8 || out.size >= 600) return
            for (f in (dir.listFiles() ?: emptyArray()).sortedBy { it.name }) {
                if (f.isDirectory) {
                    if (f.name !in skipDirs && !f.name.startsWith(".")) walk(f, depth + 1)
                } else if (f.isFile && f.length() < 1_500_000 && f.extension.lowercase() !in binaryExt) {
                    val text = runCatching { f.readText() }.getOrNull() ?: continue
                    if (text.contains('\u0000')) continue
                    out += f.absolutePath.removePrefix(root.absolutePath).removePrefix("/") to text
                }
            }
        }
        walk(root, 0)
        return out
    }

    override suspend fun checkUrl(url: String, deep: Boolean): String = withContext(Dispatchers.IO) {
        val verdict = UrlGuard.check(url)
        val certNote = if (deep && verdict.host != null) probeTls(url) else null
        buildString {
            append(verdict.render(url))
            if (!certNote.isNullOrBlank()) append("\nTLS: ").append(certNote).append('\n')
        }
    }

    private fun probeTls(url: String): String? = runCatching {
        val client = AiHttp.client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()
        val request = okhttp3.Request.Builder().url(url).head().build()
        client.newCall(request).execute().use { r ->
            val chain = r.handshake?.peerCertificates ?: return@use "no certificate presented"
            val leaf = chain.first() as? java.security.cert.X509Certificate ?: return@use "unreadable certificate"
            val daysLeft = (leaf.notAfter.time - System.currentTimeMillis()) / 86_400_000
            val issuer = leaf.issuerX500Principal.name.substringAfter("CN=").substringBefore(",")
            val sans = runCatching { leaf.subjectAlternativeNames?.mapNotNull { (it.getOrNull(1) as? String) }.orEmpty() }.getOrDefault(emptyList())
            val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
            val nameOk = sans.any { it.equals(host, true) || (it.startsWith("*.") && host.endsWith(it.drop(1), true)) }
            buildString {
                if (daysLeft < 0) append("EXPIRED ").append(abs(daysLeft)).append(" days ago. ")
                else if (daysLeft < 30) append("expires in ").append(daysLeft).append(" days. ")
                if (sans.isNotEmpty() && !nameOk) append("Certificate covers ").append(sans.take(4).joinToString()).append(", not ").append(host).append(". ")
                else append("Name match OK. ")
                append("Issuer: ").append(issuer).append('.')
            }
        }
    }.getOrNull()

    override suspend fun advisories(pkg: String, eco: String, version: String?): String {
        val list = Advisories.osvQuery(eco, pkg, version)
            ?: return "OSV could not be reached; say nothing about advisories rather than guess."
        if (list.isEmpty()) return "No known advisories for $eco:$pkg${version?.let { " $it" } ?: ""} in OSV."
        return buildString {
            append(list.size).append(" known advisory(ies) for ").append(eco).append(':').append(pkg).append(' ')
                .append(version ?: "(all versions)").append(":\n")
            list.take(12).forEach { a ->
                append("- ").append(a.id)
                if (a.aliases.isNotEmpty()) append(" (").append(a.aliases.take(3).joinToString()).append(')')
                if (a.severity != null) append(" [").append(a.severity).append(']')
                append(": ").append(a.summary).append('\n')
            }
        }
    }

    override suspend fun kevRecent(days: Int): String {
        val catalog = Advisories.kevCatalog() ?: return "CISA's KEV catalog could not be reached right now."
        val cutoff = java.time.LocalDate.now().minusDays(days.toLong())
        val window = catalog.filter { e -> runCatching { java.time.LocalDate.parse(e.dateAdded) >= cutoff }.getOrDefault(false) }
        if (window.isEmpty()) return "No new exploited-in-the-wild entries in the last $days days."
        val seen = GuardSettingsStore.kevSeen(context)
        val fresh = window.filter { it.cve !in seen }
        GuardSettingsStore.markKevSeen(context, fresh.map { it.cve }.toSet())
        return buildString {
            append(fresh.size).append(" new (").append(window.size).append(" total in window) KEV entries, ransomware-linked first:\n")
            (if (fresh.isNotEmpty()) fresh else window)
                .sortedWith(compareByDescending<Advisories.KevEntry> { it.knownRansomware }.thenByDescending { it.dateAdded })
                .take(15).forEach { e ->
                    append("- ").append(e.dateAdded).append(' ').append(e.cve).append(' ').append(e.vendor).append(' ').append(e.product)
                    if (e.knownRansomware) append(" [ransomware-linked]")
                    append('\n')
                }
        }
    }

    override suspend fun vaultAudit(): String {
        runCatching { WebVault.init(context) }
        val entries = runCatching { WebVault.list() }.getOrDefault(emptyList())
        if (entries.isEmpty()) return "The vault holds no logins yet -- nothing to audit."
        val hashes = HashSet<String>()
        val results = ArrayList<String>()
        var breached = 0
        var reused = 0
        for (e in entries.take(25)) {
            val secret = runCatching { WebVault.get(e.id)?.password }.getOrNull() ?: continue
            val sha = Pwned.sha1Hex(secret)
            if (!hashes.add(sha)) {
                reused++
                results += "- ${e.domain}: password REUSED on another saved site -- unique it"
            } else {
                when (val seen = Pwned.passwordSeenCount(secret)) {
                    null -> results += "- ${e.domain}: breach status unknown (the oracle was unreachable)"
                    0 -> results += "- ${e.domain}: not seen in known breaches"
                    else -> {
                        breached++
                        results += "- ${e.domain}: seen in $seen breach(es) -- rotate now"
                    }
                }
            }
        }
        return buildString {
            append("Vault audit (").append(entries.size).append(" login(s); passwords never left the device -- only five hash characters did):\n")
            results.forEach { append(it).append('\n') }
            if (breached > 0 || reused > 0) append("\n$breached breached, $reused reused. Rotate those first, make every password unique, and turn on 2FA wherever it exists.")
        }
    }

    override suspend fun council(topic: String, artifact: String?): CouncilResult {
        val ids = GuardSettingsStore.memberIds(context)
        val seated = ids.mapNotNull { id -> aiRepository.getAvailableModels().firstOrNull { it.id == id } }
        val models = seated.ifEmpty { aiRepository.getAvailableModels().take(3) }
        val members = models.mapIndexed { i, m -> CouncilMember(m, CouncilRole.entries[i % CouncilRole.entries.size]) }
        val council = SecurityCouncil { m ->
            AgentModelClient { req: AgentRequest ->
                aiRepository.streamChat(m, req.messages, req.tools, temperature = 0.2f, reasoningLevel = ReasoningControls.maxLevel(m))
            }
        }
        return council.deliberate(topic, artifact, members)
    }

    override suspend fun browserTask(task: String): ToolResult {
        if (task.isBlank()) return ToolResult(false, "", "Provide task.")
        val repo = aiRepository
        val model = repo.getAvailableModels().firstOrNull { it.id in GuardSettingsStore.DEFAULT_MEMBERS } ?: repo.getAvailableModels().firstOrNull()
            ?: return ToolResult(false, "", "No models configured.")
        val r = BrowserAgent.runForResult(
            context, task, model, repo,
            BrowserAgent.Settings(),
        )
        return if (r.error != null) ToolResult(false, "", r.error)
        else ToolResult(r.success, "browser ${if (r.success) "succeeded" else "failed"}: ${r.summary}")
    }
}
