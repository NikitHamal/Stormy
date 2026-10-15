package com.codex.stormy.data.ai.memory

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
 * Memory categories for semantic organization
 * Each category has specific learning triggers and context relevance
 */
enum class MemoryCategory(
    val displayName: String,
    val description: String,
    val priority: Int
) {
    // High priority - always included in context
    PROJECT_STRUCTURE("Project Structure", "File organization, folder patterns, entry points", 100),
    CODING_PATTERNS("Coding Patterns", "Code style, naming conventions, formatting preferences", 95),
    FRAMEWORK_CONFIG("Framework Configuration", "Framework settings, build config, dependencies", 90),

    // Medium priority - included when relevant
    USER_PREFERENCES("User Preferences", "User-stated preferences and corrections", 80),
    COMPONENT_KNOWLEDGE("Component Knowledge", "Understanding of specific components and their purpose", 75),
    STYLING_PATTERNS("Styling Patterns", "CSS/styling conventions, color schemes, typography", 70),

    // Lower priority - included when context allows
    ERROR_SOLUTIONS("Error Solutions", "Previously resolved errors and their fixes", 60),
    TASK_HISTORY("Task History", "Summary of completed tasks and changes made", 50),
    GENERAL_NOTES("General Notes", "Miscellaneous learnings about the project", 40)
}

/**
 * Importance level for memories - affects retention and context inclusion
 */
enum class MemoryImportance {
    CRITICAL,   // Always include, never auto-delete
    HIGH,       // Include when possible, long retention
    MEDIUM,     // Include based on relevance, moderate retention
    LOW         // Include only when directly relevant, may be pruned
}

/**
 * A semantic memory entry with rich metadata
 */
@Serializable
data class SemanticMemory(
    val id: String,
    val category: MemoryCategory,
    val key: String,
    val value: String,
    val importance: MemoryImportance = MemoryImportance.MEDIUM,
    val confidence: Float = 1.0f,
    val accessCount: Int = 0,
    val lastAccessedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: MemorySource = MemorySource.AGENT,
    val tags: List<String> = emptyList(),
    val relatedFiles: List<String> = emptyList(),
    val expiresAt: Long? = null
) {
    /**
     * Calculate relevance score for context inclusion
     */
    fun calculateRelevanceScore(
        queryTags: List<String> = emptyList(),
        queryFiles: List<String> = emptyList()
    ): Float {
        var score = category.priority / 100f * confidence

        // Boost for recent access
        val daysSinceAccess = (System.currentTimeMillis() - lastAccessedAt) / (24 * 60 * 60 * 1000f)
        val recencyBoost = (1f - (daysSinceAccess / 30f).coerceAtMost(1f)) * 0.2f
        score += recencyBoost

        // Boost for frequent access
        val accessBoost = (accessCount.coerceAtMost(20) / 20f) * 0.15f
        score += accessBoost

        // Boost for importance
        score += when (importance) {
            MemoryImportance.CRITICAL -> 0.3f
            MemoryImportance.HIGH -> 0.2f
            MemoryImportance.MEDIUM -> 0.1f
            MemoryImportance.LOW -> 0f
        }

        // Boost for tag matches
        val tagMatchCount = tags.count { it in queryTags }
        score += (tagMatchCount * 0.1f).coerceAtMost(0.3f)

        // Boost for file matches
        val fileMatchCount = relatedFiles.count { file ->
            queryFiles.any { it.contains(file) || file.contains(it) }
        }
        score += (fileMatchCount * 0.15f).coerceAtMost(0.3f)

        return score.coerceIn(0f, 2f)
    }
}

/**
 * Source of memory creation
 */
@Serializable
enum class MemorySource {
    AGENT,              // Created by Stormy agent during task execution
    USER_EXPLICIT,      // User explicitly stated something
    USER_CORRECTION,    // User corrected agent output
    AUTO_LEARNED,       // Automatically learned from patterns
    IMPORTED            // Imported from external source
}

/**
 * Container for project-specific semantic memories
 */
@Serializable
data class ProjectSemanticMemories(
    val projectId: String,
    val memories: MutableMap<String, SemanticMemory> = mutableMapOf(),
    val version: Int = 1,
    val lastOptimizedAt: Long = 0L
)

/**
 * Advanced semantic memory system for Stormy agent
 *
 * Features:
 * - Categorized memories with semantic organization
 * - Importance-based retention and context inclusion
 * - Automatic learning from interactions
 * - Smart context building based on relevance
 * - Memory consolidation and pruning
 * - Cross-project learning support
 */
class SemanticMemorySystem(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private val memoriesDir by lazy {
        File(context.filesDir, "semantic_memories").apply { mkdirs() }
    }

    private val globalMemoriesDir by lazy {
        File(context.filesDir, "global_memories").apply { mkdirs() }
    }

    // In-memory cache for frequently accessed memories (LRU-style)
    private val memoryCache = object : LinkedHashMap<String, CachedMemories>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedMemories>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    private data class CachedMemories(
        val memories: ProjectSemanticMemories,
        var lastModified: Long,
        var isDirty: Boolean = false
    )

    companion object {
        private const val MAX_MEMORIES_PER_PROJECT = 500
        private const val MAX_CONTEXT_TOKENS = 2000
        private const val CONSOLIDATION_THRESHOLD = 100
        private const val PRUNE_AFTER_DAYS = 90
        private const val MAX_CACHE_SIZE = 3
        private const val CACHE_FLUSH_INTERVAL_MS = 5000L
    }

    // ==================== Core Memory Operations ====================

    /**
     * Save a semantic memory
     */
    suspend fun saveMemory(
        projectId: String,
        category: MemoryCategory,
        key: String,
        value: String,
        importance: MemoryImportance = MemoryImportance.MEDIUM,
        source: MemorySource = MemorySource.AGENT,
        tags: List<String> = emptyList(),
        relatedFiles: List<String> = emptyList()
    ): SemanticMemory = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            val memoryId = "${category.name}:$key"

            val existing = memories.memories[memoryId]
            val memory = if (existing != null) {
                // Update existing memory
                existing.copy(
                    value = value,
                    confidence = (existing.confidence + 0.1f).coerceAtMost(1.0f),
                    updatedAt = System.currentTimeMillis(),
                    tags = (existing.tags + tags).distinct(),
                    relatedFiles = (existing.relatedFiles + relatedFiles).distinct()
                )
            } else {
                // Create new memory
                SemanticMemory(
                    id = memoryId,
                    category = category,
                    key = key,
                    value = value,
                    importance = importance,
                    source = source,
                    tags = tags,
                    relatedFiles = relatedFiles
                )
            }

            memories.memories[memoryId] = memory
            saveMemories(projectId, memories)

            // Check if consolidation is needed
            if (memories.memories.size > CONSOLIDATION_THRESHOLD) {
                consolidateMemories(projectId, memories)
            }

            memory
        }
    }

    /**
     * Recall a specific memory and update access statistics
     */
    suspend fun recallMemory(
        projectId: String,
        category: MemoryCategory,
        key: String
    ): SemanticMemory? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            val memoryId = "${category.name}:$key"

            memories.memories[memoryId]?.let { memory ->
                // Update access statistics
                val updated = memory.copy(
                    accessCount = memory.accessCount + 1,
                    lastAccessedAt = System.currentTimeMillis()
                )
                memories.memories[memoryId] = updated
                saveMemories(projectId, memories)
                updated
            }
        }
    }

    /**
     * Get all memories in a category
     */
    suspend fun getMemoriesByCategory(
        projectId: String,
        category: MemoryCategory
    ): List<SemanticMemory> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            memories.memories.values.filter { it.category == category }
        }
    }

    /**
     * Search memories by tags
     */
    suspend fun searchByTags(
        projectId: String,
        tags: List<String>
    ): List<SemanticMemory> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            memories.memories.values.filter { memory ->
                memory.tags.any { it in tags }
            }.sortedByDescending { it.calculateRelevanceScore(queryTags = tags) }
        }
    }

    /**
     * Search memories related to specific files
     */
    suspend fun searchByFiles(
        projectId: String,
        files: List<String>
    ): List<SemanticMemory> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            memories.memories.values.filter { memory ->
                memory.relatedFiles.any { relatedFile ->
                    files.any { it.contains(relatedFile) || relatedFile.contains(it) }
                }
            }.sortedByDescending { it.calculateRelevanceScore(queryFiles = files) }
        }
    }

    /**
     * Delete a specific memory
     */
    suspend fun deleteMemory(
        projectId: String,
        category: MemoryCategory,
        key: String
    ): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            val memoryId = "${category.name}:$key"
            val removed = memories.memories.remove(memoryId) != null
            if (removed) {
                saveMemories(projectId, memories)
            }
            removed
        }
    }

    // ==================== Context Building ====================

    /**
     * Build optimized context string for AI conversations
     * Intelligently selects most relevant memories within token budget
     */
    suspend fun buildContextString(
        projectId: String,
        currentFiles: List<String> = emptyList(),
        queryTags: List<String> = emptyList(),
        maxTokens: Int = MAX_CONTEXT_TOKENS,
        includeCategories: Set<MemoryCategory>? = null
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)

            if (memories.memories.isEmpty()) {
                return@withContext ""
            }

            // Score and sort all memories
            val scoredMemories = memories.memories.values
                .filter { includeCategories == null || it.category in includeCategories }
                .filter { it.expiresAt == null || it.expiresAt > System.currentTimeMillis() }
                .map { it to it.calculateRelevanceScore(queryTags, currentFiles) }
                .sortedByDescending { (_, score) -> score }

            // Build context within token budget
            val contextBuilder = StringBuilder()
            var estimatedTokens = 0
            val tokensPerChar = 0.25f // Rough estimation

            // Group by category for better organization
            val selectedByCategory = mutableMapOf<MemoryCategory, MutableList<SemanticMemory>>()

            for ((memory, _) in scoredMemories) {
                val memoryText = "- **${memory.key}**: ${memory.value}"
                val memoryTokens = (memoryText.length * tokensPerChar).toInt()

                if (estimatedTokens + memoryTokens > maxTokens) break

                selectedByCategory.getOrPut(memory.category) { mutableListOf() }.add(memory)
                estimatedTokens += memoryTokens
            }

            // Format output
            if (selectedByCategory.isEmpty()) {
                return@withContext ""
            }

            contextBuilder.appendLine("\n## Project Knowledge (Learned from past interactions)")

            for ((category, categoryMemories) in selectedByCategory.entries.sortedByDescending { it.key.priority }) {
                contextBuilder.appendLine("\n### ${category.displayName}")
                for (memory in categoryMemories) {
                    contextBuilder.appendLine("- **${memory.key}**: ${memory.value}")
                }
            }

            contextBuilder.toString()
        }
    }

    /**
     * Get a quick summary of project knowledge
     */
    suspend fun getKnowledgeSummary(projectId: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)

            if (memories.memories.isEmpty()) {
                return@withContext "No learned knowledge about this project yet."
            }

            val categoryCounts = memories.memories.values.groupBy { it.category }
                .mapValues { it.value.size }

            buildString {
                appendLine("Project Knowledge Summary:")
                for ((category, count) in categoryCounts.entries.sortedByDescending { it.key.priority }) {
                    appendLine("  - ${category.displayName}: $count items")
                }
            }
        }
    }

    // ==================== Auto-Learning ====================

    /**
     * Learn from user message to automatically extract and store relevant information
     */
    suspend fun learnFromUserMessage(
        projectId: String,
        message: String,
        currentFile: String? = null
    ) = withContext(Dispatchers.IO) {
        // Extract framework preferences
        extractFrameworkPreferences(projectId, message)

        // Extract styling preferences
        extractStylingPreferences(projectId, message)

        // Extract explicit preferences ("I prefer", "I want", "always use")
        extractExplicitPreferences(projectId, message)

        // Extract error context
        extractErrorContext(projectId, message, currentFile)
    }

    /**
     * Learn from AI-generated code to understand patterns
     */
    suspend fun learnFromGeneratedCode(
        projectId: String,
        filePath: String,
        code: String
    ) = withContext(Dispatchers.IO) {
        // Detect naming conventions
        detectNamingConventions(projectId, code, filePath)

        // Detect framework usage
        detectFrameworkUsage(projectId, code, filePath)

        // Detect styling patterns
        detectStylingPatterns(projectId, code, filePath)
    }

    /**
     * Learn from user corrections (when user edits AI output)
     */
    suspend fun learnFromCorrection(
        projectId: String,
        original: String,
        corrected: String,
        filePath: String? = null
    ) = withContext(Dispatchers.IO) {
        // Detect naming convention corrections
        val namingChange = detectNamingConventionChange(original, corrected)
        if (namingChange != null) {
            saveMemory(
                projectId = projectId,
                category = MemoryCategory.CODING_PATTERNS,
                key = "naming_convention",
                value = namingChange,
                importance = MemoryImportance.HIGH,
                source = MemorySource.USER_CORRECTION,
                tags = listOf("naming", "style", "correction")
            )
        }

        // Detect indentation preference changes
        val indentChange = detectIndentationChange(original, corrected)
        if (indentChange != null) {
            saveMemory(
                projectId = projectId,
                category = MemoryCategory.CODING_PATTERNS,
                key = "indentation",
                value = indentChange,
                importance = MemoryImportance.HIGH,
                source = MemorySource.USER_CORRECTION,
                tags = listOf("formatting", "style", "correction")
            )
        }

        // Store the correction pattern for future reference
        val correctionKey = "correction_${System.currentTimeMillis()}"
        saveMemory(
            projectId = projectId,
            category = MemoryCategory.USER_PREFERENCES,
            key = correctionKey,
            value = "User changed '${original.take(50)}...' to '${corrected.take(50)}...'",
            importance = MemoryImportance.MEDIUM,
            source = MemorySource.USER_CORRECTION,
            relatedFiles = filePath?.let { listOf(it) } ?: emptyList()
        )
    }

    // ==================== Private Helper Methods ====================

    private fun extractFrameworkPreferences(projectId: String, message: String) {
        val frameworkKeywords = mapOf(
            "react" to "React",
            "vue" to "Vue.js",
            "svelte" to "Svelte",
            "next" to "Next.js",
            "tailwind" to "Tailwind CSS",
            "bootstrap" to "Bootstrap",
            "typescript" to "TypeScript",
            "express" to "Express.js"
        )

        val lowerMessage = message.lowercase()
        for ((keyword, framework) in frameworkKeywords) {
            if (keyword in lowerMessage) {
                kotlinx.coroutines.runBlocking {
                    saveMemory(
                        projectId = projectId,
                        category = MemoryCategory.FRAMEWORK_CONFIG,
                        key = "framework_preference",
                        value = "User mentioned $framework - may prefer this framework",
                        importance = MemoryImportance.MEDIUM,
                        source = MemorySource.AUTO_LEARNED,
                        tags = listOf("framework", keyword)
                    )
                }
            }
        }
    }

    private fun extractStylingPreferences(projectId: String, message: String) {
        val colorPatterns = Regex("#[0-9A-Fa-f]{3,8}|rgb\\([^)]+\\)|hsl\\([^)]+\\)")
        val colors = colorPatterns.findAll(message).map { it.value }.toList()

        if (colors.isNotEmpty()) {
            kotlinx.coroutines.runBlocking {
                saveMemory(
                    projectId = projectId,
                    category = MemoryCategory.STYLING_PATTERNS,
                    key = "mentioned_colors",
                    value = "User mentioned colors: ${colors.joinToString(", ")}",
                    importance = MemoryImportance.LOW,
                    source = MemorySource.AUTO_LEARNED,
                    tags = listOf("colors", "styling")
                )
            }
        }
    }

    private fun extractExplicitPreferences(projectId: String, message: String) {
        val preferencePatterns = listOf(
            Regex("(?:i prefer|always use|i like|i want|please use)\\s+([^.!?]+)", RegexOption.IGNORE_CASE),
            Regex("(?:don't|do not|never)\\s+(?:use|add|include)\\s+([^.!?]+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in preferencePatterns) {
            val match = pattern.find(message)
            if (match != null) {
                val preference = match.groupValues[1].trim()
                kotlinx.coroutines.runBlocking {
                    saveMemory(
                        projectId = projectId,
                        category = MemoryCategory.USER_PREFERENCES,
                        key = "stated_preference_${System.currentTimeMillis() % 10000}",
                        value = "User preference: ${match.value}",
                        importance = MemoryImportance.HIGH,
                        source = MemorySource.USER_EXPLICIT,
                        tags = listOf("preference", "explicit")
                    )
                }
            }
        }
    }

    private fun extractErrorContext(projectId: String, message: String, currentFile: String?) {
        val errorPatterns = listOf(
            "error", "bug", "issue", "problem", "not working", "broken", "crash", "failed"
        )

        val lowerMessage = message.lowercase()
        if (errorPatterns.any { it in lowerMessage }) {
            kotlinx.coroutines.runBlocking {
                saveMemory(
                    projectId = projectId,
                    category = MemoryCategory.ERROR_SOLUTIONS,
                    key = "reported_issue_${System.currentTimeMillis() % 10000}",
                    value = "User reported issue: ${message.take(200)}",
                    importance = MemoryImportance.MEDIUM,
                    source = MemorySource.AUTO_LEARNED,
                    tags = listOf("error", "issue"),
                    relatedFiles = currentFile?.let { listOf(it) } ?: emptyList()
                )
            }
        }
    }

    private fun detectNamingConventions(projectId: String, code: String, filePath: String) {
        val camelCasePattern = Regex("[a-z][a-zA-Z0-9]*[A-Z][a-zA-Z0-9]*")
        val snakeCasePattern = Regex("[a-z][a-z0-9]*_[a-z][a-z0-9_]*")
        val kebabCasePattern = Regex("[a-z][a-z0-9]*-[a-z][a-z0-9-]*")

        val camelCount = camelCasePattern.findAll(code).count()
        val snakeCount = snakeCasePattern.findAll(code).count()
        val kebabCount = kebabCasePattern.findAll(code).count()

        val dominant = when {
            camelCount > snakeCount && camelCount > kebabCount -> "camelCase"
            snakeCount > camelCount && snakeCount > kebabCount -> "snake_case"
            kebabCount > camelCount && kebabCount > snakeCount -> "kebab-case"
            else -> null
        }

        if (dominant != null) {
            kotlinx.coroutines.runBlocking {
                saveMemory(
                    projectId = projectId,
                    category = MemoryCategory.CODING_PATTERNS,
                    key = "naming_convention_${filePath.substringAfterLast('.').take(5)}",
                    value = "Detected $dominant naming convention in ${filePath.substringAfterLast('/')}",
                    importance = MemoryImportance.MEDIUM,
                    source = MemorySource.AUTO_LEARNED,
                    tags = listOf("naming", dominant),
                    relatedFiles = listOf(filePath)
                )
            }
        }
    }

    private fun detectFrameworkUsage(projectId: String, code: String, filePath: String) {
        val frameworkSignatures = mapOf(
            "import React" to "React",
            "from 'react'" to "React",
            "import Vue" to "Vue.js",
            "from 'vue'" to "Vue.js",
            "<script setup>" to "Vue.js (Composition API)",
            "import { useState" to "React Hooks",
            "export default function" to "React functional component",
            "tailwind" to "Tailwind CSS",
            "@apply" to "Tailwind CSS"
        )

        for ((signature, framework) in frameworkSignatures) {
            if (signature in code) {
                kotlinx.coroutines.runBlocking {
                    saveMemory(
                        projectId = projectId,
                        category = MemoryCategory.FRAMEWORK_CONFIG,
                        key = "detected_framework_${framework.lowercase().replace(" ", "_")}",
                        value = "$framework detected in project (found in ${filePath.substringAfterLast('/')})",
                        importance = MemoryImportance.HIGH,
                        source = MemorySource.AUTO_LEARNED,
                        tags = listOf("framework", framework.lowercase()),
                        relatedFiles = listOf(filePath)
                    )
                }
                break
            }
        }
    }

    private fun detectStylingPatterns(projectId: String, code: String, filePath: String) {
        if (filePath.endsWith(".css") || filePath.endsWith(".scss") || filePath.endsWith(".less")) {
            // Detect CSS variable usage
            if ("--" in code && "var(" in code) {
                kotlinx.coroutines.runBlocking {
                    saveMemory(
                        projectId = projectId,
                        category = MemoryCategory.STYLING_PATTERNS,
                        key = "css_variables",
                        value = "Project uses CSS custom properties (variables)",
                        importance = MemoryImportance.MEDIUM,
                        source = MemorySource.AUTO_LEARNED,
                        tags = listOf("css", "variables", "styling"),
                        relatedFiles = listOf(filePath)
                    )
                }
            }

            // Detect flexbox or grid usage
            if ("display: flex" in code || "display:flex" in code) {
                kotlinx.coroutines.runBlocking {
                    saveMemory(
                        projectId = projectId,
                        category = MemoryCategory.STYLING_PATTERNS,
                        key = "layout_method",
                        value = "Project uses Flexbox for layouts",
                        importance = MemoryImportance.MEDIUM,
                        source = MemorySource.AUTO_LEARNED,
                        tags = listOf("flexbox", "layout", "css")
                    )
                }
            }

            if ("display: grid" in code || "display:grid" in code) {
                kotlinx.coroutines.runBlocking {
                    saveMemory(
                        projectId = projectId,
                        category = MemoryCategory.STYLING_PATTERNS,
                        key = "layout_method_grid",
                        value = "Project uses CSS Grid for layouts",
                        importance = MemoryImportance.MEDIUM,
                        source = MemorySource.AUTO_LEARNED,
                        tags = listOf("grid", "layout", "css")
                    )
                }
            }
        }
    }

    private fun detectNamingConventionChange(original: String, corrected: String): String? {
        val camelCasePattern = Regex("[a-z][a-zA-Z0-9]*[A-Z][a-zA-Z0-9]*")
        val snakeCasePattern = Regex("[a-z][a-z0-9]*_[a-z][a-z0-9_]*")

        val originalHasCamel = camelCasePattern.containsMatchIn(original)
        val correctedHasSnake = snakeCasePattern.containsMatchIn(corrected)

        val originalHasSnake = snakeCasePattern.containsMatchIn(original)
        val correctedHasCamel = camelCasePattern.containsMatchIn(corrected)

        return when {
            originalHasCamel && correctedHasSnake -> "User prefers snake_case over camelCase"
            originalHasSnake && correctedHasCamel -> "User prefers camelCase over snake_case"
            else -> null
        }
    }

    private fun detectIndentationChange(original: String, corrected: String): String? {
        val originalUsesSpaces = original.contains("    ") && !original.contains("\t")
        val correctedUsesTabs = corrected.contains("\t") && !corrected.contains("    ")

        val originalUsesTabs = original.contains("\t") && !original.contains("    ")
        val correctedUsesSpaces = corrected.contains("    ") && !corrected.contains("\t")

        return when {
            originalUsesSpaces && correctedUsesTabs -> "User prefers tabs for indentation"
            originalUsesTabs && correctedUsesSpaces -> "User prefers spaces for indentation"
            else -> null
        }
    }

    // ==================== Memory Management ====================

    /**
     * Consolidate memories to stay within limits
     */
    private suspend fun consolidateMemories(projectId: String, memories: ProjectSemanticMemories) {
        if (memories.memories.size <= MAX_MEMORIES_PER_PROJECT) return

        // Remove expired memories
        val now = System.currentTimeMillis()
        val expired = memories.memories.filter { (_, memory) ->
            memory.expiresAt != null && memory.expiresAt < now
        }
        expired.keys.forEach { memories.memories.remove(it) }

        // Remove old, low-importance memories if still over limit
        if (memories.memories.size > MAX_MEMORIES_PER_PROJECT) {
            val pruneThreshold = now - (PRUNE_AFTER_DAYS * 24 * 60 * 60 * 1000L)
            val toRemove = memories.memories.entries
                .filter { (_, memory) ->
                    memory.importance == MemoryImportance.LOW &&
                    memory.lastAccessedAt < pruneThreshold &&
                    memory.accessCount < 3
                }
                .take(memories.memories.size - MAX_MEMORIES_PER_PROJECT + 50)

            toRemove.forEach { memories.memories.remove(it.key) }
        }

        saveMemories(projectId, memories)
    }

    /**
     * Clear all memories for a project
     */
    suspend fun clearProjectMemories(projectId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            getMemoryFile(projectId).delete()
        }
    }

    /**
     * Export memories as JSON
     */
    suspend fun exportMemories(projectId: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            json.encodeToString(memories)
        }
    }

    /**
     * Get all memories for display in UI
     */
    suspend fun getAllMemories(projectId: String): List<SemanticMemory> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memories = loadMemories(projectId)
            memories.memories.values.sortedByDescending { it.category.priority }
        }
    }

    // ==================== Persistence ====================

    private fun getMemoryFile(projectId: String): File {
        return File(memoriesDir, "${projectId}_semantic.json")
    }

    /**
     * Load memories with caching support
     * Uses in-memory cache to reduce file I/O for frequently accessed projects
     */
    private fun loadMemories(projectId: String): ProjectSemanticMemories {
        // Check cache first
        val cached = memoryCache[projectId]
        if (cached != null) {
            val file = getMemoryFile(projectId)
            // Validate cache is still fresh (file hasn't been modified externally)
            if (!file.exists() || file.lastModified() <= cached.lastModified) {
                return cached.memories
            }
        }

        // Load from disk
        val file = getMemoryFile(projectId)
        val memories = if (file.exists()) {
            try {
                json.decodeFromString(file.readText())
            } catch (e: Exception) {
                ProjectSemanticMemories(projectId)
            }
        } else {
            ProjectSemanticMemories(projectId)
        }

        // Update cache
        memoryCache[projectId] = CachedMemories(
            memories = memories,
            lastModified = if (file.exists()) file.lastModified() else System.currentTimeMillis()
        )

        return memories
    }

    /**
     * Save memories with cache-through strategy
     * Updates both cache and disk for consistency
     */
    private fun saveMemories(projectId: String, memories: ProjectSemanticMemories) {
        val file = getMemoryFile(projectId)
        file.writeText(json.encodeToString(memories))

        // Update cache
        memoryCache[projectId] = CachedMemories(
            memories = memories,
            lastModified = file.lastModified()
        )
    }

    /**
     * Flush all cached memories to disk
     * Call this when the app is backgrounded or closing
     */
    fun flushCache() {
        memoryCache.forEach { (projectId, cached) ->
            if (cached.isDirty) {
                val file = getMemoryFile(projectId)
                file.writeText(json.encodeToString(cached.memories))
                cached.isDirty = false
                cached.lastModified = file.lastModified()
            }
        }
    }

    /**
     * Clear the in-memory cache
     */
    fun clearCache() {
        memoryCache.clear()
    }
}
