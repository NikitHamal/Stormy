package com.oro.app.ui.screens.browser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import com.oro.app.browser.BrowserAgent
import com.oro.app.browser.BrowserAgent.StepKind

/** One block of the browser transcript: Orobi's narration, or the actions between two narrations. */
sealed interface BrowserSegment {
    /** Stable across appends, for lazy-list keys. */
    val key: String

    data class Thought(val step: BrowserAgent.Step) : BrowserSegment {
        override val key: String get() = "t${step.id}"
    }

    data class Actions(val steps: List<BrowserAgent.Step>) : BrowserSegment {
        override val key: String get() = "a${steps.first().id}"
    }
}

/** Pure shaping of a run's steps for the browser screen. */
object BrowserTranscript {

    /**
     * Narration and actions, in order, with consecutive actions grouped: a
     * run reads like a chat turn -- what Orobi thought, then what it did --
     * instead of one long column of identical rows.
     */
    fun segments(steps: List<BrowserAgent.Step>): List<BrowserSegment> {
        val out = ArrayList<BrowserSegment>()
        var run = ArrayList<BrowserAgent.Step>()
        for (step in steps) {
            if (step.kind == StepKind.THOUGHT) {
                if (run.isNotEmpty()) {
                    out += BrowserSegment.Actions(run)
                    run = ArrayList()
                }
                out += BrowserSegment.Thought(step)
            } else {
                run += step
            }
        }
        if (run.isNotEmpty()) out += BrowserSegment.Actions(run)
        return out
    }

    /** The step currently on the live question card is not also a transcript row. */
    fun listedSteps(run: BrowserAgent.State): List<BrowserAgent.Step> = run.steps.filterNot { step ->
        step.status == BrowserAgent.StepStatus.WAITING &&
            (run.question != null || run.approval != null || run.secretRequest != null) &&
            step.id == run.steps.lastOrNull { it.status == BrowserAgent.StepStatus.WAITING }?.id
    }

    /**
     * The verb and the thing it acted on, so a row can set the target in bold
     * like the chat's tool rows ("Click **“Search”**").
     */
    fun splitTitle(step: BrowserAgent.Step): Pair<String, String?> {
        val title = step.title.trim()
        val quote = title.indexOf('“')
        if (quote > 0) return title.substring(0, quote).trim() to title.substring(quote).trim()
        if (step.kind == StepKind.NOTE && title.startsWith("Note:")) return "Noted" to title.removePrefix("Note:").trim()
        if (step.tool in TargetedTools && title.contains(' ')) {
            return title.substringBefore(' ') to title.substringAfter(' ').trim().ifEmpty { null }
        }
        return title to null
    }

    private val TargetedTools = setOf("open_url", "read_download", "scroll_to")

    /** The icon for a step's tool. */
    fun iconFor(step: BrowserAgent.Step): ImageVector = when (step.kind) {
        StepKind.QUESTION -> Icons.AutoMirrored.Outlined.HelpOutline
        StepKind.APPROVAL -> Icons.Outlined.Key
        StepKind.SECRET -> Icons.Outlined.Key
        StepKind.NOTE -> Icons.AutoMirrored.Outlined.StickyNote2
        else -> when (step.tool) {
            "open_url" -> Icons.Outlined.OpenInNew
            "back" -> Icons.Outlined.ArrowBack
            "forward" -> Icons.Outlined.ArrowForward
            "reload" -> Icons.Outlined.Refresh
            "click", "click_text" -> Icons.Outlined.PlayArrow
            "type_text" -> Icons.Outlined.TextFields
            "fill_form" -> Icons.Outlined.Tune
            "select", "check" -> Icons.Outlined.RadioButtonChecked
            "scroll", "scroll_to" -> Icons.Outlined.SwipeVertical
            "hover" -> Icons.Outlined.Mouse
            "press_key" -> Icons.AutoMirrored.Outlined.KeyboardReturn
            "web_search" -> Icons.Outlined.Search
            "extract" -> Icons.Outlined.TableChart
            "read_page", "find_text" -> Icons.Outlined.Language
            "screenshot" -> Icons.Outlined.Visibility
            "evaluate" -> Icons.Outlined.Link
            "new_tab", "switch_tab", "close_tab" -> Icons.Outlined.OpenInNew
            "upload_file" -> Icons.Outlined.Upload
            "list_downloads", "read_download" -> Icons.Outlined.Download
            "use_credential", "list_credentials" -> Icons.Outlined.Key
            "wait", "wait_for" -> Icons.Outlined.HourglassTop
            "finish_task" -> Icons.Outlined.CheckCircle
            else -> Icons.Outlined.Language
        }
    }

    /** The step's outcome as one line, failures marked. */
    fun outcomeOf(step: BrowserAgent.Step): String = step.detail
}
