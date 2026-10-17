package com.oro.app.ui.components.agent

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.oro.app.ui.theme.ExpressiveShapes
import com.oro.app.ui.theme.Sizes

/**
 * Visual identity for agents.
 *
 * Each agent type gets a fixed colour *and* a fixed shape. The shape is the
 * part that matters: on a phone, several agent cards stacked in a list are
 * distinguished faster by silhouette than by hue, and colour alone fails for
 * users who cannot separate the hues. Material 3 Expressive ships a shape
 * library precisely so that shape can carry meaning, and this is the clearest
 * case for it in the app.
 */
@Immutable
data class AgentAccent(
    val container: Color,
    val onContainer: Color,
    val solid: Color
)

object AgentVisuals {

    /**
     * Accent for an agent type, by its stable index.
     *
     * Pulled from the theme rather than hard-coded, so agents stay legible
     * under dynamic colour and in both light and dark.
     */
    @Composable
    fun accent(accentIndex: Int): AgentAccent {
        val scheme = MaterialTheme.colorScheme
        val palette = listOf(
            AgentAccent(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary),
            AgentAccent(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary),
            AgentAccent(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary),
            AgentAccent(scheme.errorContainer, scheme.onErrorContainer, scheme.error),
            AgentAccent(scheme.surfaceContainerHighest, scheme.onSurface, scheme.outline)
        )
        return palette[((accentIndex % palette.size) + palette.size) % palette.size]
    }

    /** Silhouette for an agent type. Paired 1:1 with [accent] by index. */
    @Composable
    fun shape(accentIndex: Int) = when (((accentIndex % 5) + 5) % 5) {
        0 -> ExpressiveShapes.cookie9()   // Explore
        1 -> ExpressiveShapes.clover4()   // Plan
        2 -> ExpressiveShapes.gem()       // Build
        3 -> ExpressiveShapes.verySunny() // Review
        else -> ExpressiveShapes.circle() // General
    }
}

/**
 * An agent's badge: its shape, its colour, and its initial.
 *
 * While the agent is working the badge breathes -- a slow scale pulse rather
 * than a spinner. A spinner says "waiting"; this says "alive", which is the
 * honest signal for something that may run for a minute, and it costs one
 * animated float instead of a continuously redrawn arc.
 */
@Composable
fun AgentBadge(
    label: String,
    accentIndex: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.avatar
) {
    val accent = AgentVisuals.accent(accentIndex)
    val shape = AgentVisuals.shape(accentIndex)

    val pulse = if (active) {
        val transition = rememberInfiniteTransition(label = "agentPulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            // Infinite, so this cannot come from the motion scheme -- those
            // specs are finite by type. Slow and shallow on purpose: a fast
            // pulse on a list of cards reads as an error state.
            animationSpec = infiniteRepeatable(
                animation = tween(1_100),
                repeatMode = RepeatMode.Reverse
            ),
            label = "agentPulseScale"
        ).value
    } else {
        1f
    }

    val container by animateColorAsState(
        if (active) accent.container else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "agentBadgeContainer"
    )

    Surface(
        modifier = modifier
            .size(size)
            .scale(pulse),
        shape = shape,
        color = container
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label.take(1).uppercase(),
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = if (active) accent.onContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
