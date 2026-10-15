package com.codex.stormy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.domain.model.CodeOperation
import com.codex.stormy.ui.theme.CodeXTheme
import com.codex.stormy.domain.model.CodeChange as DomainCodeChange

/**
 * Represents a line in a diff view
 */
sealed class DiffLine {
    abstract val content: String
    abstract val lineNumber: Int?

    data class Addition(
        override val content: String,
        override val lineNumber: Int
    ) : DiffLine()

    data class Deletion(
        override val content: String,
        override val lineNumber: Int
    ) : DiffLine()

    data class Context(
        override val content: String,
        override val lineNumber: Int
    ) : DiffLine()

    data class Header(
        override val content: String
    ) : DiffLine() {
        override val lineNumber: Int? = null
    }
}

/**
 * Represents a code change in a file
 */
data class CodeChange(
    val filePath: String,
    val language: String = "",
    val diffLines: List<DiffLine>,
    val isNew: Boolean = false,
    val isDeleted: Boolean = false
)

/**
 * GitHub-style diff view component
 */
@Composable
fun DiffView(
    codeChange: CodeChange,
    onApply: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val extendedColors = CodeXTheme.extendedColors
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = extendedColors.diffBackground,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Header
            DiffHeader(
                filePath = codeChange.filePath,
                isNew = codeChange.isNew,
                isDeleted = codeChange.isDeleted,
                onCopy = {
                    val content = codeChange.diffLines
                        .filter { it !is DiffLine.Header }
                        .joinToString("\n") { line ->
                            when (line) {
                                is DiffLine.Addition -> line.content
                                is DiffLine.Context -> line.content
                                else -> ""
                            }
                        }
                    clipboardManager.setText(AnnotatedString(content))
                    copied = true
                },
                onApply = onApply,
                copied = copied
            )

            // Diff content
            val horizontalScrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
            ) {
                codeChange.diffLines.forEach { line ->
                    DiffLineView(line = line)
                }
            }
        }
    }
}

@Composable
private fun DiffHeader(
    filePath: String,
    isNew: Boolean,
    isDeleted: Boolean,
    onCopy: () -> Unit,
    onApply: (() -> Unit)?,
    copied: Boolean
) {
    val extendedColors = CodeXTheme.extendedColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(extendedColors.diffHeaderBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = filePath,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = extendedColors.diffHeaderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
            )

            if (isNew) {
                DiffBadge(
                    text = "NEW",
                    color = extendedColors.diffAddedBackground
                )
            }

            if (isDeleted) {
                DiffBadge(
                    text = "DELETED",
                    color = extendedColors.diffRemovedBackground
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.padding(0.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Outlined.Done else Icons.Outlined.ContentCopy,
                    contentDescription = "Copy code",
                    tint = extendedColors.diffHeaderText
                )
            }

            if (onApply != null) {
                IconButton(
                    onClick = onApply,
                    modifier = Modifier.padding(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Apply changes",
                        tint = extendedColors.diffHeaderText
                    )
                }
            }
        }
    }
}

@Composable
private fun DiffBadge(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.3f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun DiffLineView(line: DiffLine) {
    val extendedColors = CodeXTheme.extendedColors

    val (backgroundColor, prefixChar, textColor) = when (line) {
        is DiffLine.Addition -> Triple(
            extendedColors.diffAddedBackground,
            "+",
            extendedColors.diffAddedText
        )
        is DiffLine.Deletion -> Triple(
            extendedColors.diffRemovedBackground,
            "-",
            extendedColors.diffRemovedText
        )
        is DiffLine.Context -> Triple(
            Color.Transparent,
            " ",
            extendedColors.diffContextText
        )
        is DiffLine.Header -> Triple(
            extendedColors.diffHeaderBackground.copy(alpha = 0.5f),
            "",
            extendedColors.diffHeaderText
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 4.dp)
    ) {
        // Line number
        if (line.lineNumber != null) {
            Text(
                text = line.lineNumber.toString().padStart(4),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = extendedColors.diffLineNumber,
                modifier = Modifier
                    .width(36.dp)
                    .padding(end = 8.dp)
            )
        } else {
            Box(modifier = Modifier.width(36.dp))
        }

        // Prefix character (+, -, or space)
        if (line !is DiffLine.Header) {
            Text(
                text = prefixChar,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = textColor,
                modifier = Modifier.width(16.dp)
            )
        }

        // Line content
        Text(
            text = line.content,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            fontSize = 12.sp
        )
    }
}

/**
 * Utility function to parse a unified diff string into DiffLine objects
 */
fun parseDiff(diffText: String): List<DiffLine> {
    val lines = mutableListOf<DiffLine>()
    var currentLineNumber = 1

    diffText.lines().forEach { line ->
        when {
            line.startsWith("@@") -> {
                // Parse hunk header to get line numbers
                val match = Regex("@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@(.*)").find(line)
                if (match != null) {
                    currentLineNumber = match.groupValues[2].toIntOrNull() ?: 1
                    lines.add(DiffLine.Header(line))
                }
            }
            line.startsWith("+") && !line.startsWith("+++") -> {
                lines.add(DiffLine.Addition(line.removePrefix("+"), currentLineNumber))
                currentLineNumber++
            }
            line.startsWith("-") && !line.startsWith("---") -> {
                lines.add(DiffLine.Deletion(line.removePrefix("-"), currentLineNumber))
            }
            line.startsWith(" ") || line.isEmpty() -> {
                lines.add(DiffLine.Context(line.removePrefix(" "), currentLineNumber))
                currentLineNumber++
            }
            line.startsWith("diff ") || line.startsWith("index ") ||
            line.startsWith("---") || line.startsWith("+++") -> {
                // Skip diff metadata lines
            }
            else -> {
                lines.add(DiffLine.Context(line, currentLineNumber))
                currentLineNumber++
            }
        }
    }

    return lines
}

/**
 * Create a simple diff from old and new content
 */
fun createSimpleDiff(
    oldContent: String,
    newContent: String,
    filePath: String
): CodeChange {
    val oldLines = oldContent.lines()
    val newLines = newContent.lines()
    val diffLines = mutableListOf<DiffLine>()

    // Simple line-by-line diff (not optimal but works for small changes)
    var oldIndex = 0
    var newIndex = 0

    while (oldIndex < oldLines.size || newIndex < newLines.size) {
        when {
            oldIndex >= oldLines.size -> {
                // Remaining new lines are additions
                diffLines.add(DiffLine.Addition(newLines[newIndex], newIndex + 1))
                newIndex++
            }
            newIndex >= newLines.size -> {
                // Remaining old lines are deletions
                diffLines.add(DiffLine.Deletion(oldLines[oldIndex], oldIndex + 1))
                oldIndex++
            }
            oldLines[oldIndex] == newLines[newIndex] -> {
                // Lines match - context
                diffLines.add(DiffLine.Context(newLines[newIndex], newIndex + 1))
                oldIndex++
                newIndex++
            }
            else -> {
                // Lines differ
                diffLines.add(DiffLine.Deletion(oldLines[oldIndex], oldIndex + 1))
                diffLines.add(DiffLine.Addition(newLines[newIndex], newIndex + 1))
                oldIndex++
                newIndex++
            }
        }
    }

    return CodeChange(
        filePath = filePath,
        diffLines = diffLines,
        isNew = oldContent.isEmpty(),
        isDeleted = newContent.isEmpty()
    )
}

/**
 * Convert domain CodeChange to UI CodeChange for display
 */
fun DomainCodeChange.toUiCodeChange(): CodeChange {
    val oldContent = this.oldContent ?: ""
    val newContent = this.content ?: ""

    return when (this.operation) {
        CodeOperation.CREATE -> {
            val diffLines = newContent.lines().mapIndexed { index, line ->
                DiffLine.Addition(line, index + 1)
            }
            CodeChange(
                filePath = this.filePath,
                diffLines = diffLines,
                isNew = true,
                isDeleted = false
            )
        }
        CodeOperation.DELETE -> {
            val diffLines = oldContent.lines().mapIndexed { index, line ->
                DiffLine.Deletion(line, index + 1)
            }
            CodeChange(
                filePath = this.filePath,
                diffLines = diffLines,
                isNew = false,
                isDeleted = true
            )
        }
        CodeOperation.UPDATE, CodeOperation.RENAME -> {
            createSimpleDiff(oldContent, newContent, this.filePath)
        }
    }
}
