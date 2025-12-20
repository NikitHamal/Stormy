package com.codex.stormy.ui.components

import android.graphics.Typeface
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.codex.stormy.R
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * A Compose component that renders markdown text using Markwon
 * Supports code blocks, lists, tables, links, and other markdown features
 * Uses Poppins font for text content
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    codeBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val clipboardManager = LocalClipboardManager.current

    // Parse markdown content for code blocks
    val parsedContent = remember(markdown) { parseMarkdownContent(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        parsedContent.forEach { block ->
            when (block) {
                is MarkdownBlock.Text -> {
                    MarkdownTextBlock(
                        text = block.content,
                        textColor = textColor,
                        linkColor = linkColor
                    )
                }
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockView(
                        code = block.code,
                        language = block.language,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(block.code))
                        },
                        isDarkTheme = isDarkTheme,
                        codeBackgroundColor = codeBackgroundColor
                    )
                }
                is MarkdownBlock.InlineCode -> {
                    InlineCodeView(
                        code = block.code,
                        isDarkTheme = isDarkTheme,
                        codeBackgroundColor = codeBackgroundColor
                    )
                }
            }
        }
    }
}

/**
 * Markdown text block rendered with Markwon using Poppins font
 */
@Composable
private fun MarkdownTextBlock(
    text: String,
    textColor: Color,
    linkColor: Color
) {
    val context = LocalContext.current

    // Load Poppins typeface
    val poppinsTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.poppins_regular_static)
    }

    val markwon = remember(context, textColor, linkColor) {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .build()
    }

    val spanned = remember(text, markwon) {
        markwon.toMarkdown(text)
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor.toArgb())
                setLinkTextColor(linkColor.toArgb())
                textSize = 14f
                setLineSpacing(0f, 1.3f)
                // Apply Poppins font
                typeface = poppinsTypeface ?: Typeface.DEFAULT
            }
        },
        update = { textView ->
            textView.setTextColor(textColor.toArgb())
            textView.setLinkTextColor(linkColor.toArgb())
            textView.typeface = poppinsTypeface ?: Typeface.DEFAULT
            markwon.setParsedMarkdown(textView, spanned)
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Fenced code block with syntax highlighting appearance and copy button
 */
@Composable
private fun CodeBlockView(
    code: String,
    language: String?,
    onCopy: () -> Unit,
    isDarkTheme: Boolean,
    codeBackgroundColor: Color
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(codeBackgroundColor)
    ) {
        // Header with language label and copy button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDarkTheme) Color(0xFF2D2D35)
                    else Color(0xFFE8E8EC)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language?.uppercase() ?: "CODE",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDarkTheme) Color(0xFF9A9AA0) else Color(0xFF6A6A70),
                fontFamily = FontFamily.Monospace
            )

            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.size(16.dp),
                    tint = if (isDarkTheme) Color(0xFF9A9AA0) else Color(0xFF6A6A70)
                )
            }
        }

        // Code content with horizontal scroll
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(12.dp)
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                ),
                color = if (isDarkTheme) Color(0xFFE5E1E6) else Color(0xFF1B1B1F)
            )
        }
    }
}

/**
 * Inline code span with subtle background
 */
@Composable
private fun InlineCodeView(
    code: String,
    isDarkTheme: Boolean,
    codeBackgroundColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(codeBackgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            ),
            color = if (isDarkTheme) Color(0xFFE06C75) else Color(0xFFD73A49)
        )
    }
}

/**
 * Represents different types of markdown blocks
 */
private sealed class MarkdownBlock {
    data class Text(val content: String) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock()
    data class InlineCode(val code: String) : MarkdownBlock()
}

/**
 * Parse markdown content to extract code blocks separately
 * This allows us to render code blocks with custom styling
 */
private fun parseMarkdownContent(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val codeBlockPattern = Regex("```(\\w*)\\n([\\s\\S]*?)```")

    var lastEnd = 0
    val matches = codeBlockPattern.findAll(markdown)

    for (match in matches) {
        // Add text before code block
        if (match.range.first > lastEnd) {
            val textBefore = markdown.substring(lastEnd, match.range.first).trim()
            if (textBefore.isNotEmpty()) {
                blocks.add(MarkdownBlock.Text(textBefore))
            }
        }

        // Add code block
        val language = match.groupValues[1].ifEmpty { null }
        val code = match.groupValues[2].trimEnd()
        blocks.add(MarkdownBlock.CodeBlock(code, language))

        lastEnd = match.range.last + 1
    }

    // Add remaining text after last code block
    if (lastEnd < markdown.length) {
        val remainingText = markdown.substring(lastEnd).trim()
        if (remainingText.isNotEmpty()) {
            blocks.add(MarkdownBlock.Text(remainingText))
        }
    }

    // If no code blocks found, treat entire content as text
    if (blocks.isEmpty() && markdown.isNotBlank()) {
        blocks.add(MarkdownBlock.Text(markdown))
    }

    return blocks
}

/**
 * Simplified markdown text for short messages
 * Does not handle code blocks, just basic formatting
 * Uses Poppins font
 */
@Composable
fun SimpleMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    linkColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current

    // Load Poppins typeface
    val poppinsTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.poppins_regular_static)
    }

    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .build()
    }

    val spanned = remember(markdown, markwon) {
        markwon.toMarkdown(markdown)
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor.toArgb())
                setLinkTextColor(linkColor.toArgb())
                textSize = 14f
                setLineSpacing(0f, 1.2f)
                // Apply Poppins font
                typeface = poppinsTypeface ?: Typeface.DEFAULT
            }
        },
        update = { textView ->
            textView.setTextColor(textColor.toArgb())
            textView.setLinkTextColor(linkColor.toArgb())
            textView.typeface = poppinsTypeface ?: Typeface.DEFAULT
            markwon.setParsedMarkdown(textView, spanned)
        },
        modifier = modifier
    )
}
