package com.oro.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oro.app.data.local.entity.ChatSessionEntity
import com.oro.app.data.local.entity.ChatSessionWithStats
import com.oro.app.data.local.entity.ProjectSessionStats
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    /**
     * Every session of a project with its message count and latest user
     * message, pinned first and then most recently active.
     *
     * The figures are correlated subqueries rather than stored counters: both
     * hit `index_chat_messages_sessionId`, so they cost a few index probes per
     * row, and a counter column would be one more thing that can drift out of
     * step with the messages it counts. Room re-runs this whenever either
     * table changes, so the list stays live while a run streams into it.
     */
    @Query(
        """
        SELECT s.*,
            (SELECT COUNT(*) FROM chat_messages m WHERE m.sessionId = s.id) AS messageCount,
            (SELECT substr(m.content, 1, 160) FROM chat_messages m
                WHERE m.sessionId = s.id AND m.role = 'USER'
                ORDER BY m.timestamp DESC LIMIT 1) AS lastUserMessage
        FROM chat_sessions s
        WHERE s.projectId = :projectId
        ORDER BY s.isPinned DESC, s.updatedAt DESC
        """
    )
    fun observeSessionsWithStats(projectId: String): Flow<List<ChatSessionWithStats>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    /** The session the user was last in, for restoring it when the project reopens. */
    @Query("SELECT * FROM chat_sessions WHERE projectId = :projectId ORDER BY lastOpenedAt DESC, updatedAt DESC LIMIT 1")
    suspend fun getLastOpenedSession(projectId: String): ChatSessionEntity?

    @Query("SELECT projectId, COUNT(*) AS sessionCount, MAX(updatedAt) AS lastActivity FROM chat_sessions GROUP BY projectId")
    fun observeProjectSessionStats(): Flow<List<ProjectSessionStats>>

    /** IGNORE, not REPLACE: REPLACE deletes the old row first, and the cascade would take its messages. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title WHERE id = :sessionId")
    suspend fun updateTitle(sessionId: String, title: String)

    @Query("UPDATE chat_sessions SET isPinned = :pinned WHERE id = :sessionId")
    suspend fun setPinned(sessionId: String, pinned: Boolean)

    /** Monotonic, so a late write of an older message cannot move a session backwards. */
    @Query("UPDATE chat_sessions SET updatedAt = MAX(updatedAt, :timestamp) WHERE id = :sessionId")
    suspend fun touch(sessionId: String, timestamp: Long)

    @Query("UPDATE chat_sessions SET lastOpenedAt = :timestamp WHERE id = :sessionId")
    suspend fun markOpened(sessionId: String, timestamp: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
}
