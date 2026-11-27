package com.oro.app.data.preview

import java.net.URLDecoder
import java.net.URLEncoder

/** What a file the agent produced is, as far as previewing it goes. */
enum class ArtifactKind {
    /** `.html` / `.htm`: opens in the live preview. */
    WEB_PAGE,
    /** `.css`: part of a page; changing it refreshes the live preview. */
    STYLE,
    /** `.js` / `.mjs` / `.jsx` / `.ts` / `.tsx`: part of a page, same as [STYLE]. */
    SCRIPT,
    /** `.svg`: drawn by the preview as an image, and still editable as text. */
    SVG,
    /** Raster images. */
    IMAGE,
    /** `.pdf`: opens in the built-in viewer. */
    PDF,
    /** `.md` / `.markdown`: rendered as a document by the preview server. */
    MARKDOWN,
    OTHER
}

/** The button a file earns: nothing, the live preview, or the PDF viewer. */
enum class ArtifactAction { NONE, LIVE_PREVIEW, PDF_VIEWER }

/** A localhost address typed into the preview's address bar. */
sealed interface LocalUrl {
    data class Valid(val url: String) : LocalUrl
    data class Invalid(val reason: String) : LocalUrl
}

/**
 * Pure rules the preview uses to recognise what the agent built: which files
 * deserve a Preview / View PDF button, which HTML pages a project has (for
 * the page picker), and how a project file maps to a URL on the embedded dev
 * server and back. No Android types, so it is unit-tested on the JVM.
 */
object PreviewArtifacts {

    /** The dev server renders a Markdown file as a page at `<base>/__oro/md/<path>`. */
    const val MARKDOWN_ROUTE = "__oro/md/"

    private val SKIPPED_DIRS = setOf("node_modules", "dist", "build", "uploads")

    fun kindOf(path: String): ArtifactKind {
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        if (!name.contains('.')) return ArtifactKind.OTHER
        return when (name.substringAfterLast('.').lowercase()) {
            "html", "htm" -> ArtifactKind.WEB_PAGE
            "css" -> ArtifactKind.STYLE
            "js", "mjs", "jsx", "ts", "tsx" -> ArtifactKind.SCRIPT
            "svg" -> ArtifactKind.SVG
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico" -> ArtifactKind.IMAGE
            "pdf" -> ArtifactKind.PDF
            "md", "markdown" -> ArtifactKind.MARKDOWN
            else -> ArtifactKind.OTHER
        }
    }

    fun actionFor(path: String): ArtifactAction = when (kindOf(path)) {
        ArtifactKind.PDF -> ArtifactAction.PDF_VIEWER
        ArtifactKind.WEB_PAGE, ArtifactKind.STYLE, ArtifactKind.SCRIPT,
        ArtifactKind.SVG, ArtifactKind.MARKDOWN -> ArtifactAction.LIVE_PREVIEW
        ArtifactKind.IMAGE, ArtifactKind.OTHER -> ArtifactAction.NONE
    }

    /**
     * Pages worth listing in the picker, as project-relative paths with `/`
     * separators: `index.html` first, then shallower before deeper, then by
     * name. Hidden folders and build output are left out.
     */
    fun pages(files: Collection<String>): List<String> =
        files.asSequence()
            .map { it.replace('\\', '/').trimStart('/') }
            .filter { kindOf(it) == ArtifactKind.WEB_PAGE }
            .filter { path ->
                path.split('/').dropLast(1).none { it.startsWith(".") || it in SKIPPED_DIRS }
            }
            .distinct()
            .sortedWith(
                compareBy<String>(
                    { it != "index.html" },
                    { it.count { c -> c == '/' } },
                    { it.lowercase() }
                )
            )
            .toList()

    /** The page a project opens on: its `index.html`, else the first page there is. */
    fun entryPoint(files: Collection<String>): String? = pages(files).firstOrNull()

    /** The dev-server URL that shows [relativePath], whatever its kind. */
    fun urlFor(baseUrl: String, relativePath: String): String {
        val clean = relativePath.replace('\\', '/').trimStart('/')
        val encoded = clean.split('/').filter { it.isNotEmpty() }.joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        val base = baseUrl.trimEnd('/')
        val route = if (kindOf(clean) == ArtifactKind.MARKDOWN) MARKDOWN_ROUTE else ""
        return "$base/$route$encoded"
    }

    /**
     * The project-relative path a dev-server [url] is showing, or null when it
     * is some other server. A bare origin or a directory means its `index.html`.
     */
    fun relativeOf(baseUrl: String, url: String?): String? {
        if (url == null) return null
        val base = baseUrl.trimEnd('/')
        if (url != base && !url.startsWith("$base/")) return null
        var rest = url.removePrefix(base).substringBefore('#').substringBefore('?').trimStart('/')
        rest = runCatching { URLDecoder.decode(rest.replace("+", "%2B"), "UTF-8") }.getOrDefault(rest)
        if (rest.startsWith(MARKDOWN_ROUTE)) rest = rest.removePrefix(MARKDOWN_ROUTE)
        if (rest.isEmpty() || rest.endsWith("/")) rest += "index.html"
        return rest
    }

    /**
     * Turns what a user typed ("5173", ":3000/admin", "localhost:8000",
     * "http://127.0.0.1:4000") into a URL for the preview. Only this phone's
     * own addresses are accepted: the preview page can talk to the app, so
     * other sites open in the browser instead.
     */
    fun parseLocalUrl(input: String): LocalUrl {
        val text = input.trim()
        if (text.isEmpty()) return LocalUrl.Invalid("Enter a port or a localhost address.")
        if (text.any { it.isWhitespace() }) return LocalUrl.Invalid("An address has no spaces.")

        val withScheme = when {
            Regex("""^\d{1,5}([/?#].*)?$""").matches(text) -> "http://localhost:$text"
            text.startsWith(":") -> "http://localhost$text"
            "://" in text -> text
            else -> "http://$text"
        }
        val scheme = withScheme.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") return LocalUrl.Invalid("Only http and https addresses work here.")

        val authority = withScheme.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.contains('@')) return LocalUrl.Invalid("Addresses with a login aren't supported.")
        val (host, port) = splitHostPort(authority) ?: return LocalUrl.Invalid("That address isn't valid.")
        if (!isLoopbackHost(host)) {
            return LocalUrl.Invalid("Only localhost addresses open here. Other sites open in your browser.")
        }
        if (port != null && port !in 1..65535) return LocalUrl.Invalid("Ports run from 1 to 65535.")
        return LocalUrl.Valid(withScheme)
    }

    fun isLoopbackHost(host: String): Boolean {
        val h = host.lowercase().removePrefix("[").removeSuffix("]")
        return h == "localhost" || h == "::1" || h == "0.0.0.0" || h == "127.0.0.1" ||
            Regex("""^127(\.\d{1,3}){3}$""").matches(h)
    }

    private fun splitHostPort(authority: String): Pair<String, Int?>? {
        if (authority.isEmpty()) return null
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            val host = authority.substring(0, close + 1)
            val tail = authority.substring(close + 1)
            if (tail.isEmpty()) return host to null
            if (!tail.startsWith(":")) return null
            return host to (tail.drop(1).toIntOrNull() ?: return null)
        }
        val colon = authority.lastIndexOf(':')
        if (colon < 0) return authority to null
        val port = authority.substring(colon + 1).toIntOrNull() ?: return null
        return authority.substring(0, colon) to port
    }
}
