package com.oro.app.community

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.oro.app.data.ai.community.MercuryBrowserBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * chat.inceptionlabs.ai calls made from inside the real site, in a WebView
 * that is never shown. Vercel's security checkpoint in front of the site runs
 * a JavaScript check that a plain HTTP client cannot pass; a real browser
 * engine passes it once and keeps the clearance cookie. Oro then only issues
 * same-origin `fetch` calls (mint a guest session, post the chat) and reads
 * the streamed lines back through a JavaScript interface.
 *
 * The page is loaded once and reused. While it shows the checkpoint, the
 * bridge waits: the checkpoint reloads the page itself when it is satisfied,
 * which finishes loading again. The page is dropped after a failure or
 * [MAX_REQUESTS] requests.
 */
class MercuryWebBridge(context: Context) : MercuryBrowserBridge {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val channels = ConcurrentHashMap<String, SendChannel<String>>()
    private var web: WebView? = null
    private var ready: CompletableDeferred<Unit>? = null
    private var requests = 0

    override fun chat(body: String): Flow<String> = callbackFlow {
        val id = UUID.randomUUID().toString()
        channels[id] = channel
        val failure: String? = try {
            val loaded = withContext(Dispatchers.Main) { page() }
            withTimeout(PAGE_TIMEOUT_MS) { loaded.await() }
            withContext(Dispatchers.Main) {
                requests++
                web?.evaluateJavascript(script(id, body), null) ?: channel.close()
            }
            null
        } catch (e: TimeoutCancellationException) {
            "the site's browser check did not finish; try again in a minute"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "the in-app browser could not load chat.inceptionlabs.ai"
        }
        if (failure != null) {
            withContext(Dispatchers.Main) { reset() }
            trySend("HTTP 0 browser-error")
            trySend(failure)
            channel.close()
        }
        awaitClose { channels.remove(id) }
    }

    /** The loaded page past the checkpoint, creating it on first use (main thread). */
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
            addJavascriptInterface(Js(), "OroMercury")
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // The checkpoint page also "finishes"; only the real app counts.
                    view?.evaluateJavascript("document.title") { title ->
                        if (!title.orEmpty().contains("Checkpoint", ignoreCase = true)) {
                            main.postDelayed({ done.complete(Unit) }, SETTLE_MS)
                        }
                    }
                }
            }
        }
        web = view
        view.loadUrl(SITE_URL)
        return done
    }

    private fun reset() {
        web?.let { w -> runCatching { w.stopLoading(); w.destroy() } }
        web = null
        ready = null
        requests = 0
    }

    private inner class Js {
        @JavascriptInterface fun line(id: String, text: String) { channels[id]?.trySend(text) }
        @JavascriptInterface fun end(id: String) {
            channels[id]?.close()
            channels.remove(id)
        }
    }

    private fun script(id: String, body: String): String = """
        (async () => {
          const id = ${JSONObject.quote(id)};
          const send = (t) => OroMercury.line(id, t);
          try {
            let tok = window.__oroMercuryToken;
            const mint = async () => {
              const s = await fetch('/api/session', { credentials: 'include', headers: { 'accept': '*/*' } });
              if (!s.ok) throw new Error('could not start a guest session (HTTP ' + s.status + ')');
              const j = await s.json();
              tok = j.token;
              window.__oroMercuryToken = tok;
              window.__oroMercuryExpires = Date.now() + 50 * 60 * 1000;
            };
            if (!tok || Date.now() > (window.__oroMercuryExpires || 0)) await mint();
            const post = () => {
              document.cookie = 'session=' + tok + '; path=/; secure; samesite=lax';
              return fetch('/api/chat', {
                method: 'POST', credentials: 'include',
                headers: { 'content-type': 'application/json', 'accept': 'text/event-stream, */*', 'x-session-token': tok },
                body: ${JSONObject.quote(body)}
              });
            };
            let r = await post();
            if (r.status === 403) { await mint(); r = await post(); }
            send('HTTP ' + r.status + ' ' + (r.headers.get('content-type') || ''));
            const reader = r.body.getReader(); const dec = new TextDecoder(); let buf = '';
            for (;;) {
              const { done, value } = await reader.read();
              if (done) break;
              buf += dec.decode(value, { stream: true });
              let i;
              while ((i = buf.indexOf('\n')) >= 0) { send(buf.slice(0, i)); buf = buf.slice(i + 1); }
            }
            buf += dec.decode();
            if (buf) send(buf);
          } catch (e) {
            send('HTTP 0 error'); send(String(e && e.message ? e.message : e));
          } finally { OroMercury.end(id); }
        })();
    """.trimIndent()

    private companion object {
        const val SITE_URL = "https://chat.inceptionlabs.ai/"
        const val MAX_REQUESTS = 40
        const val SETTLE_MS = 600L
        const val PAGE_TIMEOUT_MS = 45_000L
    }
}
