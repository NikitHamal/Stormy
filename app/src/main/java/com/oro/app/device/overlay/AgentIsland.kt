package com.oro.app.device.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.oro.app.device.DeviceAgent
import com.oro.app.device.DeviceAgent.StepStatus
import com.oro.app.ui.brand.OroBrand
import com.oro.app.ui.brand.OroSparkle
import com.oro.app.ui.brand.OrobiLoader
import com.oro.app.ui.brand.ShimmerText
import com.oro.app.ui.screens.device.DeviceStepVisuals
import com.oro.app.ui.theme.Elevation
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space
import kotlinx.coroutines.delay

/** What the island can ask its window and the run to do. */
internal class IslandActions(
    val onPauseToggle: () -> Unit,
    val onStop: () -> Unit,
    val onApprove: (Boolean) -> Unit,
    val onAnswer: (String) -> Unit,
    val onOpenOro: () -> Unit,
    /**
     * The user tapped the reply field (true) or is done with it (false): the
     * window takes keyboard focus only in between, so the app underneath
     * keeps its keyboard and Back the rest of the time.
     */
    val onTyping: (Boolean) -> Unit,
)

/** One shape for the pill and the card, so expanding reads as the same island growing. */
private val IslandShape = OroCorners.xLarge

/** Transparent margin inside the window so the island's shadow is not clipped by it. */
internal val IslandShadowRoom = 6.dp

/**
 * The pill's width while a run is live. Fixed on purpose: the window wraps
 * the island, so a pill that followed its label would resize the window with
 * every step -- a blocking window-manager call per frame on a budget phone.
 */
internal val PillWidth = 264.dp

/** The phase glyph: Orobi's loop at a size that sits on the pill's text line. */
private val GlyphSize = 28.dp

/** Stop's visible disc inside its 48 dp touch target. */
private val StopDisc = 30.dp

/** Height of the card's buttons: compact, but never below a comfortable tap. */
private val CardButtonHeight = 40.dp

/**
 * Orobi's floating island: what it is doing on the phone, one tap from
 * stopping it.
 *
 * Collapsed, it is a pill -- the infinity loader, one line saying what is
 * happening, and Stop -- small enough to live over any app. Tapped, it opens
 * into a card: the task, Orobi's narration, the last few actions, time and
 * the controls. A permission request or a question opens the card by itself
 * and closes it again once answered; the user can reply right there without
 * leaving the app Orobi is working in.
 *
 * It renders [DeviceAgent.state] directly, so it never shows a stale copy of
 * the run, and it steps aside while Oro's own device screen is in front
 * (that screen shows everything, with full controls).
 *
 * The island never animates its own size: its window wraps it, and resizing
 * a window every frame is exactly the work a low-end phone drops frames on.
 * Size changes are instant and the content's fade and scale carry the motion.
 */
@Composable
internal fun AgentIsland(
    state: DeviceAgent.State,
    visible: Boolean,
    screenVisible: Boolean,
    lite: Boolean,
    cardWidth: Dp,
    actions: IslandActions,
    /** The tallest the card may grow; its middle scrolls beyond that, its buttons stay put. */
    maxCardHeight: Dp = 560.dp,
    /** The island sits on the bottom half: the card opens upwards. */
    anchoredBottom: Boolean = false,
    /** Bumped by the window (Back, a touch outside) to drop the reply field's focus. */
    focusResets: Int = 0,
) {
    val phase = IslandModel.phase(state)
    var expanded by remember { mutableStateOf(false) }
    // Attention states open the card themselves; this remembers that it was
    // the run (not the user) that opened it, so it closes again afterwards.
    var autoOpened by remember { mutableStateOf<String?>(null) }
    val attention = when (phase) {
        IslandPhase.Approval -> "a:" + state.approval
        IslandPhase.Question -> "q:" + state.question?.text
        else -> null
    }
    LaunchedEffect(attention, screenVisible) {
        if (attention != null && !screenVisible) {
            if (!expanded) autoOpened = attention
            expanded = true
        } else if (attention == null && autoOpened != null) {
            expanded = false
            autoOpened = null
        }
    }
    // A finished run shows its outcome on the pill; the card was for steering.
    LaunchedEffect(phase == IslandPhase.Done || phase == IslandPhase.Failed) {
        if (phase == IslandPhase.Done || phase == IslandPhase.Failed) {
            if (autoOpened != null) expanded = false
            autoOpened = null
        }
    }

    val origin = if (anchoredBottom) TransformOrigin(0.5f, 1f) else TransformOrigin(0.5f, 0f)
    // Starts hidden, so the very first appearance animates in too.
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = visible && !screenVisible && phase != IslandPhase.Idle
    AnimatedVisibility(
        visibleState = shown,
        enter = if (lite) fadeIn(tween(120)) else fadeIn(tween(160)) + scaleIn(initialScale = 0.82f, transformOrigin = origin),
        exit = if (lite) fadeOut(tween(100)) else fadeOut(tween(140)) + scaleOut(targetScale = 0.82f, transformOrigin = origin),
    ) {
        Surface(
            modifier = Modifier.padding(IslandShadowRoom),
            shape = IslandShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = if (lite) Elevation.floating else Elevation.lifted,
        ) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    // The window takes the new size in one step; the content's
                    // fade and scale (draw-only) carry the motion.
                    val size = SizeTransform(clip = true) { _, _ -> snap<IntSize>() }
                    if (lite) {
                        (fadeIn(tween(90)) togetherWith fadeOut(tween(60))).using(size)
                    } else {
                        (fadeIn(tween(180, delayMillis = 40)) + scaleIn(initialScale = 0.94f, transformOrigin = origin, animationSpec = tween(220)))
                            .togetherWith(fadeOut(tween(80)))
                            .using(size)
                    }
                },
                label = "island",
            ) { open ->
                if (open) {
                    IslandCard(
                        state = state,
                        phase = phase,
                        lite = lite,
                        width = cardWidth,
                        maxHeight = maxCardHeight,
                        actions = actions,
                        focusResets = focusResets,
                        onCollapse = { expanded = false; autoOpened = null },
                    )
                } else {
                    IslandPill(state = state, phase = phase, lite = lite, onClick = { expanded = true }, onStop = actions.onStop)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- the pill

@Composable
private fun IslandPill(state: DeviceAgent.State, phase: IslandPhase, lite: Boolean, onClick: () -> Unit, onStop: () -> Unit) {
    val label = IslandModel.headline(state, phase)
    val live = state.running
    Row(
        modifier = Modifier
            .height(Sizes.minTouchTarget)
            .then(if (live) Modifier.width(PillWidth) else Modifier)
            .clickable(onClickLabel = "Show what Orobi is doing", onClick = onClick)
            .padding(start = Space.sm + Space.xxs, end = if (live) 0.dp else Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhaseGlyph(phase = phase, lite = lite, size = GlyphSize, confirmed = !IslandModel.unconfirmed(state), byUser = IslandModel.endedByUser(state))
        Spacer(Modifier.width(Space.sm))
        // A new step slides in under the old one, like a ticker: the pill
        // says something happened even when the words barely change. The
        // label's box is fixed, so the slide never resizes the window.
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                val swap = if (lite) {
                    fadeIn(tween(90)) togetherWith fadeOut(tween(60))
                } else {
                    (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220)))
                        .togetherWith(slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(120)))
                }
                swap.using(SizeTransform(clip = true) { _, _ -> snap<IntSize>() })
            },
            modifier = if (live) Modifier.weight(1f) else Modifier,
            label = "pillLabel",
        ) { text ->
            if (phase == IslandPhase.Working && !lite) {
                ShimmerText(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (live) {
            Spacer(Modifier.width(Space.xs))
            StopDiscButton(onStop)
        }
    }
}

/** Stop, one tap from anywhere: a small disc in a full-size touch target. */
@Composable
private fun StopDiscButton(onStop: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    IconButton(onClick = onStop, modifier = Modifier.size(Sizes.minTouchTarget)) {
        Box(
            modifier = Modifier
                .size(StopDisc)
                .clip(OroCorners.pill)
                .background(scheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = "Stop",
                tint = scheme.onErrorContainer,
                modifier = Modifier.size(Sizes.iconSmall),
            )
        }
    }
}

// ---------------------------------------------------------------- the card

@Composable
private fun IslandCard(
    state: DeviceAgent.State,
    phase: IslandPhase,
    lite: Boolean,
    width: Dp,
    maxHeight: Dp,
    actions: IslandActions,
    focusResets: Int,
    onCollapse: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .width(width)
            .heightIn(max = maxHeight)
            .padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        CardHeader(state = state, phase = phase, lite = lite, onCollapse = onCollapse)
        // The middle scrolls when the card meets the screen's edge; the
        // buttons below it never leave the screen.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scroll)
                .padding(end = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            when (phase) {
                IslandPhase.Approval -> ApprovalText(what = state.approval.orEmpty())
                IslandPhase.Question -> state.question?.let { q -> QuestionText(question = q, onAnswer = actions.onAnswer) }
                IslandPhase.Done, IslandPhase.Failed -> ResultText(state = state, phase = phase)
                else -> WorkText(state = state, lite = lite)
            }
        }
        Column(
            modifier = Modifier.padding(end = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            when (phase) {
                IslandPhase.Approval -> ApprovalButtons(onApprove = actions.onApprove)
                IslandPhase.Question -> state.question?.let { q ->
                    QuestionReply(question = q, actions = actions, focusResets = focusResets)
                }
                IslandPhase.Done, IslandPhase.Failed -> FilledTonalButton(
                    onClick = actions.onOpenOro,
                    contentPadding = CompactButtonPadding,
                    modifier = Modifier.heightIn(min = CardButtonHeight),
                ) { Text("Open Oro", maxLines = 1) }
                else -> WorkButtons(paused = state.paused, actions = actions)
            }
        }
    }
}

@Composable
private fun CardHeader(state: DeviceAgent.State, phase: IslandPhase, lite: Boolean, onCollapse: () -> Unit) {
    val running = state.running
    val now by produceState(System.currentTimeMillis(), running) {
        value = System.currentTimeMillis()
        while (running) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val end = if (running) now else state.endedAt.takeIf { it > 0 } ?: now
    val meta = listOfNotNull(
        "Paused".takeIf { phase == IslandPhase.Paused },
        state.startedAt.takeIf { it > 0 }?.let { IslandModel.elapsed(end - it) },
        state.actions.takeIf { it > 0 }?.let { IslandModel.actions(it) },
        state.currentApp.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    // While Orobi works the title is the task -- the current step is already
    // the newest row below; asking and ending states say so instead.
    val title = when (phase) {
        IslandPhase.Working, IslandPhase.Paused -> state.task.takeIf { it.isNotBlank() }?.let(IslandModel::plain)
            ?: IslandModel.headline(state, phase)
        else -> IslandModel.headline(state, phase)
    }
    // TalkBack hears a question or a permission request the moment it opens.
    val pane = when (phase) {
        IslandPhase.Approval -> "Orobi needs your OK"
        IslandPhase.Question -> "Orobi has a question"
        else -> null
    }

    Row(
        modifier = if (pane != null) Modifier.semantics { paneTitle = pane; liveRegion = LiveRegionMode.Polite } else Modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhaseGlyph(phase = phase, lite = lite, size = GlyphSize, confirmed = !IslandModel.unconfirmed(state), byUser = IslandModel.endedByUser(state))
        Spacer(Modifier.width(Space.sm + Space.xxs))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onCollapse) {
            Icon(
                imageVector = Icons.Outlined.CloseFullscreen,
                contentDescription = "Collapse",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.icon),
            )
        }
    }
}

// --------------------------------------------------------------- working

@Composable
private fun WorkText(state: DeviceAgent.State, lite: Boolean) {
    IslandModel.latestThought(state)?.let { thought ->
        Text(
            text = thought,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val recent = IslandModel.recentActions(state)
    if (recent.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            recent.forEach { step -> MiniStepRow(step = step, lite = lite) }
        }
    }
}

@Composable
private fun WorkButtons(paused: Boolean, actions: IslandActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = actions.onPauseToggle,
            modifier = Modifier.weight(1f).heightIn(min = CardButtonHeight),
            contentPadding = CompactButtonPadding,
        ) {
            Icon(
                imageVector = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                contentDescription = null,
                modifier = Modifier.size(Sizes.icon - Space.xxs),
            )
            Spacer(Modifier.width(Space.xs + Space.xxs))
            Text(if (paused) "Resume" else "Pause", maxLines = 1)
        }
        Button(
            onClick = actions.onStop,
            modifier = Modifier.weight(1f).heightIn(min = CardButtonHeight),
            contentPadding = CompactButtonPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(Sizes.icon - Space.xxs))
            Spacer(Modifier.width(Space.xs + Space.xxs))
            Text("Stop", maxLines = 1)
        }
        FilledTonalIconButton(onClick = actions.onOpenOro, modifier = Modifier.size(CardButtonHeight)) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = "Open Oro",
                modifier = Modifier.size(Sizes.icon - Space.xxs),
            )
        }
    }
}

/** One recent action: its glyph, what it was, and how it went. */
@Composable
private fun MiniStepRow(step: DeviceAgent.Step, lite: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.iconLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = DeviceStepVisuals.icon(step),
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.iconSmall),
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            text = step.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (step.status == StepStatus.RUNNING) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.sm))
        Box(Modifier.width(Sizes.icon), contentAlignment = Alignment.Center) {
            val mark = Modifier.size(Sizes.iconSmall)
            when (step.status) {
                StepStatus.RUNNING -> if (lite) {
                    Icon(Icons.Outlined.HourglassTop, "Running", tint = scheme.onSurfaceVariant, modifier = mark)
                } else {
                    OrobiLoader(modifier = Modifier.width(Sizes.icon), contentDescription = "Running")
                }
                StepStatus.WAITING -> Icon(Icons.Outlined.HourglassTop, "Waiting", tint = scheme.onSurfaceVariant, modifier = mark)
                StepStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, "Failed", tint = scheme.error, modifier = mark)
                StepStatus.BLOCKED -> Icon(Icons.Outlined.Block, "Denied", tint = scheme.error, modifier = mark)
                StepStatus.DONE -> Icon(
                    Icons.Outlined.Check,
                    contentDescription = if (step.screenChanged == false) "No change" else "Done",
                    tint = if (step.screenChanged == false) scheme.outline else scheme.onSurfaceVariant,
                    modifier = mark,
                )
            }
        }
    }
}

// -------------------------------------------------------------- approval

@Composable
private fun ApprovalText(what: String) {
    Text(
        text = IslandText.approvalQuestion(what),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ApprovalButtons(onApprove: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        OutlinedButton(
            onClick = { onApprove(false) },
            modifier = Modifier.weight(1f).heightIn(min = Sizes.minTouchTarget),
        ) { Text("Deny", maxLines = 1) }
        Button(
            onClick = { onApprove(true) },
            modifier = Modifier.weight(1f).heightIn(min = Sizes.minTouchTarget),
        ) { Text("Allow", maxLines = 1) }
    }
}

// -------------------------------------------------------------- question

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionText(question: DeviceAgent.Question, onAnswer: (String) -> Unit) {
    Text(
        text = question.text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (question.options.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            question.options.forEach { option ->
                Surface(
                    onClick = { onAnswer(option) },
                    shape = OroCorners.pill,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Space.md + Space.xxs, vertical = Space.sm),
                    )
                }
            }
        }
    }
}

/**
 * The reply field and Open Oro, pinned under the question.
 *
 * The window is not focusable until the user touches the field: an island
 * that took focus the moment a question appeared would steal the keyboard
 * and Back from the app underneath. The touch makes the window focusable,
 * and once it actually has focus the field takes it and the keyboard opens.
 * Sending, Back, a touch outside, or the question going away hands focus
 * back.
 */
@Composable
private fun QuestionReply(question: DeviceAgent.Question, actions: IslandActions, focusResets: Int) {
    var reply by remember(question) { mutableStateOf("") }
    var wantsKeyboard by remember(question) { mutableStateOf(false) }
    var hadFocus by remember(question) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused

    LaunchedEffect(wantsKeyboard) { actions.onTyping(wantsKeyboard) }
    DisposableEffect(Unit) { onDispose { actions.onTyping(false) } }
    LaunchedEffect(wantsKeyboard, windowFocused) {
        if (wantsKeyboard && windowFocused) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(focusResets) {
        if (focusResets > 0) {
            wantsKeyboard = false
            focusManager.clearFocus()
        }
    }

    fun send() {
        val text = reply.trim()
        if (text.isEmpty()) return
        wantsKeyboard = false
        focusManager.clearFocus()
        actions.onAnswer(text)
    }

    ReplyField(
        value = reply,
        onValueChange = { reply = it },
        onSend = ::send,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { f ->
                if (f.isFocused) hadFocus = true
                else if (hadFocus) {
                    hadFocus = false
                    wantsKeyboard = false
                }
            },
        // Seen before the field, whether or not it can take focus yet.
        onTouch = { if (!wantsKeyboard) wantsKeyboard = true },
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = actions.onOpenOro) { Text("Open Oro", maxLines = 1) }
    }
}

/** The composer's look, cut down: a field and a round send button. */
@Composable
private fun ReplyField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier,
    onTouch: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val touch by rememberUpdatedState(onTouch)
    Surface(
        shape = OroCorners.xLarge,
        color = scheme.surfaceContainer,
        border = BorderStroke(Sizes.hairline, scheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    touch()
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(start = Space.md + Space.xxs, end = Space.xs, top = Space.xs, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = Space.sm)) {
                if (value.isEmpty()) {
                    Text(
                        text = "Type an answer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = modifier.fillMaxWidth(),
                )
            }
            val ready = value.isNotBlank()
            Surface(
                onClick = onSend,
                enabled = ready,
                shape = OroCorners.pill,
                color = if (ready) scheme.primary else scheme.surfaceContainerHigh,
                contentColor = if (ready) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier.size(CardButtonHeight),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Send answer", modifier = Modifier.size(Sizes.icon - Space.xxs))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- result

@Composable
private fun ResultText(state: DeviceAgent.State, phase: IslandPhase) {
    val text = when (phase) {
        IslandPhase.Done -> state.summary
        else -> state.summary?.takeIf { state.success == false } ?: state.error ?: state.summary
    }
    text?.let { IslandModel.plain(it) }?.takeIf { it.isNotBlank() }?.let { body ->
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ----------------------------------------------------------------- glyphs

/**
 * The island's face: Orobi's infinity loader while it works (a still loop in
 * Lite mode), a still glyph for every other state. Swapped with a quick scale
 * so a change of state is noticed from the corner of the eye.
 */
@Composable
private fun PhaseGlyph(phase: IslandPhase, lite: Boolean, size: Dp, confirmed: Boolean = true, byUser: Boolean = false) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                if (lite) fadeIn(tween(90)) togetherWith fadeOut(tween(60))
                else (fadeIn(tween(160)) + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut(tween(100)) + scaleOut(targetScale = 0.6f))
            },
            label = "phaseGlyph",
        ) { p ->
            val scheme = MaterialTheme.colorScheme
            val icon = Modifier.size(Sizes.icon)
            when (p) {
                IslandPhase.Working -> if (lite) {
                    Icon(Icons.Outlined.AllInclusive, "Working", tint = OroBrand.Magenta, modifier = icon)
                } else {
                    OrobiLoader(modifier = Modifier.width(size - Space.xxs), contentDescription = "Working")
                }
                IslandPhase.Paused -> Icon(Icons.Outlined.Pause, "Paused", tint = scheme.onSurface, modifier = icon)
                IslandPhase.Question -> AttentionGlyph(lite) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, "Question", tint = scheme.onSurface, modifier = icon)
                }
                IslandPhase.Approval -> AttentionGlyph(lite) {
                    Icon(Icons.Outlined.PrivacyTip, "Permission needed", tint = scheme.onSurface, modifier = icon)
                }
                IslandPhase.Done -> Icon(
                    Icons.Outlined.TaskAlt,
                    contentDescription = if (confirmed) "Done" else "Finished",
                    tint = if (confirmed) OroBrand.GreenDark else scheme.onSurfaceVariant,
                    modifier = icon,
                )
                // Stop or the user's own limit is their choice, not an error.
                IslandPhase.Failed -> if (byUser) {
                    Icon(Icons.Outlined.StopCircle, "Stopped", tint = scheme.onSurfaceVariant, modifier = icon)
                } else {
                    Icon(Icons.Outlined.ErrorOutline, "Didn't finish", tint = scheme.error, modifier = icon)
                }
                IslandPhase.Idle -> OroSparkle(modifier = Modifier.size(Sizes.iconSmall - Space.xxs), color = OroBrand.Magenta)
            }
        }
    }
}

/** A slow breath that says "this is waiting on you" without flashing. */
@Composable
private fun AttentionGlyph(lite: Boolean, content: @Composable () -> Unit) {
    if (lite) {
        content()
        return
    }
    val transition = rememberInfiniteTransition(label = "attention")
    val scale = transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "attentionScale",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val CompactButtonPadding = PaddingValues(horizontal = Space.md, vertical = Space.sm)

/** Wording shared with the device screen. */
internal object IslandText {
    /** "tap “Send” in WhatsApp" -> "Tap “Send” in WhatsApp?" */
    fun approvalQuestion(what: String): String {
        val trimmed = what.trim().removeSuffix("?").removeSuffix(".")
        if (trimmed.isEmpty()) return "Allow this action?"
        return trimmed.replaceFirstChar { it.uppercaseChar() } + "?"
    }
}
