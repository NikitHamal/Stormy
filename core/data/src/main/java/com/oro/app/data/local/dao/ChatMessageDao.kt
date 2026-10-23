package com.oro.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oro.app.data.local.entity.ChatMessageEntity
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
     * Ids of rows whose content is longer than [limit] characters. Reads only
     * the ids, so it works even when a row is too big for a cursor window.
     */
    @Query("SELECT id FROM chat_messages WHERE length(content) > :limit")
    suspend fun findOversizedIds(limit: Int): List<String>

    /**
     * Shrinks every row longer than [limit] to its first [head] and last
     * characters ([negativeTail] is minus the tail length, SQLite's way of
     * counting from the end), with [marker] between. Done entirely in SQL, so
     * a row too big to read through a cursor never has to be read.
     */
    @Query(
        "UPDATE chat_messages SET content = substr(content, 1, :head) || :marker || substr(content, :negativeTail) " +
            "WHERE length(content) > :limit"
    )
    suspend fun shrinkOversized(limit: Int, head: Int, negativeTail: Int, marker: String): Int
}
