package com.oro.app.ui.components.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space

/**
 * Why the last run stopped without finishing, and what the user can do.
 *
 * Every run that does not end in an answer ends here -- provider errors after
 * retries, an empty reply, the step limit -- so a run can no longer just stop
 * with nothing on screen.
 */
@Immutable
data class RunError(
    val message: String,
    /** Label for the action that resumes the run from where it stopped. */
    val actionLabel: String = "Retry",
    /** Informational (step limit) rather than a failure. */
    val isNotice: Boolean = false
)

@Composable
fun RunErrorCard(
    error: RunError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (error.isNotice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Icon(
                    imageVector = if (error.isNotice) Icons.Outlined.Info else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                Button(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(error.actionLabel, modifier = Modifier.padding(start = Space.xs))
                }
            }
        }
    }
}
