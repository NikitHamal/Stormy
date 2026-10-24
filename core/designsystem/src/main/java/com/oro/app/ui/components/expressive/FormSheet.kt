package com.oro.app.ui.components.expressive

import com.oro.app.ui.theme.ExpressiveShapes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/**
 * A bottom sheet that holds a short form.
 *
 * Replaces [androidx.compose.material3.AlertDialog] for create, rename, clone
 * and delete. A dialog is the wrong container for a form on a phone: it floats
 * centred, so the keyboard shoves it upward and frequently covers the very
 * field being typed into, and its action buttons end up in the middle of the
 * screen rather than within thumb reach. A sheet is anchored to the bottom
 * edge, grows upward as the keyboard appears, and keeps its actions where the
 * thumb already is.
 *
 * The header carries an icon in an Expressive silhouette, so a sheet is
 * identifiable before its title is read -- useful when several of them share
 * the same shape and position.
 */
@Composable
fun FormSheet(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    destructive: Boolean = false,
    cancelLabel: String = "Cancel",
    content: @Composable () -> Unit
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
                // imePadding, not a scroll: the sheet lifts with the keyboard
                // so the focused field stays visible, which is the whole
                // reason this is not a dialog.
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Space.xl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(Sizes.avatarLarge),
                    shape = if (destructive) {
                        ExpressiveShapes.verySunny()
                    } else {
                        ExpressiveShapes.clover4()
                    },
                    color = if (destructive) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.icon),
                            tint = if (destructive) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }

                Spacer(Modifier.width(Space.md))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(Sizes.icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(Space.lg))

            content()

            Spacer(Modifier.height(Space.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                // Cancel first and lower-emphasis, so the irreversible choice
                // is neither under the thumb's resting position nor the one
                // that looks expected.
                // A long confirm label gets the room it needs instead of
                // wrapping onto two lines inside a pill.
                val confirmWeight = if (confirmLabel.length > 12) 1.7f else 1f
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(cancelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier.weight(confirmWeight),
                    shape = MaterialTheme.shapes.large,
                    colors = if (destructive) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    // The spinner lives in the button rather than over the
                    // sheet: the work was started by this control, so that is
                    // where its progress belongs, and the form stays readable.
                    if (confirmLoading) {
                        ExpressiveLoadingIndicator(
                            modifier = Modifier.size(Sizes.iconSmall),
                            color = LocalContentColor.current
                        )
                        Spacer(Modifier.width(Space.sm))
                    }
                    Text(
                        confirmLabel,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(Space.lg))
        }
    }
}
