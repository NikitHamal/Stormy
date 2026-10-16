package com.codex.stormy.ui.components.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.theme.CodeXCorners
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Space

/**
 * What the agent is asking permission for, in the main conversation.
 *
 * Separate from the subagent approval row because the stakes differ: a
 * subagent's request is one line on a card inside a list of other work, while
 * this interrupts the user's own conversation and is the only thing on screen.
 * A sheet is the right weight for that -- it is modal, it cannot be missed, and
 * it is dismissible in the direction a phone user already expects.
 */
data class PendingApproval(
    /** What will happen, in plain language. Not a command or a JSON payload. */
    val action: String,
    /** Specifics the user needs in order to decide: the file list, the branch. */
    val detail: String? = null,
    /** True when the detail is code or a command and should be monospaced. */
    val detailIsCode: Boolean = false
)

/**
 * Modal approval gate for the main conversation.
 *
 * Deliberately not auto-dismissing and not timed. An approval that expires into
 * a default is worse than no gate at all: the user cannot tell afterwards
 * whether they agreed or simply looked away.
 *
 * Dismissing by swipe or scrim counts as **declining**, not as cancelling the
 * question. There is no third state here -- the agent is blocked until it has
 * an answer, so leaving the sheet without one would hang the turn.
 */
@Composable
fun ApprovalSheet(
    approval: PendingApproval?,
    onDecision: (approved: Boolean) -> Unit
) {
    if (approval == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        // Swiping away is a decline, for the reason in the doc above.
        onDismissRequest = { onDecision(false) },
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.xl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.PrivacyTip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.iconLarge)
                )
                Spacer(Modifier.width(Space.md))
                Text(
                    text = "Permission needed",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(Space.md))

            Text(
                // The action is the headline, at body size rather than a label:
                // this is the sentence the decision rests on, so it should read
                // as prose the user is expected to actually consume.
                text = approval.action,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            approval.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Spacer(Modifier.height(Space.md))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CodeXCorners.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = detail,
                        style = if (approval.detailIsCode) {
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            // Capped as a maximum, not a fixed height: a long
                            // file list must not push the buttons off screen
                            // (leaving the user unable to answer at all), but a
                            // one-line detail should not reserve 220dp either.
                            .heightIn(max = DetailMaxHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(Space.md)
                    )
                }
            }

            Spacer(Modifier.height(Space.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                // Decline first, and as the lower-emphasis button. The
                // irreversible choice should not be the one the thumb lands on
                // by default, and it should not be the one that looks like the
                // expected answer.
                OutlinedButton(
                    onClick = { onDecision(false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Don't allow")
                }
                Button(
                    onClick = { onDecision(true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Allow once")
                }
            }

            Spacer(Modifier.height(Space.sm))

            Text(
                // States the scope of what was just granted. Approval for one
                // action does not extend to the next, and saying so prevents
                // the user reading "Allow" as a standing permission.
                text = "Applies to this action only.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Space.lg))
        }
    }
}

/** Cap on the detail block, so the decision buttons stay reachable. */
private val DetailMaxHeight = 220.dp
