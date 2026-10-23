package com.oro.app.ui.screens.editor.code

import android.os.Bundle
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.QuickQuoteHandler
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.StyleReceiver
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Span
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
 * Highlighting for [SimpleLanguage], incremental and off the main thread.
 *
 * It used to re-tokenize the whole document synchronously on every insert and
 * delete -- i.e. on the UI thread, per keystroke -- so typing in a 2,000-line
 * page meant tens of milliseconds of span building before each character
 * appeared on a low-end phone. Sora's [AsyncIncrementalAnalyzeManager] runs
 * [tokenizeLine] on its own thread, re-tokenizes only the lines an edit
 * touched (plus following lines while their start state differs), and keeps
 * the existing spans aligned while it works.
 *
 * The per-line state is whether the line starts inside a block comment, so
 * a `/* ... */` or `<!-- ... -->` spanning lines is now coloured on every
 * line it covers, not only the first.
 */
class SimpleAnalyzeManagerImpl(
    private val config: LanguageConfig
) : AsyncIncrementalAnalyzeManager<Int, SimpleTokenizer.Token>() {

    override fun getInitialState(): Int = SimpleTokenizer.NORMAL

    override fun stateEquals(state: Int, another: Int): Boolean = state == another

    override fun tokenizeLine(
        line: CharSequence,
        state: Int,
        lineIndex: Int
    ): IncrementalAnalyzeManager.LineTokenizeResult<Int, SimpleTokenizer.Token> {
        val result = SimpleTokenizer.tokenize(line, state == SimpleTokenizer.IN_BLOCK_COMMENT, config)
        return IncrementalAnalyzeManager.LineTokenizeResult<Int, SimpleTokenizer.Token>(
            if (result.endsInBlockComment) SimpleTokenizer.IN_BLOCK_COMMENT else SimpleTokenizer.NORMAL,
            result.tokens
        )
    }

    /** Fresh spans per call: Sora pools and recycles [Span]s, so tokens never hold one. */
    override fun generateSpansForLine(
        tokens: IncrementalAnalyzeManager.LineTokenizeResult<Int, SimpleTokenizer.Token>
    ): List<Span> {
        val list: List<SimpleTokenizer.Token> = tokens.tokens ?: emptyList()
        if (list.isEmpty()) return arrayListOf(Span.obtain(0, SimpleTokenizer.NORMAL_STYLE))
        val spans = ArrayList<Span>(list.size)
        for (t in list) spans.add(Span.obtain(t.column, t.style))
        return spans
    }

    override fun computeBlocks(
        text: Content,
        delegate: AsyncIncrementalAnalyzeManager<Int, SimpleTokenizer.Token>.CodeBlockAnalyzeDelegate
    ): List<CodeBlock> = emptyList()
}

/**
 * One line of [SimpleLanguage] highlighting, as plain data: keywords, types,
 * builtins, strings, numbers, operators and comments by column. Pure (no
 * editor state), so it runs on the analyzer's thread and under unit tests.
 */
object SimpleTokenizer {

    /** A highlight run: from [column] to the next token, in [style] (a [TextStyle.makeStyle] value). */
    class Token(@JvmField val column: Int, @JvmField val style: Long) {
        override fun equals(other: Any?): Boolean = other is Token && other.column == column && other.style == style
        override fun hashCode(): Int = column * 31 + style.hashCode()
        override fun toString(): String = "Token($column, $style)"
    }

    class Result(val tokens: List<Token>, val endsInBlockComment: Boolean)

    const val NORMAL = 0
    const val IN_BLOCK_COMMENT = 1

    val NORMAL_STYLE: Long = TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
    private val COMMENT: Long = TextStyle.makeStyle(EditorColorScheme.COMMENT)
    private val LITERAL: Long = TextStyle.makeStyle(EditorColorScheme.LITERAL)
    private val OPERATOR: Long = TextStyle.makeStyle(EditorColorScheme.OPERATOR)
    private val KEYWORD: Long = TextStyle.makeStyle(EditorColorScheme.KEYWORD)
    private val TYPE: Long = TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_VAR)
    private val BUILTIN: Long = TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME)
    private val NAME: Long = TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME)

    fun tokenize(line: CharSequence, inBlockComment: Boolean, config: LanguageConfig): Result {
        val tokens = ArrayList<Token>(8)
        var i = 0
        val blockStart = config.blockCommentStart
        val blockEnd = config.blockCommentEnd

        // A line that starts inside a block comment is comment up to its end marker.
        if (inBlockComment && blockEnd != null) {
            tokens.add(Token(0, COMMENT))
            val end = indexOf(line, blockEnd, 0)
            if (end < 0) return Result(tokens, endsInBlockComment = true)
            i = end + blockEnd.length
            if (i < line.length) tokens.add(Token(i, NORMAL_STYLE))
        }

        while (i < line.length) {
            val c = line[i]

            config.singleLineComment?.let { commentStart ->
                if (startsWith(line, commentStart, i)) {
                    if (i > 0 && tokens.isEmpty()) tokens.add(Token(0, NORMAL_STYLE))
                    tokens.add(Token(i, COMMENT))
                    return Result(tokens, endsInBlockComment = false)
                }
            }

            if (blockStart != null && startsWith(line, blockStart, i)) {
                if (i > 0 && tokens.isEmpty()) tokens.add(Token(0, NORMAL_STYLE))
                tokens.add(Token(i, COMMENT))
                val endIdx = if (blockEnd != null) indexOf(line, blockEnd, i + blockStart.length) else -1
                if (endIdx < 0) return Result(tokens, endsInBlockComment = blockEnd != null)
                i = endIdx + blockEnd!!.length
                if (i < line.length) tokens.add(Token(i, NORMAL_STYLE))
                continue
            }

            if (config.stringDelimiters.indexOf(c) >= 0) {
                if (tokens.isEmpty() && i > 0) tokens.add(Token(0, NORMAL_STYLE))
                tokens.add(Token(i, LITERAL))
                val endIdx = findStringEnd(line, i + 1, c)
                i = if (endIdx != -1) endIdx + 1 else line.length
                if (i < line.length) tokens.add(Token(i, NORMAL_STYLE))
                continue
            }

            if (c.isDigit() || (c == '.' && i + 1 < line.length && line[i + 1].isDigit())) {
                if (tokens.isEmpty() && i > 0) tokens.add(Token(0, NORMAL_STYLE))
                tokens.add(Token(i, LITERAL))
                while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == 'x' ||
                        line[i] in 'a'..'f' || line[i] in 'A'..'F' || line[i] == '_')
                ) {
                    i++
                }
                if (i < line.length) tokens.add(Token(i, NORMAL_STYLE))
                continue
            }

            if (config.operators.indexOf(c) >= 0) {
                if (tokens.isEmpty() && i > 0) tokens.add(Token(0, NORMAL_STYLE))
                tokens.add(Token(i, OPERATOR))
                i++
                if (i < line.length) tokens.add(Token(i, NORMAL_STYLE))
                continue
            }

            if (c.isLetter() || c == '_' || c == '$') {
                val start = i
                while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '$')) i++
                val word = line.subSequence(start, i).toString()
                if (tokens.isEmpty() && start > 0) tokens.add(Token(0, NORMAL_STYLE))
                val style = when {
                    word in config.keywords -> KEYWORD
                    word in config.types -> TYPE
                    word in config.builtins -> BUILTIN
                    word[0].isUpperCase() -> TYPE
                    else -> NAME
                }
                tokens.add(Token(start, style))
                if (i < line.length) tokens.add(Token(i, NORMAL_STYLE))
                continue
            }

            i++
        }

        if (tokens.isEmpty()) tokens.add(Token(0, NORMAL_STYLE))
        return Result(tokens, endsInBlockComment = false)
    }

    private fun startsWith(line: CharSequence, prefix: String, at: Int): Boolean {
        if (at + prefix.length > line.length) return false
        for (k in prefix.indices) if (line[at + k] != prefix[k]) return false
        return true
    }

    private fun indexOf(line: CharSequence, needle: String, from: Int): Int {
        var at = from
        val last = line.length - needle.length
        while (at <= last) {
            if (startsWith(line, needle, at)) return at
            at++
        }
        return -1
    }

    private fun findStringEnd(line: CharSequence, startIndex: Int, delimiter: Char): Int {
        var i = startIndex
        while (i < line.length) {
            if (line[i] == delimiter && (i == 0 || line[i - 1] != '\\')) return i
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
