package com.oro.app.data.ai.agent

import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.tools.ToolResult

/**
 * Notices a run that is going nowhere, without capping runs that are simply
 * long.
 *
 * Runs used to stop after 40 model calls, which paused healthy work (a full
 * site easily takes more) and did nothing for the real failure mode: a model
 * retrying the same broken edit. Two signals catch that instead:
 *  - a streak of tool rounds in which every call failed, and
 *  - the same call coming back with the same result again and again within
 *    the last [AgentLoopConfig.REPEAT_WINDOW] calls (re-reading an unchanged
 *    file, re-running a search that found nothing).
 * The first threshold earns the model a corrective note on its results; only
 * a loop that survives the notes ends the run, with a message that says why.
 */
internal class StuckDetector(private val config: AgentLoopConfig) {

    data class Verdict(
        /** Appended to the round's results for the model, or null. */
        val note: String? = null,
        /** Non-null: end the run with this message. */
        val stop: String? = null
    )

    private var failStreak = 0
    private val window = ArrayDeque<String>()

    /** Record one round of [calls] and their raw [results]. */
    fun record(calls: List<ToolCallResponse>, results: List<ToolResult>): Verdict {
        if (calls.isEmpty()) return Verdict()
        val allFailed = results.isNotEmpty() && results.all { !it.success }
        failStreak = if (allFailed) failStreak + 1 else 0

        var repeats = 0
        calls.forEachIndexed { i, call ->
            val r = results.getOrNull(i)
            val outcome = if (r == null) "" else "${r.success}\u0000${(r.error ?: "") + r.output}"
            val signature = call.function.name + "\u0000" + call.function.arguments + "\u0000" + outcome.hashCode()
            window.addLast(signature)
            while (window.size > AgentLoopConfig.REPEAT_WINDOW) window.removeFirst()
            repeats = maxOf(repeats, window.count { it == signature })
        }

        val lastError = results.lastOrNull { !it.success }
            ?.let { (it.error ?: it.output).lineSequence().firstOrNull { line -> line.isNotBlank() } }
            ?.trim()?.take(160)

        if (failStreak >= config.stuckStopAfter) {
            return Verdict(
                stop = "Orobi stopped because it was stuck: its last $failStreak attempts all failed" +
                    (lastError?.let { " (last error: $it)" } ?: "") +
                    ". Rephrase the request or try a stronger model, then retry."
            )
        }
        if (repeats >= config.repeatStopAfter) {
            return Verdict(
                stop = "Orobi stopped because it kept repeating the same step with the same result. " +
                    "Rephrase the request or try a stronger model, then retry."
            )
        }
        val notes = buildList {
            if (failStreak >= config.stuckNudgeAfter && failStreak % config.stuckNudgeAfter == 0) {
                add(
                    "[Oro: your last $failStreak tool rounds all failed. Don't repeat what didn't work: re-read " +
                        "the exact current text of the files involved, read the errors above carefully, and try a " +
                        "different, smaller step. If something is truly blocked, explain it to the user.]"
                )
            }
            if (repeats >= 3) {
                add(
                    "[Oro: you have made this same call with the same result $repeats times recently. It will " +
                        "not change; take a different approach.]"
                )
            }
        }
        return Verdict(note = notes.takeIf { it.isNotEmpty() }?.joinToString("\n"))
    }
}
