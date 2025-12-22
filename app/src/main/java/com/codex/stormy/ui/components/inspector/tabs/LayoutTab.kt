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
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.components.inspector.InspectorElementData
import com.codex.stormy.ui.components.inspector.InspectorSection
import com.codex.stormy.ui.components.inspector.InspectorUtils
import com.codex.stormy.ui.components.inspector.OptionChip
import com.codex.stormy.ui.components.inspector.SliderPropertyEditor
import com.codex.stormy.ui.components.inspector.SpacingBoxEditor
import com.codex.stormy.ui.components.inspector.StyleChangeRequest
import com.codex.stormy.ui.components.inspector.TextInputProperty
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * Layout tab for the Visual Element Inspector.
 * Handles size, spacing (margin/padding), display, position, and overflow properties.
 *
 * Production-grade implementation with comprehensive layout options,
 * proper constraints, and Material 3 design patterns.
 */
@OptIn(ExperimentalLayoutApi::class)
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Size section
        InspectorSection(
            title = "Size",
            icon = Icons.Outlined.Height
        ) {
            SizeSection(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }

        // Spacing section
        InspectorSection(
            title = "Spacing",
            icon = Icons.Outlined.SpaceBar
        ) {
            SpacingSection(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }

        // Display & Flexbox section
        InspectorSection(
            title = "Display & Flex",
            icon = Icons.Outlined.GridOn
        ) {
            DisplaySection(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }

        // Position section
        InspectorSection(
            title = "Position",
            icon = Icons.Outlined.Crop
        ) {
            PositionSection(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }

        // Overflow section
        InspectorSection(
            title = "Overflow",
            icon = Icons.Outlined.Visibility,
            initiallyExpanded = false
        ) {
            OverflowSection(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }
    }
}

/**
 * Size section with width, height, and constraints.
 */
@Composable
private fun SizeSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Width and Height row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextInputProperty(
                label = "Width",
                value = styles["width"] ?: "auto",
                onValueChange = { newValue ->
                    onStyleChange(
                        StyleChangeRequest(
                            selector = selector,
                            property = "width",
                            oldValue = styles["width"],
                            newValue = newValue,
                            elementHtml = elementHtml
                        )
                    )
                },
                placeholder = "auto",
                modifier = Modifier.weight(1f)
            )

            TextInputProperty(
                label = "Height",
                value = styles["height"] ?: "auto",
                onValueChange = { newValue ->
                    onStyleChange(
                        StyleChangeRequest(
                            selector = selector,
                            property = "height",
                            oldValue = styles["height"],
                            newValue = newValue,
                            elementHtml = elementHtml
                        )
                    )
                },
                placeholder = "auto",
                modifier = Modifier.weight(1f)
            )
        }

        // Min Width and Min Height
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextInputProperty(
                label = "Min Width",
                value = styles["min-width"] ?: "0",
                onValueChange = { newValue ->
                    onStyleChange(
                        StyleChangeRequest(
                            selector = selector,
                            property = "min-width",
                            oldValue = styles["min-width"],
                            newValue = newValue,
                            elementHtml = elementHtml
                        )
                    )
                },
                placeholder = "0",
                modifier = Modifier.weight(1f)
            )

            TextInputProperty(
                label = "Min Height",
                value = styles["min-height"] ?: "0",
                onValueChange = { newValue ->
                    onStyleChange(
                        StyleChangeRequest(
                            selector = selector,
                            property = "min-height",
                            oldValue = styles["min-height"],
                            newValue = newValue,
                            elementHtml = elementHtml
                        )
                    )
                },
                placeholder = "0",
                modifier = Modifier.weight(1f)
            )
        }

        // Max Width and Max Height
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextInputProperty(
                label = "Max Width",
                value = styles["max-width"] ?: "none",
                onValueChange = { newValue ->
                    onStyleChange(
                        StyleChangeRequest(
                            selector = selector,
                            property = "max-width",
                            oldValue = styles["max-width"],
                            newValue = newValue,
                            elementHtml = elementHtml
                        )
                    )
                },
                placeholder = "none",
                modifier = Modifier.weight(1f)
            )

            TextInputProperty(
                label = "Max Height",
                value = styles["max-height"] ?: "none",
                onValueChange = { newValue ->
                    onStyleChange(
                        StyleChangeRequest(
                            selector = selector,
                            property = "max-height",
                            oldValue = styles["max-height"],
                            newValue = newValue,
                            elementHtml = elementHtml
                        )
                    )
                },
                placeholder = "none",
                modifier = Modifier.weight(1f)
            )
        }

        // Box Sizing
        val boxSizingOptions = listOf("content-box", "border-box")
        val currentBoxSizing = styles["box-sizing"] ?: "content-box"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Box Sizing",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                boxSizingOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentBoxSizing == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "box-sizing",
                                    oldValue = currentBoxSizing,
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
 * Spacing section with margin and padding controls.
 */
@Composable
private fun SpacingSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Margin box editor
        SpacingBoxEditor(
            label = "Margin",
            top = InspectorUtils.parsePixelValue(styles["margin-top"]),
            right = InspectorUtils.parsePixelValue(styles["margin-right"]),
            bottom = InspectorUtils.parsePixelValue(styles["margin-bottom"]),
            left = InspectorUtils.parsePixelValue(styles["margin-left"]),
            onValueChange = { property, value ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = property,
                        oldValue = styles[property],
                        newValue = "${value.toInt()}px",
                        elementHtml = elementHtml
                    )
                )
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        // Padding box editor
        SpacingBoxEditor(
            label = "Padding",
            top = InspectorUtils.parsePixelValue(styles["padding-top"]),
            right = InspectorUtils.parsePixelValue(styles["padding-right"]),
            bottom = InspectorUtils.parsePixelValue(styles["padding-bottom"]),
            left = InspectorUtils.parsePixelValue(styles["padding-left"]),
            onValueChange = { property, value ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = property,
                        oldValue = styles[property],
                        newValue = "${value.toInt()}px",
                        elementHtml = elementHtml
                    )
                )
            }
        )
    }
}

/**
 * Display section with display type and flexbox controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplaySection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    val currentDisplay = styles["display"] ?: "block"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Display type options
        val displayOptions = listOf("block", "inline-block", "inline", "flex", "grid", "none")

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Display Type",
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
                displayOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentDisplay == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "display",
                                    oldValue = currentDisplay,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Flexbox controls - only show when display is flex
        if (currentDisplay == "flex" || currentDisplay == "inline-flex") {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            Text(
                text = "Flexbox",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Flex Direction
            val flexDirectionOptions = listOf("row", "row-reverse", "column", "column-reverse")
            val currentFlexDirection = styles["flex-direction"] ?: "row"

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Direction",
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
                    flexDirectionOptions.forEach { option ->
                        OptionChip(
                            label = option,
                            isSelected = currentFlexDirection == option,
                            onClick = {
                                onStyleChange(
                                    StyleChangeRequest(
                                        selector = selector,
                                        property = "flex-direction",
                                        oldValue = currentFlexDirection,
                                        newValue = option,
                                        elementHtml = elementHtml
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Flex Wrap
            val flexWrapOptions = listOf("nowrap", "wrap", "wrap-reverse")
            val currentFlexWrap = styles["flex-wrap"] ?: "nowrap"

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Wrap",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    flexWrapOptions.forEach { option ->
                        OptionChip(
                            label = option,
                            isSelected = currentFlexWrap == option,
                            onClick = {
                                onStyleChange(
                                    StyleChangeRequest(
                                        selector = selector,
                                        property = "flex-wrap",
                                        oldValue = currentFlexWrap,
                                        newValue = option,
                                        elementHtml = elementHtml
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Justify Content
            val justifyContentOptions = listOf("flex-start", "center", "flex-end", "space-between", "space-around", "space-evenly")
            val currentJustifyContent = styles["justify-content"] ?: "flex-start"

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Justify Content",
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
                    justifyContentOptions.forEach { option ->
                        OptionChip(
                            label = option,
                            isSelected = currentJustifyContent == option,
                            onClick = {
                                onStyleChange(
                                    StyleChangeRequest(
                                        selector = selector,
                                        property = "justify-content",
                                        oldValue = currentJustifyContent,
                                        newValue = option,
                                        elementHtml = elementHtml
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Align Items
            val alignItemsOptions = listOf("stretch", "flex-start", "center", "flex-end", "baseline")
            val currentAlignItems = styles["align-items"] ?: "stretch"

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Align Items",
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
                    alignItemsOptions.forEach { option ->
                        OptionChip(
                            label = option,
                            isSelected = currentAlignItems == option,
                            onClick = {
                                onStyleChange(
                                    StyleChangeRequest(
                                        selector = selector,
                                        property = "align-items",
                                        oldValue = currentAlignItems,
                                        newValue = option,
                                        elementHtml = elementHtml
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Gap
            SliderPropertyEditor(
                label = "Gap",
                value = InspectorUtils.parsePixelValue(styles["gap"]),
                range = 0f..100f,
                unit = "px",
                onValueChange = { newValue ->
                    onStyleChange(
                        StyleChangeRequest(
                            selector = selector,
                            property = "gap",
                            oldValue = styles["gap"],
                            newValue = "${newValue.toInt()}px",
                            elementHtml = elementHtml
                        )
                    )
                }
            )
        }

        // Grid controls - only show when display is grid
        if (currentDisplay == "grid" || currentDisplay == "inline-grid") {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            Text(
                text = "Grid",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            TextInputProperty(
                label = "Grid Template Columns",
                value = styles["grid-template-columns"] ?: "none",
                onValueChange = { newValue ->
                    onStyleChange(
                        StyleChangeRequest(
                            selector = selector,
                            property = "grid-template-columns",
                            oldValue = styles["grid-template-columns"],
                            newValue = newValue,
                            elementHtml = elementHtml
                        )
                    )
                },
                placeholder = "1fr 1fr 1fr"
            )

            TextInputProperty(
                label = "Grid Template Rows",
                value = styles["grid-template-rows"] ?: "none",
                onValueChange = { newValue ->
                    onStyleChange(
                        StyleChangeRequest(
                            selector = selector,
                            property = "grid-template-rows",
                            oldValue = styles["grid-template-rows"],
                            newValue = newValue,
                            elementHtml = elementHtml
                        )
                    )
                },
                placeholder = "auto"
            )

            SliderPropertyEditor(
                label = "Grid Gap",
                value = InspectorUtils.parsePixelValue(styles["grid-gap"] ?: styles["gap"]),
                range = 0f..100f,
                unit = "px",
                onValueChange = { newValue ->
                    onStyleChange(
                        StyleChangeRequest(
                            selector = selector,
                            property = "gap",
                            oldValue = styles["gap"],
                            newValue = "${newValue.toInt()}px",
                            elementHtml = elementHtml
                        )
                    )
                }
            )
        }
    }
}

/**
 * Position section with positioning mode and offset controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PositionSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    val currentPosition = styles["position"] ?: "static"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Position type
        val positionOptions = listOf("static", "relative", "absolute", "fixed", "sticky")

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Position Type",
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
                positionOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentPosition == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "position",
                                    oldValue = currentPosition,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Position offsets - only show when position is not static
        if (currentPosition != "static") {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            Text(
                text = "Offsets",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextInputProperty(
                    label = "Top",
                    value = styles["top"] ?: "auto",
                    onValueChange = { newValue ->
                        onStyleChange(
                            StyleChangeRequest(
                                selector = selector,
                                property = "top",
                                oldValue = styles["top"],
                                newValue = newValue,
                                elementHtml = elementHtml
                            )
                        )
                    },
                    placeholder = "auto",
                    modifier = Modifier.weight(1f)
                )

                TextInputProperty(
                    label = "Right",
                    value = styles["right"] ?: "auto",
                    onValueChange = { newValue ->
                        onStyleChange(
                            StyleChangeRequest(
                                selector = selector,
                                property = "right",
                                oldValue = styles["right"],
                                newValue = newValue,
                                elementHtml = elementHtml
                            )
                        )
                    },
                    placeholder = "auto",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextInputProperty(
                    label = "Bottom",
                    value = styles["bottom"] ?: "auto",
                    onValueChange = { newValue ->
                        onStyleChange(
                            StyleChangeRequest(
                                selector = selector,
                                property = "bottom",
                                oldValue = styles["bottom"],
                                newValue = newValue,
                                elementHtml = elementHtml
                            )
                        )
                    },
                    placeholder = "auto",
                    modifier = Modifier.weight(1f)
                )

                TextInputProperty(
                    label = "Left",
                    value = styles["left"] ?: "auto",
                    onValueChange = { newValue ->
                        onStyleChange(
                            StyleChangeRequest(
                                selector = selector,
                                property = "left",
                                oldValue = styles["left"],
                                newValue = newValue,
                                elementHtml = elementHtml
                            )
                        )
                    },
                    placeholder = "auto",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Z-Index
        SliderPropertyEditor(
            label = "Z-Index",
            value = styles["z-index"]?.toFloatOrNull() ?: 0f,
            range = -10f..100f,
            unit = "px",  // No unit for z-index but using px for consistency
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "z-index",
                        oldValue = styles["z-index"],
                        newValue = newValue.toInt().toString(),
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Float
        val floatOptions = listOf("none", "left", "right")
        val currentFloat = styles["float"] ?: "none"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Float",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                floatOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentFloat == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "float",
                                    oldValue = currentFloat,
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
 * Overflow section with overflow controls.
 */
@Composable
private fun OverflowSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Overflow
        val overflowOptions = listOf("visible", "hidden", "scroll", "auto")
        val currentOverflow = styles["overflow"] ?: "visible"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Overflow",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                overflowOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentOverflow == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "overflow",
                                    oldValue = currentOverflow,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Overflow X
        val currentOverflowX = styles["overflow-x"] ?: "visible"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Overflow X",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                overflowOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentOverflowX == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "overflow-x",
                                    oldValue = currentOverflowX,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Overflow Y
        val currentOverflowY = styles["overflow-y"] ?: "visible"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Overflow Y",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                overflowOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentOverflowY == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "overflow-y",
                                    oldValue = currentOverflowY,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Object Fit (for images and videos)
        val objectFitOptions = listOf("fill", "contain", "cover", "none", "scale-down")
        val currentObjectFit = styles["object-fit"] ?: "fill"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Object Fit",
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
                objectFitOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentObjectFit == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "object-fit",
                                    oldValue = currentObjectFit,
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
