package com.codex.stormy.utils

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.pow
import kotlin.random.Random

/**
 * Comprehensive retry policy with exponential backoff and jitter.
 *
 * Features:
 * - Configurable retry strategies (exponential, linear, constant)
 * - Jitter to prevent thundering herd
 * - Configurable retry conditions
 * - Detailed logging and metrics
 * - Timeout support
 *
 * @param maxAttempts Maximum number of attempts (including first attempt)
 * @param initialDelayMs Initial delay before first retry
 * @param maxDelayMs Maximum delay between retries
 * @param backoffMultiplier Multiplier for exponential backoff
 * @param jitterFactor Random jitter factor (0.0 to 1.0)
 */
class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 30_000L,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.1,
    private val retryableExceptions: Set<Class<out Throwable>> = DEFAULT_RETRYABLE_EXCEPTIONS,
    private val shouldRetry: ((Throwable, Int) -> Boolean)? = null,
    private val onRetry: ((RetryEvent) -> Unit)? = null
) {
    // Metrics
    private val totalExecutions = AtomicLong(0)
    private val successfulExecutions = AtomicLong(0)
    private val failedExecutions = AtomicLong(0)
    private val totalRetries = AtomicLong(0)

    /**
     * Execute a block with retry logic.
     *
     * @param block The operation to execute
     * @return Result of the operation
     * @throws Exception if all retries exhausted
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        totalExecutions.incrementAndGet()
        var lastException: Throwable? = null
        val startTime = System.currentTimeMillis()

        for (attempt in 1..maxAttempts) {
            try {
                val result = block()
                successfulExecutions.incrementAndGet()
                return result
            } catch (e: Throwable) {
                lastException = e

                // Check if we should retry
                val shouldRetryThis = shouldRetryException(e, attempt)

                if (attempt >= maxAttempts || !shouldRetryThis) {
                    failedExecutions.incrementAndGet()
                    throw e
                }

                totalRetries.incrementAndGet()

                // Calculate delay
                val delayMs = calculateDelay(attempt)

                // Notify listener
                onRetry?.invoke(
                    RetryEvent(
                        attempt = attempt,
                        maxAttempts = maxAttempts,
                        exception = e,
                        delayMs = delayMs,
                        totalElapsedMs = System.currentTimeMillis() - startTime
                    )
                )

                android.util.Log.w("RetryPolicy",
                    "Attempt $attempt/$maxAttempts failed: ${e.message}. Retrying in ${delayMs}ms")

                delay(delayMs)
            }
        }

        failedExecutions.incrementAndGet()
        throw lastException ?: IllegalStateException("Retry exhausted without exception")
    }

    /**
     * Execute with a fallback on final failure.
     */
    suspend fun <T> executeWithFallback(
        fallback: suspend (Throwable) -> T,
        block: suspend () -> T
    ): T {
        return try {
            execute(block)
        } catch (e: Throwable) {
            fallback(e)
        }
    }

    /**
     * Execute returning Result instead of throwing.
     */
    suspend fun <T> executeToResult(block: suspend () -> T): Result<T> {
        return try {
            Result.success(execute(block))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Calculate delay for a given attempt number.
     */
    fun calculateDelay(attempt: Int): Long {
        val exponentialDelay = (initialDelayMs * backoffMultiplier.pow(attempt - 1)).toLong()
        val cappedDelay = minOf(exponentialDelay, maxDelayMs)

        // Add jitter
        val jitter = (cappedDelay * jitterFactor * Random.nextDouble()).toLong()
        return cappedDelay + jitter
    }

    /**
     * Check if exception should trigger a retry.
     */
    private fun shouldRetryException(exception: Throwable, attempt: Int): Boolean {
        // Custom check takes precedence
        shouldRetry?.let { return it(exception, attempt) }

        // Check if exception type is retryable
        return retryableExceptions.any { it.isInstance(exception) }
    }

    /**
     * Get retry policy statistics.
     */
    fun getStats(): RetryPolicyStats = RetryPolicyStats(
        totalExecutions = totalExecutions.get(),
        successfulExecutions = successfulExecutions.get(),
        failedExecutions = failedExecutions.get(),
        totalRetries = totalRetries.get(),
        maxAttempts = maxAttempts,
        initialDelayMs = initialDelayMs,
        maxDelayMs = maxDelayMs
    )

    companion object {
        /**
         * Default set of retryable exceptions.
         */
        val DEFAULT_RETRYABLE_EXCEPTIONS: Set<Class<out Throwable>> = setOf(
            IOException::class.java,
            SocketTimeoutException::class.java,
            java.net.ConnectException::class.java,
            java.net.UnknownHostException::class.java,
            javax.net.ssl.SSLException::class.java
        )

        /**
         * Create a retry policy for network operations.
         */
        fun forNetwork() = RetryPolicy(
            maxAttempts = 3,
            initialDelayMs = 1000L,
            maxDelayMs = 10_000L,
            backoffMultiplier = 2.0,
            jitterFactor = 0.2
        )

        /**
         * Create a retry policy for AI/API calls.
         */
        fun forAi() = RetryPolicy(
            maxAttempts = 3,
            initialDelayMs = 2000L,
            maxDelayMs = 30_000L,
            backoffMultiplier = 2.0,
            jitterFactor = 0.15,
            shouldRetry = { e, _ ->
                // Retry on network errors or 5xx errors
                when {
                    e is IOException -> true
                    e.message?.contains("5") == true &&
                        e.message?.contains("HTTP") == true -> true
                    e.message?.contains("429") == true -> true // Rate limited
                    else -> false
                }
            }
        )

        /**
         * Create a retry policy for file operations.
         */
        fun forFileOps() = RetryPolicy(
            maxAttempts = 2,
            initialDelayMs = 500L,
            maxDelayMs = 2000L,
            backoffMultiplier = 1.5,
            jitterFactor = 0.1
        )

        /**
         * Create a retry policy for Git operations.
         */
        fun forGit() = RetryPolicy(
            maxAttempts = 2,
            initialDelayMs = 3000L,
            maxDelayMs = 15_000L,
            backoffMultiplier = 2.0,
            jitterFactor = 0.1
        )

        /**
         * Create a no-retry policy (single attempt only).
         */
        fun noRetry() = RetryPolicy(
            maxAttempts = 1,
            initialDelayMs = 0,
            maxDelayMs = 0,
            backoffMultiplier = 1.0,
            jitterFactor = 0.0
        )

        /**
         * Create a custom retry policy builder.
         */
        fun builder() = RetryPolicyBuilder()
    }
}

/**
 * Event data for retry callbacks.
 */
data class RetryEvent(
    val attempt: Int,
    val maxAttempts: Int,
    val exception: Throwable,
    val delayMs: Long,
    val totalElapsedMs: Long
) {
    val isLastRetry: Boolean get() = attempt >= maxAttempts - 1
    val remainingAttempts: Int get() = maxAttempts - attempt
}

/**
 * Statistics from retry policy.
 */
data class RetryPolicyStats(
    val totalExecutions: Long,
    val successfulExecutions: Long,
    val failedExecutions: Long,
    val totalRetries: Long,
    val maxAttempts: Int,
    val initialDelayMs: Long,
    val maxDelayMs: Long
) {
    val successRate: Double
        get() = if (totalExecutions > 0) successfulExecutions.toDouble() / totalExecutions else 0.0

    val failureRate: Double
        get() = if (totalExecutions > 0) failedExecutions.toDouble() / totalExecutions else 0.0

    val averageRetriesPerExecution: Double
        get() = if (totalExecutions > 0) totalRetries.toDouble() / totalExecutions else 0.0
}

/**
 * Builder for RetryPolicy with fluent API.
 */
class RetryPolicyBuilder {
    private var maxAttempts = 3
    private var initialDelayMs = 1000L
    private var maxDelayMs = 30_000L
    private var backoffMultiplier = 2.0
    private var jitterFactor = 0.1
    private var retryableExceptions: Set<Class<out Throwable>> = RetryPolicy.DEFAULT_RETRYABLE_EXCEPTIONS
    private var shouldRetry: ((Throwable, Int) -> Boolean)? = null
    private var onRetry: ((RetryEvent) -> Unit)? = null

    fun maxAttempts(value: Int) = apply { maxAttempts = value }
    fun initialDelay(ms: Long) = apply { initialDelayMs = ms }
    fun maxDelay(ms: Long) = apply { maxDelayMs = ms }
    fun backoffMultiplier(value: Double) = apply { backoffMultiplier = value }
    fun jitter(factor: Double) = apply { jitterFactor = factor }
    fun retryableExceptions(exceptions: Set<Class<out Throwable>>) = apply { retryableExceptions = exceptions }
    fun shouldRetry(predicate: (Throwable, Int) -> Boolean) = apply { shouldRetry = predicate }
    fun onRetry(callback: (RetryEvent) -> Unit) = apply { onRetry = callback }

    fun build() = RetryPolicy(
        maxAttempts = maxAttempts,
        initialDelayMs = initialDelayMs,
        maxDelayMs = maxDelayMs,
        backoffMultiplier = backoffMultiplier,
        jitterFactor = jitterFactor,
        retryableExceptions = retryableExceptions,
        shouldRetry = shouldRetry,
        onRetry = onRetry
    )
}

/**
 * Convenience extension to execute with retry.
 */
suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1000L,
    block: suspend () -> T
): T {
    return RetryPolicy(maxAttempts = maxAttempts, initialDelayMs = initialDelayMs).execute(block)
}

/**
 * Execute with retry using predefined policy.
 */
suspend fun <T> withRetryPolicy(policy: RetryPolicy, block: suspend () -> T): T {
    return policy.execute(block)
}
