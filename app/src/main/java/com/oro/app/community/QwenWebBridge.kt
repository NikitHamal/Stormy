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
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.oro.app.data.ai.community.QwenBrowserBridge
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
 * chat.qwen.ai chats made from inside the real site, in a WebView that is
 * never shown. The page brings a genuine browser's TLS and cookies, and the
 * site's own risk SDK (Baxia) signs each request with `bx-ua` /
 * `bx-umidtoken`, which is what Alibaba's WAF wants to see; Oro only issues
 * same-origin `fetch` calls -- create a chat, post the completion -- and reads
 * the streamed lines back (protocol in [QwenBrowserBridge]).
 *
 * ### Never hangs
 * When the WAF distrusts the network it answers with a slider captcha that the
 * SDK shows over the page and holds the request until it is solved -- in a
 * hidden page, forever. The request script watches for that dialog, closes it
 * and reports `CHALLENGE`, so the client can hand the request to another free
 * Qwen within seconds. Every await in the page has a deadline, and a reset, a
 * reload while a reply streams, or a crashed renderer ends every pending
 * stream with an error. A renderer that dies is handled here
 * (`onRenderProcessGone`) instead of taking the whole app down with it.
 *
 * ### Safety
 * Lines come back only from https://chat.qwen.ai: through an origin-restricted
 * web message listener where WebView supports one, else through a JavaScript
 * interface guarded by a host check before every script. Off-site main-frame
 * navigations are refused, so the request script, which carries the whole
 * conversation, never runs on another site.
 *
 * ### Identity
 * A guest is the page's device id (`localStorage.qwen_chat_device_id`, sent
 * as `x-umid`) plus the site's cookies. After a captcha, a quota answer or
 * [MAX_REQUESTS] requests the page is dropped once nothing is streaming, and
 * the next one starts as a new guest.
 *
 * Page state is touched on the main thread only.
 */
class QwenWebBridge(context: Context) : QwenBrowserBridge {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val channels = ConcurrentHashMap<String, SendChannel<String>>()
    /** Requests whose script is running in the page. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private var web: WebView? = null
    private var ready: CompletableDeferred<Unit>? = null
    /** The site has finished loading in [web]. */
    private var loaded = false
    /** Lines come back as origin-checked web messages rather than through a JS interface. */
    private var useMessages = false
    /** Start over as a new guest once nothing is streaming: a captcha, a quota answer, or the request budget. */
    @Volatile private var renewWhenIdle = false
    private var requests = 0

    /** A reset ended the request before its script ran. */
    private class Reset(message: String) : Exception(message)

    override fun chat(newChatBody: String, completionBody: String): Flow<String> = callbackFlow {
        val id = UUID.randomUUID().toString()
        channels[id] = channel
        val failure: String? = try {
            val deferred = withContext(Dispatchers.Main) { page() }
            withTimeout(PAGE_TIMEOUT_MS) { deferred.await() }
            withContext(Dispatchers.Main) {
                val view = web
                if (view == null || !onSite(view.url)) throw Reset("the in-app browser left chat.qwen.ai")
                requests++
                inFlight += id
                view.evaluateJavascript(script(id, newChatBody, completionBody, useMessages), null)
            }
            null
        } catch (e: TimeoutCancellationException) {
            withContext(Dispatchers.Main) { reset("chat.qwen.ai did not load in time") }
            "chat.qwen.ai did not load in time"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Reset) {
            e.message ?: "the in-app browser was reset"
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { reset(e.message ?: "the in-app browser failed") }
            e.message ?: "the in-app browser could not load chat.qwen.ai"
        }
        if (failure != null) {
            trySend("FAIL $failure")
            channel.close()
        }
        awaitClose {
            channels.remove(id)
            if (inFlight.remove(id)) main.post { recycleIfIdle() }
        }
    }

    /** The loaded site, creating the page on first use (main thread). */
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
                failInFlight("chat.qwen.ai reloaded during the reply; try again")
                ready = CompletableDeferred()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val page = view ?: return
                if (page !== web) return
                // The request script waits for the risk SDK itself; a short
                // settle lets the app's own startup calls go first.
                main.postDelayed({
                    if (page === web && onSite(page.url)) {
                        loaded = true
                        ready?.complete(Unit)
                    }
                }, SETTLE_MS)
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
            "line" -> line(id, o.optString("v"))
            "end" -> end(id)
        }
    }

    private fun line(id: String, text: String) {
        noteIdentity(text)
        channels[id]?.trySend(text)
    }

    private fun end(id: String) {
        channels.remove(id)?.close()
        if (inFlight.remove(id)) main.post { recycleIfIdle() }
    }

    /** A captcha or a quota answer means this guest is spent: the next page starts as a new one. */
    private fun noteIdentity(line: String) {
        if (line.startsWith("CHALLENGE") || line.contains("RateLimited", ignoreCase = true) ||
            line.contains("RGV587_ERROR") || line.contains("x5secdata")
        ) {
            renewWhenIdle = true
        }
    }

    /** Drops the page for a new guest once nothing is streaming (main thread). */
    private fun recycleIfIdle() {
        if ((renewWhenIdle || requests >= MAX_REQUESTS) && inFlight.isEmpty() && web != null) {
            renewWhenIdle = false
            reset(null)
            forgetGuest()
        }
    }

    /** Ends every reply streaming in the page with [reason] (main thread). */
    private fun failInFlight(reason: String) {
        inFlight.toList().forEach { id ->
            inFlight.remove(id)
            channels.remove(id)?.let { ch ->
                ch.trySend("FAIL $reason")
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

    /** The site keys a guest on its device id and cookies; forget both (main thread). */
    private fun forgetGuest() {
        runCatching { WebStorage.getInstance().deleteOrigin(ORIGIN) }
        val cookies = CookieManager.getInstance()
        for (url in listOf(ORIGIN, "https://qwen.ai")) {
            GUEST_COOKIES.forEach { name ->
                cookies.setCookie(url, "$name=; Max-Age=0; Path=/")
                cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.qwen.ai")
            }
        }
        cookies.flush()
    }

    private fun onSite(url: String?): Boolean {
        val host = url?.let { runCatching { Uri.parse(it).host }.getOrNull() } ?: return false
        return host.equals(HOST, ignoreCase = true)
    }

    /** Fallback channel where web message listeners are unsupported; scripts only run on the site. */
    private inner class Js {
        @JavascriptInterface fun line(id: String, text: String) = this@QwenWebBridge.line(id, text)
        @JavascriptInterface fun end(id: String) = this@QwenWebBridge.end(id)
    }

    private fun script(id: String, newChatBody: String, completionBody: String, messages: Boolean): String {
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
              const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
              // The SDK's slider: a dialog over the page and an iframe on the WAF's punish page.
              // Counted only once it is on screen; nothing of it exists until the WAF challenges.
              const captcha = () => {
                if (document.querySelector('iframe[src*="_____tmd_____"], iframe[src*="/punish"]')) return true;
                const d = document.querySelector('.baxia-dialog');
                return !!d && getComputedStyle(d).display !== 'none' && getComputedStyle(d).visibility !== 'hidden';
              };
              const closeCaptcha = () => { try { const c = document.querySelector('.baxia-dialog-close'); if (c) c.click(); } catch (e) {} };
              // Resolves with the fetch, or with 'captcha' / 'timeout' -- whichever comes first.
              const guarded = (p, ms) => new Promise((resolve) => {
                let over = false;
                const finish = (v) => { if (!over) { over = true; clearInterval(watch); clearTimeout(deadline); resolve(v); } };
                const watch = setInterval(() => { if (captcha()) finish('captcha'); }, 400);
                const deadline = setTimeout(() => finish('timeout'), ms);
                p.then(finish, (e) => finish({ error: e }));
              });
              try {
                if (location.hostname !== ${JSONObject.quote(HOST)}) throw new Error('not on the site');
                // Let the risk SDK finish initialising, so it signs our requests.
                for (let i = 0; i < 40 && !window.baxiaInitialized; i++) await sleep(150);
                let device = null;
                try { device = localStorage.getItem('qwen_chat_device_id'); } catch (e) {}
                if (!device) {
                  device = crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2);
                  try { localStorage.setItem('qwen_chat_device_id', device); } catch (e) {}
                }
                const ver = (Array.from(document.scripts).map((s) => s.src).join(' ').match(/qwen-chat-fe\/(\d+\.\d+\.\d+)\//) || [])[1] || ${JSONObject.quote(FALLBACK_VERSION)};
                const headers = () => ({
                  'content-type': 'application/json', 'accept': '*/*',
                  'source': /Mobile|Android/i.test(navigator.userAgent) ? 'h5' : 'web', 'version': ver,
                  'x-umid': device, 'x-request-id': crypto.randomUUID ? crypto.randomUUID() : String(Date.now()),
                  'Timezone': new Date().toString().replace(/\s*\(.+\)$/, '')
                });
                const created = await guarded(fetch('/api/v2/chats/new', {
                  method: 'POST', credentials: 'include', headers: headers(), body: ${JSONObject.quote(newChatBody)}
                }), 25000);
                if (created === 'captcha') { closeCaptcha(); send('CHALLENGE the site asked for a human check'); return; }
                if (created === 'timeout') { send('FAIL starting a chat timed out'); return; }
                if (created.error) { send('FAIL ' + String(created.error && created.error.message ? created.error.message : created.error)); return; }
                const text = await created.text();
                let chatId = null;
                try { const j = JSON.parse(text); chatId = j && j.data && j.data.id; } catch (e) {}
                if (!chatId) {
                  if (/RGV587_ERROR|_____tmd_____|x5secdata/.test(text)) send('CHALLENGE the site asked for a human check');
                  else send('FAIL could not start a chat: ' + text.slice(0, 200));
                  return;
                }
                send('CHAT ' + chatId);
                const body = JSON.parse(${JSONObject.quote(completionBody)});
                body.chat_id = chatId;
                const r = await guarded(fetch('/api/v2/chat/completions?chat_id=' + encodeURIComponent(chatId), {
                  method: 'POST', credentials: 'include',
                  headers: Object.assign(headers(), { 'accept': 'text/event-stream, */*' }),
                  body: JSON.stringify(body)
                }), 60000);
                if (r === 'captcha') { closeCaptcha(); send('CHALLENGE the site asked for a human check'); return; }
                if (r === 'timeout') { send('FAIL the reply did not start in time'); return; }
                if (r.error) { send('FAIL ' + String(r.error && r.error.message ? r.error.message : r.error)); return; }
                send('HTTP ' + r.status + ' ' + (r.headers.get('content-type') || ''));
                const reader = r.body.getReader(); const dec = new TextDecoder(); let buf = '';
                for (;;) {
                  const part = await guarded(reader.read(), 120000);
                  if (part === 'captcha') { closeCaptcha(); send('CHALLENGE the site asked for a human check'); break; }
                  if (part === 'timeout') { send('FAIL the reply stalled'); try { reader.cancel(); } catch (e) {} break; }
                  if (part.error) { send('FAIL ' + String(part.error)); break; }
                  if (part.done) break;
                  buf += dec.decode(part.value, { stream: true });
                  let i;
                  while ((i = buf.indexOf('\n')) >= 0) { send(buf.slice(0, i)); buf = buf.slice(i + 1); }
                }
                buf += dec.decode();
                if (buf) send(buf);
              } catch (e) {
                send('FAIL ' + String(e && e.message ? e.message : e));
              } finally { $done; }
            })();
        """.trimIndent()
    }

    private companion object {
        const val HOST = "chat.qwen.ai"
        const val ORIGIN = "https://chat.qwen.ai"
        const val SITE_URL = "https://chat.qwen.ai/"
        const val JS_NAME = "OroQwen"
        /** The web client version when the page's script URLs don't say (2026-10-23). */
        const val FALLBACK_VERSION = "0.3.22"
        const val MAX_REQUESTS = 12
        const val SETTLE_MS = 500L
        const val PAGE_TIMEOUT_MS = 45_000L

        /** The cookies a guest is known by; a new guest starts without them. */
        val GUEST_COOKIES = listOf(
            "cna", "isg", "tfstk", "ssxmod_itna", "ssxmod_itna2", "acw_tc", "x5sectag", "x5secdata",
            "atpsida", "cbc", "sca", "xlly_s"
        )
    }
}
