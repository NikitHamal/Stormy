package com.oro.app.data.ai.community

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ThreadLocalRandom

/**
 * Cryptographic helpers, token generators, header rotators, and stream sanitizers
 * ported from ZeusDesigns (`src-tauri/src/crypto.rs` and `src-tauri/src/providers.rs`).
 */
object KeylessCrypto {

    private val UTC_HOUR_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH").withZone(ZoneOffset.UTC)

    private const val DEEPAI_SALT = "hackers_become_a_little_stinkier_every_time_they_hack"

    /**
     * Generates a plausible public IPv4 address for `X-Forwarded-For` and `X-Real-IP`
     * so IP-keyed rate limiters on public gateways do not throttle multi-turn agent runs.
     */
    fun fakeIpv4(): String {
        val rng = ThreadLocalRandom.current()
        val firstOctets = intArrayOf(24, 38, 45, 64, 66, 72, 74, 96, 104, 128, 138, 142, 154, 173, 184, 198, 206, 208)
        val a = firstOctets[rng.nextInt(firstOctets.size)]
        val b = rng.nextInt(1, 254)
        val c = rng.nextInt(1, 254)
        val d = rng.nextInt(1, 254)
        return "$a.$b.$c.$d"
    }

    fun randomHex(chars: Int): String {
        val rng = ThreadLocalRandom.current()
        val sb = StringBuilder(chars)
        val hex = "0123456789abcdef"
        repeat(chars) {
            sb.append(hex[rng.nextInt(16)])
        }
        return sb.toString()
    }

    fun sha256Hex(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun md5Hex(input: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun md5ReversedHex(input: ByteArray): String = md5Hex(input).reversed()

    /**
     * MyChat Native Direct Cloud's rolling hourly `X-Encryption-Token`:
     * `md5("GPT_KEYBOARD_IS_AWESOME|YYYY-MM-DD HH")` in UTC.
     */
    fun generateMyChatEncryptionToken(unixSeconds: Long = System.currentTimeMillis() / 1000L): String {
        val utcHour = UTC_HOUR_FORMATTER.format(Instant.ofEpochSecond(unixSeconds))
        val raw = "GPT_KEYBOARD_IS_AWESOME|$utcHour"
        return md5Hex(raw.toByteArray(Charsets.UTF_8))
    }

    /**
     * DeepAI's `generateIslandKey` (`tryit-{rand}-{reversed_md5}`).
     */
    fun generateDeepAiIslandKey(userAgent: String = CommunityHeaders.USER_AGENT): String {
        val randomNum = ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L).toString()
        val h1 = md5ReversedHex("$userAgent$randomNum$DEEPAI_SALT".toByteArray(Charsets.UTF_8))
        val h2 = md5ReversedHex("$userAgent$h1".toByteArray(Charsets.UTF_8))
        val h3 = md5ReversedHex("$userAgent$h2".toByteArray(Charsets.UTF_8))
        return "tryit-$randomNum-$h3"
    }

    /**
     * Splits ChatJimmy output into `(reasoning, visibleContent)`, stripping `<|stats|>...<|/stats|>`
     * and extracting `<|think|>...<|/think|>`.
     */
    fun parseChatJimmyOutput(raw: String): Pair<String, String> {
        var s = raw
        while (true) {
            val start = s.indexOf("<|stats|>")
            if (start < 0) break
            val end = s.indexOf("<|/stats|>", start)
            s = if (end >= 0) {
                s.removeRange(start, end + "<|/stats|>".length)
            } else {
                s.substring(0, start)
            }
        }
        val reasoning = StringBuilder()
        while (true) {
            val start = s.indexOf("<|think|>")
            if (start < 0) break
            val end = s.indexOf("<|/think|>", start)
            if (end >= 0) {
                val thought = s.substring(start + "<|think|>".length, end).trim()
                if (thought.isNotEmpty()) {
                    if (reasoning.isNotEmpty()) reasoning.append("\n")
                    reasoning.append(thought)
                }
                s = s.removeRange(start, end + "<|/think|>".length)
            } else {
                break
            }
        }
        return reasoning.toString().trim() to s.trim()
    }

    /**
     * Detects upstream proxy error bodies returned inside 200 OK streams so
     * multi-target clients can fail over before emitting error text to the user.
     */
    fun isUpstreamErrorText(text: String): Boolean {
        val lower = text.trim().lowercase()
        if (lower.isEmpty()) return false
        return lower.startsWith("error:") ||
            lower.startsWith("{\"error\"") ||
            lower.contains("rate limit exceeded") ||
            lower.contains("quota exceeded") ||
            lower.contains("no available channel") ||
            lower.contains("insufficient_quota") ||
            lower.contains("model_not_found") ||
            lower.contains("upstream error") ||
            lower.contains("service unavailable") ||
            lower.contains("bad gateway")
    }
}
