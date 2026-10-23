package com.oro.app.ui.components.chat

import com.oro.app.data.ai.ModelGroup
import androidx.compose.runtime.remember

import com.oro.app.ui.brand.OrobiIdle
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.ui.components.expressive.ExpressiveLoadingIndicator
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.ExpressiveShapes
import com.oro.app.data.ai.displayName
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.navigationBarsPadding

/**
 * Model selector bottom sheet, grouped by [ModelGroup].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    models: List<AiModel>,
    selectedModel: AiModel?,
    onModelSelected: (AiModel) -> Unit,
    onDismiss: () -> Unit,
    onManageModels: () -> Unit = {},
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    /** Saved reasoning level per model id; the picker shows only when [onReasoningLevelChange] is set. */
    reasoningLevels: Map<String, String> = emptyMap(),
    onReasoningLevelChange: ((modelId: String, level: String) -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    modifier: Modifier = Modifier
) {
    // Grouped by what it takes to use a model: recommended keyless picks,
    // other free tiers, unofficial community bridges, then keyed providers.
    val groups = remember(models) { ModelGroup.grouped(models) }

    // The same sheet as chat settings: shape, colour and drag handle, so the
    // two sheets the composer opens read as one family.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.xl, end = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Models",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        ExpressiveLoadingIndicator(modifier = Modifier.size(Sizes.icon))
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh models",
                            modifier = Modifier.size(Sizes.icon),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onManageModels) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Manage models",
                        modifier = Modifier.size(Sizes.icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (groups.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Space.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    OrobiIdle(
                        look = false,
                        contentDescription = null,
                        modifier = Modifier.width(96.dp)
                    )
                    Text(
                        text = "No models yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Shown only for models that expose a reasoning budget. A disabled
                // control on every other model would be a permanent dead element,
                // and the absence is itself informative.
                val pickFor = selectedModel?.takeIf { it.supportsReasoningEffort }
                val onPick = onReasoningLevelChange
                if (pickFor != null && onPick != null) {
                    ReasoningPicker(
                        model = pickFor,
                        levels = reasoningLevels,
                        onSelect = onPick,
                        modifier = Modifier.padding(
                            start = Space.xl,
                            end = Space.xl,
                            top = Space.sm,
                            bottom = Space.xs
                        )
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.sm)
                ) {
                    groups.forEach { (group, groupModels) ->
                        item(key = "header-${group.name}", contentType = "header") {
                            ModelGroupHeader(group)
                        }
                        items(
                            items = groupModels,
                            key = { "${it.provider.name}/${it.id}" },
                            contentType = { "model" }
                        ) { model ->
                            ModelRow(
                                model = model,
                                isSelected = selectedModel != null && model.id == selectedModel.id &&
                                    model.provider == selectedModel.provider,
                                onClick = { onModelSelected(model) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A section label in the same letterspaced small caps as the sheets' field labels. */
@Composable
private fun ModelGroupHeader(group: ModelGroup) {
    Text(
        text = group.title.uppercase(),
        style = MaterialTheme.typography.labelSmallEmphasized.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.md, top = Space.lg, bottom = Space.xs)
    )
}

/**
 * One model: its provider's mark, its name, and a caption with the provider,
 * context and anything unusual about it. Rows are plain on the sheet; the
 * selected one is filled, so the choice is the only block on the list.
 */
@Composable
private fun ModelRow(
    model: AiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = remember(model.id, model.name, model.provider) { model.displayName() }
    val caption = remember(model.id, model.provider, model.contextLength) { ModelGroup.caption(model) }
    Surface(
        onClick = onClick,
        shape = OroCorners.large,
        color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            ProviderMark(provider = model.provider, selected = isSelected)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = if (isSelected) {
                        MaterialTheme.typography.bodyLargeEmphasized
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(Sizes.icon),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * The provider's initial on an Expressive shape that is the provider's own
 * (by ordinal), like the project marks on Home: in a monochrome palette the
 * silhouette is what tells providers apart at a glance. Always legible --
 * the old tinted circles were near-invisible for some providers.
 */
@Composable
private fun ProviderMark(provider: AiProvider, selected: Boolean) {
    Surface(
        modifier = Modifier.size(Sizes.avatar),
        shape = ExpressiveShapes.forIndex(provider.ordinal),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = provider.displayName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
