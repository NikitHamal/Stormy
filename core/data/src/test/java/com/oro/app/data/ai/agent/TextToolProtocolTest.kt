package com.oro.app.data.ai.agent

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.tools.AgentTools
import org.junit.Test

class TextToolProtocolTest {

    @Test
    fun `signatures carry types, optional markers and nested shapes`() {
        assertThat(TextToolProtocol.signature(AgentTools.GLOB)).isEqualTo("glob(pattern: string, path?: string)")
        val multi = TextToolProtocol.signature(AgentTools.MULTI_EDIT)
        assertThat(multi).startsWith("multi_edit(path: string, edits: [{old_string: string, new_string: string")
        assertThat(TextToolProtocol.signature(AgentTools.ASK_USER)).contains("questions: [{question: string")
    }

    @Test
    fun `first sentence is never cut at e g`() {
        assertThat(TextToolProtocol.firstSentence(AgentTools.GLOB.function.description))
            .isEqualTo("Find files by name pattern, e.g. \"**/*.css\", \"src/**/*.{js,ts}\", \"*.html\".")
    }

    @Test
    fun `rendered protocol lists every tool once`() {
        val tools = listOf(AgentTools.READ_FILE, AgentTools.EDIT_FILE, AgentTools.ASK_USER)
        val text = TextToolProtocol.render(tools)
        tools.forEach { assertThat(text).contains("- " + it.function.name + "(") }
        assertThat(text).contains("<tool_call>")
    }

    @Test
    fun `protocol teaches raw parameters and hides multi_edit in text mode`() {
        val text = TextToolProtocol.render(listOf(AgentTools.READ_FILE, AgentTools.MULTI_EDIT, AgentTools.EDIT_FILE))
        assertThat(text).contains("<function=TOOL_NAME>")
        assertThat(text).contains("<parameter=NAME>value</parameter>")
        assertThat(text).contains("never escape quotes or newlines")
        assertThat(text).doesNotContain("- multi_edit(")
        assertThat(text).contains("- edit_file(")
        // Deterministic, so it can sit in the cached prompt prefix.
        assertThat(TextToolProtocol.render(listOf(AgentTools.READ_FILE))).isEqualTo(TextToolProtocol.render(listOf(AgentTools.READ_FILE)))
    }

    @Test
    fun `the example in the protocol parses as a call`() {
        val text = TextToolProtocol.render(listOf(AgentTools.EDIT_FILE))
        val parsed = TextToolCallParser.parse(text.substringAfter("Example:"), setOf("edit_file"))
        assertThat(parsed.calls.map { it.function.name }).containsExactly("edit_file")
        assertThat(parsed.calls.single().function.arguments).contains("Hello, \\\"friend\\\"")
    }

    @Test
    fun `formatCall writes multi-line values on their own lines`() {
        val out = TextToolProtocol.formatCall("write_file", """{"path":"a.css","content":"a{}\nb{}"}""")
        assertThat(out).isEqualTo(
            "<tool_call>\n<function=write_file>\n<parameter=path>a.css</parameter>\n" +
                "<parameter=content>\na{}\nb{}\n</parameter>\n</function>\n</tool_call>"
        )
    }
}
