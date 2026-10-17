package com.oro.app.ui.screens.editor.deploy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.oro.app.OroApplication
import com.oro.app.data.deploy.DeployTarget
import com.oro.app.data.deploy.Deployer
import com.oro.app.ui.components.expressive.PickerOption
import com.oro.app.ui.components.expressive.SegmentedPicker
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Publish the project in one tap: Netlify (a zip upload) or GitHub Pages (a
 * gh-pages branch written over the API). The second deploy to the same
 * target updates the same site, so the URL the user shared keeps working.
 */
@Composable
fun DeploySheet(projectRoot: String, projectName: String, onDeployed: (String) -> Unit, onDismiss: () -> Unit) {
    val app = remember { OroApplication.getInstance() }
    val deployer = remember(projectRoot) { Deployer(File(projectRoot)) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val state = remember { deployer.state() }

    var target by rememberSaveable { mutableStateOf(DeployTarget.NETLIFY) }
    var name by rememberSaveable { mutableStateOf(Deployer.slug(projectName)) }
    var netlifyToken by rememberSaveable { mutableStateOf("") }
    var githubToken by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Result<String>?>(null) }

    LaunchedEffect(Unit) {
        netlifyToken = app.preferencesRepository.netlifyToken.first()
        githubToken = runCatching { app.gitCredentialsManager.getCredentialsForHost("github.com")?.password }.getOrNull().orEmpty()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.xl, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Text("Publish", style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                "Put this project on the web. Deploying again updates the same site.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SegmentedPicker(
                label = "Where",
                options = DeployTarget.entries.map { PickerOption(it, it.label) },
                selected = target,
                onSelect = { target = it; result = null }
            )
            val previous = if (target == DeployTarget.NETLIFY) state.netlifyUrl else state.githubUrl
            previous?.let {
                Text("Live at $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (target == DeployTarget.NETLIFY) {
                if (state.netlifySiteId == null) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Site name") }, singleLine = true, shape = OroCorners.medium,
                        supportingText = { Text("$name.netlify.app if it is free") })
                }
                OutlinedTextField(
                    netlifyToken, { netlifyToken = it }, Modifier.fillMaxWidth(),
                    label = { Text("Netlify personal access token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = OroCorners.medium,
                    supportingText = { Text("app.netlify.com → User settings → Applications") }
                )
            } else {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Repository name") }, singleLine = true, shape = OroCorners.medium)
                if (githubToken.isBlank()) {
                    Text(
                        "Add a GitHub token in Git settings (repo scope) to publish to GitHub Pages.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                    )
                }
            }

            result?.let { r ->
                Surface(shape = OroCorners.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.md)) {
                        r.onSuccess { url ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(Space.sm))
                                Text(url, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                            if (target == DeployTarget.GITHUB_PAGES) {
                                Text("Pages can take a minute to go live the first time.", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                TextButton(onClick = { runCatching { uri.openUri(url) } }) {
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(Space.xs)); Text("Open")
                                }
                                TextButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(Space.xs)); Text("Copy")
                                }
                            }
                        }.onFailure {
                            Text(it.message ?: "Deploy failed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    busy = "Starting…"
                    result = null
                    scope.launch {
                        val r = if (target == DeployTarget.NETLIFY) {
                            app.preferencesRepository.setNetlifyToken(netlifyToken)
                            deployer.deployNetlify(netlifyToken, name) { busy = it }
                        } else {
                            deployer.deployGitHubPages(githubToken, name) { busy = it }
                        }
                        busy = null
                        result = r
                        r.onSuccess(onDeployed)
                    }
                },
                enabled = busy == null && name.isNotBlank() &&
                    (if (target == DeployTarget.NETLIFY) netlifyToken.isNotBlank() else githubToken.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
                shape = OroCorners.pill
            ) {
                if (busy != null) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(Space.sm))
                    Text(busy.orEmpty())
                } else {
                    Text(if (previous != null) "Update site" else "Publish")
                }
            }
            if (target == DeployTarget.NETLIFY) {
                OutlinedButton(onClick = { runCatching { uri.openUri("https://app.netlify.com/user/applications#personal-access-tokens") } }, modifier = Modifier.fillMaxWidth(), shape = OroCorners.pill) {
                    Text("Get a Netlify token")
                }
            }
        }
    }
}
