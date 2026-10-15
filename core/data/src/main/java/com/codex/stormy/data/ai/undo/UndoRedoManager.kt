package com.codex.stormy.data.ai.undo

import com.codex.stormy.data.ai.tools.FileChangeType
import com.codex.stormy.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Represents a single file change that can be undone/redone
 */
data class FileChange(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val changeType: FileChangeType,
    val oldContent: String?,
    val newContent: String?,
    val timestamp: Long = System.currentTimeMillis(),
    // For rename/move operations
    val oldPath: String? = null,
    val newPath: String? = null
)

/**
 * Represents a group of file changes that were made together (e.g., by a single AI action)
 */
data class ChangeGroup(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val changes: List<FileChange>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * UI state for undo/redo functionality
 */
data class UndoRedoState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoDescription: String? = null,
    val redoDescription: String? = null,
    val pendingChanges: Int = 0
)

/**
 * Manages undo/redo history for AI file changes.
 * Tracks changes made by the AI agent and allows reverting them.
 */
class UndoRedoManager(
    private val projectRepository: ProjectRepository
) {
    // Undo stack - changes that can be undone
    private val undoStack = mutableListOf<ChangeGroup>()

    // Redo stack - changes that can be redone
    private val redoStack = mutableListOf<ChangeGroup>()

    // Current change group being built (for batching multiple file changes)
    private var currentGroup: MutableList<FileChange>? = null
    private var currentGroupDescription: String = ""

    // State for UI binding
    private val _state = MutableStateFlow(UndoRedoState())
    val state: StateFlow<UndoRedoState> = _state.asStateFlow()

    // Maximum history size to prevent memory issues
    private val maxHistorySize = 50

    /**
     * Start a new change group. All subsequent file changes will be grouped together
     * until endChangeGroup() is called.
     */
    fun beginChangeGroup(description: String) {
        currentGroup = mutableListOf()
        currentGroupDescription = description
    }

    /**
     * End the current change group and add it to the undo stack.
     */
    fun endChangeGroup() {
        val changes = currentGroup
        if (changes != null && changes.isNotEmpty()) {
            val group = ChangeGroup(
                description = currentGroupDescription,
                changes = changes.toList()
            )
            addToUndoStack(group)
        }
        currentGroup = null
        currentGroupDescription = ""
        updateState()
    }

    /**
     * Record a file change. If a change group is active, the change is added to it.
     * Otherwise, a single-change group is created.
     */
    fun recordChange(
        path: String,
        changeType: FileChangeType,
        oldContent: String?,
        newContent: String?,
        oldPath: String? = null,
        newPath: String? = null
    ) {
        val change = FileChange(
            path = path,
            changeType = changeType,
            oldContent = oldContent,
            newContent = newContent,
            oldPath = oldPath,
            newPath = newPath
        )

        val activeGroup = currentGroup
        if (activeGroup != null) {
            // Add to current group
            activeGroup.add(change)
        } else {
            // Create single-change group
            val description = when (changeType) {
                FileChangeType.CREATED -> "Create $path"
                FileChangeType.MODIFIED -> "Modify $path"
                FileChangeType.DELETED -> "Delete $path"
                FileChangeType.RENAMED -> "Rename ${oldPath ?: path}"
                FileChangeType.COPIED -> "Copy to $path"
                FileChangeType.MOVED -> "Move ${oldPath ?: path}"
            }
            val group = ChangeGroup(
                description = description,
                changes = listOf(change)
            )
            addToUndoStack(group)
        }

        // Clear redo stack when new changes are made
        redoStack.clear()
        updateState()
    }

    /**
     * Undo the most recent change group.
     * Returns true if successful, false if nothing to undo.
     */
    suspend fun undo(projectId: String): Result<String> {
        if (undoStack.isEmpty()) {
            return Result.failure(IllegalStateException("Nothing to undo"))
        }

        val group = undoStack.removeLast()

        return try {
            // Apply inverse changes in reverse order
            for (change in group.changes.reversed()) {
                applyInverseChange(projectId, change)
            }

            // Add to redo stack
            redoStack.add(group)
            updateState()

            Result.success("Undone: ${group.description}")
        } catch (e: Exception) {
            // Re-add to undo stack on failure
            undoStack.add(group)
            updateState()
            Result.failure(e)
        }
    }

    /**
     * Redo the most recently undone change group.
     * Returns true if successful, false if nothing to redo.
     */
    suspend fun redo(projectId: String): Result<String> {
        if (redoStack.isEmpty()) {
            return Result.failure(IllegalStateException("Nothing to redo"))
        }

        val group = redoStack.removeLast()

        return try {
            // Re-apply changes in original order
            for (change in group.changes) {
                applyChange(projectId, change)
            }

            // Add back to undo stack
            undoStack.add(group)
            updateState()

            Result.success("Redone: ${group.description}")
        } catch (e: Exception) {
            // Re-add to redo stack on failure
            redoStack.add(group)
            updateState()
            Result.failure(e)
        }
    }

    /**
     * Apply a change (for redo)
     */
    private suspend fun applyChange(projectId: String, change: FileChange) {
        when (change.changeType) {
            FileChangeType.CREATED -> {
                change.newContent?.let { content ->
                    projectRepository.createFile(projectId, change.path, content)
                        .getOrThrow()
                }
            }
            FileChangeType.MODIFIED -> {
                change.newContent?.let { content ->
                    projectRepository.writeFile(projectId, change.path, content)
                        .getOrThrow()
                }
            }
            FileChangeType.DELETED -> {
                projectRepository.deleteFile(projectId, change.path)
                    .getOrThrow()
            }
            FileChangeType.RENAMED -> {
                val oldPath = change.oldPath ?: change.path
                val newPath = change.newPath ?: change.path
                projectRepository.renameFile(projectId, oldPath, newPath)
                    .getOrThrow()
            }
            FileChangeType.MOVED -> {
                val oldPath = change.oldPath ?: change.path
                val newPath = change.newPath ?: change.path
                projectRepository.moveFile(projectId, oldPath, newPath)
                    .getOrThrow()
            }
            FileChangeType.COPIED -> {
                // For copy, we just need to recreate the file at destination
                change.newContent?.let { content ->
                    projectRepository.writeFile(projectId, change.path, content)
                        .getOrThrow()
                }
            }
        }
    }

    /**
     * Apply the inverse of a change (for undo)
     */
    private suspend fun applyInverseChange(projectId: String, change: FileChange) {
        when (change.changeType) {
            FileChangeType.CREATED -> {
                // Inverse of create is delete
                projectRepository.deleteFile(projectId, change.path)
                    .getOrThrow()
            }
            FileChangeType.MODIFIED -> {
                // Inverse of modify is restore old content
                change.oldContent?.let { content ->
                    projectRepository.writeFile(projectId, change.path, content)
                        .getOrThrow()
                }
            }
            FileChangeType.DELETED -> {
                // Inverse of delete is create with old content
                change.oldContent?.let { content ->
                    projectRepository.createFile(projectId, change.path, content)
                        .getOrThrow()
                }
            }
            FileChangeType.RENAMED -> {
                // Inverse of rename is rename back
                val oldPath = change.oldPath ?: change.path
                val newPath = change.newPath ?: change.path
                projectRepository.renameFile(projectId, newPath, oldPath)
                    .getOrThrow()
            }
            FileChangeType.MOVED -> {
                // Inverse of move is move back
                val oldPath = change.oldPath ?: change.path
                val newPath = change.newPath ?: change.path
                projectRepository.moveFile(projectId, newPath, oldPath)
                    .getOrThrow()
            }
            FileChangeType.COPIED -> {
                // Inverse of copy is delete the copy
                projectRepository.deleteFile(projectId, change.path)
                    .getOrThrow()
            }
        }
    }

    /**
     * Get the description of what would be undone
     */
    fun peekUndo(): String? {
        return undoStack.lastOrNull()?.description
    }

    /**
     * Get the description of what would be redone
     */
    fun peekRedo(): String? {
        return redoStack.lastOrNull()?.description
    }

    /**
     * Clear all history
     */
    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        currentGroup = null
        currentGroupDescription = ""
        updateState()
    }

    /**
     * Get the undo history (for UI display)
     */
    fun getUndoHistory(): List<ChangeGroup> {
        return undoStack.toList().reversed()
    }

    /**
     * Get the redo history (for UI display)
     */
    fun getRedoHistory(): List<ChangeGroup> {
        return redoStack.toList().reversed()
    }

    private fun addToUndoStack(group: ChangeGroup) {
        undoStack.add(group)

        // Trim history if too large
        while (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }
    }

    private fun updateState() {
        _state.value = UndoRedoState(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            undoDescription = peekUndo(),
            redoDescription = peekRedo(),
            pendingChanges = currentGroup?.size ?: 0
        )
    }
}
