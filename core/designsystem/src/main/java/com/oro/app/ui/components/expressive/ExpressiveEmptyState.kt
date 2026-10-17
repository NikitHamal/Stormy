package com.oro.app.ui.components.expressive

import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.ExpressiveShapes
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/** Which silhouette an empty state uses. */
enum class EmptyStateShape { Cookie, Clover, Flower, Burst, Sunny }

/**
 * The shared empty state.
 *
 * Every empty screen in the app renders through this, so "nothing here yet"
 * looks like one idea rather than five people's idea of a placeholder. A
 * circle was the previous default, which is the one shape that says nothing;
 * these carry the same silhouette language as the agent badges and project
 * marks.
 *
 * ### The animation, and why it is this restrained
 *
 * The mark rotates *very* slowly and breathes. Both are below the threshold
 * where motion competes for attention -- an empty state is something the user
 * looks at while deciding what to do, so it should feel alive without asking
 * to be watched. A bouncing or spinning illustration reads as a loading
 * indicator, which is actively misleading here: nothing is loading, there is
 * simply nothing yet.
 *
 * The content also arrives staggered on first composition, so the screen
 * assembles rather than appearing, which makes an empty screen feel like a
 * destination instead of a failure.
 */
@Composable
fun ExpressiveEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    shape: EmptyStateShape = EmptyStateShape.Cookie,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    markSize: Dp = 96.dp,
    /**
     * Replaces the shaped icon badge, e.g. with Orobi. Gets the same entrance
     * scale; its own idle motion is its business.
     */
    illustration: (@Composable () -> Unit)? = null
) {
    // --- idle motion ------------------------------------------------------
    val transition = rememberInfiniteTransition(label = "emptyState")

    // One rotation every 40 seconds. Fast enough that the shape is never
    // quite where it was, slow enough that it is never seen moving.
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "emptyRotation"
    )

    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyBreath"
    )

    // --- entrance ---------------------------------------------------------
    val markEnter = remember { Animatable(0f) }
    val textEnter = remember { Animatable(0f) }
    val actionEnter = remember { Animatable(0f) }

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        markEnter.animateTo(1f, spatial)
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(90)
        textEnter.animateTo(1f, spatial)
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(180)
        actionEnter.animateTo(1f, spatial)
    }

    Column(
        modifier = modifier.padding(horizontal = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (illustration != null) {
            Box(
                modifier = Modifier.scale(markEnter.value),
                contentAlignment = Alignment.Center
            ) { illustration() }
        } else {
            Surface(
                modifier = Modifier
                    .size(markSize)
                    // Rotation is applied to the container only, so the glyph
                    // inside stays upright -- a rotating icon reads as broken.
                    .rotate(rotation)
                    .scale(breath * markEnter.value),
                shape = shape.toShape(),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(markSize / 2.6f)
                            .rotate(-rotation),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.xl))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scale(0.96f + 0.04f * textEnter.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.xl))
            Button(
                onClick = onAction,
                modifier = Modifier.scale(0.9f + 0.1f * actionEnter.value),
                shape = OroCorners.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs)
                )
            }
        }
    }
}

@Composable
private fun EmptyStateShape.toShape() = when (this) {
    EmptyStateShape.Cookie -> ExpressiveShapes.cookie9()
    EmptyStateShape.Clover -> ExpressiveShapes.clover4()
    EmptyStateShape.Flower -> ExpressiveShapes.flower()
    EmptyStateShape.Burst -> ExpressiveShapes.softBurst()
    EmptyStateShape.Sunny -> ExpressiveShapes.sunny()
}

/**
 * The no-results variant.
 *
 * Separate from [ExpressiveEmptyState] because the two mean different things
 * and need different actions: "nothing exists yet" invites you to create
 * something, "nothing matched" invites you to change the query. Showing the
 * create button on a failed search is a small cruelty.
 */
@Composable
fun ExpressiveNoResults(
    query: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    noun: String = "results"
) {
    val shake = remember { Animatable(0f) }

    // A single short shake on arrival, not a loop. It says "that didn't
    // match" once; repeating it would nag.
    LaunchedEffect(query) {
        shake.snapTo(0f)
        shake.animateTo(1f, tween(420))
    }

    val offset = kotlin.math.sin(shake.value * Math.PI.toFloat() * 3f) *
        (1f - shake.value) * 10f

    Column(
        modifier = modifier.padding(horizontal = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer { translationX = offset },
            shape = ExpressiveShapes.burst(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconLarge * 1.4f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(Space.lg))

        Text(
            text = "No $noun match “$query”",
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Space.lg))

        Button(
            onClick = onClear,
            shape = OroCorners.pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text("Clear search", style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}
