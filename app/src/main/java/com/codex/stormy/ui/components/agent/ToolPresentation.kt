package com.codex.stormy.ui.components.agent

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * How a tool call is described to the user.
 *
 * The raw tool name is a protocol detail. `write_file` is what the model
 * emitted; "Wrote index.html" is what happened. Showing the former makes the
 * transcript read like a log of API calls rather than an account of work, and
 * it is the single biggest reason agent UIs feel like debug output.
 *
 * Each entry gives a past-tense verb for completed calls and a present
 * participle for running ones, because tense is what distinguishes "it is
 * doing this" from "it did this" at a glance -- more reliably than a colour or
 * a spinner, both of which the eye skips.
 */
data class ToolPresentation(
    /** Present participle: shown while the call is running. */
    val running: String,
    /** Past tense: shown once it has finished. */
    val done: String,
    val icon: ImageVector,
    /**
     * Whether the tool's subject is a file path. Paths are truncated from the
     * start so the filename survives; everything else truncates from the end.
     */
    val subjectIsPath: Boolean = true
)

object ToolPresentations {

    private val map: Map<String, ToolPresentation> = mapOf(
        "read_file" to ToolPresentation("Reading", "Read", Icons.Outlined.Description),
        "write_file" to ToolPresentation("Writing", "Wrote", Icons.Outlined.EditNote),
        "list_files" to ToolPresentation("Listing", "Listed", Icons.Outlined.FolderOpen),
        "delete_file" to ToolPresentation("Deleting", "Deleted", Icons.Outlined.Delete),
        "create_folder" to ToolPresentation(
            "Creating folder", "Created folder", Icons.Outlined.CreateNewFolder
        ),
        "rename_file" to ToolPresentation(
            "Renaming", "Renamed", Icons.Outlined.DriveFileRenameOutline
        ),
        "copy_file" to ToolPresentation("Copying", "Copied", Icons.Outlined.ContentCopy),
        "move_file" to ToolPresentation("Moving", "Moved", Icons.Outlined.DriveFileMove),
        "search_files" to ToolPresentation("Searching", "Searched", Icons.Outlined.FindInPage),
        "search_replace" to ToolPresentation("Replacing in", "Replaced in", Icons.Outlined.EditNote),
        "patch_file" to ToolPresentation("Patching", "Patched", Icons.Outlined.EditNote),
        "run_command" to ToolPresentation(
            "Running", "Ran", Icons.Outlined.Terminal, subjectIsPath = false
        ),
        "save_memory" to ToolPresentation(
            "Saving note", "Noted", Icons.Outlined.Memory, subjectIsPath = false
        ),
        "recall_memory" to ToolPresentation(
            "Recalling", "Recalled", Icons.Outlined.Memory, subjectIsPath = false
        ),
        "list_memories" to ToolPresentation(
            "Reading notes", "Read notes", Icons.Outlined.Memory, subjectIsPath = false
        ),
        "spawn_subagent" to ToolPresentation(
            "Delegating", "Delegated", Icons.AutoMirrored.Outlined.CallSplit, subjectIsPath = false
        ),
        "update_plan" to ToolPresentation(
            "Updating plan", "Updated plan", Icons.Outlined.Checklist, subjectIsPath = false
        ),
        "request_approval" to ToolPresentation(
            "Asking you", "Asked you", Icons.Outlined.AutoAwesome, subjectIsPath = false
        )
    )

    /**
     * Presentation for a tool name.
     *
     * Falls back to a humanised form of the raw name rather than to the name
     * itself, so a tool added without an entry here still reads as prose
     * ("run_lighthouse" becomes "Run lighthouse") instead of as an identifier.
     */
    operator fun get(toolName: String): ToolPresentation {
        map[toolName.lowercase()]?.let { return it }

        val words = toolName.replace('_', ' ').replace('-', ' ').trim()
        val humanised = words.replaceFirstChar { it.uppercase() }
        return ToolPresentation(
            running = humanised,
            done = humanised,
            icon = Icons.Outlined.AutoAwesome,
            subjectIsPath = false
        )
    }

    /**
     * One line describing the call: verb plus subject.
     *
     * The subject is omitted rather than padded when absent -- "Read" alone is
     * better than "Read (no file)".
     */
    fun describe(toolName: String, subject: String?, isRunning: Boolean): String {
        val presentation = get(toolName)
        val verb = if (isRunning) presentation.running else presentation.done
        val trimmed = subject?.trim()?.takeIf { it.isNotEmpty() } ?: return verb
        return "$verb $trimmed"
    }

    /**
     * Shorten a path so the filename stays readable in one line.
     *
     * Truncating a path from the end is actively unhelpful: every path in a
     * project shares its leading directories, so the visible part is the part
     * that identifies nothing. Keeping the tail plus one parent gives
     * "…/components/Button.kt", which is what the user actually needs.
     */
    fun shortenPath(path: String, maxSegments: Int = 2): String {
        val clean = path.trim().trimEnd('/')
        val segments = clean.split('/').filter { it.isNotEmpty() }
        if (segments.size <= maxSegments) return clean
        return "…/" + segments.takeLast(maxSegments).joinToString("/")
    }
}
