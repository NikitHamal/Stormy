package com.codex.stormy.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.stormy.BuildConfig
import com.codex.stormy.R
import com.codex.stormy.data.repository.ThemeMode

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToAiModels: () -> Unit = {},
    onNavigateToMemories: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(context.getString(R.string.settings_title))
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SettingsSection(title = context.getString(R.string.settings_appearance))
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Brightness4,
                    title = context.getString(R.string.settings_theme),
                    subtitle = when (uiState.themeMode) {
                        ThemeMode.SYSTEM -> context.getString(R.string.settings_theme_system)
                        ThemeMode.LIGHT -> context.getString(R.string.settings_theme_light)
                        ThemeMode.DARK -> context.getString(R.string.settings_theme_dark)
                    },
                    onClick = { showThemeDialog = true }
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Palette,
                        title = context.getString(R.string.settings_dynamic_colors),
                        subtitle = context.getString(R.string.settings_dynamic_colors_desc),
                        checked = uiState.dynamicColors,
                        onCheckedChange = viewModel::setDynamicColors
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSection(title = context.getString(R.string.settings_editor))
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.FormatSize,
                    title = context.getString(R.string.settings_font_size),
                    subtitle = "${uiState.fontSize.toInt()}sp",
                    onClick = { showFontSizeDialog = true }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Code,
                    title = context.getString(R.string.settings_line_numbers),
                    subtitle = null,
                    checked = uiState.lineNumbers,
                    onCheckedChange = viewModel::setLineNumbers
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.WrapText,
                    title = context.getString(R.string.settings_word_wrap),
                    subtitle = null,
                    checked = uiState.wordWrap,
                    onCheckedChange = viewModel::setWordWrap
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Save,
                    title = context.getString(R.string.settings_auto_save),
                    subtitle = context.getString(R.string.settings_auto_save_desc),
                    checked = uiState.autoSave,
                    onCheckedChange = viewModel::setAutoSave
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSection(title = context.getString(R.string.settings_ai))
            }

            item {
                val currentModelName = uiState.availableModels.find { it.id == uiState.aiModel }?.name
                    ?: uiState.aiModel
                SettingsItem(
                    icon = Icons.Outlined.ModelTraining,
                    title = "AI Models",
                    subtitle = currentModelName,
                    onClick = onNavigateToAiModels
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Memory,
                    title = "Memories",
                    subtitle = "View and manage AI memories",
                    onClick = onNavigateToMemories
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSection(title = context.getString(R.string.settings_about))
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = context.getString(R.string.settings_version),
                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onClick = null
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showThemeDialog) {
        ThemeDialog(
            currentTheme = uiState.themeMode,
            onThemeSelected = { theme ->
                viewModel.setThemeMode(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showFontSizeDialog) {
        FontSizeDialog(
            currentSize = uiState.fontSize,
            onSizeSelected = { size ->
                viewModel.setFontSize(size)
                showFontSizeDialog = false
            },
            onDismiss = { showFontSizeDialog = false }
        )
    }

}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ThemeDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.settings_theme)) },
        text = {
            Column {
                ThemeMode.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == currentTheme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (theme) {
                                ThemeMode.SYSTEM -> context.getString(R.string.settings_theme_system)
                                ThemeMode.LIGHT -> context.getString(R.string.settings_theme_light)
                                ThemeMode.DARK -> context.getString(R.string.settings_theme_dark)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun FontSizeDialog(
    currentSize: Float,
    onSizeSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var sliderValue by remember { mutableStateOf(currentSize) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.settings_font_size)) },
        text = {
            Column {
                Text(
                    text = "${sliderValue.toInt()}sp",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 10f..24f,
                    steps = 13
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "10sp",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "24sp",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSizeSelected(sliderValue) }) {
                Text(context.getString(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.action_cancel))
            }
        }
    )
}


