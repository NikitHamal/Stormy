package com.oro.app.ui.screens.memories

import com.oro.app.ui.theme.Space
import com.oro.app.ui.components.expressive.FlowerIconButton
import com.oro.app.ui.components.expressive.ExpressiveSearchTopBar
import com.oro.app.ui.components.expressive.EmptyStateShape
import com.oro.app.ui.components.expressive.ExpressiveNoResults
import com.oro.app.ui.components.expressive.ExpressiveEmptyState
import com.oro.app.ui.components.expressive.SearchPill
import com.oro.app.ui.theme.OroCorners
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import com.oro.app.ui.components.expressive.FormSheet
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oro.app.ui.components.expressive.ExpressiveLoadingIndicator
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Memory Management Screen
 * Displays and manages AI agent memories organized by project
 */
@Composable
fun MemoriesScreen(
    onBackClick: () -> Unit,
    viewModel: MemoriesViewModel = viewModel(factory = MemoriesViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf<MemoryItemState?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            // The same cross-fading bar as Home and AI Models. Memories had an
            // always-mounted search field below a TopAppBar, which cost a row
            // on every visit and was a third different-looking search.
            ExpressiveSearchTopBar(
                searchActive = searchActive,
                onSearchActiveChange = { searchActive = it },
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search memories",
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
                                text = "Memories",
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "What the assistant has learned",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    FlowerIconButton(
                        icon = Icons.Outlined.Search,
                        contentDescription = "Search memories",
                        onClick = { searchActive = true },
                        size = 44.dp
                    )
                }
            )
        },
        floatingActionButton = {
            if (uiState.selectedProject != null) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add memory"
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
            when {
                uiState.isLoading -> {
                    LoadingState(modifier = Modifier.fillMaxSize())
                }
                uiState.projectMemories.isEmpty() && searchQuery.isNotBlank() -> {
                    ExpressiveNoResults(
                        query = searchQuery,
                        onClear = { searchQuery = "" },
                        noun = "memories",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                uiState.projectMemories.isEmpty() -> {
                    EmptyState(modifier = Modifier.fillMaxSize())
                }
                else -> {
                    MemoriesList(
                        projectMemories = uiState.projectMemories.filter { projectMemory ->
                            if (searchQuery.isBlank()) true
                            else projectMemory.memories.any { memory ->
                                memory.key.contains(searchQuery, ignoreCase = true) ||
                                memory.value.contains(searchQuery, ignoreCase = true)
                            }
                        },
                        selectedProject = uiState.selectedProject,
                        onProjectSelect = { viewModel.selectProject(it) },
                        onEditMemory = { projectId, memory ->
                            viewModel.startEditingMemory(projectId, memory)
                        },
                        onDeleteMemory = { projectId, memory ->
                            showDeleteConfirmation = MemoryItemState(projectId, memory)
                        },
                        editingMemory = uiState.editingMemory,
                        onSaveEdit = { key, value ->
                            viewModel.saveMemoryEdit(key, value)
                        },
                        onCancelEdit = { viewModel.cancelEditing() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Add memory dialog
        if (showAddDialog && uiState.selectedProject != null) {
            AddMemoryDialog(
                projectName = uiState.projectMemories.find {
                    it.projectId == uiState.selectedProject
                }?.projectName ?: "Project",
                onDismiss = { showAddDialog = false },
                onAdd = { key, value ->
                    viewModel.addMemory(key, value)
                    showAddDialog = false
                }
            )
        }

        // Delete confirmation dialog
        showDeleteConfirmation?.let { memoryItem ->
            DeleteConfirmationDialog(
                memoryKey = memoryItem.memory.key,
                onDismiss = { showDeleteConfirmation = null },
                onConfirm = {
                    viewModel.deleteMemory(memoryItem.projectId, memoryItem.memory.key)
                    showDeleteConfirmation = null
                }
            )
        }
    }
}

/**
 * Search for filtering memories.
 *
 * Delegates to the shared pill rather than hand-rolling a third variant of
 * the same control.
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SearchPill(
        query = query,
        onQueryChange = onQueryChange,
        onClose = { onQueryChange("") },
        placeholder = "Search memories",
        modifier = modifier
    )
}

/**
 * Main memories list organized by project
 */
@Composable
private fun MemoriesList(
    projectMemories: List<ProjectMemoryState>,
    selectedProject: String?,
    onProjectSelect: (String?) -> Unit,
    onEditMemory: (String, MemoryState) -> Unit,
    onDeleteMemory: (String, MemoryState) -> Unit,
    editingMemory: EditingMemoryState?,
    onSaveEdit: (String, String) -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = projectMemories,
            key = { it.projectId }
        ) { projectMemory ->
            ProjectMemoryCard(
                projectMemory = projectMemory,
                isExpanded = selectedProject == projectMemory.projectId,
                onToggleExpand = {
                    onProjectSelect(
                        if (selectedProject == projectMemory.projectId) null
                        else projectMemory.projectId
                    )
                },
                onEditMemory = { memory -> onEditMemory(projectMemory.projectId, memory) },
                onDeleteMemory = { memory -> onDeleteMemory(projectMemory.projectId, memory) },
                editingMemory = if (editingMemory?.projectId == projectMemory.projectId)
                    editingMemory else null,
                onSaveEdit = onSaveEdit,
                onCancelEdit = onCancelEdit
            )
        }
    }
}

/**
 * Card for project memories with expandable content
 */
@Composable
private fun ProjectMemoryCard(
    projectMemory: ProjectMemoryState,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEditMemory: (MemoryState) -> Unit,
    onDeleteMemory: (MemoryState) -> Unit,
    editingMemory: EditingMemoryState?,
    onSaveEdit: (String, String) -> Unit,
    onCancelEdit: () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = OroCorners.large
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Project icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(OroCorners.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = projectMemory.projectName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${projectMemory.memories.size} memories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Expand/collapse indicator
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotationAngle)
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 }
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    if (projectMemory.memories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No memories yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "The AI will learn as you work",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            projectMemory.memories.forEach { memory ->
                                MemoryItem(
                                    memory = memory,
                                    isEditing = editingMemory?.key == memory.key,
                                    editValue = if (editingMemory?.key == memory.key)
                                        editingMemory.editedValue else memory.value,
                                    onEditValueChange = { /* handled by ViewModel */ },
                                    onEdit = { onEditMemory(memory) },
                                    onDelete = { onDeleteMemory(memory) },
                                    onSave = { onSaveEdit(memory.key, it) },
                                    onCancel = onCancelEdit
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual memory item with edit/delete actions
 */
@Composable
private fun MemoryItem(
    memory: MemoryState,
    isEditing: Boolean,
    editValue: String,
    onEditValueChange: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var localEditValue by remember(isEditing, editValue) { mutableStateOf(editValue) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = OroCorners.medium,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Memory key badge
                Box(
                    modifier = Modifier
                        .clip(OroCorners.small)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = memory.key,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Timestamp
                Text(
                    text = formatTimestamp(memory.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isEditing) {
                // Edit mode
                OutlinedTextField(
                    value = localEditValue,
                    onValueChange = { localEditValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    minLines = 2,
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onSave(localEditValue) }) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            } else {
                // View mode
                Text(
                    text = memory.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog for adding new memory
 */
@Composable
private fun AddMemoryDialog(
    projectName: String,
    onDismiss: () -> Unit,
    onAdd: (key: String, value: String) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }

    FormSheet(
        title = "Add memory",
        icon = Icons.Outlined.Memory,
        onDismiss = onDismiss,
        confirmLabel = "Add",
        onConfirm = {
            if (key.isBlank()) {
                keyError = "Key is required"
            } else if (!key.matches(Regex("^[a-z_][a-z0-9_]*$"))) {
                keyError = "Use lowercase letters, numbers, and underscores"
            } else {
                onAdd(key, value)
            }
        },
        confirmEnabled = key.isNotBlank()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            Text(
                text = "Add a new memory for $projectName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    keyError = null
                },
                label = { Text("Key") },
                placeholder = { Text("e.g., preferred_framework") },
                isError = keyError != null,
                supportingText = keyError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = OroCorners.large
            )

            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Value") },
                placeholder = { Text("e.g., React with TypeScript") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                shape = OroCorners.large
            )
        }
    }
}

/**
 * Confirmation dialog for deleting memory
 */
@Composable
private fun DeleteConfirmationDialog(
    memoryKey: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    FormSheet(
        title = "Delete memory",
        icon = Icons.Outlined.DeleteOutline,
        onDismiss = onDismiss,
        confirmLabel = "Delete",
        onConfirm = onConfirm,
        destructive = true
    ) {
        Text(
            text = "\"$memoryKey\" will be forgotten. This cannot be undone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        ExpressiveLoadingIndicator()
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    ExpressiveEmptyState(
        icon = Icons.Outlined.Memory,
        title = "No memories yet",
        description = "The assistant will note what it learns about your " +
            "projects as you work together.",
        // A different silhouette per screen, so empty states are told apart
        // at a glance rather than all reading as the same blank page.
        shape = EmptyStateShape.Clover,
        modifier = modifier
    )
}

/**
 * Format timestamp to readable string
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * Helper class for delete confirmation
 */
private data class MemoryItemState(
    val projectId: String,
    val memory: MemoryState
)
