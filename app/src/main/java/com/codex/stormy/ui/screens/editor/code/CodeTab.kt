package com.codex.stormy.ui.screens.editor.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import com.codex.stormy.ui.theme.SyntaxColors

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
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Code,
            contentDescription = null,
            modifier = Modifier
                .padding(16.dp)
                .then(Modifier),
            tint = MaterialTheme.colorScheme.outlineVariant
        )

        Text(
            text = context.getString(R.string.editor_no_file),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CodeEditor(
    content: String,
    onContentChange: (String) -> Unit,
    fileExtension: String,
    showLineNumbers: Boolean,
    wordWrap: Boolean,
    fontSize: Float,
    modifier: Modifier = Modifier
) {
    val extendedColors = CodeXTheme.extendedColors
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    val lines = remember(content) {
        content.split("\n")
    }

    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.5).sp,
        color = MaterialTheme.colorScheme.onSurface
    )

    var textFieldValue by remember(content) {
        mutableStateOf(TextFieldValue(content))
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
                lineCount = lines.size,
                fontSize = fontSize,
                modifier = Modifier
                    .background(extendedColors.editorBackground)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onContentChange(newValue.text)
            },
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
                .then(
                    if (wordWrap) Modifier.fillMaxWidth() else Modifier
                ),
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = { annotatedString ->
                val highlighted = highlightSyntax(
                    text = annotatedString.text,
                    extension = fileExtension,
                    extendedColors = extendedColors
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
    modifier: Modifier = Modifier
) {
    val extendedColors = CodeXTheme.extendedColors
    val maxLineNumber = lineCount.toString().length

    Column(modifier = modifier) {
        repeat(lineCount.coerceAtLeast(1)) { index ->
            Text(
                text = (index + 1).toString().padStart(maxLineNumber),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.5).sp,
                    color = extendedColors.lineNumber
                ),
                textAlign = TextAlign.End,
                modifier = Modifier.width((maxLineNumber * fontSize * 0.6).dp + 8.dp)
            )
        }
    }
}

private fun highlightSyntax(
    text: String,
    extension: String,
    extendedColors: com.codex.stormy.ui.theme.ExtendedColors
): AnnotatedString {
    return when (extension.lowercase()) {
        "html", "htm" -> highlightHtml(text, extendedColors)
        "css" -> highlightCss(text, extendedColors)
        "js", "mjs" -> highlightJavaScript(text, extendedColors)
        "json" -> highlightJson(text, extendedColors)
        else -> AnnotatedString(text)
    }
}

private fun highlightHtml(
    text: String,
    extendedColors: com.codex.stormy.ui.theme.ExtendedColors
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("<!--", i) -> {
                    val end = text.indexOf("-->", i)
                    val commentEnd = if (end == -1) text.length else end + 3
                    withStyle(SpanStyle(color = extendedColors.syntaxComment)) {
                        append(text.substring(i, commentEnd))
                    }
                    i = commentEnd
                }
                text.startsWith("<!DOCTYPE", i, ignoreCase = true) -> {
                    val end = text.indexOf(">", i)
                    val tagEnd = if (end == -1) text.length else end + 1
                    withStyle(SpanStyle(color = extendedColors.syntaxKeyword)) {
                        append(text.substring(i, tagEnd))
                    }
                    i = tagEnd
                }
                text[i] == '<' && i + 1 < text.length && text[i + 1] != ' ' -> {
                    val end = text.indexOf(">", i)
                    val tagEnd = if (end == -1) text.length else end + 1
                    val tagContent = text.substring(i, tagEnd)

                    withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                        append("<")
                    }

                    val tagNameEnd = tagContent.indexOfFirst { it.isWhitespace() || it == '>' || it == '/' }
                    val tagName = if (tagNameEnd > 1) {
                        tagContent.substring(1, tagNameEnd).removePrefix("/")
                    } else {
                        tagContent.drop(1).dropLast(1).removePrefix("/")
                    }

                    if (tagContent.startsWith("</")) {
                        withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                            append("/")
                        }
                    }

                    withStyle(SpanStyle(color = extendedColors.syntaxTag)) {
                        append(tagName)
                    }

                    val attributes = tagContent.drop(tagName.length + if (tagContent.startsWith("</")) 2 else 1).dropLast(1)
                    highlightHtmlAttributes(this, attributes, extendedColors)

                    if (tagContent.endsWith("/>")) {
                        withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                            append("/")
                        }
                    }

                    withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                        append(">")
                    }

                    i = tagEnd
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

private fun highlightHtmlAttributes(
    builder: AnnotatedString.Builder,
    attributes: String,
    extendedColors: com.codex.stormy.ui.theme.ExtendedColors
) {
    val attrRegex = Regex("""(\s*)(\w+)(\s*=\s*)(["'])([^"']*)(["'])""")
    var lastEnd = 0

    attrRegex.findAll(attributes).forEach { match ->
        val whitespace = match.groupValues[1]
        val attrName = match.groupValues[2]
        val equals = match.groupValues[3]
        val openQuote = match.groupValues[4]
        val value = match.groupValues[5]
        val closeQuote = match.groupValues[6]

        builder.append(attributes.substring(lastEnd, match.range.first))
        builder.append(whitespace)
        builder.withStyle(SpanStyle(color = extendedColors.syntaxAttribute)) {
            append(attrName)
        }
        builder.withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
            append(equals)
        }
        builder.withStyle(SpanStyle(color = extendedColors.syntaxString)) {
            append(openQuote)
            append(value)
            append(closeQuote)
        }

        lastEnd = match.range.last + 1
    }

    if (lastEnd < attributes.length) {
        builder.append(attributes.substring(lastEnd))
    }
}

private fun highlightCss(
    text: String,
    extendedColors: com.codex.stormy.ui.theme.ExtendedColors
): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            var processed = false

            if (line.trim().startsWith("/*") || line.contains("*/") || (line.contains("/*") && !line.contains("*/"))) {
                withStyle(SpanStyle(color = extendedColors.syntaxComment)) {
                    append(line)
                }
                processed = true
            } else if (line.contains(":") && line.contains(";")) {
                val colonIndex = line.indexOf(":")
                val property = line.substring(0, colonIndex)
                val value = line.substring(colonIndex)

                withStyle(SpanStyle(color = extendedColors.syntaxAttribute)) {
                    append(property)
                }
                withStyle(SpanStyle(color = SyntaxColors.Punctuation)) {
                    append(":")
                }
                withStyle(SpanStyle(color = extendedColors.syntaxString)) {
                    append(value.drop(1))
                }
                processed = true
            } else if (line.contains("{") || line.contains("}")) {
                withStyle(SpanStyle(color = extendedColors.syntaxTag)) {
                    append(line)
                }
                processed = true
            }

            if (!processed) {
                append(line)
            }

            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}

private fun highlightJavaScript(
    text: String,
    extendedColors: com.codex.stormy.ui.theme.ExtendedColors
): AnnotatedString {
    val keywords = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while",
        "do", "switch", "case", "break", "continue", "try", "catch", "finally",
        "throw", "new", "class", "extends", "import", "export", "default", "from",
        "async", "await", "yield", "this", "super", "typeof", "instanceof", "in",
        "of", "null", "undefined", "true", "false"
    )

    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("//", i) -> {
                    val end = text.indexOf("\n", i)
                    val commentEnd = if (end == -1) text.length else end
                    withStyle(SpanStyle(color = extendedColors.syntaxComment)) {
                        append(text.substring(i, commentEnd))
                    }
                    i = commentEnd
                }
                text.startsWith("/*", i) -> {
                    val end = text.indexOf("*/", i)
                    val commentEnd = if (end == -1) text.length else end + 2
                    withStyle(SpanStyle(color = extendedColors.syntaxComment)) {
                        append(text.substring(i, commentEnd))
                    }
                    i = commentEnd
                }
                text[i] == '"' || text[i] == '\'' || text[i] == '`' -> {
                    val quote = text[i]
                    var end = i + 1
                    while (end < text.length && text[end] != quote) {
                        if (text[end] == '\\' && end + 1 < text.length) end++
                        end++
                    }
                    if (end < text.length) end++
                    withStyle(SpanStyle(color = extendedColors.syntaxString)) {
                        append(text.substring(i, end))
                    }
                    i = end
                }
                text[i].isDigit() -> {
                    var end = i
                    while (end < text.length && (text[end].isDigit() || text[end] == '.')) end++
                    withStyle(SpanStyle(color = extendedColors.syntaxNumber)) {
                        append(text.substring(i, end))
                    }
                    i = end
                }
                text[i].isLetter() || text[i] == '_' -> {
                    var end = i
                    while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
                    val word = text.substring(i, end)

                    when {
                        word in keywords -> {
                            withStyle(SpanStyle(color = extendedColors.syntaxKeyword)) {
                                append(word)
                            }
                        }
                        end < text.length && text[end] == '(' -> {
                            withStyle(SpanStyle(color = extendedColors.syntaxFunction)) {
                                append(word)
                            }
                        }
                        else -> append(word)
                    }
                    i = end
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

private fun highlightJson(
    text: String,
    extendedColors: com.codex.stormy.ui.theme.ExtendedColors
): AnnotatedString {
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

                    val isKey = end < text.length && text.substring(end).trimStart().startsWith(":")
                    withStyle(SpanStyle(color = if (isKey) extendedColors.syntaxAttribute else extendedColors.syntaxString)) {
                        append(text.substring(i, end))
                    }
                    i = end
                }
                text[i].isDigit() || (text[i] == '-' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    var end = i
                    if (text[end] == '-') end++
                    while (end < text.length && (text[end].isDigit() || text[end] == '.' || text[end] == 'e' || text[end] == 'E')) end++
                    withStyle(SpanStyle(color = extendedColors.syntaxNumber)) {
                        append(text.substring(i, end))
                    }
                    i = end
                }
                text.startsWith("true", i) || text.startsWith("false", i) || text.startsWith("null", i) -> {
                    val word = when {
                        text.startsWith("true", i) -> "true"
                        text.startsWith("false", i) -> "false"
                        else -> "null"
                    }
                    withStyle(SpanStyle(color = extendedColors.syntaxKeyword)) {
                        append(word)
                    }
                    i += word.length
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
