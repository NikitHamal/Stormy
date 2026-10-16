package com.codex.stormy.ui.components.expressive

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.codex.stormy.ui.theme.CodeXCorners
import com.codex.stormy.ui.theme.ExpressiveShapes
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Space

/** One row in an [ActionSheet]. */
@Immutable
data class SheetAction(
    val label: String,
    val icon: ImageVector,
    /** One line on what the action does, when the label cannot carry it. */
    val description: String? = null,
    /** Tinted as a warning and placed last; the row is still a single tap. */
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * A list of actions on a subject, as a bottom sheet.
 *
 * Replaces the `AlertDialog`-as-context-menu pattern. A dialog is the wrong
 * container for a menu: it is centred, so a list of taps lands mid-screen
 * where the thumb is not; it dims and blocks the thing the actions apply to;
 * and it implies a decision with a confirm button when what is actually
 * offered is a set of independent choices, any one of which completes the
 * interaction.
 *
 * Each action is a card rather than a text row, because these lists mix
 * harmless and destructive operations -- a flat list of words gives the eye
 * nothing to catch on before it taps.
 *
 * Distinct from [FormSheet], which has one outcome and a confirm button.
 * Here, tapping a row *is* the confirmation, so there is only a Cancel.
 */
@Composable
fun ActionSheet(
    title: String?,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleIsMonospace: Boolean = false,
    cancelLabel: String = "Cancel",
    /**
     * Context shown above the actions -- the file being acted on, a warning,
     * a size. Optional because most menus need only their title.
     */
    content: (@Composable () -> Unit)? = null
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
                .padding(horizontal = Space.xl)
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLargeEmphasized.let { style ->
                        if (titleIsMonospace) {
                            style.copy(fontFamily = FontFamily.Monospace)
                        } else {
                            style
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            content?.let {
                Spacer(Modifier.height(Space.lg))
                it()
            }

            Spacer(Modifier.height(Space.lg))

            // Tight: with no fills the rows read as one list, and a gap
            // wide enough for cards would break it into fragments.
            Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                // Destructive last, unconditionally. A delete that happens to
                // sit where "switch branch" sat in the previous sheet is the
                // classic muscle-memory accident.
                // Dismiss is applied in the row rather than left to each
                // action, so a caller cannot forget it and leave the sheet
                // sitting open behind whatever its action opened.
                actions.sortedBy { it.destructive }.forEachIndexed { index, action ->
                    ActionRow(action = action, index = index, onDismiss = onDismiss)
                }
            }

            Spacer(Modifier.height(Space.lg))

            // A full-width tonal row, not a text button: when the list ends
            // in a destructive action the user needs a visible, safe way out
            // that is not "tap the thing next to Delete".
            Surface(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = CodeXCorners.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = cancelLabel,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = Space.md)
                )
            }

            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun ActionRow(action: SheetAction, index: Int, onDismiss: () -> Unit) {
    // Intent is carried by the content colour, not by a filled row. A tinted
    // block behind every action turns the sheet into a stack of cards and
    // makes the icon shapes -- the thing that actually distinguishes one
    // action from another here -- compete with their own backgrounds.
    val contentColor = when {
        !action.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        action.destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = {
            // Dismiss first so the sheet's exit runs while the action's own
            // effect starts, rather than after it -- otherwise a sheet opened
            // by the action appears behind one that is still closing.
            onDismiss()
            action.onClick()
        },
        enabled = action.enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = CodeXCorners.large,
        color = Color.Transparent,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The silhouette is the row's identity: a destructive action is
            // always the spiked one, and the rest take a stable shape from
            // their position, so a sheet the user has opened before looks the
            // same every time.
            Box(
                modifier = Modifier
                    .size(Sizes.avatar)
                    .border(
                        width = Sizes.hairline,
                        color = contentColor.copy(alpha = HAIRLINE_ALPHA),
                        shape = if (action.destructive) {
                            ExpressiveShapes.verySunny()
                        } else {
                            ExpressiveShapes.forIndex(index)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    // The label beside it already names the action; describing
                    // the icon too makes every row announce itself twice.
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
            }

            Spacer(Modifier.width(Space.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                action.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (action.destructive) {
                            contentColor.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The outline weight on an icon silhouette: present, not a border. */
private const val HAIRLINE_ALPHA = 0.45f
