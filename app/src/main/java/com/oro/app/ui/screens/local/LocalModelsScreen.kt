package com.oro.app.ui.screens.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oro.app.data.ai.local.LocalModels
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space
import kotlinx.coroutines.launch

/** Settings > On-device models: download, remove, see what fits this phone. */
@Composable
fun LocalModelsScreen(onBack: () -> Unit) {
    val states by LocalModels.states.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { LocalModels.refresh() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("On-device models", style = MaterialTheme.typography.titleLargeEmphasized) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            item(key = "intro") {
                Text(
                    "These run entirely on your phone: no internet, no account, no limits. They are small, so they " +
                        "suit chat and small edits; agent work on big projects is better with a cloud model. " +
                        "Pick them under \"On this phone\" in the model picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(LocalModels.catalogue, key = { it.id }) { spec ->
                val state = states[spec.id] ?: LocalModels.State.Missing
                Surface(shape = OroCorners.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(spec.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${"%.1f".format(spec.bytes / 1e9)} GB · ${spec.maxTokens / 1024.0}K context",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(spec.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when (state) {
                            LocalModels.State.Ready -> Row {
                                Text("Ready", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = { LocalModels.delete(spec) }, shape = OroCorners.pill) { Text("Remove", maxLines = 1) }
                            }
                            is LocalModels.State.Downloading -> Column {
                                LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                                Text("${(state.fraction * 100).toInt()}% — keep Oro open; it resumes if interrupted", style = MaterialTheme.typography.labelSmall)
                            }
                            is LocalModels.State.Failed -> Column {
                                Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { scope.launch { LocalModels.download(spec) } }, shape = OroCorners.pill) { Text("Resume", maxLines = 1) }
                            }
                            LocalModels.State.Missing -> Button(
                                onClick = { scope.launch { LocalModels.download(spec) } },
                                shape = OroCorners.pill
                            ) { Text("Download", maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}
