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
}
