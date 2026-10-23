package com.oro.app.device

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * set_value's pure half: what a value means on a slider, where on screen it
 * lies, and which way to move the slider there. No Android types, so it runs
 * in JVM tests; [DeviceController] does the acting.
 *
 * The ways in, best first: the slider's own ACTION_SET_PROGRESS (exact), its
 * scroll steps re-read after each one, then a press-hold-move from the thumb
 * that is steered by the value the slider reports while the finger is down.
 */
object Sliders {

    sealed interface Value {
        /** A number in the slider's own units: 35 on 0..100. */
        data class Number(val v: Float) : Value
        /** A share of the range: "40%". */
        data class Percent(val p: Float) : Value
        /** A time on a media seek bar: "1:30". */
        data class Time(val ms: Long) : Value
    }

    enum class Method { SET_PROGRESS, SCROLL_STEPS, DRAG }

    private val NUMBER = Regex("""^-?\d+(\.\d+)?$""")

    /** "35", "-5", "0.5" (a number), "40%", or a time: "1:30", "90s". */
    fun parse(raw: String): Value? {
        val s = raw.trim()
        MediaControl.percent(s)?.let { return Value.Percent(it.toFloat()) }
        if (NUMBER.matches(s)) return s.toFloatOrNull()?.let { Value.Number(it) }
        return MediaControl.parseClock(s)?.let { Value.Time(it) }
    }

    /**
     * Where [value] lies along the slider, 0..1. Null when it cannot be known:
     * a number with no range, a time with no length.
     */
    fun fraction(value: Value, range: ValueRange?, durationMs: Long?): Float? = when (value) {
        is Value.Percent -> value.p / 100f
        is Value.Number -> range?.takeIf { it.max > it.min }?.let { (value.v - it.min) / (it.max - it.min) }
        is Value.Time -> durationMs?.takeIf { it > 0 }?.let { value.ms.toFloat() / it }
    }?.coerceIn(0f, 1f)

    /** [value] in the slider's own units, as ACTION_SET_PROGRESS takes it; whole for an integer range. */
    fun target(value: Value, range: ValueRange, durationMs: Long?, whole: Boolean): Float? {
        val raw = if (value is Value.Number) value.v.coerceIn(range.min, range.max)
        else range.min + (fraction(value, range, durationMs) ?: return null) * (range.max - range.min)
        return if (whole) Math.round(raw).toFloat() else raw
    }

    /**
     * The ways to try, best first. SET_PROGRESS takes the value in the
     * slider's units and a step is only known to have landed once read back,
     * so both need a slider that reports its value; a drag needs room.
     */
    fun plan(setProgress: Boolean, scroll: Boolean, readable: Boolean, draggable: Boolean): List<Method> = buildList {
        if (setProgress && readable) add(Method.SET_PROGRESS)
        if (scroll && readable) add(Method.SCROLL_STEPS)
        if (draggable) add(Method.DRAG)
    }

    /**
     * The next scroll step from [current] toward [target]: true forward,
     * false back, null to stop -- there, or within half a [step] (the last
     * step's size, null before the first), which no step improves: halfway
     * between two steps, either is as near.
     */
    fun nextStep(current: Float, target: Float, step: Float?, span: Float): Boolean? {
        val gap = target - current
        if (abs(gap) <= span * 1e-4f) return null
        if (step != null && step > 0f && abs(gap) <= step / 2) return null
        return gap > 0
    }

    /** Whether [current] is within [tolerance] (a share of the range) of [target]. */
    fun reached(current: Float, target: Float, range: ValueRange, tolerance: Float): Boolean =
        abs(current - target) <= maxOf((range.max - range.min) * tolerance, 1e-4f)

    /** The track's inset at each end of the slider's bounds: about the thumb's radius. */
    fun inset(b: Bounds): Int = minOf(b.height * 35 / 100, b.width * 6 / 100).coerceAtLeast(0)

    /** Screen x of [fraction] along the slider's track. */
    fun xFor(fraction: Float, b: Bounds): Int {
        val i = inset(b)
        return (b.left + i + fraction.coerceIn(0f, 1f) * (b.width - 2 * i)).roundToInt()
    }

    /**
     * Where to move the finger for [target] (0..1) after two readings with it
     * down: [x0] showed [f0], [x1] showed [f1]. A line through them, so the
     * thumb's real inset -- or a bar drawn right to left -- no longer matters.
     * Null when the readings do not tell (the value did not move).
     */
    fun steer(x0: Int, f0: Float, x1: Int, f1: Float, target: Float, b: Bounds): Int? {
        if (x0 == x1 || abs(f1 - f0) < 0.005f) return null
        val x = x1 + (target - f1) * (x1 - x0) / (f1 - f0)
        return x.roundToInt().coerceIn(b.left + 1, b.right - 2)
    }

    // ------------------------------------------------------- media bars

    // Not a bare "seekbar": that is every SeekBar's generic id, volume sliders included.
    private val SEEK_ID = Regex("time_?bar|scrub|exo_progress|(player|playback|video|media|watch|controls?)\\w*(seek|progress)")
    private val TIME = Regex("""\b\d{1,2}(:\d{2}){1,2}\b""")
    private val TIME_WORDS = Regex("""\b\d+ (hours?|minutes?|seconds?)\b""", RegexOption.IGNORE_CASE)
    private val PAIR = Regex("""\s+of\s+|\s*/\s*""", RegexOption.IGNORE_CASE)

    /** How far from a seek bar its time labels sit. */
    private const val NEAR_PX = 300

    /**
     * A video's or song's seek bar: a slider with a player's id or a time in
     * its label. Kept strict: a volume or speed slider taken for one would
     * seek the video instead.
     */
    fun isSeekBar(e: ScreenElement): Boolean = e.role == ScreenElement.ROLE_SLIDER && (
        SEEK_ID.containsMatchIn(e.viewId.orEmpty().lowercase()) || TIME.containsMatchIn(e.label) || TIME_WORDS.containsMatchIn(e.label)
        )

    /** The media seek bar on [screen], the widest if several. */
    fun seekBar(screen: ScreenState): ScreenElement? = screen.elements.filter { isSeekBar(it) }.maxByOrNull { it.bounds.width }

    /**
     * The player's position and length as its labels show them by [bar]:
     * "1:23 / 4:56", "1:23 of 4:56", "1 minute 23 seconds of 4 minutes", or
     * two lone times at the bar's ends. Either may be null.
     */
    fun clock(screen: ScreenState, bar: ScreenElement): Pair<Long?, Long?> {
        val near = listOf(bar) + screen.elements.filter { it !== bar && abs(it.centerY - bar.centerY) <= NEAR_PX }
        for (e in near) {
            val parts = e.label.split(PAIR)
            if (parts.size != 2 || !timeLike(parts[0]) || !timeLike(parts[1])) continue
            val now = MediaControl.parseClock(parts[0])
            val length = MediaControl.parseClock(parts[1])
            if (now != null && length != null) return now to length
        }
        val lone = near.mapNotNull { e -> e.label.trim().takeIf { TIME.matches(it) }?.let(MediaControl::parseClock) }.distinct()
        return if (lone.size == 2) lone.min() to lone.max() else null to null
    }

    /** Written like a time (1:23, "4 minutes"), not a count ("83 of 100"). */
    private fun timeLike(s: String): Boolean = ':' in s || TIME_WORDS.containsMatchIn(s)

    /** set_value's value for a seek through the media session: a number on a bar with no range is seconds. */
    fun mediaPosition(value: Value, range: ValueRange?): String = when (value) {
        is Value.Time -> MediaControl.formatTime(value.ms)
        is Value.Percent -> pct(value.p)
        is Value.Number -> if (range != null && range.max > range.min) pct((value.v - range.min) / (range.max - range.min) * 100f)
        else MediaControl.formatTime((value.v * 1000).toLong())
    }

    /** set_value's value for the bar itself: a number on a bar with no range is seconds. */
    fun barValue(value: Value, range: ValueRange?): String = when (value) {
        is Value.Time -> MediaControl.formatTime(value.ms)
        is Value.Percent -> pct(value.p)
        is Value.Number -> if (range == null) MediaControl.formatTime((value.v * 1000).toLong()) else ValueRange.num(value.v)
    }

    /** media(seek)'s position as a value for the player's bar; null for a jump from an unknown position. */
    fun barValue(seek: MediaControl.Seek, positionMs: Long?): String? = when (seek) {
        is MediaControl.Seek.To -> MediaControl.formatTime(seek.ms)
        is MediaControl.Seek.Fraction -> pct((seek.f * 100).toFloat())
        is MediaControl.Seek.By -> positionMs?.let { MediaControl.formatTime((it + seek.ms).coerceAtLeast(0)) }
    }

    private fun pct(p: Float): String =
        String.format(Locale.ROOT, "%.1f", p.coerceIn(0f, 100f)).removeSuffix(".0") + "%"
}
