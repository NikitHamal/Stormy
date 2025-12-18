package com.codex.stormy.ui.screens.preview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.codex.stormy.ui.theme.CodeXTheme
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
                    projectPath = projectPath,
                    onBackClick = { finish() },
                    onWebViewCreated = { webView = it }
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
    }
}

data class ConsoleLogEntry(
    val message: String,
    val level: ConsoleMessage.MessageLevel,
    val lineNumber: Int,
    val sourceId: String
)

@Composable
private fun PreviewScreen(
    projectPath: String,
    onBackClick: () -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Preview") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(false) }
    var serverRunning by remember { mutableStateOf(true) }
    var showConsole by remember { mutableStateOf(false) }
    val consoleLogs = remember { mutableStateListOf<ConsoleLogEntry>() }

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
                            tint = if (showConsole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
                                    webView?.settings?.apply {
                                        userAgentString = if (desktopMode) {
                                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                        } else {
                                            null // Reset to default mobile user agent
                                        }
                                        useWideViewPort = desktopMode
                                        loadWithOverviewMode = desktopMode
                                    }
                                    webView?.reload()
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.DesktopWindows,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open in browser") },
                                onClick = {
                                    val indexFile = File(projectPath, "index.html")
                                    if (indexFile.exists()) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("file://${indexFile.absolutePath}"))
                                        context.startActivity(intent)
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
                                        imageVector = if (serverRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
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
                    onWebViewCreated = { wv ->
                        webView = wv
                        onWebViewCreated(wv)
                    },
                    onTitleChanged = { title ->
                        pageTitle = title.ifEmpty { "Preview" }
                    },
                    onConsoleMessage = { entry ->
                        consoleLogs.add(entry)
                    }
                )
            }

            // Console panel
            AnimatedVisibility(
                visible = showConsole,
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
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp
        ),
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewPreview(
    projectPath: String,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit,
    onTitleChanged: (String) -> Unit,
    onConsoleMessage: (ConsoleLogEntry) -> Unit
) {
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

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
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
        modifier = modifier,
        update = { webView ->
            val indexFile = File(projectPath, "index.html")
            if (indexFile.exists()) {
                webView.loadUrl("file://${indexFile.absolutePath}")
            }
        }
    )
}
