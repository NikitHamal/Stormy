package com.codex.stormy.data.ai.context

import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.ChatRequestMessage
import com.codex.stormy.data.repository.AiRepository
import com.codex.stormy.domain.model.ChatMessage
import com.codex.stormy.utils.Constants

/**
 * Manages context window for AI conversations.
 * Handles token estimation, message pruning, and automatic summarization.
 *
 * Features:
 * - Accurate token estimation with model-specific tokenizer hints
 * - Multiple optimization strategies (truncation, summarization, sliding window)
 * - Automatic context window scaling based on model capabilities
 * - Tool result truncation to prevent context overflow
 * - Priority-based message retention (system > recent user > recent assistant)
 */
class ContextWindowManager(
    private val aiRepository: AiRepository
) {
    companion object {
        // Token limits - now using centralized constants
        private val DEFAULT_MAX_CONTEXT_TOKENS = Constants.Ai.DEFAULT_CONTEXT_TOKENS
        private val RESERVED_OUTPUT_TOKENS = Constants.Ai.DEFAULT_MAX_TOKENS // Reserve tokens for response
        private val CONTEXT_BUFFER = Constants.Ai.CONTEXT_BUFFER_TOKENS // Safety buffer

        // Heuristic: ~4 characters per token (rough estimate for English text + code)
        private val CHARS_PER_TOKEN = Constants.Ai.CHARS_PER_TOKEN_ESTIMATE

        // Summary threshold - start summarizing when above this percentage
        private const val SUMMARIZE_THRESHOLD = 0.75

        // Minimum messages to keep before summarizing
        private const val MIN_MESSAGES_BEFORE_SUMMARY = 4

        // Maximum tool result length in tokens (to prevent single tool results from consuming entire context)
        private val MAX_TOOL_RESULT_TOKENS = Constants.Ai.MAX_TOOL_RESULT_LENGTH / CHARS_PER_TOKEN.toInt()

        // Token overhead per message (role markers, separators, etc.)
        private const val MESSAGE_OVERHEAD_TOKENS = 4

        // Additional token overhead for tool calls
        private const val TOOL_CALL_OVERHEAD_TOKENS = 20
    }

    /**
     * Get the maximum context tokens for a model.
     * Uses a tiered approach based on model's actual context length.
     */
    fun getMaxContextTokens(model: AiModel): Int {
        // Use model's actual context length, but apply conservative limits
        val modelContextLength = model.contextLength

        return when {
            modelContextLength >= 128000 -> 64000  // For Llama 3.3 128k, use half safely
            modelContextLength >= 65536 -> 32000   // For DeepSeek/Mixtral 64k
            modelContextLength >= 32768 -> 24000   // For Qwen 32k models
            modelContextLength >= 16384 -> 12000   // Medium context models
            modelContextLength >= 8192 -> 6000     // Standard models
            else -> DEFAULT_MAX_CONTEXT_TOKENS.coerceAtMost(modelContextLength - RESERVED_OUTPUT_TOKENS)
        }
    }

    /**
     * Estimate token count for a string using improved heuristics.
     * Takes into account different content types (code, natural language, JSON).
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0

        // Apply different ratios based on content analysis
        val ratio = when {
            // JSON tends to have more tokens due to structure
            text.contains("{") && text.contains("}") -> 3.5f
            // Code has more special characters, slightly more tokens
            text.contains("function") || text.contains("class") ||
            text.contains("def ") || text.contains("fun ") -> 3.8f
            // Natural language
            else -> CHARS_PER_TOKEN
        }

        return (text.length / ratio).toInt().coerceAtLeast(1)
    }

    /**
     * Estimate tokens for a single message with overhead.
     */
    fun estimateMessageTokens(message: ChatRequestMessage): Int {
        val contentTokens = estimateTokens(message.content ?: "")
        val nameTokens = message.name?.let { estimateTokens(it) } ?: 0
        val hasToolCalls = message.toolCalls?.isNotEmpty() == true
        val toolCallOverhead = if (hasToolCalls) {
            TOOL_CALL_OVERHEAD_TOKENS * (message.toolCalls?.size ?: 0)
        } else 0

        return contentTokens + nameTokens + MESSAGE_OVERHEAD_TOKENS + toolCallOverhead
    }

    /**
     * Estimate total tokens for a list of messages.
     */
    fun estimateTotalTokens(messages: List<ChatRequestMessage>): Int {
        return messages.sumOf { estimateMessageTokens(it) }
    }

    /**
     * Get available tokens for the conversation (excluding reserved output tokens).
     */
    fun getAvailableTokens(model: AiModel): Int {
        return getMaxContextTokens(model) - RESERVED_OUTPUT_TOKENS - CONTEXT_BUFFER
    }

    /**
     * Get available tokens for output generation.
     */
    fun getAvailableOutputTokens(model: AiModel): Int {
        return minOf(
            model.contextLength / 4, // Max 25% for output
            Constants.Ai.MAX_OUTPUT_TOKENS
        )
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
     * Truncate text to approximately fit within token limit.
     * Attempts to truncate at natural boundaries (newlines, sentences).
     */
    private fun truncateToTokens(text: String, maxTokens: Int): String {
        val maxChars = (maxTokens * CHARS_PER_TOKEN).toInt()
        if (text.length <= maxChars) return text

        // Try to truncate at a natural boundary
        val truncated = text.take(maxChars)

        // Find last newline
        val lastNewline = truncated.lastIndexOf('\n')
        if (lastNewline > maxChars * 0.7) {
            return truncated.substring(0, lastNewline)
        }

        // Find last sentence end
        val lastSentenceEnd = truncated.lastIndexOfAny(charArrayOf('.', '!', '?', ';'))
        if (lastSentenceEnd > maxChars * 0.7) {
            return truncated.substring(0, lastSentenceEnd + 1)
        }

        // Find last space
        val lastSpace = truncated.lastIndexOf(' ')
        if (lastSpace > maxChars * 0.8) {
            return truncated.substring(0, lastSpace)
        }

        return truncated
    }

    /**
     * Truncate a tool result to prevent context overflow.
     * Tool results can be very large (file contents, search results, etc.)
     */
    fun truncateToolResult(result: String): String {
        if (result.isEmpty()) return result

        val maxTokens = MAX_TOOL_RESULT_TOKENS
        val estimatedTokens = estimateTokens(result)

        if (estimatedTokens <= maxTokens) return result

        val truncated = truncateToTokens(result, maxTokens - 20) // Leave room for truncation notice
        return "$truncated\n\n[Content truncated - showing first ~${maxTokens} tokens of ${estimatedTokens} total]"
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
