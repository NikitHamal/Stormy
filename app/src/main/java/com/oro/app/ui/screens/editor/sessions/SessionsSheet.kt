package com.oro.app.ui.screens.editor.sessions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.oro.app.domain.model.ChatSession
import com.oro.app.ui.brand.OrobiWorking
import com.oro.app.ui.components.expressive.ActionSheet
import com.oro.app.ui.components.expressive.EmptyStateShape
import com.oro.app.ui.components.expressive.ExpressiveEmptyState
import com.oro.app.ui.components.expressive.ExpressiveNoResults
import com.oro.app.ui.components.expressive.FormSheet
import com.oro.app.ui.components.expressive.SheetAction
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/** Search appears once the list is long enough that scanning stops working. */
private const val SEARCH_THRESHOLD = 4

/**
 * The project's chat sessions, as a bottom sheet.
 *
 * A sheet rather than a drawer or a screen: the editor already spends both
 * side edges on drawers (files, git, assets), a separate screen would tear
 * the user out of the conversation for what is usually a two-second hop, and
 * everything else in the app that picks from a list -- models, actions,
 * context -- is a sheet anchored where the thumb is.
 *
 * Layout, top to bottom: what the sheet is, the one action most people open
 * it for (New chat), the run banner when switching is blocked, search once
 * the list is long, then the sessions grouped by recency the way every chat
 * app the user already knows groups them.
 */
@Composable
fun SessionsSheet(
    state: ChatSessionsUiState,
    projectName: String?,
    isRunActive: Boolean,
    onStopRun: (() -> Unit)?,
    actions: ChatSessionActions,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf<ChatSession?>(null) }
    var renameTarget by remember { mutableStateOf<ChatSession?>(null) }

    // Fixed while the sheet is open: relative times go stale by a minute at
    // most, and a ticking clock would regroup the list under the user's thumb.
    val now = remember { System.currentTimeMillis() }
    val entries = remember(state.sessions, query, now) {
        SessionGrouping.entries(state.sessions, query, now)
    }
    val showSearch = state.sessions.size >= SEARCH_THRESHOLD || query.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.padding(horizontal = Space.xl)) {
                // Title and the one action most people open this for, on one
                // row: the full-width button it replaces cost a whole row of
                // list on a small phone.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Chats",
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = sheetSubtitle(state.sessions.size, projectName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = {
                            actions.onNewChat()
                            onDismiss()
                        },
                        enabled = !isRunActive && !(state.isDraft),
                        shape = OroCorners.pill,
                        contentPadding = PaddingValues(start = Space.md, end = Space.lg)
                    ) {
                        Icon(Icons.Outlined.AddComment, contentDescription = null, modifier = Modifier.size(Sizes.icon))
                        Spacer(Modifier.width(Space.sm))
                        Text("New chat", style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                }

                AnimatedVisibility(
                    visible = isRunActive,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    RunBanner(
                        title = state.currentTitle,
                        onStop = onStopRun,
                        modifier = Modifier.padding(top = Space.md)
                    )
                }

                if (showSearch) {
                    Spacer(Modifier.height(Space.md))
                    SessionSearchField(query = query, onQueryChange = { query = it })
                }
            }

            Spacer(Modifier.height(Space.sm))

            when {
                state.sessions.isEmpty() -> ExpressiveEmptyState(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = "No chats yet",
                    description = "Each conversation you start in this project is kept here, " +
                        "so you can switch between threads without losing context.",
                    shape = EmptyStateShape.Clover,
                    markSize = 72.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.xl)
                )
                entries.isEmpty() -> ExpressiveNoResults(
                    query = query,
                    onClear = { query = "" },
                    noun = "chats",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.xl)
                )
                else -> LazyColumn(
                    // fill = false: a short list sizes the sheet to its
                    // content instead of stretching it to full height.
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentPadding = PaddingValues(
                        start = Space.md,
                        end = Space.md,
                        bottom = Space.lg
                    )
                ) {
                    items(
                        items = entries,
                        key = { it.key },
                        contentType = { if (it is SessionListEntry.Header) 0 else 1 }
                    ) { entry ->
                        when (entry) {
                            is SessionListEntry.Header -> SectionHeader(
                                label = entry.bucket.label,
                                modifier = Modifier.animateItem()
                            )
                            is SessionListEntry.Row -> {
                                val isCurrent = entry.session.id == state.currentSessionId
                                SessionRow(
                                    entry = entry,
                                    isCurrent = isCurrent,
                                    // A live run pins the user to its chat;
                                    // the banner above says why.
                                    enabled = !isRunActive || isCurrent,
                                    onClick = {
                                        if (!isCurrent) actions.onOpen(entry.session.id)
                                        onDismiss()
                                    },
                                    onShowActions = { actionTarget = entry.session },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }

            // Inside the sheet as well as under the chat: a modal sheet
            // covers the screen's own snackbar area, so an undo shown only
            // there would be invisible exactly when the delete happened here.
            SessionUndoBar(
                title = state.undoTitle,
                onUndo = actions.onUndoDelete,
                onDismiss = actions.onDismissUndo,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)
            )
        }
    }

    actionTarget?.let { session ->
        val isCurrent = session.id == state.currentSessionId
        ActionSheet(
            title = session.title,
            subtitle = SessionGrouping.messageCountLabel(session.messageCount),
            onDismiss = { actionTarget = null },
            actions = listOf(
                SheetAction(
                    label = if (session.isPinned) "Unpin" else "Pin to top",
                    // Filled when pinned, so the row states the current state
                    // as well as the action -- as on the project list.
                    icon = if (session.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    onClick = { actions.onSetPinned(session.id, !session.isPinned) }
                ),
                SheetAction(
                    label = "Rename",
                    icon = Icons.Outlined.Edit,
                    onClick = { renameTarget = session }
                ),
                SheetAction(
                    label = "Delete chat",
                    icon = Icons.Outlined.Delete,
                    description = if (isCurrent && isRunActive) {
                        "Stop the run first"
                    } else {
                        "You can undo this for a few seconds"
                    },
                    destructive = true,
                    enabled = !(isCurrent && isRunActive),
                    onClick = { actions.onDelete(session.id) }
                )
            )
        )
    }

    renameTarget?.let { session ->
        RenameSessionSheet(
            currentTitle = session.title,
            onRename = { title ->
                actions.onRename(session.id, title)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
}

private fun sheetSubtitle(count: Int, projectName: String?): String {
    val chats = when (count) {
        0 -> "No saved chats"
        1 -> "1 chat"
        else -> "$count chats"
    }
    return if (projectName.isNullOrBlank()) chats else "$chats in $projectName"
}

@Composable
private fun RunBanner(title: String, onStop: (() -> Unit)?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = Space.md, end = Space.xs, top = Space.sm, bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OrobiWorking(
                    label = "Working in “$title”",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "Stop the run to switch chats or start a new one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            if (onStop != null) {
                TextButton(onClick = onStop) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun SessionSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search chats") },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(Sizes.icon))
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search", modifier = Modifier.size(Sizes.icon))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = OroCorners.pill,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

@Composable
private fun SectionHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Space.md, end = Space.md, top = Space.lg, bottom = Space.xs)
    )
}

@Composable
private fun SessionRow(
    entry: SessionListEntry.Row,
    isCurrent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onShowActions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = entry.session
    val scheme = MaterialTheme.colorScheme
    // The current chat is marked by its container and a filled badge, not a
    // checkmark: the tint reads at a glance, a 16dp glyph does not.
    val container = if (isCurrent) scheme.secondaryContainer else Color.Transparent
    val onContainer = if (isCurrent) scheme.onSecondaryContainer else scheme.onSurface
    val secondary = if (isCurrent) scheme.onSecondaryContainer.copy(alpha = 0.75f) else scheme.onSurfaceVariant
    val preview = session.preview?.takeIf { !it.startsWith(session.title.removeSuffix("…"), ignoreCase = true) }

    Surface(modifier = modifier.fillMaxWidth(), shape = OroCorners.large, color = container) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    enabled = enabled,
                    onClickLabel = "Open chat",
                    onLongClickLabel = "Chat actions",
                    onLongClick = onShowActions,
                    onClick = onClick
                )
                .alpha(if (enabled) 1f else 0.38f)
                .padding(start = Space.md, top = Space.sm + Space.xxs, bottom = Space.sm + Space.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(Sizes.avatarLarge),
                shape = OroCorners.medium,
                color = if (isCurrent) scheme.primary else scheme.surfaceContainerHighest,
                contentColor = if (isCurrent) scheme.onPrimary else scheme.onSurfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (session.isPinned) Icons.Filled.PushPin else Icons.Outlined.ChatBubbleOutline,
                        contentDescription = if (session.isPinned) "Pinned" else null,
                        modifier = Modifier.size(Sizes.iconSmall + 2.dp)
                    )
                }
            }
            Spacer(Modifier.width(Space.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                        color = onContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(text = entry.relativeTime, style = MaterialTheme.typography.labelSmall, color = secondary)
                }
                Text(
                    text = preview ?: SessionGrouping.messageCountLabel(session.messageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Long-press is not discoverable on its own, so the overflow is
            // always there. Not disabled during a run: renaming or pinning
            // another chat does not touch the one that is running.
            IconButton(onClick = onShowActions) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "Actions for ${session.title}",
                    tint = secondary,
                    modifier = Modifier.size(Sizes.icon)
                )
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .padding(horizontal = Space.xs + Space.xxs)
            .size(3.dp)
            .background(color.copy(alpha = 0.7f), CircleShape)
    )
}

/**
 * "Deleted <title> -- Undo", inline. Used both in the sessions sheet and
 * above the chat input, so the undo is visible wherever the user is.
 */
@Composable
fun SessionUndoBar(
    title: String?,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The last non-null title, so the exit animation still has text to show.
    // A plain holder, not state: it only ever changes alongside `title`,
    // which already drives recomposition.
    val lastTitleHolder = remember { arrayOf("") }
    if (title != null) lastTitleHolder[0] = title
    val lastTitle = lastTitleHolder[0]

    AnimatedVisibility(
        visible = title != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        // The caller's padding goes on the bar, not on AnimatedVisibility,
        // so a hidden bar takes no space at all.
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = OroCorners.large,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        ) {
            Row(
                modifier = Modifier.padding(start = Space.lg, end = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Deleted “$lastTitle”",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onUndo) {
                    Text(
                        "Undo",
                        color = MaterialTheme.colorScheme.inversePrimary,
                        style = MaterialTheme.typography.labelLargeEmphasized
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                }
            }
        }
    }
}

/** Rename, as the app's standard form sheet with the title pre-selected. */
@Composable
private fun RenameSessionSheet(
    currentTitle: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Whole title selected, so typing replaces it and a tap places the caret.
    var value by remember {
        mutableStateOf(TextFieldValue(currentTitle, selection = TextRange(0, currentTitle.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val trimmed = value.text.trim()
    val canSave = trimmed.isNotEmpty() && trimmed != currentTitle

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    FormSheet(
        title = "Rename chat",
        icon = Icons.Outlined.Edit,
        onDismiss = onDismiss,
        confirmLabel = "Save",
        confirmEnabled = canSave,
        onConfirm = { if (canSave) onRename(trimmed) }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label = { Text("Title") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onRename(trimmed) })
        )
    }
}
