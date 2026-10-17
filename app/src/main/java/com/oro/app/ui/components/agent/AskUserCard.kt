package com.oro.app.ui.components.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.data.ai.tools.QuestionOption
import com.oro.app.data.ai.tools.UserQuestion
import com.oro.app.ui.brand.OrobiWorking
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space

/**
 * The questions the agent asked in one `ask_user` call, while the run waits.
 *
 * [id] changes per call so per-call UI state (step, drafts, picks) resets when
 * the agent asks something new.
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
 * Several questions are a stepper: one at a time, a progress bar, Back to
 * revise, Skip per question. Single-choice options answer and advance on tap;
 * multi-select collects picks until Next. Every answer goes back in one tool
 * result once the last question is done ([onSubmit], null entries = skipped).
 * Stopping the run (the input's stop button) cancels the whole set.
 */
@Composable
fun AskUserCard(
    question: PendingQuestion,
    onSubmit: (List<String?>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val questions = question.questions
    if (questions.isEmpty()) return
    val count = questions.size

    var step by rememberSaveable(question.id) { mutableIntStateOf(0) }
    // Per-question state lives for the life of this call. Not saveable: the
    // view model keeps the call itself alive across rotation, and losing a
    // half-picked answer there is cheaper than a Bundle round trip per pick.
    val answers = remember(question.id) { mutableStateListOf<String?>().apply { repeat(count) { add(null) } } }
    val drafts = remember(question.id) { mutableStateListOf<String>().apply { repeat(count) { add("") } } }
    val picks = remember(question.id) { List(count) { mutableStateListOf<String>() } }

    val current = questions[step.coerceIn(0, count - 1)]
    val index = step.coerceIn(0, count - 1)

    fun advance(answer: String?) {
        answers[index] = answer?.trim()?.takeIf { it.isNotEmpty() }
        if (index == count - 1) onSubmit(answers.toList()) else step = index + 1
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OrobiWorking(
                    label = if (count == 1) "Waiting for your answer" else "A few quick questions",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                if (count > 1) {
                    Text(
                        text = "${index + 1} of $count",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (count > 1) StepBar(count = count, current = index, answered = answers)

            if (current.header.isNotBlank()) {
                Text(
                    text = current.header.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = current.question,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (current.options.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    current.options.forEach { option ->
                        val selected = if (current.multiSelect) option.label in picks[index]
                        else answers[index] == option.label
                        OptionRow(
                            option = option,
                            selected = selected,
                            multiSelect = current.multiSelect,
                            onClick = {
                                if (current.multiSelect) {
                                    val p = picks[index]
                                    if (option.label in p) p.remove(option.label) else p.add(option.label)
                                } else {
                                    advance(option.label)
                                }
                            }
                        )
                    }
                }
            }

            val draft = drafts[index]
            val submitDraft = {
                val typed = drafts[index].trim()
                val chosen = picks[index].toList()
                val combined = (chosen + listOfNotNull(typed.takeIf { it.isNotEmpty() })).joinToString(", ")
                if (combined.isNotEmpty()) advance(combined)
            }
            if (current.allowFreeText || current.options.isEmpty()) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { drafts[index] = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(if (current.options.isEmpty()) "Type your answer" else "Or type your own answer")
                    },
                    shape = OroCorners.medium,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = if (index == count - 1) ImeAction.Send else ImeAction.Next),
                    keyboardActions = KeyboardActions(onSend = { submitDraft() }, onNext = { submitDraft() })
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                if (index > 0) {
                    TextButton(onClick = { step = index - 1 }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Space.xs))
                        Text("Back")
                    }
                }
                TextButton(onClick = { if (count == 1) onDismiss() else advance(null) }) {
                    Text(if (count == 1) "Skip" else "Skip question")
                }
                Spacer(Modifier.weight(1f))
                val canSubmit = draft.isNotBlank() || picks[index].isNotEmpty()
                if (canSubmit || current.multiSelect || current.options.isEmpty()) {
                    Button(onClick = submitDraft, enabled = canSubmit, shape = OroCorners.pill) {
                        Text(if (index == count - 1) "Send" else "Next")
                    }
                }
            }
        }
    }
}

/** Segmented progress: filled for answered/current, outlined ahead. */
@Composable
private fun StepBar(count: Int, current: Int, answered: List<String?>) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.fillMaxWidth()) {
        repeat(count) { i ->
            val color = when {
                i == current -> MaterialTheme.colorScheme.primary
                answered.getOrNull(i) != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(OroCorners.pill)
                    .background(color)
            )
        }
    }
}

@Composable
private fun OptionRow(
    option: QuestionOption,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = OroCorners.medium,
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(OroCorners.medium)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm + Space.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(if (multiSelect) OroCorners.tiny else CircleShape)
                    .background(if (selected) scheme.primary else scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (option.description.isNotBlank()) {
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
