package com.oro.app.data.ai.providers

import com.oro.app.data.ai.StreamEvent
import java.util.concurrent.ConcurrentHashMap

/** Why a key is out of rotation. */
enum class KeyHealth(val label: String) {
    OK("Active"),
    RATE_LIMITED("Cooling down"),
    EXHAUSTED("Out of credits"),
    INVALID("Rejected")
}

data class KeyState(
    val health: KeyHealth = KeyHealth.OK,
    /** Epoch ms when the key is tried again; ignored for [KeyHealth.OK]. */
    val until: Long = 0L,
    val message: String? = null
)

/**
 * Health of every API key, process-wide, and the order to try them in.
 *
 * Provider managers are built per request, so this state cannot live on them.
 * It is in memory only on purpose: a restart is a natural moment to give a
 * cooled-down or "exhausted" key another chance (daily quotas reset, the user
 * may have topped up), and nothing here is worth a disk write per request.
 *
 * Failover happens before any output: a 429/402/401 arrives instead of the
 * stream, so the next key can take the same request and the agent loop never
 * sees the failure. Once tokens have streamed, an error is the run's to handle.
 */
object KeyPool {

    /** Overridable for tests. */
    @Volatile
    var clock: () -> Long = { System.currentTimeMillis() }

    private val states = ConcurrentHashMap<String, KeyState>()
    private val cursors = ConcurrentHashMap<String, Int>()

    private const val DEFAULT_COOLDOWN_MS = 60_000L
    private const val EXHAUSTED_COOLDOWN_MS = 60 * 60_000L
    private const val INVALID_COOLDOWN_MS = 6 * 60 * 60_000L

    private val QUOTA_WORDS = listOf(
        "insufficient_quota", "quota", "credit", "billing", "balance", "payment", "exceeded your current",
        "out of tokens", "free limit", "daily limit"
    )

    fun state(keyId: String): KeyState {
        val s = states[keyId] ?: return KeyState()
        if (s.health != KeyHealth.OK && clock() >= s.until) {
            states.remove(keyId)
            return KeyState()
        }
        return s
    }

    /** Clear a key's state, e.g. after the user edits it or taps "Try again". */
    fun reset(keyId: String) {
        states.remove(keyId)
    }

    /**
     * Keys to try, in order: healthy ones first (rotated for round robin),
     * then cooling ones soonest-first as a last resort, so a request whose
     * every key is cooling still goes out and fails with the server's own
     * message rather than with nothing.
     */
    fun candidates(poolId: String, keys: List<ApiKeyEntry>, rotation: KeyRotation): List<ApiKeyEntry> {
        val usable = keys.filter { it.enabled && it.key.isNotBlank() }
        if (usable.size <= 1) return usable
        val (healthy, cooling) = usable.partition { state(it.id).health == KeyHealth.OK }
        val ordered = if (rotation == KeyRotation.ROUND_ROBIN && healthy.size > 1) {
            val start = cursors.merge(poolId, 1) { a, b -> a + b }!! % healthy.size
            healthy.drop(start) + healthy.take(start)
        } else healthy
        return ordered + cooling.sortedBy { state(it.id).until }
    }

    fun reportSuccess(keyId: String) {
        if (states[keyId] != null) states.remove(keyId)
    }

    /** Record a failure; returns the health it implies, or null when the key is not to blame. */
    fun reportFailure(keyId: String, error: StreamEvent.Error): KeyHealth? {
        val health = classify(error.httpCode, error.message) ?: return null
        val now = clock()
        val until = now + when (health) {
            KeyHealth.RATE_LIMITED -> error.retryAfterMs?.coerceIn(1_000L, EXHAUSTED_COOLDOWN_MS) ?: DEFAULT_COOLDOWN_MS
            KeyHealth.EXHAUSTED -> EXHAUSTED_COOLDOWN_MS
            KeyHealth.INVALID -> INVALID_COOLDOWN_MS
            KeyHealth.OK -> 0L
        }
        states[keyId] = KeyState(health, until, error.message.take(200))
        return health
    }

    /**
     * Whether a failure is about the key rather than the request. A 400 for a
     * bad request or a 5xx outage is the same on every key, so rotating would
     * only multiply it; quota messages are recognised whatever their status
     * (Anthropic says "credit balance is too low" with a 400).
     */
    fun classify(httpCode: Int?, message: String): KeyHealth? {
        val text = message.lowercase()
        val quota = QUOTA_WORDS.any { it in text }
        return when (httpCode) {
            401 -> KeyHealth.INVALID
            402 -> KeyHealth.EXHAUSTED
            403 -> if (quota) KeyHealth.EXHAUSTED else KeyHealth.INVALID
            429 -> if (quota && ("rate" !in text || "insufficient_quota" in text)) KeyHealth.EXHAUSTED else KeyHealth.RATE_LIMITED
            400 -> if ("credit balance" in text || "insufficient_quota" in text) KeyHealth.EXHAUSTED else null
            else -> null
        }
    }
}
