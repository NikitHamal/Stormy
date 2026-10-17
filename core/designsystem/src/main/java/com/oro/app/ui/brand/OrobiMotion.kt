package com.oro.app.ui.brand

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import android.app.ActivityManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * One frame of Orobi.
 *
 * Every field is in artwork units (see [OrobiGeometry]) or a plain factor, so
 * a pose is resolution independent and can be interpolated, layered, or
 * computed from a timeline without knowing how big the mascot is on screen.
 */
@Immutable
data class OrobiPose(
    /** Upward travel of the whole figure. */
    val lift: Float = 0f,
    /** Lean, in degrees, around the foot. */
    val tilt: Float = 0f,
    val stretchX: Float = 1f,
    val stretchY: Float = 1f,
    /** Where the eyes are looking, as an offset from rest. */
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    /** 1 open, ~0.1 closed. Values under 0.4 read as a happy squint. */
    val eyeOpen: Float = 1f,
    /** Sideways slide of the magenta bands, the "inhale" before an action. */
    val bandShift: Float = 0f,
    val shadowAlpha: Float = 0.1f,
    val shadowScale: Float = 1f,
    /** 0 is Orobi, 1 is the infinity loader the body is made of. */
    val morph: Float = 0f,
    /** Position of the loader's running segment, 0..1 around the loop. */
    val loaderPhase: Float = 0f,
) {
    companion object {
        val Rest = OrobiPose()
    }
}

/**
 * Orobi's motion vocabulary, as pure functions of time.
 *
 * Nothing here holds state. A screen keeps one clock and asks for the pose at
 * that instant, which keeps every layer of a choreography on the same beat and
 * makes any frame reproducible -- a reduced-motion user simply gets the frame
 * at a fixed time.
 */
object OrobiMotion {

    /** Material's standard curve, used for anything that settles. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** The emphasized decelerate curve: arrives fast, lands soft. */
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** CSS `ease`, which the blink was tuned against. */
    private val CssEase: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    private const val BreathPeriod = 3.2f
    private const val BlinkPeriod = 4.2f
    private const val LookPeriod = 5f
    const val LoaderPeriod = 1.6f

    // Glances are saccades, the way real eyes move: a quick jump (~150ms)
    // and a hold, never a slow drift. Travel stays inside the clamp in
    // drawEyes so the eyes never crowd the edge of the head.
    private val LookTimes = floatArrayOf(0f, .30f, .33f, .47f, .50f, .66f, .69f, 1f)
    private val LookX = floatArrayOf(0f, 0f, -8f, -8f, 8f, 8f, 0f, 0f)
    private val LookY = floatArrayOf(0f, 0f, 3f, 3f, -2f, -2f, 0f, 0f)

    private val BlinkTimes = floatArrayOf(0f, .92f, .95f, 1f)
    private val BlinkValues = floatArrayOf(1f, 1f, .1f, 1f)

    /**
     * Resting, alive: a slow breath, a blink every few seconds and, when
     * [look] is set, a glance left and right.
     */
    fun idle(t: Float, look: Boolean = true): OrobiPose {
        val breath = 0.5f - 0.5f * cos(2f * PI.toFloat() * (t / BreathPeriod))
        val blinkPhase = (t / BlinkPeriod).mod(1f)
        val lookPhase = (t / LookPeriod).mod(1f)
        return OrobiPose(
            stretchX = 1f + 0.02f * breath,
            stretchY = 1f - 0.02f * breath,
            eyeOpen = keyframes(blinkPhase, BlinkTimes, BlinkValues, CssEase),
            lookX = if (look) keyframes(lookPhase, LookTimes, LookX, Standard) else 0f,
            lookY = if (look) keyframes(lookPhase, LookTimes, LookY, Standard) else 0f,
        )
    }

    /**
     * A happy hop, layered on top of [base]. [t] is seconds since the hop was
     * triggered; outside 0..1.3 the base pose comes back unchanged.
     *
     * Classic structure: squash to anticipate, leave the ground stretched,
     * squint at the top, squash again on landing.
     */
    fun hop(base: OrobiPose, t: Float): OrobiPose {
        if (t < 0f || t > HopDuration) return base
        val anticipate = bump(t, 0f, .45f)
        val air = bump(t, .29f, .67f)
        val land = bump(t, .8f, .48f)
        val happy = ramp(t, .35f, .13f) - ramp(t, .9f, .16f)
        val squash = anticipate * .1f + land * .12f
        return base.copy(
            lift = base.lift + air * 160f,
            stretchX = base.stretchX * (1f + squash),
            stretchY = base.stretchY * (1f - squash * 1.1f),
            eyeOpen = minOf(base.eyeOpen, 1f - .72f * happy),
            shadowAlpha = base.shadowAlpha * (1f - .55f * air),
            shadowScale = base.shadowScale * (1f - .35f * air),
        )
    }

    const val HopDuration = 1.3f

    /**
     * The entrance: the loader runs, then pulls itself into Orobi, lands, and
     * blinks awake. Ends at [EntranceDuration] on exactly the rest pose, so
     * hand-off to [idle] is seamless.
     */
    fun entrance(t: Float): OrobiPose {
        val morph = 1f - smoothstep(t, .95f, .7f)
        val inhale = bump(t, 1.05f, .55f)
        val land = bump(t, 1.55f, .45f)
        val blink = max(0f, 1f - (t - 2.05f) * (t - 2.05f) * 260f)
        val squash = inhale * .06f + land * .1f
        return OrobiPose(
            stretchX = 1f + squash,
            stretchY = 1f - squash * 1.1f,
            eyeOpen = max(.08f, 1f - .9f * blink),
            bandShift = inhale * 50f,
            shadowAlpha = .1f * (1f - morph),
            morph = morph,
            loaderPhase = (t / LoaderPeriod).mod(1f),
        )
    }

    const val EntranceDuration = 2.4f

    // --- Timeline helpers -------------------------------------------------

    /** Linear 0..1 over [duration] starting at [start]. */
    fun ramp(t: Float, start: Float, duration: Float): Float =
        ((t - start) / duration).coerceIn(0f, 1f)

    /** Hermite-smoothed [ramp]. */
    fun smoothstep(t: Float, start: Float, duration: Float): Float {
        val x = ramp(t, start, duration)
        return x * x * (3f - 2f * x)
    }

    /** Up and back down again: a half sine over [duration]. */
    fun bump(t: Float, start: Float, duration: Float): Float =
        sin(ramp(t, start, duration) * PI.toFloat())

    /** Cubic ease-out of a 0..1 progress. */
    fun easeOut(p: Float): Float = 1f - (1f - p) * (1f - p) * (1f - p)

    /**
     * Back-out: overshoots by a few percent and settles. 0 at 0, 1 at 1.
     * Used for anything that "lands" in place.
     */
    fun backOut(p: Float): Float {
        val q = p - 1f
        return 1f + 2.1f * q * q * q + 1.1f * q * q
    }

    private fun keyframes(p: Float, times: FloatArray, values: FloatArray, easing: Easing): Float {
        for (i in 1 until times.size) {
            if (p <= times[i]) {
                val span = times[i] - times[i - 1]
                val local = if (span <= 0f) 1f else (p - times[i - 1]) / span
                return values[i - 1] + (values[i] - values[i - 1]) * easing.transform(local)
            }
        }
        return values.last()
    }
}

/**
 * Seconds since this clock started, ticking once per frame while [running].
 *
 * Read the returned state inside a draw lambda, not in composition: that way a
 * running animation invalidates only the draw pass and never recomposes.
 * When [restartKey] changes the clock starts again from zero.
 *
 * Two economies, neither visible:
 *  - It only ticks while the screen is at least STARTED. A mascot on a screen
 *    under another one, or in a backgrounded app, costs nothing.
 *  - On low-RAM devices it ticks at most 30 times a second. The brand motion
 *    is all slow easing, where 30fps and 60fps are indistinguishable, and it
 *    halves the draw work on exactly the phones that need it.
 */
@Composable
fun rememberAnimationClock(running: Boolean = true, restartKey: Any? = Unit): State<Float> {
    val time = remember(restartKey) { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val minFrameNanos = if (rememberLowEndDevice()) 33_000_000L else 0L
    LaunchedEffect(running, restartKey, lifecycle, minFrameNanos) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val base = time.floatValue
            val start = withFrameNanos { it }
            var last = start
            while (true) {
                withFrameNanos { now ->
                    if (now - last >= minFrameNanos) {
                        last = now
                        time.floatValue = base + (now - start) / 1_000_000_000f
                    }
                }
            }
        }
    }
    return time
}

/** True on devices Android itself classes as low-RAM. */
@Composable
fun rememberLowEndDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.isLowRamDevice == true
    }
}

/**
 * Whether the user has turned animations off system-wide.
 *
 * The brand animations run on their own clock rather than the animator, so
 * they would ignore the system setting unless they asked for it explicitly.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
