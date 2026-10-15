package com.codex.stormy.data.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Paces requests so free tiers are not tripped mid-conversation.
 *
 * A 429 does not degrade gracefully: it lands part-way through an agent run,
 * after files have already been written, and reads to the user as the app
 * breaking rather than as a quota. Waiting 200ms before a request is strictly
 * better than failing after it.
 *
 * ### Sliding window, not a fixed one
 *
 * Counting within fixed clock minutes lets a caller send the whole minute's
 * budget at 59s and the next minute's at 61s -- double the rate across the
 * boundary, which is exactly what providers measure and reject. Timestamps are
 * kept and aged out instead, so the limit holds across any window.
 *
 * ### Why tokens are estimated
 *
 * Tokens-per-minute is usually the binding constraint, and it has to be
 * charged *before* the request to be useful. The estimate comes from the same
 * estimator the rest of the app uses, so it is consistent even where it is
 * approximate; a reservation that disagreed with the figure shown in the UI
 * would be worse than one that is merely imprecise.
 */
class ProviderRateLimiter(
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) {
    private data class Spend(val at: Long, val tokens: Int)

    private val mutex = Mutex()
    private val history = mutableMapOf<AiProvider, MutableList<Spend>>()
    private val dayCount = mutableMapOf<AiProvider, MutableList<Long>>()

    /**
     * Wait until [provider] can take a request of roughly [estimatedTokens],
     * then record it.
     *
     * @return how long the caller was held, for surfacing in the UI. Zero when
     *   nothing was waited on, which is the overwhelmingly common case.
     */
    suspend fun acquire(provider: AiProvider, estimatedTokens: Int): Long {
        val limit = RateLimits[provider] ?: return 0L

        var totalWaited = 0L
        while (true) {
            val wait = mutex.withLock { waitRequiredMs(provider, limit, estimatedTokens) }
            if (wait <= 0L) {
                mutex.withLock { record(provider, estimatedTokens) }
                return totalWaited
            }
            // Capped per iteration so a caller that is cancelled while waiting
            // notices promptly rather than sleeping out a whole minute.
            val slice = wait.coerceAtMost(MAX_SLICE_MS)
            sleep(slice)
            totalWaited += slice
        }
    }

    /**
     * How long until this request would fit, in ms. Zero when it fits now.
     */
    private fun waitRequiredMs(
        provider: AiProvider,
        limit: RateLimit,
        estimatedTokens: Int
    ): Long {
        val current = now()
        val window = history.getOrPut(provider) { mutableListOf() }
        window.removeAll { current - it.at >= WINDOW_MS }

        // Requests per minute.
        if (window.size >= limit.requestsPerMinute) {
            val oldest = window.minOf { it.at }
            return (oldest + WINDOW_MS) - current
        }

        // Tokens per minute. A single request larger than the whole budget can
        // never fit, so it is let through rather than looping forever -- the
        // provider's own error is a better outcome than a hang.
        val spent = window.sumOf { it.tokens }
        if (estimatedTokens < limit.tokensPerMinute &&
            spent + estimatedTokens > limit.tokensPerMinute
        ) {
            val oldest = window.minOf { it.at }
            return (oldest + WINDOW_MS) - current
        }

        // Requests per day.
        if (limit.requestsPerDay > 0) {
            val daily = dayCount.getOrPut(provider) { mutableListOf() }
            daily.removeAll { current - it >= DAY_MS }
            if (daily.size >= limit.requestsPerDay) {
                // Not worth waiting out; the caller should be told instead.
                return -1L
            }
        }

        return 0L
    }

    private fun record(provider: AiProvider, estimatedTokens: Int) {
        val current = now()
        history.getOrPut(provider) { mutableListOf() }.add(Spend(current, estimatedTokens))
        if (RateLimits[provider]?.requestsPerDay?.let { it > 0 } == true) {
            dayCount.getOrPut(provider) { mutableListOf() }.add(current)
        }
    }

    /** Whether the daily budget is spent; the caller should say so, not wait. */
    suspend fun isDailyBudgetSpent(provider: AiProvider): Boolean = mutex.withLock {
        val limit = RateLimits[provider] ?: return false
        if (limit.requestsPerDay <= 0) return false
        val current = now()
        val daily = dayCount.getOrPut(provider) { mutableListOf() }
        daily.removeAll { current - it >= DAY_MS }
        daily.size >= limit.requestsPerDay
    }

    /** Requests still available this minute, for the UI. */
    suspend fun remainingThisMinute(provider: AiProvider): Int? = mutex.withLock {
        val limit = RateLimits[provider] ?: return null
        val current = now()
        val window = history.getOrPut(provider) { mutableListOf() }
        window.removeAll { current - it.at >= WINDOW_MS }
        (limit.requestsPerMinute - window.size).coerceAtLeast(0)
    }

    /** Drops all history. Used when the provider or key changes. */
    suspend fun reset() = mutex.withLock {
        history.clear()
        dayCount.clear()
    }

    companion object {
        private const val WINDOW_MS = 60_000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val MAX_SLICE_MS = 1_000L
    }
}
