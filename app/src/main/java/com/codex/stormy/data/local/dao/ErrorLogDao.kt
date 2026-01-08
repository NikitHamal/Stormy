package com.codex.stormy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.codex.stormy.data.local.entity.ErrorLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for error logs.
 *
 * Provides methods for:
 * - Logging errors
 * - Querying error history
 * - Analytics and debugging
 * - Cleanup operations
 */
@Dao
interface ErrorLogDao {

    /**
     * Insert a new error log entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(errorLog: ErrorLogEntity): Long

    /**
     * Insert multiple error log entries.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(errorLogs: List<ErrorLogEntity>)

    /**
     * Update an existing error log entry.
     */
    @Update
    suspend fun update(errorLog: ErrorLogEntity)

    /**
     * Delete an error log entry.
     */
    @Delete
    suspend fun delete(errorLog: ErrorLogEntity)

    /**
     * Get all error logs ordered by timestamp descending.
     */
    @Query("SELECT * FROM error_logs ORDER BY timestamp DESC")
    fun getAllErrorLogs(): Flow<List<ErrorLogEntity>>

    /**
     * Get error logs by category.
     */
    @Query("SELECT * FROM error_logs WHERE category = :category ORDER BY timestamp DESC")
    fun getErrorLogsByCategory(category: String): Flow<List<ErrorLogEntity>>

    /**
     * Get error logs for a specific project.
     */
    @Query("SELECT * FROM error_logs WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getErrorLogsByProject(projectId: String): Flow<List<ErrorLogEntity>>

    /**
     * Get recent error logs (within the specified time window).
     */
    @Query("SELECT * FROM error_logs WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getRecentErrorLogs(sinceTimestamp: Long): List<ErrorLogEntity>

    /**
     * Get unresolved error logs.
     */
    @Query("SELECT * FROM error_logs WHERE isResolved = 0 ORDER BY timestamp DESC")
    fun getUnresolvedErrorLogs(): Flow<List<ErrorLogEntity>>

    /**
     * Get error count by category within a time window.
     */
    @Query("""
        SELECT category, COUNT(*) as count
        FROM error_logs
        WHERE timestamp >= :sinceTimestamp
        GROUP BY category
    """)
    suspend fun getErrorCountsByCategory(sinceTimestamp: Long): List<CategoryCount>

    /**
     * Get error count for a specific project.
     */
    @Query("SELECT COUNT(*) FROM error_logs WHERE projectId = :projectId")
    suspend fun getErrorCountForProject(projectId: String): Int

    /**
     * Mark an error as resolved.
     */
    @Query("UPDATE error_logs SET isResolved = 1 WHERE id = :errorId")
    suspend fun markAsResolved(errorId: Long)

    /**
     * Mark all errors for a project as resolved.
     */
    @Query("UPDATE error_logs SET isResolved = 1 WHERE projectId = :projectId")
    suspend fun markProjectErrorsAsResolved(projectId: String)

    /**
     * Delete old resolved errors.
     */
    @Query("DELETE FROM error_logs WHERE timestamp < :beforeTimestamp AND isResolved = 1")
    suspend fun deleteOldResolvedErrors(beforeTimestamp: Long): Int

    /**
     * Delete all resolved errors.
     */
    @Query("DELETE FROM error_logs WHERE isResolved = 1")
    suspend fun deleteAllResolvedErrors(): Int

    /**
     * Delete all errors for a project.
     */
    @Query("DELETE FROM error_logs WHERE projectId = :projectId")
    suspend fun deleteErrorsForProject(projectId: String)

    /**
     * Get total error count.
     */
    @Query("SELECT COUNT(*) FROM error_logs")
    suspend fun getTotalErrorCount(): Int

    /**
     * Check if there are recent errors of a specific category.
     */
    @Query("""
        SELECT COUNT(*) > 0
        FROM error_logs
        WHERE category = :category AND timestamp >= :sinceTimestamp
    """)
    suspend fun hasRecentErrors(category: String, sinceTimestamp: Long): Boolean

    /**
     * Get the most common error codes.
     */
    @Query("""
        SELECT errorCode, COUNT(*) as count
        FROM error_logs
        WHERE timestamp >= :sinceTimestamp
        GROUP BY errorCode
        ORDER BY count DESC
        LIMIT :limit
    """)
    suspend fun getMostCommonErrors(sinceTimestamp: Long, limit: Int = 10): List<ErrorCodeCount>
}

/**
 * Data class for category count results.
 */
data class CategoryCount(
    val category: String,
    val count: Int
)

/**
 * Data class for error code count results.
 */
data class ErrorCodeCount(
    val errorCode: String,
    val count: Int
)
