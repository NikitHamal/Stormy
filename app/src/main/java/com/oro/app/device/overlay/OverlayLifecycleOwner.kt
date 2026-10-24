package com.oro.app.device.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The lifecycle a ComposeView needs when it lives in a window no Activity owns.
 *
 * Compose looks both owners up from the view tree when the view attaches; an
 * accessibility-overlay window has neither, so the view would throw on attach.
 * The lifecycle is driven by the overlay: RESUMED while the island is on
 * screen (so brand animations, which only tick while STARTED, run), DESTROYED
 * when its window goes away (which disposes the composition).
 *
 * Main thread only, like any LifecycleRegistry.
 */
internal class OverlayLifecycleOwner : SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    /** Installs this owner on [view] so a ComposeView inside it can attach. */
    fun attachTo(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    fun resume() {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.DESTROYED
    }
}
