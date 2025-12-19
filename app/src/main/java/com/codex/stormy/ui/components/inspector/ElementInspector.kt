package com.codex.stormy.ui.components.inspector

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.ui.theme.PoppinsFontFamily
import kotlin.math.roundToInt

/**
 * Style change request for AI agent
 */
data class StyleChangeRequest(
    val selector: String,
    val property: String,
    val oldValue: String?,
    val newValue: String,
    val elementHtml: String
)

/**
 * Text content change request
 */
data class TextChangeRequest(
    val selector: String,
    val oldText: String,
    val newText: String,
    val elementHtml: String
)

/**
 * Inspected element data
 */
data class InspectorElementData(
    val tagName: String,
    val id: String,
    val className: String,
    val innerHTML: String,
    val outerHTML: String,
    val computedStyles: Map<String, String>,
    val attributes: Map<String, String>,
    val boundingRect: InspectorRect
)

data class InspectorRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

/**
 * Production-grade visual element inspector/editor
 * Inspired by Chrome DevTools and Figma
 */
@Composable
fun VisualElementInspector(
    element: InspectorElementData,
    onClose: () -> Unit,
    onStyleChange: (StyleChangeRequest) -> Unit,
    onTextChange: (TextChangeRequest) -> Unit,
    onAiEditRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Design", "Layout", "Text", "Code")

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newHeight = panelHeight - with(density) { dragAmount.y.toDp() }
                            panelHeight = newHeight.coerceIn(200.dp, 500.dp)
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

            // Header
            InspectorHeader(
                element = element,
                onClose = onClose,
                onCopyHtml = {
                    clipboardManager.setText(AnnotatedString(element.outerHTML))
                },
                onAiEdit = { prompt ->
                    onAiEditRequest(prompt)
                }
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
                    3 -> CodeTab(element = element)
                }
            }
        }
    }
}

/**
 * Inspector header with element selector and quick actions
 */
@Composable
private fun InspectorHeader(
    element: InspectorElementData,
    onClose: () -> Unit,
    onCopyHtml: () -> Unit,
    onAiEdit: (String) -> Unit
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
                // AI Edit button
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

        // AI Prompt input
        AnimatedVisibility(
            visible = showAiPrompt,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
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

/**
 * Quick AI prompt input
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

/**
 * Design tab - Colors, backgrounds, borders
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesignTab(
    element: InspectorElementData,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    val styles = element.computedStyles
    val selector = buildSelector(element)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Colors section
        InspectorSection(
            title = "Colors",
            icon = Icons.Outlined.Palette
        ) {
            // Background color
            ColorPropertyEditor(
                label = "Background",
                value = styles["background-color"] ?: "transparent",
                onValueChange = { newValue ->
                    onStyleChange(StyleChangeRequest(
                        selector = selector,
                        property = "background-color",
                        oldValue = styles["background-color"],
                        newValue = newValue,
                        elementHtml = element.outerHTML
                    ))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Text color
            ColorPropertyEditor(
                label = "Text Color",
                value = styles["color"] ?: "inherit",
                onValueChange = { newValue ->
                    onStyleChange(StyleChangeRequest(
                        selector = selector,
                        property = "color",
                        oldValue = styles["color"],
                        newValue = newValue,
                        elementHtml = element.outerHTML
                    ))
                }
            )
        }

        // Border section
        InspectorSection(
            title = "Border",
            icon = Icons.Outlined.ViewModule
        ) {
            // Border radius
            SliderPropertyEditor(
                label = "Border Radius",
                value = parsePixelValue(styles["border-radius"]),
                range = 0f..50f,
                unit = "px",
                onValueChange = { newValue ->
                    onStyleChange(StyleChangeRequest(
                        selector = selector,
                        property = "border-radius",
                        oldValue = styles["border-radius"],
                        newValue = "${newValue.toInt()}px",
                        elementHtml = element.outerHTML
                    ))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Border width
            SliderPropertyEditor(
                label = "Border Width",
                value = parsePixelValue(styles["border-width"]),
                range = 0f..10f,
                unit = "px",
                onValueChange = { newValue ->
                    onStyleChange(StyleChangeRequest(
                        selector = selector,
                        property = "border-width",
                        oldValue = styles["border-width"],
                        newValue = "${newValue.toInt()}px",
                        elementHtml = element.outerHTML
                    ))
                }
            )
        }

        // Effects section
        InspectorSection(
            title = "Effects",
            icon = Icons.Outlined.Layers
        ) {
            // Opacity
            SliderPropertyEditor(
                label = "Opacity",
                value = (styles["opacity"]?.toFloatOrNull() ?: 1f) * 100f,
                range = 0f..100f,
                unit = "%",
                onValueChange = { newValue ->
                    onStyleChange(StyleChangeRequest(
                        selector = selector,
                        property = "opacity",
                        oldValue = styles["opacity"],
                        newValue = (newValue / 100f).toString(),
                        elementHtml = element.outerHTML
                    ))
                }
            )
        }
    }
}

/**
 * Layout tab - Size, spacing, positioning
 */
@Composable
private fun LayoutTab(
    element: InspectorElementData,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    val styles = element.computedStyles
    val selector = buildSelector(element)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Size section
        InspectorSection(
            title = "Size",
            icon = Icons.Outlined.Height
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextInputProperty(
                    label = "Width",
                    value = styles["width"] ?: "auto",
                    onValueChange = { newValue ->
                        onStyleChange(StyleChangeRequest(
                            selector = selector,
                            property = "width",
                            oldValue = styles["width"],
                            newValue = newValue,
                            elementHtml = element.outerHTML
                        ))
                    },
                    modifier = Modifier.weight(1f)
                )

                TextInputProperty(
                    label = "Height",
                    value = styles["height"] ?: "auto",
                    onValueChange = { newValue ->
                        onStyleChange(StyleChangeRequest(
                            selector = selector,
                            property = "height",
                            oldValue = styles["height"],
                            newValue = newValue,
                            elementHtml = element.outerHTML
                        ))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Spacing section
        InspectorSection(
            title = "Spacing",
            icon = Icons.Outlined.SpaceBar
        ) {
            // Margin
            SpacingBoxEditor(
                label = "Margin",
                top = parsePixelValue(styles["margin-top"]),
                right = parsePixelValue(styles["margin-right"]),
                bottom = parsePixelValue(styles["margin-bottom"]),
                left = parsePixelValue(styles["margin-left"]),
                onValueChange = { property, value ->
                    onStyleChange(StyleChangeRequest(
                        selector = selector,
                        property = property,
                        oldValue = styles[property],
                        newValue = "${value.toInt()}px",
                        elementHtml = element.outerHTML
                    ))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Padding
            SpacingBoxEditor(
                label = "Padding",
                top = parsePixelValue(styles["padding-top"]),
                right = parsePixelValue(styles["padding-right"]),
                bottom = parsePixelValue(styles["padding-bottom"]),
                left = parsePixelValue(styles["padding-left"]),
                onValueChange = { property, value ->
                    onStyleChange(StyleChangeRequest(
                        selector = selector,
                        property = property,
                        oldValue = styles[property],
                        newValue = "${value.toInt()}px",
                        elementHtml = element.outerHTML
                    ))
                }
            )
        }

        // Display section
        InspectorSection(
            title = "Display",
            icon = Icons.Outlined.ViewModule
        ) {
            val displayOptions = listOf("block", "inline-block", "flex", "grid", "none")
            val currentDisplay = styles["display"] ?: "block"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                displayOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentDisplay == option,
                        onClick = {
                            onStyleChange(StyleChangeRequest(
                                selector = selector,
                                property = "display",
                                oldValue = currentDisplay,
                                newValue = option,
                                elementHtml = element.outerHTML
                            ))
                        }
                    )
                }
            }
        }
    }
}

/**
 * Text tab - Typography settings
 */
@Composable
private fun TextTab(
    element: InspectorElementData,
    onTextChange: (TextChangeRequest) -> Unit,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    val styles = element.computedStyles
    val selector = buildSelector(element)

    // Extract text content (simplified - in real app would be more sophisticated)
    val textContent = element.innerHTML
        .replace(Regex("<[^>]*>"), "")
        .trim()
        .take(100)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Text content editor
        if (textContent.isNotEmpty()) {
            InspectorSection(
                title = "Content",
                icon = Icons.Outlined.TextFields
            ) {
                var editedText by remember { mutableStateOf(textContent) }

                TextContentEditor(
                    value = editedText,
                    onValueChange = { editedText = it },
                    onApply = {
                        onTextChange(TextChangeRequest(
                            selector = selector,
                            oldText = textContent,
                            newText = editedText,
                            elementHtml = element.outerHTML
                        ))
                    }
                )
            }
        }

        // Font size
        InspectorSection(
            title = "Typography",
            icon = Icons.Outlined.FormatSize
        ) {
            SliderPropertyEditor(
                label = "Font Size",
                value = parsePixelValue(styles["font-size"]),
                range = 8f..72f,
                unit = "px",
                onValueChange = { newValue ->
                    onStyleChange(StyleChangeRequest(
                        selector = selector,
                        property = "font-size",
                        oldValue = styles["font-size"],
                        newValue = "${newValue.toInt()}px",
                        elementHtml = element.outerHTML
                    ))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Font weight
            val fontWeightOptions = listOf("normal", "500", "600", "bold")
            val currentWeight = styles["font-weight"] ?: "normal"

            Text(
                text = "Font Weight",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                fontWeightOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentWeight == option ||
                                    (option == "bold" && currentWeight == "700"),
                        onClick = {
                            onStyleChange(StyleChangeRequest(
                                selector = selector,
                                property = "font-weight",
                                oldValue = currentWeight,
                                newValue = option,
                                elementHtml = element.outerHTML
                            ))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Text align
            val alignOptions = listOf("left", "center", "right", "justify")
            val currentAlign = styles["text-align"] ?: "left"

            Text(
                text = "Alignment",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                alignOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentAlign == option,
                        onClick = {
                            onStyleChange(StyleChangeRequest(
                                selector = selector,
                                property = "text-align",
                                oldValue = currentAlign,
                                newValue = option,
                                elementHtml = element.outerHTML
                            ))
                        }
                    )
                }
            }
        }
    }
}

/**
 * Code tab - Raw HTML view
 */
@Composable
private fun CodeTab(element: InspectorElementData) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HTML",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(element.outerHTML))
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = element.outerHTML.take(500),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

// ==================== Helper Components ====================

@Composable
private fun InspectorSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(modifier = Modifier.padding(start = 24.dp, top = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ColorPropertyEditor(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var editedValue by remember(value) { mutableStateOf(value) }
    val parsedColor = parseColor(value)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Color preview
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(parsedColor)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(4.dp)
                )
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            BasicTextField(
                value = editedValue,
                onValueChange = { editedValue = it },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onValueChange(editedValue)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SliderPropertyEditor(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${sliderValue.toInt()}$unit",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChange(sliderValue) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun TextInputProperty(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editedValue by remember(value) { mutableStateOf(value) }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        BasicTextField(
            value = editedValue,
            onValueChange = { editedValue = it },
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onValueChange(editedValue)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SpacingBoxEditor(
    label: String,
    top: Float,
    right: Float,
    bottom: Float,
    left: Float,
    onValueChange: (String, Float) -> Unit
) {
    val prefix = label.lowercase()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Top
        SpacingInput(
            value = top,
            onValueChange = { onValueChange("$prefix-top", it) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left
            SpacingInput(
                value = left,
                onValueChange = { onValueChange("$prefix-left", it) }
            )

            // Center box
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
            )

            // Right
            SpacingInput(
                value = right,
                onValueChange = { onValueChange("$prefix-right", it) }
            )
        }

        // Bottom
        SpacingInput(
            value = bottom,
            onValueChange = { onValueChange("$prefix-bottom", it) }
        )
    }
}

@Composable
private fun SpacingInput(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    var editedValue by remember(value) { mutableStateOf(value.toInt().toString()) }

    BasicTextField(
        value = editedValue,
        onValueChange = {
            editedValue = it.filter { c -> c.isDigit() }
        },
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = {
            onValueChange(editedValue.toFloatOrNull() ?: 0f)
        }),
        modifier = Modifier
            .width(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun OptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "chip_bg"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TextContentEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontFamily = PoppinsFontFamily
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(12.dp)
        )

        Surface(
            onClick = onApply,
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Apply",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

// ==================== Utility Functions ====================

private fun buildSelector(element: InspectorElementData): String {
    val sb = StringBuilder(element.tagName.lowercase())
    if (element.id.isNotEmpty()) {
        sb.append("#${element.id}")
    }
    if (element.className.isNotEmpty()) {
        element.className.split(" ").take(2).forEach { className ->
            if (className.isNotBlank()) {
                sb.append(".$className")
            }
        }
    }
    return sb.toString()
}

private fun parsePixelValue(value: String?): Float {
    if (value == null) return 0f
    return value.replace("px", "").toFloatOrNull() ?: 0f
}

private fun parseColor(colorString: String): Color {
    return try {
        when {
            colorString.startsWith("#") -> {
                val hex = colorString.removePrefix("#")
                when (hex.length) {
                    3 -> {
                        val r = hex[0].toString().repeat(2).toInt(16)
                        val g = hex[1].toString().repeat(2).toInt(16)
                        val b = hex[2].toString().repeat(2).toInt(16)
                        Color(r, g, b)
                    }
                    6 -> Color(android.graphics.Color.parseColor(colorString))
                    8 -> Color(android.graphics.Color.parseColor(colorString))
                    else -> Color.Transparent
                }
            }
            colorString.startsWith("rgb") -> {
                val values = colorString
                    .replace(Regex("rgba?\\("), "")
                    .replace(")", "")
                    .split(",")
                    .map { it.trim().toIntOrNull() ?: 0 }
                if (values.size >= 3) {
                    Color(values[0], values[1], values[2])
                } else {
                    Color.Transparent
                }
            }
            colorString == "transparent" -> Color.Transparent
            else -> Color.Transparent
        }
    } catch (e: Exception) {
        Color.Transparent
    }
}
