package com.codex.stormy.ui.screens.editor.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
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

    if (currentFile == null) {
        EmptyEditorState(modifier = Modifier.fillMaxSize())
    } else {
        Scaffold(
            floatingActionButton = {
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
        ) { innerPadding ->
            CodeEditor(
                content = fileContent,
                onContentChange = onContentChange,
                fileExtension = currentFile.extension,
                showLineNumbers = lineNumbers,
                wordWrap = wordWrap,
                fontSize = fontSize,
                filePath = currentFile.path,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(extendedColors.editorBackground)
            )
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
        }
    }

    // Derived line count for performance
    val lineCount by remember {
        derivedStateOf { textFieldValue.text.count { it == '\n' } + 1 }
    }

    val textStyle = remember(fontSize) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.5).sp
        )
    }

    // Memoized syntax highlighter
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
                extendedColors = extendedColors,
                modifier = Modifier
                    .background(extendedColors.editorBackground)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
            },
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
                .focusRequester(focusRequester)
                .then(if (wordWrap) Modifier.fillMaxWidth() else Modifier),
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = { annotatedString ->
                val highlighted = syntaxHighlighter.highlight(annotatedString.text)
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
    extendedColors: ExtendedColors,
    modifier: Modifier = Modifier
) {
    val maxLineNumber = remember(lineCount) { lineCount.toString().length }
    val lineWidth = remember(maxLineNumber, fontSize) { (maxLineNumber * fontSize * 0.6).dp + 8.dp }

    val textStyle = remember(fontSize, extendedColors.lineNumber) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.5).sp,
            color = extendedColors.lineNumber
        )
    }

    Column(modifier = modifier) {
        repeat(lineCount.coerceAtLeast(1)) { index ->
            Text(
                text = (index + 1).toString().padStart(maxLineNumber),
                style = textStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.width(lineWidth)
            )
        }
    }
}

/**
 * Optimized syntax highlighter with caching
 */
private class SyntaxHighlighter(
    private val extension: String,
    private val colors: ExtendedColors
) {
    private var cachedText: String = ""
    private var cachedResult: AnnotatedString = AnnotatedString("")

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
