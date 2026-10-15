package com.codex.stormy.ui.components.expressive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Helpers that read motion from [MaterialTheme.motionScheme] instead of
 * hard-coded durations.
 *
 * The point of the Expressive motion scheme is that every animation in the app
 * shares one set of springs, so a transition feels like the same system
 * wherever it happens. Hard-coding `tween(300)` in a screen breaks that, and it
 * also ignores the user's motion-style preference. These wrappers exist so call
 * sites can ask for "the default spatial animation" rather than inventing one.
 */
object ExpressiveMotion {
    /** Movement of something through space: position, size, shape. */
    val spatial: FiniteAnimationSpec<Float>
        @Composable get() = MaterialTheme.motionScheme.defaultSpatialSpec()

    val fastSpatial: FiniteAnimationSpec<Float>
        @Composable get() = MaterialTheme.motionScheme.fastSpatialSpec()

    val slowSpatial: FiniteAnimationSpec<Float>
        @Composable get() = MaterialTheme.motionScheme.slowSpatialSpec()

    /** Non-positional change: alpha, colour, elevation. */
    val effects: FiniteAnimationSpec<Float>
        @Composable get() = MaterialTheme.motionScheme.defaultEffectsSpec()

    val fastEffects: FiniteAnimationSpec<Float>
        @Composable get() = MaterialTheme.motionScheme.fastEffectsSpec()

    val slowEffects: FiniteAnimationSpec<Float>
        @Composable get() = MaterialTheme.motionScheme.slowEffectsSpec()
}

/**
 * Expressive press feedback: the target scales down slightly while held and
 * springs back on release.
 *
 * Driven from [InteractionSource] rather than from a click callback so that it
 * stays correct when the gesture is cancelled (finger dragged off the target),
 * where a click-driven animation would stay stuck in the pressed state.
 */
@Composable
fun Modifier.expressivePress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val spec = ExpressiveMotion.fastSpatial
    val scale = remember { Animatable(1f) }

    LaunchedEffect(pressed, spec) {
        scale.animateTo(if (pressed) pressedScale else 1f, spec)
    }

    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Animated 0f..1f progress for simple enter/exit effects, on the theme spec. */
@Composable
fun rememberExpressiveProgress(visible: Boolean): State<Float> {
    val spec = ExpressiveMotion.effects
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible, spec) {
        progress.animateTo(if (visible) 1f else 0f, spec)
    }
    return progress.asState()
}
