package com.oro.app.data.preview

import java.net.URLDecoder

/**
 * Turns a page of the project into one self-contained HTML file: linked
 * stylesheets become `<style>`, `src` scripts become inline scripts, and
 * small local images, icons and fonts become `data:` URIs. Relative
 * (`styles.css`, `./styles.css`, `../css/a.css`) and root (`/styles.css`)
 * references resolve against the page's folder and the project root.
 * Pure: files come in through lambdas, so it is unit-tested on the JVM.
 */
object StandaloneBundler {

    class Result(
        val html: String,
        /** Project files folded into [html], in the order met. */
        val inlined: List<String>,
        /** Local references that no file answered; left as they were. */
        val missing: List<String>
    )

    private const val MAX_ASSET_BYTES = 1_500_000
    private const val MAX_CSS_DEPTH = 3

    private val LINK_TAG = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val SCRIPT_TAG = Regex("<script\\b([^>]*)>\\s*</script\\s*>", RegexOption.IGNORE_CASE)
    private val MEDIA_TAG = Regex("<(?:img|source|audio|video)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val CSS_URL = Regex("url\\(\\s*(['\"]?)([^)'\"]+)\\1\\s*\\)")
    private val CSS_IMPORT = Regex("@import\\s+(?:url\\(\\s*)?['\"]?([^'\")\\s;]+)['\"]?\\s*\\)?[^;]*;")
    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val ANY_REF = Regex("(?<![\\w-])(?:src|href|poster)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)

    fun bundle(
        pagePath: String,
        readText: (String) -> String?,
        readBytes: (String) -> ByteArray?
    ): Result {
        val page = pagePath.replace('\\', '/').trimStart('/')
        val source = readText(page) ?: return Result("", emptyList(), listOf(page))
        val inlined = LinkedHashSet<String>()
        val missing = LinkedHashSet<String>()

        fun dataUri(path: String, bytes: ByteArray): String =
            "data:${mimeFor(path)};base64,${base64(bytes)}"

        fun asset(from: String, ref: String): String? {
            val path = resolve(from, ref) ?: return null
            val bytes = readBytes(path)
            if (bytes == null) { missing += path; return null }
            if (bytes.size > MAX_ASSET_BYTES) return null
            inlined += path
            return dataUri(path, bytes)
        }

        fun css(text: String, from: String, depth: Int): String {
            var out = text
            if (depth < MAX_CSS_DEPTH) {
                out = CSS_IMPORT.replace(out) { m ->
                    val path = resolve(from, m.groupValues[1])
                    val body = path?.let { readText(it) }
                    if (path != null && body != null) {
                        inlined += path
                        css(body, path, depth + 1)
                    } else {
                        if (path != null) missing += path
                        m.value
                    }
                }
            }
            out = CSS_URL.replace(out) { m ->
                val ref = m.groupValues[2].trim()
                val uri = if (ref.startsWith("data:", ignoreCase = true) || mimeFor(ref) == FALLBACK_MIME) null else asset(from, ref)
                if (uri == null) m.value else "url(\"$uri\")"
            }
            return out.replace(Regex("</style", RegexOption.IGNORE_CASE), "<\\\\/style")
        }

        var html = LINK_TAG.replace(source) { m ->
            val tag = m.value
            val rel = attr(tag, "rel")?.lowercase().orEmpty()
            val href = attr(tag, "href")
            when {
                href == null -> tag
                "stylesheet" in rel -> {
                    val path = resolve(page, href)
                    val body = path?.let { readText(it) }
                    if (path == null) tag
                    else if (body == null) { missing += path; tag }
                    else {
                        inlined += path
                        val media = attr(tag, "media")?.let { " media=\"${it.replace("\"", "&quot;")}\"" }.orEmpty()
                        "<style$media>\n${css(body, path, 0)}\n</style>"
                    }
                }
                "icon" in rel -> asset(page, href)?.let { setAttr(tag, "href", it) } ?: tag
                else -> tag
            }
        }

        html = SCRIPT_TAG.replace(html) { m ->
            val attrs = m.groupValues[1]
            val src = attr("<script $attrs>", "src")
            if (src == null) m.value else {
                val path = resolve(page, src)
                val body = path?.let { readText(it) }
                if (path == null) m.value
                else if (body == null) { missing += path; m.value }
                else {
                    inlined += path
                    val rest = Regex("(?<![\\w-])src\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)", RegexOption.IGNORE_CASE)
                        .replace(attrs, "").trim()
                    val open = if (rest.isEmpty()) "<script>" else "<script $rest>"
                    "$open\n${body.replace(Regex("</script", RegexOption.IGNORE_CASE), "<\\\\/script")}\n</script>"
                }
            }
        }

        html = MEDIA_TAG.replace(html) { m ->
            val src = attr(m.value, "src")
            val uri = if (src == null || src.startsWith("data:", ignoreCase = true)) null else asset(page, src)
            if (uri == null) m.value else setAttr(m.value, "src", uri)
        }

        return Result(html, inlined.toList(), missing.toList())
    }

    /**
     * Every project file [startPage] needs or links to (pages it links to,
     * their styles, scripts and images, and what those styles load), start
     * first. Used to save a page together with the files it uses.
     */
    fun closure(startPage: String, readText: (String) -> String?, exists: (String) -> Boolean, limit: Int = 80): List<String> {
        val start = startPage.replace('\\', '/').trimStart('/')
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        queue += start
        while (queue.isNotEmpty() && seen.size < limit) {
            val path = queue.removeFirst()
            if (!seen.add(path)) continue
            val refs = when (PreviewArtifacts.kindOf(path)) {
                ArtifactKind.WEB_PAGE -> ANY_REF.findAll(readText(path).orEmpty()).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                ArtifactKind.STYLE -> (CSS_URL.findAll(readText(path).orEmpty()).map { it.groupValues[2] } +
                    CSS_IMPORT.findAll(readText(path).orEmpty()).map { it.groupValues[1] })
                else -> emptySequence()
            }
            refs.mapNotNull { resolve(path, it.trim()) }
                .filter { it !in seen && exists(it) }
                .forEach { queue += it }
        }
        return seen.toList()
    }

    /**
     * The project path [ref] names when written in [fromFile]; null for
     * links that leave the project (a scheme, `//host`, `#top`) or climb out of it.
     */
    fun resolve(fromFile: String, ref: String): String? {
        val raw = ref.trim().substringBefore('#').substringBefore('?')
        if (raw.isEmpty() || raw.startsWith("//") || SCHEME.containsMatchIn(raw)) return null
        val decoded = runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
        val stack = ArrayList<String>()
        if (!decoded.startsWith("/")) stack += fromFile.replace('\\', '/').split('/').dropLast(1)
        for (segment in decoded.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isEmpty()) return null else stack.removeAt(stack.lastIndex)
                else -> stack += segment
            }
        }
        return stack.joinToString("/").takeIf { it.isNotEmpty() }
    }

    /** One attribute's value from a tag, or null; `data-src` is not `src`. */
    internal fun attr(tag: String, name: String): String? {
        val m = Regex("(?<![\\w-])${Regex.escape(name)}\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE).find(tag) ?: return null
        return m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3] } }
    }

    internal fun setAttr(tag: String, name: String, value: String): String =
        Regex("(?<![\\w-])${Regex.escape(name)}\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)", RegexOption.IGNORE_CASE)
            .replaceFirst(tag, "$name=\"$value\"".replace("\\", "\\\\").replace("$", "\\$"))

    private const val FALLBACK_MIME = "application/octet-stream"

    internal fun mimeFor(path: String): String =
        when (path.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "bmp" -> "image/bmp"
            "avif" -> "image/avif"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            else -> FALLBACK_MIME
        }

    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Standard base64 with padding (java.util.Base64 needs API 26; this app starts at 24). */
    internal fun base64(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xff
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xff else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xff else 0
            out.append(B64[b0 shr 2])
            out.append(B64[((b0 and 3) shl 4) or (b1 shr 4)])
            out.append(if (i + 1 < bytes.size) B64[((b1 and 15) shl 2) or (b2 shr 6)] else '=')
            out.append(if (i + 2 < bytes.size) B64[b2 and 63] else '=')
            i += 3
        }
        return out.toString()
    }
}
