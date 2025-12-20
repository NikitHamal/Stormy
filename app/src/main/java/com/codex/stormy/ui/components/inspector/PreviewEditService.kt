package com.codex.stormy.ui.components.inspector

import android.webkit.WebView
import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.ChatRequestMessage
import com.codex.stormy.data.ai.StreamEvent
import com.codex.stormy.data.ai.ToolCallResponse
import com.codex.stormy.data.ai.tools.StormyTools
import com.codex.stormy.data.ai.tools.ToolExecutor
import com.codex.stormy.data.ai.tools.ToolResult
import com.codex.stormy.data.repository.AiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Status of preview edit operation
 */
sealed class PreviewEditStatus {
    data object Idle : PreviewEditStatus()
    data object Analyzing : PreviewEditStatus()
    data object Editing : PreviewEditStatus()
    data class Success(val message: String) : PreviewEditStatus()
    data class Error(val message: String) : PreviewEditStatus()
}

/**
 * Service for handling direct preview editing via AI agent
 * Allows editing elements without going to chat interface
 */
class PreviewEditService(
    private val aiRepository: AiRepository,
    private val toolExecutor: ToolExecutor,
    private val projectId: String,
    private val scope: CoroutineScope
) {
    private val _status = MutableStateFlow<PreviewEditStatus>(PreviewEditStatus.Idle)
    val status: StateFlow<PreviewEditStatus> = _status.asStateFlow()

    private var currentJob: Job? = null
    private var debouncedJob: Job? = null

    // Debounce delay for batching rapid style changes (e.g., slider dragging)
    private val DEBOUNCE_DELAY_MS = 500L

    /**
     * Apply a style change to an element via AI
     * Uses debouncing for rapid changes (like slider drags) to avoid overwhelming the AI
     * Live preview is applied immediately for instant visual feedback
     */
    fun applyStyleChange(
        request: StyleChangeRequest,
        model: AiModel,
        webView: WebView?,
        onComplete: () -> Unit
    ) {
        // Always apply live preview immediately for instant feedback
        scope.launch {
            webView?.let { wv ->
                applyLiveStyleUpdate(wv, request.selector, request.property, request.newValue)
            }
        }

        // Debounce the actual AI file write to avoid cancellation issues with rapid changes
        debouncedJob?.cancel()
        debouncedJob = scope.launch {
            delay(DEBOUNCE_DELAY_MS)

            // Now start the actual AI edit
            currentJob?.cancel()
            currentJob = scope.launch {
                _status.value = PreviewEditStatus.Analyzing

                try {
                    val prompt = buildStyleChangePrompt(request)
                    val result = executeAiEdit(prompt, model)

                    if (result.success) {
                        _status.value = PreviewEditStatus.Success("Style applied")
                    } else {
                        _status.value = PreviewEditStatus.Error(result.error ?: "Unknown error")
                    }
                } catch (e: CancellationException) {
                    // Silently ignore cancellation - this is expected during rapid changes
                    _status.value = PreviewEditStatus.Idle
                } catch (e: Exception) {
                    _status.value = PreviewEditStatus.Error(e.message ?: "Failed to apply changes")
                } finally {
                    onComplete()
                }
            }
        }
    }

    /**
     * Apply a text content change via AI
     * Similar to style changes, applies live preview immediately
     */
    fun applyTextChange(
        request: TextChangeRequest,
        model: AiModel,
        webView: WebView?,
        onComplete: () -> Unit
    ) {
        // Apply live text update immediately for instant feedback
        scope.launch {
            webView?.let { wv ->
                applyLiveTextUpdate(wv, request.selector, request.newText)
            }
        }

        // Cancel any pending debounced job and start the AI edit
        debouncedJob?.cancel()
        currentJob?.cancel()
        currentJob = scope.launch {
            _status.value = PreviewEditStatus.Analyzing

            try {
                val prompt = buildTextChangePrompt(request)
                val result = executeAiEdit(prompt, model)

                if (result.success) {
                    _status.value = PreviewEditStatus.Success("Text updated")
                } else {
                    _status.value = PreviewEditStatus.Error(result.error ?: "Unknown error")
                }
            } catch (e: CancellationException) {
                // Silently ignore cancellation
                _status.value = PreviewEditStatus.Idle
            } catch (e: Exception) {
                _status.value = PreviewEditStatus.Error(e.message ?: "Failed to apply changes")
            } finally {
                onComplete()
            }
        }
    }

    /**
     * Apply freeform AI edit from user prompt
     */
    fun applyAiEdit(
        prompt: String,
        elementSelector: String,
        elementHtml: String,
        model: AiModel,
        webView: WebView?,
        onComplete: () -> Unit
    ) {
        debouncedJob?.cancel()
        currentJob?.cancel()
        currentJob = scope.launch {
            _status.value = PreviewEditStatus.Analyzing

            try {
                val fullPrompt = buildFreeformEditPrompt(prompt, elementSelector, elementHtml)
                val result = executeAiEdit(fullPrompt, model)

                if (result.success) {
                    _status.value = PreviewEditStatus.Editing

                    // Reload the WebView to show changes
                    withContext(Dispatchers.Main) {
                        webView?.reload()
                    }

                    _status.value = PreviewEditStatus.Success("Changes applied")
                } else {
                    _status.value = PreviewEditStatus.Error(result.error ?: "Unknown error")
                }
            } catch (e: CancellationException) {
                _status.value = PreviewEditStatus.Idle
            } catch (e: Exception) {
                _status.value = PreviewEditStatus.Error(e.message ?: "Failed to apply changes")
            } finally {
                onComplete()
            }
        }
    }

    /**
     * Execute AI edit with tool use
     */
    private suspend fun executeAiEdit(prompt: String, model: AiModel): ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                // Create system message for editing context
                val systemMessage = """You are a web development AI assistant helping to edit HTML/CSS files directly.
                    |
                    |When editing files:
                    |1. First read the relevant file(s) to understand the current state
                    |2. Use the patch_file tool to make precise, targeted changes
                    |3. Only change what's necessary to accomplish the user's request
                    |4. Preserve all existing functionality and styling unless specifically asked to change it
                    |
                    |Available files in the project: index.html, style.css, script.js (and any others in the project)
                    |
                    |IMPORTANT: Make minimal, targeted changes. Do not rewrite entire files.
                """.trimMargin()

                // Build messages using ChatRequestMessage
                val messages = listOf(
                    ChatRequestMessage(role = "system", content = systemMessage),
                    ChatRequestMessage(role = "user", content = prompt)
                )

                // Stream response with tools
                val responseContent = StringBuilder()
                val toolCalls = mutableListOf<ToolCallResponse>()

                aiRepository.streamChat(
                    model = model,
                    messages = messages,
                    tools = StormyTools.getAllTools()
                ).collect { event ->
                    when (event) {
                        is StreamEvent.ContentDelta -> {
                            responseContent.append(event.content)
                        }
                        is StreamEvent.ToolCalls -> {
                            toolCalls.addAll(event.toolCalls)
                        }
                        is StreamEvent.Completed -> {
                            // Processing complete
                        }
                        is StreamEvent.Error -> {
                            throw Exception(event.message)
                        }
                        else -> {}
                    }
                }

                // Process tool calls if any
                if (toolCalls.isNotEmpty()) {
                    var lastResult: ToolResult? = null

                    for (toolCall in toolCalls) {
                        val toolResult = toolExecutor.execute(projectId, toolCall)
                        lastResult = toolResult

                        if (!toolResult.success) {
                            return@withContext toolResult
                        }
                    }

                    return@withContext lastResult ?: ToolResult(true, "Changes applied successfully")
                }

                // No tool calls, return the message content
                ToolResult(
                    success = true,
                    output = responseContent.toString().ifEmpty { "Edit completed" }
                )
            } catch (e: Exception) {
                ToolResult(
                    success = false,
                    output = "",
                    error = e.message ?: "Failed to execute edit"
                )
            }
        }
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
            val script = """
                (function() {
                    var elements = document.querySelectorAll('$selector');
                    elements.forEach(function(el) {
                        el.style['$property'] = '$value';
                    });
                    return elements.length;
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
            val escapedText = newText
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")

            val script = """
                (function() {
                    var elements = document.querySelectorAll('$selector');
                    if (elements.length > 0) {
                        // Get first text node or set textContent
                        var el = elements[0];
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
                })();
            """.trimIndent()

            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Build prompt for style change
     */
    private fun buildStyleChangePrompt(request: StyleChangeRequest): String {
        return """Please update the CSS for the element matching selector "${request.selector}".

Change the "${request.property}" property from "${request.oldValue ?: "unset"}" to "${request.newValue}".

Element HTML for reference:
```html
${request.elementHtml.take(300)}
```

Instructions:
1. First read the CSS file (usually style.css or styles.css)
2. Find or create a rule for this selector
3. Use patch_file to update just the relevant CSS property
4. If the selector doesn't exist, add a new rule with just this property"""
    }

    /**
     * Build prompt for text content change
     */
    private fun buildTextChangePrompt(request: TextChangeRequest): String {
        return """Please update the text content in the HTML file.

Element selector: "${request.selector}"
Old text: "${request.oldText}"
New text: "${request.newText}"

Element HTML for reference:
```html
${request.elementHtml.take(300)}
```

Instructions:
1. First read the HTML file (usually index.html)
2. Find this element and update its text content
3. Use patch_file to make the minimal change needed
4. Preserve all attributes and child elements"""
    }

    /**
     * Build prompt for freeform AI edit
     */
    private fun buildFreeformEditPrompt(
        userPrompt: String,
        selector: String,
        elementHtml: String
    ): String {
        return """User request: $userPrompt

Target element: $selector
Element HTML:
```html
${elementHtml.take(500)}
```

Instructions:
1. Read the relevant files (HTML, CSS, JS as needed)
2. Make the changes requested by the user
3. Use patch_file for precise, targeted modifications
4. Only change what's necessary to accomplish the request
5. Test your changes mentally to ensure they work correctly"""
    }

    /**
     * Apply multi-element AI edit from agent selection mode
     * Processes multiple selected elements with a single prompt
     */
    fun applyAgentEdit(
        prompt: String,
        elements: List<AgentSelectedElement>,
        model: AiModel,
        webView: WebView?,
        onComplete: () -> Unit
    ) {
        debouncedJob?.cancel()
        currentJob?.cancel()
        currentJob = scope.launch {
            _status.value = PreviewEditStatus.Analyzing

            try {
                val fullPrompt = buildAgentEditPrompt(prompt, elements)
                val result = executeAiEdit(fullPrompt, model)

                if (result.success) {
                    _status.value = PreviewEditStatus.Editing

                    // Reload the WebView to show changes
                    withContext(Dispatchers.Main) {
                        webView?.reload()
                    }

                    _status.value = PreviewEditStatus.Success(
                        if (elements.size == 1) "Element updated"
                        else "${elements.size} elements updated"
                    )
                } else {
                    _status.value = PreviewEditStatus.Error(result.error ?: "Unknown error")
                }
            } catch (e: CancellationException) {
                _status.value = PreviewEditStatus.Idle
            } catch (e: Exception) {
                _status.value = PreviewEditStatus.Error(e.message ?: "Failed to apply changes")
            } finally {
                onComplete()
            }
        }
    }

    /**
     * Build prompt for multi-element agent edit
     */
    private fun buildAgentEditPrompt(
        userPrompt: String,
        elements: List<AgentSelectedElement>
    ): String {
        val elementDescriptions = elements.mapIndexed { index, element ->
            """
            |Element ${index + 1}: ${element.selector}
            |```html
            |${element.outerHTML.take(300)}
            |```
            """.trimMargin()
        }.joinToString("\n\n")

        return """User request: $userPrompt

Selected elements (${elements.size} total):
$elementDescriptions

Instructions:
1. Read the relevant files (HTML, CSS, JS as needed)
2. Apply the user's requested changes to ALL the selected elements
3. Use patch_file for precise, targeted modifications
4. If the change affects styling, update the CSS file appropriately
5. If the change affects HTML structure, update the HTML file
6. Ensure consistency across all modified elements
7. Only change what's necessary to accomplish the request"""
    }

    /**
     * Apply image source change via AI
     * Updates the src attribute of an <img> element
     */
    fun applyImageChange(
        request: ImageChangeRequest,
        model: AiModel,
        webView: WebView?,
        onComplete: () -> Unit
    ) {
        // Apply live preview immediately
        scope.launch {
            webView?.let { wv ->
                applyLiveImageUpdate(wv, request.selector, request.newSrc)
            }
        }

        // Execute AI edit to persist the change
        debouncedJob?.cancel()
        currentJob?.cancel()
        currentJob = scope.launch {
            _status.value = PreviewEditStatus.Analyzing

            try {
                val prompt = buildImageChangePrompt(request)
                val result = executeAiEdit(prompt, model)

                if (result.success) {
                    _status.value = PreviewEditStatus.Success("Image updated")
                } else {
                    _status.value = PreviewEditStatus.Error(result.error ?: "Unknown error")
                }
            } catch (e: CancellationException) {
                _status.value = PreviewEditStatus.Idle
            } catch (e: Exception) {
                _status.value = PreviewEditStatus.Error(e.message ?: "Failed to update image")
            } finally {
                onComplete()
            }
        }
    }

    /**
     * Apply live image source update to WebView
     */
    private suspend fun applyLiveImageUpdate(
        webView: WebView,
        selector: String,
        newSrc: String
    ) {
        withContext(Dispatchers.Main) {
            val escapedSrc = newSrc
                .replace("\\", "\\\\")
                .replace("'", "\\'")

            val script = """
                (function() {
                    var elements = document.querySelectorAll('$selector');
                    elements.forEach(function(el) {
                        if (el.tagName === 'IMG') {
                            el.src = '$escapedSrc';
                        }
                    });
                    return elements.length;
                })();
            """.trimIndent()

            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Build prompt for image source change
     */
    private fun buildImageChangePrompt(request: ImageChangeRequest): String {
        return """Please update the image source (src attribute) in the HTML file.

Element selector: "${request.selector}"
Old src: "${request.oldSrc ?: "none"}"
New src: "${request.newSrc}"

Element HTML for reference:
```html
${request.elementHtml.take(300)}
```

Instructions:
1. First read the HTML file (usually index.html)
2. Find the <img> element matching this selector
3. Use patch_file to update the src attribute to the new value
4. Keep all other attributes (alt, class, id, etc.) unchanged"""
    }

    /**
     * Cancel any ongoing edit operation
     */
    fun cancel() {
        debouncedJob?.cancel()
        currentJob?.cancel()
        _status.value = PreviewEditStatus.Idle
    }

    /**
     * Reset status to idle
     */
    fun resetStatus() {
        _status.value = PreviewEditStatus.Idle
    }
}

/**
 * Data class for agent-selected elements
 */
data class AgentSelectedElement(
    val selector: String,
    val outerHTML: String
)

/**
 * Re-export ImageChangeRequest from ElementInspector for convenience
 */
typealias ImageChangeRequest = com.codex.stormy.ui.components.inspector.ImageChangeRequest
