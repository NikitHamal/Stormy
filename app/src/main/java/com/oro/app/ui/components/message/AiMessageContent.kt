package com.oro.app.ui.components.message

import com.oro.app.ui.brand.OrobiLoader
import com.oro.app.ui.brand.OrobiWorking
import com.oro.app.ui.brand.OroSparkle
import com.oro.app.ui.brand.ShimmerText
import com.oro.app.ui.components.agent.ToolCallEntry
import com.oro.app.ui.components.agent.ToolCallGroup
import com.oro.app.ui.components.agent.ToolCallStatus
import com.oro.app.ui.components.agent.ToolCard
import com.oro.app.ui.theme.Space
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.components.agent.ToolPresentations
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oro.app.ui.components.MarkdownText
import java.util.Locale

enum class AiActivityStatus {
    IDLE,
    THINKING,
    TYPING,
    CALLING_TOOL,
    EXECUTING,
    READING_FILE,
    WRITING_FILE
}

sealed class MessageContentBlock {
    data class TextBlock(val content: String) : MessageContentBlock()

    data class CodeBlock(
        val code: String,
        val language: String? = null,
        val isActive: Boolean = false
    ) : MessageContentBlock()

    data class ThinkingBlock(val content: String, val isActive: Boolean = false) : MessageContentBlock()
    data class ReasoningBlock(val content: String, val isActive: Boolean = false) : MessageContentBlock()

    /** Two or more adjacent tool calls, shown as one collapsible group. */
    data class ToolRunBlock(
        val calls: List<ToolCallBlock>
    ) : MessageContentBlock()

    data class ToolCallBlock(
        val toolName: String,
        val status: ToolStatus,
        val output: String? = null,
        val filePath: String? = null,
        val additions: Int = 0,
        val deletions: Int = 0,
        val oldContent: String? = null,
        val newContent: String? = null,
        val isActive: Boolean = false
    ) : MessageContentBlock()
}

enum class ToolStatus { RUNNING, SUCCESS, ERROR }

/**
 * Fold runs of two or more adjacent tool calls into a single block.
 *
 * A lone call stays a card: grouping one thing adds a disclosure the user has
 * to open to see a single row, which is strictly worse than just showing it.
 */
private fun groupConsecutiveToolCalls(
    blocks: List<MessageContentBlock>
): List<MessageContentBlock> {
    val out = mutableListOf<MessageContentBlock>()
    var run = mutableListOf<MessageContentBlock.ToolCallBlock>()

    fun flush() {
        when {
            run.size >= 2 -> out += MessageContentBlock.ToolRunBlock(run.toList())
            run.size == 1 -> out += run.first()
        }
        run = mutableListOf()
    }

    blocks.forEach { block ->
        if (block is MessageContentBlock.ToolCallBlock) {
            run += block
        } else {
            flush()
            out += block
        }
    }
    flush()
    return out
}

/** Public entry point used by the UI. */
fun parseAiMessageContent(content: String, isStreaming: Boolean = false): List<MessageContentBlock> {
    if (content.isBlank()) return emptyList()

    val blocks = mutableListOf<MessageContentBlock>()
    val segments = splitByToolCalls(content)

    segments.forEachIndexed { index, segment ->
        val streamingTail = isStreaming && index == segments.lastIndex

        when (segment.type) {
            SegmentType.TEXT -> processTextSegment(segment.content, blocks, streamingTail)

            SegmentType.TOOL_CALL -> {
                val output = segment.toolOutput?.trimEnd()?.ifBlank { null }
                val filePath = output?.let { extractFilePath(segment.toolName.orEmpty(), it) }
                val (adds, dels) = computeUnifiedDiffSummary(output)
                val (oldC, newC) = output?.let(::extractOldNewContent) ?: (null to null)

                blocks.add(
                    MessageContentBlock.ToolCallBlock(
                        toolName = segment.toolName ?: "Unknown Tool",
                        status = segment.toolStatus ?: ToolStatus.RUNNING,
                        output = output,
                        filePath = filePath,
                        additions = adds,
                        deletions = dels,
                        oldContent = oldC,
                        newContent = newC,
                        isActive = streamingTail && (segment.toolStatus ?: ToolStatus.RUNNING) == ToolStatus.RUNNING
                    )
                )
            }
        }
    }

    return coalesceTextBlocks(blocks).filterNot { it is MessageContentBlock.TextBlock && it.content.isBlank() }
}

/* ----------------------------- parsing: tool calls ----------------------------- */

private enum class SegmentType { TEXT, TOOL_CALL }

private data class ContentSegment(
    val type: SegmentType,
    val content: String,
    val toolName: String? = null,
    val toolStatus: ToolStatus? = null,
    val toolOutput: String? = null
)

/**
 * Splits content into TEXT and TOOL_CALL segments.
 *
 * Accepts multiple formats:
 *  🔧 **Tool Name** ✅ (optional trailing text) - markdown bold
 *  🔧 Tool Name ✅ (optional trailing text) - plain text
 *  OR with emoji on newline:
 *  🔧 **Tool Name**
 *  ✅ (optional trailing text)
 */
private fun splitByToolCalls(content: String): List<ContentSegment> {
    val matches = TOOL_HEADER_REGEX.findAll(content).toList()
    if (matches.isEmpty()) return listOf(ContentSegment(type = SegmentType.TEXT, content = content))

    val segments = mutableListOf<ContentSegment>()
    var cursor = 0

    matches.forEachIndexed { idx, match ->
        val toolStart = match.range.first
        val headerEndExclusive = match.range.last + 1
        
        // Determine where this tool's output logically ends
        // It ends at the next tool header, OR at the start of a thinking/reasoning block
        val nextToolStart = matches.getOrNull(idx + 1)?.range?.first ?: content.length
        
        // Look for thinking/reasoning tags that might appear before the next tool
        val tagMatch = OPEN_TAG_REGEX.find(content, headerEndExclusive)
        
        // If we found a tag, and it's before the next tool, that's our effective end
        val effectiveEnd = if (tagMatch != null && tagMatch.range.first < nextToolStart) {
            tagMatch.range.first
        } else {
            nextToolStart
        }
        
        val toolEndExclusive = effectiveEnd

        if (cursor < toolStart) {
            val before = content.substring(cursor, toolStart)
            if (before.isNotEmpty()) segments.add(ContentSegment(SegmentType.TEXT, before))
        }

        val toolName = match.groupValues.getOrNull(1)?.trim().orEmpty()
        val emoji = match.groupValues.getOrNull(2)
        val status = when (emoji) {
            "✅" -> ToolStatus.SUCCESS
            "❌" -> ToolStatus.ERROR
            else -> ToolStatus.RUNNING // ⏳ or anything else
        }

        // Extract output only up to the effective end
        val rawOutput = if (extractOutput(content, headerEndExclusive, toolEndExclusive).isBlank() && status == ToolStatus.RUNNING) {
            "" // Don't show empty output for running tools
        } else {
            content.substring(headerEndExclusive, toolEndExclusive)
        }
        
        val output = rawOutput.trimEnd('\n', '\r')

        segments.add(
            ContentSegment(
                type = SegmentType.TOOL_CALL,
                content = content.substring(toolStart, toolEndExclusive), // Store full raw segment
                toolName = toolName.ifBlank { "Unknown Tool" },
                toolStatus = status,
                toolOutput = output
            )
        )

        cursor = toolEndExclusive
    }

    if (cursor < content.length) {
        val tail = content.substring(cursor)
        if (tail.isNotEmpty()) segments.add(ContentSegment(SegmentType.TEXT, tail))
    }

    return segments
}

private fun extractOutput(content: String, start: Int, end: Int): String {
    if (start >= end) return ""
    return content.substring(start, end)
}

private val TOOL_HEADER_REGEX = Regex(
    // Start-of-line: 🔧 optionally **Tool** or just Tool, then whitespace/newline, emoji, optional trailing text.
    // Supports both markdown bold format (🔧 **Tool Name** ✅) and plain text (🔧 Tool Name ✅)
    // No \b after the status emoji.
    //
    // \b asserts a word boundary, which needs a word character on one side.
    // An emoji is not a word character and the next character is a space, so
    // the assertion could never hold -- the pattern matched nothing, every
    // tool call fell through to the markdown renderer, and the transcript read
    // as "🔧 **List Files** ✅ Directory is empty" in plain text.
    pattern = """(?m)^\s*🔧\s*(?:\*\*)?([^*✅❌⏳\r\n]+?)(?:\*\*)?\s*(?:\r?\n|[ \t]+)(✅|❌|⏳)[^\r\n]*\r?\n?""",
    options = setOf(RegexOption.MULTILINE)
)

/* ----------------------------- parsing: text segment ----------------------------- */

private enum class OpenKind { THINKING, REASONING }

private val OPEN_TAG_REGEX = Regex("""(?i)<\s*(thinking|think|reasoning|reason)\s*>""")
private val CLOSE_THINKING_REGEX = Regex("""(?i)</\s*(thinking|think)\s*>""")
private val CLOSE_REASONING_REGEX = Regex("""(?i)</\s*(reasoning|reason)\s*>""")

// Code-fence start: at line-start, optional indentation, then ``` or ~~~, optional language, then newline.
private val FENCE_START_REGEX = Regex("""(?m)^\s*(```|~~~)\s*([\w.+-]+)?\s*\r?\n""")

private fun processTextSegment(
    content: String,
    blocks: MutableList<MessageContentBlock>,
    isStreaming: Boolean
) {
    if (content.isBlank()) return

    val buf = StringBuilder()
    var i = 0

    fun flushText() {
        if (buf.isNotEmpty()) {
            blocks.add(MessageContentBlock.TextBlock(buf.toString()))
            buf.clear()
        }
    }

    while (i < content.length) {
        // 1) Code fences (outside thinking/reasoning only)
        val fenceStart = FENCE_START_REGEX.find(content, i)
        if (fenceStart != null && fenceStart.range.first == i) {
            flushText()

            val fence = fenceStart.groupValues[1]
            val language = fenceStart.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
            val codeStart = fenceStart.range.last + 1

            val fenceEndRegex = Regex("""(?m)^\s*${Regex.escape(fence)}\s*$""")
            val fenceEnd = fenceEndRegex.find(content, codeStart)

            if (fenceEnd == null) {
                if (isStreaming) {
                    val code = content.substring(codeStart).trimEnd()
                    blocks.add(MessageContentBlock.CodeBlock(code = code, language = language, isActive = true))
                    return
                } else {
                    buf.append(content.substring(i))
                    break
                }
            } else {
                val code = content.substring(codeStart, fenceEnd.range.first).trimEnd('\n', '\r')
                blocks.add(MessageContentBlock.CodeBlock(code = code, language = language, isActive = false))
                i = fenceEnd.range.last + 1
                if (i < content.length && content[i] == '\n') i++
                continue
            }
        }

        // 2) <thinking>/<reasoning> tags
        val open = OPEN_TAG_REGEX.find(content, i)
        if (open != null && open.range.first == i) {
            val tag = open.groupValues[1].lowercase(Locale.ROOT)
            val kind = when (tag) {
                "thinking", "think" -> OpenKind.THINKING
                else -> OpenKind.REASONING
            }

            flushText()

            val bodyStart = open.range.last + 1
            val closeRegex = when (kind) {
                OpenKind.THINKING -> CLOSE_THINKING_REGEX
                OpenKind.REASONING -> CLOSE_REASONING_REGEX
            }

            val close = closeRegex.find(content, bodyStart)
            if (close == null) {
                if (isStreaming) {
                    val body = content.substring(bodyStart).trimEnd()
                    when (kind) {
                        OpenKind.THINKING -> blocks.add(MessageContentBlock.ThinkingBlock(body.trim(), isActive = true))
                        OpenKind.REASONING -> blocks.add(MessageContentBlock.ReasoningBlock(body.trim(), isActive = true))
                    }
                    return
                } else {
                    buf.append(content.substring(i))
                    break
                }
            } else {
                val body = content.substring(bodyStart, close.range.first).trim()
                when (kind) {
                    OpenKind.THINKING -> blocks.add(MessageContentBlock.ThinkingBlock(body, isActive = false))
                    OpenKind.REASONING -> blocks.add(MessageContentBlock.ReasoningBlock(body, isActive = false))
                }
                i = close.range.last + 1
                continue
            }
        }

        // 3) Default: consume one char
        buf.append(content[i])
        i++
    }

    flushText()
}

/* ----------------------------- parsing helpers ----------------------------- */

private fun coalesceTextBlocks(blocks: List<MessageContentBlock>): List<MessageContentBlock> {
    if (blocks.isEmpty()) return emptyList()

    val out = mutableListOf<MessageContentBlock>()
    var pendingText: StringBuilder? = null

    fun flushPending() {
        val p = pendingText ?: return
        out.add(MessageContentBlock.TextBlock(p.toString()))
        pendingText = null
    }

    for (b in blocks) {
        when (b) {
            is MessageContentBlock.TextBlock -> {
                val p = pendingText ?: StringBuilder().also { pendingText = it }
                p.append(b.content)
            }
            else -> {
                flushPending()
                out.add(b)
            }
        }
    }

    flushPending()
    return out
}

/**
 * Best-effort extraction of a file path referenced by a file-related tool call.
 * Also tries unified-diff headers (---/+++).
 */
private fun extractFilePath(toolName: String, output: String): String? {
    val normalized = toolName.lowercase(Locale.ROOT).replace(Regex("""[_\-\s]+"""), "")
    val fileTools = listOf(
        "readfile", "writefile", "createfile", "deletefile", "patchfile", "editfile", "modifyfile", "updatefile"
    )
    val looksLikeFileTool = fileTools.any { normalized.contains(it) }

    val explicitPath = Regex("""(?im)^\s*(?:file|path)\s*[:=]\s*([^\s]+)\s*$""")
        .find(output)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    if (explicitPath != null) return explicitPath

    val diffPath = Regex("""(?m)^\s*(?:\+\+\+|---)\s+[ab]/([^\s]+)\s*$""")
        .find(output)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    if (diffPath != null) return diffPath
    if (!looksLikeFileTool) return null

    val candidates = listOf(
        Regex("""["']([^"'\r\n]+\.[a-zA-Z0-9]{1,10})["']"""),
        Regex("""`([^`\r\n]+\.[a-zA-Z0-9]{1,10})`"""),
        Regex("""(?i)(?:^|[\s:(])([a-z0-9_\-./\\]+?\.[a-z0-9]{1,10})(?:$|[\s),.])""")
    )

    return candidates.asSequence()
        .mapNotNull { rx -> rx.find(output)?.groupValues?.getOrNull(1)?.trim() }
        .firstOrNull { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) }
}

private fun computeUnifiedDiffSummary(output: String?): Pair<Int, Int> {
    if (output.isNullOrBlank()) return 0 to 0
    val diffText = extractDiffText(output) ?: return 0 to 0

    var adds = 0
    var dels = 0

    diffText.lineSequence().forEach { line ->
        when {
            line.startsWith("+++") || line.startsWith("---") -> Unit
            line.startsWith("+") -> adds++
            line.startsWith("-") -> dels++
        }
    }

    return adds to dels
}

private fun extractDiffText(output: String): String? {
    val fenced = Regex("""(?is)```diff\s*\r?\n(.*?)\r?\n```""")
        .find(output)
        ?.groupValues
        ?.getOrNull(1)

    if (!fenced.isNullOrBlank()) return fenced

    val looksLikeUnified = output.contains("\n@@") && (output.contains("\n+++ ") || output.contains("\n--- "))
    return if (looksLikeUnified) output else null
}

/**
 * Tries to extract "before/after" contents from tool output, when the tool prints both versions.
 * Best-effort and permissive by design.
 */
private fun extractOldNewContent(output: String): Pair<String?, String?> {
    fun extract(label: String): String? {
        val rx = Regex(
            pattern = """(?is)\b$label(?:_content)?\b\s*[:=]\s*```[^\n]*\r?\n(.*?)\r?\n```""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        return rx.find(output)?.groupValues?.getOrNull(1)?.trim()
    }

    val old = extract("old") ?: extract("before") ?: extract("original")
    val new = extract("new") ?: extract("after") ?: extract("updated")
    return old to new
}

/* ----------------------------- UI ----------------------------- */

@Composable
fun AiMessageContent(
    content: String,
    timestamp: String,
    isStreaming: Boolean = false,
    currentActivity: AiActivityStatus = AiActivityStatus.IDLE,
    modifier: Modifier = Modifier
) {
    // During streaming, use simpler parsing to reduce lag
    // Full parsing is only done when content length changes significantly or streaming stops
    // Always parse fully.
    //
    // The old guard fell back to a lighter parser once a streaming message
    // passed 500 characters -- which is most of them -- so tool calls rendered
    // as raw text for the entire length of any real agent run and only
    // snapped into cards once streaming stopped. The parse is memoised on
    // content, so it runs once per emitted chunk rather than per frame.
    val shouldDoFullParsing = true

    val parsedBlocks = remember(content, isStreaming, shouldDoFullParsing) {
        if (shouldDoFullParsing) {
            parseAiMessageContent(content, isStreaming)
        } else {
            // During streaming with large content, just do minimal parsing
            parseStreamingContent(content)
        }
    }

    // Check if content is empty/minimal (only show generating indicator when truly empty)
    val hasSubstantialContent = content.trim().length > 10 || parsedBlocks.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Show minimal generating indicator only when streaming with no substantial content
        if (isStreaming && !hasSubstantialContent) {
            MinimalGeneratingIndicator()
        }

        // Consecutive tool calls collapse into one group.
        //
        // Rendered individually, a dozen calls from one agent turn dominate the
        // transcript and push the assistant's actual answer off screen. Runs
        // are detected here rather than in the parser so the parser stays a
        // pure text-to-blocks function.
        val grouped = remember(parsedBlocks) { groupConsecutiveToolCalls(parsedBlocks) }

        grouped.forEach { block ->
            when (block) {
                is MessageContentBlock.ToolRunBlock -> {
                    ToolCallGroup(
                        entries = block.calls.map { call ->
                            ToolCallEntry(
                                toolName = call.toolName,
                                subject = call.filePath,
                                status = call.status.toToolCallStatus(),
                                additions = call.additions,
                                deletions = call.deletions,
                                // Carried through rather than dropped: these
                                // are what each card opens to show.
                                output = call.output,
                                oldContent = call.oldContent,
                                newContent = call.newContent
                            )
                        },
                        // Expanded while anything is still running, so the user
                        // can watch; collapsed once the run finishes, so the
                        // answer is what remains on screen.
                        initiallyExpanded = block.calls.any {
                            it.status == ToolStatus.RUNNING
                        }
                    )
                }

                is MessageContentBlock.TextBlock ->
                    ResponseSection(markdown = block.content)

                is MessageContentBlock.CodeBlock ->
                    CodeSection(code = block.code, language = block.language, isActive = block.isActive && isStreaming)

                is MessageContentBlock.ThinkingBlock ->
                    ThinkingSection(content = block.content, isActive = block.isActive && isStreaming)

                is MessageContentBlock.ReasoningBlock ->
                    ReasoningSection(content = block.content, isActive = block.isActive && isStreaming)

                is MessageContentBlock.ToolCallBlock ->
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
        }

        // Show inline generating indicator at the end when streaming with content
        if (isStreaming && hasSubstantialContent) {
            InlineGeneratingIndicator()
        }

        Spacer(Modifier.height(2.dp))
        Text(
            text = timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Simplified parsing for streaming content to reduce CPU usage during streaming.
 * Only performs basic text/code fence detection without heavy regex parsing.
 */
private fun parseStreamingContent(content: String): List<MessageContentBlock> {
    // During streaming, just show content as text with basic code fence support
    val blocks = mutableListOf<MessageContentBlock>()

    // Simple check for code fences
    val fencePattern = Regex("""```(\w+)?\n([\s\S]*?)```""")
    var lastEnd = 0

    fencePattern.findAll(content).forEach { match ->
        // Add text before code block
        if (match.range.first > lastEnd) {
            val text = content.substring(lastEnd, match.range.first).trim()
            if (text.isNotEmpty()) {
                blocks.add(MessageContentBlock.TextBlock(text))
            }
        }

        // Add code block
        val lang = match.groupValues[1].takeIf { it.isNotEmpty() }
        val code = match.groupValues[2]
        blocks.add(MessageContentBlock.CodeBlock(code = code, language = lang, isActive = false))

        lastEnd = match.range.last + 1
    }

    // Add remaining text
    if (lastEnd < content.length) {
        val remaining = content.substring(lastEnd).trim()
        if (remaining.isNotEmpty()) {
            // Check if we have an unclosed code fence (streaming)
            val unclosedFence = Regex("""```(\w+)?\n([\s\S]*)$""").find(remaining)
            if (unclosedFence != null) {
                val textBefore = remaining.substring(0, unclosedFence.range.first).trim()
                if (textBefore.isNotEmpty()) {
                    blocks.add(MessageContentBlock.TextBlock(textBefore))
                }
                val lang = unclosedFence.groupValues[1].takeIf { it.isNotEmpty() }
                val code = unclosedFence.groupValues[2]
                blocks.add(MessageContentBlock.CodeBlock(code = code, language = lang, isActive = true))
            } else {
                blocks.add(MessageContentBlock.TextBlock(remaining))
            }
        }
    }

    return if (blocks.isEmpty()) listOf(MessageContentBlock.TextBlock(content)) else blocks
}

/**
 * Shown while a reply has started but nothing readable has arrived yet:
 * Orobi's loader and a label, the same indicator as everywhere the assistant
 * is busy.
 */
@Composable
private fun MinimalGeneratingIndicator(modifier: Modifier = Modifier) {
    OrobiWorking(label = "Thinking", modifier = modifier.padding(vertical = 4.dp))
}

/** At the tail of a reply that is still streaming: just the loader. */
@Composable
private fun InlineGeneratingIndicator(modifier: Modifier = Modifier) {
    OrobiLoader(
        modifier = modifier
            .padding(top = 4.dp)
            .width(22.dp),
        contentDescription = "Still writing"
    )
}

/** Small running indicator for section headers (thinking, active code). */
@Composable
private fun ActiveLoader(modifier: Modifier = Modifier) {
    OrobiLoader(modifier = modifier.width(18.dp), contentDescription = null)
}

@Composable
private fun ResponseSection(markdown: String, modifier: Modifier = Modifier) {
    MarkdownText(
        markdown = markdown,
        modifier = modifier.fillMaxWidth(),
        textColor = MaterialTheme.colorScheme.onSurface,
        linkColor = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ThinkingSection(content: String, isActive: Boolean, modifier: Modifier = Modifier) {
    CollapsibleInsightSection(
        title = "Thinking",
        content = content,
        isActive = isActive,
        modifier = modifier
    )
}

@Composable
private fun ReasoningSection(content: String, isActive: Boolean, modifier: Modifier = Modifier) {
    CollapsibleInsightSection(
        title = "Reasoning",
        content = content,
        isActive = isActive,
        modifier = modifier
    )
}

@Composable
private fun CollapsibleInsightSection(
    title: String,
    content: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "expand_rotation"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Orobi's loader while the model is still thinking, its
                // sparkle once the thought is complete.
                Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                    if (isActive) {
                        ActiveLoader()
                    } else {
                        OroSparkle(
                            modifier = Modifier.size(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                if (isActive) {
                    Box(Modifier.weight(1f)) {
                        ShimmerText(title, MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    MarkdownText(
                        markdown = content,
                        modifier = Modifier.fillMaxWidth(),
                        textColor = MaterialTheme.colorScheme.onSurface,
                        linkColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeSection(
    code: String,
    language: String?,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = language?.uppercase(Locale.ROOT) ?: "CODE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        ActiveLoader()
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy code")
                    }
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

/**
 * A single tool call.
 *
 * Delegates to [ToolCard] so a lone call and a call inside a run render
 * identically. They used to be two separate implementations that drifted:
 * the card showed diffs and the group's rows showed nothing, so whether the
 * user could see what a write did depended on how many calls happened to
 * surround it.
 */
@Composable
private fun ToolCallSection(
    toolName: String,
    status: ToolStatus,
    output: String?,
    filePath: String?,
    isActive: Boolean,
    additions: Int,
    deletions: Int,
    oldContent: String?,
    newContent: String?,
    modifier: Modifier = Modifier
) {
    ToolCard(
        entry = ToolCallEntry(
            toolName = toolName,
            subject = filePath,
            status = status.toToolCallStatus(),
            additions = additions,
            deletions = deletions,
            output = output,
            oldContent = oldContent,
            newContent = newContent
        ),
        modifier = modifier,
        // A call still in flight opens itself, so its output streams into
        // view rather than waiting for a tap that the user cannot know to
        // make.
        initiallyExpanded = isActive && status == ToolStatus.RUNNING
    )
}

private fun ToolStatus.toToolCallStatus(): ToolCallStatus = when (this) {
    ToolStatus.SUCCESS -> ToolCallStatus.Success
    ToolStatus.ERROR -> ToolCallStatus.Error
    ToolStatus.RUNNING -> ToolCallStatus.Running
}
