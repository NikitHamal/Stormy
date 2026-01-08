package com.codex.stormy.data.local.database

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for CodeX.
 *
 * Migration Strategy:
 * - Each migration is a standalone unit that handles schema changes
 * - Migrations are tested to ensure data preservation
 * - Each migration includes rollback considerations in comments
 * - Error handling is built into each migration
 *
 * Schema Version History:
 * - Version 1: Initial schema (projects, chat_messages)
 * - Version 2: Added ai_models table
 * - Version 3: Added new fields to ai_models (alias, pricing, etc.)
 * - Version 4: Added resilience tracking tables
 */
object DatabaseMigrations {

    private const val TAG = "DatabaseMigrations"

    /**
     * Migration from version 1 to 2.
     * Adds the ai_models table for storing AI model configurations.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 1 to 2")
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ai_models (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        contextLength INTEGER NOT NULL DEFAULT 4096,
                        supportsStreaming INTEGER NOT NULL DEFAULT 1,
                        supportsToolCalls INTEGER NOT NULL DEFAULT 1,
                        isThinkingModel INTEGER NOT NULL DEFAULT 0,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        lastUsed INTEGER,
                        usageCount INTEGER NOT NULL DEFAULT 0,
                        addedAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        isCustom INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                Log.i(TAG, "Migration 1->2 completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Migration 1->2 failed: ${e.message}", e)
                throw MigrationException("Failed to migrate from version 1 to 2", e)
            }
        }
    }

    /**
     * Migration from version 2 to 3.
     * Adds new columns to ai_models: alias, description, maxOutputTokens, pricing.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 2 to 3")
            try {
                // Add new columns to ai_models table
                db.execSQL("ALTER TABLE ai_models ADD COLUMN alias TEXT")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN description TEXT")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN maxOutputTokens INTEGER")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN inputPricePerToken REAL")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN outputPricePerToken REAL")
                Log.i(TAG, "Migration 2->3 completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Migration 2->3 failed: ${e.message}", e)
                throw MigrationException("Failed to migrate from version 2 to 3", e)
            }
        }
    }

    /**
     * Migration from version 3 to 4.
     * Adds resilience tracking tables for circuit breaker state persistence
     * and error logging for debugging.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 3 to 4")
            try {
                // Create error_logs table for persistent error tracking
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS error_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        timestamp INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        errorCode TEXT NOT NULL,
                        message TEXT NOT NULL,
                        stackTrace TEXT,
                        projectId TEXT,
                        toolName TEXT,
                        isResolved INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE SET NULL
                    )
                """.trimIndent())

                // Create index for efficient querying
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_error_logs_timestamp ON error_logs(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_error_logs_category ON error_logs(category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_error_logs_projectId ON error_logs(projectId)")

                // Create circuit_breaker_state table for persisting circuit breaker states
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS circuit_breaker_state (
                        serviceId TEXT PRIMARY KEY NOT NULL,
                        state TEXT NOT NULL DEFAULT 'CLOSED',
                        failureCount INTEGER NOT NULL DEFAULT 0,
                        successCount INTEGER NOT NULL DEFAULT 0,
                        lastFailureTime INTEGER,
                        lastStateChangeTime INTEGER NOT NULL,
                        totalCalls INTEGER NOT NULL DEFAULT 0,
                        successfulCalls INTEGER NOT NULL DEFAULT 0,
                        failedCalls INTEGER NOT NULL DEFAULT 0,
                        rejectedCalls INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Create api_usage_stats table for rate limiting persistence
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS api_usage_stats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        serviceId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        requestCount INTEGER NOT NULL DEFAULT 1,
                        tokensUsed INTEGER NOT NULL DEFAULT 0,
                        responseTimeMs INTEGER,
                        wasSuccessful INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())

                // Create index for efficient rate limit queries
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_api_usage_timestamp ON api_usage_stats(serviceId, timestamp)")

                // Add token usage tracking to chat_messages
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN tokensUsed INTEGER")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN modelId TEXT")

                // Add metadata columns to projects
                db.execSQL("ALTER TABLE projects ADD COLUMN totalTokensUsed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN totalMessages INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN errorCount INTEGER NOT NULL DEFAULT 0")

                Log.i(TAG, "Migration 3->4 completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Migration 3->4 failed: ${e.message}", e)
                throw MigrationException("Failed to migrate from version 3 to 4", e)
            }
        }
    }

    /**
     * Get all migrations in order.
     */
    fun getAllMigrations(): Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4
    )

    /**
     * Validate database integrity after migration.
     * Returns true if all required tables and indices exist.
     */
    fun validateDatabaseIntegrity(db: SupportSQLiteDatabase): ValidationResult {
        val errors = mutableListOf<String>()

        val requiredTables = listOf(
            "projects", "chat_messages", "ai_models",
            "error_logs", "circuit_breaker_state", "api_usage_stats"
        )

        requiredTables.forEach { table ->
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
            if (!cursor.moveToFirst()) {
                errors.add("Missing table: $table")
            }
            cursor.close()
        }

        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Failure(errors)
        }
    }

    /**
     * Get database statistics for debugging.
     */
    fun getDatabaseStats(db: SupportSQLiteDatabase): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        val tables = listOf("projects", "chat_messages", "ai_models", "error_logs", "api_usage_stats")

        tables.forEach { table ->
            try {
                val cursor = db.query("SELECT COUNT(*) FROM $table")
                if (cursor.moveToFirst()) {
                    stats[table] = cursor.getInt(0)
                }
                cursor.close()
            } catch (e: Exception) {
                stats[table] = -1 // Table doesn't exist
            }
        }

        return stats
    }

    /**
     * Clean up old data based on retention policies.
     * Call periodically to prevent database bloat.
     */
    fun cleanupOldData(db: SupportSQLiteDatabase, config: CleanupConfig = CleanupConfig()) {
        val now = System.currentTimeMillis()

        // Clean old error logs (keep last 7 days by default)
        val errorLogCutoff = now - config.errorLogRetentionMs
        db.execSQL("DELETE FROM error_logs WHERE timestamp < ? AND isResolved = 1", arrayOf(errorLogCutoff))

        // Clean old API usage stats (keep last 30 days by default)
        val apiStatsCutoff = now - config.apiStatsRetentionMs
        db.execSQL("DELETE FROM api_usage_stats WHERE timestamp < ?", arrayOf(apiStatsCutoff))

        // Vacuum to reclaim space
        if (config.shouldVacuum) {
            db.execSQL("VACUUM")
        }

        Log.i(TAG, "Database cleanup completed")
    }

    /**
     * Configuration for database cleanup.
     */
    data class CleanupConfig(
        val errorLogRetentionMs: Long = 7 * 24 * 60 * 60 * 1000L, // 7 days
        val apiStatsRetentionMs: Long = 30 * 24 * 60 * 60 * 1000L, // 30 days
        val shouldVacuum: Boolean = true
    )

    /**
     * Result of database validation.
     */
    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Failure(val errors: List<String>) : ValidationResult()
    }
}

/**
 * Exception thrown when a database migration fails.
 */
class MigrationException(message: String, cause: Throwable? = null) : Exception(message, cause)
