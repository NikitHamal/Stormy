package com.codex.stormy.ui.components.inspector.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.ui.components.inspector.ImageChangeRequest
import com.codex.stormy.ui.components.inspector.InspectorElementData
import com.codex.stormy.ui.components.inspector.InspectorSection
import com.codex.stormy.ui.components.inspector.InspectorUtils
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * Image tab for the Visual Element Inspector.
 * Handles image source editing and accessibility settings.
 * Shows when an <img> element is selected.
 */
@Composable
fun ImageTab(
    element: InspectorElementData,
    onImageChange: ((ImageChangeRequest) -> Unit)?,
    onPickImage: (() -> Unit)?
) {
    val selector = InspectorUtils.buildSelector(element)
    val currentSrc = element.attributes["src"] ?: ""
    val altText = element.attributes["alt"] ?: ""

    var editedSrc by remember(currentSrc) { mutableStateOf(currentSrc) }
    var editedAlt by remember(altText) { mutableStateOf(altText) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Image source section
        InspectorSection(
            title = "Source",
            icon = Icons.Outlined.Image
        ) {
            ImageSourceEditor(
                currentSrc = currentSrc,
                editedSrc = editedSrc,
                onEditedSrcChange = { editedSrc = it },
                selector = selector,
                elementHtml = element.outerHTML,
                onImageChange = onImageChange
            )
        }

        // Pick from device section
        InspectorSection(
            title = "From Device",
            icon = Icons.Outlined.FolderOpen
        ) {
            PickFromDeviceButton(onPickImage = onPickImage)
        }

        // Alt text section
        InspectorSection(
            title = "Accessibility",
            icon = Icons.Outlined.TextFields
        ) {
            AltTextEditor(
                editedAlt = editedAlt,
                onEditedAltChange = { editedAlt = it }
            )
        }
    }
}

@Composable
private fun ImageSourceEditor(
    currentSrc: String,
    editedSrc: String,
    onEditedSrcChange: (String) -> Unit,
    selector: String,
    elementHtml: String,
    onImageChange: ((ImageChangeRequest) -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Current source preview (truncated)
        if (currentSrc.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = currentSrc.take(50) + if (currentSrc.length > 50) "..." else "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Source URL input
        Text(
            text = "Image URL",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        BasicTextField(
            value = editedSrc,
            onValueChange = onEditedSrcChange,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (editedSrc != currentSrc && onImageChange != null) {
                    onImageChange(ImageChangeRequest(
                        selector = selector,
                        oldSrc = currentSrc,
                        newSrc = editedSrc,
                        elementHtml = elementHtml
                    ))
                }
            }),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )

        // Apply button for source change
        if (editedSrc != currentSrc) {
            Surface(
                onClick = {
                    onImageChange?.invoke(ImageChangeRequest(
                        selector = selector,
                        oldSrc = currentSrc,
                        newSrc = editedSrc,
                        elementHtml = elementHtml
                    ))
                },
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Apply URL",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PickFromDeviceButton(onPickImage: (() -> Unit)?) {
    Surface(
        onClick = { onPickImage?.invoke() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Choose from Gallery",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

    Text(
        text = "Image will be copied to project assets folder",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun AltTextEditor(
    editedAlt: String,
    onEditedAltChange: (String) -> Unit
) {
    Text(
        text = "Alt Text",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    BasicTextField(
        value = editedAlt,
        onValueChange = onEditedAltChange,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontFamily = PoppinsFontFamily
        ),
        singleLine = false,
        maxLines = 3,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            // Alt text change would be handled via AI edit
        }),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )

    Text(
        text = "Describes the image for screen readers",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 2.dp)
    )
}
