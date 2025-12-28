package com.codex.stormy.ui.screens.editor.chat

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.codex.stormy.R
import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.tools.TodoItem
import com.codex.stormy.domain.model.ChatMessage
import com.codex.stormy.ui.components.DiffView
import com.codex.stormy.ui.components.TaskPlanningPanel
import com.codex.stormy.ui.components.chat.FileMentionPopup
import com.codex.stormy.ui.components.chat.ModelSelectorSheet
import com.codex.stormy.ui.components.chat.MentionItem
import com.codex.stormy.ui.components.chat.extractMentionQuery
import com.codex.stormy.ui.components.chat.replaceMentionInText
import com.codex.stormy.ui.components.message.AiMessageContent
import com.codex.stormy.domain.model.FileTreeNode
import com.codex.stormy.ui.components.toUiCodeChange
import com.codex.stormy.ui.theme.CodeXTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTab(
    messages: List<ChatMessage>,
    inputText: String,
    isLoading: Boolean,
    agentMode: Boolean,
    taskList: List<TodoItem> = emptyList(),
    currentModel: AiModel? = null,
    availableModels: List<AiModel> = emptyList(),
    fileTree: List<FileTreeNode> = emptyList(),
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: (() -> Unit)? = null,
    onToggleAgentMode: () -> Unit,
    onModelChange: (AiModel) -> Unit = {},
    onRefreshModels: () -> Unit = {},
    onNavigateToModels: () -> Unit = {},
    isRefreshingModels: Boolean = false
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showModelSelector by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Task planning panel - show when there are active tasks
        AnimatedVisibility(
            visible = taskList.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            TaskPlanningPanel(tasks = taskList)
        }

        if (messages.isEmpty()) {
            WelcomeMessage(
                agentMode = agentMode,
                onToggleAgentMode = onToggleAgentMode,
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
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    ChatBubble(
                        message = message,
                        isStreaming = isLoading && message == messages.lastOrNull() && !message.isUser,
                        modifier = Modifier.animateItem()
                    )
                }

                if (isLoading && messages.lastOrNull()?.isUser == true) {
                    item {
                        TypingIndicator()
                    }
                }
            }
        }

        ChatInput(
            value = inputText,
            onValueChange = onInputChange,
            onSend = onSendMessage,
            onStop = onStopGeneration,
            isEnabled = !isLoading,
            isProcessing = isLoading,
            agentMode = agentMode,
            currentModel = currentModel,
            onModelClick = { showModelSelector = true },
            fileTree = fileTree,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

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

        Spacer(modifier = Modifier.height(24.dp))

        // Agent mode toggle
        AgentModeToggle(
            agentMode = agentMode,
            onToggle = onToggleAgentMode
        )
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
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(24.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Chat mode button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
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
                .clip(RoundedCornerShape(20.dp))
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
                    imageVector = Icons.Outlined.AutoAwesome,
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

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
    onApplyChange: ((String) -> Unit)? = null
) {
    val extendedColors = CodeXTheme.extendedColors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (message.isUser) {
            // User message - compact bubble style
            if (message.content.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 4.dp
                            )
                        )
                        .background(extendedColors.chatUserBubble)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = extendedColors.chatUserText
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = message.formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = extendedColors.chatUserText.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.End)
                        )
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
                    modifier = Modifier.fillMaxWidth()
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

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CodeXTheme.extendedColors.chatAssistantBubble)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 200)
                ),
                label = "dot_alpha_$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .graphicsLayer { this.alpha = alpha }
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: (() -> Unit)?,
    isEnabled: Boolean,
    isProcessing: Boolean,
    agentMode: Boolean,
    currentModel: AiModel?,
    onModelClick: () -> Unit,
    fileTree: List<FileTreeNode> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // @ mention state
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    val mentionQuery = remember(textFieldValue) {
        extractMentionQuery(textFieldValue.text, textFieldValue.selection.start)
    }
    val showMentionPopup = mentionQuery != null

    Column(modifier = modifier) {
        // @ mention popup (above the input)
        FileMentionPopup(
            isVisible = showMentionPopup,
            query = mentionQuery ?: "",
            fileTree = fileTree,
            onSelectItem = { item ->
                val (newText, newCursor) = replaceMentionInText(
                    textFieldValue.text,
                    textFieldValue.selection.start,
                    item
                )
                textFieldValue = TextFieldValue(
                    text = newText,
                    selection = TextRange(newCursor)
                )
                onValueChange(newText)
            },
            onDismiss = { },
            modifier = Modifier.padding(bottom = 4.dp)
        )
        // Model selector row - compact chip that opens bottom sheet
        Surface(
            onClick = onModelClick,
            shape = RoundedCornerShape(12.dp),
            color = if (currentModel != null) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            },
            modifier = Modifier
                .animateContentSize()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentModel != null) {
                    // Model icon based on type
                    Icon(
                        imageVector = when {
                            currentModel.isThinkingModel -> Icons.Outlined.Psychology
                            currentModel.supportsToolCalls -> Icons.Outlined.AutoAwesome
                            else -> Icons.Outlined.Bolt
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when {
                            currentModel.isThinkingModel -> MaterialTheme.colorScheme.tertiary
                            currentModel.supportsToolCalls -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    )

                    // Model name
                    Text(
                        text = currentModel.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    // No model selected - show "Select Model" with warning color
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )

                    Text(
                        text = "Select Model",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Dropdown arrow
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = "Select model",
                    modifier = Modifier.size(18.dp),
                    tint = if (currentModel != null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    onValueChange(newValue.text)
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (agentMode) {
                            context.getString(R.string.chat_hint_agent)
                        } else {
                            context.getString(R.string.chat_hint)
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        )
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp
                ),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                maxLines = 4,
                enabled = isEnabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textFieldValue.text.isNotBlank()) {
                            onSend()
                            focusManager.clearFocus()
                        }
                    }
                )
            )

            // Always reserve space for the button to prevent layout shift
            // Button container with fixed size - prevents text field expansion/contraction
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // Show stop button when processing, send button otherwise
                if (isProcessing && onStop != null) {
                    IconButton(
                        onClick = onStop,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Stop,
                            contentDescription = context.getString(R.string.action_stop)
                        )
                    }
                } else {
                    // Always show the send button, but control visibility with alpha
                    // This prevents the text field from expanding when button is hidden
                    val hasText = textFieldValue.text.isNotBlank()
                    IconButton(
                        onClick = {
                            if (hasText) {
                                onSend()
                                focusManager.clearFocus()
                            }
                        },
                        enabled = isEnabled && hasText,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (hasText) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (hasText) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = context.getString(R.string.action_send)
                        )
                    }
                }
            }
        }

        // Agent mode indicator - subtle hint at bottom
        AnimatedVisibility(
            visible = agentMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Agent mode active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

