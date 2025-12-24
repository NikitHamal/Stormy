package com.codex.stormy.ui.screens.editor.assets

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codex.stormy.ui.theme.PoppinsFontFamily
import com.codex.stormy.utils.FileUtils
import kotlinx.coroutines.launch
import java.io.File

/**
 * Dialog for viewing image details and optimization options
 */
@Composable
fun ImageDetailDialog(
    asset: AssetFile,
    projectPath: String,
    onDismiss: () -> Unit,
    onImageOptimized: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Image metadata state
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var isLoadingMetadata by remember { mutableStateOf(true) }

    // Optimization state
    var showOptimizePanel by remember { mutableStateOf(false) }
    var isOptimizing by remember { mutableStateOf(false) }
    var optimizationResult by remember { mutableStateOf<OptimizationResult?>(null) }

    // Optimization settings
    var selectedFormat by remember { mutableStateOf(ImageFormat.WEBP) }
    var quality by remember { mutableIntStateOf(85) }
    var maxWidth by remember { mutableIntStateOf(1920) }
    var maxHeight by remember { mutableIntStateOf(1920) }
    var maintainAspectRatio by remember { mutableStateOf(true) }
    var selectedUseCase by remember { mutableStateOf<ImageUseCase?>(null) }

    // Load metadata on dialog open
    LaunchedEffect(asset.path) {
        isLoadingMetadata = true
        metadata = ImageOptimizer.getImageMetadata(context, File(asset.path))
        isLoadingMetadata = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 32.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = asset.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = asset.relativePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    // Image preview
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(asset.path))
                                .crossfade(true)
                                .build(),
                            contentDescription = asset.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Metadata section
                    if (isLoadingMetadata) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Loading metadata...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        metadata?.let { meta ->
                            ImageMetadataSection(
                                metadata = meta,
                                fileSize = asset.size
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Optimization section toggle
                    Surface(
                        onClick = { showOptimizePanel = !showOptimizePanel },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Optimize Image",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Resize, compress, and convert format",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (showOptimizePanel) Icons.Outlined.Close else Icons.Outlined.Compress,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Optimization panel
                    AnimatedVisibility(
                        visible = showOptimizePanel,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            // Use case presets
                            Text(
                                text = "Quick Presets",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ImageUseCase.entries.take(4).forEach { useCase ->
                                    FilterChip(
                                        selected = selectedUseCase == useCase,
                                        onClick = {
                                            selectedUseCase = if (selectedUseCase == useCase) null else useCase
                                            if (selectedUseCase != null) {
                                                val settings = ImageOptimizer.getRecommendedSettings(
                                                    useCase = useCase,
                                                    hasTransparency = metadata?.hasAlpha == true
                                                )
                                                selectedFormat = settings.outputFormat
                                                quality = settings.quality
                                                maxWidth = settings.maxWidth
                                                maxHeight = settings.maxHeight
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = useCase.name.lowercase().replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Output format
                            Text(
                                text = "Output Format",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ImageFormat.entries.forEach { format ->
                                    FilterChip(
                                        selected = selectedFormat == format,
                                        onClick = { selectedFormat = format },
                                        label = { Text(format.name) },
                                        modifier = Modifier.weight(1f),
                                        leadingIcon = if (selectedFormat == format) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Outlined.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Quality slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.HighQuality,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Quality",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(60.dp)
                                )
                                Slider(
                                    value = quality.toFloat(),
                                    onValueChange = { quality = it.toInt() },
                                    valueRange = 10f..100f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$quality%",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(40.dp),
                                    textAlign = TextAlign.End
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Dimensions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoSizeSelectLarge,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Max Size",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(modifier = Modifier.weight(1f))

                                OutlinedTextField(
                                    value = maxWidth.toString(),
                                    onValueChange = { value ->
                                        maxWidth = value.filter { it.isDigit() }.toIntOrNull() ?: maxWidth
                                    },
                                    modifier = Modifier.width(80.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        textAlign = TextAlign.Center
                                    ),
                                    suffix = { Text("w", style = MaterialTheme.typography.labelSmall) }
                                )

                                Text(
                                    text = "×",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                OutlinedTextField(
                                    value = maxHeight.toString(),
                                    onValueChange = { value ->
                                        maxHeight = value.filter { it.isDigit() }.toIntOrNull() ?: maxHeight
                                    },
                                    modifier = Modifier.width(80.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        textAlign = TextAlign.Center
                                    ),
                                    suffix = { Text("h", style = MaterialTheme.typography.labelSmall) }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Aspect ratio toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AspectRatio,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Maintain aspect ratio",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = maintainAspectRatio,
                                    onCheckedChange = { maintainAspectRatio = it }
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Optimize button
                            Button(
                                onClick = {
                                    scope.launch {
                                        isOptimizing = true
                                        val settings = ImageOptimizationSettings(
                                            maxWidth = maxWidth,
                                            maxHeight = maxHeight,
                                            quality = quality,
                                            outputFormat = selectedFormat,
                                            maintainAspectRatio = maintainAspectRatio
                                        )

                                        val outputDir = File(projectPath, "images/optimized")
                                        if (!outputDir.exists()) {
                                            outputDir.mkdirs()
                                        }

                                        val result = ImageOptimizer.optimizeImage(
                                            context = context,
                                            sourceFile = File(asset.path),
                                            outputDir = outputDir,
                                            settings = settings
                                        )

                                        optimizationResult = result
                                        isOptimizing = false

                                        if (result.success && result.outputFile != null) {
                                            onImageOptimized(result.outputFile)
                                            Toast.makeText(
                                                context,
                                                "Image optimized! Saved ${FileUtils.formatFileSize(result.savedBytes)}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                result.errorMessage ?: "Optimization failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isOptimizing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isOptimizing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(if (isOptimizing) "Optimizing..." else "Optimize Image")
                            }

                            // Optimization result
                            optimizationResult?.let { result ->
                                if (result.success) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OptimizationResultCard(result = result)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageMetadataSection(
    metadata: ImageMetadata,
    fileSize: Long
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
                MetadataItem(
                    label = "Dimensions",
                    value = "${metadata.width} × ${metadata.height}",
                    modifier = Modifier.weight(1f)
                )
                MetadataItem(
                    label = "File Size",
                    value = FileUtils.formatFileSize(fileSize),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetadataItem(
                    label = "Format",
                    value = metadata.format.removePrefix("image/").uppercase(),
                    modifier = Modifier.weight(1f)
                )
                MetadataItem(
                    label = "Alpha Channel",
                    value = if (metadata.hasAlpha) "Yes" else "No",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetadataItem(
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

@Composable
private fun OptimizationResultCard(
    result: OptimizationResult
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Optimization Complete",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Size comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Original",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FileUtils.formatFileSize(result.originalSize),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${result.originalWidth} × ${result.originalHeight}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Optimized",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FileUtils.formatFileSize(result.optimizedSize),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${result.optimizedWidth} × ${result.optimizedHeight}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Savings indicator
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = FileUtils.formatFileSize(result.savedBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " (${String.format("%.1f", result.savedPercentage)}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
