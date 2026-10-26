package com.oro.app.ui.screens.browser

import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oro.app.browser.BrowserAgent
import com.oro.app.browser.BrowserEngine
import com.oro.app.data.ai.tools.QuestionOption
import com.oro.app.data.ai.tools.UserQuestion
import com.oro.app.ui.brand.OroBrand
import com.oro.app.ui.brand.OrobiLoader
import com.oro.app.ui.components.agent.ApprovalSheet
import com.oro.app.ui.components.agent.AskUserCard
import com.oro.app.ui.components.agent.PendingApproval
import com.oro.app.ui.components.agent.PendingQuestion
import com.oro.app.ui.components.chat.ModelSelectorSheet
import com.oro.app.ui.components.chat.ModernChatInput
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/**
 * Orobi's browser: a real in-app browser the agent drives, and the live view
 * of everything it does there.
 *
 * The WebView shown here is the very one the run acts on (the engine keeps it
 * measured and running off-screen when this screen is closed), so "watch it
 * live" and "minimise it and carry on" are the same browser at all times.
 * While a task runs: the status bar with Pause/Stop, the transcript sheet, and
 * -- when the run needs the user -- the cards that matter: a question, an
 * approval, a secret to type (into the card or straight into the page), and
 * the banner for a human check the user must pass.
 */
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    viewModel: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory)
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var showTranscript by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // A page opened a file chooser with no agent upload pending: let the user pick.
    val chooserTick by BrowserEngine.chooserTick.collectAsStateWithLifecycle()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        BrowserEngine.answerFileChooser(uri?.let { arrayOf(it) })
    }
    LaunchedEffect(chooserTick) {
        if (chooserTick > 0) runCatching { filePicker.launch(arrayOf("*/*")) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (run.running) run.status.ifBlank { "Browser" }.take(40) else "Browser",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (run.running || run.steps.isNotEmpty()) {
                        IconButton(onClick = { showTranscript = true }) {
                            Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = "Transcript")
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Downloads (${BrowserEngine.downloadsFlow.value.size})", maxLines = 1) },
                                leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                                onClick = { showMenu = false; showTranscript = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Share this page", maxLines = 1) },
                                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                onClick = { showMenu = false; BrowserEngine.shareActive() }
                            )
                            DropdownMenuItem(
                                text = { Text("Browser settings", maxLines = 1) },
                                leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                                onClick = { showMenu = false; showSettings = true }
                            )
                        }
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
            UrlBar(engine = engine, onNavigate = viewModel::openUrl)

            AnimatedVisibility(visible = engine.progress in 1..99 && engine.tabs.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { engine.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = OroBrand.Magenta,
                )
            }

            // ------------------------------------------------ the live page
            var webContainer by remember { mutableStateOf<FrameLayout?>(null) }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { context ->
                        FrameLayout(context).also {
                            webContainer = it
                            BrowserEngine.attach(it)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                DisposableEffect(Unit) {
                    onDispose {
                        webContainer?.let { BrowserEngine.detach(it) }
                    }
                }

                // The human check banner: what the run is waiting on, said loudly.
                val askingCheck = run.running && run.question != null &&
                    run.question?.text?.startsWith("Pass the") == true
                if (askingCheck) {
                    Surface(
                        color = OroBrand.Magenta,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(Space.md)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = run.question?.text ?: "Pass the check on the page",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // First-visit welcome over an empty tab.
                val started = run.startedAt > 0L
                if (!started && (engine.url.isBlank() || engine.url == "about:blank")) {
                    BrowserWelcome(
                        history = ui.history,
                        onPick = viewModel::setTask,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // -------------------------------------------------- the run's voice
            if (run.running) {
                RunBar(
                    run = run,
                    onPause = viewModel::togglePause,
                    onStop = viewModel::stop,
                    onTranscript = { showTranscript = true },
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)
                )
            }

            if (run.finished) {
                SummaryCard(
                    run = run,
                    onNew = viewModel::newTask,
                    onAgain = { viewModel.start(run.task) },
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)
                )
            }

            // A question the run is waiting on (not the human-check banner above).
            run.question?.takeIf { run.running && it.text.startsWith("Pass the").not() }?.let { q ->
                AskUserCard(
                    question = PendingQuestion(
                        id = "browser-q-${run.startedAt}-${run.steps.size}",
                        questions = listOf(
                            UserQuestion(
                                question = q.text,
                                options = q.options.map { QuestionOption(it) },
                            )
                        )
                    ),
                    onSubmit = { answers -> viewModel.answer(answers.filterNotNull().joinToString(", ").ifBlank { "You decide" }) },
                    onDismiss = { viewModel.answer("You decide") },
                    dismissLabel = "Skip",
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)
                )
            }

            // A secret to type: into the card, or straight into the live page.
            run.secretRequest?.takeIf { run.running }?.let { secret ->
                SecretCard(
                    purpose = secret.purpose,
                    hasField = secret.fieldIndex != null,
                    onSubmit = viewModel::provideSecret,
                    onTypedInPage = viewModel::secretEnteredDirectly,
                    onDecline = viewModel::declineSecret,
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)
                )
            }

            // HTTP basic auth: answered here, never through the model.
            engine.httpAuthPrompt?.let { host ->
                HttpAuthCard(
                    host = host,
                    onAnswer = { user, pass -> BrowserEngine.answerHttpAuth(true, user, pass) },
                    onDecline = { BrowserEngine.answerHttpAuth(false, null, null) },
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)
                )
            }

            // ------------------------------------------------------- composer
            val composerModifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.lg, end = Space.lg, bottom = Space.lg, top = Space.xs)
            val model = ui.model
            if (model != null) {
                ModernChatInput(
                    value = ui.task,
                    onValueChange = viewModel::setTask,
                    onSend = viewModel::startFromComposer,
                    onStop = viewModel::stop,
                    isEnabled = !run.running,
                    isProcessing = run.running,
                    currentModel = model,
                    onModelClick = { showModels = true },
                    onSettingsClick = { showSettings = true },
                    placeholder = "What should Orobi do in the browser?",
                    settingsDescription = "Browser settings",
                    mentions = false,
                    modifier = composerModifier
                )
            }
        }
    }

    // The same modal gate the editor uses: it cannot be missed, and leaving it
    // without a choice counts as Deny.
    ApprovalSheet(
        approval = run.approval?.takeIf { run.running }?.let { PendingApproval(action = "Allow Orobi to $it?") },
        onDecision = viewModel::approve
    )

    if (showTranscript) {
        TranscriptSheet(run = run, onDismiss = { showTranscript = false })
    }
    if (showSettings) {
        BrowserSettingsSheet(
            settings = ui.settings,
            onChange = viewModel::updateSettings,
            credentials = ui.credentials,
            onSaveCredential = viewModel::saveCredential,
            onDeleteCredential = viewModel::deleteCredential,
            onDismiss = { showSettings = false },
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

// ------------------------------------------------------------------ url bar

@Composable
private fun UrlBar(engine: BrowserEngine.Ui, onNavigate: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { BrowserEngine.backUi() }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { BrowserEngine.forwardUi() }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Forward", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { BrowserEngine.reloadActive() }) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Reload", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
                placeholder = { Text("Search or type a URL", maxLines = 1) },
            )
            IconButton(onClick = { onNavigate(draft); editing = false }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Go")
            }
        } else {
            Surface(
                onClick = { draft = engine.url.removePrefix("https://").removePrefix("http://"); editing = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = engine.url.removePrefix("https://").removePrefix("http://").ifBlank { "Search or type a URL" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (engine.url.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm + Space.xxs)
                )
            }
        }
        if (engine.tabs.size > 1) {
            var tabsOpen by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { tabsOpen = true }) {
                    Text("${engine.tabs.size}", maxLines = 1)
                }
                DropdownMenu(expanded = tabsOpen, onDismissRequest = { tabsOpen = false }) {
                    engine.tabs.forEach { tab ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(tab.title.ifBlank { tab.url }.take(40), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(tab.url.take(60), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            trailingIcon = {
                                IconButton(onClick = { BrowserEngine.closeTabUi(tab.id) }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Close tab", modifier = Modifier.size(Sizes.iconSmall))
                                }
                            },
                            onClick = { BrowserEngine.switchTabUi(tab.id); tabsOpen = false }
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ welcome

@Composable
private fun BrowserWelcome(history: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(Space.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Orobi's browser",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = "Type a task below, or a URL above. Orobi browses, fills forms, signs in with your saved logins and scrapes what you need -- you can watch or minimise it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 360.dp)
            )
            Spacer(Modifier.height(Space.lg))
            val starters = (history.take(2) + Starters).distinct().take(4)
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                starters.forEach { text ->
                    Surface(
                        onClick = { onPick(text) },
                        shape = OroCorners.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md + Space.xxs)
                        )
                    }
                }
            }
        }
    }
}

private val Starters = listOf(
    "Find today's top story on Hacker News and summarise it",
    "Look up the docs for CSS container queries and explain them with an example",
    "Test my site's sign-up form like a first-time visitor",
    "Find the cheapest 2 TB NVMe SSD on Amazon India and report the price",
)

// ------------------------------------------------------------------ run bar

@Composable
private fun RunBar(run: BrowserAgent.State, onPause: () -> Unit, onStop: () -> Unit, onTranscript: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (run.needsUser) {
                OrobiLoader(modifier = Modifier.width(20.dp), color = OroBrand.Magenta, contentDescription = null)
            } else if (run.paused) {
                Icon(Icons.Outlined.Pause, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
            } else {
                OrobiLoader(modifier = Modifier.width(20.dp), contentDescription = null)
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = run.status.ifBlank { "Working" },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(run.actions).append(" action").append(if (run.actions == 1) "" else "s")
                        if (run.maxActions > 0) append(" / ").append(run.maxActions)
                        run.currentUrl.takeIf { it.isNotBlank() }?.let { append(" · ").append(it.removePrefix("https://").removePrefix("http://").take(40)) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onTranscript) {
                Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = "Transcript", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPause) {
                Icon(
                    imageVector = if (run.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = if (run.paused) "Resume" else "Pause"
                )
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Outlined.Close, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// --------------------------------------------------------------- summary

@Composable
private fun SummaryCard(run: BrowserAgent.State, onNew: () -> Unit, onAgain: () -> Unit, modifier: Modifier = Modifier) {
    val done = run.endReason == BrowserAgent.EndReason.DONE
    Surface(
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Sizes.hairline, if (done) OroBrand.Magenta.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (done) Icons.Outlined.TaskAlt else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = if (done) OroBrand.Magenta else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = when (run.endReason) {
                        BrowserAgent.EndReason.DONE -> "Done"
                        BrowserAgent.EndReason.STOPPED_BY_USER -> "Stopped by you"
                        BrowserAgent.EndReason.ACTION_LIMIT -> "Action limit reached"
                        BrowserAgent.EndReason.UNCONFIRMED -> "Ended without confirming"
                        else -> "Didn't finish"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            (run.summary ?: run.error)?.let { text ->
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilledTonalButton(onClick = onNew) { Text("New task", maxLines = 1) }
                TextButton(onClick = onAgain) { Text("Run again", maxLines = 1) }
            }
        }
    }
}

// ------------------------------------------------------------ secret card

@Composable
private fun SecretCard(
    purpose: String,
    hasField: Boolean,
    onSubmit: (String) -> Unit,
    onTypedInPage: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    var secret by remember { mutableStateOf("") }
    Surface(
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(Sizes.hairline, OroBrand.Magenta.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Space.lg)) {
            Text("Orobi needs a secret from you", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.xs))
            Text(
                text = purpose + " — it goes straight into the page, never to the model.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Space.md))
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("Secret", maxLines = 1) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilledTonalButton(onClick = { onSubmit(secret) }, enabled = secret.isNotEmpty()) {
                    Text(if (hasField) "Enter it" else "Save it", maxLines = 1)
                }
                if (!hasField) {
                    TextButton(onClick = onTypedInPage) { Text("I typed it in the page", maxLines = 1) }
                }
                TextButton(onClick = onDecline) { Text("Cancel", maxLines = 1) }
            }
        }
    }
}

// ---------------------------------------------------------- http auth card

@Composable
private fun HttpAuthCard(host: String, onAnswer: (String, String) -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Surface(
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Space.lg)) {
            Text("“$host” asks you to sign in", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.md))
            OutlinedTextField(value = user, onValueChange = { user = it }, singleLine = true, label = { Text("Username", maxLines = 1) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.sm))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("Password", maxLines = 1) }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilledTonalButton(onClick = { onAnswer(user, pass) }, enabled = user.isNotBlank()) { Text("Sign in", maxLines = 1) }
                TextButton(onClick = onDecline) { Text("Cancel", maxLines = 1) }
            }
        }
    }
}

// ------------------------------------------------------------- transcript

@Composable
private fun TranscriptSheet(run: BrowserAgent.State, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listed = remember(run.steps, run.question, run.approval, run.secretRequest) { BrowserTranscript.listedSteps(run) }
    val segments = remember(listed) { BrowserTranscript.segments(listed) }
    val downloads = BrowserEngine.downloadsFlow.collectAsStateWithLifecycle().value
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = Space.xl)) {
            Text(
                text = "Transcript",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.sm)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(420.dp),
                contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                item(key = "task") {
                    Text(
                        text = run.task,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Space.sm)
                    )
                }
                items(items = segments, key = { it.key }) { segment ->
                    when (segment) {
                        is BrowserSegment.Thought -> Text(
                            text = segment.step.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = Space.xs)
                        )
                        is BrowserSegment.Actions -> Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            segment.steps.forEach { step ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = BrowserTranscript.iconFor(step),
                                        contentDescription = null,
                                        tint = when (step.status) {
                                            BrowserAgent.StepStatus.FAILED, BrowserAgent.StepStatus.BLOCKED -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(Sizes.iconSmall)
                                    )
                                    Spacer(Modifier.width(Space.md))
                                    Column(Modifier.weight(1f)) {
                                        val (verb, subject) = BrowserTranscript.splitTitle(step)
                                        Text(
                                            text = if (subject != null) "$verb $subject" else verb,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        step.detail.takeIf { it.isNotBlank() }?.let { d ->
                                            Text(
                                                text = d,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (step.status == BrowserAgent.StepStatus.RUNNING) {
                                        OrobiLoader(modifier = Modifier.width(16.dp), contentDescription = null)
                                    }
                                }
                            }
                        }
                    }
                }
                if (downloads.isNotEmpty()) {
                    item(key = "downloads") {
                        Column(Modifier.padding(top = Space.md)) {
                            HorizontalDivider(Modifier.padding(bottom = Space.sm))
                            Text("Downloads", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            downloads.forEach { d ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.xxs)) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
                                    Spacer(Modifier.width(Space.md))
                                    Text("${d.name} · ${d.bytes / 1024} KB", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
