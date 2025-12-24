package com.codex.stormy.ui.components.diff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.ui.theme.PoppinsFontFamily
import com.codex.stormy.utils.DiffUtils

/**
 * Professional diff colors
 */
object DiffColors {
    // Addition colors (green variants)
    val additionBackground = Color(0xFF1E3A1E)
    val additionBackgroundLight = Color(0xFFE6F4E6)
    val additionText = Color(0xFF4CAF50)
    val additionGutter = Color(0xFF2E7D32)

    // Deletion colors (red variants)
    val deletionBackground = Color(0xFF3E1E1E)
    val deletionBackgroundLight = Color(0xFFFCE4E4)
    val deletionText = Color(0xFFE53935)
    val deletionGutter = Color(0xFFC62828)

    // Context/unchanged colors
    val contextBackground = Color(0xFF1E1E1E)
    val contextBackgroundLight = Color(0xFFFAFAFA)
    val contextText = Color(0xFF9E9E9E)

    // Line number colors
    val lineNumber = Color(0xFF6E6E6E)
    val lineNumberBackground = Color(0xFF2D2D2D)
    val lineNumberBackgroundLight = Color(0xFFF0F0F0)
}

/**
 * Compact diff summary badge showing +X -Y with colors
 */
@Composable
fun DiffSummaryBadge(
    additions: Int,
    deletions: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (additions > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = DiffColors.additionText
                )
                Text(
                    text = additions.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = DiffColors.additionText,
                    fontSize = 11.sp
                )
            }
        }

        if (deletions > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = DiffColors.deletionText
                )
                Text(
                    text = deletions.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = DiffColors.deletionText,
                    fontSize = 11.sp
                )
            }
        }

        if (additions == 0 && deletions == 0) {
            Text(
                text = "No changes",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Collapsible diff viewer for tool call results
 */
@Composable
fun CollapsibleDiffView(
    filePath: String,
    oldContent: String,
    newContent: String,
    toolName: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    val diffResult = remember(oldContent, newContent) {
        DiffUtils.computeDiff(oldContent, newContent, contextLines = 3)
    }

    val backgroundColor = if (isSuccess) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        tonalElevation = 0.dp
    ) {
        Column {
            // Header row with file info and diff summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // File icon
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isSuccess) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )

                // File path
                Text(
                    text = filePath.substringAfterLast("/"),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Diff summary badge
                DiffSummaryBadge(
                    additions = diffResult.additions,
                    deletions = diffResult.deletions
                )

                // Expand/collapse icon
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ExpandMore
                    },
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded diff content
            AnimatedVisibility(
                visible = isExpanded && diffResult.hasChanges,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    DiffContent(
                        diffResult = diffResult,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Full diff content view with line numbers and syntax coloring
 */
@Composable
fun DiffContent(
    diffResult: DiffUtils.DiffResult,
    modifier: Modifier = Modifier,
    showLineNumbers: Boolean = true
) {
    val clipboardManager = LocalClipboardManager.current
    val horizontalScrollState = rememberScrollState()

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isDarkTheme) DiffColors.contextBackground else DiffColors.contextBackgroundLight,
        tonalElevation = 0.dp
    ) {
        Column {
            // Header with copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isDarkTheme) {
                            DiffColors.lineNumberBackground
                        } else {
                            DiffColors.lineNumberBackgroundLight
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Changes",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = PoppinsFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                IconButton(
                    onClick = {
                        val diffText = DiffUtils.toUnifiedDiff(diffResult)
                        clipboardManager.setText(AnnotatedString(diffText))
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy diff",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Scrollable diff lines
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        diffResult.lines.forEach { line ->
                            DiffLineRow(
                                line = line,
                                showLineNumbers = showLineNumbers,
                                isDarkTheme = isDarkTheme
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual diff line row with proper styling
 */
@Composable
private fun DiffLineRow(
    line: DiffUtils.DiffLine,
    showLineNumbers: Boolean,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, prefixChar, textColor, lineNumColor) = when (line) {
        is DiffUtils.DiffLine.Addition -> {
            listOf(
                if (isDarkTheme) DiffColors.additionBackground else DiffColors.additionBackgroundLight,
                "+",
                if (isDarkTheme) DiffColors.additionText else Color(0xFF2E7D32),
                DiffColors.additionGutter
            )
        }
        is DiffUtils.DiffLine.Deletion -> {
            listOf(
                if (isDarkTheme) DiffColors.deletionBackground else DiffColors.deletionBackgroundLight,
                "-",
                if (isDarkTheme) DiffColors.deletionText else Color(0xFFC62828),
                DiffColors.deletionGutter
            )
        }
        is DiffUtils.DiffLine.Unchanged, is DiffUtils.DiffLine.Context -> {
            listOf(
                Color.Transparent,
                " ",
                if (isDarkTheme) DiffColors.contextText else Color(0xFF616161),
                DiffColors.lineNumber
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor as Color)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Line number gutter
        if (showLineNumbers) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .padding(end = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = line.lineNumber?.toString() ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = lineNumColor as Color
                )
            }
        }

        // Prefix character (+, -, or space)
        Text(
            text = prefixChar as String,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor as Color,
            modifier = Modifier.width(12.dp)
        )

        // Line content
        Text(
            text = line.content,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = textColor
        )
    }
}

/**
 * Calculate color luminance for theme detection
 */
private fun Color.luminance(): Float {
    val r = red * 0.299f
    val g = green * 0.587f
    val b = blue * 0.114f
    return r + g + b
}

/**
 * Inline diff view for showing changes within a single tool output
 */
@Composable
fun InlineDiffBadge(
    additions: Int,
    deletions: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (additions > 0) {
            Text(
                text = "+$additions",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = DiffColors.additionText,
                fontSize = 10.sp
            )
        }
        if (deletions > 0) {
            Text(
                text = "-$deletions",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = DiffColors.deletionText,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Simple file change summary for file operations
 */
@Composable
fun FileChangeSummary(
    operation: FileOperation,
    fileName: String,
    additions: Int = 0,
    deletions: Int = 0,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Operation badge
        val (opText, opColor) = when (operation) {
            FileOperation.CREATE -> "NEW" to DiffColors.additionText
            FileOperation.MODIFY -> "MOD" to Color(0xFFFFA726)
            FileOperation.DELETE -> "DEL" to DiffColors.deletionText
            FileOperation.RENAME -> "REN" to Color(0xFF42A5F5)
        }

        Text(
            text = opText,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = opColor,
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(opColor.copy(alpha = 0.15f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )

        // File name
        Text(
            text = fileName,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Change stats if applicable
        if (operation == FileOperation.MODIFY && (additions > 0 || deletions > 0)) {
            DiffSummaryBadge(additions = additions, deletions = deletions)
        }
    }
}

/**
 * File operation types
 */
enum class FileOperation {
    CREATE,
    MODIFY,
    DELETE,
    RENAME
}
