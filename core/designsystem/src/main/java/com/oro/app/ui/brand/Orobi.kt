package com.oro.app.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.max
import kotlin.math.min

@Immutable
data class OrobiColors(
    val skin: Color = OroBrand.Peach,
    val marking: Color = OroBrand.Magenta,
    val eye: Color = OroBrand.Ink,
    val shadow: Color = Color.Black,
)

@Composable
fun orobiColors(): OrobiColors = OrobiColors(shadow = MaterialTheme.colorScheme.onSurface)

/** Aspect ratio of Orobi's artwork, width over height. */
const val OrobiAspectRatio = 1200f / 820f

/**
 * Orobi, drawn from a pose supplied per frame.
 *
 * [pose] is a lambda on purpose: it is only invoked in the draw pass, so a pose
 * driven by [rememberAnimationClock] redraws every frame without recomposing.
 */
@Composable
fun OrobiMascot(
    pose: () -> OrobiPose,
    modifier: Modifier = Modifier,
    colors: OrobiColors = orobiColors(),
    showShadow: Boolean = true,
    contentDescription: String? = OroBrand.AssistantName,
) {
    val scratch = remember { Path() }
    Canvas(
        modifier
            .aspectRatio(OrobiAspectRatio)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        drawOrobi(pose(), colors, scratch, showShadow = showShadow)
    }
}

/** Orobi at rest: breathing, blinking and, if [look], glancing around. */
@Composable
fun OrobiIdle(
    modifier: Modifier = Modifier,
    look: Boolean = true,
    playing: Boolean = true,
    colors: OrobiColors = orobiColors(),
    showShadow: Boolean = true,
    contentDescription: String? = OroBrand.AssistantName,
) {
    val reduced = rememberReducedMotion()
    val clock = rememberAnimationClock(running = playing && !reduced)
    OrobiMascot(
        pose = { OrobiMotion.idle(clock.value, look) },
        modifier = modifier,
        colors = colors,
        showShadow = showShadow,
        contentDescription = contentDescription,
    )
}

/**
 * The infinity loader: Orobi's body line with a segment running around it.
 * Size it by width; the height follows the artwork.
 */
@Composable
fun OrobiLoader(
    modifier: Modifier = Modifier,
    color: Color = OroBrand.Magenta,
    contentDescription: String? = "Loading",
) {
    val reduced = rememberReducedMotion()
    val clock = rememberAnimationClock(running = !reduced)
    val scratch = remember { Path() }
    val box = OrobiGeometry.LoopViewBox
    Canvas(
        modifier
            .aspectRatio(box.width / box.height)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        inViewBox(box, Rect(Offset.Zero, size)) {
            drawLoop(
                phase = if (reduced) 0.2f else (clock.value / OrobiMotion.LoaderPeriod).mod(1f),
                trackWidth = 90f,
                chaseWidth = 90f,
                color = color,
                trackAlpha = 0.2f,
                alpha = 1f,
                scratch = scratch,
            )
        }
    }
}

/**
 * Draws Orobi into [bounds] (the whole draw area by default), fitted and
 * centred like an SVG with `preserveAspectRatio="xMidYMid meet"`.
 */
fun DrawScope.drawOrobi(
    pose: OrobiPose,
    colors: OrobiColors,
    scratch: Path,
    bounds: Rect = Rect(Offset.Zero, size),
    showShadow: Boolean = true,
    alpha: Float = 1f,
) {
    if (alpha <= 0f) return
    val g = OrobiGeometry
    inViewBox(g.ViewBox, bounds) {
        if (showShadow && pose.shadowAlpha > 0f) {
            val rx = g.ShadowRadiusX * pose.shadowScale
            val ry = g.ShadowRadiusY * pose.shadowScale
            drawOval(
                color = colors.shadow,
                topLeft = Offset(g.ShadowCenter.x - rx, g.ShadowCenter.y - ry),
                size = Size(rx * 2f, ry * 2f),
                alpha = (pose.shadowAlpha * alpha).coerceIn(0f, 1f),
            )
        }

        withTransform({
            translate(top = -pose.lift)
            rotate(pose.tilt, pivot = g.Foot)
            scale(pose.stretchX, pose.stretchY, pivot = g.Foot)
        }) {
            val skinAlpha = (1f - pose.morph * 1.5f).coerceIn(0f, 1f) * alpha
            if (skinAlpha > 0f) {
                drawPath(OrobiPaths.body, colors.skin, alpha = skinAlpha)
                clipPath(OrobiPaths.body) {
                    translate(left = pose.bandShift) {
                        drawPath(OrobiPaths.bandLeft, colors.marking, alpha = skinAlpha)
                    }
                    translate(left = -pose.bandShift) {
                        drawPath(OrobiPaths.bandRight, colors.marking, alpha = skinAlpha)
                    }
                }
            }

            val eyeAlpha = (1f - pose.morph * 3f).coerceIn(0f, 1f) * alpha
            if (eyeAlpha > 0f) {
                val open = max(0.08f, pose.eyeOpen)
                for (eye in arrayOf(g.LeftEye, g.RightEye)) {
                    val c = Offset(eye.x + pose.lookX, eye.y + pose.lookY)
                    scale(scaleX = 1f, scaleY = open, pivot = c) {
                        drawOval(
                            color = colors.eye,
                            topLeft = Offset(c.x - g.EyeRadiusX, c.y - g.EyeRadiusY),
                            size = Size(g.EyeRadiusX * 2f, g.EyeRadiusY * 2f),
                            alpha = eyeAlpha,
                        )
                    }
                }
            }

            val loopAlpha = (pose.morph * 1.5f - 0.25f).coerceIn(0f, 1f) * alpha
            if (loopAlpha > 0f) {
                drawLoop(
                    phase = pose.loaderPhase,
                    trackWidth = 128f - 38f * pose.morph,
                    chaseWidth = 90f,
                    color = colors.marking,
                    trackAlpha = 0.22f,
                    alpha = loopAlpha,
                    scratch = scratch,
                )
            }
        }
    }
}

/** A four-point sparkle, scaled and spun around its own centre. */
fun DrawScope.drawSparkle(
    center: Offset,
    radius: Float,
    color: Color,
    scratch: Path,
    scale: Float = 1f,
    rotation: Float = 0f,
    alpha: Float = 1f,
) {
    if (scale <= 0f || alpha <= 0f) return
    rotate(rotation, pivot = center) {
        drawPath(sparklePath(center, radius * scale, into = scratch), color, alpha = alpha)
    }
}

/**
 * Runs [block] with the canvas mapped so that [viewBox] fits inside [bounds],
 * centred, aspect preserved.
 */
inline fun DrawScope.inViewBox(viewBox: Rect, bounds: Rect, block: DrawScope.() -> Unit) {
    val s = min(bounds.width / viewBox.width, bounds.height / viewBox.height)
    val dx = bounds.left + (bounds.width - viewBox.width * s) / 2f - viewBox.left * s
    val dy = bounds.top + (bounds.height - viewBox.height * s) / 2f - viewBox.top * s
    withTransform({
        translate(dx, dy)
        scale(s, s, pivot = Offset.Zero)
    }, block)
}

private fun DrawScope.drawLoop(
    phase: Float,
    trackWidth: Float,
    chaseWidth: Float,
    color: Color,
    trackAlpha: Float,
    alpha: Float,
    scratch: Path,
) {
    drawPath(
        OrobiPaths.loop,
        color,
        alpha = trackAlpha * alpha,
        style = Stroke(trackWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    // A dash of 24 in a pattern of 100, like the artwork's stroke-dasharray.
    val measure = OrobiPaths.loopMeasure
    val length = measure.length
    val start = phase.mod(1f) * length
    val end = start + length * 0.24f
    scratch.reset()
    measure.getSegment(start, min(end, length), scratch, true)
    if (end > length) measure.getSegment(0f, end - length, scratch, true)
    drawPath(
        scratch,
        color,
        alpha = alpha,
        style = Stroke(chaseWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
