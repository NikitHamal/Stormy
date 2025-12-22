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
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.components.inspector.BoxShadowValues
import com.codex.stormy.ui.components.inspector.ColorPropertyEditor
import com.codex.stormy.ui.components.inspector.InspectorElementData
import com.codex.stormy.ui.components.inspector.InspectorSection
import com.codex.stormy.ui.components.inspector.InspectorUtils
import com.codex.stormy.ui.components.inspector.OptionChip
import com.codex.stormy.ui.components.inspector.SliderPropertyEditor
import com.codex.stormy.ui.components.inspector.StyleChangeRequest

/**
 * Design tab for the Visual Element Inspector.
 * Handles colors, backgrounds, borders, and visual effects.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignTab(
    element: InspectorElementData,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    val styles = element.computedStyles
    val selector = InspectorUtils.buildSelector(element)

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
            BorderSection(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }

        // Effects section
        InspectorSection(
            title = "Effects",
            icon = Icons.Outlined.Layers
        ) {
            EffectsSection(
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
private fun BorderSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    // Border radius
    SliderPropertyEditor(
        label = "Border Radius",
        value = InspectorUtils.parsePixelValue(styles["border-radius"]),
        range = 0f..50f,
        unit = "px",
        onValueChange = { newValue ->
            onStyleChange(StyleChangeRequest(
                selector = selector,
                property = "border-radius",
                oldValue = styles["border-radius"],
                newValue = "${newValue.toInt()}px",
                elementHtml = elementHtml
            ))
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Border width
    SliderPropertyEditor(
        label = "Border Width",
        value = InspectorUtils.parsePixelValue(styles["border-width"]),
        range = 0f..10f,
        unit = "px",
        onValueChange = { newValue ->
            onStyleChange(StyleChangeRequest(
                selector = selector,
                property = "border-width",
                oldValue = styles["border-width"],
                newValue = "${newValue.toInt()}px",
                elementHtml = elementHtml
            ))
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Border color
    ColorPropertyEditor(
        label = "Border Color",
        value = styles["border-color"] ?: "transparent",
        onValueChange = { newValue ->
            onStyleChange(StyleChangeRequest(
                selector = selector,
                property = "border-color",
                oldValue = styles["border-color"],
                newValue = newValue,
                elementHtml = elementHtml
            ))
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Border style options
    val borderStyleOptions = listOf("none", "solid", "dashed", "dotted")
    val currentBorderStyle = styles["border-style"] ?: "none"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Border Style",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            borderStyleOptions.forEach { option ->
                OptionChip(
                    label = option,
                    isSelected = currentBorderStyle == option,
                    onClick = {
                        onStyleChange(StyleChangeRequest(
                            selector = selector,
                            property = "border-style",
                            oldValue = currentBorderStyle,
                            newValue = option,
                            elementHtml = elementHtml
                        ))
                    }
                )
            }
        }
    }
}

@Composable
private fun EffectsSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
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
                elementHtml = elementHtml
            ))
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Box Shadow",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(8.dp))

    BoxShadowEditors(
        boxShadow = styles["box-shadow"],
        selector = selector,
        elementHtml = elementHtml,
        onStyleChange = onStyleChange
    )
}

@Composable
private fun BoxShadowEditors(
    boxShadow: String?,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    val shadowValues = BoxShadowValues.parse(boxShadow)

    // Shadow X offset
    SliderPropertyEditor(
        label = "Shadow X",
        value = shadowValues.x.toFloat(),
        range = -20f..20f,
        unit = "px",
        onValueChange = { newValue ->
            val newShadow = shadowValues.copy(x = newValue.toInt()).toCssString()
            onStyleChange(StyleChangeRequest(
                selector = selector,
                property = "box-shadow",
                oldValue = boxShadow,
                newValue = newShadow,
                elementHtml = elementHtml
            ))
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Shadow Y offset
    SliderPropertyEditor(
        label = "Shadow Y",
        value = shadowValues.y.toFloat(),
        range = -20f..20f,
        unit = "px",
        onValueChange = { newValue ->
            val newShadow = shadowValues.copy(y = newValue.toInt()).toCssString()
            onStyleChange(StyleChangeRequest(
                selector = selector,
                property = "box-shadow",
                oldValue = boxShadow,
                newValue = newShadow,
                elementHtml = elementHtml
            ))
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Shadow blur
    SliderPropertyEditor(
        label = "Shadow Blur",
        value = shadowValues.blur.toFloat(),
        range = 0f..50f,
        unit = "px",
        onValueChange = { newValue ->
            val newShadow = shadowValues.copy(blur = newValue.toInt()).toCssString()
            onStyleChange(StyleChangeRequest(
                selector = selector,
                property = "box-shadow",
                oldValue = boxShadow,
                newValue = newShadow,
                elementHtml = elementHtml
            ))
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Shadow spread
    SliderPropertyEditor(
        label = "Shadow Spread",
        value = shadowValues.spread.toFloat(),
        range = -10f..20f,
        unit = "px",
        onValueChange = { newValue ->
            val newShadow = shadowValues.copy(spread = newValue.toInt()).toCssString()
            onStyleChange(StyleChangeRequest(
                selector = selector,
                property = "box-shadow",
                oldValue = boxShadow,
                newValue = newShadow,
                elementHtml = elementHtml
            ))
        }
    )
}
