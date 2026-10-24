package com.oro.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oro.app.data.local.entity.ChatMessageEntity
import com.oro.app.data.local.entity.MessageEnds
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE projectId = :projectId ORDER BY timestamp ASC")
    fun getMessagesByProjectId(projectId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySessionId(sessionId: String): Flow<List<ChatMessageEntity>>

    /** One-shot read of a whole session, oldest first: export and delete-undo snapshots. */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getAllMessagesForSession(sessionId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE projectId = :projectId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(projectId: String, limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesForSession(sessionId: String, limit: Int): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteAllMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE projectId = :projectId")
    suspend fun deleteAllMessagesForProject(projectId: String)

    @Query("UPDATE chat_messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun countMessagesForSession(sessionId: String): Int

    /** Total characters of a session's messages, computed in SQL (no row is read). */
    @Query("SELECT COALESCE(SUM(length(content)), 0) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun totalContentLength(sessionId: String): Long

    /**
     * Ids of rows whose content is longer than [limit] characters. Only ids
     * come back, so it works even when a row is too big for a cursor window;
     * SQLite still reads every row's text to measure it, so this is a one-time
     * scan (see ChatRepository.repairOversizedMessages), not a per-open one.
     */
    @Query("SELECT id FROM chat_messages WHERE length(content) > :limit")
    suspend fun findOversizedIds(limit: Int): List<String>

    /**
     * The first [head] characters and the last ones ([negativeTail] is minus
     * the tail length, SQLite's way of counting from the end) of one row. Both
     * ends fit a cursor window even when the whole row does not.
     */
    @Query("SELECT substr(content, 1, :head) AS head, substr(content, :negativeTail) AS tail FROM chat_messages WHERE id = :id")
    suspend fun readEnds(id: String, head: Int, negativeTail: Int): MessageEnds?

    /** Replaces one row's content in a single statement (a repair is never half-written). */
    @Query("UPDATE chat_messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)
}
