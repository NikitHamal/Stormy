package com.codex.stormy.ui.components.inspector.tabs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.components.inspector.InspectorElementData
import com.codex.stormy.ui.components.inspector.InspectorSection
import com.codex.stormy.ui.components.inspector.InspectorUtils
import com.codex.stormy.ui.components.inspector.OptionChip
import com.codex.stormy.ui.components.inspector.SpacingBoxEditor
import com.codex.stormy.ui.components.inspector.StyleChangeRequest
import com.codex.stormy.ui.components.inspector.TextInputProperty

/**
 * Layout tab for the Visual Element Inspector.
 * Handles size, spacing (margin/padding), and display properties.
 */
@Composable
fun LayoutTab(
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
                top = InspectorUtils.parsePixelValue(styles["margin-top"]),
                right = InspectorUtils.parsePixelValue(styles["margin-right"]),
                bottom = InspectorUtils.parsePixelValue(styles["margin-bottom"]),
                left = InspectorUtils.parsePixelValue(styles["margin-left"]),
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
                top = InspectorUtils.parsePixelValue(styles["padding-top"]),
                right = InspectorUtils.parsePixelValue(styles["padding-right"]),
                bottom = InspectorUtils.parsePixelValue(styles["padding-bottom"]),
                left = InspectorUtils.parsePixelValue(styles["padding-left"]),
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
