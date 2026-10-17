package com.oro.app.ui.brand

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

/** Text with a highlight travelling left to right, like light along a line. */
@Composable
fun ShimmerText(text: String, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface
    val reduced = rememberReducedMotion()
    if (reduced) {
        Text(text, style = style, color = base)
        return
    }
    val sweep by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerSweep",
    )
    // In pixels relative to a nominal 160px span; the text is short, so a
    // fixed span keeps the band the same width whatever the label.
    val span = 160f
    Text(
        text = text,
        style = style.copy(
            brush = Brush.linearGradient(
                0f to base, 0.4f to base, 0.5f to highlight, 0.6f to base, 1f to base,
                start = Offset(sweep * span - span, 0f),
                end = Offset(sweep * span + span, 0f),
            )
        ),
    )
}
