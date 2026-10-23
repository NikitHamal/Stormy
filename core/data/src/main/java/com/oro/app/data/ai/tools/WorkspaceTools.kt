package com.oro.app.data.ai.tools

import com.oro.app.data.ai.AiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** One file change made by a tool, reported for undo/redo and the file tree. */
data class FileChange(
    val path: String,
    val type: FileChangeType,
    val oldContent: String?,
    val newContent: String?
)

/** A tool result plus the file changes it made. */
data class ToolOutcome(
    val result: ToolResult,
    val changes: List<FileChange> = emptyList()
)

/**
 * The file-system tools, implemented directly against the project root.
 *
 * Plain `java.io.File`, no repository or Android types, so every behaviour the
 * model depends on (line ranges, uniqueness checks, glob semantics, error
 * text) is covered by JVM unit tests against a temp directory.
 *
 * Every path goes through [AgentPathPolicy] here as well as in the executor:
 * the policy is the security boundary, and checking at the point of use means
 * a new tool cannot forget it.
 */
class WorkspaceTools(private val root: File) {

    companion object {
        const val DEFAULT_READ_LIMIT = 2_000
        const val MAX_LINE_CHARS = 2_000
        const val MAX_LIST_ENTRIES = 400

        /** Budget for list_files(symbols=true) outlines, in characters. */
        const val MAX_OUTLINE_CHARS = 16_000
        const val MAX_GLOB_RESULTS = 200
        const val MAX_GREP_MATCHES = 200
        const val MAX_SEARCH_FILE_BYTES = 1_000_000L
        const val DEFAULT_FETCH_CHARS = 20_000
        /** Files one read_file call may return with `paths`. */
        const val MAX_BATCH_READ = 8

        /** A batch read stops adding files past this many characters (tool results are capped at 60k). */
        const val MAX_BATCH_CHARS = 50_000
        const val MAX_GREP_CONTEXT = 5

        /** Folders never worth walking into for search. */
        private val SKIPPED_DIRS = setOf("node_modules", ".git", ".oro", ".gradle", "build", "dist", ".cache")
    }

    // ------------------------------------------------------------------ paths

    private class PathError(message: String) : Exception(message)

    private fun resolve(path: String?, argName: String = "path"): File {
        val p = path?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw PathError("Missing required argument: $argName")
        AgentPathPolicy.check(p, root)?.let { throw PathError(it.message) }
        val f = File(p).let { if (it.isAbsolute) it else File(root, p) }
        return f.canonicalFile
    }

    private fun resolveOrRoot(path: String?): File =
        if (path.isNullOrBlank() || path.trim() == "." || path.trim() == "/") root.canonicalFile
        else resolve(path)

    private fun rel(file: File): String =
        file.canonicalPath.removePrefix(root.canonicalPath).trimStart(File.separatorChar)
            .replace(File.separatorChar, '/').ifEmpty { "." }

    private inline fun guarded(block: () -> ToolOutcome): ToolOutcome = try {
        block()
    } catch (e: PathError) {
        fail(e.message ?: "Invalid path")
    } catch (e: java.io.IOException) {
        fail("File system error: ${e.message}")
    }

    private fun ok(output: String, changes: List<FileChange> = emptyList()) =
        ToolOutcome(ToolResult(true, output), changes)

    private fun fail(message: String) = ToolOutcome(ToolResult(false, "", message))

    // -------------------------------------------------------------- arguments

    private fun JsonObject.str(key: String): String? = getStringArg(key)
    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.trim()?.toDoubleOrNull()?.toInt() }
    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.equals("true", true) } ?: false

    private fun isBinary(file: File): Boolean = file.inputStream().use { input ->
        val buf = ByteArray(8_000)
        val n = input.read(buf)
        (0 until n.coerceAtLeast(0)).any { buf[it] == 0.toByte() }
    }

    // ------------------------------------------------------------------ read

    fun readFile(args: JsonObject): ToolOutcome = guarded {
        val first = args.str("path")?.trim()?.takeIf { it.isNotEmpty() }
        val all = (listOfNotNull(first) + pathList(args)).distinct()
        if (all.size > 1) {
            // An offset belongs to one file: in a batch it would silently be ignored.
            if ((args.int("offset") ?: 1) > 1) {
                return@guarded fail("offset applies to one file. Call read_file with a single path and offset/limit, or drop offset to read the start of each file.")
            }
            return@guarded readMany(all, args)
        }
        readOne(all.firstOrNull(), args.int("offset"), args.int("limit"))
    }

    /** `paths` as a JSON array, or one string of paths separated by commas or newlines. */
    private fun pathList(args: JsonObject): List<String> {
        val raw = args["paths"] ?: return emptyList()
        val items = when (raw) {
            is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> raw.contentOrNull.orEmpty().let { text ->
                runCatching { (AiHttp.json.parseToJsonElement(text) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }
                    .getOrNull() ?: text.split(',', '\n')
            }
            else -> emptyList()
        }
        return items.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /**
     * Several files in one call: one round trip instead of one per file. Each
     * file keeps the single-read format; a missing file is reported inline
     * without failing the others.
     */
    private fun readMany(paths: List<String>, args: JsonObject): ToolOutcome {
        val limit = args.int("limit")
        val candidates = paths.take(MAX_BATCH_READ)
        val parts = mutableListOf<String>()
        var size = 0
        var anyOk = false
        // Stop before the result outgrows what one tool result may hold, and
        // say which files are left: a batch cut mid-file is useless to retry.
        val unread = mutableListOf<String>()
        for (p in candidates) {
            if (unread.isNotEmpty()) { unread += p; continue }
            val one = guarded { readOne(p, null, limit) }.result
            val part = if (one.success) one.output else "Error reading $p: ${one.error ?: "failed"}"
            if (parts.isNotEmpty() && size + part.length > MAX_BATCH_CHARS) {
                unread += p
                continue
            }
            parts += part
            size += part.length + 2
            if (one.success) anyOk = true
        }
        val notes = buildList {
            if (unread.isNotEmpty()) add("[Not read, to keep this result small: ${unread.joinToString(", ")}. Read them in another call.]")
            if (paths.size > candidates.size) add("[${paths.size - candidates.size} more paths not read; at most $MAX_BATCH_READ per call.]")
        }
        val body = (parts + notes).joinToString("\n\n")
        return if (anyOk) ok(body) else fail(body)
    }

    private fun readOne(rawPath: String?, offsetArg: Int?, limitArg: Int?): ToolOutcome {
        val file = resolve(rawPath)
        val path = rel(file)
        if (!file.exists()) return fail(notFound(path))
        if (file.isDirectory) return fail("$path is a folder. Use list_files to see its contents.")
        if (isBinary(file)) return ok("$path is a binary file (${file.length()} bytes); it cannot be shown as text.")

        val lines = file.readText().split("\n")
        // A trailing newline produces an empty last element that is not a line.
        val total = if (lines.size > 1 && lines.last().isEmpty()) lines.size - 1 else lines.size
        val offset = (offsetArg ?: 1).coerceAtLeast(1)
        val limit = (limitArg ?: DEFAULT_READ_LIMIT).coerceIn(1, 10_000)
        if (total == 0 || (total == 1 && lines[0].isEmpty())) return ok("$path is empty.")
        if (offset > total) return fail("offset $offset is past the end of $path, which has $total lines.")

        val end = (offset - 1 + limit).coerceAtMost(total)
        val width = end.toString().length
        val body = buildString {
            for (i in offset - 1 until end) {
                val line = lines[i].trimEnd('\r')
                append((i + 1).toString().padStart(width)).append('|')
                append(if (line.length > MAX_LINE_CHARS) line.take(MAX_LINE_CHARS) + "… [line truncated]" else line)
                append('\n')
            }
        }
        val header = if (offset == 1 && end == total) "File: $path ($total lines)\n"
        else "File: $path (lines $offset-$end of $total)\n"
        val footer = if (end < total) "\n[${total - end} more lines. Call read_file with offset=${end + 1} to continue.]" else ""
        return ok(header + body.trimEnd('\n') + footer)
    }

    // ----------------------------------------------------------------- write

    fun writeFile(args: JsonObject): ToolOutcome = guarded {
        val file = resolve(args.str("path"))
        val path = rel(file)
        val content = args.str("content") ?: return@guarded fail("Missing required argument: content")
        if (file.isDirectory) return@guarded fail("$path is a folder; give a file path.")
        val old = if (file.exists()) file.readText() else null
        if (args.bool("append")) return@guarded appendTo(file, path, old, content)
        if (old == content) return@guarded ok("$path already has exactly this content; nothing changed.")
        file.parentFile?.mkdirs()
        file.writeText(content)
        val lineCount = content.count { it == '\n' } + if (content.endsWith("\n") || content.isEmpty()) 0 else 1
        val type = if (old == null) FileChangeType.CREATED else FileChangeType.MODIFIED
        val verb = if (old == null) "Created" else "Overwrote"
        ok("$verb $path ($lineCount lines).\nFile: $path", listOf(FileChange(path, type, old, content)))
    }

    /**
     * The next part of a file written in parts. A part that does not start a
     * new line goes on a new line: the text protocol drops a value's trailing
     * newline, and gluing `// comment` onto the next part's first line would
     * silently comment it out.
     */
    private fun appendTo(file: File, path: String, old: String?, part: String): ToolOutcome {
        if (part.isEmpty()) return ok("Nothing to append to $path.")
        val before = old.orEmpty()
        val joiner = if (before.isNotEmpty() && !before.endsWith("\n") && !part.startsWith("\n")) "\n" else ""
        val updated = before + joiner + part
        file.parentFile?.mkdirs()
        file.writeText(updated)
        val added = part.count { it == '\n' } + if (part.endsWith("\n")) 0 else 1
        val total = updated.count { it == '\n' } + if (updated.endsWith("\n")) 0 else 1
        val type = if (old == null) FileChangeType.CREATED else FileChangeType.MODIFIED
        return ok(
            "Appended $added lines to $path (now $total lines).\nFile: $path",
            listOf(FileChange(path, type, old, updated))
        )
    }

    // ------------------------------------------------------------------ edit

    fun editFile(args: JsonObject): ToolOutcome = guarded {
        val file = resolve(args.str("path"))
        val path = rel(file)
        val old = args.str("old_string") ?: return@guarded fail("Missing required argument: old_string")
        val new = args.str("new_string") ?: return@guarded fail("Missing required argument: new_string")
        if (!file.exists()) {
            return@guarded fail(notFound(path) + " To create a new file use write_file.")
        }
        if (file.isDirectory) return@guarded fail("$path is a folder.")
        val before = file.readText()
        when (val r = TextEdits.apply(before, old, new, args.bool("replace_all"), path)) {
            is TextEdits.Result.Failed -> fail(r.message)
            is TextEdits.Result.Applied -> {
                file.writeText(r.content)
                val what = if (r.replacements == 1) "1 replacement" else "${r.replacements} replacements"
                ok(
                    "Edited $path ($what).\nFile: $path\n${TextEdits.miniDiff(old, new)}",
                    listOf(FileChange(path, FileChangeType.MODIFIED, before, r.content))
                )
            }
        }
    }

    fun multiEdit(args: JsonObject): ToolOutcome = guarded {
        val file = resolve(args.str("path"))
        val path = rel(file)
        val editsJson = args["edits"] as? JsonArray
            ?: (args.str("edits")?.let { runCatching { AiHttp.json.parseToJsonElement(it) as? JsonArray }.getOrNull() })
            ?: return@guarded fail("Missing required argument: edits (an array of {old_string, new_string}).")
        if (editsJson.isEmpty()) return@guarded fail("edits is empty.")
        if (!file.exists()) return@guarded fail(notFound(path) + " To create a new file use write_file.")
        val edits = editsJson.mapIndexed { i, e ->
            val o = e as? JsonObject ?: return@guarded fail("edits[$i] must be an object with old_string and new_string.")
            val (_, norm) = ToolAliases.normalise("edit_file", o)
            Triple(
                norm.str("old_string") ?: return@guarded fail("edits[$i] is missing old_string."),
                norm.str("new_string") ?: return@guarded fail("edits[$i] is missing new_string."),
                norm.bool("replace_all")
            )
        }
        val before = file.readText()
        when (val r = TextEdits.applyAll(before, edits, path)) {
            is TextEdits.Result.Failed -> fail(r.message)
            is TextEdits.Result.Applied -> {
                file.writeText(r.content)
                val diff = edits.joinToString("\n") { (o, n, _) -> TextEdits.miniDiff(o, n, maxLines = 15) }
                ok(
                    "Applied ${edits.size} edits to $path.\nFile: $path\n$diff",
                    listOf(FileChange(path, FileChangeType.MODIFIED, before, r.content))
                )
            }
        }
    }

    // ------------------------------------------------------------------ list

    fun listFiles(args: JsonObject): ToolOutcome = guarded {
        val dir = resolveOrRoot(args.str("path"))
        val path = rel(dir)
        if (!dir.exists()) return@guarded fail(notFound(path))
        if (!dir.isDirectory) return@guarded ok("$path is a file (${dir.length()} bytes).")
        val depth = (args.int("depth") ?: 3).coerceIn(1, 8)
        val symbols = args.bool("symbols")
        var count = 0
        var truncated = false
        var outlineChars = 0
        var outlinesCut = false
        val sb = StringBuilder()
        fun walk(d: File, level: Int) {
            val children = d.listFiles()?.filter { AgentPathPolicy.isVisible(it.name) }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
            for (c in children) {
                if (count >= MAX_LIST_ENTRIES) { truncated = true; return }
                count++
                val indent = "  ".repeat(level)
                if (c.isDirectory) {
                    if (c.name in SKIPPED_DIRS) {
                        sb.append(indent).append(c.name).append("/ (skipped)\n")
                    } else {
                        sb.append(indent).append(c.name).append("/\n")
                        if (level + 1 < depth) walk(c, level + 1)
                    }
                } else {
                    sb.append(indent).append(c.name)
                    if (symbols && !outlinesCut) {
                        val outline = ProjectOutline.of(c)
                        if (outline != null) {
                            if (outlineChars + outline.length > MAX_OUTLINE_CHARS) outlinesCut = true
                            else { sb.append(" — ").append(outline); outlineChars += outline.length }
                        }
                    }
                    sb.append('\n')
                }
            }
        }
        walk(dir, 0)
        if (count == 0) return@guarded ok("$path is empty.")
        val head = if (path == ".") "Project root:\n" else "$path/\n"
        ok(
            head + sb.toString().trimEnd() +
                (if (truncated) "\n[Listing truncated at $MAX_LIST_ENTRIES entries; list a subfolder.]" else "") +
                (if (outlinesCut) "\n[Outlines stop here to keep this short; list a subfolder with symbols=true for the rest.]" else "")
        )
    }

    // ------------------------------------------------------------ glob / grep

    private fun walkFiles(start: File, includeSkipped: Boolean = false): Sequence<File> =
        start.walkTopDown()
            .onEnter { d ->
                d == start || (AgentPathPolicy.isVisible(d.name) && (includeSkipped || d.name !in SKIPPED_DIRS))
            }
            .filter { it.isFile }

    fun glob(args: JsonObject): ToolOutcome = guarded {
        val pattern = args.str("pattern")?.trim()?.removePrefix("./")
            ?: return@guarded fail("Missing required argument: pattern")
        val base = resolveOrRoot(args.str("path"))
        if (!base.isDirectory) return@guarded fail("${rel(base)} is not a folder.")
        val regex = Glob.toRegex(pattern)
        val byName = !pattern.contains('/')
        val matches = walkFiles(base, includeSkipped = pattern.contains("node_modules"))
            .filter { f ->
                val relToBase = f.canonicalPath.removePrefix(base.canonicalPath).trimStart(File.separatorChar)
                    .replace(File.separatorChar, '/')
                regex.matches(relToBase) || (byName && regex.matches(f.name))
            }
            .sortedByDescending { it.lastModified() }
            .toList()
        if (matches.isEmpty()) return@guarded ok("No files match $pattern.")
        val shown = matches.take(MAX_GLOB_RESULTS).joinToString("\n") { rel(it) }
        ok(shown + if (matches.size > MAX_GLOB_RESULTS) "\n[${matches.size - MAX_GLOB_RESULTS} more not shown; narrow the pattern.]" else "")
    }

    fun grep(args: JsonObject): ToolOutcome = guarded {
        val pattern = args.str("pattern") ?: return@guarded fail("Missing required argument: pattern")
        if (pattern.isEmpty()) return@guarded fail("pattern is empty.")
        val base = resolveOrRoot(args.str("path"))
        if (!base.exists()) return@guarded fail(notFound(rel(base)))
        val options = if (args.bool("ignore_case")) setOf(RegexOption.IGNORE_CASE) else emptySet()
        var note = ""
        val regex = if (args.bool("literal")) {
            Regex(Regex.escape(pattern), options)
        } else {
            try {
                Regex(pattern, options)
            } catch (e: Exception) {
                note = "(pattern was not a valid regex, searched it as plain text)\n"
                Regex(Regex.escape(pattern), options)
            }
        }
        val include = args.str("include")?.takeIf { it.isNotBlank() }?.let { Glob.toRegex(it.removePrefix("./")) }
        val context = (args.int("context") ?: 0).coerceIn(0, MAX_GREP_CONTEXT)
        val files = if (base.isFile) sequenceOf(base) else walkFiles(base)
        val out = StringBuilder()
        var matches = 0
        var truncated = false
        fun clip(line: String) = line.trim().let { if (it.length > 200) it.take(200) + "…" else it }
        for (f in files) {
            if (include != null) {
                val r = rel(f)
                if (!include.matches(r) && !include.matches(f.name)) continue
            }
            if (f.length() > MAX_SEARCH_FILE_BYTES || isBinary(f)) continue
            if (context == 0) {
                f.useLines { lines ->
                    lines.forEachIndexed { i, line ->
                        if (truncated) return@forEachIndexed
                        if (regex.containsMatchIn(line)) {
                            if (matches >= MAX_GREP_MATCHES) { truncated = true; return@forEachIndexed }
                            matches++
                            out.append(rel(f)).append(':').append(i + 1).append(": ").append(clip(line)).append('\n')
                        }
                    }
                }
            } else {
                // Matches with surrounding lines, ripgrep style: "path:12: match",
                // "path-11- context", "--" between separate groups.
                val lines = f.readLines()
                val hits = lines.indices.filter { regex.containsMatchIn(lines[it]) }
                var lastPrinted = -2
                for (i in hits) {
                    if (matches >= MAX_GREP_MATCHES) { truncated = true; break }
                    matches++
                    val from = maxOf(0, i - context, lastPrinted + 1)
                    val to = minOf(lines.lastIndex, i + context)
                    if (lastPrinted >= 0 && from > lastPrinted + 1) out.append("--\n")
                    for (k in from..to) {
                        val sep = if (k == i || regex.containsMatchIn(lines[k])) ':' else '-'
                        out.append(rel(f)).append(sep).append(k + 1).append(sep).append(' ').append(clip(lines[k])).append('\n')
                    }
                    lastPrinted = maxOf(lastPrinted, to)
                }
                if (hits.isNotEmpty() && !truncated) out.append("--\n")
            }
            if (truncated) break
        }
        if (matches == 0) return@guarded ok(note + "No matches for $pattern.")
        ok(note + out.toString().trimEnd().removeSuffix("--").trimEnd() + if (truncated) "\n[Stopped at $MAX_GREP_MATCHES matches; narrow the search.]" else "")
    }

    // --------------------------------------------------------- delete / move

    fun deleteFile(args: JsonObject): ToolOutcome = guarded {
        val file = resolve(args.str("path"))
        val path = rel(file)
        if (file == root.canonicalFile) return@guarded fail("Refusing to delete the project root.")
        if (!file.exists()) return@guarded fail(notFound(path))
        val changes = if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.take(500).map { f ->
                FileChange(rel(f), FileChangeType.DELETED, runCatching { if (isBinary(f)) null else f.readText() }.getOrNull(), null)
            }.toList()
        } else {
            listOf(FileChange(path, FileChangeType.DELETED, if (isBinary(file)) null else file.readText(), null))
        }
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!deleted) return@guarded fail("Could not delete $path.")
        ok(if (changes.size > 1) "Deleted folder $path (${changes.size} files)." else "Deleted $path.", changes)
    }

    fun moveFile(args: JsonObject): ToolOutcome = guarded {
        val src = resolve(args.str("source"), "source")
        val dst = resolve(args.str("destination"), "destination")
        val from = rel(src)
        val to = rel(dst)
        if (!src.exists()) return@guarded fail(notFound(from))
        if (src == dst) return@guarded fail("source and destination are the same path.")
        if (dst.exists()) {
            if (!args.bool("overwrite") || dst.isDirectory || src.isDirectory) {
                return@guarded fail("$to already exists. Choose another destination, or set overwrite=true to replace a file.")
            }
            dst.delete()
        }
        dst.parentFile?.mkdirs()
        val moved = src.renameTo(dst) || run {
            if (src.isDirectory) src.copyRecursively(dst) && src.deleteRecursively()
            else { src.copyTo(dst, overwrite = true); src.delete() }
        }
        if (!moved) return@guarded fail("Could not move $from to $to.")
        ok("Moved $from to $to.", listOf(FileChange(from, FileChangeType.MOVED, null, to)))
    }

    // ------------------------------------------------------------------ fetch

    suspend fun webFetch(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val url = args.str("url")?.trim() ?: return@withContext fail("Missing required argument: url")
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            return@withContext fail("url must start with http:// or https://")
        }
        val maxChars = (args.int("max_chars") ?: DEFAULT_FETCH_CHARS).coerceIn(500, 100_000)
        try {
            val client = AiHttp.client.newBuilder().callTimeout(45, TimeUnit.SECONDS).build()
            val request = Request.Builder().url(url).get()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Oro/1.0")
                .header("Accept", "text/html,text/plain,application/json;q=0.9,*/*;q=0.5")
                .build()
            client.newCall(request).execute().use { response ->
                val type = response.header("Content-Type").orEmpty()
                val raw = response.body?.byteStream()?.use { s ->
                    val bytes = s.readNBytesCompat(2_000_000)
                    String(bytes, Charsets.UTF_8)
                }.orEmpty()
                if (!response.isSuccessful) {
                    return@withContext fail("HTTP ${response.code} from $url" + raw.take(200).let { if (it.isBlank()) "" else ": $it" })
                }
                val text = if (type.contains("html", true) || raw.trimStart().startsWith("<")) HtmlText.toText(raw) else raw
                val body = if (text.length > maxChars) text.take(maxChars) + "\n[Truncated at $maxChars characters.]" else text
                ok("URL: $url\nContent-Type: ${type.ifBlank { "unknown" }}\n\n$body")
            }
        } catch (e: Exception) {
            fail("Fetch failed for $url: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16_384)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun notFound(path: String): String {
        val name = path.substringAfterLast('/')
        val similar = if (name.isNotBlank()) {
            walkFiles(root.canonicalFile).filter { it.name.equals(name, ignoreCase = true) }.take(3).map { rel(it) }.toList()
        } else emptyList()
        return "$path does not exist." +
            if (similar.isNotEmpty()) " Did you mean: ${similar.joinToString(", ")}?" else " Use list_files or glob to find the right path."
    }
}

/** Glob to regex: `**` any depth, `*` within a segment, `?`, `{a,b}`, `[abc]`. */
object Glob {
    fun toRegex(glob: String): Regex {
        val sb = StringBuilder()
        var i = 0
        var inBraces = 0
        while (i < glob.length) {
            val c = glob[i]
            when {
                c == '*' && glob.getOrNull(i + 1) == '*' -> {
                    // "**/" matches zero or more directories.
                    if (glob.getOrNull(i + 2) == '/') { sb.append("(?:.*/)?"); i += 3; continue }
                    sb.append(".*"); i += 2; continue
                }
                c == '*' -> sb.append("[^/]*")
                c == '?' -> sb.append("[^/]")
                c == '{' -> { inBraces++; sb.append("(?:") }
                c == '}' && inBraces > 0 -> { inBraces--; sb.append(")") }
                c == ',' && inBraces > 0 -> sb.append("|")
                c == '[' -> {
                    val close = glob.indexOf(']', i + 1)
                    if (close > i) { sb.append(glob, i, close + 1); i = close + 1; continue }
                    sb.append("\\[")
                }
                "\\.+()^$|".contains(c) -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
            i++
        }
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}

/** Minimal HTML to readable text for web_fetch. */
object HtmlText {
    fun toText(html: String): String {
        var s = html
            .replace(Regex("(?is)<(script|style|noscript|svg|head)\\b.*?</\\1>"), " ")
            .replace(Regex("(?is)<!--.*?-->"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li|h[1-6]|tr|section|article|header|footer|pre|blockquote)>"), "\n")
            .replace(Regex("(?i)<li\\b[^>]*>"), "\n- ")
            .replace(Regex("(?i)<h([1-6])\\b[^>]*>")) { "\n" + "#".repeat(it.groupValues[1].toInt()) + " " }
            .replace(Regex("(?s)<[^>]+>"), " ")
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        return s.lines().map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}
