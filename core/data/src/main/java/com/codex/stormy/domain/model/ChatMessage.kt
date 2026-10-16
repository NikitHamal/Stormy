package com.codex.stormy.domain.model

import com.codex.stormy.data.local.entity.ChatMessageEntity
import com.codex.stormy.data.local.entity.MessageRole
import com.codex.stormy.data.local.entity.MessageStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ChatMessage(
    val id: String,
    val projectId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus,
    val codeChanges: List<CodeChange>?
) {
    val formattedTime: String
        get() {
            val dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
            )
            return dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        }

    val isUser: Boolean get() = role == MessageRole.USER
    val isAssistant: Boolean get() = role == MessageRole.ASSISTANT

    fun toEntity(): ChatMessageEntity = ChatMessageEntity(
        id = id,
        projectId = projectId,
        role = role.name,
        content = content,
        timestamp = timestamp,
        status = status.name,
        codeChanges = codeChanges?.let { changes ->
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(CodeChange.serializer()),
                changes
            )
        }
    )

    companion object {
        fun fromEntity(entity: ChatMessageEntity): ChatMessage = ChatMessage(
            id = entity.id,
            projectId = entity.projectId,
            role = try {
                MessageRole.valueOf(entity.role)
            } catch (e: IllegalArgumentException) {
                MessageRole.USER
            },
            content = entity.content,
            timestamp = entity.timestamp,
            status = try {
                MessageStatus.valueOf(entity.status)
            } catch (e: IllegalArgumentException) {
                MessageStatus.RECEIVED
            },
            codeChanges = entity.codeChanges?.let { json ->
                try {
                    kotlinx.serialization.json.Json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(CodeChange.serializer()),
                        json
                    )
                } catch (e: Exception) {
                    null
                }
            }
        )

        fun createUserMessage(projectId: String, content: String): ChatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            role = MessageRole.USER,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            codeChanges = null
        )

        fun createAssistantMessage(
            projectId: String,
            content: String,
            status: MessageStatus = MessageStatus.RECEIVED,
            codeChanges: List<CodeChange>? = null
        ): ChatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            role = MessageRole.ASSISTANT,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = status,
            codeChanges = codeChanges
        )
    }
}

@kotlinx.serialization.Serializable
data class CodeChange(
    val filePath: String,
    val operation: CodeOperation,
    val content: String?,
    val oldContent: String?
)

@kotlinx.serialization.Serializable
enum class CodeOperation {
    CREATE,
    UPDATE,
    DELETE,
    RENAME
}
