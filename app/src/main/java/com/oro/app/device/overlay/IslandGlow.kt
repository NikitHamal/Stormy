package com.oro.app.device.overlay

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oro.app.ui.brand.OroBrand
import kotlin.math.min

/** What the island's light says: Orobi is working, it needs the user, or nothing. */
internal enum class GlowMood { Off, Working, Attention }

/** The glow's pure decisions, kept apart from drawing so they are tested on the JVM. */
internal object IslandGlowModel {

    /** Lit while Orobi works; warm while it waits on the user; out when paused, done or failed. */
    fun mood(phase: IslandPhase): GlowMood = when (phase) {
        IslandPhase.Working -> GlowMood.Working
        IslandPhase.Question, IslandPhase.Approval -> GlowMood.Attention
        IslandPhase.Paused, IslandPhase.Done, IslandPhase.Failed, IslandPhase.Idle -> GlowMood.Off
    }

    /**
     * The halo's strokes, nearest first: how far out each reaches, as a
     * fraction of the halo's reach. Equal faint strokes that stack on the
     * edge fade linearly outward, so the halo needs no blur (blur on a
     * hardware canvas is not there on every phone Oro supports).
     */
    fun haloReaches(layers: Int): FloatArray = FloatArray(layers) { (it + 1f) / layers }

    /** The halo's light right at the island's edge, for [layers] strokes of [alpha] each (source-over). */
    fun edgeAlpha(layers: Int, alpha: Float): Float {
        var through = 1f
        repeat(layers) { through *= 1f - alpha }
        return 1f - through
    }
}

/** Room around the island inside its window, for the glow's halo and the shadow. */
internal val IslandGlowRoom = 10.dp

/**
 * Orobi's light around the floating island: a thin rim of its colours
 * turning slowly around the island's edge, a short spark running ahead of
 * it, and a soft halo just outside that breathes. A question or an approval
 * turns it warm and slow, so the eye finds the card; paused, done or failed,
 * it fades out. It follows the island's own shape, pill or card.
 *
 * Drawn in the draw phase only: the animation never recomposes the island or
 * resizes its window, and stops entirely when the glow is out. The halo sits
 * behind the island (so the island covers its inner half) and the rim on top
 * of its edge. Lite mode keeps a still rim and no halo.
 */
@Composable
internal fun Modifier.islandGlow(phase: IslandPhase, lite: Boolean, corner: Dp): Modifier {
    val mood = IslandGlowModel.mood(phase)
    val strength = animateFloatAsState(
        targetValue = if (mood == GlowMood.Off) 0f else 1f,
        animationSpec = tween(if (mood == GlowMood.Off) FADE_OUT_MS else FADE_IN_MS),
        label = "glowStrength",
    )
    val warmth = animateFloatAsState(
        targetValue = if (mood == GlowMood.Attention) 1f else 0f,
        animationSpec = tween(WARM_MS),
        label = "glowWarmth",
    )
    val motion: GlowMotion? = if (!lite && mood != GlowMood.Off) {
        val t = rememberInfiniteTransition(label = "glowMotion")
        GlowMotion(
            turn = t.animateFloat(0f, 360f, infiniteRepeatable(tween(TURN_MS, easing = LinearEasing)), label = "turn"),
            spark = t.animateFloat(0f, 360f, infiniteRepeatable(tween(SPARK_MS, easing = LinearEasing)), label = "spark"),
            breath = t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "breath",
            ),
        )
    } else null
    // The last angles drawn: a fade-out (no motion any more) holds them
    // rather than snapping the colours back to their start.
    val held = remember { FloatArray(3) }

    return drawWithCache {
        val w = size.width
        val h = size.height
        val radius = min(corner.toPx(), min(w, h) / 2f)
        val rimWidth = RIM.toPx()
        val reach = HALO_REACH.toPx()
        val cx = w / 2f
        val cy = h / 2f

        val edge = Path().apply { addRoundRect(RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW) }
        val rimInset = rimWidth / 2f
        val rimPath = Path().apply {
            addRoundRect(RectF(rimInset, rimInset, w - rimInset, h - rimInset), radius - rimInset, radius - rimInset, Path.Direction.CW)
        }
        val cool = SweepGradient(cx, cy, COOL, null)
        val warm = SweepGradient(cx, cy, WARM, null)
        val sparkShader = SweepGradient(cx, cy, SPARK_COLORS, SPARK_STOPS)
        val spin = Matrix()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }
        val reaches = IslandGlowModel.haloReaches(HALO_LAYERS)

        fun spun(shader: SweepGradient, degrees: Float): SweepGradient {
            spin.setRotate(degrees, cx, cy)
            shader.setLocalMatrix(spin)
            return shader
        }

        onDrawWithContent {
            val s = strength.value
            if (s <= 0.004f) {
                drawContent()
                return@onDrawWithContent
            }
            motion?.let {
                held[0] = it.turn.value
                held[1] = it.spark.value
                held[2] = it.breath.value
            }
            val turn = held[0]
            val sparkAt = held[1]
            val breath = if (lite) 1f else held[2]
            val hot = warmth.value

            // The halo: behind the island, so only its outer half shows.
            if (!lite) {
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    val pulse = HALO_LOW + (1f - HALO_LOW) * breath
                    for (i in reaches.indices.reversed()) {
                        stroke.strokeWidth = rimWidth + 2f * reach * reaches[i]
                        if (hot < 1f) {
                            stroke.shader = spun(cool, turn)
                            stroke.alpha = alpha(HALO_ALPHA * s * pulse * (1f - hot))
                            c.drawPath(edge, stroke)
                        }
                        if (hot > 0f) {
                            stroke.shader = spun(warm, -turn * 0.5f)
                            stroke.alpha = alpha(HALO_ALPHA * s * pulse * hot * WARM_HALO_BOOST)
                            c.drawPath(edge, stroke)
                        }
                    }
                }
            }

            drawContent()

            // The rim, on the island's own edge, and the spark running along it.
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                stroke.strokeWidth = rimWidth
                if (hot < 1f) {
                    stroke.shader = spun(cool, turn)
                    stroke.alpha = alpha(RIM_ALPHA * s * (1f - hot))
                    c.drawPath(rimPath, stroke)
                }
                if (hot > 0f) {
                    stroke.shader = spun(warm, -turn * 0.5f)
                    stroke.alpha = alpha(RIM_ALPHA * s * hot)
                    c.drawPath(rimPath, stroke)
                }
                if (!lite && hot < 1f) {
                    stroke.shader = spun(sparkShader, sparkAt)
                    stroke.alpha = alpha(SPARK_ALPHA * s * (1f - hot))
                    c.drawPath(rimPath, stroke)
                }
                stroke.shader = null
            }
        }
    }
}

/** The island glow's running values; their reads happen in the draw phase only. */
private class GlowMotion(val turn: State<Float>, val spark: State<Float>, val breath: State<Float>)

private fun alpha(v: Float): Int = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

private const val FADE_IN_MS = 360
private const val FADE_OUT_MS = 520
private const val WARM_MS = 600
private const val TURN_MS = 5_200
private const val SPARK_MS = 2_400
private const val BREATH_MS = 1_600

private val RIM = 1.5.dp
private val HALO_REACH = 7.dp
private const val HALO_LAYERS = 7
private const val HALO_ALPHA = 0.062f
private const val HALO_LOW = 0.62f
private const val WARM_HALO_BOOST = 1.25f
private const val RIM_ALPHA = 0.95f
private const val SPARK_ALPHA = 0.9f

/** Orobi's hues around the rim; the last repeats the first so the turn has no seam. */
private val COOL = intArrayOf(
    OroBrand.Magenta.toArgb(), OroBrand.Violet.toArgb(), OroBrand.Blue.toArgb(),
    OroBrand.Apricot.toArgb(), OroBrand.Magenta.toArgb(),
)

/** Waiting on the user: Orobi's warm end. */
private val WARM = intArrayOf(
    OroBrand.Apricot.toArgb(), OroBrand.Coral.toArgb(), OroBrand.Magenta.toArgb(),
    OroBrand.Peach.toArgb(), OroBrand.Apricot.toArgb(),
)

/** One short bright stretch of the rim; the rest of the turn is clear. */
private val SPARK_COLORS = intArrayOf(
    0x00FFFFFF, 0x00FFFFFF, 0x00FDDFC6, 0xF2FFFFFF.toInt(), 0x00FDDFC6, 0x00FFFFFF,
)
private val SPARK_STOPS = floatArrayOf(0f, 0.78f, 0.86f, 0.92f, 0.97f, 1f)
