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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Orobi's palette.
 *
 * Two fixed variants rather than colours derived from the theme: the mascot is
 * a character, and a character keeps its identity. On dark surfaces Orobi is
 * peach with ink eyes; on light ones the peach body disappears into the page
 * (about 1.3:1 against white), so the figure inverts to an ink body with peach
 * eyes. The magenta markings are the constant that makes both read as the same
 * creature.
 */
@Immutable
data class OrobiColors(
    val skin: Color = OroBrand.Peach,
    val marking: Color = OroBrand.Magenta,
    val eye: Color = OroBrand.Ink,
    /** The small highlight that makes the eyes look wet and alive. */
    val glint: Color = Color.White,
    val shadow: Color = Color.Black,
) {
    companion object {
        /** For dark surfaces: the artwork as drawn. */
        val OnDark = OrobiColors(shadow = Color.White)

        /** For light surfaces: ink body, peach eyes. */
        val OnLight = OrobiColors(
            skin = OroBrand.Ink,
            eye = OroBrand.Peach,
            glint = OroBrand.Ink.copy(alpha = 0.55f),
            shadow = OroBrand.Ink,
        )

        /** Picks the variant that reads on [background]. */
        fun forBackground(background: Color): OrobiColors =
            if (background.luminance() > 0.5f) OnLight else OnDark
    }
}

/** Orobi's colours for the current theme's background. */
@Composable
fun orobiColors(): OrobiColors = OrobiColors.forBackground(MaterialTheme.colorScheme.background)

/** Aspect ratio of Orobi's artwork, width over height. */
const val OrobiAspectRatio = 1200f / 820f

/**
 * Per-mascot drawing state: scratch paths and a cached raster of the body.
 *
 * The body and its markings are the expensive part of a frame -- three curve
 * paths, one of them used as a clip. At rest they do not change between
 * frames, only the transform around them does, so they are rendered once into
 * a bitmap at the on-screen resolution and stamped every frame after that.
 * Only the eyes (and the loader, while morphing) are drawn live.
 */
class OrobiRenderCache {
    internal val path = Path()
    private var bitmap: ImageBitmap? = null
    private var bitmapScale = 0f
    private var bitmapColors: OrobiColors? = null

    internal fun bodyBitmap(scope: DrawScope, pxPerUnit: Float, colors: OrobiColors): ImageBitmap {
        val cached = bitmap
        // Re-rasterise only when the size changes meaningfully: a mascot
        // scaling in on entrance should not allocate a bitmap per frame.
        if (cached != null && bitmapColors == colors &&
            bitmapScale > 0f && max(pxPerUnit / bitmapScale, bitmapScale / pxPerUnit) < 1.15f
        ) {
            return cached
        }
        val b = BodyBounds
        // A little over-resolution, so the breathing stretch never upscales.
        val scale = pxPerUnit * 1.05f
        val w = max(1, ceil(b.width * scale).toInt())
        val h = max(1, ceil(b.height * scale).toInt())
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(scope, scope.layoutDirection, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            withTransform({
                scale(scale, scale, pivot = Offset.Zero)
                translate(-b.left, -b.top)
            }) {
                drawBody(colors, bandShift = 0f, alpha = 1f)
            }
        }
        bitmap = image
        bitmapScale = pxPerUnit
        bitmapColors = colors
        return image
    }

    private companion object {
        /** The body's extent in artwork units, with a hair of margin. */
        val BodyBounds = Rect(250f, 466f, 1351f, 1154f)
    }

    internal val bodyBounds: Rect get() = BodyBounds
}

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
    val cache = remember { OrobiRenderCache() }
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
        drawOrobi(pose(), colors, cache, showShadow = showShadow)
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
    // Idle motion is a slow breath, a blink and the odd glance: at 30 ticks a
    // second it looks the same and the screen it sits on can idle between
    // frames (the Home bar shows it for as long as Home is open).
    val clock = rememberAnimationClock(running = playing && !reduced, maxFps = IdleFps)
    OrobiMascot(
        pose = { OrobiMotion.idle(clock.value, look) },
        modifier = modifier,
        colors = colors,
        showShadow = showShadow,
        contentDescription = contentDescription,
    )
}

/** Ticks per second for [OrobiIdle]: enough for a breath and a blink. */
private const val IdleFps = 30

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
    cache: OrobiRenderCache,
    bounds: Rect = Rect(Offset.Zero, size),
    showShadow: Boolean = true,
    alpha: Float = 1f,
    /**
     * Any scale the caller has already applied to the canvas (a scene drawn
     * in its own design units, say), so the cached body is rasterised at
     * real pixel density rather than blurred up from a thumbnail.
     */
    canvasScale: Float = 1f,
) {
    if (alpha <= 0f) return
    val g = OrobiGeometry
    val vb = g.ViewBox
    val pxPerUnit = min(bounds.width / vb.width, bounds.height / vb.height) * canvasScale
    inViewBox(vb, bounds) {
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
                if (pose.bandShift == 0f) {
                    // The common case: stamp the cached raster.
                    val image = cache.bodyBitmap(this, pxPerUnit, colors)
                    val b = cache.bodyBounds
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(b.left.roundToInt(), b.top.roundToInt()),
                        dstSize = IntSize(b.width.roundToInt(), b.height.roundToInt()),
                        alpha = skinAlpha,
                    )
                } else {
                    drawBody(colors, pose.bandShift, skinAlpha)
                }
            }

            val eyeAlpha = (1f - pose.morph * 3f).coerceIn(0f, 1f) * alpha
            if (eyeAlpha > 0f) drawEyes(pose, colors, eyeAlpha)

            val loopAlpha = (pose.morph * 1.5f - 0.25f).coerceIn(0f, 1f) * alpha
            if (loopAlpha > 0f) {
                drawLoop(
                    phase = pose.loaderPhase,
                    trackWidth = 128f - 38f * pose.morph,
                    chaseWidth = 90f,
                    color = colors.marking,
                    trackAlpha = 0.22f,
                    alpha = loopAlpha,
                    scratch = cache.path,
                )
            }
        }
    }
}

/** The body and its markings, in artwork units. */
private fun DrawScope.drawBody(colors: OrobiColors, bandShift: Float, alpha: Float) {
    drawPath(OrobiPaths.body, colors.skin, alpha = alpha)
    clipPath(OrobiPaths.body) {
        translate(left = bandShift) {
            drawPath(OrobiPaths.bandLeft, colors.marking, alpha = alpha)
        }
        translate(left = -bandShift) {
            drawPath(OrobiPaths.bandRight, colors.marking, alpha = alpha)
        }
    }
}

/**
 * The eyes.
 *
 * Gaze is clamped to a small travel so the eyes always stay on the face --
 * the right eye sits close to the head's edge, and the old ±24-unit glance
 * pushed it right up against it. Looking sideways also narrows the eyes a
 * touch, which reads as the eyeball turning rather than the eye sliding.
 */
private fun DrawScope.drawEyes(pose: OrobiPose, colors: OrobiColors, alpha: Float) {
    val g = OrobiGeometry
    val lookX = pose.lookX.coerceIn(-g.MaxLookX, g.MaxLookX)
    val lookY = pose.lookY.coerceIn(-g.MaxLookUp, g.MaxLookDown)
    val open = max(0.08f, pose.eyeOpen)
    val turn = 1f - 0.12f * (kotlin.math.abs(lookX) / g.MaxLookX)
    for (eye in arrayOf(g.LeftEye, g.RightEye)) {
        val c = Offset(eye.x + lookX, eye.y + lookY)
        scale(scaleX = turn, scaleY = open, pivot = c) {
            drawOval(
                color = colors.eye,
                topLeft = Offset(c.x - g.EyeRadiusX, c.y - g.EyeRadiusY),
                size = Size(g.EyeRadiusX * 2f, g.EyeRadiusY * 2f),
                alpha = alpha,
            )
        }
        // The glint stays put relative to the light, not the eye, and hides
        // while the eye is nearly shut.
        if (open > 0.55f) {
            drawCircle(
                color = colors.glint,
                radius = g.GlintRadius,
                center = Offset(c.x + g.GlintOffset.x * turn, c.y + g.GlintOffset.y * open),
                alpha = alpha * ((open - 0.55f) / 0.45f).coerceIn(0f, 1f),
            )
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
