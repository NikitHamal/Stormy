package com.oro.app.ui.brand

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The brand sparkle as a glyph, in the current content colour by default. */
@Composable
fun OroSparkle(modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    val path = remember { Path() }
    Canvas(modifier) {
        drawPath(sparklePath(center, size.minDimension / 2f, into = path), color)
    }
}

/**
 * "Orobi is working": the infinity loader beside a label with a slow light
 * sweep across it. The one indicator for any wait on the assistant, so
 * thinking, streaming and tool runs all look like the same character busy.
 */
@Composable
fun OrobiWorking(
    label: String,
    modifier: Modifier = Modifier,
    loaderWidth: Dp = 26.dp,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OrobiLoader(modifier = Modifier.width(loaderWidth), contentDescription = null)
        ShimmerText(label, style)
    }
}

/**
 * Text with a highlight travelling left to right, like light along a line.
 *
 * The sweep is applied in the draw phase -- the text is laid out and composed
 * once, and only a gradient band is redrawn over it each frame -- so a label
 * that shimmers for a whole agent run costs no recomposition.
 */
@Composable
fun ShimmerText(text: String, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface
    val reduced = rememberReducedMotion()
    val clock = rememberAnimationClock(running = !reduced)
    Text(
        text = text,
        style = style,
        color = base,
        modifier = if (reduced) {
            Modifier
        } else {
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val band = size.width.coerceAtLeast(80f) * 0.5f
                    val p = (clock.value / SweepSeconds).mod(1f)
                    val x = -band + (size.width + band * 2f) * p
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to highlight,
                            1f to Color.Transparent,
                            startX = x - band,
                            endX = x + band,
                        ),
                        // Only where there is already text.
                        blendMode = BlendMode.SrcAtop,
                    )
                }
        },
    )
}

private const val SweepSeconds = 1.8f
