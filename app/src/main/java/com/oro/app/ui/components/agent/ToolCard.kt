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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
 * ### Row, then sheet
 *
 * The row is one line: icon, verb, subject, and the +/- badge if anything
 * changed. Tapping it opens a sheet shaped for the kind of call (see
 * [SheetKind]); nothing expands in place.
 *
 * The leading icon sits in an Expressive silhouette whose shape comes from
 * the tool's own kind, so a run of calls is distinguishable by outline before
 * any text is read.
 */
@Composable
fun ToolCard(
    entry: ToolCallEntry,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") initiallyExpanded: Boolean = false
) {
    val presentation = ToolPresentations[entry.toolName]
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

    // A row in the transcript. Tapping it opens the detail in a sheet built
    // for that kind of call (diff, file, terminal, listing) instead of
    // expanding in place: expanding pushed the conversation around and gave
    // every kind of call the same cramped preview.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OroCorners.medium,
        color = Color.Transparent
    ) {
        ToolCardHeader(
            presentation = presentation,
            entry = entry,
            shortSubject = shortSubject,
            additions = additions,
            deletions = deletions,
            expanded = false,
            onToggle = { sheetOpen = true }
        )
    }

    if (sheetOpen) {
        ToolDetailSheet(
            entry = entry,
            presentation = presentation,
            diff = diff,
            additions = additions,
            deletions = deletions,
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
    val meta = remember(entry.output, presentation.detail, entry.status) { metaFor(presentation.detail, entry) }

    // One line per call: coloured glyph, verb, subject, and what came of it
    // ("124 lines", "+38 -2"). A log of work, readable at a glance; tapping
    // the row discloses the result.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OroCorners.small)
            .clickable(
                enabled = true,
                onClickLabel = "Show details",
                onClick = onToggle
            )
            .padding(horizontal = Space.xs, vertical = Space.xs + Space.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Neutral glyph on a soft tile: the verb and file name carry the meaning; colour is kept for +/- and errors.
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(OroCorners.small)
                .background(if (isError) scheme.errorContainer else scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isError) Icons.Outlined.ErrorOutline else presentation.icon,
                contentDescription = null,
                tint = if (isError) scheme.onErrorContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(Space.md - Space.xxs))
        // "Wrote **index.html**": the verb reads as context, the file is what the eye looks for.
        Text(
            text = if (running) presentation.running else presentation.done,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 1
        )
        if (shortSubject != null) {
            Spacer(Modifier.width(Space.xs + Space.xxs))
            Text(
                text = shortSubject,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = scheme.onSurface,
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
                    "+$additions", style = MaterialTheme.typography.labelLarge,
                    color = DiffAdd
                )
                if (additions > 0 && deletions > 0) Spacer(Modifier.width(Space.xs))
                if (deletions > 0) Text(
                    "−$deletions", style = MaterialTheme.typography.labelLarge,
                    color = DiffDel
                )
            }
            meta != null -> Text(meta, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        if (!running) {
            Spacer(Modifier.width(Space.xxs))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                tint = scheme.outline, modifier = Modifier.size(18.dp)
            )
        }
    }
}

private val READ_HEADER = Regex("""^File: .*\((?:(\d+) lines|lines \d+-\d+ of (\d+))\)""")
private val DiffAdd = Color(0xFF2E9E62)
private val DiffDel = Color(0xFFE5534B)

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

/** What a call's sheet shows, decided from the tool and what it returned. */
private enum class SheetKind { Edit, NewFile, Delete, Read, Terminal, Listing, Prose, Plain }

private fun sheetKindFor(entry: ToolCallEntry, presentation: ToolPresentation, diff: DiffUtils.DiffResult?): SheetKind {
    val n = com.oro.app.data.ai.tools.ToolAliases.canonicalName(entry.toolName)
    return when {
        n.startsWith("delete") -> SheetKind.Delete
        diff != null && entry.oldContent.isNullOrEmpty() && !entry.newContent.isNullOrEmpty() -> SheetKind.NewFile
        diff != null && diff.hasChanges -> SheetKind.Edit
        n == "read_file" -> SheetKind.Read
        presentation.detail == ToolDetailKind.Terminal -> SheetKind.Terminal
        presentation.detail == ToolDetailKind.Listing -> SheetKind.Listing
        presentation.detail == ToolDetailKind.Prose -> SheetKind.Prose
        presentation.detail == ToolDetailKind.Code -> SheetKind.Read
        else -> SheetKind.Plain
    }
}

/**
 * The detail of one call, in a sheet shaped by what the call did.
 *
 * Edits get a Changes / File switch, new files their content with line
 * numbers, commands a terminal, reads a code view, searches a list. Code and
 * terminal output scroll sideways rather than wrap: wrapping breaks the
 * column alignment that makes a diff or a stack trace readable.
 */
@Composable
private fun ToolDetailSheet(
    entry: ToolCallEntry,
    presentation: ToolPresentation,
    diff: DiffUtils.DiffResult?,
    additions: Int,
    deletions: Int,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val scheme = MaterialTheme.colorScheme
    val body = entry.output.orEmpty()
    val kind = remember(entry, diff) { sheetKindFor(entry, presentation, diff) }
    val isError = entry.status == ToolCallStatus.Error
    val running = entry.status == ToolCallStatus.Running
    var showFile by remember { mutableStateOf(kind == SheetKind.NewFile) }
    val accent = scheme.onSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = scheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
        ) {
            // Header: what happened, to what, and how it went.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(ExpressiveShapes.cookie6())
                        .background(if (isError) scheme.errorContainer else scheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isError) Icons.Outlined.ErrorOutline else presentation.icon, null,
                        tint = if (isError) scheme.error else accent, modifier = Modifier.size(Sizes.icon)
                    )
                }
                Spacer(Modifier.width(Space.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (running) presentation.running else presentation.done,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    entry.subject?.takeIf { it.isNotBlank() }?.let { full ->
                        Text(
                            text = full,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(if (showFile || kind == SheetKind.Edit) entry.newContent ?: body else body))
                }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.icon))
                }
            }

            Spacer(Modifier.height(Space.sm))
            // Facts as chips: status, size of the change, lines.
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    when {
                        running -> "Running"
                        isError -> "Failed"
                        kind == SheetKind.NewFile -> "New file"
                        kind == SheetKind.Delete -> "Deleted"
                        else -> "Done"
                    },
                    when {
                        isError || kind == SheetKind.Delete -> scheme.error
                        kind == SheetKind.NewFile -> DiffAdd
                        else -> scheme.onSurfaceVariant
                    }
                )
                if (additions > 0 || deletions > 0) DiffSummaryBadge(additions = additions, deletions = deletions)
                val lineCount = remember(entry.newContent, body) {
                    (if (kind == SheetKind.Edit || kind == SheetKind.NewFile) entry.newContent else body)
                        ?.takeIf { it.isNotEmpty() }?.lineSequence()?.count()
                }
                if (lineCount != null && kind != SheetKind.Prose) {
                    Text("$lineCount lines", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }

            if (kind == SheetKind.Edit) {
                Spacer(Modifier.height(Space.md))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !showFile, onClick = { showFile = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Changes") }
                    SegmentedButton(
                        selected = showFile, onClick = { showFile = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("File") }
                }
            }

            Spacer(Modifier.height(Space.md))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                if (isError && body.isNotBlank()) {
                    Surface(shape = OroCorners.medium, color = scheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            body.take(8_000), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = scheme.onErrorContainer, modifier = Modifier.padding(Space.md)
                        )
                    }
                }
                when (kind) {
                    SheetKind.Edit ->
                        if (showFile) CodeLines(entry.newContent.orEmpty())
                        else diff?.let { DiffContent(diffResult = it) }
                    SheetKind.NewFile -> CodeLines(entry.newContent.orEmpty())
                    SheetKind.Delete -> {
                        val gone = entry.oldContent
                        if (!gone.isNullOrEmpty()) CodeLines(gone, removed = true)
                        else if (!isError) Text(
                            "The file was removed. Restore it from the checkpoint timeline if you need it back.",
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant
                        )
                    }
                    SheetKind.Read -> if (!isError && body.isNotBlank()) CodeLines(stripReadHeader(body))
                    SheetKind.Terminal -> if (!isError || body.isBlank()) TerminalBlock(entry.subject, body, running)
                    SheetKind.Listing -> if (!isError) ListingBlock(body)
                    SheetKind.Prose -> if (!isError && body.isNotBlank()) MarkdownText(markdown = body, modifier = Modifier.fillMaxWidth())
                    SheetKind.Plain -> if (!isError && body.isNotBlank()) MonospaceBlock(text = body, listing = false)
                }
                if (running && body.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OrobiLoader(modifier = Modifier.width(Sizes.iconLarge), contentDescription = null)
                        Spacer(Modifier.width(Space.sm))
                        Text("Working…", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Surface(shape = OroCorners.pill, color = color.copy(alpha = 0.12f)) {
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = color,
            modifier = Modifier.padding(horizontal = Space.sm + Space.xxs, vertical = Space.xxs)
        )
    }
}

/** read_file's first line is "File: path (N lines)"; the sheet header already says that. */
private fun stripReadHeader(out: String): String {
    val first = out.lineSequence().firstOrNull().orEmpty()
    return if (READ_HEADER.containsMatchIn(first)) out.substringAfter('\n', "") else out
}

/** Code with a line-number gutter; scrolls sideways, never wraps. */
@Composable
private fun CodeLines(text: String, removed: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val lines = remember(text) { text.lineSequence().take(MAX_SHEET_LINES).toList() }
    // Numbered lines like "  12  text": strip a leading "12\t" or "12: " if the tool already numbered them.
    val numbered = remember(lines) { lines.count { NUMBERED.matches(it) } > lines.size / 2 }
    val gutter = remember(lines) { lines.indices.joinToString("\n") { (it + 1).toString() } }
    val code = remember(lines, numbered) { (if (numbered) lines.map { NUMBERED.replace(it, "$1") } else lines).joinToString("\n") }
    val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OroCorners.medium,
        color = if (removed) scheme.errorContainer.copy(alpha = 0.35f) else scheme.surfaceContainerHigh
    ) {
        Row(Modifier.padding(vertical = Space.sm)) {
            Text(
                gutter, style = style, color = scheme.outline,
                modifier = Modifier.padding(start = Space.sm, end = Space.sm)
            )
            Text(
                code, style = style, color = scheme.onSurface,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(end = Space.md)
            )
        }
    }
}

private val NUMBERED = Regex("""^\s*\d+(?:\t|: |\| )(.*)$""")

/** Command output as a terminal: dark in both themes, the command as a prompt line. */
@Composable
private fun TerminalBlock(command: String?, output: String, running: Boolean) {
    val bg = Color(0xFF0D1117)
    Surface(modifier = Modifier.fillMaxWidth(), shape = OroCorners.medium, color = bg) {
        Column(Modifier.padding(Space.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Color(0xFFFF5F56), Color(0xFFFFBD2E), Color(0xFF27C93F)).forEach {
                    Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(it))
                }
            }
            Spacer(Modifier.height(Space.sm))
            val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                if (!command.isNullOrBlank()) Text("$ $command", style = style, color = Color(0xFF7EE787))
                if (output.isNotBlank()) Text(output.lineSequence().take(MAX_SHEET_LINES).joinToString("\n"), style = style, color = Color(0xFFE6EDF3))
                else if (!running) Text("(no output)", style = style, color = Color(0xFF8B949E))
            }
        }
    }
}

/** Search hits and directory listings, one row each with a folder/file glyph. */
@Composable
private fun ListingBlock(output: String) {
    val scheme = MaterialTheme.colorScheme
    val rows = remember(output) { output.lineSequence().filter { it.isNotBlank() }.take(600).toList() }
    if (rows.isEmpty()) {
        Text("Nothing found.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        return
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = OroCorners.medium, color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(vertical = Space.xs)) {
            rows.forEach { row ->
                val trimmed = row.trim()
                val isDir = trimmed.endsWith("/")
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xxs + 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isDir) Icons.Outlined.Folder else Icons.Outlined.Description, null,
                        tint = if (isDir) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        row.trimEnd(), style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
            text = text.lineSequence().take(MAX_SHEET_LINES).joinToString("\n"),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (listing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.md, vertical = Space.sm)
        )
    }
}

/** Longest text a sheet renders; the copy action still copies everything. */
private const val MAX_SHEET_LINES = 4_000
