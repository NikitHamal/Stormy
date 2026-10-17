package com.oro.app.ui.screens.git

import com.oro.app.ui.theme.OroCorners
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import com.oro.app.ui.theme.Space
import com.oro.app.ui.components.expressive.FormSheet
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oro.app.ui.components.expressive.ExpressiveLoadingIndicator
import com.oro.app.data.git.CIStatusSummary
import com.oro.app.data.git.WorkflowRunInfo

/**
 * Connect to remote section - shown when no remote is configured
 */
@Composable
fun ConnectRemoteSection(
    onAddRemote: (String, String) -> Unit,
    isLoading: Boolean
) {
    var expanded by remember { mutableStateOf(true) }
    var showAddRemoteDialog by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OroCorners.small)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotationState),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Connect to Remote",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No remote repository configured. Connect to GitHub or another Git hosting service to push and pull changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { showAddRemoteDialog = true },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Remote Repository")
                }
            }
        }
    }

    if (showAddRemoteDialog) {
        AddRemoteDialog(
            onDismiss = { showAddRemoteDialog = false },
            onAddRemote = { name, url ->
                onAddRemote(name, url)
                showAddRemoteDialog = false
            }
        )
    }
}

/**
 * Dialog for adding a remote repository
 */
@Composable
fun AddRemoteDialog(
    onDismiss: () -> Unit,
    onAddRemote: (String, String) -> Unit
) {
    var remoteName by remember { mutableStateOf("origin") }
    var remoteUrl by remember { mutableStateOf("") }

    FormSheet(
        title = "Add remote",
        icon = Icons.Outlined.Hub,
        onDismiss = onDismiss,
        confirmLabel = "Add remote",
        onConfirm = { onAddRemote(remoteName, remoteUrl) },
        confirmEnabled = remoteName.isNotBlank() && remoteUrl.isNotBlank()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(
                text = "The URL of your remote repository -- GitHub, GitLab or Bitbucket.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = remoteName,
                onValueChange = { remoteName = it.replace(" ", "") },
                label = { Text("Remote name") },
                placeholder = { Text("origin") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = OroCorners.large
            )

            OutlinedTextField(
                value = remoteUrl,
                onValueChange = { remoteUrl = it },
                label = { Text("Repository URL") },
                placeholder = { Text("https://github.com/user/repo.git") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = OroCorners.large
            )

            Text(
                text = "On GitHub the URL is behind the green Code button.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * CI/CD Status section showing GitHub Actions workflow runs
 */
@Composable
fun CIStatusSection(
    ciStatus: CIStatusSummary,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onRerun: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OroCorners.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotationState),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(8.dp))

            // CI status icon
            ciStatus.currentBranchStatus?.let { status ->
                CIStatusBadge(status = status)
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = "CI/CD",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (isLoading) {
                ExpressiveLoadingIndicator(
                    modifier = Modifier.size(16.dp)
                )
            } else {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh CI status",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Workflow runs list
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                if (ciStatus.recentRuns.isEmpty()) {
                    Text(
                        text = "No workflow runs found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                } else {
                    ciStatus.recentRuns.take(5).forEach { run ->
                        WorkflowRunItem(
                            run = run,
                            onRerun = { onRerun(run.id) },
                            onCancel = { onCancel(run.id) },
                            onOpenUrl = { onOpenUrl(run.htmlUrl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowRunItem(
    run: WorkflowRunInfo,
    onRerun: () -> Unit,
    onCancel: () -> Unit,
    onOpenUrl: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenUrl)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status badge
        CIStatusBadge(status = run.displayStatus)

        Spacer(modifier = Modifier.width(8.dp))

        // Run info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = run.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                run.branch?.let { branch ->
                    Text(
                        text = branch,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
                Text(
                    text = "#${run.runNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Action buttons for in-progress or failed runs
        val status = run.displayStatus
        when (status) {
            com.oro.app.data.git.CIStatus.IN_PROGRESS,
            com.oro.app.data.git.CIStatus.PENDING -> {
                IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            com.oro.app.data.git.CIStatus.FAILURE,
            com.oro.app.data.git.CIStatus.CANCELLED -> {
                IconButton(onClick = onRerun, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Re-run",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            else -> {}
        }
    }
}
