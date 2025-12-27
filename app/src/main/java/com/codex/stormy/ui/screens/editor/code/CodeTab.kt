package com.codex.stormy.ui.screens.editor.code

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codex.stormy.R
import com.codex.stormy.domain.model.FileTreeNode
import com.codex.stormy.ui.screens.editor.assets.ImageMetadata
import com.codex.stormy.ui.screens.editor.assets.ImageOptimizer
import com.codex.stormy.ui.theme.CodeXTheme
import com.codex.stormy.ui.theme.PoppinsFontFamily
import com.codex.stormy.utils.FileUtils
import java.io.File

// Image extensions that should show preview instead of code editor
// Note: SVG is excluded since it can be edited as text/code
private val IMAGE_PREVIEW_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "ico"
)

@Composable
fun CodeTab(
    currentFile: FileTreeNode.FileNode?,
    fileContent: String,
    isModified: Boolean,
    lineNumbers: Boolean,
    wordWrap: Boolean,
    fontSize: Float,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onAiEditRequest: ((AiCodeEditRequest) -> Unit)? = null
) {
    val context = LocalContext.current
    val extendedColors = CodeXTheme.extendedColors

    if (currentFile == null) {
        EmptyEditorState(modifier = Modifier.fillMaxSize())
    } else {
        // Check if this is an image file that should show preview
        val isImageFile = currentFile.extension.lowercase() in IMAGE_PREVIEW_EXTENSIONS

        if (isImageFile) {
            // Show image preview for image files
            ImagePreviewContent(
                file = currentFile,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Show code editor for text/code files (including SVG)
            Box(modifier = Modifier.fillMaxSize()) {
                // Sora Code Editor - fills all available space with no extra padding
                SoraCodeEditorView(
                    content = fileContent,
                    onContentChange = onContentChange,
                    fileExtension = currentFile.extension,
                    fileName = currentFile.name,
                    filePath = currentFile.path,
                    showLineNumbers = lineNumbers,
                    wordWrap = wordWrap,
                    fontSize = fontSize,
                    onAiEditRequest = onAiEditRequest,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(extendedColors.editorBackground)
                )

                // Save FAB - only shown when file is modified
                if (isModified) {
                    SmallFloatingActionButton(
                        onClick = onSave,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = context.getString(R.string.action_save)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyEditorState(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Code,
            contentDescription = null,
            modifier = Modifier.padding(16.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )

        Text(
            text = context.getString(R.string.editor_no_file),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Image preview content for displaying image files in the editor
 */
@Composable
private fun ImagePreviewContent(
    file: FileTreeNode.FileNode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Load image metadata
    LaunchedEffect(file.path) {
        isLoading = true
        metadata = ImageOptimizer.getImageMetadata(context, File(file.path))
        isLoading = false
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // File name header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Image Preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Image preview
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(file.path))
                    .crossfade(true)
                    .build(),
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Image metadata card
        if (!isLoading && metadata != null) {
            ImageMetadataCard(
                metadata = metadata!!,
                file = file
            )
        }
    }
}

/**
 * Card displaying image metadata
 */
@Composable
private fun ImageMetadataCard(
    metadata: ImageMetadata,
    file: FileTreeNode.FileNode
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Image Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ImageMetadataItem(
                    label = "Dimensions",
                    value = "${metadata.width} × ${metadata.height}",
                    modifier = Modifier.weight(1f)
                )
                ImageMetadataItem(
                    label = "File Size",
                    value = FileUtils.formatFileSize(file.size),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ImageMetadataItem(
                    label = "Format",
                    value = file.extension.uppercase(),
                    modifier = Modifier.weight(1f)
                )
                ImageMetadataItem(
                    label = "Alpha Channel",
                    value = if (metadata.hasAlpha) "Yes" else "No",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Individual metadata item
 */
@Composable
private fun ImageMetadataItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Medium
        )
    }
}
