package com.oro.app.data.security

import com.oro.app.data.ai.AiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Passive, keyless open-source intelligence for a defender. Every source here
 * answers a plain HTTPS GET with no account: RDAP registry records, DNS over
 * HTTPS, certificate transparency, urlscan's public scan index and IP
 * ownership. Nothing in this file contacts the suspicious host itself -- the
 * only active request is a TLS handshake already performed by [UrlGuard]'s
 * deep check, and the header read in [webPosture], which is limited to a site
 * the user owns.
 *
 * Parsers are pure, so the network can fail without the verdicts becoming
 * fiction: a missing source is reported as missing.
 */
object ThreatIntel {

    // ------------------------------------------------------------------ data

    data class Entity(val role: String, val name: String?, val email: String?)

    data class DomainReport(
        val domain: String,
        val registrar: String? = null,
        val abuseEmail: String? = null,
        val created: String? = null,
        val expires: String? = null,
        val updated: String? = null,
        val statuses: List<String> = emptyList(),
        val nameservers: List<String> = emptyList(),
        val dns: Map<String, List<String>> = emptyMap(),
        val dnssec: Boolean = false,
        val subdomains: List<String> = emptyList(),
        val certCount: Int = 0,
        val firstCert: String? = null,
        val lastCert: String? = null,
        val scans: Int = 0,
        val maliciousScans: Int = 0,
        val lastScan: String? = null,
        val scanSamples: List<String> = emptyList(),
        val notes: List<String> = emptyList(),
    ) {
        val isNew: Boolean
            get() = runCatching {
                val year = created?.take(4)?.toIntOrNull() ?: return@runCatching false
                year >= java.time.LocalDate.now().year - 1
            }.getOrDefault(false)
    }

    data class IpReport(
        val ip: String,
        val org: String? = null,
        val country: String? = null,
        val city: String? = null,
        val hostname: String? = null,
        val netblock: String? = null,
        val abuseEmail: String? = null,
        val notes: List<String> = emptyList(),
    )

    data class ScanSummary(
        val scans: Int,
        val malicious: Int,
        val lastScan: String?,
        val samples: List<String>,
    )

    data class SubdomainSummary(
        val names: List<String>,
        val certCount: Int,
        val first: String?,
        val last: String?,
    )

    data class PostureFinding(val severity: String, val title: String, val action: String)

    data class WebPosture(
        val url: String,
        val finalUrl: String,
        val status: Int,
        val headers: Map<String, String>,
        val findings: List<PostureFinding>,
        val spf: Boolean,
        val dmarc: Boolean,
        val dnssec: Boolean,
    ) {
        fun render(): String = buildString {
            append("Web posture for ").append(finalUrl).append(" (HTTP ").append(status).append("):\n")
            if (findings.isEmpty()) append("- No missing header or mail-auth control was found.\n")
            findings.forEach { append("[").append(it.severity).append("] ").append(it.title).append(" -> ").append(it.action).append('\n') }
            append("\nMail authentication: SPF ").append(if (spf) "present" else "MISSING")
                .append(", DMARC ").append(if (dmarc) "present" else "MISSING")
                .append(", DNSSEC ").append(if (dnssec) "validated" else "not validated").append('.')
        }
    }

    data class DepVuln(
        val name: String,
        val ecosystem: String,
        val version: String?,
        val advisories: List<Advisories.Advisory>,
    )

    data class AbuseChannel(val label: String, val contact: String, val purpose: String)

    // ----------------------------------------------------------------- HTTP

    private val http: OkHttpClient by lazy {
        AiHttp.client.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun get(url: String, accept: String = "application/json"): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Oro-Shield/1.0 (personal security agent; passive OSINT)")
                .header("Accept", accept)
                .get()
                .build()
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val length = r.body?.contentLength() ?: -1
                if (length > 6_000_000) return@use null
                r.body?.string()?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun obj(json: String): JsonObject? =
        runCatching { AiHttp.json.parseToJsonElement(json) as? JsonObject }.getOrNull()

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonPrimitive.text(): String? = contentOrNull?.takeIf { it.isNotBlank() }

    // --------------------------------------------------------------- parsers

    /** RDAP domain record: events, statuses, nameservers, registrar and abuse contacts. */
    fun parseRdapDomain(json: String, domain: String): DomainReport? {
        val root = obj(json) ?: return null
        val events = root.arr("events").orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { e ->
            val action = e.str("eventAction") ?: return@mapNotNull null
            action to (e.str("eventDate") ?: return@mapNotNull null)
        }.toMap()
        val statuses = root.arr("status").orEmpty().mapNotNull { (it as? JsonPrimitive)?.text() }
        val nameservers = root.arr("nameservers").orEmpty().mapNotNull { it as? JsonObject }
            .mapNotNull { it.str("ldhName")?.lowercase(Locale.ROOT) }
        val entities = ArrayList<Entity>()
        collectEntities(root.arr("entities"), entities)

        val registrar = entities.firstOrNull { it.role.equals("registrar", true) }?.name
        val abuse = entities.firstOrNull { it.email?.contains("abuse", true) == true }?.email
            ?: entities.firstOrNull { it.role.equals("abuse", true) }?.email
        return DomainReport(
            domain = domain,
            registrar = registrar,
            abuseEmail = abuse,
            created = events["registration"],
            expires = events["expiration"],
            updated = events["last changed"] ?: events["last update of RDAP database"],
            statuses = statuses,
            nameservers = nameservers,
        )
    }

    private fun collectEntities(array: JsonArray?, into: MutableList<Entity>) {
        array?.forEach { el ->
            val e = el as? JsonObject ?: return@forEach
            val roles = e.arr("roles").orEmpty().mapNotNull { (it as? JsonPrimitive)?.text() }
            var name: String? = null
            var email: String? = null
            e.arr("vcardArray")?.let { vcard ->
                val props = vcard.getOrNull(1) as? JsonArray
                props?.forEach { p ->
                    val row = p as? JsonArray ?: return@forEach
                    val key = (row.getOrNull(0) as? JsonPrimitive)?.text() ?: return@forEach
                    val value = (row.getOrNull(3) as? JsonPrimitive)?.text() ?: return@forEach
                    when (key.lowercase(Locale.ROOT)) {
                        "fn", "org" -> if (name == null) name = value
                        "email" -> if (email == null) email = value
                    }
                }
            }
            roles.forEach { role -> into += Entity(role, name, email) }
            if (roles.isEmpty() && (name != null || email != null)) into += Entity("", name, email)
            collectEntities(e.arr("entities"), into)
        }
    }

    /** RDAP IP record: netblock, org name and abuse contact. */
    fun parseRdapIp(json: String, ip: String): IpReport? {
        val root = obj(json) ?: return null
        val entities = ArrayList<Entity>()
        collectEntities(root.arr("entities"), entities)
        val abuse = entities.firstOrNull { it.email?.contains("abuse", true) == true }?.email
            ?: entities.firstOrNull { it.role.equals("abuse", true) }?.email
        val org = root.str("name")
            ?: entities.firstOrNull { it.role.equals("registrant", true) }?.name
        val start = root.str("startAddress")
        val end = root.str("endAddress")
        return IpReport(
            ip = ip,
            org = org,
            country = root.str("country"),
            netblock = if (start != null && end != null) "$start - $end" else null,
            abuseEmail = abuse,
        )
    }

    /** DoH JSON (`Status`, `AD`, `Answer[]`) into type -> values. */
    fun parseDns(json: String, type: String): List<String> {
        val root = obj(json) ?: return emptyList()
        return root.arr("Answer").orEmpty().mapNotNull { el ->
            val a = el as? JsonObject ?: return@mapNotNull null
            val data = a.str("data") ?: return@mapNotNull null
            data.trim().trimEnd('.').removeSurrounding("\"")
        }
    }

    /** Certificate transparency rows into unique hostnames plus coverage dates. */
    fun parseCrtSh(json: String, domain: String): SubdomainSummary {
        val root = runCatching { AiHttp.json.parseToJsonElement(json) as? JsonArray }.getOrNull()
            ?: return SubdomainSummary(emptyList(), 0, null, null)
        val names = LinkedHashSet<String>()
        var first: String? = null
        var last: String? = null
        var count = 0
        root.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            count++
            val notBefore = o.str("not_before")
            val notAfter = o.str("not_after")
            if (notBefore != null && (first == null || notBefore < first!!)) first = notBefore
            if (notAfter != null && (last == null || notAfter > last!!)) last = notAfter
            o.str("name_value")?.lineSequence()?.forEach { raw ->
                val name = raw.trim().lowercase(Locale.ROOT).removePrefix("*.")
                if (name.endsWith(".$domain", true) || name == domain) {
                    if (name.length < 120) names += name
                }
            }
        }
        return SubdomainSummary(names.toList().sorted().take(120), count, first, last)
    }

    /** urlscan search rows into a verdict summary. */
    fun parseUrlscan(json: String): ScanSummary {
        val root = obj(json) ?: return ScanSummary(0, 0, null, emptyList())
        val results = root.arr("results").orEmpty()
        var malicious = 0
        var last: String? = null
        val samples = ArrayList<String>()
        results.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val task = o.obj("task")
            val page = o.obj("page")
            val verdicts = o.obj("verdicts")?.obj("overall")
            val isBad = (verdicts?.get("malicious") as? JsonPrimitive)?.contentOrNull?.toBoolean() == true ||
                ((verdicts?.get("score") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0) > 0
            if (isBad) malicious++
            val time = task?.str("time")
            if (time != null && (last == null || time > last!!)) last = time
            (page?.str("url") ?: task?.str("url"))?.let { if (samples.size < 5) samples += it }
        }
        return ScanSummary(results.size, malicious, last, samples)
    }

    /** ipinfo fallback: org, geo and reverse name. */
    fun parseIpInfo(json: String): IpReport? {
        val root = obj(json) ?: return null
        if (root.str("bogon") != null) return null
        return IpReport(
            ip = root.str("ip").orEmpty(),
            org = root.str("org"),
            country = root.str("country"),
            city = root.str("city"),
            hostname = root.str("hostname"),
        )
    }

    // -------------------------------------------------------------- lookups

    /** Passive dossier for a domain; every missing source becomes a note, not a fake negative. */
    suspend fun domain(domain: String): DomainReport = coroutineScope {
        val clean = cleanDomain(domain)
        val rdapJob = async { get("https://rdap.org/domain/$clean", "application/rdap+json")?.let { parseRdapDomain(it, clean) } }
        val dnsJob = async { dnsAll(clean) }
        val crtJob = async { get("https://crt.sh/?q=%25.$clean&output=json")?.let { parseCrtSh(it, clean) } }
        val scanJob = async { get("https://urlscan.io/api/v1/search/?q=domain:$clean&size=10")?.let { parseUrlscan(it) } }

        val rdap = rdapJob.await()
        val dns = dnsJob.await()
        val crt = crtJob.await()
        val scan = scanJob.await()
        val notes = ArrayList<String>()
        if (rdap == null) notes += "RDAP did not answer for $clean; registrar and abuse contact are unknown."
        if (dns.records.isEmpty()) notes += "DoH returned no records; the domain may be parked, down, or the resolver was unreachable."
        if (crt == null) notes += "Certificate transparency search failed; subdomains may be incomplete."
        if (scan == null) notes += "urlscan's public index was unreachable; no third-party scan verdict is included."
        (rdap ?: DomainReport(clean)).copy(
            dns = dns.records,
            dnssec = dns.dnssec,
            subdomains = crt?.names.orEmpty(),
            certCount = crt?.certCount ?: 0,
            firstCert = crt?.first,
            lastCert = crt?.last,
            scans = scan?.scans ?: 0,
            maliciousScans = scan?.malicious ?: 0,
            lastScan = scan?.lastScan,
            scanSamples = scan?.samples.orEmpty(),
            notes = notes,
        )
    }

    /** Passive dossier for an IP: RDAP first, ipinfo only as a fallback for the org. */
    suspend fun ip(address: String): IpReport = coroutineScope {
        val clean = address.trim().removeSurrounding("[", "]")
        val rdapJob = async { get("https://rdap.org/ip/$clean", "application/rdap+json")?.let { parseRdapIp(it, clean) } }
        val infoJob = async { get("https://ipinfo.io/$clean/json")?.let { parseIpInfo(it) } }
        val rdap = rdapJob.await()
        val info = infoJob.await()
        val base = rdap ?: info ?: IpReport(clean)
        base.copy(
            org = base.org ?: info?.org,
            country = base.country ?: info?.country,
            city = base.city ?: info?.city,
            hostname = info?.hostname,
            notes = if (rdap == null) listOf("RDAP did not answer; ownership comes from ipinfo only.") else emptyList(),
        )
    }

    private data class DnsResult(val records: Map<String, List<String>>, val dnssec: Boolean)

    private suspend fun dnsAll(domain: String): DnsResult = coroutineScope {
        val types = listOf("A", "AAAA", "MX", "NS", "TXT", "CAA")
        val answers = types.map { type ->
            async {
                val json = get("https://cloudflare-dns.com/dns-query?name=$domain&type=$type", "application/dns-json")
                    ?: get("https://dns.google/resolve?name=$domain&type=$type")
                val parsed = json?.let { parseDns(it, type) }.orEmpty()
                val dnssec = json?.let { obj(it)?.get("AD")?.let { ad -> (ad as? JsonPrimitive)?.contentOrNull == "true" } } ?: false
                type to (parsed to dnssec)
            }
        }.awaitAll()
        val records = answers.mapNotNull { (type, pair) -> pair.first.takeIf { it.isNotEmpty() }?.let { type to it } }.toMap()
        DnsResult(records, answers.any { it.second.second })
    }

    /** SPF, DMARC and a header/transport review of a site the user owns. */
    suspend fun webPosture(url: String): WebPosture? = withContext(Dispatchers.IO) {
        val clean = normalizeUrl(url) ?: return@withContext null
        val host = runCatching { URI(clean).host }.getOrNull() ?: return@withContext null
        val request = Request.Builder().url(clean)
            .header("User-Agent", "Oro-Shield/1.0 (posture check on a site the operator owns)")
            .header("Accept", "text/html,application/xhtml+xml")
            .get()
            .build()
        val response = runCatching { http.newCall(request).execute() }.getOrNull() ?: return@withContext null
        response.use { r ->
            val headers = r.headers.names().associateWith { name -> r.headers.values(name).joinToString("; ") }
            val finalUrl = r.request.url.toString()
            val findings = ArrayList<PostureFinding>()
            fun missing(header: String, severity: String, title: String, action: String) {
                if (headers.keys.none { it.equals(header, true) }) findings += PostureFinding(severity, title, action)
            }
            missing("Strict-Transport-Security", "MEDIUM", "HSTS is not set",
                "Add Strict-Transport-Security: max-age=31536000; includeSubDomains so the first request never travels over HTTP.")
            val csp = headers.entries.firstOrNull { it.key.equals("Content-Security-Policy", true) }?.value
            if (csp == null) {
                findings += PostureFinding("HIGH", "No Content-Security-Policy",
                    "Add a CSP that defaults to 'self' and allows only the origins the site actually loads; it is the control that turns an injected script into a blocked request.")
            } else {
                if (!csp.contains("frame-ancestors", true) && headers.keys.none { it.equals("X-Frame-Options", true) }) {
                    findings += PostureFinding("MEDIUM", "Clickjacking is not blocked",
                        "Add frame-ancestors 'none' to the CSP, or X-Frame-Options: DENY.")
                }
                if (csp.contains("'unsafe-inline'", true)) {
                    findings += PostureFinding("MEDIUM", "CSP allows inline scripts",
                        "Replace 'unsafe-inline' with a nonce or hash; otherwise any HTML injection executes.")
                }
            }
            if (headers.keys.none { it.equals("X-Content-Type-Options", true) }) {
                findings += PostureFinding("LOW", "MIME sniffing is not disabled",
                    "Add X-Content-Type-Options: nosniff.")
            }
            if (headers.keys.none { it.equals("Referrer-Policy", true) }) {
                findings += PostureFinding("LOW", "No Referrer-Policy",
                    "Add Referrer-Policy: strict-origin-when-cross-origin so URLs with tokens are not leaked to third parties.")
            }
            if (headers.keys.none { it.equals("Permissions-Policy", true) }) {
                findings += PostureFinding("LOW", "No Permissions-Policy",
                    "Add Permissions-Policy denying camera, microphone and geolocation unless the app uses them.")
            }
            val server = headers.entries.firstOrNull { it.key.equals("Server", true) }?.value
            if (server != null && Regex("""\d+\.\d+""").containsMatchIn(server)) {
                findings += PostureFinding("LOW", "The server advertises its version",
                    "Suppress the version banner; it tells an attacker which exploit to try first.")
            }
            val powered = headers.entries.firstOrNull { it.key.equals("X-Powered-By", true) }?.value
            if (powered != null) {
                findings += PostureFinding("LOW", "X-Powered-By discloses the stack",
                    "Remove X-Powered-By; it adds nothing for users and fingerprints the backend.")
            }
            val cors = headers.entries.firstOrNull { it.key.equals("Access-Control-Allow-Origin", true) }?.value
            if (cors == "*") {
                findings += PostureFinding("MEDIUM", "CORS is open to every origin",
                    "Restrict Access-Control-Allow-Origin to the origins that need it, and never combine '*' with credentials.")
            }
            val cookies = r.headers.values("Set-Cookie")
            cookies.forEach { cookie ->
                val name = cookie.substringBefore('=')
                if (!cookie.contains("Secure", true)) {
                    findings += PostureFinding("HIGH", "Cookie $name is not Secure",
                        "Mark it Secure so it never travels over plain HTTP.")
                }
                if (!cookie.contains("HttpOnly", true)) {
                    findings += PostureFinding("MEDIUM", "Cookie $name is readable by scripts",
                        "Mark session cookies HttpOnly unless JavaScript genuinely needs them.")
                }
                if (!cookie.contains("SameSite", true)) {
                    findings += PostureFinding("MEDIUM", "Cookie $name has no SameSite",
                        "Set SameSite=Lax or Strict to blunt cross-site request forgery.")
                }
            }
            if (clean.startsWith("http://", true) && finalUrl.startsWith("https://", true)) {
                findings += PostureFinding("MEDIUM", "Plain HTTP is not redirected at the edge",
                    "Serve a 301 to HTTPS at the first hop; relying on a client-side redirect leaves the first request exposed.")
            }

            val hostDns = dnsAll(host)
            val dmarcDns = dnsAll("_dmarc.$host")
            val txt = ((hostDns.records["TXT"] ?: emptyList()) + (dmarcDns.records["TXT"] ?: emptyList())).joinToString(" ")
            val spf = txt.contains("v=spf1", true)
            val dmarc = txt.contains("v=DMARC1", true)
            if (!spf) findings += PostureFinding("MEDIUM", "No SPF record", "Publish an SPF record naming the hosts that send mail for $host.")
            if (!dmarc) findings += PostureFinding("HIGH", "No DMARC record", "Publish _dmarc.$host with p=quarantine (then reject) so spoofed mail from this domain is refused.")
            val dnssec = hostDns.dnssec
            if (!dnssec) findings += PostureFinding("LOW", "DNSSEC is not validated", "Enable DNSSEC at the registrar; it stops DNS answers from being forged in transit.")
            WebPosture(clean, finalUrl, r.code, headers, findings.sortedBy { severityRank(it.severity) }, spf, dmarc, dnssec)
        }
    }

    private fun severityRank(s: String) = when (s.uppercase(Locale.ROOT)) {
        "CRITICAL" -> 0; "HIGH" -> 1; "MEDIUM" -> 2; "LOW" -> 3; else -> 4
    }

    // ------------------------------------------------------- dependency audit

    /**
     * Reads manifests the user already has and asks OSV about their declared
     * versions. Only the vulnerable packages come back; an unreachable OSV
     * yields an empty list plus a note in the renderer, never "clean".
     */
    suspend fun dependencies(files: List<Pair<String, String>>): Pair<List<DepVuln>, List<String>> = coroutineScope {
        val unique = declaredDependencies(files).distinctBy { it.first + "|" + it.second + "|" + it.third }.take(40)
        val notes = ArrayList<String>()
        if (unique.isEmpty()) notes += "No package.json, requirements.txt or go.mod was found in the open project."
        val results = ArrayList<DepVuln>()
        val failed = java.util.concurrent.atomic.AtomicInteger(0)
        unique.chunked(5).forEach { chunk ->
            val found = chunk.map { (eco, name, version) ->
                async {
                    val list = Advisories.osvQuery(eco, name, version)
                    if (list == null) { failed.incrementAndGet(); null }
                    else list.takeIf { it.isNotEmpty() }?.let { DepVuln(name, eco, version, it) }
                }
            }.awaitAll()
            results += found.filterNotNull()
        }
        if (failed.get() > 0) notes += "${failed.get()} package lookup(s) could not reach OSV; those packages are unverified, not clean."
        Pair(results, notes)
    }

    /** Declared packages from the manifests in [files], capped and deduplicated by the caller. */
    fun declaredDependencies(files: List<Pair<String, String>>): List<Triple<String, String, String?>> {
        val deps = ArrayList<Triple<String, String, String?>>()
        files.forEach { (path, text) ->
            when {
                path.endsWith("package.json", true) -> deps += npmDeps(text)
                path.endsWith("requirements.txt", true) -> deps += pipDeps(text)
                path.endsWith("go.mod", true) -> deps += goDeps(text)
            }
        }
        return deps
    }

    private fun npmDeps(json: String): List<Triple<String, String, String?>> {
        val root = obj(json) ?: return emptyList()
        val out = ArrayList<Triple<String, String, String?>>()
        listOf("dependencies", "devDependencies").forEach { section ->
            root.obj(section)?.forEach { (name, value) ->
                val raw = (value as? JsonPrimitive)?.contentOrNull ?: return@forEach
                if (raw.startsWith("file:", true) || raw.startsWith("git", true) || raw.startsWith("workspace:", true)) return@forEach
                val exact = raw.trim().removePrefix("=").takeIf { Regex("""^\d+\.\d+\.\d+([-+].+)?$""").matches(it) }
                out += Triple("npm", name, exact)
            }
        }
        return out
    }

    private fun pipDeps(text: String): List<Triple<String, String, String?>> =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("-") }
            .mapNotNull { line ->
                val clean = line.substringBefore(';').substringBefore('#').trim()
                val name = clean.substringBefore("==").substringBefore(">=").substringBefore("~=").substringBefore("[")
                    .substringBefore("=").trim()
                if (name.isEmpty()) return@mapNotNull null
                val version = Regex("""==\s*([0-9][0-9A-Za-z.\-+]*)""").find(clean)?.groupValues?.get(1)
                Triple("PyPI", name, version)
            }.toList()

    private fun goDeps(text: String): List<Triple<String, String, String?>> =
        text.lineSequence().map { it.trim() }
            .mapNotNull { line ->
                if (!line.startsWith("require ") && !Regex("""^[a-z0-9.-]+\.[a-z]{2,}/\S+\s+v""").containsMatchIn(line)) return@mapNotNull null
                val body = line.removePrefix("require ").trim().removePrefix("(").trim()
                val parts = body.split(Regex("""\s+"""))
                if (parts.size < 2 || !parts[1].startsWith("v")) return@mapNotNull null
                Triple("Go", parts[0], parts[1])
            }.toList()

    // ---------------------------------------------------------------- abuse

    /** Where a defender can lawfully report this infrastructure, best first. */
    fun abuseChannels(domain: DomainReport?, ip: IpReport?): List<AbuseChannel> {
        val out = ArrayList<AbuseChannel>()
        domain?.abuseEmail?.let { out += AbuseChannel("Registrar abuse", it, "The registrar can suspend the domain; quote its registration date and the abuse evidence.") }
        ip?.abuseEmail?.let { out += AbuseChannel("Hosting abuse", it, "The host can take the server offline; include the exact URL and timestamps in UTC.") }
        out += AbuseChannel("Google Safe Browsing", "https://safebrowsing.google.com/safebrowsing/report_phish/", "Flags the URL in Chrome and Android browsers.")
        out += AbuseChannel("Microsoft Report", "https://www.microsoft.com/en-us/wdsi/support/report-unsafe-site", "Flags the URL in Edge and SmartScreen.")
        out += AbuseChannel("APWG", "reportphishing@apwg.org", "The Anti-Phishing Working Group feeds many banks and CERTs.")
        out += AbuseChannel("IC3", "https://www.ic3.gov/", "US federal complaint channel; use for fraud and money loss.")
        out += AbuseChannel("CISA", "https://www.cisa.gov/report", "US incident reporting; also routes critical-infrastructure incidents.")
        return out
    }

    /**
     * A ready-to-send abuse report. Evidence is redacted first, timestamps are
     * normalised to UTC, and the requested action is explicit -- a report that
     * asks for "investigation" gets queued; one that asks to suspend a
     * confirmed phishing URL gets acted on.
     */
    fun draftAbuseReport(
        kind: String,
        userNote: String,
        evidence: String,
        indicators: List<ThreatTrace.Indicator>,
        channels: List<AbuseChannel>,
    ): String = buildString {
        val clean = ThreatTrace.redact(evidence)
        append("Subject: Abuse report — ").append(kind.ifBlank { "malicious activity" }).append(" — evidence attached\n\n")
        append("To the abuse desk,\n\n")
        append("I am reporting ").append(kind.ifBlank { "malicious activity" }).append(" that targets me directly. ")
        append("The material below is preserved as received; secrets have been removed.\n\n")
        if (userNote.isNotBlank()) append("Summary: ").append(userNote.trim()).append("\n\n")
        append("Indicators:\n")
        indicators.take(40).forEach { append("- ").append(it.kind.label).append(": ").append(it.value).append('\n') }
        append("\nRequested action: investigate and suspend the infrastructure above if it is confirmed malicious.\n")
        append("Please preserve the account and access logs for the window described; I am also reporting this to the relevant CERT.\n\n")
        append("Evidence:\n").append(clean.take(12_000)).append("\n\n")
        append("Please reply with a case number. I am available for any further logs.\n")
        if (channels.isNotEmpty()) {
            append("\n--\nCopy these channels in the same order: ")
            append(channels.joinToString("; ") { "${it.label} <${it.contact}>" })
        }
    }

    // ------------------------------------------------------------- rendering

    fun render(report: DomainReport): String = buildString {
        append("Domain dossier: ").append(report.domain).append('\n')
        report.registrar?.let { append("- Registrar: ").append(it).append('\n') }
        report.created?.let {
            append("- Registered: ").append(it)
            if (report.isNew) append("  (NEW — domain age is a strong phishing signal)")
            append('\n')
        }
        report.expires?.let { append("- Expires: ").append(it).append('\n') }
        if (report.statuses.isNotEmpty()) append("- Registry status: ").append(report.statuses.take(6).joinToString()).append('\n')
        if (report.nameservers.isNotEmpty()) append("- Nameservers: ").append(report.nameservers.take(4).joinToString()).append('\n')
        if (report.abuseEmail != null) append("- Abuse contact: ").append(report.abuseEmail).append('\n')
        if (report.dns.isNotEmpty()) {
            append("\nDNS:\n")
            report.dns.forEach { (type, values) ->
                append("- ").append(type).append(": ").append(values.take(4).joinToString()).append('\n')
            }
            append("- DNSSEC: ").append(if (report.dnssec) "validated" else "not validated").append('\n')
        }
        if (report.certCount > 0) {
            append("\nCertificates: ").append(report.certCount).append(" row(s)")
            report.firstCert?.let { append(", coverage from ").append(it.take(10)) }
            report.lastCert?.let { append(" to ").append(it.take(10)) }
            append(".\n")
            if (report.subdomains.isNotEmpty()) append("Subdomains: ").append(report.subdomains.take(20).joinToString(", ")).append('\n')
        }
        if (report.scans > 0) {
            append("\nThird-party scans (urlscan): ").append(report.scans).append(" found, ")
                .append(report.maliciousScans).append(" flagged malicious")
            report.lastScan?.let { append(", last ").append(it.take(10)) }
            append(".\n")
            report.scanSamples.take(3).forEach { append("- ").append(it).append('\n') }
        }
        if (report.notes.isNotEmpty()) {
            append("\nCaveats:\n")
            report.notes.forEach { append("- ").append(it).append('\n') }
        }
    }

    fun render(report: IpReport): String = buildString {
        append("IP dossier: ").append(report.ip).append('\n')
        report.org?.let { append("- Owner: ").append(it).append('\n') }
        (report.country ?: report.city)?.let { append("- Location: ").append(listOfNotNull(report.city, report.country).joinToString(", ")).append('\n') }
        report.hostname?.let { append("- Reverse name: ").append(it).append('\n') }
        report.netblock?.let { append("- Netblock: ").append(it).append('\n') }
        report.abuseEmail?.let { append("- Abuse contact: ").append(it).append('\n') }
        if (report.notes.isNotEmpty()) report.notes.forEach { append("- Note: ").append(it).append('\n') }
    }

    fun renderDependencies(vulns: List<DepVuln>, notes: List<String>, scanned: Int): String = buildString {
        append("Dependency audit: ").append(scanned).append(" declared package(s) checked against OSV.\n")
        if (vulns.isEmpty()) {
            append("- No known advisories for the versions declared in the manifests.\n")
        } else {
            vulns.forEach { v ->
                append("- ").append(v.ecosystem).append(':').append(v.name)
                    .append(v.version?.let { " $it" } ?: " (version not pinned)")
                    .append(" — ").append(v.advisories.size).append(" advisory(ies): ")
                    .append(v.advisories.take(3).joinToString("; ") { "${it.id}: ${it.summary.take(120)}" })
                    .append('\n')
            }
        }
        notes.forEach { append("- ").append(it).append('\n') }
    }

    fun cleanDomain(raw: String): String {
        val trimmed = raw.trim()
        val host = runCatching { URI(if (trimmed.contains("://")) trimmed else "https://$trimmed").host }.getOrNull()
            ?: trimmed
        return host.lowercase(Locale.ROOT).removePrefix("www.").trimEnd('.')
    }

    fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return runCatching { URI(withScheme) }.getOrNull()
            ?.takeIf { it.host != null && (it.scheme == "http" || it.scheme == "https") }
            ?.toString()
    }
}
