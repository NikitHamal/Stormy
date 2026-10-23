package com.oro.app.ui.components.agent

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.data.ai.tools.QuestionOption
import com.oro.app.data.ai.tools.UserQuestion
import com.oro.app.ui.brand.OroSparkle
import com.oro.app.ui.brand.OrobiWorking
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.OroTheme
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/**
 * The questions the agent asked in one `ask_user` call, while the run waits.
 *
 * [id] changes per call so per-call UI state (picks, drafts) resets when the
 * agent asks something new.
 */
@Immutable
data class PendingQuestion(
    val id: String,
    val questions: List<UserQuestion>
)

/**
 * Inline, in the transcript, rather than a sheet: the answer is part of the
 * conversation, and the user often needs to scroll up to what the agent did
 * before answering -- a modal would hide exactly that.
 *
 * Built to be answered in one tap, because nobody wants a quiz before their
 * site exists:
 *  - every question is on the card at once, its options as chips;
 *  - the option the agent recommends is marked and, on a card with several
 *    questions, already picked -- Send accepts them all;
 *  - a single question with options answers the moment a chip is tapped;
 *  - "You decide" is always there and hands every choice back to the agent;
 *  - "Other" opens a field for an answer of one's own.
 * [onSubmit] gets one entry per question (null = left open); [onDismiss] is
 * "You decide". Stopping the run (the composer's stop button) cancels it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskUserCard(
    question: PendingQuestion,
    onSubmit: (List<String?>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** The one-tap way out: the agent decides (the editor), or the question is skipped (the device agent). */
    dismissLabel: String = "You decide"
) {
    val questions = question.questions
    if (questions.isEmpty()) return
    val count = questions.size
    // One tap answers a lone single-choice question; a card of several collects, then Send.
    val tapToAnswer = count == 1 && !questions[0].multiSelect && questions[0].options.isNotEmpty()

    // Per-question state for the life of this call. Not saveable: the view
    // model keeps the call alive across rotation, and losing a half-picked
    // answer there is cheaper than a Bundle round trip per pick.
    val picks = remember(question.id) {
        questions.map { q ->
            mutableStateListOf<String>().apply {
                if (!tapToAnswer) q.options.filter { it.recommended }.forEach { add(it.label) }
            }
        }
    }
    val drafts = remember(question.id) { mutableStateListOf<String>().apply { repeat(count) { add("") } } }
    val typing = remember(question.id) { mutableStateListOf<Boolean>().apply { questions.forEach { add(it.options.isEmpty()) } } }

    fun answerOf(i: Int): String? {
        val q = questions[i]
        val typed = drafts[i].trim().takeIf { typing[i] && it.isNotEmpty() }
        val chosen = picks[i].toList()
        return when {
            typed != null && q.multiSelect -> (chosen + typed).joinToString(", ")
            typed != null -> typed
            chosen.isNotEmpty() -> chosen.joinToString(", ")
            else -> null
        }
    }
    val canSend = questions.indices.any { answerOf(it) != null }
    val send = { if (canSend) onSubmit(questions.indices.map(::answerOf)) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(Space.lg)
                .then(if (OroTheme.liteMode) Modifier else Modifier.animateContentSize()),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            OrobiWorking(
                label = if (count == 1) "A quick question" else "$count quick questions",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )

            questions.forEachIndexed { i, q ->
                QuestionBlock(
                    question = q,
                    picks = picks[i],
                    draft = drafts[i],
                    typing = typing[i],
                    last = i == count - 1,
                    onPick = { option ->
                        if (tapToAnswer) {
                            onSubmit(listOf(option.label))
                        } else if (q.multiSelect) {
                            val p = picks[i]
                            if (option.label in p) p.remove(option.label) else p.add(option.label)
                        } else {
                            picks[i].clear()
                            picks[i].add(option.label)
                            typing[i] = false
                        }
                    },
                    onOther = {
                        val open = !typing[i]
                        typing[i] = open
                        if (open && !q.multiSelect) picks[i].clear()
                    },
                    onDraft = { drafts[i] = it },
                    onDone = { send() }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                // A lone tap-to-answer question needs Send only for a typed answer.
                if (!tapToAnswer || typing[0]) {
                    Button(onClick = send, enabled = canSend, shape = OroCorners.pill) {
                        Text("Send", maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionBlock(
    question: UserQuestion,
    picks: List<String>,
    draft: String,
    typing: Boolean,
    last: Boolean,
    onPick: (QuestionOption) -> Unit,
    onOther: () -> Unit,
    onDraft: (String) -> Unit,
    onDone: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (question.header.isNotBlank()) {
            Text(
                text = question.header.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = question.question,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (question.options.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                question.options.forEach { option ->
                    AnswerChip(
                        label = option.label,
                        selected = option.label in picks,
                        recommended = option.recommended,
                        multiSelect = question.multiSelect,
                        onClick = { onPick(option) }
                    )
                }
                if (question.allowFreeText) {
                    AnswerChip(
                        label = "Other",
                        selected = typing,
                        recommended = false,
                        multiSelect = true,
                        onClick = onOther
                    )
                }
            }
            // The picked option's explanation, when the agent gave one: room for it only once it matters.
            question.options.firstOrNull { it.label in picks && it.description.isNotBlank() }?.let {
                Text(
                    text = it.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (typing) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Your answer", maxLines = 1) },
                shape = OroCorners.medium,
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Send else ImeAction.Next),
                keyboardActions = KeyboardActions(onSend = { onDone() })
            )
        }
    }
}

/**
 * One option. Picked: filled, with a check. Recommended: a small sparkle (the
 * brand's mark for "Orobi suggests this"), picked or not, so the suggestion is
 * still visible after the user picks something else.
 */
@Composable
private fun AnswerChip(
    label: String,
    selected: Boolean,
    recommended: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        shape = OroCorners.pill,
        color = if (selected) scheme.primary else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
        border = if (!selected && recommended) BorderStroke(Sizes.hairline, scheme.primary.copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .semantics {
                role = if (multiSelect) Role.Checkbox else Role.RadioButton
                if (recommended) stateDescription = if (selected) "Selected, recommended" else "Recommended"
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs + Space.xxs)
        ) {
            when {
                selected -> Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                recommended -> OroSparkle(Modifier.size(12.dp), color = scheme.primary)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
