package com.oro.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One conversation thread inside a project.
 *
 * A project used to own exactly one chat; it now owns any number of these,
 * and every [ChatMessageEntity] belongs to one. Deleting the project cascades
 * to its sessions, and deleting a session cascades to its messages.
 *
 * No SQL defaults are declared: the table is created whole by
 * `MIGRATION_5_6` with the same CREATE statement Room generates, so there is
 * no ALTER TABLE path whose DEFAULT clause could disagree with a fresh
 * install's.
 */
@Entity(
    tableName = "chat_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class ChatSessionEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val title: String,
    val createdAt: Long,
    /** Last message written; what the session list sorts by. */
    val updatedAt: Long,
    /** Last time the user switched to it; what reopening a project restores. */
    val lastOpenedAt: Long,
    val isPinned: Boolean = false
)

/** A session row plus the two figures the session list shows beside it. */
data class ChatSessionWithStats(
    @Embedded val session: ChatSessionEntity,
    val messageCount: Int,
    /** The latest user message, pre-truncated in SQL; null for an empty session. */
    val lastUserMessage: String?
)

/** Per-project session figures for the home screen's project cards. */
data class ProjectSessionStats(
    val projectId: String,
    val sessionCount: Int,
    val lastActivity: Long
)
