package com.oro.app.ui.components.agent

import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.ui.components.MarkdownText
import com.oro.app.ui.components.diff.DiffContent
import com.oro.app.ui.components.diff.DiffSummaryBadge
import com.oro.app.ui.components.expressive.ExpressiveLoadingIndicator
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.ExpressiveShapes
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space
import com.oro.app.utils.DiffUtils

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

    // A row in the transcript, not a card. Boxed and bordered, a run of a
    // dozen calls turned the thread into a stack of form fields; as plain
    // rows they read as a log of what Orobi did, and only the detail a user
    // opens gets a surface of its own.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OroCorners.medium,
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
    val scheme = MaterialTheme.colorScheme
    val accent = toolAccent(entry.toolName, scheme.primary)
    val meta = remember(entry.output, presentation.detail, entry.status) { metaFor(presentation.detail, entry) }

    // One line per call: coloured glyph, verb, subject, and what came of it
    // ("124 lines", "+38 -2"). A log of work, readable at a glance; tapping
    // the row discloses the result.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OroCorners.small)
            .clickable(
                enabled = entry.hasDetail,
                onClickLabel = if (expanded) "Hide result" else "Show result",
                onClick = onToggle
            )
            .padding(horizontal = Space.xs, vertical = Space.xs + Space.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isError) Icons.Outlined.ErrorOutline else presentation.icon,
            contentDescription = null,
            tint = if (isError) scheme.error else accent,
            modifier = Modifier.size(Sizes.iconSmall + 2.dp)
        )
        Spacer(Modifier.width(Space.md - Space.xxs))
        Text(
            text = if (running) presentation.running else presentation.done,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = scheme.onSurface,
            maxLines = 1
        )
        if (shortSubject != null) {
            Spacer(Modifier.width(Space.sm))
            Text(
                text = shortSubject,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = if (presentation.subjectIsPath) FontFamily.Monospace else FontFamily.Default
                ),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.width(Space.sm))
        when {
            running -> OrobiLoader(modifier = Modifier.width(Sizes.iconLarge), contentDescription = "Running")
            isError -> Text("Failed", style = MaterialTheme.typography.labelMedium, color = scheme.error)
            additions > 0 || deletions > 0 -> Row {
                if (additions > 0) Text(
                    "+$additions", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = DiffAdd
                )
                if (additions > 0 && deletions > 0) Spacer(Modifier.width(Space.xs))
                if (deletions > 0) Text(
                    "−$deletions", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = DiffDel
                )
            }
            meta != null -> Text(meta, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
    }
}

private val READ_HEADER = Regex("""^File: .*\((?:(\d+) lines|lines \d+-\d+ of (\d+))\)""")
private val DiffAdd = Color(0xFF3DBE7A)
private val DiffDel = Color(0xFFE5534B)

/** A muted hue per kind of work, so a run of calls is scannable by colour before it is read. */
private fun toolAccent(toolName: String, fallback: Color): Color {
    val n = com.oro.app.data.ai.tools.ToolAliases.canonicalName(toolName)
    return when {
        n.startsWith("read") || n == "list_files" || n == "glob" || n == "project_tree" -> Color(0xFF6E8CF7)
        n.contains("edit") || n.startsWith("write") || n.startsWith("create") || n == "apply_patch" -> Color(0xFFF0965A)
        n == "grep" || n.contains("search") -> Color(0xFFB285F2)
        n.startsWith("delete") || n.startsWith("move") || n.startsWith("rename") -> Color(0xFFE5737B)
        n.startsWith("web") || n.contains("fetch") || n.startsWith("mcp") -> Color(0xFF4FC1B6)
        n.startsWith("git") || n.startsWith("deploy") -> Color(0xFF8BC34A)
        else -> fallback
    }
}

/** "124 lines" / "12 items" from a finished call's output, when it says something useful. */
private fun metaFor(kind: ToolDetailKind, entry: ToolCallEntry): String? {
    if (entry.status != ToolCallStatus.Success) return null
    val out = entry.output?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // read_file states the true size in its header; the body is truncated.
    READ_HEADER.find(out.lineSequence().first())?.let { m ->
        return m.groupValues[2].ifEmpty { m.groupValues[1] }.let { "$it lines" }
    }
    val lines = out.lineSequence().count { it.isNotBlank() }
    return when (kind) {
        ToolDetailKind.Code -> if (lines == 1) "1 line" else "$lines lines"
        ToolDetailKind.Listing -> if (lines == 1) "1 item" else "$lines items"
        else -> null
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
    // Just the glyph: the status on the right already says running/failed,
    // so the icon only has to say what kind of thing happened.
    Box(modifier = Modifier.size(Sizes.icon), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = presentation.icon,
            contentDescription = null,
            tint = when {
                isError -> MaterialTheme.colorScheme.error
                running -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(Sizes.iconSmall)
        )
    }
}

@Composable
private fun StatusGlyph(status: ToolCallStatus) {
    when (status) {
        // Orobi's loader, sized to the row's glyph slot so a running row
        // and a finished one line up.
        ToolCallStatus.Running -> OrobiLoader(
            modifier = Modifier.width(Sizes.iconLarge),
            contentDescription = "Running"
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
        shape = OroCorners.medium,
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
