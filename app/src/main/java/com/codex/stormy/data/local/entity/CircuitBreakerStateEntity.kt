package com.codex.stormy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for persisting circuit breaker states across app restarts.
 *
 * This allows the app to remember which services were failing
 * and maintain circuit breaker state for reliability.
 */
@Entity(tableName = "circuit_breaker_state")
data class CircuitBreakerStateEntity(
    @PrimaryKey
    val serviceId: String,
    val state: String = "CLOSED", // CLOSED, OPEN, HALF_OPEN
    val failureCount: Int = 0,
    val successCount: Int = 0,
    val lastFailureTime: Long? = null,
    val lastStateChangeTime: Long = System.currentTimeMillis(),
    val totalCalls: Long = 0,
    val successfulCalls: Long = 0,
    val failedCalls: Long = 0,
    val rejectedCalls: Long = 0
) {
    /**
     * Check if the circuit breaker should be reset based on time.
     * @param openDurationMs How long the circuit stays open before testing
     */
    fun shouldTransitionToHalfOpen(openDurationMs: Long): Boolean {
        return state == "OPEN" &&
                System.currentTimeMillis() - lastStateChangeTime >= openDurationMs
    }

    /**
     * Get remaining time in OPEN state.
     */
    fun getRemainingOpenTime(openDurationMs: Long): Long {
        return if (state == "OPEN") {
            maxOf(0, openDurationMs - (System.currentTimeMillis() - lastStateChangeTime))
        } else {
            0
        }
    }

    /**
     * Calculate failure rate.
     */
    val failureRate: Double
        get() = if (totalCalls > 0) failedCalls.toDouble() / totalCalls else 0.0

    /**
     * Calculate success rate.
     */
    val successRate: Double
        get() = if (totalCalls > 0) successfulCalls.toDouble() / totalCalls else 0.0

    companion object {
        const val STATE_CLOSED = "CLOSED"
        const val STATE_OPEN = "OPEN"
        const val STATE_HALF_OPEN = "HALF_OPEN"

        /**
         * Create a new circuit breaker state for a service.
         */
        fun create(serviceId: String): CircuitBreakerStateEntity {
            return CircuitBreakerStateEntity(serviceId = serviceId)
        }
    }
}
