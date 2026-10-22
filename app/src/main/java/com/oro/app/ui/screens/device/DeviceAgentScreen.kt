package com.oro.app.ui.screens.device

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oro.app.device.DeviceAgent
import com.oro.app.device.DeviceAgent.StepKind
import com.oro.app.device.DeviceAgent.StepStatus
import com.oro.app.device.overlay.DeviceAgentUi
import com.oro.app.device.overlay.IslandModel
import com.oro.app.device.overlay.IslandPhase
import com.oro.app.device.overlay.IslandText
import com.oro.app.data.ai.tools.QuestionOption
import com.oro.app.data.ai.tools.UserQuestion
import com.oro.app.ui.brand.OroBrand
import com.oro.app.ui.brand.OroSparkle
import com.oro.app.ui.brand.OrobiIdle
import com.oro.app.ui.brand.OrobiLoader
import com.oro.app.ui.brand.OrobiWorking
import com.oro.app.ui.components.agent.ApprovalSheet
import com.oro.app.ui.components.agent.AskUserCard
import com.oro.app.ui.components.agent.PendingApproval
import com.oro.app.ui.components.agent.PendingQuestion
import com.oro.app.ui.components.agent.RunError
import com.oro.app.ui.components.agent.RunErrorCard
import com.oro.app.ui.components.chat.ModelSelectorSheet
import com.oro.app.ui.components.chat.ModernChatInput
import com.oro.app.ui.components.expressive.EmptyStateShape
import com.oro.app.ui.components.expressive.ExpressiveEmptyState
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.OroTheme
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space
import kotlinx.coroutines.delay

/**
 * Orobi operating the phone, laid out like a chat with it: the task as your
 * message, Orobi's narration and actions as its turn, the composer at the
 * bottom (with dictation and the model chip, exactly as in the editor), and
 * questions, permission requests and errors in the same cards the editor
 * uses. While a task runs in another app, the floating island carries the
 * same information; this screen takes over whenever it is in front.
 */
@Composable
fun DeviceAgentScreen(
    onBack: () -> Unit,
    viewModel: DeviceAgentViewModel = viewModel(factory = DeviceAgentViewModel.Factory)
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshService()
        DeviceAgentUi.screenVisible.value = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { DeviceAgentUi.screenVisible.value = false }
    DisposableEffect(Unit) { onDispose { DeviceAgentUi.screenVisible.value = false } }
    // The service connects asynchronously after the user flips the switch.
    LaunchedEffect(ui.serviceEnabled) {
        while (!ui.serviceEnabled) {
            delay(1_000)
            viewModel.refreshService()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device agent", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            val started = run.startedAt > 0L
            when {
                !ui.serviceEnabled && !run.running -> SetupState(
                    onEnable = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onAppInfo = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                !started -> DeviceWelcome(
                    history = ui.history,
                    onPick = viewModel::setTask,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                else -> RunTranscriptList(
                    run = run,
                    onAnswer = viewModel::answer,
                    onAgain = { viewModel.start(run.task) },
                    onNew = viewModel::newTask,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }

            if (ui.serviceEnabled || run.running) {
                // Why the last start did not happen (it never reached the transcript).
                run.error?.takeIf { !started }?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = Space.xl, end = Space.xl, top = Space.sm)
                    )
                }
                // Pause docks on the composer, like the plan does in the editor:
                // it is about what is happening now, so it sits where the user looks.
                AnimatedVisibility(
                    visible = run.running,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    RunBar(
                        run = run,
                        onPause = viewModel::togglePause,
                        modifier = Modifier.padding(start = Space.lg, end = Space.lg, top = Space.sm)
                    )
                }
                val composerModifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Space.lg, end = Space.lg, bottom = Space.lg,
                        top = if (run.running) Space.xs + Space.xxs else Space.lg
                    )
                val model = ui.model
                if (model != null) {
                    ModernChatInput(
                        value = ui.task,
                        onValueChange = viewModel::setTask,
                        onSend = viewModel::startFromComposer,
                        onStop = viewModel::stop,
                        isEnabled = ui.serviceEnabled && !run.running,
                        isProcessing = run.running,
                        currentModel = model,
                        onModelClick = { showModels = true },
                        onSettingsClick = { showSettings = true },
                        placeholder = "What should Orobi do?",
                        settingsDescription = "Device agent settings",
                        mentions = false,
                        modifier = composerModifier
                    )
                } else {
                    NoModelBar(onPick = { showModels = true }, modifier = composerModifier)
                }
            }
        }
    }

    // The same modal gate the editor uses: it cannot be missed, and leaving
    // it without a choice counts as Deny.
    ApprovalSheet(
        approval = run.approval?.takeIf { run.running }?.let { PendingApproval(action = IslandText.approvalQuestion(it)) },
        onDecision = viewModel::approve
    )

    if (showSettings) {
        DeviceSettingsSheet(
            settings = ui.settings,
            onChange = viewModel::updateSettings,
            onDismiss = { showSettings = false }
        )
    }
    if (showModels) {
        ModelSelectorSheet(
            models = ui.models,
            selectedModel = ui.model,
            onModelSelected = { viewModel.selectModel(it); showModels = false },
            onDismiss = { showModels = false }
        )
    }
}

// ------------------------------------------------------------------ setup

@Composable
internal fun SetupState(onEnable: () -> Unit, onAppInfo: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = Space.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExpressiveEmptyState(
                icon = Icons.Outlined.TouchApp,
                title = "Let Orobi use your phone",
                description = "Turn on Oro Device Agent in Accessibility settings.",
                shape = EmptyStateShape.Clover,
                actionLabel = "Open settings",
                onAction = onEnable,
                illustration = { OrobiIdle(modifier = Modifier.width(150.dp)) }
            )
            // Android 13+ greys the switch out for apps installed outside a
            // store until "Allow restricted settings" is chosen in App info.
            if (Build.VERSION.SDK_INT >= 33) {
                Spacer(Modifier.height(Space.sm))
                TextButton(onClick = onAppInfo) {
                    Text("Switch greyed out? Allow restricted settings", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// --------------------------------------------------------------- welcome

/** Mirrors the editor's empty chat: Orobi, one line, and starters that fill the composer. */
@Composable
internal fun DeviceWelcome(history: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val recent = history.take(2)
    val starters = Starters.filter { it !in recent }.take(5 - recent.size)
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl, vertical = Space.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OrobiIdle(modifier = Modifier.width(150.dp))
            Spacer(Modifier.height(Space.xl))
            Text(
                text = "What should I do on your phone?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Space.xl + Space.xs))
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                recent.forEach { StarterRow(text = it, recent = true, onClick = { onPick(it) }) }
                starters.forEach { StarterRow(text = it, recent = false, onClick = { onPick(it) }) }
            }
        }
    }
}

private val Starters = listOf(
    "Search YouTube for lo-fi beats and play the first video",
    "Set an alarm for 6:30 tomorrow morning",
    "Find coffee shops near me in Maps",
    "Turn on Do Not Disturb",
    "Check my battery usage and tell me the top app",
)

/** The editor's starter prompt: a sparkle for ideas, a clock for what you ran before. */
@Composable
private fun StarterRow(text: String, recent: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md + Space.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (recent) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = "Recent",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
            } else {
                Box(Modifier.size(Sizes.iconSmall), contentAlignment = Alignment.Center) {
                    OroSparkle(modifier = Modifier.size(12.dp), color = OroBrand.Magenta)
                }
            }
            Spacer(Modifier.width(Space.md))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ------------------------------------------------------------ transcript

@Composable
internal fun RunTranscriptList(
    run: DeviceAgent.State,
    onAnswer: (String) -> Unit,
    onAgain: () -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val segments = remember(run.steps) { DeviceTranscript.segments(run.steps) }
    val lastKey = segments.lastOrNull()?.key
    // Follow the run like the editor follows a reply: new steps, a question,
    // the result -- the end of the list is where the news is.
    val itemCount = 1 + segments.size + (if (run.running) 1 else 0) + (if (run.finished) 1 else 0)
    LaunchedEffect(run.steps.size, run.question, run.finished, run.paused) {
        listState.animateScrollToItem(itemCount - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg)
    ) {
        item(key = "task", contentType = "task") {
            TaskBubble(task = run.task, startedAt = run.startedAt, modifier = Modifier.fillMaxWidth())
        }
        items(
            items = segments,
            key = { it.key },
            contentType = { if (it is DeviceSegment.Thought) "thought" else "actions" }
        ) { segment ->
            when (segment) {
                is DeviceSegment.Thought -> ThoughtText(segment.step, Modifier.animateItem())
                is DeviceSegment.Actions -> ActionGroup(
                    steps = segment.steps,
                    live = run.running && segment.key == lastKey,
                    modifier = Modifier.animateItem()
                )
            }
        }
        if (run.running) {
            item(key = "status", contentType = "status") {
                val question = run.question
                when {
                    question != null -> {
                        val pending = remember(question) {
                            PendingQuestion(
                                id = "device-" + question.hashCode(),
                                questions = listOf(
                                    UserQuestion(
                                        question = question.text,
                                        options = question.options.map { QuestionOption(it) }
                                    )
                                )
                            )
                        }
                        AskUserCard(
                            question = pending,
                            onSubmit = { answers -> onAnswer(answers.firstOrNull()?.takeIf { it.isNotBlank() } ?: "(no answer)") },
                            onDismiss = { onAnswer("(skipped)") },
                            modifier = Modifier.animateItem()
                        )
                    }
                    run.paused -> PausedRow(Modifier.animateItem())
                    run.approval != null -> OrobiWorking(
                        label = "Waiting for your OK",
                        maxLines = 1,
                        modifier = Modifier.padding(vertical = Space.xs).animateItem()
                    )
                    else -> OrobiWorking(
                        label = IslandModel.headline(run),
                        maxLines = 1,
                        modifier = Modifier.padding(vertical = Space.xs).animateItem()
                    )
                }
            }
        }
        if (run.finished) {
            item(key = "result", contentType = "result") {
                ResultBlock(run = run, onAgain = onAgain, onNew = onNew, modifier = Modifier.animateItem())
            }
        }
    }
}

/** The task, as the user's message: the editor's own bubble. */
@Composable
private fun TaskBubble(task: String, startedAt: Long, modifier: Modifier = Modifier) {
    val colors = OroTheme.extendedColors
    val time = remember(startedAt) {
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(startedAt))
    }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                .background(colors.chatUserBubble)
                .padding(Space.md)
        ) {
            Text(
                text = task,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.chatUserText
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.chatUserText.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/** Orobi's narration between actions, as plain assistant prose. */
@Composable
private fun ThoughtText(step: DeviceAgent.Step, modifier: Modifier = Modifier) {
    val text = remember(step.title) { IslandModel.plain(step.title) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * A run of actions between two narrations. Live and short runs stay open;
 * finished long ones fold into one summary line with their icons, like the
 * editor's tool-call groups, and open again on tap.
 */
@Composable
private fun ActionGroup(steps: List<DeviceAgent.Step>, live: Boolean, modifier: Modifier = Modifier) {
    val key = steps.first().id
    var toggled by remember(key) { mutableStateOf<Boolean?>(null) }
    val foldable = steps.size >= FoldAt
    val open = toggled ?: (live || !foldable)
    val chevron by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "groupChevron"
    )

    Column(modifier.fillMaxWidth()) {
        if (foldable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(OroCorners.small)
                    .clickable(onClickLabel = if (open) "Hide actions" else "Show actions") { toggled = !open }
                    .padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.icon).rotate(chevron)
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = IslandModel.actions(steps.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (steps.any { it.status == StepStatus.RUNNING }) {
                    Spacer(Modifier.width(Space.sm))
                    OrobiLoader(modifier = Modifier.width(18.dp), contentDescription = null)
                }
                Spacer(Modifier.weight(1f))
                if (!open) IconTrail(steps)
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
        ) {
            Column(
                modifier = Modifier.padding(top = if (foldable) Space.xs else 0.dp),
                verticalArrangement = Arrangement.spacedBy(Space.xxs)
            ) {
                steps.forEach { StepRow(it) }
            }
        }
    }
}

@Composable
private fun IconTrail(steps: List<DeviceAgent.Step>) {
    val icons = remember(steps) { steps.map { DeviceStepVisuals.icon(it) }.distinct().take(5) }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        icons.forEach { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.iconSmall).alpha(0.7f)
            )
        }
    }
}

/**
 * One action, as a tool row in the editor: a neutral glyph on a soft tile,
 * the verb, the target in bold, and only the outcomes worth a glance --
 * running, failed, denied, or a tap that changed nothing.
 */
@Composable
private fun StepRow(step: DeviceAgent.Step) {
    val scheme = MaterialTheme.colorScheme
    val failed = step.status == StepStatus.FAILED || step.status == StepStatus.BLOCKED
    val (verb, subject) = remember(step.title, step.tool, step.kind) { DeviceTranscript.splitTitle(step) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.xs, vertical = Space.xs + Space.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(OroCorners.small)
                    .background(if (failed) scheme.errorContainer else scheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = DeviceStepVisuals.icon(step),
                    contentDescription = null,
                    tint = if (failed) scheme.onErrorContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
            }
            Spacer(Modifier.width(Space.md - Space.xxs))
            if (subject != null) {
                Text(
                    text = verb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp)
                )
                Spacer(Modifier.width(Space.xs + Space.xxs))
                Text(
                    text = subject,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = verb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (step.kind == StepKind.QUESTION) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = if (step.kind == StepKind.QUESTION) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.width(Space.sm))
            StepOutcome(step)
        }
        val detail = step.detail.takeIf {
            it.isNotBlank() && (failed || step.kind == StepKind.QUESTION)
        }
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // Under the label, past the icon tile.
                modifier = Modifier.padding(start = Space.xs + 28.dp + Space.md - Space.xxs, end = Space.xs)
            )
        }
    }
}

@Composable
private fun StepOutcome(step: DeviceAgent.Step) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelMedium
    when (step.status) {
        StepStatus.RUNNING -> OrobiLoader(modifier = Modifier.width(Sizes.iconLarge), contentDescription = "Running")
        StepStatus.WAITING -> Text("Waiting", style = style, color = scheme.onSurfaceVariant, maxLines = 1)
        StepStatus.FAILED -> Text("Failed", style = style, color = scheme.error, maxLines = 1)
        StepStatus.BLOCKED -> Text("Denied", style = style, color = scheme.error, maxLines = 1)
        StepStatus.DONE -> if (step.screenChanged == false) {
            Text("No change", style = style, color = scheme.outline, maxLines = 1)
        }
        else -> Unit
    }
}

@Composable
private fun PausedRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm + Space.xxs)
    ) {
        Icon(
            Icons.Outlined.Pause,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.icon)
        )
        Text("Paused", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- result

/**
 * How it ended. Success reads like Orobi's final message, with what it noted
 * along the way; anything else is the editor's run-error card, whose action
 * runs the task again.
 */
@Composable
private fun ResultBlock(run: DeviceAgent.State, onAgain: () -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    val phase = IslandModel.phase(run)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        if (phase == IslandPhase.Done) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.TaskAlt,
                    contentDescription = null,
                    tint = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) OroBrand.GreenDark else OroBrand.GreenLight,
                    modifier = Modifier.size(Sizes.icon)
                )
                Spacer(Modifier.width(Space.sm))
                Text("Done", style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = runMeta(run),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            run.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                com.oro.app.ui.components.MarkdownText(markdown = summary, modifier = Modifier.fillMaxWidth())
            }
        } else {
            val message = run.summary?.takeIf { run.success == false && it.isNotBlank() }
                ?: run.error
                ?: "Orobi didn't finish this task."
            RunErrorCard(
                error = RunError(message = message, actionLabel = "Run again", isNotice = IslandModel.stoppedByUser(run)),
                onRetry = onAgain,
                onDismiss = onNew
            )
        }
        if (run.notes.isNotEmpty()) NotesList(run.notes)
        if (phase == IslandPhase.Done) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.End)
            ) {
                TextButton(onClick = onNew) { Text("New task", maxLines = 1) }
                FilledTonalButton(onClick = onAgain) {
                    Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall + Space.xxs))
                    Spacer(Modifier.width(Space.xs + Space.xxs))
                    Text("Run again", maxLines = 1)
                }
            }
        }
    }
}

/** Facts Orobi kept while it worked: prices, codes, names. */
@Composable
private fun NotesList(notes: List<String>) {
    Surface(
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            notes.forEach { note ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.AutoMirrored.Outlined.StickyNote2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xxs).size(Sizes.iconSmall)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private fun runMeta(run: DeviceAgent.State): String = listOfNotNull(
    IslandModel.actions(run.actions).takeIf { run.actions > 0 },
    run.endedAt.takeIf { it > 0 && run.startedAt > 0 }?.let { IslandModel.elapsed(it - run.startedAt) }
).joinToString(" · ")

// --------------------------------------------------------------- run bar

/** Docked above the composer while a task runs: time, actions, where, and Pause. */
@Composable
internal fun RunBar(run: DeviceAgent.State, onPause: () -> Unit, modifier: Modifier = Modifier) {
    val running = run.running
    val now by produceState(System.currentTimeMillis(), running) {
        value = System.currentTimeMillis()
        while (running) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = Space.md, end = Space.xs, top = Space.xs, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.iconSmall)
            )
            Spacer(Modifier.width(Space.xs))
            Text(
                text = IslandModel.elapsed(now - run.startedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.width(Space.md))
            Icon(
                Icons.Outlined.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.iconSmall)
            )
            Spacer(Modifier.width(Space.xs))
            Text(
                text = if (run.maxActions > 0) "${run.actions}/${run.maxActions}" else run.actions.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (run.currentApp.isNotBlank()) {
                Spacer(Modifier.width(Space.md))
                Text(
                    text = run.currentApp,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            TextButton(onClick = onPause) {
                Icon(
                    imageVector = if (run.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconSmall + Space.xxs)
                )
                Spacer(Modifier.width(Space.xs))
                Text(if (run.paused) "Resume" else "Pause", maxLines = 1)
            }
        }
    }
}

/** Shown in place of the composer only when no model is enabled at all. */
@Composable
internal fun NoModelBar(onPick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = OroCorners.xLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "No model is switched on",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = onPick) { Text("Choose", maxLines = 1) }
        }
    }
}

/** Long finished runs of actions fold from this many steps. */
private const val FoldAt = 4
