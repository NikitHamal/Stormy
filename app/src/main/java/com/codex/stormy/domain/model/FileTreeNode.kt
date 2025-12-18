package com.codex.stormy.domain.model

import java.io.File

sealed class FileTreeNode {
    abstract val name: String
    abstract val path: String
    abstract val depth: Int

    data class FileNode(
        override val name: String,
        override val path: String,
        override val depth: Int,
        val extension: String,
        val size: Long
    ) : FileTreeNode() {
        val fileType: FileType
            get() = FileType.fromExtension(extension)
    }

    data class FolderNode(
        override val name: String,
        override val path: String,
        override val depth: Int,
        val isExpanded: Boolean = false,
        val children: List<FileTreeNode> = emptyList()
    ) : FileTreeNode()

    companion object {
        fun fromFile(file: File, basePath: String, depth: Int = 0): FileTreeNode {
            val relativePath = file.absolutePath.removePrefix(basePath).trimStart(File.separatorChar)

            return if (file.isDirectory) {
                FolderNode(
                    name = file.name,
                    path = relativePath,
                    depth = depth,
                    isExpanded = false,
                    children = emptyList()
                )
            } else {
                FileNode(
                    name = file.name,
                    path = relativePath,
                    depth = depth,
                    extension = file.extension.lowercase(),
                    size = file.length()
                )
            }
        }
    }
}

enum class FileType(
    val displayName: String,
    val extensions: List<String>
) {
    HTML("HTML", listOf("html", "htm")),
    CSS("CSS", listOf("css")),
    JAVASCRIPT("JavaScript", listOf("js", "mjs")),
    JSON("JSON", listOf("json")),
    MARKDOWN("Markdown", listOf("md", "markdown")),
    IMAGE("Image", listOf("png", "jpg", "jpeg", "gif", "svg", "webp", "ico")),
    FONT("Font", listOf("ttf", "otf", "woff", "woff2")),
    OTHER("Other", emptyList());

    companion object {
        fun fromExtension(extension: String): FileType {
            val ext = extension.lowercase()
            return entries.find { type ->
                type.extensions.contains(ext)
            } ?: OTHER
        }
    }
}
