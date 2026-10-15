package com.codex.stormy.ui.components.agent

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.stormy.data.ai.agents.AgentPlan
import com.codex.stormy.data.ai.agents.PlanTask
import com.codex.stormy.data.ai.agents.TaskState
import com.codex.stormy.ui.components.expressive.ExpressiveInlineLoader
import com.codex.stormy.ui.theme.CodeXCorners
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Space

/**
 * The agent's plan, as a collapsible checklist pinned above the conversation.
 *
 * The problem this solves is specific to a phone: during long multi-step work
 * the transcript scrolls past faster than it can be read, so "where is it now"
 * becomes unanswerable without scrolling back through tool calls. A short
 * checklist that updates in place answers it at a glance.
 *
 * Collapsed, it shows only the active task and a progress bar -- one line,
 * because that is all that fits above a conversation without stealing the
 * screen. Expanded, it shows the full list.
 */
@Composable
fun PlanTimeline(
    plan: AgentPlan,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    if (plan.isEmpty) return

    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val fraction by animateFloatAsState(plan.fraction, label = "planProgress")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CodeXCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (plan.isComplete) "Plan complete"
                        else plan.activeTask?.title ?: "Planning",
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${plan.doneCount} of ${plan.tasks.size} done",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!plan.isComplete) {
                    ExpressiveInlineLoader(modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(Space.sm))
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess
                    else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Hide plan" else "Show plan",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.icon)
                )
            }

            LinearProgressIndicator(
                // Lambda form: progress changes redraw the bar without
                // recomposing the surrounding rows.
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            if (expanded) {
                Column(modifier = Modifier.padding(vertical = Space.sm)) {
                    plan.tasks.forEach { task -> PlanRow(task) }
                }
            }
        }
    }
}

@Composable
private fun PlanRow(task: PlanTask) {
    val color by animateColorAsState(
        when (task.state) {
            TaskState.ACTIVE -> MaterialTheme.colorScheme.onSurface
            TaskState.DONE -> MaterialTheme.colorScheme.onSurfaceVariant
            TaskState.BLOCKED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        },
        label = "planRowColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.xs),
        verticalAlignment = Alignment.Top
    ) {
        PlanMarker(task.state)
        Spacer(Modifier.width(Space.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = if (task.state == TaskState.ACTIVE) {
                    // The active row is the one the eye should land on, and
                    // emphasis does that without changing its height -- so the
                    // list does not reflow every time a step completes.
                    MaterialTheme.typography.bodySmallEmphasized
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = color,
                textDecoration = if (task.state == TaskState.SKIPPED) {
                    TextDecoration.LineThrough
                } else {
                    null
                }
            )
            // Blocked and skipped tasks always carry their reason. A plan that
            // silently drops a step leaves the user believing it happened.
            task.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.state == TaskState.BLOCKED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
            task.delegatedTo?.let { agent ->
                Text(
                    text = "delegated to $agent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PlanMarker(state: TaskState) {
    val size = Modifier
        .padding(top = 2.dp)
        .size(Sizes.iconSmall)

    when (state) {
        TaskState.DONE -> Icon(
            Icons.Outlined.Check,
            contentDescription = "Done",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = size
        )
        TaskState.ACTIVE -> ExpressiveInlineLoader(modifier = size)
        TaskState.BLOCKED -> Icon(
            Icons.Outlined.Report,
            contentDescription = "Blocked",
            tint = MaterialTheme.colorScheme.error,
            modifier = size
        )
        TaskState.SKIPPED -> Icon(
            Icons.Outlined.RemoveCircleOutline,
            contentDescription = "Skipped",
            tint = MaterialTheme.colorScheme.outline,
            modifier = size
        )
        TaskState.PENDING -> Surface(
            modifier = Modifier
                .padding(top = 6.dp, start = 4.dp)
                .size(8.dp),
            shape = CodeXCorners.pill,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = {}
        )
    }
}
