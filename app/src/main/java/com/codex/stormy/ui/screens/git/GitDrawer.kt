package com.codex.stormy.ui.screens.git

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.stormy.CodeXApplication
import java.io.File

/**
 * Git drawer for source control management
 * Can be opened from the editor screen
 */
@Composable
fun GitDrawer(
    projectPath: String,
    onClose: () -> Unit,
    onNavigateToGitSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = remember { CodeXApplication.getInstance() }

    val viewModel: GitViewModel = viewModel(
        factory = GitViewModel.Factory(
            gitManager = app.gitManager,
            credentialsManager = app.gitCredentialsManager,
            gitHubActionsService = app.gitHubActionsService
        ),
        key = "git_$projectPath"
    )

    val uiState by viewModel.uiState.collectAsState()

    // Open repository when drawer opens
    LaunchedEffect(projectPath) {
        viewModel.openRepository(File(projectPath))
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GitUiEvent.ShowMessage -> {
                    // Could show snackbar here
                }
                is GitUiEvent.ShowError -> {
                    // Could show error snackbar here
                }
                is GitUiEvent.CloneComplete -> {
                    // Handle clone complete
                }
            }
        }
    }

    ModalDrawerSheet(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight()
    ) {
        GitPanel(
            uiState = uiState,
            onStageFile = { path -> viewModel.stageFiles(listOf(path)) },
            onUnstageFile = { path -> viewModel.unstageFiles(listOf(path)) },
            onStageAll = { viewModel.stageAll() },
            onDiscardChanges = { path -> viewModel.discardChanges(listOf(path)) },
            onCommit = { message -> viewModel.commit(message) },
            onPush = { viewModel.push() },
            onPull = { viewModel.pull() },
            onFetch = { viewModel.fetch() },
            onRefresh = { viewModel.refresh() },
            onCheckout = { branch -> viewModel.checkout(branch) },
            onCreateBranch = { name, checkout -> viewModel.createBranch(name, checkout) },
            onViewDiff = { path, staged -> viewModel.getFileDiff(path, staged) },
            onInitRepo = { viewModel.initRepository() },
            onOpenSettings = onNavigateToGitSettings,
            onClose = onClose,
            onRefreshCI = { viewModel.refreshCIStatus() },
            onRerunWorkflow = { runId -> viewModel.rerunWorkflow(runId) },
            onCancelWorkflow = { runId -> viewModel.cancelWorkflow(runId) },
            onOpenWorkflowUrl = { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        )
    }
}
