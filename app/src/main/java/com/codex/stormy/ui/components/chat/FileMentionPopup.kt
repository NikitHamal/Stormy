package com.codex.stormy.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.domain.model.FileTreeNode
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * Represents a file or folder that can be mentioned in chat
 */
data class MentionItem(
    val name: String,
    val path: String,
    val isFolder: Boolean,
    val extension: String = ""
)

/**
 * Popup that shows file/folder suggestions when user types @
 */
@Composable
fun FileMentionPopup(
    isVisible: Boolean,
    query: String,
    fileTree: List<FileTreeNode>,
    onSelectItem: (MentionItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredItems = remember(query, fileTree) {
        flattenFileTree(fileTree)
            .filter { item ->
                if (query.isEmpty()) true
                else item.name.contains(query, ignoreCase = true) ||
                        item.path.contains(query, ignoreCase = true)
            }
            .take(8)
    }

    val listState = rememberLazyListState()

    LaunchedEffect(filteredItems) {
        if (filteredItems.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    AnimatedVisibility(
        visible = isVisible && filteredItems.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mention file or folder",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = PoppinsFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${filteredItems.size} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // File list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 240.dp)
                ) {
                    items(
                        items = filteredItems,
                        key = { it.path }
                    ) { item ->
                        MentionItemRow(
                            item = item,
                            query = query,
                            onClick = { onSelectItem(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MentionItemRow(
    item: MentionItem,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // File/folder icon
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(getFileIconBackground(item)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getFileIcon(item),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = getFileIconTint(item)
            )
        }

        // Name and path
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (item.isFolder) PoppinsFontFamily else FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.path != item.name) {
                Text(
                    text = item.path,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Type badge for files
        if (!item.isFolder && item.extension.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.extension.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Flatten file tree into a list of MentionItems
 */
private fun flattenFileTree(
    nodes: List<FileTreeNode>,
    result: MutableList<MentionItem> = mutableListOf()
): List<MentionItem> {
    for (node in nodes) {
        when (node) {
            is FileTreeNode.FileNode -> {
                result.add(
                    MentionItem(
                        name = node.name,
                        path = node.path,
                        isFolder = false,
                        extension = node.extension
                    )
                )
            }
            is FileTreeNode.FolderNode -> {
                result.add(
                    MentionItem(
                        name = node.name,
                        path = node.path,
                        isFolder = true
                    )
                )
                flattenFileTree(node.children, result)
            }
        }
    }
    return result
}

@Composable
private fun getFileIcon(item: MentionItem): ImageVector {
    return if (item.isFolder) {
        Icons.Outlined.Folder
    } else {
        when (item.extension.lowercase()) {
            "html", "htm" -> Icons.Outlined.Code
            "css" -> Icons.Outlined.Code
            "js", "mjs", "ts", "tsx", "jsx" -> Icons.Outlined.Code
            "json" -> Icons.Outlined.Settings
            "md", "markdown" -> Icons.Outlined.Description
            "png", "jpg", "jpeg", "gif", "svg", "webp", "ico" -> Icons.Outlined.Image
            else -> Icons.Outlined.TextSnippet
        }
    }
}

@Composable
private fun getFileIconTint(item: MentionItem): Color {
    return if (item.isFolder) {
        MaterialTheme.colorScheme.primary
    } else {
        when (item.extension.lowercase()) {
            "html", "htm" -> Color(0xFFE44D26)
            "css" -> Color(0xFF2196F3)
            "js", "mjs" -> Color(0xFFF7DF1E)
            "ts", "tsx" -> Color(0xFF3178C6)
            "jsx" -> Color(0xFF61DAFB)
            "json" -> Color(0xFF5C6BC0)
            "md", "markdown" -> MaterialTheme.colorScheme.onSurfaceVariant
            "png", "jpg", "jpeg", "gif", "svg", "webp", "ico" -> Color(0xFF4CAF50)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
}

@Composable
private fun getFileIconBackground(item: MentionItem): Color {
    return if (item.isFolder) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        when (item.extension.lowercase()) {
            "html", "htm" -> Color(0xFFE44D26).copy(alpha = 0.15f)
            "css" -> Color(0xFF2196F3).copy(alpha = 0.15f)
            "js", "mjs" -> Color(0xFFF7DF1E).copy(alpha = 0.15f)
            "ts", "tsx" -> Color(0xFF3178C6).copy(alpha = 0.15f)
            "jsx" -> Color(0xFF61DAFB).copy(alpha = 0.15f)
            "json" -> Color(0xFF5C6BC0).copy(alpha = 0.15f)
            "png", "jpg", "jpeg", "gif", "svg", "webp", "ico" -> Color(0xFF4CAF50).copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }
    }
}

/**
 * Display chip for a mentioned file in the chat input
 */
@Composable
fun MentionChip(
    item: MentionItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (item.isFolder) Icons.Outlined.Folder else Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "@${item.name}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

/**
 * Extract mention query from text after @ symbol
 * Returns null if no active mention, or the query string after @
 */
fun extractMentionQuery(text: String, cursorPosition: Int): String? {
    if (cursorPosition <= 0 || cursorPosition > text.length) return null

    val textBeforeCursor = text.substring(0, cursorPosition)
    val lastAtIndex = textBeforeCursor.lastIndexOf('@')

    if (lastAtIndex == -1) return null

    val queryAfterAt = textBeforeCursor.substring(lastAtIndex + 1)

    if (queryAfterAt.contains(' ') || queryAfterAt.contains('\n')) {
        return null
    }

    if (lastAtIndex > 0) {
        val charBefore = text[lastAtIndex - 1]
        if (!charBefore.isWhitespace() && charBefore != '\n') {
            return null
        }
    }

    return queryAfterAt
}

/**
 * Replace the @ mention with the selected file path
 */
fun replaceMentionInText(
    text: String,
    cursorPosition: Int,
    mentionItem: MentionItem
): Pair<String, Int> {
    val textBeforeCursor = text.substring(0, cursorPosition)
    val textAfterCursor = text.substring(cursorPosition)
    val lastAtIndex = textBeforeCursor.lastIndexOf('@')

    if (lastAtIndex == -1) return text to cursorPosition

    val beforeMention = textBeforeCursor.substring(0, lastAtIndex)
    val replacement = "@${mentionItem.path} "

    val newText = beforeMention + replacement + textAfterCursor
    val newCursorPosition = beforeMention.length + replacement.length

    return newText to newCursorPosition
}
