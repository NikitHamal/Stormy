package com.oro.app.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oro.app.OroApplication
import com.oro.app.ui.theme.ExpressiveShapes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The mic in the prompt bar. Tap to dictate, tap again to stop. Text goes to
 * [onText] (appended by the caller); [onPartial] shows live words while the
 * system recogniser is listening. Errors go to [onError] for a snackbar.
 */
@Composable
fun DictationButton(
    onText: (String) -> Unit,
    onPartial: (String?) -> Unit = {},
    onError: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dictation = remember { Dictation(context.applicationContext) }
    val engineName by OroApplication.getInstance().preferencesRepository.dictationEngineName
        .collectAsStateWithLifecycle("SYSTEM")
    val engine = DictationEngine.entries.firstOrNull { it.name == engineName } ?: DictationEngine.SYSTEM
    val state by dictation.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun start() {
        when (engine) {
            DictationEngine.SYSTEM -> dictation.startSystem()
            DictationEngine.WHISTLE -> {
                Whistle.refresh(context)
                if (Whistle.state.value != Whistle.State.Installed) {
                    onError("Download the Whistle model in Settings > Voice dictation, or switch to System.")
                    return
                }
                job = scope.launch { dictation.recordWithWhistle() }
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else onError("Microphone access is needed for dictation.")
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is Dictation.State.Listening -> onPartial(s.partial.takeIf { it.isNotBlank() })
            is Dictation.State.Done -> { onPartial(null); if (s.text.isNotBlank()) onText(s.text); dictation.reset() }
            is Dictation.State.Error -> { onPartial(null); onError(s.message); dictation.reset() }
            else -> Unit
        }
    }
    DisposableEffect(Unit) { onDispose { dictation.cancel(job) } }

    val listening = state is Dictation.State.Listening
    val busy = state is Dictation.State.Transcribing
    val level = (state as? Dictation.State.Listening)?.level ?: 0f
    val pulse by animateFloatAsState(if (listening) 1f + 0.18f * level else 1f, label = "micPulse")
    val scheme = MaterialTheme.colorScheme

    Surface(
        onClick = {
            when {
                busy -> Unit
                listening -> if (engine == DictationEngine.SYSTEM) dictation.finishSystem() else dictation.stopWhistle()
                dictation.hasMicPermission() -> start()
                else -> permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = modifier.size(40.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
        shape = ExpressiveShapes.circle(),
        color = if (listening) scheme.errorContainer else scheme.surfaceContainerHigh,
        contentColor = if (listening) scheme.onErrorContainer else scheme.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                listening -> Icon(Icons.Rounded.Stop, contentDescription = "Stop dictation", modifier = Modifier.size(20.dp))
                else -> Icon(Icons.Outlined.Mic, contentDescription = "Dictate", modifier = Modifier.size(20.dp))
            }
        }
    }
}
