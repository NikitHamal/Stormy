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
    suspend fun onTodoCreated(todo: TodoItem)
    suspend fun onTodoUpdated(todo: TodoItem)
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

                // Enhanced file operations
                "get_file_info" -> executeGetFileInfo(projectId, arguments)
                "insert_at_line" -> executeInsertAtLine(projectId, arguments)
                "append_to_file" -> executeAppendToFile(projectId, arguments)
                "find_files" -> executeFindFiles(projectId, arguments)
                "get_project_summary" -> executeGetProjectSummary(projectId)
                "read_lines" -> executeReadLines(projectId, arguments)
                "diff_files" -> executeDiffFiles(projectId, arguments)

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

    // ==================== Enhanced File Operations ====================

    private suspend fun executeGetFileInfo(projectId: String, args: JsonObject): ToolResult {
        val path = args.getStringArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")

        return projectRepository.readFile(projectId, path)
            .fold(
                onSuccess = { content ->
                    val lines = content.lines()
                    val extension = path.substringAfterLast('.', "")
                    val fileType = getFileTypeDescription(extension)
                    val sizeBytes = content.length
                    val sizeStr = formatFileSize(sizeBytes)

                    val output = buildString {
                        appendLine("📄 File: $path")
                        appendLine("Type: $fileType")
                        appendLine("Size: $sizeStr ($sizeBytes bytes)")
                        appendLine("Lines: ${lines.size}")
                        appendLine("Extension: .$extension")
                    }
                    ToolResult(true, output)
                },
                onFailure = { ToolResult(false, "", "Failed to get file info: ${it.message}") }
            )
    }

    private fun getFileTypeDescription(extension: String): String {
        return when (extension.lowercase()) {
            "html", "htm" -> "HTML Document"
            "css" -> "CSS Stylesheet"
            "js" -> "JavaScript"
            "json" -> "JSON Data"
            "md" -> "Markdown"
            "txt" -> "Plain Text"
            "svg" -> "SVG Image"
            "xml" -> "XML Document"
            "ts" -> "TypeScript"
            "jsx" -> "JSX (React)"
            "tsx" -> "TSX (React TypeScript)"
            "vue" -> "Vue Component"
            else -> "Unknown ($extension)"
        }
    }

    private fun formatFileSize(bytes: Int): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    private suspend fun executeInsertAtLine(projectId: String, args: JsonObject): ToolResult {
        val path = args.getStringArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val lineNumberStr = args.getStringArg("line_number")
            ?: return ToolResult(false, "", "Missing required argument: line_number")
        val content = args.getStringArg("content")
            ?: return ToolResult(false, "", "Missing required argument: content")

        val lineNumber = lineNumberStr.toIntOrNull()
            ?: return ToolResult(false, "", "Invalid line_number: must be an integer")

        return projectRepository.readFile(projectId, path)
            .fold(
                onSuccess = { fileContent ->
                    val lines = fileContent.lines().toMutableList()

                    // Validate line number
                    if (lineNumber < 1) {
                        return@fold ToolResult(false, "", "Line number must be >= 1")
                    }

                    // Insert at the specified position (convert to 0-indexed)
                    val insertIndex = (lineNumber - 1).coerceAtMost(lines.size)
                    val contentLines = content.lines()
                    lines.addAll(insertIndex, contentLines)

                    val newContent = lines.joinToString("\n")
                    val oldContent = fileContent

                    projectRepository.writeFile(projectId, path, newContent)
                        .fold(
                            onSuccess = {
                                interactionCallback?.onFileChanged(path, FileChangeType.MODIFIED, oldContent, newContent)
                                ToolResult(true, "Inserted ${contentLines.size} line(s) at line $lineNumber in $path")
                            },
                            onFailure = { ToolResult(false, "", "Failed to write file: ${it.message}") }
                        )
                },
                onFailure = { ToolResult(false, "", "Failed to read file: ${it.message}") }
            )
    }

    private suspend fun executeAppendToFile(projectId: String, args: JsonObject): ToolResult {
        val path = args.getStringArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val content = args.getStringArg("content")
            ?: return ToolResult(false, "", "Missing required argument: content")

        return projectRepository.readFile(projectId, path)
            .fold(
                onSuccess = { existingContent ->
                    val newContent = if (existingContent.endsWith("\n")) {
                        existingContent + content
                    } else {
                        existingContent + "\n" + content
                    }

                    projectRepository.writeFile(projectId, path, newContent)
                        .fold(
                            onSuccess = {
                                interactionCallback?.onFileChanged(path, FileChangeType.MODIFIED, existingContent, newContent)
                                ToolResult(true, "Content appended to $path")
                            },
                            onFailure = { ToolResult(false, "", "Failed to write file: ${it.message}") }
                        )
                },
                onFailure = { ToolResult(false, "", "Failed to read file: ${it.message}") }
            )
    }

    private suspend fun executeFindFiles(projectId: String, args: JsonObject): ToolResult {
        val pattern = args.getStringArg("pattern")
            ?: return ToolResult(false, "", "Missing required argument: pattern")
        val basePath = args.getStringArg("path") ?: ""

        return try {
            val fileTree = projectRepository.getFileTree(projectId)
            val matchingFiles = mutableListOf<String>()
            findMatchingFiles(fileTree, pattern, basePath, matchingFiles)

            if (matchingFiles.isEmpty()) {
                ToolResult(true, "No files found matching pattern: $pattern")
            } else {
                val output = buildString {
                    appendLine("Found ${matchingFiles.size} file(s) matching '$pattern':")
                    matchingFiles.forEach { path ->
                        appendLine("  📄 $path")
                    }
                }
                ToolResult(true, output)
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to find files: ${e.message}")
        }
    }

    private fun findMatchingFiles(
        nodes: List<com.codex.stormy.domain.model.FileTreeNode>,
        pattern: String,
        basePath: String,
        results: MutableList<String>
    ) {
        for (node in nodes) {
            when (node) {
                is com.codex.stormy.domain.model.FileTreeNode.FileNode -> {
                    if (matchesGlobPattern(node.path, pattern, basePath)) {
                        results.add(node.path)
                    }
                }
                is com.codex.stormy.domain.model.FileTreeNode.FolderNode -> {
                    findMatchingFiles(node.children, pattern, basePath, results)
                }
            }
        }
    }

    private fun matchesGlobPattern(filePath: String, pattern: String, basePath: String): Boolean {
        // Handle ** for recursive matching
        val normalizedPattern = pattern
            .replace("**", ".*")
            .replace("*", "[^/]*")
            .replace(".", "\\.")

        val fullPattern = if (basePath.isNotEmpty()) {
            "$basePath/$normalizedPattern"
        } else {
            normalizedPattern
        }

        return try {
            val regex = Regex(fullPattern, RegexOption.IGNORE_CASE)
            regex.matches(filePath) || regex.containsMatchIn(filePath)
        } catch (e: Exception) {
            // Fallback to simple matching
            filePath.contains(pattern.replace("*", ""), ignoreCase = true)
        }
    }

    private suspend fun executeGetProjectSummary(projectId: String): ToolResult {
        return try {
            val fileTree = projectRepository.getFileTree(projectId)
            val stats = ProjectStats()
            analyzeProjectTree(fileTree, stats)

            val output = buildString {
                appendLine("📊 Project Summary")
                appendLine("━━━━━━━━━━━━━━━━━━")
                appendLine("Total files: ${stats.totalFiles}")
                appendLine("Total folders: ${stats.totalFolders}")
                appendLine()
                appendLine("Files by type:")
                stats.filesByExtension.entries
                    .sortedByDescending { it.value }
                    .forEach { (ext, count) ->
                        val icon = getFileIcon(ext)
                        appendLine("  $icon .$ext: $count file(s)")
                    }
                appendLine()
                appendLine("Structure depth: ${stats.maxDepth} levels")
            }
            ToolResult(true, output)
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to get project summary: ${e.message}")
        }
    }

    private data class ProjectStats(
        var totalFiles: Int = 0,
        var totalFolders: Int = 0,
        var filesByExtension: MutableMap<String, Int> = mutableMapOf(),
        var maxDepth: Int = 0
    )

    private fun analyzeProjectTree(
        nodes: List<com.codex.stormy.domain.model.FileTreeNode>,
        stats: ProjectStats,
        currentDepth: Int = 1
    ) {
        stats.maxDepth = maxOf(stats.maxDepth, currentDepth)

        for (node in nodes) {
            when (node) {
                is com.codex.stormy.domain.model.FileTreeNode.FileNode -> {
                    stats.totalFiles++
                    val ext = node.extension.ifEmpty { "no-ext" }
                    stats.filesByExtension[ext] = (stats.filesByExtension[ext] ?: 0) + 1
                }
                is com.codex.stormy.domain.model.FileTreeNode.FolderNode -> {
                    stats.totalFolders++
                    analyzeProjectTree(node.children, stats, currentDepth + 1)
                }
            }
        }
    }

    private fun getFileIcon(extension: String): String {
        return when (extension.lowercase()) {
            "html", "htm" -> "🌐"
            "css" -> "🎨"
            "js" -> "📜"
            "json" -> "📋"
            "md" -> "📝"
            "txt" -> "📄"
            "svg", "png", "jpg", "jpeg", "gif" -> "🖼️"
            else -> "📄"
        }
    }

    private suspend fun executeReadLines(projectId: String, args: JsonObject): ToolResult {
        val path = args.getStringArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val startLineStr = args.getStringArg("start_line")
            ?: return ToolResult(false, "", "Missing required argument: start_line")
        val endLineStr = args.getStringArg("end_line")
            ?: return ToolResult(false, "", "Missing required argument: end_line")

        val startLine = startLineStr.toIntOrNull()
            ?: return ToolResult(false, "", "Invalid start_line: must be an integer")
        val endLine = endLineStr.toIntOrNull()
            ?: return ToolResult(false, "", "Invalid end_line: must be an integer")

        if (startLine < 1) {
            return ToolResult(false, "", "start_line must be >= 1")
        }
        if (endLine < startLine) {
            return ToolResult(false, "", "end_line must be >= start_line")
        }

        return projectRepository.readFile(projectId, path)
            .fold(
                onSuccess = { content ->
                    val lines = content.lines()
                    val totalLines = lines.size

                    if (startLine > totalLines) {
                        return@fold ToolResult(false, "", "start_line ($startLine) exceeds total lines ($totalLines)")
                    }

                    val actualEndLine = minOf(endLine, totalLines)
                    val selectedLines = lines.subList(startLine - 1, actualEndLine)

                    val output = buildString {
                        appendLine("Lines $startLine-$actualEndLine of $totalLines in $path:")
                        appendLine("```")
                        selectedLines.forEachIndexed { index, line ->
                            val lineNum = startLine + index
                            appendLine("$lineNum: $line")
                        }
                        appendLine("```")
                    }
                    ToolResult(true, output)
                },
                onFailure = { ToolResult(false, "", "Failed to read file: ${it.message}") }
            )
    }

    private suspend fun executeDiffFiles(projectId: String, args: JsonObject): ToolResult {
        val path1 = args.getStringArg("path1")
            ?: return ToolResult(false, "", "Missing required argument: path1")
        val path2 = args.getStringArg("path2")
            ?: return ToolResult(false, "", "Missing required argument: path2")

        val content1Result = projectRepository.readFile(projectId, path1)
        val content2Result = projectRepository.readFile(projectId, path2)

        if (content1Result.isFailure) {
            return ToolResult(false, "", "Failed to read $path1: ${content1Result.exceptionOrNull()?.message}")
        }
        if (content2Result.isFailure) {
            return ToolResult(false, "", "Failed to read $path2: ${content2Result.exceptionOrNull()?.message}")
        }

        val content1 = content1Result.getOrNull()!!
        val content2 = content2Result.getOrNull()!!

        if (content1 == content2) {
            return ToolResult(true, "Files are identical")
        }

        val lines1 = content1.lines()
        val lines2 = content2.lines()

        val output = buildString {
            appendLine("📊 Diff: $path1 vs $path2")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("$path1: ${lines1.size} lines")
            appendLine("$path2: ${lines2.size} lines")
            appendLine()

            // Simple line-by-line diff
            val maxLines = maxOf(lines1.size, lines2.size)
            var diffCount = 0

            for (i in 0 until maxLines) {
                val line1 = lines1.getOrNull(i)
                val line2 = lines2.getOrNull(i)

                when {
                    line1 == line2 -> {
                        // Lines match, skip
                    }
                    line1 == null -> {
                        appendLine("+ Line ${i + 1}: $line2")
                        diffCount++
                    }
                    line2 == null -> {
                        appendLine("- Line ${i + 1}: $line1")
                        diffCount++
                    }
                    else -> {
                        appendLine("~ Line ${i + 1}:")
                        appendLine("  - $line1")
                        appendLine("  + $line2")
                        diffCount++
                    }
                }

                // Limit diff output
                if (diffCount > 50) {
                    appendLine("... (${maxLines - i - 1} more lines with potential differences)")
                    break
                }
            }

            if (diffCount == 0) {
                appendLine("Files have same content but different line endings or whitespace")
            } else {
                appendLine()
                appendLine("Total differences: $diffCount line(s)")
            }
        }

        return ToolResult(true, output)
    }

    // ==================== Todo Operations ====================

    private suspend fun executeCreateTodo(projectId: String, args: JsonObject): ToolResult {
        val title = args.getStringArg("title")
            ?: return ToolResult(false, "", "Missing required argument: title")
        val description = args.getStringArg("description") ?: ""

        val todo = TodoItem(
            title = title,
            description = description
        )

        val todos = sessionTodos.getOrPut(projectId) { mutableListOf() }
        todos.add(todo)

        // Notify callback about new todo
        interactionCallback?.onTodoCreated(todo)

        return ToolResult(true, "Todo created: [${todo.id.take(8)}] $title")
    }

    private suspend fun executeUpdateTodo(projectId: String, args: JsonObject): ToolResult {
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

        // Notify callback about todo update
        interactionCallback?.onTodoUpdated(todo)

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
