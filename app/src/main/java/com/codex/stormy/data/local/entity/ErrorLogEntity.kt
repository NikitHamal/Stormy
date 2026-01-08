package com.codex.stormy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for persistent error logging.
 *
 * Tracks errors for:
 * - Debugging and diagnostics
 * - Identifying recurring issues
 * - Usage analytics
 * - User support
 */
@Entity(
    tableName = "error_logs",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["category"]),
        Index(value = ["projectId"])
    ]
)
data class ErrorLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String, // NETWORK, AI, FILE, DATABASE, GIT, etc.
    val errorCode: String,
    val message: String,
    val stackTrace: String? = null,
    val projectId: String? = null,
    val toolName: String? = null,
    val isResolved: Boolean = false
) {
    companion object {
        /**
         * Create from an exception.
         */
        fun fromException(
            exception: Throwable,
            category: String,
            projectId: String? = null,
            toolName: String? = null
        ): ErrorLogEntity {
            return ErrorLogEntity(
                category = category,
                errorCode = exception::class.simpleName ?: "Unknown",
                message = exception.message ?: "No message",
                stackTrace = exception.stackTraceToString().take(4000), // Limit stack trace size
                projectId = projectId,
                toolName = toolName
            )
        }
    }
}

/**
 * Error categories for filtering and grouping.
 */
object ErrorCategory {
    const val NETWORK = "NETWORK"
    const val AI = "AI"
    const val FILE = "FILE"
    const val DATABASE = "DATABASE"
    const val GIT = "GIT"
    const val VALIDATION = "VALIDATION"
    const val PERMISSION = "PERMISSION"
    const val RESOURCE = "RESOURCE"
    const val UNKNOWN = "UNKNOWN"
}
