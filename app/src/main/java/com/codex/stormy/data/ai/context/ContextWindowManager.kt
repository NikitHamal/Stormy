package com.codex.stormy.data.ai.context

import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.ChatRequestMessage
import com.codex.stormy.data.repository.AiRepository
import com.codex.stormy.domain.model.ChatMessage

/**
 * Manages context window for AI conversations.
 * Handles token estimation, message pruning, and automatic summarization.
 */
class ContextWindowManager(
    private val aiRepository: AiRepository
) {
    companion object {
        // Token limits for different models (conservative estimates for safety margin)
        private const val DEFAULT_MAX_CONTEXT_TOKENS = 8000
        private const val LARGE_MODEL_MAX_TOKENS = 32000
        private const val RESERVED_OUTPUT_TOKENS = 2000 // Reserve tokens for response

        // Heuristic: ~4 characters per token (rough estimate for English text + code)
        private const val CHARS_PER_TOKEN = 4

        // Summary threshold - start summarizing when above this percentage
        private const val SUMMARIZE_THRESHOLD = 0.75

        // Minimum messages to keep before summarizing
        private const val MIN_MESSAGES_BEFORE_SUMMARY = 4
    }

    /**
     * Get the maximum context tokens for a model
     */
    fun getMaxContextTokens(model: AiModel): Int {
        return when {
            model.contextWindow > 32000 -> LARGE_MODEL_MAX_TOKENS
            model.contextWindow > 8000 -> 16000
            else -> DEFAULT_MAX_CONTEXT_TOKENS
        }
    }

    /**
     * Estimate token count for a string
     */
    fun estimateTokens(text: String): Int {
        return (text.length / CHARS_PER_TOKEN).coerceAtLeast(1)
    }

    /**
     * Estimate total tokens for a list of messages
     */
    fun estimateTotalTokens(messages: List<ChatRequestMessage>): Int {
        return messages.sumOf { message ->
            val contentTokens = estimateTokens(message.content ?: "")
            val roleTokens = 4 // Overhead for role markers
            contentTokens + roleTokens
        }
    }

    /**
     * Get available tokens for the conversation (excluding reserved output tokens)
     */
    fun getAvailableTokens(model: AiModel): Int {
        return getMaxContextTokens(model) - RESERVED_OUTPUT_TOKENS
    }

    /**
     * Check if context window needs optimization
     */
    fun needsOptimization(messages: List<ChatRequestMessage>, model: AiModel): Boolean {
        val currentTokens = estimateTotalTokens(messages)
        val maxTokens = getAvailableTokens(model)
        val threshold = (maxTokens * SUMMARIZE_THRESHOLD).toInt()

        return currentTokens > threshold && messages.size > MIN_MESSAGES_BEFORE_SUMMARY
    }

    /**
     * Optimize messages to fit within context window.
     * Strategies:
     * 1. Keep most recent messages
     * 2. Truncate very long messages
     * 3. Remove middle messages if needed
     */
    fun optimizeMessages(
        messages: List<ChatRequestMessage>,
        model: AiModel,
        systemMessage: ChatRequestMessage? = null
    ): List<ChatRequestMessage> {
        val maxTokens = getAvailableTokens(model)
        val systemTokens = systemMessage?.let { estimateTokens(it.content ?: "") } ?: 0
        val availableForConversation = maxTokens - systemTokens

        if (messages.isEmpty()) return messages

        val result = mutableListOf<ChatRequestMessage>()
        var usedTokens = 0

        // Strategy: Include messages from most recent to oldest, stop when limit reached
        for (message in messages.reversed()) {
            val messageTokens = estimateTokens(message.content ?: "") + 4

            if (usedTokens + messageTokens <= availableForConversation) {
                result.add(0, message) // Add at beginning to maintain order
                usedTokens += messageTokens
            } else {
                // Try to truncate if it's the last message we can fit partially
                val remainingTokens = availableForConversation - usedTokens
                if (remainingTokens > 100 && message.content != null) {
                    val truncatedContent = truncateToTokens(message.content, remainingTokens - 20)
                    if (truncatedContent.isNotEmpty()) {
                        result.add(0, message.copy(content = "$truncatedContent... [truncated]"))
                    }
                }
                break
            }
        }

        return result
    }

    /**
     * Truncate text to approximately fit within token limit
     */
    private fun truncateToTokens(text: String, maxTokens: Int): String {
        val maxChars = maxTokens * CHARS_PER_TOKEN
        return if (text.length <= maxChars) {
            text
        } else {
            text.take(maxChars)
        }
    }

    /**
     * Create a summary prompt for summarizing older conversation context
     */
    fun createSummaryPrompt(oldMessages: List<ChatMessage>): String {
        val conversation = buildString {
            oldMessages.forEach { message ->
                val role = if (message.isUser) "User" else "Assistant"
                appendLine("$role: ${message.content}")
                appendLine()
            }
        }

        return """Please provide a concise summary of the following conversation that captures:
1. The main topics and tasks discussed
2. Key decisions made or actions taken
3. Any important context that would be helpful for continuing the conversation

Conversation:
$conversation

Summary:"""
    }

    /**
     * Create a context-aware system message that includes conversation summary
     */
    fun createSystemMessageWithSummary(
        baseSystemMessage: String,
        conversationSummary: String?
    ): String {
        return if (conversationSummary != null) {
            """$baseSystemMessage

Previous conversation summary:
$conversationSummary

Continue the conversation based on this context."""
        } else {
            baseSystemMessage
        }
    }

    /**
     * Calculate the current context usage as a percentage
     */
    fun calculateContextUsage(messages: List<ChatRequestMessage>, model: AiModel): Float {
        val currentTokens = estimateTotalTokens(messages)
        val maxTokens = getAvailableTokens(model)
        return (currentTokens.toFloat() / maxTokens).coerceIn(0f, 1f)
    }

    /**
     * Get a human-readable token count string
     */
    fun getTokenCountString(messages: List<ChatRequestMessage>, model: AiModel): String {
        val currentTokens = estimateTotalTokens(messages)
        val maxTokens = getAvailableTokens(model)
        return "$currentTokens / $maxTokens tokens"
    }
}

/**
 * Data class representing context window state
 */
data class ContextWindowState(
    val currentTokens: Int,
    val maxTokens: Int,
    val usagePercentage: Float,
    val needsOptimization: Boolean
) {
    val displayString: String
        get() = "$currentTokens / $maxTokens"

    val usageLevel: ContextUsageLevel
        get() = when {
            usagePercentage < 0.5f -> ContextUsageLevel.LOW
            usagePercentage < 0.75f -> ContextUsageLevel.MEDIUM
            usagePercentage < 0.9f -> ContextUsageLevel.HIGH
            else -> ContextUsageLevel.CRITICAL
        }
}

enum class ContextUsageLevel {
    LOW,      // < 50%
    MEDIUM,   // 50-75%
    HIGH,     // 75-90%
    CRITICAL  // > 90%
}
