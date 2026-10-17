package com.oro.app.data.ai.context

import java.io.File

/**
 * Standing instructions a project carries for its agent, read from files at
 * the project root -- the convention opencode, Codex and Claude Code share
 * (`AGENTS.md`, `CLAUDE.md`), plus Oro's own `ORO.md`, which wins if present.
 *
 * Read once per run, capped, and placed after the fixed part of the system
 * prompt so it does not disturb the cacheable prefix.
 */
object ProjectInstructions {

    /** Checked in order; every file found is included, ORO.md first. */
    val FILE_NAMES = listOf("ORO.md", "AGENTS.md", "CLAUDE.md", ".oro/instructions.md")

    private const val MAX_CHARS = 12_000

    data class Loaded(val files: List<String>, val text: String) {
        val isEmpty: Boolean get() = text.isBlank()
    }

    fun load(projectRoot: File): Loaded {
        val found = FILE_NAMES.mapNotNull { name ->
            val f = File(projectRoot, name)
            if (f.isFile && f.length() in 1..(MAX_CHARS * 4L)) name to runCatching { f.readText() }.getOrNull() else null
        }.filter { !it.second.isNullOrBlank() }
        if (found.isEmpty()) return Loaded(emptyList(), "")
        var budget = MAX_CHARS
        val text = buildString {
            for ((name, body) in found) {
                if (budget <= 0) break
                val b = body!!.trim()
                val part = if (b.length > budget) b.take(budget) + "\n[... truncated]" else b
                append("## From ").append(name).append('\n').append(part).append("\n\n")
                budget -= part.length
            }
        }.trim()
        return Loaded(found.map { it.first }, text)
    }

    /** The system-prompt section, or "" when the project has none. */
    fun section(loaded: Loaded): String =
        if (loaded.isEmpty) "" else "\n\n# Project instructions\n" +
            "The project's own instructions. Follow them; they override general guidance above " +
            "when they conflict, but never the user's explicit request.\n\n" + loaded.text

    /** Starter file written by the "Create ORO.md" action. */
    fun template(projectName: String): String = """
        # $projectName — notes for Orobi

        ## What this project is
        - (One or two lines: what the site/app is and who it is for.)

        ## Conventions
        - Styling: (e.g. Tailwind via CDN / plain CSS with custom properties)
        - Structure: (e.g. one page per section in /pages, shared parts in /partials)
        - Tone of copy: (e.g. warm, short sentences)

        ## Do / don't
        - Do keep pages working offline.
        - Don't add build tools or npm packages.
    """.trimIndent() + "\n"
}
