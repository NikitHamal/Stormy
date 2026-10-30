package com.oro.app.data.security

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * Turns what the user can see -- a mail, a breach notice, headers, a chat log,
 * a wallet address -- into the evidence a defender can act on: indicators,
 * a timeline, and a record with secrets removed.
 *
 * Everything here is pure and offline. It never decides whether a domain is
 * malicious; it says what the artifact claims, and leaves attribution to the
 * intel fetchers and the council. That distinction is what keeps a frightened
 * user from hearing "confirmed attacker" about a typo.
 */
object ThreatTrace {

    enum class Kind(val label: String) {
        URL("url"),
        DOMAIN("domain"),
        IPV4("ip"),
        IPV6("ipv6"),
        EMAIL("email"),
        SHA256("sha256"),
        HASH("hash"),
        CVE("cve"),
        WALLET("wallet"),
    }

    data class Indicator(val kind: Kind, val value: String)

    data class DatedLine(val at: String?, val epochMillis: Long?, val text: String)

    data class Dossier(
        val indicators: List<Indicator>,
        val timeline: List<DatedLine>,
        val subjects: List<String>,
        val notes: List<String>,
    ) {
        fun render(): String = buildString {
            append("Evidence record (").append(indicators.size).append(" indicator(s), ")
                .append(timeline.count { it.epochMillis != null }).append(" dated event(s)).\n")
            if (subjects.isNotEmpty()) {
                append("\nSubjects:\n")
                subjects.forEach { append("- ").append(it).append('\n') }
            }
            if (indicators.isNotEmpty()) {
                append("\nIndicators:\n")
                indicators.groupBy { it.kind }.forEach { (kind, list) ->
                    list.take(30).forEach { append("- ").append(kind.label).append(": ").append(it.value).append('\n') }
                }
            }
            val dated = timeline.filter { it.epochMillis != null }
            if (dated.isNotEmpty()) {
                append("\nTimeline:\n")
                dated.take(40).forEach { append("- ").append(it.at).append("  ").append(it.text.take(240)).append('\n') }
            }
            if (notes.isNotEmpty()) {
                append("\nNotes:\n")
                notes.forEach { append("- ").append(it).append('\n') }
            }
        }
    }

    // ------------------------------------------------------------- extraction

    /** Common TLDs plus any two-letter country code; enough for real indicators, not for prose. */
    private val TLDS = setOf(
        "com", "org", "net", "edu", "gov", "mil", "int", "io", "co", "ai", "app", "dev", "me", "info",
        "biz", "xyz", "top", "site", "online", "shop", "store", "club", "live", "link", "click", "pro",
        "tech", "cloud", "space", "website", "fun", "icu", "vip", "work", "art", "blog", "page", "one",
        "monster", "quest", "cyou", "bond", "rest", "bar", "sbs", "cam", "fit", "beauty", "skin", "makeup",
        "finance", "capital", "bank", "exchange", "money", "pay", "casino", "bet", "win", "poker", "games",
        "uk", "de", "fr", "nl", "be", "ch", "at", "se", "no", "dk", "fi", "pl", "cz", "sk", "hu", "ro",
        "bg", "gr", "pt", "es", "it", "ie", "is", "lu", "lv", "lt", "ee", "ru", "ua", "by", "kz", "tr",
        "in", "pk", "bd", "lk", "np", "cn", "hk", "tw", "jp", "kr", "sg", "my", "id", "ph", "vn", "th",
        "au", "nz", "ca", "us", "mx", "br", "ar", "cl", "co", "pe", "ve", "za", "ng", "ke", "gh", "eg",
        "ma", "tn", "dz", "il", "ae", "sa", "qa", "kw", "om", "bh", "jo", "lb", "ir", "iq", "sy", "af",
        "am", "ge", "az", "uz", "kg", "tj", "tm", "mn", "kh", "la", "mm", "bn", "mv", "bt", "fm", "ws",
        "to", "tv", "fj", "pg", "sb", "vu", "nc", "pf", "gu", "pr", "vi", "do", "ht", "jm", "tt", "bb",
        "bs", "bz", "gt", "hn", "sv", "ni", "cr", "pa", "ec", "bo", "py", "uy", "gy", "sr", "fk", "gi",
        "mt", "cy", "ad", "mc", "sm", "va", "li", "fo", "gl", "ax", "im", "je", "gg", "cw", "aw", "sx",
        "bzh", "cat", "eus", "gal", "scot", "wales", "london", "nyc", "berlin", "tokyo",
    )

    private val RESERVED_DOMAINS = setOf(
        "example.com", "example.org", "example.net", "localhost", "invalid", "test", "local",
        "schema.org", "w3.org", "xmlns.com",
    )

    private val URL_RE = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private val EMAIL_RE = Regex("""\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,24}\b""", RegexOption.IGNORE_CASE)
    private val DOMAIN_RE = Regex("""\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+(?:[a-z]{2,24}|xn--[a-z0-9-]{2,})\b""", RegexOption.IGNORE_CASE)
    private val IPV4_RE = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val IPV6_RE = Regex("""\b(?:[0-9a-f]{1,4}:){3,7}[0-9a-f]{0,4}\b""", RegexOption.IGNORE_CASE)
    private val SHA256_RE = Regex("""(?<![0-9a-f])(?:[0-9a-f]{64})(?![0-9a-f])""", RegexOption.IGNORE_CASE)
    private val MD5_RE = Regex("""(?<![0-9a-f])(?:[0-9a-f]{32})(?![0-9a-f])""", RegexOption.IGNORE_CASE)
    private val CVE_RE = Regex("""\bCVE-\d{4}-\d{4,8}\b""", RegexOption.IGNORE_CASE)
    private val BTC_RE = Regex("""\b(?:bc1[a-z0-9]{25,62}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})\b""")
    private val ETH_RE = Regex("""\b0x[0-9a-fA-F]{40}\b""")

    /**
     * Every indicator in [text], in the order a person would scan them:
     * links, hosts, addresses, hashes and CVEs. Dates and version numbers are
     * explicitly not indicators.
     */
    fun extract(text: String): List<Indicator> {
        val out = LinkedHashMap<String, Indicator>()
        fun add(kind: Kind, raw: String) {
            val value = raw.trim().trimEnd('.', ',', ';', ')', ']', '}', '\'', '"')
            if (value.isEmpty()) return
            val key = kind.name + "|" + value.lowercase(Locale.ROOT)
            out.putIfAbsent(key, Indicator(kind, value))
        }

        URL_RE.findAll(text).forEach { m ->
            val url = m.value
            add(Kind.URL, url)
            runCatching { java.net.URI(url).host }.getOrNull()?.let { add(Kind.DOMAIN, it) }
        }
        EMAIL_RE.findAll(text).forEach { m ->
            add(Kind.EMAIL, m.value)
            add(Kind.DOMAIN, m.value.substringAfter('@'))
        }
        IPV4_RE.findAll(text).forEach { m ->
            if (isPublicIpv4(m.value)) add(Kind.IPV4, m.value)
        }
        IPV6_RE.findAll(text).forEach { m ->
            val v = m.value.trimEnd(':')
            if (v.count { it == ':' } >= 3 && v != "::1") add(Kind.IPV6, v)
        }
        SHA256_RE.findAll(text).forEach { add(Kind.SHA256, it.value) }
        MD5_RE.findAll(text).forEach { add(Kind.HASH, it.value) }
        CVE_RE.findAll(text).forEach { add(Kind.CVE, it.value.uppercase(Locale.ROOT)) }
        BTC_RE.findAll(text).forEach { add(Kind.WALLET, it.value) }
        ETH_RE.findAll(text).forEach { add(Kind.WALLET, it.value) }
        DOMAIN_RE.findAll(text).forEach { m ->
            val domain = m.value.lowercase(Locale.ROOT)
            if (looksLikeDomain(domain)) add(Kind.DOMAIN, domain)
        }
        return out.values.take(120).toList()
    }

    private fun isPublicIpv4(raw: String): Boolean {
        val parts = raw.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val (a, b) = parts[0] to parts[1]
        if (a == 10 || a == 127 || a == 0 || a >= 240) return false
        if (a == 169 && b == 254) return false
        if (a == 172 && b in 16..31) return false
        if (a == 192 && b == 168) return false
        if (a == 100 && b in 64..127) return false
        return true
    }

    private fun looksLikeDomain(domain: String): Boolean {
        if (domain in RESERVED_DOMAINS) return false
        if (domain.startsWith("www.")) return true
        val tld = domain.substringAfterLast('.')
        if (tld.startsWith("xn--")) return true
        if (tld.length == 2) return true
        return tld in TLDS
    }

    // --------------------------------------------------------------- timeline

    private val ISO_DATE = Regex("""\b(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:Z|[+-]\d{2}:?\d{2})?)?\b""")
    private val DMY = Regex("""\b(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{4})\b""", RegexOption.IGNORE_CASE)
    private val MDY = Regex("""\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{1,2}),?\s+(\d{4})\b""", RegexOption.IGNORE_CASE)
    private val SLASH = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{2,4})\b""")

    /**
     * Lines that carry a date, oldest first; undated lines are kept after them
     * (a notice's prose often explains the dated lines). The original date
     * token is preserved so the record reads like the artifact, not like a
     * normalised database.
     */
    fun timeline(text: String): List<DatedLine> {
        val dated = ArrayList<Triple<String, Long, String>>()
        val undated = ArrayList<String>()
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val whenAndToken = firstDate(line)
            if (whenAndToken == null) undated += line
            else dated += Triple(whenAndToken.second, whenAndToken.first, line)
        }
        val sorted = dated.sortedBy { it.second }
        return sorted.map { DatedLine(it.first, it.second, it.third) } +
            undated.take(60).map { DatedLine(null, null, it) }
    }

    private fun firstDate(line: String): Pair<Long, String>? {
        ISO_DATE.find(line)?.let { m ->
            runCatching {
                val date = LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                val time = if (m.groupValues[4].isNotEmpty()) {
                    LocalDateTime.of(date, java.time.LocalTime.of(m.groupValues[4].toInt(), m.groupValues[5].toInt(),
                        m.groupValues[6].toIntOrNull() ?: 0))
                } else date.atStartOfDay()
                return time.toInstant(ZoneOffset.UTC).toEpochMilli() to m.value
            }
        }
        DMY.find(line)?.let { m ->
            val month = monthOf(m.groupValues[2]) ?: return@let
            runCatching {
                val date = LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
                return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() to m.value
            }
        }
        MDY.find(line)?.let { m ->
            val month = monthOf(m.groupValues[1]) ?: return@let
            runCatching {
                val date = LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[2].toInt())
                return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() to m.value
            }
        }
        SLASH.find(line)?.let { m ->
            val year = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            runCatching {
                val date = LocalDate.of(year, m.groupValues[1].toInt(), m.groupValues[2].toInt())
                return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() to m.value
            }
        }
        return null
    }

    private fun monthOf(name: String): Int? = when (name.lowercase(Locale.ROOT).take(3)) {
        "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
        "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
        else -> null
    }

    // -------------------------------------------------------------- redaction

    private val KV_SECRET = Regex(
        """(?i)\b(password|passwd|pwd|secret|token|api[_-]?key|apikey|access[_-]?token|refresh[_-]?token|session[_-]?id|sessionid|auth|private[_-]?key|client[_-]?secret)\b\s*[:=]\s*("?)([^\s"',;]{3,})\2"""
    )
    private val BEARER = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]{8,}""")
    private val AUTH_LINE = Regex("""(?im)^(\s*(?:proxy-)?authorization\s*:\s*).*$""")
    private val COOKIE_LINE = Regex("""(?im)^(\s*(?:set-)?cookie\s*:\s*).*$""")

    /**
     * Masks credential-shaped values in an artifact before it is quoted back
     * to the user or into a model's context. Headers, connection strings and
     * `.env` fragments are the usual carriers; the key survives, the value
     * does not. Whole `Authorization` and `Cookie` header values go first,
     * because a bearer token is two words and a cookie jar is several pairs.
     */
    fun redact(text: String): String {
        var out = AUTH_LINE.replace(text) { m -> m.groupValues[1] + "[redacted]" }
        out = COOKIE_LINE.replace(out) { m -> m.groupValues[1] + "[redacted]" }
        out = BEARER.replace(out, "Bearer [redacted]")
        out = KV_SECRET.replace(out) { m -> "${m.groupValues[1]}: [redacted]" }
        return out
    }

    // ---------------------------------------------------------------- dossier

    /**
     * The complete offline read of an artifact: indicators, a timeline, the
     * subjects the user named, and notes that flag what the text cannot prove.
     */
    fun analyze(text: String, subjects: List<String> = emptyList()): Dossier {
        val clean = redact(text)
        val indicators = extract(clean)
        val timeline = timeline(clean)
        val notes = ArrayList<String>()
        val dated = timeline.count { it.epochMillis != null }
        if (dated == 0) notes += "The artifact carries no machine-readable date; ask for the original message headers instead of a forwarded copy."
        if (indicators.any { it.kind == Kind.DOMAIN } && indicators.none { it.kind == Kind.URL }) {
            notes += "Domains are listed without a full URL; the path and query often carry the payload and should be preserved separately."
        }
        if (indicators.any { it.kind == Kind.WALLET }) {
            notes += "Crypto addresses are included as indicators; a transfer cannot be reversed by reporting alone -- contact the exchange that received it and law enforcement immediately."
        }
        if (clean != text) notes += "Credential-shaped values were redacted before this record was written."
        return Dossier(indicators, timeline, subjects, notes)
    }

    /** Human interval, for "how long was the window" questions. */
    fun span(timeline: List<DatedLine>): String? {
        val stamps = timeline.mapNotNull { it.epochMillis }.sorted()
        if (stamps.size < 2) return null
        val minutes = (stamps.last() - stamps.first()) / 60_000
        return when {
            minutes < 60 -> "$minutes minute(s)"
            minutes < 48 * 60 -> "${minutes / 60} hour(s)"
            else -> "${minutes / (24 * 60)} day(s)"
        }
    }

    /** A stable fingerprint of the case, so two reports of one incident merge. */
    fun fingerprint(indicators: List<Indicator>): String {
        val core = indicators.filter { it.kind != Kind.URL }.map { it.kind.name + ":" + it.value.lowercase(Locale.ROOT) }
            .sorted().take(20).joinToString("|")
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(core.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }
}
