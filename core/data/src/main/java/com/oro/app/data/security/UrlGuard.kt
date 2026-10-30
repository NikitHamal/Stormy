package com.oro.app.data.security

import java.net.URI

/**
 * Phishing and malicious-URL heuristics, pure and offline: the first read of
 * any link the user or an agent is about to trust. No blocklist service is
 * involved (they need keys and phone home); these are the structural tells
 * that survive every campaign: where the brand name sits in the host, what
 * the host pretends to be, and how the URL hides its destination.
 *
 * A score, not a verdict: [Verdict.risk] buckets it, and the reasons are
 * written for a person to read, because the point is teaching the reflex,
 * not replacing it. The council can weigh in on top of this.
 */
object UrlGuard {

    enum class Risk { LOW, CAUTION, DANGEROUS }

    data class Verdict(val score: Int, val reasons: List<String>, val host: String?) {
        val risk: Risk get() = when {
            score >= 60 -> Risk.DANGEROUS
            score >= 25 -> Risk.CAUTION
            else -> Risk.LOW
        }

        fun render(url: String): String = buildString {
            append(when (risk) {
                Risk.LOW -> "No structural red flags in "
                Risk.CAUTION -> "CAUTION — "
                Risk.DANGEROUS -> "DANGEROUS — "
            })
            append(url).append(" (score ").append(score).append("/100)")
            if (reasons.isNotEmpty()) {
                append('\n')
                reasons.forEach { append("- ").append(it).append('\n') }
            }
        }
    }

    /** Brands attackers impersonate most; kept short and obvious on purpose. */
    private val BRANDS = listOf(
        "paypal", "google", "apple", "microsoft", "amazon", "facebook", "instagram",
        "whatsapp", "telegram", "binance", "coinbase", "github", "netflix", "spotify",
        "linkedin", "twitter", "dropbox", "adobe", "steam", "discord", "meta",
    )

    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "buff.ly", "ow.ly",
        "rb.gy", "short.link", "cutt.ly", "rebrand.ly", "s.id", "linkly.link",
    )

    /** TLDs that dominate abuse statistics; a flag, never a verdict alone. */
    private val RARE_TLDS = setOf(
        "zip", "mov", "top", "xyz", "club", "work", "tk", "ml", "ga", "cf", "gq",
        "icu", "bond", "cam", "rest", "click", "link", "loan", "men", "stream",
    )

    /** Unicode lookalikes of ASCII letters, the homograph kit. */
    private val CONFUSABLES = mapOf(
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y',
        'х' to 'x', 'і' to 'i', 'ѕ' to 's', 'ԁ' to 'd', 'Տ' to 's', 'ɡ' to 'g',
    )

    fun check(url: String): Verdict {
        val reasons = ArrayList<String>()
        var score = 0
        val uri = runCatching { URI(url.trim()) }.getOrNull()
        if (uri == null) return Verdict(70, listOf("The URL does not parse; treat anything unparsable as hostile."), null)

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            return Verdict(40, listOf("Not a web link ($scheme): the browser would hand it to another app."), uri.host)
        }
        if (scheme == "http") {
            score += 30; reasons += "No TLS: everything typed into this page travels in clear text."
        }
        val rawHost = uri.host ?: return Verdict(60, listOf("No host in the URL; the destination is hidden in the path."), null)
        val host = rawHost.lowercase()

        // 1. An IP literal as the host: no identity at all.
        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)) {
            score += 45; reasons += "The host is a raw IP address: no domain, no identity, no reputation to check."
        }

        // 2. Punycode / homographs: a host that looks like a brand in another alphabet.
        if (host.startsWith("xn--")) {
            score += 35; reasons += "Punycode host ($rawHost): internationalised domains are the classic homograph disguise."
        }
        val deconfused = host.map { CONFUSABLES[it] ?: it }.joinToString("")
        if (deconfused != host) {
            score += 40; reasons += "The host mixes lookalike characters (Cyrillic/Greek twins of ASCII): a homograph attack until proven otherwise."
        }

        // 3. Shorteners: the destination is deliberately hidden.
        val registrable = registrable(host)
        if (SHORTENERS.contains(host) || SHORTENERS.contains(registrable)) {
            score += 25; reasons += "A shortened link: the real destination is hidden until clicked. Expand it first (the browser agent can, safely)."
        }

        // 4. Brand impersonation: the brand in the host, but not as the registrable domain.
        val brand = BRANDS.firstOrNull { host.contains(it) || deconfused.contains(it) }
        if (brand != null) {
            val rd = registrable(deconfused)
            if (rd == "$brand.com" || rd == brand || rd.startsWith("$brand.")) {
                // The real brand's own domain: nothing to flag here.
            } else {
                score += 50
                reasons += "“$brand” appears in the host but the domain is $rd — impersonation structure (brand-as-bait on someone else's domain)."
            }
        }

        // 5. Deceptive subdomain stacking: many labels, or a brand parked in front.
        val labels = host.split('.')
        if (labels.size > 4) {
            score += 15; reasons += "${labels.size} subdomain levels: long label stacks bury the real domain at the end."
        }

        // 6. Abuse-heavy TLDs.
        val tld = labels.lastOrNull().orEmpty()
        if (tld in RARE_TLDS) {
            score += 15; reasons += "The .$tld TLD is disproportionately used for abuse; extra care, not automatic guilt."
        }

        // 7. Credentials in the URL: user@host is a very old trick.
        if (!uri.userInfo.isNullOrBlank()) {
            score += 30; reasons += "The URL carries a user@ part: browsers treat what follows @ as the real host, which is how “https://paypal.com@evil.io” works."
        }

        // 8. Path tricks: scheme-relative doubles slashes, @ in path, %00-ish escapes.
        val rest = (uri.rawPath ?: "") + (uri.rawQuery ?: "")
        if (rest.startsWith("//")) { score += 15; reasons += "Double slash right after the host: some parsers read what follows as a new authority." }
        if (rest.contains("@")) { score += 10; reasons += "An @ inside the path: harmless in most parsers, favourite in half of phishing kits." }
        if (Regex("%0[0-9a-f]", RegexOption.IGNORE_CASE).containsMatchIn(rest)) { score += 15; reasons += "Null-byte-style escape in the path: a filter-evasion tell." }

        // 9. Lookalike distance to a brand's real domain (paypa1.com, g0ogle.com).
        if (brand == null) {
            val near = BRANDS.mapNotNull { b ->
                val d = levenshtein(registrable(host).removeSuffix(".com"), b)
                if (d in 1..2 && registrable(host) != "$b.com") b to d else null
            }.minByOrNull { it.second }
            if (near != null) {
                score += 45; reasons += "The domain is ${near.second} character(s) from ${near.first}'s real domain: a typo-squat."
            }
        }

        return Verdict(score.coerceAtMost(100), reasons, host)
    }

    /** The last two labels (three for the common two-part public suffixes). */
    fun registrable(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val second = labels[labels.size - 2]
        return if (second in TWO_PART_SUFFIXES) labels.takeLast(3).joinToString(".")
        else labels.takeLast(2).joinToString(".")
    }

    private val TWO_PART_SUFFIXES = setOf("co", "com", "net", "org", "gov", "ac", "edu")

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
