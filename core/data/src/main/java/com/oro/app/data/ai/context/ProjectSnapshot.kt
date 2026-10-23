package com.oro.app.data.ai.context

import java.io.File

/**
 * A small project's source, whole, for the run's context snapshot.
 *
 * A model with a large and fast context ([com.oro.app.data.ai.agent.ModelProfile.inlineProjectChars])
 * edits better and sooner when it starts with every file in view instead of
 * spending its first turns listing and reading them. All or nothing: a
 * snapshot that silently left files out would look complete and mislead, so a
 * project over the budget gets no snapshot (the file tree and list_files
 * remain).
 */
object ProjectSnapshot {

    private val SKIPPED_DIRS = setOf("node_modules", ".git", ".oro", ".gradle", "build", "dist", ".cache", "uploads", ".idea")
    private val TEXT_EXTENSIONS = setOf(
        "html", "htm", "css", "js", "mjs", "cjs", "ts", "tsx", "jsx", "json", "md", "txt", "svg", "xml",
        "vue", "svelte", "yml", "yaml", "toml", "webmanifest"
    )

    /** A single file larger than this is never inlined (minified bundles, data dumps). */
    private const val MAX_FILE_CHARS = 60_000

    /** The backend's db.json is shown while small (it tells the model the data's shape). */
    private const val DATA_FILE_CHARS = 8_000

    private val LOCK_FILES = setOf("package-lock.json", "yarn.lock", "pnpm-lock.yaml")

    /**
     * The section to add to the context, or "" when [budgetChars] is 0, the
     * project is empty, or its text does not fit.
     */
    fun inline(root: File, budgetChars: Int): String {
        if (budgetChars <= 0 || !root.isDirectory) return ""
        val files = textFiles(root)
        if (files.isEmpty()) return ""
        val parts = ArrayList<Pair<String, String>>()
        val left = ArrayList<String>()
        var total = 0
        for (f in files) {
            val path = f.relativeTo(root).invariantSeparatorsPath
            // Data, not source: the backend's store and lock files are named, not shown.
            if (path in LOCK_FILES || (path == "db.json" && f.length() > DATA_FILE_CHARS)) { left += path; continue }
            if (f.length() > MAX_FILE_CHARS * 4L) return ""
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            if (text.length > MAX_FILE_CHARS || text.contains('\u0000')) return ""
            total += text.length
            if (total > budgetChars) return ""
            parts += path to text
        }
        if (parts.isEmpty()) return ""
        return buildString {
            append("\n\n# Project files\n")
            append("Every file of the project as the task starts (").append(parts.size).append(" files). ")
            append("This is their current text: you have read them.")
            if (left.isNotEmpty()) append(" Not shown (data): ").append(left.joinToString(", ")).append('.')
            append('\n')
            parts.forEach { (path, text) ->
                append("\n## ").append(path).append("\n")
                append(fence(text)).append(languageOf(path)).append('\n')
                append(text.trimEnd()).append('\n')
                append(fence(text)).append('\n')
            }
        }
    }

    /** Project text files, sorted with index.html first, hidden and generated folders skipped. */
    internal fun textFiles(root: File): List<File> = root.walkTopDown()
        .onEnter { dir -> dir == root || (dir.name !in SKIPPED_DIRS && !dir.name.startsWith(".")) }
        .filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in TEXT_EXTENSIONS }
        .sortedWith(compareBy({ it.relativeTo(root).invariantSeparatorsPath != "index.html" }, { it.relativeTo(root).invariantSeparatorsPath }))
        .toList()

    /** A code fence longer than any backtick run inside [text], so the file never closes it early. */
    private fun fence(text: String): String {
        var longest = 0
        var run = 0
        for (c in text) {
            if (c == '`') { run++; if (run > longest) longest = run } else run = 0
        }
        return "`".repeat(maxOf(3, longest + 1))
    }

    private fun languageOf(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "htm", "html" -> "html"
        "js", "mjs", "cjs", "jsx" -> "js"
        "ts", "tsx" -> "ts"
        "md" -> "markdown"
        "yml", "yaml" -> "yaml"
        else -> path.substringAfterLast('.').lowercase()
    }
}
