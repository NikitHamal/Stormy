package com.oro.app.preview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import com.oro.app.data.preview.Device
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

    override suspend fun capture(projectRoot: File, page: String, devices: List<Device>): List<PageShot> =
        lock.withLock { devices.map { render(projectRoot, page, it) } }

    override suspend fun audit(projectRoot: File, page: String): String = lock.withLock {
        withPage(projectRoot, page, Device.PHONE) { wv ->
            val raw = evaluate(wv, AUDIT_JS)
            formatAudit(page, raw)
        }
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
                        }
                        wv.loadUrl(File(root, page).toURI().toString())
                    }
                }
                delay(700) // fonts, images and first layout settle
                block(wv)
            } finally {
                wv.stopLoading()
                wv.destroy()
            }
        }

    private suspend fun evaluate(wv: WebView, js: String): String = suspendCancellableCoroutine { cont ->
        wv.evaluateJavascript(js) { result -> if (cont.isActive) cont.resume(result ?: "null") }
    }

    /** Draw into a bitmap at most 720 px wide; JPEG, base64. */
    private fun snapshot(wv: WebView, device: Device): String {
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

    private fun formatMetrics(raw: String): String {
        val o = JSONObject(unwrap(raw))
        return buildString {
            append("CSS viewport ").append(o.optInt("vw")).append("px, page ").append(o.optInt("sw")).append("x").append(o.optInt("sh")).append("px.")
            if (o.optInt("sw") > o.optInt("vw") + 1) append("\nHORIZONTAL OVERFLOW: the page is wider than the screen.")
            val wide = o.optJSONArray("wide")
            if (wide != null && wide.length() > 0) {
                append("\nElements wider than the viewport: ")
                append((0 until wide.length()).joinToString(", ") { wide.getString(it) })
            }
            if (o.optInt("smallTargets") > 0) append("\n").append(o.optInt("smallTargets")).append(" tap targets smaller than 40px.")
            if (o.optInt("smallText") > 0) append("\n").append(o.optInt("smallText")).append(" text elements under 12px.")
            if (o.optInt("brokenImages") > 0) append("\n").append(o.optInt("brokenImages")).append(" images failed to load.")
            o.optString("title").takeIf { it.isNotBlank() }?.let { append("\nTitle: ").append(it) }
        }
    }

    private fun formatAudit(page: String, raw: String): String {
        val issues = JSONArray(unwrap(raw))
        if (issues.length() == 0) return "Audit of $page: no issues found."
        val byCat = (0 until issues.length()).map { issues.getJSONObject(it) }.groupBy { it.optString("cat") }
        val score = (100 - issues.length() * 4).coerceIn(0, 100)
        return buildString {
            append("Audit of ").append(page).append(" — ").append(issues.length()).append(" issues (rough score ").append(score).append("/100)\n")
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
        private const val SEL = """function sel(e){if(!e||!e.tagName)return '';var s=e.tagName.toLowerCase();if(e.id)s+='#'+e.id;else if(e.className&&typeof e.className==='string'){var c=e.className.trim().split(/\s+/).slice(0,2).join('.');if(c)s+='.'+c;}return s;}"""

        private const val METRICS_JS = """(function(){$SEL
var vw=document.documentElement.clientWidth,de=document.documentElement,wide=[],st=0,sx=0,bi=0;
document.querySelectorAll('body *').forEach(function(e){var r=e.getBoundingClientRect();
if(r.width>vw+2&&wide.length<8&&getComputedStyle(e).position!=='fixed')wide.push(sel(e)+' ('+Math.round(r.width)+'px)');
if((e.tagName==='A'||e.tagName==='BUTTON')&&r.width>0&&(r.width<40||r.height<40))st++;
if(e.childNodes.length&&e.childNodes[0].nodeType===3&&e.textContent.trim().length>2){var fs=parseFloat(getComputedStyle(e).fontSize);if(fs<12)sx++;}});
document.querySelectorAll('img').forEach(function(i){if(i.complete&&i.naturalWidth===0)bi++;});
return JSON.stringify({vw:vw,sw:de.scrollWidth,sh:de.scrollHeight,wide:wide,smallTargets:st,smallText:sx,brokenImages:bi,title:document.title});})()"""

        private const val AUDIT_JS = """(function(){$SEL
var out=[];function add(c,m,e){if(out.length<60)out.push({cat:c,msg:m,sel:e?sel(e):''});}
function lum(c){var m=c.match(/\d+(\.\d+)?/g);if(!m)return null;var a=m.slice(0,3).map(function(v){v=v/255;return v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4);});return 0.2126*a[0]+0.7152*a[1]+0.0722*a[2];}
function bg(e){while(e){var b=getComputedStyle(e).backgroundColor;if(b&&b!=='transparent'&&!/rgba\(.*,\s*0\)/.test(b))return b;e=e.parentElement;}return 'rgb(255,255,255)';}
if(!document.title)add('seo','Missing <title>');
if(!document.querySelector('meta[name=description]'))add('seo','Missing <meta name="description">');
if(!document.querySelector('meta[name=viewport]'))add('seo','Missing viewport meta tag (page will not scale on phones)');
if(!document.documentElement.lang)add('accessibility','Missing lang attribute on <html>');
var h1=document.querySelectorAll('h1').length;if(h1===0)add('seo','No <h1> heading');if(h1>1)add('seo',h1+' <h1> headings; use one');
var last=0;document.querySelectorAll('h1,h2,h3,h4,h5,h6').forEach(function(h){var l=+h.tagName[1];if(last&&l>last+1)add('accessibility','Heading level skips from h'+last+' to h'+l,h);last=l;});
document.querySelectorAll('img').forEach(function(i){if(!i.hasAttribute('alt'))add('accessibility','Image without alt text',i);if(i.naturalWidth>2*i.clientWidth&&i.naturalWidth>1200)add('performance','Image much larger than shown ('+i.naturalWidth+'px wide)',i);if(!i.loading)add('performance','Image without loading="lazy"',i);});
document.querySelectorAll('input,select,textarea').forEach(function(f){if(f.type==='hidden')return;var id=f.id;if(!(id&&document.querySelector('label[for="'+id+'"]'))&&!f.getAttribute('aria-label')&&!f.closest('label'))add('accessibility','Form field without a label',f);});
document.querySelectorAll('a,button').forEach(function(b){if(!b.textContent.trim()&&!b.getAttribute('aria-label')&&!b.querySelector('img[alt]'))add('accessibility','Link or button with no text',b);var r=b.getBoundingClientRect();if(r.width>0&&(r.width<40||r.height<40))add('accessibility','Tap target smaller than 40px',b);});
var checked=0;document.querySelectorAll('p,span,a,li,h1,h2,h3,h4,button,label,td').forEach(function(e){if(checked>200||!e.textContent.trim())return;checked++;var s=getComputedStyle(e),f=lum(s.color),b=lum(bg(e));if(f===null||b===null)return;var r=(Math.max(f,b)+0.05)/(Math.min(f,b)+0.05),large=parseFloat(s.fontSize)>=24;if(r<(large?3:4.5))add('accessibility','Low contrast text ('+r.toFixed(1)+':1)',e);});
document.querySelectorAll('head script[src]:not([defer]):not([async])').forEach(function(s){add('performance','Render-blocking script in <head>: '+s.getAttribute('src'));});
var n=document.getElementsByTagName('*').length;if(n>1500)add('performance','Large DOM ('+n+' elements)');
if(document.documentElement.scrollWidth>document.documentElement.clientWidth+1)add('mobile','Page scrolls sideways on a phone');
return JSON.stringify(out);})()"""
    }
}
