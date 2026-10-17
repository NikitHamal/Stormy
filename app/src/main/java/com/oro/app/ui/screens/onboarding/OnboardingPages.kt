package com.oro.app.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oro.app.R
import com.oro.app.data.repository.ThemeMode
import com.oro.app.ui.brand.OroBrand
import com.oro.app.ui.brand.OrobiAspectRatio
import com.oro.app.ui.brand.OrobiColors
import com.oro.app.ui.brand.OrobiMotion
import com.oro.app.ui.brand.drawOrobi
import com.oro.app.ui.brand.drawSparkle
import com.oro.app.ui.brand.orobiColors
import com.oro.app.ui.brand.rememberAnimationClock
import com.oro.app.ui.brand.rememberReducedMotion
import com.oro.app.ui.brand.OroSparkle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// Shared page structure
// ============================================================================

/**
 * Illustration on top, copy underneath, both pinned to the same gutters.
 *
 * While paging, the illustration travels at half the speed of the page and
 * the copy at ~85%, so the three planes (page, copy, illustration) separate
 * in depth. Alpha falls off only on the illustration: copy that fades while
 * the user is mid-swipe is copy they cannot read.
 */
@Composable
private fun PageLayout(
    active: Boolean,
    offset: () -> Float,
    title: String,
    body: String,
    illustration: @Composable () -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    // Clipped: the parallax pulls a neighbouring page's illustration back
    // towards the centre, and unclipped it would show through this one.
    Column(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    val o = offset()
                    translationX = o * size.width * 0.5f
                    alpha = 1f - abs(o).coerceIn(0f, 1f)
                    val k = 1f - 0.06f * abs(o).coerceIn(0f, 1f)
                    scaleX = k
                    scaleY = k
                },
            contentAlignment = Alignment.Center,
        ) {
            illustration()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp)
                .graphicsLayer { translationX = offset() * size.width * 0.15f },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    letterSpacing = (-1.1).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.reveal(active, order = 0),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp, letterSpacing = 0.1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .reveal(active, order = 1),
            )
            footer()
        }
    }
}

/**
 * Rises into place the first time [active] becomes true, [order] steps after
 * its siblings. Plays once: content the user has already read does not
 * perform again when they swipe back to it.
 */
@Composable
internal fun Modifier.reveal(active: Boolean, order: Int): Modifier {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(active) {
        if (active && progress.targetValue < 1f) {
            delay(90L + order * 70L)
            progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 260f))
        }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 22.dp.toPx()
    }
}

// ============================================================================
// 1 -- Welcome: Orobi builds itself, says hi, and plays along
// ============================================================================

@Composable
internal fun WelcomePage(active: Boolean, offset: () -> Float) {
    PageLayout(
        active = active,
        offset = offset,
        title = stringResource(R.string.onboarding_title_1),
        body = stringResource(R.string.onboarding_desc_1),
        illustration = { WelcomeIllustration(active) },
    )
}

/**
 * The entrance: the infinity loader runs once, pulls itself into Orobi, lands
 * with a squash and blinks awake, then settles into idle. After that the
 * eyes follow the user's finger, and a tap makes it hop.
 */
@Composable
private fun WelcomeIllustration(active: Boolean) {
    val reduced = rememberReducedMotion()
    val clock = rememberAnimationClock(running = active && !reduced)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val colors = orobiColors()
    val dot = MaterialTheme.colorScheme.onSurface
    val scratch = remember { Path() }
    val sparkle = remember { Path() }

    // Gaze, in artwork units, and how much it overrides the idle glance.
    val gaze = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val gazeWeight = remember { Animatable(0f) }
    var hopAt by remember { mutableFloatStateOf(-10f) }
    val hint = stringResource(R.string.onboarding_mascot_hint)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .aspectRatio(1.18f)
                .semantics { contentDescription = hint }
                .pointerInput(reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val mascot = mascotRect(size.width.toFloat(), size.height.toFloat())
                        fun look(at: Offset) {
                            val eyes = Offset(mascot.center.x, mascot.top + mascot.height * 0.3f)
                            val dx = (at.x - eyes.x) / mascot.width
                            val dy = (at.y - eyes.y) / mascot.height
                            val target = Offset((dx * 70f).coerceIn(-26f, 26f), (dy * 40f).coerceIn(-12f, 10f))
                            scope.launch { gaze.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 420f)) }
                        }
                        look(down.position)
                        scope.launch { gazeWeight.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                        var last = down.position
                        var travelled = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            travelled += (change.position - last).getDistance()
                            last = change.position
                            look(last)
                        }
                        scope.launch {
                            gazeWeight.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow))
                        }
                        val now = clock.value
                        val isTap = travelled < viewConfiguration.touchSlop
                        if (isTap && mascot.inflate(mascot.width * 0.1f).contains(down.position) &&
                            now - hopAt > OrobiMotion.HopDuration * 0.8f
                        ) {
                            hopAt = now
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }
                    }
                }
                .drawBehind {
                    val t = if (reduced) OrobiMotion.EntranceDuration + 1f else clock.value
                    val mascot = mascotRect(size.width, size.height)

                    drawDotHalo(mascot.center, size.minDimension * 0.62f, dot)

                    // Entrance, then idle. The idle clock starts from zero at the
                    // hand-off so it begins on the rest pose the entrance ends on.
                    val base = if (t < OrobiMotion.EntranceDuration) {
                        OrobiMotion.entrance(t)
                    } else {
                        OrobiMotion.idle(t - OrobiMotion.EntranceDuration)
                    }
                    val w = gazeWeight.value
                    val looked = base.copy(
                        lookX = base.lookX * (1f - w) + gaze.value.x * w,
                        lookY = base.lookY * (1f - w) + gaze.value.y * w,
                    )
                    val pose = OrobiMotion.hop(looked, t - hopAt)

                    // The loader pops in before it starts to run.
                    val appear = OrobiMotion.backOut(OrobiMotion.ramp(t, 0.05f, 0.5f))
                    val appearAlpha = OrobiMotion.easeOut(OrobiMotion.ramp(t, 0.05f, 0.35f))
                    val k = 0.7f + 0.3f * appear
                    scale(k, k, pivot = mascot.center) {
                        drawOrobi(pose, colors, scratch, bounds = mascot, alpha = appearAlpha)
                    }

                    // Sparkles burst on a hop, and once as Orobi lands from the entrance.
                    val burst = stronger(
                        sparkleBurst(t - hopAt - 0.45f),
                        sparkleBurst(t - 1.55f),
                    )
                    if (burst.first > 0f) {
                        val r = mascot.width
                        val c = mascot.center
                        drawSparkle(Offset(c.x - r * 0.52f, c.y - r * 0.18f), r * 0.07f, OroBrand.Magenta, sparkle, burst.first, burst.second)
                        drawSparkle(Offset(c.x + r * 0.5f, c.y - r * 0.26f), r * 0.055f, OroBrand.Apricot, sparkle, burst.first, burst.second)
                        drawSparkle(Offset(c.x + r * 0.44f, c.y + r * 0.2f), r * 0.04f, OroBrand.Magenta, sparkle, burst.first, burst.second)
                        drawSparkle(Offset(c.x - r * 0.42f, c.y + r * 0.24f), r * 0.035f, OroBrand.Apricot, sparkle, burst.first, burst.second)
                    }
                }
        ) {}

        GreetingPill(active = active, reduced = reduced)
    }
}

/** Mascot bounds inside the welcome illustration: centred, slightly high. */
private fun mascotRect(width: Float, height: Float): Rect {
    val w = width * 0.7f
    val h = w / OrobiAspectRatio
    val left = (width - w) / 2f
    val top = height * 0.46f - h / 2f
    return Rect(left, top, left + w, top + h)
}

/** (scale, rotation) for a sparkle burst [t] seconds in. */
private fun sparkleBurst(t: Float): Pair<Float, Float> {
    if (t < 0f || t > 0.6f) return 0f to 0f
    val p = t / 0.6f
    return sin(p * PI.toFloat()) to p * 90f
}

private fun stronger(a: Pair<Float, Float>, b: Pair<Float, Float>) = if (a.first >= b.first) a else b

/**
 * The dot field from the hero, but fading out radially from Orobi, so the
 * texture frames the mascot instead of filling a rectangle.
 */
private fun DrawScope.drawDotHalo(center: Offset, radius: Float, color: Color) {
    val step = 18.dp.toPx()
    val dotRadius = 1.dp.toPx()
    var y = center.y - radius
    // Aligned to a grid anchored on the centre, so the pattern is symmetric.
    while (y <= center.y + radius) {
        var x = center.x - radius
        while (x <= center.x + radius) {
            val d = sqrt((x - center.x) * (x - center.x) + (y - center.y) * (y - center.y)) / radius
            if (d < 1f) {
                val a = (1f - d) * (1f - d) * 0.28f
                drawCircle(color, dotRadius, Offset(x, y), alpha = a)
            }
            x += step
        }
        y += step
    }
}

/**
 * "Hi, I'm Orobi", typed out once Orobi has blinked awake, with the same
 * caret the prompt pill uses in the next step.
 */
@Composable
private fun GreetingPill(active: Boolean, reduced: Boolean) {
    val text = stringResource(R.string.onboarding_greeting)
    var shown by remember { mutableIntStateOf(if (reduced) text.length else 0) }
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    var typing by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (!active || shown == text.length) return@LaunchedEffect
        delay(2050)
        launch { appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 380f)) }
        delay(160)
        typing = true
        while (shown < text.length) {
            shown++
            delay(if (text[shown - 1] == ',') 140 else 42)
        }
        delay(500)
        typing = false
    }

    val caretAlpha by animateFloatAsState(if (typing) 1f else 0f, label = "caret")

    Row(
        modifier = Modifier
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 10.dp.toPx()
                val k = 0.92f + 0.08f * appear.value
                scaleX = k
                scaleY = k
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(start = 14.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OroSparkle(Modifier.size(14.dp), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(10.dp))
        Box {
            // Full text, invisible, holds the width so the pill never resizes
            // while typing.
            Text(text, style = MaterialTheme.typography.labelLarge, color = Color.Transparent)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text.take(shown),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(
                    Modifier
                        .padding(start = 2.dp)
                        .size(width = 2.dp, height = 16.dp)
                        .graphicsLayer { alpha = caretAlpha }
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

// ============================================================================
// 2 -- Describe it: the prompt-to-page hero
// ============================================================================

@Composable
internal fun DescribePage(active: Boolean, offset: () -> Float) {
    PageLayout(
        active = active,
        offset = offset,
        title = stringResource(R.string.onboarding_title_2),
        body = stringResource(R.string.onboarding_desc_2),
        illustration = {
            PromptToPageHero(
                playing = active,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)),
            )
        },
    )
}

// ============================================================================
// 3 -- Any model: the provider orbit
// ============================================================================

@Composable
internal fun ModelsPage(active: Boolean, offset: () -> Float) {
    PageLayout(
        active = active,
        offset = offset,
        title = stringResource(R.string.onboarding_title_3),
        body = stringResource(R.string.onboarding_desc_3),
        illustration = {
            ModelOrbit(
                playing = active,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .widthIn(max = 360.dp),
            )
        },
    )
}

// ============================================================================
// 4 -- Make it yours: theme, applied live
// ============================================================================

private data class ThemeChoice(val mode: ThemeMode, val label: Int)

private val ThemeChoices = listOf(
    ThemeChoice(ThemeMode.SYSTEM, R.string.onboarding_theme_system),
    ThemeChoice(ThemeMode.LIGHT, R.string.onboarding_theme_light),
    ThemeChoice(ThemeMode.DARK, R.string.onboarding_theme_dark),
)

private val TileLight = Color(0xFFFFFFFF)
private val TileDark = Color(0xFF05070A)

@Composable
internal fun ThemePage(
    active: Boolean,
    offset: () -> Float,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    celebrate: Int,
) {
    PageLayout(
        active = active,
        offset = offset,
        title = stringResource(R.string.onboarding_title_4),
        body = stringResource(R.string.onboarding_desc_4),
        illustration = {
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 480.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ThemeChoices.forEachIndexed { index, choice ->
                    ThemeTile(
                        choice = choice,
                        selected = selected == choice.mode,
                        playing = active,
                        celebrate = celebrate,
                        onClick = { onSelect(choice.mode) },
                        modifier = Modifier
                            .weight(1f)
                            .reveal(active, order = index),
                    )
                }
            }
        },
    )
}

@Composable
private fun ThemeTile(
    choice: ThemeChoice,
    selected: Boolean,
    playing: Boolean,
    celebrate: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val clock = rememberAnimationClock(running = playing && !reduced)
    var hopAt by remember { mutableFloatStateOf(-10f) }
    val scratch = remember { Path() }

    // Hop when chosen, and again when the user finishes onboarding with it.
    LaunchedEffect(selected, playing) {
        if (selected && playing) hopAt = clock.value
    }
    LaunchedEffect(celebrate) {
        if (celebrate > 0 && selected) hopAt = clock.value
    }

    val ring by animateDpAsState(
        if (selected) 2.dp else 1.dp,
        spring(stiffness = Spring.StiffnessMedium),
        label = "ring",
    )
    val ringColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
    val check by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "check",
    )
    val shape = RoundedCornerShape(20.dp)
    val label = stringResource(choice.label)

    Column(
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(shape)
                .drawBehind {
                    when (choice.mode) {
                        ThemeMode.LIGHT -> drawRect(TileLight)
                        ThemeMode.DARK -> drawRect(TileDark)
                        ThemeMode.SYSTEM -> {
                            // Day into night on a diagonal.
                            drawRect(
                                Brush.linearGradient(
                                    0.499f to TileLight, 0.501f to TileDark,
                                    start = Offset(0f, 0f), end = Offset(size.width, size.height),
                                )
                            )
                        }
                    }
                    val w = size.width * 0.62f
                    val h = w / OrobiAspectRatio
                    val bounds = Rect(
                        (size.width - w) / 2f, size.height * 0.56f - h / 2f,
                        (size.width + w) / 2f, size.height * 0.56f + h / 2f,
                    )
                    val t = clock.value
                    val shadow = if (choice.mode == ThemeMode.LIGHT) Color.Black else Color.White
                    drawOrobi(
                        OrobiMotion.hop(OrobiMotion.idle(t + choice.mode.ordinal * 0.9f, look = false), t - hopAt),
                        OrobiColors(shadow = shadow),
                        scratch,
                        bounds = bounds,
                    )
                }
                .border(ring, ringColor, shape),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = check
                        scaleY = check
                        alpha = check.coerceIn(0f, 1f)
                    }
                    .background(OroBrand.Magenta, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
