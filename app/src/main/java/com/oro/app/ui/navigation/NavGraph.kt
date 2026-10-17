package com.oro.app.ui.navigation

import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oro.app.ui.screens.settings.SettingsViewModel
import com.oro.app.ui.screens.providers.ProvidersScreen
import com.oro.app.ui.screens.providers.ProvidersViewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.oro.app.ui.screens.aimodels.AiModelsScreen
import com.oro.app.ui.screens.editor.EditorScreen
import com.oro.app.ui.screens.git.GitSettingsScreen
import com.oro.app.ui.screens.home.HomeScreen
import com.oro.app.ui.screens.memories.MemoriesScreen
import com.oro.app.ui.screens.onboarding.OnboardingScreen
import com.oro.app.ui.screens.settings.SettingsScreen

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
    data object Mcp : Screen("mcp")
    data object ProviderEditor : Screen("provider_editor/{ref}") {
        fun createRoute(ref: String) = "provider_editor/" + android.net.Uri.encode(ref)
    }
}

@Composable
fun OroNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    // NavHost's transition lambdas are not @Composable, so the specs are read
    // here, in composable scope, and captured. Reading them per transition
    // would not compile -- and would be wasteful even if it did.
    //
    // One spring per property, shared by both screens in a transition, so they
    // move on the same curve and stay locked together rather than arriving at
    // slightly different times.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val scaleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val enterFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    // --- The lateral push, used by every screen that is a sibling ---------
    //
    // Forward and back are deliberately asymmetric. Sliding both screens the
    // same distance in opposite directions reads as a carousel: two peers
    // swapping places, with no sense of depth. Pushing forward should feel
    // like going *into* something.
    //
    // So the incoming screen travels the full width while the outgoing one
    // moves a third of it, scales down a little and dims -- parallax, which
    // the eye reads as the old screen receding behind the new one. It is
    // dimmed to 0.6 rather than to nothing: it is *behind* the new screen, not
    // gone, and a full fade turns the transition into a cut.
    //
    // Scale is deliberately absent on the incoming screen. Scaling a full page
    // of text makes it legible only once it lands, which is the one moment the
    // animation should already be over.
    val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(spatial) { full -> full } + fadeIn(enterFade)
    }
    val pushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(spatial) { full -> -full / 3 } +
            scaleOut(scaleSpec, targetScale = RecedeScale) +
            fadeOut(exitFade, targetAlpha = RecedeAlpha)
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(spatial) { full -> -full / 3 } +
            scaleIn(scaleSpec, initialScale = RecedeScale) +
            fadeIn(enterFade, initialAlpha = RecedeAlpha)
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(spatial) { full -> full } + fadeOut(exitFade)
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = pushEnter,
        exitTransition = pushExit,
        popEnterTransition = popEnter,
        popExitTransition = popExit
    ) {
        // Leaving onboarding is not a push -- there is nothing to go back to.
        // The onboarding fades itself out as Orobi hops; Home then rises in
        // from slightly smaller, so the hand-off reads as arriving rather
        // than as navigating sideways.
        composable(
            route = Screen.Onboarding.route,
            exitTransition = { fadeOut(exitFade) }
        ) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Home.route,
            enterTransition = {
                if (initialState.destination.route == Screen.Onboarding.route) {
                    scaleIn(scaleSpec, initialScale = ArriveScale) + fadeIn(enterFade)
                } else {
                    pushEnter(this)
                }
            }
        ) {
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
            ),
            // Opening a project is not a sideways move between peers -- it is
            // going *into* the card that was tapped. So the editor expands
            // into place while the list grows past the viewport behind it,
            // which is the closest a route-level transition gets to a
            // container transform without a shared element to anchor to.
            //
            // Going back reverses it: the editor shrinks away and the list
            // comes back down to size, so the project card feels like where
            // the user returned to.
            enterTransition = {
                scaleIn(scaleSpec, initialScale = OpenIntoScale) + fadeIn(enterFade)
            },
            exitTransition = {
                scaleOut(scaleSpec, targetScale = OpenIntoScale) + fadeOut(exitFade)
            },
            popEnterTransition = {
                scaleIn(scaleSpec, initialScale = PushPastScale) +
                    fadeIn(enterFade, initialAlpha = RecedeAlpha)
            },
            popExitTransition = {
                scaleOut(scaleSpec, targetScale = OpenIntoScale) + fadeOut(exitFade)
            }
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

        composable(Screen.Mcp.route) {
            com.oro.app.ui.screens.mcp.McpScreen(onBackClick = { navController.popBackStack() })
        }

        composable(Screen.Providers.route) {
            val viewModel: ProvidersViewModel = viewModel(factory = ProvidersViewModel.Factory)
            ProvidersScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onOpenEditor = { ref -> navController.navigate(Screen.ProviderEditor.createRoute(ref)) }
            )
        }

        composable(Screen.ProviderEditor.route) { entry ->
            val viewModel: ProvidersViewModel = viewModel(factory = ProvidersViewModel.Factory)
            com.oro.app.ui.screens.providers.ProviderEditorScreen(
                viewModel = viewModel,
                route = entry.arguments?.getString("ref"),
                onBack = { navController.popBackStack() }
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
                },
                onNavigateToMcp = {
                    navController.navigate(Screen.Mcp.route)
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

/**
 * How far the screen being covered pulls back.
 *
 * Small on purpose. Past about 6% the page visibly reflows its own layout
 * during the animation, which costs more than the depth cue is worth.
 */
private const val RecedeScale = 0.94f

/** How far it dims. Behind the new screen, not gone. */
private const val RecedeAlpha = 0.6f

/** The editor's arrival: close enough to feel like the card growing. */
private const val OpenIntoScale = 0.92f

/** The list behind it, which grows past the viewport rather than shrinking. */
private const val PushPastScale = 1.06f

/** Home's first appearance, straight after onboarding. */
private const val ArriveScale = 0.96f
