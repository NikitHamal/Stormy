package com.oro.app.data.ai.search

import java.io.File
import kotlin.math.ln

/**
 * On-device project search: BM25 over ~40-line chunks of every text file,
 * with code-aware tokens (camelCase, kebab-case and snake_case split; light
 * stemming) and a small web vocabulary of synonyms, so "pricing table" finds
 * `<section class="plans">` and "nav" finds `navbar`. Nothing leaves the phone.
 *
 * The index is rebuilt only for files whose size or mtime changed.
 */
class ProjectSearch(private val root: File) {

    data class Hit(val path: String, val startLine: Int, val endLine: Int, val score: Double, val snippet: String)

    private data class Chunk(val path: String, val start: Int, val end: Int, val text: String, val tf: Map<String, Int>, val len: Int)
    private data class FileEntry(val stamp: Long, val chunks: List<Chunk>)

    private val files = HashMap<String, FileEntry>()

    @Synchronized
    fun search(query: String, limit: Int = 8): List<Hit> {
        refresh()
        val all = files.values.flatMap { it.chunks }
        if (all.isEmpty()) return emptyList()
        val q = expand(tokens(query))
        if (q.isEmpty()) return emptyList()
        val n = all.size.toDouble()
        val avg = all.sumOf { it.len }.toDouble() / n
        val df = q.associate { (t, _) -> t to all.count { c -> t in c.tf } }
        val k1 = 1.4; val b = 0.72
        return all.mapNotNull { c ->
            var s = 0.0
            for ((t, w) in q) {
                val f = c.tf[t] ?: continue
                val d = df.getValue(t)
                val idf = ln(1 + (n - d + 0.5) / (d + 0.5))
                s += w * idf * (f * (k1 + 1)) / (f + k1 * (1 - b + b * c.len / avg))
            }
            // A query term in the path is a strong hint.
            if (q.any { (t, _) -> c.path.lowercase().contains(t) }) s *= 1.3
            if (s <= 0) null else Hit(c.path, c.start, c.end, s, snippet(c.text, q.map { it.first }))
        }.sortedByDescending { it.score }.distinctBy { it.path to it.startLine / 20 }.take(limit)
    }

    private fun refresh() {
        val seen = HashSet<String>()
        root.walkTopDown()
            .onEnter { it == root || it.name !in SKIP_DIRS }
            .filter { it.isFile && it.extension.lowercase() in TEXT_EXT && it.length() < 400_000 }
            .take(3_000)
            .forEach { f ->
                val rel = f.relativeTo(root).invariantSeparatorsPath
                seen += rel
                val stamp = f.lastModified() xor (f.length() shl 20)
                if (files[rel]?.stamp != stamp) files[rel] = FileEntry(stamp, chunk(rel, runCatching { f.readLines() }.getOrDefault(emptyList())))
            }
        files.keys.retainAll(seen)
    }

    private fun chunk(path: String, lines: List<String>): List<Chunk> {
        val out = ArrayList<Chunk>()
        var i = 0
        while (i < lines.size || (i == 0 && lines.isEmpty())) {
            val end = minOf(lines.size, i + CHUNK)
            val text = lines.subList(i, end).joinToString("\n")
            val toks = tokens(text) + tokens(path)
            out += Chunk(path, i + 1, end, text, toks.groupingBy { it }.eachCount(), toks.size.coerceAtLeast(1))
            if (end >= lines.size) break
            i += CHUNK - OVERLAP
        }
        return out
    }

    private fun snippet(text: String, terms: List<String>): String {
        val lines = text.lines()
        val best = lines.indices.maxByOrNull { idx -> terms.count { lines[idx].lowercase().contains(it) } } ?: 0
        return lines.subList((best - 1).coerceAtLeast(0), minOf(lines.size, best + 3)).joinToString("\n").take(400)
    }

    companion object {
        private const val CHUNK = 40
        private const val OVERLAP = 8
        private val SKIP_DIRS = setOf(".oro", ".git", "node_modules", "dist", "build", ".gradle")
        private val TEXT_EXT = setOf("html", "htm", "css", "scss", "js", "mjs", "ts", "tsx", "jsx", "json", "md", "txt", "svg", "xml", "vue", "svelte", "py", "kt", "java")
        private val STOP = setOf("the", "a", "an", "of", "to", "and", "or", "in", "on", "for", "is", "it", "this", "that", "with", "where", "what", "my", "me", "find")
        private val SYN = mapOf(
            "nav" to listOf("navbar", "navigation", "menu", "header"), "menu" to listOf("nav", "navbar", "hamburger"),
            "button" to listOf("btn", "cta"), "btn" to listOf("button"), "image" to listOf("img", "picture", "photo"),
            "photo" to listOf("img", "image", "gallery"), "pricing" to listOf("price", "plans", "plan", "tier"),
            "footer" to listOf("bottom", "copyright"), "hero" to listOf("banner", "jumbotron", "headline"),
            "color" to listOf("colour", "theme", "palette"), "dark" to listOf("theme", "prefers-color-scheme"),
            "form" to listOf("input", "submit", "contact"), "contact" to listOf("email", "form", "mailto"),
            "style" to listOf("css", "class"), "script" to listOf("js", "function"), "click" to listOf("onclick", "addEventListener"),
            "font" to listOf("typography", "font-family"), "link" to listOf("href", "anchor"), "mobile" to listOf("media", "responsive"),
        )

        /** "navBar-item_title" -> [nav, bar, item, title]. */
        fun tokens(text: String): List<String> = Regex("[A-Za-z][a-z0-9]*|[A-Z]+(?![a-z])|\\d+")
            .findAll(text.replace(Regex("([a-z])([A-Z])"), "$1 $2")).map { stem(it.value.lowercase()) }
            .filter { it.length > 1 && it !in STOP }.toList()

        private fun stem(w: String) = when {
            w.length > 5 && w.endsWith("ing") -> w.dropLast(3)
            w.length > 4 && w.endsWith("es") -> w.dropLast(2)
            w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
            w.length > 4 && w.endsWith("ed") -> w.dropLast(2)
            else -> w
        }

        /** Query terms with weights: 1.0 for what the user typed, 0.5 for synonyms. */
        fun expand(terms: List<String>): List<Pair<String, Double>> {
            val out = LinkedHashMap<String, Double>()
            terms.forEach { t -> out[t] = 1.0 }
            terms.forEach { t -> SYN[t]?.forEach { s -> tokens(s).forEach { out.putIfAbsent(it, 0.5) } } }
            return out.map { it.key to it.value }
        }
    }
}
