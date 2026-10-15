package com.codex.stormy.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// CODEX MONOCHROME PALETTE
//
// Greyscale with a faintly warm cast, and no brand hue at all. The previous
// palette carried a blue primary, an emerald tertiary and slate greys with a
// cool (blue-leaning) cast, which meant three competing hues before any content
// was drawn.
//
// Two rules hold this together:
//
//  1. *Emphasis is tonal, not chromatic.* The primary is near-black in light
//     and near-white in dark, so a primary button is the highest-contrast thing
//     on screen rather than the most colourful. That is what makes a monochrome
//     UI read as deliberate instead of unfinished.
//
//  2. *Error keeps its hue.* It is the one place where colour carries meaning
//     that tone cannot: a grey "failed" state is indistinguishable from a
//     disabled one, and no amount of shape or weight fixes that. It is muted to
//     sit inside the palette rather than shout against it.
//
// The neutrals are warm (a few points more red than blue) because a pure-neutral
// greyscale reads as cold and slightly clinical on OLED, which is the opposite
// of the intent here.
// ============================================================================

// ----------------------------------------------------------------------------
// LIGHT
// ----------------------------------------------------------------------------
val LightPrimary = Color(0xFF1C1917)           // Near-black, warm
val LightOnPrimary = Color(0xFFFAFAF9)
val LightPrimaryContainer = Color(0xFFE7E5E4)  // Light warm grey
val LightOnPrimaryContainer = Color(0xFF1C1917)

val LightSecondary = Color(0xFF57534E)         // Mid warm grey
val LightOnSecondary = Color(0xFFFAFAF9)
val LightSecondaryContainer = Color(0xFFF5F5F4)
val LightOnSecondaryContainer = Color(0xFF292524)

// Tertiary is neutral too. It marks success/completion, which in this UI is
// carried by shape and by an explicit check glyph -- see ExpressiveStatusDot --
// so it does not need a hue of its own.
val LightTertiary = Color(0xFF44403C)
val LightOnTertiary = Color(0xFFFAFAF9)
val LightTertiaryContainer = Color(0xFFEDEBE9)
val LightOnTertiaryContainer = Color(0xFF1C1917)

val LightError = Color(0xFFB4342A)             // Muted brick, not a signal red
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFF7E0DE)
val LightOnErrorContainer = Color(0xFF5C1710)

val LightBackground = Color(0xFFFCFCFB)        // Warm off-white, not pure
val LightOnBackground = Color(0xFF1C1917)
val LightSurface = Color(0xFFFCFCFB)
val LightOnSurface = Color(0xFF1C1917)
val LightSurfaceVariant = Color(0xFFF0EFED)
val LightOnSurfaceVariant = Color(0xFF57534E)
val LightOutline = Color(0xFFD6D3D1)
val LightOutlineVariant = Color(0xFFE7E5E4)
val LightInverseSurface = Color(0xFF292524)
val LightInverseOnSurface = Color(0xFFF5F5F4)
val LightInversePrimary = Color(0xFFD6D3D1)

// Surface levels. The steps are deliberately small: M3 separates surfaces
// tonally rather than with shadow, and in a monochrome palette a large step
// reads as a different colour rather than a different height.
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFAFAF9)
val LightSurfaceContainer = Color(0xFFF5F5F4)
val LightSurfaceContainerHigh = Color(0xFFF0EFED)
val LightSurfaceContainerHighest = Color(0xFFE7E5E4)
val LightSurfaceDim = Color(0xFFE7E5E4)
val LightSurfaceBright = Color(0xFFFCFCFB)

// ----------------------------------------------------------------------------
// DARK
// ----------------------------------------------------------------------------
val DarkPrimary = Color(0xFFF5F5F4)            // Near-white, warm
val DarkOnPrimary = Color(0xFF1C1917)
val DarkPrimaryContainer = Color(0xFF373330)
val DarkOnPrimaryContainer = Color(0xFFF5F5F4)

val DarkSecondary = Color(0xFFA8A29E)
val DarkOnSecondary = Color(0xFF1C1917)
val DarkSecondaryContainer = Color(0xFF2C2825)
val DarkOnSecondaryContainer = Color(0xFFE7E5E4)

val DarkTertiary = Color(0xFFD6D3D1)
val DarkOnTertiary = Color(0xFF1C1917)
val DarkTertiaryContainer = Color(0xFF332F2C)
val DarkOnTertiaryContainer = Color(0xFFEDEBE9)

val DarkError = Color(0xFFE88B81)              // Lifted for contrast on black
val DarkOnError = Color(0xFF42100B)
val DarkErrorContainer = Color(0xFF5C1710)
val DarkOnErrorContainer = Color(0xFFF7E0DE)

// True black background: on OLED this is an actual power saving, and it is the
// look the monochrome direction is after. The surfaces above it carry the
// hierarchy.
val DarkBackground = Color(0xFF0C0B0A)
val DarkOnBackground = Color(0xFFF5F5F4)
val DarkSurface = Color(0xFF0C0B0A)
val DarkOnSurface = Color(0xFFF5F5F4)
val DarkSurfaceVariant = Color(0xFF2C2825)
val DarkOnSurfaceVariant = Color(0xFFBFB9B4)
val DarkOutline = Color(0xFF5C5650)
val DarkOutlineVariant = Color(0xFF373330)
val DarkInverseSurface = Color(0xFFF5F5F4)
val DarkInverseOnSurface = Color(0xFF292524)
val DarkInversePrimary = Color(0xFF44403C)

val DarkSurfaceContainerLowest = Color(0xFF070606)
val DarkSurfaceContainerLow = Color(0xFF131110)
val DarkSurfaceContainer = Color(0xFF191716)
val DarkSurfaceContainerHigh = Color(0xFF221F1D)
val DarkSurfaceContainerHighest = Color(0xFF2C2825)
val DarkSurfaceDim = Color(0xFF0C0B0A)
val DarkSurfaceBright = Color(0xFF373330)

// ============================================================================
// SYNTAX HIGHLIGHTING COLORS - One Dark Pro inspired
// ============================================================================
object SyntaxColors {
    // A fully greyscale editor is genuinely worse for reading code: token type
    // is the fastest structural cue there is, and weight alone cannot carry six
    // categories. These keep the distinctions but at roughly a third of the
    // usual saturation, so the editor reads as part of the monochrome app
    // rather than as a pasted-in One Dark window. No blue.
    val Keyword = Color(0xFF9B7FA6)            // Muted mauve
    val String = Color(0xFF7E9B76)             // Muted sage
    val Number = Color(0xFFB08968)             // Muted clay
    val Comment = Color(0xFF7D7772)            // Warm grey
    val Function = Color(0xFFB8A472)           // Muted brass
    val Tag = Color(0xFFB07F79)                // Muted rose
    val Attribute = Color(0xFFB08968)
    val Operator = Color(0xFF8A8580)
    val Variable = Color(0xFFC2B48C)
    val Property = Color(0xFFB07F79)
    val Punctuation = Color(0xFF9A948E)
    val ClassName = Color(0xFFC2B48C)
    val Regex = Color(0xFF7E9B76)
    val Constant = Color(0xFFB08968)

    // Editor backgrounds - Clean and professional
    val EditorBackgroundLight = Color(0xFFFCFCFB)
    val EditorBackgroundDark = Color(0xFF0C0B0A)
    val LineNumberLight = Color(0xFFBFB9B4)
    val LineNumberDark = Color(0xFF5C5650)
    val CurrentLineLight = Color(0xFFF5F5F4)
    val CurrentLineDark = Color(0xFF191716)
    val SelectionLight = Color(0xFFE7E5E4)
    val SelectionDark = Color(0xFF373330)

    // Search and match highlights
    val SearchHighlight = Color(0xFFFEF3C7)        // Amber-100
    val CurrentSearchHighlight = Color(0xFFFBBF24) // Amber-400
    val BracketMatch = Color(0xFF8A8580)           // Neutral; brackets are matched by position, not hue
}

// ============================================================================
// ACCENT COLORS - Semantic colors for various use cases
// ============================================================================
object AccentColors {
    // Kept as names so existing call sites compile, but every value is now a
    // neutral step. A monochrome theme with a dozen accent hues available is
    // not monochrome -- it is a monochrome base that drifts the first time
    // someone needs to distinguish two things.
    //
    // To distinguish items, use shape (ExpressiveShapes.forIndex) or tone, not
    // hue. Red remains only through the scheme's error role.
    val Ink = Color(0xFF1C1917)
    val Graphite = Color(0xFF44403C)
    val Stone = Color(0xFF78716C)
    val Ash = Color(0xFFA8A29E)
    val Mist = Color(0xFFD6D3D1)
    val Chalk = Color(0xFFF5F5F4)
}

// ============================================================================
// CHAT MESSAGE COLORS - Clean, minimal bubble design
// ============================================================================
object ChatColors {
    // The user bubble is the inverse of the surface rather than a colour: in a
    // monochrome theme the strongest available signal for "this is you" is full
    // tonal inversion, and it stays legible in both schemes without a second
    // palette.
    val UserBubbleLight = Color(0xFF1C1917)
    val UserBubbleDark = Color(0xFFF5F5F4)
    val UserTextLight = Color(0xFFFAFAF9)
    val UserTextDark = Color(0xFF1C1917)

    // Assistant text sits directly on the surface with no bubble tint, so the
    // response reads as the page rather than as a quoted block. These values
    // back the few places that still need an explicit container.
    val AssistantBubbleLight = Color(0xFFF5F5F4)
    val AssistantBubbleDark = Color(0xFF191716)
    val AssistantTextLight = Color(0xFF1C1917)
    val AssistantTextDark = Color(0xFFF5F5F4)

    val SystemBubbleLight = Color(0xFFEDEBE9)
    val SystemBubbleDark = Color(0xFF221F1D)
    val SystemTextLight = Color(0xFF57534E)
    val SystemTextDark = Color(0xFFBFB9B4)
}

// ============================================================================
// DIFF VIEW COLORS - GitHub-inspired diff styling
// ============================================================================
object DiffColors {
    // Added/removed keep a hue, heavily muted.
    //
    // This is the same exception the error role gets: a diff rendered in pure
    // greyscale is measurably harder to read, because the +/- gutter is a
    // single glyph at the start of the line while the tint covers the whole
    // row -- the eye uses the row, not the glyph. The hues are desaturated far
    // enough to sit inside the monochrome palette rather than against it, and
    // the gutter markers still carry the meaning for anyone who cannot
    // separate them.
    val AddedBackgroundLight = Color(0xFFE8EFE6)
    val AddedTextLight = Color(0xFF3A5A38)
    val RemovedBackgroundLight = Color(0xFFF5E7E5)
    val RemovedTextLight = Color(0xFF7A3A33)
    val ContextTextLight = Color(0xFF57534E)
    val HeaderBackgroundLight = Color(0xFFF0EFED)
    val HeaderTextLight = Color(0xFF44403C)
    val BackgroundLight = Color(0xFFFAFAF9)
    val LineNumberLight = Color(0xFFA8A29E)

    val AddedBackgroundDark = Color(0xFF1C2620)
    val AddedTextDark = Color(0xFF9BBF9B)
    val RemovedBackgroundDark = Color(0xFF2A1C1A)
    val RemovedTextDark = Color(0xFFD49B93)
    val ContextTextDark = Color(0xFFBFB9B4)
    val HeaderBackgroundDark = Color(0xFF221F1D)
    val HeaderTextDark = Color(0xFFD6D3D1)
    val BackgroundDark = Color(0xFF131110)
    val LineNumberDark = Color(0xFF5C5650)
}

// ============================================================================
// GIT STATUS COLORS - For file status indicators
// ============================================================================
object GitStatusColors {
    // Git status is already carried by a letter badge (M, A, D, ?), so the
    // colour was redundant -- it is now tonal weight only. Conflicting keeps
    // the error hue, because that is the one status the user must not scroll
    // past, and a conflict read as an ordinary modification costs real work.
    val Added = Color(0xFF44403C)
    val Modified = Color(0xFF78716C)
    val Deleted = Color(0xFF8A8580)
    val Renamed = Color(0xFF78716C)
    val Copied = Color(0xFF8A8580)
    val Untracked = Color(0xFFA8A29E)
    val Conflicting = Color(0xFFB4342A)
    val Ignored = Color(0xFFBFB9B4)
}

// ============================================================================
// STATUS INDICATOR COLORS - For various status states
// ============================================================================
object StatusColors {
    // Success and info are tonal. Only error and warning keep a hue, and
    // warning only barely: those are the two states a user must notice without
    // looking for them.
    val Success = Color(0xFF44403C)
    val SuccessLight = Color(0xFFD6D3D1)
    val Warning = Color(0xFF8A6A32)
    val WarningLight = Color(0xFFC8A868)
    val Error = Color(0xFFB4342A)
    val ErrorLight = Color(0xFFE88B81)
    val Info = Color(0xFF57534E)
    val InfoLight = Color(0xFFA8A29E)
    val Neutral = Color(0xFF78716C)
    val NeutralLight = Color(0xFFA8A29E)
}
