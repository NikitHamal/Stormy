package com.oro.app.data.security

/**
 * Secret detection for projects, pastes and logs: the patterns that mean a
 * credential escaped, and the masking that keeps the found secret out of
 * every transcript, report and model context.
 *
 * Deliberately high-precision over high-recall (the quality-gate rule of the
 * house): a false positive in a security tool teaches the user to ignore the
 * tool. Entropy alone never fires; every hit is a shape.
 */
object SecretScan {

    data class LineHit(val kind: String, val value: String, val start: Int, val line: Int)

    data class Hit(val file: String, val line: Int, val kind: String, val masked: String)

    private val PATTERNS: List<Pair<String, Regex>> = listOf(
        "private key" to Regex("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----"),
        "aws access key" to Regex("\\b(AKIA|ASIA)[0-9A-Z]{16}\\b"),
        "aws secret key" to Regex("(?i)aws.{0,20}['\"][0-9a-zA-Z/+]{40}['\"]"),
        "github token" to Regex("\\b(ghp|gho|ghu|ghs|ghr)_[0-9A-Za-z]{36,}\\b"),
        "github fine-grained token" to Regex("\\bgithub_pat_[0-9A-Za-z_]{22,}\\b"),
        "stripe live key" to Regex("\\b(sk|rk)_live_[0-9a-zA-Z]{16,}\\b"),
        "slack token" to Regex("\\bxox[baprs]-[0-9A-Za-z-]{10,}\\b"),
        "google api key" to Regex("\\bAIza[0-9A-Za-z_\\-]{35}\\b"),
        "sendgrid key" to Regex("\\bSG\\.[0-9A-Za-z_\\-]{16,}\\.[0-9A-Za-z_\\-]{16,}\\b"),
        "twilio token" to Regex("\\bAC[0-9a-f]{32}\\b"),
        "npm token" to Regex("//registry\\.npmjs\\.org/:_authToken=[0-9a-f-]{30,}"),
        "jwt" to Regex("\\beyJ[0-9A-Za-z_\\-]{10,}\\.eyJ[0-9A-Za-z_\\-]{10,}\\.[0-9A-Za-z_\\-]{10,}\\b"),
        "telegram bot token" to Regex("\\b\\d{8,10}:AA[0-9A-Za-z_\\-]{30,}\\b"),
        "openai-style key" to Regex("\\bsk-(proj-)?[0-9A-Za-z_\\-]{20,}\\b"),
        "anthropic key" to Regex("\\bsk-ant-[0-9A-Za-z_\\-]{20,}\\b"),
        "connection string with password" to Regex("(?i)\\b(mysql|postgres|postgresql|mongodb(\\+srv)?|redis|amqp)://[^\\s:@/]+:[^\\s@]+@[^\\s]+"),
        "assigned secret" to Regex("(?i)\\b(password|passwd|pwd|secret|api[_-]?key|apikey|auth[_-]?token|access[_-]?token|private[_-]?key)\\b\\s*[:=]\\s*['\"][^'\"]{12,}['\"]"),
    )

    /** Placeholder values that must not fire: examples, env refs, prompts. */
    private val PLACEHOLDER = Regex(
        "(?i)^(x{4,}|\\*+|changeme|change_me|placeholder|example|dummy|test|your[_-]?(password|key|secret)[\\w-]*|" +
            "\\$\\{?[A-Z_]+\\}?|%[A-Z_]+%|<[^>]+>|none|null|todo|fixme)$"
    )

    fun scanText(text: String): List<LineHit> {
        val out = ArrayList<LineHit>()
        text.lineSequence().forEachIndexed { idx, line ->
            if (line.length > 4000) return@forEachIndexed
            val lineNo = idx + 1
            for ((kind, pattern) in PATTERNS) {
                val m = pattern.find(line) ?: continue
                val value = if (kind == "assigned secret") m.value.substringAfter(Regex("[:=]\\s*").find(m.value)?.value ?: ":") 
                    .trim('\'', '"', ' ') else m.value
                if (value.length < 8 || PLACEHOLDER.matches(value)) continue
                out += LineHit(kind, value, m.range.first, lineNo)
            }
        }
        return out
    }

    /**
     * The found secret, unreadable: first three characters and length, so a
     * report can say "this line holds a 40-char Stripe live key starting sk_"
     * without carrying the key to whatever reads the report.
     */
    fun mask(value: String): String {
        val v = value.trim()
        if (v.length <= 4) return "•".repeat(v.length)
        return v.take(3) + "…" + v.length + " chars"
    }

    fun scanFile(file: String, text: String): List<Hit> =
        scanText(text).map { h -> Hit(file, h.line, h.kind, mask(h.value)) }
}
