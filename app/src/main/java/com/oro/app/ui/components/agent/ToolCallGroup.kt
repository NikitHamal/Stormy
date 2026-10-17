package com.oro.app.ui.components.agent

import com.oro.app.ui.brand.OroSparkle
import com.oro.app.ui.brand.OrobiLoader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.ExpressiveShapes
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/** A reasoning block that sits inside the same group as the tool calls. */
data class ReasoningEntry(val text: String, val isActive: Boolean)

/**
 * A run of tool calls, collapsed into one block.
 *
 * ### Why group at all
 *
 * A single agent turn can make a dozen calls. Rendered as a dozen separate
 * cards they dominate the transcript, pushing the assistant's actual answer --
 * the thing the user is reading for -- off screen. Collapsed into one row the
 * run occupies a line, states what it did, and expands when the user wants the
 * detail.
 *
 * The summary line counts rather than lists, because "7 tool calls" is a
 * better scan target than seven truncated verbs.
 *
 * ### What expanding gives you
 *
 * A stack of [ToolCard]s, each of which opens its own result -- a diff for a
 * write, terminal output for a command, hits for a search. The group used to
 * expand to a list of verb-and-path rows with the results thrown away, which
 * made it a table of contents for a document that did not exist.
 *
 * Changed files are summarised beneath as chips with their +/- counts, so the
 * blast radius of the turn is visible without expanding anything.
 */
@Composable
fun ToolCallGroup(
    entries: List<ToolCallEntry>,
    modifier: Modifier = Modifier,
    reasoning: ReasoningEntry? = null,
    messageCount: Int = 0,
    initiallyExpanded: Boolean = false
) {
    if (entries.isEmpty() && reasoning == null) return

    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val anyRunning = entries.any { it.status == ToolCallStatus.Running } ||
        reasoning?.isActive == true
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "groupChevron"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // --- Summary line -------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = if (expanded) "Hide tool calls" else "Show tool calls"
                ) { expanded = !expanded }
                .padding(vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(Sizes.icon)
                    .rotate(chevronRotation)
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                text = summaryLabel(entries.size, messageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (anyRunning) {
                Spacer(Modifier.width(Space.sm))
                RunningDots()
            }
            Spacer(Modifier.weight(1f))
            // The icons of the calls in the run, collapsed. Gives the shape of
            // the turn -- read, read, write, run -- without opening it.
            if (!expanded) {
                ToolIconTrail(entries)
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
        ) {
            Column(
                modifier = Modifier.padding(top = Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                reasoning?.let { ReasoningCard(it) }
                entries.forEach { entry ->
                    ToolCard(
                        entry = entry,
                        // The running call is the one the user is watching, so
                        // it opens itself; finished ones stay shut.
                        initiallyExpanded = entry.status == ToolCallStatus.Running
                    )
                }
            }
        }

        // --- Changed files, always visible --------------------------------
        val changed = entries.filter { it.additions > 0 || it.deletions > 0 }
        if (changed.isNotEmpty()) {
            Spacer(Modifier.height(Space.sm))
            ChangedFileChips(changed)
        }
    }
}

private fun summaryLabel(toolCount: Int, messageCount: Int): String = buildString {
    append(toolCount)
    append(if (toolCount == 1) " tool call" else " tool calls")
    if (messageCount > 0) {
        append(", ")
        append(messageCount)
        append(if (messageCount == 1) " message" else " messages")
    }
}

/** The distinct tool icons in a run, as a small trail on the summary row. */
@Composable
private fun ToolIconTrail(entries: List<ToolCallEntry>) {
    val icons = remember(entries) {
        entries.map { ToolPresentations[it.toolName].icon }
            .distinct()
            .take(MAX_TRAIL_ICONS)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        icons.forEach { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(Sizes.iconSmall)
                    .alpha(0.7f)
            )
        }
    }
}

/**
 * The model's reasoning for this turn.
 *
 * Previously headed by a generic sparkle, which said "AI" -- true of every
 * row in the thread and so informative about none of them. A head with a
 * thought in it says what this block actually is, and the Expressive
 * silhouette distinguishes it from the tool cards below without a label.
 */
@Composable
private fun ReasoningCard(reasoning: ReasoningEntry) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "reasoningChevron"
    )
    val firstLine = remember(reasoning.text) {
        reasoning.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }
    val hasMore = reasoning.text.trim() != firstLine

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = hasMore,
                        onClickLabel = if (expanded) "Hide reasoning" else "Show reasoning"
                    ) { expanded = !expanded }
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(Sizes.avatar),
                    shape = ExpressiveShapes.cookie6(),
                    color = if (reasoning.isActive) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (reasoning.isActive) {
                            OrobiLoader(
                                modifier = Modifier.width(Sizes.icon),
                                contentDescription = null
                            )
                        } else {
                            OroSparkle(
                                modifier = Modifier.size(Sizes.iconSmall - 4.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(Modifier.width(Space.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (reasoning.isActive) "Thinking" else "Thought",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!expanded) {
                        Text(
                            text = firstLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (reasoning.isActive) {
                    RunningDots()
                    Spacer(Modifier.width(Space.xs))
                }

                if (hasMore) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(Sizes.icon)
                            .rotate(chevronRotation)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
            ) {
                Text(
                    text = reasoning.text.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = Space.md,
                        end = Space.md,
                        bottom = Space.md
                    )
                )
            }
        }
    }
}

/**
 * Changed files as chips with their line counts.
 *
 * Capped, with a "+N more" tail: beyond a handful the chips wrap into a block
 * that costs more attention than the information is worth, and the exact list
 * is available by expanding the group.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChangedFileChips(entries: List<ToolCallEntry>) {
    val shown = entries.take(MAX_FILE_CHIPS)
    val overflow = entries.size - shown.size

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        shown.forEach { entry ->
            Surface(
                shape = OroCorners.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.subject?.substringAfterLast('/').orEmpty(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (entry.additions > 0) {
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            text = "+${entry.additions}",
                            style = MaterialTheme.typography.labelMedium,
                            // Diff keeps a hue, muted -- see DiffColors. The
                            // sign is doing the work; the tint only speeds it.
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (entry.deletions > 0) {
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            text = "-${entry.deletions}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        if (overflow > 0) {
            Text(
                text = "+$overflow more",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Space.xs)
            )
        }
    }
}

/** Three dots that fade in sequence while a run is in flight. */
@Composable
internal fun RunningDots() {
    val transition = rememberInfiniteTransition(label = "toolDots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                // Infinite, so it cannot come from the motion scheme -- those
                // specs are finite by type. Offset per dot so they chase.
                animationSpec = infiniteRepeatable(
                    animation = tween(560, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "toolDot$index"
            )
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .alpha(alpha)
            ) {
                Surface(
                    shape = OroCorners.pill,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    content = {}
                )
            }
        }
    }
}

private const val MAX_FILE_CHIPS = 4
private const val MAX_TRAIL_ICONS = 5
