package com.oro.app.ui.screens.editor.sessions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/**
 * The chat's header: which conversation this is, and the way to the others.
 *
 * Deliberately thin (one 44dp row) because the chat already sits under the
 * app bar and the Chat/Code switch; every pixel here is one less line of the
 * conversation. The title is the switcher -- the ChatGPT/Claude pattern of
 * "tap the name of the thing to change it" -- so there is no separate
 * history icon to learn, and New chat is the one action that earns its own
 * button because it is the most frequent thing done from here.
 */
@Composable
fun SessionBar(
    title: String,
    sessionCount: Int,
    isRunActive: Boolean,
    newChatEnabled: Boolean,
    onOpenSessions: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.tabBar)
            .padding(start = Space.sm, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The Box takes the free width so New chat stays at the end; the
        // pill inside wraps its title and ellipsizes only when it must.
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                onClick = onOpenSessions,
                shape = OroCorners.pill,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.semantics { contentDescription = "Chat: $title. Show all chats" }
            ) {
                Row(
                    modifier = Modifier.padding(start = Space.md, end = Space.sm, top = Space.xs + Space.xxs, bottom = Space.xs + Space.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // A live run is marked here too: it is why the switcher will
                    // not switch, and the user should not have to open it to learn
                    // that something is running.
                    if (isRunActive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(Modifier.width(Space.sm))
                    }
                    // Cross-fades when the session changes, so a switch is seen
                    // to have happened even when two titles look alike.
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.weight(1f, fill = false),
                        label = "session_title"
                    ) { shown ->
                        Text(
                            text = shown,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (sessionCount > 1) {
                        Spacer(Modifier.width(Space.sm))
                        CountBadge(sessionCount)
                    }
                    Spacer(Modifier.width(Space.xxs))
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(Sizes.icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        IconButton(onClick = onNewChat, enabled = newChatEnabled) {
            Icon(
                imageVector = Icons.Outlined.AddComment,
                contentDescription = "New chat",
                modifier = Modifier.size(Sizes.icon)
            )
        }
    }
}

/** How many chats this project has: the hint that there are others to switch to. */
@Composable
private fun CountBadge(count: Int) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, OroCorners.pill)
            .padding(horizontal = Space.sm - Space.xxs, vertical = Space.xxs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
