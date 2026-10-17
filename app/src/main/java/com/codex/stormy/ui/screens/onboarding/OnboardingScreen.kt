package com.codex.stormy.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.stormy.R
import com.codex.stormy.ui.brand.OroBrand
import com.codex.stormy.ui.components.expressive.expressivePress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val PageCount = 4

/**
 * Onboarding.
 *
 * Four beats, each carried by motion rather than by an icon in a circle:
 *  1. Orobi assembles itself out of its own loader and says hello.
 *  2. A prompt turns into a page (the product, in eleven seconds).
 *  3. Providers orbit Orobi (any model, starting free).
 *  4. Pick a theme -- applied live, so the choice is felt, not imagined.
 *
 * Illustrations and copy move at different speeds while paging, so the
 * illustration reads as being further away than the text. Copy is revealed
 * line by line when a page becomes current.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory),
    initialPage: Int = 0,
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { PageCount })
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var finishing by remember { mutableStateOf(false) }
    var celebrate by remember { mutableIntStateOf(0) }
    val exit = remember { Animatable(0f) }
    val isLast = pagerState.currentPage == PageCount - 1

    fun finish(celebrateFirst: Boolean) {
        if (finishing) return
        finishing = true
        val write = viewModel.completeOnboarding()
        scope.launch {
            if (celebrateFirst) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                celebrate++
                // Long enough to see the hop leave the ground, short enough
                // that nobody waits for it.
                delay(560)
            }
            exit.animateTo(1f, tween(durationMillis = 220))
            write.join()
            onComplete()
        }
    }

    BackHandler(enabled = pagerState.currentPage > 0 && !finishing) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .graphicsLayer {
                    alpha = 1f - exit.value
                    val k = 1f - 0.04f * exit.value
                    scaleX = k
                    scaleY = k
                }
        ) {
            TopBar(
                showSkip = !isLast && !finishing,
                onSkip = { finish(celebrateFirst = false) }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
                userScrollEnabled = !finishing,
            ) { page ->
                val active = pagerState.currentPage == page
                val offset = { pageOffset(pagerState, page) }
                when (page) {
                    0 -> WelcomePage(active = active, offset = offset)
                    1 -> DescribePage(active = active, offset = offset)
                    2 -> ModelsPage(active = active, offset = offset)
                    else -> ThemePage(
                        active = active,
                        offset = offset,
                        selected = themeMode,
                        onSelect = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            viewModel.setThemeMode(it)
                        },
                        celebrate = celebrate,
                    )
                }
            }

            BottomBar(
                pagerState = pagerState,
                onNext = {
                    if (isLast) {
                        finish(celebrateFirst = true)
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                }
            )
        }
    }
}

/** Signed distance of [page] from the centre of the viewport, in pages. */
internal fun pageOffset(state: PagerState, page: Int): Float =
    (state.currentPage - page) + state.currentPageOffsetFraction

@Composable
private fun TopBar(showSkip: Boolean, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = OroBrand.AppName,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.6).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(visible = showSkip, enter = fadeIn(), exit = fadeOut()) {
            TextButton(onClick = onSkip) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(pagerState: PagerState, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepIndicator(pagerState)
        Spacer(Modifier.weight(1f))
        PrimaryButton(page = pagerState.currentPage, onClick = onNext)
    }
}

/**
 * Pills that stretch towards the page being dragged to. Follows the finger
 * continuously rather than snapping when the page changes, so the indicator
 * and the content always agree.
 */
@Composable
private fun StepIndicator(pagerState: PagerState) {
    val active = MaterialTheme.colorScheme.onSurface
    val idle = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.onboarding_step, pagerState.currentPage + 1, PageCount)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        repeat(PageCount) { index ->
            val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val focus = (1f - abs(position - index)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .height(6.dp)
                    .width(lerp(6.dp, 26.dp, focus))
                    .clip(CircleShape)
                    .background(lerp(idle, active, focus))
            )
        }
    }
}

/**
 * The one call to action. Its label changes with the step and the pill
 * resizes on a spring, so "Continue" growing into "Start building" reads as
 * the same control gaining weight rather than a new button.
 */
@Composable
private fun PrimaryButton(page: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val label = when (page) {
        0 -> stringResource(R.string.onboarding_get_started)
        PageCount - 1 -> stringResource(R.string.onboarding_finish)
        else -> stringResource(R.string.onboarding_next)
    }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onSurface,
        contentColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .expressivePress(interaction, pressedScale = 0.94f)
            .semantics { contentDescription = label },
    ) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                .padding(start = 24.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn())
                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                        .using(SizeTransform(clip = false))
                },
                label = "cta",
            ) { text ->
                Text(text = text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.width(10.dp))
            AnimatedContent(
                targetState = page == PageCount - 1,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) + fadeIn())
                        .togetherWith(scaleOut() + fadeOut())
                },
                label = "cta-icon",
            ) { last ->
                if (last) {
                    // Orobi's sparkle: the last step hands over to the assistant.
                    SparkleGlyph(Modifier.size(18.dp))
                } else {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
