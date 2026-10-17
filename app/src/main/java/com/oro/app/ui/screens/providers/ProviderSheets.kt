package com.oro.app.ui.screens.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.providers.CustomProviderConfig
import com.oro.app.data.ai.providers.KeyHealth
import com.oro.app.data.ai.providers.KeyRotation
import com.oro.app.data.ai.providers.ProviderPreset
import com.oro.app.data.ai.providers.ProviderPresets
import com.oro.app.data.ai.providers.WireProtocol
import com.oro.app.data.ai.providers.ModelIds
import com.oro.app.ui.components.expressive.ActionSheet
import com.oro.app.ui.components.expressive.FormSheet
import com.oro.app.ui.components.expressive.PickerOption
import com.oro.app.ui.components.expressive.SegmentedPicker
import com.oro.app.ui.components.expressive.SheetAction
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space
import java.text.DateFormat
import java.util.Date

/**
 * Everything about one provider in one sheet: keys (health, order, rotation,
 * test), and for your own providers their models and settings.
 */
@Composable
fun ProviderDetailSheet(
    ref: ProviderRef,
    state: ProvidersUiState,
    viewModel: ProvidersViewModel,
    onDismiss: () -> Unit
) {
    val custom = (ref as? ProviderRef.Custom)?.let { state.settings.customById(it.id) }
    val builtIn = (ref as? ProviderRef.BuiltIn)?.provider
    if (custom == null && builtIn == null) {
        // Deleted while open (or a stale ref): close instead of drawing nothing.
        androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val keys = state.keysOf(ref)
    val models by viewModel.customModels.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var keyMenu by remember { mutableStateOf<KeyUi?>(null) }
    var editing by remember { mutableStateOf(false) }
    var addingModel by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val title = custom?.name ?: builtIn!!.displayName
    val keyUrl = custom?.presetId?.let { ProviderPresets.byId(it)?.keyUrl } ?: builtIn?.apiKeyUrl.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.xl, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item(key = "title") {
                Column {
                    Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
                    Text(
                        text = custom?.let { "${it.protocol.label} · ${it.normalizedBaseUrl}" } ?: builtIn!!.keyNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (keyUrl.isNotBlank()) {
                        TextButton(onClick = { runCatching { uri.openUri(keyUrl) } }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text("Get a key")
                            Spacer(Modifier.size(Space.xs))
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                        }
                    }
                }
            }

            item(key = "keys-h") { Heading("API keys", if (keys.size > 1) "Tried in this order" else null) }
            if (keys.isEmpty()) {
                item(key = "keys-empty") {
                    Text(
                        "No keys yet." + if (builtIn?.worksWithoutKey == true || custom != null) " This provider may work without one." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(keys, key = { "k-" + it.entry.id }) { k ->
                KeyRow(k, testing = "test:${k.entry.id}" in state.busy, onClick = { keyMenu = k })
            }
            item(key = "keys-add") { AddKeyField(onAdd = { viewModel.addKey(ref, it) }) }
            if (keys.size > 1) {
                item(key = "rotation") {
                    SegmentedPicker(
                        label = "When several keys work",
                        options = KeyRotation.entries.map { PickerOption(it, it.label) },
                        selected = state.rotationOf(ref),
                        onSelect = { viewModel.setRotation(ref, it) }
                    )
                }
            }

            if (custom != null) {
                val busy = "models:${custom.id}" in state.busy
                item(key = "models-h") {
                    Heading("Models", "${models.size}") {
                        IconButton(onClick = { viewModel.refreshModels(custom.id) }, enabled = !busy) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Refresh, contentDescription = "Fetch models")
                        }
                        IconButton(onClick = { addingModel = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add model")
                        }
                    }
                }
                if (models.isEmpty()) {
                    item(key = "models-empty") {
                        Text(
                            "Fetch the provider's catalogue, or add a model by its id.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(models, key = { "m-" + it.id }, contentType = { "model" }) { m ->
                    ModelToggleRow(m, onToggle = { viewModel.setModelEnabled(m, it) })
                }
                item(key = "settings-h") { Heading("Provider", null) }
                item(key = "settings") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        SwitchRow("Enabled", "Show its models in the picker", custom.enabled) {
                            viewModel.updateCustomProvider(custom.copy(enabled = it))
                        }
                        SwitchRow("Native tool calling", "Turn off for models that only chat; Orobi then uses text tool calls", custom.supportsTools) {
                            viewModel.updateCustomProvider(custom.copy(supportsTools = it))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            OutlinedButton(onClick = { editing = true }) {
                                Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.size(Space.xs)); Text("Edit")
                            }
                            OutlinedButton(onClick = { confirmDelete = true }) {
                                Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.size(Space.xs)); Text("Remove")
                            }
                        }
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.size(Space.xl)) }
        }
    }

    keyMenu?.let { k ->
        val index = keys.indexOfFirst { it.entry.id == k.entry.id }
        ActionSheet(
            title = k.entry.label.ifBlank { k.entry.masked },
            subtitle = healthText(k),
            onDismiss = { keyMenu = null },
            actions = buildList {
                add(SheetAction("Test key", Icons.Outlined.Verified, onClick = { viewModel.testKey(ref, k.entry.id); keyMenu = null }))
                if (k.state.health != KeyHealth.OK) {
                    add(SheetAction("Use it again now", Icons.Outlined.Refresh, onClick = { viewModel.resetKeyHealth(k.entry.id); keyMenu = null }))
                }
                if (index > 0) add(SheetAction("Move up", Icons.Outlined.KeyboardArrowUp, onClick = { viewModel.moveKey(ref, k.entry.id, -1); keyMenu = null }))
                if (index in 0 until keys.lastIndex) add(SheetAction("Move down", Icons.Outlined.KeyboardArrowDown, onClick = { viewModel.moveKey(ref, k.entry.id, 1); keyMenu = null }))
                add(
                    SheetAction(
                        if (k.entry.enabled) "Turn off" else "Turn on", Icons.Outlined.PowerSettingsNew,
                        onClick = { viewModel.setKeyEnabled(ref, k.entry.id, !k.entry.enabled); keyMenu = null }
                    )
                )
                add(SheetAction("Delete key", Icons.Outlined.DeleteOutline, destructive = true, onClick = { viewModel.removeKey(ref, k.entry.id); keyMenu = null }))
            }
        )
    }

    if (editing && custom != null) {
        EditProviderSheet(custom, onDismiss = { editing = false }, onSave = { viewModel.updateCustomProvider(it); editing = false })
    }
    if (addingModel && custom != null) {
        AddModelSheet(
            defaultTools = custom.supportsTools,
            onDismiss = { addingModel = false },
            onAdd = { id, name, ctx, tools, thinking ->
                viewModel.addManualModel(custom.id, id, name, ctx, tools, thinking)
                addingModel = false
            }
        )
    }
    if (confirmDelete && custom != null) {
        FormSheet(
            title = "Remove ${custom.name}?",
            icon = Icons.Outlined.DeleteOutline,
            onDismiss = { confirmDelete = false },
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteCustomProvider(custom.id)
                onDismiss()
            }
        ) {
            Text(
                "Its keys and its ${models.size} models are removed from this device. Chats keep their history.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun Heading(title: String, trailingText: String?, actions: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmallEmphasized, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trailingText?.let {
            Spacer(Modifier.size(Space.sm))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        actions?.invoke()
    }
}

private fun healthText(k: KeyUi): String = when (k.state.health) {
    KeyHealth.OK -> if (k.entry.enabled) "Active" else "Off"
    else -> k.state.health.label + " until " +
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(k.state.until))
}

@Composable
private fun KeyRow(k: KeyUi, testing: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bad = k.state.health != KeyHealth.OK
    Surface(onClick = onClick, shape = OroCorners.medium, color = scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Key, null, tint = if (bad) scheme.error else scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    k.entry.label.ifBlank { k.entry.masked },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = com.oro.app.ui.theme.CodeFontFamily),
                    color = if (k.entry.enabled) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    healthText(k),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bad) scheme.error else scheme.onSurfaceVariant
                )
            }
            if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.MoreVert, null, tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddKeyField(onAdd: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Paste an API key") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = OroCorners.medium
        )
        Button(onClick = { onAdd(draft); draft = "" }, enabled = draft.isNotBlank(), shape = OroCorners.pill) { Text("Add") }
    }
}

@Composable
private fun ModelToggleRow(model: AiModel, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(model.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                ModelIds.upstream(model.id) + " · " + com.oro.app.data.ai.ModelGroup.formatContext(model.contextLength) +
                    if (model.supportsToolCalls) "" else " · text tools",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(checked = model.isEnabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** "Header: value" lines <-> map. */
internal fun parseHeaders(text: String): Map<String, String> = text.lines()
    .mapNotNull { line ->
        val i = line.indexOf(':')
        if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
    }
    .filter { it.first.isNotEmpty() }
    .toMap()

internal fun formatHeaders(headers: Map<String, String>): String = headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }

/** Pick a preset (or start blank), then fill in the details. */
@Composable
fun AddProviderSheet(
    onDismiss: () -> Unit,
    onCreate: (ProviderPreset?, String, WireProtocol, String, String, Map<String, String>, Boolean, Int?) -> Unit
) {
    var presetId by rememberSaveable { mutableStateOf("openai") }
    val preset = ProviderPresets.byId(presetId)
    var name by rememberSaveable(presetId) { mutableStateOf(preset?.name?.takeUnless { it.startsWith("Custom") }.orEmpty()) }
    var protocol by rememberSaveable(presetId) { mutableStateOf(preset?.protocol ?: WireProtocol.OPENAI) }
    var url by rememberSaveable(presetId) { mutableStateOf(preset?.baseUrl.orEmpty()) }
    var key by rememberSaveable { mutableStateOf("") }
    var headers by rememberSaveable { mutableStateOf("") }
    var tools by rememberSaveable { mutableStateOf(true) }
    var maxTokens by rememberSaveable(presetId) { mutableStateOf(preset?.maxOutputTokens?.toString().orEmpty()) }

    FormSheet(
        title = "Add provider",
        icon = Icons.Outlined.Dns,
        onDismiss = onDismiss,
        confirmLabel = "Add and fetch models",
        confirmEnabled = url.startsWith("http"),
        onConfirm = {
            onCreate(preset, name, protocol, url, key, parseHeaders(headers), tools, maxTokens.toIntOrNull())
        }
    ) {
        Column(
            Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                items(ProviderPresets.all, key = { it.id }) { p ->
                    FilterChip(selected = p.id == presetId, onClick = { presetId = p.id }, label = { Text(p.name) })
                }
            }
            preset?.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, shape = OroCorners.medium)
            SegmentedPicker(
                label = "Protocol",
                options = WireProtocol.entries.map { PickerOption(it, it.label) },
                selected = protocol,
                onSelect = { protocol = it }
            )
            OutlinedTextField(
                url, { url = it.trim() }, Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                placeholder = { Text(if (protocol == WireProtocol.ANTHROPIC) "https://api.example.com/v1" else "https://api.example.com/v1") },
                supportingText = { Text(if (protocol == WireProtocol.ANTHROPIC) "Oro calls <base>/messages and <base>/models" else "Oro calls <base>/chat/completions and <base>/models") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = OroCorners.medium
            )
            OutlinedTextField(
                key, { key = it }, Modifier.fillMaxWidth(),
                label = { Text(if (preset?.needsKey == false) "API key (optional)" else "API key") },
                supportingText = { Text("You can add more keys later; Oro fails over between them.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = OroCorners.medium
            )
            OutlinedTextField(
                headers, { headers = it }, Modifier.fillMaxWidth(),
                label = { Text("Extra headers (optional)") },
                placeholder = { Text("HTTP-Referer: https://example.com") },
                minLines = 2,
                shape = OroCorners.medium
            )
            OutlinedTextField(
                maxTokens, { v -> maxTokens = v.filter { it.isDigit() }.take(7) }, Modifier.fillMaxWidth(),
                label = { Text(if (protocol == WireProtocol.ANTHROPIC) "Max output tokens" else "Max output tokens (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = OroCorners.medium
            )
            SwitchRow("Native tool calling", "Most modern models support it", tools) { tools = it }
        }
    }
}

@Composable
private fun EditProviderSheet(config: CustomProviderConfig, onDismiss: () -> Unit, onSave: (CustomProviderConfig) -> Unit) {
    var name by rememberSaveable { mutableStateOf(config.name) }
    var url by rememberSaveable { mutableStateOf(config.baseUrl) }
    var headers by rememberSaveable { mutableStateOf(formatHeaders(config.headers)) }
    var maxTokens by rememberSaveable { mutableStateOf(config.maxOutputTokens?.toString().orEmpty()) }
    var protocol by rememberSaveable { mutableStateOf(config.protocol) }
    FormSheet(
        title = "Edit ${config.name}",
        icon = Icons.Outlined.Edit,
        onDismiss = onDismiss,
        confirmLabel = "Save",
        confirmEnabled = name.isNotBlank() && url.startsWith("http"),
        onConfirm = {
            onSave(
                config.copy(
                    name = name.trim(), baseUrl = url.trim(), headers = parseHeaders(headers),
                    maxOutputTokens = maxTokens.toIntOrNull(), protocol = protocol
                )
            )
        }
    ) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, shape = OroCorners.medium)
            SegmentedPicker("Protocol", WireProtocol.entries.map { PickerOption(it, it.label) }, protocol, { protocol = it })
            OutlinedTextField(url, { url = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Base URL") }, singleLine = true, shape = OroCorners.medium)
            OutlinedTextField(headers, { headers = it }, Modifier.fillMaxWidth(), label = { Text("Extra headers") }, minLines = 2, shape = OroCorners.medium)
            OutlinedTextField(
                maxTokens, { v -> maxTokens = v.filter { it.isDigit() }.take(7) }, Modifier.fillMaxWidth(),
                label = { Text("Max output tokens") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = OroCorners.medium
            )
        }
    }
}

@Composable
private fun AddModelSheet(
    defaultTools: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, Boolean, Boolean) -> Unit
) {
    var id by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var ctx by rememberSaveable { mutableStateOf("128000") }
    var tools by rememberSaveable { mutableStateOf(defaultTools) }
    var thinking by rememberSaveable { mutableStateOf(false) }
    FormSheet(
        title = "Add model",
        icon = Icons.Outlined.CloudSync,
        onDismiss = onDismiss,
        confirmLabel = "Add",
        confirmEnabled = id.isNotBlank(),
        onConfirm = { onAdd(id.trim(), name.trim(), ctx.toIntOrNull()?.coerceAtLeast(1_024) ?: 128_000, tools, thinking) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            OutlinedTextField(id, { id = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Model id") }, placeholder = { Text("e.g. gpt-5.1-mini") }, singleLine = true, shape = OroCorners.medium)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Display name (optional)") }, singleLine = true, shape = OroCorners.medium)
            OutlinedTextField(
                ctx, { v -> ctx = v.filter { it.isDigit() }.take(8) }, Modifier.fillMaxWidth(),
                label = { Text("Context window (tokens)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = OroCorners.medium
            )
            SwitchRow("Tool calling", "Off: Orobi uses text tool calls", tools) { tools = it }
            SwitchRow("Reasoning model", "Shows its thinking separately", thinking) { thinking = it }
        }
    }
}
