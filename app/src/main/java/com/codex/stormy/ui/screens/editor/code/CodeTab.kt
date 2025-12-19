package com.codex.stormy.ui.screens.editor.code

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.R
import com.codex.stormy.domain.model.FileTreeNode
import com.codex.stormy.ui.theme.CodeXTheme
import com.codex.stormy.ui.theme.ExtendedColors
import com.codex.stormy.ui.theme.SyntaxColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

@Composable
fun CodeTab(
    currentFile: FileTreeNode.FileNode?,
    fileContent: String,
    isModified: Boolean,
    lineNumbers: Boolean,
    wordWrap: Boolean,
    fontSize: Float,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val extendedColors = CodeXTheme.extendedColors

    // Search state
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var showReplace by remember { mutableStateOf(false) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    var searchMatches by remember { mutableStateOf<List<IntRange>>(emptyList()) }

    // Update search matches when query or content changes
    LaunchedEffect(searchQuery, fileContent) {
        searchMatches = if (searchQuery.isNotEmpty()) {
            findAllMatches(fileContent, searchQuery)
        } else {
            emptyList()
        }
        if (searchMatches.isNotEmpty() && currentMatchIndex >= searchMatches.size) {
            currentMatchIndex = 0
        }
    }

    if (currentFile == null) {
        EmptyEditorState(modifier = Modifier.fillMaxSize())
    } else {
        Scaffold(
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search FAB
                    SmallFloatingActionButton(
                        onClick = { showSearch = !showSearch },
                        containerColor = if (showSearch) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (showSearch) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search"
                        )
                    }

                    // Save FAB
                    if (isModified) {
                        SmallFloatingActionButton(
                            onClick = onSave,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Save,
                                contentDescription = context.getString(R.string.action_save)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Search bar
                AnimatedVisibility(
                    visible = showSearch,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    SearchReplaceBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        replaceText = replaceText,
                        onReplaceTextChange = { replaceText = it },
                        showReplace = showReplace,
                        onToggleReplace = { showReplace = !showReplace },
                        matchCount = searchMatches.size,
                        currentMatch = if (searchMatches.isNotEmpty()) currentMatchIndex + 1 else 0,
                        onPrevious = {
                            if (searchMatches.isNotEmpty()) {
                                currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else searchMatches.size - 1
                            }
                        },
                        onNext = {
                            if (searchMatches.isNotEmpty()) {
                                currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                            }
                        },
                        onReplace = {
                            if (searchMatches.isNotEmpty() && currentMatchIndex < searchMatches.size) {
                                val match = searchMatches[currentMatchIndex]
                                val newContent = fileContent.replaceRange(match, replaceText)
                                onContentChange(newContent)
                            }
                        },
                        onReplaceAll = {
                            if (searchQuery.isNotEmpty()) {
                                val newContent = fileContent.replace(searchQuery, replaceText, ignoreCase = true)
                                onContentChange(newContent)
                            }
                        },
                        onClose = {
                            showSearch = false
                            searchQuery = ""
                            replaceText = ""
                        }
                    )
                }

                CodeEditor(
                    content = fileContent,
                    onContentChange = onContentChange,
                    fileExtension = currentFile.extension,
                    showLineNumbers = lineNumbers,
                    wordWrap = wordWrap,
                    fontSize = fontSize,
                    filePath = currentFile.path,
                    searchMatches = searchMatches,
                    currentMatchIndex = if (searchMatches.isNotEmpty()) currentMatchIndex else -1,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(extendedColors.editorBackground)
                )
            }
        }
    }
}

/**
 * Find all occurrences of a search query in the text
 */
private fun findAllMatches(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()

    val matches = mutableListOf<IntRange>()
    var startIndex = 0

    while (startIndex < text.length) {
        val index = text.indexOf(query, startIndex, ignoreCase = true)
        if (index == -1) break
        matches.add(index until (index + query.length))
        startIndex = index + 1
    }

    return matches
}

/**
 * Search and replace bar component
 */
@Composable
private fun SearchReplaceBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    replaceText: String,
    onReplaceTextChange: (String) -> Unit,
    showReplace: Boolean,
    onToggleReplace: () -> Unit,
    matchCount: Int,
    currentMatch: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Search row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Search input
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search...",
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Match count
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = if (matchCount > 0) "$currentMatch/$matchCount" else "0 results",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // Navigation buttons
                IconButton(
                    onClick = onPrevious,
                    enabled = matchCount > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "Previous",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    enabled = matchCount > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Next",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Toggle replace
                TextButton(
                    onClick = onToggleReplace
                ) {
                    Text(
                        text = if (showReplace) "−" else "+",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Replace row
            AnimatedVisibility(visible = showReplace) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BasicTextField(
                        value = replaceText,
                        onValueChange = onReplaceTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (replaceText.isEmpty()) {
                                    Text(
                                        text = "Replace with...",
                                        style = TextStyle(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    TextButton(
                        onClick = onReplace,
                        enabled = matchCount > 0
                    ) {
                        Text("Replace")
                    }

                    TextButton(
                        onClick = onReplaceAll,
                        enabled = matchCount > 0
                    ) {
                        Text("All")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyEditorState(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Code,
            contentDescription = null,
            modifier = Modifier.padding(16.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )

        Text(
            text = context.getString(R.string.editor_no_file),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun CodeEditor(
    content: String,
    onContentChange: (String) -> Unit,
    fileExtension: String,
    showLineNumbers: Boolean,
    wordWrap: Boolean,
    fontSize: Float,
    filePath: String,
    searchMatches: List<IntRange> = emptyList(),
    currentMatchIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    val extendedColors = CodeXTheme.extendedColors
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    // Use a stable key based on file path to properly reset state when file changes
    val fileKey = remember(filePath) { filePath }

    // Initialize text field value - only reinitialize when file changes
    var textFieldValue by remember(fileKey) {
        mutableStateOf(TextFieldValue(text = content, selection = TextRange(0)))
    }

    // Track external content changes (from file reload)
    var lastExternalContent by remember(fileKey) { mutableStateOf(content) }

    // Track previous text for auto-indent detection
    var previousText by remember(fileKey) { mutableStateOf(content) }

    // Update textFieldValue when external content changes (but not from our own edits)
    LaunchedEffect(content, fileKey) {
        if (content != textFieldValue.text && content != lastExternalContent) {
            // External content change - preserve cursor if possible
            val newSelection = if (textFieldValue.selection.end <= content.length) {
                textFieldValue.selection
            } else {
                TextRange(content.length.coerceAtLeast(0))
            }
            textFieldValue = TextFieldValue(text = content, selection = newSelection)
            lastExternalContent = content
            previousText = content
        }
    }

    // Derived line count for performance
    val lineCount by remember {
        derivedStateOf { textFieldValue.text.count { it == '\n' } + 1 }
    }

    // Calculate current line for highlighting
    val currentLine by remember {
        derivedStateOf {
            val cursorPos = textFieldValue.selection.start
            textFieldValue.text.substring(0, cursorPos.coerceIn(0, textFieldValue.text.length)).count { it == '\n' }
        }
    }

    val textStyle = remember(fontSize) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.5).sp
        )
    }

    // Memoized syntax highlighter with search highlighting
    val syntaxHighlighter = remember(fileExtension, extendedColors) {
        SyntaxHighlighter(fileExtension, extendedColors)
    }

    // Debounced content change to prevent excessive recompositions
    LaunchedEffect(Unit) {
        snapshotFlow { textFieldValue.text }
            .debounce(50)
            .collect { text ->
                if (text != lastExternalContent) {
                    lastExternalContent = text
                    onContentChange(text)
                }
            }
    }

    // Current line highlight color
    val currentLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val lineHeight = fontSize * 1.5f

    Row(
        modifier = modifier
            .then(
                if (wordWrap) {
                    Modifier.verticalScroll(verticalScrollState)
                } else {
                    Modifier
                        .verticalScroll(verticalScrollState)
                        .horizontalScroll(horizontalScrollState)
                }
            )
    ) {
        if (showLineNumbers) {
            LineNumbers(
                lineCount = lineCount,
                fontSize = fontSize,
                currentLine = currentLine,
                extendedColors = extendedColors,
                modifier = Modifier
                    .background(extendedColors.editorBackground)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                // Auto-indentation: when user presses Enter, maintain indentation
                val newText = newValue.text
                val oldText = textFieldValue.text

                if (newText.length > oldText.length) {
                    val insertedChars = newText.length - oldText.length
                    val cursorPos = newValue.selection.start

                    // Check if a newline was just inserted
                    if (insertedChars == 1 && cursorPos > 0 && newText[cursorPos - 1] == '\n') {
                        // Find the indentation of the previous line
                        val lineStart = oldText.lastIndexOf('\n', cursorPos - 2) + 1
                        val lineContent = oldText.substring(lineStart, cursorPos - 1)
                        val indentation = lineContent.takeWhile { it == ' ' || it == '\t' }

                        // Check if we should add extra indent (after { or :)
                        val lastNonWhitespace = lineContent.trimEnd().lastOrNull()
                        val extraIndent = when (lastNonWhitespace) {
                            '{', ':', '(' -> "    "
                            else -> ""
                        }

                        if (indentation.isNotEmpty() || extraIndent.isNotEmpty()) {
                            val totalIndent = indentation + extraIndent
                            val textWithIndent = newText.substring(0, cursorPos) + totalIndent +
                                    newText.substring(cursorPos)
                            textFieldValue = TextFieldValue(
                                text = textWithIndent,
                                selection = TextRange(cursorPos + totalIndent.length)
                            )
                            previousText = textWithIndent
                            return@BasicTextField
                        }
                    }

                    // Handle tab key - convert to spaces
                    if (insertedChars == 1 && cursorPos > 0 && newText[cursorPos - 1] == '\t') {
                        val spaces = "    " // 4 spaces
                        val textWithSpaces = newText.substring(0, cursorPos - 1) + spaces +
                                newText.substring(cursorPos)
                        textFieldValue = TextFieldValue(
                            text = textWithSpaces,
                            selection = TextRange(cursorPos - 1 + spaces.length)
                        )
                        previousText = textWithSpaces
                        return@BasicTextField
                    }
                }

                textFieldValue = newValue
                previousText = newText
            },
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
                .focusRequester(focusRequester)
                .then(if (wordWrap) Modifier.fillMaxWidth() else Modifier)
                .drawBehind {
                    // Draw current line highlight
                    val lineY = currentLine * lineHeight.sp.toPx()
                    drawRect(
                        color = currentLineColor,
                        topLeft = Offset(0f, lineY),
                        size = Size(size.width, lineHeight.sp.toPx())
                    )
                },
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = { annotatedString ->
                val highlighted = syntaxHighlighter.highlightWithSearch(
                    annotatedString.text,
                    searchMatches,
                    currentMatchIndex
                )
                androidx.compose.ui.text.input.TransformedText(
                    highlighted,
                    androidx.compose.ui.text.input.OffsetMapping.Identity
                )
            }
        )
    }
}

@Composable
private fun LineNumbers(
    lineCount: Int,
    fontSize: Float,
    currentLine: Int = -1,
    extendedColors: ExtendedColors,
    modifier: Modifier = Modifier
) {
    val maxLineNumber = remember(lineCount) { lineCount.toString().length }
    val lineWidth = remember(maxLineNumber, fontSize) { (maxLineNumber * fontSize * 0.6).dp + 8.dp }

    val normalTextStyle = remember(fontSize, extendedColors.lineNumber) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.5).sp,
            color = extendedColors.lineNumber
        )
    }

    val currentLineTextStyle = remember(fontSize, extendedColors.lineNumber) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.5).sp,
            color = extendedColors.lineNumber.copy(alpha = 1f)
        )
    }

    Column(modifier = modifier) {
        repeat(lineCount.coerceAtLeast(1)) { index ->
            val isCurrentLine = index == currentLine
            Text(
                text = (index + 1).toString().padStart(maxLineNumber),
                style = if (isCurrentLine) currentLineTextStyle else normalTextStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.width(lineWidth)
            )
        }
    }
}

/**
 * Optimized syntax highlighter with caching and search highlight support
 */
private class SyntaxHighlighter(
    private val extension: String,
    private val colors: ExtendedColors
) {
    private var cachedText: String = ""
    private var cachedResult: AnnotatedString = AnnotatedString("")
    private var cachedSearchMatches: List<IntRange> = emptyList()
    private var cachedCurrentMatch: Int = -1
    private var cachedSearchResult: AnnotatedString = AnnotatedString("")

    fun highlight(text: String): AnnotatedString {
        // Return cached result if text hasn't changed
        if (text == cachedText && cachedResult.text.isNotEmpty()) {
            return cachedResult
        }

        val result = when (extension.lowercase()) {
            "html", "htm" -> highlightHtml(text)
            "css" -> highlightCss(text)
            "js", "mjs", "jsx" -> highlightJavaScript(text)
            "ts", "tsx" -> highlightTypeScript(text)
            "json" -> highlightJson(text)
            else -> AnnotatedString(text)
        }

        cachedText = text
        cachedResult = result
        return result
    }

    /**
     * Highlight syntax and search matches
     */
    fun highlightWithSearch(
        text: String,
        searchMatches: List<IntRange>,
        currentMatchIndex: Int
    ): AnnotatedString {
        // First get syntax highlighting
        val syntaxHighlighted = highlight(text)

        // If no search matches, return syntax-only highlighting
        if (searchMatches.isEmpty()) {
            return syntaxHighlighted
        }

        // Check cache for search highlighting
        if (text == cachedText && searchMatches == cachedSearchMatches &&
            currentMatchIndex == cachedCurrentMatch && cachedSearchResult.text.isNotEmpty()) {
            return cachedSearchResult
        }

        // Apply search highlighting on top of syntax highlighting
        val result = buildAnnotatedString {
            append(syntaxHighlighted)

            // Add search match highlighting
            val searchHighlightColor = SyntaxColors.SearchHighlight
            val currentMatchColor = SyntaxColors.CurrentSearchHighlight

            searchMatches.forEachIndexed { index, range ->
                if (range.last < text.length) {
                    val bgColor = if (index == currentMatchIndex) currentMatchColor else searchHighlightColor
                    addStyle(
                        SpanStyle(background = bgColor),
                        range.first,
                        range.last + 1
                    )
                }
            }
        }

        cachedSearchMatches = searchMatches
        cachedCurrentMatch = currentMatchIndex
        cachedSearchResult = result
        return result
    }

    private fun highlightHtml(text: String): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                when {
                    text.startsWith("<!--", i) -> {
                        val end = text.indexOf("-->", i)
                        val commentEnd = if (end == -1) text.length else end + 3
                        withStyle(SpanStyle(color = colors.syntaxComment)) {
                            append(text.substring(i, commentEnd))
                        }
                        i = commentEnd
                    }
                    text.startsWith("<!DOCTYPE", i, ignoreCase = true) -> {
                        val end = text.indexOf(">", i)
                        val tagEnd = if (end == -1) text.length else end + 1
                        withStyle(SpanStyle(color = colors.syntaxKeyword)) {
                            append(text.substring(i, tagEnd))
                        }
                        i = tagEnd
                    }
                    text[i] == '<' && i + 1 < text.length && (text[i + 1].isLetter() || text[i + 1] == '/' || text[i + 1] == '!') -> {
                        i = parseHtmlTag(this, text, i)
                    }
                    else -> {
                        append(text[i])
                        i++
                    }
                }
            }
        }
    }

    private fun parseHtmlTag(builder: AnnotatedString.Builder, text: String, startIndex: Int): Int {
        val end = text.indexOf(">", startIndex)
        val tagEnd = if (end == -1) text.length else end + 1
        val tagContent = text.substring(startIndex, tagEnd)

        builder.withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
            append("<")
        }

        val isClosingTag = tagContent.startsWith("</")
        val contentStart = if (isClosingTag) 2 else 1

        if (isClosingTag) {
            builder.withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                append("/")
            }
        }

        // Find tag name end
        var tagNameEnd = contentStart
        while (tagNameEnd < tagContent.length &&
               !tagContent[tagNameEnd].isWhitespace() &&
               tagContent[tagNameEnd] != '>' &&
               tagContent[tagNameEnd] != '/') {
            tagNameEnd++
        }

        val tagName = tagContent.substring(contentStart, tagNameEnd)
        builder.withStyle(SpanStyle(color = colors.syntaxTag)) {
            append(tagName)
        }

        // Parse attributes
        var attrIndex = tagNameEnd
        while (attrIndex < tagContent.length - 1) {
            when {
                tagContent[attrIndex].isWhitespace() -> {
                    builder.append(tagContent[attrIndex])
                    attrIndex++
                }
                tagContent[attrIndex] == '/' -> {
                    builder.withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                        append("/")
                    }
                    attrIndex++
                }
                tagContent[attrIndex].isLetter() || tagContent[attrIndex] == '_' || tagContent[attrIndex] == ':' -> {
                    // Attribute name
                    var attrNameEnd = attrIndex
                    while (attrNameEnd < tagContent.length &&
                           (tagContent[attrNameEnd].isLetterOrDigit() ||
                            tagContent[attrNameEnd] == '-' ||
                            tagContent[attrNameEnd] == '_' ||
                            tagContent[attrNameEnd] == ':')) {
                        attrNameEnd++
                    }

                    builder.withStyle(SpanStyle(color = colors.syntaxAttribute)) {
                        append(tagContent.substring(attrIndex, attrNameEnd))
                    }
                    attrIndex = attrNameEnd

                    // Check for =
                    while (attrIndex < tagContent.length && tagContent[attrIndex].isWhitespace()) {
                        builder.append(tagContent[attrIndex])
                        attrIndex++
                    }

                    if (attrIndex < tagContent.length && tagContent[attrIndex] == '=') {
                        builder.withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                            append("=")
                        }
                        attrIndex++

                        // Skip whitespace
                        while (attrIndex < tagContent.length && tagContent[attrIndex].isWhitespace()) {
                            builder.append(tagContent[attrIndex])
                            attrIndex++
                        }

                        // Attribute value
                        if (attrIndex < tagContent.length) {
                            val quote = tagContent[attrIndex]
                            if (quote == '"' || quote == '\'') {
                                val valueEnd = tagContent.indexOf(quote, attrIndex + 1)
                                val actualEnd = if (valueEnd == -1) tagContent.length - 1 else valueEnd + 1
                                builder.withStyle(SpanStyle(color = colors.syntaxString)) {
                                    append(tagContent.substring(attrIndex, actualEnd))
                                }
                                attrIndex = actualEnd
                            }
                        }
                    }
                }
                else -> {
                    attrIndex++
                }
            }
        }

        builder.withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
            append(">")
        }

        return tagEnd
    }

    private fun highlightCss(text: String): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            var inComment = false
            var inBlock = false

            while (i < text.length) {
                when {
                    text.startsWith("/*", i) -> {
                        val end = text.indexOf("*/", i + 2)
                        val commentEnd = if (end == -1) text.length else end + 2
                        withStyle(SpanStyle(color = colors.syntaxComment)) {
                            append(text.substring(i, commentEnd))
                        }
                        i = commentEnd
                    }
                    text[i] == '{' -> {
                        inBlock = true
                        withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                            append("{")
                        }
                        i++
                    }
                    text[i] == '}' -> {
                        inBlock = false
                        withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                            append("}")
                        }
                        i++
                    }
                    text[i] == ':' && inBlock -> {
                        withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                            append(":")
                        }
                        i++

                        // Collect value until semicolon or closing brace
                        val valueStart = i
                        while (i < text.length && text[i] != ';' && text[i] != '}') {
                            i++
                        }
                        if (valueStart < i) {
                            withStyle(SpanStyle(color = colors.syntaxString)) {
                                append(text.substring(valueStart, i))
                            }
                        }
                    }
                    text[i] == ';' -> {
                        withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                            append(";")
                        }
                        i++
                    }
                    !inBlock && !text[i].isWhitespace() -> {
                        // Selector
                        val selectorStart = i
                        while (i < text.length && text[i] != '{' && !text.startsWith("/*", i)) {
                            i++
                        }
                        withStyle(SpanStyle(color = colors.syntaxTag)) {
                            append(text.substring(selectorStart, i))
                        }
                    }
                    inBlock && text[i].isLetter() -> {
                        // Property name
                        val propStart = i
                        while (i < text.length && text[i] != ':' && text[i] != '}' && !text[i].isWhitespace()) {
                            i++
                        }
                        withStyle(SpanStyle(color = colors.syntaxAttribute)) {
                            append(text.substring(propStart, i))
                        }
                    }
                    else -> {
                        append(text[i])
                        i++
                    }
                }
            }
        }
    }

    private fun highlightJavaScript(text: String): AnnotatedString {
        return highlightJsTs(text, JS_KEYWORDS)
    }

    private fun highlightTypeScript(text: String): AnnotatedString {
        return highlightJsTs(text, TS_KEYWORDS)
    }

    private fun highlightJsTs(text: String, keywords: Set<String>): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                when {
                    text.startsWith("//", i) -> {
                        val end = text.indexOf("\n", i)
                        val commentEnd = if (end == -1) text.length else end
                        withStyle(SpanStyle(color = colors.syntaxComment)) {
                            append(text.substring(i, commentEnd))
                        }
                        i = commentEnd
                    }
                    text.startsWith("/*", i) -> {
                        val end = text.indexOf("*/", i)
                        val commentEnd = if (end == -1) text.length else end + 2
                        withStyle(SpanStyle(color = colors.syntaxComment)) {
                            append(text.substring(i, commentEnd))
                        }
                        i = commentEnd
                    }
                    text[i] == '"' || text[i] == '\'' || text[i] == '`' -> {
                        i = parseString(this, text, i)
                    }
                    text[i].isDigit() || (text[i] == '.' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                        i = parseNumber(this, text, i)
                    }
                    text[i].isLetter() || text[i] == '_' || text[i] == '$' -> {
                        i = parseIdentifier(this, text, i, keywords)
                    }
                    text[i] in "+-*/%=!<>&|?:" -> {
                        withStyle(SpanStyle(color = SyntaxColors.Operator)) {
                            append(text[i])
                        }
                        i++
                    }
                    text[i] in "{}[]();," -> {
                        withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                            append(text[i])
                        }
                        i++
                    }
                    else -> {
                        append(text[i])
                        i++
                    }
                }
            }
        }
    }

    private fun parseString(builder: AnnotatedString.Builder, text: String, startIndex: Int): Int {
        val quote = text[startIndex]
        var end = startIndex + 1

        while (end < text.length) {
            when {
                text[end] == '\\' && end + 1 < text.length -> end += 2
                text[end] == quote -> {
                    end++
                    break
                }
                quote == '`' && text[end] == '\n' -> end++
                quote != '`' && text[end] == '\n' -> break
                else -> end++
            }
        }

        builder.withStyle(SpanStyle(color = colors.syntaxString)) {
            append(text.substring(startIndex, end))
        }
        return end
    }

    private fun parseNumber(builder: AnnotatedString.Builder, text: String, startIndex: Int): Int {
        var end = startIndex
        var hasDecimal = false
        var hasExponent = false

        // Handle hex, binary, octal
        if (text[end] == '0' && end + 1 < text.length) {
            when (text[end + 1].lowercaseChar()) {
                'x' -> {
                    end += 2
                    while (end < text.length && (text[end].isDigit() || text[end].lowercaseChar() in 'a'..'f')) {
                        end++
                    }
                    builder.withStyle(SpanStyle(color = colors.syntaxNumber)) {
                        append(text.substring(startIndex, end))
                    }
                    return end
                }
                'b' -> {
                    end += 2
                    while (end < text.length && text[end] in "01") {
                        end++
                    }
                    builder.withStyle(SpanStyle(color = colors.syntaxNumber)) {
                        append(text.substring(startIndex, end))
                    }
                    return end
                }
                'o' -> {
                    end += 2
                    while (end < text.length && text[end] in '0'..'7') {
                        end++
                    }
                    builder.withStyle(SpanStyle(color = colors.syntaxNumber)) {
                        append(text.substring(startIndex, end))
                    }
                    return end
                }
            }
        }

        while (end < text.length) {
            when {
                text[end].isDigit() -> end++
                text[end] == '.' && !hasDecimal && !hasExponent -> {
                    hasDecimal = true
                    end++
                }
                text[end].lowercaseChar() == 'e' && !hasExponent -> {
                    hasExponent = true
                    end++
                    if (end < text.length && text[end] in "+-") end++
                }
                text[end] == '_' -> end++ // Numeric separator
                else -> break
            }
        }

        builder.withStyle(SpanStyle(color = colors.syntaxNumber)) {
            append(text.substring(startIndex, end))
        }
        return end
    }

    private fun parseIdentifier(builder: AnnotatedString.Builder, text: String, startIndex: Int, keywords: Set<String>): Int {
        var end = startIndex
        while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_' || text[end] == '$')) {
            end++
        }

        val word = text.substring(startIndex, end)

        when {
            word in keywords -> {
                builder.withStyle(SpanStyle(color = colors.syntaxKeyword)) {
                    append(word)
                }
            }
            end < text.length && text[end] == '(' -> {
                builder.withStyle(SpanStyle(color = colors.syntaxFunction)) {
                    append(word)
                }
            }
            word.first().isUpperCase() && word.any { it.isLowerCase() } -> {
                // Likely a class/type name
                builder.withStyle(SpanStyle(color = SyntaxColors.ClassName)) {
                    append(word)
                }
            }
            else -> {
                builder.append(word)
            }
        }

        return end
    }

    private fun highlightJson(text: String): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                when {
                    text[i] == '"' -> {
                        var end = i + 1
                        while (end < text.length && text[end] != '"') {
                            if (text[end] == '\\' && end + 1 < text.length) end++
                            end++
                        }
                        if (end < text.length) end++

                        // Check if this is a key (followed by colon)
                        var checkIndex = end
                        while (checkIndex < text.length && text[checkIndex].isWhitespace()) checkIndex++
                        val isKey = checkIndex < text.length && text[checkIndex] == ':'

                        withStyle(SpanStyle(color = if (isKey) colors.syntaxAttribute else colors.syntaxString)) {
                            append(text.substring(i, end))
                        }
                        i = end
                    }
                    text[i].isDigit() || (text[i] == '-' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                        var end = i
                        if (text[end] == '-') end++
                        while (end < text.length && (text[end].isDigit() || text[end] == '.' || text[end].lowercaseChar() in "e+-")) {
                            end++
                        }
                        withStyle(SpanStyle(color = colors.syntaxNumber)) {
                            append(text.substring(i, end))
                        }
                        i = end
                    }
                    text.startsWith("true", i) -> {
                        withStyle(SpanStyle(color = colors.syntaxKeyword)) { append("true") }
                        i += 4
                    }
                    text.startsWith("false", i) -> {
                        withStyle(SpanStyle(color = colors.syntaxKeyword)) { append("false") }
                        i += 5
                    }
                    text.startsWith("null", i) -> {
                        withStyle(SpanStyle(color = colors.syntaxKeyword)) { append("null") }
                        i += 4
                    }
                    text[i] in "{}[]" -> {
                        withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                            append(text[i])
                        }
                        i++
                    }
                    text[i] == ':' || text[i] == ',' -> {
                        withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                            append(text[i])
                        }
                        i++
                    }
                    else -> {
                        append(text[i])
                        i++
                    }
                }
            }
        }
    }

    companion object {
        private val JS_KEYWORDS = setOf(
            "const", "let", "var", "function", "return", "if", "else", "for", "while",
            "do", "switch", "case", "break", "continue", "try", "catch", "finally",
            "throw", "new", "class", "extends", "import", "export", "default", "from",
            "async", "await", "yield", "this", "super", "typeof", "instanceof", "in",
            "of", "null", "undefined", "true", "false", "void", "delete", "with",
            "debugger", "static", "get", "set", "constructor"
        )

        private val TS_KEYWORDS = JS_KEYWORDS + setOf(
            "interface", "type", "enum", "namespace", "module", "declare", "abstract",
            "implements", "private", "protected", "public", "readonly", "as", "is",
            "keyof", "infer", "never", "unknown", "any", "string", "number", "boolean",
            "object", "symbol", "bigint"
        )
    }
}
