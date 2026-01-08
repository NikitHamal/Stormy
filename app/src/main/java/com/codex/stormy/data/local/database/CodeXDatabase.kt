package com.codex.stormy.data.local.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.codex.stormy.data.local.dao.AiModelDao
import com.codex.stormy.data.local.dao.ApiUsageStatsDao
import com.codex.stormy.data.local.dao.ChatMessageDao
import com.codex.stormy.data.local.dao.CircuitBreakerStateDao
import com.codex.stormy.data.local.dao.ErrorLogDao
import com.codex.stormy.data.local.dao.ProjectDao
import com.codex.stormy.data.local.entity.AiModelEntity
import com.codex.stormy.data.local.entity.ApiUsageStatsEntity
import com.codex.stormy.data.local.entity.ChatMessageEntity
import com.codex.stormy.data.local.entity.CircuitBreakerStateEntity
import com.codex.stormy.data.local.entity.ErrorLogEntity
import com.codex.stormy.data.local.entity.ProjectEntity

/**
 * Main database for CodeX application.
 *
 * Schema Version History:
 * - Version 1: Initial schema (projects, chat_messages)
 * - Version 2: Added ai_models table
 * - Version 3: Added new fields to ai_models (alias, pricing, etc.)
 * - Version 4: Added resilience tracking tables (error_logs, circuit_breaker_state, api_usage_stats)
 *              and additional tracking columns to existing tables
 */
@Database(
    entities = [
        ProjectEntity::class,
        ChatMessageEntity::class,
        AiModelEntity::class,
        ErrorLogEntity::class,
        CircuitBreakerStateEntity::class,
        ApiUsageStatsEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class CodeXDatabase : RoomDatabase() {

    // Core DAOs
    abstract fun projectDao(): ProjectDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun aiModelDao(): AiModelDao

    // Resilience tracking DAOs
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun circuitBreakerStateDao(): CircuitBreakerStateDao
    abstract fun apiUsageStatsDao(): ApiUsageStatsDao

    companion object {
        private const val TAG = "CodeXDatabase"
        private const val DATABASE_NAME = "codex_database"

        @Volatile
        private var INSTANCE: CodeXDatabase? = null

        /**
         * Get the singleton database instance.
         * Uses proper migrations instead of destructive migration.
         */
        fun getInstance(context: Context): CodeXDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CodeXDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*DatabaseMigrations.getAllMigrations())
                    .addCallback(DatabaseCallback())
                    // Fallback only for development - in production, migrations should handle all cases
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Clear the singleton instance (for testing).
         */
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }

        /**
         * Get database without initializing (for checking if exists).
         */
        fun getDatabasePath(context: Context): String {
            return context.getDatabasePath(DATABASE_NAME).absolutePath
        }
    }

    /**
     * Database callback for initialization and monitoring.
     */
    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Log.i(TAG, "Database created with version ${db.version}")
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            Log.d(TAG, "Database opened, version: ${db.version}")

            // Validate database integrity on open
            val validationResult = DatabaseMigrations.validateDatabaseIntegrity(db)
            when (validationResult) {
                is DatabaseMigrations.ValidationResult.Success -> {
                    Log.d(TAG, "Database integrity check passed")
                }
                is DatabaseMigrations.ValidationResult.Failure -> {
                    Log.w(TAG, "Database integrity issues: ${validationResult.errors}")
                }
            }
        }

        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
            super.onDestructiveMigration(db)
            Log.w(TAG, "Destructive migration performed - data was lost!")
        }
    }

    /**
     * Perform database maintenance operations.
     * Call periodically (e.g., on app startup or in background worker).
     */
    suspend fun performMaintenance() {
        runInTransaction {
            val db = openHelper.writableDatabase

            // Clean up old data
            DatabaseMigrations.cleanupOldData(db)

            // Log database stats
            val stats = DatabaseMigrations.getDatabaseStats(db)
            Log.d(TAG, "Database stats: $stats")
        }
    }

    /**
     * Get database statistics.
     */
    fun getStats(): Map<String, Int> {
        return DatabaseMigrations.getDatabaseStats(openHelper.readableDatabase)
    }

    /**
     * Check database health.
     */
    fun checkHealth(): DatabaseHealth {
        return try {
            val db = openHelper.readableDatabase
            val validation = DatabaseMigrations.validateDatabaseIntegrity(db)
            val stats = DatabaseMigrations.getDatabaseStats(db)

            when (validation) {
                is DatabaseMigrations.ValidationResult.Success -> {
                    DatabaseHealth(
                        isHealthy = true,
                        version = db.version,
                        tableStats = stats,
                        issues = emptyList()
                    )
                }
                is DatabaseMigrations.ValidationResult.Failure -> {
                    DatabaseHealth(
                        isHealthy = false,
                        version = db.version,
                        tableStats = stats,
                        issues = validation.errors
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Database health check failed", e)
            DatabaseHealth(
                isHealthy = false,
                version = -1,
                tableStats = emptyMap(),
                issues = listOf("Failed to check database: ${e.message}")
            )
        }
    }
}

/**
 * Database health status.
 */
data class DatabaseHealth(
    val isHealthy: Boolean,
    val version: Int,
    val tableStats: Map<String, Int>,
    val issues: List<String>
)
