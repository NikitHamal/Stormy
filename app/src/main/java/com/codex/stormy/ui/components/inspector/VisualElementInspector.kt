package com.codex.stormy.ui.components.inspector

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.ui.components.inspector.tabs.DesignTab
import com.codex.stormy.ui.components.inspector.tabs.ImageTab
import com.codex.stormy.ui.components.inspector.tabs.LayoutTab
import com.codex.stormy.ui.components.inspector.tabs.TextTab
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * Production-grade visual element inspector/editor.
 * Inspired by Chrome DevTools and Figma.
 *
 * Provides a bottom panel for inspecting and editing HTML element properties
 * including design (colors, borders, effects), layout (size, spacing), and text.
 *
 * @param element The inspected element data
 * @param onClose Callback when the inspector is closed
 * @param onStyleChange Callback for style changes (direct or AI-assisted)
 * @param onTextChange Callback for text content changes
 * @param onAiEditRequest Callback for freeform AI edit requests (optional - hide AI features if null)
 * @param onImageChange Callback for image source changes
 * @param onPickImage Callback to open image picker
 * @param enableAiFeatures Whether to show AI editing features (default: true)
 * @param hasValidModel Whether a valid AI model is available
 * @param modifier Modifier for the composable
 */
@Composable
fun VisualElementInspector(
    element: InspectorElementData,
    onClose: () -> Unit,
    onStyleChange: (StyleChangeRequest) -> Unit,
    onTextChange: (TextChangeRequest) -> Unit,
    onAiEditRequest: ((String) -> Unit)? = null,
    onImageChange: ((ImageChangeRequest) -> Unit)? = null,
    onPickImage: (() -> Unit)? = null,
    enableAiFeatures: Boolean = true,
    hasValidModel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Determine if this is an image element
    val isImageElement = InspectorUtils.isImageElement(element)

    // Build tabs list dynamically - add Image tab for <img> elements
    val tabs = remember(isImageElement) {
        if (isImageElement) {
            listOf("Image", "Design", "Layout")
        } else {
            listOf("Design", "Layout", "Text")
        }
    }

    // Panel height state for drag resize
    var panelHeight by remember { mutableStateOf(320.dp) }
    val density = LocalDensity.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle for resizing
            ResizeHandle(
                onDrag = { dragAmountY ->
                    val newHeight = panelHeight - with(density) { dragAmountY.toDp() }
                    panelHeight = newHeight.coerceIn(200.dp, 500.dp)
                }
            )

            // Header
            InspectorHeader(
                element = element,
                onClose = onClose,
                onCopyHtml = {
                    clipboardManager.setText(AnnotatedString(element.outerHTML))
                },
                onAiEdit = if (enableAiFeatures && onAiEditRequest != null) {
                    { prompt -> onAiEditRequest(prompt) }
                } else null,
                hasValidModel = hasValidModel
            )

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isImageElement) {
                    // Image element tabs: Image, Design, Layout
                    when (selectedTab) {
                        0 -> ImageTab(
                            element = element,
                            onImageChange = onImageChange,
                            onPickImage = onPickImage
                        )
                        1 -> DesignTab(
                            element = element,
                            onStyleChange = onStyleChange
                        )
                        2 -> LayoutTab(
                            element = element,
                            onStyleChange = onStyleChange
                        )
                    }
                } else {
                    // Regular element tabs: Design, Layout, Text
                    when (selectedTab) {
                        0 -> DesignTab(
                            element = element,
                            onStyleChange = onStyleChange
                        )
                        1 -> LayoutTab(
                            element = element,
                            onStyleChange = onStyleChange
                        )
                        2 -> TextTab(
                            element = element,
                            onTextChange = onTextChange,
                            onStyleChange = onStyleChange
                        )
                    }
                }
            }
        }
    }
}

/**
 * Drag handle for resizing the inspector panel
 */
@Composable
private fun ResizeHandle(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = "Resize",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * Inspector header with element selector and quick actions
 * @param onAiEdit Callback for AI edit - if null, AI button is hidden
 * @param hasValidModel Whether a valid AI model is selected
 */
@Composable
private fun InspectorHeader(
    element: InspectorElementData,
    onClose: () -> Unit,
    onCopyHtml: () -> Unit,
    onAiEdit: ((String) -> Unit)?,
    hasValidModel: Boolean = true
) {
    var showAiPrompt by remember { mutableStateOf(false) }
    var aiPromptText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Element selector display
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tag badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = element.tagName.lowercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // ID if present
                    if (element.id.isNotEmpty()) {
                        Text(
                            text = "#${element.id}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Classes
                if (element.className.isNotEmpty()) {
                    Text(
                        text = element.className.split(" ").take(3).joinToString(" ") { ".$it" },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick actions
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // AI Edit button - only show if AI features are enabled
                if (onAiEdit != null) {
                    IconButton(
                        onClick = { showAiPrompt = !showAiPrompt },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "AI Edit",
                            modifier = Modifier.size(18.dp),
                            tint = if (showAiPrompt) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Copy HTML
                IconButton(
                    onClick = onCopyHtml,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy HTML",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Close
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // AI Prompt input - only show if AI features are enabled
        if (onAiEdit != null) {
            AnimatedVisibility(
                visible = showAiPrompt,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (!hasValidModel) {
                    NoModelWarning()
                } else {
                    AiPromptInput(
                        value = aiPromptText,
                        onValueChange = { aiPromptText = it },
                        onSubmit = {
                            if (aiPromptText.isNotBlank()) {
                                onAiEdit(aiPromptText)
                                aiPromptText = ""
                                showAiPrompt = false
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Warning shown when no AI model is selected
 */
@Composable
private fun NoModelWarning() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "No AI Model Selected",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Please select a model in AI Models settings to use AI editing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Quick AI prompt input field
 */
@Composable
private fun AiPromptInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontFamily = PoppinsFontFamily
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    keyboardController?.hide()
                    onSubmit()
                }),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "Describe changes (e.g., 'make text blue')...",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontFamily = PoppinsFontFamily
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        IconButton(
            onClick = {
                keyboardController?.hide()
                onSubmit()
            },
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (value.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Apply",
                modifier = Modifier.size(18.dp),
                tint = if (value.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
