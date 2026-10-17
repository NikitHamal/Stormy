package com.oro.app.data.ai.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextToolCallParserTest {

    private val known = setOf("read_file", "write_file", "edit_file", "ask_user", "list_files")

    @Test
    fun `hermes tool_call tags with nested braces in content`() {
        val content = """
            I'll create the stylesheet.
            <tool_call>
            {"name": "write_file", "arguments": {"path": "style.css", "content": "body { margin: 0; }\n.a { color: red; }"}}
            </tool_call>
        """.trimIndent()
        val parsed = TextToolCallParser.parse(content, known)
        assertThat(parsed.calls).hasSize(1)
        assertThat(parsed.calls[0].function.name).isEqualTo("write_file")
        assertThat(parsed.calls[0].function.arguments).contains(".a { color: red; }")
        assertThat(parsed.visibleText).isEqualTo("I'll create the stylesheet.")
    }

    @Test
    fun `qwen3 coder xml function format`() {
        val content = """
            <function=edit_file>
            <parameter=path>
            index.html
            </parameter>
            <parameter=old_string>
            <h1>Hi</h1>
            </parameter>
            <parameter=new_string>
            <h1>Hello</h1>
            </parameter>
            </function>
        """.trimIndent()
        val call = TextToolCallParser.parse(content, known).calls.single()
        assertThat(call.function.name).isEqualTo("edit_file")
        assertThat(call.function.arguments).contains("\"path\":\"index.html\"")
        assertThat(call.function.arguments).contains("<h1>Hello</h1>")
    }

    @Test
    fun `mistral marker and fenced json and aliases`() {
        val mistral = TextToolCallParser.parse("""[TOOL_CALLS][{"name":"read_file","arguments":{"path":"a.js"}}]""", known)
        assertThat(mistral.calls.single().function.name).isEqualTo("read_file")

        val fenced = TextToolCallParser.parse("Reading it:\n```json\n{\"name\": \"view\", \"arguments\": {\"path\": \"b.js\"}}\n```", known)
        // "view" is an alias of read_file, so it is accepted.
        assertThat(fenced.calls.single().function.name).isEqualTo("view")
        assertThat(fenced.visibleText).isEqualTo("Reading it:")

        val openAiShape = TextToolCallParser.parse("""{"type":"function","function":{"name":"list_files","arguments":"{}"}}""", known)
        assertThat(openAiShape.calls.single().function.arguments).isEqualTo("{}")
    }

    @Test
    fun `prose and unknown tools are not executed`() {
        val prose = "You could call delete_file(path='index.html') to remove it, or use read_file."
        assertThat(TextToolCallParser.parse(prose, known).calls).isEmpty()

        val example = "Here is JSON:\n```json\n{\"name\": \"Alice\", \"age\": 3}\n```"
        assertThat(TextToolCallParser.parse(example, known).calls).isEmpty()

        val unknown = "<tool_call>{\"name\": \"format_disk\", \"arguments\": {}}</tool_call>"
        assertThat(TextToolCallParser.parse(unknown, known).calls).isEmpty()
    }

    @Test
    fun `balanced json extraction ignores braces in strings`() {
        assertThat(TextToolCallParser.extractJson("x {\"a\": \"}{\", \"b\": [1, {\"c\": 2}]} tail"))
            .isEqualTo("{\"a\": \"}{\", \"b\": [1, {\"c\": 2}]}")
    }

    @Test
    fun `k2 horizon namespaced key-value tool calls`() {
        // Recorded from chat.ifm.ai on 2026-10-17, including the malformed first key.
        val content = """
            <ifm|tool_calls>
            <ifm|tool_call>read_file
            <ifm|arg_key>path":
            <ifm|arg_value>index.html</ifm|arg_value>
            </ifm|tool_call>
            <ifm|tool_call>read_file
            <ifm|arg_key>path</ifm|arg_key>
            <ifm|arg_value>style.css</ifm|arg_value>
            </ifm|tool_call>
            </ifm|tool_calls>
        """.trimIndent()
        val parsed = TextToolCallParser.parse(content, known)
        assertThat(parsed.calls.map { it.function.arguments })
            .containsExactly("{\"path\":\"index.html\"}", "{\"path\":\"style.css\"}").inOrder()
        assertThat(parsed.visibleText).isEmpty()
    }

    @Test
    fun `glm key-value form with multi-line html value`() {
        val content = "<tool_call>write_file<arg_key>path</arg_key><arg_value>a.html</arg_value>" +
            "<arg_key>content</arg_key><arg_value><p>\n  hi\n</p></arg_value></tool_call>"
        val call = TextToolCallParser.parse(content, known).calls.single()
        assertThat(call.function.name).isEqualTo("write_file")
        assertThat(call.function.arguments).contains("<p>\\n  hi\\n</p>")
    }

    @Test
    fun `k2 horizon json-in-key-value hybrids are repaired`() {
        // Recorded from chat.ifm.ai on 2026-10-17.
        val content = """
            <ifm|tool_calls>
            <ifm|tool_call>read_file
            <ifm|arg_key>path": "index.html"}}</ifm|tool_call>
            <ifm|tool_call>grep
            <ifm|arg_key>pattern</ifm|arg_key> ".*", "path": "style.css", "include": "*.css"}}</ifm|tool_call>
            <ifm|tool_call>read_file
            <ifm|arg_key>path": "style.css</ifm|arg_value>
            </ifm|tool_call>
            </ifm|tool_calls>
        """.trimIndent()
        val calls = TextToolCallParser.parse(content, known + "grep").calls
        assertThat(calls).hasSize(3)
        assertThat(calls[0].function.arguments).isEqualTo("{\"path\":\"index.html\"}")
        assertThat(calls[2].function.arguments).isEqualTo("{\"path\":\"style.css\"}")
        assertThat(calls[1].function.name).isEqualTo("grep")
        assertThat(calls[1].function.arguments).isEqualTo("{\"pattern\":\".*\",\"path\":\"style.css\",\"include\":\"*.css\"}")
    }
}
