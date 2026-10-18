package com.oro.app.community

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.oro.app.data.ai.community.QwenBrowserBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * chat.qwen.ai requests made from inside the real site, in a WebView that is
 * never shown. The page brings a genuine browser's TLS, cookies and the
 * site's own risk SDK, which is what its anti-bot layer wants to see; Oro
 * only issues same-origin `fetch` calls and reads the streamed lines back
 * through a JavaScript interface.
 *
 * The page is loaded once and reused; it is dropped (and reloaded on the
 * next request) after a failure or [MAX_REQUESTS] requests, which also rotates
 * the guest identity the site sees.
 */
class QwenWebBridge(context: Context) : QwenBrowserBridge {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val channels = ConcurrentHashMap<String, SendChannel<String>>()
    private var web: WebView? = null
    private var ready: CompletableDeferred<Unit>? = null
    private var requests = 0

    override fun post(path: String, body: String): Flow<String> = callbackFlow {
        val id = UUID.randomUUID().toString()
        channels[id] = channel
        try {
            withContext(Dispatchers.Main) { page() }.let { deferred -> withTimeout(PAGE_TIMEOUT_MS) { deferred.await() } }
            withContext(Dispatchers.Main) {
                requests++
                web?.evaluateJavascript(script(id, path, body), null) ?: channel.close()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { reset() }
            trySend("HTTP 0 browser-error")
            trySend(e.message ?: "the in-app browser could not load chat.qwen.ai")
            channel.close()
        }
        awaitClose { channels.remove(id) }
    }

    /** The loaded page, creating it on first use (main thread). */
    @SuppressLint("SetJavaScriptEnabled")
    private fun page(): CompletableDeferred<Unit> {
        if (requests >= MAX_REQUESTS) reset()
        ready?.let { if (web != null) return it }
        val done = CompletableDeferred<Unit>()
        ready = done
        CookieManager.getInstance().setAcceptCookie(true)
        val view = WebView(app).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Js(), "OroQwen")
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Give the site's risk SDK a moment to initialise before the first call.
                    main.postDelayed({ done.complete(Unit) }, SDK_SETTLE_MS)
                }
            }
        }
        web = view
        view.loadUrl("https://chat.qwen.ai/")
        return done
    }

    private fun reset() {
        web?.let { w -> runCatching { w.stopLoading(); w.destroy() } }
        web = null
        ready = null
        requests = 0
        // A new identity next time: the site keys its guest quota on these cookies.
        CookieManager.getInstance().setCookie("https://chat.qwen.ai", "ssxmod_itna=; Max-Age=0")
        CookieManager.getInstance().setCookie("https://chat.qwen.ai", "ssxmod_itna2=; Max-Age=0")
    }

    private inner class Js {
        @JavascriptInterface fun line(id: String, text: String) { channels[id]?.trySend(text) }
        @JavascriptInterface fun end(id: String) {
            channels[id]?.close()
            channels.remove(id)
        }
    }

    private fun script(id: String, path: String, body: String): String {
        val q = JSONObject.quote(id)
        return """
            (async () => {
              const id = $q;
              try {
                const r = await fetch(${JSONObject.quote(path)}, {
                  method: 'POST', credentials: 'include',
                  headers: { 'content-type': 'application/json', 'accept': '*/*', 'source': 'web', 'x-request-id': (crypto.randomUUID ? crypto.randomUUID() : String(Date.now())) },
                  body: ${JSONObject.quote(body)}
                });
                OroQwen.line(id, 'HTTP ' + r.status + ' ' + (r.headers.get('content-type') || ''));
                const reader = r.body.getReader(); const dec = new TextDecoder(); let buf = '';
                for (;;) {
                  const { done, value } = await reader.read();
                  if (done) break;
                  buf += dec.decode(value, { stream: true });
                  let i;
                  while ((i = buf.indexOf('\n')) >= 0) { OroQwen.line(id, buf.slice(0, i)); buf = buf.slice(i + 1); }
                }
                if (buf) OroQwen.line(id, buf);
              } catch (e) {
                OroQwen.line(id, 'HTTP 0 error'); OroQwen.line(id, String(e));
              } finally { OroQwen.end(id); }
            })();
        """.trimIndent()
    }

    private companion object {
        const val MAX_REQUESTS = 12
        const val SDK_SETTLE_MS = 1_500L
        const val PAGE_TIMEOUT_MS = 30_000L
    }
}
