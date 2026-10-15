package com.codex.stormy.ui.components.agent

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.theme.CodeXCorners
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Space

/** One tool call inside a [ToolCallGroup]. */
data class ToolCallEntry(
    val toolName: String,
    /** The path, command or query the call acted on. */
    val subject: String?,
    val status: ToolCallStatus,
    val additions: Int = 0,
    val deletions: Int = 0
)

enum class ToolCallStatus { Running, Success, Error }

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
 * ### Layout
 *
 * Each row is verb on the left, subject on the right in a monospaced chip.
 * Splitting them that way lets the eye run down the verbs to see the *shape*
 * of what happened -- read, read, write, run -- without parsing paths, while
 * the paths stay available in a column of their own.
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
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.icon)
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
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
        ) {
            Column(
                modifier = Modifier.padding(start = Space.lg, top = Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                reasoning?.let { ReasoningRow(it) }
                entries.forEach { entry -> ToolRow(entry) }
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

@Composable
private fun ReasoningRow(reasoning: ReasoningEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.iconSmall)
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            text = "Thinking",
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(Space.sm))
        SubjectChip(
            // The first line only: reasoning runs to paragraphs, and the point
            // of this row is that it is a one-line trace, not a transcript.
            text = reasoning.text.lineSequence().firstOrNull()?.trim().orEmpty(),
            monospace = false,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun ToolRow(entry: ToolCallEntry) {
    val presentation = ToolPresentations[entry.toolName]
    val running = entry.status == ToolCallStatus.Running
    val subject = entry.subject?.takeIf { it.isNotBlank() }?.let { raw ->
        if (presentation.subjectIsPath) ToolPresentations.shortenPath(raw) else raw
    }

    Row(
        modifier = Modifier.alpha(if (entry.status == ToolCallStatus.Error) 0.75f else 1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = presentation.icon,
            contentDescription = null,
            tint = if (entry.status == ToolCallStatus.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(Sizes.iconSmall)
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            // Tense carries the status: "Writing" while it runs, "Wrote" once
            // done. More reliable than a colour, which the eye skips.
            text = if (running) presentation.running else presentation.done,
            style = if (running) {
                MaterialTheme.typography.labelLargeEmphasized
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        if (subject != null) {
            Spacer(Modifier.width(Space.sm))
            SubjectChip(
                text = subject,
                monospace = presentation.subjectIsPath,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun SubjectChip(
    text: String,
    monospace: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CodeXCorners.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.let {
                if (monospace) it.copy(fontFamily = FontFamily.Monospace) else it
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xxs)
        )
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
                shape = CodeXCorners.small,
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
private fun RunningDots() {
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
                    shape = CodeXCorners.pill,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    content = {}
                )
            }
        }
    }
}

private const val MAX_FILE_CHIPS = 4
