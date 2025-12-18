package com.codex.stormy.data.repository

import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.AiProvider
import com.codex.stormy.data.ai.ChatRequestMessage
import com.codex.stormy.data.ai.DeepInfraModels
import com.codex.stormy.data.ai.DeepInfraProvider
import com.codex.stormy.data.ai.StreamEvent
import com.codex.stormy.data.ai.Tool
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
     * Create a system message for Stormy
     */
    fun createSystemMessage(projectContext: String = ""): ChatRequestMessage {
        val systemPrompt = buildString {
            append("""
                You are Stormy, an AI coding assistant built into CodeX IDE. You help users build websites using HTML, CSS, JavaScript, and Tailwind CSS.

                Your capabilities:
                - Write, edit, and explain code
                - Create complete web pages from descriptions
                - Debug and fix code issues
                - Suggest improvements and best practices
                - Use Tailwind CSS for styling when appropriate

                Guidelines:
                - Be concise but thorough
                - Provide complete, working code examples
                - Explain your changes when modifying existing code
                - Use modern web development practices
                - Be helpful and friendly
            """.trimIndent())

            if (projectContext.isNotBlank()) {
                append("\n\nProject context:\n")
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
}
