package com.codex.stormy.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// ORO MONOCHROME PALETTE
//
// Greyscale with a faint cool (ink-blue) cast, and no brand hue in the chrome.
// The brand's colour lives in Orobi -- peach, magenta, ink, see OroBrand -- and
// the interface stays out of its way, so the mascot is always the most
// colourful thing on screen.
//
// Two rules hold this together:
//
//  1. *Emphasis is tonal, not chromatic.* The primary is near-black in light
//     and near-white in dark, so a primary button is the highest-contrast thing
//     on screen rather than the most colourful.
//
//  2. *Error keeps its hue.* It is the one place where colour carries meaning
//     that tone cannot: a grey "failed" state is indistinguishable from a
//     disabled one.
//
// The neutrals lean towards Orobi's ink (#1A1F4A) rather than to pure grey, so
// the mascot's eyes and the UI's darkest tones read as the same family.
// ============================================================================

// ----------------------------------------------------------------------------
// LIGHT
// ----------------------------------------------------------------------------
val LightPrimary = Color(0xFF12151C)           // Near-black, ink cast
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE1E4E9)
val LightOnPrimaryContainer = Color(0xFF0E1117)

val LightSecondary = Color(0xFF4E5666)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFEDEFF2)
val LightOnSecondaryContainer = Color(0xFF181C25)

// Tertiary is neutral too. Success is carried by shape and an explicit check
// glyph -- see ExpressiveStatusDot -- so it does not need a hue of its own.
val LightTertiary = Color(0xFF3A4150)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFE6E9EE)
val LightOnTertiaryContainer = Color(0xFF12151C)

val LightError = Color(0xFFD93F3F)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFBE3E3)
val LightOnErrorContainer = Color(0xFF5C1414)

val LightBackground = Color(0xFFF7F8FA)        // Cool off-white, not pure
val LightOnBackground = Color(0xFF0E1117)
val LightSurface = Color(0xFFF7F8FA)
val LightOnSurface = Color(0xFF0E1117)
val LightSurfaceVariant = Color(0xFFEDEFF2)
val LightOnSurfaceVariant = Color(0xFF5A6273) // 6.0:1 on surface
// Measured, not eyeballed: outline clears the 3:1 WCAG asks of non-text UI,
// outline variant is a visible hairline.
val LightOutline = Color(0xFF7C8494)        // 3.6:1 on surface
val LightOutlineVariant = Color(0xFFD3D7DE) // hairline
val LightInverseSurface = Color(0xFF181C25)
val LightInverseOnSurface = Color(0xFFEDEFF2)
val LightInversePrimary = Color(0xFFD9DDE4)

// Surface levels, spaced to roughly 1.1 / 1.2 / 1.3 / 1.5 against the page,
// which separates the levels without turning the surface grey.
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF0F2F5)
val LightSurfaceContainer = Color(0xFFE9ECF0)
val LightSurfaceContainerHigh = Color(0xFFE1E4E9)
val LightSurfaceContainerHighest = Color(0xFFD6DAE1)
val LightSurfaceDim = Color(0xFFE1E4E9)
val LightSurfaceBright = Color(0xFFFFFFFF)

// ----------------------------------------------------------------------------
// DARK
// ----------------------------------------------------------------------------
val DarkPrimary = Color(0xFFF2F4F7)            // Near-white, cool
val DarkOnPrimary = Color(0xFF0B0D12)
val DarkPrimaryContainer = Color(0xFF2A303C)
val DarkOnPrimaryContainer = Color(0xFFE8ECF2)

val DarkSecondary = Color(0xFF9AA3B2)
val DarkOnSecondary = Color(0xFF0B0D12)
val DarkSecondaryContainer = Color(0xFF222834)
val DarkOnSecondaryContainer = Color(0xFFE1E5EC)

val DarkTertiary = Color(0xFFCDD3DD)
val DarkOnTertiary = Color(0xFF0B0D12)
val DarkTertiaryContainer = Color(0xFF262C38)
val DarkOnTertiaryContainer = Color(0xFFE8ECF2)

val DarkError = Color(0xFFFF6B6B)
val DarkOnError = Color(0xFF3D0B0B)
val DarkErrorContainer = Color(0xFF5C1717)
val DarkOnErrorContainer = Color(0xFFFFDADA)

// Near-black with an ink cast. Not pure black: pure black against the
// mascot's peach is harsh, and the surfaces above it carry the hierarchy.
val DarkBackground = Color(0xFF0B0D12)
val DarkOnBackground = Color(0xFFE8ECF2)
val DarkSurface = Color(0xFF0B0D12)
val DarkOnSurface = Color(0xFFE8ECF2)
val DarkSurfaceVariant = Color(0xFF222834)
val DarkOnSurfaceVariant = Color(0xFF9AA3B2)   // 7.4:1 on surface
val DarkOutline = Color(0xFF6B7486)            // 4.0:1 on surface
val DarkOutlineVariant = Color(0xFF2A303C)     // hairline
val DarkInverseSurface = Color(0xFFE8ECF2)
val DarkInverseOnSurface = Color(0xFF181C25)
val DarkInversePrimary = Color(0xFF3A4150)

val DarkSurfaceContainerLowest = Color(0xFF06080B)
val DarkSurfaceContainerLow = Color(0xFF12151C)
val DarkSurfaceContainer = Color(0xFF181C25)
val DarkSurfaceContainerHigh = Color(0xFF222834)
val DarkSurfaceContainerHighest = Color(0xFF2B3240)
val DarkSurfaceDim = Color(0xFF0B0D12)
val DarkSurfaceBright = Color(0xFF353C4A)

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
    val EditorBackgroundLight = Color(0xFFF7F8FA)
    val EditorBackgroundDark = Color(0xFF0B0D12)
    val LineNumberLight = Color(0xFFBFB9B4)
    val LineNumberDark = Color(0xFF5C5650)
    val CurrentLineLight = Color(0xFFEDEFF2)
    val CurrentLineDark = Color(0xFF151922)
    val SelectionLight = Color(0xFFDDE1E8)
    val SelectionDark = Color(0xFF2A303C)

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
    val UserBubbleLight = Color(0xFF12151C)
    val UserBubbleDark = Color(0xFFF2F4F7)
    val UserTextLight = Color(0xFFFFFFFF)
    val UserTextDark = Color(0xFF0B0D12)

    // Assistant text sits directly on the surface with no bubble tint, so the
    // response reads as the page rather than as a quoted block. These values
    // back the few places that still need an explicit container.
    val AssistantBubbleLight = Color(0xFFEDEFF2)
    val AssistantBubbleDark = Color(0xFF12151C)
    val AssistantTextLight = Color(0xFF0E1117)
    val AssistantTextDark = Color(0xFFE8ECF2)

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
