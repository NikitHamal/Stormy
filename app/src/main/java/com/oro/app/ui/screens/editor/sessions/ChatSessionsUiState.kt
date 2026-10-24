package com.oro.app.ui.screens.editor.sessions

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.oro.app.domain.model.ChatSession
import com.oro.app.domain.model.SessionTitles

/**
 * The chat sessions of the open project, for the switcher and the sessions
 * sheet.
 *
 * Kept out of EditorUiState on purpose: the session list re-emits whenever a
 * message is written (its counts and ordering change), and folding that into
 * the editor's one big state object would recompose the code tab and every
 * other reader of it during a streaming run for a list nobody is looking at.
 *
 * @Immutable for the same reason as EditorUiState: the list is only ever
 * replaced wholesale.
 */
@Immutable
data class ChatSessionsUiState(
    /** Pinned first, then most recently active -- the order the DAO returns. */
    val sessions: List<ChatSession> = emptyList(),
    /** Null while the user is in a fresh chat that has not been sent yet. */
    val currentSessionId: String? = null,
    val currentTitle: String = SessionTitles.DEFAULT_TITLE,
    /** True between choosing a session and its messages arriving. */
    val isLoadingSession: Boolean = true,
    /** Title of a just-deleted session that can still be restored. */
    val undoTitle: String? = null
) {
    val isDraft: Boolean get() = currentSessionId == null
}

/**
 * What the sessions UI can ask of the editor.
 *
 * One object instead of seven lambda parameters threaded through ChatTab;
 * built once per ViewModel with `remember`, so it is referentially stable and
 * never the reason anything recomposes.
 */
@Stable
class ChatSessionActions(
    val onOpen: (String) -> Unit,
    val onNewChat: () -> Unit,
    val onRename: (String, String) -> Unit,
    val onSetPinned: (String, Boolean) -> Unit,
    val onDelete: (String) -> Unit,
    val onUndoDelete: () -> Unit,
    val onDismissUndo: () -> Unit
) {
    companion object {
        /** For previews and call sites that do not support sessions. */
        val None = ChatSessionActions({}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {})
    }
}
