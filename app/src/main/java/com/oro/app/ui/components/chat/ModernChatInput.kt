package com.oro.app.ui.components.chat

import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.outlined.Lightbulb
import com.oro.app.ui.theme.Space
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.ExpressiveShapes
import com.oro.app.ui.theme.Elevation
import com.oro.app.ui.components.expressive.expressivePress
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import com.oro.app.ui.theme.OroCorners
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oro.app.data.ai.AiModel
import com.oro.app.domain.model.FileTreeNode

/**
 * Modern chat input component inspired by professional AI coding assistants
 * Features:
 * - Clean, minimal design with rounded corners
 * - Model selector integration
 * - Context actions (attach file, add code, etc.)
 * - Agent mode indicator
 * - Expandable input area
 * - @ file/folder mention support
 */
@Composable
fun ModernChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    isProcessing: Boolean = false,
    agentMode: Boolean = true,
    currentModel: AiModel,
    onModelClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onContextClick: () -> Unit = {},
    contextPercent: Int = 0,
    contextIsCritical: Boolean = false,
    fileTree: List<FileTreeNode> = emptyList(),
    placeholder: String = "Ask anything, or describe what to build",
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var showContextActions by remember { mutableStateOf(false) }

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

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
            shape = OroCorners.xLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            // Flat. The bar already separates from the transcript by tonal
            // level; a shadow under a full-width surface at the bottom edge
            // just darkens the gap.
            tonalElevation = Elevation.flat,
            shadowElevation = Elevation.flat,
            border = BorderStroke(
                Sizes.hairline,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(modifier = Modifier.padding(Space.md)) {
                // Text first, controls beneath.
                //
                // The old layout put the model chip *above* the field and the
                // send button beside it, which left the text in a narrow column
                // between two control stacks. Typing is the primary act here,
                // so it gets the full width and the top position; everything
                // else sits on one row below where it does not compete.
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onValueChange(newValue.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.sm, vertical = Space.xs)
                        .focusRequester(focusRequester),
                    enabled = isEnabled,
                    // Capped at three lines, then it scrolls. An input that
                    // grows without limit eventually owns the screen and
                    // pushes the conversation it refers to out of view.
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (textFieldValue.text.isNotBlank() && isEnabled) {
                                onSend()
                                focusManager.clearFocus()
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    // One line, ellipsised. A wrapping hint
                                    // makes the empty bar taller than the
                                    // filled one, so the bar jumps the moment
                                    // the user starts typing.
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(Modifier.height(Space.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    InputActionButton(
                        icon = Icons.Outlined.Tune,
                        contentDescription = "Chat settings",
                        onClick = onSettingsClick,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // The chip takes whatever the fixed controls leave and no
                    // more. A long model id ("org/model-name-instruct:free")
                    // used to be measured at full width, which pushed the
                    // context meter and the send button off the right edge.
                    Box(Modifier.weight(1f)) {
                        ModelChip(
                            model = currentModel,
                            onClick = onModelClick
                        )
                    }

                    // Context meter. A ring rather than a number alone: it is
                    // glanceable at this size, and it only earns its place on
                    // the bar once there is something to notice.
                    AnimatedVisibility(
                        // Always shown once a conversation exists. Hiding it
                        // below a threshold meant the number appeared out of
                        // nowhere at the point it became a problem, which is
                        // the worst moment to introduce a new control.
                        visible = contextPercent > 0,
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                            scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            scaleOut(MaterialTheme.motionScheme.fastSpatialSpec())
                    ) {
                        ContextMeter(
                            percent = contextPercent,
                            isCritical = contextIsCritical,
                            onClick = onContextClick
                        )
                    }

                    SendButton(
                        canSend = textFieldValue.text.isNotBlank() && isEnabled,
                        isProcessing = isProcessing,
                        onSend = {
                            onSend()
                            focusManager.clearFocus()
                        },
                        onStop = onStop
                    )
                }
            }
        }
    }
}

/**
 * Send, or stop while generating.
 *
 * One control in two states rather than two controls: the stop button replaces
 * send in place, so the target the user is already aiming at is the one that
 * interrupts. A separate stop button elsewhere means hunting for it mid-stream.
 */
@Composable
private fun SendButton(
    canSend: Boolean,
    isProcessing: Boolean,
    onSend: () -> Unit,
    onStop: (() -> Unit)?
) {
    val interaction = remember { MutableInteractionSource() }

    val container by animateColorAsState(
        when {
            isProcessing -> MaterialTheme.colorScheme.surfaceContainerHighest
            canSend -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "sendContainer"
    )
    val content by animateColorAsState(
        when {
            isProcessing -> MaterialTheme.colorScheme.onSurface
            canSend -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "sendContent"
    )

    Surface(
        onClick = { if (isProcessing) onStop?.invoke() else onSend() },
        enabled = isProcessing || canSend,
        // Same 40dp as the context meter beside it, so the row's controls
        // share one height instead of the send button towering over them.
        modifier = Modifier
            .size(Sizes.minTouchTarget - Space.sm)
            .expressivePress(interaction),
        shape = ExpressiveShapes.circle(),
        color = container,
        contentColor = content,
        interactionSource = interaction
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isProcessing) {
                    Icons.Rounded.Stop
                } else {
                    Icons.Rounded.ArrowUpward
                },
                contentDescription = if (isProcessing) "Stop generating" else "Send message",
                modifier = Modifier.size(Sizes.icon)
            )
        }
    }
}

/**
 * Context fill, as a ring with the percentage inside.
 *
 * Tapping opens the breakdown, so the meter is the entry point to the thing
 * that explains it rather than a read-only badge the user cannot act on.
 */
@Composable
private fun ContextMeter(
    percent: Int,
    isCritical: Boolean,
    onClick: () -> Unit
) {
    val sweep by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "contextSweep"
    )
    val ringColor = if (isCritical) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Surface(
        onClick = onClick,
        modifier = Modifier.size(Sizes.minTouchTarget - Space.sm),
        shape = ExpressiveShapes.circle(),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(Sizes.iconLarge)) {
                val stroke = 3.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Text(
                text = "$percent",
                style = MaterialTheme.typography.labelSmall,
                color = ringColor
            )
        }
    }
}

/**
 * Compact model selector chip
 */
@Composable
private fun ModelChip(
    model: AiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationAngle by animateFloatAsState(
        targetValue = 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "chevron_rotation"
    )

    Surface(
        onClick = onClick,
        shape = OroCorners.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Model type icon
            Icon(
                imageVector = when {
                    model.isThinkingModel -> Icons.Outlined.Lightbulb
                    // A wrench for tool use, a bolt for plain completion.
                    // Both used to be the same sparkle, which made the icon
                    // decorative rather than informative.
                    model.supportsToolCalls -> Icons.Outlined.Handyman
                    else -> Icons.Outlined.Bolt
                },
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = when {
                    model.isThinkingModel -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
            )

            // Model name. Weighted so it is the part that gives way: the
            // chevron stays visible however long the name is.
            Text(
                text = model.shortName(),
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Dropdown indicator
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = "Select model",
                modifier = Modifier
                    .size(14.dp)
                    .rotate(rotationAngle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Context actions bar with attachment options
 */
@Composable
private fun ContextActionsBar(
    onAddFile: () -> Unit,
    onAddCode: () -> Unit,
    onAddImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ContextActionChip(
            icon = Icons.Outlined.AttachFile,
            label = "Add file",
            onClick = onAddFile
        )

        ContextActionChip(
            icon = Icons.Outlined.Code,
            label = "Add code",
            onClick = onAddCode
        )

        ContextActionChip(
            icon = Icons.Outlined.Image,
            label = "Add image",
            onClick = onAddImage
        )
    }
}

/**
 * Individual context action chip
 */
@Composable
private fun ContextActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = OroCorners.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Small action button for input area
 */
@Composable
private fun InputActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = tint
        )
    }
}

/**
 * The name to show in the compact chip. Model ids from routers carry an
 * owner prefix ("meta-llama/") that tells the user nothing at this size.
 */
private fun AiModel.shortName(): String =
    name.substringAfterLast('/').ifBlank { name }
