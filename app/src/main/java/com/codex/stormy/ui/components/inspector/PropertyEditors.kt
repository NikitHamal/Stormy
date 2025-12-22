package com.codex.stormy.ui.components.inspector

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * Property editor composables for the Visual Element Inspector.
 * Reusable UI components for editing CSS properties.
 *
 * Production-grade implementation with proper layout constraints,
 * accessibility support, and Material 3 design patterns.
 */

/**
 * Color property editor with preview swatch, label, and editable input.
 * Features proper vertical layout to prevent text overlap.
 */
@Composable
fun ColorPropertyEditor(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editedValue by remember(value) { mutableStateOf(value) }
    val parsedColor = InspectorUtils.parseColor(value)
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Label row
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Input row with color swatch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Color preview swatch
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(parsedColor)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    )
            )

            // Text input field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = editedValue,
                    onValueChange = { editedValue = it },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (editedValue != value) {
                            onValueChange(editedValue)
                        }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Copy button
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(editedValue)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy color value",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Slider property editor for numeric values.
 * Features proper layout with label above and value indicator that doesn't overlap.
 */
@Composable
fun SliderPropertyEditor(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    val animatedValue by animateFloatAsState(
        targetValue = sliderValue,
        label = "slider_value_animation"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Label and value row - separate line to prevent overlap
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label with flex grow
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Value display with minimum width to prevent overlap
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.widthIn(min = 48.dp)
            ) {
                Text(
                    text = formatSliderValue(animatedValue, unit),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Slider on separate line
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
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        )
    }
}

/**
 * Format slider value for display based on unit type.
 */
private fun formatSliderValue(value: Float, unit: String): String {
    return when (unit) {
        "x" -> String.format("%.1f", value) + unit
        "%" -> String.format("%.0f", value) + unit
        else -> "${value.toInt()}$unit"
    }
}

/**
 * Text input property editor with label above input.
 * Clean vertical layout for CSS property values.
 */
@Composable
fun TextInputProperty(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    var editedValue by remember(value) { mutableStateOf(value) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = editedValue,
                onValueChange = { editedValue = it },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (editedValue != value) {
                        onValueChange(editedValue)
                    }
                }),
                decorationBox = { innerTextField ->
                    Box {
                        if (editedValue.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Spacing box editor for margin/padding with visual box model representation.
 * Features clean layout with proper spacing between elements.
 */
@Composable
fun SpacingBoxEditor(
    label: String,
    top: Float,
    right: Float,
    bottom: Float,
    left: Float,
    onValueChange: (String, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefix = label.lowercase()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Top input
        SpacingInput(
            value = top,
            onValueChange = { onValueChange("$prefix-top", it) }
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Middle row: Left - Box - Right
        Row(
            modifier = Modifier.width(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left input
            SpacingInput(
                value = left,
                onValueChange = { onValueChange("$prefix-left", it) }
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Center box (element representation)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    )
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Right input
            SpacingInput(
                value = right,
                onValueChange = { onValueChange("$prefix-right", it) }
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Bottom input
        SpacingInput(
            value = bottom,
            onValueChange = { onValueChange("$prefix-bottom", it) }
        )
    }
}

/**
 * Individual spacing input field with consistent sizing.
 */
@Composable
private fun SpacingInput(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    var editedValue by remember(value) { mutableStateOf(value.toInt().toString()) }

    Box(
        modifier = Modifier
            .width(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = editedValue,
            onValueChange = { newValue ->
                editedValue = newValue.filter { c -> c.isDigit() || c == '-' }
            },
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                val newFloat = editedValue.toFloatOrNull() ?: 0f
                if (newFloat != value) {
                    onValueChange(newFloat)
                }
            }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Option chip for selecting from predefined values.
 * Material 3 styled with proper selection states.
 */
@Composable
fun OptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "chip_bg_animation"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chip_text_animation"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/**
 * Text content editor with multi-line support and apply button.
 */
@Composable
fun TextContentEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Text input area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontFamily = PoppinsFontFamily,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Apply button
        Surface(
            onClick = onApply,
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Apply",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Toggle switch style property editor for boolean CSS values.
 */
@Composable
fun TogglePropertyEditor(
    label: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "toggle_bg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onToggle(!isEnabled) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = PoppinsFontFamily,
            color = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isEnabled) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/**
 * Dual slider for range values (e.g., min/max width).
 */
@Composable
fun DualValueEditor(
    label: String,
    value1Label: String,
    value1: String,
    value2Label: String,
    value2: String,
    onValue1Change: (String) -> Unit,
    onValue2Change: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextInputProperty(
                label = value1Label,
                value = value1,
                onValueChange = onValue1Change,
                modifier = Modifier.weight(1f)
            )

            TextInputProperty(
                label = value2Label,
                value = value2,
                onValueChange = onValue2Change,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
