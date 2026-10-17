package com.oro.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.oro.app.designsystem.R

val PoppinsFontFamily = FontFamily(
    Font(R.font.poppins_regular_static, FontWeight.Normal),
    Font(R.font.poppins_medium_static, FontWeight.Medium),
    Font(R.font.poppins_semibold_static, FontWeight.SemiBold),
    Font(R.font.poppins_bold_static, FontWeight.Bold)
)

/**
 * Trim the extra leading Compose adds above the first line and below the last
 * line of a text block. Without this, dense UI (chat rows, list items, chips)
 * reads as vertically lopsided because the baseline sits low in its box.
 */
private val TrimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun poppins(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: Double = 0.0
) = TextStyle(
    fontFamily = PoppinsFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = TrimmedLineHeight
)

/**
 * Material 3 Expressive type scale.
 *
 * Expressive adds an *emphasized* variant for all fifteen roles. They are not
 * simply "bold" versions: emphasis steps the weight up one notch and tightens
 * tracking slightly, so a style can be stressed without changing its size and
 * therefore without reflowing the layout around it. That is what makes them
 * usable for state changes -- an active tab or a running task can switch to the
 * emphasized style in place.
 */
val OroTypography = Typography(
    // --- Display -----------------------------------------------------------
    displayLarge = poppins(FontWeight.Normal, 57, 64, -0.25),
    displayLargeEmphasized = poppins(FontWeight.SemiBold, 57, 64, -0.25),
    displayMedium = poppins(FontWeight.Normal, 45, 52),
    displayMediumEmphasized = poppins(FontWeight.SemiBold, 45, 52),
    displaySmall = poppins(FontWeight.Normal, 36, 44),
    displaySmallEmphasized = poppins(FontWeight.SemiBold, 36, 44),

    // --- Headline ----------------------------------------------------------
    headlineLarge = poppins(FontWeight.Normal, 32, 40),
    headlineLargeEmphasized = poppins(FontWeight.SemiBold, 32, 40),
    headlineMedium = poppins(FontWeight.Medium, 28, 36),
    headlineMediumEmphasized = poppins(FontWeight.Bold, 28, 36),
    headlineSmall = poppins(FontWeight.Medium, 24, 32),
    headlineSmallEmphasized = poppins(FontWeight.Bold, 24, 32),

    // --- Title -------------------------------------------------------------
    titleLarge = poppins(FontWeight.SemiBold, 22, 28),
    titleLargeEmphasized = poppins(FontWeight.Bold, 22, 28),
    titleMedium = poppins(FontWeight.Medium, 16, 24, 0.15),
    titleMediumEmphasized = poppins(FontWeight.SemiBold, 16, 24, 0.1),
    titleSmall = poppins(FontWeight.Medium, 14, 20, 0.1),
    titleSmallEmphasized = poppins(FontWeight.SemiBold, 14, 20, 0.05),

    // --- Body --------------------------------------------------------------
    bodyLarge = poppins(FontWeight.Normal, 16, 24, 0.5),
    bodyLargeEmphasized = poppins(FontWeight.Medium, 16, 24, 0.4),
    bodyMedium = poppins(FontWeight.Normal, 14, 20, 0.25),
    bodyMediumEmphasized = poppins(FontWeight.Medium, 14, 20, 0.2),
    bodySmall = poppins(FontWeight.Normal, 12, 16, 0.4),
    bodySmallEmphasized = poppins(FontWeight.Medium, 12, 16, 0.3),

    // --- Label -------------------------------------------------------------
    labelLarge = poppins(FontWeight.Medium, 14, 20, 0.1),
    labelLargeEmphasized = poppins(FontWeight.SemiBold, 14, 20, 0.05),
    labelMedium = poppins(FontWeight.Medium, 12, 16, 0.5),
    labelMediumEmphasized = poppins(FontWeight.SemiBold, 12, 16, 0.4),
    labelSmall = poppins(FontWeight.Medium, 11, 16, 0.5),
    labelSmallEmphasized = poppins(FontWeight.SemiBold, 11, 16, 0.4)
)

/** Monospace family used by the code editor and any inline code spans. */
val CodeFontFamily = FontFamily.Monospace

@Deprecated(
    "Renamed to CodeFontFamily; the family is not JetBrains Mono.",
    ReplaceWith("CodeFontFamily")
)
val JetBrainsMonoFamily = CodeFontFamily

val CodeEditorTextStyle = TextStyle(
    fontFamily = CodeFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp
)
