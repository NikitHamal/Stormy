package com.codex.stormy.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Token bucket rate limiter with sliding window support.
 * Provides precise rate limiting for API calls and resource access.
 *
 * Features:
 * - Token bucket algorithm for smooth rate limiting
 * - Sliding window for accurate request counting
 * - Thread-safe with coroutine support
 * - Configurable burst capacity
 * - Detailed metrics and status reporting
 *
 * @param maxRequests Maximum requests allowed in the time window
 * @param windowMs Time window in milliseconds
 * @param burstCapacity Maximum burst capacity (defaults to maxRequests)
 */
class RateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long,
    private val burstCapacity: Int = maxRequests
) {
    private val mutex = Mutex()
    private val requestTimestamps = ConcurrentLinkedQueue<Long>()
    private var tokens = burstCapacity.toDouble()
    private var lastRefillTime = System.currentTimeMillis()
    private val totalRequests = AtomicLong(0)
    private val rejectedRequests = AtomicLong(0)
    private val waitedRequests = AtomicLong(0)

    // Refill rate: tokens per millisecond
    private val refillRate = maxRequests.toDouble() / windowMs

    /**
     * Attempt to acquire a permit without blocking.
     *
     * @return true if permit acquired, false otherwise
     */
    suspend fun tryAcquire(): Boolean = mutex.withLock {
        cleanOldTimestamps()
        refillTokens()

        return@withLock if (tokens >= 1.0 && requestTimestamps.size < maxRequests) {
            tokens -= 1.0
            requestTimestamps.add(System.currentTimeMillis())
            totalRequests.incrementAndGet()
            true
        } else {
            rejectedRequests.incrementAndGet()
            false
        }
    }

    /**
     * Acquire a permit, waiting if necessary.
     * Blocks until a permit is available.
     *
     * @param maxWaitMs Maximum time to wait in milliseconds (0 = no limit)
     * @return true if permit acquired, false if timed out
     */
    suspend fun acquire(maxWaitMs: Long = 0): Boolean {
        val startTime = System.currentTimeMillis()
        var waited = false

        while (true) {
            if (tryAcquire()) {
                if (waited) waitedRequests.incrementAndGet()
                return true
            }

            // Check timeout
            if (maxWaitMs > 0) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= maxWaitMs) {
                    return false
                }
            }

            waited = true
            // Calculate optimal wait time
            val waitTime = calculateWaitTime()
            delay(minOf(waitTime, if (maxWaitMs > 0) maxWaitMs - (System.currentTimeMillis() - startTime) else waitTime))
        }
    }

    /**
     * Calculate time until a permit becomes available.
     *
     * @return Wait time in milliseconds
     */
    private fun calculateWaitTime(): Long {
        mutex.tryLock()
        try {
            // Time until a token is refilled
            val tokenWait = if (tokens < 1.0) {
                ((1.0 - tokens) / refillRate).toLong()
            } else 0L

            // Time until oldest request leaves the window
            val windowWait = requestTimestamps.peek()?.let { oldest ->
                val age = System.currentTimeMillis() - oldest
                if (age < windowMs) windowMs - age else 0L
            } ?: 0L

            return maxOf(10L, minOf(tokenWait, windowWait)) // At least 10ms, at most min of both
        } finally {
            if (mutex.isLocked) mutex.unlock()
        }
    }

    /**
     * Remove timestamps outside the current window.
     */
    private fun cleanOldTimestamps() {
        val cutoff = System.currentTimeMillis() - windowMs
        while (requestTimestamps.peek()?.let { it < cutoff } == true) {
            requestTimestamps.poll()
        }
    }

    /**
     * Refill tokens based on elapsed time.
     */
    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRefillTime
        tokens = minOf(burstCapacity.toDouble(), tokens + elapsed * refillRate)
        lastRefillTime = now
    }

    /**
     * Get current available tokens (approximate).
     */
    val availableTokens: Double
        get() {
            refillTokens()
            return tokens
        }

    /**
     * Get current request count in window.
     */
    val requestCount: Int
        get() {
            cleanOldTimestamps()
            return requestTimestamps.size
        }

    /**
     * Check if rate limited (without consuming a token).
     */
    val isRateLimited: Boolean
        get() = requestCount >= maxRequests || availableTokens < 1.0

    /**
     * Get rate limiter statistics.
     */
    fun getStats(): RateLimiterStats = RateLimiterStats(
        totalRequests = totalRequests.get(),
        rejectedRequests = rejectedRequests.get(),
        waitedRequests = waitedRequests.get(),
        currentRequestCount = requestCount,
        availableTokens = availableTokens.toInt(),
        maxRequests = maxRequests,
        windowMs = windowMs,
        burstCapacity = burstCapacity
    )

    /**
     * Reset the rate limiter.
     */
    suspend fun reset() = mutex.withLock {
        requestTimestamps.clear()
        tokens = burstCapacity.toDouble()
        lastRefillTime = System.currentTimeMillis()
        totalRequests.set(0)
        rejectedRequests.set(0)
        waitedRequests.set(0)
    }

    companion object {
        /**
         * Create a rate limiter for requests per minute.
         */
        fun perMinute(requests: Int, burstCapacity: Int = requests) =
            RateLimiter(requests, 60_000L, burstCapacity)

        /**
         * Create a rate limiter for requests per second.
         */
        fun perSecond(requests: Int, burstCapacity: Int = requests) =
            RateLimiter(requests, 1_000L, burstCapacity)

        /**
         * Create a rate limiter for requests per hour.
         */
        fun perHour(requests: Int, burstCapacity: Int = requests) =
            RateLimiter(requests, 3_600_000L, burstCapacity)
    }
}

/**
 * Statistics from a rate limiter.
 */
data class RateLimiterStats(
    val totalRequests: Long,
    val rejectedRequests: Long,
    val waitedRequests: Long,
    val currentRequestCount: Int,
    val availableTokens: Int,
    val maxRequests: Int,
    val windowMs: Long,
    val burstCapacity: Int
) {
    val rejectionRate: Double
        get() = if (totalRequests > 0) rejectedRequests.toDouble() / totalRequests else 0.0

    val waitRate: Double
        get() = if (totalRequests > 0) waitedRequests.toDouble() / totalRequests else 0.0

    val utilizationPercent: Double
        get() = (currentRequestCount.toDouble() / maxRequests) * 100
}

/**
 * Composite rate limiter that enforces multiple rate limits.
 * Useful for enforcing both per-minute and per-hour limits.
 */
class CompositeRateLimiter(
    private val limiters: List<RateLimiter>
) {
    /**
     * Try to acquire from all limiters.
     */
    suspend fun tryAcquire(): Boolean {
        return limiters.all { it.tryAcquire() }
    }

    /**
     * Acquire from all limiters, waiting if necessary.
     */
    suspend fun acquire(maxWaitMs: Long = 0): Boolean {
        return limiters.all { it.acquire(maxWaitMs) }
    }

    /**
     * Check if any limiter is rate limiting.
     */
    val isRateLimited: Boolean
        get() = limiters.any { it.isRateLimited }

    companion object {
        /**
         * Create a composite limiter with per-minute and per-hour limits.
         */
        fun withMinuteAndHourLimits(
            perMinute: Int,
            perHour: Int
        ) = CompositeRateLimiter(
            listOf(
                RateLimiter.perMinute(perMinute),
                RateLimiter.perHour(perHour)
            )
        )
    }
}
