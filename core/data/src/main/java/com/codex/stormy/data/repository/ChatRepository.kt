package com.codex.stormy.data.repository

import android.content.Context
import com.codex.stormy.data.local.dao.ChatMessageDao
import com.codex.stormy.data.local.entity.ChatMessageEntity
import com.codex.stormy.data.local.entity.MessageStatus
import com.codex.stormy.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Repository for managing chat message persistence.
 * Handles saving, loading, and exporting conversation history per project.
 */
class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val context: Context
) {
    /**
     * Get all messages for a project as a Flow for reactive updates
     */
    fun getMessagesForProject(projectId: String): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesByProjectId(projectId)
            .map { entities -> entities.map { ChatMessage.fromEntity(it) } }
    }

    /**
     * Get recent messages for a project (for context window management)
     */
    suspend fun getRecentMessages(projectId: String, limit: Int): List<ChatMessage> {
        return chatMessageDao.getRecentMessages(projectId, limit)
            .map { ChatMessage.fromEntity(it) }
            .reversed() // Return in chronological order
    }

    /**
     * Save a single message to the database
     */
    suspend fun saveMessage(message: ChatMessage) {
        chatMessageDao.insertMessage(message.toEntity())
    }

    /**
     * Save multiple messages to the database
     */
    suspend fun saveMessages(messages: List<ChatMessage>) {
        chatMessageDao.insertMessages(messages.map { it.toEntity() })
    }

    /**
     * Update an existing message (e.g., when streaming completes)
     */
    suspend fun updateMessage(message: ChatMessage) {
        chatMessageDao.updateMessage(message.toEntity())
    }

    /**
     * Update message status
     */
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
        chatMessageDao.updateMessageStatus(messageId, status.name)
    }

    /**
     * Delete a specific message
     */
    suspend fun deleteMessage(message: ChatMessage) {
        chatMessageDao.deleteMessage(message.toEntity())
    }

    /**
     * Clear all chat history for a project
     */
    suspend fun clearChatHistory(projectId: String) {
        chatMessageDao.deleteAllMessagesForProject(projectId)
    }

    /**
     * Export chat history to markdown format
     */
    suspend fun exportToMarkdown(projectId: String, projectName: String): Result<File> {
        return try {
            val messages = chatMessageDao.getRecentMessages(projectId, Int.MAX_VALUE)
                .map { ChatMessage.fromEntity(it) }
                .reversed()

            if (messages.isEmpty()) {
                return Result.failure(IllegalStateException("No messages to export"))
            }

            val markdown = buildMarkdownExport(projectName, messages)

            val exportDir = File(context.filesDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            )
            val fileName = "${projectName.replace(" ", "_")}_chat_$timestamp.md"
            val exportFile = File(exportDir, fileName)

            exportFile.writeText(markdown)
            Result.success(exportFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Build markdown content from messages
     */
    private fun buildMarkdownExport(projectName: String, messages: List<ChatMessage>): String {
        val sb = StringBuilder()

        // Header
        sb.appendLine("# Chat History: $projectName")
        sb.appendLine()
        sb.appendLine("Exported on: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // Messages
        for (message in messages) {
            val time = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(message.timestamp),
                ZoneId.systemDefault()
            ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val role = if (message.isUser) "**User**" else "**Assistant**"

            sb.appendLine("### $role")
            sb.appendLine("*$time*")
            sb.appendLine()
            sb.appendLine(message.content)
            sb.appendLine()

            // Include code changes if present
            message.codeChanges?.forEach { change ->
                sb.appendLine("**Code Change: ${change.filePath}** (${change.operation.name})")
                sb.appendLine()
                if (change.content != null) {
                    sb.appendLine("```")
                    sb.appendLine(change.content)
                    sb.appendLine("```")
                }
                sb.appendLine()
            }

            sb.appendLine("---")
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Get total message count for a project
     */
    suspend fun getMessageCount(projectId: String): Int {
        return chatMessageDao.getRecentMessages(projectId, Int.MAX_VALUE).size
    }

    /**
     * Get estimated token count for context window management
     * Uses a simple heuristic: ~4 characters per token
     */
    suspend fun getEstimatedTokenCount(projectId: String): Int {
        val messages = chatMessageDao.getRecentMessages(projectId, Int.MAX_VALUE)
        val totalChars = messages.sumOf { it.content.length }
        return totalChars / 4
    }

    /**
     * Get messages within token budget for context window
     */
    suspend fun getMessagesWithinTokenBudget(
        projectId: String,
        maxTokens: Int
    ): List<ChatMessage> {
        val allMessages = chatMessageDao.getRecentMessages(projectId, Int.MAX_VALUE)
            .map { ChatMessage.fromEntity(it) }
            .reversed()

        if (allMessages.isEmpty()) return emptyList()

        val result = mutableListOf<ChatMessage>()
        var currentTokens = 0

        // Always include the most recent messages first (in reverse chronological order)
        for (message in allMessages.reversed()) {
            val messageTokens = message.content.length / 4
            if (currentTokens + messageTokens <= maxTokens) {
                result.add(0, message) // Add at beginning to maintain order
                currentTokens += messageTokens
            } else {
                break
            }
        }

        return result
    }
}
