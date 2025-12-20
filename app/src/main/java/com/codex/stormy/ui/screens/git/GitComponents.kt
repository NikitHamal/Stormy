package com.codex.stormy.ui.screens.git

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.data.git.CIStatusSummary
import com.codex.stormy.data.git.GitBranch
import com.codex.stormy.data.git.GitChangedFile
import com.codex.stormy.data.git.GitCommit
import com.codex.stormy.data.git.GitFileStatus
import com.codex.stormy.data.git.GitOperationProgress
import com.codex.stormy.data.git.GitRepositoryStatus
import com.codex.stormy.data.git.WorkflowRunInfo
import com.codex.stormy.ui.theme.PoppinsFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Git status badge showing current branch and sync status
 */
@Composable
fun GitStatusBadge(
    status: GitRepositoryStatus?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (status == null || !status.isGitRepo) return

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Branch icon with status color
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            status.hasUncommittedChanges -> Color(0xFFFFB347) // Orange
                            status.aheadCount > 0 -> MaterialTheme.colorScheme.primary
                            status.behindCount > 0 -> MaterialTheme.colorScheme.tertiary
                            else -> Color(0xFF81C784) // Green
                        }
                    )
            )

            // Branch name
            Text(
                text = status.currentBranch.ifEmpty { "detached" },
                style = MaterialTheme.typography.labelMedium,
                fontFamily = PoppinsFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Ahead/behind indicators
            if (status.aheadCount > 0 || status.behindCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (status.aheadCount > 0) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowUpward,
                            contentDescription = "Ahead",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = status.aheadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (status.behindCount > 0) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDownward,
                            contentDescription = "Behind",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = status.behindCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Main Git panel content
 */
@Composable
fun GitPanel(
    uiState: GitUiState,
    onStageFile: (String) -> Unit,
    onUnstageFile: (String) -> Unit,
    onStageAll: () -> Unit,
    onDiscardChanges: (String) -> Unit,
    onCommit: (String) -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onFetch: () -> Unit,
    onRefresh: () -> Unit,
    onCheckout: (String) -> Unit,
    onCreateBranch: (String, Boolean) -> Unit,
    onViewDiff: (String, Boolean) -> Unit,
    onInitRepo: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshCI: () -> Unit = {},
    onRerunWorkflow: (Long) -> Unit = {},
    onCancelWorkflow: (Long) -> Unit = {},
    onOpenWorkflowUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var commitMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header
        GitPanelHeader(
            status = uiState.status,
            isLoading = uiState.isLoading,
            onRefresh = onRefresh,
            onSettings = onOpenSettings
        )

        // Operation progress
        uiState.operationProgress?.let { progress ->
            GitProgressBar(progress = progress)
        }

        // Content
        if (!uiState.isGitRepo) {
            // Not a git repo - show init option
            GitInitPrompt(onInit = onInitRepo)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick actions
                item {
                    GitQuickActions(
                        status = uiState.status,
                        onPush = onPush,
                        onPull = onPull,
                        onFetch = onFetch,
                        isLoading = uiState.isLoading
                    )
                }

                // Staged changes
                if (uiState.stagedFiles.isNotEmpty()) {
                    item {
                        GitChangesSection(
                            title = "Staged Changes",
                            files = uiState.stagedFiles,
                            isStaged = true,
                            onToggleStage = { path -> onUnstageFile(path) },
                            onViewDiff = { path -> onViewDiff(path, true) },
                            onDiscard = onDiscardChanges
                        )
                    }
                }

                // Unstaged changes
                if (uiState.unstagedFiles.isNotEmpty()) {
                    item {
                        GitChangesSection(
                            title = "Changes",
                            files = uiState.unstagedFiles,
                            isStaged = false,
                            onToggleStage = { path -> onStageFile(path) },
                            onViewDiff = { path -> onViewDiff(path, false) },
                            onDiscard = onDiscardChanges,
                            onStageAll = onStageAll
                        )
                    }
                }

                // Commit input (show if there are staged changes)
                if (uiState.stagedFiles.isNotEmpty()) {
                    item {
                        CommitInput(
                            message = commitMessage,
                            onMessageChange = { commitMessage = it },
                            onCommit = {
                                if (commitMessage.isNotBlank()) {
                                    onCommit(commitMessage)
                                    commitMessage = ""
                                }
                            },
                            isLoading = uiState.isLoading
                        )
                    }
                }

                // Branches section
                item {
                    BranchesSection(
                        branches = uiState.localBranches,
                        currentBranch = uiState.currentBranch,
                        onCheckout = onCheckout,
                        onCreateBranch = onCreateBranch
                    )
                }

                // Recent commits
                if (uiState.commits.isNotEmpty()) {
                    item {
                        CommitsSection(commits = uiState.commits.take(5))
                    }
                }

                // CI/CD Status (only for GitHub repos)
                if (uiState.ciStatus?.isGitHubRepo == true) {
                    item {
                        CIStatusSection(
                            ciStatus = uiState.ciStatus,
                            isLoading = uiState.isCILoading,
                            onRefresh = onRefreshCI,
                            onRerun = onRerunWorkflow,
                            onCancel = onCancelWorkflow,
                            onOpenUrl = onOpenWorkflowUrl
                        )
                    }
                }

                // Bottom padding
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun GitPanelHeader(
    status: GitRepositoryStatus?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Code,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Source Control",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onSettings, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GitProgressBar(progress: GitOperationProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = progress.operation,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${(progress.percentage * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress.percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
        )
        if (progress.message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = progress.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GitInitPrompt(onInit: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "Not a Git repository",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = PoppinsFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onInit) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Initialize Repository", fontFamily = PoppinsFontFamily)
            }
        }
    }
}

@Composable
private fun GitQuickActions(
    status: GitRepositoryStatus?,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onFetch: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GitActionButton(
            icon = Icons.Outlined.CloudUpload,
            label = "Push",
            badge = status?.aheadCount?.takeIf { it > 0 }?.toString(),
            onClick = onPush,
            enabled = !isLoading && status?.hasRemote == true,
            modifier = Modifier.weight(1f)
        )

        GitActionButton(
            icon = Icons.Outlined.CloudDownload,
            label = "Pull",
            badge = status?.behindCount?.takeIf { it > 0 }?.toString(),
            onClick = onPull,
            enabled = !isLoading && status?.hasRemote == true,
            modifier = Modifier.weight(1f)
        )

        GitActionButton(
            icon = Icons.Outlined.Sync,
            label = "Fetch",
            onClick = onFetch,
            enabled = !isLoading && status?.hasRemote == true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GitActionButton(
    icon: ImageVector,
    label: String,
    badge: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = PoppinsFontFamily
        )
        if (badge != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun GitChangesSection(
    title: String,
    files: List<GitChangedFile>,
    isStaged: Boolean,
    onToggleStage: (String) -> Unit,
    onViewDiff: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onStageAll: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(true) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
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

            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // File count badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = files.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Stage all button (for unstaged changes)
            if (onStageAll != null && !isStaged) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onStageAll,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Stage all",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Files list
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                files.forEach { file ->
                    GitChangedFileItem(
                        file = file,
                        isStaged = isStaged,
                        onToggleStage = { onToggleStage(file.path) },
                        onViewDiff = { onViewDiff(file.path) },
                        onDiscard = { onDiscard(file.path) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GitChangedFileItem(
    file: GitChangedFile,
    isStaged: Boolean,
    onToggleStage: () -> Unit,
    onViewDiff: () -> Unit,
    onDiscard: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewDiff)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        GitFileStatusIcon(status = file.status)

        Spacer(modifier = Modifier.width(8.dp))

        // File path
        Text(
            text = file.path,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Actions
        if (!isStaged && file.status != GitFileStatus.UNTRACKED) {
            IconButton(onClick = onDiscard, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Discard",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }

        IconButton(onClick = onToggleStage, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = if (isStaged) Icons.Outlined.Remove else Icons.Outlined.Add,
                contentDescription = if (isStaged) "Unstage" else "Stage",
                modifier = Modifier.size(14.dp),
                tint = if (isStaged) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun GitFileStatusIcon(status: GitFileStatus) {
    val (color, letter) = when (status) {
        GitFileStatus.UNTRACKED -> Color(0xFF81C784) to "U"
        GitFileStatus.ADDED -> Color(0xFF81C784) to "A"
        GitFileStatus.MODIFIED -> Color(0xFFFFB347) to "M"
        GitFileStatus.DELETED -> Color(0xFFFF6B6B) to "D"
        GitFileStatus.RENAMED -> Color(0xFF64B5F6) to "R"
        GitFileStatus.COPIED -> Color(0xFF64B5F6) to "C"
        GitFileStatus.CONFLICTING -> Color(0xFFFF6B6B) to "!"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to "?"
    }

    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = color
        )
    }
}

@Composable
private fun CommitInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onCommit: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            placeholder = {
                Text(
                    "Commit message...",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = PoppinsFontFamily
                )
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PoppinsFontFamily
            ),
            minLines = 2,
            maxLines = 4
        )

        Button(
            onClick = onCommit,
            enabled = message.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Commit", fontFamily = PoppinsFontFamily)
        }
    }
}

@Composable
private fun BranchesSection(
    branches: List<GitBranch>,
    currentBranch: GitBranch?,
    onCheckout: (String) -> Unit,
    onCreateBranch: (String, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
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

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.CallMerge,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Branches",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "New branch",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Branches list
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                branches.forEach { branch ->
                    BranchItem(
                        branch = branch,
                        isCurrent = branch.name == currentBranch?.name,
                        onCheckout = { onCheckout(branch.name) }
                    )
                }
            }
        }
    }

    // Create branch dialog would go here
}

@Composable
private fun BranchItem(
    branch: GitBranch,
    isCurrent: Boolean,
    onCheckout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrent, onClick = onCheckout)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Current branch",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Spacer(modifier = Modifier.width(14.dp))
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = branch.name,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        // Tracking info
        if (branch.aheadCount > 0 || branch.behindCount > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (branch.aheadCount > 0) {
                    Text(
                        text = "↑${branch.aheadCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (branch.behindCount > 0) {
                    Text(
                        text = "↓${branch.behindCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun CommitsSection(commits: List<GitCommit>) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
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

            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Recent Commits",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        // Commits list
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                commits.forEach { commit ->
                    CommitItem(commit = commit)
                }
            }
        }
    }
}

@Composable
private fun CommitItem(commit: GitCommit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Commit hash
        Text(
            text = commit.shortId,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(60.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = commit.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = PoppinsFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = commit.authorName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTimestamp(commit.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * CI/CD Status section showing GitHub Actions workflow runs
 */
@Composable
private fun CIStatusSection(
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
            .clip(RoundedCornerShape(8.dp))
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
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
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
                        fontFamily = PoppinsFontFamily,
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
                fontFamily = PoppinsFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            com.codex.stormy.data.git.CIStatus.IN_PROGRESS,
            com.codex.stormy.data.git.CIStatus.PENDING -> {
                IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            com.codex.stormy.data.git.CIStatus.FAILURE,
            com.codex.stormy.data.git.CIStatus.CANCELLED -> {
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
