package com.oro.app.device.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.ArrowUpward
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
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
    /** The reply field is on screen: the window must take keyboard focus while it is. */
    val onTyping: (Boolean) -> Unit,
)

/** Corner radius shared by the pill and the card, so expanding reads as one shape growing. */
private val IslandCorner = 24.dp

/** Transparent margin inside the window so the island's shadow is not clipped by it. */
internal val IslandShadowRoom = 6.dp

/**
 * Orobi's floating island: what it is doing on the phone, one tap from
 * pausing or stopping it.
 *
 * Collapsed, it is a pill -- the infinity loader and one line saying what is
 * happening -- small enough to live over any app. Tapped, it grows into a
 * card: the current narration, the last few actions, elapsed time and the
 * controls. A permission request or a question opens the card by itself and
 * closes it again once answered; the user can reply right there without
 * leaving the app Orobi is working in.
 *
 * It renders [DeviceAgent.state] directly, so it never shows a stale copy of
 * the run, and it steps aside while Oro's own device screen is in front
 * (that screen shows everything, with full controls).
 */
@Composable
internal fun AgentIsland(
    state: DeviceAgent.State,
    visible: Boolean,
    screenVisible: Boolean,
    lite: Boolean,
    cardWidth: Dp,
    actions: IslandActions,
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

    val typing = expanded && phase == IslandPhase.Question && visible && !screenVisible
    LaunchedEffect(typing) { actions.onTyping(typing) }

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    AnimatedVisibility(
        visible = visible && !screenVisible && phase != IslandPhase.Idle,
        enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.82f, transformOrigin = TransformOrigin(0.5f, 0f)),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.82f, transformOrigin = TransformOrigin(0.5f, 0f)),
    ) {
        Surface(
            modifier = Modifier.padding(IslandShadowRoom),
            shape = RoundedCornerShape(IslandCorner),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = if (lite) 2.dp else 6.dp,
        ) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    val size = SizeTransform(clip = true) { _, _ -> if (lite) snap<IntSize>() else spatial }
                    if (lite) {
                        (fadeIn(tween(90)) togetherWith fadeOut(tween(60))).using(size)
                    } else {
                        (fadeIn(tween(180, delayMillis = 70)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220, delayMillis = 40)))
                            .togetherWith(fadeOut(tween(90)))
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
                        actions = actions,
                        onCollapse = { expanded = false; autoOpened = null },
                    )
                } else {
                    IslandPill(state = state, phase = phase, lite = lite, onClick = { expanded = true })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- the pill

@Composable
private fun IslandPill(state: DeviceAgent.State, phase: IslandPhase, lite: Boolean, onClick: () -> Unit) {
    val label = IslandModel.headline(state, phase)
    Row(
        modifier = Modifier
            .height(Sizes.minTouchTarget)
            .widthIn(min = Sizes.minTouchTarget, max = 264.dp)
            .clickable(onClickLabel = "Show what Orobi is doing", onClick = onClick)
            .padding(start = Space.sm + Space.xxs, end = Space.md + Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhaseGlyph(phase = phase, lite = lite, size = 28.dp)
        Spacer(Modifier.width(Space.sm))
        // A new step slides in under the old one, like a ticker: the pill
        // says something happened even when the words barely change.
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                val swap = if (lite) {
                    fadeIn(tween(90)) togetherWith fadeOut(tween(60))
                } else {
                    (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220)))
                        .togetherWith(slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(120)))
                }
                swap.using(SizeTransform(clip = false))
            },
            modifier = Modifier.weight(1f, fill = false),
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
        if ((phase == IslandPhase.Working || phase == IslandPhase.Paused) && state.actions > 0) {
            Spacer(Modifier.width(Space.sm))
            Text(
                text = state.actions.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = IslandModel.actions(state.actions) },
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
    actions: IslandActions,
    onCollapse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        CardHeader(state = state, phase = phase, lite = lite, onCollapse = onCollapse)
        Column(
            modifier = Modifier.padding(end = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            when (phase) {
                IslandPhase.Approval -> ApprovalBody(what = state.approval.orEmpty(), onApprove = actions.onApprove)
                IslandPhase.Question -> state.question?.let { q ->
                    QuestionBody(question = q, onAnswer = actions.onAnswer, onOpenOro = actions.onOpenOro)
                }
                IslandPhase.Done, IslandPhase.Failed -> ResultBody(state = state, phase = phase, onOpenOro = actions.onOpenOro)
                else -> WorkBody(state = state, lite = lite, actions = actions)
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
        state.startedAt.takeIf { it > 0 }?.let { IslandModel.elapsed(end - it) },
        state.actions.takeIf { it > 0 }?.let { IslandModel.actions(it) },
        state.currentApp.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    Row(verticalAlignment = Alignment.CenterVertically) {
        PhaseGlyph(phase = phase, lite = lite, size = 28.dp)
        Spacer(Modifier.width(Space.sm + Space.xxs))
        Column(Modifier.weight(1f)) {
            Text(
                text = IslandModel.headline(state, phase),
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
                modifier = Modifier.size(Sizes.iconSmall + Space.xxs),
            )
        }
    }
}

@Composable
private fun WorkBody(state: DeviceAgent.State, lite: Boolean, actions: IslandActions) {
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = actions.onPauseToggle,
            modifier = Modifier.weight(1f).heightIn(min = 40.dp),
            contentPadding = CompactButtonPadding,
        ) {
            Icon(
                imageVector = if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconSmall + Space.xxs),
            )
            Spacer(Modifier.width(Space.xs + Space.xxs))
            Text(if (state.paused) "Resume" else "Pause", maxLines = 1)
        }
        Button(
            onClick = actions.onStop,
            modifier = Modifier.weight(1f).heightIn(min = 40.dp),
            contentPadding = CompactButtonPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall + Space.xxs))
            Spacer(Modifier.width(Space.xs + Space.xxs))
            Text("Stop", maxLines = 1)
        }
        FilledTonalIconButton(onClick = actions.onOpenOro, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = "Open Oro",
                modifier = Modifier.size(Sizes.iconSmall + Space.xxs),
            )
        }
    }
}

/** One recent action: its glyph, what it was, and how it went. */
@Composable
private fun MiniStepRow(step: DeviceAgent.Step, lite: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
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
            when (step.status) {
                StepStatus.RUNNING -> if (lite) {
                    Icon(Icons.Outlined.HourglassTop, "Running", tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                } else {
                    OrobiLoader(modifier = Modifier.width(Sizes.icon), contentDescription = "Running")
                }
                StepStatus.WAITING -> Icon(Icons.Outlined.HourglassTop, "Waiting", tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                StepStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, "Failed", tint = scheme.error, modifier = Modifier.size(14.dp))
                StepStatus.BLOCKED -> Icon(Icons.Outlined.Block, "Denied", tint = scheme.error, modifier = Modifier.size(14.dp))
                StepStatus.DONE -> Icon(
                    Icons.Outlined.Check,
                    contentDescription = if (step.screenChanged == false) "No change" else "Done",
                    tint = if (step.screenChanged == false) scheme.outline else scheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun ApprovalBody(what: String, onApprove: (Boolean) -> Unit) {
    Text(
        text = IslandText.approvalQuestion(what),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        OutlinedButton(
            onClick = { onApprove(false) },
            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
        ) { Text("Deny", maxLines = 1) }
        Button(
            onClick = { onApprove(true) },
            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
        ) { Text("Allow", maxLines = 1) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionBody(question: DeviceAgent.Question, onAnswer: (String) -> Unit, onOpenOro: () -> Unit) {
    var reply by remember(question) { mutableStateOf("") }
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
    ReplyField(value = reply, onValueChange = { reply = it }, onSend = {
        val text = reply.trim()
        if (text.isNotEmpty()) onAnswer(text)
    })
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = onOpenOro) { Text("Open Oro", maxLines = 1) }
    }
}

/** The composer's look, cut down: a field and a round send button. */
@Composable
private fun ReplyField(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = OroCorners.xLarge,
        color = scheme.surfaceContainer,
        border = BorderStroke(Sizes.hairline, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val ready = value.isNotBlank()
            Surface(
                onClick = onSend,
                enabled = ready,
                shape = RoundedCornerShape(percent = 50),
                color = if (ready) scheme.primary else scheme.surfaceContainerHigh,
                contentColor = if (ready) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Send answer", modifier = Modifier.size(Sizes.icon - Space.xxs))
                }
            }
        }
    }
}

@Composable
private fun ResultBody(state: DeviceAgent.State, phase: IslandPhase, onOpenOro: () -> Unit) {
    val text = when (phase) {
        IslandPhase.Done -> state.summary
        else -> state.summary?.takeIf { state.success == false } ?: state.error ?: state.summary
    }
    text?.let { IslandModel.plain(it) }?.takeIf { it.isNotBlank() }?.let { body ->
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
    FilledTonalButton(onClick = onOpenOro, contentPadding = CompactButtonPadding) {
        Text("Open Oro", maxLines = 1)
    }
}

// ----------------------------------------------------------------- glyphs

/**
 * The island's face: Orobi's infinity loader while it works, a still glyph
 * for every other state. Swapped with a quick scale so a change of state is
 * noticed from the corner of the eye.
 */
@Composable
private fun PhaseGlyph(phase: IslandPhase, lite: Boolean, size: Dp) {
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
            when (p) {
                IslandPhase.Working -> OrobiLoader(modifier = Modifier.width(size - Space.xxs), contentDescription = "Working")
                IslandPhase.Paused -> Icon(Icons.Outlined.Pause, "Paused", tint = scheme.onSurface, modifier = Modifier.size(Sizes.icon))
                IslandPhase.Question -> AttentionGlyph(lite) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, "Question", tint = scheme.onSurface, modifier = Modifier.size(Sizes.icon))
                }
                IslandPhase.Approval -> AttentionGlyph(lite) {
                    Icon(Icons.Outlined.PrivacyTip, "Permission needed", tint = scheme.onSurface, modifier = Modifier.size(Sizes.icon))
                }
                IslandPhase.Done -> Icon(Icons.Outlined.TaskAlt, "Done", tint = OroBrand.GreenDark, modifier = Modifier.size(Sizes.icon))
                IslandPhase.Failed -> Icon(Icons.Outlined.ErrorOutline, "Stopped", tint = scheme.error, modifier = Modifier.size(Sizes.icon))
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
