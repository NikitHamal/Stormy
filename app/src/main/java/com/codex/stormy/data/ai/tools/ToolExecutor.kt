package com.codex.stormy.data.ai.tools

import com.codex.stormy.data.ai.ToolCallResponse
import com.codex.stormy.data.repository.ProjectRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Executes tool calls from the AI agent
 */
class ToolExecutor(
    private val projectRepository: ProjectRepository,
    private val memoryStorage: MemoryStorage
) {
    private val json = Json { ignoreUnknownKeys = true }

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
                "read_file" -> executeReadFile(projectId, arguments)
                "write_file" -> executeWriteFile(projectId, arguments)
                "list_files" -> executeListFiles(projectId, arguments)
                "delete_file" -> executeDeleteFile(projectId, arguments)
                "create_folder" -> executeCreateFolder(projectId, arguments)
                "rename_file" -> executeRenameFile(projectId, arguments)
                "save_memory" -> executeSaveMemory(projectId, arguments)
                "recall_memory" -> executeRecallMemory(projectId, arguments)
                "list_memories" -> executeListMemories(projectId)
                "search_files" -> executeSearchFiles(projectId, arguments)
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

        // First check if file exists, if not create it
        val fileExists = projectRepository.readFile(projectId, path).isSuccess

        return if (!fileExists) {
            // Create the file first
            projectRepository.createFile(projectId, path)
                .fold(
                    onSuccess = {
                        // Then write content
                        projectRepository.writeFile(projectId, path, content)
                            .fold(
                                onSuccess = { ToolResult(true, "File created and written successfully: $path") },
                                onFailure = { ToolResult(false, "", "Failed to write file: ${it.message}") }
                            )
                    },
                    onFailure = { ToolResult(false, "", "Failed to create file: ${it.message}") }
                )
        } else {
            projectRepository.writeFile(projectId, path, content)
                .fold(
                    onSuccess = { ToolResult(true, "File updated successfully: $path") },
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

        return projectRepository.deleteFile(projectId, path)
            .fold(
                onSuccess = { ToolResult(true, "File deleted successfully: $path") },
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
                onSuccess = { ToolResult(true, "File renamed from '$oldPath' to '$newPath'") },
                onFailure = { ToolResult(false, "", "Failed to rename file: ${it.message}") }
            )
    }

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
                    // Check file pattern
                    if (filePattern != null && !matchesPattern(node.name, filePattern)) {
                        continue
                    }

                    // Read and search file content
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

    private fun matchesPattern(filename: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(filename)
    }
}
