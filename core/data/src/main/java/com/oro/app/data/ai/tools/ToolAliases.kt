package com.oro.app.data.ai.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Maps tool names and argument names models commonly emit onto the
 * canonical [AgentTools] set.
 *
 * Models trained against other harnesses reach for those harnesses' names
 * (`str_replace`, `todowrite`, `search_files`), and older Oro conversations in
 * history still contain the legacy names. Executing those through the
 * canonical implementation is much better than answering "Unknown tool" and
 * spending a turn on the correction.
 */
object ToolAliases {

    private val nameAliases = mapOf(
        // edits
        "patch_file" to "edit_file",
        "str_replace" to "edit_file",
        "str_replace_editor" to "edit_file",
        "replace_in_file" to "edit_file",
        "edit" to "edit_file",
        "multiedit" to "multi_edit",
        "multi_edit_file" to "multi_edit",
        // reads / writes
        "read" to "read_file",
        "view" to "read_file",
        "cat" to "read_file",
        "write" to "write_file",
        "create_file" to "write_file",
        // listing / search
        "ls" to "list_files",
        "list" to "list_files",
        "list_dir" to "list_files",
        "list_directory" to "list_files",
        "print_tree" to "list_files",
        "tree" to "list_files",
        "open_file" to "read_file",
        "read_file_lines" to "read_file",
        "find_files" to "glob",
        "search_files" to "grep",
        "search" to "grep",
        "rg" to "grep",
        // moves
        "rename_file" to "move_file",
        "mv" to "move_file",
        "delete" to "delete_file",
        "remove_file" to "delete_file",
        // misc
        "fetch" to "web_fetch",
        "webfetch" to "web_fetch",
        "todowrite" to "update_plan",
        "todo_write" to "update_plan",
        "ask" to "ask_user",
        "question" to "ask_user",
        "bash" to "run_command",
        "shell" to "run_command"
    )

    /** Argument renames per canonical tool: alias -> canonical. */
    private val argAliases: Map<String, Map<String, String>> = mapOf(
        "edit_file" to mapOf(
            "old_content" to "old_string", "old_str" to "old_string", "old" to "old_string",
            "search" to "old_string", "find" to "old_string",
            "new_content" to "new_string", "new_str" to "new_string", "new" to "new_string",
            "replace" to "new_string", "replacement" to "new_string",
            "file_path" to "path", "filepath" to "path", "file" to "path", "filename" to "path"
        ),
        "multi_edit" to mapOf(
            "file_path" to "path", "filepath" to "path", "file" to "path"
        ),
        "read_file" to mapOf(
            "file_path" to "path", "filepath" to "path", "file" to "path", "filename" to "path",
            "start_line" to "offset", "line" to "offset", "line_start" to "offset"
        ),
        "write_file" to mapOf(
            "file_path" to "path", "filepath" to "path", "file" to "path", "filename" to "path",
            "text" to "content", "contents" to "content", "data" to "content"
        ),
        "list_files" to mapOf(
            "directory" to "path", "dir" to "path", "folder" to "path", "dir_path" to "path"
        ),
        "glob" to mapOf("glob" to "pattern", "directory" to "path", "dir" to "path"),
        "grep" to mapOf(
            "query" to "pattern", "regex" to "pattern", "search" to "pattern",
            "file_pattern" to "include", "glob" to "include", "directory" to "path"
        ),
        "move_file" to mapOf(
            "old_path" to "source", "source_path" to "source", "from" to "source", "src" to "source",
            "new_path" to "destination", "destination_path" to "destination", "to" to "destination",
            "dest" to "destination", "target" to "destination"
        ),
        "delete_file" to mapOf("file_path" to "path", "file" to "path"),
        "web_fetch" to mapOf("link" to "url", "uri" to "url"),
        "ask_user" to mapOf("prompt" to "question", "choices" to "options", "message" to "question")
    )

    fun canonicalName(name: String): String {
        // Namespaced names ("functions.read_file", gpt-oss's
        // "repo_browser.print_tree") keep only the last segment.
        val n = name.trim().lowercase().substringAfterLast('.')
        return nameAliases[n] ?: n
    }

    /**
     * Canonical name and arguments. `search_files` was a plain-text search, so
     * it maps to grep with `literal = true`.
     */
    fun normalise(name: String, args: JsonObject): Pair<String, JsonObject> {
        val raw = name.trim().lowercase()
        val canonical = canonicalName(name)
        val renames = argAliases[canonical].orEmpty()
        val out = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        for ((key, value) in args) {
            val target = renames[key] ?: key
            // Never let an alias overwrite an explicitly given canonical arg.
            if (target != key && args.containsKey(target)) continue
            out[target] = value
        }
        // read_file with an end line instead of a count.
        if (canonical == "read_file" && !out.containsKey("limit")) {
            val end = (args["line_end"] ?: args["end_line"])?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() }
            val start = (out["offset"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
            if (end != null && end >= start) out["limit"] = JsonPrimitive(end - start + 1)
            out.remove("line_end"); out.remove("end_line")
        }
        if (raw == "search_files" && !out.containsKey("literal")) {
            out["literal"] = JsonPrimitive(true)
            out["ignore_case"] = out["ignore_case"] ?: JsonPrimitive(true)
        }
        return canonical to JsonObject(out)
    }
}
