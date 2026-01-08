package com.codex.stormy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for tracking API usage statistics.
 *
 * Used for:
 * - Rate limiting enforcement persistence
 * - Usage analytics
 * - Cost tracking
 * - Performance monitoring
 */
@Entity(
    tableName = "api_usage_stats",
    indices = [
        Index(value = ["serviceId", "timestamp"])
    ]
)
data class ApiUsageStatsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serviceId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val requestCount: Int = 1,
    val tokensUsed: Int = 0,
    val responseTimeMs: Long? = null,
    val wasSuccessful: Boolean = true
) {
    companion object {
        /**
         * Create a new usage record for a successful request.
         */
        fun success(
            serviceId: String,
            tokensUsed: Int = 0,
            responseTimeMs: Long? = null
        ): ApiUsageStatsEntity {
            return ApiUsageStatsEntity(
                serviceId = serviceId,
                tokensUsed = tokensUsed,
                responseTimeMs = responseTimeMs,
                wasSuccessful = true
            )
        }

        /**
         * Create a new usage record for a failed request.
         */
        fun failure(
            serviceId: String,
            responseTimeMs: Long? = null
        ): ApiUsageStatsEntity {
            return ApiUsageStatsEntity(
                serviceId = serviceId,
                tokensUsed = 0,
                responseTimeMs = responseTimeMs,
                wasSuccessful = false
            )
        }
    }
}

/**
 * Aggregated usage statistics for a time period.
 */
data class AggregatedUsageStats(
    val serviceId: String,
    val periodStart: Long,
    val periodEnd: Long,
    val totalRequests: Int,
    val successfulRequests: Int,
    val failedRequests: Int,
    val totalTokensUsed: Long,
    val averageResponseTimeMs: Long?,
    val maxResponseTimeMs: Long?,
    val minResponseTimeMs: Long?
) {
    val successRate: Double
        get() = if (totalRequests > 0) successfulRequests.toDouble() / totalRequests else 0.0

    val failureRate: Double
        get() = if (totalRequests > 0) failedRequests.toDouble() / totalRequests else 0.0
}
