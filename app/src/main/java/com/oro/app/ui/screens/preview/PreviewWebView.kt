package com.oro.app.ui.screens.preview

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.File

/**
 * The preview's WebView, set up to show a project the way a visitor's
 * browser would and to survive what a phone does to it:
 *
 * - [device] lays the page out at a phone, tablet or desktop width (see
 *   [PreviewDevice]) with a matching user agent, zoomed to fit; [darkPage]
 *   is the color scheme the page is told the visitor prefers.
 * - A renderer that dies (out of memory on a small phone, or a crash) no
 *   longer takes Oro with it: the dead WebView is replaced by a fresh one
 *   that reopens the same page. A page that keeps crashing is held until
 *   the user retries, instead of looping.
 * - `<input type=file>` opens the system picker; a microphone request asks
 *   for Oro's own permission first; camera and location are declined, and
 *   the console says so. Downloads and links to other sites and apps open
 *   outside the preview.
 * - A page that fails to load shows why, with a retry, instead of
 *   Chromium's error page.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
internal fun PreviewWebView(
    projectPath: String,
    previewUrl: String?,
    device: PreviewDevice,
    landscape: Boolean,
    darkPage: Boolean,
    selectionMode: SelectionMode,
    onWebViewCreated: (WebView) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: (WebView) -> Unit,
    onTitleChanged: (String) -> Unit,
    onConsoleMessage: (ConsoleLogEntry) -> Unit,
    onRendererGone: (reopened: Boolean) -> Unit,
    onElementInspected: (InspectedElement, SelectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnCreated by rememberUpdatedState(onWebViewCreated)
    val currentOnPageStarted by rememberUpdatedState(onPageStarted)
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentOnTitle by rememberUpdatedState(onTitleChanged)
    val currentOnConsole by rememberUpdatedState(onConsoleMessage)
    val currentOnGone by rememberUpdatedState(onRendererGone)
    val currentOnElementInspected by rememberUpdatedState(onElementInspected)
    val currentSelectionMode by rememberUpdatedState(selectionMode)

    // Reads the mode at the moment of the tap, so a fast mode switch routes right.
    val inspectorInterface = remember {
        InspectorJsInterface(
            onElementInspected = { element -> currentOnElementInspected(element, currentSelectionMode) },
            onMultiSelectElement = { element -> currentOnElementInspected(element, currentSelectionMode) }
        )
    }

    val session = remember { PreviewSession() }
    // Bumped when the renderer dies: a new key, so a new WebView.
    var generation by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val defaultUa = remember { runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("") }

    fun notice(message: String) =
        currentOnConsole(ConsoleLogEntry(message, ConsoleMessage.MessageLevel.LOG, 0, "Oro"))

    val fileChooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val picked = if (session.fileMultiple) uris else uris.take(1)
        session.takeFileCallback()?.onReceiveValue(if (picked.isEmpty()) null else picked.toTypedArray())
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = session.pendingMic ?: return@rememberLauncherForActivityResult
        session.pendingMic = null
        if (granted) request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else request.deny()
    }

    // Device changes after the first load: new settings, then reload.
    LaunchedEffect(device, landscape, generation) {
        val webView = session.webView ?: return@LaunchedEffect
        if (session.applied == device to landscape) return@LaunchedEffect
        webView.applyDevice(session, device, landscape, defaultUa)
        webView.reload()
    }

    Box(modifier) {
        // A new scheme needs a new WebView: it reads prefers-color-scheme from
        // its context's theme (isLightTheme) once, when it is created.
        key(generation, darkPage) {
            AndroidView(
                factory = { ctx ->
                    val theme = if (darkPage) android.R.style.Theme_Material_NoActionBar
                    else android.R.style.Theme_Material_Light_NoActionBar
                    WebView(ContextThemeWrapper(ctx, theme)).apply {
                        session.webView = this
                        configure()
                        addJavascriptInterface(inspectorInterface, "OroInspector")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            // Kept responsive while on screen; its memory can go first in the background.
                            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val req = request ?: return false
                                val uri = req.url ?: return false
                                return when (uri.scheme?.lowercase()) {
                                    "http", "https" -> {
                                        // The project's own pages stay here; other sites open in a browser.
                                        if (!req.isForMainFrame || isLocalHost(uri.host) || uri.host == Uri.parse(view?.url.orEmpty()).host) {
                                            false
                                        } else {
                                            openOutside(ctx, uri)
                                            true
                                        }
                                    }
                                    "file", "about", "data", "blob", "javascript" -> false
                                    // mailto:, tel:, sms:, market: and the like belong to other apps.
                                    else -> {
                                        openOutside(ctx, uri)
                                        true
                                    }
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                loadError = null
                                currentOnPageStarted()
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (view == null || view !== session.webView) return
                                if (url != null && url != "about:blank" && !url.startsWith("data:")) session.lastUrl = url
                                // Without document-start scripts the width is set after load, then fitted.
                                val width = session.layoutWidth
                                if (width != null && session.scriptHandler == null) {
                                    view.evaluateJavascript(PreviewDevice.viewportNowScript(width)) {
                                        view.postDelayed({ runCatching { view.zoomBy(0.01f) } }, 120)
                                    }
                                }
                                currentOnPageFinished(view)
                            }

                            // Failed loads (a missing stylesheet, a 404 script) never reach
                            // the console, so report them as console errors ourselves.
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                super.onReceivedError(view, request, error)
                                if (request == null) return
                                val description = error?.description?.toString() ?: "error"
                                // Cancelled loads (a new navigation, a link that became a download) aren't failures.
                                if ("ERR_ABORTED" in description) return
                                currentOnConsole(
                                    ConsoleLogEntry(
                                        message = "Failed to load ${request.url}: $description",
                                        level = ConsoleMessage.MessageLevel.ERROR,
                                        lineNumber = 0,
                                        sourceId = request.url?.toString().orEmpty()
                                    )
                                )
                                if (request.isForMainFrame) loadError = loadErrorMessage(description)
                            }

                            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                val code = errorResponse?.statusCode ?: return
                                if (request == null || code < 400) return
                                currentOnConsole(
                                    ConsoleLogEntry(
                                        message = "HTTP $code loading ${request.url}",
                                        level = ConsoleMessage.MessageLevel.ERROR,
                                        lineNumber = 0,
                                        sourceId = request.url?.toString().orEmpty()
                                    )
                                )
                            }

                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                // Returning true keeps Oro alive. This WebView is dead now; a new
                                // generation replaces it and reopens the page, unless the page
                                // has crashed twice in the last minute already.
                                if (view != null && view === session.webView) {
                                    val now = SystemClock.elapsedRealtime()
                                    session.crashes.removeAll { now - it > 60_000L }
                                    session.crashes.add(now)
                                    session.held = session.crashes.size > 2
                                    session.webView = null
                                    session.scriptHandler = null
                                    session.applied = null
                                    if (session.held) loadError = "This page keeps crashing the preview."
                                    currentOnGone(!session.held)
                                    generation++
                                }
                                return true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                currentOnTitle(title ?: "")
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                consoleMessage?.let {
                                    currentOnConsole(
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

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                if (filePathCallback == null) return false
                                // Every request must be answered, or the page's input never fires again.
                                session.takeFileCallback()?.onReceiveValue(null)
                                session.fileCallback = filePathCallback
                                session.fileMultiple = fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                                try {
                                    fileChooser.launch(acceptedMimeTypes(fileChooserParams?.acceptTypes))
                                } catch (e: ActivityNotFoundException) {
                                    session.takeFileCallback()?.onReceiveValue(null)
                                    notice("No app on this phone can pick files.")
                                }
                                return true
                            }

                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request ?: return
                                val resources = request.resources.orEmpty()
                                if (resources.isEmpty() || resources.any { it != PermissionRequest.RESOURCE_AUDIO_CAPTURE }) {
                                    request.deny()
                                    notice("The preview can't share the camera or other devices with the page; test that in a browser.")
                                    return
                                }
                                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                                } else {
                                    session.pendingMic?.deny()
                                    session.pendingMic = request
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }

                            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                                super.onPermissionRequestCanceled(request)
                                if (request === session.pendingMic) session.pendingMic = null
                            }

                            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                                callback?.invoke(origin, false, false)
                                notice("The preview doesn't share your location with the page.")
                            }
                        }

                        setDownloadListener { url, _, _, _, _ ->
                            val uri = Uri.parse(url)
                            if (uri.scheme == "http" || uri.scheme == "https") {
                                openOutside(ctx, uri)
                            } else {
                                notice("Files the page makes itself (${uri.scheme}: links) can't be saved from the preview. Open the page in a browser to save one.")
                            }
                        }

                        applyDevice(session, device, landscape, defaultUa)
                        val url = session.lastUrl ?: previewUrl
                        session.loadedPreviewUrl = previewUrl
                        if (url != null && !session.held) loadPreview(url, projectPath)
                        currentOnCreated(this)
                    }
                },
                update = { view ->
                    // Only a new preview URL (the server came up, or moved) navigates;
                    // the page the user has clicked through to is left alone.
                    if (previewUrl != null && previewUrl != session.loadedPreviewUrl && !session.held) {
                        session.loadedPreviewUrl = previewUrl
                        view.loadPreview(previewUrl, projectPath)
                    }
                },
                onRelease = { view ->
                    if (session.webView === view) session.webView = null
                    session.takeFileCallback()?.onReceiveValue(null)
                    runCatching {
                        view.stopLoading()
                        view.destroy()
                    }
                }
            )
        }

        loadError?.let { message ->
            PreviewLoadError(
                message = message,
                onRetry = {
                    loadError = null
                    session.held = false
                    session.crashes.clear()
                    val webView = session.webView ?: return@PreviewLoadError
                    val url = session.lastUrl ?: previewUrl
                    if (url != null) webView.loadPreview(url, projectPath) else webView.reload()
                }
            )
        }
    }
}

/** What outlives a WebView: renderers can die and be replaced mid-session. */
private class PreviewSession {
    var webView: WebView? = null
    var lastUrl: String? = null
    var loadedPreviewUrl: String? = null
    var scriptHandler: ScriptHandler? = null
    var applied: Pair<PreviewDevice, Boolean>? = null
    var layoutWidth: Int? = null
    var fileCallback: ValueCallback<Array<Uri>>? = null
    var fileMultiple = false
    var pendingMic: PermissionRequest? = null
    val crashes = ArrayList<Long>()
    var held = false

    fun takeFileCallback(): ValueCallback<Array<Uri>>? = fileCallback.also { fileCallback = null }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configure() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = true
        allowContentAccess = true
        javaScriptCanOpenWindowsAutomatically = true
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        // Like Chrome: device-width pages render at the phone's width, pages
        // without a viewport tag at 980px zoomed out -- what visitors get.
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = true
        displayZoomControls = false
        setSupportZoom(true)
        // The page's own font sizes, whatever the phone's font scale.
        textZoom = 100
        // Normal HTTP caching. The project's own files are always fresh anyway
        // -- the dev server sends no-store on every response -- while CDN
        // libraries (Tailwind, fonts, icon sets) are not re-downloaded on
        // every reload over mobile data.
        cacheMode = WebSettings.LOAD_DEFAULT
        mediaPlaybackRequiresUserGesture = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeBrowsingEnabled = false
        }
    }
}

/**
 * Sets the user agent and layout width for [device]; takes effect on the
 * next load. Widths go in through a document-start script where WebView
 * supports one, else after load (see onPageFinished).
 */
private fun WebView.applyDevice(session: PreviewSession, device: PreviewDevice, landscape: Boolean, defaultUa: String) {
    session.scriptHandler?.let { handler -> runCatching { handler.remove() } }
    session.scriptHandler = null
    settings.userAgentString = device.userAgent(defaultUa)
    val width = device.width(landscape)
    if (width != null && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        session.scriptHandler = runCatching {
            WebViewCompat.addDocumentStartJavaScript(this, PreviewDevice.viewportScript(width), setOf("*"))
        }.getOrNull()
    }
    session.layoutWidth = width
    session.applied = device to landscape
}

/** file:// pages may read their neighbours; pages from the dev server don't need to. */
@Suppress("DEPRECATION")
private fun WebView.loadPreview(url: String, projectPath: String) {
    val local = url.startsWith("file:")
    settings.allowFileAccessFromFileURLs = local
    settings.allowUniversalAccessFromFileURLs = local
    if (url == "about:blank" && !File(projectPath, "index.html").exists()) {
        loadData(NO_INDEX_PAGE, "text/html", "UTF-8")
    } else {
        loadUrl(url)
    }
}

private const val NO_INDEX_PAGE =
    "<html><body style=\"font-family:sans-serif;padding:24px\"><h2>No index.html yet</h2>" +
        "<p>Add an index.html to the project to preview it.</p></body></html>"

private fun isLocalHost(host: String?): Boolean =
    host == null || host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0" || host == "[::1]" || host == "::1"

private fun openOutside(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app here can open ${uri.scheme}: links", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "That link can't be opened from the preview", Toast.LENGTH_SHORT).show()
    }
}

/** An `accept` list (`image/png`, `.pdf`, ...) as MIME types for the system picker. */
internal fun acceptedMimeTypes(accept: Array<String>?): Array<String> {
    val types = accept.orEmpty()
        .flatMap { it.split(',') }
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .mapNotNull { type ->
            when {
                '/' in type -> type
                type.startsWith(".") -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.removePrefix("."))
                else -> null
            }
        }
        .distinct()
    return if (types.isEmpty()) arrayOf("*/*") else types.toTypedArray()
}

private fun loadErrorMessage(description: String): String = when {
    "CONNECTION_REFUSED" in description || "CONNECTION_RESET" in description ->
        "The preview server isn't answering. Start it again from the menu, or retry."
    "NAME_NOT_RESOLVED" in description || "INTERNET_DISCONNECTED" in description ->
        "That address can't be reached. Check the connection, or retry."
    "FILE_NOT_FOUND" in description -> "That page isn't in the project."
    else -> "The page didn't load ($description)."
}
