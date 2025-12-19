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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.R
import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.DeepInfraModels
import com.codex.stormy.data.ai.tools.TodoItem
import com.codex.stormy.data.local.entity.MessageStatus
import com.codex.stormy.domain.model.ChatMessage
import com.codex.stormy.ui.components.DiffView
import com.codex.stormy.ui.components.MarkdownText
import com.codex.stormy.ui.components.TaskPlanningPanel
import com.codex.stormy.ui.components.toUiCodeChange
import com.codex.stormy.ui.theme.CodeXTheme

@Composable
fun ChatTab(
    messages: List<ChatMessage>,
    inputText: String,
    isLoading: Boolean,
    agentMode: Boolean,
    taskList: List<TodoItem> = emptyList(),
    currentModel: AiModel = DeepInfraModels.defaultModel,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onToggleAgentMode: () -> Unit,
    onModelChange: (AiModel) -> Unit = {}
) {
    val listState = rememberLazyListState()

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    ChatBubble(
                        message = message,
                        modifier = Modifier.animateItem()
                    )
                }

                if (isLoading) {
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
            isEnabled = !isLoading,
            agentMode = agentMode,
            currentModel = currentModel,
            onModelChange = onModelChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
    modifier: Modifier = Modifier,
    onApplyChange: ((String) -> Unit)? = null
) {
    val extendedColors = CodeXTheme.extendedColors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main message bubble
        if (message.content.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isUser) 16.dp else 4.dp,
                            bottomEnd = if (message.isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (message.isUser) {
                            extendedColors.chatUserBubble
                        } else {
                            extendedColors.chatAssistantBubble
                        }
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isUser) {
                            extendedColors.chatUserText
                        } else {
                            extendedColors.chatAssistantText
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = message.formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isUser) {
                            extendedColors.chatUserText.copy(alpha = 0.7f)
                        } else {
                            extendedColors.chatAssistantText.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.align(Alignment.End)
                    )
                }
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
    isEnabled: Boolean,
    agentMode: Boolean,
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier) {
        // Model selector row - compact and minimal
        CompactModelSelector(
            currentModel = currentModel,
            onModelChange = onModelChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
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
                        if (value.isNotBlank()) {
                            onSend()
                            focusManager.clearFocus()
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            AnimatedVisibility(
                visible = value.isNotBlank(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(
                    onClick = {
                        onSend()
                        focusManager.clearFocus()
                    },
                    enabled = isEnabled && value.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
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

/**
 * Compact model selector that displays current model with dropdown
 * Shows model name with capability badges (streaming, tools, thinking)
 */
@Composable
private fun CompactModelSelector(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }
    val availableModels = remember { DeepInfraModels.allModels }

    Box(modifier = modifier) {
        // Current model display - clickable to show dropdown
        Surface(
            onClick = { showDropdown = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // Capability badges
                ModelCapabilityBadges(
                    model = currentModel,
                    compact = true
                )

                // Dropdown arrow
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = "Select model",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Dropdown menu with model options
        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            modifier = Modifier.widthIn(min = 280.dp, max = 340.dp)
        ) {
            // Group models by category
            val thinkingModels = availableModels.filter { it.isThinkingModel }
            val agentModels = availableModels.filter { it.supportsToolCalls && !it.isThinkingModel }
            val fastModels = availableModels.filter { !it.supportsToolCalls && !it.isThinkingModel }

            // Thinking/Reasoning models section
            if (thinkingModels.isNotEmpty()) {
                ModelSectionHeader(
                    title = "Reasoning Models",
                    icon = Icons.Outlined.Psychology
                )
                thinkingModels.forEach { model ->
                    ModelDropdownItem(
                        model = model,
                        isSelected = model.id == currentModel.id,
                        onClick = {
                            onModelChange(model)
                            showDropdown = false
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            // Agent-capable models section
            if (agentModels.isNotEmpty()) {
                ModelSectionHeader(
                    title = "Agent Models",
                    icon = Icons.Outlined.AutoAwesome
                )
                agentModels.forEach { model ->
                    ModelDropdownItem(
                        model = model,
                        isSelected = model.id == currentModel.id,
                        onClick = {
                            onModelChange(model)
                            showDropdown = false
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            // Fast/lightweight models section
            if (fastModels.isNotEmpty()) {
                ModelSectionHeader(
                    title = "Fast Models",
                    icon = Icons.Outlined.Speed
                )
                fastModels.forEach { model ->
                    ModelDropdownItem(
                        model = model,
                        isSelected = model.id == currentModel.id,
                        onClick = {
                            onModelChange(model)
                            showDropdown = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Section header for model groups in dropdown
 */
@Composable
private fun ModelSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Individual model item in dropdown menu
 */
@Composable
private fun ModelDropdownItem(
    model: AiModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatContextLength(model.contextLength),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModelCapabilityBadges(model = model, compact = false)

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Selected",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/**
 * Small capability badges showing model features
 */
@Composable
private fun ModelCapabilityBadges(
    model: AiModel,
    compact: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (model.supportsStreaming) {
            CapabilityBadge(
                icon = Icons.Outlined.Bolt,
                label = if (compact) null else "Stream",
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (model.supportsToolCalls) {
            CapabilityBadge(
                icon = Icons.Outlined.AutoAwesome,
                label = if (compact) null else "Tools",
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (model.isThinkingModel) {
            CapabilityBadge(
                icon = Icons.Outlined.Psychology,
                label = if (compact) null else "Think",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

/**
 * Individual capability badge
 */
@Composable
private fun CapabilityBadge(
    icon: ImageVector,
    label: String?,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(12.dp),
                tint = color
            )
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = color
                )
            }
        }
    }
}

/**
 * Format context length for display (e.g., "128K context")
 */
private fun formatContextLength(length: Int): String {
    return when {
        length >= 1000000 -> "${length / 1000000}M context"
        length >= 1000 -> "${length / 1000}K context"
        else -> "$length tokens"
    }
}
