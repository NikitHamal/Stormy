package com.oro.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.oro.app.device.DeviceAgent
import com.oro.app.ui.components.chat.ModernChatInput
import com.oro.app.ui.screens.device.DeviceSettingsSheet
import com.oro.app.ui.screens.device.DeviceWelcome
import com.oro.app.ui.screens.device.RunBar
import com.oro.app.ui.screens.device.RunTranscriptList
import com.oro.app.ui.screens.device.SetupState
import com.oro.app.ui.theme.OroTheme
import com.oro.app.ui.theme.Space
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The device agent screen, light and dark, in each state. The frame below
 * mirrors DeviceAgentScreen's own Scaffold (top bar, content, run bar,
 * composer) around its real inner composables, since the screen itself needs
 * the ViewModel and a running app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = ShotsApplication::class)
class DeviceScreenShots {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun stubApp() = OroAppStub.install()

    @Composable
    private fun Screen(
        run: DeviceAgent.State,
        dark: Boolean,
        serviceEnabled: Boolean = true,
        history: List<String> = emptyList(),
        draft: String = "",
    ) {
        OroTheme(darkTheme = dark) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Device agent", fontWeight = FontWeight.SemiBold) },
                            navigationIcon = {
                                IconButton(onClick = {}) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        val started = run.startedAt > 0L
                        when {
                            !serviceEnabled && !run.running -> SetupState(
                                onEnable = {}, onAppInfo = {}, modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                            !started -> DeviceWelcome(
                                history = history, onPick = {}, modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                            else -> RunTranscriptList(
                                run = run, onAnswer = {}, onAgain = {}, onNew = {},
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                        if (serviceEnabled || run.running) {
                            if (run.running) {
                                RunBar(
                                    run = run,
                                    onPause = {},
                                    modifier = Modifier.padding(start = Space.lg, end = Space.lg, top = Space.sm)
                                )
                            }
                            ModernChatInput(
                                value = draft,
                                onValueChange = {},
                                onSend = {},
                                onStop = {},
                                isEnabled = serviceEnabled && !run.running,
                                isProcessing = run.running,
                                currentModel = ShotModel,
                                onModelClick = {},
                                onSettingsClick = {},
                                placeholder = "What should Orobi do?",
                                settingsDescription = "Device agent settings",
                                mentions = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = Space.lg, end = Space.lg, bottom = Space.lg,
                                        top = if (run.running) Space.xs + Space.xxs else Space.lg
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    private val history = listOf("Turn on Do Not Disturb until 8 am", "What's the weather in Kathmandu tomorrow?")

    @Test
    fun device_setup_light() = compose.shoot("device_setup_light") { Screen(Runs.idle(), dark = false, serviceEnabled = false) }

    @Test
    fun device_setup_dark() = compose.shoot("device_setup_dark") { Screen(Runs.idle(), dark = true, serviceEnabled = false) }

    @Test
    fun device_welcome_light() = compose.shoot("device_welcome_light") { Screen(Runs.idle(), dark = false, history = history) }

    @Test
    fun device_welcome_dark() = compose.shoot("device_welcome_dark") { Screen(Runs.idle(), dark = true, history = history) }

    @Test
    fun device_running_light() = compose.shoot("device_running_light") { Screen(Runs.working(), dark = false) }

    @Test
    fun device_running_dark() = compose.shoot("device_running_dark") { Screen(Runs.working(), dark = true) }

    @Test
    fun device_question_light() = compose.shoot("device_question_light") { Screen(Runs.question(), dark = false) }

    @Test
    fun device_finished_light() = compose.shoot("device_finished_light") { Screen(Runs.finished(), dark = false) }

    @Test
    fun device_finished_dark() = compose.shoot("device_finished_dark") { Screen(Runs.finished(), dark = true) }

    @Test
    fun device_failed_dark() = compose.shoot("device_failed_dark") { Screen(Runs.failed(), dark = true) }

    /** The settings sheet is a modal window, so the whole screen is captured. */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun device_settings_light() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Screen(Runs.idle(), dark = false, history = history)
            OroTheme(darkTheme = false) {
                DeviceSettingsSheet(settings = DeviceAgent.Settings(), onChange = {}, onDismiss = {})
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        captureScreenRoboImage("$SHOTS_DIR/device_settings_light.png")
    }
}
