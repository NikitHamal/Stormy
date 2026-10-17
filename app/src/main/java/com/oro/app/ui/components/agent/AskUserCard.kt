package com.oro.app.ui.components.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.oro.app.ui.brand.OrobiWorking
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space

/**
 * A question the agent asked with `ask_user`, while the run waits on it.
 *
 * [id] changes per question so per-question UI state (the typed draft) resets
 * when the agent asks something new.
 */
@Immutable
data class PendingQuestion(
    val id: String,
    val question: String,
    val options: List<String> = emptyList()
)

/**
 * Inline, in the transcript, rather than a sheet: the answer is part of the
 * conversation, and the user often needs to scroll up to what the agent did
 * before answering -- a modal would hide exactly that.
 *
 * Tapping a suggested option answers immediately; the text field takes any
 * other answer. "Skip" tells the agent the user declined, which the agent is
 * instructed to handle by proceeding on its best judgement. Stopping the run
 * (the input's stop button) cancels the question along with everything else.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskUserCard(
    question: PendingQuestion,
    onAnswer: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by rememberSaveable(question.id) { mutableStateOf("") }
    val send = {
        val answer = draft.trim()
        if (answer.isNotEmpty()) onAnswer(answer)
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
            OrobiWorking(
                label = "Waiting for your answer",
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                text = question.question,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (question.options.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    question.options.forEach { option ->
                        SuggestionChip(
                            onClick = { onAnswer(option) },
                            label = { Text(option, style = MaterialTheme.typography.labelLarge) },
                            shape = OroCorners.pill
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (question.options.isEmpty()) "Type your answer" else "Or type something else")
                    },
                    shape = OroCorners.medium,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() })
                )
                FilledIconButton(
                    onClick = send,
                    enabled = draft.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send answer",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            TextButton(
                onClick = onSkip,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Skip question")
            }
        }
    }
}
