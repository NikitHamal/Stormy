package com.codex.stormy.data.ai.context

import com.codex.stormy.data.ai.ChatRequestMessage
import com.codex.stormy.data.ai.Tool

/**
 * Where the context window is actually going.
 *
 * A single "72% full" number tells the user they have a problem but not what
 * to do about it. The same 72% means different things when it is the tool
 * definitions (nothing to compact, turn tools off or pick a bigger model) and
 * when it is the transcript (compaction will genuinely help). Splitting it is
 * the difference between a warning and an actionable one.
 */
data class ContextSegment(
    val label: String,
    val tokens: Int,
    /** Whether compaction can reduce this segment. */
    val compactable: Boolean = false
)

data class ContextBreakdown(
    val segments: List<ContextSegment> = emptyList(),
    val usedTokens: Int = 0,
    val maxTokens: Int = 0
) {
    val fraction: Float
        get() = if (maxTokens <= 0) 0f else (usedTokens.toFloat() / maxTokens).coerceIn(0f, 1f)

    val percent: Int get() = (fraction * 100).toInt()

    val level: ContextUsageLevel
        get() = when {
            fraction < 0.5f -> ContextUsageLevel.LOW
            fraction < 0.75f -> ContextUsageLevel.MEDIUM
            fraction < 0.9f -> ContextUsageLevel.HIGH
            else -> ContextUsageLevel.CRITICAL
        }

    /** Tokens compaction could plausibly reclaim. */
    val compactableTokens: Int
        get() = segments.filter { it.compactable }.sumOf { it.tokens }

    /**
     * Whether compaction is worth offering.
     *
     * Gated on there being something substantial to reclaim, not merely on the
     * window being full: offering to compact when the transcript is two
     * messages and the weight is all tool definitions wastes the user's time
     * and a model call, and teaches them the button does nothing.
     */
    val canCompact: Boolean
        get() = compactableTokens > COMPACTION_MIN_TOKENS

    /** Share of the window one segment occupies, for the bar. */
    fun shareOf(segment: ContextSegment): Float =
        if (maxTokens <= 0) 0f else (segment.tokens.toFloat() / maxTokens).coerceIn(0f, 1f)

    companion object {
        /**
         * Below this there is nothing worth summarising -- a compaction pass
         * costs a model round trip and would reclaim less than it spends
         * describing what it removed.
         */
        const val COMPACTION_MIN_TOKENS = 1_500

        val Empty = ContextBreakdown()
    }
}

/**
 * Builds a [ContextBreakdown] from the pieces that make up a request.
 *
 * Token counts are estimates, as they are everywhere else in this app -- there
 * is no tokeniser on device and asking the provider would cost a round trip
 * per keystroke. They are consistently derived from the same estimator the
 * send path uses, so the bar and the actual request agree even when both are
 * approximate; a breakdown that disagreed with the thing it describes would be
 * worse than none.
 */
class ContextBreakdownBuilder(private val manager: ContextWindowManager) {

    fun build(
        systemPrompt: String?,
        tools: List<Tool>?,
        messages: List<ChatRequestMessage>,
        maxTokens: Int
    ): ContextBreakdown {
        val systemTokens = systemPrompt?.let { manager.estimateTokens(it) } ?: 0

        // Tool definitions are sent in full on every request, so their cost is
        // paid per turn and is invisible to the user otherwise -- this is
        // usually the surprising row.
        val toolTokens = tools?.sumOf { tool ->
            manager.estimateTokens(tool.function.name) +
                manager.estimateTokens(tool.function.description) +
                manager.estimateTokens(tool.function.parameters.toString())
        } ?: 0

        // The system message is counted above; counting it again here would
        // double it, since it is also present in the message list.
        val conversational = messages.filterNot { it.role == "system" }
        val messageTokens = manager.estimateTotalTokens(conversational)

        val segments = buildList {
            if (systemTokens > 0) add(ContextSegment("System prompt", systemTokens))
            if (toolTokens > 0) add(ContextSegment("Tool definitions", toolTokens))
            if (messageTokens > 0) {
                add(ContextSegment("Conversation", messageTokens, compactable = true))
            }
        }

        return ContextBreakdown(
            segments = segments,
            usedTokens = systemTokens + toolTokens + messageTokens,
            maxTokens = maxTokens
        )
    }
}
