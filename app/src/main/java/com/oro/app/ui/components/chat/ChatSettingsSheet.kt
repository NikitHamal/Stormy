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
import androidx.compose.material.icons.outlined.Info
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
import com.oro.app.data.ai.ReasoningEffort
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
 * and the absence already says the model cannot do it -- there is a line at
 * the bottom explaining why the set changes.
 */
@Composable
fun ChatSettingsSheet(
    agentMode: Boolean,
    onAgentModeChange: (Boolean) -> Unit,
    reasoningEffort: ReasoningEffort,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
    currentModel: AiModel?,
    onDismiss: () -> Unit,
    autonomy: com.oro.app.data.ai.agent.AutonomyMode = com.oro.app.data.ai.agent.AutonomyMode.DEFAULT,
    onAutonomyChange: (com.oro.app.data.ai.agent.AutonomyMode) -> Unit = {},
    selfCheck: Boolean = true,
    onSelfCheckChange: (Boolean) -> Unit = {},
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
                subtitle = if (currentModel?.supportsToolCalls == false) {
                    "This model cannot use tools"
                } else {
                    "Let the assistant read and edit files on its own"
                },
                checked = agentMode,
                enabled = currentModel?.supportsToolCalls != false,
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
                                    "Orobi shows each change (write, edit, delete, move, MCP action) and waits for your OK."
                                com.oro.app.data.ai.agent.AutonomyMode.UNLEASHED ->
                                    "Orobi works on its own. It still asks before deleting folders, touching secrets " +
                                        "or running risky MCP actions. Checkpoints let you undo any run."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (agentMode) {
                Spacer(Modifier.height(Space.md))
                SettingToggle(
                    icon = Icons.Outlined.FactCheck,
                    title = "Self-check",
                    subtitle = "Orobi renders and checks what it changed, and fixes problems before finishing",
                    checked = selfCheck,
                    enabled = true,
                    onCheckedChange = onSelfCheckChange
                )
            }

            Spacer(Modifier.height(Space.md))
            SettingToggle(
                icon = Icons.Outlined.AutoAwesome,
                title = "Rich answers",
                subtitle = "Orobi can answer with interactive cards, tables, charts and choices",
                checked = richUi,
                enabled = true,
                onCheckedChange = onRichUiChange
            )

            // Only for models that expose a budget; see the note on the file.
            if (currentModel?.supportsReasoningEffort == true) {
                Spacer(Modifier.height(Space.lg))
                SegmentedPicker(
                    label = "Reasoning effort",
                    options = ReasoningEffort.entries.map { PickerOption(it, it.label) },
                    selected = reasoningEffort,
                    onSelect = onReasoningEffortChange
                )
            }

            Spacer(Modifier.height(Space.lg))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = OroCorners.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(Space.md),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        text = "Which options appear depends on the model you have selected.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
