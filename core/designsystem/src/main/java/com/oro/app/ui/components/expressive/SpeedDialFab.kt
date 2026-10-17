package com.oro.app.ui.components.expressive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.ExpressiveShapes
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/**
 * Scale origin for the expanding rows: the bottom-right corner, where the
 * trigger sits. Anchoring the scale there makes every row grow out of the
 * button and shrink back into it, instead of pulsing about its own centre.
 */
private val TriggerOrigin = TransformOrigin(1f, 1f)

/**
 * The FAB's silhouette: a squircle with slightly unequal corners, so it reads
 * as hand-drawn rather than as a rounded rectangle.
 */
private val FabSquircle = BlobShape(
    topStart = 0.40f to 0.44f,
    topEnd = 0.44f to 0.40f,
    bottomEnd = 0.40f to 0.44f,
    bottomStart = 0.44f to 0.40f
)

/** One action in a [SpeedDialFab]. */
data class SpeedDialAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Expanding action button.
 *
 * Replaces a single extended FAB that could only do one thing, forcing every
 * other way of starting a project into a menu somewhere else. Here each action
 * is its own target with its own label, so the choice is visible before it is
 * made rather than after opening a menu.
 *
 * Layout follows the Material speed-dial pattern: a labelled pill paired with a
 * circular icon button per action, stacked above the trigger and right-aligned
 * so the icons form a single column under the thumb. The label sits to the
 * *left* of its icon rather than inside the button -- a pill wide enough for
 * "Clone repository" would otherwise shift the whole column sideways and move
 * every target each time the set changes.
 *
 * The trigger uses a four-leaf clover rather than a circle. A circle is the one
 * shape that carries no information, and this is the single most-used control
 * on the screen -- giving it a silhouette makes it findable peripherally, which
 * is what a FAB is for.
 */
@Composable
fun SpeedDialFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<SpeedDialAction>,
    modifier: Modifier = Modifier,
    collapsedDescription: String = "New",
    size: Dp = 64.dp
) {
    // The trigger rotates 45 degrees, turning the plus into the close glyph.
    // One shape doing both states reads as the same object changing rather than
    // two buttons swapping, which is what keeps the expansion legible.
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "speedDialRotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        // Declared order is preserved top-to-bottom; the stagger keys off each
        // row's *distance from the trigger* instead. The nearest row therefore
        // travels least and the far rows fan out, which is what makes the
        // column read as unfolding from the button rather than as a list
        // appearing all at once.
        actions.forEachIndexed { index, action ->
            val indexFromTrigger = actions.lastIndex - index
            AnimatedVisibility(
                visible = expanded,
                // Vertical, not horizontal. The previous version slid in from
                // the right, which pointed at the screen edge rather than at
                // the control the user had just touched -- the motion has to
                // originate where the gesture did.
                //
                // Expanding: each row rises into place from the trigger.
                // Collapsing: each row drops back down into it. Scaling about
                // the bottom-right corner anchors both to the trigger rather
                // than to the row's own centre.
                enter = fadeIn(
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
                ) + slideInVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                ) { height ->
                    // Offset grows with distance from the trigger, so the far
                    // rows travel further and the column fans open.
                    height * (indexFromTrigger + 1)
                } + scaleIn(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    initialScale = 0.7f,
                    transformOrigin = TriggerOrigin
                ),
                exit = fadeOut(
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
                ) + slideOutVertically(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                ) { height ->
                    height * (indexFromTrigger + 1)
                } + scaleOut(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    targetScale = 0.7f,
                    transformOrigin = TriggerOrigin
                )
            ) {
                SpeedDialRow(
                    action = action,
                    shapeIndex = index,
                    onInvoke = {
                        onExpandedChange(false)
                        action.onClick()
                    }
                )
            }
        }

        val triggerInteraction = remember { MutableInteractionSource() }

        Surface(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier
                .size(size)
                // No halo. A glow reads as an effect applied to the button
                // rather than as the button; the squircle's silhouette and
                // its full-contrast fill already make it the strongest thing
                // on screen.
                .expressivePress(triggerInteraction)
                .semantics {
                    contentDescription = if (expanded) "Close actions" else collapsedDescription
                },
            // A rounded squircle rather than the clover: at FAB size the
            // clover's four lobes fight the plus glyph in the middle, and the
            // reference uses the softer form.
            shape = FabSquircle,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            interactionSource = triggerInteraction
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier
                        .size(Sizes.iconLarge)
                        .rotate(rotation)
                )
            }
        }
    }
}

@Composable
private fun SpeedDialRow(
    action: SpeedDialAction,
    shapeIndex: Int,
    onInvoke: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The label is its own tappable surface, not decoration. On a phone the
        // icon alone is a small target, and reaching past the label to hit it
        // is the most common miss in this pattern.
        Surface(
            onClick = onInvoke,
            shape = OroCorners.pill,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLargeEmphasized,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md)
            )
        }

        Spacer(Modifier.width(Space.md))

        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onInvoke,
            modifier = Modifier
                .size(Sizes.minTouchTarget)
                .expressivePress(interaction),
            // Each action carries a different silhouette, so the column is
            // distinguishable by shape once the labels are familiar.
            shape = ExpressiveShapes.forIndex(shapeIndex + 1),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            interactionSource = interaction
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = action.icon,
                    // The label beside it already names the action; repeating
                    // it here makes a screen reader announce every row twice.
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.icon)
                )
            }
        }
    }
}

/** Scrim shown behind an expanded [SpeedDialFab]; tapping it collapses. */
@Composable
fun SpeedDialScrim(expanded: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
    ) {
        Surface(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Close actions" },
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
            content = {}
        )
    }
}
