package com.codex.stormy.ui.components.expressive

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.theme.Sizes

/**
 * An organic, slightly irregular rounded shape.
 *
 * CSS can give each corner a different horizontal and vertical radius
 * (`border-radius: 42% 58% 46% 54% / 54% 42% 58% 46%`), which is what makes
 * the reference buttons read as soft blobs rather than squircles.
 * [androidx.compose.foundation.shape.RoundedCornerShape] only takes one radius
 * per corner, so it cannot express that asymmetry -- this builds the outline
 * from a [RoundRect] with independent x and y radii instead.
 *
 * The percentages are relative to the button's own size, so one shape works at
 * any dimension.
 */
class BlobShape(
    private val topStart: Pair<Float, Float>,
    private val topEnd: Pair<Float, Float>,
    private val bottomEnd: Pair<Float, Float>,
    private val bottomStart: Pair<Float, Float>
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        fun corner(pair: Pair<Float, Float>) =
            CornerRadius(size.width * pair.first, size.height * pair.second)

        // Mirrored for RTL, so the irregularity leans the same way relative to
        // the reading direction rather than flipping the button's character.
        val (start, end) = if (layoutDirection == LayoutDirection.Ltr) {
            (topStart to bottomStart) to (topEnd to bottomEnd)
        } else {
            (topEnd to bottomEnd) to (topStart to bottomStart)
        }

        return Outline.Rounded(
            RoundRect(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                topLeft = corner(start.first),
                topRight = corner(end.first),
                bottomRight = corner(end.second),
                bottomLeft = corner(start.second)
            )
        )
    }

    companion object {
        /** Matches the reference's primary button. */
        val Primary = BlobShape(
            topStart = 0.42f to 0.54f,
            topEnd = 0.58f to 0.42f,
            bottomEnd = 0.46f to 0.58f,
            bottomStart = 0.54f to 0.46f
        )

        /**
         * The alternate lean. Two adjacent buttons using the same blob read as
         * a rendering artefact; different ones read as deliberate.
         */
        val Alternate = BlobShape(
            topStart = 0.58f to 0.42f,
            topEnd = 0.42f to 0.58f,
            bottomEnd = 0.38f to 0.62f,
            bottomStart = 0.62f to 0.38f
        )

        /** Pressed state: a plain circle. */
        val Circle = BlobShape(
            topStart = 0.5f to 0.5f,
            topEnd = 0.5f to 0.5f,
            bottomEnd = 0.5f to 0.5f,
            bottomStart = 0.5f to 0.5f
        )
    }
}

/**
 * Interpolate between two blobs, so the press can *morph* the shape rather
 * than swap it. A swap at this size reads as a glitch; the morph is the whole
 * effect.
 */
private fun lerpBlob(from: BlobShape, to: BlobShape, t: Float): Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val a = from.createOutline(size, layoutDirection, density) as Outline.Rounded
        val b = to.createOutline(size, layoutDirection, density) as Outline.Rounded
        fun mix(x: CornerRadius, y: CornerRadius) = CornerRadius(
            x.x + (y.x - x.x) * t,
            x.y + (y.y - x.y) * t
        )
        return Outline.Rounded(
            RoundRect(
                rect = a.roundRect.let {
                    androidx.compose.ui.geometry.Rect(it.left, it.top, it.right, it.bottom)
                },
                topLeft = mix(a.roundRect.topLeftCornerRadius, b.roundRect.topLeftCornerRadius),
                topRight = mix(a.roundRect.topRightCornerRadius, b.roundRect.topRightCornerRadius),
                bottomRight = mix(
                    a.roundRect.bottomRightCornerRadius,
                    b.roundRect.bottomRightCornerRadius
                ),
                bottomLeft = mix(
                    a.roundRect.bottomLeftCornerRadius,
                    b.roundRect.bottomLeftCornerRadius
                )
            )
        )
    }
}

/**
 * Top-bar icon button with an organic silhouette.
 *
 * Pressing it shrinks the button and rounds the blob toward a circle at the
 * same time. Both halves of that matter: the scale gives the tactile response,
 * and the shape settling to a circle is what makes the button feel like it is
 * made of something soft rather than being a rectangle that got smaller.
 */
@Composable
fun FlowerIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alternate: Boolean = false,
    size: Dp = 48.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    badge: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val morph = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }

    // Spring, not tween: the reference uses a bouncy spatial curve, and the
    // overshoot on release is what sells the softness.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(pressed, spatial) {
        morph.animateTo(if (pressed) 1f else 0f, spatial)
    }
    LaunchedEffect(pressed, spatial) {
        scale.animateTo(if (pressed) 0.92f else 1f, spatial)
    }

    val base = if (alternate) BlobShape.Alternate else BlobShape.Primary

    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .scale(scale.value),
            shape = lerpBlob(base, BlobShape.Circle, morph.value),
            color = container,
            contentColor = content,
            interactionSource = interaction
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(Sizes.iconLarge)
                )
            }
        }
        badge?.invoke()
    }
}
