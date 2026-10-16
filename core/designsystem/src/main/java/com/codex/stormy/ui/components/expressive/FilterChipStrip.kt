package com.codex.stormy.ui.components.expressive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Space

/** One chip in a [FilterChipStrip]. */
@Immutable
data class FilterChipItem(
    val id: String,
    val label: String,
    val icon: ImageVector? = null
)

/**
 * A horizontally scrolling strip of filter chips.
 *
 * Selection morphs the chip's corner radius -- 11dp at rest, 22dp when
 * selected -- rather than only recolouring it. On a monochrome palette the
 * colour step alone is weak, and the shape change survives being glanced at;
 * it is also the Expressive idiom for state, which is why a selected chip
 * looks like a different *kind* of object rather than the same object in a
 * different colour.
 *
 * Pressing morphs the other way (toward 20dp) and shrinks slightly, so the
 * chip acknowledges the touch before the selection lands.
 */
@Composable
fun FilterChipStrip(
    items: List<FilterChipItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    AnimatedVisibility(
        visible = visible,
        // Reveals downward out of the bar above it, delayed slightly so the
        // bar's own transition is underway first -- the strip should look like
        // a consequence of the bar changing, not a simultaneous second event.
        enter = fadeIn(
            MaterialTheme.motionScheme.defaultEffectsSpec()
        ) + expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
            slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it / 2 },
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
    ) {
        LazyRow(
            modifier = modifier,
            contentPadding = PaddingValues(
                horizontal = Space.lg,
                vertical = Space.sm
            ),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            items(items, key = { it.id }) { item ->
                FilterChip(
                    item = item,
                    selected = item.id == selectedId,
                    index = items.indexOf(item),
                    onClick = { onSelect(item.id) }
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    item: FilterChipItem,
    selected: Boolean,
    index: Int,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val radius = remember { Animatable(if (selected) SELECTED_RADIUS else REST_RADIUS) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(selected, pressed, spatial) {
        radius.animateTo(
            when {
                pressed -> PRESSED_RADIUS
                selected -> SELECTED_RADIUS
                else -> REST_RADIUS
            },
            spatial
        )
    }
    LaunchedEffect(pressed, spatial) {
        scale.animateTo(if (pressed) 0.92f else 1f, spatial)
    }

    val container by animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "chipContainer"
    )
    val content by animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "chipContent"
    )

    // Staggered entrance, 40ms apart, so the strip unrolls left to right
    // instead of arriving as a block.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 40L)
        entrance.animateTo(1f, tween(220))
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .scale(scale.value * (0.9f + 0.1f * entrance.value))
            .alpha(entrance.value),
        shape = RoundedCornerShape(radius.value.dp),
        color = container,
        contentColor = content,
        border = if (selected) {
            null
        } else {
            BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant)
        },
        interactionSource = interaction
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The icon becomes a check when selected -- the same slot carrying
            // the state, rather than a tick appearing beside the icon and
            // widening the chip.
            val glyph = if (selected) Icons.Outlined.Check else item.icon
            if (glyph != null) {
                Icon(
                    imageVector = glyph,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
                Spacer(Modifier.width(Space.xs))
            }
            Text(
                text = item.label,
                style = if (selected) {
                    MaterialTheme.typography.labelMediumEmphasized
                } else {
                    MaterialTheme.typography.labelMedium
                }
            )
        }
    }
}

private const val REST_RADIUS = 11f
private const val PRESSED_RADIUS = 20f
private const val SELECTED_RADIUS = 22f
