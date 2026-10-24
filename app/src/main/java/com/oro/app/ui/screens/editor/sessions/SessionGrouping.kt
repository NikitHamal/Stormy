package com.oro.app.ui.screens.editor.sessions

import androidx.compose.runtime.Immutable
import com.oro.app.domain.model.ChatSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The sections of the sessions sheet, in display order. */
enum class SessionBucket(val label: String) {
    PINNED("Pinned"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    PREVIOUS_7_DAYS("Previous 7 days"),
    OLDER("Older")
}

/** One row of the sessions list: a section header or a session. */
@Immutable
sealed interface SessionListEntry {
    val key: String

    @Immutable
    data class Header(val bucket: SessionBucket) : SessionListEntry {
        override val key: String get() = "header:${bucket.name}"
    }

    @Immutable
    data class Row(
        val session: ChatSession,
        /** Pre-formatted, so rows do no date work while scrolling. */
        val relativeTime: String
    ) : SessionListEntry {
        override val key: String get() = session.id
    }
}

/**
 * Turns the session list into sectioned rows.
 *
 * Pure and outside composition: the sheet calls it once per change of list or
 * query (inside `remember`), so scrolling a long history formats no dates and
 * allocates nothing per frame.
 */
object SessionGrouping {

    fun bucketOf(session: ChatSession, today: LocalDate, zone: ZoneId): SessionBucket {
        if (session.isPinned) return SessionBucket.PINNED
        val day = Instant.ofEpochMilli(session.updatedAt).atZone(zone).toLocalDate()
        val daysAgo = ChronoUnit.DAYS.between(day, today)
        return when {
            // <= 0 also catches a clock that has since moved backwards.
            daysAgo <= 0L -> SessionBucket.TODAY
            daysAgo == 1L -> SessionBucket.YESTERDAY
            daysAgo <= 7L -> SessionBucket.PREVIOUS_7_DAYS
            else -> SessionBucket.OLDER
        }
    }

    /**
     * Sessions filtered by [query] (title or latest message, case-insensitive)
     * and grouped under headers. Order within a group is the input order,
     * which is the DAO's most-recently-active first.
     */
    fun entries(
        sessions: List<ChatSession>,
        query: String,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): List<SessionListEntry> {
        val needle = query.trim()
        val matching = if (needle.isEmpty()) {
            sessions
        } else {
            sessions.filter {
                it.title.contains(needle, ignoreCase = true) ||
                    it.preview?.contains(needle, ignoreCase = true) == true
            }
        }
        if (matching.isEmpty()) return emptyList()

        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val grouped = matching.groupBy { bucketOf(it, today, zone) }
        val out = ArrayList<SessionListEntry>(matching.size + grouped.size)
        for (bucket in SessionBucket.entries) {
            val rows = grouped[bucket] ?: continue
            out += SessionListEntry.Header(bucket)
            rows.mapTo(out) { SessionListEntry.Row(it, relativeTime(it.updatedAt, now, zone, locale)) }
        }
        return out
    }

    /**
     * A compact "when": `now`, `5m`, `3h`, `Yesterday`, `Mon`, `Mar 4`, or
     * `Mar 4, 2024`. Short because it sits at the end of a row beside a
     * title that needs the width more.
     */
    fun relativeTime(
        timestamp: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        val elapsed = (now - timestamp).coerceAtLeast(0L)
        val minutes = elapsed / 60_000L
        val then = Instant.ofEpochMilli(timestamp).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val daysAgo = ChronoUnit.DAYS.between(then.toLocalDate(), today)
        return when {
            minutes < 1L -> "now"
            minutes < 60L -> "${minutes}m"
            daysAgo <= 0L -> "${minutes / 60L}h"
            daysAgo == 1L -> "Yesterday"
            daysAgo < 7L -> then.format(DateTimeFormatter.ofPattern("EEE", locale))
            then.year == today.year -> then.format(DateTimeFormatter.ofPattern("MMM d", locale))
            else -> then.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
        }
    }

    /** "1 message", "12 messages". */
    fun messageCountLabel(count: Int): String =
        if (count == 1) "1 message" else "$count messages"
}
