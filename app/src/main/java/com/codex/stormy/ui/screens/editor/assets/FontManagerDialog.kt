package com.codex.stormy.ui.screens.editor.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * Font category for filtering
 */
enum class FontCategory(val displayName: String) {
    ALL("All"),
    SERIF("Serif"),
    SANS_SERIF("Sans Serif"),
    DISPLAY("Display"),
    HANDWRITING("Handwriting"),
    MONOSPACE("Monospace")
}

/**
 * Popular Google Font data
 */
data class GoogleFont(
    val name: String,
    val category: FontCategory,
    val weights: List<Int>,
    val hasItalic: Boolean = true,
    val popularity: Int = 0, // Higher = more popular
    val cssImport: String,
    val cssFamily: String
)

/**
 * Font Manager Dialog with Google Fonts browser
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontManagerDialog(
    onDismiss: () -> Unit,
    onFontSelected: (GoogleFont, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current

    // State
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(FontCategory.ALL) }
    var selectedFont by remember { mutableStateOf<GoogleFont?>(null) }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var previewText by remember { mutableStateOf("The quick brown fox jumps over the lazy dog") }

    // Get fonts
    val allFonts = remember { getPopularGoogleFonts() }

    // Filtered fonts
    val filteredFonts by remember(allFonts, searchQuery, selectedCategory, favorites, selectedTab) {
        derivedStateOf {
            val baseList = if (selectedTab == 1) {
                allFonts.filter { it.name in favorites }
            } else {
                allFonts
            }

            baseList.filter { font ->
                val matchesSearch = searchQuery.isEmpty() ||
                        font.name.contains(searchQuery, ignoreCase = true)
                val matchesCategory = selectedCategory == FontCategory.ALL ||
                        font.category == selectedCategory

                matchesSearch && matchesCategory
            }.sortedByDescending { it.popularity }
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
                    imageVector = Icons.Outlined.FontDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Font Manager",
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
                    text = { Text("Browse") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Favorites (${favorites.size})") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Search fonts...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category filter
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FontCategory.entries) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // Font list
            if (filteredFonts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.FontDownload,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedTab == 1) "No favorites yet" else "No fonts found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredFonts) { font ->
                        FontListItem(
                            font = font,
                            isFavorite = font.name in favorites,
                            isSelected = selectedFont == font,
                            previewText = previewText,
                            onClick = { selectedFont = font },
                            onToggleFavorite = {
                                favorites = if (font.name in favorites) {
                                    favorites - font.name
                                } else {
                                    favorites + font.name
                                }
                            }
                        )
                    }
                }
            }

            // Selected font details
            selectedFont?.let { font ->
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                FontDetailSection(
                    font = font,
                    onCopyCode = { code ->
                        clipboardManager.setText(AnnotatedString(code))
                        onFontSelected(font, code)
                    }
                )
            }
        }
    }
}

@Composable
private fun FontListItem(
    font: GoogleFont,
    isFavorite: Boolean,
    isSelected: Boolean,
    previewText: String,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = font.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = font.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Preview text (would need actual font loading for real preview)
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${font.weights.size} weights" + if (font.hasItalic) " + italic" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun FontDetailSection(
    font: GoogleFont,
    onCopyCode: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = font.name,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${font.category.displayName} • ${font.weights.size} weights",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Weight pills
        Text(
            text = "Available Weights:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(font.weights) { weight ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = getWeightName(weight),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Copy as:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // HTML Link tag
        CopyCodeCard(
            label = "HTML <link>",
            code = """<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="${font.cssImport}" rel="stylesheet">""",
            onCopy = onCopyCode
        )

        Spacer(modifier = Modifier.height(8.dp))

        // CSS @import
        CopyCodeCard(
            label = "CSS @import",
            code = """@import url('${font.cssImport}');""",
            onCopy = onCopyCode
        )

        Spacer(modifier = Modifier.height(8.dp))

        // CSS font-family
        CopyCodeCard(
            label = "CSS font-family",
            code = """font-family: ${font.cssFamily};""",
            onCopy = onCopyCode
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CopyCodeCard(
    label: String,
    code: String,
    onCopy: (String) -> Unit
) {
    Surface(
        onClick = { onCopy(code) },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun getWeightName(weight: Int): String {
    return when (weight) {
        100 -> "Thin (100)"
        200 -> "Extra Light (200)"
        300 -> "Light (300)"
        400 -> "Regular (400)"
        500 -> "Medium (500)"
        600 -> "Semi Bold (600)"
        700 -> "Bold (700)"
        800 -> "Extra Bold (800)"
        900 -> "Black (900)"
        else -> weight.toString()
    }
}

/**
 * Get list of popular Google Fonts with their metadata
 */
private fun getPopularGoogleFonts(): List<GoogleFont> {
    return listOf(
        // Most Popular Sans-Serif
        GoogleFont(
            name = "Roboto",
            category = FontCategory.SANS_SERIF,
            weights = listOf(100, 300, 400, 500, 700, 900),
            popularity = 100,
            cssImport = "https://fonts.googleapis.com/css2?family=Roboto:wght@100;300;400;500;700;900&display=swap",
            cssFamily = "'Roboto', sans-serif"
        ),
        GoogleFont(
            name = "Open Sans",
            category = FontCategory.SANS_SERIF,
            weights = listOf(300, 400, 500, 600, 700, 800),
            popularity = 99,
            cssImport = "https://fonts.googleapis.com/css2?family=Open+Sans:wght@300;400;500;600;700;800&display=swap",
            cssFamily = "'Open Sans', sans-serif"
        ),
        GoogleFont(
            name = "Lato",
            category = FontCategory.SANS_SERIF,
            weights = listOf(100, 300, 400, 700, 900),
            popularity = 98,
            cssImport = "https://fonts.googleapis.com/css2?family=Lato:wght@100;300;400;700;900&display=swap",
            cssFamily = "'Lato', sans-serif"
        ),
        GoogleFont(
            name = "Montserrat",
            category = FontCategory.SANS_SERIF,
            weights = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900),
            popularity = 97,
            cssImport = "https://fonts.googleapis.com/css2?family=Montserrat:wght@100;200;300;400;500;600;700;800;900&display=swap",
            cssFamily = "'Montserrat', sans-serif"
        ),
        GoogleFont(
            name = "Poppins",
            category = FontCategory.SANS_SERIF,
            weights = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900),
            popularity = 96,
            cssImport = "https://fonts.googleapis.com/css2?family=Poppins:wght@100;200;300;400;500;600;700;800;900&display=swap",
            cssFamily = "'Poppins', sans-serif"
        ),
        GoogleFont(
            name = "Inter",
            category = FontCategory.SANS_SERIF,
            weights = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900),
            popularity = 95,
            cssImport = "https://fonts.googleapis.com/css2?family=Inter:wght@100;200;300;400;500;600;700;800;900&display=swap",
            cssFamily = "'Inter', sans-serif"
        ),
        GoogleFont(
            name = "Nunito",
            category = FontCategory.SANS_SERIF,
            weights = listOf(200, 300, 400, 500, 600, 700, 800, 900),
            popularity = 90,
            cssImport = "https://fonts.googleapis.com/css2?family=Nunito:wght@200;300;400;500;600;700;800;900&display=swap",
            cssFamily = "'Nunito', sans-serif"
        ),
        GoogleFont(
            name = "Raleway",
            category = FontCategory.SANS_SERIF,
            weights = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900),
            popularity = 89,
            cssImport = "https://fonts.googleapis.com/css2?family=Raleway:wght@100;200;300;400;500;600;700;800;900&display=swap",
            cssFamily = "'Raleway', sans-serif"
        ),
        GoogleFont(
            name = "Ubuntu",
            category = FontCategory.SANS_SERIF,
            weights = listOf(300, 400, 500, 700),
            popularity = 85,
            cssImport = "https://fonts.googleapis.com/css2?family=Ubuntu:wght@300;400;500;700&display=swap",
            cssFamily = "'Ubuntu', sans-serif"
        ),
        GoogleFont(
            name = "Work Sans",
            category = FontCategory.SANS_SERIF,
            weights = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900),
            popularity = 84,
            cssImport = "https://fonts.googleapis.com/css2?family=Work+Sans:wght@100;200;300;400;500;600;700;800;900&display=swap",
            cssFamily = "'Work Sans', sans-serif"
        ),
        GoogleFont(
            name = "Quicksand",
            category = FontCategory.SANS_SERIF,
            weights = listOf(300, 400, 500, 600, 700),
            popularity = 82,
            cssImport = "https://fonts.googleapis.com/css2?family=Quicksand:wght@300;400;500;600;700&display=swap",
            cssFamily = "'Quicksand', sans-serif"
        ),
        GoogleFont(
            name = "Rubik",
            category = FontCategory.SANS_SERIF,
            weights = listOf(300, 400, 500, 600, 700, 800, 900),
            popularity = 81,
            cssImport = "https://fonts.googleapis.com/css2?family=Rubik:wght@300;400;500;600;700;800;900&display=swap",
            cssFamily = "'Rubik', sans-serif"
        ),
        GoogleFont(
            name = "Manrope",
            category = FontCategory.SANS_SERIF,
            weights = listOf(200, 300, 400, 500, 600, 700, 800),
            popularity = 80,
            cssImport = "https://fonts.googleapis.com/css2?family=Manrope:wght@200;300;400;500;600;700;800&display=swap",
            cssFamily = "'Manrope', sans-serif"
        ),
        GoogleFont(
            name = "DM Sans",
            category = FontCategory.SANS_SERIF,
            weights = listOf(400, 500, 700),
            popularity = 79,
            cssImport = "https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;700&display=swap",
            cssFamily = "'DM Sans', sans-serif"
        ),
        GoogleFont(
            name = "Plus Jakarta Sans",
            category = FontCategory.SANS_SERIF,
            weights = listOf(200, 300, 400, 500, 600, 700, 800),
            popularity = 78,
            cssImport = "https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@200;300;400;500;600;700;800&display=swap",
            cssFamily = "'Plus Jakarta Sans', sans-serif"
        ),

        // Popular Serif Fonts
        GoogleFont(
            name = "Roboto Slab",
            category = FontCategory.SERIF,
            weights = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900),
            hasItalic = false,
            popularity = 88,
            cssImport = "https://fonts.googleapis.com/css2?family=Roboto+Slab:wght@100;200;300;400;500;600;700;800;900&display=swap",
            cssFamily = "'Roboto Slab', serif"
        ),
        GoogleFont(
            name = "Merriweather",
            category = FontCategory.SERIF,
            weights = listOf(300, 400, 700, 900),
            popularity = 87,
            cssImport = "https://fonts.googleapis.com/css2?family=Merriweather:wght@300;400;700;900&display=swap",
            cssFamily = "'Merriweather', serif"
        ),
        GoogleFont(
            name = "Playfair Display",
            category = FontCategory.SERIF,
            weights = listOf(400, 500, 600, 700, 800, 900),
            popularity = 86,
            cssImport = "https://fonts.googleapis.com/css2?family=Playfair+Display:wght@400;500;600;700;800;900&display=swap",
            cssFamily = "'Playfair Display', serif"
        ),
        GoogleFont(
            name = "Lora",
            category = FontCategory.SERIF,
            weights = listOf(400, 500, 600, 700),
            popularity = 83,
            cssImport = "https://fonts.googleapis.com/css2?family=Lora:wght@400;500;600;700&display=swap",
            cssFamily = "'Lora', serif"
        ),
        GoogleFont(
            name = "PT Serif",
            category = FontCategory.SERIF,
            weights = listOf(400, 700),
            popularity = 77,
            cssImport = "https://fonts.googleapis.com/css2?family=PT+Serif:wght@400;700&display=swap",
            cssFamily = "'PT Serif', serif"
        ),
        GoogleFont(
            name = "Source Serif Pro",
            category = FontCategory.SERIF,
            weights = listOf(200, 300, 400, 600, 700, 900),
            popularity = 76,
            cssImport = "https://fonts.googleapis.com/css2?family=Source+Serif+Pro:wght@200;300;400;600;700;900&display=swap",
            cssFamily = "'Source Serif Pro', serif"
        ),
        GoogleFont(
            name = "Libre Baskerville",
            category = FontCategory.SERIF,
            weights = listOf(400, 700),
            popularity = 75,
            cssImport = "https://fonts.googleapis.com/css2?family=Libre+Baskerville:wght@400;700&display=swap",
            cssFamily = "'Libre Baskerville', serif"
        ),
        GoogleFont(
            name = "Crimson Text",
            category = FontCategory.SERIF,
            weights = listOf(400, 600, 700),
            popularity = 74,
            cssImport = "https://fonts.googleapis.com/css2?family=Crimson+Text:wght@400;600;700&display=swap",
            cssFamily = "'Crimson Text', serif"
        ),
        GoogleFont(
            name = "EB Garamond",
            category = FontCategory.SERIF,
            weights = listOf(400, 500, 600, 700, 800),
            popularity = 73,
            cssImport = "https://fonts.googleapis.com/css2?family=EB+Garamond:wght@400;500;600;700;800&display=swap",
            cssFamily = "'EB Garamond', serif"
        ),
        GoogleFont(
            name = "Bitter",
            category = FontCategory.SERIF,
            weights = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900),
            popularity = 72,
            cssImport = "https://fonts.googleapis.com/css2?family=Bitter:wght@100;200;300;400;500;600;700;800;900&display=swap",
            cssFamily = "'Bitter', serif"
        ),

        // Display Fonts
        GoogleFont(
            name = "Oswald",
            category = FontCategory.DISPLAY,
            weights = listOf(200, 300, 400, 500, 600, 700),
            hasItalic = false,
            popularity = 94,
            cssImport = "https://fonts.googleapis.com/css2?family=Oswald:wght@200;300;400;500;600;700&display=swap",
            cssFamily = "'Oswald', sans-serif"
        ),
        GoogleFont(
            name = "Bebas Neue",
            category = FontCategory.DISPLAY,
            weights = listOf(400),
            hasItalic = false,
            popularity = 70,
            cssImport = "https://fonts.googleapis.com/css2?family=Bebas+Neue&display=swap",
            cssFamily = "'Bebas Neue', cursive"
        ),
        GoogleFont(
            name = "Anton",
            category = FontCategory.DISPLAY,
            weights = listOf(400),
            hasItalic = false,
            popularity = 69,
            cssImport = "https://fonts.googleapis.com/css2?family=Anton&display=swap",
            cssFamily = "'Anton', sans-serif"
        ),
        GoogleFont(
            name = "Archivo Black",
            category = FontCategory.DISPLAY,
            weights = listOf(400),
            hasItalic = false,
            popularity = 68,
            cssImport = "https://fonts.googleapis.com/css2?family=Archivo+Black&display=swap",
            cssFamily = "'Archivo Black', sans-serif"
        ),
        GoogleFont(
            name = "Righteous",
            category = FontCategory.DISPLAY,
            weights = listOf(400),
            hasItalic = false,
            popularity = 67,
            cssImport = "https://fonts.googleapis.com/css2?family=Righteous&display=swap",
            cssFamily = "'Righteous', cursive"
        ),
        GoogleFont(
            name = "Lobster",
            category = FontCategory.DISPLAY,
            weights = listOf(400),
            hasItalic = false,
            popularity = 66,
            cssImport = "https://fonts.googleapis.com/css2?family=Lobster&display=swap",
            cssFamily = "'Lobster', cursive"
        ),

        // Monospace Fonts
        GoogleFont(
            name = "Roboto Mono",
            category = FontCategory.MONOSPACE,
            weights = listOf(100, 200, 300, 400, 500, 600, 700),
            popularity = 93,
            cssImport = "https://fonts.googleapis.com/css2?family=Roboto+Mono:wght@100;200;300;400;500;600;700&display=swap",
            cssFamily = "'Roboto Mono', monospace"
        ),
        GoogleFont(
            name = "Source Code Pro",
            category = FontCategory.MONOSPACE,
            weights = listOf(200, 300, 400, 500, 600, 700, 800, 900),
            popularity = 92,
            cssImport = "https://fonts.googleapis.com/css2?family=Source+Code+Pro:wght@200;300;400;500;600;700;800;900&display=swap",
            cssFamily = "'Source Code Pro', monospace"
        ),
        GoogleFont(
            name = "JetBrains Mono",
            category = FontCategory.MONOSPACE,
            weights = listOf(100, 200, 300, 400, 500, 600, 700, 800),
            popularity = 91,
            cssImport = "https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@100;200;300;400;500;600;700;800&display=swap",
            cssFamily = "'JetBrains Mono', monospace"
        ),
        GoogleFont(
            name = "Fira Code",
            category = FontCategory.MONOSPACE,
            weights = listOf(300, 400, 500, 600, 700),
            hasItalic = false,
            popularity = 65,
            cssImport = "https://fonts.googleapis.com/css2?family=Fira+Code:wght@300;400;500;600;700&display=swap",
            cssFamily = "'Fira Code', monospace"
        ),
        GoogleFont(
            name = "IBM Plex Mono",
            category = FontCategory.MONOSPACE,
            weights = listOf(100, 200, 300, 400, 500, 600, 700),
            popularity = 64,
            cssImport = "https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@100;200;300;400;500;600;700&display=swap",
            cssFamily = "'IBM Plex Mono', monospace"
        ),
        GoogleFont(
            name = "Space Mono",
            category = FontCategory.MONOSPACE,
            weights = listOf(400, 700),
            popularity = 63,
            cssImport = "https://fonts.googleapis.com/css2?family=Space+Mono:wght@400;700&display=swap",
            cssFamily = "'Space Mono', monospace"
        ),
        GoogleFont(
            name = "Inconsolata",
            category = FontCategory.MONOSPACE,
            weights = listOf(200, 300, 400, 500, 600, 700, 800, 900),
            hasItalic = false,
            popularity = 62,
            cssImport = "https://fonts.googleapis.com/css2?family=Inconsolata:wght@200;300;400;500;600;700;800;900&display=swap",
            cssFamily = "'Inconsolata', monospace"
        ),

        // Handwriting Fonts
        GoogleFont(
            name = "Dancing Script",
            category = FontCategory.HANDWRITING,
            weights = listOf(400, 500, 600, 700),
            hasItalic = false,
            popularity = 71,
            cssImport = "https://fonts.googleapis.com/css2?family=Dancing+Script:wght@400;500;600;700&display=swap",
            cssFamily = "'Dancing Script', cursive"
        ),
        GoogleFont(
            name = "Pacifico",
            category = FontCategory.HANDWRITING,
            weights = listOf(400),
            hasItalic = false,
            popularity = 61,
            cssImport = "https://fonts.googleapis.com/css2?family=Pacifico&display=swap",
            cssFamily = "'Pacifico', cursive"
        ),
        GoogleFont(
            name = "Caveat",
            category = FontCategory.HANDWRITING,
            weights = listOf(400, 500, 600, 700),
            hasItalic = false,
            popularity = 60,
            cssImport = "https://fonts.googleapis.com/css2?family=Caveat:wght@400;500;600;700&display=swap",
            cssFamily = "'Caveat', cursive"
        ),
        GoogleFont(
            name = "Satisfy",
            category = FontCategory.HANDWRITING,
            weights = listOf(400),
            hasItalic = false,
            popularity = 59,
            cssImport = "https://fonts.googleapis.com/css2?family=Satisfy&display=swap",
            cssFamily = "'Satisfy', cursive"
        ),
        GoogleFont(
            name = "Kalam",
            category = FontCategory.HANDWRITING,
            weights = listOf(300, 400, 700),
            hasItalic = false,
            popularity = 58,
            cssImport = "https://fonts.googleapis.com/css2?family=Kalam:wght@300;400;700&display=swap",
            cssFamily = "'Kalam', cursive"
        ),
        GoogleFont(
            name = "Indie Flower",
            category = FontCategory.HANDWRITING,
            weights = listOf(400),
            hasItalic = false,
            popularity = 57,
            cssImport = "https://fonts.googleapis.com/css2?family=Indie+Flower&display=swap",
            cssFamily = "'Indie Flower', cursive"
        ),
        GoogleFont(
            name = "Great Vibes",
            category = FontCategory.HANDWRITING,
            weights = listOf(400),
            hasItalic = false,
            popularity = 56,
            cssImport = "https://fonts.googleapis.com/css2?family=Great+Vibes&display=swap",
            cssFamily = "'Great Vibes', cursive"
        ),
        GoogleFont(
            name = "Sacramento",
            category = FontCategory.HANDWRITING,
            weights = listOf(400),
            hasItalic = false,
            popularity = 55,
            cssImport = "https://fonts.googleapis.com/css2?family=Sacramento&display=swap",
            cssFamily = "'Sacramento', cursive"
        )
    )
}
