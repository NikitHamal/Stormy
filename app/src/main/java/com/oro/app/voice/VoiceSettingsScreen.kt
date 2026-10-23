package com.oro.app.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oro.app.OroApplication
import com.oro.app.ui.components.expressive.PickerOption
import com.oro.app.ui.components.expressive.SegmentedPicker
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space
import kotlinx.coroutines.launch

/** Settings > Voice dictation: pick the engine, install Whistle. */
@Composable
fun VoiceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { OroApplication.getInstance().preferencesRepository }
    val engineName by prefs.dictationEngineName.collectAsStateWithLifecycle("SYSTEM")
    val engine = DictationEngine.entries.firstOrNull { it.name == engineName } ?: DictationEngine.SYSTEM
    val whistle by Whistle.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Whistle.refresh(context) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Voice dictation", style = MaterialTheme.typography.titleLargeEmphasized) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            Text(
                "Tap the mic in the prompt bar and speak. Both engines can run with no internet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SegmentedPicker(
                label = "Engine",
                options = DictationEngine.entries.map { PickerOption(it, it.label) },
                selected = engine,
                onSelect = { scope.launch { prefs.setDictationEngineName(it.name) } }
            )
            Card(
                title = "System",
                body = "Android's own recogniser, free with no limits. It prefers Google's offline model; if it " +
                    "asks for a network, download your language in Android Settings > System > Languages > " +
                    "Speech (on-device recognition)."
            )
            Card(
                title = "Whistle (on-device, 16.9 MB)",
                body = "Cactus Compute's speech model, running entirely on this phone: private, offline, " +
                    "English, German, French, Spanish, Italian, Dutch and Polish."
            ) {
                when (val s = whistle) {
                    Whistle.State.Unsupported -> Text("Not available on this phone's processor.", color = MaterialTheme.colorScheme.error)
                    Whistle.State.Installed -> Row {
                        Text("Installed", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { Whistle.delete(context) }, shape = OroCorners.pill) { Text("Remove", maxLines = 1) }
                    }
                    is Whistle.State.Downloading -> Column {
                        LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
                        Text("${(s.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                    is Whistle.State.Failed -> Column {
                        Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { scope.launch { Whistle.download(context) } }, shape = OroCorners.pill) { Text("Try again", maxLines = 1) }
                    }
                    Whistle.State.NotInstalled -> Button(
                        onClick = {
                            scope.launch {
                                Whistle.download(context).onSuccess { prefs.setDictationEngineName(DictationEngine.WHISTLE.name) }
                            }
                        },
                        shape = OroCorners.pill
                    ) { Text("Download Whistle", maxLines = 1) }
                }
            }
            Spacer(Modifier.width(Space.xl))
        }
    }
}

@Composable
private fun Card(title: String, body: String, extra: (@Composable () -> Unit)? = null) {
    Surface(shape = OroCorners.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            extra?.invoke()
        }
    }
}
