package com.oro.app.ui.screens.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.providers.CustomProviderConfig
import com.oro.app.data.ai.providers.KeyHealth
import com.oro.app.data.ai.providers.KeyRotation
import com.oro.app.data.ai.providers.ProviderPresets
import com.oro.app.data.ai.providers.WireProtocol
import com.oro.app.ui.components.expressive.ActionSheet
import com.oro.app.ui.components.expressive.FormSheet
import com.oro.app.ui.components.expressive.PickerOption
import com.oro.app.ui.components.expressive.SegmentedPicker
import com.oro.app.ui.components.expressive.SheetAction
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space

/** How the editor route names its subject: "new", "custom:<id>" or "builtin:<NAME>". */
object ProviderRoute {
    const val NEW = "new"
    fun of(ref: ProviderRef): String = when (ref) {
        is ProviderRef.Custom -> "custom:${ref.id}"
        is ProviderRef.BuiltIn -> "builtin:${ref.provider.name}"
    }
    fun parse(arg: String?): ProviderRef? = when {
        arg == null || arg == NEW -> null
        arg.startsWith("custom:") -> ProviderRef.Custom(arg.removePrefix("custom:"))
        arg.startsWith("builtin:") -> runCatching { ProviderRef.BuiltIn(AiProvider.valueOf(arg.removePrefix("builtin:"))) }.getOrNull()
        else -> null
    }
}

/**
 * Add or edit one provider, as a full screen.
 *
 * A screen rather than a sheet: a provider has a form, a list of keys and a
 * catalogue of models, which is too much for a sheet that fights the keyboard
 * and has no room for a fixed Save. Adding asks only what a custom endpoint
 * needs -- protocol, URL, key -- because the well-known vendors already ship
 * in the list.
 */
@Composable
fun ProviderEditorScreen(
    viewModel: ProvidersViewModel,
    route: String?,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val models by viewModel.customModels.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uri = LocalUriHandler.current
    val ref = remember(route) { ProviderRoute.parse(route) }
    val custom = (ref as? ProviderRef.Custom)?.let { state.settings.customById(it.id) }
    val builtIn = (ref as? ProviderRef.BuiltIn)?.provider
    val isNew = ref == null

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(ref) { viewModel.openCustom((ref as? ProviderRef.Custom)?.id) }

    // Form state: seeded from the config once it has loaded.
    var protocol by rememberSaveable(custom?.id) { mutableStateOf(custom?.protocol ?: WireProtocol.OPENAI) }
    var name by rememberSaveable(custom?.id) { mutableStateOf(custom?.name.orEmpty()) }
    var url by rememberSaveable(custom?.id) { mutableStateOf(custom?.baseUrl.orEmpty()) }
    var key by rememberSaveable { mutableStateOf("") }
    var headers by rememberSaveable(custom?.id) { mutableStateOf(custom?.headers?.let(::formatHeaders).orEmpty()) }
    var maxTokens by rememberSaveable(custom?.id) { mutableStateOf(custom?.maxOutputTokens?.toString().orEmpty()) }
    var tools by rememberSaveable(custom?.id) { mutableStateOf(custom?.supportsTools ?: true) }
    var keyMenu by remember { mutableStateOf<KeyUi?>(null) }
    var addingModel by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val title = when {
        isNew -> "Add provider"
        custom != null -> custom.name
        else -> builtIn?.displayName ?: "Provider"
    }
    val keyUrl = custom?.presetId?.let { ProviderPresets.byId(it)?.keyUrl } ?: builtIn?.apiKeyUrl.orEmpty()
    val formValid = name.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))
    val dirty = custom != null && (
        name != custom.name || url != custom.baseUrl || protocol != custom.protocol ||
            headers != formatHeaders(custom.headers) || maxTokens != (custom.maxOutputTokens?.toString().orEmpty()) ||
            tools != custom.supportsTools
        )

    fun save() {
        if (isNew) {
            viewModel.addCustomProvider(null, name, protocol, url, key, parseHeaders(headers), tools, maxTokens.toIntOrNull())
            onBack()
        } else if (custom != null) {
            viewModel.updateCustomProvider(
                custom.copy(
                    name = name.trim(), baseUrl = url.trim(), protocol = protocol, headers = parseHeaders(headers),
                    maxOutputTokens = maxTokens.toIntOrNull(), supportsTools = tools
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (custom != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Remove provider") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // Fixed Save, above the keyboard: the reason the editor is a screen.
            if (isNew || dirty) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                    Button(
                        onClick = ::save,
                        enabled = formValid,
                        shape = OroCorners.pill,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Space.lg)
                    ) { Text(if (isNew) "Add provider" else "Save changes", maxLines = 1) }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            if (isNew || custom != null) {
                item(key = "form") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        SegmentedPicker(
                            label = "API protocol",
                            options = WireProtocol.entries.map { PickerOption(it, it.label) },
                            selected = protocol,
                            onSelect = { protocol = it }
                        )
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, shape = OroCorners.medium)
                        OutlinedTextField(
                            url, { url = it.trim() }, Modifier.fillMaxWidth(),
                            label = { Text("Base URL") },
                            placeholder = { Text("https://api.example.com/v1") },
                            supportingText = {
                                Text(if (protocol == WireProtocol.ANTHROPIC) "Calls <base>/messages and <base>/models" else "Calls <base>/chat/completions and <base>/models")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = OroCorners.medium
                        )
                        if (isNew) {
                            OutlinedTextField(
                                key, { key = it }, Modifier.fillMaxWidth(),
                                label = { Text("API key (optional)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = OroCorners.medium
                            )
                        }
                        OutlinedTextField(
                            headers, { headers = it }, Modifier.fillMaxWidth(),
                            label = { Text("Extra headers (optional)") },
                            placeholder = { Text("Header-Name: value") },
                            minLines = 2,
                            shape = OroCorners.medium
                        )
                        OutlinedTextField(
                            maxTokens, { v -> maxTokens = v.filter { it.isDigit() }.take(7) }, Modifier.fillMaxWidth(),
                            label = { Text("Max output tokens (optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = OroCorners.medium
                        )
                        SwitchRow("Native tool calling", "Off for chat-only models", tools) { tools = it }
                    }
                }
            }

            if (!isNew) {
                val keys = ref?.let { state.keysOf(it) }.orEmpty()
                item(key = "keys-h") {
                    Heading("API keys", if (keys.size > 1) "Tried in this order" else null) {
                        if (keyUrl.isNotBlank()) {
                            TextButton(onClick = { runCatching { uri.openUri(keyUrl) } }) {
                                Text("Get a key", maxLines = 1)
                                Spacer(Modifier.width(Space.xs))
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
                if (builtIn != null) {
                    item(key = "note") {
                        Text(builtIn.keyNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(keys, key = { "k-" + it.entry.id }) { k ->
                    KeyRow(k, testing = "test:${k.entry.id}" in state.busy, onClick = { keyMenu = k })
                }
                item(key = "keys-add") { AddKeyField(onAdd = { k -> ref?.let { viewModel.addKey(it, k) } }) }
                if (keys.size > 1 && ref != null) {
                    item(key = "rotation") {
                        SegmentedPicker(
                            label = "When several keys work",
                            options = KeyRotation.entries.map { PickerOption(it, it.label) },
                            selected = state.rotationOf(ref),
                            onSelect = { viewModel.setRotation(ref, it) }
                        )
                    }
                }
            }

            if (custom != null) {
                val busy = "models:${custom.id}" in state.busy
                item(key = "models-h") {
                    Heading("Models", "${models.count { it.isEnabled }} of ${models.size} on") {
                        IconButton(onClick = { viewModel.refreshModels(custom.id) }, enabled = !busy) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Refresh, contentDescription = "Fetch models")
                        }
                        IconButton(onClick = { addingModel = true }) { Icon(Icons.Outlined.Add, contentDescription = "Add model") }
                    }
                }
                if (models.isEmpty()) {
                    item(key = "models-empty") {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Text(
                                "Fetch the catalogue (new models start switched off; turn on the ones you want), or add one by its id.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = { viewModel.refreshModels(custom.id) }, enabled = !busy, shape = OroCorners.pill) {
                                Text("Fetch models", maxLines = 1)
                            }
                        }
                    }
                }
                items(models, key = { "m-" + it.id }, contentType = { "model" }) { m ->
                    ModelToggleRow(m, onToggle = { viewModel.setModelEnabled(m, it) })
                }
                item(key = "enabled") {
                    SwitchRow("Enabled", "Show this provider's models in the picker", custom.enabled) {
                        viewModel.updateCustomProvider(custom.copy(enabled = it))
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.size(Space.xxl)) }
        }
    }

    keyMenu?.let { k ->
        val keys = ref?.let { state.keysOf(it) }.orEmpty()
        val index = keys.indexOfFirst { it.entry.id == k.entry.id }
        val r = ref ?: return@let
        ActionSheet(
            title = k.entry.label.ifBlank { k.entry.masked },
            subtitle = healthText(k),
            onDismiss = { keyMenu = null },
            actions = buildList {
                add(SheetAction("Test key", Icons.Outlined.Verified, onClick = { viewModel.testKey(r, k.entry.id); keyMenu = null }))
                if (k.state.health != KeyHealth.OK) add(SheetAction("Use it again now", Icons.Outlined.Refresh, onClick = { viewModel.resetKeyHealth(k.entry.id); keyMenu = null }))
                if (index > 0) add(SheetAction("Move up", Icons.Outlined.KeyboardArrowUp, onClick = { viewModel.moveKey(r, k.entry.id, -1); keyMenu = null }))
                if (index in 0 until keys.lastIndex) add(SheetAction("Move down", Icons.Outlined.KeyboardArrowDown, onClick = { viewModel.moveKey(r, k.entry.id, 1); keyMenu = null }))
                add(SheetAction(if (k.entry.enabled) "Turn off" else "Turn on", Icons.Outlined.PowerSettingsNew, onClick = { viewModel.setKeyEnabled(r, k.entry.id, !k.entry.enabled); keyMenu = null }))
                add(SheetAction("Delete key", Icons.Outlined.DeleteOutline, destructive = true, onClick = { viewModel.removeKey(r, k.entry.id); keyMenu = null }))
            }
        )
    }
    if (addingModel && custom != null) {
        AddModelSheet(
            defaultTools = custom.supportsTools,
            onDismiss = { addingModel = false },
            onAdd = { id, n, ctx, t, thinking ->
                viewModel.addManualModel(custom.id, id, n, ctx, t, thinking)
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
                onBack()
            }
        ) {
            Text("Its keys and models are removed from this device. Chats keep their history.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
