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
            textTools = true, settings = DeviceAgent.Settings(maxActions = 30),
            tools = DeviceTools.forRun(false), vision = false, fromChat = true,
        )
        assertThat(prompt).contains("<tool_call>")
        assertThat(prompt).contains("coding agent")
        // The prompt stays the same all run (prompt caching): the count travels with each screen.
        assertThat(prompt).doesNotContain("actions left")
    }

    @Test
    fun `every way a run ends has one honest reason`() {
        val done = com.oro.app.data.ai.agent.AgentRunResult.Completed("Done")
        val failed = com.oro.app.data.ai.agent.AgentRunResult.Failed("Model error", retryable = true)
        val limit = com.oro.app.data.ai.agent.AgentRunResult.IterationLimit(70)
        fun reason(r: com.oro.app.data.ai.agent.AgentRunResult, called: Boolean = false, ok: Boolean? = null,
                   cancel: DeviceAgent.CancelReason? = null, limitHit: Boolean = false) =
            DeviceAgent.endReason(r, called, ok, cancel, limitHit)

        assertThat(reason(done, called = true, ok = true)).isEqualTo(DeviceAgent.EndReason.DONE)
        assertThat(reason(done, called = true, ok = false)).isEqualTo(DeviceAgent.EndReason.FAILED)
        // The model stopped talking without finish_task: not a success.
        assertThat(reason(done)).isEqualTo(DeviceAgent.EndReason.UNCONFIRMED)
        assertThat(reason(failed, cancel = DeviceAgent.CancelReason.USER)).isEqualTo(DeviceAgent.EndReason.STOPPED_BY_USER)
        // Service lost wins over the "stopped" the cancellation looks like.
        assertThat(reason(failed, cancel = DeviceAgent.CancelReason.SERVICE_LOST)).isEqualTo(DeviceAgent.EndReason.SERVICE_LOST)
        assertThat(reason(limit)).isEqualTo(DeviceAgent.EndReason.ACTION_LIMIT)
        assertThat(reason(done, called = true, ok = false, limitHit = true)).isEqualTo(DeviceAgent.EndReason.ACTION_LIMIT)
        assertThat(reason(failed)).isEqualTo(DeviceAgent.EndReason.ERROR)
    }

    @Test
    fun `the coding agent is told plainly when the user stopped the phone`() {
        val stopped = DeviceAgent.Result(false, "", emptyList(), 3, 12, "YouTube", error = "Stopped by you.",
            endReason = DeviceAgent.EndReason.STOPPED_BY_USER)
        assertThat(stopped.stoppedByUser).isTrue()
        assertThat(stopped.forModel()).startsWith("The user stopped the phone task")
        assertThat(stopped.forModel()).contains("Do not call use_phone again")

        val unconfirmed = DeviceAgent.Result(false, "I opened the page.", emptyList(), 2, 8, "Chrome",
            endReason = DeviceAgent.EndReason.UNCONFIRMED)
        assertThat(unconfirmed.forModel()).startsWith("Phone task ended without confirming")
        assertThat(unconfirmed.stoppedByUser).isFalse()
    }

    @Test
    fun `media steps name their action`() {
        assertThat(listOf("play", "pause", "next", "previous", "forward", "rewind").map { DeviceAgent.mediaTitle(it) })
            .containsExactly("Play", "Pause", "Next", "Previous", "Forward 10 s", "Rewind 10 s").inOrder()
    }

    @Test
    fun `only talking tools run while paused`() {
        assertThat(DeviceTools.allowedWhilePaused).containsNoneOf("read_screen", "find_text", "look", "wait_for", "tap")
        assertThat(DeviceTools.allowedWhilePaused).containsAtLeast("ask_user", "finish_task", "take_note")
    }
}
