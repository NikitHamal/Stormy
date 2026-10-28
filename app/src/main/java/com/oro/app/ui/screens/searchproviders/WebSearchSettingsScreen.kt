package com.oro.app.ui.screens.searchproviders

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oro.app.data.ai.search.CustomKind
import com.oro.app.data.ai.search.CustomSearchProvider
import com.oro.app.data.ai.search.SearchPresets
import com.oro.app.data.ai.search.WebSearch
import com.oro.app.ui.components.expressive.PickerOption
import com.oro.app.ui.components.expressive.SegmentedPicker
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/**
 * Where the web-search chain is assembled: the keyless engines that always
 * work, the free-tier APIs a key unlocks (TinyFish, Brave, Exa, Tavily,
 * Serper and the rest), and the user's own endpoints -- a SearXNG box at
 * home, a work proxy, anything that answers JSON or HTML. Each row tests
 * itself, so a provider proves it belongs before the agents rely on it.
 */
@Composable
fun WebSearchSettingsScreen(
    onBack: () -> Unit,
    viewModel: WebSearchSettingsViewModel = viewModel(factory = WebSearchSettingsViewModel.Factory)
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val customs = ui.views.filter { it.kind == WebSearch.ProviderView.Kind.CUSTOM }
    val presets = ui.views.filter { it.kind == WebSearch.ProviderView.Kind.PRESET }
    val builtIns = ui.views.filter { it.kind == WebSearch.ProviderView.Kind.BUILT_IN }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Web search", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.xxl),
        ) {
            item {
                Text(
                    text = "Orobi searches through these, first to last, skipping any that are blocked or rate-limited. Yours go first; keys stay on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.md)
                )
            }

            // ------------------------------------------------------- yours
            item { SectionHeader("Your providers") }
            items(customs, key = { it.id }) { view ->
                val spec = ui.config.custom.firstOrNull { it.id == view.id } ?: return@items
                ProviderRow(
                    view = view,
                    subtitle = spec.urlTemplate.take(70),
                    onToggle = { on -> viewModel.toggleCustom(view.id, on) },
                    onEdit = { viewModel.edit(view.id) },
                    onDelete = { viewModel.deleteCustom(view.id) },
                    onTest = { viewModel.test(view.id) },
                    testing = ui.testing == view.id,
                )
            }
            item {
                FilledTonalButton(
                    onClick = { viewModel.edit("custom-" + java.util.UUID.randomUUID().toString().take(8)) },
                    modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.sm)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Spacer(Modifier.width(Space.xs))
                    Text("Add a provider", maxLines = 1)
                }
            }

            // ------------------------------------------------------ presets
            item { SectionHeader("Free APIs, your key") }
            items(presets, key = { it.id }) { view ->
                ProviderRow(
                    view = view,
                    subtitle = view.note,
                    onToggle = { on -> viewModel.togglePreset(view.id, on) },
                    onEdit = { viewModel.edit(view.id) },
                    onTest = { viewModel.test(view.id) },
                    testing = ui.testing == view.id,
                )
            }

            // ----------------------------------------------------- built-ins
            item { SectionHeader("Keyless engines") }
            items(builtIns, key = { it.id }) { view ->
                ProviderRow(
                    view = view,
                    subtitle = view.note,
                    onToggle = { on -> viewModel.toggleBuiltIn(view.id, on) },
                    onTest = { viewModel.test(view.id) },
                    testing = ui.testing == view.id,
                )
            }
        }
    }

    ui.editing?.let { id ->
        val custom = ui.config.custom.firstOrNull { it.id == id }
        val preset = SearchPresets.byId(id)
        when {
            custom != null || preset == null && id.startsWith("custom-") ->
                CustomEditorSheet(
                    initial = custom ?: viewModel.newCustom().copy(id = id),
                    onSave = { spec -> viewModel.saveCustom(spec); viewModel.edit(null) },
                    onDismiss = { viewModel.edit(null) },
                )
            preset != null ->
                PresetKeySheet(
                    presetId = preset.id,
                    presetLabel = preset.label,
                    presetNote = preset.note,
                    currentKey = ui.config.presetKeys[preset.id].orEmpty(),
                    enabled = ui.config.presetEnabled(preset.id),
                    onSave = { key, enabled ->
                        viewModel.setPresetKey(preset.id, key)
                        viewModel.togglePreset(preset.id, enabled)
                        viewModel.edit(null)
                    },
                    onTest = { viewModel.test(preset.id) },
                    testResult = ui.testResult,
                    onDismiss = { viewModel.edit(null) },
                )
        }
    }

    ui.testResult?.let { result ->
        if (ui.editing == null) {
            ModalBottomSheet(onDismissRequest = viewModel::dismissTest, shape = MaterialTheme.shapes.extraLarge) {
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.lg)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column(Modifier.padding(top = Space.lg)) {
        HorizontalDivider(Modifier.padding(bottom = Space.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.xs)
        )
    }
}

@Composable
private fun ProviderRow(
    view: WebSearch.ProviderView,
    subtitle: String,
    onToggle: (Boolean) -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onTest: () -> Unit,
    testing: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (view.kind) {
                WebSearch.ProviderView.Kind.CUSTOM -> Icons.Outlined.Edit
                WebSearch.ProviderView.Kind.PRESET -> Icons.Outlined.Key
                WebSearch.ProviderView.Kind.BUILT_IN -> Icons.Outlined.TravelExplore
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.iconSmall)
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = view.label + when {
                    testing -> " · testing…"
                    view.coolingDown -> " · resting"
                    view.needsKey && !view.hasKey -> " · no key"
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onTest) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = "Test ${view.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        onEdit?.let {
            IconButton(onClick = it) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${view.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        onDelete?.let {
            IconButton(onClick = it) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete ${view.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (view.kind != WebSearch.ProviderView.Kind.PRESET) {
            Switch(checked = view.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun PresetKeySheet(
    presetId: String,
    presetLabel: String,
    presetNote: String,
    currentKey: String,
    enabled: Boolean,
    onSave: (String, Boolean) -> Unit,
    onTest: () -> Unit,
    testResult: String?,
    onDismiss: () -> Unit,
) {
    var key by remember(presetId) { mutableStateOf(currentKey) }
    var on by remember(presetId) { mutableStateOf(enabled) }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.xl).padding(bottom = Space.xl)) {
            Text(presetLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(presetNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Space.xxs))
            Spacer(Modifier.height(Space.md))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API key", maxLines = 1) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use it in searches", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = { on = it }, enabled = key.isNotBlank())
            }
            testResult?.let {
                Spacer(Modifier.height(Space.sm))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilledTonalButton(onClick = { onSave(key, on) }, enabled = key.isNotBlank()) { Text("Save", maxLines = 1) }
                TextButton(onClick = onTest, enabled = key.isNotBlank()) { Text("Test", maxLines = 1) }
                TextButton(onClick = onDismiss) { Text("Cancel", maxLines = 1) }
            }
        }
    }
}

@Composable
private fun CustomEditorSheet(
    initial: CustomSearchProvider,
    onSave: (CustomSearchProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    var spec by remember(initial.id) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.xl)
                .padding(bottom = Space.xl)
        ) {
            Text("Your search endpoint", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "{query}, {limit} and {key} are filled in for you. JSON answers are read automatically -- any API whose results carry a URL and a title works as-is.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xxs)
            )
            Spacer(Modifier.height(Space.md))
            OutlinedTextField(spec.label, { spec = spec.copy(label = it) }, label = { Text("Name", maxLines = 1) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.sm))
            SegmentedPicker(
                label = "Answers in",
                options = listOf(
                    PickerOption(CustomKind.JSON_SNIFF, "JSON"),
                    PickerOption(CustomKind.SEARXNG_JSON, "SearXNG"),
                    PickerOption(CustomKind.HTML_GENERIC, "HTML"),
                ),
                selected = spec.kind,
                onSelect = { spec = spec.copy(kind = it) },
            )
            Spacer(Modifier.height(Space.sm))
            OutlinedTextField(
                spec.urlTemplate, { spec = spec.copy(urlTemplate = it) },
                label = { Text("URL template", maxLines = 1) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Space.sm))
            OutlinedTextField(spec.key, { spec = spec.copy(key = it) }, label = { Text("API key (optional)", maxLines = 1) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.sm))
            Row {
                TextButton(onClick = { spec = spec.copy(bearer = !spec.bearer) }) {
                    Text(if (spec.bearer) "✓ Bearer header" else "Bearer header", maxLines = 1)
                }
            }
            OutlinedTextField(
                spec.keyHeader.orEmpty(), { spec = spec.copy(keyHeader = it.takeIf { v -> v.isNotBlank() }) },
                label = { Text("or key header, e.g. X-API-Key", maxLines = 1) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Space.sm))
            OutlinedTextField(
                spec.keyParam.orEmpty(), { spec = spec.copy(keyParam = it.takeIf { v -> v.isNotBlank() }) },
                label = { Text("or key query param, e.g. api_key", maxLines = 1) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Space.sm))
            OutlinedTextField(
                spec.method, { spec = spec.copy(method = it.uppercase().take(4)) },
                label = { Text("Method", maxLines = 1) }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (spec.method.equals("POST", true)) {
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    spec.bodyTemplate.orEmpty(), { spec = spec.copy(bodyTemplate = it.takeIf { v -> v.isNotBlank() }) },
                    label = { Text("Body template (JSON)", maxLines = 1) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (spec.kind == CustomKind.HTML_GENERIC) {
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    spec.htmlUrlPattern.orEmpty(), { spec = spec.copy(htmlUrlPattern = it.takeIf { v -> v.isNotBlank() }) },
                    label = { Text("URL regex (group 1 url, 2 title)", maxLines = 1) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    spec.htmlSnippetPattern.orEmpty(), { spec = spec.copy(htmlSnippetPattern = it.takeIf { v -> v.isNotBlank() }) },
                    label = { Text("Snippet regex (group 1)", maxLines = 1) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = spec.enabled, onCheckedChange = { spec = spec.copy(enabled = it) })
            }
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilledTonalButton(
                    onClick = { onSave(spec) },
                    enabled = spec.label.isNotBlank() && spec.urlTemplate.contains("{query}")
                ) { Text("Save", maxLines = 1) }
                TextButton(onClick = onDismiss) { Text("Cancel", maxLines = 1) }
            }
        }
    }
}
