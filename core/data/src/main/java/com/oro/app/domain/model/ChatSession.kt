package com.oro.app.domain.model

import com.oro.app.data.local.entity.ChatSessionEntity
import com.oro.app.data.local.entity.ChatSessionWithStats
import com.oro.app.data.local.entity.ChatMessageEntity

/** One conversation thread in a project, as the session list shows it. */
data class ChatSession(
    val id: String,
    val projectId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long,
    val isPinned: Boolean,
    val messageCount: Int = 0,
    /** The latest thing the user asked, one line; null for an empty session. */
    val preview: String? = null
) {
    companion object {
        fun fromEntity(row: ChatSessionWithStats): ChatSession = ChatSession(
            id = row.session.id,
            projectId = row.session.projectId,
            title = row.session.title,
            createdAt = row.session.createdAt,
            updatedAt = row.session.updatedAt,
            lastOpenedAt = row.session.lastOpenedAt,
            isPinned = row.session.isPinned,
            messageCount = row.messageCount,
            preview = row.lastUserMessage?.let(SessionTitles::oneLine)?.takeIf { it.isNotEmpty() }
        )
    }
}

/**
 * A deleted session, kept in memory long enough to undo the delete.
 *
 * Deleting is immediate (the cascade removes the messages in the same
 * statement) and undo re-inserts from this, which keeps the database the only
 * source of truth: there is no "pending delete" state for a crash or a
 * navigation to strand half-applied.
 */
class DeletedSessionSnapshot internal constructor(
    internal val session: ChatSessionEntity,
    internal val messages: List<ChatMessageEntity>
) {
    val id: String get() = session.id
    val title: String get() = session.title
}

/**
 * Session titles derived from the first user message.
 *
 * Deliberately a heuristic and not a model call: the title must exist the
 * instant the first message is sent (the session list and the switcher show
 * it immediately), it must work with no network and no model selected, and a
 * request per new chat would cost the user tokens for a label.
 */
object SessionTitles {
    const val DEFAULT_TITLE = "New chat"
    const val MAX_LENGTH = 48

    /** Pleasantries that make a poor title when they lead the message. */
    private val leadingFiller = Regex(
        "^(?:(?:hey|hi|hello|yo|ok(?:ay)?|so|please|pls|can you|could you|would you|i want you to|i'd like you to|i need you to)[\\s,!.:-]+)+",
        RegexOption.IGNORE_CASE
    )
    private val whitespace = Regex("\\s+")
    private val markdownNoise = Regex("[`*#>~]+")
    /** An `@path/to/file` mention is noise in a title; the file name alone is kept. */
    private val mention = Regex("@(?:[\\w.-]+/)*([\\w.-]+)")

    /** Collapse a message to a single trimmed line. */
    fun oneLine(text: String): String = whitespace.replace(text, " ").trim()

    /**
     * A short title for a session whose first user message is [message].
     *
     * Takes the first line (a pasted stack trace or file should not become
     * the title), strips markdown and leading filler, capitalises, and cuts
     * at a word boundary with an ellipsis.
     */
    fun fromFirstMessage(message: String?): String {
        if (message.isNullOrBlank()) return DEFAULT_TITLE
        val firstLine = message.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: return DEFAULT_TITLE
        var text = mention.replace(firstLine) { it.groupValues[1] }
        text = markdownNoise.replace(text, "")
        text = oneLine(text)
        val withoutFiller = leadingFiller.replace(text, "").trim()
        // A message that is nothing but filler ("hi") is still a better
        // title than "New chat": it is what the user actually typed.
        if (withoutFiller.isNotEmpty()) text = withoutFiller
        text = text.trimEnd('?', '.', '!', ',', ':', ';', ' ')
        if (text.isEmpty()) return DEFAULT_TITLE
        text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return truncate(text, MAX_LENGTH)
    }

    /** A user-entered title: one line, bounded, never blank. */
    fun sanitizeUserTitle(input: String): String? {
        val text = oneLine(input)
        if (text.isEmpty()) return null
        return truncate(text, MAX_USER_LENGTH)
    }

    private const val MAX_USER_LENGTH = 80

    private fun truncate(text: String, max: Int): String {
        if (text.length <= max) return text
        val cut = text.substring(0, max - 1)
        val lastSpace = cut.lastIndexOf(' ')
        // Break on a word only if that keeps most of the budget; a single
        // very long token (a URL, a path) is cut mid-word instead.
        val base = if (lastSpace >= max / 2) cut.substring(0, lastSpace) else cut
        return base.trimEnd(' ', ',', '.', ':', ';', '-') + "…"
    }
}
