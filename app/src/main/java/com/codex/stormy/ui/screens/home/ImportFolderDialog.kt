package com.codex.stormy.ui.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.stormy.R
import com.codex.stormy.ui.theme.PoppinsFontFamily
import com.codex.stormy.utils.FileUtils

/**
 * Dialog for importing a folder from device storage as a new project.
 * Uses OpenDocumentTree to pick folders via Storage Access Framework.
 */
@Composable
fun ImportFolderDialog(
    onDismiss: () -> Unit,
    onImport: (name: String, description: String, folderUri: Uri) -> Unit,
    isImporting: Boolean,
    progress: Float?,
    progressMessage: String?,
    error: String?
) {
    val context = LocalContext.current
    var projectName by remember { mutableStateOf("") }
    var projectDescription by remember { mutableStateOf("") }
    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFolderName by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            selectedFolderUri = it
            selectedFolderName = FileUtils.getFolderNameFromTreeUri(context, it)
            // Auto-fill project name from folder name if empty
            if (projectName.isBlank() && selectedFolderName != null) {
                projectName = selectedFolderName!!
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = {
            Text(
                text = "Import Folder",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Folder selection button
                if (!isImporting) {
                    FolderSelector(
                        selectedFolderName = selectedFolderName,
                        onSelectFolder = { folderPickerLauncher.launch(null) }
                    )
                }

                // Project name input
                OutlinedTextField(
                    value = projectName,
                    onValueChange = {
                        projectName = it
                        nameError = null
                    },
                    label = { Text(context.getString(R.string.create_project_name_label)) },
                    placeholder = { Text("Enter project name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Description input
                OutlinedTextField(
                    value = projectDescription,
                    onValueChange = { projectDescription = it },
                    label = { Text(context.getString(R.string.create_project_description_label)) },
                    placeholder = { Text("Optional description") },
                    maxLines = 2,
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Progress indicator
                AnimatedVisibility(
                    visible = isImporting,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ImportProgressIndicator(
                        progress = progress,
                        message = progressMessage
                    )
                }

                // Error display
                AnimatedVisibility(
                    visible = error != null && !isImporting,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ErrorDisplay(error = error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        selectedFolderUri == null -> {
                            // Trigger folder picker
                            folderPickerLauncher.launch(null)
                        }
                        projectName.isBlank() -> {
                            nameError = context.getString(R.string.create_project_error_empty_name)
                        }
                        else -> {
                            onImport(
                                projectName.trim(),
                                projectDescription.trim(),
                                selectedFolderUri!!
                            )
                        }
                    }
                },
                enabled = !isImporting,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (selectedFolderUri == null) "Select Folder" else "Import")
                }
            }
        },
        dismissButton = {
            if (!isImporting) {
                TextButton(onClick = onDismiss) {
                    Text(context.getString(R.string.action_cancel))
                }
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun FolderSelector(
    selectedFolderName: String?,
    onSelectFolder: () -> Unit
) {
    OutlinedButton(
        onClick = onSelectFolder,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selectedFolderName != null) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selectedFolderName != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selectedFolderName != null) {
                        Icons.Outlined.FolderOpen
                    } else {
                        Icons.Outlined.Folder
                    },
                    contentDescription = null,
                    tint = if (selectedFolderName != null) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (selectedFolderName != null) {
                    Text(
                        text = selectedFolderName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap to change",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Select a folder",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = PoppinsFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Choose folder from your device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (selectedFolderName != null) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ImportProgressIndicator(
    progress: Float?,
    message: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (progress != null && progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message ?: "Importing...",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = PoppinsFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorDisplay(error: String?) {
    if (error == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = PoppinsFontFamily,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}
