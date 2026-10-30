package com.oro.app.ui.screens.shield

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oro.app.guard.GuardAgent
import com.oro.app.ui.components.chat.ModernChatInput
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/**
 * The Shield: a chat with the guard. A message starts or continues a run; the
 * guard's prose streams in as replies, every tool call becomes an inline card
 * that opens to its evidence, and a question becomes a tap-to-answer card in
 * the transcript. The composer carries the guard model and the council behind
 * it; findings live one tap away in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShieldScreen(
    onBack: () -> Unit,
    onOpenModels: () -> Unit = {},
    vm: ShieldViewModel = viewModel(),
) {
    val guard by vm.guardState.collectAsState()
    val findings by vm.findings.collectAsState()
    val models by vm.models.collectAsState()
    val memberIds by vm.memberIds.collectAsState()
    val chair by vm.chair.collectAsState()
    val dailyPass by vm.dailyPass.collectAsState()
    val councilOnPrompt by vm.councilOnPrompt.collectAsState()
    val news by vm.news.collectAsState()
    val runnerUrl by vm.runnerUrl.collectAsState()
    val runnerToken by vm.runnerToken.collectAsState()

    var draft by rememberSaveable { mutableStateOf("") }
    var councilOpen by rememberSaveable { mutableStateOf(false) }
    var findingsOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var runnerOpen by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val seatNames = memberIds.mapNotNull { id -> models.firstOrNull { it.id == id }?.name }
    val hasRunningTool = guard.messages.any { it.role == GuardAgent.MsgRole.TOOL && it.status == GuardAgent.MsgStatus.RUNNING }
    val trailing = (if (guard.question != null) 1 else 0) +
        (if (guard.running && guard.question == null && !hasRunningTool) 1 else 0) +
        (if (!guard.running && guard.result?.error != null) 1 else 0)
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(guard.messages.size, guard.question?.text, guard.running, imeVisible) {
        val total = guard.messages.size + trailing
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    val status = when {
        guard.question != null -> "Waiting for you"
        guard.running -> guard.status.ifBlank { "Working" }
        guard.result?.error != null -> "Run failed"
        guard.messages.isNotEmpty() -> "Ready"
        else -> "Personal cyber-defence"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                title = {
                    Column {
                        Text("Shield", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { findingsOpen = true }) {
                        BadgedBox(badge = {
                            if (findings.isNotEmpty()) {
                                Badge { Text(if (findings.size > 9) "9+" else findings.size.toString()) }
                            }
                        }) {
                            Icon(Icons.Outlined.Assessment, "Findings")
                        }
                    }
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Outlined.Tune, "Shield settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            if (guard.messages.isEmpty() && !guard.running) {
                ShieldWelcome(
                    seatNames = seatNames,
                    findingCount = findings.size,
                    latestNews = news,
                    onSuggestion = { draft = it },
                    onOpenCouncil = { councilOpen = true },
                    onOpenFindings = { findingsOpen = true },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    items(
                        items = guard.messages,
                        key = { it.id },
                        contentType = { it.role.name },
                    ) { message ->
                        when (message.role) {
                            GuardAgent.MsgRole.USER -> ShieldUserBubble(message.text, message.at)
                            GuardAgent.MsgRole.GUARD -> ShieldGuardMessage(message, message.streaming)
                            GuardAgent.MsgRole.TOOL -> GuardToolCard(message)
                            GuardAgent.MsgRole.COUNCIL -> ShieldCouncilCard(message)
                            GuardAgent.MsgRole.NOTE -> GuardNoteRow(message)
                            GuardAgent.MsgRole.SYSTEM -> Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    guard.question?.let { question ->
                        item(key = "question") {
                            ShieldQuestionCard(
                                question = question,
                                onAnswer = { vm.answer(it) },
                                modifier = Modifier.padding(top = Space.xs),
                            )
                        }
                    }
                    if (guard.question == null && guard.running && !hasRunningTool) {
                        item(key = "working") {
                            ShieldWorking(guard.status.ifBlank { "Working" })
                        }
                    }
                    guard.result?.let { result ->
                        if (!guard.running && result.error != null) {
                            item(key = "run-error") {
                                ShieldErrorCard(result = result, onNewChat = { vm.newChat(); draft = "" })
                            }
                        }
                    }
                }
            }

            if (!guard.running) {
                ShieldQuickChips(
                    councilOn = councilOnPrompt,
                    seatCount = memberIds.size,
                    onCouncilToggle = { vm.setCouncilOnPrompt(!councilOnPrompt) },
                    onPick = { draft = it },
                )
                Spacer(Modifier.height(Space.sm))
            }

            val model = chair
            if (model != null) {
                ModernChatInput(
                    value = draft,
                    onValueChange = { draft = it },
                    onSend = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            vm.send(text)
                            draft = ""
                        }
                    },
                    onStop = vm::stop,
                    isEnabled = !guard.running,
                    isProcessing = guard.running,
                    agentMode = true,
                    currentModel = model,
                    onModelClick = { councilOpen = true },
                    onSettingsClick = { settingsOpen = true },
                    settingsDescription = "Shield settings",
                    mentions = false,
                    placeholder = "Ask the Shield, or describe what happened",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.lg, end = Space.lg, top = Space.sm, bottom = Space.lg),
                )
            } else {
                NoModelCard(onOpenModels = onOpenModels)
            }
        }
    }

    if (councilOpen) {
        CouncilSheet(
            models = models,
            memberIds = memberIds,
            chairId = chair?.id,
            dailyPass = dailyPass,
            onToggleMember = vm::toggleMember,
            onSetChair = vm::setChair,
            onDailyPass = vm::setDailyPass,
            onDismiss = { councilOpen = false },
        )
    }
    if (findingsOpen) {
        FindingsSheet(
            findings = findings,
            onAskGuard = { prompt ->
                findingsOpen = false
                vm.send(prompt)
            },
            onClear = vm::clearFindings,
            onDismiss = { findingsOpen = false },
        )
    }
    if (settingsOpen) {
        GuardSettingsSheet(
            dailyPass = dailyPass,
            councilOnPrompt = councilOnPrompt,
            runnerUrl = runnerUrl,
            onDailyPass = vm::setDailyPass,
            onCouncilOnPrompt = vm::setCouncilOnPrompt,
            onOpenCouncil = {
                settingsOpen = false
                councilOpen = true
            },
            onOpenRunner = {
                settingsOpen = false
                runnerOpen = true
            },
            onNewChat = {
                settingsOpen = false
                vm.newChat()
                draft = ""
            },
            onDismiss = { settingsOpen = false },
        )
    }
    if (runnerOpen) {
        RunnerFormSheet(
            initialUrl = runnerUrl,
            initialToken = runnerToken,
            onSave = { url, token ->
                vm.saveRunner(url, token)
                runnerOpen = false
            },
            onDismiss = { runnerOpen = false },
        )
    }
}

@Composable
private fun NoModelCard(onOpenModels: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.lg),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Security,
                contentDescription = null,
                modifier = Modifier.size(Sizes.icon),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("No model is set up", style = MaterialTheme.typography.bodyMediumEmphasized, maxLines = 1)
                Text(
                    "Add one to start the Shield.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Button(onClick = onOpenModels) { Text("Add") }
        }
    }
}
