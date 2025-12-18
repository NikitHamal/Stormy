package com.codex.stormy.data.ai.tools

import com.codex.stormy.data.ai.Tool
import com.codex.stormy.data.ai.ToolFunction
import com.codex.stormy.data.ai.ToolParameter
import com.codex.stormy.data.ai.ToolParameters
import kotlinx.serialization.Serializable
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
 * All available tools for Stormy agent
 */
object StormyTools {

    // File operation tools
    val READ_FILE = Tool(
        type = "function",
        function = ToolFunction(
            name = "read_file",
            description = "Read the contents of a file from the project. Use this to view existing code before making changes.",
            parameters = ToolParameters(
                type = "object",
                properties = mapOf(
                    "path" to ToolParameter(
                        type = "string",
                        description = "The relative path to the file from the project root (e.g., 'index.html', 'css/styles.css')"
                    )
                ),
                required = listOf("path")
            )
        )
    )

    val WRITE_FILE = Tool(
        type = "function",
        function = ToolFunction(
            name = "write_file",
            description = "Write content to a file. This will create the file if it doesn't exist or overwrite it if it does. Use this to create or update code files.",
            parameters = ToolParameters(
                type = "object",
                properties = mapOf(
                    "path" to ToolParameter(
                        type = "string",
                        description = "The relative path to the file from the project root"
                    ),
                    "content" to ToolParameter(
                        type = "string",
                        description = "The full content to write to the file"
                    )
                ),
                required = listOf("path", "content")
            )
        )
    )

    val LIST_FILES = Tool(
        type = "function",
        function = ToolFunction(
            name = "list_files",
            description = "List all files and folders in a directory. Use this to explore the project structure.",
            parameters = ToolParameters(
                type = "object",
                properties = mapOf(
                    "path" to ToolParameter(
                        type = "string",
                        description = "The relative path to the directory. Use empty string '' or '.' for root"
                    )
                ),
                required = listOf("path")
            )
        )
    )

    val DELETE_FILE = Tool(
        type = "function",
        function = ToolFunction(
            name = "delete_file",
            description = "Delete a file from the project. Use with caution.",
            parameters = ToolParameters(
                type = "object",
                properties = mapOf(
                    "path" to ToolParameter(
                        type = "string",
                        description = "The relative path to the file to delete"
                    )
                ),
                required = listOf("path")
            )
        )
    )

    val CREATE_FOLDER = Tool(
        type = "function",
        function = ToolFunction(
            name = "create_folder",
            description = "Create a new folder in the project.",
            parameters = ToolParameters(
                type = "object",
                properties = mapOf(
                    "path" to ToolParameter(
                        type = "string",
                        description = "The relative path for the new folder"
                    )
                ),
                required = listOf("path")
            )
        )
    )

    val RENAME_FILE = Tool(
        type = "function",
        function = ToolFunction(
            name = "rename_file",
            description = "Rename or move a file to a new location.",
            parameters = ToolParameters(
                type = "object",
                properties = mapOf(
                    "old_path" to ToolParameter(
                        type = "string",
                        description = "The current path of the file"
                    ),
                    "new_path" to ToolParameter(
                        type = "string",
                        description = "The new path for the file"
                    )
                ),
                required = listOf("old_path", "new_path")
            )
        )
    )

    // Memory tools
    val SAVE_MEMORY = Tool(
        type = "function",
        function = ToolFunction(
            name = "save_memory",
            description = "Save information about the project for future reference. Use this to remember important patterns, decisions, or learnings.",
            parameters = ToolParameters(
                type = "object",
                properties = mapOf(
                    "key" to ToolParameter(
                        type = "string",
                        description = "A unique key to identify this memory (e.g., 'color_scheme', 'nav_pattern')"
                    ),
                    "value" to ToolParameter(
                        type = "string",
                        description = "The information to remember"
                    )
                ),
                required = listOf("key", "value")
            )
        )
    )

    val RECALL_MEMORY = Tool(
        type = "function",
        function = ToolFunction(
            name = "recall_memory",
            description = "Retrieve previously saved information about the project.",
            parameters = ToolParameters(
                type = "object",
                properties = mapOf(
                    "key" to ToolParameter(
                        type = "string",
                        description = "The key of the memory to retrieve"
                    )
                ),
                required = listOf("key")
            )
        )
    )

    val LIST_MEMORIES = Tool(
        type = "function",
        function = ToolFunction(
            name = "list_memories",
            description = "List all saved memories for this project.",
            parameters = ToolParameters(
                type = "object",
                properties = emptyMap(),
                required = emptyList()
            )
        )
    )

    // Search tool
    val SEARCH_FILES = Tool(
        type = "function",
        function = ToolFunction(
            name = "search_files",
            description = "Search for text content across all files in the project.",
            parameters = ToolParameters(
                type = "object",
                properties = mapOf(
                    "query" to ToolParameter(
                        type = "string",
                        description = "The text to search for"
                    ),
                    "file_pattern" to ToolParameter(
                        type = "string",
                        description = "Optional file pattern to filter (e.g., '*.html', '*.css'). Leave empty to search all files."
                    )
                ),
                required = listOf("query")
            )
        )
    )

    /**
     * Get all tools for agent mode
     */
    fun getAllTools(): List<Tool> = listOf(
        READ_FILE,
        WRITE_FILE,
        LIST_FILES,
        DELETE_FILE,
        CREATE_FOLDER,
        RENAME_FILE,
        SAVE_MEMORY,
        RECALL_MEMORY,
        LIST_MEMORIES,
        SEARCH_FILES
    )

    /**
     * Get basic tools for non-agent mode (just read access)
     */
    fun getBasicTools(): List<Tool> = listOf(
        READ_FILE,
        LIST_FILES
    )
}

/**
 * Helper to extract string argument from tool call
 */
fun JsonObject.getStringArg(key: String): String? {
    return this[key]?.jsonPrimitive?.content
}
