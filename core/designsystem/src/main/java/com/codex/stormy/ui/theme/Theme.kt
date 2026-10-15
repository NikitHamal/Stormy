package com.codex.stormy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright
)

@Immutable
data class ExtendedColors(
    val chatUserBubble: Color,
    val chatUserText: Color,
    val chatAssistantBubble: Color,
    val chatAssistantText: Color,
    val editorBackground: Color,
    val lineNumber: Color,
    val currentLine: Color,
    val selection: Color,
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxNumber: Color,
    val syntaxComment: Color,
    val syntaxFunction: Color,
    val syntaxTag: Color,
    val syntaxAttribute: Color,
    val syntaxOperator: Color,
    // Diff view colors
    val diffAddedBackground: Color,
    val diffAddedText: Color,
    val diffRemovedBackground: Color,
    val diffRemovedText: Color,
    val diffContextText: Color,
    val diffHeaderBackground: Color,
    val diffHeaderText: Color,
    val diffBackground: Color,
    val diffLineNumber: Color
)

val LightExtendedColors = ExtendedColors(
    chatUserBubble = ChatColors.UserBubbleLight,
    chatUserText = ChatColors.UserTextLight,
    chatAssistantBubble = ChatColors.AssistantBubbleLight,
    chatAssistantText = ChatColors.AssistantTextLight,
    editorBackground = SyntaxColors.EditorBackgroundLight,
    lineNumber = SyntaxColors.LineNumberLight,
    currentLine = SyntaxColors.CurrentLineLight,
    selection = SyntaxColors.SelectionLight,
    syntaxKeyword = SyntaxColors.Keyword,
    syntaxString = SyntaxColors.String,
    syntaxNumber = SyntaxColors.Number,
    syntaxComment = SyntaxColors.Comment,
    syntaxFunction = SyntaxColors.Function,
    syntaxTag = SyntaxColors.Tag,
    syntaxAttribute = SyntaxColors.Attribute,
    syntaxOperator = SyntaxColors.Operator,
    diffAddedBackground = DiffColors.AddedBackgroundLight,
    diffAddedText = DiffColors.AddedTextLight,
    diffRemovedBackground = DiffColors.RemovedBackgroundLight,
    diffRemovedText = DiffColors.RemovedTextLight,
    diffContextText = DiffColors.ContextTextLight,
    diffHeaderBackground = DiffColors.HeaderBackgroundLight,
    diffHeaderText = DiffColors.HeaderTextLight,
    diffBackground = DiffColors.BackgroundLight,
    diffLineNumber = DiffColors.LineNumberLight
)

val DarkExtendedColors = ExtendedColors(
    chatUserBubble = ChatColors.UserBubbleDark,
    chatUserText = ChatColors.UserTextDark,
    chatAssistantBubble = ChatColors.AssistantBubbleDark,
    chatAssistantText = ChatColors.AssistantTextDark,
    editorBackground = SyntaxColors.EditorBackgroundDark,
    lineNumber = SyntaxColors.LineNumberDark,
    currentLine = SyntaxColors.CurrentLineDark,
    selection = SyntaxColors.SelectionDark,
    syntaxKeyword = SyntaxColors.Keyword,
    syntaxString = SyntaxColors.String,
    syntaxNumber = SyntaxColors.Number,
    syntaxComment = SyntaxColors.Comment,
    syntaxFunction = SyntaxColors.Function,
    syntaxTag = SyntaxColors.Tag,
    syntaxAttribute = SyntaxColors.Attribute,
    syntaxOperator = SyntaxColors.Operator,
    diffAddedBackground = DiffColors.AddedBackgroundDark,
    diffAddedText = DiffColors.AddedTextDark,
    diffRemovedBackground = DiffColors.RemovedBackgroundDark,
    diffRemovedText = DiffColors.RemovedTextDark,
    diffContextText = DiffColors.ContextTextDark,
    diffHeaderBackground = DiffColors.HeaderBackgroundDark,
    diffHeaderText = DiffColors.HeaderTextDark,
    diffBackground = DiffColors.BackgroundDark,
    diffLineNumber = DiffColors.LineNumberDark
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * Whether components animate with the Expressive motion scheme or the standard
 * one. Expressive uses springier, slightly overshooting spatial specs; standard
 * is the calmer M3 baseline. Exposed as a setting because the springy feel is
 * polarising and because reduced-motion users need a way out.
 */
enum class MotionStyle { Expressive, Standard }

@Composable
fun CodeXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),

    motionStyle: MotionStyle = MotionStyle.Expressive,
    content: @Composable () -> Unit
) {
    // Dynamic colour is deliberately not offered.
    //
    // Material You derives a scheme from the wallpaper, which means an
    // arbitrary hue is injected over every surface -- and this app's whole
    // visual direction is monochrome, with emphasis carried tonally and the one
    // remaining hue reserved for error. With dynamic colour on, a blue
    // wallpaper turned the primary blue and silently undid that, which is
    // exactly what happened in practice. A single intentional palette beats a
    // theming switch that fights the design.
    val colorScheme: ColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    // System bar handling lives in the Activity via enableEdgeToEdge(). Setting
    // window.statusBarColor here was both deprecated (no-op from API 35) and the
    // wrong layer -- it fired a SideEffect on every theme recomposition.

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            motionScheme = when (motionStyle) {
                MotionStyle.Expressive -> MotionScheme.expressive()
                MotionStyle.Standard -> MotionScheme.standard()
            },
            shapes = CodeXShapes,
            typography = CodeXTypography,
            content = content
        )
    }
}

object CodeXTheme {
    val extendedColors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
