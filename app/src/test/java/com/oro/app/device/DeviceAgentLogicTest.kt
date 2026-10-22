package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.ChatRequestMessage
import org.junit.Test

class DeviceAgentLogicTest {

    @Test
    fun `the coding agent gets a readable report`() {
        val r = DeviceAgent.Result(
            success = true, summary = "Cheapest is ₹4,120 on IndiGo.", notes = listOf("IndiGo 6:05 ₹4,120", "Air India 9:10 ₹4,600"),
            actions = 6, seconds = 41, finalApp = "Chrome"
        )
        val text = r.forModel()
        assertThat(text).startsWith("Phone task done (6 actions, 41 s, ended in Chrome).")
        assertThat(text).contains("Result: Cheapest is ₹4,120 on IndiGo.")
        assertThat(text).contains("- Air India 9:10 ₹4,600")

        val failed = DeviceAgent.Result(false, "", emptyList(), 0, 0, "", error = "The phone is busy.").forModel()
        assertThat(failed).startsWith("Phone task not completed")
        assertThat(failed).contains("Problem: The phone is busy.")
    }

    @Test
    fun `only the newest screen stays whole in history`() {
        val marker = DeviceAgent.SCREEN_MARKER
        val history = listOf(
            ChatRequestMessage("user", "Task: x\n\n$marker\nApp: A\n[0] button \"One\""),
            ChatRequestMessage("assistant", "Tapping one."),
            ChatRequestMessage("tool", "Tapped [0].\n\n$marker\n⚠ The screen did not change after this action.\nApp: A\n[0] button \"One\""),
            ChatRequestMessage("assistant", "Reading."),
            ChatRequestMessage("tool", "Page text:\n" + "lorem ".repeat(400) + "\n\n$marker\nApp: B\n[0] button \"Two\""),
        )
        val out = DeviceAgent.compact(history, pressure = 0)
        assertThat(out[0].content).doesNotContain("button \"One\"")
        assertThat(out[2].content).contains("[screen did not change]")
        assertThat(out[4].content).contains("button \"Two\"")
        // Older long outcomes are trimmed once they are no longer the latest.
        val olderPage = DeviceAgent.compact(history + ChatRequestMessage("tool", "Ok.\n\n$marker\nApp: C"), 0)[4].content.orEmpty()
        assertThat(olderPage.length).isLessThan(1_400)
    }

    @Test
    fun `vision models get look, others do not`() {
        assertThat(DeviceTools.forRun(vision = true).map { it.function.name }).contains("look")
        assertThat(DeviceTools.forRun(vision = false).map { it.function.name }).doesNotContain("look")
        assertThat(DeviceTools.names).containsAtLeast("tap_text", "scroll_to", "wait_for", "quick_action", "media", "read_page", "find_text")
    }

    @Test
    fun `text protocol signatures carry types and optional marks`() {
        assertThat(DevicePrompt.signature(DeviceTools.TAP_TEXT)).isEqualTo("tap_text(text: string, nth?: int)")
        assertThat(DevicePrompt.signature(DeviceTools.MEDIA)).startsWith("media(action: \"play\"|\"pause\"")
        val prompt = DevicePrompt.system(
            textTools = true, settings = DeviceAgent.Settings(), actionsLeft = null,
            tools = DeviceTools.forRun(false), vision = false, fromChat = true,
        )
        assertThat(prompt).contains("<tool_call>")
        assertThat(prompt).contains("coding agent")
        assertThat(prompt).doesNotContain("actions left")
    }
}
