package com.oro.app.ui.screens.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oro.app.browser.BrowserAgent
import com.oro.app.ui.brand.OroBrand
import com.oro.app.ui.brand.OrobiLoader
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/**
 * The live transcript of the browser run, for the `use_browser` tool card's
 * sheet: the same steps the browser screen shows, updating as they happen,
 * with the summary once the run ends. The sheet is where a user checks on a
 * delegated browser task without leaving the chat; the eye beside it jumps
 * to the live page.
 */
@Composable
fun BrowserLivePanel(modifier: Modifier = Modifier) {
    val run by BrowserAgent.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val stepCount = run.steps.size
    LaunchedEffect(stepCount, run.status) {
        scroll.scrollTo(scroll.maxValue)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (run.running) {
                OrobiLoader(modifier = Modifier.width(18.dp), color = OroBrand.Magenta, contentDescription = null)
            }
            Spacer(Modifier.width(Space.sm))
            Text(
                text = when {
                    run.running -> run.status.ifBlank { "Working in the browser" } +
                        (run.currentUrl.takeIf { it.isNotBlank() }?.let { " · " + it.removePrefix("https://").take(34) } ?: "")
                    run.finished -> run.status.ifBlank { "Ended" }
                    else -> "No browser run yet"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (run.running) {
                Text(
                    text = "${run.actions} actions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (run.task.isNotBlank()) {
            Text(
                text = run.task,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (run.steps.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = Space.xs))
            run.steps.takeLast(40).forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = BrowserTranscript.iconFor(step),
                        contentDescription = null,
                        tint = when (step.status) {
                            BrowserAgent.StepStatus.FAILED, BrowserAgent.StepStatus.BLOCKED -> MaterialTheme.colorScheme.error
                            BrowserAgent.StepStatus.WAITING -> OroBrand.Magenta
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        val (verb, subject) = BrowserTranscript.splitTitle(step)
                        Text(
                            text = if (subject != null) "$verb $subject" else verb,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        step.detail.takeIf { it.isNotBlank() }?.let { d ->
                            Text(
                                text = d,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        if (run.finished) {
            HorizontalDivider(Modifier.padding(vertical = Space.xs))
            (run.summary ?: run.error)?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (run.running && run.needsUser) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "Orobi needs you on the browser screen (a check, a secret or an approval).",
                style = MaterialTheme.typography.labelMedium,
                color = OroBrand.Magenta
            )
        }
    }
}
