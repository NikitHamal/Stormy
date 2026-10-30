package com.oro.app.ui.screens.shield

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.council.CouncilRole
import com.oro.app.ui.brand.OroSparkle
import com.oro.app.ui.components.expressive.FormSheet
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/* --------------------------------- council -------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouncilSheet(
    models: List<AiModel>,
    memberIds: List<String>,
    chairId: String?,
    dailyPass: Boolean,
    onToggleMember: (String) -> Unit,
    onSetChair: (String) -> Unit,
    onDailyPass: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val seated = models.filter { it.id in memberIds }
        .sortedBy { memberIds.indexOf(it.id) }
    val available = models.filter { it.id !in memberIds }
        .sortedWith(compareBy({ it.name.lowercase() }, { it.provider.displayName.lowercase() }))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = Space.xl),
        ) {
            SheetHeader(
                title = "Council",
                subtitle = if (seated.isEmpty()) "Seat at least one model." else "${seated.size} seats · every seat reasons at its maximum",
                onDone = onDismiss,
            )
            Spacer(Modifier.height(Space.md))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (seated.isNotEmpty()) {
                    item(key = "seated-label") { SectionLabel("Seated") }
                    items(seated, key = { it.id }) { model ->
                        SeatRow(
                            model = model,
                            seated = true,
                            chair = model.id == chairId,
                            role = roleFor(memberIds.indexOf(model.id)),
                            onToggle = { onToggleMember(model.id) },
                            onChair = { onSetChair(model.id) },
                        )
                    }
                }
                if (available.isNotEmpty()) {
                    item(key = "available-label") {
                        SectionLabel(if (seated.isEmpty()) "Choose a model" else "Add a seat")
                    }
                    items(available, key = { it.id }) { model ->
                        SeatRow(
                            model = model,
                            seated = false,
                            chair = false,
                            role = null,
                            onToggle = { onToggleMember(model.id) },
                            onChair = { onSetChair(model.id) },
                        )
                    }
                }
                if (models.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "No models are configured yet. Add one in Settings → AI models.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "daily") {
                    Spacer(Modifier.height(Space.sm))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onDailyPass(!dailyPass) }
                            .padding(vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Daily guard pass", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "First Shield visit each day: posture, vault, exploitation news.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(checked = dailyPass, onCheckedChange = onDailyPass)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeatRow(
    model: AiModel,
    seated: Boolean,
    chair: Boolean,
    role: CouncilRole?,
    onToggle: () -> Unit,
    onChair: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = OroCorners.large,
        color = if (seated) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            Sizes.hairline,
            if (chair) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(Sizes.avatarLarge),
                shape = CircleShape,
                color = if (seated) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = model.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (seated) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(model.provider.displayName)
                        if (role != null) append(" · ").append(roleLabel(role))
                        if (chair) append(" · guard model")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chair) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (seated) {
                IconButton(onClick = onChair) {
                    Icon(
                        imageVector = if (chair) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (chair) "This seat drives the guard" else "Use ${model.name} as the guard model",
                        modifier = Modifier.size(Sizes.icon),
                        tint = if (chair) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Checkbox(checked = seated, onCheckedChange = { onToggle() })
        }
    }
}

private fun roleFor(index: Int): CouncilRole? = when (index) {
    0 -> CouncilRole.FIRST_RESPONDER
    1 -> CouncilRole.ADVERSARY
    2 -> CouncilRole.VERIFIER
    else -> null
}

private fun roleLabel(role: CouncilRole): String = when (role) {
    CouncilRole.FIRST_RESPONDER -> "first responder"
    CouncilRole.ADVERSARY -> "adversary's advocate"
    CouncilRole.VERIFIER -> "verifier"
}

/* -------------------------------- findings -------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindingsSheet(
    findings: List<Map<String, String>>,
    onAskGuard: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ordered = remember(findings) {
        findings.sortedWith(compareBy({ severityRank(it["severity"]) }, { -(it["at"]?.toLongOrNull() ?: 0L) }))
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = Space.xl),
        ) {
            SheetHeader(
                title = "Findings",
                subtitle = if (ordered.isEmpty()) "Audits and council verdicts land here." else "${ordered.size} on the board",
                onDone = onDismiss,
            )
            if (ordered.isNotEmpty()) {
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = Space.sm)) {
                    Text("Clear", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(Space.sm))
            if (ordered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.avatarLarge),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "Nothing on the board.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    items(ordered, key = { (it["at"] ?: "") + it["title"] }) { finding ->
                        FindingCard(
                            finding = finding,
                            onAsk = {
                                onAskGuard(
                                    "Explain this finding and fix it if you can: " +
                                        "[${finding["severity"]?.uppercase().orEmpty()}] ${finding["title"].orEmpty()} -> ${finding["action"].orEmpty()} " +
                                        "(source: ${finding["source"].orEmpty()})"
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingCard(finding: Map<String, String>, onAsk: () -> Unit) {
    val severity = finding["severity"]?.uppercase().orEmpty().ifBlank { "INFO" }
    val accent = severityColor(severity)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = severityGlyph(severity),
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconSmall),
                    tint = accent,
                )
                Spacer(Modifier.width(Space.xxs + Space.xxs))
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f)) {
                    Text(
                        text = severity,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xxs + 1.dp),
                    )
                }
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = listOfNotNull(finding["source"], finding["at"]?.toLongOrNull()?.let(::relativeTime)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                text = finding["title"].orEmpty(),
                style = MaterialTheme.typography.bodyMediumEmphasized,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (!finding["action"].isNullOrBlank()) {
                Spacer(Modifier.height(Space.xxs))
                Text(
                    text = finding["action"].orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onAsk,
                contentPadding = PaddingValues(horizontal = Space.sm, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text("Fix with the guard", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun severityRank(severity: String?): Int = when (severity?.trim()?.uppercase()) {
    "CRITICAL" -> 0
    "HIGH" -> 1
    "MEDIUM" -> 2
    "LOW" -> 3
    else -> 4
}

private fun severityGlyph(severity: String): ImageVector = when (severity.uppercase()) {
    "CRITICAL", "HIGH" -> Icons.Outlined.ErrorOutline
    "MEDIUM" -> Icons.Outlined.WarningAmber
    "LOW" -> Icons.Outlined.Info
    else -> Icons.Outlined.CheckCircle
}

/* -------------------------------- settings -------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardSettingsSheet(
    dailyPass: Boolean,
    councilOnPrompt: Boolean,
    runnerUrl: String,
    onDailyPass: (Boolean) -> Unit,
    onCouncilOnPrompt: (Boolean) -> Unit,
    onOpenCouncil: () -> Unit,
    onOpenRunner: () -> Unit,
    onNewChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.xl),
        ) {
            SheetHeader(title = "Shield settings", subtitle = null, onDone = onDismiss)
            Spacer(Modifier.height(Space.md))
            SettingsRow(
                icon = Icons.Outlined.Groups,
                title = "Council",
                subtitle = "Seats, roles and the guard model",
                onClick = onOpenCouncil,
            )
            SettingsRow(
                icon = Icons.Outlined.Terminal,
                title = "Code execution",
                subtitle = if (runnerUrl.isBlank()) "Public sandboxes (keyless)" else "Your runner",
                onClick = onOpenRunner,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onCouncilOnPrompt(!councilOnPrompt) }
                    .padding(vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Council on every prompt", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "All seats deliberate before the guard acts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = councilOnPrompt, onCheckedChange = onCouncilOnPrompt)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onDailyPass(!dailyPass) }
                    .padding(vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Daily guard pass", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "First visit each day: posture, vault, news",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Switch(checked = dailyPass, onCheckedChange = onDailyPass)
            }
            SettingsRow(
                icon = Icons.Outlined.AddComment,
                title = "New chat",
                subtitle = "Clear the transcript, keep the findings",
                onClick = onNewChat,
            )
            Spacer(Modifier.height(Space.md))
            Text(
                "The Shield traces and reports attackers through the parties that control their infrastructure. It never touches their systems.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Sizes.icon),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RunnerFormSheet(
    initialUrl: String,
    initialToken: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var token by rememberSaveable(initialToken) { mutableStateOf(initialToken) }
    FormSheet(
        title = "Code execution",
        icon = Icons.Outlined.Terminal,
        onDismiss = onDismiss,
        confirmLabel = "Save",
        onConfirm = { onSave(url.trim(), token.trim()) },
    ) {
        Text(
            "Empty uses Judge0 and Wandbox: keyless, disposable, no account. Point at your own runner — a self-hosted Piston, a free VM, a Cloudflare quick tunnel — to keep code off third parties.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.md))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Runner URL") },
            placeholder = { Text("https://runner.example.com/run") },
        )
        Spacer(Modifier.height(Space.sm))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Bearer token (optional)") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            EngineChip("Judge0")
            EngineChip("Wandbox")
            if (url.isNotBlank()) EngineChip("Your runner")
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "The runner receives {language, code, stdin} and returns {stdout, stderr, exit_code}. Never send secrets.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EngineChip(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(horizontal = Space.sm, vertical = Space.xxs + 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xxs),
        ) {
            OroSparkle(Modifier.size(8.dp), color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/* ---------------------------------- shared -------------------------------- */

@Composable
private fun SheetHeader(title: String, subtitle: String?, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onDone, contentPadding = PaddingValues(horizontal = Space.sm)) {
            Text("Done", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.xs, bottom = Space.xxs),
    )
}
