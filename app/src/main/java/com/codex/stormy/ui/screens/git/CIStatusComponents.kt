package com.codex.stormy.ui.screens.git

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.stormy.data.git.CIStatus
import com.codex.stormy.data.git.CIStatusSummary
import com.codex.stormy.data.git.JobInfo
import com.codex.stormy.data.git.WorkflowRunInfo
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * CI Status badge that shows current workflow status
 */
@Composable
fun CIStatusBadge(
    status: CIStatus,
    modifier: Modifier = Modifier
) {
    val (icon, color, label) = when (status) {
        CIStatus.SUCCESS -> Triple(Icons.Outlined.CheckCircle, Color(0xFF4CAF50), "Passing")
        CIStatus.FAILURE -> Triple(Icons.Outlined.Error, Color(0xFFF44336), "Failing")
        CIStatus.CANCELLED -> Triple(Icons.Outlined.Cancel, Color(0xFF9E9E9E), "Cancelled")
        CIStatus.PENDING -> Triple(Icons.Outlined.Schedule, Color(0xFFFFC107), "Pending")
        CIStatus.IN_PROGRESS -> Triple(Icons.Outlined.HourglassEmpty, Color(0xFF2196F3), "Running")
        CIStatus.SKIPPED -> Triple(Icons.Outlined.Cancel, Color(0xFF9E9E9E), "Skipped")
        CIStatus.UNKNOWN -> Triple(Icons.Outlined.HourglassEmpty, Color(0xFF9E9E9E), "Unknown")
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ci_status")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(14.dp)
                    .then(
                        if (status == CIStatus.IN_PROGRESS) Modifier.rotate(rotation)
                        else Modifier
                    )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontFamily = PoppinsFontFamily
            )
        }
    }
}

/**
 * Compact CI status indicator for the Git panel header
 */
@Composable
fun CIStatusIndicator(
    summary: CIStatusSummary?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = "Checking CI...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = PoppinsFontFamily
            )
        } else if (summary == null || !summary.isGitHubRepo) {
            Text(
                text = "CI/CD",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = PoppinsFontFamily
            )
        } else {
            val status = summary.currentBranchStatus ?: summary.latestRun?.displayStatus
            if (status != null) {
                CIStatusBadge(status = status)
            } else {
                Text(
                    text = "No runs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = PoppinsFontFamily
                )
            }
        }

        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(24.dp)
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

/**
 * CI/CD Panel showing workflow runs and status
 */
@Composable
fun CIPanelContent(
    summary: CIStatusSummary?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onRunClick: (WorkflowRunInfo) -> Unit,
    onRerun: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CI/CD Status",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        when {
            summary == null -> {
                EmptyState(
                    message = "Loading CI/CD status...",
                    modifier = Modifier.padding(16.dp)
                )
            }
            !summary.isGitHubRepo -> {
                EmptyState(
                    message = summary.errorMessage ?: "Not a GitHub repository",
                    modifier = Modifier.padding(16.dp)
                )
            }
            summary.recentRuns.isEmpty() -> {
                EmptyState(
                    message = "No workflow runs found",
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                // Show recent runs
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(summary.recentRuns) { run ->
                        WorkflowRunCard(
                            run = run,
                            onClick = { onRunClick(run) },
                            onRerun = { onRerun(run.id) },
                            onCancel = { onCancel(run.id) },
                            onOpenInBrowser = { onOpenInBrowser(run.htmlUrl) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * Card showing a single workflow run
 */
@Composable
fun WorkflowRunCard(
    run: WorkflowRunInfo,
    onClick: () -> Unit,
    onRerun: () -> Unit,
    onCancel: () -> Unit,
    onOpenInBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // First row: Name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = run.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                CIStatusBadge(status = run.displayStatus)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Second row: Branch, commit, run number
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                run.branch?.let { branch ->
                    Text(
                        text = branch,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = PoppinsFontFamily
                    )
                }

                Text(
                    text = run.commitSha.take(7),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = PoppinsFontFamily
                )

                Text(
                    text = "#${run.runNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = PoppinsFontFamily
                )
            }

            // Third row: Commit message
            run.commitMessage?.let { message ->
                Text(
                    text = message.lines().first(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (run.displayStatus == CIStatus.IN_PROGRESS ||
                    run.displayStatus == CIStatus.PENDING) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cancel,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (run.displayStatus == CIStatus.FAILURE ||
                    run.displayStatus == CIStatus.CANCELLED) {
                    IconButton(
                        onClick = onRerun,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = "Re-run",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = onOpenInBrowser,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "Open in browser",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Job details view showing steps
 */
@Composable
fun JobDetailsView(
    job: JobInfo,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Job header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = job.name,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium
            )

            CIStatusBadge(status = job.displayStatus)
        }

        // Steps
        job.steps.forEach { step ->
            StepRow(
                name = step.name,
                number = step.number,
                status = step.displayStatus
            )
        }
    }
}

/**
 * Single step row in job details
 */
@Composable
private fun StepRow(
    name: String,
    number: Int,
    status: CIStatus,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (status) {
        CIStatus.SUCCESS -> Icons.Outlined.CheckCircle to Color(0xFF4CAF50)
        CIStatus.FAILURE -> Icons.Outlined.Error to Color(0xFFF44336)
        CIStatus.CANCELLED -> Icons.Outlined.Cancel to Color(0xFF9E9E9E)
        CIStatus.PENDING -> Icons.Outlined.Schedule to Color(0xFFFFC107)
        CIStatus.IN_PROGRESS -> Icons.Outlined.HourglassEmpty to Color(0xFF2196F3)
        CIStatus.SKIPPED -> Icons.Outlined.Cancel to Color(0xFF9E9E9E)
        CIStatus.UNKNOWN -> Icons.Outlined.HourglassEmpty to Color(0xFF9E9E9E)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Step number
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Step name
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = PoppinsFontFamily,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Status icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Empty state component
 */
@Composable
private fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = PoppinsFontFamily
        )
    }
}
