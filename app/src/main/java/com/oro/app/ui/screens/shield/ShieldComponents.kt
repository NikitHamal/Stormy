package com.oro.app.ui.screens.shield

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oro.app.data.ai.tools.QuestionOption
import com.oro.app.data.ai.tools.UserQuestion
import com.oro.app.guard.GuardAgent
import com.oro.app.guard.GuardTools
import com.oro.app.ui.brand.OrobiIdle
import com.oro.app.ui.brand.OrobiWorking
import com.oro.app.ui.brand.OroSparkle
import com.oro.app.ui.components.agent.AskUserCard
import com.oro.app.ui.components.agent.PendingQuestion
import com.oro.app.ui.components.expressive.ExpressiveInlineLoader
import com.oro.app.ui.components.message.AiMessageContent
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.OroTheme
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------------- transcript ------------------------------- */

@Composable
fun ShieldUserBubble(text: String, at: Long, modifier: Modifier = Modifier) {
    val colors = OroTheme.extendedColors
    val threshold = 600
    var expanded by rememberSaveable { mutableStateOf(false) }
    val long = text.length > threshold
    val shown = if (long && !expanded) text.take(threshold).trimEnd() + "…" else text
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            shape = OroCorners.bubble(fromUser = true),
            color = colors.chatUserBubble,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(
                Modifier
                    .then(if (OroTheme.liteMode) Modifier else Modifier.animateContentSize())
                    .clickable(enabled = long) { expanded = !expanded }
                    .padding(horizontal = Space.md, vertical = Space.sm + Space.xxs),
            ) {
                Text(
                    text = shown,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = colors.chatUserText,
                )
                if (long) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = if (expanded) "Show less" else "Show more",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.chatUserText.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.End),
                    )
                }
                Spacer(Modifier.height(Space.xxs))
                Text(
                    text = clock(at),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.chatUserText.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
fun ShieldGuardMessage(
    message: GuardAgent.Message,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    AiMessageContent(
        content = message.text,
        timestamp = clock(message.at),
        isStreaming = isStreaming,
        modifier = modifier.fillMaxWidth(),
        showWorkingIndicator = false,
        cacheKey = "shield-${message.id}",
    )
}

/**
 * A tool call as an inline card: one line while it works, expandable to the
 * full evidence when the user wants it. Long outputs scroll inside the card so
 * one dumps of a certificate chain cannot bury the conversation.
 */
@Composable
fun GuardToolCard(message: GuardAgent.Message, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val failed = message.status == GuardAgent.MsgStatus.FAILED
    val running = message.status == GuardAgent.MsgStatus.RUNNING
    val firstLine = remember(message.detail) { message.detail.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
    val container = when {
        failed -> MaterialTheme.colorScheme.errorContainer
        running -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val onContainer = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    val accent = when {
        failed -> MaterialTheme.colorScheme.error
        running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = container,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            Modifier
                .then(if (OroTheme.liteMode) Modifier else Modifier.animateContentSize())
                .clickable(enabled = message.detail.isNotBlank()) { expanded = !expanded }
                .padding(horizontal = Space.md, vertical = Space.sm + Space.xxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(Sizes.avatar),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = toolIcon(message.tool),
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconSmall),
                            tint = accent,
                        )
                    }
                }
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        color = onContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            running -> "Working"
                            failed -> "Failed"
                            else -> message.tool?.replace('_', ' ').orEmpty().ifBlank { firstLine }.ifBlank { "Done" }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (failed) onContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (running) {
                    ExpressiveInlineLoader(Modifier.size(Sizes.icon))
                } else if (message.detail.isNotBlank()) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                        contentDescription = if (expanded) "Hide details" else "Show details",
                        modifier = Modifier.size(Sizes.icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded && message.detail.isNotBlank()) {
                Spacer(Modifier.height(Space.sm))
                SelectionContainer {
                    Text(
                        text = message.detail,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                        color = onContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
fun GuardNoteRow(message: GuardAgent.Message, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        OroSparkle(Modifier.size(10.dp).padding(top = 4.dp), color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(Space.sm))
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ShieldQuestionCard(
    question: GuardAgent.Question,
    onAnswer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = remember(question.text, question.options) {
        PendingQuestion(
            id = question.text,
            questions = listOf(
                UserQuestion(
                    question = question.text,
                    options = question.options.map { QuestionOption(it) },
                )
            ),
        )
    }
    AskUserCard(
        question = pending,
        onSubmit = { answers -> onAnswer(answers.filterNotNull().joinToString(", ").ifBlank { "You decide" }) },
        onDismiss = { onAnswer("You decide") },
        modifier = modifier,
    )
}

@Composable
fun ShieldWorking(label: String, modifier: Modifier = Modifier) {
    OrobiWorking(label = label.ifBlank { "Working" }, modifier = modifier.padding(vertical = Space.xs), maxLines = 1)
}

@Composable
fun ShieldErrorCard(result: GuardAgent.Result, onNewChat: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(Space.md)) {
            Text(
                text = if (result.error == "Stopped.") "Stopped" else "Run failed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = result.error.orEmpty().ifBlank { result.summary },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(Space.sm))
            TextButton(onClick = onNewChat, contentPadding = PaddingValues(horizontal = Space.sm)) {
                Text("New chat", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/* --------------------------------- welcome -------------------------------- */

@Composable
fun ShieldWelcome(
    seatNames: List<String>,
    findingCount: Int,
    onSuggestion: (String) -> Unit,
    onOpenCouncil: () -> Unit,
    onOpenFindings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl, vertical = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OrobiIdle(modifier = Modifier.width(112.dp))
            Spacer(Modifier.height(Space.md))
            Text(
                text = "What are we defending?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(Space.md))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onOpenCouncil,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        Modifier.padding(horizontal = Space.md, vertical = Space.sm - Space.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Icon(
                            Icons.Outlined.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconSmall),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (seatNames.isEmpty()) "Seat a council" else "${seatNames.size}-seat council",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
                if (findingCount > 0) {
                    Surface(
                        onClick = onOpenFindings,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "$findingCount finding${if (findingCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm - Space.xxs),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.xl))

            Column(
                modifier = Modifier.widthIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                StarterCard(
                    icon = Icons.Outlined.Security,
                    label = "Something is wrong right now",
                    accent = true,
                    onClick = { onSuggestion(ShieldAction.INCIDENT.task("")) },
                )
                StarterCard(
                    icon = Icons.Outlined.TravelExplore,
                    label = "Trace an attack",
                    onClick = { onSuggestion(ShieldAction.TRACE.task("")) },
                )
                StarterCard(
                    icon = Icons.Outlined.PhoneAndroid,
                    label = "Audit this phone",
                    onClick = { onSuggestion(ShieldAction.PHONE_AUDIT.task("")) },
                )
                StarterCard(
                    icon = Icons.Outlined.Policy,
                    label = "Harden my project",
                    onClick = { onSuggestion(ShieldAction.HARDEN.task("")) },
                )
                StarterCard(
                    icon = Icons.Outlined.Public,
                    label = "Check my accounts",
                    onClick = { onSuggestion(ShieldAction.ACCOUNTS.task("")) },
                )
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun StarterCard(
    icon: ImageVector,
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = OroCorners.large,
        color = if (accent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Sizes.hairline, if (accent) Color.Transparent else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Sizes.icon),
                tint = if (accent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Space.md))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (accent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* --------------------------------- chips ---------------------------------- */

@Composable
fun ShieldQuickChips(onPick: (String) -> Unit,modifier: Modifier = Modifier) {
    val actions = listOf(
        ShieldAction.INCIDENT to "Incident",
        ShieldAction.TRACE to "Trace",
        ShieldAction.CHECK_URL to "Link",
        ShieldAction.PHONE_AUDIT to "Phone",
        ShieldAction.VAULT_AUDIT to "Leaks",
        ShieldAction.HARDEN to "Harden",
        ShieldAction.KEV_NEWS to "News",
    )
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        items(actions.size, key = { actions[it].first.name }) { index ->
            val (action, label) = actions[index]
            AssistChip(
                onClick = { onPick(action.task("")) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/* --------------------------------- helpers -------------------------------- */

fun toolIcon(name: String?): ImageVector = when (name) {
    "audit_phone" -> Icons.Outlined.PhoneAndroid
    "check_url" -> Icons.Outlined.Link
    "lookup_advisory" -> Icons.Outlined.BugReport
    "kev_recent" -> Icons.Outlined.Newspaper
    "scan_project_secrets" -> Icons.Outlined.Password
    "check_vault" -> Icons.Outlined.Password
    "trace_incident" -> Icons.Outlined.TravelExplore
    "enrich_indicator" -> Icons.Outlined.TravelExplore
    "audit_web_posture" -> Icons.Outlined.Policy
    "audit_dependencies" -> Icons.Outlined.Inventory2
    "draft_abuse_report" -> Icons.Outlined.Report
    "read_file", "write_file", "edit_file" -> Icons.Outlined.Description
    "list_files" -> Icons.Outlined.FolderOpen
    "run_script" -> Icons.Outlined.Terminal
    "delegate_investigation" -> Icons.Outlined.Groups
    "convene_council" -> Icons.Outlined.Groups
    "use_browser" -> Icons.Outlined.Public
    "ask_user" -> Icons.Outlined.QuestionAnswer
    "take_note" -> Icons.Outlined.StickyNote2
    "finish_task" -> Icons.Outlined.TaskAlt
    else -> Icons.Outlined.Security
}

fun toolLabel(name: String?): String = GuardTools.humanize(name.orEmpty())

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

fun clock(at: Long): String = clockFormat.format(Date(at))

fun relativeTime(at: Long): String {
    val delta = (System.currentTimeMillis() - at).coerceAtLeast(0)
    return when {
        delta < 60_000 -> "now"
        delta < 3_600_000 -> "${delta / 60_000} min ago"
        delta < 86_400_000 -> "${delta / 3_600_000} h ago"
        delta < 7 * 86_400_000 -> "${delta / 86_400_000} d ago"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(at))
    }
}

fun severityColor(severity: String?): Color = when (severity?.trim()?.uppercase()) {
    "CRITICAL" -> Color(0xFFB3261E)
    "HIGH" -> Color(0xFFD84315)
    "MEDIUM" -> Color(0xFFB26A00)
    "LOW" -> Color(0xFF3F6F3F)
    else -> Color(0xFF5A6570)
}
