package com.oro.app.ui.screens.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oro.app.data.preview.ArtifactHistory
import com.oro.app.data.preview.CanvasBackdrop
import com.oro.app.data.preview.DiffKind
import com.oro.app.data.preview.LineDiff
import com.oro.app.data.preview.PreviewTelemetry
import com.oro.app.data.preview.PreviewViewport
import com.oro.app.data.preview.RuntimeIssue
import com.oro.app.ui.theme.OroCorners
import kotlin.math.roundToInt

/**
 * The strip of runtime errors under the page: the newest error, "Fix with
 * Stormy" (puts the file, position, message and stack into the chat) and a
 * copy button for anywhere else.
 */
@Composable
internal fun RuntimeErrorBanner(
    issues: List<RuntimeIssue>,
    onFix: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.errorContainer, contentColor = scheme.onErrorContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = PreviewTelemetry.headline(issues),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Dismiss error", modifier = Modifier.size(18.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = onFix) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fix with Stormy", maxLines = 1)
                }
                TextButton(onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy error context", maxLines = 1)
                }
            }
        }
    }
}

/**
 * Under the device bar: the layout size as `W × H`, a slider that drags the
 * width live (it snaps to common breakpoints when let go), the canvas
 * backdrop, and, once a file has more than one version, a version chip.
 */
@Composable
internal fun ViewportStudioBar(
    width: Int,
    cssHeight: Int,
    customWidth: Boolean,
    onWidthChange: (Int) -> Unit,
    onWidthSettled: () -> Unit,
    onResetWidth: () -> Unit,
    backdrop: CanvasBackdrop,
    onBackdrop: () -> Unit,
    versionLabel: String?,
    onVersions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = PreviewViewport.label(width, cssHeight),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.width(76.dp)
            )
            Slider(
                value = width.toFloat().coerceIn(PreviewViewport.MIN_WIDTH.toFloat(), PreviewViewport.MAX_WIDTH.toFloat()),
                onValueChange = { onWidthChange(it.roundToInt()) },
                onValueChangeFinished = onWidthSettled,
                valueRange = PreviewViewport.MIN_WIDTH.toFloat()..PreviewViewport.MAX_WIDTH.toFloat(),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Preview width" }
            )
            if (customWidth) {
                IconButton(onClick = onResetWidth, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Replay, contentDescription = "Back to the device width", modifier = Modifier.size(18.dp))
                }
            }
            StudioChip(
                icon = backdrop.icon(),
                label = backdrop.label,
                description = "Canvas backdrop: ${backdrop.label}",
                onClick = onBackdrop
            )
            if (versionLabel != null) {
                StudioChip(
                    icon = Icons.Outlined.History,
                    label = versionLabel,
                    description = "Versions and changes",
                    onClick = onVersions
                )
            }
        }
    }
}

@Composable
private fun StudioChip(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(OroCorners.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun CanvasBackdrop.icon(): ImageVector = when (this) {
    CanvasBackdrop.AUTO -> Icons.Outlined.Contrast
    CanvasBackdrop.LIGHT -> Icons.Outlined.LightMode
    CanvasBackdrop.DARK -> Icons.Outlined.DarkMode
    CanvasBackdrop.GRID -> Icons.Outlined.GridOn
}

/** What shows behind the page where it has no background of its own. */
internal fun Modifier.canvasBackdrop(backdrop: CanvasBackdrop): Modifier = when (backdrop) {
    CanvasBackdrop.AUTO -> this
    CanvasBackdrop.LIGHT -> background(Color(CanvasBackdrop.LIGHT_COLOR))
    CanvasBackdrop.DARK -> background(Color(CanvasBackdrop.DARK_COLOR))
    CanvasBackdrop.GRID -> drawBehind {
        drawRect(Color.White)
        val cell = 12.dp.toPx()
        val shade = Color(0xFFD8D8D8)
        var row = 0
        var y = 0f
        while (y < size.height) {
            var col = 0
            var x = 0f
            while (x < size.width) {
                if ((row + col) % 2 == 1) drawRect(shade, Offset(x, y), Size(cell, cell))
                x += cell
                col++
            }
            y += cell
            row++
        }
    }
}

/**
 * A file's source and, once it has more than one version, what changed:
 * pick `v1`, `v2` … `Latest` and read the diff against the version before it
 * (added lines green with `+`, removed red with `-`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VersionsSheet(
    path: String,
    history: ArtifactHistory,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val revisions = remember(path) { history.revisions(path) }
    var selected by remember(path) { mutableIntStateOf(revisions.lastIndex.coerceAtLeast(0)) }
    var tab by remember(path) { mutableIntStateOf(if (revisions.size > 1) 1 else 0) }
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp)
        ) {
            Text(path, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Code", maxLines = 1) })
                if (revisions.size > 1) {
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Diff", maxLines = 1) })
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { revisions.getOrNull(selected)?.let { onCopy(it.content) } }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy", maxLines = 1)
                }
            }
            if (revisions.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    items(revisions.indices.toList(), key = { it }) { index ->
                        FilterChip(
                            selected = index == selected,
                            onClick = { selected = index },
                            label = { Text(ArtifactHistory.label(index, revisions.size, revisions[index].number), maxLines = 1) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            val mono = FontFamily.Monospace
            if (tab == 1 && selected > 0) {
                val lines = remember(path, selected) { LineDiff.withContext(history.diff(path, selected - 1, selected), 3) }
                val added = remember(lines) { lines.count { it.kind == DiffKind.ADDED } }
                val removed = remember(lines) { lines.count { it.kind == DiffKind.REMOVED } }
                Text(
                    text = "+$added  −$removed  against ${ArtifactHistory.label(selected - 1, revisions.size, revisions[selected - 1].number)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                if (added + removed == 0) {
                    Text("No changes.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                } else {
                    SelectionContainer {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(lines.take(MAX_SHOWN_LINES), contentType = { it.kind }) { line ->
                                val (bg, mark) = when (line.kind) {
                                    DiffKind.ADDED -> Color(0xFF2E7D32).copy(alpha = 0.25f) to "+ "
                                    DiffKind.REMOVED -> Color(0xFFC62828).copy(alpha = 0.25f) to "- "
                                    DiffKind.SKIPPED -> scheme.surfaceContainerHigh to "  "
                                    DiffKind.SAME -> Color.Transparent to "  "
                                }
                                Text(
                                    text = if (line.kind == DiffKind.SKIPPED) "··· ${line.text}" else mark + line.text,
                                    fontFamily = mono,
                                    fontSize = 12.sp,
                                    fontStyle = if (line.kind == DiffKind.SKIPPED) FontStyle.Italic else FontStyle.Normal,
                                    color = if (line.kind == DiffKind.SKIPPED) scheme.onSurfaceVariant else scheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bg)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                val content = revisions.getOrNull(selected)?.content.orEmpty()
                val lines = remember(content) { content.lines() }
                if (content.isEmpty()) {
                    Text("Nothing recorded for this file yet.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                } else {
                    SelectionContainer {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(lines.take(MAX_SHOWN_LINES), contentType = { "code" }) { line ->
                                Text(
                                    text = line.ifEmpty { " " },
                                    fontFamily = mono,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_SHOWN_LINES = 3000
