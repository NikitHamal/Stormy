package com.codex.stormy.ui.components.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.stormy.data.ai.agents.AgentPhase
import com.codex.stormy.data.ai.agents.AgentSession
import com.codex.stormy.ui.components.expressive.ExpressiveInlineLoader
import com.codex.stormy.ui.theme.CodeXCorners
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Space

/**
 * A running (or finished) subagent, as a card in the conversation.
 *
 * Design intent: the collapsed card answers "what is it doing, is it fine, can
 * I stop it" without being tapped, because that is the whole question while
 * something spends your tokens in the background. Everything else -- the
 * brief, the step timeline, the summary -- is one tap away and off screen until
 * asked for.
 *
 * The card is deliberately not a chat bubble. Delegated work is a different
 * kind of object from a message: it has a lifecycle, it can be interrupted, and
 * it outlives the turn that started it. Giving it its own container keeps that
 * difference visible in a scrolling transcript.
 */
@Composable
fun AgentActivityCard(
    session: AgentSession,
    onStop: () -> Unit,
    onApprove: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val container by animateColorAsState(
        when {
            session.phase == AgentPhase.AWAITING_APPROVAL ->
                MaterialTheme.colorScheme.tertiaryContainer
            session.phase == AgentPhase.FAILED -> MaterialTheme.colorScheme.errorContainer
            session.isLive -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        label = "agentCardContainer"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CodeXCorners.large,
        color = container
    ) {
        Column {
            // --- Collapsed header: always visible --------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Space.md, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AgentBadge(
                    label = session.definition.label,
                    accentIndex = session.definition.accentIndex,
                    active = session.isLive
                )

                Spacer(Modifier.width(Space.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.description,
                        // Emphasized: the task name is what the user scans for,
                        // and it has to win against the status line beneath it
                        // without being a larger size.
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.statusLine(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (session.isLive) {
                            Spacer(Modifier.width(Space.sm))
                            ExpressiveInlineLoader(modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(Modifier.width(Space.sm))

                when {
                    // The approval row below carries its own actions; a stop
                    // button here would compete with them.
                    session.phase == AgentPhase.AWAITING_APPROVAL -> Unit
                    session.isLive -> IconButton(onClick = onStop) {
                        Icon(
                            Icons.Outlined.Stop,
                            // Named for the consequence, not the glyph: a
                            // screen-reader user needs to know what stops.
                            contentDescription = "Stop ${session.description}",
                            modifier = Modifier.size(Sizes.icon)
                        )
                    }
                    else -> TerminalMark(session.phase)
                }

                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess
                    else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "Show details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.icon)
                )
            }

            // --- Turn-budget bar -------------------------------------------
            session.budgetFraction?.takeIf { session.isLive }?.let { fraction ->
                val animated by animateFloatAsState(fraction, label = "agentBudget")
                LinearProgressIndicator(
                    // Lambda form: the bar redraws without recomposing the card.
                    progress = { animated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            // --- Approval gate ---------------------------------------------
            session.pendingApproval?.let { request ->
                ApprovalRow(
                    action = request.description,
                    detail = request.detail,
                    onApprove = { onApprove(true) },
                    onDecline = { onApprove(false) }
                )
            }

            // --- Expanded detail -------------------------------------------
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
            ) {
                AgentDetail(session)
            }
        }
    }
}

/**
 * Status line for the collapsed card.
 *
 * Prefers the newest step's own label, because "Read index.html" tells the user
 * more than "Reading files". Falls back to the phase's generic phrase.
 */
private fun AgentSession.statusLine(): String {
    val latest = steps.lastOrNull()
    val failure = error
    return when {
        phase == AgentPhase.COMPLETED -> "Done in ${elapsedMs / 1000}s"
        phase == AgentPhase.EXHAUSTED -> "Stopped after $turnsUsed steps"
        failure != null -> failure
        latest != null && phase.isLive -> latest.label
        else -> phase.display
    }
}

@Composable
private fun TerminalMark(phase: AgentPhase) {
    val (icon, tint, description) = when (phase) {
        AgentPhase.COMPLETED -> Triple(
            Icons.Outlined.Check, MaterialTheme.colorScheme.tertiary, "Completed"
        )
        AgentPhase.FAILED -> Triple(
            Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, "Failed"
        )
        else -> Triple(
            Icons.Outlined.Close, MaterialTheme.colorScheme.onSurfaceVariant, phase.display
        )
    }
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(Sizes.icon))
}

@Composable
private fun ApprovalRow(
    action: String,
    detail: String?,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.sm)
    ) {
        Text(
            text = action,
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            // Decline is placed first so the irreversible choice is not the one
            // sitting under the thumb's resting position.
            TextButton(onClick = onDecline) { Text("Don't") }
            Spacer(Modifier.width(Space.sm))
            TextButton(onClick = onApprove) { Text("Allow") }
        }
    }
}

@Composable
private fun AgentDetail(session: AgentSession) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.md, end = Space.md, bottom = Space.md)
    ) {
        DetailLabel("${session.definition.label} agent")
        Text(
            text = session.prompt,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )

        if (session.steps.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            DetailLabel("Steps")
            session.steps.forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = Space.xxs),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .size(5.dp),
                        shape = CodeXCorners.pill,
                        color = if (step.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outline,
                        content = {}
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    step.durationMs?.let { ms ->
                        Text(
                            text = "${ms / 100 / 10f}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        session.summary?.let { summary ->
            Spacer(Modifier.height(Space.md))
            DetailLabel("Result")
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DetailLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = Space.xs)
    )
}
