package com.codex.stormy.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// CODEX PROFESSIONAL DESIGN SYSTEM
// Modern, clean black-white-grey theme with subtle blue accents
// ============================================================================

// Primary Brand Colors - Clean blue accent for actions
val CodeXPrimary = Color(0xFF2563EB)           // Vibrant blue
val CodeXPrimaryVariant = Color(0xFF1D4ED8)    // Darker blue
val CodeXSecondary = Color(0xFF64748B)         // Slate grey

// ============================================================================
// LIGHT THEME COLORS - Clean white with grey tones
// ============================================================================
val LightPrimary = Color(0xFF2563EB)           // Blue for primary actions
val LightOnPrimary = Color(0xFFFFFFFF)         // White text on primary
val LightPrimaryContainer = Color(0xFFDBEAFE)  // Light blue container
val LightOnPrimaryContainer = Color(0xFF1E3A5F) // Dark blue text

val LightSecondary = Color(0xFF475569)         // Slate-600
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFF1F5F9) // Slate-100
val LightOnSecondaryContainer = Color(0xFF1E293B) // Slate-800

val LightTertiary = Color(0xFF059669)          // Emerald for success
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFD1FAE5) // Emerald-100
val LightOnTertiaryContainer = Color(0xFF064E3B) // Emerald-900

val LightError = Color(0xFFDC2626)             // Red-600
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFEE2E2)    // Red-100
val LightOnErrorContainer = Color(0xFF7F1D1D)  // Red-900

val LightBackground = Color(0xFFFFFFFF)        // Pure white
val LightOnBackground = Color(0xFF0F172A)      // Slate-900
val LightSurface = Color(0xFFFFFFFF)           // Pure white
val LightOnSurface = Color(0xFF0F172A)         // Slate-900
val LightSurfaceVariant = Color(0xFFF1F5F9)    // Slate-100
val LightOnSurfaceVariant = Color(0xFF475569)  // Slate-600
val LightOutline = Color(0xFFCBD5E1)           // Slate-300
val LightOutlineVariant = Color(0xFFE2E8F0)    // Slate-200
val LightInverseSurface = Color(0xFF1E293B)    // Slate-800
val LightInverseOnSurface = Color(0xFFF8FAFC)  // Slate-50
val LightInversePrimary = Color(0xFF93C5FD)    // Blue-300

// Light Surface Containers - Subtle grey hierarchy
val LightSurfaceContainer = Color(0xFFF8FAFC)      // Slate-50
val LightSurfaceContainerLow = Color(0xFFFAFAFA)   // Near white
val LightSurfaceContainerHigh = Color(0xFFF1F5F9)  // Slate-100
val LightSurfaceContainerHighest = Color(0xFFE2E8F0) // Slate-200
val LightSurfaceContainerLowest = Color(0xFFFFFFFF) // Pure white
val LightSurfaceDim = Color(0xFFE2E8F0)            // Slate-200
val LightSurfaceBright = Color(0xFFFFFFFF)         // Pure white

// ============================================================================
// DARK THEME COLORS - True dark with grey accents
// ============================================================================
val DarkPrimary = Color(0xFF60A5FA)            // Blue-400 - softer for dark
val DarkOnPrimary = Color(0xFF1E3A5F)          // Dark blue text
val DarkPrimaryContainer = Color(0xFF1E3A5F)   // Deep blue container
val DarkOnPrimaryContainer = Color(0xFFDBEAFE) // Light blue text

val DarkSecondary = Color(0xFF94A3B8)          // Slate-400
val DarkOnSecondary = Color(0xFF1E293B)        // Slate-800
val DarkSecondaryContainer = Color(0xFF334155) // Slate-700
val DarkOnSecondaryContainer = Color(0xFFE2E8F0) // Slate-200

val DarkTertiary = Color(0xFF34D399)           // Emerald-400
val DarkOnTertiary = Color(0xFF064E3B)         // Emerald-900
val DarkTertiaryContainer = Color(0xFF065F46)  // Emerald-800
val DarkOnTertiaryContainer = Color(0xFFD1FAE5) // Emerald-100

val DarkError = Color(0xFFF87171)              // Red-400
val DarkOnError = Color(0xFF7F1D1D)            // Red-900
val DarkErrorContainer = Color(0xFF991B1B)     // Red-800
val DarkOnErrorContainer = Color(0xFFFEE2E2)   // Red-100

val DarkBackground = Color(0xFF0A0A0B)         // Near black
val DarkOnBackground = Color(0xFFF8FAFC)       // Slate-50
val DarkSurface = Color(0xFF0A0A0B)            // Near black
val DarkOnSurface = Color(0xFFF8FAFC)          // Slate-50
val DarkSurfaceVariant = Color(0xFF1E293B)     // Slate-800
val DarkOnSurfaceVariant = Color(0xFFCBD5E1)   // Slate-300
val DarkOutline = Color(0xFF475569)            // Slate-600
val DarkOutlineVariant = Color(0xFF334155)     // Slate-700
val DarkInverseSurface = Color(0xFFF1F5F9)     // Slate-100
val DarkInverseOnSurface = Color(0xFF1E293B)   // Slate-800
val DarkInversePrimary = Color(0xFF2563EB)     // Blue-600

// Dark Surface Containers - Layered dark hierarchy
val DarkSurfaceContainer = Color(0xFF111113)       // Slight elevation
val DarkSurfaceContainerLow = Color(0xFF0D0D0E)    // Lower elevation
val DarkSurfaceContainerHigh = Color(0xFF1A1A1C)   // Higher elevation
val DarkSurfaceContainerHighest = Color(0xFF222224) // Highest elevation
val DarkSurfaceContainerLowest = Color(0xFF050506) // Lowest
val DarkSurfaceDim = Color(0xFF0A0A0B)             // Base
val DarkSurfaceBright = Color(0xFF2A2A2D)          // Brightest dark

// ============================================================================
// SYNTAX HIGHLIGHTING COLORS - One Dark Pro inspired
// ============================================================================
object SyntaxColors {
    val Keyword = Color(0xFFC678DD)            // Purple
    val String = Color(0xFF98C379)             // Green
    val Number = Color(0xFFD19A66)             // Orange
    val Comment = Color(0xFF5C6370)            // Grey
    val Function = Color(0xFF61AFEF)           // Blue
    val Tag = Color(0xFFE06C75)                // Red
    val Attribute = Color(0xFFD19A66)          // Orange
    val Operator = Color(0xFF56B6C2)           // Cyan
    val Variable = Color(0xFFE5C07B)           // Yellow
    val Property = Color(0xFFE06C75)           // Red
    val Punctuation = Color(0xFFABB2BF)        // Grey
    val ClassName = Color(0xFFE5C07B)          // Yellow
    val Regex = Color(0xFF56B6C2)              // Cyan
    val Constant = Color(0xFFD19A66)           // Orange

    // Editor backgrounds - Clean and professional
    val EditorBackgroundLight = Color(0xFFFFFFFF)  // Pure white
    val EditorBackgroundDark = Color(0xFF0D0D0E)   // Near black
    val LineNumberLight = Color(0xFFCBD5E1)        // Slate-300
    val LineNumberDark = Color(0xFF475569)         // Slate-600
    val CurrentLineLight = Color(0xFFF8FAFC)       // Slate-50
    val CurrentLineDark = Color(0xFF1A1A1C)        // Elevated dark
    val SelectionLight = Color(0xFFDBEAFE)         // Blue-100
    val SelectionDark = Color(0xFF1E3A5F)          // Blue dark

    // Search and match highlights
    val SearchHighlight = Color(0xFFFEF3C7)        // Amber-100
    val CurrentSearchHighlight = Color(0xFFFBBF24) // Amber-400
    val BracketMatch = Color(0xFF60A5FA)           // Blue-400
}

// ============================================================================
// ACCENT COLORS - Semantic colors for various use cases
// ============================================================================
object AccentColors {
    val Blue = Color(0xFF2563EB)               // Primary blue
    val BlueLight = Color(0xFF60A5FA)          // Lighter blue
    val Purple = Color(0xFF8B5CF6)             // Purple
    val Pink = Color(0xFFEC4899)               // Pink
    val Green = Color(0xFF059669)              // Emerald
    val GreenLight = Color(0xFF34D399)         // Emerald light
    val Orange = Color(0xFFF97316)             // Orange
    val Red = Color(0xFFDC2626)                // Red
    val RedLight = Color(0xFFF87171)           // Red light
    val Cyan = Color(0xFF06B6D4)               // Cyan
    val Yellow = Color(0xFFEAB308)             // Yellow
    val Slate = Color(0xFF64748B)              // Slate
}

// ============================================================================
// CHAT MESSAGE COLORS - Clean, minimal bubble design
// ============================================================================
object ChatColors {
    // Light theme - subtle distinction
    val UserBubbleLight = Color(0xFF2563EB)        // Blue
    val UserBubbleDark = Color(0xFF1D4ED8)         // Darker blue
    val UserTextLight = Color(0xFFFFFFFF)
    val UserTextDark = Color(0xFFFFFFFF)

    // Assistant - clean grey
    val AssistantBubbleLight = Color(0xFFF1F5F9)   // Slate-100
    val AssistantBubbleDark = Color(0xFF1A1A1C)    // Dark elevated
    val AssistantTextLight = Color(0xFF0F172A)     // Slate-900
    val AssistantTextDark = Color(0xFFF8FAFC)      // Slate-50

    // System messages
    val SystemBubbleLight = Color(0xFFFEF3C7)      // Amber-100
    val SystemBubbleDark = Color(0xFF422006)       // Amber-900
    val SystemTextLight = Color(0xFF92400E)        // Amber-800
    val SystemTextDark = Color(0xFFFDE68A)         // Amber-200
}

// ============================================================================
// DIFF VIEW COLORS - GitHub-inspired diff styling
// ============================================================================
object DiffColors {
    // Light theme - classic diff colors
    val AddedBackgroundLight = Color(0xFFDCFCE7)   // Green-100
    val AddedTextLight = Color(0xFF166534)         // Green-800
    val RemovedBackgroundLight = Color(0xFFFEE2E2) // Red-100
    val RemovedTextLight = Color(0xFFDC2626)       // Red-600
    val ContextTextLight = Color(0xFF374151)       // Grey-700
    val HeaderBackgroundLight = Color(0xFFF3F4F6)  // Grey-100
    val HeaderTextLight = Color(0xFF2563EB)        // Blue-600
    val BackgroundLight = Color(0xFFFAFAFA)        // Grey-50
    val LineNumberLight = Color(0xFF9CA3AF)        // Grey-400

    // Dark theme - muted but visible
    val AddedBackgroundDark = Color(0xFF14532D)    // Green-900
    val AddedTextDark = Color(0xFF86EFAC)          // Green-300
    val RemovedBackgroundDark = Color(0xFF7F1D1D)  // Red-900
    val RemovedTextDark = Color(0xFFFCA5A5)        // Red-300
    val ContextTextDark = Color(0xFFD1D5DB)        // Grey-300
    val HeaderBackgroundDark = Color(0xFF1F2937)   // Grey-800
    val HeaderTextDark = Color(0xFF60A5FA)         // Blue-400
    val BackgroundDark = Color(0xFF111827)         // Grey-900
    val LineNumberDark = Color(0xFF6B7280)         // Grey-500
}

// ============================================================================
// GIT STATUS COLORS - For file status indicators
// ============================================================================
object GitStatusColors {
    val Added = Color(0xFF059669)              // Emerald-600
    val Modified = Color(0xFFF59E0B)           // Amber-500
    val Deleted = Color(0xFFDC2626)            // Red-600
    val Renamed = Color(0xFF8B5CF6)            // Violet-500
    val Copied = Color(0xFF06B6D4)             // Cyan-500
    val Untracked = Color(0xFF64748B)          // Slate-500
    val Conflicting = Color(0xFFEF4444)        // Red-500
    val Ignored = Color(0xFF9CA3AF)            // Grey-400
}

// ============================================================================
// STATUS INDICATOR COLORS - For various status states
// ============================================================================
object StatusColors {
    val Success = Color(0xFF059669)            // Emerald-600
    val SuccessLight = Color(0xFF34D399)       // Emerald-400
    val Warning = Color(0xFFF59E0B)            // Amber-500
    val WarningLight = Color(0xFFFBBF24)       // Amber-400
    val Error = Color(0xFFDC2626)              // Red-600
    val ErrorLight = Color(0xFFF87171)         // Red-400
    val Info = Color(0xFF2563EB)               // Blue-600
    val InfoLight = Color(0xFF60A5FA)          // Blue-400
    val Neutral = Color(0xFF64748B)            // Slate-500
    val NeutralLight = Color(0xFF94A3B8)       // Slate-400
}
