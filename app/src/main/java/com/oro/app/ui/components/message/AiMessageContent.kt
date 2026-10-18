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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.oro.app.ui.theme.OroTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
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

/** Inputs of one parse; see AiMessageContent. */
private data class ParseInput(val content: String, val isStreaming: Boolean)

/** The synchronous first parse, reused if the inputs come back unchanged. */
private class FirstParse(
    val content: String,
    val isStreaming: Boolean,
    val blocks: List<MessageContentBlock>
)

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
                var (adds, dels) = computeUnifiedDiffSummary(output)
                var (oldC, newC) = output?.let(::extractOldNewContent) ?: (null to null)
                val canonical = com.oro.app.data.ai.tools.ToolAliases.canonicalName(segment.toolName.orEmpty().lowercase(Locale.ROOT).replace(' ', '_'))
                if (canonical == "write_file" && output != null) {
                    // A write's card carries the written text; show it as all-new lines.
                    if (newC != null && oldC == null) oldC = ""
                    if (adds == 0) WROTE_LINES_REGEX.find(output)?.groupValues?.get(1)?.toIntOrNull()?.let { adds = it }
                } else if (oldC == null && newC == null && output != null) {
                    // Edits print a short "-old / +new" diff; turn it into before/after so the sheet draws a real diff.
                    miniDiffSides(output)?.let { (o, n) -> oldC = o; newC = n }
                }

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
        val tagEnd = if (tagMatch != null && tagMatch.range.first < nextToolStart) {
            tagMatch.range.first
        } else {
            nextToolStart
        }
        // A blank line also ends a tool's output: the agent writes cards with
        // no blank lines inside, so text after one (the final reply) renders
        // as text instead of disappearing into the last card.
        // (Searched from the header's own newline, which the header match
        // consumes, so "status line, blank line, reply" ends the card there.)
        val searchFrom = (headerEndExclusive - 1).coerceAtLeast(0)
        val blankLine = content.indexOf("\n\n", searchFrom)
        val effectiveEnd = if (blankLine in searchFrom until tagEnd) {
            maxOf(blankLine, headerEndExclusive)
        } else {
            tagEnd
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
private val FENCE_END_REGEXES: Map<String, Regex> = listOf("```", "~~~").associateWith { fence ->
    Regex("""(?m)^\s*${Regex.escape(fence)}\s*$""")
}

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

    // The next fence / tag match at or after i, cached. The loop used to call
    // find(content, i) for both patterns at *every character*, and each call
    // scans ahead to the next match (or the end of the message) -- quadratic
    // in the message length, re-run on every streamed chunk, and the main
    // source of chat lag on long replies. A match found from an earlier
    // position is still the first match at or after i as long as it starts at
    // or after i, so it only needs refreshing once i moves past it.
    var nextFence: MatchResult? = FENCE_START_REGEX.find(content, 0)
    var nextOpen: MatchResult? = OPEN_TAG_REGEX.find(content, 0)

    while (i < content.length) {
        if (nextFence != null && nextFence.range.first < i) {
            nextFence = FENCE_START_REGEX.find(content, i)
        }
        if (nextOpen != null && nextOpen.range.first < i) {
            nextOpen = OPEN_TAG_REGEX.find(content, i)
        }

        // 1) Code fences (outside thinking/reasoning only)
        val fenceStart = nextFence
        if (fenceStart != null && fenceStart.range.first == i) {
            flushText()

            val fence = fenceStart.groupValues[1]
            val language = fenceStart.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
            val codeStart = fenceStart.range.last + 1

            val fenceEndRegex = FENCE_END_REGEXES[fence]
                ?: Regex("""(?m)^\s*${Regex.escape(fence)}\s*$""")
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
        val open = nextOpen
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

        // 3) Default: plain text up to the next possible fence/tag start.
        // Neither cached match starts at i here, and both start after i, so
        // this always advances.
        val stop = minOf(
            fenceStart?.range?.first ?: content.length,
            open?.range?.first ?: content.length
        )
        buf.append(content, i, stop)
        i = stop
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

// Compiled once. These used to be built inside the extract* helpers, i.e.
// recompiled for every tool call of every message on every streamed chunk.
private val TOOL_NAME_SEPARATORS_REGEX = Regex("""[_\-\s]+""")
private val FILE_TOOL_NAMES = listOf(
    "readfile", "writefile", "createfile", "deletefile", "patchfile", "editfile", "modifyfile", "updatefile"
)
private val EXPLICIT_PATH_REGEX = Regex("""(?im)^\s*(?:file|path)\s*[:=]\s*([^\s]+)\s*$""")
private val DIFF_PATH_REGEX = Regex("""(?m)^\s*(?:\+\+\+|---)\s+[ab]/([^\s]+)\s*$""")
private val PATH_CANDIDATE_REGEXES = listOf(
    Regex("""["']([^"'\r\n]+\.[a-zA-Z0-9]{1,10})["']"""),
    Regex("""`([^`\r\n]+\.[a-zA-Z0-9]{1,10})`"""),
    Regex("""(?i)(?:^|[\s:(])([a-z0-9_\-./\\]+?\.[a-z0-9]{1,10})(?:$|[\s),.])""")
)
private val FENCED_DIFF_REGEX = Regex("""(?is)```diff\s*\r?\n(.*?)\r?\n```""")
private val LABELLED_CONTENT_REGEXES: Map<String, Regex> =
    listOf("old", "before", "original", "new", "after", "updated").associateWith { label ->
        Regex(
            pattern = """(?is)\b$label(?:_content)?\b\s*[:=]\s*```[^\n]*\r?\n(.*?)\r?\n```""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
    }
private val STREAM_FENCE_REGEX = Regex("""```(\w+)?\n([\s\S]*?)```""")
private val STREAM_UNCLOSED_FENCE_REGEX = Regex("""```(\w+)?\n([\s\S]*)$""")

/**
 * Best-effort extraction of a file path referenced by a file-related tool call.
 * Also tries unified-diff headers (---/+++).
 */
private fun extractFilePath(toolName: String, output: String): String? {
    val normalized = toolName.lowercase(Locale.ROOT).replace(TOOL_NAME_SEPARATORS_REGEX, "")
    val looksLikeFileTool = FILE_TOOL_NAMES.any { normalized.contains(it) }

    val explicitPath = EXPLICIT_PATH_REGEX
        .find(output)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    if (explicitPath != null) return explicitPath

    val diffPath = DIFF_PATH_REGEX
        .find(output)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    if (diffPath != null) return diffPath
    if (!looksLikeFileTool) return null

    return PATH_CANDIDATE_REGEXES.asSequence()
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

private val WROTE_LINES_REGEX = Regex("""(?:Created|Overwrote) .* \((\d+) lines\)""")

/** The "-" and "+" sides of a fenced mini diff, without its "… more lines" notes. */
private fun miniDiffSides(output: String): Pair<String, String>? {
    val diff = FENCED_DIFF_REGEX.find(output)?.groupValues?.getOrNull(1) ?: return null
    if (diff.contains("\n@@") || diff.startsWith("@@")) return null
    val old = StringBuilder(); val new = StringBuilder()
    diff.lineSequence().forEach { line ->
        when {
            line.startsWith("-… ") || line.startsWith("+… ") -> Unit
            line.startsWith("-") -> old.append(line.substring(1)).append('\n')
            line.startsWith("+") -> new.append(line.substring(1)).append('\n')
        }
    }
    if (old.isEmpty() && new.isEmpty()) return null
    return old.toString().trimEnd('\n') to new.toString().trimEnd('\n')
}

private fun extractDiffText(output: String): String? {
    val fenced = FENCED_DIFF_REGEX
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
        val rx = LABELLED_CONTENT_REGEXES.getValue(label)
        return rx.find(output)?.groupValues?.getOrNull(1)?.trim()
    }

    val old = extract("old") ?: extract("before") ?: extract("original")
    // Inside a card, blank lines travel as a single space (a blank line ends the card).
    val new = (extract("new") ?: extract("after") ?: extract("updated"))
        ?.lines()?.joinToString("\n") { if (it == " ") "" else it }
    return old to new
}

/* ----------------------------- UI ----------------------------- */

@Composable
fun AiMessageContent(
    content: String,
    timestamp: String,
    isStreaming: Boolean = false,
    currentActivity: AiActivityStatus = AiActivityStatus.IDLE,
    modifier: Modifier = Modifier,
    /**
     * False when the caller shows its own labelled working indicator (the chat
     * does, so the label can say "Running edit_file" or "Waiting for your
     * answer"); avoids two loaders on screen.
     */
    showWorkingIndicator: Boolean = true
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
    //
    // The first parse is synchronous, so a message scrolled into view (or
    // restored from history) appears fully formed with its final height and
    // the list does not jump. Every later change -- i.e. each streamed chunk
    // -- is parsed on Dispatchers.Default: the parse is a dozen regex passes
    // over the *whole* message, so on a long agent reply it grew to many
    // milliseconds of main-thread work per chunk. conflate() rather than
    // collectLatest: if chunks arrive faster than a parse completes, the
    // newest content is parsed next instead of every parse being cancelled
    // and the message freezing until streaming stops.
    val firstParse = remember {
        FirstParse(content, isStreaming, parseAiMessageContent(content, isStreaming))
    }
    var parsedBlocks by remember { mutableStateOf(firstParse.blocks) }
    val latestInput = rememberUpdatedState(ParseInput(content, isStreaming))
    LaunchedEffect(Unit) {
        snapshotFlow { latestInput.value }
            .conflate()
            .collect { input ->
                parsedBlocks = if (
                    input.content == firstParse.content && input.isStreaming == firstParse.isStreaming
                ) {
                    firstParse.blocks
                } else {
                    withContext(Dispatchers.Default) {
                        parseAiMessageContent(input.content, input.isStreaming)
                    }
                }
            }
    }

    // Check if content is empty/minimal (only show generating indicator when truly empty)
    // (Memoised: trim() copies the whole message, and this ran per recomposition.)
    val hasSubstantialContent = remember(content) { content.trim().length > 10 } ||
        parsedBlocks.isNotEmpty()

    // Lite mode drops the size animation: while streaming it restarts on
    // every chunk, so the message re-measures on every frame of the reply.
    val sizeAnimation = if (OroTheme.liteMode) {
        Modifier
    } else {
        Modifier.animateContentSize(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(sizeAnimation),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Show minimal generating indicator only when streaming with no substantial content
        if (isStreaming && showWorkingIndicator && !hasSubstantialContent) {
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
                    // Mapped once per block rather than on every recomposition
                    // of the message, so the group can skip when unchanged.
                    val entries = remember(block) { block.calls.map { call ->
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
                        } }
                    ToolCallGroup(
                        entries = entries,
                        // Expanded while anything is still running, so the user
                        // can watch; collapsed once the run finishes, so the
                        // answer is what remains on screen.
                        initiallyExpanded = block.calls.any {
                            it.status == ToolStatus.RUNNING
                        }
                    )
                }

                is MessageContentBlock.TextBlock ->
                    if (com.oro.app.data.ai.genui.GenUi.looksLikeUi(block.content)) {
                        // A UI block the model forgot to fence.
                        com.oro.app.ui.genui.GenUiBlock(source = block.content.trim(), streaming = false)
                    } else {
                        ResponseSection(markdown = block.content)
                    }

                is MessageContentBlock.CodeBlock ->
                    if (com.oro.app.data.ai.genui.GenUi.isUiFence(block.language) ||
                        (com.oro.app.data.ai.genui.GenUi.mayHoldUi(block.language) && com.oro.app.data.ai.genui.GenUi.looksLikeUi(block.code))
                    ) {
                        // A UI block: rendered as native components (A2UI), not as code.
                        com.oro.app.ui.genui.GenUiBlock(source = block.code, streaming = block.isActive && isStreaming)
                    } else {
                        CodeSection(code = block.code, language = block.language, isActive = block.isActive && isStreaming)
                    }

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
        if (isStreaming && showWorkingIndicator && hasSubstantialContent) {
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
    var lastEnd = 0

    STREAM_FENCE_REGEX.findAll(content).forEach { match ->
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
            val unclosedFence = STREAM_UNCLOSED_FENCE_REGEX.find(remaining)
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
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1_500); copied = false } }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Header: language in the app's font, a compact copy action.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language?.lowercase(Locale.ROOT)?.ifBlank { null } ?: "code",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (isActive) {
                    ActiveLoader()
                    Spacer(Modifier.width(8.dp))
                }
                androidx.compose.material3.TextButton(
                    onClick = { clipboard.setText(AnnotatedString(code)); copied = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (copied) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SelectionContainer {
                // Code scrolls sideways rather than wrapping, so indentation stays readable.
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    softWrap = false,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(14.dp)
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
