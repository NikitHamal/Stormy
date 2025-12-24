package com.codex.stormy.ui.screens.editor.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.theme.PoppinsFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Color format for export
 */
enum class ColorFormat(val displayName: String) {
    HEX("HEX"),
    RGB("RGB"),
    HSL("HSL"),
    RGBA("RGBA"),
    CSS_VAR("CSS Variable")
}

/**
 * Color harmony type for palette generation
 */
enum class ColorHarmony(val displayName: String) {
    COMPLEMENTARY("Complementary"),
    ANALOGOUS("Analogous"),
    TRIADIC("Triadic"),
    SPLIT_COMPLEMENTARY("Split Complementary"),
    TETRADIC("Tetradic"),
    MONOCHROMATIC("Monochromatic")
}

/**
 * Predefined color palette categories
 */
enum class PaletteCategory(val displayName: String) {
    MATERIAL("Material Design"),
    TAILWIND("Tailwind CSS"),
    WEB_SAFE("Web Safe"),
    PASTEL("Pastel"),
    VIBRANT("Vibrant"),
    NEUTRAL("Neutral")
}

/**
 * Color Picker Dialog with harmony suggestions and palettes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerDialog(
    onDismiss: () -> Unit,
    onColorSelected: (Color, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current

    // State
    var selectedTab by remember { mutableIntStateOf(0) }
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var lightness by remember { mutableFloatStateOf(0.5f) }
    var alpha by remember { mutableFloatStateOf(1f) }
    var hexInput by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf(ColorFormat.HEX) }
    var selectedHarmony by remember { mutableStateOf(ColorHarmony.COMPLEMENTARY) }
    var selectedPaletteCategory by remember { mutableStateOf(PaletteCategory.MATERIAL) }

    // Computed color from HSL
    val selectedColor by remember(hue, saturation, lightness, alpha) {
        derivedStateOf {
            hslToColor(hue, saturation, lightness, alpha)
        }
    }

    // Update hex input when color changes
    val hexValue by remember(selectedColor) {
        derivedStateOf {
            colorToHex(selectedColor)
        }
    }

    // Color harmony palette
    val harmonyColors by remember(selectedColor, selectedHarmony) {
        derivedStateOf {
            generateHarmonyPalette(selectedColor, selectedHarmony)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Color Picker",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Picker") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Harmony") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Palettes") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> ColorPickerTab(
                    selectedColor = selectedColor,
                    hue = hue,
                    saturation = saturation,
                    lightness = lightness,
                    alpha = alpha,
                    hexValue = hexValue,
                    selectedFormat = selectedFormat,
                    onHueChange = { hue = it },
                    onSaturationChange = { saturation = it },
                    onLightnessChange = { lightness = it },
                    onAlphaChange = { alpha = it },
                    onHexInput = { input ->
                        hexInput = input
                        parseHexColor(input)?.let { color ->
                            val hsl = colorToHsl(color)
                            hue = hsl[0]
                            saturation = hsl[1]
                            lightness = hsl[2]
                        }
                    },
                    onFormatChange = { selectedFormat = it },
                    onCopyCode = { code ->
                        clipboardManager.setText(AnnotatedString(code))
                        onColorSelected(selectedColor, code)
                    }
                )
                1 -> ColorHarmonyTab(
                    selectedColor = selectedColor,
                    selectedHarmony = selectedHarmony,
                    harmonyColors = harmonyColors,
                    onHarmonyChange = { selectedHarmony = it },
                    onColorSelect = { color ->
                        val hsl = colorToHsl(color)
                        hue = hsl[0]
                        saturation = hsl[1]
                        lightness = hsl[2]
                        selectedTab = 0 // Switch to picker tab
                    },
                    onCopyCode = { code ->
                        clipboardManager.setText(AnnotatedString(code))
                        onColorSelected(selectedColor, code)
                    }
                )
                2 -> ColorPalettesTab(
                    selectedCategory = selectedPaletteCategory,
                    onCategoryChange = { selectedPaletteCategory = it },
                    onColorSelect = { color ->
                        val hsl = colorToHsl(color)
                        hue = hsl[0]
                        saturation = hsl[1]
                        lightness = hsl[2]
                        selectedTab = 0 // Switch to picker tab
                    }
                )
            }
        }
    }
}

@Composable
private fun ColorPickerTab(
    selectedColor: Color,
    hue: Float,
    saturation: Float,
    lightness: Float,
    alpha: Float,
    hexValue: String,
    selectedFormat: ColorFormat,
    onHueChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onLightnessChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onHexInput: (String) -> Unit,
    onFormatChange: (ColorFormat) -> Unit,
    onCopyCode: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // Color preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Large color preview
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp)),
                color = selectedColor,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 4.dp
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }

            Column(modifier = Modifier.weight(1f)) {
                // Hex input
                OutlinedTextField(
                    value = hexValue,
                    onValueChange = onHexInput,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("HEX Color") },
                    placeholder = { Text("#RRGGBB") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    trailingIcon = {
                        IconButton(onClick = { onCopyCode(hexValue) }) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy"
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Hue slider
        ColorSliderRow(
            label = "Hue",
            value = hue,
            valueRange = 0f..360f,
            displayValue = "${hue.roundToInt()}°",
            onValueChange = onHueChange,
            sliderColors = listOf(
                Color.Red,
                Color.Yellow,
                Color.Green,
                Color.Cyan,
                Color.Blue,
                Color.Magenta,
                Color.Red
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Saturation slider
        ColorSliderRow(
            label = "Saturation",
            value = saturation,
            valueRange = 0f..1f,
            displayValue = "${(saturation * 100).roundToInt()}%",
            onValueChange = onSaturationChange,
            sliderColors = listOf(
                hslToColor(hue, 0f, lightness),
                hslToColor(hue, 1f, lightness)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Lightness slider
        ColorSliderRow(
            label = "Lightness",
            value = lightness,
            valueRange = 0f..1f,
            displayValue = "${(lightness * 100).roundToInt()}%",
            onValueChange = onLightnessChange,
            sliderColors = listOf(
                Color.Black,
                hslToColor(hue, saturation, 0.5f),
                Color.White
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Alpha slider
        ColorSliderRow(
            label = "Alpha",
            value = alpha,
            valueRange = 0f..1f,
            displayValue = "${(alpha * 100).roundToInt()}%",
            onValueChange = onAlphaChange,
            sliderColors = listOf(
                selectedColor.copy(alpha = 0f),
                selectedColor.copy(alpha = 1f)
            )
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Format selection
        Text(
            text = "Copy as:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ColorFormat.entries) { format ->
                FilterChip(
                    selected = selectedFormat == format,
                    onClick = { onFormatChange(format) },
                    label = { Text(format.displayName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Format preview and copy
        val formattedColor = formatColor(selectedColor, selectedFormat)
        Surface(
            onClick = { onCopyCode(formattedColor) },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
    sliderColors: List<Color>
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Gradient background for slider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(sliderColors)
                )
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun ColorHarmonyTab(
    selectedColor: Color,
    selectedHarmony: ColorHarmony,
    harmonyColors: List<Color>,
    onHarmonyChange: (ColorHarmony) -> Unit,
    onColorSelect: (Color) -> Unit,
    onCopyCode: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Current color preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Base Color:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = selectedColor,
                shadowElevation = 2.dp
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = colorToHex(selectedColor),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Harmony type selection
        Text(
            text = "Harmony Type:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ColorHarmony.entries) { harmony ->
                FilterChip(
                    selected = selectedHarmony == harmony,
                    onClick = { onHarmonyChange(harmony) },
                    label = { Text(harmony.displayName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Harmony palette display
        Text(
            text = "Generated Palette:",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Color swatches
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            harmonyColors.forEach { color ->
                ColorSwatch(
                    color = color,
                    isSelected = false,
                    onClick = { onColorSelect(color) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Copy all as CSS
        val cssVariables = harmonyColors.mapIndexed { index, color ->
            "--color-${index + 1}: ${colorToHex(color)};"
        }.joinToString("\n")

        Surface(
            onClick = { onCopyCode(cssVariables) },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Copy as CSS Variables",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = cssVariables,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ColorPalettesTab(
    selectedCategory: PaletteCategory,
    onCategoryChange: (PaletteCategory) -> Unit,
    onColorSelect: (Color) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Category selection
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PaletteCategory.entries) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategoryChange(category) },
                    label = { Text(category.displayName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Palette colors grid
        val colors = getPaletteColors(selectedCategory)

        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(colors) { color ->
                ColorGridItem(
                    color = color,
                    onClick = { onColorSelect(color) }
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            ),
        color = color,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = if (isColorLight(color)) Color.Black else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ColorGridItem(
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = color,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 1.dp
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}

// Color utility functions

private fun hslToColor(h: Float, s: Float, l: Float, a: Float = 1f): Color {
    val c = (1 - abs(2 * l - 1)) * s
    val x = c * (1 - abs((h / 60) % 2 - 1))
    val m = l - c / 2

    val (r1, g1, b1) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = a
    )
}

private fun colorToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2

    return if (max == min) {
        floatArrayOf(0f, 0f, l) // achromatic
    } else {
        val d = max - min
        val s = if (l > 0.5f) d / (2 - max - min) else d / (max + min)

        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6 else 0)) * 60
            g -> ((b - r) / d + 2) * 60
            else -> ((r - g) / d + 4) * 60
        }

        floatArrayOf(h, s, l)
    }
}

private fun colorToHex(color: Color): String {
    val r = (color.red * 255).roundToInt()
    val g = (color.green * 255).roundToInt()
    val b = (color.blue * 255).roundToInt()
    return String.format("#%02X%02X%02X", r, g, b)
}

private fun parseHexColor(hex: String): Color? {
    return try {
        val cleanHex = hex.removePrefix("#")
        if (cleanHex.length == 6) {
            val r = cleanHex.substring(0, 2).toInt(16) / 255f
            val g = cleanHex.substring(2, 4).toInt(16) / 255f
            val b = cleanHex.substring(4, 6).toInt(16) / 255f
            Color(r, g, b)
        } else null
    } catch (e: Exception) {
        null
    }
}

private fun formatColor(color: Color, format: ColorFormat): String {
    val r = (color.red * 255).roundToInt()
    val g = (color.green * 255).roundToInt()
    val b = (color.blue * 255).roundToInt()
    val a = color.alpha

    return when (format) {
        ColorFormat.HEX -> colorToHex(color)
        ColorFormat.RGB -> "rgb($r, $g, $b)"
        ColorFormat.RGBA -> "rgba($r, $g, $b, ${String.format("%.2f", a)})"
        ColorFormat.HSL -> {
            val hsl = colorToHsl(color)
            "hsl(${hsl[0].roundToInt()}, ${(hsl[1] * 100).roundToInt()}%, ${(hsl[2] * 100).roundToInt()}%)"
        }
        ColorFormat.CSS_VAR -> "--color-primary: ${colorToHex(color)};"
    }
}

private fun generateHarmonyPalette(baseColor: Color, harmony: ColorHarmony): List<Color> {
    val hsl = colorToHsl(baseColor)
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]

    return when (harmony) {
        ColorHarmony.COMPLEMENTARY -> listOf(
            baseColor,
            hslToColor((h + 180) % 360, s, l)
        )
        ColorHarmony.ANALOGOUS -> listOf(
            hslToColor((h - 30 + 360) % 360, s, l),
            baseColor,
            hslToColor((h + 30) % 360, s, l)
        )
        ColorHarmony.TRIADIC -> listOf(
            baseColor,
            hslToColor((h + 120) % 360, s, l),
            hslToColor((h + 240) % 360, s, l)
        )
        ColorHarmony.SPLIT_COMPLEMENTARY -> listOf(
            baseColor,
            hslToColor((h + 150) % 360, s, l),
            hslToColor((h + 210) % 360, s, l)
        )
        ColorHarmony.TETRADIC -> listOf(
            baseColor,
            hslToColor((h + 90) % 360, s, l),
            hslToColor((h + 180) % 360, s, l),
            hslToColor((h + 270) % 360, s, l)
        )
        ColorHarmony.MONOCHROMATIC -> listOf(
            hslToColor(h, s, 0.2f),
            hslToColor(h, s, 0.4f),
            baseColor,
            hslToColor(h, s, 0.7f),
            hslToColor(h, s, 0.9f)
        )
    }
}

private fun isColorLight(color: Color): Boolean {
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance > 0.5
}

private fun getPaletteColors(category: PaletteCategory): List<Color> {
    return when (category) {
        PaletteCategory.MATERIAL -> listOf(
            // Red
            Color(0xFFFFEBEE), Color(0xFFEF9A9A), Color(0xFFF44336), Color(0xFFD32F2F), Color(0xFFB71C1C),
            // Pink
            Color(0xFFFCE4EC), Color(0xFFF48FB1), Color(0xFFE91E63), Color(0xFFC2185B), Color(0xFF880E4F),
            // Purple
            Color(0xFFF3E5F5), Color(0xFFCE93D8), Color(0xFF9C27B0), Color(0xFF7B1FA2), Color(0xFF4A148C),
            // Deep Purple
            Color(0xFFEDE7F6), Color(0xFFB39DDB), Color(0xFF673AB7), Color(0xFF512DA8), Color(0xFF311B92),
            // Indigo
            Color(0xFFE8EAF6), Color(0xFF9FA8DA), Color(0xFF3F51B5), Color(0xFF303F9F), Color(0xFF1A237E),
            // Blue
            Color(0xFFE3F2FD), Color(0xFF90CAF9), Color(0xFF2196F3), Color(0xFF1976D2), Color(0xFF0D47A1),
            // Light Blue
            Color(0xFFE1F5FE), Color(0xFF81D4FA), Color(0xFF03A9F4), Color(0xFF0288D1), Color(0xFF01579B),
            // Cyan
            Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFF00BCD4), Color(0xFF0097A7), Color(0xFF006064),
            // Teal
            Color(0xFFE0F2F1), Color(0xFF80CBC4), Color(0xFF009688), Color(0xFF00796B), Color(0xFF004D40),
            // Green
            Color(0xFFE8F5E9), Color(0xFFA5D6A7), Color(0xFF4CAF50), Color(0xFF388E3C), Color(0xFF1B5E20),
            // Light Green
            Color(0xFFF1F8E9), Color(0xFFC5E1A5), Color(0xFF8BC34A), Color(0xFF689F38), Color(0xFF33691E),
            // Lime
            Color(0xFFF9FBE7), Color(0xFFE6EE9C), Color(0xFFCDDC39), Color(0xFFAFB42B), Color(0xFF827717),
            // Yellow
            Color(0xFFFFFDE7), Color(0xFFFFF59D), Color(0xFFFFEB3B), Color(0xFFFBC02D), Color(0xFFF57F17),
            // Amber
            Color(0xFFFFF8E1), Color(0xFFFFE082), Color(0xFFFFC107), Color(0xFFFFA000), Color(0xFFFF6F00),
            // Orange
            Color(0xFFFFF3E0), Color(0xFFFFCC80), Color(0xFFFF9800), Color(0xFFF57C00), Color(0xFFE65100),
            // Deep Orange
            Color(0xFFFBE9E7), Color(0xFFFFAB91), Color(0xFFFF5722), Color(0xFFE64A19), Color(0xFFBF360C),
            // Brown
            Color(0xFFEFEBE9), Color(0xFFBCAAA4), Color(0xFF795548), Color(0xFF5D4037), Color(0xFF3E2723),
            // Grey
            Color(0xFFFAFAFA), Color(0xFFBDBDBD), Color(0xFF9E9E9E), Color(0xFF616161), Color(0xFF212121)
        )
        PaletteCategory.TAILWIND -> listOf(
            // Slate
            Color(0xFFF8FAFC), Color(0xFFCBD5E1), Color(0xFF64748B), Color(0xFF334155), Color(0xFF0F172A),
            // Gray
            Color(0xFFF9FAFB), Color(0xFFD1D5DB), Color(0xFF6B7280), Color(0xFF374151), Color(0xFF111827),
            // Zinc
            Color(0xFFFAFAFA), Color(0xFFD4D4D8), Color(0xFF71717A), Color(0xFF3F3F46), Color(0xFF18181B),
            // Red
            Color(0xFFFEF2F2), Color(0xFFFCA5A5), Color(0xFFEF4444), Color(0xFFB91C1C), Color(0xFF7F1D1D),
            // Orange
            Color(0xFFFFF7ED), Color(0xFFFDBA74), Color(0xFFF97316), Color(0xFFC2410C), Color(0xFF7C2D12),
            // Amber
            Color(0xFFFFFBEB), Color(0xFFFCD34D), Color(0xFFF59E0B), Color(0xFFB45309), Color(0xFF78350F),
            // Yellow
            Color(0xFFFEFCE8), Color(0xFFFDE047), Color(0xFFEAB308), Color(0xFFA16207), Color(0xFF713F12),
            // Lime
            Color(0xFFF7FEE7), Color(0xFFBEF264), Color(0xFF84CC16), Color(0xFF4D7C0F), Color(0xFF365314),
            // Green
            Color(0xFFF0FDF4), Color(0xFF86EFAC), Color(0xFF22C55E), Color(0xFF15803D), Color(0xFF14532D),
            // Emerald
            Color(0xFFECFDF5), Color(0xFF6EE7B7), Color(0xFF10B981), Color(0xFF047857), Color(0xFF064E3B),
            // Teal
            Color(0xFFF0FDFA), Color(0xFF5EEAD4), Color(0xFF14B8A6), Color(0xFF0F766E), Color(0xFF134E4A),
            // Cyan
            Color(0xFFECFEFF), Color(0xFF67E8F9), Color(0xFF06B6D4), Color(0xFF0E7490), Color(0xFF164E63),
            // Sky
            Color(0xFFF0F9FF), Color(0xFF7DD3FC), Color(0xFF0EA5E9), Color(0xFF0369A1), Color(0xFF0C4A6E),
            // Blue
            Color(0xFFEFF6FF), Color(0xFF93C5FD), Color(0xFF3B82F6), Color(0xFF1D4ED8), Color(0xFF1E3A8A),
            // Indigo
            Color(0xFFEEF2FF), Color(0xFFA5B4FC), Color(0xFF6366F1), Color(0xFF4338CA), Color(0xFF312E81),
            // Violet
            Color(0xFFF5F3FF), Color(0xFFC4B5FD), Color(0xFF8B5CF6), Color(0xFF6D28D9), Color(0xFF4C1D95),
            // Purple
            Color(0xFFFAF5FF), Color(0xFFD8B4FE), Color(0xFFA855F7), Color(0xFF7E22CE), Color(0xFF581C87),
            // Fuchsia
            Color(0xFFFDF4FF), Color(0xFFF0ABFC), Color(0xFFD946EF), Color(0xFFA21CAF), Color(0xFF701A75),
            // Pink
            Color(0xFFFDF2F8), Color(0xFFF9A8D4), Color(0xFFEC4899), Color(0xFFBE185D), Color(0xFF831843),
            // Rose
            Color(0xFFFFF1F2), Color(0xFFFDA4AF), Color(0xFFF43F5E), Color(0xFFBE123C), Color(0xFF881337)
        )
        PaletteCategory.WEB_SAFE -> listOf(
            Color(0xFF000000), Color(0xFF333333), Color(0xFF666666), Color(0xFF999999), Color(0xFFCCCCCC), Color(0xFFFFFFFF),
            Color(0xFFFF0000), Color(0xFFFF3300), Color(0xFFFF6600), Color(0xFFFF9900), Color(0xFFFFCC00), Color(0xFFFFFF00),
            Color(0xFF00FF00), Color(0xFF33FF00), Color(0xFF66FF00), Color(0xFF99FF00), Color(0xFFCCFF00), Color(0xFFFFFF33),
            Color(0xFF0000FF), Color(0xFF0033FF), Color(0xFF0066FF), Color(0xFF0099FF), Color(0xFF00CCFF), Color(0xFF00FFFF),
            Color(0xFFFF00FF), Color(0xFFFF33FF), Color(0xFFFF66FF), Color(0xFFFF99FF), Color(0xFFFFCCFF), Color(0xFFFFFFCC)
        )
        PaletteCategory.PASTEL -> listOf(
            Color(0xFFFFB3BA), Color(0xFFFFDFBA), Color(0xFFFFFFBA), Color(0xFFBAFFB3), Color(0xFFBAE1FF), Color(0xFFD4BAFF),
            Color(0xFFFFC4D6), Color(0xFFFFE4C4), Color(0xFFFFFFC4), Color(0xFFC4FFC4), Color(0xFFC4E4FF), Color(0xFFE4C4FF),
            Color(0xFFFFD1DC), Color(0xFFFFE4D1), Color(0xFFFFFFD1), Color(0xFFD1FFD1), Color(0xFFD1E4FF), Color(0xFFE4D1FF),
            Color(0xFFFFE4E8), Color(0xFFFFF0E4), Color(0xFFFFFEE4), Color(0xFFE4FFE4), Color(0xFFE4F0FF), Color(0xFFF0E4FF),
            Color(0xFFFFF0F5), Color(0xFFFFFAF0), Color(0xFFFFFFF0), Color(0xFFF0FFF0), Color(0xFFF0F8FF), Color(0xFFF8F0FF)
        )
        PaletteCategory.VIBRANT -> listOf(
            Color(0xFFFF1744), Color(0xFFF50057), Color(0xFFD500F9), Color(0xFF651FFF), Color(0xFF3D5AFE), Color(0xFF2979FF),
            Color(0xFF00B0FF), Color(0xFF00E5FF), Color(0xFF1DE9B6), Color(0xFF00E676), Color(0xFF76FF03), Color(0xFFC6FF00),
            Color(0xFFFFEA00), Color(0xFFFFC400), Color(0xFFFF9100), Color(0xFFFF3D00), Color(0xFFFF6E40), Color(0xFFFF8A80),
            Color(0xFFEA80FC), Color(0xFFB388FF), Color(0xFF8C9EFF), Color(0xFF82B1FF), Color(0xFF80D8FF), Color(0xFF84FFFF),
            Color(0xFFA7FFEB), Color(0xFFB9F6CA), Color(0xFFCCFF90), Color(0xFFF4FF81), Color(0xFFFFFF8D), Color(0xFFFFE57F)
        )
        PaletteCategory.NEUTRAL -> listOf(
            Color(0xFFFAFAFA), Color(0xFFF5F5F5), Color(0xFFEEEEEE), Color(0xFFE0E0E0), Color(0xFFBDBDBD), Color(0xFF9E9E9E),
            Color(0xFF757575), Color(0xFF616161), Color(0xFF424242), Color(0xFF212121), Color(0xFF000000), Color(0xFF263238),
            Color(0xFF37474F), Color(0xFF455A64), Color(0xFF546E7A), Color(0xFF607D8B), Color(0xFF78909C), Color(0xFF90A4AE),
            Color(0xFFB0BEC5), Color(0xFFCFD8DC), Color(0xFFECEFF1), Color(0xFFF5F5F5), Color(0xFFFAFAFA), Color(0xFFFFFFFF),
            Color(0xFF3E2723), Color(0xFF4E342E), Color(0xFF5D4037), Color(0xFF6D4C41), Color(0xFF795548), Color(0xFF8D6E63)
        )
    }
}
