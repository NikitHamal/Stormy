package com.oro.app.ui.components.agent

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.LiveHelp
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * What a tool's detail looks like, which decides how the card renders it.
 *
 * A file write and a shell command both produce "output", but a unified diff
 * and a stream of terminal lines want completely different treatment: one
 * needs gutters, signs and per-line tinting, the other needs a monospaced
 * block that wraps nowhere and scrolls sideways. Rendering both as generic
 * text is the main reason tool output reads as debug spew.
 */
enum class ToolDetailKind {
    /** Before/after content: render a unified diff. */
    Diff,

    /** File contents or a code result: monospaced, horizontally scrollable. */
    Code,

    /** A directory listing or search hits: one entry per line, path-styled. */
    Listing,

    /** Shell output: monospaced, preserves blank lines, no markdown. */
    Terminal,

    /** Prose the model produced for the user to read: render as markdown. */
    Prose
}

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
    val subjectIsPath: Boolean = true,
    /** How this tool's detail should be rendered when expanded. */
    val detail: ToolDetailKind = ToolDetailKind.Terminal
)

object ToolPresentations {

    private val map: Map<String, ToolPresentation> = mapOf(
        "search_project" to ToolPresentation(
            "Searching the project", "Searched the project", Icons.Outlined.BugReport,
            subjectIsPath = false, detail = ToolDetailKind.Listing
        ),
        "open_pull_request" to ToolPresentation(
            "Opening a pull request", "Opened a pull request", Icons.AutoMirrored.Outlined.MergeType,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),
        "docs_lookup" to ToolPresentation(
            "Checking the docs", "Checked the docs", Icons.Outlined.BugReport,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),
        "screenshot_preview" to ToolPresentation(
            "Looking at the preview", "Looked at the preview", Icons.Outlined.BugReport,
            subjectIsPath = true, detail = ToolDetailKind.Terminal
        ),
        "audit_page" to ToolPresentation(
            "Auditing", "Audited", Icons.Outlined.BugReport,
            subjectIsPath = true, detail = ToolDetailKind.Terminal
        ),
        "get_preview_errors" to ToolPresentation(
            "Checking preview", "Checked preview", Icons.Outlined.BugReport,
            subjectIsPath = false, detail = ToolDetailKind.Terminal
        ),

        // --- Reading ------------------------------------------------------
        "read_file" to ToolPresentation(
            "Reading", "Read", Icons.Outlined.Description, detail = ToolDetailKind.Code
        ),
        "read_files" to ToolPresentation(
            "Reading", "Read", Icons.Outlined.Description, detail = ToolDetailKind.Code
        ),
        "list_files" to ToolPresentation(
            "Listing", "Listed", Icons.Outlined.FolderOpen, detail = ToolDetailKind.Listing
        ),
        "list_directory" to ToolPresentation(
            "Listing", "Listed", Icons.Outlined.FolderOpen, detail = ToolDetailKind.Listing
        ),
        "project_tree" to ToolPresentation(
            "Mapping", "Mapped", Icons.AutoMirrored.Outlined.ListAlt,
            detail = ToolDetailKind.Listing
        ),

        // --- Writing ------------------------------------------------------
        "write_file" to ToolPresentation(
            "Writing", "Wrote", Icons.Outlined.EditNote, detail = ToolDetailKind.Diff
        ),
        "create_file" to ToolPresentation(
            "Creating", "Created", Icons.Outlined.EditNote, detail = ToolDetailKind.Diff
        ),
        "edit_file" to ToolPresentation(
            "Editing", "Edited", Icons.Outlined.Difference, detail = ToolDetailKind.Diff
        ),
        "search_replace" to ToolPresentation(
            "Replacing in", "Replaced in", Icons.Outlined.Difference,
            detail = ToolDetailKind.Diff
        ),
        "patch_file" to ToolPresentation(
            "Patching", "Patched", Icons.Outlined.Difference, detail = ToolDetailKind.Diff
        ),
        "apply_patch" to ToolPresentation(
            "Applying patch to", "Patched", Icons.AutoMirrored.Outlined.MergeType,
            detail = ToolDetailKind.Diff
        ),
        "append_file" to ToolPresentation(
            "Appending to", "Appended to", Icons.Outlined.EditNote,
            detail = ToolDetailKind.Diff
        ),

        // --- Moving things around -----------------------------------------
        "delete_file" to ToolPresentation("Deleting", "Deleted", Icons.Outlined.Delete),
        "create_folder" to ToolPresentation(
            "Creating folder", "Created folder", Icons.Outlined.CreateNewFolder
        ),
        "rename_file" to ToolPresentation(
            "Renaming", "Renamed", Icons.Outlined.DriveFileRenameOutline
        ),
        "copy_file" to ToolPresentation("Copying", "Copied", Icons.Outlined.ContentCopy),
        "move_file" to ToolPresentation("Moving", "Moved", Icons.AutoMirrored.Outlined.DriveFileMove),

        // --- Searching ----------------------------------------------------
        "search_files" to ToolPresentation(
            "Searching", "Searched", Icons.Outlined.FindInPage, detail = ToolDetailKind.Listing
        ),
        "grep" to ToolPresentation(
            "Grepping for", "Found", Icons.AutoMirrored.Outlined.ManageSearch,
            subjectIsPath = false, detail = ToolDetailKind.Listing
        ),
        "glob" to ToolPresentation(
            "Matching", "Matched", Icons.AutoMirrored.Outlined.ManageSearch,
            subjectIsPath = false, detail = ToolDetailKind.Listing
        ),
        "web_search" to ToolPresentation(
            "Searching the web for", "Searched the web for", Icons.Outlined.TravelExplore,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),
        "fetch_url" to ToolPresentation(
            "Fetching", "Fetched", Icons.Outlined.Link,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),
        "http_request" to ToolPresentation(
            "Requesting", "Requested", Icons.Outlined.CloudDownload,
            subjectIsPath = false, detail = ToolDetailKind.Code
        ),

        // --- Running ------------------------------------------------------
        "run_command" to ToolPresentation(
            "Running", "Ran", Icons.Outlined.Terminal,
            subjectIsPath = false, detail = ToolDetailKind.Terminal
        ),
        "run_tests" to ToolPresentation(
            "Testing", "Tested", Icons.Outlined.Troubleshoot,
            subjectIsPath = false, detail = ToolDetailKind.Terminal
        ),
        "format_code" to ToolPresentation(
            "Formatting", "Formatted", Icons.Outlined.DataObject, detail = ToolDetailKind.Diff
        ),

        // --- Memory -------------------------------------------------------
        "save_memory" to ToolPresentation(
            "Saving note", "Noted", Icons.Outlined.Memory,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),
        "recall_memory" to ToolPresentation(
            "Recalling", "Recalled", Icons.Outlined.Memory,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),
        "list_memories" to ToolPresentation(
            "Reading notes", "Read notes", Icons.Outlined.Memory,
            subjectIsPath = false, detail = ToolDetailKind.Listing
        ),

        // --- Coordination -------------------------------------------------
        "spawn_subagent" to ToolPresentation(
            "Delegating", "Delegated", Icons.AutoMirrored.Outlined.CallSplit,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),
        "update_plan" to ToolPresentation(
            "Updating plan", "Updated plan", Icons.Outlined.Checklist,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),
        "request_approval" to ToolPresentation(
            "Asking you", "Asked you", Icons.AutoMirrored.Outlined.LiveHelp,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),

        // --- Assets -------------------------------------------------------
        "generate_image" to ToolPresentation(
            "Generating image", "Generated image", Icons.Outlined.Image,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        )
    )

    /**
     * Keyword fallbacks, longest first.
     *
     * A single generic sparkle icon for every unmapped tool was the worst part
     * of the old rendering: it told the user nothing, and because unmapped
     * tools are exactly the ones they have not seen before, it withheld the
     * information precisely where it was most needed. Matching on the verb in
     * the name recovers most of it -- anything containing "search" is a search,
     * whatever else it does.
     */
    private val keywordIcons: List<Pair<String, ToolPresentation>> = listOf(
        "replace" to ToolPresentation(
            "Replacing in", "Replaced in", Icons.Outlined.Difference,
            detail = ToolDetailKind.Diff
        ),
        "command" to ToolPresentation(
            "Running", "Ran", Icons.Outlined.Terminal,
            subjectIsPath = false, detail = ToolDetailKind.Terminal
        ),
        "search" to ToolPresentation(
            "Searching", "Searched", Icons.AutoMirrored.Outlined.ManageSearch,
            subjectIsPath = false, detail = ToolDetailKind.Listing
        ),
        "delete" to ToolPresentation("Deleting", "Deleted", Icons.Outlined.Delete),
        "rename" to ToolPresentation(
            "Renaming", "Renamed", Icons.Outlined.DriveFileRenameOutline
        ),
        "folder" to ToolPresentation(
            "Listing", "Listed", Icons.Outlined.FolderOpen, detail = ToolDetailKind.Listing
        ),
        "memory" to ToolPresentation(
            "Recalling", "Recalled", Icons.Outlined.Memory,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),
        "shell" to ToolPresentation(
            "Running", "Ran", Icons.Outlined.Terminal,
            subjectIsPath = false, detail = ToolDetailKind.Terminal
        ),
        "patch" to ToolPresentation(
            "Patching", "Patched", Icons.Outlined.Difference, detail = ToolDetailKind.Diff
        ),
        "fetch" to ToolPresentation(
            "Fetching", "Fetched", Icons.Outlined.Link,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),
        "write" to ToolPresentation(
            "Writing", "Wrote", Icons.Outlined.EditNote, detail = ToolDetailKind.Diff
        ),
        "list" to ToolPresentation(
            "Listing", "Listed", Icons.AutoMirrored.Outlined.ListAlt,
            detail = ToolDetailKind.Listing
        ),
        "grep" to ToolPresentation(
            "Grepping for", "Found", Icons.AutoMirrored.Outlined.ManageSearch,
            subjectIsPath = false, detail = ToolDetailKind.Listing
        ),
        "edit" to ToolPresentation(
            "Editing", "Edited", Icons.Outlined.Difference, detail = ToolDetailKind.Diff
        ),
        "diff" to ToolPresentation(
            "Diffing", "Diffed", Icons.Outlined.Difference, detail = ToolDetailKind.Diff
        ),
        "move" to ToolPresentation("Moving", "Moved", Icons.AutoMirrored.Outlined.DriveFileMove),
        "copy" to ToolPresentation("Copying", "Copied", Icons.Outlined.ContentCopy),
        "test" to ToolPresentation(
            "Testing", "Tested", Icons.Outlined.Troubleshoot,
            subjectIsPath = false, detail = ToolDetailKind.Terminal
        ),
        "plan" to ToolPresentation(
            "Updating plan", "Updated plan", Icons.Outlined.Checklist,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),
        "http" to ToolPresentation(
            "Requesting", "Requested", Icons.Outlined.CloudDownload,
            subjectIsPath = false, detail = ToolDetailKind.Code
        ),
        "json" to ToolPresentation(
            "Parsing", "Parsed", Icons.Outlined.DataObject,
            subjectIsPath = false, detail = ToolDetailKind.Code
        ),
        "web" to ToolPresentation(
            "Searching the web", "Searched the web", Icons.Outlined.TravelExplore,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        ),
        "run" to ToolPresentation(
            "Running", "Ran", Icons.Outlined.Terminal,
            subjectIsPath = false, detail = ToolDetailKind.Terminal
        ),
        "read" to ToolPresentation(
            "Reading", "Read", Icons.Outlined.Description, detail = ToolDetailKind.Code
        ),
        "file" to ToolPresentation(
            "Working on", "Updated", Icons.Outlined.Description, detail = ToolDetailKind.Code
        ),
        "url" to ToolPresentation(
            "Fetching", "Fetched", Icons.Outlined.Link,
            subjectIsPath = false, detail = ToolDetailKind.Prose
        )
    ).sortedByDescending { it.first.length }

    /**
     * Presentation for a tool name.
     *
     * Three tiers: an exact entry, then a keyword in the name, then a
     * humanised form of the name itself. The last tier still reads as prose
     * ("run_lighthouse" becomes "Run lighthouse") rather than as an
     * identifier, and keeps whatever icon the keyword tier inferred.
     */
    operator fun get(toolName: String): ToolPresentation {
        val key = toolName.lowercase().trim()
        map[key]?.let { return it }

        // Tool names arrive both as identifiers (`write_file`) and as the
        // humanised headings the model prints (`Write File`), because the
        // transcript parser reads the latter. Normalising both to the same
        // shape means one table serves both.
        val normalised = key.replace(' ', '_').replace('-', '_')
        map[normalised]?.let { return it }

        val words = toolName.replace('_', ' ').replace('-', ' ').trim()
        val humanised = words.replaceFirstChar { it.uppercase() }

        keywordIcons.firstOrNull { (keyword, _) -> normalised.contains(keyword) }
            ?.let { (_, template) ->
                return template.copy(running = humanised, done = humanised)
            }

        return ToolPresentation(
            running = humanised,
            done = humanised,
            // A gear, not a sparkle: this is machinery doing something
            // specific, and sparkle has come to mean "generated by AI", which
            // is true of every row here and so distinguishes nothing.
            icon = Icons.Outlined.DataObject,
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
