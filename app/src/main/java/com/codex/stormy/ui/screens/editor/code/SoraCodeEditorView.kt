package com.codex.stormy.ui.screens.editor.code

import android.content.Context
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.codex.stormy.ui.theme.SyntaxColors
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance code editor view built on Rosemoe/Sora editor
 * Provides professional-grade syntax highlighting using TextMate grammars
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    var isInitialized by remember { mutableStateOf(TextMateManager.isInitialized()) }
    var editorInstance by remember { mutableStateOf<CodeEditor?>(null) }
    var lastExternalContent by remember { mutableStateOf(content) }
    var isUpdatingFromEditor by remember { mutableStateOf(false) }

    // Initialize TextMate registry once
    LaunchedEffect(Unit) {
        if (!TextMateManager.isInitialized()) {
            withContext(Dispatchers.IO) {
                TextMateManager.initialize(context)
            }
            isInitialized = true
        }
    }

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
            }
        }
    }

    // Update theme when dark mode changes
    LaunchedEffect(isDarkTheme, editorInstance, isInitialized) {
        if (isInitialized) {
            editorInstance?.let { editor ->
                withContext(Dispatchers.Main) {
                    applyEditorTheme(editor, isDarkTheme)
                }
            }
        }
    }

    // Update language when file extension changes
    LaunchedEffect(fileExtension, editorInstance, isInitialized) {
        if (isInitialized) {
            editorInstance?.let { editor ->
                val language = withContext(Dispatchers.IO) {
                    createTextMateLanguage(fileExtension)
                }
                withContext(Dispatchers.Main) {
                    editor.setEditorLanguage(language)
                }
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            createCodeEditor(ctx, isDarkTheme).also { editor ->
                editorInstance = editor

                // Set initial content
                editor.setText(content)
                lastExternalContent = content

                // Configure editor settings
                editor.isLineNumberEnabled = showLineNumbers
                editor.isWordwrap = wordWrap
                editor.setTextSize(fontSize)

                // Set up content change listener
                editor.subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                    val newText = editor.text.toString()
                    if (newText != lastExternalContent) {
                        isUpdatingFromEditor = true
                        lastExternalContent = newText
                        onContentChange(newText)
                    }
                }

                // Apply language and theme based on file extension
                if (TextMateManager.isInitialized()) {
                    scope.launch(Dispatchers.IO) {
                        val language = createTextMateLanguage(fileExtension)
                        withContext(Dispatchers.Main) {
                            editor.setEditorLanguage(language)
                            applyEditorTheme(editor, isDarkTheme)
                        }
                    }
                } else {
                    // Use fallback color scheme until TextMate is initialized
                    editor.colorScheme = if (isDarkTheme) {
                        createDarkColorScheme()
                    } else {
                        createLightColorScheme()
                    }
                }
            }
        },
        update = { _ ->
            // Editor updates are handled via LaunchedEffects
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            editorInstance?.release()
            editorInstance = null
        }
    }
}

/**
 * Singleton manager for TextMate initialization
 * Ensures TextMate resources are initialized only once across all editor instances
 */
private object TextMateManager {
    private val initialized = AtomicBoolean(false)
    private val initMutex = Mutex()

    fun isInitialized(): Boolean = initialized.get()

    suspend fun initialize(context: Context) {
        if (initialized.get()) return

        initMutex.withLock {
            if (initialized.get()) return

            try {
                // Set up file provider for loading resources
                FileProviderRegistry.getInstance().addFileProvider(
                    AssetsFileResolver(context.assets)
                )

                // Load themes
                loadThemes()

                initialized.set(true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadThemes() {
        try {
            val themeRegistry = ThemeRegistry.getInstance()

            // Register dark theme
            val darkTheme = createDarkEditorTheme()
            themeRegistry.loadTheme(darkTheme)

            // Register light theme
            val lightTheme = createLightEditorTheme()
            themeRegistry.loadTheme(lightTheme)

            // Set default theme
            themeRegistry.setTheme(DARK_THEME_NAME)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Create and configure the CodeEditor instance with optimized scrolling
 */
private fun createCodeEditor(context: Context, isDarkTheme: Boolean): CodeEditor {
    return object : CodeEditor(context) {
        // Override touch handling for smoother scrolling
        override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
            // Always intercept vertical scroll events to prevent parent interference
            when (ev?.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(event: MotionEvent?): Boolean {
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
        overScrollMode = OVER_SCROLL_NEVER

        // Set scrolling parameters for smoothness
        setScrollMaxX(0)
        setScrollMaxY(0)

        // Accessibility
        contentDescription = "Code Editor"

        // Apply initial color scheme
        colorScheme = if (isDarkTheme) {
            createDarkColorScheme()
        } else {
            createLightColorScheme()
        }
    }
}

/**
 * Create TextMate language for syntax highlighting
 */
private fun createTextMateLanguage(extension: String): Language {
    return try {
        val scopeName = getScopeNameForExtension(extension)
        if (scopeName != null && TextMateManager.isInitialized()) {
            TextMateLanguage.create(scopeName, true)
        } else {
            EmptyLanguage()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        EmptyLanguage()
    }
}

/**
 * Get TextMate scope name for file extension
 */
private fun getScopeNameForExtension(extension: String): String? {
    return when (extension.lowercase()) {
        "html", "htm" -> "text.html.basic"
        "css" -> "source.css"
        "js", "mjs" -> "source.js"
        "ts" -> "source.ts"
        "tsx" -> "source.tsx"
        "jsx" -> "source.jsx"
        "json" -> "source.json"
        "md", "markdown" -> "text.html.markdown"
        "xml", "svg" -> "text.xml"
        "yaml", "yml" -> "source.yaml"
        "py" -> "source.python"
        "kt", "kts" -> "source.kotlin"
        "java" -> "source.java"
        "swift" -> "source.swift"
        "go" -> "source.go"
        "rs" -> "source.rust"
        "c", "h" -> "source.c"
        "cpp", "cc", "cxx", "hpp" -> "source.cpp"
        "sh", "bash", "zsh" -> "source.shell"
        "sql" -> "source.sql"
        "php" -> "text.html.php"
        "rb" -> "source.ruby"
        "scss" -> "source.css.scss"
        "sass" -> "source.sass"
        "less" -> "source.css.less"
        "vue" -> "source.vue"
        "toml" -> "source.toml"
        "ini", "conf" -> "source.ini"
        "txt" -> null
        else -> null
    }
}

/**
 * Apply theme to the editor based on dark/light mode
 */
private fun applyEditorTheme(editor: CodeEditor, isDarkTheme: Boolean) {
    try {
        val themeName = if (isDarkTheme) DARK_THEME_NAME else LIGHT_THEME_NAME

        // Try to apply TextMate color scheme
        if (TextMateManager.isInitialized()) {
            try {
                ThemeRegistry.getInstance().setTheme(themeName)
                val textMateScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                editor.colorScheme = textMateScheme
                return
            } catch (e: Exception) {
                // Fall back to basic color scheme
                e.printStackTrace()
            }
        }

        // Fallback to basic color scheme
        editor.colorScheme = if (isDarkTheme) {
            createDarkColorScheme()
        } else {
            createLightColorScheme()
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Create dark color scheme for the editor
 */
private fun createDarkColorScheme(): EditorColorScheme {
    return EditorColorScheme().apply {
        // Editor background and foreground
        setColor(EditorColorScheme.WHOLE_BACKGROUND, Color(0xFF1E1E24).toArgb())
        setColor(EditorColorScheme.TEXT_NORMAL, Color(0xFFE5E1E6).toArgb())

        // Line numbers
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, Color(0xFF1E1E24).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER, Color(0xFF4A4A52).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, Color(0xFF8B8B8B).toArgb())

        // Current line highlight
        setColor(EditorColorScheme.CURRENT_LINE, Color(0xFF2A2A32).toArgb())

        // Selection
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, Color(0xFF3A3A52).toArgb())

        // Cursor
        setColor(EditorColorScheme.SELECTION_INSERT, Color(0xFFBFC1FF).toArgb())
        setColor(EditorColorScheme.SELECTION_HANDLE, Color(0xFFBFC1FF).toArgb())

        // Block line
        setColor(EditorColorScheme.BLOCK_LINE, Color(0xFF3A3A42).toArgb())
        setColor(EditorColorScheme.BLOCK_LINE_CURRENT, Color(0xFF5B5BD6).toArgb())

        // Matched bracket
        setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, Color(0xFF4A4A62).toArgb())

        // Scrollbar
        setColor(EditorColorScheme.SCROLL_BAR_THUMB, Color(0xFF4A4A52).toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, Color(0xFF6A6A72).toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_TRACK, Color(0xFF1E1E24).toArgb())

        // Syntax highlighting - keywords
        setColor(EditorColorScheme.KEYWORD, SyntaxColors.Keyword.toArgb())

        // Operators
        setColor(EditorColorScheme.OPERATOR, SyntaxColors.Operator.toArgb())

        // Comments
        setColor(EditorColorScheme.COMMENT, SyntaxColors.Comment.toArgb())

        // Strings
        setColor(EditorColorScheme.LITERAL, SyntaxColors.String.toArgb())

        // Functions
        setColor(EditorColorScheme.FUNCTION_NAME, SyntaxColors.Function.toArgb())

        // Identifiers
        setColor(EditorColorScheme.IDENTIFIER_NAME, Color(0xFFE5E1E6).toArgb())
        setColor(EditorColorScheme.IDENTIFIER_VAR, SyntaxColors.Variable.toArgb())

        // HTML/XML tags
        setColor(EditorColorScheme.HTML_TAG, SyntaxColors.Tag.toArgb())
        setColor(EditorColorScheme.ATTRIBUTE_NAME, SyntaxColors.Attribute.toArgb())
        setColor(EditorColorScheme.ATTRIBUTE_VALUE, SyntaxColors.String.toArgb())

        // Completion window
        setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, Color(0xFF272730).toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_CORNER, Color(0xFF272730).toArgb())

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
    }
}

/**
 * Create light color scheme for the editor
 */
private fun createLightColorScheme(): EditorColorScheme {
    return EditorColorScheme().apply {
        // Editor background and foreground
        setColor(EditorColorScheme.WHOLE_BACKGROUND, Color(0xFFFAFAFA).toArgb())
        setColor(EditorColorScheme.TEXT_NORMAL, Color(0xFF1B1B1F).toArgb())

        // Line numbers
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, Color(0xFFFAFAFA).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER, Color(0xFFB0B0B0).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, Color(0xFF666666).toArgb())

        // Current line highlight
        setColor(EditorColorScheme.CURRENT_LINE, Color(0xFFF0F0F4).toArgb())

        // Selection
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, Color(0xFFE0E4F4).toArgb())

        // Cursor
        setColor(EditorColorScheme.SELECTION_INSERT, Color(0xFF5B5BD6).toArgb())
        setColor(EditorColorScheme.SELECTION_HANDLE, Color(0xFF5B5BD6).toArgb())

        // Block line
        setColor(EditorColorScheme.BLOCK_LINE, Color(0xFFE0E0E8).toArgb())
        setColor(EditorColorScheme.BLOCK_LINE_CURRENT, Color(0xFF5B5BD6).toArgb())

        // Matched bracket
        setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, Color(0xFFD0D4E4).toArgb())

        // Scrollbar
        setColor(EditorColorScheme.SCROLL_BAR_THUMB, Color(0xFFB0B0B8).toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, Color(0xFF909098).toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_TRACK, Color(0xFFFAFAFA).toArgb())

        // Syntax highlighting - keywords
        setColor(EditorColorScheme.KEYWORD, Color(0xFFAF00DB).toArgb())

        // Operators
        setColor(EditorColorScheme.OPERATOR, Color(0xFF0000FF).toArgb())

        // Comments
        setColor(EditorColorScheme.COMMENT, Color(0xFF008000).toArgb())

        // Strings
        setColor(EditorColorScheme.LITERAL, Color(0xFFA31515).toArgb())

        // Functions
        setColor(EditorColorScheme.FUNCTION_NAME, Color(0xFF795E26).toArgb())

        // Identifiers
        setColor(EditorColorScheme.IDENTIFIER_NAME, Color(0xFF1B1B1F).toArgb())
        setColor(EditorColorScheme.IDENTIFIER_VAR, Color(0xFF001080).toArgb())

        // HTML/XML tags
        setColor(EditorColorScheme.HTML_TAG, Color(0xFF800000).toArgb())
        setColor(EditorColorScheme.ATTRIBUTE_NAME, Color(0xFFFF0000).toArgb())
        setColor(EditorColorScheme.ATTRIBUTE_VALUE, Color(0xFF0000FF).toArgb())

        // Completion window
        setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, Color(0xFFFFFFFF).toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_CORNER, Color(0xFFE0E0E0).toArgb())

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
    }
}

/**
 * Create dark theme model for TextMate
 */
private fun createDarkEditorTheme(): ThemeModel {
    val darkThemeJson = """
    {
        "name": "$DARK_THEME_NAME",
        "type": "dark",
        "colors": {
            "editor.background": "#1E1E24",
            "editor.foreground": "#E5E1E6",
            "editor.lineHighlightBackground": "#2A2A32",
            "editorLineNumber.foreground": "#4A4A52",
            "editorLineNumber.activeForeground": "#8B8B8B",
            "editor.selectionBackground": "#3A3A52",
            "editorCursor.foreground": "#BFC1FF"
        },
        "tokenColors": [
            { "scope": "comment", "settings": { "foreground": "#5C6370", "fontStyle": "italic" } },
            { "scope": "keyword", "settings": { "foreground": "#C678DD" } },
            { "scope": "keyword.control", "settings": { "foreground": "#C678DD" } },
            { "scope": "storage", "settings": { "foreground": "#C678DD" } },
            { "scope": "storage.type", "settings": { "foreground": "#C678DD" } },
            { "scope": "string", "settings": { "foreground": "#98C379" } },
            { "scope": "string.quoted", "settings": { "foreground": "#98C379" } },
            { "scope": "constant.numeric", "settings": { "foreground": "#D19A66" } },
            { "scope": "constant.language", "settings": { "foreground": "#D19A66" } },
            { "scope": "constant.character", "settings": { "foreground": "#D19A66" } },
            { "scope": "variable", "settings": { "foreground": "#E5C07B" } },
            { "scope": "variable.parameter", "settings": { "foreground": "#E06C75" } },
            { "scope": "entity.name.function", "settings": { "foreground": "#61AFEF" } },
            { "scope": "entity.name.type", "settings": { "foreground": "#E5C07B" } },
            { "scope": "entity.name.class", "settings": { "foreground": "#E5C07B" } },
            { "scope": "entity.name.tag", "settings": { "foreground": "#E06C75" } },
            { "scope": "entity.other.attribute-name", "settings": { "foreground": "#D19A66" } },
            { "scope": "punctuation", "settings": { "foreground": "#ABB2BF" } },
            { "scope": "punctuation.definition", "settings": { "foreground": "#ABB2BF" } },
            { "scope": "support.function", "settings": { "foreground": "#61AFEF" } },
            { "scope": "support.class", "settings": { "foreground": "#E5C07B" } },
            { "scope": "support.type", "settings": { "foreground": "#56B6C2" } },
            { "scope": "meta.tag", "settings": { "foreground": "#E06C75" } },
            { "scope": "meta.selector", "settings": { "foreground": "#C678DD" } },
            { "scope": "meta.property-name", "settings": { "foreground": "#61AFEF" } },
            { "scope": "meta.property-value", "settings": { "foreground": "#98C379" } }
        ]
    }
    """.trimIndent()

    return ThemeModel(
        IThemeSource.fromInputStream(
            darkThemeJson.byteInputStream(),
            "codex-dark.json",
            null
        ),
        DARK_THEME_NAME
    )
}

/**
 * Create light theme model for TextMate
 */
private fun createLightEditorTheme(): ThemeModel {
    val lightThemeJson = """
    {
        "name": "$LIGHT_THEME_NAME",
        "type": "light",
        "colors": {
            "editor.background": "#FAFAFA",
            "editor.foreground": "#1B1B1F",
            "editor.lineHighlightBackground": "#F0F0F4",
            "editorLineNumber.foreground": "#B0B0B0",
            "editorLineNumber.activeForeground": "#666666",
            "editor.selectionBackground": "#E0E4F4",
            "editorCursor.foreground": "#5B5BD6"
        },
        "tokenColors": [
            { "scope": "comment", "settings": { "foreground": "#008000", "fontStyle": "italic" } },
            { "scope": "keyword", "settings": { "foreground": "#AF00DB" } },
            { "scope": "keyword.control", "settings": { "foreground": "#AF00DB" } },
            { "scope": "storage", "settings": { "foreground": "#AF00DB" } },
            { "scope": "storage.type", "settings": { "foreground": "#0000FF" } },
            { "scope": "string", "settings": { "foreground": "#A31515" } },
            { "scope": "string.quoted", "settings": { "foreground": "#A31515" } },
            { "scope": "constant.numeric", "settings": { "foreground": "#098658" } },
            { "scope": "constant.language", "settings": { "foreground": "#0000FF" } },
            { "scope": "constant.character", "settings": { "foreground": "#098658" } },
            { "scope": "variable", "settings": { "foreground": "#001080" } },
            { "scope": "variable.parameter", "settings": { "foreground": "#001080" } },
            { "scope": "entity.name.function", "settings": { "foreground": "#795E26" } },
            { "scope": "entity.name.type", "settings": { "foreground": "#267F99" } },
            { "scope": "entity.name.class", "settings": { "foreground": "#267F99" } },
            { "scope": "entity.name.tag", "settings": { "foreground": "#800000" } },
            { "scope": "entity.other.attribute-name", "settings": { "foreground": "#FF0000" } },
            { "scope": "punctuation", "settings": { "foreground": "#1B1B1F" } },
            { "scope": "punctuation.definition", "settings": { "foreground": "#1B1B1F" } },
            { "scope": "support.function", "settings": { "foreground": "#795E26" } },
            { "scope": "support.class", "settings": { "foreground": "#267F99" } },
            { "scope": "support.type", "settings": { "foreground": "#267F99" } },
            { "scope": "meta.tag", "settings": { "foreground": "#800000" } },
            { "scope": "meta.selector", "settings": { "foreground": "#800000" } },
            { "scope": "meta.property-name", "settings": { "foreground": "#FF0000" } },
            { "scope": "meta.property-value", "settings": { "foreground": "#0451A5" } }
        ]
    }
    """.trimIndent()

    return ThemeModel(
        IThemeSource.fromInputStream(
            lightThemeJson.byteInputStream(),
            "codex-light.json",
            null
        ),
        LIGHT_THEME_NAME
    )
}

// Theme names
private const val DARK_THEME_NAME = "codex-dark"
private const val LIGHT_THEME_NAME = "codex-light"
