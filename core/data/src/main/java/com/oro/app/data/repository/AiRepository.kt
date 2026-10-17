package com.oro.app.data.repository

import com.oro.app.data.ai.ProviderRateLimiter
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.AiModels
import com.oro.app.data.ai.AiProviderManager
import com.oro.app.data.ai.ChatCompletionResponse
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.Tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Repository for AI interactions
 * Routes requests to the selected model provider.
 */
/**
 * Shared across the app so pacing is per-provider, not per-call-site: two
 * screens sending at once must share one budget, since the provider counts
 * them together.
 */
private val rateLimiter = ProviderRateLimiter()

/**
 * Same 4-chars-per-token heuristic the rest of the app uses.
 *
 * Deliberately not routed through ContextWindowManager: that class depends on
 * AiRepository, so using it here would be circular. The constant is shared so
 * the reservation and the context meter cannot disagree.
 */
private fun List<ChatRequestMessage>.estimateTokens(): Int =
    com.oro.app.data.ai.agent.TokenEstimator.estimate(this)

class AiRepository(
    private val preferencesRepository: PreferencesRepository
) {
    /**
     * Get available models
     */
    fun getAvailableModels(): List<AiModel> {
        return AiModels.getAllModels()
    }

    /**
     * Get enabled models for display
     */
    fun getEnabledModels(): List<AiModel> {
        return AiModels.getAllModels()
    }

    /**
     * Get the default model
     */
    fun getDefaultModel(): AiModel {
        return AiModels.getDefaultModel(AiProvider.DEFAULT)
    }

    /**
     * Find model by ID
     */
    fun findModelById(modelId: String): AiModel? {
        return AiModels.findModelById(modelId)
    }

    /**
     * Stream a chat completion with provider routing.
     */
    suspend fun streamChat(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null,
        /** Told when the local free-tier pacer makes this request wait. */
        onRateLimitWait: (suspend () -> Unit)? = null
    ): Flow<StreamEvent> {
        val providerManager = createProviderManager()

        // Pace against the free tier before sending. A 429 arrives part-way
        // through an agent run, after files are already written, and reads as
        // the app breaking rather than as a quota.
        if (rateLimiter.isDailyBudgetSpent(model.provider)) {
            return flow {
                emit(
                    StreamEvent.Error(
                        "${model.provider.displayName}'s free daily limit is used up. " +
                            "Switch provider in the model picker, or try again tomorrow."
                    )
                )
            }
        }
        if (onRateLimitWait != null && rateLimiter.remainingThisMinute(model.provider) == 0) {
            onRateLimitWait()
        }
        rateLimiter.acquire(
            provider = model.provider,
            estimatedTokens = messages.estimateTokens()
        )

        // Get the stream from the selected provider
        val stream = providerManager.streamChatCompletion(
            model = model,
            messages = messages,
            tools = tools,
            temperature = temperature,
            maxTokens = maxTokens,
            // Read from preferences here rather than threaded through every
            // caller: the setting is global, and the provider drops it for
            // models that do not advertise support.
            reasoningEffort = preferencesRepository.reasoningEffort.first()
        )

        return stream ?: flow {
            emit(StreamEvent.Error("Failed to connect to ${model.provider.displayName}. Check network and API key settings."))
        }
    }

    /**
     * Non-streaming chat completion with provider routing.
     */
    suspend fun chat(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): Result<ChatCompletionResponse> {
        val providerManager = createProviderManager()
        rateLimiter.acquire(
            provider = model.provider,
            estimatedTokens = messages.estimateTokens()
        )
        return providerManager.chatCompletion(
            model = model,
            messages = messages,
            tools = tools,
            temperature = temperature,
            maxTokens = maxTokens,
            reasoningEffort = preferencesRepository.reasoningEffort.first()
        )
    }

    suspend fun hasApiKey(): Boolean {
        val selectedProvider = AiProvider.fromString(preferencesRepository.aiProvider.first())
        return !selectedProvider.requiresApiKey ||
            preferencesRepository.getApiKeyForProvider(selectedProvider).isNotBlank()
    }

    /**
     * Get current model ID from preferences
     */
    suspend fun getCurrentModelId(): String {
        return preferencesRepository.aiModel.first()
    }

    private suspend fun createProviderManager(): AiProviderManager {
        return AiProviderManager.fromApiKeys(
            apiKeys = preferencesRepository.apiKeys.first(),
            customBaseUrl = preferencesRepository.customBaseUrl.first(),
            settings = preferencesRepository.providerSettings.first()
        )
    }

    /**
     * Invalidate the cached provider manager
     */
    fun invalidateProviderCache() {
        // Kept for compatibility; managers are now created from latest settings per request.
    }

    /**
     * The system message for a request.
     *
     * @param agentMode whether tools are available at all.
     * @param nativeTools false when the model cannot take tools through the
     *   API; the prompt then teaches the `<tool_call>` text protocol the agent
     *   loop recovers calls from, and lists the tools inline.
     * @param toolList the tools offered, used for the text protocol listing.
     */
    fun createSystemMessage(
        projectContext: String = "",
        agentMode: Boolean = true,
        nativeTools: Boolean = true,
        toolList: List<Tool> = emptyList()
    ): ChatRequestMessage = systemMessage(projectContext, agentMode, nativeTools, toolList)

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
        memories: String = "",
        semanticMemories: String = "",
        userPreferences: String = ""
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

            // Include semantic memories (learned project knowledge)
            if (semanticMemories.isNotBlank()) {
                appendLine(semanticMemories)
            }

            // Include user preferences
            if (userPreferences.isNotBlank()) {
                appendLine(userPreferences)
            }

            // Include basic memories for backwards compatibility
            if (memories.isNotBlank()) {
                appendLine("\n$memories")
            }
        }
    }

    companion object {
        /** The system message; static so tests and tools can build the real prompt. */
        fun systemMessage(
            projectContext: String = "",
            agentMode: Boolean = true,
            nativeTools: Boolean = true,
            toolList: List<Tool> = emptyList()
        ): ChatRequestMessage {
            val systemPrompt = buildString {
                append(ORO_IDENTITY)
                append("\n\n")
                if (agentMode) {
                    append(AGENT_RULES)
                    if (!nativeTools && toolList.isNotEmpty()) {
                        append("\n\n")
                        append(textToolProtocol(toolList))
                    }
                } else {
                    append(CHAT_RULES)
                }
                append("\n\n")
                append(WEB_GUIDELINES)
                if (projectContext.isNotBlank()) {
                    append("\n\n# Project context\n")
                    append(projectContext.trim())
                }
            }
            return ChatRequestMessage(role = "system", content = systemPrompt)
        }

        private fun textToolProtocol(tools: List<Tool>): String =
            com.oro.app.data.ai.agent.TextToolProtocol.render(tools)

        /**
         * Written for current models, in the style of the open harnesses
         * (opencode, Codex CLI, Claude Code): short, imperative, and about
         * judgement rather than about the mechanics of function calling. The
         * previous prompt spent most of its length shouting at weak models to
         * "USE THE TOOL" and listed ~60 tools by hand, which duplicated the
         * schemas, went stale, and cost ~2.5k tokens per request.
         */
        private val ORO_IDENTITY = """
            You are Orobi, the coding agent built into Oro, a web studio on Android. Users build
            websites and web apps -- HTML, CSS, JavaScript and Tailwind -- and see them in Oro's
            live preview. You work directly on the files of the open project.

            Be concise and direct. Write in plain language for someone on a phone: short
            paragraphs, no filler, no restating the request. Use Markdown sparingly.
        """.trimIndent()

        private val AGENT_RULES = """
            # How you work
            - Do the task, don't describe it. Use the tools to read and change files; never paste
              a whole file into chat for the user to copy.
            - Understand before you change: look at the project structure and read the files you
              will touch. Never edit a file you have not read in this conversation.
            - Prefer small, targeted edits with edit_file / multi_edit over rewriting whole files.
              old_string must match the file exactly; if an edit fails, re-read the file and retry
              with the exact text instead of guessing.
            - Match the project's existing structure, naming and style. Don't add frameworks,
              build steps or dependencies the project doesn't already use. There is no node, npm
              or build tooling on the device: load libraries from a CDN when needed.
            - You can call several independent read-only tools at once (read_file, grep, glob).
            - For work with several distinct steps, publish a short plan with update_plan and keep
              it current. Skip it for small tasks.
            - Verify your work: re-read what you changed when unsure, check that links between
              files (paths in <link>, <script>, imports) are correct, and use check_syntax on files
              you wrote. Fix what you find before finishing.
            - Keep going until the task is fully done. If something fails, read the error, adjust
              and try again; don't stop at the first problem.
            - Use ask_user only when genuinely blocked (batch every question into one call): the request is ambiguous in a way that
              changes the outcome, or you need something only the user knows. Don't ask permission
              for ordinary work and don't ask what you can find out by reading the project. For
              destructive steps (deleting files you didn't create, discarding user work) use
              request_approval first.
            - Never claim to have done something you did not do. If you could not finish, say what
              is left and why.

            # Finishing
            When done, reply with a brief summary: what you changed (files), anything the user must
            do next, and any assumptions. A few lines, no code dumps. Don't call a tool to finish --
            just reply.
        """.trimIndent()

        private val CHAT_RULES = """
            # Chat mode
            You cannot change files in this mode. Answer questions, explain code and suggest
            changes. When you show code, show only the relevant part with the file it belongs in.
            If the user wants you to make the change, suggest switching to Agent mode.
        """.trimIndent()

        private val WEB_GUIDELINES = """
            # Web guidelines
            - Semantic HTML5, a viewport meta tag, accessible markup (labels, alt text, headings in
              order, sufficient contrast).
            - Mobile-first, responsive layouts. Most users preview on a phone.
            - With Tailwind, use utility classes and keep custom CSS minimal; without it, keep CSS
              organised and use custom properties for colours and spacing.
            - Modern JavaScript, no inline event-handler attributes, handle errors, keep it small.
            - Relative paths between project files; never reference files that don't exist.
        """.trimIndent()
    }
}
