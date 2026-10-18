package com.oro.app.ui.screens.editor.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.oro.app.ui.brand.OroBrand
import com.oro.app.ui.brand.OroSparkle
import com.oro.app.ui.brand.OrobiIdle
import com.oro.app.ui.brand.OrobiWorking
import com.oro.app.ui.theme.Space
import com.oro.app.ui.components.chat.ModernChatInput
import com.oro.app.ui.components.chat.ContextSheet
import com.oro.app.ui.components.chat.ChatSettingsSheet
import com.oro.app.data.ai.context.ContextUsageLevel
import com.oro.app.data.ai.context.ContextBreakdown
import com.oro.app.ui.theme.OroCorners
import com.oro.app.data.ai.ReasoningEffort
import com.oro.app.ui.components.agent.AgentActivityCard
import com.oro.app.ui.components.agent.AskUserCard
import com.oro.app.ui.components.agent.PendingQuestion
import com.oro.app.ui.components.agent.RunError
import com.oro.app.ui.components.agent.RunErrorCard
import com.oro.app.data.ai.agents.AgentSession
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oro.app.R
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.tools.TodoItem
import com.oro.app.domain.model.ChatMessage
import com.oro.app.ui.components.DiffView
import com.oro.app.ui.components.TaskPlanningPanel
import com.oro.app.ui.components.chat.FileMentionPopup
import com.oro.app.ui.components.chat.ModelSelectorSheet
import com.oro.app.ui.components.chat.MentionItem
import com.oro.app.ui.components.chat.extractMentionQuery
import com.oro.app.ui.components.chat.replaceMentionInText
import com.oro.app.ui.components.message.AiMessageContent
import com.oro.app.domain.model.FileTreeNode
import com.oro.app.ui.components.toUiCodeChange
import com.oro.app.ui.theme.OroTheme
import com.oro.app.ui.screens.editor.sessions.ChatSessionActions
import com.oro.app.ui.screens.editor.sessions.ChatSessionsUiState
import com.oro.app.ui.screens.editor.sessions.SessionUndoBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTab(
    messages: List<ChatMessage>,
    sessionsState: ChatSessionsUiState = ChatSessionsUiState(isLoadingSession = false),
    sessionActions: ChatSessionActions = ChatSessionActions.None,
    agentPlan: com.oro.app.data.ai.agents.AgentPlan? = null,
    sessionUsage: com.oro.app.data.ai.usage.SessionUsage = com.oro.app.data.ai.usage.SessionUsage(),
    /** User messages whose run left a checkpoint, i.e. can be rewound. */
    checkpointMessageIds: Set<String> = emptySet(),
    onRestoreCheckpoint: (String) -> Unit = {},
    autonomy: com.oro.app.data.ai.agent.AutonomyMode = com.oro.app.data.ai.agent.AutonomyMode.DEFAULT,
    onAutonomyChange: (com.oro.app.data.ai.agent.AutonomyMode) -> Unit = {},
    richUi: Boolean = true,
    onRichUiChange: (Boolean) -> Unit = {},
    attachedPhotos: Int = 0,
    onAttachPhoto: (android.net.Uri) -> Unit = {},
    onClearPhotos: () -> Unit = {},
    projectName: String? = null,
    inputText: String,
    isLoading: Boolean,
    agentMode: Boolean,
    taskList: List<TodoItem> = emptyList(),
    currentModel: AiModel? = null,
    availableModels: List<AiModel> = emptyList(),
    fileTree: List<FileTreeNode> = emptyList(),
    agentSessions: List<AgentSession> = emptyList(),
    reasoningEffort: ReasoningEffort = ReasoningEffort.DEFAULT,
    onReasoningEffortChange: (ReasoningEffort) -> Unit = {},
    contextBreakdown: ContextBreakdown = ContextBreakdown.Empty,
    compactableMessageCount: Int = 0,
    isCompacting: Boolean = false,
    lastCompactionSavedPercent: Int? = null,
    onCompactContext: () -> Unit = {},
    onStopAgent: (String) -> Unit = {},
    onResolveAgentApproval: (String, Boolean) -> Unit = { _, _ -> },
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: (() -> Unit)? = null,
    onToggleAgentMode: () -> Unit,
    onModelChange: (AiModel) -> Unit = {},
    onRefreshModels: () -> Unit = {},
    onNavigateToModels: () -> Unit = {},
    isRefreshingModels: Boolean = false,
    /** What the running turn is doing; drives the working indicator's label. */
    workingLabel: String? = null,
    pendingQuestion: PendingQuestion? = null,
    onAnswerQuestion: (List<String?>) -> Unit = {},
    onSkipQuestion: () -> Unit = {},
    runError: RunError? = null,
    onRetryRun: () -> Unit = {},
    onDismissRunError: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showModelSelector by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    var showContextSheet by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<ChatMessage?>(null) }

    restoreTarget?.let { target ->
        com.oro.app.ui.components.expressive.FormSheet(
            title = "Restore files?",
            icon = androidx.compose.material.icons.Icons.Outlined.History,
            onDismiss = { restoreTarget = null },
            confirmLabel = "Restore",
            destructive = true,
            onConfirm = {
                onRestoreCheckpoint(target.id)
                restoreTarget = null
            }
        ) {
            Text(
                "Every file Orobi created, changed or deleted from this message on goes back to how it was " +
                    "before it. The conversation itself is kept.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    if (showChatSettings) {
        ChatSettingsSheet(
            agentMode = agentMode,
            onAgentModeChange = { onToggleAgentMode() },
            reasoningEffort = reasoningEffort,
            onReasoningEffortChange = onReasoningEffortChange,
            currentModel = currentModel,
            onDismiss = { showChatSettings = false },
            autonomy = autonomy,
            onAutonomyChange = onAutonomyChange,
            richUi = richUi,
            onRichUiChange = onRichUiChange
        )
    }

    if (showContextSheet) {
        ContextSheet(
            breakdown = contextBreakdown,
            compactableMessageCount = compactableMessageCount,
            isCompacting = isCompacting,
            lastSavedPercent = lastCompactionSavedPercent,
            onCompact = onCompactContext,
            onDismiss = { showContextSheet = false },
            usage = sessionUsage
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Trailing items after messages and agent cards: the working indicator /
    // question while a run is live, or the error card after one failed.
    val trailingItems = (if (isLoading) 1 else 0) + (if (!isLoading && runError != null) 1 else 0)
    // The session whose transcript the list was last scrolled for. Opening
    // another session jumps straight to its end; animating would scroll
    // visibly through a conversation the user did not ask to read.
    var scrolledSession by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.size, agentSessions.size, pendingQuestion?.id, runError, isLoading) {
        val lastIndex = messages.size + agentSessions.size + trailingItems - 1
        if (lastIndex >= 0) {
            val session = sessionsState.currentSessionId
            if (messages.isNotEmpty() && session != scrolledSession) {
                scrolledSession = session
                listState.scrollToItem(lastIndex)
            } else {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {

        if (messages.isEmpty() && sessionsState.isLoadingSession) {
            // Between choosing a session and its rows arriving (a few ms):
            // blank, rather than flashing the welcome for a chat that has
            // history.
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else if (messages.isEmpty()) {
            WelcomeMessage(
                agentMode = agentMode,
                onToggleAgentMode = onToggleAgentMode,
                onSuggestion = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Compared by id: `message == last` was a full content compare
                // of every bubble on every streamed frame.
                val lastId = messages.lastOrNull()?.id
                items(
                    items = messages,
                    key = { it.id },
                    contentType = { if (it.isUser) "user" else "ai" }
                ) { message ->
                    ChatBubble(
                        message = message,
                        canRestore = !isLoading && message.isUser && message.id in checkpointMessageIds,
                        onRestore = { restoreTarget = message },
                        isStreaming = isLoading && message.id == lastId && !message.isUser,
                        modifier = Modifier.animateItem()
                    )
                }

                // Delegated work is appended after the transcript rather than
                // interleaved by timestamp. A subagent outlives the turn that
                // started it, so a card placed inline would scroll away while
                // still running -- exactly when the user most wants to see it.
                if (agentSessions.isNotEmpty()) {
                    items(
                        items = agentSessions,
                        key = { it.id },
                        contentType = { "agent" }
                    ) { session ->
                        AgentActivityCard(
                            session = session,
                            onStop = { onStopAgent(session.id) },
                            onApprove = { approved ->
                                onResolveAgentApproval(session.id, approved)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                // While a turn is live there is always exactly one visible
                // working state at the end of the list: the question the agent
                // is waiting on, or Orobi's indicator with what it is doing.
                // It used to show only until the (empty) assistant message was
                // added, then vanish for the rest of the run.
                if (isLoading) {
                    item(key = "run-status") {
                        if (pendingQuestion != null) {
                            AskUserCard(
                                question = pendingQuestion,
                                onSubmit = onAnswerQuestion,
                                onDismiss = onSkipQuestion,
                                modifier = Modifier.animateItem()
                            )
                        } else {
                            TypingIndicator(label = workingLabel ?: "Thinking")
                        }
                    }
                } else if (runError != null) {
                    item(key = "run-error") {
                        RunErrorCard(
                            error = runError,
                            onRetry = onRetryRun,
                            onDismiss = onDismissRunError,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        SessionUndoBar(
            title = sessionsState.undoTitle,
            onUndo = sessionActions.onUndoDelete,
            onDismiss = sessionActions.onDismissUndo,
            modifier = Modifier.padding(start = Space.lg, end = Space.lg, top = Space.sm)
        )

        // The plan docks on the composer, like the run it describes: it is
        // about what Orobi is doing *now*, so it sits where the user is
        // looking, not at the top of a transcript that scrolls away.
        AnimatedVisibility(
            visible = agentPlan != null && !agentPlan.isEmpty,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            agentPlan?.let {
                com.oro.app.ui.components.agent.PlanTimeline(
                    plan = it,
                    modifier = Modifier.padding(start = Space.lg, end = Space.lg, top = Space.sm)
                )
            }
        }
        AnimatedVisibility(
            visible = taskList.isNotEmpty(),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            TaskPlanningPanel(
                tasks = taskList,
                outerPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.sm)
            )
        }

        val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri -> if (uri != null) onAttachPhoto(uri) }
        ModernChatInput(
            onAttachPhoto = {
                photoPicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            attachedPhotos = attachedPhotos,
            onClearPhotos = onClearPhotos,
            value = inputText,
            onValueChange = onInputChange,
            onSend = onSendMessage,
            onStop = onStopGeneration,
            isEnabled = !isLoading,
            isProcessing = isLoading,
            agentMode = agentMode,
            currentModel = currentModel ?: return@Column,
            onModelClick = { showModelSelector = true },
            onSettingsClick = { showChatSettings = true },
            onContextClick = { showContextSheet = true },
            contextPercent = contextBreakdown.percent,
            contextIsCritical = contextBreakdown.level == ContextUsageLevel.CRITICAL,
            fileTree = fileTree,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Space.lg, end = Space.lg, bottom = Space.lg,
                    top = if (taskList.isNotEmpty() || (agentPlan != null && !agentPlan.isEmpty)) Space.xs + Space.xxs else Space.lg
                )
        )
    }

    // Model selector bottom sheet
    if (showModelSelector) {
        ModelSelectorSheet(
            models = availableModels,
            selectedModel = currentModel,
            onModelSelected = { model ->
                onModelChange(model)
                scope.launch { sheetState.hide() }
                showModelSelector = false
            },
            onDismiss = { showModelSelector = false },
            onManageModels = {
                showModelSelector = false
                onNavigateToModels()
            },
            onRefresh = onRefreshModels,
            isRefreshing = isRefreshingModels,
            sheetState = sheetState
        )
    }
}

@Composable
private fun WelcomeMessage(
    agentMode: Boolean,
    onToggleAgentMode: () -> Unit,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Centred when it fits, scrollable when it does not (keyboard up, small
    // phone): a Box around a scrolling column does both.
    Box(modifier, contentAlignment = Alignment.Center) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OrobiIdle(modifier = Modifier.width(150.dp))

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = context.getString(R.string.chat_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = context.getString(R.string.chat_welcome_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Chat / Agent lives in the composer's settings sheet; a second
        // switch here only duplicated it.
        Spacer(modifier = Modifier.height(28.dp))

        // Starters. They fill the input rather than sending, so the user can
        // adjust the wording first -- a sent prompt they did not quite mean
        // costs a whole agent run to undo.
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StarterPrompts.forEach { prompt ->
                StarterPrompt(text = prompt, onClick = { onSuggestion(prompt) })
            }
        }
    }
    }
}

private val StarterPrompts = listOf(
    "Build a landing page for a coffee shop",
    "Add a responsive navbar with a mobile menu",
    "Review this project and suggest improvements",
    "Audit my site and fix what it finds",
    "Check my site on phone, tablet and desktop"
)

@Composable
private fun StarterPrompt(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OroSparkle(
                modifier = Modifier.size(12.dp),
                color = OroBrand.Magenta
            )
            Spacer(Modifier.width(12.dp))
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

@Composable
private fun AgentModeToggle(
    agentMode: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .clip(OroCorners.xLarge)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = OroCorners.xLarge
            )
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Chat mode button
        Box(
            modifier = Modifier
                .clip(OroCorners.xLarge)
                .background(
                    if (!agentMode) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
                .clickable { if (agentMode) onToggle() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (!agentMode) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Chat",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (!agentMode) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Agent mode button
        Box(
            modifier = Modifier
                .clip(OroCorners.xLarge)
                .background(
                    if (agentMode) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
                .clickable { if (!agentMode) onToggle() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    // Agent mode is tool use; a wrench says so.
                    imageVector = Icons.Outlined.Handyman,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (agentMode) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Agent",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (agentMode) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * User message bubble with reduced font size and expandable text for long messages.
 * Font size reduced by 2sp from default bodyMedium (14sp -> 12sp)
 * Long messages (>300 chars) are collapsed with "Show more" option
 */
@Composable
private fun UserMessageBubble(
    content: String,
    timestamp: String,
    bubbleColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    // Threshold for collapsing long messages (in characters)
    val collapseThreshold = 300
    val isLongMessage = content.length > collapseThreshold
    var isExpanded by remember { mutableStateOf(false) }

    // Display text - truncated if long and not expanded
    val displayText = if (isLongMessage && !isExpanded) {
        content.take(collapseThreshold) + "..."
    } else {
        content
    }

    Box(
        modifier = modifier
            .widthIn(max = 320.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 4.dp
                )
            )
            .background(bubbleColor)
            .then(
                if (isLongMessage) {
                    Modifier.clickable { isExpanded = !isExpanded }
                } else {
                    Modifier
                }
            )
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,  // Reduced from 14sp (bodyMedium) to 12sp
                    lineHeight = 18.sp
                ),
                color = textColor,
                modifier = Modifier.animateContentSize()
            )

            // Show expand/collapse indicator for long messages
            if (isLongMessage) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isExpanded) "Show less" else "Show more",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = textColor.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.End)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp
                ),
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    canRestore: Boolean = false,
    onRestore: () -> Unit = {},
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
    onApplyChange: ((String) -> Unit)? = null
) {
    val extendedColors = OroTheme.extendedColors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (message.isUser) {
            // User message - compact bubble style
            if (message.content.isNotEmpty()) {
                UserMessageBubble(
                    content = message.content,
                    timestamp = message.formattedTime,
                    bubbleColor = extendedColors.chatUserBubble,
                    textColor = extendedColors.chatUserText
                )
                if (canRestore) {
                    // A checkpoint was taken before this message's run.
                    androidx.compose.material3.TextButton(
                        onClick = onRestore,
                        contentPadding = PaddingValues(horizontal = Space.sm, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(Space.xs))
                        Text("Restore files", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else {
            // AI message - professional IDE-style with structured content
            if (message.content.isNotEmpty()) {
                AiMessageContent(
                    content = message.content,
                    timestamp = message.formattedTime,
                    isStreaming = isStreaming,
                    modifier = Modifier.fillMaxWidth(),
                    // The list's trailing indicator carries the label.
                    showWorkingIndicator = false
                )
            }
        }

        // Code changes as diff views
        message.codeChanges?.forEach { codeChange ->
            DiffView(
                codeChange = codeChange.toUiCodeChange(),
                onApply = onApplyChange?.let { { it(codeChange.filePath) } },
                modifier = Modifier.widthIn(max = 340.dp)
            )
        }
    }
}

/** Orobi is on it: shown for the whole turn, labelled with the current step. */
@Composable
private fun TypingIndicator(label: String) {
    OrobiWorking(label = label, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun ContextStatsTooltip(
    usedTokens: Int,
    contextLimit: Int,
    modifier: Modifier = Modifier
) {
    val percentage = (usedTokens.toFloat() / contextLimit.coerceAtLeast(1)) * 100
    val remaining = (contextLimit - usedTokens).coerceAtLeast(0)

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = OroCorners.small
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = OroCorners.small
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${"%,d".format(usedTokens)} Tokens",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${percentage.toInt()}% Usage",
                style = MaterialTheme.typography.labelSmall,
                color = if (percentage > 90) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (percentage / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        if (percentage > 90) MaterialTheme.colorScheme.error 
                        else MaterialTheme.colorScheme.primary
                    )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "$0.00 Cost", // Placeholder for cost as we don't have pricing info yet
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

