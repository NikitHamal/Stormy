package com.codex.stormy.ui.screens.editor.filetree

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.stormy.R
import com.codex.stormy.domain.model.FileTreeNode
import com.codex.stormy.domain.model.FileType

@Composable
fun FileTreeDrawer(
    fileTree: List<FileTreeNode>,
    expandedFolders: Set<String>,
    selectedFilePath: String?,
    onFileClick: (FileTreeNode.FileNode) -> Unit,
    onFolderToggle: (String) -> Unit,
    onCreateFile: (parentPath: String, fileName: String) -> Unit,
    onCreateFolder: (parentPath: String, folderName: String) -> Unit,
    onDeleteFile: (path: String) -> Unit,
    onRenameFile: (oldPath: String, newName: String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var showCreateFileDialog by remember { mutableStateOf<String?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf<FileTreeNode?>(null) }
    var showDeleteDialog by remember { mutableStateOf<FileTreeNode?>(null) }

    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.editor_files),
                    style = MaterialTheme.typography.titleMedium
                )

                Row {
                    IconButton(
                        onClick = { showCreateFileDialog = "" },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.NoteAdd,
                            contentDescription = "New File",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showCreateFolderDialog = "" },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CreateNewFolder,
                            contentDescription = "New Folder",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(fileTree) { node ->
                    FileTreeItem(
                        node = node,
                        expandedFolders = expandedFolders,
                        selectedFilePath = selectedFilePath,
                        onFileClick = onFileClick,
                        onFolderToggle = onFolderToggle,
                        onCreateFile = { showCreateFileDialog = it },
                        onCreateFolder = { showCreateFolderDialog = it },
                        onRename = { showRenameDialog = it },
                        onDelete = { showDeleteDialog = it }
                    )
                }
            }
        }
    }

    showCreateFileDialog?.let { parentPath ->
        CreateItemDialog(
            title = context.getString(R.string.file_new_file),
            label = context.getString(R.string.file_name_label),
            hint = "index.html",
            onDismiss = { showCreateFileDialog = null },
            onConfirm = { name ->
                onCreateFile(parentPath, name)
                showCreateFileDialog = null
            }
        )
    }

    showCreateFolderDialog?.let { parentPath ->
        CreateItemDialog(
            title = context.getString(R.string.file_new_folder),
            label = context.getString(R.string.file_name_label),
            hint = "assets",
            onDismiss = { showCreateFolderDialog = null },
            onConfirm = { name ->
                onCreateFolder(parentPath, name)
                showCreateFolderDialog = null
            }
        )
    }

    showRenameDialog?.let { node ->
        CreateItemDialog(
            title = context.getString(R.string.file_rename),
            label = context.getString(R.string.file_name_label),
            hint = node.name,
            initialValue = node.name,
            onDismiss = { showRenameDialog = null },
            onConfirm = { newName ->
                onRenameFile(node.path, newName)
                showRenameDialog = null
            }
        )
    }

    showDeleteDialog?.let { node ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(context.getString(R.string.file_delete)) },
            text = { Text(context.getString(R.string.file_delete_confirm, node.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFile(node.path)
                        showDeleteDialog = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(context.getString(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(context.getString(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun FileTreeItem(
    node: FileTreeNode,
    expandedFolders: Set<String>,
    selectedFilePath: String?,
    onFileClick: (FileTreeNode.FileNode) -> Unit,
    onFolderToggle: (String) -> Unit,
    onCreateFile: (parentPath: String) -> Unit,
    onCreateFolder: (parentPath: String) -> Unit,
    onRename: (FileTreeNode) -> Unit,
    onDelete: (FileTreeNode) -> Unit,
    depth: Int = 0
) {
    var showMenu by remember { mutableStateOf(false) }

    when (node) {
        is FileTreeNode.FolderNode -> {
            val isExpanded = node.path in expandedFolders
            val rotation by animateFloatAsState(
                targetValue = if (isExpanded) 0f else -90f,
                label = "folder_rotation"
            )

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFolderToggle(node.path) }
                        .padding(
                            start = (16 + depth * 16).dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FileTreeContextMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            isFolder = true,
                            onCreateFile = {
                                showMenu = false
                                onCreateFile(node.path)
                            },
                            onCreateFolder = {
                                showMenu = false
                                onCreateFolder(node.path)
                            },
                            onRename = {
                                showMenu = false
                                onRename(node)
                            },
                            onDelete = {
                                showMenu = false
                                onDelete(node)
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        node.children.forEach { child ->
                            FileTreeItem(
                                node = child,
                                expandedFolders = expandedFolders,
                                selectedFilePath = selectedFilePath,
                                onFileClick = onFileClick,
                                onFolderToggle = onFolderToggle,
                                onCreateFile = onCreateFile,
                                onCreateFolder = onCreateFolder,
                                onRename = onRename,
                                onDelete = onDelete,
                                depth = depth + 1
                            )
                        }
                    }
                }
            }
        }

        is FileTreeNode.FileNode -> {
            val isSelected = node.path == selectedFilePath

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isSelected) {
                            Modifier.background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        } else Modifier
                    )
                    .clickable { onFileClick(node) }
                    .padding(
                        start = (38 + depth * 16).dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = getFileColor(node.fileType)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FileTreeContextMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        isFolder = false,
                        onCreateFile = null,
                        onCreateFolder = null,
                        onRename = {
                            showMenu = false
                            onRename(node)
                        },
                        onDelete = {
                            showMenu = false
                            onDelete(node)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileTreeContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isFolder: Boolean,
    onCreateFile: (() -> Unit)?,
    onCreateFolder: (() -> Unit)?,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (isFolder && onCreateFile != null) {
            DropdownMenuItem(
                text = { Text(context.getString(R.string.file_new_file)) },
                onClick = onCreateFile,
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null)
                }
            )
        }

        if (isFolder && onCreateFolder != null) {
            DropdownMenuItem(
                text = { Text(context.getString(R.string.file_new_folder)) },
                onClick = onCreateFolder,
                leadingIcon = {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                }
            )
        }

        DropdownMenuItem(
            text = { Text(context.getString(R.string.file_rename)) },
            onClick = onRename,
            leadingIcon = {
                Icon(Icons.Outlined.Edit, contentDescription = null)
            }
        )

        DropdownMenuItem(
            text = {
                Text(
                    context.getString(R.string.file_delete),
                    color = MaterialTheme.colorScheme.error
                )
            },
            onClick = onDelete,
            leadingIcon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        )
    }
}

@Composable
private fun CreateItemDialog(
    title: String,
    label: String,
    hint: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                placeholder = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun getFileColor(fileType: FileType): Color {
    return when (fileType) {
        FileType.HTML -> Color(0xFFE34C26)
        FileType.CSS -> Color(0xFF264DE4)
        FileType.JAVASCRIPT -> Color(0xFFF7DF1E)
        FileType.JSON -> Color(0xFF5B5B5B)
        FileType.MARKDOWN -> Color(0xFF083FA1)
        FileType.IMAGE -> Color(0xFF4CAF50)
        FileType.FONT -> Color(0xFF9C27B0)
        FileType.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
