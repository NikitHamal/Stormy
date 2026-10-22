package com.oro.app.device.overlay

import com.oro.app.device.DeviceAgent
import com.oro.app.device.DeviceAgent.EndReason
import com.oro.app.device.DeviceAgent.StepKind
import com.oro.app.device.DeviceAgent.StepStatus
import kotlinx.coroutines.flow.MutableStateFlow

/** What the floating island is about right now, derived from the run. */
internal enum class IslandPhase { Working, Paused, Question, Approval, Done, Failed, Idle }

/**
 * Pure reads of [DeviceAgent.State] shared by the floating island and the
 * device screen, so both always describe a run in the same words.
 */
internal object IslandModel {

    fun phase(s: DeviceAgent.State): IslandPhase = when {
        s.running && s.approval != null -> IslandPhase.Approval
        s.running && s.question != null -> IslandPhase.Question
        s.running && s.paused -> IslandPhase.Paused
        s.running -> IslandPhase.Working
        s.finished && endedWell(s) -> IslandPhase.Done
        s.finished -> IslandPhase.Failed
        else -> IslandPhase.Idle
    }

    /**
     * The run reached its goal, or at least ended on the model's own word.
     * Runs from before [DeviceAgent.State.endReason] existed fall back to
     * success without an error.
     */
    private fun endedWell(s: DeviceAgent.State): Boolean = when (s.endReason) {
        EndReason.DONE, EndReason.UNCONFIRMED -> true
        null -> s.success == true && s.error == null
        else -> false
    }

    /** Ended without finish_task: the summary is the model's last word, not a confirmed result. */
    fun unconfirmed(s: DeviceAgent.State): Boolean = s.endReason == EndReason.UNCONFIRMED

    /** The action being performed or waited on right now, if any. */
    fun currentStep(s: DeviceAgent.State): DeviceAgent.Step? =
        s.steps.lastOrNull()?.takeIf {
            it.kind != StepKind.THOUGHT && (it.status == StepStatus.RUNNING || it.status == StepStatus.WAITING)
        }

    /** One line saying what is happening: the pill's label and the screen's working row. */
    fun headline(s: DeviceAgent.State, phase: IslandPhase = this.phase(s)): String = when (phase) {
        IslandPhase.Working -> currentStep(s)?.title ?: s.status.ifBlank { "Working" }
        IslandPhase.Paused -> "Paused"
        IslandPhase.Question -> "Question for you"
        IslandPhase.Approval -> "Needs your OK"
        IslandPhase.Done -> if (unconfirmed(s)) "Finished" else "Done"
        IslandPhase.Failed -> when (s.endReason) {
            EndReason.STOPPED_BY_USER -> "Stopped"
            EndReason.ACTION_LIMIT -> "Limit reached"
            EndReason.SERVICE_LOST -> "Agent turned off"
            else -> "Didn't finish"
        }
        IslandPhase.Idle -> "Orobi"
    }

    /** True when the run ended because the user pressed Stop, which is not a failure. */
    fun stoppedByUser(s: DeviceAgent.State): Boolean = s.endReason == EndReason.STOPPED_BY_USER

    /** Ends the user chose or set up (Stop, their own action limit): a notice, never an error. */
    fun endedByUser(s: DeviceAgent.State): Boolean =
        s.endReason == EndReason.STOPPED_BY_USER || s.endReason == EndReason.ACTION_LIMIT

    /** An action is in flight right now (not a thought, a question or a wait for the user). */
    fun actionRunning(s: DeviceAgent.State): Boolean = currentStep(s)?.status == StepStatus.RUNNING

    /**
     * The steps worth listing: the question the user is being asked right now
     * is left out, because its card is already on screen; once answered it is
     * listed with the answer.
     */
    fun listedSteps(s: DeviceAgent.State): List<DeviceAgent.Step> =
        if (s.question == null) s.steps
        else s.steps.filterNot { it.kind == StepKind.QUESTION && it.status == StepStatus.WAITING }

    /** Orobi's latest narration, without Markdown markers. */
    fun latestThought(s: DeviceAgent.State): String? =
        s.steps.lastOrNull { it.kind == StepKind.THOUGHT }?.title?.let(::plain)?.takeIf { it.isNotBlank() }

    /** The newest [n] actions (anything but narration), oldest first. */
    fun recentActions(s: DeviceAgent.State, n: Int = 3): List<DeviceAgent.Step> =
        s.steps.asReversed().asSequence().filter { it.kind != StepKind.THOUGHT }.take(n).toList().asReversed()

    /** Markdown emphasis, code ticks and heading/quote markers removed; whitespace collapsed. */
    fun plain(markdown: String): String = markdown
        .replace("**", "").replace("__", "").replace("`", "")
        .lineSequence().joinToString(" ") { it.trim().trimStart('#', '>').trim() }
        .replace(Regex("\\s+"), " ")
        .trim()

    /** "45s", "3m 05s", "1h 02m". */
    fun elapsed(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m ${(s % 60).toString().padStart(2, '0')}s"
            else -> "${s / 3600}h ${((s % 3600) / 60).toString().padStart(2, '0')}m"
        }
    }

    /** "12 actions", "1 action". */
    fun actions(count: Int): String = if (count == 1) "1 action" else "$count actions"
}

/**
 * UI facts the island and the device screen share.
 *
 * [screenVisible] is true while Oro's device-agent screen is in front: that
 * screen already shows every step, question and approval with full controls,
 * so the island steps aside instead of duplicating them on top of it.
 */
object DeviceAgentUi {
    val screenVisible = MutableStateFlow(false)
}
