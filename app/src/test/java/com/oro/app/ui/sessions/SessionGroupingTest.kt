package com.oro.app.ui.sessions

import com.google.common.truth.Truth.assertThat
import com.oro.app.domain.model.ChatSession
import com.oro.app.ui.screens.editor.sessions.SessionBucket
import com.oro.app.ui.screens.editor.sessions.SessionGrouping
import com.oro.app.ui.screens.editor.sessions.SessionListEntry
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Test

class SessionGroupingTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val locale = Locale.US
    /** Wednesday 2026-10-14, 15:00 UTC. */
    private val now = at(2026, 10, 14, 15, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun session(id: String, updatedAt: Long, pinned: Boolean = false, title: String = id, preview: String? = null) =
        ChatSession(
            id = id,
            projectId = "p",
            title = title,
            createdAt = updatedAt,
            updatedAt = updatedAt,
            lastOpenedAt = updatedAt,
            isPinned = pinned,
            messageCount = 2,
            preview = preview
        )

    private fun headers(entries: List<SessionListEntry>) =
        entries.filterIsInstance<SessionListEntry.Header>().map { it.bucket }

    private fun ids(entries: List<SessionListEntry>) =
        entries.filterIsInstance<SessionListEntry.Row>().map { it.session.id }

    @Test
    fun `sessions fall into recency buckets by calendar day, pinned first`() {
        val sessions = listOf(
            session("pinned-old", at(2025, 1, 1, 9, 0), pinned = true),
            session("today", at(2026, 10, 14, 0, 5)),
            session("yesterday-late", at(2026, 10, 13, 23, 59)),
            session("six-days", at(2026, 10, 8, 12, 0)),
            session("seven-days", at(2026, 10, 7, 12, 0)),
            session("eight-days", at(2026, 10, 6, 12, 0))
        )
        val entries = SessionGrouping.entries(sessions, "", now, zone, locale)

        assertThat(headers(entries)).containsExactly(
            SessionBucket.PINNED,
            SessionBucket.TODAY,
            SessionBucket.YESTERDAY,
            SessionBucket.PREVIOUS_7_DAYS,
            SessionBucket.OLDER
        ).inOrder()
        assertThat(ids(entries)).containsExactly(
            "pinned-old", "today", "yesterday-late", "six-days", "seven-days", "eight-days"
        ).inOrder()
    }

    @Test
    fun `empty buckets get no header`() {
        val entries = SessionGrouping.entries(listOf(session("a", now - 60_000)), "", now, zone, locale)
        assertThat(headers(entries)).containsExactly(SessionBucket.TODAY)
    }

    @Test
    fun `a timestamp in the future counts as today`() {
        val entries = SessionGrouping.entries(listOf(session("skew", now + 86_400_000L)), "", now, zone, locale)
        assertThat(headers(entries)).containsExactly(SessionBucket.TODAY)
    }

    @Test
    fun `search matches title or preview, case-insensitively`() {
        val sessions = listOf(
            session("a", now, title = "Navbar fixes"),
            session("b", now, title = "Something else", preview = "the NAVBAR is broken on iOS"),
            session("c", now, title = "Footer")
        )
        val entries = SessionGrouping.entries(sessions, "  navbar ", now, zone, locale)
        assertThat(ids(entries)).containsExactly("a", "b").inOrder()
        assertThat(SessionGrouping.entries(sessions, "zzz", now, zone, locale)).isEmpty()
    }

    @Test
    fun `entry keys are unique so LazyColumn keys never collide`() {
        val sessions = (1..20).map { session("s$it", now - it * 86_400_000L, pinned = it % 5 == 0) }
        val keys = SessionGrouping.entries(sessions, "", now, zone, locale).map { it.key }
        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun `relative time is compact`() {
        fun rel(t: Long) = SessionGrouping.relativeTime(t, now, zone, locale)
        assertThat(rel(now - 10_000)).isEqualTo("now")
        assertThat(rel(now - 5 * 60_000)).isEqualTo("5m")
        assertThat(rel(at(2026, 10, 14, 11, 30))).isEqualTo("3h")
        assertThat(rel(at(2026, 10, 13, 22, 0))).isEqualTo("Yesterday")
        assertThat(rel(at(2026, 10, 10, 9, 0))).isEqualTo("Sat")
        assertThat(rel(at(2026, 3, 4, 9, 0))).isEqualTo("Mar 4")
        assertThat(rel(at(2024, 3, 4, 9, 0))).isEqualTo("Mar 4, 2024")
    }

    @Test
    fun `message counts read naturally`() {
        assertThat(SessionGrouping.messageCountLabel(1)).isEqualTo("1 message")
        assertThat(SessionGrouping.messageCountLabel(0)).isEqualTo("0 messages")
        assertThat(SessionGrouping.messageCountLabel(12)).isEqualTo("12 messages")
    }
}
