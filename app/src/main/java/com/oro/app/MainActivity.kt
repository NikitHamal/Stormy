package com.oro.app

import androidx.compose.runtime.LaunchedEffect
import androidx.activity.SystemBarStyle
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.oro.app.data.repository.LiteMode
import com.oro.app.data.repository.ThemeMode
import com.oro.app.ui.theme.DevicePerformance
import com.oro.app.ui.navigation.OroNavGraph
import com.oro.app.ui.navigation.Screen
import com.oro.app.ui.theme.OroTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val preferencesRepository by lazy {
        (application as OroApplication).preferencesRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // The start destination depends on a DataStore read. Doing that with
        // runBlocking stalled the main thread for the whole disk read before the
        // first frame; instead the splash screen stays up while the read happens
        // off-thread, which is exactly what setKeepOnScreenCondition is for.
        // Backed by snapshot state so that setContent below actually recomposes
        // when the read lands; a plain captured var would never notify Compose.
        val startDestination = mutableStateOf<String?>(null)
        splashScreen.setKeepOnScreenCondition { startDestination.value == null }

        lifecycleScope.launch {
            val onboardingCompleted = preferencesRepository.onboardingCompleted.first()
            startDestination.value =
                if (onboardingCompleted) Screen.Home.route else Screen.Onboarding.route
        }

        setContent {
            val themeMode by preferencesRepository.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // System bar icons have to follow the *app's* theme, not the
            // system's. enableEdgeToEdge() with no arguments picks its style
            // from the system dark-mode setting, so an app set to Light on a
            // phone in Dark mode got white icons on a near-white bar -- the
            // status bar simply vanished. Re-applied whenever the theme
            // changes, because the user can flip it from Settings without the
            // activity being recreated.
            LaunchedEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            // Lite mode: AUTO resolves against the device class (computed once
            // per process), so budget phones get the cheaper rendering path
            // without the user having to find the setting.
            val liteModeChoice by preferencesRepository.liteMode.collectAsStateWithLifecycle(
                initialValue = LiteMode.AUTO
            )
            val lowEndDevice = remember { DevicePerformance.isLowEndDevice(applicationContext) }
            val liteMode = liteModeChoice.isEnabled(lowEndDevice)

            OroTheme(darkTheme = darkTheme, liteMode = liteMode) {
                // Null only while the splash screen is still covering us.
                startDestination.value?.let { MainContent(startDestination = it) }
            }
        }
    }
}

@Composable
private fun MainContent(startDestination: String) {
    val navController = rememberNavController()

    OroNavGraph(
        navController = navController,
        startDestination = startDestination
    )
}
