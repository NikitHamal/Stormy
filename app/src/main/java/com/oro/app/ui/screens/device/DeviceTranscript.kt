package com.oro.app.ui.screens.device

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Pinch
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.oro.app.device.DeviceAgent
import com.oro.app.device.DeviceAgent.StepKind

/** One block of the device transcript: Orobi's narration, or the actions between two narrations. */
sealed interface DeviceSegment {
    /** Stable across appends, for lazy-list keys. */
    val key: String

    data class Thought(val step: DeviceAgent.Step) : DeviceSegment {
        override val key: String get() = "t${step.id}"
    }

    data class Actions(val steps: List<DeviceAgent.Step>) : DeviceSegment {
        override val key: String get() = "a${steps.first().id}"
    }
}

/** Pure shaping of a run's steps for the device screen. */
object DeviceTranscript {

    /**
     * Narration and actions, in order, with consecutive actions grouped: a
     * run reads like a chat turn -- what Orobi thought, then what it did --
     * instead of one long column of identical cards.
     */
    fun segments(steps: List<DeviceAgent.Step>): List<DeviceSegment> {
        val out = ArrayList<DeviceSegment>()
        var run = ArrayList<DeviceAgent.Step>()
        for (step in steps) {
            if (step.kind == StepKind.THOUGHT) {
                if (run.isNotEmpty()) {
                    out += DeviceSegment.Actions(run)
                    run = ArrayList()
                }
                out += DeviceSegment.Thought(step)
            } else {
                run += step
            }
        }
        if (run.isNotEmpty()) out += DeviceSegment.Actions(run)
        return out
    }

    /**
     * The verb and the thing it acted on, so a row can set the target in
     * bold like the chat's tool rows ("Tap **“Search”**"). Null subject when
     * the title has no target worth emphasising.
     */
    fun splitTitle(step: DeviceAgent.Step): Pair<String, String?> {
        val title = step.title.trim()
        val quote = title.indexOf('“')
        if (quote > 0) return title.substring(0, quote).trim() to title.substring(quote).trim()
        if (step.kind == StepKind.NOTE && title.startsWith("Note:")) return "Noted" to title.removePrefix("Note:").trim()
        if (step.tool in TargetedTools && title.contains(' ')) {
            return title.substringBefore(' ') to title.substringAfter(' ').trim().ifEmpty { null }
        }
        return title to null
    }

    private val TargetedTools = setOf("open_app", "open_url")

    /**
     * A summary as the narration lines above it read: the same text style,
     * with **bold** kept as bold and the other Markdown markers dropped.
     * Line breaks survive, so a short list stays a list.
     */
    fun summaryText(markdown: String): AnnotatedString = buildAnnotatedString {
        val lines = markdown.trim().lines()
            .map { it.trim().trimStart('#', '>').trim().replace("`", "").replace("__", "**") }
            .filter { it.isNotEmpty() }
        lines.forEachIndexed { index, line ->
            if (index > 0) append('\n')
            val text = if (line.startsWith("- ") || line.startsWith("* ")) "• " + line.drop(2) else line
            var bold = false
            text.split("**").forEachIndexed { i, part ->
                if (i > 0) bold = !bold
                if (bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
            }
        }
    }
}

/** Glyphs for steps, shared by the device screen and the floating island. */
object DeviceStepVisuals {

    fun icon(step: DeviceAgent.Step): ImageVector = when (step.kind) {
        StepKind.THOUGHT -> Icons.Outlined.Psychology
        StepKind.QUESTION -> Icons.AutoMirrored.Outlined.HelpOutline
        StepKind.APPROVAL -> Icons.Outlined.PrivacyTip
        StepKind.NOTE -> Icons.AutoMirrored.Outlined.StickyNote2
        else -> if (step.tool == "media" || step.tool == "media_control") mediaIcon(step.title) else icon(step.tool)
    }

    /** Media steps are titled by what they did ("Pause", "Forward 10 s"); the glyph follows. */
    fun mediaIcon(title: String): ImageVector = when (mediaAction(title)) {
        MediaAction.PAUSE -> Icons.Outlined.Pause
        MediaAction.PREVIOUS -> Icons.Outlined.SkipPrevious
        MediaAction.NEXT -> Icons.Outlined.SkipNext
        MediaAction.FORWARD -> Icons.Outlined.Forward10
        MediaAction.REWIND -> Icons.Outlined.Replay10
        MediaAction.STOP -> Icons.Outlined.Stop
        MediaAction.PLAY -> Icons.Outlined.PlayArrow
    }

    enum class MediaAction { PLAY, PAUSE, NEXT, PREVIOUS, FORWARD, REWIND, STOP }

    /** Which media action a step title names. Pure, for tests. */
    fun mediaAction(title: String): MediaAction {
        val t = title.lowercase()
        return when {
            "pause" in t -> MediaAction.PAUSE
            "previous" in t || "prev" in t -> MediaAction.PREVIOUS
            "next" in t || "skip" in t -> MediaAction.NEXT
            "forward" in t -> MediaAction.FORWARD
            "rewind" in t || "back" in t -> MediaAction.REWIND
            "stop" in t -> MediaAction.STOP
            else -> MediaAction.PLAY
        }
    }

    /** By tool name; names the engine may add later fall back to a bolt. */
    fun icon(tool: String?): ImageVector = when (tool) {
        "tap", "tap_text", "double_tap" -> Icons.Outlined.TouchApp
        "long_press" -> Icons.Outlined.PanTool
        "type_text" -> Icons.Outlined.Keyboard
        "scroll", "scroll_to" -> Icons.Outlined.SwipeVertical
        "swipe" -> Icons.Outlined.Swipe
        "drag" -> Icons.Outlined.Gesture
        "pinch" -> Icons.Outlined.Pinch
        "open_app", "list_apps" -> Icons.Outlined.Apps
        "open_url", "web_search" -> Icons.Outlined.Language
        "press_key" -> Icons.AutoMirrored.Outlined.KeyboardReturn
        "find_text", "read_screen", "read_page", "wait_for" -> Icons.Outlined.Search
        "wait" -> Icons.Outlined.HourglassTop
        "media", "media_control" -> Icons.Outlined.PlayArrow
        "screenshot", "look" -> Icons.Outlined.Screenshot
        "skip_ad" -> Icons.Outlined.SkipNext
        "take_note" -> Icons.AutoMirrored.Outlined.StickyNote2
        "ask_user" -> Icons.AutoMirrored.Outlined.HelpOutline
        "view" -> Icons.Outlined.Visibility
        else -> Icons.Outlined.Bolt
    }
}
