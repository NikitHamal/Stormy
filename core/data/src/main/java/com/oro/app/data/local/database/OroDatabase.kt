package com.oro.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oro.app.data.local.dao.AiModelDao
import com.oro.app.data.local.dao.ChatMessageDao
import com.oro.app.data.local.dao.ProjectDao
import com.oro.app.data.local.entity.AiModelEntity
import com.oro.app.data.local.entity.ChatMessageEntity
import com.oro.app.data.local.entity.ProjectEntity

@Database(
    entities = [
        ProjectEntity::class,
        ChatMessageEntity::class,
        AiModelEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class OroDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun aiModelDao(): AiModelDao

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

        @Volatile
        private var INSTANCE: OroDatabase? = null

        fun getInstance(context: Context): OroDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OroDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_3_4)
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
