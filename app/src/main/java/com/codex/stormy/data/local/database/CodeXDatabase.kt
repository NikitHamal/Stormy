package com.codex.stormy.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.codex.stormy.data.local.dao.ChatMessageDao
import com.codex.stormy.data.local.dao.ProjectDao
import com.codex.stormy.data.local.entity.ChatMessageEntity
import com.codex.stormy.data.local.entity.ProjectEntity

@Database(
    entities = [
        ProjectEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class CodeXDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        private const val DATABASE_NAME = "codex_database"

        @Volatile
        private var INSTANCE: CodeXDatabase? = null

        fun getInstance(context: Context): CodeXDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CodeXDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
