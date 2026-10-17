package com.codex.stormy.ui.screens.home

import com.codex.stormy.ui.components.expressive.SortOption
import com.codex.stormy.ui.components.expressive.FilterTuneButton
import com.codex.stormy.ui.components.expressive.AdvancedFilterPanel
import androidx.compose.material.icons.outlined.Tune
import com.codex.stormy.ui.components.expressive.EmptyStateShape
import com.codex.stormy.ui.components.expressive.ExpressiveNoResults
import com.codex.stormy.ui.components.expressive.ExpressiveEmptyState
import com.codex.stormy.ui.components.expressive.FilterChipItem
import com.codex.stormy.ui.components.expressive.FilterChipStrip
import com.codex.stormy.ui.components.expressive.FlowerIconButton
import com.codex.stormy.ui.components.expressive.ExpressiveSearchTopBar
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Today
import com.codex.stormy.ui.theme.Space
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Elevation
import com.codex.stormy.ui.theme.ExpressiveShapes
import com.codex.stormy.ui.components.expressive.expressivePress
import com.codex.stormy.ui.components.agent.AgentVisuals
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Surface
import com.codex.stormy.ui.theme.CodeXCorners
import com.codex.stormy.ui.components.expressive.SheetAction
import com.codex.stormy.ui.components.expressive.ActionSheet
import com.codex.stormy.ui.components.expressive.SpeedDialScrim
import com.codex.stormy.ui.components.expressive.SpeedDialFab
import com.codex.stormy.ui.components.expressive.SpeedDialAction
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.CloudDownload
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
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
    var searchExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(ProjectFilterAll) }
    var selectedSort by remember { mutableStateOf(ProjectSortRecent) }
    var filterPanelOpen by remember { mutableStateOf(false) }

    // Quick filter and sort applied here rather than in the view model: both
    // are view concerns that do not survive leaving the screen, and routing
    // them through state would mean a round trip for every chip tap.
    val visibleProjects = remember(uiState.projects, selectedFilter, selectedSort) {
        val now = System.currentTimeMillis()
        uiState.projects
            .filter { project ->
                when (selectedFilter) {
                    "today" -> now - project.lastOpenedAt < DAY_MS
                    "week" -> now - project.lastOpenedAt < 7 * DAY_MS
                    // Detected from the working tree: Project carries no git
                    // flag, and adding one would mean a schema migration for a
                    // value that can go stale the moment the user runs init.
                    "git" -> java.io.File(project.rootPath, ".git").exists()
                    else -> true
                }
            }
            .let { filtered ->
                when (selectedSort) {
                    "name" -> filtered.sortedBy { it.name.lowercase() }
                    "template" -> filtered.sortedBy { it.template.name }
                    else -> filtered.sortedByDescending { it.lastOpenedAt }
                }
            }
    }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var projectToEdit by remember { mutableStateOf<Project?>(null) }

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
            Column {
                ExpressiveSearchTopBar(
                    searchActive = searchExpanded,
                    onSearchActiveChange = { searchExpanded = it },
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    placeholder = "Search projects",
                    searchTrailing = {
                        FilterTuneButton(
                            active = filterPanelOpen,
                            activeCount = if (selectedFilter != ProjectFilterAll) 1 else 0,
                            icon = Icons.Outlined.Tune,
                            onClick = { filterPanelOpen = !filterPanelOpen }
                        )
                    },
                    title = {
                        Text(
                            text = context.getString(R.string.home_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        FlowerIconButton(
                            icon = Icons.Outlined.Search,
                            contentDescription = "Search projects",
                            onClick = { searchExpanded = true }
                        )
                        FlowerIconButton(
                            icon = Icons.Outlined.Settings,
                            contentDescription = context.getString(R.string.action_settings),
                            onClick = onSettingsClick,
                            // The alternate lean, so two adjacent blobs read as
                            // deliberate rather than as a rendering artefact.
                            alternate = true
                        )
                    }
                )

                // The chip strip belongs to search: it filters what search is
                // showing, so it arrives with the pill and leaves with it.
                FilterChipStrip(
                    items = ProjectFilters,
                    selectedId = selectedFilter,
                    onSelect = { selectedFilter = it },
                    visible = searchExpanded
                )

                AdvancedFilterPanel(
                    visible = searchExpanded && filterPanelOpen,
                    sortOptions = ProjectSorts,
                    selectedSortId = selectedSort,
                    onSortSelected = { selectedSort = it },
                    onReset = {
                        selectedFilter = ProjectFilterAll
                        selectedSort = ProjectSortRecent
                    },
                    activeFilterCount = if (selectedFilter != ProjectFilterAll) 1 else 0
                )
            }
        },
        floatingActionButton = {
            // A speed dial rather than an extended FAB plus a dropdown. The old
            // control showed one action and hid the other two behind a menu, so
            // cloning a repository -- the way a lot of real work starts -- was
            // two taps and invisible until the first one.
            SpeedDialFab(
                expanded = showFabMenu,
                onExpandedChange = { showFabMenu = it },
                collapsedDescription = context.getString(R.string.home_create_project),
                actions = listOf(
                    SpeedDialAction(
                        label = "New project",
                        icon = Icons.Outlined.Add
                    ) { showCreateDialog = true },
                    SpeedDialAction(
                        label = "Clone repository",
                        icon = Icons.Outlined.CloudDownload
                    ) { showCloneDialog = true },
                    SpeedDialAction(
                        label = "Import folder",
                        icon = Icons.Outlined.DriveFolderUpload
                    ) { showImportDialog = true }
                )
            )
        }
    ) { innerPadding ->
        // Dismiss layer for the expanded speed dial. Inside the Scaffold body
        // so it covers the list but never the FAB itself.
        SpeedDialScrim(expanded = showFabMenu, onDismiss = { showFabMenu = false })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

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
                        onClear = { viewModel.updateSearchQuery("") },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    ProjectsList(
                        projects = visibleProjects,
                        onProjectClick = { project ->
                            viewModel.openProject(project.id)
                            onProjectClick(project.id)
                        },
                        onDeleteClick = { project ->
                            projectToDelete = project
                        },
                        onEditClick = { project ->
                            projectToEdit = project
                        },
                        onPinClick = { project ->
                            viewModel.setProjectPinned(project.id, !project.isPinned)
                        },
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
        shape = CodeXCorners.large,
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
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.orobi),
            contentDescription = null,
            modifier = Modifier.size(width = 156.dp, height = 110.dp)
        )
        Spacer(Modifier.height(28.dp))
        Text(context.getString(R.string.home_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(context.getString(R.string.home_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onCreateClick) { Text(context.getString(R.string.home_create_project)) }
    }
}

@Composable
private fun EmptySearchState(
    query: String,
    onClear: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ExpressiveNoResults(
        query = query,
        onClear = onClear,
        noun = "projects",
        modifier = modifier
    )
}

@Composable
private fun ProjectsList(
    projects: List<Project>,
    onProjectClick: (Project) -> Unit,
    onDeleteClick: (Project) -> Unit,
    onEditClick: (Project) -> Unit,
    onPinClick: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
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
                onPinClick = { onPinClick(project) },
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
 * Swipeable project card with swipe-to-delete functionality
 * Features:
 * - Smooth swipe-to-delete with visual feedback
 * - Edit and delete options via dropdown menu
 * - Clean minimalist card design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableProjectCard(
    project: Project,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit,
    onPinClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    if (showMenu) {
        ActionSheet(
            title = project.name,
            onDismiss = { showMenu = false },
            actions = listOf(
                SheetAction(
                    label = if (project.isPinned) "Unpin" else "Pin to top",
                    // Filled when it is pinned, outlined when it is not, so
                    // the row states the current state as well as the action.
                    icon = if (project.isPinned) {
                        Icons.Filled.PushPin
                    } else {
                        Icons.Outlined.PushPin
                    },
                    description = if (project.isPinned) {
                        "Let it sort by when you last opened it"
                    } else {
                        "Keep it above the rest of the list"
                    },
                    onClick = onPinClick
                ),
                SheetAction(
                    label = "Rename",
                    icon = Icons.Outlined.Edit,
                    onClick = onEditClick
                ),
                SheetAction(
                    label = "Delete project",
                    icon = Icons.Outlined.Delete,
                    destructive = true,
                    onClick = onDeleteClick
                )
            )
        )
    }
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

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            // Delete background with gradient effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CodeXCorners.large)
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
            val interaction = remember { MutableInteractionSource() }
            Card(
                modifier = Modifier
                    .expressivePress(interaction)
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        onClick = onClick
                    ),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                // Flat. M3 separates surfaces by tonal level, not shadow, and a
                // list of shadowed cards is the fastest way to make an M3 app
                // look like Material 1.
                elevation = CardDefaults.cardElevation(defaultElevation = Elevation.flat)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Every project used to get the same folder glyph in the
                    // same rounded square, so a list of them had nothing to
                    // scan by -- only the name differed, and names in a list
                    // all read as one texture. A silhouette derived from the
                    // project id gives each one a stable, recognisable mark
                    // that survives reordering and relaunching.
                    val accentSlot = remember(project.id) {
                        (project.id.hashCode().mod(ProjectAccentCount))
                    }
                    val accent = AgentVisuals.accent(accentSlot)
                    Surface(
                        modifier = Modifier.size(Sizes.avatarLarge),
                        shape = ExpressiveShapes.forIndex(accentSlot),
                        color = accent.container
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = project.name.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = accent.onContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(Space.lg))

                    // Project info
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = project.name,
                                // Emphasized: the name is the only thing the
                                // user is looking for in this row.
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            // Without this a pinned project looks like it was
                            // opened most recently, and the user wonders why
                            // the list will not reorder.
                            if (project.isPinned) {
                                Spacer(modifier = Modifier.width(Space.xs))
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Pinned",
                                    modifier = Modifier.size(Sizes.iconSmall),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (project.description.isNotEmpty()) {
                            Text(
                                text = project.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(Space.xxs))
                        Text(
                            text = project.formattedLastOpened,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Action menu. A sheet, not a dropdown: a menu anchored
                    // to this icon opens wherever the row has scrolled to, and
                    // near the bottom of the screen it flips above the anchor
                    // and lands under the thumb that just tapped it.
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Actions for ${project.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
}

/**
 * Number of distinct project marks. Matches the palette and shape lists in
 * AgentVisuals/ExpressiveShapes so colour and silhouette stay paired.
 */
private const val ProjectAccentCount = 5

/** Quick filters offered beside the project search. */
private const val ProjectFilterAll = "all"

private val ProjectFilters = listOf(
    FilterChipItem(ProjectFilterAll, "All"),
    FilterChipItem("today", "Today", Icons.Outlined.Today),
    FilterChipItem("week", "This week", Icons.Outlined.DateRange),
    FilterChipItem("git", "Has Git", Icons.Outlined.Commit)
)

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Sort options offered in the advanced filter panel. */
private const val ProjectSortRecent = "recent"

private val ProjectSorts = listOf(
    SortOption(ProjectSortRecent, "Recent"),
    SortOption("name", "Name A-Z"),
    SortOption("template", "Template")
)
