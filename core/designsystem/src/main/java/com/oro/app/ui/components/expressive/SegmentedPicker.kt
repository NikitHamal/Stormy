package com.oro.app.ui.components.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space

/** One option in a [SegmentedPicker]. */
data class PickerOption<T>(
    val value: T,
    val label: String
)

/**
 * A labelled segmented control: a track with the selected option filled.
 *
 * Distinct from [ExpressiveToggleGroup], which wraps Material's ButtonGroup and
 * can overflow into a menu. This one never overflows -- every option stays
 * visible and the segments share the width equally -- which is the right
 * behaviour for a small, fixed set where the *range* is part of the meaning.
 * Seeing "Low … Deep" tells the user what the scale is; an overflow menu
 * hiding "Deep" would not.
 *
 * In a monochrome palette the selected segment is the only filled surface in
 * the row, so selection reads as tonal inversion rather than as a colour. That
 * is deliberately the strongest contrast available here.
 */
@Composable
fun <T> SegmentedPicker(
    label: String?,
    options: List<PickerOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (options.isEmpty()) return

    Column(modifier = modifier) {
        label?.let {
            Text(
                text = it.uppercase(),
                // Letterspaced small caps: this is a field label, and spacing
                // it out separates it from the control without needing a
                // heavier weight or a rule above it.
                style = MaterialTheme.typography.labelSmallEmphasized.copy(
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.sm)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // One group for assistive tech, so the row is announced as a
                // single choice rather than as N unrelated buttons.
                .selectableGroup(),
            shape = OroCorners.pill,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEach { option ->
                    val isSelected = option.value == selected

                    val container by animateColorAsState(
                        when {
                            !isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh
                            enabled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "segmentContainer"
                    )
                    val content by animateColorAsState(
                        when {
                            isSelected && enabled -> MaterialTheme.colorScheme.onPrimary
                            isSelected -> MaterialTheme.colorScheme.onSurfaceVariant
                            enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.outline
                        },
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "segmentContent"
                    )

                    val interaction = remember { MutableInteractionSource() }
                    Surface(
                        selected = isSelected,
                        onClick = { onSelect(option.value) },
                        enabled = enabled,
                        modifier = Modifier
                            // Equal weights: the segments must not resize as
                            // the selection moves, or the row shifts under the
                            // finger between taps.
                            .weight(1f)
                            .expressivePress(interaction),
                        shape = OroCorners.pill,
                        color = container,
                        contentColor = content,
                        interactionSource = interaction
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = option.label,
                                // Emphasized only when selected: the weight
                                // change reinforces the fill without altering
                                // the text size, so nothing reflows.
                                style = if (isSelected) {
                                    MaterialTheme.typography.labelLargeEmphasized
                                } else {
                                    MaterialTheme.typography.labelLarge
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(
                                    horizontal = Space.sm,
                                    vertical = Space.md
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
