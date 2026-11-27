package com.oro.app.ui.screens.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.data.preview.ArtifactKind
import com.oro.app.data.preview.LocalUrl
import com.oro.app.data.preview.PreviewArtifacts
import java.io.File

/**
 * What the preview can switch to inside a project: its HTML pages (index
 * first) and its Markdown documents, as project-relative paths. Looks a few
 * folders deep and skips hidden folders and node_modules. Blocking: call it
 * off the main thread.
 */
internal fun listPreviewEntries(root: File): List<String> {
    if (!root.isDirectory) return emptyList()
    val files = root.walkTopDown()
        .maxDepth(5)
        .onEnter { d -> d == root || !(d.name.startsWith(".") || d.name == "node_modules") }
        .filter { it.isFile }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .toList()
    val docs = files
        .filter { PreviewArtifacts.kindOf(it) == ArtifactKind.MARKDOWN }
        .sortedWith(compareBy({ it.count { c -> c == '/' } }, { it.lowercase() }))
    return PreviewArtifacts.pages(files) + docs
}

/** The page picker: every page and document of the project, the one on screen marked. */
@Composable
internal fun PagePickerDialog(
    entries: List<String>,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pages") },
        text = {
            if (entries.isEmpty()) {
                Text("No pages yet. Add an HTML file to the project.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(entries, key = { it }) { path ->
                        val selected = path == current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(path) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/**
 * The address bar: a port ("5173") or a localhost address, for a dev server
 * running on this phone. "Project" goes back to the project's own preview.
 */
@Composable
internal fun LocalAddressDialog(
    onGo: (String) -> Unit,
    onProject: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }

    fun submit() {
        when (val parsed = PreviewArtifacts.parseLocalUrl(text)) {
            is LocalUrl.Valid -> onGo(parsed.url)
            is LocalUrl.Invalid -> problem = parsed.reason
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open a local address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; problem = null },
                    singleLine = true,
                    label = { Text("Port or localhost address") },
                    placeholder = { Text("5173") },
                    isError = problem != null,
                    supportingText = problem?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() })
                )
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("Open") } },
        dismissButton = {
            Row {
                TextButton(onClick = onProject) { Text("Project") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
