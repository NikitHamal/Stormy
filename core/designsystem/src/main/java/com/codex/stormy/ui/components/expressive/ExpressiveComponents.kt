package com.codex.stormy.ui.components.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * App-level wrappers over the Material 3 Expressive components.
 *
 * These exist so screens do not each re-derive the same sizing, colours and
 * semantics. Anything here is a thin layer over a real Expressive component --
 * no bespoke reimplementations.
 */

/**
 * The Expressive loading indicator: a sequence of morphing [MaterialShapes]
 * rather than a spinning arc.
 *
 * Prefer this over CircularProgressIndicator for indeterminate work. Pass
 * [contained] for cases where the indicator sits on an arbitrary background and
 * needs its own container to stay legible.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    contained: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String = "Loading"
) {
    val semanticsModifier = modifier.semantics { this.contentDescription = contentDescription }
    if (contained) {
        ContainedLoadingIndicator(
            modifier = semanticsModifier,
            indicatorColor = color,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    } else {
        LoadingIndicator(
            modifier = semanticsModifier,
            color = color,
            polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
        )
    }
}

/**
 * Determinate variant, for work with real progress (clone, push, export).
 *
 * [progress] is read through a lambda rather than passed as a Float so that
 * progress updates do not recompose the caller -- only the indicator's draw
 * phase re-runs. On a long clone that is the difference between one
 * recomposition and several hundred.
 */
@Composable
fun ExpressiveProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    LoadingIndicator(
        progress = progress,
        modifier = modifier,
        color = color
    )
}

/** A single entry in an [ExpressiveToggleGroup]. */
data class ToggleOption(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    /**
     * Drawn instead of [icon] when set -- for marks that are not single-colour
     * glyphs, such as Orobi on the chat tab.
     */
    val iconContent: (@Composable () -> Unit)? = null
)

/**
 * Segmented selector built on the Expressive [ButtonGroup] + [ToggleButton].
 *
 * Replaces the hand-rolled "row of Surfaces with a selected background" pattern
 * that was repeated across the editor tabs, preview device switcher and
 * inspector tabs. Using the real component means the group gets Expressive's
 * press-squish, shape morphing on selection and correct a11y roles for free.
 */
@Composable
fun ExpressiveToggleGroup(
    options: List<ToggleOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true
) {
    ButtonGroup(
        // ButtonGroup measures its children and moves anything that does not fit
        // into an overflow menu, which is what makes it safe on narrow phones
        // where a fixed Row would clip.
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ToggleGroupSpacing)
    ) {
        options.forEach { option ->
            val checked = option.id == selectedId
            toggleableItem(
                checked = checked,
                label = if (showLabels) option.label else "",
                onCheckedChange = { isChecked -> if (isChecked) onSelect(option.id) },
                icon = option.iconContent ?: option.icon?.let { icon ->
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = if (showLabels) null else option.label,
                            modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                        )
                    }
                }
            )
        }
    }
}

private val ToggleGroupSpacing = 6.dp

/**
 * A status dot whose shape carries the state in addition to its colour, so the
 * state is still distinguishable without relying on colour alone.
 */
@Composable
fun ExpressiveStatusDot(
    state: StatusDotState,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 10.dp
) {
    val target = when (state) {
        StatusDotState.Idle -> MaterialTheme.colorScheme.outline
        StatusDotState.Working -> MaterialTheme.colorScheme.primary
        StatusDotState.Success -> MaterialTheme.colorScheme.tertiary
        StatusDotState.Error -> MaterialTheme.colorScheme.error
    }
    val color by animateColorAsState(target, label = "statusDotColor")
    val shape = when (state) {
        StatusDotState.Idle -> com.codex.stormy.ui.theme.ExpressiveShapes.circle()
        StatusDotState.Working -> com.codex.stormy.ui.theme.ExpressiveShapes.cookie4()
        StatusDotState.Success -> com.codex.stormy.ui.theme.ExpressiveShapes.clover4()
        StatusDotState.Error -> com.codex.stormy.ui.theme.ExpressiveShapes.verySunny()
    }
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = color,
        content = {}
    )
}

enum class StatusDotState { Idle, Working, Success, Error }

/**
 * Section heading used across the settings, inspector and drawer surfaces.
 * Uses the Expressive emphasized label role so the heading reads as stressed
 * without being a larger size than the rows beneath it.
 */
@Composable
fun ExpressiveSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/** Inline indeterminate indicator sized for a text row. */
@Composable
fun ExpressiveInlineLoader(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(18.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}
