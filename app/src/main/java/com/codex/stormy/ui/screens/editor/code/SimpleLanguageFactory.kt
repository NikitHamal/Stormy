package com.codex.stormy.ui.screens.editor.code

import android.os.Bundle
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.QuickQuoteHandler
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.StyleReceiver
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Factory for creating simple syntax highlighting languages
 * Provides basic keyword and token highlighting without full TextMate grammar
 */
object SimpleLanguageFactory {

    /**
     * Get appropriate language for file extension
     */
    fun getLanguage(extension: String): Language {
        return when (extension.lowercase()) {
            "html", "htm" -> SimpleLanguage(HtmlConfig)
            "css", "scss", "sass", "less" -> SimpleLanguage(CssConfig)
            "js", "mjs", "jsx" -> SimpleLanguage(JavaScriptConfig)
            "ts", "tsx" -> SimpleLanguage(TypeScriptConfig)
            "json" -> SimpleLanguage(JsonConfig)
            "md", "markdown" -> SimpleLanguage(MarkdownConfig)
            "kt", "kts" -> SimpleLanguage(KotlinConfig)
            "java" -> SimpleLanguage(JavaConfig)
            "py" -> SimpleLanguage(PythonConfig)
            "xml", "svg" -> SimpleLanguage(XmlConfig)
            else -> EmptyLanguage()
        }
    }
}

/**
 * Language configuration interface
 */
interface LanguageConfig {
    val keywords: Set<String>
    val types: Set<String>
    val builtins: Set<String>
    val operators: String
    val stringDelimiters: String
    val singleLineComment: String?
    val blockCommentStart: String?
    val blockCommentEnd: String?
}

/**
 * Simple language implementation using incremental analysis
 */
class SimpleLanguage(private val config: LanguageConfig) : Language {

    private var receiver: StyleReceiver? = null
    private var content: ContentReference? = null
    private val analyzeManager = SimpleAnalyzeManagerImpl(config)

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_STRONG

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        // No auto-completion for simple language
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        val text = content.getLine(line)
        val trimmed = text.toString().trimEnd()
        return if (trimmed.endsWith("{") || trimmed.endsWith(":") || trimmed.endsWith("(")) {
            4
        } else {
            0
        }
    }

    override fun useTab(): Boolean = false

    override fun getFormatter(): Formatter = object : Formatter {
        override fun format(text: Content, cursorRange: io.github.rosemoe.sora.text.TextRange) {}
        override fun formatRegion(text: Content, rangeToFormat: io.github.rosemoe.sora.text.TextRange, cursorRange: io.github.rosemoe.sora.text.TextRange) {}
        override fun setReceiver(receiver: Formatter.FormatResultReceiver?) {}
        override fun isRunning(): Boolean = false
        override fun destroy() {}
    }

    override fun getSymbolPairs(): SymbolPairMatch = SymbolPairMatch.DefaultSymbolPairs()

    override fun getNewlineHandlers(): Array<NewlineHandler>? = null

    override fun getQuickQuoteHandler(): QuickQuoteHandler? = null

    override fun destroy() {
        analyzeManager.destroy()
    }
}

/**
 * Simple analyze manager that provides basic syntax highlighting
 */
class SimpleAnalyzeManagerImpl(private val config: LanguageConfig) : AnalyzeManager {

    private var receiver: StyleReceiver? = null
    private var reference: ContentReference? = null
    @Volatile
    private var shouldReanalyze = false

    override fun setReceiver(receiver: StyleReceiver?) {
        this.receiver = receiver
    }

    override fun reset(content: ContentReference, extraArguments: Bundle) {
        this.reference = content
        rerun()
    }

    override fun insert(start: CharPosition, end: CharPosition, insertedContent: CharSequence) {
        rerun()
    }

    override fun delete(start: CharPosition, end: CharPosition, deletedContent: CharSequence) {
        rerun()
    }

    override fun rerun() {
        shouldReanalyze = true
        reference?.let { content ->
            try {
                val styles = analyze(content)
                receiver?.setStyles(this, styles)
            } catch (e: Exception) {
                // Silently handle analysis errors
            }
        }
    }

    override fun destroy() {
        receiver = null
        reference = null
    }

    private fun analyze(content: ContentReference): Styles {
        val styles = Styles()
        val spansBuilder = MappedSpans.Builder()
        val lineCount = content.lineCount

        for (line in 0 until lineCount) {
            val lineContent = content.getLine(line)
            val spans = analyzeLine(lineContent.toString(), line)
            for (span in spans) {
                spansBuilder.add(line, span)
            }
        }
        spansBuilder.determine(lineCount)

        styles.spans = spansBuilder.build()
        return styles
    }

    private fun analyzeLine(line: String, lineNumber: Int): MutableList<Span> {
        val spans = mutableListOf<Span>()
        var i = 0

        while (i < line.length) {
            val c = line[i]

            // Check for single line comment
            config.singleLineComment?.let { commentStart ->
                if (line.startsWith(commentStart, i)) {
                    if (i > 0 && spans.isEmpty()) {
                        spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                    }
                    spans.add(Span.obtain(i, TextStyle.makeStyle(EditorColorScheme.COMMENT)))
                    return spans
                }
            }

            // Check for block comment start
            val blockCommentStart = config.blockCommentStart
            if (blockCommentStart != null && line.startsWith(blockCommentStart, i)) {
                if (i > 0 && spans.isEmpty()) {
                    spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                }
                spans.add(Span.obtain(i, TextStyle.makeStyle(EditorColorScheme.COMMENT)))
                // Find end of block comment
                val blockCommentEnd = config.blockCommentEnd
                val endIdx = if (blockCommentEnd != null) line.indexOf(blockCommentEnd, i + blockCommentStart.length) else -1
                if (endIdx != -1) {
                    i = endIdx + (blockCommentEnd?.length ?: 0)
                } else {
                    return spans
                }
            }

            // Check for string start
            if (c in config.stringDelimiters) {
                if (spans.isEmpty() && i > 0) {
                    spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                }
                spans.add(Span.obtain(i, TextStyle.makeStyle(EditorColorScheme.LITERAL)))
                val endIdx = findStringEnd(line, i + 1, c)
                i = if (endIdx != -1) endIdx + 1 else line.length
                if (i < line.length) {
                    spans.add(Span.obtain(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                }
                continue
            }

            // Check for numbers
            if (c.isDigit() || (c == '.' && i + 1 < line.length && line[i + 1].isDigit())) {
                if (spans.isEmpty() && i > 0) {
                    spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                }
                spans.add(Span.obtain(i, TextStyle.makeStyle(EditorColorScheme.LITERAL)))
                while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == 'x' ||
                            line[i] in 'a'..'f' || line[i] in 'A'..'F' || line[i] == '_')) {
                    i++
                }
                if (i < line.length) {
                    spans.add(Span.obtain(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                }
                continue
            }

            // Check for operators
            if (c in config.operators) {
                if (spans.isEmpty() && i > 0) {
                    spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                }
                spans.add(Span.obtain(i, TextStyle.makeStyle(EditorColorScheme.OPERATOR)))
                i++
                if (i < line.length) {
                    spans.add(Span.obtain(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                }
                continue
            }

            // Check for keywords/identifiers
            if (c.isLetter() || c == '_' || c == '$') {
                val start = i
                while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '$')) {
                    i++
                }
                val word = line.substring(start, i)

                if (spans.isEmpty() && start > 0) {
                    spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                }

                val style = when {
                    word in config.keywords -> TextStyle.makeStyle(EditorColorScheme.KEYWORD)
                    word in config.types -> TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_VAR)
                    word in config.builtins -> TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME)
                    word.firstOrNull()?.isUpperCase() == true -> TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_VAR)
                    else -> TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME)
                }

                spans.add(Span.obtain(start, style))
                if (i < line.length) {
                    spans.add(Span.obtain(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                }
                continue
            }

            i++
        }

        // Ensure at least one span
        if (spans.isEmpty()) {
            spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
        }

        return spans
    }

    private fun findStringEnd(line: String, startIndex: Int, delimiter: Char): Int {
        var i = startIndex
        while (i < line.length) {
            if (line[i] == delimiter && (i == 0 || line[i - 1] != '\\')) {
                return i
            }
            i++
        }
        return -1
    }
}

// Language Configurations

object HtmlConfig : LanguageConfig {
    override val keywords = setOf(
        "html", "head", "body", "title", "meta", "link", "script", "style",
        "div", "span", "p", "a", "img", "ul", "ol", "li", "table", "tr", "td", "th",
        "form", "input", "button", "select", "option", "textarea", "label",
        "header", "footer", "nav", "main", "section", "article", "aside",
        "h1", "h2", "h3", "h4", "h5", "h6", "strong", "em", "br", "hr"
    )
    override val types = setOf<String>()
    override val builtins = setOf("class", "id", "src", "href", "alt", "type", "value", "name", "placeholder")
    override val operators = "<>/="
    override val stringDelimiters = "\"'"
    override val singleLineComment: String? = null
    override val blockCommentStart = "<!--"
    override val blockCommentEnd = "-->"
}

object CssConfig : LanguageConfig {
    override val keywords = setOf(
        "color", "background", "background-color", "font-size", "font-family", "font-weight",
        "margin", "padding", "border", "width", "height", "display", "position",
        "top", "right", "bottom", "left", "flex", "grid", "align-items", "justify-content",
        "text-align", "line-height", "z-index", "overflow", "opacity", "transform",
        "transition", "animation", "box-shadow", "border-radius", "cursor"
    )
    override val types = setOf<String>()
    override val builtins = setOf(
        "important", "inherit", "initial", "none", "auto", "block", "inline",
        "absolute", "relative", "fixed", "sticky", "center", "start", "end"
    )
    override val operators = "{}:;,.#"
    override val stringDelimiters = "\"'"
    override val singleLineComment = "//"
    override val blockCommentStart = "/*"
    override val blockCommentEnd = "*/"
}

object JavaScriptConfig : LanguageConfig {
    override val keywords = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while", "do",
        "switch", "case", "default", "break", "continue", "try", "catch", "finally", "throw",
        "class", "extends", "constructor", "super", "this", "new", "delete", "typeof", "instanceof",
        "import", "export", "from", "as", "async", "await", "yield"
    )
    override val types = setOf("true", "false", "null", "undefined", "NaN", "Infinity")
    override val builtins = setOf(
        "console", "document", "window", "Array", "Object", "String", "Number", "Boolean",
        "Promise", "Map", "Set", "JSON", "Math", "Date", "RegExp", "Error"
    )
    override val operators = "+-*/%=<>!&|?:.,;()[]{}@"
    override val stringDelimiters = "\"'`"
    override val singleLineComment = "//"
    override val blockCommentStart = "/*"
    override val blockCommentEnd = "*/"
}

object TypeScriptConfig : LanguageConfig {
    override val keywords = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while", "do",
        "switch", "case", "default", "break", "continue", "try", "catch", "finally", "throw",
        "class", "extends", "constructor", "super", "this", "new", "delete", "typeof", "instanceof",
        "import", "export", "from", "as", "async", "await", "yield",
        "type", "interface", "enum", "implements", "private", "public", "protected", "readonly",
        "static", "abstract", "declare", "namespace", "module"
    )
    override val types = setOf(
        "true", "false", "null", "undefined", "NaN", "Infinity",
        "any", "void", "string", "number", "boolean", "object", "symbol", "bigint",
        "keyof", "infer", "never", "unknown"
    )
    override val builtins = setOf(
        "console", "document", "window", "Array", "Object", "String", "Number", "Boolean",
        "Promise", "Map", "Set", "JSON", "Math", "Date", "RegExp", "Error"
    )
    override val operators = "+-*/%=<>!&|?:.,;()[]{}@"
    override val stringDelimiters = "\"'`"
    override val singleLineComment = "//"
    override val blockCommentStart = "/*"
    override val blockCommentEnd = "*/"
}

object JsonConfig : LanguageConfig {
    override val keywords = setOf<String>()
    override val types = setOf("true", "false", "null")
    override val builtins = setOf<String>()
    override val operators = "{}[]:,"
    override val stringDelimiters = "\""
    override val singleLineComment: String? = null
    override val blockCommentStart: String? = null
    override val blockCommentEnd: String? = null
}

object MarkdownConfig : LanguageConfig {
    override val keywords = setOf<String>()
    override val types = setOf<String>()
    override val builtins = setOf<String>()
    override val operators = "#*_->[]()!"
    override val stringDelimiters = "`"
    override val singleLineComment: String? = null
    override val blockCommentStart: String? = null
    override val blockCommentEnd: String? = null
}

object KotlinConfig : LanguageConfig {
    override val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "enum", "sealed", "data", "annotation",
        "open", "abstract", "final", "override", "private", "protected", "public", "internal",
        "if", "else", "when", "for", "while", "do", "return", "break", "continue",
        "try", "catch", "finally", "throw", "is", "as", "in", "out", "by", "where",
        "import", "package", "companion", "init", "constructor", "suspend", "inline", "crossinline",
        "reified", "noinline", "tailrec", "operator", "infix", "lateinit", "lazy"
    )
    override val types = setOf(
        "Int", "Long", "Float", "Double", "Boolean", "String", "Char", "Byte", "Short",
        "Unit", "Nothing", "Any", "Array", "List", "Map", "Set", "Pair", "Triple"
    )
    override val builtins = setOf("true", "false", "null", "this", "super", "it")
    override val operators = "+-*/%=<>!&|?:.,;()[]{}@"
    override val stringDelimiters = "\"'"
    override val singleLineComment = "//"
    override val blockCommentStart = "/*"
    override val blockCommentEnd = "*/"
}

object JavaConfig : LanguageConfig {
    override val keywords = setOf(
        "public", "private", "protected", "static", "final", "abstract", "synchronized",
        "class", "interface", "enum", "extends", "implements", "throws", "throw",
        "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return",
        "try", "catch", "finally", "new", "this", "super", "instanceof",
        "import", "package", "void"
    )
    override val types = setOf(
        "boolean", "byte", "short", "int", "long", "float", "double", "char",
        "String", "Object", "Integer", "Long", "Float", "Double", "Boolean"
    )
    override val builtins = setOf("true", "false", "null")
    override val operators = "+-*/%=<>!&|?:.,;()[]{}@"
    override val stringDelimiters = "\"'"
    override val singleLineComment = "//"
    override val blockCommentStart = "/*"
    override val blockCommentEnd = "*/"
}

object PythonConfig : LanguageConfig {
    override val keywords = setOf(
        "def", "class", "if", "elif", "else", "for", "while", "try", "except", "finally",
        "with", "as", "import", "from", "return", "yield", "break", "continue", "pass",
        "raise", "assert", "del", "global", "nonlocal", "lambda", "and", "or", "not", "in", "is",
        "async", "await"
    )
    override val types = setOf(
        "int", "float", "str", "bool", "list", "dict", "tuple", "set", "bytes",
        "None", "True", "False"
    )
    override val builtins = setOf(
        "self", "print", "len", "range", "type", "isinstance", "open", "input",
        "int", "float", "str", "bool", "list", "dict", "tuple", "set"
    )
    override val operators = "+-*/%=<>!&|:.,()[]{}@"
    override val stringDelimiters = "\"'"
    override val singleLineComment = "#"
    override val blockCommentStart: String? = null
    override val blockCommentEnd: String? = null
}

object XmlConfig : LanguageConfig {
    override val keywords = setOf<String>()
    override val types = setOf<String>()
    override val builtins = setOf<String>()
    override val operators = "<>/=?!"
    override val stringDelimiters = "\"'"
    override val singleLineComment: String? = null
    override val blockCommentStart = "<!--"
    override val blockCommentEnd = "-->"
}
