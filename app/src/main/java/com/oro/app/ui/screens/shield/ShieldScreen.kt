package com.oro.app.ui.screens.shield

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oro.app.guard.GuardAgent

/**
 * The shield: the guard agent's home. Cards start standing orders (audit the
 * phone, judge a link, scan the project, read the exploitation news, run
 * incident response, convene the council); while a run is live the whole
 * screen becomes its transcript, questions and all; finished findings stay
 * on the board. The council roster sits in the settings sheet -- every
 * member deliberates at its model's deepest reasoning level.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldScreen(
    onBack: () -> Unit,
    vm: ShieldViewModel = viewModel(),
) {
    val guard by vm.guardState.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }
    var inputAction by remember { mutableStateOf<ShieldAction?>(null) }
    var inputText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.Security, "Shield") }
                },
                title = { Column {
                    Text("Shield", fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            guard.running -> guard.status.ifBlank { "Watching" }
                            guard.result != null -> if (guard.result?.success == true) "Task answered" else "Task ended"
                            else -> "Personal cyber-defence"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } },
                actions = {
                    IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Outlined.Settings, "Council settings") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Council seats, always visible: who answers when it matters.
            val seats by vm.seatedNames.collectAsState()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                seats.forEach { name ->
                    AssistChip(onClick = { settingsOpen = true }, label = { Text(name, style = MaterialTheme.typography.labelSmall) })
                }
                if (seats.isEmpty()) Text("No council seated", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            if (guard.running || guard.result != null) {
                RunTranscript(guard, vm)
            } else {
                ActionGrid(onPick = { action ->
                    when (action) {
                        ShieldAction.PHONE_AUDIT, ShieldAction.VAULT_AUDIT, ShieldAction.PROJECT_SECRETS, ShieldAction.KEV_NEWS -> vm.runAction(action)
                        else -> { inputAction = action; inputText = "" }
                    }
                })
                FindingsBoard(vm)
            }
        }
    }

    // Input dialog for the actions that take a subject.
    inputAction?.let { action ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { inputAction = null },
            title = { Text(action.label) },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = {
                        Text(when (action) {
                            ShieldAction.CHECK_URL -> "https://the-suspicious.link/…"
                            ShieldAction.INCIDENT -> "What are you seeing? (optional)"
                            ShieldAction.COUNCIL -> "The question for the council…"
                            else -> "Your question…"
                        })
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    val a = action; val t = inputText
                    inputAction = null
                    if (a != ShieldAction.INCIDENT && t.isBlank() && a != ShieldAction.FREEFORM) return@Button
                    vm.runAction(a, t)
                }) { Text("Go") }
            },
            dismissButton = { OutlinedButton(onClick = { inputAction = null }) { Text("Cancel") } },
        )
    }

    if (settingsOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val models by vm.models.collectAsState()
        val memberIds by vm.memberIds.collectAsState()
        val daily by vm.dailyPass.collectAsState()
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text("The council", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Every seated model deliberates each brief in parallel at its deepest reasoning level: first responder, adversary's advocate, verifier -- then a chair merges the record. Seat the models you trust; three is a council, one is an opinion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                models.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { vm.toggleMember(m.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = m.id in memberIds, onCheckedChange = { vm.toggleMember(m.id) })
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.bodyMedium)
                            Text(m.provider.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily guard pass", style = MaterialTheme.typography.bodyMedium)
                        Text("Exploitation news, vault and posture checks once a day.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = daily, onCheckedChange = { vm.setDailyPass(it) })
                }
            }
        }
    }
}

@Composable
private fun ActionGrid(onPick: (ShieldAction) -> Unit) {
    val cards = listOf(
        Triple(ShieldAction.INCIDENT, Icons.Outlined.BugReport, "Something is wrong — start here"),
        Triple(ShieldAction.PHONE_AUDIT, Icons.Outlined.PhoneAndroid, "Audit this phone"),
        Triple(ShieldAction.CHECK_URL, Icons.Outlined.Link, "Judge a link"),
        Triple(ShieldAction.VAULT_AUDIT, Icons.Outlined.Key, "Audit saved logins"),
        Triple(ShieldAction.PROJECT_SECRETS, Icons.Outlined.Code, "Scan project for secrets"),
        Triple(ShieldAction.KEV_NEWS, Icons.Outlined.Newspaper, "Exploitation news"),
        Triple(ShieldAction.COUNCIL, Icons.Outlined.Groups, "Ask the council"),
        Triple(ShieldAction.FREEFORM, Icons.Outlined.Security, "Ask the guard anything"),
    )
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cards) { (action, icon, label) ->
            Card(
                Modifier.fillMaxWidth().clickable { onPick(action) },
                colors = CardDefaults.cardColors(
                    containerColor = if (action == ShieldAction.INCIDENT)
                        MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow
                ),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(22.dp),
                        tint = if (action == ShieldAction.INCIDENT) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun RunTranscript(guard: GuardAgent.State, vm: ShieldViewModel) {
    var answer by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        if (guard.running) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
        }
        guard.result?.let { r ->
            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor =
                    if (r.success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (r.success) "Verdict" else (r.error ?: "Ended"), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(r.summary.ifBlank { r.error.orEmpty() }, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { vm.clear() }) { Text("New task") }
                }
            }
        }
        val steps = guard.steps
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            items(steps, key = { it.id }) { step ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(
                            when (step.status) {
                                GuardAgent.StepStatus.RUNNING -> MaterialTheme.colorScheme.primary
                                GuardAgent.StepStatus.FAILED -> MaterialTheme.colorScheme.error
                                GuardAgent.StepStatus.WAITING -> MaterialTheme.colorScheme.tertiary
                                GuardAgent.StepStatus.DONE -> MaterialTheme.colorScheme.outline
                            }, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(10.dp))
                        Text(step.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    if (step.detail.isNotBlank()) {
                        Text(step.detail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = if (step.kind == GuardAgent.StepKind.THOUGHT) FontFamily.Default else FontFamily.Monospace,
                            modifier = Modifier.padding(start = 18.dp, top = 2.dp))
                    }
                }
            }
            guard.question?.let { q ->
                item {
                    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(q.text, style = MaterialTheme.typography.bodyMedium)
                            if (q.options.isNotEmpty()) {
                                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    q.options.forEach { opt ->
                                        AssistChip(onClick = { vm.answer(opt) }, label = { Text(opt) })
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = answer, onValueChange = { answer = it },
                                modifier = Modifier.fillMaxWidth(), placeholder = { Text("Answer…") })
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { if (answer.isNotBlank()) { vm.answer(answer); answer = "" } }) { Text("Send") }
                        }
                    }
                }
            }
            guard.seats.forEach { seat ->
                item { Text(seat, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
        if (guard.running) {
            OutlinedButton(
                onClick = { vm.stop() },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Icon(Icons.Outlined.Stop, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Stop the guard") }
        }
    }
}

@Composable
private fun FindingsBoard(vm: ShieldViewModel) {
    val findings by vm.findings.collectAsState()
    if (findings.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Findings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButtonSmall("Clear") { vm.clearFindings() }
        }
        findings.sortedBy { severityRank(it["severity"]) }.take(12).forEach { f ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Text(f["severity"]?.uppercase() ?: "INFO", style = MaterialTheme.typography.labelSmall,
                    color = severityColor(f["severity"]), modifier = Modifier.width(64.dp))
                Column(Modifier.weight(1f)) {
                    Text(f["title"].orEmpty(), style = MaterialTheme.typography.bodySmall)
                    if (!f["action"].isNullOrBlank()) {
                        Text(f["action"]!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButtonSmall(label: String, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp))
}

private fun severityRank(s: String?) = when (s?.uppercase()) {
    "CRITICAL" -> 0; "HIGH" -> 1; "MEDIUM" -> 2; "LOW" -> 3; else -> 4
}

@Composable
private fun severityColor(s: String?): Color = when (s?.uppercase()) {
    "CRITICAL", "HIGH" -> MaterialTheme.colorScheme.error
    "MEDIUM" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
