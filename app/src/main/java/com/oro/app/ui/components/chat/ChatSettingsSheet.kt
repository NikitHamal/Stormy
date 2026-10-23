package com.oro.app.ui.components.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import com.oro.app.data.ai.AiModel
import com.oro.app.ui.components.expressive.PickerOption
import com.oro.app.ui.components.expressive.SegmentedPicker
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/**
 * Per-conversation settings, in one place.
 *
 * These lived as chips crowding the input bar, which is the wrong home for
 * them: the bar is where you type, and every control on it competes with the
 * text field for a row that is already short. Settings that are set once and
 * then left alone belong behind a tap.
 *
 * Options that do not apply to the selected model are *hidden*, not disabled.
 * A permanently greyed row teaches the user to ignore that part of the sheet,
 * and the absence already says the model cannot do it.
 *
 * Agent mode is offered for every model: one without native function calling
 * runs tools through the text protocol, so "this model cannot use tools" was
 * never true -- and it greyed out agent mode on Mercury 3, the default.
 */
@Composable
fun ChatSettingsSheet(
    agentMode: Boolean,
    onAgentModeChange: (Boolean) -> Unit,
    /** Saved reasoning level per model id (see ReasoningPicker). */
    reasoningLevels: Map<String, String>,
    onReasoningLevelChange: (modelId: String, level: String) -> Unit,
    currentModel: AiModel?,
    onDismiss: () -> Unit,
    autonomy: com.oro.app.data.ai.agent.AutonomyMode = com.oro.app.data.ai.agent.AutonomyMode.DEFAULT,
    onAutonomyChange: (com.oro.app.data.ai.agent.AutonomyMode) -> Unit = {},
    richUi: Boolean = true,
    onRichUiChange: (Boolean) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.xl)
        ) {
            Text(
                text = "Chat settings",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(Space.lg))

            SettingToggle(
                icon = Icons.Outlined.Handyman,
                title = "Agent mode",
                // Describes the consequence, not the feature. "Enable
                // autonomous coding workflow" tells the user nothing about what
                // will happen to their files.
                subtitle = "Orobi reads and edits your files",
                checked = agentMode,
                enabled = true,
                onCheckedChange = onAgentModeChange
            )

            // The leash. Only meaningful when Orobi can act at all.
            if (agentMode) {
                Spacer(Modifier.height(Space.lg))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = OroCorners.large,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(Modifier.padding(Space.md)) {
                        SegmentedPicker(
                            label = "Autonomy",
                            options = com.oro.app.data.ai.agent.AutonomyMode.entries.map { PickerOption(it, it.label) },
                            selected = autonomy,
                            onSelect = onAutonomyChange
                        )
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            text = when (autonomy) {
                                com.oro.app.data.ai.agent.AutonomyMode.LEASHED ->
                                    "Asks before every change"
                                com.oro.app.data.ai.agent.AutonomyMode.UNLEASHED ->
                                    "Works on its own, asks before risky steps"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }


            Spacer(Modifier.height(Space.md))
            SettingToggle(
                icon = Icons.Outlined.AutoAwesome,
                title = "Rich answers",
                subtitle = "Cards, tables and charts in replies",
                checked = richUi,
                enabled = true,
                onCheckedChange = onRichUiChange
            )

            // Only for models whose provider has a reasoning control; see the note on the file.
            currentModel?.takeIf { it.supportsReasoningEffort }?.let { model ->
                Spacer(Modifier.height(Space.lg))
                ReasoningPicker(model = model, levels = reasoningLevels, onSelect = onReasoningLevelChange)
            }

            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun SettingToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row toggles, not just the switch: a switch alone is a
            // small target, and the label is what the user is looking at.
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(Sizes.iconLarge)
        )
        Spacer(Modifier.width(Space.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLargeEmphasized,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Space.md))
        Switch(
            checked = checked,
            // Null: the row owns the toggle semantics, and a switch with its
            // own handler would be announced as a second, separate control.
            onCheckedChange = null,
            enabled = enabled
        )
    }
}
