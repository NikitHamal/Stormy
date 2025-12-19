package com.codex.stormy.data.ai.tools

import com.codex.stormy.data.ai.ToolCallResponse
import com.codex.stormy.data.repository.ProjectRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/**
 * Todo item for task tracking
 */
data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    var status: TodoStatus = TodoStatus.PENDING
)

enum class TodoStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}

/**
 * Result of asking user a question
 */
sealed class UserInputResult {
    data class Answered(val answer: String) : UserInputResult()
    data object Pending : UserInputResult()
}

/**
 * Callback interface for tools that require user interaction
 */
interface ToolInteractionCallback {
    suspend fun askUser(question: String, options: List<String>?): String?
    suspend fun onTaskFinished(summary: String)
    suspend fun onFileChanged(path: String, changeType: FileChangeType, oldContent: String?, newContent: String?)
}

enum class FileChangeType {
    CREATED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    MOVED
}

/**
 * Executes tool calls from the AI agent
 */
class ToolExecutor(
    private val projectRepository: ProjectRepository,
    private val memoryStorage: MemoryStorage
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Session-based todo storage
    private val sessionTodos = mutableMapOf<String, MutableList<TodoItem>>()

    // Interaction callback for user input and notifications
    var interactionCallback: ToolInteractionCallback? = null

    /**
     * Execute a tool call and return the result
     */
    suspend fun execute(
        projectId: String,
        toolCall: ToolCallResponse
    ): ToolResult {
        return try {
            val arguments = json.parseToJsonElement(toolCall.function.arguments).jsonObject

            when (toolCall.function.name) {
                // File operations
                "read_file" -> executeReadFile(projectId, arguments)
                "write_file" -> executeWriteFile(projectId, arguments)
                "list_files" -> executeListFiles(projectId, arguments)
                "delete_file" -> executeDeleteFile(projectId, arguments)
                "create_folder" -> executeCreateFolder(projectId, arguments)
                "rename_file" -> executeRenameFile(projectId, arguments)
                "copy_file" -> executeCopyFile(projectId, arguments)
                "move_file" -> executeMoveFile(projectId, arguments)

                // Memory operations
                "save_memory" -> executeSaveMemory(projectId, arguments)
                "recall_memory" -> executeRecallMemory(projectId, arguments)
                "list_memories" -> executeListMemories(projectId)
                "delete_memory" -> executeDeleteMemory(projectId, arguments)
                "update_memory" -> executeUpdateMemory(projectId, arguments)

                // Search operations
                "search_files" -> executeSearchFiles(projectId, arguments)
                "search_replace" -> executeSearchReplace(projectId, arguments)
                "patch_file" -> executePatchFile(projectId, arguments)

                // Todo operations
                "create_todo" -> executeCreateTodo(projectId, arguments)
                "update_todo" -> executeUpdateTodo(projectId, arguments)
                "list_todos" -> executeListTodos(projectId)

                // Agent control
                "ask_user" -> executeAskUser(arguments)
                "finish_task" -> executeFinishTask(arguments)

                else -> ToolResult(
                    success = false,
                    output = "",
                    error = "Unknown tool: ${toolCall.function.name}"
                )
            }
        } catch (e: Exception) {
            ToolResult(
                success = false,
                output = "",
                error = "Error executing tool: ${e.message}"
            )
        }
    }

    // ==================== File Operations ====================

    private suspend fun executeReadFile(projectId: String, args: JsonObject): ToolResult {
        val path = args.getStringArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")

        return projectRepository.readFile(projectId, path)
            .fold(
                onSuccess = { content ->
                    ToolResult(true, content)
                },
                onFailure = { error ->
                    ToolResult(false, "", "Failed to read file: ${error.message}")
                }
            )
    }

    private suspend fun executeWriteFile(projectId: String, args: JsonObject): ToolResult {
        val path = args.getStringArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val content = args.getStringArg("content")
            ?: return ToolResult(false, "", "Missing required argument: content")

        // Check if file exists and get old content for diff
        val oldContent = projectRepository.readFile(projectId, path).getOrNull()
        val isNewFile = oldContent == null

        return if (isNewFile) {
            projectRepository.createFile(projectId, path, content)
                .fold(
                    onSuccess = {
                        interactionCallback?.onFileChanged(path, FileChangeType.CREATED, null, content)
                        ToolResult(true, "File created successfully: $path")
                    },
                    onFailure = {
                        // File might exist but was not readable, try write directly
                        projectRepository.writeFile(projectId, path, content)
                            .fold(
                                onSuccess = {
                                    interactionCallback?.onFileChanged(path, FileChangeType.MODIFIED, oldContent, content)
                                    ToolResult(true, "File written successfully: $path")
                                },
                                onFailure = { ToolResult(false, "", "Failed to write file: ${it.message}") }
                            )
                    }
                )
        } else {
            projectRepository.writeFile(projectId, path, content)
                .fold(
                    onSuccess = {
                        interactionCallback?.onFileChanged(path, FileChangeType.MODIFIED, oldContent, content)
                        ToolResult(true, "File updated successfully: $path")
                    },
                    onFailure = { ToolResult(false, "", "Failed to write file: ${it.message}") }
                )
        }
    }

    private suspend fun executeListFiles(projectId: String, args: JsonObject): ToolResult {
        val path = args.getStringArg("path") ?: ""

        return try {
            val fileTree = projectRepository.getFileTree(projectId)
            val output = buildFileTreeString(fileTree, path)
            ToolResult(true, output.ifEmpty { "Directory is empty" })
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to list files: ${e.message}")
        }
    }

    private fun buildFileTreeString(
        nodes: List<com.codex.stormy.domain.model.FileTreeNode>,
        basePath: String = "",
        indent: String = ""
    ): String {
        val sb = StringBuilder()

        for (node in nodes) {
            when (node) {
                is com.codex.stormy.domain.model.FileTreeNode.FileNode -> {
                    sb.appendLine("$indent📄 ${node.name}")
                }
                is com.codex.stormy.domain.model.FileTreeNode.FolderNode -> {
                    sb.appendLine("$indent📁 ${node.name}/")
                    sb.append(buildFileTreeString(node.children, node.path, "$indent  "))
                }
            }
        }

        return sb.toString()
    }

    private suspend fun executeDeleteFile(projectId: String, args: JsonObject): ToolResult {
        val path = args.getStringArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")

        // Get old content for reference
        val oldContent = projectRepository.readFile(projectId, path).getOrNull()

        return projectRepository.deleteFile(projectId, path)
            .fold(
                onSuccess = {
                    interactionCallback?.onFileChanged(path, FileChangeType.DELETED, oldContent, null)
                    ToolResult(true, "File deleted successfully: $path")
                },
                onFailure = { ToolResult(false, "", "Failed to delete file: ${it.message}") }
            )
    }

    private suspend fun executeCreateFolder(projectId: String, args: JsonObject): ToolResult {
        val path = args.getStringArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")

        return projectRepository.createFolder(projectId, path)
            .fold(
                onSuccess = { ToolResult(true, "Folder created successfully: $path") },
                onFailure = { ToolResult(false, "", "Failed to create folder: ${it.message}") }
            )
    }

    private suspend fun executeRenameFile(projectId: String, args: JsonObject): ToolResult {
        val oldPath = args.getStringArg("old_path")
            ?: return ToolResult(false, "", "Missing required argument: old_path")
        val newPath = args.getStringArg("new_path")
            ?: return ToolResult(false, "", "Missing required argument: new_path")

        return projectRepository.renameFile(projectId, oldPath, newPath)
            .fold(
                onSuccess = {
                    interactionCallback?.onFileChanged(oldPath, FileChangeType.RENAMED, null, newPath)
                    ToolResult(true, "File renamed from '$oldPath' to '$newPath'")
                },
                onFailure = { ToolResult(false, "", "Failed to rename file: ${it.message}") }
            )
    }

    private suspend fun executeCopyFile(projectId: String, args: JsonObject): ToolResult {
        val sourcePath = args.getStringArg("source_path")
            ?: return ToolResult(false, "", "Missing required argument: source_path")
        val destinationPath = args.getStringArg("destination_path")
            ?: return ToolResult(false, "", "Missing required argument: destination_path")

        return projectRepository.copyFile(projectId, sourcePath, destinationPath)
            .fold(
                onSuccess = {
                    interactionCallback?.onFileChanged(destinationPath, FileChangeType.COPIED, sourcePath, null)
                    ToolResult(true, "File copied from '$sourcePath' to '$destinationPath'")
                },
                onFailure = { ToolResult(false, "", "Failed to copy file: ${it.message}") }
            )
    }

    private suspend fun executeMoveFile(projectId: String, args: JsonObject): ToolResult {
        val sourcePath = args.getStringArg("source_path")
            ?: return ToolResult(false, "", "Missing required argument: source_path")
        val destinationPath = args.getStringArg("destination_path")
            ?: return ToolResult(false, "", "Missing required argument: destination_path")

        return projectRepository.moveFile(projectId, sourcePath, destinationPath)
            .fold(
                onSuccess = {
                    interactionCallback?.onFileChanged(sourcePath, FileChangeType.MOVED, null, destinationPath)
                    ToolResult(true, "File moved from '$sourcePath' to '$destinationPath'")
                },
                onFailure = { ToolResult(false, "", "Failed to move file: ${it.message}") }
            )
    }

    // ==================== Memory Operations ====================

    private suspend fun executeSaveMemory(projectId: String, args: JsonObject): ToolResult {
        val key = args.getStringArg("key")
            ?: return ToolResult(false, "", "Missing required argument: key")
        val value = args.getStringArg("value")
            ?: return ToolResult(false, "", "Missing required argument: value")

        return try {
            memoryStorage.save(projectId, key, value)
            ToolResult(true, "Memory saved: $key")
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to save memory: ${e.message}")
        }
    }

    private suspend fun executeRecallMemory(projectId: String, args: JsonObject): ToolResult {
        val key = args.getStringArg("key")
            ?: return ToolResult(false, "", "Missing required argument: key")

        return try {
            val value = memoryStorage.recall(projectId, key)
            if (value != null) {
                ToolResult(true, value)
            } else {
                ToolResult(true, "No memory found for key: $key")
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to recall memory: ${e.message}")
        }
    }

    private suspend fun executeListMemories(projectId: String): ToolResult {
        return try {
            val memories = memoryStorage.list(projectId)
            if (memories.isEmpty()) {
                ToolResult(true, "No memories saved for this project")
            } else {
                val output = memories.entries.joinToString("\n") { (key, value) ->
                    "• $key: $value"
                }
                ToolResult(true, output)
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to list memories: ${e.message}")
        }
    }

    private suspend fun executeDeleteMemory(projectId: String, args: JsonObject): ToolResult {
        val key = args.getStringArg("key")
            ?: return ToolResult(false, "", "Missing required argument: key")

        return try {
            val deleted = memoryStorage.delete(projectId, key)
            if (deleted) {
                ToolResult(true, "Memory deleted: $key")
            } else {
                ToolResult(true, "No memory found for key: $key")
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to delete memory: ${e.message}")
        }
    }

    private suspend fun executeUpdateMemory(projectId: String, args: JsonObject): ToolResult {
        val key = args.getStringArg("key")
            ?: return ToolResult(false, "", "Missing required argument: key")
        val value = args.getStringArg("value")
            ?: return ToolResult(false, "", "Missing required argument: value")

        return try {
            // Check if memory exists first
            val existing = memoryStorage.recall(projectId, key)
            memoryStorage.save(projectId, key, value)
            if (existing != null) {
                ToolResult(true, "Memory updated: $key")
            } else {
                ToolResult(true, "Memory created: $key")
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to update memory: ${e.message}")
        }
    }

    // ==================== Search Operations ====================

    private suspend fun executeSearchFiles(projectId: String, args: JsonObject): ToolResult {
        val query = args.getStringArg("query")
            ?: return ToolResult(false, "", "Missing required argument: query")
        val filePattern = args.getStringArg("file_pattern")

        return try {
            val results = searchInProject(projectId, query, filePattern)
            if (results.isEmpty()) {
                ToolResult(true, "No matches found for: $query")
            } else {
                ToolResult(true, results)
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Search failed: ${e.message}")
        }
    }

    private suspend fun searchInProject(
        projectId: String,
        query: String,
        filePattern: String?
    ): String {
        val fileTree = projectRepository.getFileTree(projectId)
        val results = StringBuilder()

        searchInNodes(projectId, fileTree, query, filePattern, results)

        return results.toString()
    }

    private suspend fun searchInNodes(
        projectId: String,
        nodes: List<com.codex.stormy.domain.model.FileTreeNode>,
        query: String,
        filePattern: String?,
        results: StringBuilder
    ) {
        for (node in nodes) {
            when (node) {
                is com.codex.stormy.domain.model.FileTreeNode.FileNode -> {
                    if (filePattern != null && !matchesPattern(node.name, filePattern)) {
                        continue
                    }

                    projectRepository.readFile(projectId, node.path)
                        .onSuccess { content ->
                            val lines = content.lines()
                            lines.forEachIndexed { index, line ->
                                if (line.contains(query, ignoreCase = true)) {
                                    results.appendLine("${node.path}:${index + 1}: ${line.trim()}")
                                }
                            }
                        }
                }
                is com.codex.stormy.domain.model.FileTreeNode.FolderNode -> {
                    searchInNodes(projectId, node.children, query, filePattern, results)
                }
            }
        }
    }

    private suspend fun executeSearchReplace(projectId: String, args: JsonObject): ToolResult {
        val search = args.getStringArg("search")
            ?: return ToolResult(false, "", "Missing required argument: search")
        val replace = args.getStringArg("replace")
            ?: return ToolResult(false, "", "Missing required argument: replace")
        val filePattern = args.getStringArg("file_pattern")
        val dryRun = args.getBooleanArg("dry_run", false)

        return projectRepository.searchAndReplace(projectId, search, replace, filePattern, dryRun)
            .fold(
                onSuccess = { result ->
                    if (result.filesModified == 0) {
                        ToolResult(true, "No matches found for: $search")
                    } else {
                        val action = if (dryRun) "Would replace" else "Replaced"
                        val output = buildString {
                            appendLine("$action ${"occurrence".pluralize(result.totalReplacements)} in ${"file".pluralize(result.filesModified)}:")
                            result.files.forEach { file ->
                                appendLine("  • ${file.path}: ${"replacement".pluralize(file.replacements)}")
                            }
                        }
                        ToolResult(true, output)
                    }
                },
                onFailure = { ToolResult(false, "", "Search and replace failed: ${it.message}") }
            )
    }

    private suspend fun executePatchFile(projectId: String, args: JsonObject): ToolResult {
        val path = args.getStringArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val oldContent = args.getStringArg("old_content")
            ?: return ToolResult(false, "", "Missing required argument: old_content")
        val newContent = args.getStringArg("new_content")
            ?: return ToolResult(false, "", "Missing required argument: new_content")

        // Get file content before patch for diff
        val beforeContent = projectRepository.readFile(projectId, path).getOrNull()

        return projectRepository.patchFile(projectId, path, oldContent, newContent)
            .fold(
                onSuccess = {
                    val afterContent = projectRepository.readFile(projectId, path).getOrNull()
                    interactionCallback?.onFileChanged(path, FileChangeType.MODIFIED, beforeContent, afterContent)
                    ToolResult(true, "File patched successfully: $path")
                },
                onFailure = { ToolResult(false, "", "Failed to patch file: ${it.message}") }
            )
    }

    // ==================== Todo Operations ====================

    private fun executeCreateTodo(projectId: String, args: JsonObject): ToolResult {
        val title = args.getStringArg("title")
            ?: return ToolResult(false, "", "Missing required argument: title")
        val description = args.getStringArg("description") ?: ""

        val todo = TodoItem(
            title = title,
            description = description
        )

        val todos = sessionTodos.getOrPut(projectId) { mutableListOf() }
        todos.add(todo)

        return ToolResult(true, "Todo created: [${todo.id.take(8)}] $title")
    }

    private fun executeUpdateTodo(projectId: String, args: JsonObject): ToolResult {
        val todoId = args.getStringArg("todo_id")
            ?: return ToolResult(false, "", "Missing required argument: todo_id")
        val statusStr = args.getStringArg("status")
            ?: return ToolResult(false, "", "Missing required argument: status")

        val status = when (statusStr.lowercase()) {
            "pending" -> TodoStatus.PENDING
            "in_progress" -> TodoStatus.IN_PROGRESS
            "completed" -> TodoStatus.COMPLETED
            else -> return ToolResult(false, "", "Invalid status. Use: pending, in_progress, or completed")
        }

        val todos = sessionTodos[projectId] ?: return ToolResult(false, "", "No todos found")
        val todo = todos.find { it.id.startsWith(todoId) || it.id == todoId }
            ?: return ToolResult(false, "", "Todo not found: $todoId")

        todo.status = status
        return ToolResult(true, "Todo updated: [${todo.id.take(8)}] ${todo.title} -> $status")
    }

    private fun executeListTodos(projectId: String): ToolResult {
        val todos = sessionTodos[projectId]
        if (todos.isNullOrEmpty()) {
            return ToolResult(true, "No todos for this session")
        }

        val output = buildString {
            appendLine("Current todos:")
            todos.forEach { todo ->
                val statusIcon = when (todo.status) {
                    TodoStatus.PENDING -> "⬜"
                    TodoStatus.IN_PROGRESS -> "🔄"
                    TodoStatus.COMPLETED -> "✅"
                }
                appendLine("$statusIcon [${todo.id.take(8)}] ${todo.title}")
                if (todo.description.isNotEmpty()) {
                    appendLine("   ${todo.description}")
                }
            }
        }

        return ToolResult(true, output)
    }

    // ==================== Agent Control ====================

    private suspend fun executeAskUser(args: JsonObject): ToolResult {
        val question = args.getStringArg("question")
            ?: return ToolResult(false, "", "Missing required argument: question")
        val optionsStr = args.getStringArg("options")
        val options = optionsStr?.split(",")?.map { it.trim() }

        val callback = interactionCallback
            ?: return ToolResult(true, "Question for user: $question" +
                (options?.let { "\nOptions: ${it.joinToString(", ")}" } ?: ""))

        val answer = callback.askUser(question, options)
        return if (answer != null) {
            ToolResult(true, "User response: $answer")
        } else {
            ToolResult(true, "Waiting for user response...")
        }
    }

    private suspend fun executeFinishTask(args: JsonObject): ToolResult {
        val summary = args.getStringArg("summary")
            ?: return ToolResult(false, "", "Missing required argument: summary")

        interactionCallback?.onTaskFinished(summary)
        return ToolResult(true, "Task completed: $summary")
    }

    // ==================== Utility Functions ====================

    private fun matchesPattern(filename: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(filename)
    }

    private fun String.pluralize(count: Int): String {
        return if (count == 1) "$count $this" else "$count ${this}s"
    }

    /**
     * Clear session todos for a project
     */
    fun clearSessionTodos(projectId: String) {
        sessionTodos.remove(projectId)
    }

    /**
     * Get all session todos for a project
     */
    fun getSessionTodos(projectId: String): List<TodoItem> {
        return sessionTodos[projectId]?.toList() ?: emptyList()
    }
}
