package com.codex.stormy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.codex.stormy.data.local.entity.AggregatedUsageStats
import com.codex.stormy.data.local.entity.ApiUsageStatsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for API usage statistics.
 *
 * Provides methods for:
 * - Recording API requests
 * - Querying usage history
 * - Rate limit calculations
 * - Analytics and cost tracking
 */
@Dao
interface ApiUsageStatsDao {

    /**
     * Insert a new API usage record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: ApiUsageStatsEntity): Long

    /**
     * Insert multiple API usage records.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stats: List<ApiUsageStatsEntity>)

    /**
     * Get all usage stats for a service.
     */
    @Query("SELECT * FROM api_usage_stats WHERE serviceId = :serviceId ORDER BY timestamp DESC")
    fun getStatsForService(serviceId: String): Flow<List<ApiUsageStatsEntity>>

    /**
     * Get recent stats for rate limiting.
     */
    @Query("""
        SELECT * FROM api_usage_stats
        WHERE serviceId = :serviceId AND timestamp >= :sinceTimestamp
        ORDER BY timestamp DESC
    """)
    suspend fun getRecentStats(serviceId: String, sinceTimestamp: Long): List<ApiUsageStatsEntity>

    /**
     * Get request count in a time window (for rate limiting).
     */
    @Query("""
        SELECT COALESCE(SUM(requestCount), 0)
        FROM api_usage_stats
        WHERE serviceId = :serviceId AND timestamp >= :sinceTimestamp
    """)
    suspend fun getRequestCount(serviceId: String, sinceTimestamp: Long): Int

    /**
     * Get total tokens used in a time window.
     */
    @Query("""
        SELECT COALESCE(SUM(tokensUsed), 0)
        FROM api_usage_stats
        WHERE serviceId = :serviceId AND timestamp >= :sinceTimestamp
    """)
    suspend fun getTotalTokensUsed(serviceId: String, sinceTimestamp: Long): Long

    /**
     * Get average response time in a time window.
     */
    @Query("""
        SELECT AVG(responseTimeMs)
        FROM api_usage_stats
        WHERE serviceId = :serviceId AND timestamp >= :sinceTimestamp AND responseTimeMs IS NOT NULL
    """)
    suspend fun getAverageResponseTime(serviceId: String, sinceTimestamp: Long): Long?

    /**
     * Get aggregated stats for a time period.
     */
    @Query("""
        SELECT
            serviceId,
            :periodStart as periodStart,
            :periodEnd as periodEnd,
            COALESCE(SUM(requestCount), 0) as totalRequests,
            COALESCE(SUM(CASE WHEN wasSuccessful = 1 THEN requestCount ELSE 0 END), 0) as successfulRequests,
            COALESCE(SUM(CASE WHEN wasSuccessful = 0 THEN requestCount ELSE 0 END), 0) as failedRequests,
            COALESCE(SUM(tokensUsed), 0) as totalTokensUsed,
            AVG(responseTimeMs) as averageResponseTimeMs,
            MAX(responseTimeMs) as maxResponseTimeMs,
            MIN(responseTimeMs) as minResponseTimeMs
        FROM api_usage_stats
        WHERE serviceId = :serviceId AND timestamp >= :periodStart AND timestamp < :periodEnd
        GROUP BY serviceId
    """)
    suspend fun getAggregatedStats(
        serviceId: String,
        periodStart: Long,
        periodEnd: Long
    ): AggregatedUsageStats?

    /**
     * Get success rate for a service in a time window.
     */
    @Query("""
        SELECT CASE
            WHEN SUM(requestCount) = 0 THEN 1.0
            ELSE CAST(SUM(CASE WHEN wasSuccessful = 1 THEN requestCount ELSE 0 END) AS REAL) /
                 CAST(SUM(requestCount) AS REAL)
        END
        FROM api_usage_stats
        WHERE serviceId = :serviceId AND timestamp >= :sinceTimestamp
    """)
    suspend fun getSuccessRate(serviceId: String, sinceTimestamp: Long): Double

    /**
     * Delete old stats entries.
     */
    @Query("DELETE FROM api_usage_stats WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldStats(beforeTimestamp: Long): Int

    /**
     * Delete stats for a specific service.
     */
    @Query("DELETE FROM api_usage_stats WHERE serviceId = :serviceId")
    suspend fun deleteStatsForService(serviceId: String)

    /**
     * Delete all stats.
     */
    @Query("DELETE FROM api_usage_stats")
    suspend fun deleteAll()

    /**
     * Get all unique service IDs.
     */
    @Query("SELECT DISTINCT serviceId FROM api_usage_stats")
    suspend fun getAllServiceIds(): List<String>

    /**
     * Get total requests count all-time.
     */
    @Query("SELECT COALESCE(SUM(requestCount), 0) FROM api_usage_stats")
    suspend fun getTotalRequestsAllTime(): Long

    /**
     * Get total tokens used all-time.
     */
    @Query("SELECT COALESCE(SUM(tokensUsed), 0) FROM api_usage_stats")
    suspend fun getTotalTokensAllTime(): Long

    /**
     * Check if rate limit is exceeded (helper query).
     */
    @Query("""
        SELECT COALESCE(SUM(requestCount), 0) >= :limit
        FROM api_usage_stats
        WHERE serviceId = :serviceId AND timestamp >= :sinceTimestamp
    """)
    suspend fun isRateLimitExceeded(
        serviceId: String,
        sinceTimestamp: Long,
        limit: Int
    ): Boolean

    /**
     * Get requests per minute for the last hour (for analytics).
     */
    @Query("""
        SELECT
            (timestamp / 60000) * 60000 as minute,
            SUM(requestCount) as count
        FROM api_usage_stats
        WHERE serviceId = :serviceId AND timestamp >= :sinceTimestamp
        GROUP BY minute
        ORDER BY minute DESC
    """)
    suspend fun getRequestsPerMinute(
        serviceId: String,
        sinceTimestamp: Long
    ): List<RequestsPerMinute>
}

/**
 * Data class for requests per minute results.
 */
data class RequestsPerMinute(
    val minute: Long,
    val count: Int
)
