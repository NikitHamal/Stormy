package com.oro.app.data.repository

import android.content.Context
import com.oro.app.data.local.dao.ChatMessageDao
import com.oro.app.data.local.dao.ChatSessionDao
import com.oro.app.data.local.entity.ChatSessionEntity
import com.oro.app.data.local.entity.MessageStatus
import com.oro.app.data.local.entity.ProjectSessionStats
import com.oro.app.domain.model.ChatMessage
import com.oro.app.domain.model.ChatSession
import com.oro.app.domain.model.DeletedSessionSnapshot
import com.oro.app.domain.model.SessionTitles
import com.oro.app.data.ai.text.TranscriptCompactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Repository for chat sessions and their messages.
 *
 * A project holds many sessions; every message belongs to exactly one. All
 * conversation reads are session-scoped -- the agent's history, the export
 * and "clear" each act on one thread, never on the whole project.
 */
class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val chatSessionDao: ChatSessionDao,
    private val context: Context
) {
    // ---- Sessions ---------------------------------------------------------

    /** The project's sessions, pinned first, then most recently active. */
    fun observeSessions(projectId: String): Flow<List<ChatSession>> =
        chatSessionDao.observeSessionsWithStats(projectId)
            .map { rows -> rows.map { ChatSession.fromEntity(it) } }

    /** Session count and last activity per project, keyed by project id. */
    fun observeProjectSessionStats(): Flow<Map<String, ProjectSessionStats>> =
        chatSessionDao.observeProjectSessionStats()
            .map { rows -> rows.associateBy { it.projectId } }

    /** The session to reopen for a project, or null if it has none yet. */
    suspend fun getLastOpenedSessionId(projectId: String): String? =
        chatSessionDao.getLastOpenedSession(projectId)?.id

    suspend fun sessionExists(sessionId: String): Boolean =
        chatSessionDao.getSession(sessionId) != null

    /**
     * Create a session titled from its first message. A no-op if [sessionId]
     * already exists, so a retried send cannot wipe a session.
     */
    suspend fun createSession(
        sessionId: String,
        projectId: String,
        firstMessage: String?,
        now: Long = System.currentTimeMillis()
    ) {
        chatSessionDao.insertSession(
            ChatSessionEntity(
                id = sessionId,
                projectId = projectId,
                title = SessionTitles.fromFirstMessage(firstMessage),
                createdAt = now,
                updatedAt = now,
                lastOpenedAt = now,
                isPinned = false
            )
        )
    }

    /** Returns false when [title] is blank and nothing was changed. */
    suspend fun renameSession(sessionId: String, title: String): Boolean {
        val clean = SessionTitles.sanitizeUserTitle(title) ?: return false
        chatSessionDao.updateTitle(sessionId, clean)
        return true
    }

    suspend fun setSessionPinned(sessionId: String, pinned: Boolean) {
        chatSessionDao.setPinned(sessionId, pinned)
    }

    suspend fun markSessionOpened(sessionId: String, now: Long = System.currentTimeMillis()) {
        chatSessionDao.markOpened(sessionId, now)
    }

    /**
     * Delete a session and its messages, returning what is needed to undo
     * it, or null if it no longer existed.
     */
    suspend fun deleteSession(sessionId: String): DeletedSessionSnapshot? {
        val session = chatSessionDao.getSession(sessionId) ?: return null
        val messages = readSafely { chatMessageDao.getAllMessagesForSession(sessionId) }
        chatSessionDao.deleteSession(sessionId) // cascades to its messages
        return DeletedSessionSnapshot(session, messages)
    }

    /** Put a deleted session back exactly as it was. */
    suspend fun restoreSession(snapshot: DeletedSessionSnapshot) {
        chatSessionDao.insertSession(snapshot.session)
        if (snapshot.messages.isNotEmpty()) {
            chatMessageDao.insertMessages(snapshot.messages)
        }
    }

    // ---- Messages ---------------------------------------------------------

    /**
     * All messages of a session, oldest first, as a live Flow.
     *
     * Never fails on a row too big for a cursor window: stored rows are
     * repaired once per process before the first read, and a read that still
     * hits one repairs and re-subscribes. Mapping runs off the main thread --
     * a long chat is megabytes of text, and the collector is the UI.
     */
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> =
        flow {
            repairOversizedMessages()
            emitAll(chatMessageDao.getMessagesBySessionId(sessionId))
        }
            .retryWhen { cause, attempt ->
                if (attempt < 2 && ChatRowRepair.isOversizedRow(cause)) {
                    repairOversizedMessages(force = true)
                    true
                } else {
                    false
                }
            }
            .map { entities -> entities.map { ChatMessage.fromEntity(it) } }
            .flowOn(Dispatchers.Default)

    /**
     * Get recent messages of a session (for context window management)
     */
    suspend fun getRecentMessages(sessionId: String, limit: Int): List<ChatMessage> {
        return readSafely { chatMessageDao.getRecentMessagesForSession(sessionId, limit) }
            .map { ChatMessage.fromEntity(it) }
            .reversed() // Return in chronological order
    }

    private val repairLock = Mutex()
    @Volatile private var repairedOnce = false

    /**
     * Shrinks every stored message too big to read back (see [ChatRowRepair]),
     * then tidies each repaired row with [TranscriptCompactor] so it still
     * renders as a transcript. Runs once per process unless [force]d: the scan
     * is one `length()` pass in SQL, and reads no message content.
     *
     * @return how many messages were repaired.
     */
    suspend fun repairOversizedMessages(force: Boolean = false): Int = repairLock.withLock {
        if (repairedOnce && !force) return@withLock 0
        val repaired = withContext(Dispatchers.IO) {
            runCatching {
                val ids = chatMessageDao.findOversizedIds(ChatRowRepair.LIMIT_CHARS)
                if (ids.isEmpty()) return@runCatching 0
                chatMessageDao.shrinkOversized(
                    limit = ChatRowRepair.LIMIT_CHARS,
                    head = ChatRowRepair.HEAD_CHARS,
                    negativeTail = -ChatRowRepair.TAIL_CHARS,
                    marker = ChatRowRepair.MARKER
                )
                for (id in ids) {
                    val row = runCatching { chatMessageDao.getMessageById(id) }.getOrNull() ?: continue
                    val tidy = TranscriptCompactor.compact(row.content)
                    if (tidy != row.content) chatMessageDao.updateMessage(row.copy(content = tidy))
                }
                ids.size
            }.getOrDefault(0)
        }
        repairedOnce = true
        repaired
    }

    /** Runs a read, repairing oversized rows first (once) and again if the read still hits one. */
    private suspend fun <T> readSafely(read: suspend () -> T): T {
        repairOversizedMessages()
        return try {
            read()
        } catch (e: Exception) {
            if (!ChatRowRepair.isOversizedRow(e)) throw e
            repairOversizedMessages(force = true)
            read()
        }
    }

    /**
     * Save a message and mark its session as active now.
     */
    suspend fun saveMessage(message: ChatMessage) {
        requireSession(message)
        chatMessageDao.insertMessage(message.toEntity())
        chatSessionDao.touch(message.sessionId, System.currentTimeMillis())
    }

    /**
     * Save multiple messages to the database
     */
    suspend fun saveMessages(messages: List<ChatMessage>) {
        messages.forEach(::requireSession)
        chatMessageDao.insertMessages(messages.map { it.toEntity() })
        val now = System.currentTimeMillis()
        messages.map { it.sessionId }.distinct().forEach { chatSessionDao.touch(it, now) }
    }

    /**
     * Update an existing message (e.g., when streaming completes)
     */
    suspend fun updateMessage(message: ChatMessage) {
        requireSession(message)
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
     * Clear the messages of one session. The session itself stays, with its
     * title, so "clear" does not also make the thread vanish from the list.
     */
    suspend fun clearChatHistory(sessionId: String) {
        chatMessageDao.deleteAllMessagesForSession(sessionId)
    }

    /**
     * Export one session to markdown format
     */
    suspend fun exportToMarkdown(
        sessionId: String,
        projectName: String,
        sessionTitle: String? = null
    ): Result<File> {
        return try {
            val messages = readSafely { chatMessageDao.getAllMessagesForSession(sessionId) }
                .map { ChatMessage.fromEntity(it) }

            if (messages.isEmpty()) {
                return Result.failure(IllegalStateException("No messages to export"))
            }

            val heading = if (sessionTitle.isNullOrBlank()) projectName else "$projectName — $sessionTitle"
            val markdown = buildMarkdownExport(heading, messages)

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
     * A message with no session would fail the foreign key with an opaque
     * SQLite error; this names the actual mistake.
     */
    private fun requireSession(message: ChatMessage) {
        require(message.sessionId.isNotEmpty()) {
            "ChatMessage ${message.id} has no sessionId; create it with the current session's id"
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
     * Get total message count for a session
     */
    suspend fun getMessageCount(sessionId: String): Int {
        return chatMessageDao.countMessagesForSession(sessionId)
    }

    /**
     * Get estimated token count for context window management
     * Uses a simple heuristic: ~4 characters per token
     */
    suspend fun getEstimatedTokenCount(sessionId: String): Int {
        return (chatMessageDao.totalContentLength(sessionId) / 4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Get messages within token budget for context window
     */
    suspend fun getMessagesWithinTokenBudget(
        sessionId: String,
        maxTokens: Int
    ): List<ChatMessage> {
        val allMessages = readSafely { chatMessageDao.getAllMessagesForSession(sessionId) }
            .map { ChatMessage.fromEntity(it) }

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
