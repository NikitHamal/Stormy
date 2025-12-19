package com.codex.stormy.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Brand Colors
val CodeXPrimary = Color(0xFF6366F1)
val CodeXPrimaryVariant = Color(0xFF4F46E5)
val CodeXSecondary = Color(0xFF8B5CF6)

// Light Theme Colors
val LightPrimary = Color(0xFF5B5BD6)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE0E0FF)
val LightOnPrimaryContainer = Color(0xFF17005D)
val LightSecondary = Color(0xFF7C5AC7)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFEADDFF)
val LightOnSecondaryContainer = Color(0xFF250059)
val LightTertiary = Color(0xFF006C51)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFF84F8CF)
val LightOnTertiaryContainer = Color(0xFF002117)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightBackground = Color(0xFFFCFCFF)
val LightOnBackground = Color(0xFF1B1B1F)
val LightSurface = Color(0xFFFCFCFF)
val LightOnSurface = Color(0xFF1B1B1F)
val LightSurfaceVariant = Color(0xFFE4E1EC)
val LightOnSurfaceVariant = Color(0xFF46464F)
val LightOutline = Color(0xFF777680)
val LightOutlineVariant = Color(0xFFC7C5D0)
val LightInverseSurface = Color(0xFF303034)
val LightInverseOnSurface = Color(0xFFF3EFF4)
val LightInversePrimary = Color(0xFFBFC1FF)

// Dark Theme Colors
val DarkPrimary = Color(0xFFBFC1FF)
val DarkOnPrimary = Color(0xFF2B2B93)
val DarkPrimaryContainer = Color(0xFF4343AA)
val DarkOnPrimaryContainer = Color(0xFFE0E0FF)
val DarkSecondary = Color(0xFFD0BCFF)
val DarkOnSecondary = Color(0xFF3E2470)
val DarkSecondaryContainer = Color(0xFF5540A5)
val DarkOnSecondaryContainer = Color(0xFFEADDFF)
val DarkTertiary = Color(0xFF67DBB4)
val DarkOnTertiary = Color(0xFF003829)
val DarkTertiaryContainer = Color(0xFF00513C)
val DarkOnTertiaryContainer = Color(0xFF84F8CF)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkBackground = Color(0xFF0F0F14)
val DarkOnBackground = Color(0xFFE5E1E6)
val DarkSurface = Color(0xFF0F0F14)
val DarkOnSurface = Color(0xFFE5E1E6)
val DarkSurfaceVariant = Color(0xFF46464F)
val DarkOnSurfaceVariant = Color(0xFFC7C5D0)
val DarkOutline = Color(0xFF91909A)
val DarkOutlineVariant = Color(0xFF46464F)
val DarkInverseSurface = Color(0xFFE5E1E6)
val DarkInverseOnSurface = Color(0xFF303034)
val DarkInversePrimary = Color(0xFF5B5BD6)

// Surface Container Colors - Light
val LightSurfaceContainer = Color(0xFFF1EFF4)
val LightSurfaceContainerLow = Color(0xFFF7F5FA)
val LightSurfaceContainerHigh = Color(0xFFEBE9EE)
val LightSurfaceContainerHighest = Color(0xFFE5E3E8)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFDDDBE0)
val LightSurfaceBright = Color(0xFFFCFCFF)

// Surface Container Colors - Dark
val DarkSurfaceContainer = Color(0xFF1D1D22)
val DarkSurfaceContainerLow = Color(0xFF1B1B1F)
val DarkSurfaceContainerHigh = Color(0xFF272730)
val DarkSurfaceContainerHighest = Color(0xFF32323C)
val DarkSurfaceContainerLowest = Color(0xFF0A0A0F)
val DarkSurfaceDim = Color(0xFF13131A)
val DarkSurfaceBright = Color(0xFF38383F)

// Syntax Highlighting Colors
object SyntaxColors {
    val Keyword = Color(0xFFC678DD)
    val String = Color(0xFF98C379)
    val Number = Color(0xFFD19A66)
    val Comment = Color(0xFF5C6370)
    val Function = Color(0xFF61AFEF)
    val Tag = Color(0xFFE06C75)
    val Attribute = Color(0xFFD19A66)
    val Operator = Color(0xFF56B6C2)
    val Variable = Color(0xFFE5C07B)
    val Property = Color(0xFFE06C75)
    val Punctuation = Color(0xFFABB2BF)
    val ClassName = Color(0xFFE5C07B)

    // Background colors for editor
    val EditorBackgroundLight = Color(0xFFFAFAFA)
    val EditorBackgroundDark = Color(0xFF1E1E24)
    val LineNumberLight = Color(0xFFB0B0B0)
    val LineNumberDark = Color(0xFF4A4A52)
    val CurrentLineLight = Color(0xFFF0F0F4)
    val CurrentLineDark = Color(0xFF2A2A32)
    val SelectionLight = Color(0xFFE0E4F4)
    val SelectionDark = Color(0xFF3A3A52)
}

// Accent Colors
object AccentColors {
    val Blue = Color(0xFF3B82F6)
    val Purple = Color(0xFF8B5CF6)
    val Pink = Color(0xFFEC4899)
    val Green = Color(0xFF10B981)
    val Orange = Color(0xFFF97316)
    val Red = Color(0xFFEF4444)
    val Cyan = Color(0xFF06B6D4)
    val Yellow = Color(0xFFEAB308)
}

// Chat Message Colors
object ChatColors {
    val UserBubbleLight = Color(0xFF5B5BD6)
    val UserBubbleDark = Color(0xFF5B5BD6)
    val UserTextLight = Color(0xFFFFFFFF)
    val UserTextDark = Color(0xFFFFFFFF)
    val AssistantBubbleLight = Color(0xFFF1EFF4)
    val AssistantBubbleDark = Color(0xFF1D1D22)
    val AssistantTextLight = Color(0xFF1B1B1F)
    val AssistantTextDark = Color(0xFFE5E1E6)
}

// Diff View Colors (GitHub-style)
object DiffColors {
    // Light theme
    val AddedBackgroundLight = Color(0xFFDCFFDC)
    val AddedTextLight = Color(0xFF22863A)
    val RemovedBackgroundLight = Color(0xFFFFDCDC)
    val RemovedTextLight = Color(0xFFCB2431)
    val ContextTextLight = Color(0xFF24292E)
    val HeaderBackgroundLight = Color(0xFFF1F8FF)
    val HeaderTextLight = Color(0xFF0366D6)
    val BackgroundLight = Color(0xFFF6F8FA)
    val LineNumberLight = Color(0xFFBBBBBB)

    // Dark theme
    val AddedBackgroundDark = Color(0xFF1B4332)
    val AddedTextDark = Color(0xFF7EE787)
    val RemovedBackgroundDark = Color(0xFF4C1D1D)
    val RemovedTextDark = Color(0xFFF97583)
    val ContextTextDark = Color(0xFFE6EDF3)
    val HeaderBackgroundDark = Color(0xFF161B22)
    val HeaderTextDark = Color(0xFF58A6FF)
    val BackgroundDark = Color(0xFF0D1117)
    val LineNumberDark = Color(0xFF484F58)
}
