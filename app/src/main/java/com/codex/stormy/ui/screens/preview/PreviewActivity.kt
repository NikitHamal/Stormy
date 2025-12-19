package com.codex.stormy.ui.screens.preview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.codex.stormy.ui.theme.CodeXTheme
import org.json.JSONObject
import java.io.File

class PreviewActivity : ComponentActivity() {

    private var projectId: String = ""
    private var projectPath: String = ""
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: ""
        projectPath = intent.getStringExtra(EXTRA_PROJECT_PATH) ?: ""

        setContent {
            CodeXTheme {
                PreviewScreen(
                    projectId = projectId,
                    projectPath = projectPath,
                    onBackClick = { finish() },
                    onWebViewCreated = { webView = it },
                    onAgentEditRequest = { elements, prompt ->
                        // Return the agent edit request to the calling activity
                        val intent = Intent().apply {
                            putExtra(RESULT_AGENT_PROMPT, prompt)
                            putExtra(RESULT_SELECTED_ELEMENTS, elements.map { it.outerHTML }.toTypedArray())
                            putExtra(RESULT_ELEMENT_SELECTORS, elements.map { buildElementSelector(it) }.toTypedArray())
                        }
                        setResult(Activity.RESULT_OK, intent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.destroy()
    }

    companion object {
        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_PROJECT_PATH = "extra_project_path"

        // Result extras for agent edit mode
        const val RESULT_AGENT_PROMPT = "result_agent_prompt"
        const val RESULT_SELECTED_ELEMENTS = "result_selected_elements"
        const val RESULT_ELEMENT_SELECTORS = "result_element_selectors"
    }
}

data class ConsoleLogEntry(
    val message: String,
    val level: ConsoleMessage.MessageLevel,
    val lineNumber: Int,
    val sourceId: String
)

/**
 * Represents an inspected HTML element with its properties
 */
data class InspectedElement(
    val tagName: String,
    val id: String,
    val className: String,
    val innerHTML: String,
    val outerHTML: String,
    val computedStyles: Map<String, String>,
    val attributes: Map<String, String>,
    val boundingRect: ElementRect
)

data class ElementRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

/**
 * JavaScript interface for receiving element data from WebView
 */
class InspectorJsInterface(
    private val onElementInspected: (InspectedElement) -> Unit,
    private val onMultiSelectElement: (InspectedElement) -> Unit = {}
) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @JavascriptInterface
    fun onElementSelected(jsonData: String) {
        try {
            val element = parseElementJson(jsonData)
            mainHandler.post {
                onElementInspected(element)
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    @JavascriptInterface
    fun onMultiElementSelected(jsonData: String) {
        try {
            val element = parseElementJson(jsonData)
            mainHandler.post {
                onMultiSelectElement(element)
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    private fun parseElementJson(jsonData: String): InspectedElement {
        val json = JSONObject(jsonData)

        // Parse computed styles
        val stylesJson = json.optJSONObject("computedStyles") ?: JSONObject()
        val styles = mutableMapOf<String, String>()
        stylesJson.keys().forEach { key ->
            styles[key] = stylesJson.optString(key, "")
        }

        // Parse attributes
        val attrsJson = json.optJSONObject("attributes") ?: JSONObject()
        val attributes = mutableMapOf<String, String>()
        attrsJson.keys().forEach { key ->
            attributes[key] = attrsJson.optString(key, "")
        }

        // Parse bounding rect
        val rectJson = json.optJSONObject("boundingRect") ?: JSONObject()
        val rect = ElementRect(
            x = rectJson.optDouble("x", 0.0).toFloat(),
            y = rectJson.optDouble("y", 0.0).toFloat(),
            width = rectJson.optDouble("width", 0.0).toFloat(),
            height = rectJson.optDouble("height", 0.0).toFloat()
        )

        return InspectedElement(
            tagName = json.optString("tagName", ""),
            id = json.optString("id", ""),
            className = json.optString("className", ""),
            innerHTML = json.optString("innerHTML", ""),
            outerHTML = json.optString("outerHTML", ""),
            computedStyles = styles,
            attributes = attributes,
            boundingRect = rect
        )
    }
}

@Composable
private fun PreviewScreen(
    projectId: String,
    projectPath: String,
    onBackClick: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onAgentEditRequest: (List<InspectedElement>, String) -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Preview") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(false) }
    var serverRunning by remember { mutableStateOf(true) }
    var showConsole by remember { mutableStateOf(false) }
    val consoleLogs = remember { mutableStateListOf<ConsoleLogEntry>() }

    // Inspector state (single element)
    var inspectorEnabled by remember { mutableStateOf(false) }
    var inspectedElement by remember { mutableStateOf<InspectedElement?>(null) }
    var showInspectorPanel by remember { mutableStateOf(false) }

    // Agent selection mode state (multi-element)
    var agentSelectionMode by remember { mutableStateOf(false) }
    val selectedElements = remember { mutableStateListOf<InspectedElement>() }

    // Apply desktop mode settings when changed
    LaunchedEffect(desktopMode) {
        webView?.applyViewportMode(desktopMode)
    }

    // Apply inspector mode when changed
    LaunchedEffect(inspectorEnabled) {
        webView?.let { wv ->
            if (inspectorEnabled) {
                wv.injectInspectorScript(multiSelect = false)
            } else if (!agentSelectionMode) {
                wv.disableInspector()
                inspectedElement = null
            }
        }
    }

    // Apply agent selection mode when changed
    LaunchedEffect(agentSelectionMode) {
        webView?.let { wv ->
            if (agentSelectionMode) {
                // Disable single inspector mode
                inspectorEnabled = false
                showInspectorPanel = false
                inspectedElement = null
                selectedElements.clear()
                wv.injectInspectorScript(multiSelect = true)
            } else {
                wv.disableInspector()
                selectedElements.clear()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = pageTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Element inspector toggle
                    IconButton(onClick = {
                        if (agentSelectionMode) {
                            // Can't enable inspector while in agent selection mode
                            return@IconButton
                        }
                        inspectorEnabled = !inspectorEnabled
                        if (inspectorEnabled) {
                            showInspectorPanel = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.TouchApp,
                            contentDescription = if (inspectorEnabled) "Disable inspector"
                            else "Enable inspector",
                            tint = when {
                                agentSelectionMode -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                inspectorEnabled -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    // Agent selection mode toggle
                    BadgedBox(
                        badge = {
                            if (selectedElements.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(selectedElements.size.toString())
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = {
                            agentSelectionMode = !agentSelectionMode
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = if (agentSelectionMode) "Disable agent selection"
                                else "Enable agent selection mode",
                                tint = if (agentSelectionMode) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(onClick = { webView?.reload() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh"
                        )
                    }

                    IconButton(onClick = { showConsole = !showConsole }) {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = "Console",
                            tint = if (showConsole) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (desktopMode) "Mobile mode" else "Desktop mode") },
                                onClick = {
                                    desktopMode = !desktopMode
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.DesktopWindows,
                                        contentDescription = null,
                                        tint = if (desktopMode) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open in browser") },
                                onClick = {
                                    val indexFile = File(projectPath, "index.html")
                                    if (indexFile.exists()) {
                                        try {
                                            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                                FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    indexFile
                                                )
                                            } else {
                                                Uri.fromFile(indexFile)
                                            }
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "text/html")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "No app available to open HTML files",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.OpenInBrowser,
                                        contentDescription = null
                                    )
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (serverRunning) "Stop server" else "Start server") },
                                onClick = {
                                    serverRunning = !serverRunning
                                    if (serverRunning) {
                                        webView?.reload()
                                    } else {
                                        webView?.loadUrl("about:blank")
                                    }
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (serverRunning) Icons.Outlined.Stop
                                        else Icons.Outlined.PlayArrow,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                WebViewPreview(
                    projectPath = projectPath,
                    modifier = Modifier.fillMaxSize(),
                    inspectorEnabled = inspectorEnabled || agentSelectionMode,
                    onWebViewCreated = { wv ->
                        webView = wv
                        onWebViewCreated(wv)
                    },
                    onTitleChanged = { title ->
                        pageTitle = title.ifEmpty { "Preview" }
                    },
                    onConsoleMessage = { entry ->
                        consoleLogs.add(entry)
                    },
                    onElementInspected = { element ->
                        if (agentSelectionMode) {
                            // In agent selection mode, toggle element selection
                            val existingIndex = selectedElements.indexOfFirst {
                                it.outerHTML == element.outerHTML
                            }
                            if (existingIndex >= 0) {
                                selectedElements.removeAt(existingIndex)
                            } else {
                                selectedElements.add(element)
                            }
                        } else {
                            // Single element inspection
                            inspectedElement = element
                            showInspectorPanel = true
                        }
                    }
                )

                // Inspector mode indicator overlay
                if (inspectorEnabled && !agentSelectionMode) {
                    InspectorModeIndicator(
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                // Agent selection mode indicator
                if (agentSelectionMode) {
                    AgentSelectionIndicator(
                        selectedCount = selectedElements.size,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }

            // Agent selection floating prompt bar
            AnimatedVisibility(
                visible = agentSelectionMode && selectedElements.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                AgentPromptBar(
                    selectedElements = selectedElements.toList(),
                    onClearSelection = { selectedElements.clear() },
                    onSubmit = { prompt ->
                        onAgentEditRequest(selectedElements.toList(), prompt)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Inspector panel
            AnimatedVisibility(
                visible = showInspectorPanel && inspectedElement != null && !agentSelectionMode,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                inspectedElement?.let { element ->
                    InspectorPanel(
                        element = element,
                        onClose = {
                            showInspectorPanel = false
                            inspectorEnabled = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                }
            }

            // Console panel
            AnimatedVisibility(
                visible = showConsole && !showInspectorPanel && !agentSelectionMode,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                ConsolePanel(
                    logs = consoleLogs,
                    onClear = { consoleLogs.clear() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}

/**
 * Indicator shown when inspector mode is active
 */
@Composable
private fun InspectorModeIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = "Tap element to inspect",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * Indicator shown when agent selection mode is active
 */
@Composable
private fun AgentSelectionIndicator(
    selectedCount: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = if (selectedCount == 0) "Tap elements to select for AI editing"
                else "$selectedCount element${if (selectedCount > 1) "s" else ""} selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Floating prompt bar for agent selection mode
 * Allows users to input instructions for AI-driven element editing
 */
@Composable
private fun AgentPromptBar(
    selectedElements: List<InspectedElement>,
    onClearSelection: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var promptText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .imePadding()
        ) {
            // Selected elements preview
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(selectedElements) { element ->
                    SelectedElementChip(
                        element = element,
                        onRemove = {
                            // This would need to be handled by the parent
                        }
                    )
                }
            }

            // Prompt input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Clear selection button
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Clear selection",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Prompt text field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = false,
                        maxLines = 3,
                        decorationBox = { innerTextField ->
                            Box {
                                if (promptText.isEmpty()) {
                                    Text(
                                        text = "Describe what to change...",
                                        style = TextStyle(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // Send button
                IconButton(
                    onClick = {
                        if (promptText.isNotBlank()) {
                            keyboardController?.hide()
                            onSubmit(promptText)
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (promptText.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send to AI",
                        tint = if (promptText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Quick action suggestions
            LazyRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf(
                    "Change the color",
                    "Make it larger",
                    "Add animation",
                    "Center this element",
                    "Add hover effect"
                )
                items(suggestions) { suggestion ->
                    QuickSuggestionChip(
                        text = suggestion,
                        onClick = { promptText = suggestion }
                    )
                }
            }
        }
    }
}

/**
 * Chip displaying a selected element
 */
@Composable
private fun SelectedElementChip(
    element: InspectedElement,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = buildElementSelector(element),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Quick suggestion chip for common editing actions
 */
@Composable
private fun QuickSuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ConsolePanel(
    logs: List<ConsoleLogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new logs are added
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Console",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Clear",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable { onClear() }
            )
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "No console output yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { log ->
                    ConsoleLogItem(log)
                }
            }
        }
    }
}

@Composable
private fun ConsoleLogItem(log: ConsoleLogEntry) {
    val color = when (log.level) {
        ConsoleMessage.MessageLevel.ERROR -> Color(0xFFFF6B6B)
        ConsoleMessage.MessageLevel.WARNING -> Color(0xFFFFB347)
        ConsoleMessage.MessageLevel.LOG -> MaterialTheme.colorScheme.onSurface
        ConsoleMessage.MessageLevel.DEBUG -> Color(0xFF64B5F6)
        ConsoleMessage.MessageLevel.TIP -> Color(0xFF81C784)
    }

    val prefix = when (log.level) {
        ConsoleMessage.MessageLevel.ERROR -> "[ERROR]"
        ConsoleMessage.MessageLevel.WARNING -> "[WARN]"
        ConsoleMessage.MessageLevel.LOG -> "[LOG]"
        ConsoleMessage.MessageLevel.DEBUG -> "[DEBUG]"
        ConsoleMessage.MessageLevel.TIP -> "[TIP]"
    }

    Text(
        text = "$prefix ${log.message}",
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp
        ),
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/**
 * Element inspector panel showing details of the selected element
 */
@Composable
private fun InspectorPanel(
    element: InspectedElement,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Element", "Styles", "Attributes")

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Element Inspector",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildElementSelector(element),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(element.outerHTML))
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy HTML",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }

        // Content based on selected tab
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            when (selectedTab) {
                0 -> ElementTab(element)
                1 -> StylesTab(element.computedStyles)
                2 -> AttributesTab(element.attributes)
            }
        }
    }
}

@Composable
private fun ElementTab(element: InspectedElement) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InspectorProperty("Tag", element.tagName.lowercase())
        if (element.id.isNotEmpty()) {
            InspectorProperty("ID", "#${element.id}")
        }
        if (element.className.isNotEmpty()) {
            InspectorProperty("Classes", element.className.split(" ").joinToString(", ") { ".$it" })
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Size & Position",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InspectorProperty("Width", "${element.boundingRect.width.toInt()}px", Modifier.weight(1f))
            InspectorProperty("Height", "${element.boundingRect.height.toInt()}px", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InspectorProperty("X", "${element.boundingRect.x.toInt()}px", Modifier.weight(1f))
            InspectorProperty("Y", "${element.boundingRect.y.toInt()}px", Modifier.weight(1f))
        }

        // Show inner HTML preview (truncated)
        if (element.innerHTML.isNotBlank() && element.innerHTML.length < 200) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Content",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = element.innerHTML.take(150),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun StylesTab(styles: Map<String, String>) {
    val importantStyles = listOf(
        "display", "position", "width", "height", "margin", "padding",
        "color", "background-color", "font-size", "font-family", "font-weight",
        "border", "border-radius", "flex", "grid", "gap"
    )

    val filteredStyles = styles.filterKeys { key ->
        importantStyles.any { key.contains(it, ignoreCase = true) }
    }.filterValues { it.isNotBlank() && it != "none" && it != "0px" && it != "normal" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (filteredStyles.isEmpty()) {
            Text(
                text = "No computed styles available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            filteredStyles.forEach { (name, value) ->
                StyleProperty(name, value)
            }
        }
    }
}

@Composable
private fun AttributesTab(attributes: Map<String, String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (attributes.isEmpty()) {
            Text(
                text = "No attributes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            attributes.forEach { (name, value) ->
                InspectorProperty(name, value)
            }
        }
    }
}

@Composable
private fun InspectorProperty(
    name: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StyleProperty(name: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.5f)
        )
    }
}

private fun buildElementSelector(element: InspectedElement): String {
    val sb = StringBuilder(element.tagName.lowercase())
    if (element.id.isNotEmpty()) {
        sb.append("#${element.id}")
    }
    if (element.className.isNotEmpty()) {
        element.className.split(" ").take(2).forEach { className ->
            if (className.isNotBlank()) {
                sb.append(".$className")
            }
        }
    }
    return sb.toString()
}

/**
 * Extension function to apply viewport mode (mobile or desktop) to WebView
 */
private fun WebView.applyViewportMode(desktopMode: Boolean) {
    settings.apply {
        if (desktopMode) {
            // Desktop mode settings
            userAgentString = DESKTOP_USER_AGENT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // Set initial scale to fit desktop content
            setInitialScale(50) // 50% to fit more content
        } else {
            // Mobile mode settings (default)
            userAgentString = null // Reset to default mobile UA
            useWideViewPort = false
            loadWithOverviewMode = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setInitialScale(0) // 0 = default scale
        }
    }
    reload()
}

/**
 * Inject inspector script into the WebView
 * @param multiSelect Whether to enable multi-selection mode for agent editing
 */
private fun WebView.injectInspectorScript(multiSelect: Boolean = false) {
    val script = """
        (function() {
            // Remove any existing inspector
            if (window.__stormyInspector) {
                window.__stormyInspector.disable();
            }

            // Create highlight overlay
            var overlay = document.createElement('div');
            overlay.id = '__stormy_inspector_overlay';
            overlay.style.cssText = 'position:fixed;pointer-events:none;z-index:999999;border:2px solid ${if (multiSelect) "#6366F1" else "#6200EE"};background:rgba(${if (multiSelect) "99,102,241" else "98,0,238"},0.1);display:none;transition:all 0.1s ease;';
            document.body.appendChild(overlay);

            // Track selected elements for multi-select mode
            var selectedElements = new Set();

            // Inspector object
            window.__stormyInspector = {
                enabled: true,
                multiSelect: $multiSelect,
                lastElement: null,

                getElementData: function(el) {
                    // Get computed styles
                    var computedStyles = {};
                    var style = window.getComputedStyle(el);
                    var importantProps = ['display', 'position', 'width', 'height', 'margin', 'padding',
                        'color', 'background-color', 'font-size', 'font-family', 'font-weight',
                        'border', 'border-radius', 'flex', 'flex-direction', 'justify-content',
                        'align-items', 'gap', 'grid-template-columns'];
                    importantProps.forEach(function(prop) {
                        var val = style.getPropertyValue(prop);
                        if (val) computedStyles[prop] = val;
                    });

                    // Get attributes
                    var attributes = {};
                    for (var i = 0; i < el.attributes.length; i++) {
                        var attr = el.attributes[i];
                        attributes[attr.name] = attr.value;
                    }

                    // Get bounding rect
                    var rect = el.getBoundingClientRect();

                    return {
                        tagName: el.tagName,
                        id: el.id || '',
                        className: el.className || '',
                        innerHTML: el.innerHTML.substring(0, 500),
                        outerHTML: el.outerHTML.substring(0, 1000),
                        computedStyles: computedStyles,
                        attributes: attributes,
                        boundingRect: {
                            x: rect.x,
                            y: rect.y,
                            width: rect.width,
                            height: rect.height
                        }
                    };
                },

                handleClick: function(e) {
                    if (!window.__stormyInspector.enabled) return;
                    e.preventDefault();
                    e.stopPropagation();

                    var el = e.target;
                    if (el.id === '__stormy_inspector_overlay' ||
                        el.classList.contains('__stormy_selected_marker')) return;

                    var data = this.getElementData(el);

                    // Send to Android
                    if (window.StormyInspector) {
                        window.StormyInspector.onElementSelected(JSON.stringify(data));
                    }

                    // In multi-select mode, toggle selection marker
                    if (this.multiSelect) {
                        this.toggleSelectionMarker(el);
                    }
                },

                toggleSelectionMarker: function(el) {
                    var existingMarker = el.querySelector('.__stormy_selected_marker');
                    if (existingMarker) {
                        existingMarker.remove();
                        selectedElements.delete(el);
                    } else {
                        var marker = document.createElement('div');
                        marker.className = '__stormy_selected_marker';
                        marker.style.cssText = 'position:absolute;top:0;right:0;width:20px;height:20px;background:#6366F1;border-radius:50%;display:flex;align-items:center;justify-content:center;z-index:999998;pointer-events:none;';
                        marker.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="white"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>';

                        // Make parent relative if needed
                        var computedPos = window.getComputedStyle(el).position;
                        if (computedPos === 'static') {
                            el.style.position = 'relative';
                        }

                        el.appendChild(marker);
                        selectedElements.add(el);
                    }
                },

                handleMove: function(e) {
                    if (!window.__stormyInspector.enabled) return;
                    var el = e.target;
                    if (el.id === '__stormy_inspector_overlay' ||
                        el.classList.contains('__stormy_selected_marker')) return;

                    var rect = el.getBoundingClientRect();
                    var overlay = document.getElementById('__stormy_inspector_overlay');
                    if (overlay) {
                        overlay.style.display = 'block';
                        overlay.style.left = rect.left + 'px';
                        overlay.style.top = rect.top + 'px';
                        overlay.style.width = rect.width + 'px';
                        overlay.style.height = rect.height + 'px';
                    }
                },

                disable: function() {
                    this.enabled = false;
                    var overlay = document.getElementById('__stormy_inspector_overlay');
                    if (overlay) overlay.remove();

                    // Remove all selection markers
                    document.querySelectorAll('.__stormy_selected_marker').forEach(function(m) {
                        m.remove();
                    });
                    selectedElements.clear();

                    document.removeEventListener('click', this.boundHandleClick, true);
                    document.removeEventListener('mousemove', this.boundHandleMove, true);
                    document.removeEventListener('touchmove', this.boundHandleTouchMove, true);
                }
            };

            // Bind handlers
            window.__stormyInspector.boundHandleClick = window.__stormyInspector.handleClick.bind(window.__stormyInspector);
            window.__stormyInspector.boundHandleMove = window.__stormyInspector.handleMove.bind(window.__stormyInspector);
            window.__stormyInspector.boundHandleTouchMove = function(e) {
                if (e.touches.length > 0) {
                    var touch = e.touches[0];
                    var el = document.elementFromPoint(touch.clientX, touch.clientY);
                    if (el) {
                        window.__stormyInspector.handleMove({target: el});
                    }
                }
            };

            // Add event listeners
            document.addEventListener('click', window.__stormyInspector.boundHandleClick, true);
            document.addEventListener('mousemove', window.__stormyInspector.boundHandleMove, true);
            document.addEventListener('touchmove', window.__stormyInspector.boundHandleTouchMove, true);
        })();
    """.trimIndent()

    evaluateJavascript(script, null)
}

/**
 * Disable the inspector in WebView
 */
private fun WebView.disableInspector() {
    evaluateJavascript("if(window.__stormyInspector) window.__stormyInspector.disable();", null)
}

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun WebViewPreview(
    projectPath: String,
    modifier: Modifier = Modifier,
    inspectorEnabled: Boolean = false,
    onWebViewCreated: (WebView) -> Unit,
    onTitleChanged: (String) -> Unit,
    onConsoleMessage: (ConsoleLogEntry) -> Unit,
    onElementInspected: (InspectedElement) -> Unit = {}
) {
    // Create the JS interface once
    val inspectorInterface = remember {
        InspectorJsInterface(
            onElementInspected = onElementInspected,
            onMultiSelectElement = onElementInspected // Same handler for both modes
        )
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Re-inject inspector script after page load if enabled
                        if (inspectorEnabled) {
                            view?.injectInspectorScript()
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        onTitleChanged(title ?: "")
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            onConsoleMessage(
                                ConsoleLogEntry(
                                    message = it.message(),
                                    level = it.messageLevel(),
                                    lineNumber = it.lineNumber(),
                                    sourceId = it.sourceId()
                                )
                            )
                        }
                        return true
                    }
                }

                // Add JavaScript interface for element inspector
                addJavascriptInterface(inspectorInterface, "StormyInspector")

                // Default mobile mode settings
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    loadWithOverviewMode = false // Mobile mode default
                    useWideViewPort = false // Mobile mode default
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    // Enable modern web features
                    mediaPlaybackRequiresUserGesture = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = true
                    }
                }

                val indexFile = File(projectPath, "index.html")
                if (indexFile.exists()) {
                    loadUrl("file://${indexFile.absolutePath}")
                } else {
                    loadData(
                        "<html><body><h2>No index.html found</h2><p>Create an index.html file to preview your website.</p></body></html>",
                        "text/html",
                        "UTF-8"
                    )
                }

                onWebViewCreated(this)
            }
        },
        modifier = modifier
    )
}
