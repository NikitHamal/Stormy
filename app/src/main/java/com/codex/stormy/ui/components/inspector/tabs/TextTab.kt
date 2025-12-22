package com.codex.stormy.ui.components.inspector.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.components.inspector.InspectorElementData
import com.codex.stormy.ui.components.inspector.InspectorSection
import com.codex.stormy.ui.components.inspector.InspectorUtils
import com.codex.stormy.ui.components.inspector.OptionChip
import com.codex.stormy.ui.components.inspector.SliderPropertyEditor
import com.codex.stormy.ui.components.inspector.StyleChangeRequest
import com.codex.stormy.ui.components.inspector.TextChangeRequest
import com.codex.stormy.ui.components.inspector.TextContentEditor

/**
 * Text tab for the Visual Element Inspector.
 * Handles typography settings and text content editing.
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

        // Typography section
        InspectorSection(
            title = "Typography",
            icon = Icons.Outlined.FormatSize
        ) {
            TypographyEditors(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypographyEditors(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    // Font size
    SliderPropertyEditor(
        label = "Font Size",
        value = InspectorUtils.parsePixelValue(styles["font-size"]),
        range = 8f..72f,
        unit = "px",
        onValueChange = { newValue ->
            onStyleChange(StyleChangeRequest(
                selector = selector,
                property = "font-size",
                oldValue = styles["font-size"],
                newValue = "${newValue.toInt()}px",
                elementHtml = elementHtml
            ))
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Font weight
    val fontWeightOptions = listOf("normal", "500", "600", "bold")
    val currentWeight = styles["font-weight"] ?: "normal"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Font Weight",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            elementHtml = elementHtml
                        ))
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Text align
    val alignOptions = listOf("left", "center", "right", "justify")
    val currentAlign = styles["text-align"] ?: "left"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Alignment",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            elementHtml = elementHtml
                        ))
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Line Height
    SliderPropertyEditor(
        label = "Line Height",
        value = InspectorUtils.parseLineHeight(styles["line-height"]),
        range = 1f..3f,
        unit = "x",
        onValueChange = { newValue ->
            onStyleChange(StyleChangeRequest(
                selector = selector,
                property = "line-height",
                oldValue = styles["line-height"],
                newValue = String.format("%.1f", newValue),
                elementHtml = elementHtml
            ))
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Letter Spacing
    SliderPropertyEditor(
        label = "Letter Spacing",
        value = InspectorUtils.parsePixelValue(styles["letter-spacing"]),
        range = -2f..10f,
        unit = "px",
        onValueChange = { newValue ->
            onStyleChange(StyleChangeRequest(
                selector = selector,
                property = "letter-spacing",
                oldValue = styles["letter-spacing"],
                newValue = "${newValue.toInt()}px",
                elementHtml = elementHtml
            ))
        }
    )
}
