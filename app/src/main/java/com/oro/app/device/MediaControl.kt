package com.oro.app.device

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * What is playing, reached through the app's own media session instead of
 * its screen: an exact seek instead of a drag on a bar that hides after a
 * few seconds, and the app that is really playing instead of whichever one
 * the media keys happen to reach.
 *
 * The sessions need Notification access ([OroNotificationListener]), which
 * only the user can grant; without it callers fall back to media keys and the
 * player's own gestures. Parsing, arithmetic and the choice of session are
 * pure, for JVM tests.
 */
object MediaControl {

    /** A position for seek, as written. */
    sealed interface Seek {
        /** A time from the start: "1:30", "90". */
        data class To(val ms: Long) : Seek
        /** A jump from where playback is: "+30", "-10". */
        data class By(val ms: Long) : Seek
        /** A share of the length: "50%". */
        data class Fraction(val f: Double) : Seek
    }

    sealed interface SeekResult {
        data class Done(val text: String) : SeekResult
        /** Notification access is off: no session can be reached. */
        data object NoAccess : SeekResult
        /** No session can do it; [why] says what is missing. */
        data class Unavailable(val why: String) : SeekResult
    }

    /** One active session, for [choose]. */
    data class Candidate(val pkg: String, val playing: Boolean)

    /** What a session is doing, for media(status). */
    data class Status(
        val app: String,
        val title: String?,
        val artist: String?,
        val state: String,
        val positionMs: Long?,
        val durationMs: Long?,
    )

    /** Said wherever Media control would have done better, so the model can tell the user. */
    const val ACCESS_HINT =
        "Exact seeking and media status need Media control: the user can turn it on in Oro's device agent settings."

    // ---------------------------------------------------------------- pure

    private val CLOCK = Regex("""^\d+(:\d{1,2}){1,2}(\.\d+)?$""")
    private val SECONDS = Regex("""^\d+(\.\d+)?$""")
    private val UNIT_WORDS = Regex("""hours?|hrs?|minutes?|mins?|seconds?|secs?""")
    private val UNITS = Regex("""^(?:(\d+(?:\.\d+)?)h)?(?:(\d+(?:\.\d+)?)m)?(?:(\d+(?:\.\d+)?)s)?$""")
    private val PERCENT = Regex("""^(\d+(?:\.\d+)?)\s*%$""")

    /**
     * A time as people and players write it, in ms: "1:30", "1:02:03", "90"
     * (seconds), "90s", "1m30s", "1 minute 30 seconds". Null for anything else.
     */
    fun parseClock(raw: String): Long? {
        val s = raw.trim().lowercase()
        if (s.isEmpty()) return null
        if (CLOCK.matches(s)) {
            val parts = s.split(':')
            val secs = parts.last().toDoubleOrNull() ?: return null
            val mins = parts[parts.size - 2].toLongOrNull() ?: return null
            val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
            // "1:75" is a typo, not a time.
            if (secs >= 60 || (parts.size == 3 && mins >= 60)) return null
            return (hours * 3600 + mins * 60) * 1000 + (secs * 1000).roundToLong()
        }
        if (SECONDS.matches(s)) return (s.toDouble() * 1000).roundToLong()
        val compact = UNIT_WORDS.replace(s) { it.value.take(1) }.replace(Regex("""\band\b|[\s,]"""), "")
        val m = UNITS.matchEntire(compact) ?: return null
        val (h, min, sec) = m.destructured
        if (h.isEmpty() && min.isEmpty() && sec.isEmpty()) return null
        val total = (h.toDoubleOrNull() ?: 0.0) * 3600 + (min.toDoubleOrNull() ?: 0.0) * 60 + (sec.toDoubleOrNull() ?: 0.0)
        return (total * 1000).roundToLong()
    }

    /** "40%" as 40.0; null unless it is a percentage from 0 to 100. */
    fun percent(raw: String): Double? =
        PERCENT.matchEntire(raw.trim())?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }

    /** media(seek)'s position: "1:30" or "90" (from the start), "+30" / "-10" (a jump), "50%" (of the length). */
    fun parsePosition(raw: String): Seek? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        percent(s)?.let { return Seek.Fraction(it / 100.0) }
        if (s[0] == '+' || s[0] == '-') {
            val ms = parseClock(s.substring(1)) ?: return null
            return Seek.By(if (s[0] == '-') -ms else ms)
        }
        return parseClock(s)?.let { Seek.To(it) }
    }

    /**
     * Where [seek] lands from [positionMs] (null: unknown), inside 0..length
     * when the length is known. Null when it cannot be known: a jump from an
     * unknown position, a share of an unknown length.
     */
    fun targetMs(seek: Seek, positionMs: Long?, durationMs: Long?): Long? {
        val length = durationMs?.takeIf { it > 0 }
        val t = when (seek) {
            is Seek.To -> seek.ms
            is Seek.By -> (positionMs ?: return null) + seek.ms
            is Seek.Fraction -> ((length ?: return null) * seek.f).roundToLong()
        }
        return if (length != null) t.coerceIn(0, length) else t.coerceAtLeast(0)
    }

    /**
     * Where playback is now. A session reports its position as of
     * [updatedAt] (elapsedRealtime) and, while playing, has moved on at
     * [speed] since. Null when it reports no position.
     */
    fun livePosition(positionMs: Long, updatedAt: Long, speed: Float, now: Long, playing: Boolean, durationMs: Long?): Long? {
        if (positionMs < 0) return null
        val moved = if (playing && updatedAt > 0 && now > updatedAt) ((now - updatedAt) * speed).roundToLong() else 0L
        val p = (positionMs + moved).coerceAtLeast(0)
        val length = durationMs?.takeIf { it > 0 }
        return if (length != null) p.coerceAtMost(length) else p
    }

    /** 1:30, 1:02:03. */
    fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 500) / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /**
     * The session a command is for, as an index into [sessions] (which come
     * most important first): the app in front if it is playing, else whatever
     * is playing, else the app in front, else the most recent. Null when
     * there is none.
     */
    fun choose(sessions: List<Candidate>, front: String?): Int? {
        if (sessions.isEmpty()) return null
        val inFront = sessions.indices.filter { sessions[it].pkg == front }
        return inFront.firstOrNull { sessions[it].playing }
            ?: sessions.indices.firstOrNull { sessions[it].playing }
            ?: inFront.firstOrNull()
            ?: 0
    }

    /** Buffering and fast-forwarding count: the app is trying to play. */
    fun isPlaying(state: Int?): Boolean = when (state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> true
        else -> false
    }

    fun canSeek(actions: Long): Boolean = actions and PlaybackState.ACTION_SEEK_TO != 0L

    fun stateName(state: Int): String = when (state) {
        PlaybackState.STATE_PLAYING -> "playing"
        PlaybackState.STATE_PAUSED -> "paused"
        PlaybackState.STATE_BUFFERING -> "buffering"
        PlaybackState.STATE_STOPPED -> "stopped"
        PlaybackState.STATE_FAST_FORWARDING -> "fast-forwarding"
        PlaybackState.STATE_REWINDING -> "rewinding"
        PlaybackState.STATE_CONNECTING -> "connecting"
        PlaybackState.STATE_ERROR -> "in error"
        PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "skipping"
        else -> "idle"
    }

    /** `YouTube: playing "Lofi radio" by Lofi Girl, 1:23 of 4:56.` */
    fun describe(s: Status): String = buildString {
        append(s.app).append(": ").append(s.state)
        s.title?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" \"").append(it.take(80)).append('"') }
        s.artist?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" by ").append(it.take(60)) }
        s.positionMs?.let { p ->
            append(", ").append(formatTime(p))
            s.durationMs?.takeIf { it > 0 }?.let { append(" of ").append(formatTime(it)) }
        }
        append('.')
    }

    // ------------------------------------------------------------ platform

    /** Every active session, most important first; null when Notification access is off. */
    fun sessions(context: Context): List<MediaController>? {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return null
        // Refused (SecurityException) unless the user granted Notification access.
        return runCatching { msm.getActiveSessions(OroNotificationListener.component(context)) }.getOrNull()
    }

    /** The session [choose] picks among [sessions], or null when there is none. */
    fun pick(sessions: List<MediaController>, front: String?): MediaController? =
        choose(sessions.map { Candidate(it.packageName, isPlaying(it.playbackState?.state)) }, front)?.let { sessions[it] }

    /** media(status): what is playing, where and how far in. */
    fun status(context: Context, front: String?): String {
        val all = sessions(context) ?: return "Could not read what is playing. $ACCESS_HINT"
        val c = pick(all, front) ?: return "Nothing is playing: no app has an active media session."
        val md = c.metadata
        val st = c.playbackState
        val length = durationOf(c)
        return describe(
            Status(
                app = appName(context, c.packageName),
                title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.description?.title?.toString(),
                artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: md?.description?.subtitle?.toString(),
                state = stateName(st?.state ?: PlaybackState.STATE_NONE),
                positionMs = st?.let { positionOf(it, length) },
                durationMs = length,
            )
        )
    }

    /**
     * Seeks the session [choose] picks (only [pkg]'s when given) with its own
     * seekTo, then reads the position back.
     */
    suspend fun seek(context: Context, seek: Seek, front: String?, pkg: String?): SeekResult {
        val all = sessions(context) ?: return SeekResult.NoAccess
        val c = pick(if (pkg == null) all else all.filter { it.packageName == pkg }, front)
            ?: return SeekResult.Unavailable(if (pkg == null) "nothing is playing" else "${appName(context, pkg)} has no media session")
        val app = appName(context, c.packageName)
        val state = c.playbackState ?: return SeekResult.Unavailable("$app reports no playback")
        if (!canSeek(state.actions)) return SeekResult.Unavailable("$app does not take seek requests")
        val length = durationOf(c)
        val target = targetMs(seek, positionOf(state, length), length)
            ?: return SeekResult.Unavailable("$app does not report its position and length")
        c.transportControls.seekTo(target)
        // The app reports where it landed within a moment; a keyframe can be a second or two off.
        var landed: Long? = null
        repeat(SEEK_CHECKS) {
            delay(SEEK_CHECK_MS)
            val at = c.playbackState?.let { positionOf(it, length) }
            landed = at
            if (at != null && abs(at - target) <= SEEK_SLACK_MS) {
                return SeekResult.Done("Jumped to ${formatTime(target)}${length?.let { " of " + formatTime(it) }.orEmpty()} in $app.")
            }
        }
        val at = landed
        return SeekResult.Done(
            if (at != null) "$app was asked to jump to ${formatTime(target)} but reports ${formatTime(at)}: the seek could not be confirmed."
            else "Asked $app to jump to ${formatTime(target)}."
        )
    }

    /** The length [c] reports for what it plays, in ms; null for a live stream or none. */
    fun durationOf(c: MediaController): Long? =
        c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }

    /**
     * play, pause, next... sent to [c] itself: its transport control when the
     * session declares it, else the media key delivered to that session
     * alone. Null for what it cannot take (a forward with no fast-forward:
     * the player's own gesture does better than a key it ignores).
     */
    fun transport(context: Context, c: MediaController, action: String): String? {
        val st = c.playbackState
        val a = when (action) {
            "play_pause", "toggle" -> if (isPlaying(st?.state)) "pause" else "play"
            "skip" -> "next"
            "prev", "back" -> "previous"
            "fast_forward" -> "forward"
            else -> action
        }
        val (flag, key) = when (a) {
            "play" -> PlaybackState.ACTION_PLAY to KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> PlaybackState.ACTION_PAUSE to KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> PlaybackState.ACTION_SKIP_TO_NEXT to KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> PlaybackState.ACTION_SKIP_TO_PREVIOUS to KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> PlaybackState.ACTION_STOP to KeyEvent.KEYCODE_MEDIA_STOP
            "forward" -> PlaybackState.ACTION_FAST_FORWARD to KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "rewind" -> PlaybackState.ACTION_REWIND to KeyEvent.KEYCODE_MEDIA_REWIND
            else -> return null
        }
        val app = appName(context, c.packageName)
        if ((st?.actions ?: 0L) and flag != 0L) {
            val tc = c.transportControls
            when (a) {
                "play" -> tc.play()
                "pause" -> tc.pause()
                "next" -> tc.skipToNext()
                "previous" -> tc.skipToPrevious()
                "stop" -> tc.stop()
                "forward" -> tc.fastForward()
                else -> tc.rewind()
            }
            return "Sent $a to $app."
        }
        if (a == "forward" || a == "rewind") return null
        val now = SystemClock.uptimeMillis()
        val sent = runCatching { c.dispatchMediaButtonEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0)) }.getOrDefault(false)
        runCatching { c.dispatchMediaButtonEvent(KeyEvent(now, now + 40, KeyEvent.ACTION_UP, key, 0)) }
        return if (sent) "Sent media $a to $app." else null
    }

    private fun positionOf(state: PlaybackState, length: Long?): Long? = livePosition(
        state.position, state.lastPositionUpdateTime, state.playbackSpeed, SystemClock.elapsedRealtime(),
        state.state == PlaybackState.STATE_PLAYING, length,
    )

    private fun appName(context: Context, pkg: String): String = AppCatalog.labelFor(context, pkg) ?: pkg

    private const val SEEK_CHECKS = 8
    private const val SEEK_CHECK_MS = 120L
    private const val SEEK_SLACK_MS = 3_000L
}
