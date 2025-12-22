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
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.components.inspector.BoxShadowValues
import com.codex.stormy.ui.components.inspector.ColorPropertyEditor
import com.codex.stormy.ui.components.inspector.InspectorElementData
import com.codex.stormy.ui.components.inspector.InspectorSection
import com.codex.stormy.ui.components.inspector.InspectorUtils
import com.codex.stormy.ui.components.inspector.OptionChip
import com.codex.stormy.ui.components.inspector.SliderPropertyEditor
import com.codex.stormy.ui.components.inspector.StyleChangeRequest
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * Design tab for the Visual Element Inspector.
 * Handles colors, backgrounds, borders, and visual effects.
 *
 * Production-grade implementation with comprehensive styling options,
 * proper layout constraints, and Material 3 design patterns.
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Colors section
        InspectorSection(
            title = "Colors",
            icon = Icons.Outlined.Palette
        ) {
            ColorsSection(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }

        // Background section
        InspectorSection(
            title = "Background",
            icon = Icons.Outlined.Gradient
        ) {
            BackgroundSection(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }

        // Border section
        InspectorSection(
            title = "Border",
            icon = Icons.Outlined.RoundedCorner
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

        // Filters section
        InspectorSection(
            title = "Filters",
            icon = Icons.Outlined.FilterBAndW,
            initiallyExpanded = false
        ) {
            FiltersSection(
                styles = styles,
                selector = selector,
                elementHtml = element.outerHTML,
                onStyleChange = onStyleChange
            )
        }
    }
}

/**
 * Colors section for text and background colors.
 */
@Composable
private fun ColorsSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ColorPropertyEditor(
            label = "Text Color",
            value = styles["color"] ?: "inherit",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "color",
                        oldValue = styles["color"],
                        newValue = newValue,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        ColorPropertyEditor(
            label = "Background Color",
            value = styles["background-color"] ?: "transparent",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "background-color",
                        oldValue = styles["background-color"],
                        newValue = newValue,
                        elementHtml = elementHtml
                    )
                )
            }
        )
    }
}

/**
 * Background section for advanced background properties.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Background Size options
        val bgSizeOptions = listOf("auto", "cover", "contain")
        val currentBgSize = styles["background-size"] ?: "auto"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Background Size",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bgSizeOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentBgSize == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "background-size",
                                    oldValue = currentBgSize,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Background Position options
        val bgPositionOptions = listOf("center", "top", "bottom", "left", "right")
        val currentBgPosition = styles["background-position"] ?: "center"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Background Position",
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
                bgPositionOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentBgPosition.contains(option),
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "background-position",
                                    oldValue = currentBgPosition,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Background Repeat options
        val bgRepeatOptions = listOf("no-repeat", "repeat", "repeat-x", "repeat-y")
        val currentBgRepeat = styles["background-repeat"] ?: "no-repeat"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Background Repeat",
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
                bgRepeatOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentBgRepeat == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "background-repeat",
                                    oldValue = currentBgRepeat,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Background Attachment
        val bgAttachmentOptions = listOf("scroll", "fixed", "local")
        val currentBgAttachment = styles["background-attachment"] ?: "scroll"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Background Attachment",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bgAttachmentOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentBgAttachment == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "background-attachment",
                                    oldValue = currentBgAttachment,
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
 * Border section with radius, width, color, and style controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BorderSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Border Radius
        SliderPropertyEditor(
            label = "Border Radius",
            value = InspectorUtils.parsePixelValue(styles["border-radius"]),
            range = 0f..100f,
            unit = "px",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "border-radius",
                        oldValue = styles["border-radius"],
                        newValue = "${newValue.toInt()}px",
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Border Width
        SliderPropertyEditor(
            label = "Border Width",
            value = InspectorUtils.parsePixelValue(styles["border-width"]),
            range = 0f..20f,
            unit = "px",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "border-width",
                        oldValue = styles["border-width"],
                        newValue = "${newValue.toInt()}px",
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Border Color
        ColorPropertyEditor(
            label = "Border Color",
            value = styles["border-color"] ?: "transparent",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "border-color",
                        oldValue = styles["border-color"],
                        newValue = newValue,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Border Style options
        val borderStyleOptions = listOf("none", "solid", "dashed", "dotted", "double", "groove")
        val currentBorderStyle = styles["border-style"] ?: "none"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Border Style",
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
                borderStyleOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentBorderStyle == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "border-style",
                                    oldValue = currentBorderStyle,
                                    newValue = option,
                                    elementHtml = elementHtml
                                )
                            )
                        }
                    )
                }
            }
        }

        // Outline section
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        Text(
            text = "Outline",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        SliderPropertyEditor(
            label = "Outline Width",
            value = InspectorUtils.parsePixelValue(styles["outline-width"]),
            range = 0f..10f,
            unit = "px",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "outline-width",
                        oldValue = styles["outline-width"],
                        newValue = "${newValue.toInt()}px",
                        elementHtml = elementHtml
                    )
                )
            }
        )

        ColorPropertyEditor(
            label = "Outline Color",
            value = styles["outline-color"] ?: "transparent",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "outline-color",
                        oldValue = styles["outline-color"],
                        newValue = newValue,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        SliderPropertyEditor(
            label = "Outline Offset",
            value = InspectorUtils.parsePixelValue(styles["outline-offset"]),
            range = 0f..20f,
            unit = "px",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "outline-offset",
                        oldValue = styles["outline-offset"],
                        newValue = "${newValue.toInt()}px",
                        elementHtml = elementHtml
                    )
                )
            }
        )
    }
}

/**
 * Effects section with opacity and box shadow controls.
 */
@Composable
private fun EffectsSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Opacity
        SliderPropertyEditor(
            label = "Opacity",
            value = (styles["opacity"]?.toFloatOrNull() ?: 1f) * 100f,
            range = 0f..100f,
            unit = "%",
            onValueChange = { newValue ->
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "opacity",
                        oldValue = styles["opacity"],
                        newValue = String.format("%.2f", newValue / 100f),
                        elementHtml = elementHtml
                    )
                )
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        // Box Shadow section
        Text(
            text = "Box Shadow",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        BoxShadowEditors(
            boxShadow = styles["box-shadow"],
            selector = selector,
            elementHtml = elementHtml,
            onStyleChange = onStyleChange
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        // Cursor options
        val cursorOptions = listOf("auto", "pointer", "text", "move", "not-allowed", "grab")
        val currentCursor = styles["cursor"] ?: "auto"

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Cursor",
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
                cursorOptions.forEach { option ->
                    OptionChip(
                        label = option,
                        isSelected = currentCursor == option,
                        onClick = {
                            onStyleChange(
                                StyleChangeRequest(
                                    selector = selector,
                                    property = "cursor",
                                    oldValue = currentCursor,
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
 * Box shadow editors for X, Y, blur, spread, and color.
 */
@Composable
private fun BoxShadowEditors(
    boxShadow: String?,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    val shadowValues = BoxShadowValues.parse(boxShadow)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Shadow X offset
        SliderPropertyEditor(
            label = "Offset X",
            value = shadowValues.x.toFloat(),
            range = -30f..30f,
            unit = "px",
            onValueChange = { newValue ->
                val newShadow = shadowValues.copy(x = newValue.toInt()).toCssString()
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "box-shadow",
                        oldValue = boxShadow,
                        newValue = newShadow,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Shadow Y offset
        SliderPropertyEditor(
            label = "Offset Y",
            value = shadowValues.y.toFloat(),
            range = -30f..30f,
            unit = "px",
            onValueChange = { newValue ->
                val newShadow = shadowValues.copy(y = newValue.toInt()).toCssString()
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "box-shadow",
                        oldValue = boxShadow,
                        newValue = newShadow,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Shadow blur
        SliderPropertyEditor(
            label = "Blur",
            value = shadowValues.blur.toFloat(),
            range = 0f..80f,
            unit = "px",
            onValueChange = { newValue ->
                val newShadow = shadowValues.copy(blur = newValue.toInt()).toCssString()
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "box-shadow",
                        oldValue = boxShadow,
                        newValue = newShadow,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Shadow spread
        SliderPropertyEditor(
            label = "Spread",
            value = shadowValues.spread.toFloat(),
            range = -20f..40f,
            unit = "px",
            onValueChange = { newValue ->
                val newShadow = shadowValues.copy(spread = newValue.toInt()).toCssString()
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "box-shadow",
                        oldValue = boxShadow,
                        newValue = newShadow,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Shadow color
        ColorPropertyEditor(
            label = "Shadow Color",
            value = shadowValues.color,
            onValueChange = { newValue ->
                val newShadow = shadowValues.copy(color = newValue).toCssString()
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "box-shadow",
                        oldValue = boxShadow,
                        newValue = newShadow,
                        elementHtml = elementHtml
                    )
                )
            }
        )
    }
}

/**
 * CSS Filters section for blur, brightness, contrast, etc.
 */
@Composable
private fun FiltersSection(
    styles: Map<String, String>,
    selector: String,
    elementHtml: String,
    onStyleChange: (StyleChangeRequest) -> Unit
) {
    val filterString = styles["filter"] ?: "none"
    val filterValues = parseFilterValues(filterString)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Blur
        SliderPropertyEditor(
            label = "Blur",
            value = filterValues["blur"] ?: 0f,
            range = 0f..20f,
            unit = "px",
            onValueChange = { newValue ->
                val newFilter = buildFilterString(filterValues + ("blur" to newValue))
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "filter",
                        oldValue = filterString,
                        newValue = newFilter,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Brightness
        SliderPropertyEditor(
            label = "Brightness",
            value = (filterValues["brightness"] ?: 100f),
            range = 0f..200f,
            unit = "%",
            onValueChange = { newValue ->
                val newFilter = buildFilterString(filterValues + ("brightness" to newValue))
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "filter",
                        oldValue = filterString,
                        newValue = newFilter,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Contrast
        SliderPropertyEditor(
            label = "Contrast",
            value = (filterValues["contrast"] ?: 100f),
            range = 0f..200f,
            unit = "%",
            onValueChange = { newValue ->
                val newFilter = buildFilterString(filterValues + ("contrast" to newValue))
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "filter",
                        oldValue = filterString,
                        newValue = newFilter,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Grayscale
        SliderPropertyEditor(
            label = "Grayscale",
            value = (filterValues["grayscale"] ?: 0f),
            range = 0f..100f,
            unit = "%",
            onValueChange = { newValue ->
                val newFilter = buildFilterString(filterValues + ("grayscale" to newValue))
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "filter",
                        oldValue = filterString,
                        newValue = newFilter,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Saturate
        SliderPropertyEditor(
            label = "Saturate",
            value = (filterValues["saturate"] ?: 100f),
            range = 0f..200f,
            unit = "%",
            onValueChange = { newValue ->
                val newFilter = buildFilterString(filterValues + ("saturate" to newValue))
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "filter",
                        oldValue = filterString,
                        newValue = newFilter,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Hue Rotate
        SliderPropertyEditor(
            label = "Hue Rotate",
            value = (filterValues["hue-rotate"] ?: 0f),
            range = 0f..360f,
            unit = "px",  // Actually degrees but using px for display
            onValueChange = { newValue ->
                val newFilter = buildFilterString(filterValues + ("hue-rotate" to newValue))
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "filter",
                        oldValue = filterString,
                        newValue = newFilter,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Sepia
        SliderPropertyEditor(
            label = "Sepia",
            value = (filterValues["sepia"] ?: 0f),
            range = 0f..100f,
            unit = "%",
            onValueChange = { newValue ->
                val newFilter = buildFilterString(filterValues + ("sepia" to newValue))
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "filter",
                        oldValue = filterString,
                        newValue = newFilter,
                        elementHtml = elementHtml
                    )
                )
            }
        )

        // Invert
        SliderPropertyEditor(
            label = "Invert",
            value = (filterValues["invert"] ?: 0f),
            range = 0f..100f,
            unit = "%",
            onValueChange = { newValue ->
                val newFilter = buildFilterString(filterValues + ("invert" to newValue))
                onStyleChange(
                    StyleChangeRequest(
                        selector = selector,
                        property = "filter",
                        oldValue = filterString,
                        newValue = newFilter,
                        elementHtml = elementHtml
                    )
                )
            }
        )
    }
}

/**
 * Parse CSS filter string into a map of filter names and values.
 */
private fun parseFilterValues(filterString: String): Map<String, Float> {
    if (filterString == "none" || filterString.isBlank()) {
        return emptyMap()
    }

    val result = mutableMapOf<String, Float>()
    val filterRegex = Regex("""(\w+(?:-\w+)?)\(([^)]+)\)""")

    filterRegex.findAll(filterString).forEach { match ->
        val filterName = match.groupValues[1]
        val valueString = match.groupValues[2]

        val value = when {
            valueString.endsWith("px") -> valueString.removeSuffix("px").toFloatOrNull()
            valueString.endsWith("%") -> valueString.removeSuffix("%").toFloatOrNull()
            valueString.endsWith("deg") -> valueString.removeSuffix("deg").toFloatOrNull()
            else -> valueString.toFloatOrNull()?.times(100) // For decimal values like 1.0 -> 100%
        }

        if (value != null) {
            result[filterName] = value
        }
    }

    return result
}

/**
 * Build CSS filter string from a map of filter values.
 */
private fun buildFilterString(filters: Map<String, Float>): String {
    val nonDefaultFilters = filters.filter { (name, value) ->
        when (name) {
            "blur" -> value > 0
            "brightness", "contrast", "saturate" -> value != 100f
            "grayscale", "sepia", "invert" -> value > 0
            "hue-rotate" -> value > 0
            else -> false
        }
    }

    if (nonDefaultFilters.isEmpty()) {
        return "none"
    }

    return nonDefaultFilters.map { (name, value) ->
        when (name) {
            "blur" -> "blur(${value.toInt()}px)"
            "hue-rotate" -> "hue-rotate(${value.toInt()}deg)"
            "brightness", "contrast", "saturate" -> "$name(${(value / 100).let { "%.2f".format(it) }})"
            else -> "$name(${value.toInt()}%)"
        }
    }.joinToString(" ")
}
