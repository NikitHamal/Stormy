package com.oro.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.content.FileProvider
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.oro.app.data.ai.AiHttp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * The real browser: WebViews the agent drives through JavaScript, kept alive
 * in the application process so a run outlives every screen.
 *
 * ### Why it works with the screen closed
 * The WebViews are created with the application context and kept measured and
 * laid out at a phone viewport even when nothing shows them -- the same trick
 * [com.oro.app.preview.WebViewPagePreviewer] uses to render audits off-screen.
 * evaluateJavascript, rendering and timers keep running detached, so the user
 * can minimise the browser, edit code, switch chats or lock the phone while a
 * task runs (a foreground [com.oro.app.agent.AgentRunService] run keeps the
 * process warm). Opening the browser screen re-parents the same WebView: what
 * the user sees is literally what the agent acts on.
 *
 * ### What the JS layer does
 * [BrowserScripts.RUNTIME_JS] is installed at document start when the WebView
 * supports it (androidx.webkit DOCUMENT_START_SCRIPT), else on page start plus
 * a liveness check before every operation. It tags interactive elements with
 * `data-oid`, snapshots the page to JSON, and executes the action primitives
 * with framework-safe event sequences (pointer events plus native activation;
 * React's native value setter plus input/change). Cross-origin iframes are
 * listed and flagged (human checks included) but never scripted into: the
 * reCAPTCHA inside one is the user's to pass, in the live view.
 *
 * One engine, at most [MAX_TABS] tabs, one run at a time (guarded by
 * [BrowserAgent]). Everything here is thin: JSON in, JSON out, decisions in
 * the run loop, which JVM tests drive through the [Browser] seam.
 */
object BrowserEngine : Browser {

    private const val MAX_TABS = 3
    private const val VIEWPORT_FALLBACK_W = 412
    private const val VIEWPORT_FALLBACK_H = 915
    private const val NAV_TIMEOUT_MS = 25_000L
    private const val EVAL_TIMEOUT_MS = 12_000L
    private const val MAX_DOWNLOAD_BYTES = 25L * 1024 * 1024

    /** Desktop Chrome UA for the "desktop site" setting. */
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tabSeq = AtomicInteger(0)

    @Volatile private var initialised = false

    private class Tab(val id: Int, val webView: WebView) {
        @Volatile var url: String = "about:blank"
        @Volatile var title: String = ""
        @Volatile var loading: Boolean = false
        @Volatile var lastNavAt: Long = 0L
        @Volatile var loadError: String? = null
        @Volatile var consoleErrors: Int = 0
        var navDone: CompletableDeferred<Unit>? = null
        var httpAuth: HttpAuthHandler? = null
        var httpAuthHost: String? = null
        var httpAuthRealm: String? = null
    }

    private val tabs = LinkedHashMap<Int, Tab>()
    @Volatile private var activeId: Int = -1

    /** UI state the screen and the pill observe. */
    data class Ui(
        val tabs: List<TabInfo> = emptyList(),
        val url: String = "",
        val title: String = "",
        val progress: Int = 0,
        val attached: Boolean = false,
        val desktopMode: Boolean = false,
        val httpAuthPrompt: String? = null,
        val lastError: String? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val downloadsFlow: StateFlow<List<DownloadInfo>> = _downloads.asStateFlow()

    /** The file the agent asked to upload; consumed by the next file chooser. */
    @Volatile private var pendingUpload: File? = null
    @Volatile private var uploadConsumed: Boolean = false

    /** A file chooser with no agent upload pending: the user picks, if the screen is open. */
    @Volatile private var chooserWaiting: ValueCallback<Array<Uri>>? = null

    /** Bumped when a page opens a file chooser the user must answer; the screen watches it. */
    private val _chooserTick = MutableStateFlow(0)
    val chooserTick: StateFlow<Int> = _chooserTick.asStateFlow()

    @Volatile var desktopMode: Boolean = false
        private set

    fun init(context: Context) {
        if (initialised) return
        synchronized(this) {
            if (initialised) return
            app = context.applicationContext
            runCatching { CookieManager.getInstance().setAcceptCookie(true) }
            initialised = true
        }
    }

    fun isReady(): Boolean = initialised

    fun setDesktopMode(on: Boolean) {
        desktopMode = on
        ui { tab -> tab.webView.settings.userAgentString = if (on) DESKTOP_UA else null }
        _ui.value = _ui.value.copy(desktopMode = on)
    }

    /** The active tab, created on demand. Only call on the main thread. */
    private fun ensureTab(): Tab {
        check(initialised) { "BrowserEngine.init was not called" }
        tabs[activeId]?.let { return it }
        val tab = createTab("about:blank")
        activeId = tab.id
        return tab
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createTab(startUrl: String): Tab {
        val id = tabSeq.incrementAndGet()
        val tab = Tab(id, WebView(app))
        tabs[id] = tab
        while (tabs.size > MAX_TABS) {
            val oldest = tabs.keys.firstOrNull { it != id && it != activeId } ?: break
            removeTab(oldest)
        }
        val wv = tab.webView
        wv.setBackgroundColor(Color.WHITE)
        runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true) }
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportZoom(false)
            builtInZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = false
            userAgentString = if (desktopMode) DESKTOP_UA else null
            textZoom = 100
        }
        wv.addJavascriptInterface(Bridge(tab), "OroBridge")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching { WebViewCompat.addDocumentStartJavaScript(wv, BrowserScripts.RUNTIME_JS, setOf("*")) }
        }
        wv.webViewClient = Client(tab)
        wv.webChromeClient = Chrome(tab)
        wv.setDownloadListener(Downloader())
        ensureLaidOut(wv)
        if (startUrl != "about:blank") main.post { wv.loadUrl(startUrl) }
        publishUi()
        return tab
    }

    private fun removeTab(id: Int) {
        val tab = tabs.remove(id) ?: return
        runCatching {
            tab.webView.stopLoading()
            (tab.webView.parent as? FrameLayout)?.removeView(tab.webView)
            tab.webView.destroy()
        }
        if (activeId == id) activeId = tabs.keys.lastOrNull() ?: -1
        publishUi()
    }

    /** Keep a detached WebView measured and laid out so JS and rendering run. */
    private fun ensureLaidOut(wv: WebView) {
        if (wv.parent != null) return
        val dm = app.resources.displayMetrics
        val w = if (dm.widthPixels > 0) dm.widthPixels else (VIEWPORT_FALLBACK_W * dm.density).toInt()
        val h = if (dm.heightPixels > 0) (dm.heightPixels * 0.75).toInt() else (VIEWPORT_FALLBACK_H * dm.density).toInt()
        wv.measure(
            View.MeasureSpec.makeMeasureSpec(w.coerceAtLeast(1), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h.coerceAtLeast(1), View.MeasureSpec.EXACTLY)
        )
        wv.layout(0, 0, wv.measuredWidth, wv.measuredHeight)
    }

    // ------------------------------------------------------ attach for UI

    /** The container the live view is in, while the browser screen is open. */
    @Volatile private var attachedContainer: FrameLayout? = null

    /** Puts the active tab's WebView inside [container] (main thread, from AndroidView). */
    fun attach(container: FrameLayout) {
        if (!initialised) return
        main.post {
            attachedContainer = container
            val tab = ensureTab()
            reparent(tab)
            publishUi()
        }
    }

    /** Takes the WebView back so it keeps running off-screen. */
    fun detach(container: FrameLayout?) {
        if (!initialised) return
        main.post {
            if (attachedContainer === container || container == null) attachedContainer = null
            val tab = tabs[activeId] ?: return@post
            val wv = tab.webView
            (wv.parent as? FrameLayout)?.removeView(wv)
            ensureLaidOut(wv)
            _ui.value = _ui.value.copy(attached = false)
        }
    }

    /** Puts [tab]'s WebView in the live container when there is one; else keeps it running off-screen. */
    private fun reparent(tab: Tab) {
        val c = attachedContainer
        val wv = tab.webView
        (wv.parent as? FrameLayout)?.let { if (it !== c) it.removeView(wv) }
        if (c != null) {
            if (wv.parent == null) {
                c.addView(
                    wv,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                )
            }
        } else ensureLaidOut(wv)
        _ui.value = _ui.value.copy(attached = c != null)
    }

    /** Switches the shown tab (live container follows when the screen is open). */
    fun showTab(id: Int) {
        if (!initialised) return
        main.post {
            if (!tabs.containsKey(id)) return@post
            activeId = id
            tabs[id]?.let { reparent(it) }
            publishUi()
        }
    }

    fun flushCookies() = runCatching { CookieManager.getInstance().flush() }

    private fun ui(block: (Tab) -> Unit) {
        if (!initialised) return
        main.post {
            val tab = ensureTab()
            block(tab)
        }
    }

    /** The active tab or null; never throws (a run may call before any tab exists). */
    private fun tabOrNull(): Tab? = tabs[activeId]

    private fun publishUi() {
        val tab = tabs[activeId]
        _ui.value = _ui.value.copy(
            tabs = tabs.values.map { TabInfo(it.id, it.url, it.title, it.id == activeId) },
            url = tab?.url.orEmpty(),
            title = tab?.title.orEmpty(),
            httpAuthPrompt = tab?.httpAuthHost?.let { h -> "$h${tab.httpAuthRealm?.let { " ($it)" } ?: ""}" },
            lastError = tab?.loadError,
        )
    }

    private fun normaliseUrl(raw: String): String {
        val u = raw.trim()
        if (u.isEmpty()) return u
        if (u.startsWith("http://") || u.startsWith("https://")) return u
        if (u.startsWith("about:")) return u
        if (u.startsWith("tel:") || u.startsWith("mailto:") || u.startsWith("intent:") || u.startsWith("market:")) return u
        if (u.contains('.') && !u.contains(' ')) return "https://$u"
        // Not a URL: a search.
        return "https://duckduckgo.com/?q=" + URLEncoder.encode(u, "UTF-8")
    }

    // ---------------------------------------------------- main-thread glue

    private suspend fun <T> onUi(timeoutMs: Long = EVAL_TIMEOUT_MS, block: suspend () -> T): T? =
        withTimeoutOrNull(timeoutMs) { withContext(Dispatchers.Main) { block() } }

    /** evaluateJavascript on [tab]; the JSON-string result unwrapped. */
    private suspend fun eval(js: String, tab: Tab): String? = onUi {
        suspendCancellableCoroutine { cont ->
            runCatching {
                tab.webView.evaluateJavascript(js) { result ->
                    if (cont.isActive) cont.resume(unwrap(result))
                }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    /** evaluateJavascript returns a JSON-encoded string; unwrap it once. */
    private fun unwrap(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault(raw)
    }

    /** The runtime is alive in this document (SPA navigations keep it; loads drop it). */
    private suspend fun ensureRuntime(tab: Tab) {
        val alive = eval("typeof window.__oro !== 'undefined' ? '1' : ''", tab)
        if (alive != "1") {
            onUi(EVAL_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    runCatching { tab.webView.evaluateJavascript(BrowserScripts.RUNTIME_JS) { if (cont.isActive) cont.resume(Unit) } }
                        .onFailure { if (cont.isActive) cont.resume(Unit) }
                }
            }
        }
    }

    private fun jsonObj(raw: String?): JSONObject? = raw?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun outcome(obj: JSONObject?, okText: String, failPrefix: String): String {
        if (obj == null) return "$failPrefix: the page did not answer."
        if (obj.optBoolean("ok")) return okText
        val why = obj.optString("why").ifBlank { "the element is gone or the page changed" }
        val options = obj.optJSONArray("options")
        val hint = if (options != null && options.length() > 0) {
            " Options: " + (0 until minOf(options.length(), 10)).joinToString(", ") { options.optString(it) }
        } else ""
        return "$failPrefix: $why.$hint"
    }

    /** The oid is gone (an SPA re-rendered): rebind it to the same selector and retry once. */
    private suspend fun rebind(oid: String, selector: String, tab: Tab): Boolean {
        if (oid.isBlank() || selector.isBlank()) return false
        val js = "(function(){try{var el=document.querySelector(${BrowserScripts.q(selector)});" +
            "if(el){el.setAttribute('data-oid',${BrowserScripts.q(oid)});return '1';}return '';}catch(e){return '';}})()"
        return eval(js, tab) == "1"
    }

    private fun describe(e: PageElement): String =
        if (e.label.isNotBlank()) "“${e.label.take(40)}”" else "${e.role} [${e.index}]"

    // ------------------------------------------------------------ Browser

    override fun uptime(): Long = SystemClock.uptimeMillis()

    override val lastChangeAt: Long
        get() = tabs[activeId]?.lastNavAt ?: 0L

    override fun isLatest(state: PageState): Boolean {
        val tab = tabs[activeId] ?: return false
        return state.capturedAt >= tab.lastNavAt && tab.url == state.url
    }

    override suspend fun snapshot(): PageState {
        val tab = tabOrNull() ?: return missingTabPage()
        ensureRuntime(tab)
        val raw = eval(BrowserScripts.snapshotJs(), tab)
        val obj = jsonObj(raw) ?: return PageState(
            url = tab.url, title = tab.title, elements = emptyList(), text = "", signature = 0,
            scrollY = 0, pageHeight = 0, viewportWidth = VIEWPORT_FALLBACK_W, viewportHeight = VIEWPORT_FALLBACK_H,
            loading = tab.loading,
        )
        val page = runCatching {
            PageModel.fromJson(kotlinx.serialization.json.Json.parseToJsonElement(obj.toString()).let {
                it as kotlinx.serialization.json.JsonObject
            })
        }.getOrNull() ?: return PageState(
            url = tab.url, title = tab.title, elements = emptyList(), text = "", signature = 0,
            scrollY = 0, pageHeight = 0, viewportWidth = VIEWPORT_FALLBACK_W, viewportHeight = VIEWPORT_FALLBACK_H,
            loading = tab.loading,
        )
        return page.copy(
            consoleErrors = tab.consoleErrors,
            loading = page.loading || tab.loading,
            capturedAt = System.currentTimeMillis(),
        )
    }

    private fun missingTabPage() = PageState(
        url = "", title = "", elements = emptyList(), text = "", signature = 0,
        scrollY = 0, pageHeight = 0, viewportWidth = VIEWPORT_FALLBACK_W, viewportHeight = VIEWPORT_FALLBACK_H,
        loading = false,
    )

    override suspend fun settle(quietMs: Long, maxMs: Long, minMs: Long) {
        val tab = tabOrNull() ?: return
        val start = uptime()
        var lastHash: String? = null
        var lastChange = start
        while (true) {
            val now = uptime()
            if (now - start >= maxMs) break
            if (now - start >= minMs && now - lastChange >= quietMs && !tab.loading) break
            val h = eval(BrowserScripts.hashJs(), tab)?.let { jsonObj(it)?.opt("h")?.toString() }
            if (h != null && h != lastHash) {
                lastHash = h
                lastChange = now
                tab.lastNavAt = now
            }
            delay(110)
        }
    }

    override suspend fun navigate(url: String): String {
        val target = normaliseUrl(url)
        WebGuard.url(target)?.let { return externalIntent(target) }
        if (!target.startsWith("http://") && !target.startsWith("https://") && !target.startsWith("about:")) {
            return "Refused to open “$url”: only http(s) pages open in the browser."
        }
        val tab = onUi { ensureTab() } ?: return "Could not open a browser tab."
        tab.loadError = null
        tab.consoleErrors = 0
        val done = CompletableDeferred<Unit>()
        tab.navDone = done
        onUi(5_000) { tab.webView.loadUrl(target) }
        val arrived = withTimeoutOrNull(NAV_TIMEOUT_MS) { done.await(); true } ?: false
        tab.navDone = null
        settle(quietMs = 250, maxMs = 3_000, minMs = 200)
        tab.loadError?.let { return "Could not load $target: $it" }
        return if (arrived) {
            "Opened ${tab.url.ifBlank { target }}${tab.title.takeIf { it.isNotBlank() }?.let { " — “$it”" } ?: ""}."
        } else "Opened $target but it is still loading after ${NAV_TIMEOUT_MS / 1000}s; wait_for something on it, or reload."
    }

    /** tel:/mailto:/intent: leave the browser through Android, where the user confirms. */
    private fun externalIntent(uri: String): String {
        val action = if (uri.startsWith("tel:", true)) Intent.ACTION_DIAL else Intent.ACTION_VIEW // never place the call
        return try {
            val intent = Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            "Opened ${uri.substringBefore(':')} outside the browser; Android asks the user to confirm."
        } catch (e: Exception) {
            "Could not open $uri: ${e.message ?: "no app handles it"}"
        }
    }

    override suspend fun back(): String = navButton("back") { wv -> if (wv.canGoBack()) { wv.goBack(); true } else false }
    override suspend fun forward(): String = navButton("forward") { wv -> if (wv.canGoForward()) { wv.goForward(); true } else false }
    override suspend fun reload(): String = navButton("reload") { wv -> wv.reload(); true }

    private suspend fun navButton(name: String, act: (WebView) -> Boolean): String {
        val tab = onUi { ensureTab() } ?: return "No browser tab."
        val done = CompletableDeferred<Unit>()
        tab.navDone = done
        val could = onUi(5_000) { act(tab.webView) } ?: false
        if (!could) {
            tab.navDone = null
            return "Cannot go $name: ${if (name == "back") "this tab has no earlier page" else "nothing to go to"}."
        }
        withTimeoutOrNull(NAV_TIMEOUT_MS) { done.await() }
        tab.navDone = null
        settle(quietMs = 200, maxMs = 2_000, minMs = 150)
        return when (name) {
            "reload" -> "Reloaded ${tab.url}."
            else -> "Went $name to ${tab.url}."
        }
    }

    override suspend fun click(e: PageElement): String {
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        var r = eval(BrowserScripts.clickJs(e.oid), tab)?.let { jsonObj(it) }
        if (r != null && !r.optBoolean("ok") && r.optString("why") == "gone" && rebind(e.oid, e.selector, tab)) {
            r = eval(BrowserScripts.clickJs(e.oid), tab)?.let { jsonObj(it) }
        }
        return outcome(r, "Clicked ${describe(e)}.", "Could not click ${describe(e)}")
    }

    override suspend fun bySelector(selector: String): PageElement? {
        val tab = tabOrNull() ?: return null
        ensureRuntime(tab)
        val js = "(function(){try{" +
            "var el=document.querySelector(${BrowserScripts.q(selector)});if(!el)return JSON.stringify({found:false});" +
            "var oid=el.getAttribute('data-oid');if(!oid){oid='s'+Date.now();el.setAttribute('data-oid',oid);}" +
            "var r=el.getBoundingClientRect();var tag=el.tagName.toLowerCase();" +
            "return JSON.stringify({found:true,oid:oid,tag:tag," +
            "t:window.__oro.txt(el.getAttribute('aria-label')||el.textContent||el.getAttribute('placeholder')||el.getAttribute('name')||'',60)," +
            "x:Math.round(r.left),y:Math.round(r.top),w:Math.round(r.width),h:Math.round(r.height)," +
            "ed:(tag==='input'||tag==='textarea')&&el.type!=='checkbox'&&el.type!=='radio',pw:el.type==='password'});" +
            "}catch(e){return JSON.stringify({found:false});}})()"
        val obj = eval(js, tab)?.let { jsonObj(it) } ?: return null
        if (!obj.optBoolean("found")) return null
        val x = obj.optInt("x"); val y = obj.optInt("y")
        val tag = obj.optString("tag")
        val role = when (tag) {
            "a" -> "link"
            "button" -> "button"
            "select" -> "combobox"
            "textarea" -> "textbox"
            "input" -> if (obj.optBoolean("pw")) "textbox" else if (obj.optBoolean("ed")) "textbox" else "button"
            else -> "area"
        }
        return PageElement(
            index = -1, role = role, label = obj.optString("t"),
            bounds = Bounds(x, y, x + obj.optInt("w"), y + obj.optInt("h")),
            oid = obj.optString("oid"), selector = selector,
            editable = obj.optBoolean("ed"), password = obj.optBoolean("pw"),
        )
    }

    override suspend fun type(e: PageElement?, text: String, append: Boolean): String {
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        if (e == null) {
            val r = eval(BrowserScripts.typeJs(null, text, append), tab)?.let { jsonObj(it) }
            return if (r?.optBoolean("ok") == true) "Typed into the focused field: now “${r.optString("value").take(40)}”."
            else "No field is focused; give the field's index from the page listing."
        }
        var r = eval(BrowserScripts.typeJs(e.oid, text, append), tab)?.let { jsonObj(it) }
        if (r != null && !r.optBoolean("ok") && r.optString("why") == "gone" && rebind(e.oid, e.selector, tab)) {
            r = eval(BrowserScripts.typeJs(e.oid, text, append), tab)?.let { jsonObj(it) }
        }
        return when {
            r == null -> "Could not type into ${describe(e)}: the page did not answer."
            r.optBoolean("ok") -> "Typed into ${describe(e)}" +
                (if (e.password) " (hidden)." else ": now “${r.optString("value").take(40)}”.")
            else -> outcome(r, "Typed.", "Could not type into ${describe(e)}")
        }
    }

    override suspend fun submit(): String {
        val tab = tabOrNull() ?: return NO_TAB
        val r = eval(BrowserScripts.keyJs("enter", null), tab)?.let { jsonObj(it) }
        return if (r?.optBoolean("ok") == true) "Pressed Enter (the form submits if it has one)." else "Could not press Enter."
    }

    override suspend fun pressKey(key: String): String {
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        val r = eval(BrowserScripts.keyJs(key, null), tab)?.let { jsonObj(it) }
        return outcome(r, "Pressed $key.", "Could not press $key")
    }

    override suspend fun select(e: PageElement, option: String): String {
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        var r = eval(BrowserScripts.selectJs(e.oid, option), tab)?.let { jsonObj(it) }
        if (r != null && !r.optBoolean("ok") && rebind(e.oid, e.selector, tab)) {
            r = eval(BrowserScripts.selectJs(e.oid, option), tab)?.let { jsonObj(it) }
        }
        return when {
            r?.optBoolean("ok") == true -> "Chose “${r.optString("chose").take(40)}” in ${describe(e)}."
            else -> outcome(r, "Chose.", "Could not choose “${option.take(30)}” in ${describe(e)}")
        }
    }

    override suspend fun setChecked(e: PageElement, checked: Boolean): String {
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        var r = eval(BrowserScripts.checkJs(e.oid, checked), tab)?.let { jsonObj(it) }
        if (r != null && !r.optBoolean("ok") && rebind(e.oid, e.selector, tab)) {
            r = eval(BrowserScripts.checkJs(e.oid, checked), tab)?.let { jsonObj(it) }
        }
        return when {
            r == null -> "Could not change ${describe(e)}: the page did not answer."
            r.optBoolean("ok") -> when {
                r.optBoolean("already") -> if (checked) "${describe(e)} was already checked." else "${describe(e)} was already off."
                checked -> "Checked ${describe(e)}."
                else -> "Unchecked ${describe(e)}."
            }
            else -> outcome(r, "Changed.", "Could not change ${describe(e)}")
        }
    }

    override suspend fun scroll(direction: String, e: PageElement?, amount: Float): String {
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        val r = eval(BrowserScripts.scrollJs(direction, e?.oid, amount), tab)?.let { jsonObj(it) }
        return when {
            r?.optBoolean("ok") == true -> "Scrolled $direction to y=${r.optInt("scrollY")} of ${r.optInt("pageHeight")}."
            else -> outcome(r, "Scrolled $direction.", "Could not scroll")
        }
    }

    override suspend fun scrollToText(text: String): String {
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        val r = eval(BrowserScripts.scrollToTextJs(text), tab)?.let { jsonObj(it) }
        return if (r?.optBoolean("ok") == true) "Scrolled to “${r.optString("text").take(40)}” (${r.optInt("count")} on the page)."
        else "“${text.take(40)}” is not on this page."
    }

    override suspend fun hover(e: PageElement): String {
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        val r = eval(BrowserScripts.hoverJs(e.oid), tab)?.let { jsonObj(it) }
        return outcome(r, "Hovered over ${describe(e)}.", "Could not hover over ${describe(e)}")
    }

    override suspend fun evaluate(js: String): String {
        val trimmed = js.trim()
        if (trimmed.startsWith("javascript:", true)) {
            return "Refused: drop the javascript: prefix; the code runs in the page as-is."
        }
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        val r = eval(BrowserScripts.evalJs(trimmed), tab)?.let { jsonObj(it) }
        return when {
            r == null -> "The page did not answer."
            r.optBoolean("ok") -> r.optString("result").ifBlank { "(no result)" }
            else -> "JavaScript failed: ${r.optString("why")}"
        }
    }

    override suspend fun extract(selector: String?, fieldsJson: String, limit: Int): String {
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        val obj = eval(BrowserScripts.extractJs(selector, fieldsJson, limit), tab)?.let { jsonObj(it) }
            ?: return "The page did not answer the extraction."
        if (!obj.optBoolean("ok")) return "Extraction failed: ${obj.optString("why")}"
        val rows = obj.optJSONArray("rows") ?: return "No rows."
        val text = (0 until rows.length()).joinToString("\n") { rows.get(it).toString() }
        return "${rows.length()} row(s) of ${obj.optInt("count")} matched:\n" + text.take(12_000) +
            if (text.length > 12_000) "\n[... truncated; raise precision with a tighter selector]" else ""
    }

    override suspend fun readPage(mode: String, selector: String?, maxChars: Int): String {
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        val r = eval(BrowserScripts.readJs(mode, selector, maxChars), tab)?.let { jsonObj(it) }
        return r?.optString("text")?.takeIf { it.isNotBlank() }
            ?: "The page has no readable text (or the selector matched nothing)."
    }

    override suspend fun screenshot(fullPage: Boolean): String? = onUi(15_000) {
        suspendCancellableCoroutine { cont ->
            val tab = tabOrNull()
            if (tab == null) { if (cont.isActive) cont.resume(null); return@suspendCancellableCoroutine }
            val wv = tab.webView
            fun draw(width: Int, height: Int): String? = runCatching {
                val scale = minOf(1f, 1080f / width.coerceAtLeast(1))
                val bw = (width * scale).toInt().coerceAtLeast(1)
                val bh = (height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
                try {
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    canvas.scale(scale, scale)
                    wv.draw(canvas)
                    val out = java.io.ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 72, out)
                    android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                } finally { bmp.recycle() }
            }.getOrNull()

            val result = if (!fullPage || wv.parent != null) {
                draw(wv.width.coerceAtLeast(1), wv.height.coerceAtLeast(1))
            } else {
                val contentH = (wv.contentHeight * wv.scale).toInt()
                val capH = wv.height * 4
                val h = contentH.coerceIn(wv.height.coerceAtLeast(1), capH.coerceAtLeast(wv.height.coerceAtLeast(1)))
                val oldH = wv.height
                wv.measure(
                    View.MeasureSpec.makeMeasureSpec(wv.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, wv.width, h)
                val shot = draw(wv.width, h)
                wv.measure(
                    View.MeasureSpec.makeMeasureSpec(wv.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(oldH, View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, wv.width, oldH)
                shot
            }
            if (cont.isActive) cont.resume(result)
        }
    }

    override suspend fun waitForText(text: String, gone: Boolean, timeoutMs: Long): String {
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        val start = uptime()
        while (uptime() - start < timeoutMs) {
            val r = eval(BrowserScripts.textVisibleJs(text), tab)?.let { jsonObj(it) }
            val found = r?.optBoolean("found") ?: false
            if (found != gone) {
                val waited = "%.1f".format((uptime() - start) / 1000.0)
                return if (gone) "“${text.take(40)}” is gone after ${waited}s." else "“${text.take(40)}” appeared after ${waited}s."
            }
            delay(400)
        }
        return "Waited ${timeoutMs / 1000}s; “${text.take(40)}” ${if (gone) "is still there" else "did not appear"}."
    }

    override suspend fun waitForSelector(selector: String, gone: Boolean, timeoutMs: Long): String {
        val tab = tabOrNull() ?: return NO_TAB
        val start = uptime()
        val js = "(function(){try{return JSON.stringify({found:!!document.querySelector(${BrowserScripts.q(selector)})});}catch(e){return JSON.stringify({found:false});}})()"
        while (uptime() - start < timeoutMs) {
            val found = eval(js, tab)?.let { jsonObj(it)?.optBoolean("found") } ?: false
            if (found != gone) {
                val waited = "%.1f".format((uptime() - start) / 1000.0)
                return if (gone) "$selector is gone after ${waited}s." else "$selector appeared after ${waited}s."
            }
            delay(350)
        }
        return "Waited ${timeoutMs / 1000}s; $selector ${if (gone) "is still there" else "did not appear"}."
    }

    override suspend fun waitForUrl(fragment: String, timeoutMs: Long): String {
        val tab = tabOrNull() ?: return NO_TAB
        val start = uptime()
        while (uptime() - start < timeoutMs) {
            if (tab.url.contains(fragment, true)) return "The URL now contains “$fragment” (${tab.url})."
            delay(250)
        }
        return "Waited ${timeoutMs / 1000}s; the URL (${tab.url}) still does not contain “$fragment”."
    }

    override suspend fun textVisible(text: String): Boolean {
        val tab = tabOrNull() ?: return false
        val r = eval(BrowserScripts.textVisibleJs(text), tab)?.let { jsonObj(it) }
        return r?.optBoolean("found") ?: false
    }

    override fun url(): String = tabs[activeId]?.url.orEmpty()
    override fun title(): String = tabs[activeId]?.title.orEmpty()

    override fun tabs(): List<TabInfo> = tabs.values.map { TabInfo(it.id, it.url, it.title, it.id == activeId) }

    override suspend fun switchTab(id: Int): String {
        if (!tabs.containsKey(id)) return "No tab $id. Tabs: ${tabs.keys.joinToString().ifBlank { "(none)" }}."
        onUi(5_000) {
            activeId = id
            tabs[id]?.let { reparent(it) }
            publishUi()
        }
        settle(quietMs = 200, maxMs = 1_500, minMs = 100)
        return "Switched to tab $id: ${tabs[id]?.url.orEmpty()}."
    }

    override suspend fun closeTab(id: Int): String {
        if (!tabs.containsKey(id)) return "No tab $id."
        onUi(5_000) { removeTab(id) }
        return "Closed tab $id. ${tabs.size} tab(s) left."
    }

    override suspend fun newTab(url: String?): String {
        val tab = onUi(8_000) {
            val t = createTab("about:blank")
            activeId = t.id
            reparent(t)
            t
        } ?: return "Could not open a new tab."
        if (!url.isNullOrBlank()) return navigate(url)
        return "Opened a blank tab ${tab.id}."
    }

    override suspend fun uploadFile(e: PageElement, absolutePath: String): String {
        val f = File(absolutePath)
        if (!f.exists()) return "No such file to upload: $absolutePath"
        pendingUpload = f
        uploadConsumed = false
        val click = click(e)
        // Wait (briefly) for the chooser to consume it, so a stale file can
        // never answer a later, human-opened chooser.
        var waitedMs = 0
        while (!uploadConsumed && waitedMs < 2_000) {
            delay(200)
            waitedMs += 200
        }
        pendingUpload = null
        return if (click.startsWith("Clicked")) {
            if (uploadConsumed) "Gave ${f.name} (${f.length()} bytes) to ${describe(e)}."
            else "Clicked ${describe(e)} and prepared ${f.name}; no file dialog opened -- the page may not use one here."
        } else click
    }

    override fun downloads(): List<DownloadInfo> = _downloads.value

    override fun readDownload(name: String, maxChars: Int): String {
        val d = _downloads.value.firstOrNull { it.name.equals(name, true) || it.path.endsWith(name, true) }
            ?: return "No download named “$name”. Downloads: ${_downloads.value.joinToString { it.name }.ifBlank { "(none)" }}"
        val f = File(d.path)
        if (!f.exists()) return "The download ${d.name} is gone from disk."
        val textual = d.mime.startsWith("text") || listOf("json", "csv", "xml", "javascript").any { d.mime.contains(it) }
        if (!textual) {
            return "${d.name} is a ${d.mime.ifBlank { "binary" }} file (${d.bytes} bytes). It cannot be read as text; the user can open or share it from the browser screen."
        }
        val text = runCatching { f.readText().take(maxChars) }.getOrNull() ?: return "Could not read ${d.name}."
        return "${d.name} (${d.bytes} bytes):\n$text" + (if (f.length() > maxChars) "\n[... truncated]" else "")
    }

    override suspend fun focusForUser(e: PageElement?) {
        val tab = tabOrNull() ?: return
        ensureRuntime(tab)
        if (e != null && e.oid.isNotBlank()) {
            val js = "(function(){var el=window.__oro&&window.__oro.byId(${BrowserScripts.q(e.oid)});" +
                "if(el){try{el.scrollIntoView({block:'center'});el.focus();}catch(x){}}return '{}';})()"
            eval(js, tab)
        }
    }

    override suspend fun typeSecret(e: PageElement, secret: String): String {
        val tab = tabOrNull() ?: return NO_TAB
        ensureRuntime(tab)
        val r = eval(BrowserScripts.typeJs(e.oid, secret, append = false), tab)?.let { jsonObj(it) }
        return if (r?.optBoolean("ok") == true) "The secret was entered into ${describe(e)} (its value is never shown)."
        else "Could not enter the secret into ${describe(e)}: the field may be gone; take a fresh look."
    }

    override fun answerHttpAuth(proceed: Boolean, user: String?, password: String?) {
        main.post {
            val tab = tabs[activeId] ?: return@post
            val handler = tab.httpAuth ?: return@post
            tab.httpAuth = null; tab.httpAuthHost = null; tab.httpAuthRealm = null
            if (proceed) handler.proceed(user.orEmpty(), password.orEmpty()) else handler.cancel()
            publishUi()
        }
    }

    override fun status(): String = buildString {
        val tab = tabs[activeId]
        append("Browser: ").append(if (tab == null) "no tab open" else "${tabs.size} tab(s), active ${tab.id}")
        if (tab != null) {
            append("\nURL: ").append(tab.url)
            if (tab.title.isNotBlank()) append("\nTitle: ").append(tab.title)
            if (tab.loading) append("\n(loading)")
            tab.loadError?.let { append("\nLast error: ").append(it) }
        }
        append("\nDownloads: ").append(if (_downloads.value.isEmpty()) "none" else _downloads.value.joinToString { "${it.name} (${it.bytes} b)" })
        append("\nDesktop mode: ").append(if (desktopMode) "on" else "off")
    }

    private const val NO_TAB = "No browser tab is open; open_url first."

    // ------------------------------------------------------- WebView plumbing

    // Small hooks the nested WebView clients use (nested classes inside an
    // object cannot be inner, so they call back through these).
    internal fun appContext(): Context = app
    internal fun setProgress(p: Int) { _ui.value = _ui.value.copy(progress = p) }
    internal fun bumpChooser() { _chooserTick.value += 1 }


    private class Client(private val tab: Tab) : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            tab.loading = true
            tab.url = url ?: tab.url
            tab.loadError = null
            tab.lastNavAt = SystemClock.uptimeMillis()
            BrowserEngine.publishUi()
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                BrowserEngine.main.postDelayed({ runCatching { view?.evaluateJavascript(BrowserScripts.RUNTIME_JS, null) } }, 60)
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            tab.loading = false
            tab.url = view?.url ?: url ?: tab.url
            tab.title = view?.title ?: tab.title
            tab.lastNavAt = SystemClock.uptimeMillis()
            tab.navDone?.complete(Unit)
            BrowserEngine.publishUi()
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            // SPA navigations: the URL moved without a load.
            tab.url = url ?: tab.url
            tab.lastNavAt = SystemClock.uptimeMillis()
            tab.navDone?.complete(Unit)
            BrowserEngine.publishUi()
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            val lower = url.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("about:")) return false
            // Everything else (tel:, mailto:, intent:, market:, app links) goes
            // out through Android, where the user confirms.
            BrowserEngine.externalIntent(url)
            return true
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame == true) {
                tab.loadError = "network error ${error?.errorCode ?: "?"} (${error?.description ?: "unknown"})"
                tab.navDone?.complete(Unit)
                BrowserEngine.publishUi()
            }
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            // Never proceed on a broken certificate, whatever the page says.
            handler?.cancel()
            val host = error?.url?.let { runCatching { Uri.parse(it).host }.getOrNull() } ?: "the site"
            tab.loadError = "TLS error for $host: the certificate is not trusted. Not proceeding."
            tab.navDone?.complete(Unit)
            BrowserEngine.publishUi()
        }

        override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
            tab.httpAuth = handler
            tab.httpAuthHost = host
            tab.httpAuthRealm = realm
            BrowserEngine.publishUi()
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            // The renderer died (a heavy page, low memory): rebuild the tab and
            // reload what was there, like PreviewWebView does.
            tab.loadError = "The page's renderer stopped; the tab was rebuilt and reloaded."
            val oldUrl = tab.url
            BrowserEngine.main.post {
                val wasAttached = view?.parent != null
                val parent = view?.parent as? FrameLayout
                BrowserEngine.tabs.remove(tab.id)
                runCatching { parent?.removeView(view) }
                runCatching { view?.destroy() }
                val fresh = BrowserEngine.createTab(oldUrl)
                BrowserEngine.activeId = fresh.id
                if (wasAttached && parent != null) {
                    BrowserEngine.attachedContainer = parent
                    BrowserEngine.reparent(fresh)
                }
                tab.navDone?.complete(Unit)
                BrowserEngine.publishUi()
            }
            return true
        }
    }

    private class Chrome(private val tab: Tab) : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            BrowserEngine.setProgress(newProgress)
            if (newProgress >= 100) {
                tab.loading = false
                tab.navDone?.complete(Unit)
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            tab.title = title ?: tab.title
            BrowserEngine.publishUi()
        }

        override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
            if (msg?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) tab.consoleErrors++
            return true
        }

        override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
            val upload = BrowserEngine.pendingUpload
            if (upload != null && upload.exists()) {
                BrowserEngine.uploadConsumed = true
                BrowserEngine.pendingUpload = null
                runCatching {
                    val uri = FileProvider.getUriForFile(BrowserEngine.appContext(), BrowserEngine.appContext().packageName + ".fileprovider", upload)
                    callback?.onReceiveValue(arrayOf(uri))
                }.onFailure { callback?.onReceiveValue(null) }
                return true
            }
            // No agent upload: the user picks, if the browser screen is open.
            BrowserEngine.chooserWaiting?.onReceiveValue(null)
            BrowserEngine.chooserWaiting = callback
            BrowserEngine.bumpChooser()
            BrowserEngine.scope.launch {
                delay(120_000)
                if (BrowserEngine.chooserWaiting === callback) {
                    callback?.onReceiveValue(null)
                    BrowserEngine.chooserWaiting = null
                }
            }
            return true
        }

        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            // window.open / target=_blank: a real new tab, wired through the transport.
            val temp = WebView(BrowserEngine.appContext())
            temp.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString()
                    runCatching { temp.destroy() }
                    if (url != null) BrowserEngine.main.post {
                        val t = BrowserEngine.createTab("about:blank")
                        BrowserEngine.activeId = t.id
                        BrowserEngine.reparent(t)
                        t.webView.loadUrl(url)
                    }
                    return true
                }
            }
            val transport = resultMsg?.obj as? WebView.WebViewTransport
            if (transport == null) { runCatching { temp.destroy() }; return false }
            transport.webView = temp
            resultMsg.sendToTarget()
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            // Camera/mic inside pages: denied by default.
            request?.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            callback?.invoke(origin, false, false)
        }

        override fun onCloseWindow(window: WebView?) {
            val t = BrowserEngine.tabs.values.firstOrNull { it.webView === window } ?: return
            BrowserEngine.main.post { BrowserEngine.removeTab(t.id) }
        }
    }

    private class Downloader : DownloadListener {
        override fun onDownloadStart(url: String?, userAgent: String?, contentDisposition: String?, mimeType: String?, contentLength: Long) {
            if (url == null) return
            BrowserEngine.scope.launch(Dispatchers.IO) { BrowserEngine.saveDownload(url, userAgent, contentDisposition, mimeType, contentLength) }
        }
    }

    private suspend fun saveDownload(url: String, userAgent: String?, disposition: String?, mime: String?, length: Long) {
        if (length > MAX_DOWNLOAD_BYTES) return
        val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        val name = guessName(url, disposition, mime)
        val dir = File(app.filesDir, "downloads").apply { mkdirs() }
        val file = File(dir, uniqueName(dir, name))
        runCatching {
            val request = Request.Builder().url(url)
                .header("User-Agent", userAgent ?: DESKTOP_UA)
                .apply { cookie?.let { header("Cookie", it) } }
                .build()
            val client = AiHttp.client.newBuilder()
                .callTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return
                val type = r.header("Content-Type") ?: mime ?: "application/octet-stream"
                r.body?.byteStream()?.use { input ->
                    file.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_DOWNLOAD_BYTES) { file.delete(); return }
                            output.write(buf, 0, n)
                        }
                    }
                    val info = DownloadInfo(file.name, type, file.length(), System.currentTimeMillis(), file.absolutePath)
                    _downloads.value = (_downloads.value + info).takeLast(12)
                }
            }
        }.onFailure { runCatching { file.delete() } }
    }

    private fun guessName(url: String, disposition: String?, mime: String?): String {
        disposition?.let {
            val m = Regex("filename\\*?=(?:UTF-8''|\")?([^\";]+)").find(it)
            if (m != null) return m.groupValues[1].trim().removeSurrounding("\"")
        }
        val last = runCatching { Uri.parse(url).lastPathSegment }.getOrNull()
        if (!last.isNullOrBlank() && last.contains('.')) return last.take(80)
        val ext = when {
            mime == null -> "bin"
            mime.contains("pdf") -> "pdf"
            mime.contains("json") -> "json"
            mime.contains("csv") -> "csv"
            mime.contains("zip") -> "zip"
            mime.contains("html") -> "html"
            mime.contains("image/png") -> "png"
            mime.contains("image") -> "jpg"
            mime.startsWith("text") -> "txt"
            else -> "bin"
        }
        return "download-${System.currentTimeMillis()}.$ext"
    }

    private fun uniqueName(dir: File, name: String): String {
        var f = File(dir, name)
        var i = 1
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        while (f.exists()) { f = File(dir, "$base-$i$ext"); i++ }
        return f.name
    }

    /** JS → native channel: page events the engine folds into its state. */
    private class Bridge(private val tab: Tab) {
        @JavascriptInterface
        fun event(kind: String?, data: String?) {
            when (kind) {
                "formsubmit" -> tab.lastNavAt = SystemClock.uptimeMillis()
                "nav" -> tab.navDone?.complete(Unit)
            }
        }

        @JavascriptInterface
        fun log(message: String?) {
            if (message != null) tab.consoleErrors++
        }
    }

    /** The browser screen's file picker answered a page's chooser. */
    fun answerFileChooser(uris: Array<Uri>?) {
        val cb = chooserWaiting
        chooserWaiting = null
        cb?.onReceiveValue(uris)
    }
}
