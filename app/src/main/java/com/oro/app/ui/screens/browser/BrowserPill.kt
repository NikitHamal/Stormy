package com.oro.app.ui.screens.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * The way back to a live browser run from anywhere else in Oro.
 *
 * The run lives in the process-wide [BrowserAgent], not in a screen, so
 * minimising the browser, editing code or switching chats never stops it --
 * and the user is never left wondering where it went: the pill shows the
 * current step, turns insistent when the run needs them (a question, an
 * approval, a secret, a human check), one tap opens the live view, and the
 * ✕ stops the run. It hides on the browser screen itself, which shows all of
 * this in full.
 */
@Composable
fun BrowserPill(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val run by BrowserAgent.state.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = run.running,
        enter = fadeIn() + slideInVertically { it * 2 },
        exit = fadeOut() + slideOutVertically { it * 2 },
        modifier = modifier,
    ) {
        val needsUser = run.needsUser
        Surface(
            onClick = onOpen,
            shape = CircleShape,
            color = if (needsUser) OroBrand.Magenta else MaterialTheme.colorScheme.inverseSurface,
            contentColor = if (needsUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = Space.lg)
        ) {
            Row(
                modifier = Modifier.padding(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (needsUser) {
                    Icon(
                        imageVector = Icons.Outlined.PriorityHigh,
                        contentDescription = null,
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                } else {
                    Box(Modifier.size(Sizes.iconSmall), contentAlignment = Alignment.Center) {
                        OrobiLoader(modifier = Modifier.width(18.dp), contentDescription = null)
                    }
                }
                Spacer(Modifier.width(Space.md))
                Text(
                    text = run.status.ifBlank { "Working in the browser" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp)
                )
                IconButton(
                    onClick = { BrowserAgent.stop() },
                    modifier = Modifier.size(Sizes.iconLarge)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Stop the browser task", modifier = Modifier.size(Sizes.iconSmall))
                }
            }
        }
    }
}
