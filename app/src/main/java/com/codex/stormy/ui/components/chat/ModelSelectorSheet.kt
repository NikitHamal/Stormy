package com.codex.stormy.ui.components.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.data.ai.AiModel

/**
 * Model category for grouping
 */
enum class ModelCategory(val displayName: String, val icon: ImageVector) {
    REASONING("Reasoning", Icons.Outlined.Psychology),
    AGENT("Agent", Icons.Outlined.AutoAwesome),
    FAST("Fast", Icons.Outlined.Bolt)
}

/**
 * Get category for a model
 */
private fun AiModel.getCategory(): ModelCategory {
    return when {
        isThinkingModel -> ModelCategory.REASONING
        supportsToolCalls -> ModelCategory.AGENT
        else -> ModelCategory.FAST
    }
}

/**
 * Modern model selector bottom sheet
 * Displays available AI models with grouping and selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    models: List<AiModel>,
    selectedModel: AiModel,
    onModelSelected: (AiModel) -> Unit,
    onDismiss: () -> Unit,
    onManageModels: () -> Unit = {},
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    modifier: Modifier = Modifier
) {
    val groupedModels = models.groupBy { it.getCategory() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Refresh button
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh models",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Settings button
                    IconButton(onClick = onManageModels) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Manage models",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Model list grouped by category
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelCategory.entries.forEach { category ->
                    val categoryModels = groupedModels[category] ?: emptyList()
                    if (categoryModels.isNotEmpty()) {
                        // Category header
                        item(key = "header_${category.name}") {
                            CategoryHeader(
                                category = category,
                                modelCount = categoryModels.size
                            )
                        }

                        // Category models
                        items(
                            items = categoryModels,
                            key = { it.id }
                        ) { model ->
                            ModelSelectionCard(
                                model = model,
                                isSelected = model.id == selectedModel.id,
                                onClick = { onModelSelected(model) }
                            )
                        }

                        // Spacer between categories
                        item(key = "spacer_${category.name}") {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Category header with icon and model count
 */
@Composable
private fun CategoryHeader(
    category: ModelCategory,
    modelCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = category.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "($modelCount)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Individual model selection card
 */
@Composable
private fun ModelSelectionCard(
    model: AiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Model icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            model.isThinkingModel -> MaterialTheme.colorScheme.tertiaryContainer
                            model.supportsToolCalls -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        model.isThinkingModel -> Icons.Outlined.Psychology
                        model.supportsToolCalls -> Icons.Outlined.AutoAwesome
                        else -> Icons.Outlined.Bolt
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = when {
                        model.isThinkingModel -> MaterialTheme.colorScheme.onTertiaryContainer
                        model.supportsToolCalls -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }

            // Model info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Context length
                    Text(
                        text = formatContextLength(model.contextLength),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    // Capability badges
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (model.supportsStreaming) {
                            CapabilityDot(color = MaterialTheme.colorScheme.secondary, label = "Stream")
                        }
                        if (model.supportsToolCalls) {
                            CapabilityDot(color = MaterialTheme.colorScheme.primary, label = "Tools")
                        }
                        if (model.isThinkingModel) {
                            CapabilityDot(color = MaterialTheme.colorScheme.tertiary, label = "Think")
                        }
                    }
                }
            }

            // Selection indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Small capability indicator dot
 */
@Composable
private fun CapabilityDot(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.8f))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = color.copy(alpha = 0.8f)
        )
    }
}

/**
 * Format context length for display
 */
private fun formatContextLength(length: Int): String {
    return when {
        length >= 1000000 -> "${length / 1000000}M ctx"
        length >= 1000 -> "${length / 1000}K ctx"
        else -> "$length tokens"
    }
}
