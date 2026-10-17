package com.oro.app.ui.components.chat

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.data.ai.context.ContextBreakdown
import com.oro.app.data.ai.context.ContextSegment
import com.oro.app.data.ai.context.ContextUsageLevel
import com.oro.app.ui.components.expressive.ExpressiveInlineLoader
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/**
 * What the context window is being spent on, and what to do about it.
 *
 * A single percentage tells the user they have a problem but not what to do:
 * 72% spent on tool definitions and 72% spent on transcript call for opposite
 * responses -- the first needs a bigger model or fewer tools, the second is
 * exactly what compaction fixes. The breakdown is what makes the number
 * actionable.
 *
 * The bar is segmented rather than a single fill, so the proportions are
 * legible without reading the rows beneath it.
 */
@Composable
fun ContextSheet(
    breakdown: ContextBreakdown,
    compactableMessageCount: Int,
    isCompacting: Boolean,
    lastSavedPercent: Int?,
    onCompact: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.xl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Context window",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${breakdown.percent}%",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = usageColor(breakdown.level)
                )
            }

            Spacer(Modifier.height(Space.xs))

            Text(
                text = "How much of this conversation the model can still see. " +
                    "Compacting summarises the earlier messages and keeps the recent ones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Space.lg))

            SegmentedUsageBar(breakdown)

            Spacer(Modifier.height(Space.lg))

            breakdown.segments.forEachIndexed { index, segment ->
                SegmentRow(
                    segment = segment,
                    breakdown = breakdown,
                    index = index
                )
            }

            // Remaining headroom, stated rather than left as arithmetic.
            SegmentRow(
                segment = ContextSegment(
                    "Free",
                    (breakdown.maxTokens - breakdown.usedTokens).coerceAtLeast(0)
                ),
                breakdown = breakdown,
                index = -1
            )

            Spacer(Modifier.height(Space.lg))

            CompactButton(
                enabled = breakdown.canCompact && !isCompacting && compactableMessageCount > 0,
                isCompacting = isCompacting,
                messageCount = compactableMessageCount,
                onClick = onCompact
            )

            lastSavedPercent?.let { saved ->
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = "Last compaction freed $saved% of the window.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(Space.xl))
        }
    }
}

/**
 * One bar divided by segment, rather than a stack of separate bars.
 *
 * Proportion is the whole point of this control, and separate bars force the
 * user to compare lengths that do not share a baseline.
 */
@Composable
private fun SegmentedUsageBar(breakdown: ContextBreakdown) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(OroCorners.pill)
    ) {
        breakdown.segments.forEachIndexed { index, segment ->
            val share = breakdown.shareOf(segment)
            if (share <= 0f) return@forEachIndexed
            val animated by animateFloatAsState(share, label = "segmentShare")
            Box(
                modifier = Modifier
                    .weight(animated.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(segmentColor(index))
            )
        }
        val free = (1f - breakdown.fraction).coerceAtLeast(0f)
        if (free > 0f) {
            Box(
                modifier = Modifier
                    .weight(free)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        }
    }
}

@Composable
private fun SegmentRow(segment: ContextSegment, breakdown: ContextBreakdown, index: Int) {
    val share = breakdown.shareOf(segment)
    val percent = (share * 100).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = OroCorners.pill,
            color = if (index >= 0) {
                segmentColor(index)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            content = {}
        )
        Spacer(Modifier.width(Space.md))
        Text(
            text = segment.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            // Tokens alongside the percentage: the percentage answers "is this
            // a problem", the token count answers "will the next message fit".
            text = "${formatTokens(segment.tokens)}  ${if (percent < 1) "<1" else "$percent"}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompactButton(
    enabled: Boolean,
    isCompacting: Boolean,
    messageCount: Int,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = OroCorners.xLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        if (isCompacting) {
            ExpressiveInlineLoader(modifier = Modifier.size(Sizes.iconSmall))
            Spacer(Modifier.width(Space.sm))
            Text("Compacting…", style = MaterialTheme.typography.labelLargeEmphasized)
        } else {
            Icon(
                Icons.Outlined.Compress,
                contentDescription = null,
                modifier = Modifier.size(Sizes.icon)
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                // Names the count, so the user knows the scale of what they are
                // agreeing to before pressing it.
                text = if (messageCount > 0) {
                    "Compact $messageCount earlier messages"
                } else {
                    "Nothing to compact yet"
                },
                style = MaterialTheme.typography.labelLargeEmphasized
            )
        }
    }
}

/**
 * Segment colours.
 *
 * Tonal steps, not hues: the palette is monochrome, and the legend dots pair
 * each row to its slice, so the bar does not need colour to be decodable.
 */
@Composable
private fun segmentColor(index: Int): Color {
    val scheme = MaterialTheme.colorScheme
    return when (index % 3) {
        0 -> scheme.onSurface
        1 -> scheme.onSurfaceVariant
        else -> scheme.outline
    }
}

@Composable
private fun usageColor(level: ContextUsageLevel): Color {
    val target = when (level) {
        ContextUsageLevel.LOW, ContextUsageLevel.MEDIUM -> MaterialTheme.colorScheme.onSurface
        ContextUsageLevel.HIGH -> MaterialTheme.colorScheme.onSurfaceVariant
        // Only the genuinely urgent level takes the error hue; using it at 75%
        // would make it meaningless by the time it matters.
        ContextUsageLevel.CRITICAL -> MaterialTheme.colorScheme.error
    }
    val color by animateColorAsState(target, label = "usageColor")
    return color
}

private fun formatTokens(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 100_000 / 10f}M"
    tokens >= 1_000 -> "${tokens / 1_000}k"
    else -> tokens.toString()
}
