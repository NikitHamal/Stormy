package com.oro.app.ui.components.inspector

import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

/**
 * Status of direct edit operation
 */
sealed class DirectEditStatus {
    data object Idle : DirectEditStatus()
    data object Editing : DirectEditStatus()
    data class Success(val message: String) : DirectEditStatus()
    data class Error(val message: String) : DirectEditStatus()
}

/**
 * Service for handling direct file editing without AI
 * This service directly modifies CSS/HTML files for style and text changes,
 * providing immediate persistence without relying on AI models.
 *
 * Use this for Edit mode (manual editing) while PreviewEditService
 * is used for Agent mode (AI-assisted editing).
 */
class DirectEditService(
    private val projectPath: String,
    private val scope: CoroutineScope
) {
    private val _status = MutableStateFlow<DirectEditStatus>(DirectEditStatus.Idle)
    val status: StateFlow<DirectEditStatus> = _status.asStateFlow()

    /**
     * The page being edited (its location.pathname). Style, text and image
     * writes go to that page and the stylesheet it links, rather than always
     * to index.html and whichever CSS file a folder walk finds first.
     */
    @Volatile
    var pagePath: String? = null

    /** One write to a file, kept so it can be undone while the file still holds it. */
    private data class Snapshot(val file: File, val before: String, val after: String)
    private val history = ArrayDeque<Snapshot>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    /**
     * One debounce job per (selector, property).
     *
     * This used to be a single shared job, so changing `color` and then
     * `font-size` within the debounce window cancelled the first write
     * entirely: the live preview showed both changes but only the last one was
     * ever persisted to CSS. Keying by target means rapid edits to *different*
     * properties all land, while repeated edits to the *same* property (a
     * slider drag) still collapse to one write, which is the behaviour the
     * debounce was for.
     */
    private val debouncedJobs = mutableMapOf<String, Job>()

    // Debounce delay for batching rapid style changes (e.g., slider dragging)
    private val DEBOUNCE_DELAY_MS = 300L

    // Common CSS files to search for
    private val cssFileNames = listOf("style.css", "styles.css", "main.css", "index.css", "app.css")

    // Common HTML files
    private val htmlFileNames = listOf("index.html", "main.html", "app.html")

    /**
     * Apply a style change directly to CSS file
     * Uses debouncing for rapid changes to avoid excessive file writes
     * Live preview is applied immediately via JavaScript
     */
    fun applyStyleChange(
        request: StyleChangeRequest,
        webView: WebView?,
        onComplete: () -> Unit
    ) {
        // Apply live preview immediately for instant visual feedback, but only
        // once the selector is known to address a single element.
        scope.launch {
            if (!ensureSingleTarget(webView, request.selector)) {
                onComplete()
                return@launch
            }
            webView?.let { wv ->
                applyLiveStyleUpdate(wv, request.selector, request.property, request.newValue)
            }
        }

        // Debounce the file write per target, so edits to other properties of
        // the same element are not cancelled along with it.
        val key = "${request.selector}|${request.property}"
        debouncedJobs.remove(key)?.cancel()
        debouncedJobs[key] = scope.launch {
            delay(DEBOUNCE_DELAY_MS)

            _status.value = DirectEditStatus.Editing

            try {
                val result = withContext(Dispatchers.IO) {
                    updateCssFile(request)
                }

                if (result.isSuccess) {
                    _status.value = DirectEditStatus.Success("Style applied")
                } else {
                    _status.value = DirectEditStatus.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _status.value = DirectEditStatus.Error(e.message ?: "Failed to apply style")
            } finally {
                // A newer edit to the same property may own the key by now.
                if (debouncedJobs[key] === coroutineContext[Job]) debouncedJobs.remove(key)
                onComplete()
            }
        }
    }

    /**
     * Apply a text change: shown in the page at once, then written to the one
     * place in the page's source the text came from (see [SourceTextEdit]).
     * If it can't be written, the page is put back so it never shows an
     * edit that isn't in the file.
     */
    fun applyTextChange(
        request: TextChangeRequest,
        webView: WebView?,
        onComplete: () -> Unit
    ) {
        scope.launch {
            if (webView != null && countMatches(webView, request.selector) == 1) {
                LivePreviewEditor.applyOwnText(webView, request.selector, request.newText)
            }
        }

        val key = "${request.selector}|#text"
        debouncedJobs.remove(key)?.cancel()
        debouncedJobs[key] = scope.launch {
            delay(DEBOUNCE_DELAY_MS)

            _status.value = DirectEditStatus.Editing

            try {
                val result = withContext(Dispatchers.IO) {
                    updateHtmlText(request)
                }

                if (result.isSuccess) {
                    _status.value = DirectEditStatus.Success("Text updated")
                } else {
                    if (webView != null && countMatches(webView, request.selector) == 1) {
                        LivePreviewEditor.applyOwnText(webView, request.selector, request.oldText)
                    }
                    _status.value = DirectEditStatus.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _status.value = DirectEditStatus.Error(e.message ?: "Failed to update text")
            } finally {
                if (debouncedJobs[key] === coroutineContext[Job]) debouncedJobs.remove(key)
                onComplete()
            }
        }
    }

    /**
     * Apply image source change directly to HTML file
     */
    fun applyImageChange(
        request: ImageChangeRequest,
        webView: WebView?,
        onComplete: () -> Unit
    ) {

        // Apply live preview immediately
        scope.launch {
            if (!ensureSingleTarget(webView, request.selector)) {
                onComplete()
                return@launch
            }
            webView?.let { wv ->
                applyLiveImageUpdate(wv, request.selector, request.newSrc)
            }
        }

        // Write to file
        val key = "${request.selector}|#src"
        debouncedJobs.remove(key)?.cancel()
        debouncedJobs[key] = scope.launch {
            delay(DEBOUNCE_DELAY_MS)

            _status.value = DirectEditStatus.Editing

            try {
                val result = withContext(Dispatchers.IO) {
                    updateImageSrc(request)
                }

                if (result.isSuccess) {
                    _status.value = DirectEditStatus.Success("Image updated")
                } else {
                    _status.value = DirectEditStatus.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _status.value = DirectEditStatus.Error(e.message ?: "Failed to update image")
            } finally {
                onComplete()
            }
        }
    }

    /**
     * Update CSS file with the style change
     * Finds or creates a rule for the selector and updates the property
     * Falls back to inline <style> tag in HTML if no external CSS file exists
     */
    private fun updateCssFile(request: StyleChangeRequest): Result<Unit> {
        val selector = request.selector
        val property = request.property
        val value = request.newValue

        // First, try to find an external CSS file
        val cssFile = findCssFile()
        if (cssFile != null) {
            return updateExternalCssFile(cssFile, selector, property, value)
        }

        // No external CSS file - check for inline <style> tag in HTML
        val htmlFile = findHtmlFile()
        if (htmlFile != null) {
            val htmlContent = htmlFile.readText()
            if (htmlContent.contains("<style", ignoreCase = true)) {
                return updateInlineStyleTag(htmlFile, htmlContent, selector, property, value)
            }

            // No <style> tag exists - create one in the HTML
            return createInlineStyleTag(htmlFile, htmlContent, selector, property, value)
        }

        return Result.failure(Exception("No CSS or HTML file found to apply styles"))
    }

    /**
     * Update an external CSS file with the style change
     */
    private fun updateExternalCssFile(
        cssFile: File,
        selector: String,
        property: String,
        value: String
    ): Result<Unit> {
        val content = cssFile.readText()

        // Try to find existing rule for this selector
        val rulePattern = Pattern.compile(
            """(${Pattern.quote(selector)})\s*\{([^}]*)\}""",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val matcher = rulePattern.matcher(content)

        val newContent = if (matcher.find()) {
            // Found existing rule - update or add the property
            val existingStyles = matcher.group(2) ?: ""
            val newStyles = updatePropertyInStyles(existingStyles, property, value)
            matcher.replaceFirst("$selector {\n$newStyles}")
        } else {
            // No existing rule - create new one at the end
            val newRule = "\n$selector {\n    $property: $value;\n}\n"
            content + newRule
        }

        commit(cssFile, content, newContent)
        return Result.success(Unit)
    }

    /**
     * Update an inline <style> tag in HTML with the style change
     */
    private fun updateInlineStyleTag(
        htmlFile: File,
        htmlContent: String,
        selector: String,
        property: String,
        value: String
    ): Result<Unit> {
        // Find the <style>...</style> block
        val stylePattern = Pattern.compile(
            """(<style[^>]*>)([\s\S]*?)(</style>)""",
            Pattern.CASE_INSENSITIVE
        )
        val styleMatcher = stylePattern.matcher(htmlContent)

        if (!styleMatcher.find()) {
            return Result.failure(Exception("Could not parse style tag"))
        }

        val styleOpenTag = styleMatcher.group(1) ?: "<style>"
        val styleContent = styleMatcher.group(2) ?: ""
        val styleCloseTag = styleMatcher.group(3) ?: "</style>"

        // Try to find existing rule for this selector within the style content
        val rulePattern = Pattern.compile(
            """(${Pattern.quote(selector)})\s*\{([^}]*)\}""",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val ruleMatcher = rulePattern.matcher(styleContent)

        val newStyleContent = if (ruleMatcher.find()) {
            // Found existing rule - update or add the property
            val existingStyles = ruleMatcher.group(2) ?: ""
            val newStyles = updatePropertyInStyles(existingStyles, property, value)
            ruleMatcher.replaceFirst("$selector {\n$newStyles}")
        } else {
            // No existing rule - add new one
            val newRule = "\n        $selector {\n            $property: $value;\n        }"
            styleContent + newRule
        }

        val newHtmlContent = styleMatcher.replaceFirst("$styleOpenTag$newStyleContent\n    $styleCloseTag")
        commit(htmlFile, htmlContent, newHtmlContent)
        return Result.success(Unit)
    }

    /**
     * Create an inline <style> tag in HTML when none exists
     */
    private fun createInlineStyleTag(
        htmlFile: File,
        htmlContent: String,
        selector: String,
        property: String,
        value: String
    ): Result<Unit> {
        val styleBlock = """
    <style>
        $selector {
            $property: $value;
        }
    </style>"""

        // Insert the style tag before </head> if it exists, otherwise before </html> or at the end
        val newHtmlContent = when {
            htmlContent.contains("</head>", ignoreCase = true) -> {
                htmlContent.replaceFirst(
                    Regex("</head>", RegexOption.IGNORE_CASE),
                    "$styleBlock\n</head>"
                )
            }
            htmlContent.contains("<body", ignoreCase = true) -> {
                htmlContent.replaceFirst(
                    Regex("<body", RegexOption.IGNORE_CASE),
                    "$styleBlock\n<body"
                )
            }
            htmlContent.contains("</html>", ignoreCase = true) -> {
                htmlContent.replaceFirst(
                    Regex("</html>", RegexOption.IGNORE_CASE),
                    "$styleBlock\n</html>"
                )
            }
            else -> {
                // No standard HTML structure - prepend the style
                "$styleBlock\n$htmlContent"
            }
        }

        commit(htmlFile, htmlContent, newHtmlContent)
        return Result.success(Unit)
    }

    /**
     * Helper to update or add a property in an existing styles block
     */
    private fun updatePropertyInStyles(existingStyles: String, property: String, value: String): String {
        // Check if property already exists in the rule
        val propertyPattern = Pattern.compile(
            """(${Pattern.quote(property)})\s*:\s*[^;]+;?""",
            Pattern.CASE_INSENSITIVE
        )
        val propertyMatcher = propertyPattern.matcher(existingStyles)

        return if (propertyMatcher.find()) {
            // Replace existing property
            propertyMatcher.replaceFirst("$property: $value;")
        } else {
            // Add new property to the rule
            val trimmedStyles = existingStyles.trim()
            if (trimmedStyles.isEmpty()) {
                "\n    $property: $value;\n"
            } else if (trimmedStyles.endsWith(";")) {
                "$trimmedStyles\n    $property: $value;\n"
            } else {
                "$trimmedStyles;\n    $property: $value;\n"
            }
        }
    }

    /**
     * Write the text change to the exact place it came from. Fails, with a
     * reason the user can act on, rather than guess: the old version replaced
     * every copy of the text in index.html and fell back to a fuzzy match.
     */
    private fun updateHtmlText(request: TextChangeRequest): Result<Unit> {
        val target = request.target
            ?: return Result.failure(Exception("Select the text again, then edit it."))
        val htmlFile = if (request.pagePath != null) {
            SourceTextEdit.pageFile(File(projectPath), request.pagePath)
                ?: return Result.failure(Exception("This page isn't a file in the project, so its text can't be edited here."))
        } else {
            findHtmlFile() ?: return Result.failure(Exception("No HTML file found"))
        }
        val before = htmlFile.readText()
        return when (val outcome = SourceTextEdit.replace(before, target, request.newText, htmlFile.name)) {
            is SourceTextEdit.Outcome.Edited -> {
                commit(htmlFile, before, outcome.html)
                Result.success(Unit)
            }
            is SourceTextEdit.Outcome.Failed -> Result.failure(Exception(outcome.reason))
        }
    }

    /**
     * Update image src attribute in HTML file
     */
    private fun updateImageSrc(request: ImageChangeRequest): Result<Unit> {
        val htmlFile = findHtmlFile() ?: return Result.failure(Exception("No HTML file found"))

        val content = htmlFile.readText()
        val oldSrc = request.oldSrc ?: return Result.failure(Exception("No old source to replace"))
        val newSrc = request.newSrc

        // Find and replace the src attribute
        val srcPattern = Pattern.compile(
            """src\s*=\s*["']${Pattern.quote(oldSrc)}["']""",
            Pattern.CASE_INSENSITIVE
        )

        val matcher = srcPattern.matcher(content)
        if (!matcher.find()) {
            return Result.failure(Exception("Could not find image source to replace"))
        }

        val newContent = matcher.replaceFirst("src=\"$newSrc\"")
        commit(htmlFile, content, newContent)
        return Result.success(Unit)
    }

    /**
     * Find the CSS file in the project: the last stylesheet the page being
     * edited links (its rules win the cascade), else a conventional name.
     * Never the UI kit in oro-ui/, which a theme change rewrites.
     */
    private fun findCssFile(): File? {
        val projectDir = File(projectPath)

        currentPage()?.let { page ->
            val root = runCatching { projectDir.canonicalFile }.getOrNull()
            linkedStylesheets(page.readText()).asReversed().forEach { href ->
                val path = href.substringBefore('?').substringBefore('#')
                val file = if (path.startsWith("/")) File(projectDir, path.trimStart('/')) else File(page.parentFile, path)
                val canonical = runCatching { file.canonicalFile }.getOrNull()
                if (root != null && canonical != null && canonical.isFile && !isKitFile(canonical) &&
                    canonical.path.startsWith(root.path + File.separator)
                ) return canonical
            }
        }

        // First, look in standard locations
        for (fileName in cssFileNames) {
            val file = File(projectDir, fileName)
            if (file.exists()) return file

            // Also check css subdirectory
            val cssDir = File(projectDir, "css/$fileName")
            if (cssDir.exists()) return cssDir

            // Check styles subdirectory
            val stylesDir = File(projectDir, "styles/$fileName")
            if (stylesDir.exists()) return stylesDir
        }

        // If not found, search recursively (up to 2 levels)
        return findFileRecursively(projectDir, ".css", maxDepth = 2)?.takeUnless { isKitFile(it) }
    }

    /** The HTML file of the page being edited, if it is one in the project. */
    private fun currentPage(): File? = pagePath?.let { SourceTextEdit.pageFile(File(projectPath), it) }

    private fun isKitFile(file: File): Boolean = file.parentFile?.name == "oro-ui"

    /**
     * Find the HTML file in the project
     */
    private fun findHtmlFile(): File? {
        currentPage()?.let { return it }
        val projectDir = File(projectPath)

        for (fileName in htmlFileNames) {
            val file = File(projectDir, fileName)
            if (file.exists()) return file
        }

        // Search recursively if not found
        return findFileRecursively(projectDir, ".html", maxDepth = 2)
    }

    /**
     * Recursively find a file with the given extension
     */
    private fun findFileRecursively(dir: File, extension: String, maxDepth: Int, currentDepth: Int = 0): File? {
        if (currentDepth > maxDepth || !dir.exists() || !dir.isDirectory) return null

        val files = dir.listFiles() ?: return null

        // First, check files in current directory
        files.filter { it.isFile && it.name.endsWith(extension) }
            .firstOrNull()?.let { return it }

        // Then, recurse into subdirectories (not the UI kit's, nor hidden ones)
        if (currentDepth < maxDepth) {
            for (subDir in files.filter { it.isDirectory && it.name != "oro-ui" && !it.name.startsWith(".") }) {
                findFileRecursively(subDir, extension, maxDepth, currentDepth + 1)?.let { return it }
            }
        }

        return null
    }

    /**
     * Apply live CSS update to WebView for instant visual feedback
     */
    private suspend fun countMatches(webView: WebView?, selector: String): Int =
        LivePreviewEditor.countMatches(webView, selector)

    /**
     * Single enforcement point for "this edit addresses exactly one element".
     *
     * Checked here, against the live DOM at write time, rather than threaded as
     * a flag through the ~75 places the inspector tabs build change requests.
     * One place to be correct, and it reflects the page as it is now rather
     * than as it was when the element was first inspected.
     *
     * Without this, `document.querySelectorAll(sel).forEach(...)` in the live
     * update and the CSS rule written to disk both applied to *every* match --
     * editing one card restyled all of them, off screen and silently.
     */
    private suspend fun ensureSingleTarget(webView: WebView?, selector: String): Boolean {
        val matches = countMatches(webView, selector)
        return when {
            matches == 1 || matches < 0 -> true
            matches == 0 -> {
                _status.value = DirectEditStatus.Error(
                    "That element is no longer in the page. Re-select it and try again."
                )
                false
            }
            else -> {
                _status.value = DirectEditStatus.Error(
                    "\"$selector\" matches $matches elements, so editing it would change " +
                        "all of them. Give this element an id, or use Agent mode."
                )
                false
            }
        }
    }

    private suspend fun applyLiveStyleUpdate(
        webView: WebView,
        selector: String,
        property: String,
        value: String
    ) {
        LivePreviewEditor.applyStyle(webView, selector, property, value)
    }

    /**
     * Apply live text update to WebView
     */
    private suspend fun applyLiveTextUpdate(
        webView: WebView,
        selector: String,
        newText: String
    ) {
        LivePreviewEditor.applyText(webView, selector, newText)
    }

    /**
     * Apply live image update to WebView
     */
    private suspend fun applyLiveImageUpdate(
        webView: WebView,
        selector: String,
        newSrc: String
    ) {
        LivePreviewEditor.applyImageSrc(webView, selector, newSrc)
    }

    /** Writes [after] over [before] in [file] and remembers it for [undo]. */
    private fun commit(file: File, before: String, after: String) {
        if (before == after) return
        file.writeText(after)
        synchronized(history) {
            history.addLast(Snapshot(file, before, after))
            while (history.size > MAX_UNDO) history.removeFirstOrNull()
            _canUndo.value = true
        }
    }

    /**
     * Put back the file the last edit wrote, once pending edits have landed.
     * Refuses if the file changed since (Orobi or the code editor wrote it),
     * rather than throw that newer work away. [onUndone] runs after a
     * successful undo, to reload the page.
     */
    fun undo(onUndone: () -> Unit = {}) {
        scope.launch {
            debouncedJobs.values.toList().joinAll()
            val snapshot = synchronized(history) {
                history.removeLastOrNull().also { _canUndo.value = history.isNotEmpty() }
            } ?: return@launch
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    check(snapshot.file.readText() == snapshot.after) {
                        "${snapshot.file.name} changed since that edit, so it can't be undone here."
                    }
                    snapshot.file.writeText(snapshot.before)
                }
            }
            result.onSuccess {
                _status.value = DirectEditStatus.Success("Undone")
                onUndone()
            }.onFailure {
                _status.value = DirectEditStatus.Error(it.message ?: "Couldn't undo")
            }
        }
    }

    /**
     * Cancel any pending edit operation
     */
    fun cancel() {
        debouncedJobs.values.forEach { it.cancel() }
        debouncedJobs.clear()
        _status.value = DirectEditStatus.Idle
    }

    /**
     * Reset status to idle
     */
    fun resetStatus() {
        _status.value = DirectEditStatus.Idle
    }

    internal companion object {
        /** Undo steps kept; each holds two copies of one file. */
        private const val MAX_UNDO = 20

        private val LINK_TAG = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val REL_STYLESHEET = Regex("""\brel\s*=\s*["']?[^"'>]*\bstylesheet\b""", RegexOption.IGNORE_CASE)
        private val HREF = Regex("""\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
        private val REL_ALTERNATE = Regex("""\brel\s*=\s*["']?[^"'>]*\balternate\b""", RegexOption.IGNORE_CASE)
        private val PRINT_ONLY = Regex("""\bmedia\s*=\s*["']?\s*print\b""", RegexOption.IGNORE_CASE)

        /**
         * Local hrefs of the stylesheets the page applies on screen, in
         * cascade order: `<link rel="stylesheet">`, not alternate or print-only.
         */
        fun linkedStylesheets(html: String): List<String> = LINK_TAG.findAll(html)
            .map { it.value }
            .filter { REL_STYLESHEET.containsMatchIn(it) && !REL_ALTERNATE.containsMatchIn(it) && !PRINT_ONLY.containsMatchIn(it) }
            .mapNotNull { tag -> HREF.find(tag)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() } }
            .filter { !it.contains("://") && !it.startsWith("//") && !it.startsWith("data:") }
            .toList()
    }
}
