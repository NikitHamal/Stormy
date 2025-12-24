package com.codex.stormy.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.stormy.R
import com.codex.stormy.domain.model.Project
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onProjectClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var projectToEdit by remember { mutableStateOf<Project?>(null) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedProjects = remember { mutableStateListOf<String>() }

    // Exit selection mode when all items are deselected
    LaunchedEffect(selectedProjects.size) {
        if (selectedProjects.isEmpty() && isSelectionMode) {
            isSelectionMode = false
        }
    }

    // Handle clone completion - navigate to cloned project
    LaunchedEffect(uiState.clonedProjectId) {
        uiState.clonedProjectId?.let { projectId ->
            viewModel.acknowledgeClonedProject()
            onProjectClick(projectId)
        }
    }

    // Show clone error
    LaunchedEffect(uiState.cloneError) {
        uiState.cloneError?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(error)
            }
            viewModel.clearCloneState()
        }
    }

    // Handle import completion - navigate to imported project
    LaunchedEffect(uiState.importedProjectId) {
        uiState.importedProjectId?.let { projectId ->
            viewModel.acknowledgeImportedProject()
            showImportDialog = false
            onProjectClick(projectId)
        }
    }

    // Show import error
    LaunchedEffect(uiState.importError) {
        uiState.importError?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(error)
            }
            viewModel.clearImportState()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            selectedProjects.clear()
                            isSelectionMode = false
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Cancel selection"
                            )
                        }
                    }
                },
                title = {
                    if (isSelectionMode) {
                        Text(
                            text = "${selectedProjects.size} selected",
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Text(
                            text = context.getString(R.string.home_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        // Select all action
                        IconButton(onClick = {
                            if (selectedProjects.size == uiState.projects.size) {
                                selectedProjects.clear()
                            } else {
                                selectedProjects.clear()
                                selectedProjects.addAll(uiState.projects.map { it.id })
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.SelectAll,
                                contentDescription = "Select all"
                            )
                        }
                        // Delete selected action
                        IconButton(onClick = {
                            // Delete all selected projects
                            selectedProjects.forEach { projectId ->
                                viewModel.deleteProject(projectId)
                            }
                            selectedProjects.clear()
                            isSelectionMode = false
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = context.getString(R.string.action_settings)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isSelectionMode) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = if (showFabMenu) Icons.Outlined.Close else Icons.Outlined.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (showFabMenu) "Close" else context.getString(R.string.home_create_project))
                }

                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("New Project") },
                        onClick = {
                            showFabMenu = false
                            showCreateDialog = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clone from Git") },
                        onClick = {
                            showFabMenu = false
                            showCloneDialog = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Import Folder") },
                        onClick = {
                            showFabMenu = false
                            showImportDialog = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Upload,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when {
                uiState.projects.isEmpty() && uiState.searchQuery.isEmpty() -> {
                    EmptyState(
                        onCreateClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                uiState.projects.isEmpty() && uiState.searchQuery.isNotEmpty() -> {
                    EmptySearchState(
                        query = uiState.searchQuery,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    ProjectsList(
                        projects = uiState.projects,
                        onProjectClick = { project ->
                            if (isSelectionMode) {
                                // Toggle selection
                                if (selectedProjects.contains(project.id)) {
                                    selectedProjects.remove(project.id)
                                } else {
                                    selectedProjects.add(project.id)
                                }
                            } else {
                                viewModel.openProject(project.id)
                                onProjectClick(project.id)
                            }
                        },
                        onDeleteClick = { project ->
                            projectToDelete = project
                        },
                        onEditClick = { project ->
                            projectToEdit = project
                        },
                        onLongClick = { project ->
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedProjects.add(project.id)
                            }
                        },
                        isSelectionMode = isSelectionMode,
                        selectedProjects = selectedProjects,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description, template ->
                viewModel.createProject(name, description, template)
                showCreateDialog = false
            }
        )
    }

    projectToDelete?.let { project ->
        DeleteProjectDialog(
            projectName = project.name,
            onDismiss = { projectToDelete = null },
            onConfirm = {
                viewModel.deleteProject(project.id)
                projectToDelete = null
            }
        )
    }

    projectToEdit?.let { project ->
        EditProjectDialog(
            project = project,
            onDismiss = { projectToEdit = null },
            onConfirm = { newName, newDescription ->
                viewModel.updateProject(project.id, newName, newDescription)
                projectToEdit = null
            }
        )
    }

    if (showCloneDialog) {
        CloneRepositoryDialog(
            onDismiss = {
                showCloneDialog = false
                viewModel.clearCloneState()
            },
            onClone = { url, projectName, shallow ->
                viewModel.cloneRepository(url, projectName, shallow)
            },
            isCloning = uiState.isCloning,
            progress = uiState.cloneProgress,
            progressMessage = uiState.cloneProgressMessage,
            error = uiState.cloneError
        )
    }

    if (showImportDialog) {
        ImportFolderDialog(
            onDismiss = {
                showImportDialog = false
                viewModel.clearImportState()
            },
            onImport = { name, description, folderUri ->
                viewModel.importFolder(name, description, folderUri)
            },
            isImporting = uiState.isImporting,
            progress = uiState.importProgress,
            progressMessage = uiState.importProgressMessage,
            error = uiState.importError
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = {
            Text(context.getString(R.string.home_search_hint))
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = null
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
    )
}

@Composable
private fun EmptyState(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Code,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = context.getString(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = context.getString(R.string.home_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun EmptySearchState(
    query: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No projects found for \"$query\"",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProjectsList(
    projects: List<Project>,
    onProjectClick: (Project) -> Unit,
    onDeleteClick: (Project) -> Unit,
    onEditClick: (Project) -> Unit,
    onLongClick: (Project) -> Unit,
    isSelectionMode: Boolean,
    selectedProjects: List<String>,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = projects,
            key = { it.id }
        ) { project ->
            SwipeableProjectCard(
                project = project,
                onClick = { onProjectClick(project) },
                onDeleteClick = { onDeleteClick(project) },
                onEditClick = { onEditClick(project) },
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(project)
                },
                isSelectionMode = isSelectionMode,
                isSelected = selectedProjects.contains(project.id),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
            )
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * Swipeable project card with swipe-to-delete and modern selection support
 * Features:
 * - Professional selection indicator with animated checkmark
 * - Elevated selection visual with colored left border
 * - Subtle scale animation for tactile feedback
 * - Clean minimalist design in both normal and selection modes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableProjectCard(
    project: Project,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDeleteClick()
                false // Return false to not actually dismiss (let dialog handle it)
            } else {
                false
            }
        }
    )

    // Animate selection state
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "selection_scale"
    )

    // Selection indicator animation
    val checkmarkScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkmark_scale"
    )

    // Border indicator animation
    val borderWidth by animateFloatAsState(
        targetValue = if (isSelected) 4f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "border_width"
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isSelectionMode,
        backgroundContent = {
            // Delete background with gradient effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.errorContainer
                            )
                        )
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        content = {
            Card(
                modifier = Modifier
                    .scale(selectionScale)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isSelected) 2.dp else 0.dp
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (borderWidth > 0f) {
                                Modifier.drawWithContent {
                                    drawContent()
                                    // Draw selection indicator bar on the left
                                    drawRect(
                                        color = Color(0xFF3B82F6), // Primary blue
                                        size = androidx.compose.ui.geometry.Size(
                                            width = borderWidth,
                                            height = size.height
                                        )
                                    )
                                }
                            } else Modifier
                        )
                        .padding(start = if (isSelected) 4.dp else 0.dp)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Project icon or selection checkbox
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Folder icon (always visible but fades in selection mode)
                        AnimatedVisibility(
                            visible = !isSelectionMode || !isSelected,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelectionMode && !isSelected) {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelectionMode && !isSelected) {
                                    // Empty circle for unselected items in selection mode
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color.Transparent)
                                            .border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.outline,
                                                shape = CircleShape
                                            )
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Selection checkmark (animates in when selected)
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + scaleIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ),
                            exit = fadeOut() + scaleOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF3B82F6),
                                                Color(0xFF2563EB)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Selected",
                                    modifier = Modifier
                                        .size(28.dp)
                                        .scale(checkmarkScale),
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Project info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (project.description.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = project.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = project.formattedLastOpened,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    }

                    // Action menu (only in normal mode)
                    AnimatedVisibility(
                        visible = !isSelectionMode,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        showMenu = false
                                        onEditClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Delete",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDeleteClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun DeleteProjectDialog(
    projectName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(context.getString(R.string.home_delete_project_title))
        },
        text = {
            Text(context.getString(R.string.home_delete_project_message, projectName))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(context.getString(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.action_cancel))
            }
        }
    )
}

/**
 * Dialog for editing project name and description
 */
@Composable
private fun EditProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(project.name) }
    var description by remember { mutableStateOf(project.description) }
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Project")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
