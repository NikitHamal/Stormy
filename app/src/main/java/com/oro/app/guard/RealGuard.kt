package com.oro.app.guard

import android.content.Context
import com.oro.app.browser.BrowserAgent
import com.oro.app.browser.WebVault
import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ReasoningControls
import com.oro.app.data.ai.agent.AgentModelClient
import com.oro.app.data.ai.agent.AgentRequest
import com.oro.app.data.ai.council.CouncilMember
import com.oro.app.data.ai.council.CouncilResult
import com.oro.app.data.ai.council.CouncilRole
import com.oro.app.data.ai.council.SecurityCouncil
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.repository.AiRepository
import com.oro.app.data.security.Advisories
import com.oro.app.data.security.Pwned
import com.oro.app.data.security.Sandbox
import com.oro.app.data.security.SecretScan
import com.oro.app.data.security.ThreatIntel
import com.oro.app.data.security.ThreatTrace
import com.oro.app.data.security.UrlGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The environment-touching half of the guard: the phone, the network, the
 * project, the vault, the sandbox, the models -- behind [GuardAgent.Guard] so
 * the dispatch and the loop stay testable on the JVM.
 *
 * File access is confined to the open project and a private workspace;
 * "workspace/..." is the guard's own scratch space. The sandbox is a
 * disposable third-party runner by default, or the user's own when configured.
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

    // ------------------------------------------------------------ files

    override fun fileOp(operation: String, path: String, content: String, old: String, new: String): String {
        val workspace = File(context.filesDir, "guard/workspace").apply { mkdirs() }.canonicalFile
        val project = projectRoot?.takeIf { it.isDirectory }?.canonicalFile
        val trimmed = path.trim().removePrefix("./")
        val (root, rel) = when {
            trimmed == "workspace" || trimmed.startsWith("workspace/") ->
                workspace to trimmed.removePrefix("workspace").removePrefix("/")
            project != null -> project to trimmed
            else -> workspace to trimmed
        }
        val target = confined(root, rel)
        return when (operation) {
            "read" -> {
                if (!target.isFile) error("No such file: ${display(root, target)}")
                val text = target.readText()
                if (text.contains('\u0000')) error("${display(root, target)} is binary; not reading it into the transcript.")
                val sensitiveName = Regex("""(?i)(^|/)(\.env|\.npmrc|\.pypirc|credentials|secrets?|id_rsa|id_ed25519)(\.|$)|\.(pem|key|p12|pfx)$""")
                    .containsMatchIn(rel)
                val looksSecret = sensitiveName || SecretScan.scanFile(display(root, target), text).isNotEmpty()
                val safe = if (looksSecret) ThreatTrace.redact(text) else text
                val note = if (looksSecret) {
                    "\n\n[Credential-shaped values are masked in this view. Never ask for or print them; rotate an exposed secret and keep the new value in the vault.]"
                } else ""
                safe.take(200_000) +
                    (if (safe.length > 200_000) "\n… (${safe.length - 200_000} more characters; read a narrower path)" else "") +
                    note
            }

            "list" -> {
                val base = if (target.isFile) target.parentFile ?: target else target
                if (!base.isDirectory) error("No such folder: ${display(root, target)}")
                val lines = ArrayList<String>()
                val skip = setOf(".git", "node_modules", "build", ".gradle", "dist", "out", ".next", "__pycache__", ".venv")
                fun walk(dir: File, depth: Int) {
                    if (depth > 3 || lines.size >= 200) return
                    (dir.listFiles() ?: emptyArray()).sortedWith(compareBy({ !it.isDirectory }, { it.name })).forEach { f ->
                        if (lines.size >= 200) return
                        if (f.name in skip) return@forEach
                        val label = display(root, f) + if (f.isDirectory) "/" else "  (${f.length()} B)"
                        lines += label
                        if (f.isDirectory) walk(f, depth + 1)
                    }
                }
                walk(base, 0)
                if (lines.isEmpty()) "No files under ${display(root, target)}." else lines.joinToString("\n")
            }

            "grep" -> {
                val regex = runCatching { Regex(content) }.getOrElse {
                    error("Invalid regular expression: ${it.message}")
                }
                val skip = setOf(".git", "node_modules", "build", ".gradle", "dist", "out", ".next", "__pycache__", ".venv")
                val binary = setOf("png", "jpg", "jpeg", "gif", "webp", "ico", "pdf", "zip", "gz", "jar", "apk", "mp3", "mp4", "woff", "woff2", "ttf", "so", "exe", "class")
                val hits = ArrayList<String>()
                fun scan(file: File) {
                    if (hits.size >= 200 || !file.isFile || file.length() > 1_500_000 || file.extension.lowercase() in binary) return
                    val text = runCatching { file.readText() }.getOrNull() ?: return
                    if (text.contains('\u0000')) return
                    text.lineSequence().forEachIndexed { index, line ->
                        if (hits.size >= 200) return@forEachIndexed
                        if (regex.containsMatchIn(line)) {
                            hits += "${display(root, file)}:${index + 1}: ${ThreatTrace.redact(line.trim()).take(300)}"
                        }
                    }
                }
                val base = if (target.isFile) target.parentFile ?: target else target
                if (!base.isDirectory) error("No such folder: ${display(root, target)}")
                fun walk(dir: File, depth: Int) {
                    if (hits.size >= 200 || depth > 8) return
                    (dir.listFiles() ?: emptyArray()).sortedBy { it.name }.forEach { f ->
                        if (f.name in skip) return@forEach
                        if (f.isDirectory) walk(f, depth + 1) else scan(f)
                    }
                }
                if (target.isFile) scan(target) else walk(base, 0)
                if (hits.isEmpty()) "No matches for ${content.take(80)} in ${display(root, target)}."
                else hits.joinToString("\n") + if (hits.size >= 200) "\n… capped at 200 matches; narrow the pattern." else ""
            }

            "write", "append" -> {
                val blocked = rel.split('/', '\\').any { it in setOf(".git", "node_modules", "build", ".gradle") }
                if (blocked) error("Refusing to write inside a build or VCS folder.")
                if (content.length > 400_000) error("Content is too large (limit 400k characters).")
                target.parentFile?.mkdirs()
                if (operation == "append") target.appendText(content) else target.writeText(content)
                "${if (operation == "append") "Appended to" else "Wrote"} ${display(root, target)} (${content.length} characters)."
            }

            "edit" -> {
                if (!target.isFile) error("No such file: ${display(root, target)}")
                if (old.isEmpty()) error("The old text is empty; use write_file for a full rewrite.")
                val text = target.readText()
                val occurrences = text.split(old).size - 1
                if (occurrences == 0) error("The old text was not found in ${display(root, target)}. Read the file first; do not guess.")
                if (occurrences > 1) error("The old text occurs $occurrences times in ${display(root, target)}. Include more context so the edit is unambiguous.")
                target.writeText(text.replace(old, new))
                "Patched ${display(root, target)} (1 replacement)."
            }

            else -> error("Unknown file operation: $operation")
        }
    }

    private fun confined(root: File, rel: String): File {
        val rootCanon = root.canonicalFile
        val cleanRel = rel.trim().trimStart('/', '\\')
        if (cleanRel.split('/', '\\').any { it == ".." }) throw IllegalArgumentException("Paths may not escape the project or workspace.")
        val target = if (cleanRel.isEmpty()) rootCanon else File(rootCanon, cleanRel).canonicalFile
        val prefix = rootCanon.path + File.separator
        if (target != rootCanon && !target.path.startsWith(prefix)) {
            throw IllegalArgumentException("Path escapes the project or workspace: $rel")
        }
        return target
    }

    private fun display(root: File, file: File): String {
        val rel = file.absolutePath.removePrefix(root.absolutePath).removePrefix(File.separator)
        val workspace = File(context.filesDir, "guard/workspace").canonicalFile
        return if (root == workspace) "workspace/$rel" else rel.ifBlank { "." }
    }

    // -------------------------------------------------------- network

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
        val cutoff = LocalDate.now().minusDays(days.toLong())
        val window = catalog.filter { e -> runCatching { LocalDate.parse(e.dateAdded) >= cutoff }.getOrDefault(false) }
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
        val available = aiRepository.getAvailableModels()
        val seated = ids.mapNotNull { id -> available.firstOrNull { it.id == id } }
        val models = seated.ifEmpty { available.take(3) }
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
        val model = repo.getAvailableModels().firstOrNull { it.id in GuardSettingsStore.DEFAULT_MEMBERS }
            ?: repo.getAvailableModels().firstOrNull()
            ?: return ToolResult(false, "", "No models configured.")
        val r = BrowserAgent.runForResult(context, task, model, repo, BrowserAgent.Settings())
        return if (r.error != null) ToolResult(false, "", r.error)
        else ToolResult(r.success, "browser ${if (r.success) "succeeded" else "failed"}: ${r.summary}")
    }

    // ---------------------------------------------------- investigation

    override suspend fun intel(kind: String, value: String): String = withContext(Dispatchers.IO) {
        val indicator = resolveIndicator(kind, value)
        when (indicator.kind) {
            ThreatTrace.Kind.URL -> {
                val host = runCatching { java.net.URI(indicator.value).host }.getOrNull()
                buildString {
                    append(checkUrl(indicator.value, true))
                    if (host != null) append("\n\n").append(ThreatIntel.render(ThreatIntel.domain(host)))
                }
            }
            ThreatTrace.Kind.DOMAIN -> ThreatIntel.render(ThreatIntel.domain(indicator.value))
            ThreatTrace.Kind.IPV4, ThreatTrace.Kind.IPV6 -> ThreatIntel.render(ThreatIntel.ip(indicator.value))
            ThreatTrace.Kind.EMAIL -> {
                val domain = indicator.value.substringAfter('@', "")
                buildString {
                    append("Email: ").append(indicator.value).append('\n')
                    append("- A breach check for this mailbox needs HIBP's account API (a key); the phone's vault check covers saved passwords without one.\n")
                    append("- Check the mailbox's own sign-in history and recovery options after the domain below.\n\n")
                    if (domain.isNotBlank()) append(ThreatIntel.render(ThreatIntel.domain(domain)))
                }
            }
            ThreatTrace.Kind.SHA256, ThreatTrace.Kind.HASH -> {
                "Hash: ${indicator.value}\nNo keyless malware-reputation source is configured. Preserve the hash with the artifact; it is the identifier that lets a CERT or the provider match the sample later. Never upload a sample that may contain the user's personal data without asking."
            }
            ThreatTrace.Kind.WALLET -> {
                "Wallet: ${indicator.value}\nA crypto transfer cannot be reversed by reporting. Preserve the transaction ids, contact the receiving exchange's compliance team and law enforcement in parallel, and do not pay a second demand to a 'recovery' service."
            }
            ThreatTrace.Kind.CVE -> {
                val catalog = Advisories.kevCatalog()
                val entry = catalog?.firstOrNull { it.cve.equals(indicator.value, true) }
                if (entry != null) {
                    "CVE ${indicator.value} is in CISA's KEV: ${entry.vendor} ${entry.product}, added ${entry.dateAdded}${if (entry.knownRansomware) ", known ransomware use" else ""}.\nPatch it now; exploited-in-the-wild means the exploit exists and is being used."
                } else if (catalog == null) {
                    "CVE ${indicator.value}: the CISA KEV catalog was unreachable, so exploitation status is unknown. Check NVD and the vendor advisory before deciding."
                } else {
                    "CVE ${indicator.value} is not in the current CISA KEV catalog. That is not proof it is harmless: check the vendor advisory and NVD for CVSS, exploitability and fixed versions."
                }
            }
        }
    }

    private fun resolveIndicator(kind: String, raw: String): ThreatTrace.Indicator {
        val value = raw.trim()
        val k = kind.trim().lowercase(Locale.ROOT)
        val extracted = ThreatTrace.extract(value)
        val chosen = when (k) {
            "url" -> extracted.firstOrNull { it.kind == ThreatTrace.Kind.URL }
            "domain" -> extracted.firstOrNull { it.kind == ThreatTrace.Kind.DOMAIN }
            "ip", "ipv4" -> extracted.firstOrNull { it.kind == ThreatTrace.Kind.IPV4 || it.kind == ThreatTrace.Kind.IPV6 }
            "ipv6" -> extracted.firstOrNull { it.kind == ThreatTrace.Kind.IPV6 }
            "email" -> extracted.firstOrNull { it.kind == ThreatTrace.Kind.EMAIL }
            "hash", "sha256", "md5" -> extracted.firstOrNull { it.kind == ThreatTrace.Kind.SHA256 || it.kind == ThreatTrace.Kind.HASH }
            "wallet" -> extracted.firstOrNull { it.kind == ThreatTrace.Kind.WALLET }
            "cve" -> extracted.firstOrNull { it.kind == ThreatTrace.Kind.CVE }
            else -> extracted.firstOrNull()
        }
        return chosen ?: ThreatTrace.Indicator(ThreatTrace.Kind.DOMAIN, ThreatIntel.cleanDomain(value))
    }

    override suspend fun webPosture(url: String): String {
        val posture = ThreatIntel.webPosture(url)
            ?: return "Could not fetch $url. A site that does not answer cannot be assessed; check the URL and try again."
        return posture.render() + "\n\nThis was a passive header and DNS review of the public site. Nothing was probed or changed."
    }

    override suspend fun dependencyAudit(): String {
        val files = projectFiles()
        if (files.isEmpty()) return "No open project to audit."
        val (vulns, notes) = ThreatIntel.dependencies(files)
        val declared = ThreatIntel.declaredDependencies(files)
            .distinctBy { it.first + "|" + it.second + "|" + it.third }.size
        return ThreatIntel.renderDependencies(vulns, notes, declared)
    }

    override suspend fun abuseReport(kind: String, note: String, evidence: String): String {
        val indicators = ThreatTrace.extract(evidence)
        val domain = indicators.firstOrNull { it.kind == ThreatTrace.Kind.DOMAIN }?.value
        val ip = indicators.firstOrNull { it.kind == ThreatTrace.Kind.IPV4 || it.kind == ThreatTrace.Kind.IPV6 }?.value
        val domainReport = domain?.let { runCatching { ThreatIntel.domain(it) }.getOrNull() }
        val ipReport = ip?.let { runCatching { ThreatIntel.ip(it) }.getOrNull() }
        val channels = ThreatIntel.abuseChannels(domainReport, ipReport)
        return ThreatIntel.draftAbuseReport(kind, note, evidence, indicators, channels)
    }

    override suspend fun delegate(task: String, focus: String?): String = coroutineScope {
        val ids = GuardSettingsStore.memberIds(context)
        val available = aiRepository.getAvailableModels()
        val seated = ids.mapNotNull { id -> available.firstOrNull { it.id == id } }
        val models = seated.ifEmpty { available.take(2) }
        if (models.isEmpty()) return@coroutineScope "No models are configured for investigators."
        val foci = if (focus.isNullOrBlank()) listOf("evidence", "countermeasures") else listOf(focus)
        val reports = foci.mapIndexed { index, f ->
            async {
                val model = models[index % models.size]
                val text = GuardInvestigator(this@RealGuard, aiRepository, context).run(task, f, model)
                "### Investigator (${model.name}, $f)\n$text"
            }
        }.awaitAll()
        reports.joinToString("\n\n")
    }

    override suspend fun runScript(language: String, code: String, stdin: String?): String {
        val config = Sandbox.Config(
            runnerUrl = GuardSettingsStore.sandboxRunnerUrl(context),
            runnerToken = GuardSettingsStore.sandboxRunnerToken(context),
        )
        return Sandbox.run(language, code, stdin, config).render()
    }
}
