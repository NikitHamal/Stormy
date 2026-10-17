package com.oro.app.data.ai.tools

import android.util.Log
import java.io.File

/**
 * The single place that decides whether an AI tool call may touch a path.
 *
 * There were two independent implementations of this -- one in
 * [JsonParserUtils] and one in [ToolArgumentValidator] -- which is a bad shape
 * for a security check: a rule added to one is silently absent from the other,
 * and neither caller can tell which protections it actually has. Both now
 * delegate here.
 *
 * Two rules:
 *
 *  1. **Stay inside the project.** Compared on canonical paths, so `..`,
 *     symlinks and absolute paths all resolve before the check rather than
 *     being pattern-matched beforehand.
 *  2. **`.oro/` is off limits.** That directory is the app's own workspace --
 *     agent memory, raw SSE transcripts, debug logs. Letting the agent read it
 *     feeds its own prior transcripts back to it as if they were project
 *     content, and letting it write there lets a model edit the record of what
 *     it did. Neither is something a user asked for when they said "build me a
 *     website".
 */
object AgentPathPolicy {

    private const val TAG = "AgentPathPolicy"

    /** Directories an agent may never read from or write to, project-relative. */
    val RESERVED_DIRECTORIES = setOf(".oro", ".git")

    /** Why a path was refused, or null when it is allowed. */
    sealed interface Denial {
        val message: String

        data class OutsideProject(override val message: String) : Denial
        data class Reserved(override val message: String) : Denial
        data class Malformed(override val message: String) : Denial
    }

    /**
     * Check [path] (project-relative or absolute) against both rules.
     *
     * @return null when the path may be used, otherwise the reason it may not.
     */
    fun check(path: String, projectRoot: File?): Denial? {
        if (path.contains('\u0000')) {
            return Denial.Malformed("Path contains null bytes")
        }
        if (projectRoot == null) return null

        val canonicalRoot: String
        val canonicalTarget: String
        try {
            canonicalRoot = projectRoot.canonicalFile.path
            val target = File(path).let { if (it.isAbsolute) it else File(projectRoot, path) }
            canonicalTarget = target.canonicalFile.path
        } catch (e: Exception) {
            Log.w(TAG, "Could not canonicalise path: ${e.message}")
            return Denial.Malformed("Invalid path format")
        }

        // Compare against root + separator so that a sibling directory sharing
        // a name prefix ("/projects/site-backup" vs "/projects/site") is not
        // mistaken for a child.
        if (canonicalTarget != canonicalRoot &&
            !canonicalTarget.startsWith(canonicalRoot + File.separator)
        ) {
            Log.w(TAG, "Blocked path outside project root: $path")
            return Denial.OutsideProject("Path escapes the project directory")
        }

        val relative = canonicalTarget.removePrefix(canonicalRoot)
            .trimStart(File.separatorChar)
        val firstSegment = relative.substringBefore(File.separatorChar)
        if (firstSegment in RESERVED_DIRECTORIES) {
            Log.w(TAG, "Blocked reserved directory access: $path")
            return Denial.Reserved(
                "'$firstSegment/' is reserved for the app and is not accessible to tools"
            )
        }

        return null
    }

    /**
     * Argument names that carry a filesystem path across the tool set.
     *
     * Checked by name rather than per-tool, so a newly added tool is covered
     * the moment it uses a conventional parameter name -- the failure mode of
     * a per-tool allowlist is that the one tool somebody forgot is the one
     * that leaks.
     */
    private val PATH_ARGUMENT_NAMES = setOf(
        "path", "file_path", "filepath", "filename", "file",
        "directory", "dir", "folder", "folder_path", "directory_path",
        "source", "source_path", "src",
        "destination", "destination_path", "dest", "target", "target_path",
        "old_path", "new_path", "from", "to"
    )

    /**
     * Inspect every path-shaped argument of a tool call and return the first
     * refusal reason, or null if all of them are acceptable.
     */
    fun denyIfAnyPathBlocked(
        arguments: kotlinx.serialization.json.JsonObject,
        projectRoot: File?
    ): String? {
        for ((name, element) in arguments) {
            if (name.lowercase() !in PATH_ARGUMENT_NAMES) continue
            val value = (element as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeIf { it.isString }?.content
                ?: continue
            if (value.isBlank()) continue
            check(value, projectRoot)?.let { return it.message }
        }
        return null
    }

    /** Convenience boolean form for call sites that only need allow/deny. */
    fun isAllowed(path: String, projectRoot: File?): Boolean = check(path, projectRoot) == null

    /**
     * Filter entries that must not be shown in a listing.
     *
     * Directory listings need this as much as reads do: an agent that can see
     * `.oro/raw_sse.log` in a tree will ask to read it.
     */
    fun isVisible(name: String): Boolean = name !in RESERVED_DIRECTORIES
}
