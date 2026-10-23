package com.oro.app.ui.screens.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oro.app.ui.theme.OroCorners

/**
 * The strip under the preview's top bar: what the page is laid out as
 * (phone, tablet or desktop, turned or not, and at how many CSS pixels),
 * light or dark, zoom to fit, and the page's error count, which opens the
 * console. [darkPage] is null where WebView can't be told (before Android 10).
 */
@Composable
internal fun PreviewDeviceBar(
    device: PreviewDevice,
    landscape: Boolean,
    layoutWidth: Int?,
    errorCount: Int,
    onDeviceChange: (PreviewDevice) -> Unit,
    onRotate: () -> Unit,
    onFit: () -> Unit,
    onShowErrors: () -> Unit,
    modifier: Modifier = Modifier,
    darkPage: Boolean? = null,
    onToggleDark: () -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(OroCorners.pill)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(3.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    PreviewDevice.entries.forEach { option ->
                        val selected = option == device
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 30.dp)
                                .clip(OroCorners.pill)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .selectable(selected = selected, role = Role.Tab, onClick = { onDeviceChange(option) }),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = option.icon(),
                                contentDescription = option.label,
                                modifier = Modifier.size(18.dp),
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (layoutWidth != null) {
                    Text(
                        text = "$layoutWidth px",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.weight(1f))

                if (errorCount > 0) {
                    val label = if (errorCount == 1) "1 error" else "$errorCount errors"
                    Row(
                        modifier = Modifier
                            .clip(OroCorners.pill)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .clickable(onClickLabel = "Show console", onClick = onShowErrors)
                            .semantics(mergeDescendants = true) { contentDescription = label }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (errorCount > 99) "99+" else errorCount.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 1
                        )
                    }
                }

                if (darkPage != null) {
                    IconButton(onClick = onToggleDark, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (darkPage) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (darkPage) "Show the page light" else "Show the page dark",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRotate, enabled = device.canRotate, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.ScreenRotation,
                        contentDescription = if (landscape) "Turn upright" else "Turn sideways",
                        modifier = Modifier.size(20.dp),
                        tint = when {
                            !device.canRotate -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            landscape -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onFit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.FitScreen,
                        contentDescription = "Zoom to fit",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }
    }
}

private fun PreviewDevice.icon(): ImageVector = when (this) {
    PreviewDevice.PHONE -> Icons.Outlined.PhoneAndroid
    PreviewDevice.TABLET -> Icons.Outlined.TabletAndroid
    PreviewDevice.DESKTOP -> Icons.Outlined.DesktopWindows
}

/** Shown over the preview when the page itself didn't load. */
@Composable
internal fun PreviewLoadError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            FilledTonalButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
