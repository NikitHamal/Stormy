package com.oro.app.ui.screens.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.browser.BrowserAgent
import com.oro.app.browser.WebVault
import com.oro.app.ui.components.expressive.PickerOption
import com.oro.app.ui.components.expressive.SegmentedPicker
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/** No limit, or a cap on actions per task. */
internal val LimitChoices = listOf(0, 30, 60, 120)

/** The picker's choice for any stored limit. */
internal fun nearestLimit(maxActions: Int): Int =
    if (maxActions <= 0) 0 else LimitChoices.filter { it > 0 }.minBy { kotlin.math.abs(it - maxActions) }

/** One switch in the browser agent's settings. */
internal data class BrowserToggle(
    val icon: ImageVector,
    val title: String,
    val checked: Boolean,
    val onChange: (Boolean) -> Unit,
)

/**
 * The browser agent's settings and the login vault, opened from the composer's
 * tune button: the same sheet and rows as the editor's chat settings. The
 * vault section lists saved logins (never their passwords) and adds new ones;
 * use_credential signs in with them after asking.
 */
@Composable
internal fun BrowserSettingsSheet(
    settings: BrowserAgent.Settings,
    onChange: ((BrowserAgent.Settings) -> BrowserAgent.Settings) -> Unit,
    credentials: List<WebVault.Visible>,
    onSaveCredential: (label: String, domain: String, username: String, password: String) -> Unit,
    onDeleteCredential: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val toggles = listOf(
        BrowserToggle(
            icon = Icons.Outlined.PrivacyTip,
            title = "Ask before sensitive actions",
            checked = settings.confirmSensitive,
            onChange = { v -> onChange { it.copy(confirmSensitive = v) } },
        ),
        BrowserToggle(
            icon = Icons.Outlined.PlayCircle,
            title = "Return to Oro when a task ends",
            checked = settings.returnToOro,
            onChange = { v -> onChange { it.copy(returnToOro = v) } },
        ),
        BrowserToggle(
            icon = Icons.Outlined.Screenshot,
            title = "Screenshots for vision models",
            checked = settings.screenshots,
            onChange = { v -> onChange { it.copy(screenshots = v) } },
        ),
        BrowserToggle(
            icon = Icons.Outlined.DesktopWindows,
            title = "Desktop site mode",
            checked = settings.desktopMode,
            onChange = { v -> onChange { it.copy(desktopMode = v) } },
        ),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Space.xl)
        ) {
            Text(
                text = "Browser settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Space.xl, bottom = Space.sm)
            )
            toggles.forEach { toggle ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = toggle.checked,
                            role = Role.Switch,
                            onValueChange = toggle.onChange
                        )
                        .padding(horizontal = Space.xl, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = toggle.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                    Spacer(Modifier.width(Space.md))
                    Text(
                        text = toggle.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Switch(checked = toggle.checked, onCheckedChange = toggle.onChange)
                }
            }

            Spacer(Modifier.height(Space.lg))
            SegmentedPicker(
                label = "Action limit",
                options = LimitChoices.map { PickerOption(it, if (it == 0) "∞" else it.toString()) },
                selected = nearestLimit(settings.maxActions),
                onSelect = { limit -> onChange { it.copy(maxActions = limit) } },
            )
            Spacer(Modifier.height(Space.md))

            HorizontalDivider(Modifier.padding(vertical = Space.md))

            // ------------------------------------------------- credentials
            Text(
                text = "Saved logins",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Space.xl, bottom = Space.xs)
            )
            Text(
                text = "Passwords are encrypted on this phone and never sent to a model. use_credential asks you before signing in anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.xl, end = Space.xl, bottom = Space.sm)
            )
            credentials.forEach { c ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.xl, vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(c.label.ifBlank { c.domain }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${c.domain} · ${c.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onDeleteCredential(c.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete ${c.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            var adding by remember { mutableStateOf(false) }
            if (!adding) {
                FilledTonalButton(
                    onClick = { adding = true },
                    modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.sm)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Spacer(Modifier.width(Space.xs))
                    Text("Add a login", maxLines = 1)
                }
            } else {
                AddCredentialForm(
                    onSave = { label, domain, username, password ->
                        onSaveCredential(label, domain, username, password)
                        adding = false
                    },
                    onCancel = { adding = false },
                )
            }
        }
    }
}

@Composable
private fun AddCredentialForm(onSave: (String, String, String, String) -> Unit, onCancel: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.padding(horizontal = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Name (e.g. GitHub)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = domain, onValueChange = { domain = it }, label = { Text("Site (e.g. github.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username or email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            TextButton(onClick = onCancel) { Text("Cancel", maxLines = 1) }
            FilledTonalButton(
                onClick = { onSave(label, domain, username, password) },
                enabled = label.isNotBlank() && domain.isNotBlank() && password.isNotEmpty()
            ) { Text("Save login", maxLines = 1) }
        }
        Spacer(Modifier.height(Space.sm))
    }
}
