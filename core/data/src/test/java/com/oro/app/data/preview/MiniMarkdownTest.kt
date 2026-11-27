package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MiniMarkdownTest {

    @Test
    fun `headings paragraphs and emphasis`() {
        val html = MiniMarkdown.toHtml("# Title\n\nSome **bold**, *italic*, ~~gone~~ and `code`.\nSecond line.")
        assertThat(html).contains("<h1>Title</h1>")
        assertThat(html).contains("<strong>bold</strong>")
        assertThat(html).contains("<em>italic</em>")
        assertThat(html).contains("<del>gone</del>")
        assertThat(html).contains("<code>code</code>")
        assertThat(html).contains("Second line.")
        assertThat(html).contains("<br>")
    }

    @Test
    fun `html in the text is escaped, never passed through`() {
        val html = MiniMarkdown.toHtml("<script>alert(1)</script> & <img src=x onerror=y>")
        assertThat(html).doesNotContain("<script>")
        assertThat(html).doesNotContain("<img")
        assertThat(html).contains("&lt;script&gt;")
        assertThat(html).contains("&amp;")
    }

    @Test
    fun `code spans and fences are literal`() {
        val html = MiniMarkdown.toHtml("Use `**not bold**` here.\n\n```js\nconst a = <b>1</b>;\n*x*\n```\n")
        assertThat(html).contains("<code>**not bold**</code>")
        assertThat(html).contains("<pre><code class=\"language-js\">const a = &lt;b&gt;1&lt;/b&gt;;\n*x*</code></pre>")
    }

    @Test
    fun `an unclosed fence runs to the end without hanging`() {
        val html = MiniMarkdown.toHtml("```\nline one\nline two")
        assertThat(html).contains("line one\nline two")
    }

    @Test
    fun `links and images keep safe targets only`() {
        val html = MiniMarkdown.toHtml(
            "[ok](https://a.test/x?y=1&z=2) [rel](docs/a.html) [mail](mailto:a@b.c) [bad](javascript:alert(1)) ![pic](img/a.png) ![x](data:text/html,hi)"
        )
        assertThat(html).contains("<a href=\"https://a.test/x?y=1&amp;z=2\">ok</a>")
        assertThat(html).contains("<a href=\"docs/a.html\">rel</a>")
        assertThat(html).contains("<a href=\"mailto:a@b.c\">mail</a>")
        assertThat(html).doesNotContain("href=\"javascript")
        assertThat(html).contains("<img alt=\"pic\" src=\"img/a.png\">")
        assertThat(html).doesNotContain("src=\"data:")
    }

    @Test
    fun `lists quotes rules and tables`() {
        val html = MiniMarkdown.toHtml(
            "- one\n- two\n\n1. first\n2. second\n\n> quoted *text*\n\n---\n\n| A | B |\n|---|:-:|\n| 1 | 2 |\n| 3 | 4 |\n"
        )
        assertThat(html).contains("<ul><li>one</li><li>two</li></ul>")
        assertThat(html).contains("<ol><li>first</li><li>second</li></ol>")
        assertThat(html).contains("<blockquote><p>quoted <em>text</em></p>\n</blockquote>")
        assertThat(html).contains("<hr>")
        assertThat(html).contains("<th>A</th><th>B</th>")
        assertThat(html).contains("<td>3</td><td>4</td>")
    }

    @Test
    fun `underscores inside words are left alone`() {
        val html = MiniMarkdown.toHtml("snake_case_name and file_name.txt")
        assertThat(html).doesNotContain("<em>")
    }

    @Test
    fun `awkward input terminates`() {
        listOf("", "\n\n", "#", "# ", "-", "- ", "|", "| a |", ">", "```", "\u0000", "*", "**", "[](", "![](", "a|b\n--")
            .forEach { MiniMarkdown.toHtml(it) }
    }

    @Test
    fun `page wraps the body with an escaped title`() {
        val page = MiniMarkdown.page("A <b> & C", "hi")
        assertThat(page).contains("<title>A &lt;b&gt; &amp; C</title>")
        assertThat(page).contains("<main><p>hi</p>")
        assertThat(page).contains("prefers-color-scheme:dark")
    }
}
