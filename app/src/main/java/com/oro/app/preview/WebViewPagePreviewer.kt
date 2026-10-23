package com.oro.app.preview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import android.view.View
import android.webkit.WebView
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebViewClient
import com.oro.app.data.preview.Device
import com.oro.app.data.preview.PageMetrics
import com.oro.app.data.preview.PageShot
import com.oro.app.data.preview.PagePreviewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume

/**
 * Renders project pages in an off-screen WebView: screenshots at phone /
 * tablet / desktop widths plus measured layout facts, and a Lighthouse-style
 * audit run as JavaScript inside the page.
 *
 * The WebView is laid out at `width x density` pixels so the page sees the
 * device's CSS width, and drawn straight into a down-scaled bitmap so a
 * desktop render never allocates a full-resolution frame on a small phone.
 * One render at a time; the WebView is destroyed after each.
 */
class WebViewPagePreviewer(private val app: Context) : PagePreviewer {

    private val lock = Mutex()

    /** The current render's renderer process died; its WebView must not be used again. */
    @Volatile private var rendererGone = false

    override suspend fun capture(projectRoot: File, page: String, devices: List<Device>): List<PageShot> =
        lock.withLock { devices.map { render(projectRoot, page, it) } }

    override suspend fun audit(projectRoot: File, page: String): String = lock.withLock {
        val phone = withPage(projectRoot, page, Device.PHONE) { wv -> evaluate(wv, AUDIT_JS) }
        // Some defects exist only at desktop width: a header button restyled by
        // the nav's link colour, a menu that is hidden on phones. Checked too,
        // and reported only where the phone pass did not already say it.
        val desktop = runCatching { withPage(projectRoot, page, Device.DESKTOP) { wv -> evaluate(wv, AUDIT_JS) } }.getOrNull()
        formatAudit(page, phone, desktop)
    }

    private suspend fun render(root: File, page: String, device: Device): PageShot =
        withPage(root, page, device) { wv ->
            val metrics = runCatching { formatMetrics(evaluate(wv, METRICS_JS)) }.getOrDefault("(could not measure)")
            val jpeg = runCatching { snapshot(wv, device) }.getOrNull()
            PageShot(device.label, device.width, device.height, jpeg, metrics)
        }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun <T> withPage(root: File, page: String, device: Device, block: suspend (WebView) -> T): T =
        withContext(Dispatchers.Main) {
            val density = app.resources.displayMetrics.density
            val w = (device.width * density).toInt()
            val h = (device.height * density).toInt()
            val wv = WebView(app)
            rendererGone = false
            try {
                wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                wv.setBackgroundColor(Color.WHITE)
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                    useWideViewPort = true
                    loadWithOverviewMode = false
                }
                wv.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                wv.layout(0, 0, w, h)
                withTimeoutOrNull(12_000) {
                    suspendCancellableCoroutine { cont ->
                        wv.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (cont.isActive) cont.resume(Unit)
                            }

                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                // Handled: a user's page that crashes the renderer must not crash Oro.
                                rendererGone = true
                                if (cont.isActive) cont.resume(Unit)
                                return true
                            }
                        }
                        wv.loadUrl(File(root, page).toURI().toString())
                    }
                }
                delay(700) // fonts, images and first layout settle
                check(!rendererGone) { "The preview's renderer stopped while loading $page" }
                block(wv)
            } finally {
                wv.stopLoading()
                wv.destroy()
            }
        }

    /** Runs [js]; a renderer that died (or hangs) fails fast instead of suspending forever. */
    private suspend fun evaluate(wv: WebView, js: String): String {
        check(!rendererGone) { "The preview's renderer stopped" }
        return withTimeoutOrNull(EVALUATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                wv.evaluateJavascript(js) { result -> if (cont.isActive) cont.resume(result ?: "null") }
            }
        } ?: error(if (rendererGone) "The preview's renderer stopped" else "The page did not answer within ${EVALUATE_TIMEOUT_MS / 1000}s")
    }

    /** Draw into a bitmap at most 720 px wide; JPEG, base64. */
    private fun snapshot(wv: WebView, device: Device): String {
        check(!rendererGone) { "The preview's renderer stopped" }
        val scale = minOf(1f, 720f / wv.width)
        val bw = (wv.width * scale).toInt().coerceAtLeast(1)
        val bh = (wv.height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
        try {
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.scale(scale, scale)
            wv.draw(canvas)
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, if (device == Device.DESKTOP) 62 else 70, out)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            bmp.recycle()
        }
    }

    /** evaluateJavascript returns a JSON-encoded string; unwrap it. */
    private fun unwrap(raw: String): String = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault(raw)

    private fun formatMetrics(raw: String): String =
        PageMetrics.parse(unwrap(raw))?.describe() ?: "(could not measure)"

    private fun formatAudit(page: String, raw: String, desktopRaw: String? = null): String {
        val phone = JSONArray(unwrap(raw)).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        val seen = phone.map { it.optString("msg") + "\u0000" + it.optString("sel") }.toSet()
        val desktopOnly = desktopRaw?.let { r -> runCatching { JSONArray(unwrap(r)) }.getOrNull() }
            ?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
            .filter { it.optString("cat") == "accessibility" && (it.optString("msg") + "\u0000" + it.optString("sel")) !in seen }
            .map { it.put("msg", it.optString("msg") + " (desktop)") }
        val all = (phone + desktopOnly).take(60)
        if (all.isEmpty()) return "Audit of $page: no issues found."
        val byCat = all.groupBy { it.optString("cat") }
        val score = (100 - all.size * 4).coerceIn(0, 100)
        return buildString {
            append("Audit of ").append(page).append(" — ").append(all.size).append(" issues (rough score ").append(score).append("/100)\n")
            for ((cat, list) in byCat) {
                append("\n").append(cat.uppercase()).append('\n')
                list.forEach { i ->
                    append("- ").append(i.optString("msg"))
                    i.optString("sel").takeIf { it.isNotBlank() }?.let { append("  [").append(it).append(']') }
                    append('\n')
                }
            }
        }.trimEnd()
    }

    companion object {
        private const val EVALUATE_TIMEOUT_MS = 10_000L

        private const val SEL = """function shown(e){if(e.checkVisibility)return e.checkVisibility({opacityProperty:true,visibilityProperty:true});var n=e;while(n){var s=getComputedStyle(n);if(s.opacity==='0'||s.visibility==='hidden'||s.display==='none')return false;n=n.parentElement;}return true;}var cx=document.createElement('canvas').getContext('2d');function rgba(c){if(!c||!cx)return null;cx.clearRect(0,0,1,1);cx.fillStyle='rgba(0,0,0,0)';cx.fillStyle=c;cx.fillRect(0,0,1,1);var d=cx.getImageData(0,0,1,1).data;return [d[0],d[1],d[2],d[3]/255];}function L(c){var a=c.map(function(v){v=v/255;return v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4);});return 0.2126*a[0]+0.7152*a[1]+0.0722*a[2];}function bgc(e){var layers=[];while(e){var cs=getComputedStyle(e);if(cs.backgroundImage&&cs.backgroundImage!=='none')return null;var c=rgba(cs.backgroundColor);if(c&&c[3]>0){layers.push(c);if(c[3]>=0.99)break;}e=e.parentElement;}var b=[255,255,255];for(var i=layers.length-1;i>=0;i--){var l=layers[i];b=[l[0]*l[3]+b[0]*(1-l[3]),l[1]*l[3]+b[1]*(1-l[3]),l[2]*l[3]+b[2]*(1-l[3])];}return b;}function inl(e){if(getComputedStyle(e).display!=='inline'||!e.parentElement)return false;var n=0;e.parentElement.childNodes.forEach(function(c){if(c.nodeType===3)n+=c.textContent.trim().length;});return n>2;}function tiny(r){return r.width>0&&(Math.min(r.width,r.height)<32||(r.width<40&&r.height<40));}function sel(e){if(!e||!e.tagName)return '';var s=e.tagName.toLowerCase();if(e.id)s+='#'+e.id;else if(e.className&&typeof e.className==='string'){var c=e.className.trim().split(/\s+/).slice(0,2).join('.');if(c)s+='.'+c;}return s;}function lowc(){var res=[],checked=0;document.querySelectorAll('p,span,a,li,h1,h2,h3,h4,button,label,td').forEach(function(e){if(checked>200||!e.textContent.trim())return;var s=getComputedStyle(e);if(s.backgroundClip==='text'||s.getPropertyValue('-webkit-background-clip')==='text')return;var tf=rgba(s.getPropertyValue('-webkit-text-fill-color')||s.color);if(!tf||tf[3]<0.05||!shown(e))return;checked++;var g=bgc(e);if(!g)return;var t=[tf[0]*tf[3]+g[0]*(1-tf[3]),tf[1]*tf[3]+g[1]*(1-tf[3]),tf[2]*tf[3]+g[2]*(1-tf[3])],f=L(t),b=L(g);var r=(Math.max(f,b)+0.05)/(Math.min(f,b)+0.05),fs=parseFloat(s.fontSize),large=fs>=24||(fs>=18.66&&parseInt(s.fontWeight,10)>=700);if(r<(large?3:4.5))res.push([e,r]);});return res;}"""

        private const val METRICS_JS = """(function(){$SEL
var vw=document.documentElement.clientWidth,touch=vw<1024,de=document.documentElement,wide=[],out=new Set(),st=0,sta=[],sx=0,sxa=[],bi=0;
function clipped(e){for(var p=e.parentElement;p;p=p.parentElement){if(getComputedStyle(p).overflowX!=='visible')return true;}return false;}
function lab(e){var t=(e.textContent||e.getAttribute('aria-label')||'').trim().replace(/\s+/g,' ').slice(0,18);return sel(e)+(t?' "'+t+'"':'');}
document.querySelectorAll('body *').forEach(function(e){var r=e.getBoundingClientRect();
if(r.right>vw+2&&r.width>0&&getComputedStyle(e).position!=='fixed'&&!clipped(e))out.add(e);
if(touch&&(e.tagName==='A'||e.tagName==='BUTTON')&&tiny(r)&&!inl(e)&&shown(e)){st++;if(sta.length<3)sta.push(lab(e)+' '+Math.round(r.width)+'x'+Math.round(r.height));}
if(e.childNodes.length&&e.childNodes[0].nodeType===3&&e.textContent.trim().length>2){var fs=parseFloat(getComputedStyle(e).fontSize);if(fs<12&&shown(e)){sx++;if(sxa.length<3)sxa.push(lab(e)+' '+fs+'px');}}});
out.forEach(function(e){if(wide.length<6&&!out.has(e.parentElement)){var r=e.getBoundingClientRect();wide.push(sel(e)+' ('+Math.round(r.width)+'px wide, right edge at '+Math.round(r.right)+'px)');}});
var lc=lowc(),lca=lc.slice(0,3).map(function(x){return lab(x[0])+' '+x[1].toFixed(1)+':1';});
document.querySelectorAll('img').forEach(function(i){if(i.complete&&i.naturalWidth===0)bi++;});
var hs=0;document.querySelectorAll('[hidden]').forEach(function(e){if(getComputedStyle(e).display!=='none')hs++;});
return JSON.stringify({vw:vw,sw:de.scrollWidth,sh:de.scrollHeight,wide:wide,smallTargets:st,smallTargetsAt:sta,smallText:sx,smallTextAt:sxa,lowContrast:lc.length,lowContrastAt:lca,brokenImages:bi,hiddenShown:hs,title:document.title});})()"""

        private const val AUDIT_JS = """(function(){$SEL
var out=[];function add(c,m,e){if(out.length<60)out.push({cat:c,msg:m,sel:e?sel(e):''});}
if(!document.title)add('seo','Missing <title>');
if(!document.querySelector('meta[name=description]'))add('seo','Missing <meta name="description">');
if(!document.querySelector('meta[name=viewport]'))add('seo','Missing viewport meta tag (page will not scale on phones)');
if(!document.documentElement.lang)add('accessibility','Missing lang attribute on <html>');
var h1=document.querySelectorAll('h1').length;if(h1===0)add('seo','No <h1> heading');if(h1>1)add('seo',h1+' <h1> headings; use one');
var last=0;document.querySelectorAll('h1,h2,h3,h4,h5,h6').forEach(function(h){var l=+h.tagName[1];if(last&&l>last+1)add('accessibility','Heading level skips from h'+last+' to h'+l,h);last=l;});
document.querySelectorAll('img').forEach(function(i){if(!i.hasAttribute('alt'))add('accessibility','Image without alt text',i);if(i.naturalWidth>2*i.clientWidth&&i.naturalWidth>1200)add('performance','Image much larger than shown ('+i.naturalWidth+'px wide)',i);if(!i.loading)add('performance','Image without loading="lazy"',i);});
document.querySelectorAll('input,select,textarea').forEach(function(f){if(f.type==='hidden')return;var id=f.id;if(!(id&&document.querySelector('label[for="'+id+'"]'))&&!f.getAttribute('aria-label')&&!f.closest('label'))add('accessibility','Form field without a label',f);});
document.querySelectorAll('a,button').forEach(function(b){if(!b.textContent.trim()&&!b.getAttribute('aria-label')&&!b.querySelector('img[alt]'))add('accessibility','Link or button with no text',b);var r=b.getBoundingClientRect();if(innerWidth<1024&&tiny(r)&&!inl(b)&&shown(b))add('accessibility','Tap target too small for a finger ('+Math.round(r.width)+'x'+Math.round(r.height)+'px)',b);});
lowc().forEach(function(x){add('accessibility','Low contrast text ('+x[1].toFixed(1)+':1)',x[0]);});
document.querySelectorAll('head script[src]:not([defer]):not([async])').forEach(function(s){add('performance','Render-blocking script in <head>: '+s.getAttribute('src'));});
var n=document.getElementsByTagName('*').length;if(n>1500)add('performance','Large DOM ('+n+' elements)');
if(document.documentElement.scrollWidth>document.documentElement.clientWidth+1)add('mobile','Page scrolls sideways on a phone');
return JSON.stringify(out);})()"""
    }
}
