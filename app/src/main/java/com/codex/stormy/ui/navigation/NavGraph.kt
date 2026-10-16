package com.codex.stormy.ui.navigation

import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.stormy.ui.screens.settings.SettingsViewModel
import com.codex.stormy.ui.screens.providers.ProvidersScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.codex.stormy.ui.screens.aimodels.AiModelsScreen
import com.codex.stormy.ui.screens.editor.EditorScreen
import com.codex.stormy.ui.screens.git.GitSettingsScreen
import com.codex.stormy.ui.screens.home.HomeScreen
import com.codex.stormy.ui.screens.memories.MemoriesScreen
import com.codex.stormy.ui.screens.onboarding.OnboardingScreen
import com.codex.stormy.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Editor : Screen("editor/{projectId}") {
        fun createRoute(projectId: String) = "editor/$projectId"
    }
    data object Settings : Screen("settings")
    data object AiModels : Screen("ai_models")
    data object Memories : Screen("memories")
    data object GitSettings : Screen("git_settings")
    data object Providers : Screen("providers")
}

@Composable
fun CodeXNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    // NavHost's transition lambdas are not @Composable, so the scheme is read
    // here, in composable scope, and captured. Reading it per transition would
    // not compile -- and would be wasteful even if it did.
    // One spring for the slide, so both screens move on the same curve and
    // stay locked together through the transition.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val enterFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        // Forward and back are deliberately asymmetric.
        //
        // The old version slid both screens the same distance in opposite
        // directions, which reads as a carousel: two peers swapping places,
        // with no sense of depth. Pushing forward should feel like going
        // *into* something.
        //
        // So: the incoming screen travels the full width while the outgoing
        // one moves a third of it and dims slightly -- parallax, which the eye
        // reads as the old screen receding behind the new one rather than
        // sliding away beside it. Going back reverses exactly that, so the
        // screen the user came from appears to come forward again.
        //
        // Scale is deliberately absent on the incoming screen: scaling a full
        // page of text makes it legible only once it lands, which is the one
        // moment the animation should already be over.
        enterTransition = {
            slideInHorizontally(spatial) { full -> full } +
                fadeIn(enterFade)
        },
        exitTransition = {
            slideOutHorizontally(spatial) { full -> -full / 3 } +
                fadeOut(exitFade)
        },
        popEnterTransition = {
            slideInHorizontally(spatial) { full -> -full / 3 } +
                fadeIn(enterFade)
        },
        popExitTransition = {
            slideOutHorizontally(spatial) { full -> full } +
                fadeOut(exitFade)
        }
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onProjectClick = { projectId ->
                    navController.navigate(Screen.Editor.createRoute(projectId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Editor.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            EditorScreen(
                projectId = projectId,
                onBackClick = {
                    navController.popBackStack()
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onGitSettingsClick = {
                    navController.navigate(Screen.GitSettings.route)
                }
            )
        }

        composable(Screen.Providers.route) {
            val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            ProvidersScreen(
                apiKeys = uiState.apiKeys,
                customBaseUrl = uiState.customBaseUrl,
                onKeyChange = viewModel::setApiKey,
                onCustomBaseUrlChange = viewModel::setCustomBaseUrl,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onNavigateToAiModels = {
                    navController.navigate(Screen.AiModels.route)
                },
                onNavigateToMemories = {
                    navController.navigate(Screen.Memories.route)
                },
                onNavigateToGitSettings = {
                    navController.navigate(Screen.GitSettings.route)
                },
                onNavigateToProviders = {
                    navController.navigate(Screen.Providers.route)
                }
            )
        }

        composable(Screen.AiModels.route) {
            AiModelsScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onNavigateToProviders = { navController.navigate(Screen.Providers.route) }
            )
        }

        composable(Screen.Memories.route) {
            MemoriesScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.GitSettings.route) {
            GitSettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
