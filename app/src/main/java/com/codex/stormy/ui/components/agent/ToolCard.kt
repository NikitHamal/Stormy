package com.codex.stormy.ui.components.agent

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.components.MarkdownText
import com.codex.stormy.ui.components.diff.DiffContent
import com.codex.stormy.ui.components.diff.DiffSummaryBadge
import com.codex.stormy.ui.components.expressive.ExpressiveLoadingIndicator
import com.codex.stormy.ui.theme.CodeXCorners
import com.codex.stormy.ui.theme.ExpressiveShapes
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Space
import com.codex.stormy.utils.DiffUtils

/**
 * Everything one tool call has to say for itself.
 *
 * Kept as a plain value rather than reading from the parser's block type, so
 * the same card serves a lone call, a call inside a collapsed run, and a
 * replayed one from history.
 */
data class ToolCallEntry(
    val toolName: String,
    /** The path, command or query the call acted on. */
    val subject: String?,
    val status: ToolCallStatus,
    val additions: Int = 0,
    val deletions: Int = 0,
    /** Raw result text, when the tool produced one. */
    val output: String? = null,
    /** File content before the edit; paired with [newContent] to make a diff. */
    val oldContent: String? = null,
    val newContent: String? = null
) {
    /** Whether there is anything to show when this card is opened. */
    val hasDetail: Boolean
        get() = !output.isNullOrBlank() || (oldContent != null && newContent != null)
}

enum class ToolCallStatus { Running, Success, Error }

/**
 * One tool call, as a card.
 *
 * ### Why a card rather than a line of text
 *
 * The previous rendering was a verb and a path on one row and nothing else:
 * everything the call actually *produced* -- the diff it applied, the output
 * it printed, the files it found -- was discarded before it reached the
 * screen. Tool calls are the bulk of what an agent does, so dropping their
 * results makes the transcript a list of claims the user cannot check.
 *
 * ### The three depths
 *
 * Collapsed, the card is one row: icon, verb, subject, and the +/- badge if
 * anything changed. Expanded in place, it shows the first [PREVIEW_LINES]
 * lines, which is enough to confirm a write did what it said. The sheet shows
 * the whole thing, scrollable, with a copy action -- reserved for the full
 * detail because a sheet covers the conversation, and that is only worth
 * doing when the user has asked for the detail specifically.
 *
 * The leading icon sits in an Expressive silhouette whose shape comes from
 * the tool's own kind, so a run of calls is distinguishable by outline before
 * any text is read.
 */
@Composable
fun ToolCard(
    entry: ToolCallEntry,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    val presentation = ToolPresentations[entry.toolName]
    val running = entry.status == ToolCallStatus.Running
    val isError = entry.status == ToolCallStatus.Error

    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var sheetOpen by remember { mutableStateOf(false) }

    val shortSubject = entry.subject?.takeIf { it.isNotBlank() }?.let { raw ->
        if (presentation.subjectIsPath) ToolPresentations.shortenPath(raw) else raw
    }

    val diff = remember(entry.oldContent, entry.newContent) {
        if (entry.oldContent != null && entry.newContent != null) {
            DiffUtils.computeDiff(entry.oldContent, entry.newContent, contextLines = 3)
        } else {
            null
        }
    }
    val additions = if (entry.additions > 0) entry.additions else diff?.additions ?: 0
    val deletions = if (entry.deletions > 0) entry.deletions else diff?.deletions ?: 0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CodeXCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            // A hairline rather than elevation: these stack several deep in a
            // run, and tonal elevation on each one turns the thread into a
            // gradient with no readable edges.
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = Sizes.hairline,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CodeXCorners.large
                )
        ) {
            ToolCardHeader(
                presentation = presentation,
                entry = entry,
                shortSubject = shortSubject,
                additions = additions,
                deletions = deletions,
                expanded = expanded,
                onToggle = { if (entry.hasDetail) expanded = !expanded }
            )

            AnimatedVisibility(
                visible = expanded && entry.hasDetail,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
            ) {
                ToolCardDetail(
                    entry = entry,
                    presentation = presentation,
                    diff = diff,
                    onOpenSheet = { sheetOpen = true }
                )
            }

            if (running) {
                ToolCardRunningBar()
            }
        }
    }

    if (sheetOpen) {
        ToolDetailSheet(
            entry = entry,
            presentation = presentation,
            diff = diff,
            onDismiss = { sheetOpen = false }
        )
    }
}

@Composable
private fun ToolCardHeader(
    presentation: ToolPresentation,
    entry: ToolCallEntry,
    shortSubject: String?,
    additions: Int,
    deletions: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val running = entry.status == ToolCallStatus.Running
    val isError = entry.status == ToolCallStatus.Error
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "toolChevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = entry.hasDetail,
                // Names the consequence: the row is a disclosure, not a
                // button called "expand".
                onClickLabel = if (expanded) "Hide result" else "Show result",
                onClick = onToggle
            )
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolIconBadge(presentation = presentation, running = running, isError = isError)

        Spacer(Modifier.width(Space.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                // Tense carries the status: "Writing" while it runs, "Wrote"
                // once done -- more reliable than a colour, which the eye
                // skips. Emphasized rather than larger while running, so the
                // list does not reflow as each call completes.
                text = if (running) presentation.running else presentation.done,
                style = if (running) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (shortSubject != null) {
                Text(
                    text = shortSubject,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = if (presentation.subjectIsPath) {
                            FontFamily.Monospace
                        } else {
                            FontFamily.Default
                        }
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (additions > 0 || deletions > 0) {
            Spacer(Modifier.width(Space.sm))
            DiffSummaryBadge(additions = additions, deletions = deletions)
        }

        Spacer(Modifier.width(Space.xs))
        StatusGlyph(entry.status)

        if (entry.hasDetail) {
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
}

/**
 * The tool's icon in a shape chosen by what the tool does.
 *
 * The shape, not just the glyph, carries the category: at the size an icon
 * occupies in a thread the silhouette resolves before the glyph does, so a
 * column of diffs reads as a column of one shape.
 */
@Composable
private fun ToolIconBadge(
    presentation: ToolPresentation,
    running: Boolean,
    isError: Boolean
) {
    val shape = when (presentation.detail) {
        ToolDetailKind.Diff -> ExpressiveShapes.cookie4()
        ToolDetailKind.Code -> ExpressiveShapes.square()
        ToolDetailKind.Listing -> ExpressiveShapes.arch()
        ToolDetailKind.Terminal -> ExpressiveShapes.slanted()
        ToolDetailKind.Prose -> ExpressiveShapes.clover4()
    }

    val infinite = rememberInfiniteTransition(label = "toolBadge")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        // Infinite, so it cannot come from the motion scheme -- those specs
        // are finite by type.
        animationSpec = infiniteRepeatable(tween(620), repeatMode = RepeatMode.Reverse),
        label = "toolBadgePulse"
    )

    Surface(
        modifier = Modifier
            .size(Sizes.avatar)
            .graphicsLayer {
                if (running) {
                    scaleX = pulse
                    scaleY = pulse
                }
            },
        shape = shape,
        color = when {
            isError -> MaterialTheme.colorScheme.errorContainer
            running -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = presentation.icon,
                contentDescription = null,
                tint = when {
                    isError -> MaterialTheme.colorScheme.onErrorContainer
                    running -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(Sizes.iconSmall)
            )
        }
    }
}

@Composable
private fun StatusGlyph(status: ToolCallStatus) {
    when (status) {
        ToolCallStatus.Running -> ExpressiveLoadingIndicator(
            modifier = Modifier.size(Sizes.iconSmall)
        )

        ToolCallStatus.Success -> Icon(
            imageVector = Icons.Outlined.CheckCircle,
            // The glyph is the only place status is stated for a screen
            // reader; the verb's tense carries it visually.
            contentDescription = "Succeeded",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.iconSmall)
        )

        ToolCallStatus.Error -> Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = "Failed",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(Sizes.iconSmall)
        )
    }
}

/** A thin indeterminate line along the card's bottom edge while it runs. */
@Composable
private fun ToolCardRunningBar() {
    val infinite = rememberInfiniteTransition(label = "toolBar")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100), repeatMode = RepeatMode.Reverse),
        label = "toolBarSweep"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.xs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.25f + sweep * 0.6f)
                .height(2.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = CodeXCorners.pill
                )
        )
    }
}

/** Inline preview: the head of the result, plus the way to see all of it. */
@Composable
private fun ToolCardDetail(
    entry: ToolCallEntry,
    presentation: ToolPresentation,
    diff: DiffUtils.DiffResult?,
    onOpenSheet: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val body = entry.output.orEmpty()
    val lines = remember(body) { body.lineSequence().toList() }
    val truncated = lines.size > PREVIEW_LINES
    val preview = remember(body) { lines.take(PREVIEW_LINES).joinToString("\n") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.md, end = Space.md, bottom = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        when {
            // A diff always wins over the raw output: the output of a write is
            // usually just "ok", and the change is the thing worth seeing.
            diff != null && diff.hasChanges -> DiffContent(
                diffResult = diff,
                modifier = Modifier.heightIn(max = PREVIEW_MAX_HEIGHT)
            )

            presentation.detail == ToolDetailKind.Prose ->
                MarkdownText(markdown = preview, modifier = Modifier.fillMaxWidth())

            body.isNotBlank() -> MonospaceBlock(
                text = preview,
                listing = presentation.detail == ToolDetailKind.Listing
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (truncated || (diff != null && diff.hasChanges)) {
                TextButton(onClick = onOpenSheet) {
                    Icon(
                        Icons.Outlined.OpenInFull,
                        contentDescription = null,
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        text = if (truncated) {
                            "View all ${lines.size} lines"
                        } else {
                            "View full diff"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (body.isNotBlank()) {
                IconButton(onClick = { clipboard.setText(AnnotatedString(body)) }) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy result",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                }
            }
        }
    }
}

/**
 * The whole result, in a sheet.
 *
 * Horizontal scrolling rather than wrapping for code and terminal output:
 * wrapped lines break the column alignment that makes a diff or a stack trace
 * readable, and a wrapped 200-character line is not more legible than a
 * scrolled one.
 */
@Composable
private fun ToolDetailSheet(
    entry: ToolCallEntry,
    presentation: ToolPresentation,
    diff: DiffUtils.DiffResult?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val body = entry.output.orEmpty()

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
                .padding(horizontal = Space.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolIconBadge(
                    presentation = presentation,
                    running = false,
                    isError = entry.status == ToolCallStatus.Error
                )
                Spacer(Modifier.width(Space.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = presentation.done,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    entry.subject?.takeIf { it.isNotBlank() }?.let { full ->
                        Text(
                            // The full path here, not the shortened one: this
                            // is the view the user opened to see detail.
                            text = full,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = {
                        clipboard.setText(
                            AnnotatedString(
                                if (diff != null && diff.hasChanges) {
                                    entry.newContent.orEmpty()
                                } else {
                                    body
                                }
                            )
                        )
                    }
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.icon)
                    )
                }
            }

            Spacer(Modifier.height(Space.md))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = SHEET_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                if (diff != null && diff.hasChanges) {
                    DiffContent(diffResult = diff)
                }
                when {
                    presentation.detail == ToolDetailKind.Prose && body.isNotBlank() ->
                        MarkdownText(markdown = body, modifier = Modifier.fillMaxWidth())

                    body.isNotBlank() -> MonospaceBlock(
                        text = body,
                        listing = presentation.detail == ToolDetailKind.Listing
                    )
                }
            }

            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun MonospaceBlock(text: String, listing: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CodeXCorners.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (listing) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.md, vertical = Space.sm)
        )
    }
}

/** How much of a result is shown without opening the sheet. */
private const val PREVIEW_LINES = 12
private val PREVIEW_MAX_HEIGHT = 220.dp
private val SHEET_MAX_HEIGHT = 480.dp
