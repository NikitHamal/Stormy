package com.codex.stormy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.codex.stormy.data.local.entity.CircuitBreakerStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for circuit breaker state persistence.
 *
 * Allows circuit breaker states to survive app restarts,
 * maintaining protection against failing services.
 */
@Dao
interface CircuitBreakerStateDao {

    /**
     * Insert or update a circuit breaker state.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: CircuitBreakerStateEntity)

    /**
     * Update an existing circuit breaker state.
     */
    @Update
    suspend fun update(state: CircuitBreakerStateEntity)

    /**
     * Delete a circuit breaker state.
     */
    @Delete
    suspend fun delete(state: CircuitBreakerStateEntity)

    /**
     * Get circuit breaker state by service ID.
     */
    @Query("SELECT * FROM circuit_breaker_state WHERE serviceId = :serviceId")
    suspend fun getByServiceId(serviceId: String): CircuitBreakerStateEntity?

    /**
     * Get circuit breaker state as Flow for reactive updates.
     */
    @Query("SELECT * FROM circuit_breaker_state WHERE serviceId = :serviceId")
    fun getByServiceIdFlow(serviceId: String): Flow<CircuitBreakerStateEntity?>

    /**
     * Get all circuit breaker states.
     */
    @Query("SELECT * FROM circuit_breaker_state")
    suspend fun getAll(): List<CircuitBreakerStateEntity>

    /**
     * Get all circuit breaker states as Flow.
     */
    @Query("SELECT * FROM circuit_breaker_state")
    fun getAllFlow(): Flow<List<CircuitBreakerStateEntity>>

    /**
     * Get all open circuit breakers.
     */
    @Query("SELECT * FROM circuit_breaker_state WHERE state = 'OPEN'")
    suspend fun getOpenCircuits(): List<CircuitBreakerStateEntity>

    /**
     * Get all circuit breakers with failures.
     */
    @Query("SELECT * FROM circuit_breaker_state WHERE failureCount > 0 ORDER BY failureCount DESC")
    suspend fun getCircuitsWithFailures(): List<CircuitBreakerStateEntity>

    /**
     * Reset a circuit breaker to CLOSED state.
     */
    @Query("""
        UPDATE circuit_breaker_state
        SET state = 'CLOSED',
            failureCount = 0,
            successCount = 0,
            lastStateChangeTime = :timestamp
        WHERE serviceId = :serviceId
    """)
    suspend fun resetCircuit(serviceId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Reset all circuit breakers.
     */
    @Query("""
        UPDATE circuit_breaker_state
        SET state = 'CLOSED',
            failureCount = 0,
            successCount = 0,
            lastStateChangeTime = :timestamp
    """)
    suspend fun resetAllCircuits(timestamp: Long = System.currentTimeMillis())

    /**
     * Update circuit state.
     */
    @Query("""
        UPDATE circuit_breaker_state
        SET state = :newState,
            lastStateChangeTime = :timestamp,
            failureCount = CASE WHEN :newState = 'CLOSED' THEN 0 ELSE failureCount END,
            successCount = CASE WHEN :newState IN ('OPEN', 'HALF_OPEN') THEN 0 ELSE successCount END
        WHERE serviceId = :serviceId
    """)
    suspend fun updateState(serviceId: String, newState: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Increment failure count.
     */
    @Query("""
        UPDATE circuit_breaker_state
        SET failureCount = failureCount + 1,
            failedCalls = failedCalls + 1,
            totalCalls = totalCalls + 1,
            lastFailureTime = :timestamp
        WHERE serviceId = :serviceId
    """)
    suspend fun incrementFailure(serviceId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Increment success count.
     */
    @Query("""
        UPDATE circuit_breaker_state
        SET successCount = successCount + 1,
            successfulCalls = successfulCalls + 1,
            totalCalls = totalCalls + 1
        WHERE serviceId = :serviceId
    """)
    suspend fun incrementSuccess(serviceId: String)

    /**
     * Increment rejected calls count.
     */
    @Query("""
        UPDATE circuit_breaker_state
        SET rejectedCalls = rejectedCalls + 1,
            totalCalls = totalCalls + 1
        WHERE serviceId = :serviceId
    """)
    suspend fun incrementRejected(serviceId: String)

    /**
     * Delete circuit breaker state by service ID.
     */
    @Query("DELETE FROM circuit_breaker_state WHERE serviceId = :serviceId")
    suspend fun deleteByServiceId(serviceId: String)

    /**
     * Delete all circuit breaker states.
     */
    @Query("DELETE FROM circuit_breaker_state")
    suspend fun deleteAll()

    /**
     * Get services with high failure rates.
     */
    @Query("""
        SELECT * FROM circuit_breaker_state
        WHERE totalCalls > 10 AND
              CAST(failedCalls AS REAL) / CAST(totalCalls AS REAL) > :threshold
        ORDER BY failedCalls DESC
    """)
    suspend fun getHighFailureRateServices(threshold: Double = 0.3): List<CircuitBreakerStateEntity>
}
