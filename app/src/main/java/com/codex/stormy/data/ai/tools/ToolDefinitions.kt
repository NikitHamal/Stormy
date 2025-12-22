package com.codex.stormy.data.ai.tools

import com.codex.stormy.data.ai.FunctionDefinition
import com.codex.stormy.data.ai.Tool
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tool result from execution
 */
@Serializable
data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
)

/**
 * Helper class for building JSON Schema parameters
 */
@Serializable
private data class ParameterProperty(
    val type: String,
    val description: String
)

@Serializable
private data class ParametersSchema(
    val type: String = "object",
    val properties: Map<String, ParameterProperty>,
    val required: List<String>
)

/**
 * All available tools for Stormy agent
 */
object StormyTools {

    private val json = Json { encodeDefaults = true }

    private fun createParametersJson(
        properties: Map<String, Pair<String, String>>,
        required: List<String>
    ): JsonElement {
        val schema = ParametersSchema(
            type = "object",
            properties = properties.mapValues { (_, value) ->
                ParameterProperty(type = value.first, description = value.second)
            },
            required = required
        )
        return json.parseToJsonElement(json.encodeToString(schema))
    }

    // File operation tools
    val READ_FILE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "read_file",
            description = "Read the contents of a file from the project. Use this to view existing code before making changes.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to Pair("string", "The relative path to the file from the project root (e.g., 'index.html', 'css/styles.css')")
                ),
                required = listOf("path")
            )
        )
    )

    val WRITE_FILE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "write_file",
            description = "Write content to a file. This will create the file if it doesn't exist or overwrite it if it does. Use this to create or update code files.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to Pair("string", "The relative path to the file from the project root"),
                    "content" to Pair("string", "The full content to write to the file")
                ),
                required = listOf("path", "content")
            )
        )
    )

    val LIST_FILES = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "list_files",
            description = "List all files and folders in a directory. Use this to explore the project structure.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to Pair("string", "The relative path to the directory. Use empty string '' or '.' for root")
                ),
                required = listOf("path")
            )
        )
    )

    val DELETE_FILE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "delete_file",
            description = "Delete a file from the project. Use with caution.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to Pair("string", "The relative path to the file to delete")
                ),
                required = listOf("path")
            )
        )
    )

    val CREATE_FOLDER = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "create_folder",
            description = "Create a new folder in the project.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to Pair("string", "The relative path for the new folder")
                ),
                required = listOf("path")
            )
        )
    )

    val RENAME_FILE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "rename_file",
            description = "Rename or move a file to a new location.",
            parameters = createParametersJson(
                properties = mapOf(
                    "old_path" to Pair("string", "The current path of the file"),
                    "new_path" to Pair("string", "The new path for the file")
                ),
                required = listOf("old_path", "new_path")
            )
        )
    )

    val COPY_FILE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "copy_file",
            description = "Copy a file to a new location.",
            parameters = createParametersJson(
                properties = mapOf(
                    "source_path" to Pair("string", "The path of the file to copy"),
                    "destination_path" to Pair("string", "The destination path for the copied file")
                ),
                required = listOf("source_path", "destination_path")
            )
        )
    )

    val MOVE_FILE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "move_file",
            description = "Move a file to a new location.",
            parameters = createParametersJson(
                properties = mapOf(
                    "source_path" to Pair("string", "The current path of the file"),
                    "destination_path" to Pair("string", "The new path for the file")
                ),
                required = listOf("source_path", "destination_path")
            )
        )
    )

    // Memory tools
    val SAVE_MEMORY = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "save_memory",
            description = "Save information about the project for future reference. Use this to remember important patterns, decisions, or learnings.",
            parameters = createParametersJson(
                properties = mapOf(
                    "key" to Pair("string", "A unique key to identify this memory (e.g., 'color_scheme', 'nav_pattern')"),
                    "value" to Pair("string", "The information to remember")
                ),
                required = listOf("key", "value")
            )
        )
    )

    val RECALL_MEMORY = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "recall_memory",
            description = "Retrieve previously saved information about the project.",
            parameters = createParametersJson(
                properties = mapOf(
                    "key" to Pair("string", "The key of the memory to retrieve")
                ),
                required = listOf("key")
            )
        )
    )

    val LIST_MEMORIES = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "list_memories",
            description = "List all saved memories for this project.",
            parameters = createParametersJson(
                properties = emptyMap(),
                required = emptyList()
            )
        )
    )

    val DELETE_MEMORY = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "delete_memory",
            description = "Delete a previously saved memory.",
            parameters = createParametersJson(
                properties = mapOf(
                    "key" to Pair("string", "The key of the memory to delete")
                ),
                required = listOf("key")
            )
        )
    )

    val UPDATE_MEMORY = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "update_memory",
            description = "Update an existing memory with new information.",
            parameters = createParametersJson(
                properties = mapOf(
                    "key" to Pair("string", "The key of the memory to update"),
                    "value" to Pair("string", "The new value for this memory")
                ),
                required = listOf("key", "value")
            )
        )
    )

    // Search tools
    val SEARCH_FILES = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "search_files",
            description = "Search for text content across all files in the project.",
            parameters = createParametersJson(
                properties = mapOf(
                    "query" to Pair("string", "The text to search for"),
                    "file_pattern" to Pair("string", "Optional file pattern to filter (e.g., '*.html', '*.css'). Leave empty to search all files.")
                ),
                required = listOf("query")
            )
        )
    )

    val SEARCH_REPLACE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "search_replace",
            description = "Search and replace text across files in the project.",
            parameters = createParametersJson(
                properties = mapOf(
                    "search" to Pair("string", "The text to search for"),
                    "replace" to Pair("string", "The text to replace with"),
                    "file_pattern" to Pair("string", "Optional file pattern to filter (e.g., '*.html', '*.css'). Leave empty to search all files."),
                    "dry_run" to Pair("boolean", "If true, only show what would be replaced without making changes")
                ),
                required = listOf("search", "replace")
            )
        )
    )

    // Patch tool for precise file modifications
    val PATCH_FILE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "patch_file",
            description = "Apply a precise patch to a file. Useful for making targeted changes to specific sections of code.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to Pair("string", "The path to the file to patch"),
                    "old_content" to Pair("string", "The exact content to find and replace"),
                    "new_content" to Pair("string", "The content to replace the old content with")
                ),
                required = listOf("path", "old_content", "new_content")
            )
        )
    )

    // Insert at specific line number
    val INSERT_AT_LINE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "insert_at_line",
            description = "Insert content at a specific line number in a file. Lines are 1-indexed. Use 0 to insert at the beginning.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to Pair("string", "The path to the file"),
                    "line_number" to Pair("integer", "The line number to insert at (1-indexed, 0 for beginning)"),
                    "content" to Pair("string", "The content to insert")
                ),
                required = listOf("path", "line_number", "content")
            )
        )
    )

    // Get file information
    val GET_FILE_INFO = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "get_file_info",
            description = "Get information about a file including size, extension, line count, and last modified time.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to Pair("string", "The path to the file")
                ),
                required = listOf("path")
            )
        )
    )

    // Regex-based find and replace
    val REGEX_REPLACE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "regex_replace",
            description = "Find and replace content using regular expressions. Powerful for complex pattern matching.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to Pair("string", "The path to the file"),
                    "pattern" to Pair("string", "The regular expression pattern to search for"),
                    "replacement" to Pair("string", "The replacement string (can use $1, $2 for capture groups)"),
                    "flags" to Pair("string", "Optional regex flags: 'i' for case-insensitive, 'g' for global (default), 'm' for multiline")
                ),
                required = listOf("path", "pattern", "replacement")
            )
        )
    )

    // Append to file
    val APPEND_TO_FILE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "append_to_file",
            description = "Append content to the end of a file. Creates the file if it doesn't exist.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to Pair("string", "The path to the file"),
                    "content" to Pair("string", "The content to append")
                ),
                required = listOf("path", "content")
            )
        )
    )

    // Prepend to file
    val PREPEND_TO_FILE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "prepend_to_file",
            description = "Prepend content to the beginning of a file. Creates the file if it doesn't exist.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to Pair("string", "The path to the file"),
                    "content" to Pair("string", "The content to prepend")
                ),
                required = listOf("path", "content")
            )
        )
    )

    // Task management tools
    val CREATE_TODO = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "create_todo",
            description = "Create a new todo item for tracking work on this task.",
            parameters = createParametersJson(
                properties = mapOf(
                    "title" to Pair("string", "Short title for the todo item"),
                    "description" to Pair("string", "Detailed description of what needs to be done")
                ),
                required = listOf("title")
            )
        )
    )

    val UPDATE_TODO = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "update_todo",
            description = "Update the status of a todo item.",
            parameters = createParametersJson(
                properties = mapOf(
                    "todo_id" to Pair("string", "The ID of the todo item to update"),
                    "status" to Pair("string", "New status: 'pending', 'in_progress', or 'completed'")
                ),
                required = listOf("todo_id", "status")
            )
        )
    )

    val LIST_TODOS = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "list_todos",
            description = "List all todo items for the current task.",
            parameters = createParametersJson(
                properties = emptyMap(),
                required = emptyList()
            )
        )
    )

    // Agent control tools
    val ASK_USER = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "ask_user",
            description = "Ask the user a question when clarification is needed. Use this when you need more information to proceed.",
            parameters = createParametersJson(
                properties = mapOf(
                    "question" to Pair("string", "The question to ask the user"),
                    "options" to Pair("string", "Optional comma-separated list of suggested answers")
                ),
                required = listOf("question")
            )
        )
    )

    val FINISH_TASK = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "finish_task",
            description = "Mark the current task as complete. Call this when you have finished all requested work.",
            parameters = createParametersJson(
                properties = mapOf(
                    "summary" to Pair("string", "A brief summary of what was accomplished")
                ),
                required = listOf("summary")
            )
        )
    )

    // Self-reflection tool
    val THINK = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "think",
            description = "Use this tool to reason about your approach, plan next steps, or reflect on the current state of the task. This helps organize complex tasks and make better decisions. Call this before making significant changes or when facing difficult problems.",
            parameters = createParametersJson(
                properties = mapOf(
                    "thought" to Pair("string", "Your reasoning, analysis, or plan. Be specific about what you're thinking and why.")
                ),
                required = listOf("thought")
            )
        )
    )

    // ==================== Git Tools ====================

    val GIT_STATUS = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "git_status",
            description = "Get the current Git repository status including branch name, changed files, staged files, and sync status with remote.",
            parameters = createParametersJson(
                properties = emptyMap(),
                required = emptyList()
            )
        )
    )

    val GIT_STAGE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "git_stage",
            description = "Stage files for commit. Use 'all' to stage all changes or provide specific file paths.",
            parameters = createParametersJson(
                properties = mapOf(
                    "paths" to Pair("string", "Comma-separated list of file paths to stage, or 'all' to stage everything")
                ),
                required = listOf("paths")
            )
        )
    )

    val GIT_UNSTAGE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "git_unstage",
            description = "Unstage files that were previously staged for commit.",
            parameters = createParametersJson(
                properties = mapOf(
                    "paths" to Pair("string", "Comma-separated list of file paths to unstage")
                ),
                required = listOf("paths")
            )
        )
    )

    val GIT_COMMIT = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "git_commit",
            description = "Create a Git commit with the staged changes.",
            parameters = createParametersJson(
                properties = mapOf(
                    "message" to Pair("string", "The commit message describing the changes")
                ),
                required = listOf("message")
            )
        )
    )

    val GIT_PUSH = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "git_push",
            description = "Push local commits to the remote repository.",
            parameters = createParametersJson(
                properties = mapOf(
                    "remote" to Pair("string", "The remote name (default: origin)"),
                    "set_upstream" to Pair("boolean", "Set upstream tracking for the current branch")
                ),
                required = emptyList()
            )
        )
    )

    val GIT_PULL = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "git_pull",
            description = "Pull changes from the remote repository.",
            parameters = createParametersJson(
                properties = mapOf(
                    "remote" to Pair("string", "The remote name (default: origin)"),
                    "rebase" to Pair("boolean", "Use rebase instead of merge")
                ),
                required = emptyList()
            )
        )
    )

    val GIT_BRANCH = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "git_branch",
            description = "List branches or create a new branch.",
            parameters = createParametersJson(
                properties = mapOf(
                    "action" to Pair("string", "Action to perform: 'list', 'create', or 'delete'"),
                    "name" to Pair("string", "Branch name (required for create/delete)"),
                    "checkout" to Pair("boolean", "Checkout the branch after creating it")
                ),
                required = listOf("action")
            )
        )
    )

    val GIT_CHECKOUT = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "git_checkout",
            description = "Switch to a different branch.",
            parameters = createParametersJson(
                properties = mapOf(
                    "branch" to Pair("string", "The name of the branch to checkout")
                ),
                required = listOf("branch")
            )
        )
    )

    val GIT_LOG = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "git_log",
            description = "View recent commit history.",
            parameters = createParametersJson(
                properties = mapOf(
                    "count" to Pair("integer", "Number of commits to show (default: 10)")
                ),
                required = emptyList()
            )
        )
    )

    val GIT_DIFF = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "git_diff",
            description = "View changes in a file or all changed files.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to Pair("string", "Optional file path to get diff for. Leave empty for all changes."),
                    "staged" to Pair("boolean", "Show staged changes instead of unstaged")
                ),
                required = emptyList()
            )
        )
    )

    val GIT_DISCARD = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "git_discard",
            description = "Discard local changes to files. Use with caution as this cannot be undone.",
            parameters = createParametersJson(
                properties = mapOf(
                    "paths" to Pair("string", "Comma-separated list of file paths to discard changes for")
                ),
                required = listOf("paths")
            )
        )
    )

    /**
     * Get all tools for agent mode
     */
    fun getAllTools(): List<Tool> = listOf(
        // File operations
        READ_FILE,
        WRITE_FILE,
        LIST_FILES,
        DELETE_FILE,
        CREATE_FOLDER,
        RENAME_FILE,
        COPY_FILE,
        MOVE_FILE,
        // Enhanced file operations
        INSERT_AT_LINE,
        GET_FILE_INFO,
        REGEX_REPLACE,
        APPEND_TO_FILE,
        PREPEND_TO_FILE,
        // Memory operations
        SAVE_MEMORY,
        RECALL_MEMORY,
        LIST_MEMORIES,
        DELETE_MEMORY,
        UPDATE_MEMORY,
        // Search operations
        SEARCH_FILES,
        SEARCH_REPLACE,
        PATCH_FILE,
        // Task management
        CREATE_TODO,
        UPDATE_TODO,
        LIST_TODOS,
        // Agent control
        ASK_USER,
        FINISH_TASK,
        THINK,
        // Git tools
        GIT_STATUS,
        GIT_STAGE,
        GIT_UNSTAGE,
        GIT_COMMIT,
        GIT_PUSH,
        GIT_PULL,
        GIT_BRANCH,
        GIT_CHECKOUT,
        GIT_LOG,
        GIT_DIFF,
        GIT_DISCARD
    )

    /**
     * Get basic tools for non-agent mode (just read access)
     */
    fun getBasicTools(): List<Tool> = listOf(
        READ_FILE,
        LIST_FILES,
        SEARCH_FILES
    )

    /**
     * Get tool names for logging and display
     */
    fun getToolNames(): List<String> = getAllTools().map { it.function.name }
}

/**
 * Helper to extract string argument from tool call
 */
fun JsonObject.getStringArg(key: String): String? {
    return this[key]?.jsonPrimitive?.content
}

/**
 * Helper to extract boolean argument from tool call
 */
fun JsonObject.getBooleanArg(key: String, default: Boolean = false): Boolean {
    return this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: default
}
