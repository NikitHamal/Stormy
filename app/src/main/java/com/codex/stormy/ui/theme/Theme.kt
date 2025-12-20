package com.codex.stormy.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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

@Composable
fun CodeXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
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
