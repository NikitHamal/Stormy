package com.oro.app.ui.screens.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.OroApplication
import com.oro.app.data.ai.checkpoint.CheckpointFile
import com.oro.app.data.ai.checkpoint.CheckpointManifest
import com.oro.app.data.ai.checkpoint.Checkpoints
import com.oro.app.ui.components.diff.DiffContent
import com.oro.app.ui.components.expressive.FormSheet
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space
import com.oro.app.utils.DiffUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Every checkpoint of a project as a timeline: what each run changed, a diff
 * of any file against how it is now, and restore -- the whole run (and later
 * ones in that chat), or a single file.
 */
@Composable
fun TimelineScreen(projectId: String, onBack: () -> Unit) {
    val app = remember { OroApplication.getInstance() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var version by remember { mutableIntStateOf(0) }
    val root by produceState<File?>(null, projectId) {
        value = withContext(Dispatchers.IO) { app.projectRepository.getProjectById(projectId)?.rootPath?.let { File(it) } }
    }
    val cps = remember(root) { root?.let { Checkpoints(it) } }
    val list by produceState(emptyList<CheckpointManifest>(), cps, version) {
        value = withContext(Dispatchers.IO) { cps?.list().orEmpty() }
    }
    var diff by remember { mutableStateOf<Pair<CheckpointManifest, CheckpointFile>?>(null) }
    var confirm by remember { mutableStateOf<CheckpointManifest?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timeline", style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        TimelineList(
            list = list,
            onDiff = { cp, f -> diff = cp to f },
            onRestore = { confirm = it },
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }

    diff?.let { (cp, f) ->
        val target = root
        val result by produceState<DiffUtils.DiffResult?>(null, cp.id, f.path) {
            value = withContext(Dispatchers.Default) {
                val before = cps?.before(cp, f).orEmpty()
                val now = target?.let { File(it, f.path) }?.takeIf { it.isFile && it.length() < 2_000_000 }?.readText().orEmpty()
                DiffUtils.computeDiff(before, now, contextLines = 3)
            }
        }
        ModalBottomSheet(onDismissRequest = { diff = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = Space.lg).padding(bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(f.path, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Then (before this run) → now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                result?.let { DiffContent(diffResult = it, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) }
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { cps?.restoreFile(cp, f) }
                        diff = null
                        snackbar.showSnackbar("Restored ${f.path}")
                    }
                }, shape = OroCorners.pill) { Text("Restore just this file", maxLines = 1) }
            }
        }
    }

    confirm?.let { cp ->
        FormSheet(
            title = "Restore this point?",
            icon = Icons.Outlined.History,
            onDismiss = { confirm = null },
            confirmLabel = "Restore",
            destructive = true,
            onConfirm = {
                confirm = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) { cps?.restoreFrom(cp.sessionId, cp.messageId) }
                    version++
                    snackbar.showSnackbar("Restored ${r?.files?.size ?: 0} files")
                }
            }
        ) {
            Text(
                "Files go back to how they were before this run. Later runs in the same chat are undone too. Conversations are kept.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** Checkpoints as a rail of cards, newest first; [TimelineScreen] loads them and handles diff and restore. */
@Composable
internal fun TimelineList(
    list: List<CheckpointManifest>,
    onDiff: (CheckpointManifest, CheckpointFile) -> Unit,
    onRestore: (CheckpointManifest) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(Space.lg),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (list.isEmpty()) item(key = "empty") {
            com.oro.app.ui.components.expressive.ExpressiveEmptyState(
                icon = Icons.Outlined.History,
                title = "No checkpoints yet",
                description = "Each Orobi run saves what it changed, to compare and restore.",
                modifier = Modifier.fillMaxWidth().padding(top = Space.huge)
            )
        }
        items(list, key = { it.id }) { cp ->
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                // The rail.
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp).fillMaxHeight()) {
                    Box(Modifier.padding(top = 18.dp).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
                }
                Spacer(Modifier.width(Space.sm))
                Surface(shape = OroCorners.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(bottom = Space.md)) {
                    Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Text(cp.label.lineSequence().first().ifBlank { "Orobi run" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(cp.createdAt)) +
                                " · ${cp.files.size} file${if (cp.files.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        cp.files.take(12).forEach { f ->
                            Row(
                                Modifier.fillMaxWidth().clip(OroCorners.small).padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (!f.existed) Icons.Outlined.AddCircleOutline else Icons.Outlined.Edit, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(Space.sm))
                                Text(f.path, style = MaterialTheme.typography.bodySmall.copy(fontFamily = com.oro.app.ui.theme.CodeFontFamily), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { onDiff(cp, f) }) { Text("Diff", maxLines = 1) }
                            }
                        }
                        Row {
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = { onRestore(cp) }, shape = OroCorners.pill) {
                                Icon(Icons.Outlined.History, null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.xs)); Text("Restore", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
