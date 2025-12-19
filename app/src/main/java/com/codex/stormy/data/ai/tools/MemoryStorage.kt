package com.codex.stormy.data.ai.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Memory entry for a project
 */
@Serializable
data class MemoryEntry(
    val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Project memories container
 */
@Serializable
data class ProjectMemories(
    val projectId: String,
    val entries: MutableMap<String, MemoryEntry> = mutableMapOf()
)

/**
 * Persistent storage for agent memories
 * Stores learnings and context about projects that persist across sessions
 */
class MemoryStorage(private val context: Context) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val mutex = Mutex()
    private val memoriesDir by lazy {
        File(context.filesDir, "agent_memories").apply { mkdirs() }
    }

    /**
     * Save a memory for a project
     */
    suspend fun save(projectId: String, key: String, value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            memories.entries[key] = MemoryEntry(key, value)
            saveMemories(projectId, memories)
        }
    }

    /**
     * Recall a specific memory
     */
    suspend fun recall(projectId: String, key: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            memories.entries[key]?.value
        }
    }

    /**
     * List all memories for a project
     */
    suspend fun list(projectId: String): Map<String, String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            memories.entries.mapValues { it.value.value }
        }
    }

    /**
     * Delete a specific memory
     */
    suspend fun delete(projectId: String, key: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            val removed = memories.entries.remove(key) != null
            if (removed) {
                saveMemories(projectId, memories)
            }
            removed
        }
    }

    /**
     * Clear all memories for a project
     */
    suspend fun clearProject(projectId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            getMemoryFile(projectId).delete()
        }
    }

    /**
     * Get all memories as context string for the AI
     */
    suspend fun getContextString(projectId: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            if (memories.entries.isEmpty()) {
                return@withContext ""
            }

            buildString {
                appendLine("\n## Project Memories")
                appendLine("Previously learned information about this project:")
                memories.entries.forEach { (key, entry) ->
                    appendLine("- **$key**: ${entry.value}")
                }
            }
        }
    }

    private fun getMemoryFile(projectId: String): File {
        return File(memoriesDir, "${projectId}.json")
    }

    private fun loadMemories(projectId: String): ProjectMemories {
        val file = getMemoryFile(projectId)
        return if (file.exists()) {
            try {
                json.decodeFromString(file.readText())
            } catch (e: Exception) {
                ProjectMemories(projectId)
            }
        } else {
            ProjectMemories(projectId)
        }
    }

    private fun saveMemories(projectId: String, memories: ProjectMemories) {
        val file = getMemoryFile(projectId)
        file.writeText(json.encodeToString(memories))
    }
}
