package com.codex.stormy.ui.screens.editor.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Image optimization settings
 */
data class ImageOptimizationSettings(
    val maxWidth: Int = 1920,
    val maxHeight: Int = 1920,
    val quality: Int = 85, // 0-100
    val outputFormat: ImageFormat = ImageFormat.WEBP,
    val maintainAspectRatio: Boolean = true
)

/**
 * Supported output formats for image optimization
 */
enum class ImageFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp")
}

/**
 * Result of image optimization operation
 */
data class OptimizationResult(
    val success: Boolean,
    val outputFile: File? = null,
    val originalSize: Long = 0,
    val optimizedSize: Long = 0,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val optimizedWidth: Int = 0,
    val optimizedHeight: Int = 0,
    val errorMessage: String? = null
) {
    val savedBytes: Long get() = originalSize - optimizedSize
    val savedPercentage: Float get() = if (originalSize > 0) {
        ((originalSize - optimizedSize).toFloat() / originalSize * 100)
    } else 0f
}

/**
 * Image metadata for display
 */
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val format: String,
    val hasAlpha: Boolean
)

/**
 * Image optimization utility for reducing image file sizes while maintaining quality.
 * Supports JPEG, PNG, and WebP output formats with configurable quality settings.
 */
object ImageOptimizer {

    /**
     * Get metadata about an image file without loading the full bitmap
     */
    suspend fun getImageMetadata(context: Context, file: File): ImageMetadata? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            ImageMetadata(
                width = options.outWidth,
                height = options.outHeight,
                fileSize = file.length(),
                format = options.outMimeType ?: "unknown",
                hasAlpha = options.outConfig == Bitmap.Config.ARGB_8888
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get metadata about an image from a content URI
     */
    suspend fun getImageMetadata(context: Context, uri: Uri): ImageMetadata? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                val fileSize = context.contentResolver.openInputStream(uri)?.use {
                    it.available().toLong()
                } ?: 0L

                ImageMetadata(
                    width = options.outWidth,
                    height = options.outHeight,
                    fileSize = fileSize,
                    format = options.outMimeType ?: "unknown",
                    hasAlpha = options.outConfig == Bitmap.Config.ARGB_8888
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Optimize an image file with the given settings
     */
    suspend fun optimizeImage(
        context: Context,
        sourceFile: File,
        outputDir: File,
        settings: ImageOptimizationSettings = ImageOptimizationSettings()
    ): OptimizationResult = withContext(Dispatchers.IO) {
        try {
            // Get original metadata
            val metadata = getImageMetadata(context, sourceFile)
                ?: return@withContext OptimizationResult(
                    success = false,
                    errorMessage = "Could not read image metadata"
                )

            // Load bitmap with sample size to avoid OOM
            val sampleSize = calculateSampleSize(
                metadata.width,
                metadata.height,
                settings.maxWidth,
                settings.maxHeight
            )

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = if (settings.outputFormat == ImageFormat.JPEG) {
                    Bitmap.Config.RGB_565
                } else {
                    Bitmap.Config.ARGB_8888
                }
            }

            val originalBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options)
                ?: return@withContext OptimizationResult(
                    success = false,
                    errorMessage = "Could not decode image"
                )

            // Calculate target dimensions
            val (targetWidth, targetHeight) = calculateTargetDimensions(
                originalBitmap.width,
                originalBitmap.height,
                settings.maxWidth,
                settings.maxHeight,
                settings.maintainAspectRatio
            )

            // Scale bitmap if needed
            val scaledBitmap = if (originalBitmap.width != targetWidth || originalBitmap.height != targetHeight) {
                Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true).also {
                    if (it != originalBitmap) {
                        originalBitmap.recycle()
                    }
                }
            } else {
                originalBitmap
            }

            // Create output file
            val baseName = sourceFile.nameWithoutExtension
            val outputFile = File(outputDir, "${baseName}_optimized.${settings.outputFormat.extension}")

            // Compress and save
            val compressFormat = when (settings.outputFormat) {
                ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP
            }

            FileOutputStream(outputFile).use { outputStream ->
                scaledBitmap.compress(compressFormat, settings.quality, outputStream)
            }

            // Clean up
            scaledBitmap.recycle()

            OptimizationResult(
                success = true,
                outputFile = outputFile,
                originalSize = sourceFile.length(),
                optimizedSize = outputFile.length(),
                originalWidth = metadata.width,
                originalHeight = metadata.height,
                optimizedWidth = targetWidth,
                optimizedHeight = targetHeight
            )
        } catch (e: Exception) {
            OptimizationResult(
                success = false,
                errorMessage = e.message ?: "Unknown error during optimization"
            )
        }
    }

    /**
     * Optimize an image from a content URI
     */
    suspend fun optimizeImage(
        context: Context,
        uri: Uri,
        outputDir: File,
        outputFileName: String,
        settings: ImageOptimizationSettings = ImageOptimizationSettings()
    ): OptimizationResult = withContext(Dispatchers.IO) {
        try {
            // Get original metadata
            val metadata = getImageMetadata(context, uri)
                ?: return@withContext OptimizationResult(
                    success = false,
                    errorMessage = "Could not read image metadata"
                )

            // Calculate sample size
            val sampleSize = calculateSampleSize(
                metadata.width,
                metadata.height,
                settings.maxWidth,
                settings.maxHeight
            )

            // Load bitmap from URI
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = if (settings.outputFormat == ImageFormat.JPEG) {
                    Bitmap.Config.RGB_565
                } else {
                    Bitmap.Config.ARGB_8888
                }
            }

            val originalBitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            } ?: return@withContext OptimizationResult(
                success = false,
                errorMessage = "Could not decode image"
            )

            // Calculate target dimensions
            val (targetWidth, targetHeight) = calculateTargetDimensions(
                originalBitmap.width,
                originalBitmap.height,
                settings.maxWidth,
                settings.maxHeight,
                settings.maintainAspectRatio
            )

            // Scale bitmap if needed
            val scaledBitmap = if (originalBitmap.width != targetWidth || originalBitmap.height != targetHeight) {
                Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true).also {
                    if (it != originalBitmap) {
                        originalBitmap.recycle()
                    }
                }
            } else {
                originalBitmap
            }

            // Create output file
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val outputFile = File(outputDir, "$outputFileName.${settings.outputFormat.extension}")

            // Compress and save
            val compressFormat = when (settings.outputFormat) {
                ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP
            }

            FileOutputStream(outputFile).use { outputStream ->
                scaledBitmap.compress(compressFormat, settings.quality, outputStream)
            }

            // Clean up
            scaledBitmap.recycle()

            OptimizationResult(
                success = true,
                outputFile = outputFile,
                originalSize = metadata.fileSize,
                optimizedSize = outputFile.length(),
                originalWidth = metadata.width,
                originalHeight = metadata.height,
                optimizedWidth = targetWidth,
                optimizedHeight = targetHeight
            )
        } catch (e: Exception) {
            OptimizationResult(
                success = false,
                errorMessage = e.message ?: "Unknown error during optimization"
            )
        }
    }

    /**
     * Resize an existing image file to specific dimensions
     */
    suspend fun resizeImage(
        context: Context,
        sourceFile: File,
        outputDir: File,
        targetWidth: Int,
        targetHeight: Int,
        quality: Int = 90,
        format: ImageFormat = ImageFormat.WEBP
    ): OptimizationResult = withContext(Dispatchers.IO) {
        optimizeImage(
            context = context,
            sourceFile = sourceFile,
            outputDir = outputDir,
            settings = ImageOptimizationSettings(
                maxWidth = targetWidth,
                maxHeight = targetHeight,
                quality = quality,
                outputFormat = format,
                maintainAspectRatio = true
            )
        )
    }

    /**
     * Quick compress - optimize with default settings for web
     */
    suspend fun quickCompress(
        context: Context,
        sourceFile: File,
        outputDir: File
    ): OptimizationResult {
        return optimizeImage(
            context = context,
            sourceFile = sourceFile,
            outputDir = outputDir,
            settings = ImageOptimizationSettings(
                maxWidth = 1200,
                maxHeight = 1200,
                quality = 80,
                outputFormat = ImageFormat.WEBP
            )
        )
    }

    /**
     * Calculate the sample size for BitmapFactory to avoid OOM
     */
    private fun calculateSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var sampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    /**
     * Calculate target dimensions while optionally maintaining aspect ratio
     */
    private fun calculateTargetDimensions(
        currentWidth: Int,
        currentHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
        maintainAspectRatio: Boolean
    ): Pair<Int, Int> {
        if (!maintainAspectRatio) {
            return Pair(
                min(currentWidth, maxWidth),
                min(currentHeight, maxHeight)
            )
        }

        // Already within limits
        if (currentWidth <= maxWidth && currentHeight <= maxHeight) {
            return Pair(currentWidth, currentHeight)
        }

        val widthRatio = maxWidth.toFloat() / currentWidth
        val heightRatio = maxHeight.toFloat() / currentHeight
        val ratio = min(widthRatio, heightRatio)

        return Pair(
            (currentWidth * ratio).roundToInt().coerceAtLeast(1),
            (currentHeight * ratio).roundToInt().coerceAtLeast(1)
        )
    }

    /**
     * Get recommended quality settings based on file type and use case
     */
    fun getRecommendedSettings(
        useCase: ImageUseCase = ImageUseCase.WEB,
        hasTransparency: Boolean = false
    ): ImageOptimizationSettings {
        return when (useCase) {
            ImageUseCase.WEB -> ImageOptimizationSettings(
                maxWidth = 1920,
                maxHeight = 1920,
                quality = 85,
                outputFormat = if (hasTransparency) ImageFormat.WEBP else ImageFormat.WEBP
            )
            ImageUseCase.THUMBNAIL -> ImageOptimizationSettings(
                maxWidth = 300,
                maxHeight = 300,
                quality = 75,
                outputFormat = ImageFormat.WEBP
            )
            ImageUseCase.ICON -> ImageOptimizationSettings(
                maxWidth = 64,
                maxHeight = 64,
                quality = 90,
                outputFormat = if (hasTransparency) ImageFormat.PNG else ImageFormat.WEBP
            )
            ImageUseCase.AVATAR -> ImageOptimizationSettings(
                maxWidth = 200,
                maxHeight = 200,
                quality = 80,
                outputFormat = ImageFormat.WEBP
            )
            ImageUseCase.HERO -> ImageOptimizationSettings(
                maxWidth = 2560,
                maxHeight = 1440,
                quality = 90,
                outputFormat = ImageFormat.WEBP
            )
            ImageUseCase.ORIGINAL -> ImageOptimizationSettings(
                maxWidth = 4096,
                maxHeight = 4096,
                quality = 95,
                outputFormat = ImageFormat.PNG
            )
        }
    }
}

/**
 * Common image use cases for recommended settings
 */
enum class ImageUseCase {
    WEB,        // General web use
    THUMBNAIL,  // Small preview images
    ICON,       // App/site icons
    AVATAR,     // User profile images
    HERO,       // Full-width hero images
    ORIGINAL    // Preserve quality
}
