package com.codex.stormy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.codex.stormy.data.repository.ThemeMode
import com.codex.stormy.ui.navigation.CodeXNavGraph
import com.codex.stormy.ui.navigation.Screen
import com.codex.stormy.ui.theme.CodeXTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val preferencesRepository by lazy {
        (application as CodeXApplication).preferencesRepository
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

        enableEdgeToEdge()

        setContent {
            val themeMode by preferencesRepository.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            val dynamicColors by preferencesRepository.dynamicColors.collectAsStateWithLifecycle(
                initialValue = false
            )

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            CodeXTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColors
            ) {
                // Null only while the splash screen is still covering us.
                startDestination.value?.let { MainContent(startDestination = it) }
            }
        }
    }
}

@Composable
private fun MainContent(startDestination: String) {
    val navController = rememberNavController()

    CodeXNavGraph(
        navController = navController,
        startDestination = startDestination
    )
}
