package com.oro.app.data.ai.tools

import java.io.File

/**
 * One line per file saying what is in it: HTML ids and the scripts and
 * styles it loads, CSS selectors and custom properties, JS functions,
 * classes, the events it listens for and the URLs it fetches, JSON keys.
 *
 * Behind `list_files(symbols=true)`. Weak models re-read whole files to find
 * where a button's handler lives or which stylesheet defines `.card`; an
 * outline of the project answers that in one call and a few hundred tokens.
 * Pure regex over text, bounded per file, so it is cheap on any project.
 */
object ProjectOutline {

    private const val MAX_FILE_BYTES = 400_000L
    private const val MAX_LINE = 170
    private const val MAX_ITEMS = 10

    fun of(file: File): String? {
        if (!file.isFile) return null
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (ext !in TEXT_TYPES) return null
        if (file.length() > MAX_FILE_BYTES) return "large file (${file.length() / 1024} KB)"
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        return of(file.name, text)
    }

    /** The outline of [text] as a file named [name]; null when there is nothing worth saying. */
    fun of(name: String, text: String): String? {
        val parts = when (name.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> html(text)
            "css" -> css(text)
            "js", "mjs", "jsx", "ts", "tsx" -> js(text)
            "json" -> json(text)
            "md" -> listOfNotNull(HEADING.find(text)?.groupValues?.get(1)?.trim()?.let { "\"$it\"" })
            else -> emptyList()
        }
        val lines = text.count { it == '\n' } + 1
        val body = parts.filter { it.isNotBlank() }.joinToString(" · ")
        val line = if (body.isEmpty()) "$lines lines" else "$lines lines · $body"
        return if (line.length > MAX_LINE) line.take(MAX_LINE - 1) + "…" else line
    }

    private fun html(text: String): List<String> {
        val inlineScripts = INLINE_SCRIPT.findAll(text).joinToString("\n") { it.groupValues[1] }
        return listOf(
            items("ids", ID.findAll(stripScripts(text)).map { it.groupValues[1] }, "#"),
            items("loads", (SCRIPT_SRC.findAll(text) + STYLESHEET.findAll(text)).map { it.groupValues[1] }),
            if (inlineScripts.isNotBlank()) items("inline js", jsSymbols(inlineScripts)) else ""
        )
    }

    private fun css(text: String): List<String> {
        val clean = COMMENT.replace(text, "")
        val selectors = RULE.findAll(clean).map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() && !it.startsWith("@") && !it.endsWith("%") && it != "from" && it != "to" }
            .flatMap { it.split(',').asSequence().map { s -> s.trim() } }
            .filter { it.isNotEmpty() && it.length < 60 }
        val media = AT_RULE.findAll(clean).count()
        return listOf(
            items("selectors", selectors),
            items("vars", CUSTOM_PROPERTY.findAll(clean).map { it.groupValues[1] }),
            if (media > 0) "@rules: $media" else ""
        )
    }

    private fun js(text: String): List<String> {
        val clean = BLOCK_COMMENT.replace(text, "")
        return listOf(
            items("functions", jsSymbols(clean)),
            items("classes", CLASS.findAll(clean).map { it.groupValues[1] }),
            items("listens", LISTENER.findAll(clean).map { it.groupValues[1] }),
            items("fetches", FETCH.findAll(clean).map { it.groupValues[1] })
        )
    }

    private fun jsSymbols(text: String): Sequence<String> =
        FUNCTION.findAll(text).map { it.groupValues[1] } + ARROW.findAll(text).map { it.groupValues[1] }

    private fun json(text: String): List<String> {
        val obj = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) }.getOrNull()
        return when (obj) {
            is kotlinx.serialization.json.JsonObject -> listOf(items("keys", obj.keys.asSequence()))
            is kotlinx.serialization.json.JsonArray -> listOf("array of ${obj.size}")
            else -> listOf("not valid JSON")
        }
    }

    private fun items(label: String, values: Sequence<String>, prefix: String = ""): String {
        val distinct = values.map { it.trim() }.filter { it.isNotEmpty() }.distinct().toList()
        if (distinct.isEmpty()) return ""
        val shown = distinct.take(MAX_ITEMS).joinToString(", ") { prefix + it }
        return "$label: $shown" + if (distinct.size > MAX_ITEMS) " +${distinct.size - MAX_ITEMS}" else ""
    }

    private fun stripScripts(text: String) = INLINE_SCRIPT.replace(text, "")

    private val TEXT_TYPES = setOf("html", "htm", "css", "js", "mjs", "jsx", "ts", "tsx", "json", "md")
    private val ID = Regex("""(?<![\w:-])id\s*=\s*["']([^"'{}\s]+)["']""", RegexOption.IGNORE_CASE)
    private val SCRIPT_SRC = Regex("""<script[^>]*\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val STYLESHEET = Regex("""<link[^>]*\bhref\s*=\s*["']([^"']+\.css[^"']*)["']""", RegexOption.IGNORE_CASE)
    private val INLINE_SCRIPT = Regex("""(?is)<script(?![^>]*\bsrc=)[^>]*>(.*?)</script>""")
    private val COMMENT = Regex("""(?s)/\*.*?\*/""")
    private val BLOCK_COMMENT = Regex("""(?s)/\*.*?\*/""")
    private val RULE = Regex("""([^{};]+)\{""")
    private val AT_RULE = Regex("""@(media|supports|container|keyframes)\b""")
    private val CUSTOM_PROPERTY = Regex("""(--[\w-]+)\s*:""")
    private val FUNCTION = Regex("""(?:^|[^\w$.])function\s*\*?\s*([A-Za-z_$][\w$]*)\s*\(""")
    private val ARROW = Regex("""(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?(?:\([^()]*\)|[A-Za-z_$][\w$]*)\s*=>""")
    private val CLASS = Regex("""(?:^|[^\w$.])class\s+([A-Z][\w$]*)""")
    private val LISTENER = Regex("""addEventListener\(\s*['"]([\w:-]+)['"]""")
    private val FETCH = Regex("""fetch\(\s*[`'"]([^`'"$]{1,80})""")
    private val HEADING = Regex("""(?m)^#{1,3}\s+(.+)$""")
}
