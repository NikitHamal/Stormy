package com.codex.stormy.ui.components.inspector

import android.util.Log
import android.webkit.WebView
import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.ChatRequestMessage
import com.codex.stormy.data.ai.StreamEvent
import com.codex.stormy.data.ai.ToolCallRequest
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

private const val TAG = "PreviewEditService"

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
 * Result of AI edit execution with detailed information
 */
data class AiEditResult(
    val success: Boolean,
    val message: String,
    val toolCallsExecuted: Int = 0,
    val filesModified: List<String> = emptyList(),
    val error: String? = null
)

/**
 * Service for handling direct preview editing via AI agent.
 * Allows editing elements without going to chat interface.
 *
 * This service handles:
 * - Style changes (CSS property modifications)
 * - Text content changes
 * - Image source changes
 * - Multi-element agent edits
 *
 * Changes are applied both as live preview (instant visual feedback)
 * and persisted to files (via AI tool calls).
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

    // Maximum turns for multi-turn tool execution
    private val MAX_TOOL_TURNS = 5

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

                    if (result.success && result.toolCallsExecuted > 0) {
                        _status.value = PreviewEditStatus.Success("Style applied")
                    } else if (result.success && result.toolCallsExecuted == 0) {
                        // AI didn't make any tool calls - this is a failure for style changes
                        Log.w(TAG, "AI did not execute any tool calls for style change")
                        _status.value = PreviewEditStatus.Error("AI did not modify any files")
                    } else {
                        _status.value = PreviewEditStatus.Error(result.error ?: "Unknown error")
                    }
                } catch (e: CancellationException) {
                    // Silently ignore cancellation - this is expected during rapid changes
                    _status.value = PreviewEditStatus.Idle
                } catch (e: Exception) {
                    Log.e(TAG, "Style change failed", e)
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

                if (result.success && result.toolCallsExecuted > 0) {
                    _status.value = PreviewEditStatus.Success("Text updated")
                } else if (result.success && result.toolCallsExecuted == 0) {
                    Log.w(TAG, "AI did not execute any tool calls for text change")
                    _status.value = PreviewEditStatus.Error("AI did not modify any files")
                } else {
                    _status.value = PreviewEditStatus.Error(result.error ?: "Unknown error")
                }
            } catch (e: CancellationException) {
                _status.value = PreviewEditStatus.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Text change failed", e)
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

                if (result.success && result.toolCallsExecuted > 0) {
                    _status.value = PreviewEditStatus.Editing

                    // Reload the WebView to show changes
                    withContext(Dispatchers.Main) {
                        webView?.reload()
                    }

                    _status.value = PreviewEditStatus.Success("Changes applied")
                } else if (result.success && result.toolCallsExecuted == 0) {
                    Log.w(TAG, "AI did not execute any tool calls for freeform edit")
                    _status.value = PreviewEditStatus.Error("AI did not modify any files. Try rephrasing your request.")
                } else {
                    _status.value = PreviewEditStatus.Error(result.error ?: "Unknown error")
                }
            } catch (e: CancellationException) {
                _status.value = PreviewEditStatus.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Freeform edit failed", e)
                _status.value = PreviewEditStatus.Error(e.message ?: "Failed to apply changes")
            } finally {
                onComplete()
            }
        }
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
                Log.d(TAG, "Starting agent edit with ${elements.size} elements")
                Log.d(TAG, "Prompt: $prompt")
                Log.d(TAG, "Model: ${model.id}, supportsToolCalls: ${model.supportsToolCalls}")

                val fullPrompt = buildAgentEditPrompt(prompt, elements)
                val result = executeAiEdit(fullPrompt, model)

                Log.d(TAG, "Agent edit result: success=${result.success}, toolCallsExecuted=${result.toolCallsExecuted}, files=${result.filesModified}")

                if (result.success && result.toolCallsExecuted > 0) {
                    _status.value = PreviewEditStatus.Editing

                    // Small delay to ensure file changes are complete
                    delay(100)

                    // Reload the WebView to show changes
                    withContext(Dispatchers.Main) {
                        webView?.reload()
                    }

                    val successMessage = if (elements.size == 1) {
                        "Element updated"
                    } else {
                        "${elements.size} elements updated"
                    }
                    _status.value = PreviewEditStatus.Success(successMessage)
                } else if (result.success && result.toolCallsExecuted == 0) {
                    // AI responded but didn't execute any tool calls
                    Log.w(TAG, "AI did not execute any tool calls. Message: ${result.message}")

                    // Check if the model supports tool calls
                    if (!model.supportsToolCalls) {
                        _status.value = PreviewEditStatus.Error(
                            "This model doesn't support tool calls. Please select a different model."
                        )
                    } else {
                        _status.value = PreviewEditStatus.Error(
                            "AI did not modify any files. Try being more specific about what changes you want."
                        )
                    }
                } else {
                    _status.value = PreviewEditStatus.Error(result.error ?: "Unknown error")
                }
            } catch (e: CancellationException) {
                _status.value = PreviewEditStatus.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Agent edit failed", e)
                _status.value = PreviewEditStatus.Error(e.message ?: "Failed to apply changes")
            } finally {
                onComplete()
            }
        }
    }

    /**
     * Execute AI edit with multi-turn tool use support.
     * Handles the complete cycle of:
     * 1. Initial AI response with potential tool calls
     * 2. Tool execution
     * 3. Feeding results back to AI
     * 4. Continuing until AI completes or max turns reached
     */
    private suspend fun executeAiEdit(prompt: String, model: AiModel): AiEditResult {
        return withContext(Dispatchers.IO) {
            try {
                // Check if model supports tool calls
                if (!model.supportsToolCalls) {
                    return@withContext AiEditResult(
                        success = false,
                        message = "Model does not support tool calls",
                        error = "Please select a model that supports function calling/tool use"
                    )
                }

                // Create system message for editing context
                val systemMessage = buildSystemPrompt()

                // Build initial message list
                val messages = mutableListOf(
                    ChatRequestMessage(role = "system", content = systemMessage),
                    ChatRequestMessage(role = "user", content = prompt)
                )

                var totalToolCalls = 0
                val modifiedFiles = mutableListOf<String>()
                var turns = 0

                // Multi-turn loop to handle AI tool calls
                while (turns < MAX_TOOL_TURNS) {
                    turns++
                    Log.d(TAG, "AI turn $turns")

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
                                Log.d(TAG, "Received ${event.toolCalls.size} tool calls")
                            }
                            is StreamEvent.Completed -> {
                                Log.d(TAG, "Stream completed")
                            }
                            is StreamEvent.Error -> {
                                throw Exception(event.message)
                            }
                            else -> {}
                        }
                    }

                    Log.d(TAG, "Turn $turns: content=${responseContent.length} chars, toolCalls=${toolCalls.size}")

                    // If no tool calls, the AI has finished
                    if (toolCalls.isEmpty()) {
                        Log.d(TAG, "No tool calls, AI finished. Total tool calls executed: $totalToolCalls")

                        return@withContext AiEditResult(
                            success = true,
                            message = responseContent.toString().ifEmpty { "Edit completed" },
                            toolCallsExecuted = totalToolCalls,
                            filesModified = modifiedFiles.distinct()
                        )
                    }

                    // Add assistant message with tool calls to conversation history
                    val assistantContent = if (responseContent.isNotEmpty()) responseContent.toString() else null
                    // Convert ToolCallResponse to ToolCallRequest for ChatRequestMessage
                    val toolCallRequests = toolCalls.map { response ->
                        ToolCallRequest(
                            id = response.id,
                            type = response.type,
                            function = response.function
                        )
                    }
                    messages.add(
                        ChatRequestMessage(
                            role = "assistant",
                            content = assistantContent,
                            toolCalls = toolCallRequests
                        )
                    )

                    // Execute each tool call and collect results
                    for (toolCall in toolCalls) {
                        Log.d(TAG, "Executing tool: ${toolCall.function.name}")
                        val toolResult = toolExecutor.execute(projectId, toolCall)
                        totalToolCalls++

                        Log.d(TAG, "Tool ${toolCall.function.name} result: success=${toolResult.success}")

                        // Track file modifications
                        if (toolResult.success && isFileModificationTool(toolCall.function.name)) {
                            val path = extractPathFromToolCall(toolCall)
                            if (path != null && !modifiedFiles.contains(path)) {
                                modifiedFiles.add(path)
                            }
                        }

                        // Add tool result to messages
                        messages.add(
                            ChatRequestMessage(
                                role = "tool",
                                content = if (toolResult.success) toolResult.output else (toolResult.error ?: "Tool execution failed"),
                                toolCallId = toolCall.id,
                                name = toolCall.function.name
                            )
                        )

                        // If a tool failed, report the error but continue
                        if (!toolResult.success) {
                            Log.w(TAG, "Tool ${toolCall.function.name} failed: ${toolResult.error}")
                        }
                    }
                }

                // Max turns reached
                Log.w(TAG, "Max turns ($MAX_TOOL_TURNS) reached")
                AiEditResult(
                    success = true,
                    message = "Edit completed (max turns reached)",
                    toolCallsExecuted = totalToolCalls,
                    filesModified = modifiedFiles.distinct()
                )
            } catch (e: Exception) {
                Log.e(TAG, "executeAiEdit failed", e)
                AiEditResult(
                    success = false,
                    message = "",
                    error = e.message ?: "Failed to execute edit"
                )
            }
        }
    }

    /**
     * Build the system prompt that guides the AI's behavior
     */
    private fun buildSystemPrompt(): String {
        return """You are a web development AI assistant that directly modifies HTML/CSS/JS files.

CRITICAL INSTRUCTIONS:
1. You MUST use tools to make changes. Do NOT just describe what you would do.
2. ALWAYS use read_file first to see the current file contents before making changes.
3. Use patch_file for precise, targeted modifications (preferred for small changes).
4. Use write_file only when creating new files or making large changes.
5. Make the MINIMUM changes necessary to accomplish the task.

WORKFLOW:
1. Read the relevant file(s) to understand current state
2. Identify exactly what needs to change
3. Use patch_file with old_content and new_content to make precise changes
4. DO NOT rewrite entire files unless absolutely necessary

IMPORTANT:
- The project typically has index.html, style.css, and script.js files
- CSS changes should modify style.css (or the main CSS file)
- HTML structure changes should modify index.html (or the main HTML file)
- Always preserve existing code that isn't being changed
- Use valid CSS/HTML syntax

You have access to these tools:
- read_file: Read file contents
- write_file: Create or overwrite a file
- patch_file: Make precise changes to specific parts of a file (PREFERRED)
- list_files: List project files

START IMMEDIATELY with tool calls. Do not ask for confirmation."""
    }

    /**
     * Check if a tool name is a file modification tool
     */
    private fun isFileModificationTool(toolName: String): Boolean {
        return toolName in listOf("write_file", "patch_file", "delete_file", "rename_file")
    }

    /**
     * Extract file path from tool call arguments
     */
    private fun extractPathFromToolCall(toolCall: ToolCallResponse): String? {
        return try {
            val json = kotlinx.serialization.json.Json.parseToJsonElement(toolCall.function.arguments)
            json.jsonObject["path"]?.let {
                it.jsonPrimitive.content
            }
        } catch (e: Exception) {
            null
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
                    } catch (e) {
                        console.error('Live style update error:', e);
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
                .replace("\r", "\\r")

            val script = """
                (function() {
                    try {
                        var elements = document.querySelectorAll('$escapedSelector');
                        if (elements.length > 0) {
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
                    } catch (e) {
                        console.error('Live text update error:', e);
                        return 0;
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(script, null)
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
                    } catch (e) {
                        console.error('Live image update error:', e);
                        return 0;
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Build prompt for style change
     */
    private fun buildStyleChangePrompt(request: StyleChangeRequest): String {
        return """Update the CSS for element matching selector "${request.selector}".

TASK: Change the "${request.property}" property from "${request.oldValue ?: "unset"}" to "${request.newValue}".

Element HTML for reference:
```html
${request.elementHtml.take(500)}
```

INSTRUCTIONS:
1. First, read the CSS file (try style.css, then styles.css, then main.css)
2. Find or create a CSS rule for the selector "${request.selector}"
3. Use patch_file to update ONLY the "${request.property}" property
4. If the selector doesn't exist in the CSS file, add a new rule

Example patch for an existing rule:
- old_content: "background-color: blue;"
- new_content: "background-color: red;"

START NOW - read the CSS file first."""
    }

    /**
     * Build prompt for text content change
     */
    private fun buildTextChangePrompt(request: TextChangeRequest): String {
        return """Update the text content in the HTML file.

TASK: Change the text content of element "${request.selector}".

OLD TEXT: "${request.oldText}"
NEW TEXT: "${request.newText}"

Element HTML for reference:
```html
${request.elementHtml.take(500)}
```

INSTRUCTIONS:
1. First, read index.html (or the main HTML file)
2. Find the element and locate the text to change
3. Use patch_file to replace ONLY the old text with the new text
4. Preserve all HTML tags, attributes, and surrounding content

START NOW - read index.html first."""
    }

    /**
     * Build prompt for freeform AI edit
     */
    private fun buildFreeformEditPrompt(
        userPrompt: String,
        selector: String,
        elementHtml: String
    ): String {
        return """USER REQUEST: $userPrompt

TARGET ELEMENT: $selector

Element HTML:
```html
${elementHtml.take(500)}
```

INSTRUCTIONS:
1. Read the relevant file(s) first to understand the current state
2. Make the specific changes requested
3. Use patch_file for precise modifications
4. Only change what's necessary for the user's request

START NOW - read the relevant files and make the changes."""
    }

    /**
     * Build prompt for multi-element agent edit
     */
    private fun buildAgentEditPrompt(
        userPrompt: String,
        elements: List<AgentSelectedElement>
    ): String {
        val elementDescriptions = elements.mapIndexed { index, element ->
            """Element ${index + 1}: ${element.selector}
```html
${element.outerHTML.take(300)}
```"""
        }.joinToString("\n\n")

        return """USER REQUEST: $userPrompt

SELECTED ELEMENTS (${elements.size} total):
$elementDescriptions

INSTRUCTIONS:
1. Read the HTML and CSS files to understand the current state
2. Apply the user's requested changes to ALL the selected elements
3. Use patch_file for each modification
4. If multiple elements share a class, you can modify the CSS class instead of inline styles
5. Ensure consistency across all modified elements

START NOW - read the files and apply the changes to all selected elements."""
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

                if (result.success && result.toolCallsExecuted > 0) {
                    _status.value = PreviewEditStatus.Success("Image updated")
                } else if (result.success && result.toolCallsExecuted == 0) {
                    Log.w(TAG, "AI did not execute any tool calls for image change")
                    _status.value = PreviewEditStatus.Error("AI did not modify any files")
                } else {
                    _status.value = PreviewEditStatus.Error(result.error ?: "Unknown error")
                }
            } catch (e: CancellationException) {
                _status.value = PreviewEditStatus.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Image change failed", e)
                _status.value = PreviewEditStatus.Error(e.message ?: "Failed to update image")
            } finally {
                onComplete()
            }
        }
    }

    /**
     * Build prompt for image source change
     */
    private fun buildImageChangePrompt(request: ImageChangeRequest): String {
        return """Update the image source (src attribute) in the HTML file.

TASK: Change the src attribute of the image element.

Element selector: "${request.selector}"
Old src: "${request.oldSrc ?: "none"}"
New src: "${request.newSrc}"

Element HTML for reference:
```html
${request.elementHtml.take(300)}
```

INSTRUCTIONS:
1. First read index.html (or the main HTML file)
2. Find the <img> element matching this selector
3. Use patch_file to update ONLY the src attribute to the new value
4. Keep all other attributes (alt, class, id, etc.) unchanged

Example patch:
- old_content: src="old-image.jpg"
- new_content: src="${request.newSrc}"

START NOW - read index.html first."""
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

// Extension to access JsonObject
private val kotlinx.serialization.json.JsonElement.jsonObject: kotlinx.serialization.json.JsonObject
    get() = this as kotlinx.serialization.json.JsonObject

private val kotlinx.serialization.json.JsonElement.jsonPrimitive: kotlinx.serialization.json.JsonPrimitive
    get() = this as kotlinx.serialization.json.JsonPrimitive
