package com.codex.stormy.ui.screens.editor.code

import android.content.Context
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Callback for AI-powered code edit requests from text selection
 */
data class AiCodeEditRequest(
    val selectedText: String,
    val startLine: Int,
    val endLine: Int,
    val filePath: String,
    val fileName: String
)

/**
 * High-performance code editor view built on Rosemoe/Sora editor
 * Provides professional-grade syntax highlighting with built-in color schemes
 * Optimized for smooth scrolling on mobile devices
 */
@Composable
fun SoraCodeEditorView(
    content: String,
    onContentChange: (String) -> Unit,
    fileExtension: String,
    fileName: String,
    filePath: String,
    showLineNumbers: Boolean,
    wordWrap: Boolean,
    fontSize: Float,
    onAiEditRequest: ((AiCodeEditRequest) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    var editorInstance by remember { mutableStateOf<CodeEditor?>(null) }
    var lastExternalContent by remember { mutableStateOf(content) }
    var isUpdatingFromEditor by remember { mutableStateOf(false) }

    // Track text selection for AI edit button
    var hasSelection by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }
    var selectionStartLine by remember { mutableStateOf(0) }
    var selectionEndLine by remember { mutableStateOf(0) }

    // Update content when it changes externally (not from editor)
    LaunchedEffect(content) {
        if (!isUpdatingFromEditor && content != lastExternalContent) {
            editorInstance?.let { editor ->
                val currentText = editor.text.toString()
                if (currentText != content) {
                    withContext(Dispatchers.Main) {
                        editor.setText(content)
                    }
                }
            }
            lastExternalContent = content
        }
        isUpdatingFromEditor = false
    }

    // Update editor settings when they change
    LaunchedEffect(showLineNumbers, wordWrap, fontSize, editorInstance) {
        editorInstance?.let { editor ->
            withContext(Dispatchers.Main) {
                editor.isLineNumberEnabled = showLineNumbers
                editor.isWordwrap = wordWrap
                editor.setTextSize(fontSize)
                // Disable auto-completion feature
                editor.getComponent(EditorAutoCompletion::class.java)?.isEnabled = false
            }
        }
    }

    // Update theme when dark mode changes
    LaunchedEffect(isDarkTheme, editorInstance) {
        editorInstance?.let { editor ->
            withContext(Dispatchers.Main) {
                editor.colorScheme = createColorScheme(isDarkTheme, fileExtension)
            }
        }
    }

    // Update language/colors when file extension changes
    LaunchedEffect(fileExtension, editorInstance, isDarkTheme) {
        editorInstance?.let { editor ->
            withContext(Dispatchers.Main) {
                editor.setEditorLanguage(SimpleLanguageFactory.getLanguage(fileExtension))
                editor.colorScheme = createColorScheme(isDarkTheme, fileExtension)
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                createCodeEditor(ctx, isDarkTheme, fileExtension).also { editor ->
                    editorInstance = editor

                    // Set initial content
                    editor.setText(content)
                    lastExternalContent = content

                    // Configure editor settings
                    editor.isLineNumberEnabled = showLineNumbers
                    editor.isWordwrap = wordWrap
                    editor.setTextSize(fontSize)
                    // Auto-completion is disabled
                    editor.getComponent(EditorAutoCompletion::class.java)?.isEnabled = false

                    // Set up content change listener
                    editor.subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                        val newText = editor.text.toString()
                        if (newText != lastExternalContent) {
                            isUpdatingFromEditor = true
                            lastExternalContent = newText
                            onContentChange(newText)
                        }
                    }

                    // Set up selection change listener for AI edit feature
                    if (onAiEditRequest != null) {
                        editor.subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
                            val cursor = event.editor.cursor
                            if (cursor.isSelected) {
                                hasSelection = true
                                selectedText = event.editor.text.subSequence(
                                    cursor.left,
                                    cursor.right
                                ).toString()
                                selectionStartLine = cursor.leftLine
                                selectionEndLine = cursor.rightLine
                            } else {
                                hasSelection = false
                                selectedText = ""
                            }
                        }
                    }
                }
            },
            update = { _ ->
                // Editor updates are handled via LaunchedEffects
            }
        )

        // Floating AI Edit button - appears when text is selected
        AnimatedVisibility(
            visible = hasSelection && onAiEditRequest != null && selectedText.isNotEmpty(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = {
                    onAiEditRequest?.invoke(
                        AiCodeEditRequest(
                            selectedText = selectedText,
                            startLine = selectionStartLine,
                            endLine = selectionEndLine,
                            filePath = filePath,
                            fileName = fileName
                        )
                    )
                    // Clear selection state after triggering
                    hasSelection = false
                    selectedText = ""
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "AI Edit",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            editorInstance?.release()
            editorInstance = null
        }
    }
}

/**
 * Create and configure the CodeEditor instance with optimized scrolling
 */
private fun createCodeEditor(context: Context, isDarkTheme: Boolean, extension: String): CodeEditor {
    return object : CodeEditor(context) {
        override fun onTouchEvent(event: MotionEvent?): Boolean {
            // Handle touch events for smoother scrolling
            when (event?.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            return super.onTouchEvent(event)
        }
    }.apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Performance optimizations
        isHighlightCurrentBlock = true
        isHighlightCurrentLine = true
        isHighlightBracketPair = true
        setBlockLineEnabled(true)
        isCursorAnimationEnabled = true
        isEditable = true
        isScalable = true
        isUndoEnabled = true

        // Line number configuration
        isLineNumberEnabled = true
        setLineNumberMarginLeft(8f)

        // Hardware acceleration for smooth scrolling
        isHardwareAcceleratedDrawAllowed = true

        // Tab settings
        tabWidth = 2

        // Auto-completion settings
        getComponent(EditorAutoCompletion::class.java)?.apply {
            isEnabled = true
        }

        // Set monospace font for better code display
        typefaceText = Typeface.MONOSPACE
        typefaceLineNumber = Typeface.MONOSPACE

        // Cursor configuration
        setCursorWidth(2f)
        setInterceptParentHorizontalScrollIfNeeded(true)

        // Scrollbar configuration for smooth scrolling
        isScrollbarFadingEnabled = true

        // Disable over-scroll to prevent bouncy feel
        overScrollMode = View.OVER_SCROLL_NEVER

        // Accessibility
        contentDescription = "Code Editor"

        // Use SimpleLanguageFactory for syntax highlighting
        setEditorLanguage(SimpleLanguageFactory.getLanguage(extension))

        // Apply color scheme based on file type and theme
        colorScheme = createColorScheme(isDarkTheme, extension)
    }
}

/**
 * Create comprehensive color scheme with syntax-aware colors
 * Provides visual differentiation for different file types
 */
private fun createColorScheme(isDarkTheme: Boolean, extension: String): EditorColorScheme {
    return if (isDarkTheme) {
        createDarkColorScheme(extension)
    } else {
        createLightColorScheme(extension)
    }
}

/**
 * Get language-specific accent color for visual differentiation
 */
private fun getLanguageAccent(extension: String, isDark: Boolean): Int {
    val color = when (extension.lowercase()) {
        "html", "htm" -> if (isDark) Color(0xFFE06C75) else Color(0xFF800000)
        "css", "scss", "sass", "less" -> if (isDark) Color(0xFF61AFEF) else Color(0xFF0451A5)
        "js", "mjs", "jsx" -> if (isDark) Color(0xFFE5C07B) else Color(0xFFB07D2B)
        "ts", "tsx" -> if (isDark) Color(0xFF56B6C2) else Color(0xFF267F99)
        "json" -> if (isDark) Color(0xFF98C379) else Color(0xFF008000)
        "md", "markdown" -> if (isDark) Color(0xFFD19A66) else Color(0xFF795E26)
        "py" -> if (isDark) Color(0xFF56B6C2) else Color(0xFF0000FF)
        "kt", "kts" -> if (isDark) Color(0xFFC678DD) else Color(0xFFAF00DB)
        "java" -> if (isDark) Color(0xFFE06C75) else Color(0xFFE51400)
        "xml", "svg" -> if (isDark) Color(0xFFE5C07B) else Color(0xFF800000)
        else -> if (isDark) Color(0xFFABB2BF) else Color(0xFF1B1B1F)
    }
    return color.toArgb()
}

/**
 * Create dark color scheme for the editor
 */
private fun createDarkColorScheme(extension: String): EditorColorScheme {
    val accent = getLanguageAccent(extension, true)

    return EditorColorScheme().apply {
        // Editor background and foreground
        setColor(EditorColorScheme.WHOLE_BACKGROUND, Color(0xFF1E1E24).toArgb())
        setColor(EditorColorScheme.TEXT_NORMAL, Color(0xFFE5E1E6).toArgb())

        // Line numbers
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, Color(0xFF1E1E24).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER, Color(0xFF5A5A62).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, Color(0xFF9A9AA2).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_PANEL, Color(0xFF1E1E24).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, Color(0xFF6A6A72).toArgb())

        // Current line highlight
        setColor(EditorColorScheme.CURRENT_LINE, Color(0xFF2A2A32).toArgb())

        // Selection
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, Color(0xFF3A3A52).toArgb())

        // Cursor
        setColor(EditorColorScheme.SELECTION_INSERT, Color(0xFFBFC1FF).toArgb())
        setColor(EditorColorScheme.SELECTION_HANDLE, Color(0xFFBFC1FF).toArgb())

        // Block line
        setColor(EditorColorScheme.BLOCK_LINE, Color(0xFF3A3A42).toArgb())
        setColor(EditorColorScheme.BLOCK_LINE_CURRENT, accent)

        // Matched bracket
        setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, Color(0xFF4A4A62).toArgb())

        // Scrollbar
        setColor(EditorColorScheme.SCROLL_BAR_THUMB, Color(0xFF4A4A52).toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, Color(0xFF6A6A72).toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_TRACK, Color(0xFF1E1E24).toArgb())

        // Syntax highlighting - keywords (purple)
        setColor(EditorColorScheme.KEYWORD, Color(0xFFC678DD).toArgb())

        // Operators
        setColor(EditorColorScheme.OPERATOR, Color(0xFF56B6C2).toArgb())

        // Comments (gray italic)
        setColor(EditorColorScheme.COMMENT, Color(0xFF5C6370).toArgb())

        // Strings (green)
        setColor(EditorColorScheme.LITERAL, Color(0xFF98C379).toArgb())

        // Functions (blue)
        setColor(EditorColorScheme.FUNCTION_NAME, Color(0xFF61AFEF).toArgb())

        // Identifiers
        setColor(EditorColorScheme.IDENTIFIER_NAME, Color(0xFFE5E1E6).toArgb())
        setColor(EditorColorScheme.IDENTIFIER_VAR, Color(0xFFE5C07B).toArgb())

        // HTML/XML tags (red)
        setColor(EditorColorScheme.HTML_TAG, Color(0xFFE06C75).toArgb())
        setColor(EditorColorScheme.ATTRIBUTE_NAME, Color(0xFFD19A66).toArgb())
        setColor(EditorColorScheme.ATTRIBUTE_VALUE, Color(0xFF98C379).toArgb())

        // Completion window
        setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, Color(0xFF272730).toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_CORNER, Color(0xFF272730).toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, Color(0xFFE5E1E6).toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, Color(0xFF9A9AA2).toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, Color(0xFF3A3A52).toArgb())

        // Non-printable characters
        setColor(EditorColorScheme.NON_PRINTABLE_CHAR, Color(0xFF4A4A52).toArgb())

        // Underline
        setColor(EditorColorScheme.UNDERLINE, Color(0xFFBFC1FF).toArgb())

        // Sticky scroll
        setColor(EditorColorScheme.STICKY_SCROLL_DIVIDER, Color(0xFF3A3A42).toArgb())

        // Side block line
        setColor(EditorColorScheme.SIDE_BLOCK_LINE, Color(0xFF3A3A42).toArgb())

        // Text selected
        setColor(EditorColorScheme.TEXT_SELECTED, Color(0xFFFFFFFF).toArgb())

        // Diagnostic colors
        setColor(EditorColorScheme.PROBLEM_ERROR, Color(0xFFFF6B6B).toArgb())
        setColor(EditorColorScheme.PROBLEM_WARNING, Color(0xFFFFB74D).toArgb())
        setColor(EditorColorScheme.PROBLEM_TYPO, Color(0xFF64B5F6).toArgb())

        // Snippet-related
        setColor(EditorColorScheme.SNIPPET_BACKGROUND_EDITING, Color(0xFF3A3A52).toArgb())
        setColor(EditorColorScheme.SNIPPET_BACKGROUND_RELATED, Color(0xFF2A2A32).toArgb())
        setColor(EditorColorScheme.SNIPPET_BACKGROUND_INACTIVE, Color(0xFF232328).toArgb())

        // Hard wrap marker
        setColor(EditorColorScheme.HARD_WRAP_MARKER, Color(0xFF3A3A42).toArgb())
    }
}

/**
 * Create light color scheme for the editor
 */
private fun createLightColorScheme(extension: String): EditorColorScheme {
    val accent = getLanguageAccent(extension, false)

    return EditorColorScheme().apply {
        // Editor background and foreground
        setColor(EditorColorScheme.WHOLE_BACKGROUND, Color(0xFFFAFAFA).toArgb())
        setColor(EditorColorScheme.TEXT_NORMAL, Color(0xFF1B1B1F).toArgb())

        // Line numbers
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, Color(0xFFFAFAFA).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER, Color(0xFFB0B0B8).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, Color(0xFF666670).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_PANEL, Color(0xFFFAFAFA).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, Color(0xFF808088).toArgb())

        // Current line highlight
        setColor(EditorColorScheme.CURRENT_LINE, Color(0xFFF0F0F4).toArgb())

        // Selection
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, Color(0xFFE0E4F4).toArgb())

        // Cursor
        setColor(EditorColorScheme.SELECTION_INSERT, Color(0xFF5B5BD6).toArgb())
        setColor(EditorColorScheme.SELECTION_HANDLE, Color(0xFF5B5BD6).toArgb())

        // Block line
        setColor(EditorColorScheme.BLOCK_LINE, Color(0xFFE0E0E8).toArgb())
        setColor(EditorColorScheme.BLOCK_LINE_CURRENT, accent)

        // Matched bracket
        setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, Color(0xFFD0D4E4).toArgb())

        // Scrollbar
        setColor(EditorColorScheme.SCROLL_BAR_THUMB, Color(0xFFB0B0B8).toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, Color(0xFF909098).toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_TRACK, Color(0xFFFAFAFA).toArgb())

        // Syntax highlighting - keywords (purple)
        setColor(EditorColorScheme.KEYWORD, Color(0xFFAF00DB).toArgb())

        // Operators
        setColor(EditorColorScheme.OPERATOR, Color(0xFF0000FF).toArgb())

        // Comments (green italic)
        setColor(EditorColorScheme.COMMENT, Color(0xFF008000).toArgb())

        // Strings (red)
        setColor(EditorColorScheme.LITERAL, Color(0xFFA31515).toArgb())

        // Functions (brown)
        setColor(EditorColorScheme.FUNCTION_NAME, Color(0xFF795E26).toArgb())

        // Identifiers
        setColor(EditorColorScheme.IDENTIFIER_NAME, Color(0xFF1B1B1F).toArgb())
        setColor(EditorColorScheme.IDENTIFIER_VAR, Color(0xFF001080).toArgb())

        // HTML/XML tags (maroon)
        setColor(EditorColorScheme.HTML_TAG, Color(0xFF800000).toArgb())
        setColor(EditorColorScheme.ATTRIBUTE_NAME, Color(0xFFFF0000).toArgb())
        setColor(EditorColorScheme.ATTRIBUTE_VALUE, Color(0xFF0000FF).toArgb())

        // Completion window
        setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, Color(0xFFFFFFFF).toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_CORNER, Color(0xFFE0E0E0).toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, Color(0xFF1B1B1F).toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, Color(0xFF808088).toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, Color(0xFFE8E8F0).toArgb())

        // Non-printable characters
        setColor(EditorColorScheme.NON_PRINTABLE_CHAR, Color(0xFFD0D0D0).toArgb())

        // Underline
        setColor(EditorColorScheme.UNDERLINE, Color(0xFF5B5BD6).toArgb())

        // Sticky scroll
        setColor(EditorColorScheme.STICKY_SCROLL_DIVIDER, Color(0xFFE0E0E8).toArgb())

        // Side block line
        setColor(EditorColorScheme.SIDE_BLOCK_LINE, Color(0xFFE0E0E8).toArgb())

        // Text selected
        setColor(EditorColorScheme.TEXT_SELECTED, Color(0xFF1B1B1F).toArgb())

        // Diagnostic colors
        setColor(EditorColorScheme.PROBLEM_ERROR, Color(0xFFE51400).toArgb())
        setColor(EditorColorScheme.PROBLEM_WARNING, Color(0xFFBF8803).toArgb())
        setColor(EditorColorScheme.PROBLEM_TYPO, Color(0xFF1976D2).toArgb())

        // Snippet-related
        setColor(EditorColorScheme.SNIPPET_BACKGROUND_EDITING, Color(0xFFE8E8F0).toArgb())
        setColor(EditorColorScheme.SNIPPET_BACKGROUND_RELATED, Color(0xFFF0F0F4).toArgb())
        setColor(EditorColorScheme.SNIPPET_BACKGROUND_INACTIVE, Color(0xFFF8F8FC).toArgb())

        // Hard wrap marker
        setColor(EditorColorScheme.HARD_WRAP_MARKER, Color(0xFFE0E0E8).toArgb())
    }
}
