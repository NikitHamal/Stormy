package com.codex.stormy.utils

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Centralized file utility functions for common file operations.
 * Provides consistent handling across the app for file operations,
 * MIME type detection, and file size formatting.
 */
object FileUtils {

    /**
     * Common text file extensions for the code editor
     */
    val TEXT_FILE_EXTENSIONS = setOf(
        // Web
        "html", "htm", "css", "js", "jsx", "ts", "tsx", "json", "xml",
        // Markup
        "md", "markdown", "txt", "yaml", "yml", "toml",
        // Code
        "kt", "java", "py", "rb", "php", "go", "rs", "c", "cpp", "h", "hpp",
        "swift", "m", "mm",
        // Config
        "env", "gitignore", "dockerignore", "editorconfig",
        // Data
        "csv", "sql", "graphql",
        // Misc
        "svg", "sh", "bash", "zsh", "ps1", "bat", "cmd"
    )

    /**
     * Image file extensions
     */
    val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "ico", "svg"
    )

    /**
     * Font file extensions
     */
    val FONT_EXTENSIONS = setOf(
        "ttf", "otf", "woff", "woff2", "eot"
    )

    /**
     * Check if a file is a text-based file that can be edited
     */
    fun isTextFile(file: File): Boolean {
        return file.extension.lowercase() in TEXT_FILE_EXTENSIONS
    }

    /**
     * Check if a file is a text-based file by path
     */
    fun isTextFile(path: String): Boolean {
        return path.substringAfterLast(".").lowercase() in TEXT_FILE_EXTENSIONS
    }

    /**
     * Check if a file is an image
     */
    fun isImageFile(file: File): Boolean {
        return file.extension.lowercase() in IMAGE_EXTENSIONS
    }

    /**
     * Check if a file is a font file
     */
    fun isFontFile(file: File): Boolean {
        return file.extension.lowercase() in FONT_EXTENSIONS
    }

    /**
     * Format file size for display
     * @param sizeInBytes File size in bytes
     * @return Formatted string like "1.5 MB", "256 KB", "1024 B"
     */
    fun formatFileSize(sizeInBytes: Long): String {
        return when {
            sizeInBytes < 1024 -> "$sizeInBytes B"
            sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
            sizeInBytes < 1024 * 1024 * 1024 -> {
                val mb = sizeInBytes / (1024.0 * 1024.0)
                "%.1f MB".format(mb)
            }
            else -> {
                val gb = sizeInBytes / (1024.0 * 1024.0 * 1024.0)
                "%.2f GB".format(gb)
            }
        }
    }

    /**
     * Get the relative path from a base directory
     * @param file The file to get relative path for
     * @param baseDir The base directory
     * @return Relative path string
     */
    fun getRelativePath(file: File, baseDir: File): String {
        return file.absolutePath.removePrefix(baseDir.absolutePath + File.separator)
    }

    /**
     * Safely read file content
     * @return Result with file content or error
     */
    suspend fun readFileContent(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(file.readText())
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /**
     * Safely write content to file
     * @return Result indicating success or failure
     */
    suspend fun writeFileContent(file: File, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            file.writeText(content)
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /**
     * Safely create a new file
     * @return Result with the created file or error
     */
    suspend fun createFile(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val newFile = File(parent, name)
            if (newFile.exists()) {
                return@withContext Result.failure(IOException("File already exists: $name"))
            }
            newFile.parentFile?.mkdirs()
            newFile.createNewFile()
            Result.success(newFile)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /**
     * Safely create a new directory
     * @return Result with the created directory or error
     */
    suspend fun createDirectory(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val newDir = File(parent, name)
            if (newDir.exists()) {
                return@withContext Result.failure(IOException("Directory already exists: $name"))
            }
            if (newDir.mkdirs()) {
                Result.success(newDir)
            } else {
                Result.failure(IOException("Failed to create directory: $name"))
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /**
     * Safely delete a file or directory
     * @return Result indicating success or failure
     */
    suspend fun delete(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Safely rename a file
     * @return Result with the renamed file or error
     */
    suspend fun rename(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val newFile = File(file.parentFile, newName)
            if (newFile.exists()) {
                return@withContext Result.failure(IOException("A file with that name already exists"))
            }
            if (file.renameTo(newFile)) {
                Result.success(newFile)
            } else {
                Result.failure(IOException("Failed to rename file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Copy a file from URI to a destination directory
     * @param context Android context for content resolver
     * @param sourceUri Source content URI
     * @param destDir Destination directory
     * @param fileName Optional file name (auto-generated if null)
     * @return Result with the copied file or error
     */
    suspend fun copyFromUri(
        context: Context,
        sourceUri: Uri,
        destDir: File,
        fileName: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Ensure destination directory exists
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            // Determine file name and extension
            val mimeType = context.contentResolver.getType(sourceUri)
            val extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType) ?: "bin"

            val finalFileName = fileName
                ?: "file_${System.currentTimeMillis()}.$extension"

            val destFile = File(destDir, finalFileName)

            // Copy content
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IOException("Failed to open source file"))

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get MIME type for a file
     */
    fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    /**
     * Get MIME type from extension
     */
    fun getMimeType(extension: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
    }

    /**
     * Validate that a path doesn't escape the project root (security)
     * @param requestedPath The path requested by user/agent
     * @param projectRoot The project root directory
     * @return Result with validated absolute file or error
     */
    fun validateProjectPath(requestedPath: String, projectRoot: File): Result<File> {
        return try {
            val resolved = File(projectRoot, requestedPath).canonicalFile
            val root = projectRoot.canonicalFile

            if (resolved.startsWith(root)) {
                Result.success(resolved)
            } else {
                Result.failure(SecurityException("Path traversal detected: $requestedPath"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Scan directory for files matching criteria
     * @param directory Directory to scan
     * @param recursive Whether to scan subdirectories
     * @param filter Optional filter predicate
     * @return List of matching files
     */
    suspend fun scanDirectory(
        directory: File,
        recursive: Boolean = true,
        filter: (File) -> Boolean = { true }
    ): List<File> = withContext(Dispatchers.IO) {
        val results = mutableListOf<File>()

        fun scan(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory && recursive) {
                    scan(file)
                } else if (file.isFile && filter(file)) {
                    results.add(file)
                }
            }
        }

        if (directory.exists() && directory.isDirectory) {
            scan(directory)
        }

        results
    }

    /**
     * Get file icon resource based on extension
     * @return Color tint suggestion for the file type
     */
    fun getFileTypeColor(extension: String): FileTypeColor {
        return when (extension.lowercase()) {
            "html", "htm" -> FileTypeColor.HTML
            "css" -> FileTypeColor.CSS
            "js", "jsx", "mjs" -> FileTypeColor.JAVASCRIPT
            "ts", "tsx" -> FileTypeColor.TYPESCRIPT
            "json" -> FileTypeColor.JSON
            "md", "markdown" -> FileTypeColor.MARKDOWN
            "kt", "java" -> FileTypeColor.KOTLIN_JAVA
            "py" -> FileTypeColor.PYTHON
            "svg" -> FileTypeColor.SVG
            in IMAGE_EXTENSIONS -> FileTypeColor.IMAGE
            in FONT_EXTENSIONS -> FileTypeColor.FONT
            else -> FileTypeColor.DEFAULT
        }
    }

    /**
     * File type colors for UI display
     */
    enum class FileTypeColor(val colorHex: Long) {
        HTML(0xFFE34C26),
        CSS(0xFF264DE4),
        JAVASCRIPT(0xFFF7DF1E),
        TYPESCRIPT(0xFF3178C6),
        JSON(0xFF5B5B5B),
        MARKDOWN(0xFF083FA1),
        KOTLIN_JAVA(0xFF7F52FF),
        PYTHON(0xFF3776AB),
        SVG(0xFFFFB13B),
        IMAGE(0xFF4CAF50),
        FONT(0xFF9C27B0),
        DEFAULT(0xFF808080)
    }

    /**
     * Copy a folder and its contents from a document tree URI to destination.
     * Uses DocumentsContract for proper SAF traversal.
     * @param context Android context
     * @param sourceTreeUri Source document tree URI from folder picker
     * @param destDir Destination directory
     * @param progressCallback Optional callback for progress updates (copied files, total files)
     * @return Result with total files copied or error
     */
    suspend fun copyFolderFromUri(
        context: Context,
        sourceTreeUri: Uri,
        destDir: File,
        progressCallback: ((copied: Int, total: Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val documentId = android.provider.DocumentsContract.getTreeDocumentId(sourceTreeUri)
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                sourceTreeUri,
                documentId
            )

            // First pass: count total files for progress
            val totalFiles = countDocumentsRecursive(context, sourceTreeUri, documentId)
            var copiedFiles = 0

            // Ensure destination exists
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            // Copy contents recursively
            copyDocumentsRecursive(
                context = context,
                treeUri = sourceTreeUri,
                documentId = documentId,
                destDir = destDir
            ) {
                copiedFiles++
                progressCallback?.invoke(copiedFiles, totalFiles)
            }

            Result.success(copiedFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Count documents recursively in a document tree
     */
    private fun countDocumentsRecursive(
        context: Context,
        treeUri: Uri,
        documentId: String
    ): Int {
        var count = 0
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            documentId
        )

        context.contentResolver.query(
            childrenUri,
            arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val mimeIndex = cursor.getColumnIndexOrThrow(
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIndex)
                val mimeType = cursor.getString(mimeIndex)

                if (mimeType == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                    count += countDocumentsRecursive(context, treeUri, childId)
                } else {
                    count++
                }
            }
        }

        return count
    }

    /**
     * Copy documents recursively from a document tree
     */
    private fun copyDocumentsRecursive(
        context: Context,
        treeUri: Uri,
        documentId: String,
        destDir: File,
        onFileCopied: () -> Unit
    ) {
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            documentId
        )

        context.contentResolver.query(
            childrenUri,
            arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val nameIndex = cursor.getColumnIndexOrThrow(
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            val mimeIndex = cursor.getColumnIndexOrThrow(
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIndex)
                val childName = cursor.getString(nameIndex)
                val mimeType = cursor.getString(mimeIndex)

                if (mimeType == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                    // Create subdirectory and recurse
                    val subDir = File(destDir, childName)
                    subDir.mkdirs()
                    copyDocumentsRecursive(context, treeUri, childId, subDir, onFileCopied)
                } else {
                    // Copy file
                    val documentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        childId
                    )
                    val destFile = File(destDir, childName)

                    try {
                        context.contentResolver.openInputStream(documentUri)?.use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        onFileCopied()
                    } catch (e: Exception) {
                        // Log and continue with other files
                        android.util.Log.w("FileUtils", "Failed to copy file: $childName", e)
                    }
                }
            }
        }
    }

    /**
     * Get the display name of a document from its URI
     */
    fun getDocumentDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get folder name from a document tree URI
     */
    fun getFolderNameFromTreeUri(context: Context, treeUri: Uri): String? {
        return try {
            val documentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val documentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                documentId
            )
            getDocumentDisplayName(context, documentUri)
        } catch (e: Exception) {
            null
        }
    }
}
