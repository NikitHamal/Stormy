package com.codex.stormy.utils

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manages multiple drawer states with proper coordination to prevent conflicts.
 * This is a centralized solution for multi-drawer screens like the Editor.
 *
 * Key features:
 * - Ensures only one drawer is open at a time
 * - Provides state tracking for back button handling
 * - Handles proper closing animation sequencing
 */
@Stable
class DrawerStateManager(
    val drawers: Map<String, DrawerState>,
    private val scope: CoroutineScope
) {
    /**
     * Check if any drawer is currently open
     */
    val isAnyDrawerOpen: Boolean
        get() = drawers.values.any { it.isOpen }

    /**
     * Get the currently open drawer name, or null if none are open
     */
    val currentlyOpenDrawer: String?
        get() = drawers.entries.firstOrNull { it.value.isOpen }?.key

    /**
     * Open a specific drawer, closing any others first
     */
    suspend fun openDrawer(drawerName: String) {
        val drawerToOpen = drawers[drawerName] ?: return

        // Close all other drawers first
        drawers.forEach { (name, state) ->
            if (name != drawerName && state.isOpen) {
                state.close()
            }
        }

        // Open the requested drawer
        drawerToOpen.open()
    }

    /**
     * Close a specific drawer
     */
    suspend fun closeDrawer(drawerName: String) {
        drawers[drawerName]?.close()
    }

    /**
     * Close all drawers
     */
    suspend fun closeAllDrawers() {
        // Close in reverse priority order (innermost first for proper animation)
        drawers.values.reversed().forEach { drawer ->
            if (drawer.isOpen) {
                drawer.close()
            }
        }
    }

    /**
     * Force close all drawers without animation (for initial state)
     */
    suspend fun snapAllClosed() {
        drawers.values.forEach { drawer ->
            drawer.snapTo(DrawerValue.Closed)
        }
    }

    /**
     * Launch a coroutine to open a drawer (convenience method)
     */
    fun launchOpenDrawer(drawerName: String) {
        scope.launch { openDrawer(drawerName) }
    }

    /**
     * Launch a coroutine to close a drawer (convenience method)
     */
    fun launchCloseDrawer(drawerName: String) {
        scope.launch { closeDrawer(drawerName) }
    }

    /**
     * Launch a coroutine to close all drawers (convenience method)
     */
    fun launchCloseAllDrawers() {
        scope.launch { closeAllDrawers() }
    }

    /**
     * Handle back button press - closes open drawer if any
     * @return true if a drawer was closed, false if no drawer was open
     */
    fun handleBackPress(): Boolean {
        if (isAnyDrawerOpen) {
            launchCloseAllDrawers()
            return true
        }
        return false
    }

    /**
     * Check if a specific drawer is open
     */
    fun isDrawerOpen(drawerName: String): Boolean {
        return drawers[drawerName]?.isOpen == true
    }

    /**
     * Get drawer state by name
     */
    operator fun get(drawerName: String): DrawerState? = drawers[drawerName]
}

/**
 * Remember a DrawerStateManager with the given drawer configurations
 *
 * @param drawerNames List of drawer names to manage
 * @return A stable DrawerStateManager instance
 *
 * Example usage:
 * ```
 * val drawerManager = rememberDrawerStateManager(
 *     "files", "assets", "git"
 * )
 *
 * // In BackHandler:
 * BackHandler(enabled = drawerManager.isAnyDrawerOpen) {
 *     drawerManager.launchCloseAllDrawers()
 * }
 *
 * // To open a drawer:
 * onClick = { drawerManager.launchOpenDrawer("files") }
 *
 * // Access individual drawer state:
 * ModalNavigationDrawer(
 *     drawerState = drawerManager["files"]!!,
 *     ...
 * )
 * ```
 */
@Composable
fun rememberDrawerStateManager(
    vararg drawerNames: String
): DrawerStateManager {
    val scope = rememberCoroutineScope()

    val drawers = remember(drawerNames.contentHashCode()) {
        drawerNames.associateWith { DrawerState(DrawerValue.Closed) }
    }

    return remember(drawers, scope) {
        DrawerStateManager(drawers, scope)
    }
}

/**
 * Composable state for tracking if any drawer is open
 * Useful for BackHandler enabled state
 */
@Composable
fun DrawerStateManager.rememberIsAnyDrawerOpen(): Boolean {
    val isOpen by remember(this) {
        derivedStateOf { isAnyDrawerOpen }
    }
    return isOpen
}
