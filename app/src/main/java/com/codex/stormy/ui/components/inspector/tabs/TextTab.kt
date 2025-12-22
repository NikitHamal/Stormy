package com.codex.stormy.ui.components.inspector.tabs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.components.inspector.InspectorElementData
import com.codex.stormy.ui.components.inspector.InspectorSection
import com.codex.stormy.ui.components.inspector.InspectorUtils
import com.codex.stormy.ui.components.inspector.OptionChip
import com.codex.stormy.ui.components.inspector.SliderPropertyEditor
import com.codex.stormy.ui.components.inspector.StyleChangeRequest
import com.codex.stormy.ui.components.inspector.TextChangeRequest
import com.codex.stormy.ui.components.inspector.TextContentEditor
import com.codex.stormy.ui.components.inspector.TextInputProperty
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * Text tab for the Visual Element Inspector.
 * Handles typography settings and text content editing.
 *
 * Production-grade implementation with comprehensive text styling options,
 * proper layout constraints, and Material 3 design patterns.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextTab(
    element: InspectorElementData,
    onTextChange: (TextChangeRequest) -> Unit,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    val styles = element.computedStyles
    val selector = InspectorUtils.buildSelector(element)
    val textContent = InspectorUtils.extractTextContent(element.innerHTML)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Text content editor - only show if element has text
        if (textContent.isNotEmpty()) {
            InspectorSection(
                title = "Content",
                icon = Icons.Outlined.TextFields
            ) {
                var editedText by remember(textContent) { mutableStateOf(textContent) }

                TextContentEditor(
                    value = editedText,
                    onValueChange = { editedText = it },
                    onApply = {
                        if (editedText != textContent) {
                            onTextChange(
                                TextChangeRequest(
                                    selector = selector,
                                    oldText = textContent,
                                    newText = editedText,
                                    elementHtml = element.outerHTML
                                )
                            )
                        }
                    }
                )
            }
        }

        // Typography section
        InspectorSection(
            title = "Typography",
            icon = Icons.Outlined.FormatSize
        ) {
            TypographySection(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }

        // Font Weight & Style section
        InspectorSection(
            title = "Weight & Style",
            icon = Icons.Outlined.FormatBold
        ) {
            FontWeightSection(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }

        // Alignment section
        InspectorSection(
            title = "Alignment",
            icon = Icons.Outlined.FormatAlignCenter
        ) {
            AlignmentSection(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }
    }
}

/**
 * Typography controls for font size, line height, letter spacing.
 */
@Composable
private fun TypographySection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Font Size
        SliderPropertyEditor(
            label = "Font Size",
            value = InspectorUtils.parsePixelValue(styles["font-size"]),
            range = 8f..72f,
            unit = "px",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "font-size",
                        oldValue = styles["font-size"],
                        newValue = "${newValue.toInt()}px",
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Line Height
        SliderPropertyEditor(
            label = "Line Height",
            value = InspectorUtils.parseLineHeight(styles["line-height"]),
            range = 0.8f..3f,
            unit = "x",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "line-height",
                        oldValue = styles["line-height"],
                        newValue = String.format("%.2f", newValue),
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Letter Spacing
        SliderPropertyEditor(
            label = "Letter Spacing",
            value = InspectorUtils.parsePixelValue(styles["letter-spacing"]),
            range = -5f..20f,
            unit = "px",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "letter-spacing",
                        oldValue = styles["letter-spacing"],
                        newValue = "${newValue.toInt()}px",
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Word Spacing
        SliderPropertyEditor(
            label = "Word Spacing",
            value = InspectorUtils.parsePixelValue(styles["word-spacing"]),
            range = -5f..30f,
            unit = "px",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "word-spacing",
                        oldValue = styles["word-spacing"],
                        newValue = "${newValue.toInt()}px",
                        elementHtml = elementHtml
                    )
                )
            }
        )
    }
}

/**
 * Font weight and style options.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontWeightSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Font Weight options
        val fontWeightOptions = listOf(
            "100" to "Thin",
            "300" to "Light",
            "400" to "Regular",
            "500" to "Medium",
            "600" to "Semibold",
            "700" to "Bold",
            "900" to "Black"
        )
        val currentWeight = styles["font-weight"] ?: "400"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Font Weight",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                fontWeightOptions.forEach { (value, label) ->
                    val isSelected = currentWeight == value ||
                            (value == "400" && currentWeight == "normal") ||
                            (value == "700" && currentWeight == "bold")

                    OptionChip(
                        label = label,
                        isSelected = isSelected,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "font-weight",
                                    oldValue = currentWeight,
                                    newValue = value,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Font Style options
        val fontStyleOptions = listOf("normal", "italic", "oblique")
        val currentStyle = styles["font-style"] ?: "normal"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Font Style",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                fontStyleOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentStyle == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "font-style",
                                    oldValue = currentStyle,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Text Decoration options
        val textDecorationOptions = listOf("none", "underline", "line-through", "overline")
        val currentDecoration = styles["text-decoration"]?.split(" ")?.firstOrNull() ?: "none"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Text Decoration",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                textDecorationOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentDecoration == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "text-decoration",
                                    oldValue = styles["text-decoration"],
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Text Transform options
        val textTransformOptions = listOf("none", "uppercase", "lowercase", "capitalize")
        val currentTransform = styles["text-transform"] ?: "none"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Text Transform",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                textTransformOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentTransform == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "text-transform",
                                    oldValue = currentTransform,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Text alignment options.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlignmentSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Text Align
        val alignOptions = listOf("left", "center", "right", "justify")
        val currentAlign = styles["text-align"] ?: "left"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Horizontal Align",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
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
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "text-align",
                                    oldValue = currentAlign,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Vertical Align
        val verticalAlignOptions = listOf("baseline", "top", "middle", "bottom")
        val currentVerticalAlign = styles["vertical-align"] ?: "baseline"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Vertical Align",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                verticalAlignOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentVerticalAlign == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "vertical-align",
                                    oldValue = currentVerticalAlign,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // White Space
        val whiteSpaceOptions = listOf("normal", "nowrap", "pre", "pre-wrap", "pre-line")
        val currentWhiteSpace = styles["white-space"] ?: "normal"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "White Space",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                whiteSpaceOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentWhiteSpace == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "white-space",
                                    oldValue = currentWhiteSpace,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Text Indent
        SliderPropertyEditor(
            label = "Text Indent",
            value = InspectorUtils.parsePixelValue(styles["text-indent"]),
            range = 0f..100f,
            unit = "px",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "text-indent",
                        oldValue = styles["text-indent"],
                        newValue = "${newValue.toInt()}px",
                        elementHtml = elementHtml
                    )
                )
            }
        )
    }
}
