package com.codex.stormy.ui.screens.aimodels

import com.codex.stormy.ui.components.expressive.FilterChipItem
import com.codex.stormy.ui.components.expressive.FilterChipStrip
import com.codex.stormy.ui.components.expressive.FlowerIconButton
import com.codex.stormy.ui.components.expressive.ExpressiveSearchTopBar
import androidx.compose.material.icons.outlined.Search
import com.codex.stormy.ui.theme.CodeXCorners
import androidx.compose.material3.TextButton
import com.codex.stormy.ui.theme.Space
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Switch
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
import com.codex.stormy.ui.components.expressive.ExpressiveLoadingIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.AiProvider
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

    var searchActive by remember { mutableStateOf(false) }
    val providerChips = remember(uiState.allModels) {
        buildList {
            add(FilterChipItem(ProviderChipAll, "All"))
            uiState.allModels.map { it.provider }.distinct().forEach { provider ->
                add(FilterChipItem(provider.name, provider.displayName))
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                ModelsTopBar(
                    enabledCount = uiState.enabledCount,
                    totalCount = uiState.totalCount,
                    searchActive = searchActive,
                    onSearchActiveChange = { searchActive = it },
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    onBackClick = onBackClick,
                    onRefresh = viewModel::refreshModels,
                    isLoading = uiState.isLoading || uiState.isRefreshing
                )

                // Provider filter, in the same strip idiom as Home. Always
                // visible here: filtering by provider is how you navigate a
                // catalogue of several hundred models, not an occasional act.
                FilterChipStrip(
                    items = providerChips,
                    selectedId = uiState.selectedProvider?.name ?: ProviderChipAll,
                    onSelect = { id ->
                        viewModel.selectProvider(
                            if (id == ProviderChipAll) null else AiProvider.valueOf(id)
                        )
                    }
                )
            }
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
                        onModelEnabledChange = viewModel::setModelEnabled,
                        onProviderEnabledChange = viewModel::setProviderEnabled,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Same cross-fading bar as Home.
 *
 * Deliberately the identical component: a second search that behaved
 * differently on a second screen is how an app stops feeling like one app.
 * The subtitle carries the enabled count, which is the number the user is
 * here to change.
 */
@Composable
private fun ModelsTopBar(
    enabledCount: Int,
    totalCount: Int,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean
) {
    ExpressiveSearchTopBar(
        searchActive = searchActive,
        onSearchActiveChange = onSearchActiveChange,
        query = query,
        onQueryChange = onQueryChange,
        placeholder = "Search models",
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(Modifier.width(Space.sm))
                Column {
                    Text(
                        text = "AI Models",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$enabledCount of $totalCount enabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            FlowerIconButton(
                icon = Icons.Outlined.Search,
                contentDescription = "Search models",
                onClick = { onSearchActiveChange(true) },
                size = 44.dp
            )
            FlowerIconButton(
                icon = Icons.Outlined.Refresh,
                contentDescription = "Refresh models",
                onClick = { if (!isLoading) onRefresh() },
                alternate = true,
                size = 44.dp
            )
        }
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

        items(AiProvider.entries.toList(), key = { it.name }) { provider ->
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
    onModelEnabledChange: (AiModel, Boolean) -> Unit,
    onProviderEnabledChange: (AiProvider, Boolean) -> Unit,
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
                    modelCount = providerModels.size,
                    enabledCount = providerModels.count { it.isEnabled },
                    onEnabledChange = { enabled ->
                        onProviderEnabledChange(provider, enabled)
                    }
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
                    onEnabledChange = { enabled -> onModelEnabledChange(model, enabled) },
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
    modelCount: Int,
    enabledCount: Int,
    onEnabledChange: (Boolean) -> Unit
) {
    val allEnabled = enabledCount == modelCount && modelCount > 0
    val noneEnabled = enabledCount == 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.md, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        Text(
            text = provider.displayName,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            // The count is the state: "4 / 37 on" answers what a tri-state
            // checkbox only hints at, and it stays readable when a provider
            // returns hundreds of models.
            text = "$enabledCount / $modelCount on",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        // One action, labelled for what it will do rather than for the current
        // state -- a toggle here would be ambiguous when the set is partly on.
        TextButton(
            onClick = { onEnabledChange(!allEnabled) },
            enabled = modelCount > 0
        ) {
            Text(
                text = when {
                    allEnabled -> "Turn all off"
                    noneEnabled -> "Turn all on"
                    else -> "Turn all on"
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
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
    onEnabledChange: (Boolean) -> Unit,
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
            shape = CodeXCorners.medium,
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
                    // Disabled rows fade rather than disappear: the user needs
                    // to find them again to switch them back on.
                    .alpha(if (model.isEnabled) 1f else 0.45f)
                    .padding(horizontal = Space.md, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md)
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
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                        )
                    }
                }

                // On/off. A Switch rather than a checkbox because this is a
                // setting that takes effect immediately, not a selection being
                // accumulated for a later action.
                Switch(
                    checked = model.isEnabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics {
                        contentDescription = if (model.isEnabled) {
                            "${model.name} is on. Turn off to hide it from the model picker."
                        } else {
                            "${model.name} is off. Turn on to show it in the model picker."
                        }
                    }
                )

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
                            text = "Remove as default"
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
                            text = "Set as default"
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
            ExpressiveLoadingIndicator(
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "Loading models...",
                style = MaterialTheme.typography.bodyMedium,
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
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Check your API keys in settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Sentinel id for the "All providers" chip. */
private const val ProviderChipAll = "__all__"
