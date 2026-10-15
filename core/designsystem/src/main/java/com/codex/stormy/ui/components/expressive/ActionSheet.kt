package com.codex.stormy.ui.components.expressive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.codex.stormy.ui.theme.CodeXCorners
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Space

/** One row in an [ActionSheet]. */
@Immutable
data class SheetAction(
    val label: String,
    val icon: ImageVector,
    /**
     * Marks an action that destroys something. Rendered in the error colour
     * and placed last, because a destructive row sitting between two ordinary
     * ones gets hit by accident.
     */
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * A bottom sheet of actions, replacing a dropdown menu.
 *
 * A `DropdownMenu` anchored to a small icon in a list row is the wrong control
 * on a phone: it opens wherever the row happens to be, so its position moves
 * with the scroll, its rows are small, and near the bottom of the screen it
 * flips above the anchor and lands under the thumb that just tapped. A sheet
 * always arrives from the same edge, at the same size, with full-width targets.
 *
 * An explicit Cancel row is included. Dismissing by scrim or back gesture works
 * too, but when the list ends in a destructive action the user needs a visible,
 * safe way out that is not "tap the thing next to Delete".
 */
@Composable
fun ActionSheet(
    title: String?,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "Cancel"
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.md)
        ) {
            title?.let {
                Text(
                    text = it.uppercase(),
                    // Letterspaced small caps, as a section label rather than a
                    // heading: it names the group without competing with the
                    // actions, which are what the user came here to read.
                    style = MaterialTheme.typography.labelSmallEmphasized.copy(
                        letterSpacing = 1.4.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = Space.lg,
                        top = Space.sm,
                        bottom = Space.md
                    )
                )
            }

            // Destructive actions sort last regardless of declaration order, so
            // Delete is never adjacent to the row above it in the list the
            // caller happened to write.
            val ordered = actions.sortedBy { it.destructive }
            ordered.forEach { action ->
                ActionRow(action = action, onDismiss = onDismiss)
            }

            Spacer(Modifier.height(Space.md))

            Surface(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = CodeXCorners.xLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = cancelLabel,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.lg)
                )
            }

            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun ActionRow(action: SheetAction, onDismiss: () -> Unit) {
    val contentColor = when {
        !action.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        action.destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = {
            // Dismiss first so the sheet's exit runs while the action's own
            // effect starts, rather than after it -- otherwise a dialog opened
            // by the action appears behind a sheet that is still closing.
            onDismiss()
            action.onClick()
        },
        enabled = action.enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = CodeXCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = action.icon,
                // The label beside it already names the action; describing the
                // icon too makes every row announce itself twice.
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconLarge)
            )
            Spacer(Modifier.width(Space.lg))
            Text(
                text = action.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
