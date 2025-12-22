package com.codex.stormy.ui.screens.editor

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.stormy.R
import com.codex.stormy.ui.screens.editor.assets.AssetManagerDrawer
import com.codex.stormy.ui.screens.editor.chat.ChatTab
import com.codex.stormy.ui.screens.editor.code.CodeTab
import com.codex.stormy.ui.screens.editor.filetree.FileTreeDrawer
import com.codex.stormy.ui.screens.git.GitDrawer
import com.codex.stormy.ui.screens.preview.PreviewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class EditorTab {
    CHAT, CODE
}

@Composable
fun EditorScreen(
    projectId: String,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: EditorViewModel = viewModel(
        factory = EditorViewModel.Factory,
        key = projectId
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fileDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val assetDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val gitDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Track if any drawer is open for back handler
    val isAnyDrawerOpen by remember {
        derivedStateOf {
            fileDrawerState.isOpen || assetDrawerState.isOpen || gitDrawerState.isOpen
        }
    }

    // Ensure all drawers start closed - critical fix for auto-opening issue
    LaunchedEffect(Unit) {
        // Force close all drawers on initial composition
        fileDrawerState.snapTo(DrawerValue.Closed)
        assetDrawerState.snapTo(DrawerValue.Closed)
        gitDrawerState.snapTo(DrawerValue.Closed)
    }

    // Back button handler - closes drawers first before navigating back
    BackHandler(enabled = isAnyDrawerOpen) {
        scope.launch {
            closeAllDrawers(
                scope = scope,
                fileDrawerState = fileDrawerState,
                assetDrawerState = assetDrawerState,
                gitDrawerState = gitDrawerState
            )
        }
    }

    // File Tree drawer (start-side drawer - outer for proper gesture handling)
    ModalNavigationDrawer(
        drawerState = fileDrawerState,
        drawerContent = {
            // Only render drawer content when drawer is being shown to prevent unnecessary composition
            if (fileDrawerState.isOpen || fileDrawerState.isAnimationRunning) {
                FileTreeDrawer(
                    fileTree = uiState.fileTree,
                    expandedFolders = uiState.expandedFolders,
                    selectedFilePath = uiState.currentFile?.path,
                    onFileClick = { file ->
                        viewModel.openFile(file.path)
                        scope.launch { fileDrawerState.close() }
                    },
                    onFolderToggle = viewModel::toggleFolder,
                    onCreateFile = viewModel::createFile,
                    onCreateFolder = viewModel::createFolder,
                    onDeleteFile = viewModel::deleteFile,
                    onRenameFile = viewModel::renameFile,
                    onClose = { scope.launch { fileDrawerState.close() } }
                )
            }
        },
        gesturesEnabled = fileDrawerState.isOpen // Only enable gestures when drawer is open
    ) {
        // Git drawer (end-side drawer - no gestures to avoid conflicts)
        ModalNavigationDrawer(
            drawerState = gitDrawerState,
            drawerContent = {
                // Only render when drawer is being shown
                if (gitDrawerState.isOpen || gitDrawerState.isAnimationRunning) {
                    uiState.project?.let { project ->
                        GitDrawer(
                            projectPath = project.rootPath,
                            onClose = { scope.launch { gitDrawerState.close() } }
                        )
                    }
                }
            },
            gesturesEnabled = false,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
        ) {
            // Asset Manager drawer (inner drawer - no gestures to avoid conflicts)
            ModalNavigationDrawer(
                drawerState = assetDrawerState,
                drawerContent = {
                    // Only render when drawer is being shown
                    if (assetDrawerState.isOpen || assetDrawerState.isAnimationRunning) {
                        uiState.project?.let { project ->
                            AssetManagerDrawer(
                                projectPath = project.rootPath,
                                onClose = { scope.launch { assetDrawerState.close() } },
                                onAssetClick = { asset ->
                                    // Open asset file in code editor if it's a text-based file
                                    val textExtensions = listOf("svg", "json", "xml", "txt", "md")
                                    if (asset.extension.lowercase() in textExtensions) {
                                        viewModel.openFile(asset.path)
                                        scope.launch { assetDrawerState.close() }
                                    }
                                },
                                onAssetDelete = { asset ->
                                    // Refresh file tree after deletion
                                    viewModel.refreshFileTree()
                                },
                                onAssetAdded = {
                                    // Refresh file tree after adding asset
                                    viewModel.refreshFileTree()
                                },
                                onCopyPath = { path ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.assets_path_copied),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                },
                gesturesEnabled = false
            ) {
                Scaffold(
                    topBar = {
                        EditorTopBar(
                            projectName = uiState.project?.name ?: "",
                            onBackClick = {
                                // Close drawers first if any are open, otherwise navigate back
                                if (isAnyDrawerOpen) {
                                    scope.launch {
                                        closeAllDrawers(
                                            scope = scope,
                                            fileDrawerState = fileDrawerState,
                                            assetDrawerState = assetDrawerState,
                                            gitDrawerState = gitDrawerState
                                        )
                                    }
                                } else {
                                    onBackClick()
                                }
                            },
                            onMenuClick = {
                                scope.launch {
                                    // Close other drawers first
                                    if (gitDrawerState.isOpen) gitDrawerState.close()
                                    if (assetDrawerState.isOpen) assetDrawerState.close()
                                    fileDrawerState.open()
                                }
                            },
                            onAssetsClick = {
                                scope.launch {
                                    // Close other drawers first
                                    if (fileDrawerState.isOpen) fileDrawerState.close()
                                    if (gitDrawerState.isOpen) gitDrawerState.close()
                                    assetDrawerState.open()
                                }
                            },
                            onGitClick = {
                                scope.launch {
                                    // Close other drawers first
                                    if (fileDrawerState.isOpen) fileDrawerState.close()
                                    if (assetDrawerState.isOpen) assetDrawerState.close()
                                    gitDrawerState.open()
                                }
                            },
                            onPreviewClick = {
                                uiState.project?.let { project ->
                                    val intent = Intent(context, PreviewActivity::class.java).apply {
                                        putExtra(PreviewActivity.EXTRA_PROJECT_ID, project.id)
                                        putExtra(PreviewActivity.EXTRA_PROJECT_PATH, project.rootPath)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            onSettingsClick = onSettingsClick
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        EditorTabs(
                            selectedTab = uiState.selectedTab,
                            onTabSelected = viewModel::selectTab
                        )

                        AnimatedContent(
                            targetState = uiState.selectedTab,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "editor_tab_content"
                        ) { tab ->
                            when (tab) {
                                EditorTab.CHAT -> ChatTab(
                                    messages = uiState.messages,
                                    inputText = uiState.chatInput,
                                    isLoading = uiState.isAiProcessing,
                                    agentMode = uiState.agentMode,
                                    taskList = uiState.taskList,
                                    currentModel = uiState.currentModel,
                                    availableModels = uiState.availableModels,
                                    onInputChange = viewModel::updateChatInput,
                                    onSendMessage = viewModel::sendMessage,
                                    onToggleAgentMode = viewModel::toggleAgentMode,
                                    onModelChange = viewModel::setModel
                                )
                                EditorTab.CODE -> Column(modifier = Modifier.fillMaxSize()) {
                                    // File tabs row
                                    if (uiState.openFiles.isNotEmpty()) {
                                        FileTabsRow(
                                            openFiles = uiState.openFiles,
                                            currentIndex = uiState.currentFileIndex,
                                            onTabClick = viewModel::switchToFileTab,
                                            onTabClose = viewModel::closeFileTab,
                                            onCloseOthers = viewModel::closeOtherTabs,
                                            onCloseAll = viewModel::closeAllTabs
                                        )
                                    }

                                    CodeTab(
                                        currentFile = uiState.currentFile,
                                        fileContent = uiState.fileContent,
                                        isModified = uiState.isFileModified,
                                        lineNumbers = uiState.showLineNumbers,
                                        wordWrap = uiState.wordWrap,
                                        fontSize = uiState.fontSize,
                                        onContentChange = viewModel::updateFileContent,
                                        onSave = viewModel::saveCurrentFile
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Helper function to close all drawers
 */
private suspend fun closeAllDrawers(
    scope: CoroutineScope,
    fileDrawerState: DrawerState,
    assetDrawerState: DrawerState,
    gitDrawerState: DrawerState
) {
    // Close drawers in order of priority (innermost first to prevent visual glitches)
    if (assetDrawerState.isOpen) {
        assetDrawerState.close()
    }
    if (gitDrawerState.isOpen) {
        gitDrawerState.close()
    }
    if (fileDrawerState.isOpen) {
        fileDrawerState.close()
    }
}

@Composable
private fun EditorTopBar(
    projectName: String,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    onAssetsClick: () -> Unit,
    onGitClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val iconSize = 20.dp // Reduced from 24dp (default) by 4dp

    TopAppBar(
        title = {
            Text(
                text = projectName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(iconSize)
                )
            }
        },
        actions = {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(40.dp) // Reduced from 48dp by 8dp for tighter spacing
            ) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "Files",
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(
                onClick = onAssetsClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "Assets",
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(
                onClick = onGitClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = "Source Control",
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(
                onClick = onPreviewClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = "Preview",
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(iconSize)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun EditorTabs(
    selectedTab: EditorTab,
    onTabSelected: (EditorTab) -> Unit
) {
    val context = LocalContext.current

    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Tab(
            selected = selectedTab == EditorTab.CHAT,
            onClick = { onTabSelected(EditorTab.CHAT) },
            text = { Text(context.getString(R.string.editor_tab_chat)) }
        )
        Tab(
            selected = selectedTab == EditorTab.CODE,
            onClick = { onTabSelected(EditorTab.CODE) },
            text = { Text(context.getString(R.string.editor_tab_code)) }
        )
    }
}

@Composable
private fun FileTabsRow(
    openFiles: List<OpenFileTab>,
    currentIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onCloseAll: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            openFiles.forEachIndexed { index, file ->
                FileTab(
                    file = file,
                    isSelected = index == currentIndex,
                    onClick = { onTabClick(index) },
                    onClose = { onTabClose(index) },
                    onCloseOthers = { onCloseOthers(index) },
                    onCloseAll = onCloseAll
                )
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun FileTab(
    file: OpenFileTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseAll: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // File icon based on extension
            FileTypeIcon(
                extension = file.extension,
                modifier = Modifier.size(16.dp)
            )

            // File name with modified indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (file.isModified) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                Text(
                    text = file.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Close button
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Close") },
                        onClick = {
                            showMenu = false
                            onClose()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Close Others") },
                        onClick = {
                            showMenu = false
                            onCloseOthers()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Close All") },
                        onClick = {
                            showMenu = false
                            onCloseAll()
                        }
                    )
                }
            }
        }

        // Bottom border for selected tab
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }

    // Separator between tabs
    if (!isSelected) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun FileTypeIcon(
    extension: String,
    modifier: Modifier = Modifier
) {
    val iconColor = when (extension.lowercase()) {
        "html", "htm" -> MaterialTheme.colorScheme.error
        "css" -> MaterialTheme.colorScheme.primary
        "js", "jsx", "mjs" -> MaterialTheme.colorScheme.tertiary
        "ts", "tsx" -> MaterialTheme.colorScheme.primary
        "json" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(iconColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = extension.take(2).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = iconColor,
            maxLines = 1
        )
    }
}
