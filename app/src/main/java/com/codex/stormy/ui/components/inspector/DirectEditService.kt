package com.codex.stormy.ui.components.inspector

import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private var debouncedJob: Job? = null

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
        // Apply live preview immediately for instant visual feedback
        scope.launch {
            webView?.let { wv ->
                applyLiveStyleUpdate(wv, request.selector, request.property, request.newValue)
            }
        }

        // Debounce the file write to batch rapid changes
        debouncedJob?.cancel()
        debouncedJob = scope.launch {
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
                onComplete()
            }
        }
    }

    /**
     * Apply a text content change directly to HTML file
     */
    fun applyTextChange(
        request: TextChangeRequest,
        webView: WebView?,
        onComplete: () -> Unit
    ) {
        // Apply live preview immediately
        scope.launch {
            webView?.let { wv ->
                applyLiveTextUpdate(wv, request.selector, request.newText)
            }
        }

        // Write to file
        debouncedJob?.cancel()
        debouncedJob = scope.launch {
            delay(DEBOUNCE_DELAY_MS)

            _status.value = DirectEditStatus.Editing

            try {
                val result = withContext(Dispatchers.IO) {
                    updateHtmlText(request)
                }

                if (result.isSuccess) {
                    _status.value = DirectEditStatus.Success("Text updated")
                } else {
                    _status.value = DirectEditStatus.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _status.value = DirectEditStatus.Error(e.message ?: "Failed to update text")
            } finally {
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
            webView?.let { wv ->
                applyLiveImageUpdate(wv, request.selector, request.newSrc)
            }
        }

        // Write to file
        debouncedJob?.cancel()
        debouncedJob = scope.launch {
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
     */
    private fun updateCssFile(request: StyleChangeRequest): Result<Unit> {
        // Find the CSS file
        val cssFile = findCssFile() ?: return Result.failure(Exception("No CSS file found"))

        val content = cssFile.readText()
        val selector = request.selector
        val property = request.property
        val value = request.newValue

        // Try to find existing rule for this selector
        val rulePattern = Pattern.compile(
            """(${Pattern.quote(selector)})\s*\{([^}]*)\}""",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val matcher = rulePattern.matcher(content)

        val newContent = if (matcher.find()) {
            // Found existing rule - update or add the property
            val existingStyles = matcher.group(2) ?: ""

            // Check if property already exists in the rule
            val propertyPattern = Pattern.compile(
                """(${Pattern.quote(property)})\s*:\s*[^;]+;?""",
                Pattern.CASE_INSENSITIVE
            )
            val propertyMatcher = propertyPattern.matcher(existingStyles)

            val newStyles = if (propertyMatcher.find()) {
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

            matcher.replaceFirst("$selector {\n$newStyles}")
        } else {
            // No existing rule - create new one at the end
            val newRule = "\n$selector {\n    $property: $value;\n}\n"
            content + newRule
        }

        cssFile.writeText(newContent)
        return Result.success(Unit)
    }

    /**
     * Update HTML file with text content change
     * This is a simplified implementation - for complex cases, use AI service
     */
    private fun updateHtmlText(request: TextChangeRequest): Result<Unit> {
        val htmlFile = findHtmlFile() ?: return Result.failure(Exception("No HTML file found"))

        val content = htmlFile.readText()
        val oldText = request.oldText
        val newText = request.newText

        // Simple text replacement - find and replace the old text
        if (!content.contains(oldText)) {
            return Result.failure(Exception("Could not find text to replace"))
        }

        val newContent = content.replace(oldText, newText)
        htmlFile.writeText(newContent)
        return Result.success(Unit)
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
        htmlFile.writeText(newContent)
        return Result.success(Unit)
    }

    /**
     * Find the CSS file in the project
     */
    private fun findCssFile(): File? {
        val projectDir = File(projectPath)

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
        return findFileRecursively(projectDir, ".css", maxDepth = 2)
    }

    /**
     * Find the HTML file in the project
     */
    private fun findHtmlFile(): File? {
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

        // Then, recurse into subdirectories
        if (currentDepth < maxDepth) {
            for (subDir in files.filter { it.isDirectory }) {
                findFileRecursively(subDir, extension, maxDepth, currentDepth + 1)?.let { return it }
            }
        }

        return null
    }

    /**
     * Apply live CSS update to WebView for instant visual feedback
     */
    private suspend fun applyLiveStyleUpdate(
        webView: WebView,
        selector: String,
        property: String,
        value: String
    ) {
        withContext(Dispatchers.Main) {
            val escapedSelector = selector.replace("'", "\\'")
            val escapedProperty = property.replace("'", "\\'")
            val escapedValue = value.replace("'", "\\'")

            val script = """
                (function() {
                    try {
                        var elements = document.querySelectorAll('$escapedSelector');
                        elements.forEach(function(el) {
                            el.style['$escapedProperty'] = '$escapedValue';
                        });
                        return elements.length;
                    } catch(e) {
                        return 0;
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Apply live text update to WebView
     */
    private suspend fun applyLiveTextUpdate(
        webView: WebView,
        selector: String,
        newText: String
    ) {
        withContext(Dispatchers.Main) {
            val escapedSelector = selector.replace("'", "\\'")
            val escapedText = newText
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")

            val script = """
                (function() {
                    try {
                        var elements = document.querySelectorAll('$escapedSelector');
                        if (elements.length > 0) {
                            var el = elements[0];
                            // Find first text node
                            var textNode = null;
                            for (var i = 0; i < el.childNodes.length; i++) {
                                if (el.childNodes[i].nodeType === Node.TEXT_NODE && el.childNodes[i].textContent.trim()) {
                                    textNode = el.childNodes[i];
                                    break;
                                }
                            }
                            if (textNode) {
                                textNode.textContent = '$escapedText';
                            } else if (!el.children.length) {
                                el.textContent = '$escapedText';
                            }
                        }
                        return elements.length;
                    } catch(e) {
                        return 0;
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Apply live image update to WebView
     */
    private suspend fun applyLiveImageUpdate(
        webView: WebView,
        selector: String,
        newSrc: String
    ) {
        withContext(Dispatchers.Main) {
            val escapedSelector = selector.replace("'", "\\'")
            val escapedSrc = newSrc
                .replace("\\", "\\\\")
                .replace("'", "\\'")

            val script = """
                (function() {
                    try {
                        var elements = document.querySelectorAll('$escapedSelector');
                        elements.forEach(function(el) {
                            if (el.tagName === 'IMG') {
                                el.src = '$escapedSrc';
                            }
                        });
                        return elements.length;
                    } catch(e) {
                        return 0;
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Cancel any pending edit operation
     */
    fun cancel() {
        debouncedJob?.cancel()
        _status.value = DirectEditStatus.Idle
    }

    /**
     * Reset status to idle
     */
    fun resetStatus() {
        _status.value = DirectEditStatus.Idle
    }
}
