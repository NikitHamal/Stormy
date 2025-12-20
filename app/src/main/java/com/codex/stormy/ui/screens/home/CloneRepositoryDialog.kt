package com.codex.stormy.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp

/**
 * Dialog for cloning a Git repository
 */
@Composable
fun CloneRepositoryDialog(
    onDismiss: () -> Unit,
    onClone: (url: String, projectName: String, shallow: Boolean) -> Unit,
    isCloning: Boolean = false,
    progress: Float? = null,
    progressMessage: String? = null,
    error: String? = null
) {
    var url by remember { mutableStateOf("") }
    var projectName by remember { mutableStateOf("") }
    var shallow by remember { mutableStateOf(true) }
    var urlError by remember { mutableStateOf<String?>(null) }

    // Auto-detect project name from URL or username/repo format
    fun extractProjectName(gitUrl: String): String {
        val normalized = normalizeGitUrl(gitUrl)
        return normalized
            .removeSuffix("/")
            .removeSuffix(".git")
            .substringAfterLast("/")
            .substringAfterLast(":")
            .ifBlank { "" }
    }

    // Convert username/repo format to full GitHub URL
    fun normalizeGitUrl(input: String): String {
        val trimmed = input.trim()
        // Check if it's already a full URL
        if (trimmed.contains("://") || trimmed.contains("@")) {
            return trimmed
        }
        // Check if it's username/repo format (e.g., "user/repo" or "org/repo")
        val parts = trimmed.split("/")
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            // Validate that both parts look like valid GitHub identifiers
            val validIdentifier = Regex("^[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?$")
            if (validIdentifier.matches(parts[0]) && validIdentifier.matches(parts[1].removeSuffix(".git"))) {
                return "https://github.com/${parts[0]}/${parts[1]}"
            }
        }
        return trimmed
    }

    // Check if input is a valid git URL or username/repo format
    fun isValidGitInput(input: String): Boolean {
        val trimmed = input.trim()
        // Full URL formats
        if (trimmed.contains("://") || trimmed.contains("@")) {
            return true
        }
        // username/repo format
        val parts = trimmed.split("/")
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            return true
        }
        return false
    }

    AlertDialog(
        onDismissRequest = { if (!isCloning) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Clone Repository",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlError = null
                        // Auto-fill project name if empty
                        if (projectName.isBlank()) {
                            projectName = extractProjectName(it)
                        }
                    },
                    label = { Text("Repository URL") },
                    placeholder = { Text("user/repo or https://github.com/...") },
                    isError = urlError != null || error != null,
                    supportingText = {
                        when {
                            urlError != null -> Text(urlError!!)
                            error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    enabled = !isCloning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("my-project") },
                    singleLine = true,
                    enabled = !isCloning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = shallow,
                        onCheckedChange = { shallow = it },
                        enabled = !isCloning
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Shallow clone",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Faster download, limited history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Progress indicator during clone
                AnimatedVisibility(visible = isCloning) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (progress != null && progress > 0f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = progressMessage ?: "Cloning...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = progressMessage ?: "Connecting...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Info tip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "For private repos, configure Git credentials in Settings first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        url.isBlank() -> urlError = "Repository URL is required"
                        !isValidGitInput(url) ->
                            urlError = "Invalid repository URL or format"
                        projectName.isBlank() -> urlError = "Project name is required"
                        else -> onClone(normalizeGitUrl(url.trim()), projectName.trim(), shallow)
                    }
                },
                enabled = !isCloning,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isCloning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isCloning) "Cloning..." else "Clone")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCloning
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
