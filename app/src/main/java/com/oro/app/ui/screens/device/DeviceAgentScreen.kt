package com.oro.app.ui.screens.device

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.oro.app.ui.brand.OrobiIdle
import com.oro.app.ui.brand.OrobiWorking
import com.oro.app.ui.components.chat.ModelSelectorSheet
import kotlinx.coroutines.delay

private val Magenta = Color(0xFFE8508E)
private val Violet = Color(0xFF7C5CFF)
private val Emerald = Color(0xFF10B981)
private val Amber = Color(0xFFF59E0B)
private val Rose = Color(0xFFEF4444)

private val Suggestions = listOf(
    "Set an alarm for 6:30 tomorrow morning",
    "Turn on dark mode",
    "Search YouTube for lo-fi beats and play the first video",
    "What's the weather in Kathmandu today?",
    "Open Maps and find coffee shops near me",
    "Check my battery usage and tell me the top app",
    "Turn on Do Not Disturb",
    "Take a screenshot",
)

@OptIn(ExperimentalMaterial3Api::class)
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

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshService() }
    // The service connects asynchronously after the user flips the switch.
    LaunchedEffect(ui.serviceEnabled) {
        while (!ui.serviceEnabled) { delay(1_000); viewModel.refreshService() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device agent", fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Tune, contentDescription = "Device agent settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        val listState = rememberLazyListState()
        val steps = run.steps
        LaunchedEffect(steps.size, run.question, run.approval, run.finished) {
            val target = listState.layoutInfo.totalItemsCount - 1
            if (target > 0 && (run.running || run.finished)) listState.animateScrollToItem(target)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!ui.serviceEnabled) {
                item(key = "setup") {
                    SetupCard(onEnable = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    })
                }
            }

            if (!run.running && !run.finished) {
                item(key = "composer") {
                    ComposerCard(
                        task = ui.task,
                        onTask = viewModel::setTask,
                        modelName = ui.model?.name ?: "Choose a model",
                        onPickModel = { showModels = true },
                        enabled = ui.serviceEnabled && ui.model != null,
                        onStart = { viewModel.start() },
                        history = ui.history,
                        onSuggestion = { viewModel.setTask(it) },
                        error = run.error
                    )
                }
            } else {
                item(key = "live") {
                    LiveHeader(run = run, onPause = viewModel::togglePause, onStop = viewModel::stop)
                }
                items(steps, key = { it.id }) { step ->
                    StepCard(step = step, modifier = Modifier.animateItem())
                }
                if (run.question != null) {
                    item(key = "question") { QuestionCard(run.question!!, onAnswer = viewModel::answer) }
                }
                if (run.approval != null) {
                    item(key = "approval") { ApprovalCard(run.approval!!, onAllow = { viewModel.approve(true) }, onDeny = { viewModel.approve(false) }) }
                }
                if (run.running && run.question == null && run.approval == null) {
                    item(key = "working") { WorkingRow(run) }
                }
                if (run.finished) {
                    item(key = "result") {
                        ResultCard(
                            run = run,
                            onAgain = { viewModel.start(run.task) },
                            onNew = viewModel::newTask
                        )
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsSheet(ui.settings, onChange = viewModel::updateSettings)
        }
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
private fun SetupCard(onEnable: () -> Unit) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Violet.copy(alpha = 0.18f), Magenta.copy(alpha = 0.16f))))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OrobiIdle(modifier = Modifier.width(96.dp), showShadow = false)
            Text("Let Orobi use your phone", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Magenta)
            ) {
                Text("Turn on", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// --------------------------------------------------------------- composer

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerCard(
    task: String,
    onTask: (String) -> Unit,
    modelName: String,
    onPickModel: () -> Unit,
    enabled: Boolean,
    onStart: () -> Unit,
    history: List<String>,
    onSuggestion: (String) -> Unit,
    error: String?,
) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = task,
                onValueChange = onTask,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What should Orobi do?") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(18.dp)
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (history.take(3) + Suggestions).distinct().forEach { s ->
                    SuggestionPill(s, recent = s in history) { onSuggestion(s) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onPickModel,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp), tint = Violet)
                        Spacer(Modifier.width(8.dp))
                        Text(modelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                val ready = enabled && task.isNotBlank()
                val scale by animateFloatAsState(if (ready) 1f else 0.94f, spring(dampingRatio = 0.5f), label = "go")
                Button(
                    onClick = onStart,
                    enabled = ready,
                    modifier = Modifier.height(48.dp).scale(scale),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Magenta)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start", fontWeight = FontWeight.SemiBold)
                }
            }
            AnimatedVisibility(visible = error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SuggestionPill(text: String, recent: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (recent) Violet.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = null
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (recent) Icons.Rounded.HourglassTop else Icons.Rounded.Lightbulb, null, Modifier.size(14.dp), tint = if (recent) Violet else Amber)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

// --------------------------------------------------------------- live run

@Composable
private fun LiveHeader(run: DeviceAgent.State, onPause: () -> Unit, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run.running) { while (run.running) { now = System.currentTimeMillis(); delay(1_000) } }
    val end = if (run.running) now else run.endedAt
    val elapsed = ((end - run.startedAt) / 1000).coerceAtLeast(0)
    val progress by animateFloatAsState(
        if (run.maxActions > 0) run.actions / run.maxActions.toFloat() else 0f, tween(500, easing = FastOutSlowInEasing), label = "p"
    )
    val accent by animateColorAsState(
        when { run.paused -> Amber; run.running -> Magenta; run.success == true -> Emerald; else -> Rose }, label = "accent"
    )

    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.20f), Violet.copy(alpha = 0.10f))))
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(accent, active = run.running && !run.paused)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when { run.paused -> "Paused"; run.running -> "Live"; run.success == true -> "Completed"; else -> "Stopped" },
                        style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    MetaChip(Icons.Rounded.Timer, formatElapsed(elapsed))
                    Spacer(Modifier.width(6.dp))
                    MetaChip(Icons.Rounded.TouchApp, "${run.actions}")
                }
                Text(run.task, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.15f),
                    drawStopIndicator = {}
                )
                if (run.notes.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        run.notes.forEach { MetaChip(Icons.AutoMirrored.Rounded.StickyNote2, it.take(40)) }
                    }
                }
                AnimatedVisibility(visible = run.running, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(if (run.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (run.paused) "Resume" else "Pause")
                        }
                        Button(
                            onClick = onStop, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Rose)
                        ) {
                            Icon(Icons.Rounded.Stop, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Stop")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseDot(color: Color, active: Boolean) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(1f, if (active) 1.9f else 1f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "s")
    val a by t.animateFloat(0.5f, if (active) 0f else 0.5f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "a")
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).scale(s).clip(CircleShape).background(color.copy(alpha = a)))
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun MetaChip(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun formatElapsed(s: Long) = if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"

@Composable
private fun StepCard(step: DeviceAgent.Step, modifier: Modifier = Modifier) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    AnimatedVisibility(
        visible = appeared,
        enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { it / 3 } + fadeIn(tween(220)) + scaleIn(initialScale = 0.96f),
        modifier = modifier
    ) {
        if (step.kind == StepKind.THOUGHT) {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 24.dp), verticalAlignment = Alignment.Top) {
                Text("💭", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    step.title, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis
                )
            }
            return@AnimatedVisibility
        }
        val (icon, tint) = iconFor(step)
        val statusColor by animateColorAsState(
            when (step.status) {
                StepStatus.RUNNING -> Magenta; StepStatus.DONE -> Emerald; StepStatus.FAILED -> Rose
                StepStatus.BLOCKED -> Amber; StepStatus.WAITING -> Violet
            }, label = "sc"
        )
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth().border(
                1.dp, if (step.status == StepStatus.RUNNING || step.status == StepStatus.WAITING) statusColor.copy(alpha = 0.45f) else Color.Transparent,
                RoundedCornerShape(20.dp)
            )
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.dp), tint = tint)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    // Only what the title can't say: why it failed, or the user's answer.
                    val showDetail = step.detail.isNotBlank() &&
                        (step.status == StepStatus.FAILED || step.kind == StepKind.QUESTION)
                    if (showDetail) {
                        Text(step.detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                AnimatedContent(targetState = step.status, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "st") { st ->
                    when (st) {
                        StepStatus.RUNNING -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp, color = Magenta)
                        StepStatus.WAITING -> Icon(Icons.Rounded.HourglassTop, "Waiting", Modifier.size(20.dp), tint = Violet)
                        StepStatus.DONE -> if (step.screenChanged == false) StatusBadge(Icons.Rounded.Warning, Amber) else StatusBadge(Icons.Rounded.Check, Emerald)
                        StepStatus.FAILED -> StatusBadge(Icons.Rounded.Close, Rose)
                        StepStatus.BLOCKED -> StatusBadge(Icons.Rounded.Shield, Amber)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(icon: ImageVector, color: Color) {
    Box(Modifier.size(22.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(14.dp), tint = Color.White)
    }
}

private fun iconFor(step: DeviceAgent.Step): Pair<ImageVector, Color> = when (step.kind) {
    StepKind.QUESTION -> Icons.AutoMirrored.Rounded.HelpOutline to Violet
    StepKind.APPROVAL -> Icons.Rounded.Shield to Amber
    StepKind.NOTE -> Icons.AutoMirrored.Rounded.StickyNote2 to Amber
    else -> when (step.tool) {
        "tap", "double_tap" -> Icons.Rounded.TouchApp to Magenta
        "long_press" -> Icons.Rounded.TouchApp to Violet
        "type_text" -> Icons.Rounded.Keyboard to Violet
        "scroll", "swipe" -> Icons.Rounded.SwapVert to Color(0xFF0EA5E9)
        "drag", "pinch" -> Icons.Rounded.Gesture to Color(0xFF0EA5E9)
        "open_app", "list_apps" -> Icons.Rounded.Apps to Emerald
        "open_url" -> Icons.Rounded.Language to Emerald
        "press_key" -> Icons.AutoMirrored.Rounded.KeyboardReturn to Color(0xFF64748B)
        "find_text", "read_screen" -> Icons.Rounded.Search to Color(0xFF64748B)
        "wait" -> Icons.Rounded.Timer to Color(0xFF64748B)
        else -> Icons.Rounded.Bolt to Magenta
    }
}

@Composable
private fun WorkingRow(run: DeviceAgent.State) {
    AnimatedVisibility(visible = !run.paused, enter = fadeIn(), exit = fadeOut()) {
        OrobiWorking(label = run.status.ifBlank { "Working" }, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionCard(q: DeviceAgent.Question, onAnswer: (String) -> Unit) {
    var text by rememberSaveable(q.text) { mutableStateOf("") }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Violet.copy(alpha = 0.10f)),
        modifier = Modifier.border(1.dp, Violet.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = Violet)
                Spacer(Modifier.width(10.dp))
                Text(q.text, style = MaterialTheme.typography.titleMedium)
            }
            if (q.options.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    q.options.forEach { opt ->
                        OutlinedButton(onClick = { onAnswer(opt) }, shape = RoundedCornerShape(14.dp)) { Text(opt) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
                    singleLine = true, shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { if (text.isNotBlank()) onAnswer(text.trim()) }, enabled = text.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Send answer", tint = if (text.isNotBlank()) Violet else MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(what: String, onAllow: () -> Unit, onDeny: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Amber.copy(alpha = 0.10f)),
        modifier = Modifier.border(1.dp, Amber.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Shield, null, tint = Amber)
                Spacer(Modifier.width(10.dp))
                Text("Allow Orobi to $what?", style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp)) { Text("Deny") }
                Button(onClick = onAllow, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Emerald)) {
                    Text("Allow")
                }
            }
        }
    }
}

@Composable
private fun ResultCard(run: DeviceAgent.State, onAgain: () -> Unit, onNew: () -> Unit) {
    val ok = run.success == true && run.error == null
    val color = if (ok) Emerald else Rose
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val iconScale by animateFloatAsState(if (shown) 1f else 0.3f, spring(dampingRatio = 0.45f, stiffness = 300f), label = "pop")
    val spin by animateFloatAsState(if (shown) 0f else -90f, spring(dampingRatio = 0.6f), label = "spin")
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), MaterialTheme.colorScheme.surfaceContainerLow)))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(64.dp).scale(iconScale).rotate(spin).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Icon(if (ok) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(34.dp), tint = Color.White)
                }
                Text(if (ok) "Done" else "Didn't finish", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    run.summary ?: run.error ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onAgain, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp)) { Text("Run again") }
                    Button(onClick = onNew, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Magenta)) {
                        Text("New task")
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------- settings

@Suppress("DEPRECATION")
@Composable
private fun SettingsSheet(s: DeviceAgent.Settings, onChange: ((DeviceAgent.Settings) -> DeviceAgent.Settings) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToggleRow("Ask before sensitive actions", s.confirmSensitive) { v -> onChange { it.copy(confirmSensitive = v) } }
        ToggleRow("Show touches", s.showTouches) { v -> onChange { it.copy(showTouches = v) } }
        ToggleRow("Return to Oro when done", s.returnToOro) { v -> onChange { it.copy(returnToOro = v) } }
        Spacer(Modifier.height(8.dp))
        Text("Action limit · ${s.maxActions}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = s.maxActions.toFloat(), onValueChange = { v -> onChange { it.copy(maxActions = v.toInt()) } },
            valueRange = 15f..150f, steps = 26
        )
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChecked(!checked) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
