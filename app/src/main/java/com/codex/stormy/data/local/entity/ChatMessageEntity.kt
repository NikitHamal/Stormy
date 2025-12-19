package com.codex.stormy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
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
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val status: String,
    val codeChanges: String?
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class MessageStatus {
    PENDING,
    SENT,
    RECEIVED,
    ERROR,
    STREAMING
}
