package com.oro.app.data.ai.tools

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.Tool
import com.oro.app.data.ai.agents.DelegationTools
import com.oro.app.data.ai.agents.ToolCapability

/**
 * The tool set Orobi is given in agent mode.
 *
 * ### Why a curated set
 *
 * The agent used to be sent ~70 tools (five memory tools, three todo tools,
 * eleven git tools, "generate_tests", "security_scan", ...). Every schema is
 * paid for in context on every request, and a long menu of overlapping tools
 * measurably degrades tool choice: models picked `append_to_file` to edit the
 * middle of a file, or `search_replace` across the project for a one-line fix.
 * This is the set modern coding agents converge on (Claude Code, opencode,
 * Codex CLI): read with line ranges, exact-string edits with a uniqueness
 * check, glob/grep, a plan, a way to ask the user, and fetch.
 *
 * Older tool names are still executed when a model emits them (see
 * [ToolAliases]); they are just no longer advertised.
 *
 * Schemas are written out as JSON Schema literals: nested arrays/objects and
 * integer types cannot be expressed by the old flat string-only builder, and
 * that builder is why `insert_at_line` took its line number as a string.
 */
object AgentTools {

    private fun tool(name: String, description: String, schema: String) = Tool(
        type = "function",
        function = FunctionDefinition(
            name = name,
            description = description.trimIndent(),
            parameters = AiHttp.json.parseToJsonElement(schema.trimIndent())
        )
    )

    val READ_FILE = tool(
        "read_file",
        """
        Read a text file from the project. Output is numbered "<line>|<text>"
        (the line-number prefix is not part of the file). Reads up to 2000
        lines from `offset`; for long files read the part you need with
        offset/limit. Always read a file before editing it.
        """,
        """
        {"type":"object","properties":{
          "path":{"type":"string","description":"File path relative to the project root, e.g. \"index.html\" or \"css/style.css\"."},
          "offset":{"type":"integer","description":"1-based line to start from. Default 1."},
          "limit":{"type":"integer","description":"Maximum number of lines to return. Default 2000."}
        },"required":["path"]}
        """
    )

    val WRITE_FILE = tool(
        "write_file",
        """
        Create a new file, or completely replace an existing one. Parent
        folders are created automatically. Prefer edit_file for changes to an
        existing file; only rewrite a whole file when most of it changes. The
        content is the literal file text, never JSON-encoded structure.
        """,
        """
        {"type":"object","properties":{
          "path":{"type":"string","description":"File path relative to the project root."},
          "content":{"type":"string","description":"The complete file contents."}
        },"required":["path","content"]}
        """
    )

    val EDIT_FILE = tool(
        "edit_file",
        """
        Replace an exact string in a file. `old_string` must match the file
        text exactly (whitespace and indentation included, without read_file's
        line-number prefixes) and must be unique in the file unless
        replace_all is true; include a few surrounding lines to make it unique.
        To create a new file, use write_file.
        """,
        """
        {"type":"object","properties":{
          "path":{"type":"string","description":"File path relative to the project root."},
          "old_string":{"type":"string","description":"Exact text to replace."},
          "new_string":{"type":"string","description":"Replacement text. Must differ from old_string."},
          "replace_all":{"type":"boolean","description":"Replace every occurrence instead of requiring a unique match. Default false."}
        },"required":["path","old_string","new_string"]}
        """
    )

    val MULTI_EDIT = tool(
        "multi_edit",
        """
        Apply several edit_file-style replacements to ONE file, in order,
        atomically: if any edit fails, none are applied. Each edit sees the
        result of the previous one. Use this instead of several edit_file calls
        on the same file.
        """,
        """
        {"type":"object","properties":{
          "path":{"type":"string","description":"File path relative to the project root."},
          "edits":{"type":"array","description":"Edits to apply in order.","items":{
            "type":"object","properties":{
              "old_string":{"type":"string","description":"Exact text to replace."},
              "new_string":{"type":"string","description":"Replacement text."},
              "replace_all":{"type":"boolean","description":"Replace every occurrence. Default false."}
            },"required":["old_string","new_string"]}}
        },"required":["path","edits"]}
        """
    )

    val LIST_FILES = tool(
        "list_files",
        """
        List files and folders as a tree. Folders end with "/". Reserved app
        folders are hidden.
        """,
        """
        {"type":"object","properties":{
          "path":{"type":"string","description":"Folder relative to the project root. Default \".\" (the root)."},
          "depth":{"type":"integer","description":"How many levels deep to list. Default 3, max 8."}
        },"required":[]}
        """
    )

    val GLOB = tool(
        "glob",
        """
        Find files by name pattern, e.g. "**/*.css", "src/**/*.{js,ts}",
        "*.html". Returns matching paths, most recently modified first.
        """,
        """
        {"type":"object","properties":{
          "pattern":{"type":"string","description":"Glob pattern relative to `path`."},
          "path":{"type":"string","description":"Folder to search from. Default the project root."}
        },"required":["pattern"]}
        """
    )

    val GREP = tool(
        "grep",
        """
        Search file contents with a regular expression (Java/Kotlin regex
        syntax). Returns "path:line: text" for each match. Use `include` to
        restrict to files matching a glob, e.g. "*.js".
        """,
        """
        {"type":"object","properties":{
          "pattern":{"type":"string","description":"Regular expression to search for."},
          "path":{"type":"string","description":"File or folder to search. Default the project root."},
          "include":{"type":"string","description":"Only search files whose path matches this glob, e.g. \"*.{html,css}\"."},
          "ignore_case":{"type":"boolean","description":"Case-insensitive match. Default false."},
          "literal":{"type":"boolean","description":"Treat pattern as plain text, not a regex. Default false."}
        },"required":["pattern"]}
        """
    )

    val DELETE_FILE = tool(
        "delete_file",
        """
        Delete a file, or a folder and everything in it. Ask with
        request_approval first before deleting work you did not create in this
        conversation.
        """,
        """
        {"type":"object","properties":{
          "path":{"type":"string","description":"File or folder path relative to the project root."}
        },"required":["path"]}
        """
    )

    val MOVE_FILE = tool(
        "move_file",
        """
        Move or rename a file or folder. Fails if the destination exists
        unless overwrite is true.
        """,
        """
        {"type":"object","properties":{
          "source":{"type":"string","description":"Current path relative to the project root."},
          "destination":{"type":"string","description":"New path relative to the project root."},
          "overwrite":{"type":"boolean","description":"Replace an existing destination file. Default false."}
        },"required":["source","destination"]}
        """
    )

    val ASK_USER = tool(
        "ask_user",
        """
        Ask the user one or more questions and wait for the answers. Only use
        this when you are genuinely blocked: the request is ambiguous in a way
        that changes the result, or you need information only the user has
        (brand name, copy, credentials). Do not ask for permission to proceed
        with ordinary work, and do not ask what you can find out by reading the
        project. Batch everything you need into ONE call (up to 6 questions);
        the user answers them in sequence. Give each question 2-5 short
        `options` when the answer is a choice (the user can always type their
        own answer); set multiSelect when several options can apply.
        """,
        """
        {"type":"object","properties":{
          "questions":{"type":"array","description":"1-6 questions, asked in order.","items":{"type":"object","properties":{
            "question":{"type":"string","description":"One clear, specific question, ending with '?'."},
            "header":{"type":"string","description":"A 1-3 word label for the question, e.g. 'Framework'."},
            "options":{"type":"array","items":{"type":"object","properties":{
              "label":{"type":"string","description":"The answer, a few words."},
              "description":{"type":"string","description":"Optional one-line explanation of this choice."}
            },"required":["label"]}},
            "multiSelect":{"type":"boolean","description":"Allow choosing several options. Default false."}
          },"required":["question"]}}
        },"required":["questions"]}
        """
    )

    val GET_PREVIEW_ERRORS = tool(
        "get_preview_errors",
        """
        Read the errors and warnings the live preview reported: JavaScript
        exceptions, console.error output and resources that failed to load,
        with file and line. Call it after changing scripts or markup the user
        is previewing, or when the user says something is broken.
        """,
        """
        {"type":"object","properties":{
          "include_warnings":{"type":"boolean","description":"Also list warnings. Default true."},
          "clear":{"type":"boolean","description":"Forget the reported problems after reading, so the next call shows only new ones. Default false."}
        }}
        """
    )

    val SCREENSHOT_PREVIEW = tool(
        "screenshot_preview",
        """
        Render a page of the project and look at it: returns layout facts
        (horizontal overflow, elements wider than the screen, small tap
        targets and text) and, for models that can see images, the screenshot.
        Use device "all" for a responsive check at phone, tablet and desktop
        widths. Do this after visual changes.
        """,
        """
        {"type":"object","properties":{
          "page":{"type":"string","description":"Page path relative to the project root. Default index.html."},
          "device":{"type":"string","enum":["phone","tablet","desktop","all"],"description":"Viewport. Default phone."}
        }}
        """
    )

    val AUDIT_PAGE = tool(
        "audit_page",
        """
        Run a Lighthouse-style audit of a page: accessibility (alt text,
        labels, contrast, heading order, tap targets), SEO (title, meta
        description, viewport, lang) and performance (image weight, render-
        blocking scripts, DOM size). Returns issues with selectors to fix.
        """,
        """
        {"type":"object","properties":{
          "page":{"type":"string","description":"Page path relative to the project root. Default index.html."}
        }}
        """
    )

    val WEB_FETCH = tool(
        "web_fetch",
        """
        Fetch a public http(s) URL and return its text (HTML is converted to
        readable text). Use it for documentation or to inspect a page the user
        mentions. GET only; output is truncated to max_chars.
        """,
        """
        {"type":"object","properties":{
          "url":{"type":"string","description":"Absolute http(s) URL."},
          "max_chars":{"type":"integer","description":"Maximum characters to return. Default 20000."}
        },"required":["url"]}
        """
    )

    val CHECK_SYNTAX = tool(
        "check_syntax",
        """
        Quick static check of an HTML, CSS, JS or JSON file for unbalanced
        tags/brackets and similar mistakes. Use it to verify files you wrote.
        """,
        """
        {"type":"object","properties":{
          "path":{"type":"string","description":"File path relative to the project root."}
        },"required":["path"]}
        """
    )

    val SAVE_MEMORY = tool(
        "save_memory",
        """
        Remember a durable fact about this project or the user's preferences
        for future conversations (e.g. "brand colour is #0f766e", "user wants
        no external CDNs"). Not for task progress.
        """,
        """
        {"type":"object","properties":{
          "key":{"type":"string","description":"Short identifier, e.g. \"brand_colors\"."},
          "value":{"type":"string","description":"The fact to remember."}
        },"required":["key","value"]}
        """
    )

    /** Tools for the main agent, in the order they are presented. */
    val all: List<Tool> = listOf(
        READ_FILE, LIST_FILES, GLOB, GREP,
        EDIT_FILE, MULTI_EDIT, WRITE_FILE, MOVE_FILE, DELETE_FILE,
        CHECK_SYNTAX, GET_PREVIEW_ERRORS, SCREENSHOT_PREVIEW, AUDIT_PAGE, WEB_FETCH,
        ASK_USER, SAVE_MEMORY
    )
    // run_command is deliberately not offered: on Android there is no node,
    // npm or python, so models burned turns trying to install packages, and
    // the shell's cp/mv/sed -i bypass both the project-path policy and undo.
    // It still executes if a model calls it (legacy), within the allowlist.

    /** Names of tools that never modify anything, safe to run concurrently. */
    val readOnly: Set<String> = setOf(
        "read_file", "list_files", "glob", "grep", "check_syntax", "web_fetch", "get_preview_errors", "screenshot_preview", "audit_page",
        // legacy names
        "search_files", "get_file_info", "recall_memory", "list_memories"
    )

    /** Capability a tool needs, for subagent permission filtering. */
    fun capabilityOf(name: String): ToolCapability? = when (ToolAliases.canonicalName(name)) {
        "read_file", "list_files", "glob", "grep", "check_syntax", "web_fetch" -> ToolCapability.READ
        "write_file", "edit_file", "multi_edit", "move_file", "delete_file" -> ToolCapability.WRITE
        "run_command" -> ToolCapability.EXECUTE
        "save_memory" -> ToolCapability.MEMORY
        "spawn_subagent" -> ToolCapability.DELEGATE
        else -> null
    }

    /**
     * Present-progressive label for the working indicator while [name] runs,
     * e.g. "Reading index.html".
     */
    fun activityLabel(name: String, subject: String?): String {
        val s = subject?.takeIf { it.isNotBlank() }?.let { shorten(it) }
        val verb = when (ToolAliases.canonicalName(name)) {
            "read_file" -> "Reading"
            "write_file" -> "Writing"
            "edit_file", "multi_edit" -> "Editing"
            "list_files" -> "Listing"
            "glob", "grep" -> "Searching"
            "delete_file" -> "Deleting"
            "move_file" -> "Moving"
            "check_syntax" -> "Checking"
            "web_fetch" -> "Fetching"
            "run_command" -> "Running"
            "save_memory" -> "Remembering"
            "spawn_subagent" -> "Delegating"
            "update_plan" -> "Planning"
            "ask_user" -> return "Waiting for your answer"
            "request_approval" -> return "Waiting for your approval"
            else -> return "Running ${name.replace('_', ' ')}"
        }
        return if (s != null) "$verb $s" else verb
    }

    private fun shorten(subject: String): String {
        val oneLine = subject.lineSequence().first().trim()
        return if (oneLine.length <= 40) oneLine else "…" + oneLine.takeLast(39)
    }

    /** Full set offered to the main agent, including orchestration tools. */
    fun forMainAgent(): List<Tool> = all + DelegationTools.all
}
