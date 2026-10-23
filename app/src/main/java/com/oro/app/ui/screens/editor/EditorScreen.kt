package com.oro.app.ui.screens.editor

import com.oro.app.ui.brand.OrobiColors
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.LocalContentColor
import com.oro.app.ui.brand.OrobiMascot
import com.oro.app.ui.brand.OrobiPose
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.components.agent.ApprovalSheet
import com.oro.app.ui.theme.Space
import com.oro.app.ui.components.agent.PlanTimeline
import com.oro.app.ui.components.expressive.ToggleOption
import com.oro.app.ui.components.expressive.ExpressiveToggleGroup
import androidx.compose.material.icons.outlined.Code
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.CloudUpload
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.components.expressive.SheetAction
import com.oro.app.ui.components.expressive.ActionSheet
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
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
import com.oro.app.R
import com.oro.app.ui.screens.editor.assets.AssetManagerDrawer
import com.oro.app.ui.screens.editor.chat.ChatTab
import com.oro.app.ui.screens.editor.sessions.ChatSessionActions
import com.oro.app.ui.screens.editor.sessions.SessionsSheet
import com.oro.app.ui.screens.editor.code.AiCodeEditRequest
import com.oro.app.ui.screens.editor.code.CodeTab
import com.oro.app.ui.screens.editor.filetree.FileTreeDrawer
import com.oro.app.ui.screens.git.GitDrawer
import com.oro.app.ui.screens.preview.PreviewActivity
import androidx.compose.ui.text.font.FontWeight
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
    onGitSettingsClick: () -> Unit = {},
    onTimelineClick: () -> Unit = {},
    viewModel: EditorViewModel = viewModel(
        factory = EditorViewModel.Factory,
        key = projectId
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val sessionUsage by viewModel.sessionUsage.collectAsStateWithLifecycle()
    val checkpointIds by viewModel.checkpointMessageIds.collectAsStateWithLifecycle()
    val autonomy by viewModel.autonomyMode.collectAsStateWithLifecycle()
    val attachedImages by viewModel.attachedImages.collectAsStateWithLifecycle()
    val richUi by viewModel.richUi.collectAsStateWithLifecycle()

    // Ask for notifications the first time a run starts, the moment the
    // reason is obvious: so Orobi can say when it is done while you are away.
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    var askedNotifications by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val notifyContext = LocalContext.current
    LaunchedEffect(uiState.isAiProcessing) {
        if (uiState.isAiProcessing && !askedNotifications &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !com.oro.app.agent.AgentNotifications.canPost(notifyContext)
        ) {
            askedNotifications = true
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val sessionActions = remember(viewModel) {
        ChatSessionActions(
            onOpen = viewModel::openSession,
            onNewChat = viewModel::startNewChat,
            onRename = viewModel::renameSession,
            onSetPinned = viewModel::setSessionPinned,
            onDelete = viewModel::deleteSession,
            onUndoDelete = viewModel::undoDeleteSession,
            onDismissUndo = viewModel::dismissSessionUndo
        )
    }
    val context = LocalContext.current
    var showSessions by remember { mutableStateOf(false) }
    var showDeploy by remember { mutableStateOf(false) }
    // Read once in composable scope: AnimatedContent's transitionSpec lambda
    // is not @Composable, so MaterialTheme cannot be read inside it.
    val tabEnterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val tabExitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    val fileDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val assetDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val gitDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Track target path for import operations
    var importTargetPath by remember { mutableStateOf("") }

    // File picker launcher for importing files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importFile(it, importTargetPath)
        }
    }

    // Folder picker launcher for importing folders
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importFolder(it, importTargetPath)
        }
    }

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

    // Approval sheet sits outside the drawer stack so it overlays whatever is
    // on screen -- the question blocks the agent, so it must not be hidden
    // behind an open drawer.
    val pendingApproval by viewModel.pendingApproval.collectAsStateWithLifecycle()
    ApprovalSheet(
        approval = pendingApproval,
        onDecision = viewModel::resolveApproval
    )

    // File Tree drawer (start-side drawer - outer for proper gesture handling)
    ModalNavigationDrawer(
        drawerState = fileDrawerState,
        drawerContent = {
            // The drawer is always composed; only its body is skipped while
            // closed. Gating the whole sheet here gave it zero width, which
            // collapsed the open/closed anchors and left the drawer
            // permanently reopening itself.
            FileTreeDrawer(
                contentVisible = fileDrawerState.isOpen || fileDrawerState.isAnimationRunning,
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
                onImportFile = { parentPath ->
                    importTargetPath = parentPath
                    filePickerLauncher.launch("*/*")
                },
                onImportFolder = { parentPath ->
                    importTargetPath = parentPath
                    folderPickerLauncher.launch(null)
                },
                onClose = { scope.launch { fileDrawerState.close() } }
            )
        },
        gesturesEnabled = fileDrawerState.isOpen // Only enable gestures when drawer is open
    ) {
        // Git drawer (end-side drawer - no gestures to avoid conflicts)
        ModalNavigationDrawer(
            drawerState = gitDrawerState,
            drawerContent = {
                // Composed unconditionally, including while `project` is still
                // null. Wrapping this in `project?.let` gave the sheet zero
                // width for the first frames after opening a project; when the
                // project then loaded, the sheet gained real width, the
                // drawer's open/closed anchors recomputed, and the swipeable
                // state settled to Open -- which is why Source Control and
                // Assets (the two drawers behind that null check) appeared
                // open by themselves while the File drawer, composed
                // unconditionally, never did.
                GitDrawer(
                    contentVisible = gitDrawerState.isOpen ||
                        gitDrawerState.isAnimationRunning,
                    projectPath = uiState.project?.rootPath,
                    onClose = { scope.launch { gitDrawerState.close() } },
                    onNavigateToGitSettings = onGitSettingsClick
                )
            },
            gesturesEnabled = gitDrawerState.isOpen,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
        ) {
            // Asset Manager drawer (inner drawer)
            ModalNavigationDrawer(
                drawerState = assetDrawerState,
                scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
                drawerContent = {
                    // See the note on the Git drawer above: the sheet must be
                    // composed even before the project loads, or it settles
                    // open on its own.
                    AssetManagerDrawer(
                        contentVisible = assetDrawerState.isOpen ||
                            assetDrawerState.isAnimationRunning,
                        projectPath = uiState.project?.rootPath,
                        onClose = { scope.launch { assetDrawerState.close() } },
                        onAssetClick = { asset ->
                            val textExtensions = listOf("svg", "json", "xml", "txt", "md")
                            if (asset.extension.lowercase() in textExtensions) {
                                viewModel.openFile(asset.path)
                                scope.launch { assetDrawerState.close() }
                            }
                        },
                        onAssetDelete = { viewModel.refreshFileTree() },
                        onAssetAdded = { viewModel.refreshFileTree() },
                        onCopyPath = {
                            Toast.makeText(
                                context,
                                context.getString(R.string.assets_path_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                },
                gesturesEnabled = assetDrawerState.isOpen
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
                            onSettingsClick = onSettingsClick,
                            onDeployClick = { showDeploy = true }
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
                            onTabSelected = viewModel::selectTab,
                            sessionsOpen = showSessions,
                            runActive = uiState.isAiProcessing,
                            onOpenSessions = {
                                if (uiState.selectedTab != EditorTab.CHAT) viewModel.selectTab(EditorTab.CHAT)
                                showSessions = true
                            }
                        )
                        val deployRoot = uiState.project?.rootPath
                        if (showDeploy && deployRoot != null) {
                            com.oro.app.ui.screens.editor.deploy.DeploySheet(
                                projectRoot = deployRoot,
                                projectName = uiState.project?.name ?: "site",
                                onDeployed = viewModel::onDeployed,
                                onDismiss = { showDeploy = false }
                            )
                        }
                        if (showSessions) {
                            SessionsSheet(
                                onTimeline = { showSessions = false; onTimelineClick() },
                                state = sessionsState,
                                projectName = uiState.project?.name,
                                isRunActive = uiState.isAiProcessing,
                                onStopRun = viewModel::stopGeneration,
                                actions = sessionActions,
                                onDismiss = { showSessions = false }
                            )
                        }


                        AnimatedContent(
                            targetState = uiState.selectedTab,
                            transitionSpec = {
                                // Cross-fade on the effects spec. These are two
                                // views of one file, not a navigation step, so
                                // there is deliberately no slide: sliding would
                                // imply moving somewhere else.
                                fadeIn(tabEnterSpec) togetherWith fadeOut(tabExitSpec)
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "editor_tab_content"
                        ) { tab ->
                            when (tab) {
                                EditorTab.CHAT -> androidx.compose.runtime.CompositionLocalProvider(
                                    com.oro.app.ui.genui.LocalGenUiActions provides viewModel::sendText
                                ) { ChatTab(
                                    sessionsState = sessionsState,
                                    sessionActions = sessionActions,
                                    agentPlan = uiState.agentPlan,
                                    sessionUsage = sessionUsage,
                                    checkpointMessageIds = checkpointIds,
                                    onRestoreCheckpoint = viewModel::restoreCheckpoint,
                                    autonomy = autonomy,
                                    onAutonomyChange = viewModel::setAutonomyMode,
                                    richUi = richUi,
                                    onRichUiChange = viewModel::setRichUi,
                                    attachedPhotos = attachedImages.size,
                                    onAttachPhoto = viewModel::attachImage,
                                    onClearPhotos = viewModel::clearAttachedImages,
                                    projectName = uiState.project?.name,
                                    agentSessions = uiState.agentSessions,
                                    reasoningLevels = uiState.reasoningLevels,
                                    onReasoningLevelChange = viewModel::setReasoningLevel,
                                    contextBreakdown = uiState.contextBreakdown,
                                    compactableMessageCount = uiState.compactableMessageCount,
                                    isCompacting = uiState.isCompacting,
                                    lastCompactionSavedPercent = uiState.lastCompactionSavedPercent,
                                    onCompactContext = viewModel::compactContext,
                                    onStopAgent = viewModel::stopSubagent,
                                    onResolveAgentApproval = viewModel::resolveSubagentApproval,
                                    messages = uiState.messages,
                                    inputText = uiState.chatInput,
                                    isLoading = uiState.isAiProcessing,
                                    agentMode = uiState.agentMode,
                                    taskList = uiState.taskList,
                                    currentModel = uiState.currentModel,
                                    availableModels = uiState.availableModels,
                                    fileTree = uiState.fileTree,
                                    onInputChange = viewModel::updateChatInput,
                                    onSendMessage = viewModel::sendMessage,
                                    onStopGeneration = viewModel::stopGeneration,
                                    onToggleAgentMode = viewModel::toggleAgentMode,
                                    onModelChange = viewModel::setModel,
                                    onRefreshModels = viewModel::refreshModels,
                                    isRefreshingModels = uiState.isRefreshingModels,
                                    workingLabel = uiState.runStatus?.label,
                                    pendingQuestion = uiState.pendingQuestion,
                                    onAnswerQuestion = viewModel::answerQuestion,
                                    onSkipQuestion = viewModel::skipQuestion,
                                    runError = uiState.runError,
                                    onRetryRun = viewModel::retryRun,
                                    onDismissRunError = viewModel::dismissRunError
                                ) }
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
                                        onSave = viewModel::saveCurrentFile,
                                        onAiEditRequest = { request ->
                                            viewModel.handleAiCodeEdit(request)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Large file warning dialog
    uiState.largeFileWarning?.let { warning ->
        LargeFileWarningDialog(
            warning = warning,
            onDismiss = viewModel::dismissLargeFileWarning,
            onOpenAnyway = { viewModel.confirmOpenLargeFile(readOnly = false) },
            onOpenReadOnly = { viewModel.confirmOpenLargeFile(readOnly = true) }
        )
    }

    // File loading progress indicator
    if (uiState.fileLoadProgress < 1f && uiState.fileLoadProgress > 0f) {
        FileLoadingProgressDialog(
            fileName = uiState.openFiles.lastOrNull()?.name ?: "file",
            progress = uiState.fileLoadProgress
        )
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
    onSettingsClick: () -> Unit,
    onDeployClick: () -> Unit = {}
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
                onClick = onDeployClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudUpload,
                    contentDescription = "Publish",
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

/**
 * Chat / Code switch.
 *
 * Was a full-width [TabRow] with an underline indicator. Two problems with
 * that here: a two-item TabRow spreads a binary choice across the whole screen
 * width, so the tap targets are enormous and the labels float in space; and the
 * sliding underline is a desktop idiom that reads as a page-level navigation
 * rail, which these are not -- they are two views of the same file.
 *
 * An Expressive segmented control states the binary nature of the choice,
 * occupies only the width it needs, and brings the Expressive press-squish and
 * selection shape morph with it. Centred, so it sits under the thumb on a
 * phone rather than at the screen edge.
 */
@Composable
private fun EditorTabs(
    selectedTab: EditorTab,
    onTabSelected: (EditorTab) -> Unit,
    sessionsOpen: Boolean,
    runActive: Boolean,
    onOpenSessions: () -> Unit
) {
    val context = LocalContext.current
    val chatSelected = selectedTab == EditorTab.CHAT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chat is an Expressive split button: the label opens the chat, the
        // chevron opens the chats sheet. It replaces the session bar that
        // used to sit under the tabs, so the transcript gets that row back.
        TabSegment(
            selected = chatSelected,
            shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp),
            onClick = { onTabSelected(EditorTab.CHAT) },
            contentDescription = context.getString(R.string.editor_tab_chat)
        ) { container ->
            OrobiMascot(
                pose = { OrobiPose.Rest },
                // Chosen from the segment's own background, not the content
                // colour: the unselected segment is dark in dark mode, and the
                // ink-bodied variant all but vanished on it.
                colors = OrobiColors.forBackground(container),
                showShadow = false,
                contentDescription = null,
                modifier = Modifier.width(22.dp)
            )
            Spacer(Modifier.width(Space.sm))
            Text(context.getString(R.string.editor_tab_chat), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(2.dp))
        val chevron by androidx.compose.animation.core.animateFloatAsState(if (sessionsOpen) 180f else 0f, label = "chevron")
        TabSegment(
            selected = chatSelected,
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp),
            onClick = onOpenSessions,
            horizontalPadding = Space.md,
            contentDescription = "Chats"
        ) { _ ->
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevron }
                )
                if (runActive) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
        Spacer(Modifier.width(Space.sm))
        TabSegment(
            selected = !chatSelected,
            shape = RoundedCornerShape(50),
            onClick = { onTabSelected(EditorTab.CODE) },
            contentDescription = context.getString(R.string.editor_tab_code)
        ) { _ ->
            Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.sm))
            Text(context.getString(R.string.editor_tab_code), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** One segment of the editor tab bar; selected segments invert, like the old toggle group. */
@Composable
private fun TabSegment(
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    contentDescription: String,
    horizontalPadding: androidx.compose.ui.unit.Dp = Space.lg,
    content: @Composable androidx.compose.foundation.layout.RowScope.(container: androidx.compose.ui.graphics.Color) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val container by androidx.compose.animation.animateColorAsState(
        if (selected) scheme.inverseSurface else scheme.surfaceContainerHigh, label = "segment"
    )
    val contentColor = if (selected) scheme.inverseOnSurface else scheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = shape,
        color = container,
        contentColor = contentColor,
        modifier = Modifier
            .height(48.dp)
            .semantics { this.contentDescription = contentDescription; this.selected = selected }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) { content(container) }
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
            .clip(OroCorners.tiny)
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

/**
 * Dialog shown when user tries to open a large file
 * Provides options to open normally, open read-only, or cancel
 */
@Composable
private fun LargeFileWarningDialog(
    warning: LargeFileWarning,
    onDismiss: () -> Unit,
    onOpenAnyway: () -> Unit,
    onOpenReadOnly: () -> Unit
) {
    ActionSheet(
        title = "This file is large",
        subtitle = warning.warningMessage,
        onDismiss = onDismiss,
        actions = buildList {
            if (warning.suggestReadOnly) {
                add(
                    SheetAction(
                        label = "Open read-only",
                        icon = Icons.Outlined.Visibility,
                        description = "Fast, and cannot be edited by accident",
                        onClick = onOpenReadOnly
                    )
                )
            }
            if (warning.canEdit) {
                add(
                    SheetAction(
                        label = "Open anyway",
                        icon = Icons.Outlined.EditNote,
                        description = "Editing may be slow at this size",
                        onClick = onOpenAnyway
                    )
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = OroCorners.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = warning.fileName,
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = warning.formattedSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // The two figures that decide whether to wait: how long, how big.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconSmall),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = buildString {
                        append("About ")
                        append(warning.estimatedLoadTime)
                        append(" to load")
                        if (warning.lineCount > 0) {
                            append(" · ~")
                            append(warning.lineCount)
                            append(" lines")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Dialog showing file loading progress for large files
 */
@Composable
private fun FileLoadingProgressDialog(
    fileName: String,
    progress: Float
) {
    AlertDialog(
        onDismissRequest = { /* Cannot dismiss while loading */ },
        title = {
            Text(
                text = "Loading File",
                style = MaterialTheme.typography.titleMediumEmphasized,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )

                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { /* Nothing to decide while loading */ },
        // The one modal that stays centred: it has no actions and must not be
        // dismissable, and a bottom sheet is swipe-dismissable by nature.
        shape = OroCorners.xLarge
    )
}
