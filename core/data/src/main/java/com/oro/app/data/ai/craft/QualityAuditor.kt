package com.oro.app.data.ai.craft

import java.io.File

/**
 * Static review of a web project, run by `check_syntax` and automatically as
 * the quality gate when an agent run finishes.
 *
 * ### Why a gate
 *
 * Strong models check their own work; weak ones announce "Done!" over a page
 * whose script queries `#submit-btn` while the button is `#submitBtn`, whose
 * stylesheet link points at `styles.css` beside a `style.css`, or whose HTML
 * was cut off by the output limit halfway through `<footer>`. None of that
 * needs a model to find. The harness finds it deterministically and hands the
 * list back, so every model gets the same second pass a careful engineer
 * would do -- which is most of the gap between "demo" and "professional".
 *
 * Checks are deliberately high-precision: a false positive costs a model turn
 * and teaches it to ignore the gate, so anything ambiguous is a [Severity.TIP]
 * and never blocks.
 */
class QualityAuditor(private val root: File) {

    enum class Severity { ERROR, WARNING, TIP }

    data class Issue(val severity: Severity, val path: String, val line: Int?, val message: String) {
        fun render(): String = buildString {
            append(
                when (severity) {
                    Severity.ERROR -> "ERROR"
                    Severity.WARNING -> "WARN"
                    Severity.TIP -> "TIP"
                }
            )
            append("  ").append(path)
            if (line != null) append(':').append(line)
            append("  ").append(message)
        }
    }

    data class Report(val issues: List<Issue>, val filesChecked: Int) {
        val blocking: List<Issue> get() = issues.filter { it.severity != Severity.TIP }
        val errors: Int get() = issues.count { it.severity == Severity.ERROR }
        val warnings: Int get() = issues.count { it.severity == Severity.WARNING }

        fun render(max: Int = 40): String = buildString {
            if (issues.isEmpty()) {
                append("No issues found in $filesChecked files.")
                return@buildString
            }
            append("Checked $filesChecked files: $errors errors, $warnings warnings, ")
            append("${issues.size - errors - warnings} tips.\n")
            issues.sortedBy { it.severity.ordinal }.take(max).forEach { append(it.render()).append('\n') }
            if (issues.size > max) append("… ${issues.size - max} more.\n")
        }
    }

    private data class Source(val rel: String, val file: File, val text: String)

    /**
     * @param focus project-relative paths to report on (files changed in this
     *   run); null reports on everything. Cross-file checks still read the
     *   whole project.
     */
    fun audit(focus: Set<String>? = null): Report {
        val sources = collect()
        val html = sources.filter { it.rel.endsWith(".html") || it.rel.endsWith(".htm") }
        val css = sources.filter { it.rel.endsWith(".css") }
        val js = sources.filter { it.rel.endsWith(".js") || it.rel.endsWith(".mjs") }
        val issues = mutableListOf<Issue>()

        val allIds = HashSet<String>()
        html.forEach { s -> ID_ATTR.findAll(s.text).forEach { allIds += it.groupValues[2] } }
        // Elements created from script also count as existing.
        js.forEach { s ->
            ID_ATTR.findAll(s.text).forEach { allIds += it.groupValues[2] }
            JS_ID_ASSIGN.findAll(s.text).forEach { allIds += it.groupValues[1] }
        }
        val hasJsxOrFramework = sources.any { it.rel.endsWith(".jsx") || it.rel.endsWith(".tsx") || it.rel.endsWith(".vue") || it.rel.endsWith(".svelte") } ||
            js.any { it.text.contains("React.createElement") || it.text.contains("from 'react'") || it.text.contains("from \"react\"") || it.text.contains("createApp(") }

        html.forEach { checkHtml(it, issues) }
        css.forEach { checkCss(it, issues) }
        js.forEach { checkJs(it, issues, allIds, skipIdCheck = hasJsxOrFramework) }

        // Project-level.
        val anyAnimation = css.any { ANIMATION.containsMatchIn(it.text) } || html.any { ANIMATION.containsMatchIn(it.text) }
        val reducedMotion = sources.any { it.text.contains("prefers-reduced-motion") }
        if (anyAnimation && !reducedMotion) {
            val target = css.firstOrNull { ANIMATION.containsMatchIn(it.text) }?.rel ?: html.first().rel
            issues += Issue(
                Severity.WARNING, target, null,
                "Animations/transitions are used but nothing honours prefers-reduced-motion. Add a " +
                    "@media (prefers-reduced-motion: reduce) block that disables non-essential motion."
            )
        }
        val styledInteractive = css.any { it.text.contains(":hover") }
        val hasFocus = sources.any { it.text.contains(":focus") }
        if (styledInteractive && !hasFocus) {
            issues += Issue(
                Severity.WARNING, css.first { it.text.contains(":hover") }.rel, null,
                "Hover styles exist but no :focus-visible styles. Keyboard users can't see focus; style :focus-visible."
            )
        }
        if (html.isNotEmpty() && File(root, "index.html").let { !it.exists() } &&
            sources.none { it.rel.endsWith("package.json") }
        ) {
            issues += Issue(
                Severity.ERROR, ".", null,
                "There is no index.html at the project root, so Oro's preview has nothing to open."
            )
        }

        val reported = if (focus == null) issues else issues.filter { it.path in focus || it.path == "." }
        return Report(reported.distinct(), sources.size)
    }

    /**
     * The checks that need nothing but the file itself, run on every write:
     * cut-off HTML, unbalanced script and style brackets, invalid JSON,
     * duplicate ids. Cross-file checks (missing ids, broken links) are left to
     * the full review -- mid-build, the file they point at may simply not be
     * written yet, and a false alarm after every write teaches the model to
     * ignore the check.
     */
    fun quickCheck(rel: String, text: String): List<Issue> {
        val out = mutableListOf<Issue>()
        val ext = rel.substringAfterLast('.', "").lowercase()
        when (ext) {
            "html", "htm" -> {
                val lower = text.lowercase()
                if (lower.contains("<html") && !lower.contains("</html>")) {
                    out += Issue(Severity.ERROR, rel, text.count { it == '\n' } + 1, "No closing </html>: the file looks cut off.")
                } else if (lower.contains("<body") && !lower.contains("</body>")) {
                    out += Issue(Severity.ERROR, rel, text.count { it == '\n' } + 1, "No closing </body>: the file looks cut off.")
                }
                duplicateIds(text).forEach { (id, ms) ->
                    out += Issue(Severity.ERROR, rel, lineAt(text, ms[1].range.first), "Duplicate id=\"$id\" (${ms.size} times).")
                }
                INLINE_SCRIPT.findAll(text).forEach { m ->
                    val type = SCRIPT_TYPE.find(m.value.substringBefore('>'))?.groupValues?.get(1)
                    if (type != null && type != "module" && !type.contains("javascript")) return@forEach
                    JsBalance.check(m.groupValues[1])?.let { msg ->
                        out += Issue(Severity.ERROR, rel, lineAt(text, m.range.first), "Inline <script>: $msg")
                    }
                }
                INLINE_STYLE.findAll(text).forEach { m ->
                    cssBalance(m.groupValues[1])?.let { msg -> out += Issue(Severity.ERROR, rel, lineAt(text, m.range.first), "Inline <style>: $msg") }
                }
            }
            "css" -> cssBalance(text)?.let { out += Issue(Severity.ERROR, rel, null, it) }
            "js", "mjs" -> JsBalance.check(text)?.let { out += Issue(Severity.ERROR, rel, null, it) }
            // JSON-with-comments configs (tsconfig, .vscode) are valid as they are.
            "json" -> if (text.isNotBlank() && !isJsonWithComments(rel)) {
                runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) }.exceptionOrNull()?.let { e ->
                    val detail = e.message?.lineSequence()?.firstOrNull()?.take(140).orEmpty()
                    out += Issue(Severity.ERROR, rel, null, "Invalid JSON: $detail")
                }
            }
        }
        return out
    }

    // ---------------------------------------------------------------- HTML

    private fun checkHtml(s: Source, out: MutableList<Issue>) {
        val t = s.text
        val lower = t.lowercase()
        val isFragment = !lower.contains("<html") && !lower.contains("<body")
        if (!isFragment) {
            if (!lower.trimStart().startsWith("<!doctype html")) {
                out += Issue(Severity.WARNING, s.rel, 1, "Missing <!DOCTYPE html> as the first line (page renders in quirks mode).")
            }
            if (!Regex("<html[^>]*\\blang=", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
                out += Issue(Severity.WARNING, s.rel, lineOf(t, "<html"), "<html> has no lang attribute (e.g. lang=\"en\").")
            }
            if (!Regex("<meta[^>]+name=[\"']viewport", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
                out += Issue(
                    Severity.ERROR, s.rel, lineOf(t, "<head"),
                    "No viewport meta tag; the page will render zoomed-out on phones. Add " +
                        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">."
                )
            }
            if (!Regex("<title>\\s*[^<\\s][^<]*</title>", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
                out += Issue(Severity.WARNING, s.rel, lineOf(t, "<head"), "Missing or empty <title>.")
            }
            if (lower.contains("<html") && !lower.contains("</html>")) {
                out += Issue(
                    Severity.ERROR, s.rel, t.count { it == '\n' } + 1,
                    "The file has <html> but no closing </html>: it looks cut off. Read the end of the file and complete it."
                )
            } else if (lower.contains("<body") && !lower.contains("</body>")) {
                out += Issue(Severity.ERROR, s.rel, t.count { it == '\n' } + 1, "No closing </body>: the file looks truncated.")
            }
        }

        // Local references must exist.
        val baseDir = s.file.parentFile ?: root
        REF_ATTR.findAll(t).forEach { m ->
            val raw = m.groupValues[3].trim()
            if (!isLocalRef(raw)) return@forEach
            val clean = raw.substringBefore('#').substringBefore('?')
            if (clean.isEmpty()) return@forEach
            val target = if (clean.startsWith("/")) File(root, clean.trimStart('/')) else File(baseDir, clean)
            if (!target.exists()) {
                val suggestion = nearestFile(target)
                out += Issue(
                    Severity.ERROR, s.rel, lineAt(t, m.range.first),
                    "References \"$raw\" but that file does not exist" +
                        (suggestion?.let { "; did you mean \"$it\"?" } ?: ". Create it or fix the path.")
                )
            }
        }

        // In-page anchors must have a target.
        val ids = ID_ATTR.findAll(t).map { it.groupValues[2] }.toSet()
        Regex("""href\s*=\s*["']#([A-Za-z][\w:-]*)["']""").findAll(t).forEach { m ->
            if (m.groupValues[1] !in ids) {
                out += Issue(Severity.WARNING, s.rel, lineAt(t, m.range.first), "Link to #${m.groupValues[1]} but no element has id=\"${m.groupValues[1]}\".")
            }
        }

        // Duplicate ids.
        duplicateIds(t).forEach { (id, ms) ->
            out += Issue(Severity.ERROR, s.rel, lineAt(t, ms[1].range.first), "Duplicate id=\"$id\" (${ms.size} times); ids must be unique.")
        }

        // Images without alt.
        Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(t).forEach { m ->
            if (!Regex("\\balt\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(m.value)) {
                out += Issue(Severity.WARNING, s.rel, lineAt(t, m.range.first), "<img> without alt text (use alt=\"\" if decorative).")
            }
        }

        // Icon-only buttons need an accessible name.
        Regex("<button\\b([^>]*)>(.*?)</button>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(t).forEach { m ->
            val attrs = m.groupValues[1]
            val inner = m.groupValues[2].replace(Regex("<[^>]+>"), "").replace("&nbsp;", "").trim()
            if (inner.isEmpty() && !Regex("aria-label|aria-labelledby|title=", RegexOption.IGNORE_CASE).containsMatchIn(attrs)) {
                out += Issue(Severity.WARNING, s.rel, lineAt(t, m.range.first), "Button with no text and no aria-label; screen readers announce it as just \"button\".")
            }
        }

        // Clickable divs.
        Regex("<(div|span)\\b[^>]*\\bonclick=", RegexOption.IGNORE_CASE).findAll(t).forEach { m ->
            out += Issue(Severity.WARNING, s.rel, lineAt(t, m.range.first), "Clickable <${m.groupValues[1]}>; use a <button> (keyboard and screen-reader accessible).")
        }

        if (Regex("lorem ipsum", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            out += Issue(Severity.WARNING, s.rel, lineOf(t, "orem ipsum"), "Placeholder \"lorem ipsum\" copy; write real content for the brief.")
        }
        if (Regex("""src\s*=\s*["'](image|img|photo|placeholder)\d*\.(jpg|png|jpeg|webp)["']""", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            out += Issue(Severity.TIP, s.rel, null, "Generic image filenames; use real assets or https://picsum.photos/seed/<word>/1200/800 placeholders.")
        }

        // Inline <style>/<script> balance.
        Regex("<script\\b(?![^>]*\\bsrc=)[^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(t).forEach { m ->
            val type = Regex("type\\s*=\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE).find(m.value.substringBefore('>'))?.groupValues?.get(1)
            if (type != null && type != "module" && !type.contains("javascript")) return@forEach
            JsBalance.check(m.groupValues[1])?.let { msg ->
                out += Issue(Severity.ERROR, s.rel, lineAt(t, m.range.first), "Inline <script>: $msg")
            }
        }
        Regex("<style\\b[^>]*>(.*?)</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(t).forEach { m ->
            cssBalance(m.groupValues[1])?.let { msg -> out += Issue(Severity.ERROR, s.rel, lineAt(t, m.range.first), "Inline <style>: $msg") }
        }
    }

    // ----------------------------------------------------------------- CSS

    private fun checkCss(s: Source, out: MutableList<Issue>) {
        cssBalance(s.text)?.let { out += Issue(Severity.ERROR, s.rel, null, it) }
        Regex("""url\(\s*["']?([^"')]+)["']?\s*\)""").findAll(s.text).forEach { m ->
            val raw = m.groupValues[1].trim()
            if (!isLocalRef(raw)) return@forEach
            val clean = raw.substringBefore('#').substringBefore('?')
            val target = if (clean.startsWith("/")) File(root, clean.trimStart('/')) else File(s.file.parentFile ?: root, clean)
            if (!target.exists()) {
                out += Issue(Severity.ERROR, s.rel, lineAt(s.text, m.range.first), "url(\"$raw\") points to a file that does not exist.")
            }
        }
    }

    private fun cssBalance(css: String): String? {
        val stripped = css.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"), "\"\"")
        var depth = 0
        stripped.forEachIndexed { i, c ->
            if (c == '{') depth++
            if (c == '}') {
                depth--
                if (depth < 0) return "Unexpected '}' near line ${stripped.substring(0, i).count { it == '\n' } + 1}."
            }
        }
        return if (depth > 0) "$depth unclosed '{' -- a rule is missing its closing brace (file may be truncated)." else null
    }

    // ------------------------------------------------------------------ JS

    private fun checkJs(s: Source, out: MutableList<Issue>, allIds: Set<String>, skipIdCheck: Boolean) {
        JsBalance.check(s.text)?.let { out += Issue(Severity.ERROR, s.rel, null, it) }

        if (!skipIdCheck && allIds.isNotEmpty()) {
            val missing = LinkedHashMap<String, Int>()
            JS_ID_LOOKUP.findAll(s.text).forEach { m ->
                val id = m.groupValues[1].ifEmpty { m.groupValues[2] }
                if (id.isNotEmpty() && id !in allIds && !id.contains("\${")) missing.putIfAbsent(id, lineAt(s.text, m.range.first))
            }
            missing.forEach { (id, line) ->
                val close = allIds.minByOrNull { levenshtein(it.lowercase(), id.lowercase()) }
                    ?.takeIf { levenshtein(it.lowercase(), id.lowercase()) <= maxOf(2, id.length / 4) }
                out += Issue(
                    Severity.ERROR, s.rel, line,
                    "Looks up #$id but no element has id=\"$id\"" +
                        (close?.let { "; the HTML has id=\"$it\"." } ?: ". The lookup returns null and the next line will throw.")
                )
            }
        }

        // Relative ES module imports.
        Regex("""(?:import\s[^'"]*?from\s*|import\s*\(\s*|import\s+)["'](\.{1,2}/[^"']+)["']""").findAll(s.text).forEach { m ->
            val p = m.groupValues[1]
            val target = File(s.file.parentFile ?: root, p)
            if (!target.exists() && !File(target.path + ".js").exists()) {
                out += Issue(Severity.ERROR, s.rel, lineAt(s.text, m.range.first), "Imports \"$p\" but that file does not exist.")
            }
        }

        if (Regex("""(?<![\w.])alert\s*\(""").containsMatchIn(s.text)) {
            out += Issue(Severity.TIP, s.rel, lineOf(s.text, "alert("), "Uses alert(); a toast or inline message feels far more polished.")
        }
        if (Regex("""\.innerHTML\s*\+=\s*""").containsMatchIn(s.text)) {
            out += Issue(Severity.TIP, s.rel, lineOf(s.text, "innerHTML +="), "innerHTML += in a loop re-parses the DOM each time; build a string or use a fragment.")
        }
    }

    // ------------------------------------------------------------- helpers

    private fun collect(): List<Source> {
        val out = mutableListOf<Source>()
        val base = root.canonicalFile
        base.walkTopDown()
            .onEnter { d -> d == base || (d.name !in SKIPPED_DIRS && !d.name.startsWith(".")) }
            .filter { it.isFile && it.extension.lowercase() in EXTENSIONS && it.length() < MAX_FILE_BYTES }
            .take(MAX_FILES)
            .forEach { f ->
                val rel = f.relativeTo(base).path.replace(File.separatorChar, '/')
                if (rel.startsWith("${UiKit.DIR}/")) return@forEach // vetted
                out += Source(rel, f, runCatching { f.readText() }.getOrDefault(""))
            }
        return out
    }

    private fun isLocalRef(raw: String): Boolean {
        if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("{") || raw.contains("\${") || raw.contains("{{")) return false
        val lower = raw.lowercase()
        if (SCHEMES.any { lower.startsWith(it) } || lower.startsWith("//")) return false
        if (lower.startsWith("/api") || lower.startsWith("api/")) return false
        return true
    }

    private fun nearestFile(target: File): String? {
        val dir = target.parentFile ?: return null
        if (!dir.isDirectory) return null
        val name = target.name.lowercase()
        val candidates = dir.listFiles()?.filter { it.isFile } ?: return null
        val best = candidates.minByOrNull { levenshtein(it.name.lowercase(), name) } ?: return null
        if (levenshtein(best.name.lowercase(), name) > maxOf(2, name.length / 3)) return null
        return best.relativeTo(root.canonicalFile).path.replace(File.separatorChar, '/')
    }

    private fun lineAt(text: String, index: Int): Int = text.substring(0, index.coerceIn(0, text.length)).count { it == '\n' } + 1

    private fun lineOf(text: String, needle: String): Int? {
        val i = text.indexOf(needle, ignoreCase = true)
        return if (i < 0) null else lineAt(text, i)
    }

    companion object {
        private val SKIPPED_DIRS = setOf("node_modules", "dist", "build", "vendor", "vendors", "lib", "libs", "assets/vendor")
        private val EXTENSIONS = setOf("html", "htm", "css", "js", "mjs", "jsx", "tsx", "vue", "svelte", "json")
        private const val MAX_FILES = 400
        private const val MAX_FILE_BYTES = 600_000L
        private val SCHEMES = listOf("http:", "https:", "data:", "mailto:", "tel:", "javascript:", "blob:", "sms:", "about:")

        /** An `id` attribute; not `data-product-id`, `aria-…-id` or a `:id` binding. */
        private val ID_ATTR = Regex("""(?<![\w:-])id\s*=\s*(["'])([^"'\s{}$]+)\1""")

        /** Script and template bodies: their markup is strings and clones, not elements on the page. */
        private val SCRIPT_OR_TEMPLATE = Regex("<(script|template)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        /**
         * Ids that appear more than once among the page's own elements. A
         * script that builds markup in a loop or a <template> cloned per item
         * legitimately repeats ids in the source, so those bodies are blanked
         * (offsets and lines kept) before counting.
         */
        internal fun duplicateIds(html: String): Map<String, List<MatchResult>> {
            val elements = SCRIPT_OR_TEMPLATE.replace(html) { m -> m.value.map { c -> if (c == '\n') '\n' else ' ' }.joinToString("") }
            return ID_ATTR.findAll(elements).groupBy { it.groupValues[2] }.filter { it.value.size > 1 }
        }

        /** Config files that allow comments and trailing commas (JSONC). */
        internal fun isJsonWithComments(rel: String): Boolean {
            val path = rel.replace('\\', '/').lowercase()
            val name = path.substringAfterLast('/')
            return path.startsWith(".vscode/") || path.contains("/.vscode/") ||
                name.startsWith("tsconfig") || name.startsWith("jsconfig") ||
                name == "devcontainer.json" || name == ".devcontainer.json" || name.endsWith(".code-workspace")
        }
        private val JS_ID_ASSIGN = Regex("""\.id\s*=\s*["'`]([\w-]+)["'`]""")
        private val JS_ID_LOOKUP = Regex("""getElementById\(\s*["']([\w-]+)["']\s*\)|querySelector\(\s*["']#([\w-]+)["']\s*\)""")
        private val REF_ATTR = Regex("""<(link|script|img|source|video|audio|iframe)\b[^>]*?\b(href|src)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val ANIMATION = Regex("""\b(animation|transition)\s*:|@keyframes""")
        private val INLINE_SCRIPT = Regex("<script\\b(?![^>]*\\bsrc=)[^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val INLINE_STYLE = Regex("<style\\b[^>]*>(.*?)</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val SCRIPT_TYPE = Regex("type\\s*=\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE)

        fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                }
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }
    }
}

/**
 * Bracket balance for JavaScript that understands strings, template literals
 * (with nested `${}`), comments and regex literals -- enough to catch a file
 * truncated by the output limit or a lost closing brace, without a parser.
 */
internal object JsBalance {
    fun check(src: String): String? {
        val stack = ArrayDeque<Pair<Char, Int>>() // bracket, line
        var i = 0
        var line = 1
        var lastSignificant = ' '
        val n = src.length
        fun err(msg: String) = "$msg (line $line)."
        while (i < n) {
            val c = src[i]
            when {
                c == '\n' -> { line++; i++; continue }
                c == '/' && i + 1 < n && src[i + 1] == '/' -> { while (i < n && src[i] != '\n') i++; continue }
                c == '/' && i + 1 < n && src[i + 1] == '*' -> {
                    val end = src.indexOf("*/", i + 2)
                    if (end < 0) return err("Unclosed /* comment")
                    line += src.substring(i, end).count { it == '\n' }
                    i = end + 2; continue
                }
                c == '"' || c == '\'' -> {
                    i++
                    while (i < n && src[i] != c) {
                        if (src[i] == '\\') i++
                        else if (src[i] == '\n') return err("Unterminated string")
                        i++
                    }
                    if (i >= n) return err("Unterminated string")
                    i++; lastSignificant = 'a'; continue
                }
                c == '`' -> {
                    val r = skipTemplate(src, i + 1)
                    if (r < 0) return err("Unterminated template literal")
                    line += src.substring(i, r).count { it == '\n' }
                    i = r; lastSignificant = 'a'; continue
                }
                c == '/' && (lastSignificant in REGEX_PRECEDERS || precededByKeyword(src, i)) -> {
                    // regex literal
                    var j = i + 1
                    var inClass = false
                    while (j < n && src[j] != '\n') {
                        val d = src[j]
                        if (d == '\\') { j += 2; continue }
                        if (d == '[') inClass = true else if (d == ']') inClass = false
                        else if (d == '/' && !inClass) break
                        j++
                    }
                    if (j >= n || src[j] == '\n') { i++; lastSignificant = '/'; continue } // was division
                    i = j + 1; lastSignificant = 'a'; continue
                }
                c == '(' || c == '[' || c == '{' -> stack.addLast(c to line)
                c == ')' || c == ']' || c == '}' -> {
                    val open = stack.removeLastOrNull() ?: return err("Unexpected '$c'")
                    val expected = PAIRS[open.first]
                    if (expected != c) return err("'${open.first}' opened on line ${open.second} is closed by '$c'")
                }
            }
            if (!c.isWhitespace()) lastSignificant = c
            i++
        }
        if (stack.isNotEmpty()) {
            val (b, l) = stack.last()
            return "${stack.size} unclosed bracket(s); '$b' opened on line $l is never closed -- the file may be truncated."
        }
        return null
    }

    private fun precededByKeyword(src: String, at: Int): Boolean {
        var j = at - 1
        while (j >= 0 && src[j].isWhitespace()) j--
        val end = j + 1
        while (j >= 0 && (src[j].isLetter())) j--
        return src.substring(j + 1, end) in REGEX_KEYWORDS
    }

    private val REGEX_KEYWORDS = setOf("return", "typeof", "case", "in", "of", "delete", "void", "throw", "new", "yield", "await", "else", "do")

    /** Index just past the closing backtick, or -1. Handles nested ${ ... `...` ... }. */
    private fun skipTemplate(src: String, start: Int): Int {
        var i = start
        while (i < src.length) {
            val c = src[i]
            if (c == '\\') { i += 2; continue }
            if (c == '`') return i + 1
            if (c == '$' && i + 1 < src.length && src[i + 1] == '{') {
                var depth = 1
                i += 2
                while (i < src.length && depth > 0) {
                    val d = src[i]
                    when {
                        d == '`' -> { val r = skipTemplate(src, i + 1); if (r < 0) return -1; i = r; continue }
                        d == '"' || d == '\'' -> {
                            i++
                            while (i < src.length && src[i] != d) { if (src[i] == '\\') i++; i++ }
                        }
                        d == '{' -> depth++
                        d == '}' -> depth--
                    }
                    i++
                }
                continue
            }
            i++
        }
        return -1
    }

    private val PAIRS = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val REGEX_PRECEDERS = setOf(' ', '(', ',', '=', ':', '[', '!', '&', '|', '?', '{', '}', ';', '+', '-', '*', '%', '<', '>', '~', '^')
}
