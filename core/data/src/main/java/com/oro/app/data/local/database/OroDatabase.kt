package com.oro.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oro.app.data.local.dao.AiModelDao
import com.oro.app.data.local.dao.ChatMessageDao
import com.oro.app.data.local.dao.ChatSessionDao
import com.oro.app.data.local.dao.ProjectDao
import com.oro.app.data.local.entity.AiModelEntity
import com.oro.app.data.local.entity.ChatMessageEntity
import com.oro.app.data.local.entity.ChatSessionEntity
import com.oro.app.data.local.entity.ProjectEntity
import com.oro.app.domain.model.SessionTitles
import java.util.UUID

@Database(
    entities = [
        ProjectEntity::class,
        ChatMessageEntity::class,
        AiModelEntity::class,
        ChatSessionEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class OroDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun chatSessionDao(): ChatSessionDao

    companion object {
        private const val DATABASE_NAME = "oro_database"

        /** Adds `projects.isPinned`, defaulted so existing rows are valid. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE projects ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Adds `ai_models.reportedContextLength`. Nullable with no default,
         * which is exactly what Room expects for an `Int?` column; existing
         * rows read as "provider said nothing" and resolve from the table.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_models ADD COLUMN reportedContextLength INTEGER")
            }
        }

        /**
         * Splits each project's single chat into sessions.
         *
         * Creates `chat_sessions`, gives every project that has messages one
         * session holding all of them (titled from its first user message, as
         * a new session would be), and rebuilds `chat_messages` with a
         * NOT NULL `sessionId` and its foreign key. SQLite cannot add a
         * foreign key to an existing table, so the table is copied rather
         * than altered.
         *
         * Every CREATE below is Room's own generated statement for the
         * entities, character for character apart from the table name, and
         * the index names are Room's `index_<table>_<column>`; Room validates
         * the migrated schema against the entities on first open and crashes
         * on any difference. ProjectSchemaMigrationTest pins them against
         * schemas/6.json.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_sessions` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_sessions_projectId` ON `chat_sessions` (`projectId`)"
                )

                // One session per project that has a conversation. Only
                // projects that still exist: a message orphaned by a delete
                // made before foreign keys were enforced has no project to
                // belong to and is dropped by the join below.
                data class Seed(val projectId: String, val first: Long, val last: Long)
                val seeds = mutableListOf<Seed>()
                db.query(
                    "SELECT m.projectId, MIN(m.timestamp), MAX(m.timestamp) FROM chat_messages m " +
                        "INNER JOIN projects p ON p.id = m.projectId GROUP BY m.projectId"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        seeds += Seed(cursor.getString(0), cursor.getLong(1), cursor.getLong(2))
                    }
                }
                for (seed in seeds) {
                    val firstUserMessage = db.query(
                        "SELECT content FROM chat_messages WHERE projectId = ? AND role = 'USER' " +
                            "ORDER BY timestamp ASC LIMIT 1",
                        arrayOf<Any?>(seed.projectId)
                    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    db.execSQL(
                        "INSERT INTO chat_sessions (id, projectId, title, createdAt, updatedAt, lastOpenedAt, isPinned) " +
                            "VALUES (?, ?, ?, ?, ?, ?, 0)",
                        arrayOf<Any?>(
                            UUID.randomUUID().toString(),
                            seed.projectId,
                            SessionTitles.fromFirstMessage(firstUserMessage),
                            seed.first,
                            seed.last,
                            // Reopening the project lands in this session.
                            seed.last
                        )
                    )
                }

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, `codeChanges` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                // Exactly one session per project at this point, so the join
                // assigns each message to its project's session.
                db.execSQL(
                    "INSERT INTO chat_messages_new (id, projectId, sessionId, role, content, timestamp, status, codeChanges) " +
                        "SELECT m.id, m.projectId, s.id, m.role, m.content, m.timestamp, m.status, m.codeChanges " +
                        "FROM chat_messages m INNER JOIN chat_sessions s ON s.projectId = m.projectId"
                )
                db.execSQL("DROP TABLE chat_messages")
                db.execSQL("ALTER TABLE chat_messages_new RENAME TO chat_messages")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_projectId` ON `chat_messages` (`projectId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: OroDatabase? = null

        fun getInstance(context: Context): OroDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OroDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // Still the fallback for the older unversioned schemas,
                    // but 3 -> 4 is migrated properly: by this point users
                    // have real projects in here, and dropping the table to
                    // add one boolean column would delete them.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
