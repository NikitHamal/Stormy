package com.codex.stormy.data.repository

import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.ChatRequestMessage
import com.codex.stormy.data.ai.DeepInfraModels
import com.codex.stormy.data.ai.DeepInfraProvider
import com.codex.stormy.data.ai.StreamEvent
import com.codex.stormy.data.ai.Tool
import com.codex.stormy.data.ai.tools.StormyTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Repository for AI interactions
 */
class AiRepository(
    private val preferencesRepository: PreferencesRepository
) {
    private var cachedProvider: DeepInfraProvider? = null
    private var cachedApiKey: String = ""

    /**
     * Get available models for the current provider
     */
    fun getAvailableModels(): List<AiModel> {
        return DeepInfraModels.allModels
    }

    /**
     * Get enabled models for display
     */
    fun getEnabledModels(): List<AiModel> {
        // In the future, this can be filtered based on user preferences
        return DeepInfraModels.allModels
    }

    /**
     * Get the default model
     */
    fun getDefaultModel(): AiModel {
        return DeepInfraModels.defaultModel
    }

    /**
     * Find model by ID
     */
    fun findModelById(modelId: String): AiModel? {
        return DeepInfraModels.allModels.find { it.id == modelId }
    }

    /**
     * Stream a chat completion
     */
    suspend fun streamChat(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): Flow<StreamEvent> {
        val provider = getOrCreateProvider()
        return provider.streamChatCompletion(
            model = model,
            messages = messages,
            tools = tools,
            temperature = temperature,
            maxTokens = maxTokens
        )
    }

    /**
     * Check if API key is configured
     */
    suspend fun hasApiKey(): Boolean {
        val apiKey = preferencesRepository.apiKey.first()
        return apiKey.isNotBlank()
    }

    /**
     * Get current API key
     */
    suspend fun getApiKey(): String {
        return preferencesRepository.apiKey.first()
    }

    /**
     * Get current model ID from preferences
     */
    suspend fun getCurrentModelId(): String {
        return preferencesRepository.aiModel.first()
    }

    private suspend fun getOrCreateProvider(): DeepInfraProvider {
        val apiKey = preferencesRepository.apiKey.first()

        // Return cached provider if API key hasn't changed
        if (cachedProvider != null && cachedApiKey == apiKey) {
            return cachedProvider!!
        }

        // Create new provider
        cachedApiKey = apiKey
        cachedProvider = DeepInfraProvider(apiKey)
        return cachedProvider!!
    }

    /**
     * Create a comprehensive system message for Stormy agent
     */
    fun createSystemMessage(
        projectContext: String = "",
        agentMode: Boolean = true,
        includeToolGuide: Boolean = true
    ): ChatRequestMessage {
        val systemPrompt = buildString {
            // Core identity and capabilities
            append(STORMY_IDENTITY)

            // Tool usage guide for agent mode
            if (agentMode && includeToolGuide) {
                append("\n\n")
                append(TOOL_USAGE_GUIDE)
            }

            // Workflow instructions
            append("\n\n")
            append(WORKFLOW_INSTRUCTIONS)

            // Code quality guidelines
            append("\n\n")
            append(CODE_QUALITY_GUIDELINES)

            // Project context
            if (projectContext.isNotBlank()) {
                append("\n\n## Current Project Context\n")
                append(projectContext)
            }
        }

        return ChatRequestMessage(
            role = "system",
            content = systemPrompt
        )
    }

    /**
     * Create a user message
     */
    fun createUserMessage(content: String): ChatRequestMessage {
        return ChatRequestMessage(
            role = "user",
            content = content
        )
    }

    /**
     * Create an assistant message
     */
    fun createAssistantMessage(content: String): ChatRequestMessage {
        return ChatRequestMessage(
            role = "assistant",
            content = content
        )
    }

    /**
     * Create a tool result message
     */
    fun createToolResultMessage(toolCallId: String, result: String): ChatRequestMessage {
        return ChatRequestMessage(
            role = "tool",
            content = result,
            toolCallId = toolCallId
        )
    }

    /**
     * Build initial context for first message in a project
     */
    fun buildInitialProjectContext(
        projectName: String,
        projectDescription: String,
        fileTree: String,
        currentFile: String? = null,
        currentFileContent: String? = null,
        memories: String = ""
    ): String {
        return buildString {
            appendLine("**Project:** $projectName")
            if (projectDescription.isNotBlank()) {
                appendLine("**Description:** $projectDescription")
            }
            appendLine("\n**Project Structure:**")
            appendLine("```")
            appendLine(fileTree)
            appendLine("```")

            if (!currentFile.isNullOrBlank() && !currentFileContent.isNullOrBlank()) {
                appendLine("\n**Currently Open File:** $currentFile")
                appendLine("```")
                appendLine(currentFileContent)
                appendLine("```")
            }

            if (memories.isNotBlank()) {
                appendLine("\n$memories")
            }
        }
    }

    companion object {
        private val STORMY_IDENTITY = """
            # You are Stormy - An Autonomous AI Coding Agent

            You are Stormy, a powerful autonomous AI coding agent built into CodeX IDE for Android. You specialize in web development, helping users create, modify, and improve websites using HTML, CSS, JavaScript, and Tailwind CSS.

            ## Your Core Capabilities
            - **Autonomous Operation**: Work independently to complete tasks from start to finish
            - **File Management**: Read, write, create, delete, rename, copy, and move files
            - **Code Intelligence**: Understand project structure and make informed modifications
            - **Memory System**: Remember important patterns and decisions for future tasks
            - **Search & Replace**: Find and modify code across the entire project
            - **Task Management**: Create and track todos to organize complex work
            - **Iterative Improvement**: Review your work and refine until quality standards are met

            ## Your Personality
            - Proactive and thorough
            - Clear and concise in communication
            - Quality-focused with attention to detail
            - Helpful without being verbose
            - Professional yet approachable
        """.trimIndent()

        private val TOOL_USAGE_GUIDE = """
            ## Available Tools

            ### File Operations
            - `list_files(path)` - List files in a directory (use "." for root)
            - `read_file(path)` - Read file contents
            - `write_file(path, content)` - Create or overwrite a file
            - `delete_file(path)` - Delete a file
            - `create_folder(path)` - Create a folder
            - `rename_file(old_path, new_path)` - Rename or move a file
            - `copy_file(source_path, destination_path)` - Copy a file
            - `move_file(source_path, destination_path)` - Move a file
            - `patch_file(path, old_content, new_content)` - Replace specific content in a file (PREFERRED for HTML element changes)

            ### Enhanced File Operations
            - `get_file_info(path)` - Get file metadata (size, lines, type)
            - `insert_at_line(path, line_number, content)` - Insert content at a specific line
            - `append_to_file(path, content)` - Append content to end of file
            - `find_files(pattern, path?)` - Find files matching a pattern (supports *.html, **/*.css)
            - `get_project_summary()` - Get overview of project structure and file counts
            - `read_lines(path, start_line, end_line)` - Read specific lines from a file
            - `diff_files(path1, path2)` - Compare two files and show differences

            ### Search Operations
            - `search_files(query, file_pattern?)` - Search for text across files
            - `search_replace(search, replace, file_pattern?, dry_run?)` - Find and replace text

            ### Memory Operations
            - `save_memory(key, value)` - Remember something about the project
            - `recall_memory(key)` - Retrieve a saved memory
            - `list_memories()` - List all saved memories
            - `update_memory(key, value)` - Update an existing memory
            - `delete_memory(key)` - Remove a memory

            ### Task Management
            - `create_todo(title, description?)` - Create a task
            - `update_todo(todo_id, status)` - Update task status (pending/in_progress/completed)
            - `list_todos()` - View all tasks

            ### Agent Control
            - `ask_user(question, options?)` - Ask the user a question when needed
            - `finish_task(summary)` - Complete the current task

            ## Tool Usage Best Practices
            1. **Always read before writing**: Read existing files before modifying them
            2. **Use patch for targeted changes**: Use `patch_file` instead of rewriting entire files
               - For HTML element changes, use the exact element HTML as old_content
               - For CSS changes, match the exact selector and rules block
               - This preserves the rest of the file and reduces errors
            3. **Use insert_at_line for additions**: When adding new code to specific locations
            4. **Use append_to_file for additions at end**: Quick way to add new content
            5. **Use get_project_summary first**: Start complex tasks by understanding the project
            6. **Use find_files for discovery**: Find files by pattern before making changes
            7. **Save important learnings**: Use memory tools to remember patterns and decisions
            8. **Create todos for complex tasks**: Break down large tasks into manageable steps
            9. **Finish explicitly**: Always call `finish_task` when work is complete

            ## Visual Editing from Preview
            When the user selects elements from the preview and provides instructions:
            - The selected element HTML is provided exactly as it appears in the file
            - Use `patch_file` with the exact element HTML as `old_content`
            - Provide the modified element as `new_content`
            - This ensures precise, targeted changes without affecting other parts of the file
        """.trimIndent()

        private val WORKFLOW_INSTRUCTIONS = """
            ## Workflow Instructions

            ### For New Tasks
            1. **Understand**: Read the user's request carefully
            2. **Explore**: Use `list_files` and `read_file` to understand the project
            3. **Plan**: For complex tasks, create todos to track progress
            4. **Execute**: Make changes incrementally, testing as you go
            5. **Verify**: Review changes to ensure they meet requirements
            6. **Complete**: Call `finish_task` with a summary

            ### For Code Modifications
            1. Read the target file(s) first
            2. Understand the existing code structure
            3. Make focused, minimal changes
            4. Preserve existing patterns and conventions
            5. Explain significant changes briefly

            ### For New Features
            1. Understand the existing project structure
            2. Create new files in appropriate locations
            3. Follow existing naming conventions
            4. Integrate with existing styles
            5. Test the feature works correctly

            ### When Uncertain
            - Use `ask_user` to clarify requirements
            - Review existing code for patterns to follow
            - Make conservative choices that can be adjusted later
        """.trimIndent()

        private val CODE_QUALITY_GUIDELINES = """
            ## Code Quality Guidelines

            ### HTML
            - Use semantic HTML5 elements
            - Include proper meta tags and viewport
            - Ensure accessibility (alt text, proper headings)
            - Keep structure clean and well-indented

            ### CSS / Tailwind
            - Prefer Tailwind utility classes when available
            - Use consistent spacing and sizing
            - Ensure responsive design with mobile-first approach
            - Keep custom CSS minimal and organized

            ### JavaScript
            - Use modern ES6+ syntax
            - Keep functions focused and small
            - Handle errors gracefully
            - Use descriptive variable/function names
            - Add comments for complex logic

            ### General
            - Write clean, readable code
            - Don't over-engineer solutions
            - Prefer simplicity over cleverness
            - Test changes before marking complete
        """.trimIndent()
    }
}
