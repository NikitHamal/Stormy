package com.codex.stormy.ui.screens.home

import com.codex.stormy.ui.theme.CodeXCorners
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.components.expressive.ExpressiveLoadingIndicator
import com.codex.stormy.ui.components.expressive.FormSheet
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Space

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

    FormSheet(
        title = "Clone repository",
        icon = Icons.Outlined.CloudDownload,
        // Swiping the sheet away mid-clone would orphan a running job, so the
        // dismiss paths are closed for the duration rather than cancelling.
        onDismiss = { if (!isCloning) onDismiss() },
        confirmLabel = if (isCloning) "Cloning..." else "Clone",
        onConfirm = {
            when {
                url.isBlank() -> urlError = "Repository URL is required"
                !isValidGitInput(url) ->
                    urlError = "Invalid repository URL or format"
                projectName.isBlank() -> urlError = "Project name is required"
                else -> onClone(normalizeGitUrl(url.trim()), projectName.trim(), shallow)
            }
        },
        confirmEnabled = !isCloning,
        confirmLoading = isCloning
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    urlError = null
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
                shape = CodeXCorners.large
            )

            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                label = { Text("Project name") },
                placeholder = { Text("my-project") },
                singleLine = true,
                enabled = !isCloning,
                modifier = Modifier.fillMaxWidth(),
                shape = CodeXCorners.large
            )

            // The whole row toggles, not just the box: a 20dp checkbox is the
            // smallest target on the sheet and the label explains the choice.
            Surface(
                onClick = { if (!isCloning) shallow = !shallow },
                enabled = !isCloning,
                modifier = Modifier.fillMaxWidth(),
                shape = CodeXCorners.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(
                        start = Space.md,
                        end = Space.lg,
                        top = Space.sm,
                        bottom = Space.sm
                    )
                ) {
                    Checkbox(
                        checked = shallow,
                        onCheckedChange = { shallow = it },
                        enabled = !isCloning
                    )
                    Spacer(modifier = Modifier.width(Space.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shallow clone",
                            style = MaterialTheme.typography.bodyMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Faster download, limited history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isCloning) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
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
                            ExpressiveLoadingIndicator(
                                modifier = Modifier.size(Sizes.iconSmall)
                            )
                            Spacer(modifier = Modifier.width(Space.md))
                            Text(
                                text = progressMessage ?: "Connecting...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconSmall),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(Space.sm))
                Text(
                    text = "For private repos, configure Git credentials in Settings first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
