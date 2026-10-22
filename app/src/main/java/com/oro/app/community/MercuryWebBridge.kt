package com.oro.app.community

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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
 * the streamed lines back.
 *
 * ### Safety
 *  - The page can only talk back from https://chat.inceptionlabs.ai: lines
 *    arrive through an origin-restricted web message listener where WebView
 *    supports one, else through a JavaScript interface guarded by a host
 *    check before every script. Off-site navigations are refused, so the
 *    request script, which carries the whole conversation, never runs on
 *    another site.
 *  - Nothing hangs: a reset, a reload while a reply streams, or a crashed
 *    renderer ends every pending stream with an error the client can retry.
 *    A renderer that dies is handled here (`onRenderProcessGone`) instead of
 *    taking the whole app down with it.
 *
 * The page is loaded once and reused. While it shows the checkpoint, the
 * bridge waits: the checkpoint reloads the page itself when it is satisfied,
 * which finishes loading again. The page is dropped after a failure, after a
 * fetch meets the checkpoint again, or after [MAX_REQUESTS] requests -- never
 * while a reply is still streaming.
 *
 * Page state is touched on the main thread only.
 */
class MercuryWebBridge(context: Context) : MercuryBrowserBridge {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val channels = ConcurrentHashMap<String, SendChannel<String>>()
    /** Requests whose script is running in the page. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private var web: WebView? = null
    private var ready: CompletableDeferred<Unit>? = null
    /** The real app (not the checkpoint) has finished loading in [web]. */
    private var loaded = false
    /** Lines come back as origin-checked web messages rather than through a JS interface. */
    private var useMessages = false
    /** Reload once nothing is streaming: a fetch met the checkpoint, or the request budget is spent. */
    @Volatile private var reloadWhenIdle = false
    private var requests = 0

    /** A reset ended the request before its script ran. */
    private class Reset(message: String) : Exception(message)

    override fun chat(body: String): Flow<String> = callbackFlow {
        val id = UUID.randomUUID().toString()
        channels[id] = channel
        val failure: String? = try {
            val deferred = withContext(Dispatchers.Main) { page() }
            withTimeout(PAGE_TIMEOUT_MS) { deferred.await() }
            withContext(Dispatchers.Main) {
                val view = web
                if (view == null || !onSite(view.url)) throw Reset("the in-app browser left chat.inceptionlabs.ai")
                requests++
                inFlight += id
                view.evaluateJavascript(script(id, body, useMessages), null)
            }
            null
        } catch (e: TimeoutCancellationException) {
            withContext(Dispatchers.Main) { reset("the site's browser check did not finish") }
            "the site's browser check did not finish; try again in a minute"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Reset) {
            e.message ?: "the in-app browser was reset"
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { reset(e.message ?: "the in-app browser failed") }
            e.message ?: "the in-app browser could not load chat.inceptionlabs.ai"
        }
        if (failure != null) {
            trySend("HTTP 0 browser-error")
            trySend(failure)
            channel.close()
        }
        awaitClose {
            channels.remove(id)
            if (inFlight.remove(id)) main.post { recycleIfIdle() }
        }
    }

    /** The loaded page past the checkpoint, creating it on first use (main thread). */
    @SuppressLint("SetJavaScriptEnabled")
    private fun page(): CompletableDeferred<Unit> {
        recycleIfIdle()
        ready?.let { if (web != null) return it }
        val done = CompletableDeferred<Unit>()
        ready = done
        loaded = false
        CookieManager.getInstance().setAcceptCookie(true)
        val view = WebView(app)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        useMessages = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        if (useMessages) {
            WebViewCompat.addWebMessageListener(view, JS_NAME, setOf(ORIGIN)) { _, message, sourceOrigin, isMainFrame, _ ->
                if (isMainFrame && sourceOrigin.host.equals(HOST, ignoreCase = true)) {
                    runCatching { message.data }.getOrNull()?.let { onMessage(it) }
                }
            }
        } else {
            view.addJavascriptInterface(Js(), JS_NAME)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Only the site itself; a redirect elsewhere is refused.
                if (request == null) return false
                return request.isForMainFrame && !onSite(request.url?.toString())
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (view !== web || !loaded) return
                // The page navigated while it was in use: replies streaming in
                // the old document are gone, and later requests wait for the new one.
                loaded = false
                failInFlight("the site reloaded during the reply; try again")
                ready = CompletableDeferred()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val page = view ?: return
                if (page !== web) return
                // The checkpoint page also "finishes"; only the real app counts.
                page.evaluateJavascript("document.title") { title ->
                    if (page !== web || title.orEmpty().contains("Checkpoint", ignoreCase = true)) return@evaluateJavascript
                    main.postDelayed({
                        if (page === web && onSite(page.url)) {
                            loaded = true
                            ready?.complete(Unit)
                        }
                    }, SETTLE_MS)
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                // Handled: without this, a dead renderer kills the whole app.
                if (view === web) reset("the in-app browser stopped unexpectedly; try again")
                else runCatching { view?.destroy() }
                return true
            }
        }
        web = view
        view.loadUrl(SITE_URL)
        return done
    }

    /** A line or end from the page: `{"id":…, "t":"line"|"end", "v":…}`. */
    private fun onMessage(data: String) {
        val o = runCatching { JSONObject(data) }.getOrNull() ?: return
        val id = o.optString("id")
        when (o.optString("t")) {
            "line" -> {
                val text = o.optString("v")
                noteChallenge(text)
                channels[id]?.trySend(text)
            }
            "end" -> end(id)
        }
    }

    private fun end(id: String) {
        channels.remove(id)?.close()
        if (inFlight.remove(id)) main.post { recycleIfIdle() }
    }

    /** A fetch inside the page met the checkpoint again: its clearance expired. */
    private fun noteChallenge(line: String) {
        if (line.startsWith("HTTP 429") && line.contains("text/html", ignoreCase = true)) reloadWhenIdle = true
    }

    /** Drops the page for a fresh one once nothing is streaming (main thread). */
    private fun recycleIfIdle() {
        if ((reloadWhenIdle || requests >= MAX_REQUESTS) && inFlight.isEmpty() && web != null) {
            reloadWhenIdle = false
            reset(null)
        }
    }

    /** Ends every reply streaming in the page with [reason] (main thread). */
    private fun failInFlight(reason: String) {
        inFlight.toList().forEach { id ->
            inFlight.remove(id)
            channels.remove(id)?.let { ch ->
                ch.trySend("HTTP 0 browser-error")
                ch.trySend(reason)
                ch.close()
            }
        }
    }

    /**
     * Destroys the page (main thread). With a [reason], every pending request
     * -- streaming, or still waiting for the page -- ends with it.
     */
    private fun reset(reason: String?) {
        val waiting = ready
        ready = null
        loaded = false
        if (reason != null) {
            waiting?.completeExceptionally(Reset(reason))
            failInFlight(reason)
        } else {
            waiting?.completeExceptionally(Reset("the in-app browser was recycled; try again"))
        }
        web?.let { w -> runCatching { w.stopLoading(); w.destroy() } }
        web = null
        requests = 0
    }

    private fun onSite(url: String?): Boolean {
        val host = url?.let { runCatching { Uri.parse(it).host }.getOrNull() } ?: return false
        return host.equals(HOST, ignoreCase = true)
    }

    /** Fallback channel where web message listeners are unsupported; scripts only run on the site. */
    private inner class Js {
        @JavascriptInterface fun line(id: String, text: String) {
            noteChallenge(text)
            channels[id]?.trySend(text)
        }
        @JavascriptInterface fun end(id: String) = this@MercuryWebBridge.end(id)
    }

    private fun script(id: String, body: String, messages: Boolean): String {
        val send = if (messages) {
            "(t) => $JS_NAME.postMessage(JSON.stringify({ id, t: 'line', v: String(t) }))"
        } else {
            "(t) => $JS_NAME.line(id, String(t))"
        }
        val done = if (messages) "$JS_NAME.postMessage(JSON.stringify({ id, t: 'end' }))" else "$JS_NAME.end(id)"
        return """
            (async () => {
              const id = ${JSONObject.quote(id)};
              const send = $send;
              try {
                if (location.hostname !== ${JSONObject.quote(HOST)}) throw new Error('not on the site');
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
              } finally { $done; }
            })();
        """.trimIndent()
    }

    private companion object {
        const val HOST = "chat.inceptionlabs.ai"
        const val ORIGIN = "https://chat.inceptionlabs.ai"
        const val SITE_URL = "https://chat.inceptionlabs.ai/"
        const val JS_NAME = "OroMercury"
        const val MAX_REQUESTS = 40
        const val SETTLE_MS = 600L
        const val PAGE_TIMEOUT_MS = 45_000L
    }
}
