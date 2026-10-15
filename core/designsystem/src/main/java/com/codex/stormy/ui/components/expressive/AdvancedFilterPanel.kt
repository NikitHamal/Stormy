package com.codex.stormy.ui.components.expressive

import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.codex.stormy.ui.theme.CodeXCorners
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Space

/** One sort option. */
@Immutable
data class SortOption(
    val id: String,
    val label: String,
    val icon: ImageVector? = null
)

/**
 * Sort and filter controls, revealed by the tune button in the search pill.
 *
 * Kept out of the chip strip deliberately. Chips are a *filter* -- they narrow
 * what is shown and each is independently meaningful at a glance. Sort is a
 * single exclusive choice that changes order rather than membership, and
 * mixing the two in one row makes neither readable. Putting sort behind the
 * tune button also keeps the strip short enough not to scroll on a phone.
 *
 * The panel expands in place under the search bar rather than opening a sheet:
 * it is a refinement of a search already in progress, and a sheet would cover
 * the results the user is adjusting.
 */
@Composable
fun AdvancedFilterPanel(
    visible: Boolean,
    sortOptions: List<SortOption>,
    selectedSortId: String,
    onSortSelected: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    activeFilterCount: Int = 0
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.sm),
            shape = CodeXCorners.xLarge,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(Space.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SORT & FILTER",
                        style = MaterialTheme.typography.labelSmallEmphasized.copy(
                            letterSpacing = 1.4.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    // Only offered when something is actually applied -- a
                    // permanently-present Reset implies state that may not
                    // exist, and invites a tap that does nothing.
                    if (activeFilterCount > 0) {
                        TextButton(onClick = onReset) {
                            Text("Reset all", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                SegmentedPicker(
                    label = null,
                    options = sortOptions.map { PickerOption(it.id, it.label) },
                    selected = selectedSortId,
                    onSelect = onSortSelected,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }
        }
    }
}

/**
 * The tune button that lives inside the search pill.
 *
 * Carries a count badge when filters are applied, so the user can see that a
 * short result list is their own doing rather than an empty library -- the
 * single most common confusion with a collapsed filter panel.
 */
@Composable
fun FilterTuneButton(
    active: Boolean,
    activeCount: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(Sizes.minTouchTarget - Space.sm),
            shape = CodeXCorners.pill,
            color = if (active || activeCount > 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (active || activeCount > 0) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (activeCount > 0) {
                        "Filter options, $activeCount applied"
                    } else {
                        "Filter options"
                    },
                    modifier = Modifier.size(Sizes.icon)
                )
            }
        }

        if (activeCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp),
                shape = CodeXCorners.pill,
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = activeCount.toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
