package com.codex.stormy.ui.screens.aimodels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.AiProvider
import com.codex.stormy.ui.theme.PoppinsFontFamily
import kotlinx.coroutines.launch

/**
 * Minimal, clean AI Models screen
 * Shows provider tabs and simple model list without feature filters
 */
@Composable
fun AiModelsScreen(
    onBackClick: () -> Unit,
    viewModel: AiModelsViewModel = viewModel(factory = AiModelsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            MinimalTopBar(
                onBackClick = onBackClick,
                onRefresh = viewModel::refreshModels,
                isLoading = uiState.isLoading || uiState.isRefreshing
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Provider tabs
            ProviderTabs(
                selectedProvider = uiState.selectedProvider,
                onProviderSelected = viewModel::selectProvider,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Content
            when {
                uiState.isLoading && uiState.filteredModels.isEmpty() -> {
                    LoadingState(modifier = Modifier.fillMaxSize())
                }
                uiState.filteredModels.isEmpty() -> {
                    EmptyState(modifier = Modifier.fillMaxSize())
                }
                else -> {
                    ModelsList(
                        models = uiState.filteredModels,
                        currentModelId = uiState.currentModel?.id,
                        defaultModelId = uiState.defaultModelId,
                        onModelSelect = viewModel::selectModel,
                        onSetAsDefault = viewModel::setAsDefaultModel,
                        onClearDefault = viewModel::clearDefaultModel,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Minimal top bar with just back and refresh
 */
@Composable
private fun MinimalTopBar(
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean
) {
    TopAppBar(
        title = {
            Text(
                text = "AI Models",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(
                onClick = onRefresh,
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * Provider filter tabs - minimal style
 */
@Composable
private fun ProviderTabs(
    selectedProvider: AiProvider?,
    onProviderSelected: (AiProvider?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ProviderChip(
                label = "All",
                isSelected = selectedProvider == null,
                onClick = { onProviderSelected(null) }
            )
        }

        items(AiProvider.entries.toList()) { provider ->
            ProviderChip(
                label = provider.displayName,
                isSelected = selectedProvider == provider,
                onClick = { onProviderSelected(provider) }
            )
        }
    }
}

/**
 * Provider selection chip
 */
@Composable
private fun ProviderChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontFamily = PoppinsFontFamily,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    )
}

/**
 * Clean model list without excessive decoration
 */
@Composable
private fun ModelsList(
    models: List<AiModel>,
    currentModelId: String?,
    defaultModelId: String,
    onModelSelect: (AiModel) -> Unit,
    onSetAsDefault: (AiModel) -> Unit,
    onClearDefault: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val modelsByProvider = models.groupBy { it.provider }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        modelsByProvider.forEach { (provider, providerModels) ->
            // Provider header
            item(key = "header_${provider.name}") {
                ProviderSectionHeader(
                    provider = provider,
                    modelCount = providerModels.size
                )
            }

            // Models
            items(
                items = providerModels,
                key = { it.id }
            ) { model ->
                MinimalModelItem(
                    model = model,
                    isSelected = model.id == currentModelId,
                    isDefault = model.id == defaultModelId,
                    onClick = { onModelSelect(model) },
                    onSetAsDefault = {
                        onSetAsDefault(model)
                        scope.launch {
                            snackbarHostState.showSnackbar("${model.name} set as default")
                        }
                    },
                    onClearDefault = {
                        onClearDefault()
                        scope.launch {
                            snackbarHostState.showSnackbar("Default model cleared")
                        }
                    }
                )
            }

            // Spacer
            item(key = "spacer_${provider.name}") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * Provider section header
 */
@Composable
private fun ProviderSectionHeader(
    provider: AiProvider,
    modelCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = provider.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "• $modelCount",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = PoppinsFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Minimal model list item - clean and compact
 * Long press to access context menu (set as default, etc.)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinimalModelItem(
    model: AiModel,
    isSelected: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
    onSetAsDefault: () -> Unit,
    onClearDefault: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            isDefault -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "bg"
    )

    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Type indicator
                Box(
                    modifier = Modifier
                        .size(32.dp)
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
                        imageVector = getModelIcon(model),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when {
                            model.isThinkingModel -> MaterialTheme.colorScheme.onTertiaryContainer
                            model.supportsToolCalls -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }

                // Model name with default indicator
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = if (isSelected || isDefault) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isDefault -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Default badge
                        AnimatedVisibility(
                            visible = isDefault,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Star,
                                contentDescription = "Default model",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    // Show "Default" label if this is the default model
                    AnimatedVisibility(visible = isDefault) {
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = PoppinsFontFamily,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                        )
                    }
                }

                // Selection indicator
                AnimatedVisibility(
                    visible = isSelected,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Selected",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        // Context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            if (isDefault) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Remove as default",
                            fontFamily = PoppinsFontFamily
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onClearDefault()
                    }
                )
            } else {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Set as default",
                            fontFamily = PoppinsFontFamily
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onSetAsDefault()
                    }
                )
            }
        }
    }
}

/**
 * Get icon for model type
 */
private fun getModelIcon(model: AiModel): ImageVector {
    return when {
        model.isThinkingModel -> Icons.Outlined.Psychology
        model.supportsToolCalls -> Icons.Outlined.AutoAwesome
        else -> Icons.Outlined.Bolt
    }
}

/**
 * Loading state
 */
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp
            )
            Text(
                text = "Loading models...",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = PoppinsFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Empty state
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No models found",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Check your API keys in settings",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = PoppinsFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
