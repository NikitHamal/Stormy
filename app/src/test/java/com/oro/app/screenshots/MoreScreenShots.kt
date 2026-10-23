package com.oro.app.screenshots

import android.webkit.ConsoleMessage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.oro.app.OroApplication
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import com.oro.app.data.ai.checkpoint.CheckpointFile
import com.oro.app.data.ai.checkpoint.CheckpointManifest
import com.oro.app.ui.screens.editor.deploy.DeploySheet
import com.oro.app.ui.screens.git.GitSettingsScreen
import com.oro.app.ui.screens.preview.AgentPromptBar
import com.oro.app.ui.screens.preview.ConsoleLogEntry
import com.oro.app.ui.screens.preview.ConsolePanel
import com.oro.app.ui.screens.preview.ElementRect
import com.oro.app.ui.screens.preview.InspectedElement
import com.oro.app.ui.screens.preview.SelectionMode
import com.oro.app.ui.screens.preview.SelectionModeIndicator
import com.oro.app.ui.screens.providers.ProviderEditorScreen
import com.oro.app.ui.screens.providers.ProvidersViewModel
import com.oro.app.ui.screens.timeline.TimelineList
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.oro.app.data.ai.AiModels
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.SeedModels
import com.oro.app.data.git.GitBranch
import com.oro.app.data.git.GitChangedFile
import com.oro.app.data.git.GitCommit
import com.oro.app.data.git.GitFileStatus
import com.oro.app.data.git.GitRemote
import com.oro.app.data.git.GitRepositoryStatus
import com.oro.app.data.repository.LiteMode
import com.oro.app.data.repository.ThemeMode
import com.oro.app.domain.model.FileTreeNode
import com.oro.app.ui.components.expressive.FilterChipItem
import com.oro.app.ui.components.expressive.FilterChipStrip
import com.oro.app.ui.screens.aimodels.ModelsList
import com.oro.app.ui.screens.aimodels.ModelsTopBar
import com.oro.app.ui.screens.debug.CrashScreen
import com.oro.app.ui.screens.editor.EditorTab
import com.oro.app.ui.screens.editor.EditorTabs
import com.oro.app.ui.screens.editor.EditorTopBar
import com.oro.app.ui.screens.editor.FileTabsRow
import com.oro.app.ui.screens.editor.OpenFileTab
import com.oro.app.ui.screens.editor.code.CodeTab
import com.oro.app.ui.screens.editor.filetree.FileTreeDrawer
import com.oro.app.ui.screens.git.GitPanel
import com.oro.app.ui.screens.git.GitUiState
import com.oro.app.ui.screens.onboarding.OnboardingScreen
import com.oro.app.ui.screens.providers.ProvidersContent
import com.oro.app.ui.screens.providers.ProvidersUiState
import com.oro.app.ui.screens.providers.providerRows
import com.oro.app.ui.screens.settings.SettingsContent
import com.oro.app.ui.screens.settings.SettingsUiState
import com.oro.app.ui.screens.skills.SkillsScreen
import com.oro.app.ui.theme.OroTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The rest of the app people move through: settings, providers, the model
 * catalogue, skills, the git panel, the editor shell and file tree, the crash
 * screen and onboarding. Real composables, made-up data.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = ShotsApplication::class)
class MoreScreenShots {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun stubApp() = OroAppStub.install()

    @Composable
    private fun Themed(dark: Boolean, content: @Composable () -> Unit) {
        OroTheme(darkTheme = dark) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { content() }
        }
    }

    // -------------------------------------------------------- settings

    private val settingsState = SettingsUiState(
        themeMode = ThemeMode.SYSTEM,
        aiModel = SeedModels.default.id,
        availableModels = AiModels.getAllModels(),
        liteMode = LiteMode.AUTO,
    )

    @Composable
    private fun Settings(dark: Boolean) = Themed(dark) {
        SettingsContent(
            uiState = settingsState,
            lowEndDevice = false,
            onBackClick = {},
            onNavigateToAiModels = {},
            onNavigateToMemories = {},
            onNavigateToGitSettings = {},
            onNavigateToProviders = {},
            onNavigateToMcp = {},
            onNavigateToVoice = {},
            onNavigateToSkills = {},
            onThemeSelected = {},
            onLiteModeSelected = {},
            onFontSizeChange = {},
            onLineNumbersChange = {},
            onWordWrapChange = {},
            onDebugLogsChange = {},
        )
    }

    @Test
    fun settings_light() = compose.shoot("settings_light", height = 1_700.dp) { Settings(dark = false) }

    @Test
    fun settings_dark() = compose.shoot("settings_dark", height = 1_700.dp) { Settings(dark = true) }

    // ------------------------------------------------------- providers

    @Composable
    private fun Providers(dark: Boolean) = Themed(dark) {
        val rows = remember { providerRows(ProvidersUiState(), emptyMap()) }
        ProvidersContent(
            customRows = rows.first,
            builtInRows = rows.second,
            snackbar = remember { SnackbarHostState() },
            onBackClick = {},
            onOpenEditor = {},
        )
    }

    @Test
    fun providers_light() = compose.shoot("providers_light", height = 1_700.dp) { Providers(dark = false) }

    @Test
    fun providers_dark() = compose.shoot("providers_dark") { Providers(dark = true) }

    // ------------------------------------------------------ AI models

    @Composable
    private fun Models(dark: Boolean) = Themed(dark) {
        val all = AiModels.getAllModels()
        Scaffold(
            topBar = {
                Column {
                    ModelsTopBar(
                        enabledCount = all.size,
                        totalCount = all.size + 41,
                        searchActive = false,
                        onSearchActiveChange = {},
                        query = "",
                        onQueryChange = {},
                        onBackClick = {},
                        onRefresh = {},
                        isLoading = false,
                    )
                    FilterChipStrip(
                        items = listOf(FilterChipItem("all", "All")) +
                            AiProvider.entries.map { FilterChipItem(it.name, it.displayName) },
                        selectedId = "all",
                        onSelect = {},
                    )
                }
            },
        ) { padding ->
            ModelsList(
                models = all,
                currentModelId = SeedModels.default.id,
                defaultModelId = SeedModels.default.id,
                onModelSelect = {},
                onSetAsDefault = {},
                onClearDefault = {},
                onModelEnabledChange = { _, _ -> },
                onProviderEnabledChange = { _, _ -> },
                snackbarHostState = remember { SnackbarHostState() },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    @Test
    fun ai_models_light() = compose.shoot("ai_models_light", height = 1_500.dp) { Models(dark = false) }

    @Test
    fun ai_models_dark() = compose.shoot("ai_models_dark") { Models(dark = true) }

    // ---------------------------------------------------------- skills

    @Test
    fun skills_light() = compose.shoot("skills_light", height = 1_500.dp) { Themed(false) { SkillsScreen(onBack = {}) } }

    @Test
    fun skills_dark() = compose.shoot("skills_dark") { Themed(true) { SkillsScreen(onBack = {}) } }

    // ------------------------------------------------------------- git

    private fun gitState(): GitUiState {
        val now = System.currentTimeMillis()
        fun commit(id: String, msg: String, ago: Long) = GitCommit(
            id = id.padEnd(40, '0'), shortId = id, message = msg, fullMessage = msg,
            authorName = "Nikit Hamal", authorEmail = "n@example.com", timestamp = now - ago,
        )
        return GitUiState(
            isGitRepo = true,
            status = GitRepositoryStatus(
                isGitRepo = true, currentBranch = "main", hasRemote = true,
                remoteUrl = "https://github.com/nikit/brew-and-bloom.git", isClean = false,
                hasUncommittedChanges = true, hasUnstagedChanges = true, hasStagedChanges = true, aheadCount = 2,
            ),
            changedFiles = listOf(
                GitChangedFile("index.html", GitFileStatus.MODIFIED, isStaged = true),
                GitChangedFile("css/style.css", GitFileStatus.MODIFIED, isStaged = true),
                GitChangedFile("js/app.js", GitFileStatus.MODIFIED),
                GitChangedFile("api/db.json", GitFileStatus.UNTRACKED),
                GitChangedFile("old/hero.png", GitFileStatus.DELETED),
            ),
            branches = listOf(
                GitBranch("main", isCurrent = true, trackingBranch = "origin/main", aheadCount = 2),
                GitBranch("menu-cards"),
                GitBranch("origin/main", isLocal = false, isRemote = true),
            ),
            commits = listOf(
                commit("a1b2c3d", "Menu as cards with prices", 3_600_000),
                commit("9f8e7d6", "Contact form saves to /api/messages", 86_400_000),
                commit("5c4b3a2", "Hero, opening hours and map link", 86_400_000 * 2),
            ),
            remotes = listOf(GitRemote("origin", "https://github.com/nikit/brew-and-bloom.git", "https://github.com/nikit/brew-and-bloom.git")),
            userName = "Nikit Hamal",
            userEmail = "n@example.com",
        )
    }

    @Composable
    private fun Git(dark: Boolean, state: GitUiState) = Themed(dark) {
        GitPanel(
            uiState = state,
            onStageFile = {}, onUnstageFile = {}, onStageAll = {}, onDiscardChanges = {}, onCommit = {},
            onPush = {}, onPull = {}, onFetch = {}, onRefresh = {}, onCheckout = {},
            onCreateBranch = { _, _ -> }, onDeleteBranch = { _, _ -> }, onViewDiff = { _, _ -> },
            onInitRepo = {}, onAddRemote = { _, _ -> }, onOpenSettings = {}, onClose = {},
        )
    }

    @Test
    fun git_panel_light() = compose.shoot("git_panel_light", height = 1_500.dp) { Git(dark = false, state = gitState()) }

    @Test
    fun git_panel_dark() = compose.shoot("git_panel_dark") { Git(dark = true, state = gitState()) }

    @Test
    fun git_init_light() = compose.shoot("git_init_light") { Git(dark = false, state = GitUiState()) }

    // ---------------------------------------------------- editor shell

    private val tabs = listOf(
        OpenFileTab(path = "index.html", name = "index.html", extension = "html"),
        OpenFileTab(path = "css/style.css", name = "style.css", extension = "css", isModified = true),
        OpenFileTab(path = "js/app.js", name = "app.js", extension = "js"),
    )

    @Composable
    private fun EditorShell(dark: Boolean, tab: EditorTab) = Themed(dark) {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(
                projectName = "Brew & Bloom",
                onBackClick = {}, onMenuClick = {}, onAssetsClick = {}, onGitClick = {},
                onPreviewClick = {}, onSettingsClick = {}, onDeployClick = {},
            )
            EditorTabs(selectedTab = tab, onTabSelected = {}, sessionsOpen = false, runActive = false, onOpenSessions = {})
            if (tab == EditorTab.CODE) {
                FileTabsRow(openFiles = tabs, currentIndex = 1, onTabClick = {}, onTabClose = {}, onCloseOthers = {}, onCloseAll = {})
            }
            Column(Modifier.fillMaxWidth().weight(1f)) {
                CodeTab(
                    currentFile = null, fileContent = "", isModified = false, lineNumbers = true, wordWrap = true,
                    fontSize = 14f, onContentChange = {}, onSave = {},
                )
            }
        }
    }

    @Test
    fun editor_shell_code_light() = compose.shoot("editor_shell_code_light") { EditorShell(dark = false, tab = EditorTab.CODE) }

    @Test
    fun editor_shell_code_dark() = compose.shoot("editor_shell_code_dark") { EditorShell(dark = true, tab = EditorTab.CODE) }

    private fun fileTree(): List<FileTreeNode> = listOf(
        FileTreeNode.FolderNode("css", "css", 0, isExpanded = true, children = listOf(
            FileTreeNode.FileNode("style.css", "css/style.css", 1, "css", 4_200),
        )),
        FileTreeNode.FolderNode("js", "js", 0, isExpanded = true, children = listOf(
            FileTreeNode.FileNode("app.js", "js/app.js", 1, "js", 2_900),
            FileTreeNode.FileNode("menu.js", "js/menu.js", 1, "js", 1_100),
        )),
        FileTreeNode.FolderNode("images", "images", 0),
        FileTreeNode.FolderNode("kit", "kit", 0),
        FileTreeNode.FileNode("db.json", "db.json", 0, "json", 640),
        FileTreeNode.FileNode("index.html", "index.html", 0, "html", 9_800),
        FileTreeNode.FileNode("ORO.md", "ORO.md", 0, "md", 320),
    )

    @Composable
    private fun Files(dark: Boolean) = Themed(dark) {
        FileTreeDrawer(
            fileTree = fileTree(),
            expandedFolders = setOf("css", "js"),
            selectedFilePath = "css/style.css",
            onFileClick = {}, onFolderToggle = {},
            onCreateFile = { _, _ -> }, onCreateFolder = { _, _ -> }, onDeleteFile = {},
            onRenameFile = { _, _ -> }, onImportFile = {}, onImportFolder = {}, onClose = {},
        )
    }

    @Test
    fun file_tree_light() = compose.shoot("file_tree_light") { Files(dark = false) }

    @Test
    fun file_tree_dark() = compose.shoot("file_tree_dark") { Files(dark = true) }

    // ----------------------------------------------------------- crash

    private val crashLog = """
        Oro 2.4.0 (240) · Android 14 (SDK 34) · Pixel 7a
        Time: 2026-10-23 09:41:07

        java.lang.IllegalStateException: Expected a project root, found nothing at /data/user/0/com.oro.app/files/projects/brew
        	at com.oro.app.data.repository.ProjectRepository.root(ProjectRepository.kt:212)
        	at com.oro.app.ui.screens.editor.EditorViewModel.loadProject(EditorViewModel.kt:618)
        	at com.oro.app.ui.screens.editor.EditorViewModel.access${'$'}loadProject(EditorViewModel.kt:96)
        	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:108)
    """.trimIndent()

    @Test
    fun crash_light() = compose.shoot("crash_light") { OroTheme(darkTheme = false) { CrashScreen(initialLog = crashLog, onRestart = {}) } }

    @Test
    fun crash_dark() = compose.shoot("crash_dark") { OroTheme(darkTheme = true) { CrashScreen(initialLog = crashLog, onRestart = {}) } }

    // ------------------------------------------------------ onboarding

    @Test
    fun onboarding_0_light() = compose.shoot("onboarding_0_light") { Themed(false) { OnboardingScreen(onComplete = {}, initialPage = 0) } }

    @Test
    fun onboarding_1_light() = compose.shoot("onboarding_1_light") { Themed(false) { OnboardingScreen(onComplete = {}, initialPage = 1) } }

    @Test
    fun onboarding_2_dark() = compose.shoot("onboarding_2_dark") { Themed(true) { OnboardingScreen(onComplete = {}, initialPage = 2) } }

    @Test
    fun onboarding_3_dark() = compose.shoot("onboarding_3_dark") { Themed(true) { OnboardingScreen(onComplete = {}, initialPage = 3) } }

    // -------------------------------------------------------- timeline

    private fun checkpoints(): List<CheckpointManifest> {
        val now = System.currentTimeMillis()
        fun cp(id: String, label: String, ago: Long, vararg files: Pair<String, Boolean>) = CheckpointManifest(
            id = id, sessionId = "s", messageId = "m$id", createdAt = now - ago, label = label,
            files = files.map { (path, existed) -> CheckpointFile(path, existed) },
        )
        return listOf(
            cp("3", "Add online ordering with a cart", 600_000, "index.html" to true, "js/cart.js" to false, "css/style.css" to true),
            cp("2", "Menu as cards with prices", 3_600_000, "index.html" to true, "css/style.css" to true),
            cp("1", "Build a landing page for Brew & Bloom", 86_400_000, "index.html" to false, "css/style.css" to false, "js/app.js" to false),
        )
    }

    @Composable
    private fun Timeline(dark: Boolean, list: List<CheckpointManifest>) = Themed(dark) {
        Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = { Text("Timeline", style = MaterialTheme.typography.titleLargeEmphasized) },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = {}) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                        }
                    },
                )
            },
        ) { padding ->
            TimelineList(list = list, onDiff = { _, _ -> }, onRestore = {}, modifier = Modifier.fillMaxSize().padding(padding))
        }
    }

    @Test
    fun timeline_light() = compose.shoot("timeline_light", height = 1_300.dp) { Timeline(dark = false, list = checkpoints()) }

    @Test
    fun timeline_dark() = compose.shoot("timeline_dark") { Timeline(dark = true, list = checkpoints()) }

    @Test
    fun timeline_empty_light() = compose.shoot("timeline_empty_light") { Timeline(dark = false, list = emptyList()) }

    // ---------------------------------------------------- preview chrome

    private val logs = listOf(
        ConsoleLogEntry("Service worker registered", ConsoleMessage.MessageLevel.LOG, 12, "js/app.js"),
        ConsoleLogEntry("Image /images/hero.jpg is 2.4 MB; consider a smaller one", ConsoleMessage.MessageLevel.WARNING, 3, "index.html"),
        ConsoleLogEntry("Uncaught TypeError: Cannot read properties of null (reading 'addEventListener')", ConsoleMessage.MessageLevel.ERROR, 41, "js/menu.js"),
    )

    private val selected = listOf(
        InspectedElement(
            tagName = "button", id = "order", className = "btn btn-primary", innerHTML = "Order now",
            outerHTML = "<button id=\"order\" class=\"btn btn-primary\">Order now</button>",
            computedStyles = mapOf("color" to "#fff"), attributes = mapOf("id" to "order"),
            boundingRect = ElementRect(24f, 540f, 160f, 48f), selector = "#order", selectorIsUnique = true, textContent = "Order now",
        ),
    )

    @Composable
    private fun PreviewChrome(dark: Boolean) = Themed(dark) {
        Box(Modifier.fillMaxSize()) {
            // A stand-in page under the chrome.
            Column(Modifier.fillMaxSize().background(Color(0xFFFFF8F0)).padding(24.dp)) {
                Text("Brew & Bloom", color = Color(0xFF3B2A1A), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("Small-batch coffee, slow mornings.", color = Color(0xFF6B5444))
            }
            SelectionModeIndicator(mode = SelectionMode.AGENT, selectedCount = 1, modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                ConsolePanel(logs = logs, onClear = {}, modifier = Modifier.fillMaxWidth().height(260.dp))
                AgentPromptBar(selectedElements = selected, onClearSelection = {}, onSubmit = {})
            }
        }
    }

    @Test
    fun preview_chrome_light() = compose.shoot("preview_chrome_light") { PreviewChrome(dark = false) }

    @Test
    fun preview_chrome_dark() = compose.shoot("preview_chrome_dark") { PreviewChrome(dark = true) }

    // ------------------------------------------- git settings, deploy, editor

    @Test
    fun git_settings_light() = compose.shoot("git_settings_light", height = 1_300.dp) { Themed(false) { GitSettingsScreen(onBackClick = {}) } }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun deploy_sheet_light() {
        val root = java.io.File(System.getProperty("java.io.tmpdir"), "oro-shot-deploy").apply { mkdirs() }
        java.io.File(root, "index.html").writeText("<!doctype html><title>Brew</title>")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Themed(false) {
                DeploySheet(projectRoot = root.path, projectName = "Brew & Bloom", onDeployed = {}, onDismiss = {})
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        captureScreenRoboImage("$SHOTS_DIR/deploy_sheet_light.png")
    }

    @Test
    fun provider_editor_new_light() = compose.shoot("provider_editor_new_light") {
        val app = OroApplication.getInstance()
        val vm = remember { ProvidersViewModel(app.preferencesRepository, app.aiModelRepository) }
        Themed(false) { ProviderEditorScreen(viewModel = vm, route = null, onBack = {}) }
    }
}
