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

@Composable
internal fun Heading(title: String, trailingText: String?, actions: (@Composable () -> Unit)? = null) {
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

internal fun healthText(k: KeyUi): String = when (k.state.health) {
    KeyHealth.OK -> if (k.entry.enabled) "Active" else "Off"
    else -> k.state.health.label + " until " +
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(k.state.until))
}

@Composable
internal fun KeyRow(k: KeyUi, testing: Boolean, onClick: () -> Unit) {
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
internal fun AddKeyField(onAdd: (String) -> Unit) {
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
internal fun ModelToggleRow(model: AiModel, onToggle: (Boolean) -> Unit) {
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
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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

@Composable
internal fun AddModelSheet(
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
