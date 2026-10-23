package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.DeviceAgent.Step
import com.oro.app.device.DeviceAgent.StepKind
import com.oro.app.device.DeviceAgent.StepStatus
import com.oro.app.device.overlay.IslandModel
import com.oro.app.device.overlay.IslandPhase
import com.oro.app.device.overlay.IslandText
import com.oro.app.ui.screens.device.DeviceSegment
import com.oro.app.ui.screens.device.DeviceStepVisuals
import com.oro.app.ui.screens.device.DeviceTranscript
import com.oro.app.ui.screens.device.nearestLimit
import org.junit.Test

class IslandModelTest {

    private fun step(id: Long, kind: StepKind, title: String, tool: String? = null, status: StepStatus = StepStatus.DONE) =
        Step(id = id, kind = kind, tool = tool, title = title, status = status)

    private val running = DeviceAgent.State(running = true, startedAt = 1L, status = "Looking at the screen")

    @Test
    fun `attention states win over plain work`() {
        assertThat(IslandModel.phase(running)).isEqualTo(IslandPhase.Working)
        assertThat(IslandModel.phase(running.copy(paused = true))).isEqualTo(IslandPhase.Paused)
        assertThat(IslandModel.phase(running.copy(paused = true, approval = "tap “Send”"))).isEqualTo(IslandPhase.Approval)
        assertThat(IslandModel.phase(running.copy(question = DeviceAgent.Question("Which account?", emptyList()))))
            .isEqualTo(IslandPhase.Question)
    }

    @Test
    fun `finished runs read as done, failed or stopped`() {
        val ended = DeviceAgent.State(running = false, startedAt = 1L, endedAt = 2L)
        // Runs without an end reason fall back to success and error.
        assertThat(IslandModel.phase(ended.copy(success = true))).isEqualTo(IslandPhase.Done)
        assertThat(IslandModel.phase(ended.copy(success = false, error = "Model error"))).isEqualTo(IslandPhase.Failed)
        assertThat(IslandModel.headline(ended.copy(success = false, error = "boom"))).isEqualTo("Didn't finish")
        assertThat(IslandModel.phase(DeviceAgent.State())).isEqualTo(IslandPhase.Idle)
    }

    @Test
    fun `the end reason decides how a run reads, not its error text`() {
        val ended = DeviceAgent.State(running = false, startedAt = 1L, endedAt = 2L)
        fun ending(reason: DeviceAgent.EndReason) = ended.copy(endReason = reason, success = false, error = "Stopped after 30 actions.")

        val done = ended.copy(endReason = DeviceAgent.EndReason.DONE, success = true)
        assertThat(IslandModel.phase(done)).isEqualTo(IslandPhase.Done)
        assertThat(IslandModel.headline(done)).isEqualTo("Done")

        val unconfirmed = ended.copy(endReason = DeviceAgent.EndReason.UNCONFIRMED)
        assertThat(IslandModel.phase(unconfirmed)).isEqualTo(IslandPhase.Done)
        assertThat(IslandModel.headline(unconfirmed)).isEqualTo("Finished")

        // "Stopped after N actions" is the limit, not the user's Stop.
        assertThat(IslandModel.headline(ending(DeviceAgent.EndReason.ACTION_LIMIT))).isEqualTo("Limit reached")
        assertThat(IslandModel.stoppedByUser(ending(DeviceAgent.EndReason.ACTION_LIMIT))).isFalse()
        assertThat(IslandModel.endedByUser(ending(DeviceAgent.EndReason.ACTION_LIMIT))).isTrue()

        assertThat(IslandModel.headline(ending(DeviceAgent.EndReason.STOPPED_BY_USER))).isEqualTo("Stopped")
        assertThat(IslandModel.stoppedByUser(ending(DeviceAgent.EndReason.STOPPED_BY_USER))).isTrue()
        assertThat(IslandModel.headline(ending(DeviceAgent.EndReason.SERVICE_LOST))).isEqualTo("Agent turned off")
        assertThat(IslandModel.headline(ending(DeviceAgent.EndReason.ERROR))).isEqualTo("Didn't finish")
        assertThat(IslandModel.endedByUser(ending(DeviceAgent.EndReason.ERROR))).isFalse()
        // A failure reason wins over a stale success flag.
        assertThat(IslandModel.phase(ending(DeviceAgent.EndReason.FAILED).copy(success = true, error = null)))
            .isEqualTo(IslandPhase.Failed)
    }

    @Test
    fun `the pending question is the card, not also a row`() {
        val asking = running.copy(
            question = DeviceAgent.Question("Which account?", listOf("A", "B")),
            steps = listOf(
                step(1, StepKind.ACTION, "Tap “Share”", "tap"),
                step(2, StepKind.QUESTION, "Which account?", "ask_user", status = StepStatus.WAITING),
            )
        )
        assertThat(IslandModel.listedSteps(asking).map { it.id }).containsExactly(1L)
        // Answered, it is history and listed with the answer.
        val answered = asking.copy(question = null, steps = asking.steps.map { it.copy(status = StepStatus.DONE) })
        assertThat(IslandModel.listedSteps(answered).map { it.id }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `only a running action hides the status line`() {
        assertThat(IslandModel.actionRunning(running)).isFalse()
        val acting = running.copy(steps = listOf(step(1, StepKind.ACTION, "Tap “Go”", "tap", status = StepStatus.RUNNING)))
        assertThat(IslandModel.actionRunning(acting)).isTrue()
        val waiting = running.copy(steps = listOf(step(1, StepKind.QUESTION, "Which?", "ask_user", status = StepStatus.WAITING)))
        assertThat(IslandModel.actionRunning(waiting)).isFalse()
    }

    @Test
    fun `media steps pick their glyph from the action`() {
        assertThat(DeviceStepVisuals.mediaAction("Play")).isEqualTo(DeviceStepVisuals.MediaAction.PLAY)
        assertThat(DeviceStepVisuals.mediaAction("Pause")).isEqualTo(DeviceStepVisuals.MediaAction.PAUSE)
        assertThat(DeviceStepVisuals.mediaAction("Next")).isEqualTo(DeviceStepVisuals.MediaAction.NEXT)
        assertThat(DeviceStepVisuals.mediaAction("Previous")).isEqualTo(DeviceStepVisuals.MediaAction.PREVIOUS)
        assertThat(DeviceStepVisuals.mediaAction("Forward 10 s")).isEqualTo(DeviceStepVisuals.MediaAction.FORWARD)
        assertThat(DeviceStepVisuals.mediaAction("Rewind 10 s")).isEqualTo(DeviceStepVisuals.MediaAction.REWIND)
        assertThat(DeviceStepVisuals.mediaAction("Stop")).isEqualTo(DeviceStepVisuals.MediaAction.STOP)
    }

    @Test
    fun `summaries keep bold and lines but drop other markdown`() {
        val text = DeviceTranscript.summaryText("Playing **Lofi Girl** on `YouTube`.\n- one ad skipped\n## done")
        assertThat(text.text).isEqualTo("Playing Lofi Girl on YouTube.\n• one ad skipped\ndone")
        val bold = text.spanStyles.single()
        assertThat(text.text.substring(bold.start, bold.end)).isEqualTo("Lofi Girl")
    }

    @Test
    fun `the headline is the running action, else the model's status`() {
        assertThat(IslandModel.headline(running)).isEqualTo("Looking at the screen")
        val acting = running.copy(
            steps = listOf(
                step(1, StepKind.THOUGHT, "I see the home screen."),
                step(2, StepKind.ACTION, "Tap “YouTube”", tool = "tap", status = StepStatus.RUNNING),
            )
        )
        assertThat(IslandModel.headline(acting)).isEqualTo("Tap “YouTube”")
        // A finished action is history, not what is happening now.
        val between = acting.copy(steps = acting.steps.map { it.copy(status = StepStatus.DONE) })
        assertThat(IslandModel.headline(between)).isEqualTo("Looking at the screen")
    }

    @Test
    fun `recent actions skip narration and keep order`() {
        val s = running.copy(
            steps = listOf(
                step(1, StepKind.ACTION, "Open YouTube", "open_app"),
                step(2, StepKind.THOUGHT, "Now search."),
                step(3, StepKind.ACTION, "Tap “Search”", "tap"),
                step(4, StepKind.ACTION, "Type “lofi”", "type_text"),
                step(5, StepKind.THOUGHT, "Results are up."),
                step(6, StepKind.ACTION, "Tap “Lofi Girl”", "tap"),
            )
        )
        assertThat(IslandModel.recentActions(s).map { it.id }).containsExactly(3L, 4L, 6L).inOrder()
        assertThat(IslandModel.latestThought(s)).isEqualTo("Results are up.")
    }

    @Test
    fun `plain strips markdown markers but keeps words`() {
        assertThat(IslandModel.plain("**Found** it: `com.app_id`\n## next")).isEqualTo("Found it: com.app_id next")
    }

    @Test
    fun `elapsed and counts read naturally`() {
        assertThat(IslandModel.elapsed(45_000)).isEqualTo("45s")
        assertThat(IslandModel.elapsed(185_000)).isEqualTo("3m 05s")
        assertThat(IslandModel.elapsed(3_720_000)).isEqualTo("1h 02m")
        assertThat(IslandModel.elapsed(-5)).isEqualTo("0s")
        assertThat(IslandModel.actions(1)).isEqualTo("1 action")
        assertThat(IslandModel.actions(12)).isEqualTo("12 actions")
    }

    @Test
    fun `approval text becomes a question`() {
        assertThat(IslandText.approvalQuestion("tap “Send” in WhatsApp")).isEqualTo("Tap “Send” in WhatsApp?")
        assertThat(IslandText.approvalQuestion("open tel:123?")).isEqualTo("Open tel:123?")
        assertThat(IslandText.approvalQuestion("  ")).isEqualTo("Allow this action?")
    }

    @Test
    fun `transcript groups actions between narrations`() {
        val steps = listOf(
            step(1, StepKind.ACTION, "Open YouTube", "open_app"),
            step(2, StepKind.SYSTEM, "Wait 2s for loading", "wait"),
            step(3, StepKind.THOUGHT, "Loaded."),
            step(4, StepKind.ACTION, "Tap “Search”", "tap"),
            step(5, StepKind.THOUGHT, "Typing next."),
        )
        val segments = DeviceTranscript.segments(steps)
        assertThat(segments.map { it.key }).containsExactly("a1", "t3", "a4", "t5").inOrder()
        assertThat((segments[0] as DeviceSegment.Actions).steps.map { it.id }).containsExactly(1L, 2L).inOrder()
        assertThat(DeviceTranscript.segments(emptyList())).isEmpty()
    }

    @Test
    fun `titles split into verb and bold target`() {
        assertThat(DeviceTranscript.splitTitle(step(1, StepKind.ACTION, "Tap “Search”", "tap")))
            .isEqualTo("Tap" to "“Search”")
        assertThat(DeviceTranscript.splitTitle(step(1, StepKind.ACTION, "Press and hold “Photo”", "long_press")))
            .isEqualTo("Press and hold" to "“Photo”")
        assertThat(DeviceTranscript.splitTitle(step(1, StepKind.ACTION, "Open YouTube", "open_app")))
            .isEqualTo("Open" to "YouTube")
        assertThat(DeviceTranscript.splitTitle(step(1, StepKind.NOTE, "Note: Price is $12", "take_note")))
            .isEqualTo("Noted" to "Price is $12")
        assertThat(DeviceTranscript.splitTitle(step(1, StepKind.SYSTEM, "Read the screen", "read_screen")))
            .isEqualTo("Read the screen" to null)
    }

    @Test
    fun `stored limits map onto the picker`() {
        assertThat(nearestLimit(0)).isEqualTo(0)
        assertThat(nearestLimit(-1)).isEqualTo(0)
        assertThat(nearestLimit(60)).isEqualTo(60)
        assertThat(nearestLimit(75)).isEqualTo(60)
        assertThat(nearestLimit(150)).isEqualTo(120)
        assertThat(nearestLimit(15)).isEqualTo(30)
    }
}
