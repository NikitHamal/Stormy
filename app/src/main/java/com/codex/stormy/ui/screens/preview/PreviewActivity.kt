package com.codex.stormy.ui.screens.preview

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tablet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.codex.stormy.R
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

enum class PreviewMode {
    MOBILE, TABLET, DESKTOP
}

@Composable
private fun PreviewScreen(
    projectPath: String,
    onBackClick: () -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    val context = LocalContext.current
    var previewMode by remember { mutableStateOf(PreviewMode.MOBILE) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(context.getString(R.string.preview_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = context.getString(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = context.getString(R.string.preview_refresh)
                        )
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
            PreviewModeSelector(
                currentMode = previewMode,
                onModeSelected = { previewMode = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val (width, height) = when (previewMode) {
                    PreviewMode.MOBILE -> 360.dp to 640.dp
                    PreviewMode.TABLET -> 600.dp to 800.dp
                    PreviewMode.DESKTOP -> 1024.dp to 768.dp
                }

                Box(
                    modifier = Modifier
                        .then(
                            if (previewMode == PreviewMode.DESKTOP) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier.size(width = width.coerceAtMost(400.dp), height = height)
                            }
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    WebViewPreview(
                        projectPath = projectPath,
                        modifier = Modifier.fillMaxSize(),
                        onWebViewCreated = { wv ->
                            webView = wv
                            onWebViewCreated(wv)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewModeSelector(
    currentMode: PreviewMode,
    onModeSelected: (PreviewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PreviewModeButton(
            icon = Icons.Outlined.PhoneAndroid,
            label = context.getString(R.string.preview_mobile_mode),
            isSelected = currentMode == PreviewMode.MOBILE,
            onClick = { onModeSelected(PreviewMode.MOBILE) },
            modifier = Modifier.weight(1f)
        )

        PreviewModeButton(
            icon = Icons.Outlined.Tablet,
            label = context.getString(R.string.preview_tablet_mode),
            isSelected = currentMode == PreviewMode.TABLET,
            onClick = { onModeSelected(PreviewMode.TABLET) },
            modifier = Modifier.weight(1f)
        )

        PreviewModeButton(
            icon = Icons.Outlined.DesktopWindows,
            label = context.getString(R.string.preview_desktop_mode),
            isSelected = currentMode == PreviewMode.DESKTOP,
            onClick = { onModeSelected(PreviewMode.DESKTOP) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PreviewModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewPreview(
    projectPath: String,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit
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

                webChromeClient = WebChromeClient()

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
