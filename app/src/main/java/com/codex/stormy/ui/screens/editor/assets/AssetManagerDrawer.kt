package com.codex.stormy.ui.screens.editor.assets

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codex.stormy.R
import com.codex.stormy.ui.theme.PoppinsFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Asset types for categorization
 */
enum class AssetType {
    IMAGE, FONT, OTHER
}

/**
 * Data class representing an asset file
 */
data class AssetFile(
    val name: String,
    val path: String,
    val relativePath: String,
    val type: AssetType,
    val size: Long,
    val extension: String
)

/**
 * Asset Manager drawer for browsing and managing project assets
 * Provides a dedicated interface for images, fonts, and other static files
 */
@Composable
fun AssetManagerDrawer(
    projectPath: String,
    onClose: () -> Unit,
    onAssetClick: (AssetFile) -> Unit,
    onAssetDelete: (AssetFile) -> Unit,
    onAssetAdded: () -> Unit,
    onCopyPath: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // State
    var assets by remember { mutableStateOf<List<AssetFile>>(emptyList()) }
    var isGridView by remember { mutableStateOf(true) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<AssetFile?>(null) }
    var expandedSections by remember { mutableStateOf(setOf(AssetType.IMAGE, AssetType.FONT, AssetType.OTHER)) }

    // Load assets
    fun loadAssets() {
        scope.launch {
            assets = withContext(Dispatchers.IO) {
                scanAssetsFolder(projectPath)
            }
        }
    }

    // Initial load
    androidx.compose.runtime.LaunchedEffect(projectPath) {
        loadAssets()
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            scope.launch {
                copyAssetToProject(context, uri, projectPath, "images")
                loadAssets()
                onAssetAdded()
            }
        }
    }

    // General file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                copyAssetToProject(context, uri, projectPath, "assets")
                loadAssets()
                onAssetAdded()
            }
        }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.assets_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold
                )

                Row {
                    // View toggle
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                            contentDescription = if (isGridView) "List View" else "Grid View",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Add button
                    Box {
                        IconButton(
                            onClick = { showAddMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "Add Asset",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.assets_add_image)) },
                                onClick = {
                                    showAddMenu = false
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Image, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.assets_add_file)) },
                                onClick = {
                                    showAddMenu = false
                                    filePickerLauncher.launch("*/*")
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.assets_add_folder)) },
                                onClick = {
                                    showAddMenu = false
                                    showCreateFolderDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                                }
                            )
                        }
                    }

                    // Close button
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

            if (assets.isEmpty()) {
                // Empty state
                EmptyAssetsState(
                    onAddImage = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else {
                // Asset list/grid by category
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Images section
                    val images = assets.filter { it.type == AssetType.IMAGE }
                    if (images.isNotEmpty()) {
                        item {
                            AssetSectionHeader(
                                title = context.getString(R.string.assets_images),
                                count = images.size,
                                icon = Icons.Outlined.Image,
                                isExpanded = AssetType.IMAGE in expandedSections,
                                onToggle = {
                                    expandedSections = if (AssetType.IMAGE in expandedSections) {
                                        expandedSections - AssetType.IMAGE
                                    } else {
                                        expandedSections + AssetType.IMAGE
                                    }
                                }
                            )
                        }

                        if (AssetType.IMAGE in expandedSections) {
                            if (isGridView) {
                                item {
                                    AssetGridSection(
                                        assets = images,
                                        projectPath = projectPath,
                                        onAssetClick = onAssetClick,
                                        onCopyPath = { asset ->
                                            clipboardManager.setText(AnnotatedString(asset.relativePath))
                                            onCopyPath(asset.relativePath)
                                        },
                                        onDelete = { showDeleteDialog = it }
                                    )
                                }
                            } else {
                                items(images) { asset ->
                                    AssetListItem(
                                        asset = asset,
                                        projectPath = projectPath,
                                        onClick = { onAssetClick(asset) },
                                        onCopyPath = {
                                            clipboardManager.setText(AnnotatedString(asset.relativePath))
                                            onCopyPath(asset.relativePath)
                                        },
                                        onDelete = { showDeleteDialog = asset }
                                    )
                                }
                            }
                        }
                    }

                    // Fonts section
                    val fonts = assets.filter { it.type == AssetType.FONT }
                    if (fonts.isNotEmpty()) {
                        item {
                            AssetSectionHeader(
                                title = context.getString(R.string.assets_fonts),
                                count = fonts.size,
                                icon = Icons.Outlined.FontDownload,
                                isExpanded = AssetType.FONT in expandedSections,
                                onToggle = {
                                    expandedSections = if (AssetType.FONT in expandedSections) {
                                        expandedSections - AssetType.FONT
                                    } else {
                                        expandedSections + AssetType.FONT
                                    }
                                }
                            )
                        }

                        if (AssetType.FONT in expandedSections) {
                            items(fonts) { asset ->
                                AssetListItem(
                                    asset = asset,
                                    projectPath = projectPath,
                                    onClick = { onAssetClick(asset) },
                                    onCopyPath = {
                                        clipboardManager.setText(AnnotatedString(asset.relativePath))
                                        onCopyPath(asset.relativePath)
                                    },
                                    onDelete = { showDeleteDialog = asset }
                                )
                            }
                        }
                    }

                    // Other files section
                    val other = assets.filter { it.type == AssetType.OTHER }
                    if (other.isNotEmpty()) {
                        item {
                            AssetSectionHeader(
                                title = context.getString(R.string.assets_other),
                                count = other.size,
                                icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                                isExpanded = AssetType.OTHER in expandedSections,
                                onToggle = {
                                    expandedSections = if (AssetType.OTHER in expandedSections) {
                                        expandedSections - AssetType.OTHER
                                    } else {
                                        expandedSections + AssetType.OTHER
                                    }
                                }
                            )
                        }

                        if (AssetType.OTHER in expandedSections) {
                            items(other) { asset ->
                                AssetListItem(
                                    asset = asset,
                                    projectPath = projectPath,
                                    onClick = { onAssetClick(asset) },
                                    onCopyPath = {
                                        clipboardManager.setText(AnnotatedString(asset.relativePath))
                                        onCopyPath(asset.relativePath)
                                    },
                                    onDelete = { showDeleteDialog = asset }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create folder dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { folderName ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val assetsDir = File(projectPath, "assets")
                        if (!assetsDir.exists()) {
                            assetsDir.mkdirs()
                        }
                        File(assetsDir, folderName).mkdirs()
                    }
                    loadAssets()
                }
                showCreateFolderDialog = false
            }
        )
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { asset ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(context.getString(R.string.file_delete)) },
            text = { Text(context.getString(R.string.file_delete_confirm, asset.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                File(asset.path).delete()
                            }
                            loadAssets()
                            onAssetDelete(asset)
                        }
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
private fun EmptyAssetsState(
    onAddImage: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = context.getString(R.string.assets_empty),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = PoppinsFontFamily,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = context.getString(R.string.assets_empty_subtitle),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = PoppinsFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            onClick = onAddImage,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = context.getString(R.string.assets_add_image),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun AssetSectionHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        label = "section_rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun AssetGridSection(
    assets: List<AssetFile>,
    projectPath: String,
    onAssetClick: (AssetFile) -> Unit,
    onCopyPath: (AssetFile) -> Unit,
    onDelete: (AssetFile) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height((((assets.size + 2) / 3) * 100).dp.coerceAtMost(300.dp)),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        items(assets) { asset ->
            AssetGridItem(
                asset = asset,
                projectPath = projectPath,
                onClick = { onAssetClick(asset) },
                onCopyPath = { onCopyPath(asset) },
                onDelete = { onDelete(asset) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetGridItem(
    asset: AssetFile,
    projectPath: String,
    onClick: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box {
        Surface(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (asset.type == AssetType.IMAGE) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(asset.path))
                            .crossfade(true)
                            .build(),
                        contentDescription = asset.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = getAssetIcon(asset.type),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = asset.extension.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(context.getString(R.string.assets_copy_path)) },
                onClick = {
                    showMenu = false
                    onCopyPath()
                },
                leadingIcon = {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        context.getString(R.string.file_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    showMenu = false
                    onDelete()
                },
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetListItem(
    asset: AssetFile,
    projectPath: String,
    onClick: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail or icon
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            if (asset.type == AssetType.IMAGE) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(asset.path))
                        .crossfade(true)
                        .build(),
                    contentDescription = asset.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getAssetIcon(asset.type),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = PoppinsFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFileSize(asset.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Context menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy path",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(context.getString(R.string.assets_copy_path)) },
                    onClick = {
                        showMenu = false
                        onCopyPath()
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            context.getString(R.string.file_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
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
    }
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    val context = LocalContext.current
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.assets_add_folder)) },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text(context.getString(R.string.file_name_label)) },
                placeholder = { Text("images") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text(context.getString(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.action_cancel))
            }
        }
    )
}

// Helper functions

private fun getAssetIcon(type: AssetType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        AssetType.IMAGE -> Icons.Outlined.Image
        AssetType.FONT -> Icons.Outlined.FontDownload
        AssetType.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
    }
}

private fun getAssetType(extension: String): AssetType {
    return when (extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico" -> AssetType.IMAGE
        "ttf", "otf", "woff", "woff2", "eot" -> AssetType.FONT
        else -> AssetType.OTHER
    }
}

private fun scanAssetsFolder(projectPath: String): List<AssetFile> {
    val assets = mutableListOf<AssetFile>()
    val projectDir = File(projectPath)

    // Scan common asset directories
    val assetDirs = listOf("assets", "images", "img", "fonts", "media", "static")

    assetDirs.forEach { dirName ->
        val dir = File(projectDir, dirName)
        if (dir.exists() && dir.isDirectory) {
            scanDirectory(dir, projectDir, assets)
        }
    }

    // Also scan root for any image/font files
    projectDir.listFiles()?.forEach { file ->
        if (file.isFile) {
            val ext = file.extension.lowercase()
            val type = getAssetType(ext)
            if (type == AssetType.IMAGE || type == AssetType.FONT) {
                assets.add(
                    AssetFile(
                        name = file.name,
                        path = file.absolutePath,
                        relativePath = file.name,
                        type = type,
                        size = file.length(),
                        extension = ext
                    )
                )
            }
        }
    }

    return assets.distinctBy { it.path }
}

private fun scanDirectory(dir: File, projectRoot: File, assets: MutableList<AssetFile>) {
    dir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            scanDirectory(file, projectRoot, assets)
        } else if (file.isFile) {
            val ext = file.extension.lowercase()
            val type = getAssetType(ext)
            val relativePath = file.absolutePath.removePrefix(projectRoot.absolutePath + "/")

            assets.add(
                AssetFile(
                    name = file.name,
                    path = file.absolutePath,
                    relativePath = relativePath,
                    type = type,
                    size = file.length(),
                    extension = ext
                )
            )
        }
    }
}

private suspend fun copyAssetToProject(
    context: android.content.Context,
    uri: Uri,
    projectPath: String,
    targetFolder: String
) {
    withContext(Dispatchers.IO) {
        try {
            // Create target directory if needed
            val targetDir = File(projectPath, targetFolder)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            // Get file name from URI
            val mimeType = context.contentResolver.getType(uri)
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"

            // Generate unique filename
            val fileName = "asset_${System.currentTimeMillis()}.$extension"
            val destFile = File(targetDir, fileName)

            // Copy file
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            // Silently fail - could add error handling/callback if needed
        }
    }
}
