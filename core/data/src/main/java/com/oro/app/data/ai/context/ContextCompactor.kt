package com.oro.app.data.ai.context

import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.domain.model.ChatMessage

/**
 * Replaces the older part of a conversation with an anchored summary of it.
 *
 * ### Why not just drop the oldest messages
 *
 * Truncation is cheap and wrong for coding work: the decisions that constrain
 * everything after them are made *early* ("use Tailwind", "the API lives at
 * /api/v2"), so the oldest messages are often the most expensive to lose.
 * Summarising keeps those constraints at a fraction of the tokens.
 *
 * ### Prompts
 *
 * The system prompt and the output template are OpenCode's, used verbatim --
 * see [SYSTEM_PROMPT] and [SUMMARY_TEMPLATE]. Two things about that design are
 * worth stating because they are easy to get wrong when writing one from
 * scratch:
 *
 *  - The summary is *anchored*: a previous summary is fed back in a
 *    `<previous-summary>` block and updated in place, rather than summarising
 *    a summary. Repeated re-summarisation compounds loss, and after three or
 *    four rounds the early constraints are gone entirely.
 *  - The output is a fixed Markdown skeleton with every section retained even
 *    when empty. A free-form summary drifts in shape between runs, which makes
 *    the result unusable as stable context.
 *
 * ### What is never compacted
 *
 * The most recent turns stay verbatim, selected by *token budget* rather than
 * message count -- six short messages and six messages containing file dumps
 * are not comparable, and a fixed count silently keeps far too much or far too
 * little. A summary is lossy by construction, and the model needs the exact
 * text of what it just did in order to continue it.
 */
class ContextCompactor(
    private val contextWindowManager: ContextWindowManager
) {

    /** What a compaction run produced. */
    data class Result(
        val summary: String,
        val compactedCount: Int,
        val tokensBefore: Int,
        val tokensAfter: Int
    ) {
        val tokensSaved: Int get() = (tokensBefore - tokensAfter).coerceAtLeast(0)

        val savedPercent: Int
            get() = if (tokensBefore <= 0) 0 else (tokensSaved * 100 / tokensBefore)
    }

    /**
     * Split a conversation into the part to summarise and the part to keep.
     *
     * Walks backwards accumulating tokens until [KEEP_RECENT_TOKENS] is
     * reached, so "recent" means a comparable amount of context regardless of
     * how long the individual messages are.
     */
    fun partition(messages: List<ChatMessage>): Partition {
        if (messages.size <= MIN_RECENT_MESSAGES) {
            return Partition(older = emptyList(), recent = messages)
        }

        var budget = KEEP_RECENT_TOKENS
        var keepFrom = messages.size

        for (index in messages.indices.reversed()) {
            val cost = contextWindowManager.estimateTokens(messages[index].content)
            val kept = messages.size - index
            // Always keep a minimum number of turns even if one of them is
            // huge, otherwise a single large message consumes the whole budget
            // and the model is left with no recent exchange at all.
            if (budget - cost < 0 && kept > MIN_RECENT_MESSAGES) break
            budget -= cost
            keepFrom = index
        }

        return Partition(
            older = messages.take(keepFrom),
            recent = messages.drop(keepFrom)
        )
    }

    data class Partition(
        val older: List<ChatMessage>,
        val recent: List<ChatMessage>
    ) {
        val canCompact: Boolean get() = older.isNotEmpty()
    }

    /**
     * Build the request that asks a model to produce the summary.
     *
     * Holds no provider, so the whole policy is testable without a network;
     * the caller runs it.
     *
     * @param previousSummary the summary from an earlier compaction, if any.
     *   Passing it keeps the result anchored instead of a summary of a summary.
     */
    fun buildSummaryRequest(
        older: List<ChatMessage>,
        previousSummary: String? = null
    ): List<ChatRequestMessage> = listOf(
        ChatRequestMessage(role = "system", content = SYSTEM_PROMPT),
        ChatRequestMessage(
            role = "user",
            content = buildString {
                previousSummary?.takeIf { it.isNotBlank() }?.let { previous ->
                    appendLine("<previous-summary>")
                    appendLine(previous.trim())
                    appendLine("</previous-summary>")
                    appendLine()
                }

                appendLine("<conversation>")
                older.forEach { message ->
                    appendLine(if (message.isUser) "USER:" else "ASSISTANT:")
                    // Capped per message: a transcript containing a 40k-token
                    // file dump would otherwise overflow the very window being
                    // compacted.
                    appendLine(message.content.take(TOOL_OUTPUT_MAX_CHARS))
                    appendLine()
                }
                appendLine("</conversation>")
                appendLine()
                append(SUMMARY_TEMPLATE)
            }
        )
    )

    /**
     * Fold a completed summary back into the conversation.
     *
     * Injected as a *system* message rather than an assistant turn, so the
     * model treats it as established context rather than as something it said
     * and might argue with.
     *
     * @return the compacted request messages and a report, or null when there
     *   was nothing to compact or the model returned nothing usable.
     */
    fun applySummary(
        messages: List<ChatMessage>,
        summary: String
    ): Pair<List<ChatRequestMessage>, Result>? {
        val partition = partition(messages)
        if (!partition.canCompact) return null

        val cleanSummary = summary.trim().takeIf { it.isNotEmpty() } ?: return null

        val before = contextWindowManager.estimateTotalTokens(messages.toRequestMessages())
        val compacted = buildList {
            add(
                ChatRequestMessage(
                    role = "system",
                    content = "$SUMMARY_MARKER\n\n$cleanSummary"
                )
            )
            addAll(partition.recent.toRequestMessages())
        }
        val after = contextWindowManager.estimateTotalTokens(compacted)

        return compacted to Result(
            summary = cleanSummary,
            compactedCount = partition.older.size,
            tokensBefore = before,
            tokensAfter = after
        )
    }

    /** Recover a prior summary from a compacted message list, for anchoring. */
    fun extractPreviousSummary(messages: List<ChatRequestMessage>): String? =
        messages.firstOrNull { it.role == "system" && it.content?.startsWith(SUMMARY_MARKER) == true }
            ?.content
            ?.removePrefix(SUMMARY_MARKER)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun List<ChatMessage>.toRequestMessages(): List<ChatRequestMessage> = map { message ->
        ChatRequestMessage(
            role = if (message.isUser) "user" else "assistant",
            content = message.content
        )
    }

    companion object {
        /**
         * OpenCode's compaction system prompt, verbatim.
         * https://github.com/sst/opencode -- packages/opencode/src/agent/prompt/compaction.txt
         */
        const val SYSTEM_PROMPT =
            "You are an anchored context summarization assistant for coding sessions.\n" +
                "\n" +
                "Summarize only the conversation history you are given. The newest turns may be " +
                "kept verbatim outside your summary, so focus on the older context that still " +
                "matters for continuing the work.\n" +
                "\n" +
                "If the prompt includes a <previous-summary> block, treat it as the current " +
                "anchored summary. Update it with the new history by preserving still-true " +
                "details, removing stale details, and merging in new facts.\n" +
                "\n" +
                "Always follow the exact output structure requested by the user prompt. Keep " +
                "every section, preserve exact file paths and identifiers when known, and prefer " +
                "terse bullets over paragraphs.\n" +
                "\n" +
                "Do not answer the conversation itself. Do not mention that you are summarizing, " +
                "compacting, or merging context. Respond in the same language as the conversation."

        /**
         * OpenCode's output template, verbatim.
         * The fixed skeleton is the point: a free-form summary changes shape
         * between runs, which makes it unusable as stable context.
         */
        const val SUMMARY_TEMPLATE =
            "Output exactly the Markdown structure shown inside <template> and keep the section " +
                "order unchanged. Do not include the <template> tags in your response.\n" +
                "<template>\n" +
                "## Goal\n" +
                "- [single-sentence task summary]\n" +
                "\n" +
                "## Constraints & Preferences\n" +
                "- [user constraints, preferences, specs, or \"(none)\"]\n" +
                "\n" +
                "## Progress\n" +
                "### Done\n" +
                "- [completed work or \"(none)\"]\n" +
                "\n" +
                "### In Progress\n" +
                "- [current work or \"(none)\"]\n" +
                "\n" +
                "### Blocked\n" +
                "- [blockers or \"(none)\"]\n" +
                "\n" +
                "## Key Decisions\n" +
                "- [decision and why, or \"(none)\"]\n" +
                "\n" +
                "## Next Steps\n" +
                "- [ordered next actions or \"(none)\"]\n" +
                "\n" +
                "## Critical Context\n" +
                "- [important technical facts, errors, open questions, or \"(none)\"]\n" +
                "\n" +
                "## Relevant Files\n" +
                "- [file or directory path: why it matters, or \"(none)\"]\n" +
                "</template>\n" +
                "\n" +
                "Rules:\n" +
                "- Keep every section, even when empty.\n" +
                "- Use terse bullets, not prose paragraphs.\n" +
                "- Preserve exact file paths, commands, error strings, and identifiers when known.\n" +
                "- Do not mention the summary process or that context was compacted."

        /** Marks the injected summary so a later run can anchor onto it. */
        const val SUMMARY_MARKER = "<context-summary>"

        /** Token budget of recent turns kept verbatim. OpenCode's default. */
        const val KEEP_RECENT_TOKENS = 8_000

        /** Per-message cap fed to the summariser. OpenCode's default. */
        const val TOOL_OUTPUT_MAX_CHARS = 2_000

        /** Output cap for the summary itself. OpenCode's default. */
        const val SUMMARY_OUTPUT_TOKENS = 4_096

        /**
         * Turns always kept whatever the token budget says, so one very large
         * message cannot leave the model with no recent exchange.
         */
        const val MIN_RECENT_MESSAGES = 4
    }
}
