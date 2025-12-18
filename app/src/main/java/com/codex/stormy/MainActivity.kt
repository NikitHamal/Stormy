package com.codex.stormy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.codex.stormy.data.repository.ThemeMode
import com.codex.stormy.ui.navigation.CodeXNavGraph
import com.codex.stormy.ui.navigation.Screen
import com.codex.stormy.ui.theme.CodeXTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val preferencesRepository by lazy {
        (application as CodeXApplication).preferencesRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var isReady = false
        var onboardingCompleted = false

        runBlocking {
            onboardingCompleted = preferencesRepository.onboardingCompleted.first()
            isReady = true
        }

        splashScreen.setKeepOnScreenCondition { !isReady }

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
                MainContent(
                    startDestination = if (onboardingCompleted) {
                        Screen.Home.route
                    } else {
                        Screen.Onboarding.route
                    }
                )
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
