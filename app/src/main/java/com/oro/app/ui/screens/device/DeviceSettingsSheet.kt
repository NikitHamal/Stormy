package com.oro.app.ui.screens.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.oro.app.device.DeviceAgent
import com.oro.app.ui.components.expressive.PickerOption
import com.oro.app.ui.components.expressive.SegmentedPicker
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/** One switch in the device agent's settings. */
internal data class DeviceToggle(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val checked: Boolean,
    val onChange: (Boolean) -> Unit,
)

/**
 * The device agent's settings, opened from the composer's tune button: the
 * same sheet, rows and picker as the editor's chat settings.
 */
@Composable
internal fun DeviceSettingsSheet(
    settings: DeviceAgent.Settings,
    onChange: ((DeviceAgent.Settings) -> DeviceAgent.Settings) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val toggles = listOf(
        DeviceToggle(
            icon = Icons.Outlined.PrivacyTip,
            title = "Ask before sensitive actions",
            subtitle = "Sending, paying, deleting and passwords wait for your OK",
            checked = settings.confirmSensitive,
            onChange = { v -> onChange { it.copy(confirmSensitive = v) } },
        ),
        DeviceToggle(
            icon = Icons.Outlined.TouchApp,
            title = "Show touches",
            subtitle = "A ring wherever Orobi taps or swipes",
            checked = settings.showTouches,
            onChange = { v -> onChange { it.copy(showTouches = v) } },
        ),
        DeviceToggle(
            icon = Icons.AutoMirrored.Outlined.KeyboardReturn,
            title = "Return to Oro when done",
            subtitle = "Come back here when the task ends",
            checked = settings.returnToOro,
            onChange = { v -> onChange { it.copy(returnToOro = v) } },
        ),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.xl),
        ) {
            Text(
                text = "Device agent",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.lg))
            toggles.forEach { ToggleRow(it) }
            Spacer(Modifier.height(Space.lg))
            SegmentedPicker(
                label = "Action limit",
                options = LimitChoices.map { PickerOption(it, if (it == 0) "∞" else it.toString()) },
                selected = nearestLimit(settings.maxActions),
                onSelect = { limit -> onChange { it.copy(maxActions = limit) } },
            )
            Spacer(Modifier.height(Space.xl))
        }
    }
}

/** No limit, or a cap on actions per task. */
internal val LimitChoices = listOf(0, 30, 60, 120)

/** The picker's choice for any stored limit (older builds stored values in steps of 5). */
internal fun nearestLimit(maxActions: Int): Int =
    if (maxActions <= 0) 0 else LimitChoices.filter { it > 0 }.minBy { kotlin.math.abs(it - maxActions) }

/** The editor chat settings' row: the whole row toggles, the switch only shows the state. */
@Composable
private fun ToggleRow(toggle: DeviceToggle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = toggle.checked, role = Role.Switch, onValueChange = toggle.onChange)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = toggle.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(Sizes.iconLarge),
        )
        Spacer(Modifier.width(Space.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = toggle.title,
                style = MaterialTheme.typography.bodyLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = toggle.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Space.md))
        Switch(checked = toggle.checked, onCheckedChange = null)
    }
}
