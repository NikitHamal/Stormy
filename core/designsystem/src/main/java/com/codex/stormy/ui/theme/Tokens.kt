package com.codex.stormy.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout scale.
 *
 * One 4dp-based scale for the whole app. The screens previously used ad-hoc
 * values -- 6, 10, 14, 18dp all appeared -- and the result is that nothing
 * lines up across screens even though each screen looks deliberate on its own.
 * Optical rhythm comes from *repetition*, so the scale is intentionally coarse:
 * if a value is not on it, the layout usually wants a different structure
 * rather than a new number.
 */
@Immutable
object Space {
    /** Hairline gaps: icon-to-label inside a dense chip. */
    val xxs: Dp = 2.dp

    /** Inside a compact control. */
    val xs: Dp = 4.dp

    /** Between tightly related items: icon and its label. */
    val sm: Dp = 8.dp

    /** The default gap between sibling elements. */
    val md: Dp = 12.dp

    /** Screen gutters and card padding. */
    val lg: Dp = 16.dp

    /** Between groups inside a screen. */
    val xl: Dp = 24.dp

    /** Between major sections. */
    val xxl: Dp = 32.dp

    /** Breathing room above an empty-state illustration. */
    val huge: Dp = 48.dp

    /** Horizontal screen gutter, applied consistently by every screen. */
    val gutter: Dp = lg

    val screen: PaddingValues = PaddingValues(horizontal = lg, vertical = md)
    val card: PaddingValues = PaddingValues(all = lg)
    val listRow: PaddingValues = PaddingValues(horizontal = lg, vertical = md)
    val compactRow: PaddingValues = PaddingValues(horizontal = md, vertical = sm)
}

/**
 * Elevation.
 *
 * Material 3 carries depth with tonal surface colour rather than shadow, so
 * these are deliberately small -- they exist for the few places that need a
 * real cast shadow (a floating toolbar over scrolling content, a dragged item)
 * and nothing else. A card that needs to separate from its background should
 * change `surfaceContainer` level instead of casting a shadow; piling shadows
 * on flat surfaces is the single most common way an M3 app ends up looking
 * like Material 1.
 */
@Immutable
object Elevation {
    val flat: Dp = 0.dp

    /** Resting state of something that can float. */
    val raised: Dp = 1.dp

    /** Floating toolbars and FABs. */
    val floating: Dp = 3.dp

    /** Actively dragged or pressed-and-lifted. */
    val lifted: Dp = 6.dp

    /** Modal sheets and menus. */
    val modal: Dp = 8.dp
}

/** Fixed sizes that recur across the chrome. */
@Immutable
object Sizes {
    /** Minimum touch target. Non-negotiable: below this, taps miss. */
    val minTouchTarget: Dp = 48.dp

    val iconSmall: Dp = 16.dp
    val icon: Dp = 20.dp
    val iconLarge: Dp = 24.dp

    val avatarSmall: Dp = 24.dp
    val avatar: Dp = 32.dp
    val avatarLarge: Dp = 40.dp

    /** Height of the editor's segmented tab bar. */
    val tabBar: Dp = 44.dp

    /** Hairline divider. Uses dp rather than 1px so it scales with density. */
    val hairline: Dp = 1.dp
}
