package com.oro.app.ui.screens.editor.code

import com.google.common.truth.Truth.assertThat
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.junit.Test

class SimpleTokenizerTest {

    private fun style(id: Int) = TextStyle.makeStyle(id)
    private val comment = style(EditorColorScheme.COMMENT)
    private val normal = style(EditorColorScheme.TEXT_NORMAL)
    private val keyword = style(EditorColorScheme.KEYWORD)
    private val literal = style(EditorColorScheme.LITERAL)

    private fun stylesAt(line: String, inComment: Boolean = false, config: LanguageConfig = JavaScriptConfig) =
        SimpleTokenizer.tokenize(line, inComment, config)

    @Test
    fun `keywords strings and a line comment`() {
        val r = stylesAt("""const s = "a"; // hi""")
        assertThat(r.endsInBlockComment).isFalse()
        assertThat(r.tokens.first()).isEqualTo(SimpleTokenizer.Token(0, keyword))
        assertThat(r.tokens).contains(SimpleTokenizer.Token(10, literal))
        assertThat(r.tokens.last()).isEqualTo(SimpleTokenizer.Token(15, comment))
    }

    @Test
    fun `a block comment carries its state across lines`() {
        val first = stylesAt("let a = 1; /* starts here")
        assertThat(first.endsInBlockComment).isTrue()
        assertThat(first.tokens.last()).isEqualTo(SimpleTokenizer.Token(11, comment))

        val middle = stylesAt("still inside const let", inComment = true)
        assertThat(middle.endsInBlockComment).isTrue()
        assertThat(middle.tokens).containsExactly(SimpleTokenizer.Token(0, comment))

        val last = stylesAt("ends */ let b", inComment = true)
        assertThat(last.endsInBlockComment).isFalse()
        assertThat(last.tokens[0]).isEqualTo(SimpleTokenizer.Token(0, comment))
        assertThat(last.tokens[1]).isEqualTo(SimpleTokenizer.Token(7, normal))
        assertThat(last.tokens).contains(SimpleTokenizer.Token(8, keyword))
    }

    @Test
    fun `a block comment closed on its own line leaves the state normal`() {
        val r = stylesAt("x /* note */ y")
        assertThat(r.endsInBlockComment).isFalse()
        assertThat(r.tokens).contains(SimpleTokenizer.Token(2, comment))
        assertThat(r.tokens).contains(SimpleTokenizer.Token(12, normal))
    }

    @Test
    fun `html comments span lines too`() {
        assertThat(stylesAt("<div> <!-- hidden", config = HtmlConfig).endsInBlockComment).isTrue()
        assertThat(stylesAt("still -->", inComment = true, config = HtmlConfig).endsInBlockComment).isFalse()
    }

    @Test
    fun `languages without block comments never enter the comment state`() {
        val r = stylesAt("""{"a": "/* not a comment"}""", config = JsonConfig)
        assertThat(r.endsInBlockComment).isFalse()
        assertThat(r.tokens).doesNotContain(SimpleTokenizer.Token(6, comment))
    }

    @Test
    fun `an empty or blank line still gets one span`() {
        assertThat(stylesAt("").tokens).containsExactly(SimpleTokenizer.Token(0, normal))
        assertThat(stylesAt("    ").tokens).containsExactly(SimpleTokenizer.Token(0, normal))
    }

    @Test
    fun `an escaped quote does not end a string`() {
        val r = stylesAt("""x = "a\"b" + 1""")
        assertThat(r.tokens).contains(SimpleTokenizer.Token(4, literal))
        // The string runs to the unescaped quote at 9; normal text resumes at 10.
        assertThat(r.tokens).contains(SimpleTokenizer.Token(10, normal))
    }
}
