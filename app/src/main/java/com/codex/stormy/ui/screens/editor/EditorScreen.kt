package com.codex.stormy.ui.screens.editor

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.stormy.R
import com.codex.stormy.ui.screens.editor.chat.ChatTab
import com.codex.stormy.ui.screens.editor.code.CodeTab
import com.codex.stormy.ui.screens.editor.filetree.FileTreeDrawer
import com.codex.stormy.ui.screens.preview.PreviewActivity
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FileTreeDrawer(
                fileTree = uiState.fileTree,
                expandedFolders = uiState.expandedFolders,
                selectedFilePath = uiState.currentFile?.path,
                onFileClick = { file ->
                    viewModel.openFile(file.path)
                    scope.launch { drawerState.close() }
                },
                onFolderToggle = viewModel::toggleFolder,
                onCreateFile = viewModel::createFile,
                onCreateFolder = viewModel::createFolder,
                onDeleteFile = viewModel::deleteFile,
                onRenameFile = viewModel::renameFile,
                onClose = { scope.launch { drawerState.close() } }
            )
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            topBar = {
                EditorTopBar(
                    projectName = uiState.project?.name ?: "",
                    onBackClick = onBackClick,
                    onMenuClick = { scope.launch { drawerState.open() } },
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
                            onInputChange = viewModel::updateChatInput,
                            onSendMessage = viewModel::sendMessage
                        )
                        EditorTab.CODE -> CodeTab(
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

@Composable
private fun EditorTopBar(
    projectName: String,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
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
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "Files"
                )
            }
            IconButton(onClick = onPreviewClick) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = "Preview"
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings"
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
