package com.oro.app.ui.screens.settings

import com.oro.app.ui.theme.OroCorners
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.Speed
import com.oro.app.data.repository.LiteMode
import com.oro.app.ui.theme.DevicePerformance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oro.app.BuildConfig
import com.oro.app.R
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.ApiKeyRequirement
import com.oro.app.data.repository.ThemeMode
import com.oro.app.ui.components.expressive.PickerOption
import com.oro.app.ui.components.expressive.SegmentedPicker
import com.oro.app.ui.theme.Space
import androidx.compose.ui.unit.sp
import com.oro.app.data.ai.displayName

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToAiModels: () -> Unit = {},
    onNavigateToMemories: () -> Unit = {},
    onNavigateToGitSettings: () -> Unit,
    onNavigateToProviders: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lowEndDevice = remember(context) { DevicePerformance.isLowEndDevice(context) }
    SettingsContent(
        uiState = uiState,
        lowEndDevice = lowEndDevice,
        onBackClick = onBackClick,
        onNavigateToAiModels = onNavigateToAiModels,
        onNavigateToMemories = onNavigateToMemories,
        onNavigateToGitSettings = onNavigateToGitSettings,
        onNavigateToProviders = onNavigateToProviders,
        onNavigateToMcp = onNavigateToMcp,
        onNavigateToVoice = onNavigateToVoice,
        onNavigateToSkills = onNavigateToSkills,
        onThemeSelected = viewModel::setThemeMode,
        onLiteModeSelected = viewModel::setLiteMode,
        onFontSizeChange = viewModel::setFontSize,
        onLineNumbersChange = viewModel::setLineNumbers,
        onWordWrapChange = viewModel::setWordWrap,
        onDebugLogsChange = viewModel::setDebugLogsEnabled,
    )
}

/** The settings screen for a given state; [SettingsScreen] wires it to its view model. */
@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    lowEndDevice: Boolean,
    onBackClick: () -> Unit,
    onNavigateToAiModels: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToGitSettings: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToMcp: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onLiteModeSelected: (com.oro.app.data.repository.LiteMode) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineNumbersChange: (Boolean) -> Unit,
    onWordWrapChange: (Boolean) -> Unit,
    onDebugLogsChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = context.getString(R.string.settings_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = context.getString(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Appearance Section
            SettingsSection(title = "Appearance") {
                // Theme Setting
                ThemeSelector(
                    currentTheme = uiState.themeMode,
                    onThemeSelected = onThemeSelected
                )

                // The Dynamic Colors toggle was removed. Material You derives
                // a scheme from the wallpaper, which injects an arbitrary hue
                // over every surface and silently undoes the monochrome
                // direction -- in practice a blue wallpaper turned the whole
                // app blue. See the note in OroTheme.
            }

            // Performance Section
            SettingsSection(title = "Performance") {
                LiteModeSelector(
                    current = uiState.liteMode,
                    lowEndDevice = lowEndDevice,
                    onSelected = onLiteModeSelected
                )
            }

            // Editor Section
            SettingsSection(title = "Editor") {
                // Font Size
                FontSizeSetting(
                    currentSize = uiState.fontSize,
                    onSizeChange = onFontSizeChange
                )

                // Line Numbers
                SettingsToggle(
                    icon = Icons.Outlined.Code,
                    title = "Line numbers",
                    description = null,
                    checked = uiState.lineNumbers,
                    onCheckedChange = onLineNumbersChange
                )

                // Word Wrap
                SettingsToggle(
                    icon = Icons.AutoMirrored.Outlined.WrapText,
                    title = "Word wrap",
                    description = null,
                    checked = uiState.wordWrap,
                    onCheckedChange = onWordWrapChange
                )


            }

            // AI Section
            SettingsSection(title = "AI") {
                // AI Models Navigation
                val currentModelName = uiState.availableModels.find { it.id == uiState.aiModel }?.displayName()
                    ?: uiState.aiModel.substringAfterLast("/")
                SettingsNavItem(
                    icon = Icons.Outlined.ModelTraining,
                    title = "AI models",
                    subtitle = currentModelName,
                    onClick = onNavigateToAiModels
                )

                // Memories Navigation
                SettingsNavItem(
                    icon = Icons.Outlined.Memory,
                    title = "Memories",
                    subtitle = null,
                    onClick = onNavigateToMemories
                )

                // The key fields moved to their own screen. Stacked password
                // inputs gave no sense of which providers were usable, which
                // needed a key, or where a key came from -- the user had to
                // already know.
                SettingsNavItem(
                    icon = Icons.Outlined.Key,
                    title = "AI providers",
                    subtitle = providerSummary(uiState.apiKeys),
                    onClick = onNavigateToProviders
                )
                SettingsNavItem(
                    icon = Icons.Outlined.Hub,
                    title = "MCP servers",
                    subtitle = null,
                    onClick = onNavigateToMcp
                )
                SettingsNavItem(
                    icon = Icons.Outlined.Mic,
                    title = "Voice dictation",
                    subtitle = null,
                    onClick = onNavigateToVoice
                )
                SettingsNavItem(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Skills",
                    subtitle = null,
                    onClick = onNavigateToSkills
                )
            }

            // Git Section
            SettingsSection(title = "Git") {
                GitSettingsNavItem(
                    title = "Git settings",
                    subtitle = null,
                    onClick = onNavigateToGitSettings
                )
            }

            // About Section
            SettingsSection(title = "About") {
                SettingsInfoItem(
                    icon = Icons.Outlined.Info,
                    title = "Version",
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                )
            }
            
            // Beta Section
            SettingsSection(title = "Beta") {
                 SettingsToggle(
                    icon = Icons.Outlined.Code, // Reusing Code icon or maybe Bug icon if available
                    title = "Debug logs",
                    description = "Raw model traffic in .oro/logs.txt",
                    checked = uiState.debugLogsEnabled,
                    onCheckedChange = onDebugLogsChange
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ApiKeyField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    note: String = "",
    signupUrl: String = "",
    masked: Boolean = true
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                // Emphasized role: stresses the provider name against the note
                // below it without changing its size.
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (value.isNotBlank()) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Key saved",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (note.isNotBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            visualTransformation =
                if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (masked) KeyboardType.Password else KeyboardType.Uri
            ),
            textStyle = MaterialTheme.typography.bodySmall
        )
        if (signupUrl.isNotBlank()) {
            TextButton(onClick = { uriHandler.openUri(signupUrl) }) {
                Text("Get a key", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Section container with title
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title.uppercase(),
            // The small caps every Oro sheet uses for a field label.
            style = MaterialTheme.typography.labelSmallEmphasized.copy(letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Space.xs)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = OroCorners.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                content()
            }
        }
    }
}

/** Theme: System / Light / Dark as a segmented control (selection is the one filled segment). */
@Composable
private fun ThemeSelector(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Brightness4,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Theme",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        SegmentedPicker(
            label = null,
            options = listOf(
                PickerOption(ThemeMode.SYSTEM, "System"),
                PickerOption(ThemeMode.LIGHT, "Light"),
                PickerOption(ThemeMode.DARK, "Dark")
            ),
            selected = currentTheme,
            onSelect = onThemeSelected
        )
    }
}

/**
 * Font size slider setting
 */
@Composable
private fun FontSizeSetting(
    currentSize: Float,
    onSizeChange: (Float) -> Unit
) {
    // The slider tracks a local value while dragging and persists once on
    // release. Writing through on every onValueChange meant a DataStore disk
    // write (and a full settings-state re-emission) per drag event.
    var draftSize by remember(currentSize) { mutableFloatStateOf(currentSize) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FormatSize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Font size",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "${draftSize.toInt()}sp",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = draftSize,
            onValueChange = { draftSize = it },
            onValueChangeFinished = { onSizeChange(draftSize) },
            valueRange = 10f..24f,
            steps = 13,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "10",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = "24",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Toggle setting item
 */
@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/**
 * Navigation item to another screen
 */
@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Info item (read-only)
 */
@Composable
private fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Git settings navigation item with GitHub icon
 */
@Composable
private fun GitSettingsNavItem(
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_github),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Lite mode: Auto / On / Off. Auto follows the device class, and the caption
 * says what Auto resolved to so the choice is never a guess.
 */
@Composable
private fun LiteModeSelector(
    current: LiteMode,
    lowEndDevice: Boolean,
    onSelected: (LiteMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lite mode",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (current) {
                        LiteMode.AUTO -> if (lowEndDevice) "On for this phone" else "Off for this phone"
                        LiteMode.ON -> "Calmer motion, no blur"
                        LiteMode.OFF -> "Full effects"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SegmentedPicker(
            label = null,
            options = LiteMode.entries.map { mode ->
                PickerOption(
                    mode,
                    when (mode) {
                        LiteMode.AUTO -> "Auto"
                        LiteMode.ON -> "On"
                        LiteMode.OFF -> "Off"
                    }
                )
            },
            selected = current,
            onSelect = onSelected
        )
    }
}

/** "3 of 11 ready" -- answers the only question this row needs to answer. */
private fun providerSummary(apiKeys: Map<AiProvider, String>): String {
    val ready = AiProvider.entries.count { provider ->
        !provider.requiresApiKey || apiKeys[provider].orEmpty().isNotBlank()
    }
    return "$ready of ${AiProvider.entries.size} ready"
}
