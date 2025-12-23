package com.codex.stormy.ui.components.message

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.TextFields
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.ui.components.MarkdownText
import com.codex.stormy.ui.components.diff.CollapsibleDiffView
import com.codex.stormy.ui.components.diff.DiffSummaryBadge
import com.codex.stormy.ui.theme.PoppinsFontFamily
import com.codex.stormy.utils.DiffUtils

/**
 * Represents the current AI activity status for live indicators
 */
enum class AiActivityStatus {
    IDLE,
    THINKING,
    TYPING,
    CALLING_TOOL,
    WRITING_FILE,
    READING_FILE,
    EXECUTING
}

/**
 * Parsed content block types for AI messages
 */
sealed class MessageContentBlock {
    data class ThinkingBlock(val content: String, val isActive: Boolean = false) : MessageContentBlock()
    data class TextBlock(val content: String) : MessageContentBlock()
    data class ToolCallBlock(
        val toolName: String,
        val status: ToolStatus,
        val output: String? = null,
        val filePath: String? = null,
        val isActive: Boolean = false,
        val additions: Int = 0,
        val deletions: Int = 0,
        val oldContent: String? = null,
        val newContent: String? = null
    ) : MessageContentBlock()
    data class CodeBlock(val code: String, val language: String?) : MessageContentBlock()
    data class ReasoningBlock(val content: String, val isActive: Boolean = false) : MessageContentBlock()
}

enum class ToolStatus {
    RUNNING, SUCCESS, ERROR
}

/**
 * Parse AI message content into structured blocks with improved reasoning detection.
 * This parser properly separates AI text responses from tool outputs to prevent content bleeding.
 */
fun parseAiMessageContent(content: String, isStreaming: Boolean = false): List<MessageContentBlock> {
    val blocks = mutableListOf<MessageContentBlock>()
    if (content.isBlank()) return blocks

    // Split content by tool call markers to properly separate text from tool outputs
    val segments = splitByToolCalls(content)

    for ((index, segment) in segments.withIndex()) {
        when (segment.type) {
            SegmentType.TEXT -> {
                // Process text segment for thinking/reasoning tags
                processTextSegment(segment.content, blocks, isStreaming && index == segments.lastIndex)
            }
            SegmentType.TOOL_CALL -> {
                // Add tool call block directly
                blocks.add(MessageContentBlock.ToolCallBlock(
                    toolName = segment.toolName ?: "Unknown Tool",
                    status = segment.toolStatus ?: ToolStatus.RUNNING,
                    output = segment.toolOutput,
                    filePath = extractFilePath(segment.toolName ?: "", segment.toolOutput ?: ""),
                    isActive = segment.toolStatus == ToolStatus.RUNNING && isStreaming
                ))
            }
        }
    }

    // If no blocks were created, add the entire content as text
    if (blocks.isEmpty() && content.isNotBlank()) {
        blocks.add(MessageContentBlock.TextBlock(content))
    }

    return blocks
}

/**
 * Segment type for content splitting
 */
private enum class SegmentType {
    TEXT, TOOL_CALL
}

/**
 * Represents a parsed segment of the AI message
 */
private data class ContentSegment(
    val type: SegmentType,
    val content: String,
    val toolName: String? = null,
    val toolStatus: ToolStatus? = null,
    val toolOutput: String? = null
)

/**
 * Split content by tool call markers (\n\n🔧) to properly separate text from tool outputs.
 * This ensures that text responses don't bleed into tool sections.
 */
private fun splitByToolCalls(content: String): List<ContentSegment> {
    val segments = mutableListOf<ContentSegment>()

    // Pattern to match tool calls: 🔧 **Tool Name**\n✅/❌/⏳ output
    val toolCallPattern = Regex(
        """🔧\s*\*\*([^*]+)\*\*\s*\n(✅|❌|⏳)\s*(.*)""",
        setOf(RegexOption.MULTILINE)
    )

    // Split by tool call separator (double newline before 🔧)
    val parts = content.split(Regex("""\n\n(?=🔧)"""))

    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) continue

        // Check if this part is a tool call
        val toolMatch = toolCallPattern.find(trimmed)
        if (toolMatch != null && trimmed.startsWith("🔧")) {
            val toolName = toolMatch.groupValues[1].trim()
            val statusEmoji = toolMatch.groupValues[2]
            val output = toolMatch.groupValues[3].trim()

            val status = when (statusEmoji) {
                "✅" -> ToolStatus.SUCCESS
                "❌" -> ToolStatus.ERROR
                else -> ToolStatus.RUNNING
            }

            segments.add(ContentSegment(
                type = SegmentType.TOOL_CALL,
                content = trimmed,
                toolName = toolName,
                toolStatus = status,
                toolOutput = output.ifEmpty { null }
            ))
        } else {
            // This is text content
            segments.add(ContentSegment(
                type = SegmentType.TEXT,
                content = trimmed
            ))
        }
    }

    return segments
}

/**
 * Process a text segment for thinking/reasoning tags
 */
private fun processTextSegment(
    content: String,
    blocks: MutableList<MessageContentBlock>,
    isStreaming: Boolean
) {
    if (content.isBlank()) return

    var lastEnd = 0

    // Pattern to match thinking/reasoning sections - multiple formats
    val thinkingPatterns = listOf(
        Regex("""<thinking>([\s\S]*?)</thinking>""", RegexOption.IGNORE_CASE),
        Regex("""<think>([\s\S]*?)</think>""", RegexOption.IGNORE_CASE),
        Regex("""<reasoning>([\s\S]*?)</reasoning>""", RegexOption.IGNORE_CASE),
        Regex("""<reason>([\s\S]*?)</reason>""", RegexOption.IGNORE_CASE)
    )

    // Pattern for unclosed thinking tags (streaming)
    val unclosedThinkingPatterns = listOf(
        Regex("""<thinking>([\s\S]*)$""", RegexOption.IGNORE_CASE),
        Regex("""<think>([\s\S]*)$""", RegexOption.IGNORE_CASE),
        Regex("""<reasoning>([\s\S]*)$""", RegexOption.IGNORE_CASE),
        Regex("""<reason>([\s\S]*)$""", RegexOption.IGNORE_CASE)
    )

    // First, extract thinking/reasoning blocks (closed tags)
    for (pattern in thinkingPatterns) {
        val matches = pattern.findAll(content)
        for (match in matches) {
            val textBefore = content.substring(lastEnd, match.range.first).trim()
            if (textBefore.isNotEmpty()) {
                blocks.add(MessageContentBlock.TextBlock(textBefore))
            }
            blocks.add(MessageContentBlock.ReasoningBlock(
                content = match.groupValues[1].trim(),
                isActive = false
            ))
            lastEnd = match.range.last + 1
        }
    }

    // If streaming, check for unclosed thinking tags
    if (isStreaming && lastEnd < content.length) {
        for (pattern in unclosedThinkingPatterns) {
            val match = pattern.find(content.substring(lastEnd))
            if (match != null) {
                val textBefore = content.substring(lastEnd, lastEnd + match.range.first).trim()
                if (textBefore.isNotEmpty()) {
                    blocks.add(MessageContentBlock.TextBlock(textBefore))
                }
                blocks.add(MessageContentBlock.ReasoningBlock(
                    content = match.groupValues[1].trim(),
                    isActive = true
                ))
                lastEnd = content.length
                break
            }
        }
    }

    // Process remaining content
    if (lastEnd < content.length) {
        val remainingText = content.substring(lastEnd).trim()
        if (remainingText.isNotEmpty()) {
            blocks.add(MessageContentBlock.TextBlock(remainingText))
        }
    } else if (lastEnd == 0 && content.isNotEmpty()) {
        // No patterns matched, add entire content as text
        blocks.add(MessageContentBlock.TextBlock(content))
    }
}


/**
 * Extract file path from tool output
 */
private fun extractFilePath(toolName: String, output: String): String? {
    val fileTools = listOf("read_file", "write_file", "create_file", "delete_file", "patch_file")
    if (fileTools.any { toolName.lowercase().contains(it.replace("_", " ")) }) {
        val pathPattern = Regex("""([a-zA-Z0-9_\-./]+\.[a-zA-Z0-9]+)""")
        return pathPattern.find(output)?.value
    }
    return null
}

/**
 * Professional IDE-style AI message component with live status indicators
 * Displays structured content with collapsible sections for thinking, tools, and response
 */
@Composable
fun AiMessageContent(
    content: String,
    timestamp: String,
    isStreaming: Boolean = false,
    currentActivity: AiActivityStatus = AiActivityStatus.IDLE,
    modifier: Modifier = Modifier
) {
    val parsedBlocks = remember(content, isStreaming) {
        parseAiMessageContent(content, isStreaming)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live activity indicator when streaming
        if (isStreaming) {
            LiveActivityIndicator(status = currentActivity)
        }

        parsedBlocks.forEachIndexed { index, block ->
            when (block) {
                is MessageContentBlock.ThinkingBlock -> {
                    ThinkingSection(
                        content = block.content,
                        isActive = block.isActive && isStreaming
                    )
                }
                is MessageContentBlock.ReasoningBlock -> {
                    ReasoningSection(
                        content = block.content,
                        isActive = block.isActive && isStreaming
                    )
                }
                is MessageContentBlock.ToolCallBlock -> {
                    ToolCallSection(
                        toolName = block.toolName,
                        status = block.status,
                        output = block.output,
                        filePath = block.filePath,
                        isActive = block.isActive && isStreaming,
                        additions = block.additions,
                        deletions = block.deletions,
                        oldContent = block.oldContent,
                        newContent = block.newContent
                    )
                }
                is MessageContentBlock.TextBlock -> {
                    ResponseSection(
                        content = block.content,
                        timestamp = if (index == parsedBlocks.lastIndex && !isStreaming) timestamp else null,
                        isStreaming = index == parsedBlocks.lastIndex && isStreaming
                    )
                }
                is MessageContentBlock.CodeBlock -> {
                    CodeSection(
                        code = block.code,
                        language = block.language
                    )
                }
            }
        }
    }
}

/**
 * Live activity indicator showing what AI is currently doing
 */
@Composable
private fun LiveActivityIndicator(
    status: AiActivityStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "activity_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    if (status != AiActivityStatus.IDLE) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pulsing activity dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                        )
                )

                // Activity icon
                Icon(
                    imageVector = when (status) {
                        AiActivityStatus.THINKING -> Icons.Outlined.Psychology
                        AiActivityStatus.TYPING -> Icons.Outlined.TextFields
                        AiActivityStatus.CALLING_TOOL -> Icons.Outlined.Build
                        AiActivityStatus.WRITING_FILE -> Icons.Outlined.Edit
                        AiActivityStatus.READING_FILE -> Icons.Outlined.Description
                        AiActivityStatus.EXECUTING -> Icons.Outlined.Code
                        else -> Icons.Outlined.AutoAwesome
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                // Activity text
                Text(
                    text = when (status) {
                        AiActivityStatus.THINKING -> "Thinking..."
                        AiActivityStatus.TYPING -> "Typing..."
                        AiActivityStatus.CALLING_TOOL -> "Calling tool..."
                        AiActivityStatus.WRITING_FILE -> "Writing file..."
                        AiActivityStatus.READING_FILE -> "Reading file..."
                        AiActivityStatus.EXECUTING -> "Executing..."
                        else -> "Processing..."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = PoppinsFontFamily,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Collapsible reasoning section - separate from thinking
 */
@Composable
private fun ReasoningSection(
    content: String,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(200),
        label = "arrow_rotation"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Text(
                    text = "Reasoning",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                if (isActive) {
                    PulsingDots()
                }

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    SelectionContainer {
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = PoppinsFontFamily,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Collapsible thinking section
 */
@Composable
private fun ThinkingSection(
    content: String,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(200),
        label = "arrow_rotation"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Text(
                    text = "Thinking",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isActive) {
                    PulsingDots()
                }

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    SelectionContainer {
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = PoppinsFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated pulsing dots indicator
 */
@Composable
private fun PulsingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_alpha_$index"
            )
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.tertiary.copy(alpha = alpha)
                    )
            )
        }
    }
}

/**
 * Tool call section with collapsible output and diff view for file operations
 */
@Composable
private fun ToolCallSection(
    toolName: String,
    status: ToolStatus,
    output: String?,
    filePath: String?,
    isActive: Boolean = false,
    additions: Int = 0,
    deletions: Int = 0,
    oldContent: String? = null,
    newContent: String? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val statusColor = when (status) {
        ToolStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        ToolStatus.ERROR -> MaterialTheme.colorScheme.error
        ToolStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
    }

    // Check if this is a file modification tool that should show diff
    val isFileModificationTool = toolName.lowercase().let { name ->
        name.contains("write") || name.contains("patch") ||
        name.contains("create") || name.contains("edit") ||
        name.contains("modify") || name.contains("update")
    }
    val hasDiffData = oldContent != null && newContent != null && oldContent != newContent
    val showDiffView = isFileModificationTool && hasDiffData && status == ToolStatus.SUCCESS

    // Compute diff once and cache it
    val diffResult = remember(oldContent, newContent) {
        if (showDiffView && oldContent != null && newContent != null) {
            DiffUtils.computeDiff(oldContent, newContent)
        } else null
    }

    val displayAdditions = if (additions > 0) additions else diffResult?.additions ?: 0
    val displayDeletions = if (deletions > 0) deletions else diffResult?.deletions ?: 0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = when (status) {
            ToolStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            ToolStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            ToolStatus.RUNNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)
        },
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = output != null || showDiffView) { isExpanded = !isExpanded }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tool icon with activity animation
                Box {
                    Icon(
                        imageVector = getToolIcon(toolName),
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .then(
                                if (isActive) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "tool_pulse")
                                    val scale by infiniteTransition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = 1.2f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(500),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "tool_scale"
                                    )
                                    Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                                } else Modifier
                            ),
                        tint = statusColor
                    )
                }

                // Tool name
                Text(
                    text = toolName,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // File path if available
                filePath?.let { path ->
                    Text(
                        text = path.substringAfterLast("/"),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(80.dp)
                    )
                }

                // Diff summary badge for file modifications
                if (showDiffView || (displayAdditions > 0 || displayDeletions > 0)) {
                    DiffSummaryBadge(
                        additions = displayAdditions,
                        deletions = displayDeletions
                    )
                }

                // Status icon
                Icon(
                    imageVector = when (status) {
                        ToolStatus.SUCCESS -> Icons.Outlined.CheckCircle
                        ToolStatus.ERROR -> Icons.Outlined.Error
                        ToolStatus.RUNNING -> Icons.Outlined.AutoAwesome
                    },
                    contentDescription = status.name,
                    modifier = Modifier.size(16.dp),
                    tint = statusColor
                )

                // Expand indicator if there's output or diff
                if (output != null || showDiffView) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded && (output != null || showDiffView),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )

                    // Show diff view for file modifications
                    if (showDiffView) {
                        CollapsibleDiffView(
                            filePath = filePath ?: "file",
                            oldContent = oldContent ?: "",
                            newContent = newContent ?: "",
                            toolName = toolName,
                            isSuccess = status == ToolStatus.SUCCESS,
                            modifier = Modifier.padding(8.dp),
                            initiallyExpanded = true
                        )
                    }

                    // Show text output if available (and not just diff)
                    if (output != null && (!showDiffView || output.isNotBlank())) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = output,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Get appropriate icon for tool type
 */
private fun getToolIcon(toolName: String): ImageVector {
    val name = toolName.lowercase()
    return when {
        name.contains("read") && name.contains("file") -> Icons.Outlined.Description
        name.contains("write") || name.contains("create") || name.contains("patch") -> Icons.Outlined.Edit
        name.contains("list") || name.contains("folder") -> Icons.Outlined.FolderOpen
        name.contains("delete") -> Icons.Outlined.Error
        name.contains("code") || name.contains("execute") -> Icons.Outlined.Code
        else -> Icons.Outlined.Build
    }
}

/**
 * Response text section with markdown rendering and Poppins font
 */
@Composable
private fun ResponseSection(
    content: String,
    timestamp: String?,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Markdown rendered content
        MarkdownText(
            markdown = content,
            modifier = Modifier.fillMaxWidth(),
            textColor = MaterialTheme.colorScheme.onSurface,
            linkColor = MaterialTheme.colorScheme.primary
        )

        // Timestamp and streaming indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isStreaming) {
                StreamingIndicator()
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            timestamp?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = PoppinsFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Streaming indicator with animated dots
 */
@Composable
private fun StreamingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "stream_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
        )
        Text(
            text = "Generating",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = PoppinsFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Code block section with syntax highlighting appearance
 */
@Composable
private fun CodeSection(
    code: String,
    language: String?,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language?.uppercase() ?: "CODE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SelectionContainer {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
