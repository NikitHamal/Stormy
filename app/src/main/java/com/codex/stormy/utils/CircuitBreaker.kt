package com.codex.stormy.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Circuit breaker implementation for fault tolerance.
 *
 * Prevents cascading failures by stopping requests to failing services
 * and allowing them to recover.
 *
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Failure threshold exceeded, requests are rejected immediately
 * - HALF_OPEN: Testing if service has recovered
 *
 * @param failureThreshold Number of failures before opening the circuit
 * @param successThreshold Number of successes in half-open before closing
 * @param openDurationMs How long to stay open before testing (half-open)
 * @param halfOpenMaxAttempts Max concurrent attempts in half-open state
 */
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val successThreshold: Int = 2,
    private val openDurationMs: Long = 30_000L,
    private val halfOpenMaxAttempts: Int = 3,
    private val name: String = "default"
) {
    private val mutex = Mutex()

    @Volatile
    private var state: State = State.CLOSED

    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val halfOpenAttempts = AtomicInteger(0)
    private var lastFailureTime = AtomicLong(0)
    private var lastStateChange = AtomicLong(System.currentTimeMillis())

    // Metrics
    private val totalCalls = AtomicLong(0)
    private val successfulCalls = AtomicLong(0)
    private val failedCalls = AtomicLong(0)
    private val rejectedCalls = AtomicLong(0)
    private val stateTransitions = AtomicLong(0)

    /**
     * Circuit breaker states
     */
    enum class State {
        CLOSED,    // Normal operation
        OPEN,      // Rejecting requests
        HALF_OPEN  // Testing recovery
    }

    /**
     * Execute a block with circuit breaker protection.
     *
     * @param block The operation to execute
     * @return Result of the operation
     * @throws CircuitOpenException if circuit is open
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        totalCalls.incrementAndGet()

        // Check if we should transition from OPEN to HALF_OPEN
        checkStateTransition()

        return when (state) {
            State.OPEN -> {
                rejectedCalls.incrementAndGet()
                throw CircuitOpenException(
                    "Circuit breaker '$name' is OPEN. Retry after ${getRemainingOpenTime()}ms"
                )
            }

            State.HALF_OPEN -> {
                // Limit concurrent attempts in half-open
                if (halfOpenAttempts.incrementAndGet() > halfOpenMaxAttempts) {
                    halfOpenAttempts.decrementAndGet()
                    rejectedCalls.incrementAndGet()
                    throw CircuitOpenException(
                        "Circuit breaker '$name' is HALF_OPEN with max attempts reached"
                    )
                }

                try {
                    val result = block()
                    onSuccess()
                    result
                } catch (e: Exception) {
                    onFailure(e)
                    throw e
                } finally {
                    halfOpenAttempts.decrementAndGet()
                }
            }

            State.CLOSED -> {
                try {
                    val result = block()
                    onSuccess()
                    result
                } catch (e: Exception) {
                    onFailure(e)
                    throw e
                }
            }
        }
    }

    /**
     * Execute with a fallback if circuit is open.
     *
     * @param fallback Function to call if circuit is open
     * @param block The primary operation
     */
    suspend fun <T> executeWithFallback(
        fallback: suspend (CircuitOpenException) -> T,
        block: suspend () -> T
    ): T {
        return try {
            execute(block)
        } catch (e: CircuitOpenException) {
            fallback(e)
        }
    }

    /**
     * Check if a call is allowed (without executing).
     */
    fun isCallPermitted(): Boolean {
        checkStateTransition()
        return when (state) {
            State.CLOSED -> true
            State.HALF_OPEN -> halfOpenAttempts.get() < halfOpenMaxAttempts
            State.OPEN -> false
        }
    }

    /**
     * Check and perform state transitions.
     */
    private fun checkStateTransition() {
        when (state) {
            State.OPEN -> {
                if (System.currentTimeMillis() - lastStateChange.get() >= openDurationMs) {
                    transitionTo(State.HALF_OPEN)
                }
            }
            else -> { /* No automatic transition */ }
        }
    }

    /**
     * Handle successful operation.
     */
    private suspend fun onSuccess() = mutex.withLock {
        successfulCalls.incrementAndGet()

        when (state) {
            State.HALF_OPEN -> {
                val successes = successCount.incrementAndGet()
                if (successes >= successThreshold) {
                    transitionTo(State.CLOSED)
                }
            }
            State.CLOSED -> {
                // Reset failure count on success
                failureCount.set(0)
            }
            State.OPEN -> { /* Shouldn't happen */ }
        }
    }

    /**
     * Handle failed operation.
     */
    private suspend fun onFailure(exception: Exception) = mutex.withLock {
        failedCalls.incrementAndGet()
        lastFailureTime.set(System.currentTimeMillis())

        // Check if this exception should be counted
        if (!shouldCountFailure(exception)) return@withLock

        when (state) {
            State.CLOSED -> {
                val failures = failureCount.incrementAndGet()
                if (failures >= failureThreshold) {
                    transitionTo(State.OPEN)
                }
            }
            State.HALF_OPEN -> {
                // Any failure in half-open immediately opens the circuit
                transitionTo(State.OPEN)
            }
            State.OPEN -> { /* Already open */ }
        }
    }

    /**
     * Determine if an exception should count as a failure.
     * Override to customize failure detection.
     */
    private fun shouldCountFailure(exception: Exception): Boolean {
        // Don't count cancellation as failure
        if (exception is kotlinx.coroutines.CancellationException) return false
        // Don't count client errors (4xx) as circuit-breaker-worthy failures
        if (exception.message?.contains("4") == true &&
            exception.message?.contains("HTTP") == true) return false
        return true
    }

    /**
     * Transition to a new state.
     */
    private fun transitionTo(newState: State) {
        if (state != newState) {
            val oldState = state
            state = newState
            lastStateChange.set(System.currentTimeMillis())
            stateTransitions.incrementAndGet()

            // Reset counters based on new state
            when (newState) {
                State.CLOSED -> {
                    failureCount.set(0)
                    successCount.set(0)
                    halfOpenAttempts.set(0)
                }
                State.OPEN -> {
                    successCount.set(0)
                    halfOpenAttempts.set(0)
                }
                State.HALF_OPEN -> {
                    successCount.set(0)
                    halfOpenAttempts.set(0)
                }
            }

            android.util.Log.d("CircuitBreaker",
                "[$name] State transition: $oldState -> $newState")
        }
    }

    /**
     * Get remaining time before OPEN -> HALF_OPEN transition.
     */
    fun getRemainingOpenTime(): Long {
        return if (state == State.OPEN) {
            maxOf(0, openDurationMs - (System.currentTimeMillis() - lastStateChange.get()))
        } else 0
    }

    /**
     * Force reset the circuit breaker to CLOSED state.
     */
    suspend fun reset() = mutex.withLock {
        transitionTo(State.CLOSED)
        android.util.Log.i("CircuitBreaker", "[$name] Manually reset to CLOSED")
    }

    /**
     * Get current state.
     */
    val currentState: State get() = state

    /**
     * Check if circuit is open.
     */
    val isOpen: Boolean get() = state == State.OPEN

    /**
     * Check if circuit is closed.
     */
    val isClosed: Boolean get() = state == State.CLOSED

    /**
     * Check if circuit is half-open.
     */
    val isHalfOpen: Boolean get() = state == State.HALF_OPEN

    /**
     * Get circuit breaker statistics.
     */
    fun getStats(): CircuitBreakerStats = CircuitBreakerStats(
        name = name,
        state = state,
        failureCount = failureCount.get(),
        successCount = successCount.get(),
        totalCalls = totalCalls.get(),
        successfulCalls = successfulCalls.get(),
        failedCalls = failedCalls.get(),
        rejectedCalls = rejectedCalls.get(),
        stateTransitions = stateTransitions.get(),
        lastFailureTime = lastFailureTime.get(),
        lastStateChangeTime = lastStateChange.get(),
        remainingOpenTimeMs = getRemainingOpenTime(),
        failureThreshold = failureThreshold,
        successThreshold = successThreshold,
        openDurationMs = openDurationMs
    )

    companion object {
        /**
         * Create a circuit breaker with aggressive settings (faster failure detection).
         */
        fun aggressive(name: String = "aggressive") = CircuitBreaker(
            failureThreshold = 3,
            successThreshold = 1,
            openDurationMs = 10_000L,
            halfOpenMaxAttempts = 1,
            name = name
        )

        /**
         * Create a circuit breaker with lenient settings (more tolerant).
         */
        fun lenient(name: String = "lenient") = CircuitBreaker(
            failureThreshold = 10,
            successThreshold = 3,
            openDurationMs = 60_000L,
            halfOpenMaxAttempts = 5,
            name = name
        )

        /**
         * Create a circuit breaker for AI/API calls.
         */
        fun forApi(name: String = "api") = CircuitBreaker(
            failureThreshold = 5,
            successThreshold = 2,
            openDurationMs = 30_000L,
            halfOpenMaxAttempts = 2,
            name = name
        )
    }
}

/**
 * Exception thrown when circuit breaker is open.
 */
class CircuitOpenException(message: String) : Exception(message)

/**
 * Circuit breaker statistics.
 */
data class CircuitBreakerStats(
    val name: String,
    val state: CircuitBreaker.State,
    val failureCount: Int,
    val successCount: Int,
    val totalCalls: Long,
    val successfulCalls: Long,
    val failedCalls: Long,
    val rejectedCalls: Long,
    val stateTransitions: Long,
    val lastFailureTime: Long,
    val lastStateChangeTime: Long,
    val remainingOpenTimeMs: Long,
    val failureThreshold: Int,
    val successThreshold: Int,
    val openDurationMs: Long
) {
    val failureRate: Double
        get() = if (totalCalls > 0) failedCalls.toDouble() / totalCalls else 0.0

    val rejectionRate: Double
        get() = if (totalCalls > 0) rejectedCalls.toDouble() / totalCalls else 0.0

    val successRate: Double
        get() = if (totalCalls > 0) successfulCalls.toDouble() / totalCalls else 0.0

    val timeSinceLastFailure: Long
        get() = if (lastFailureTime > 0) System.currentTimeMillis() - lastFailureTime else -1

    val timeInCurrentState: Long
        get() = System.currentTimeMillis() - lastStateChangeTime
}
