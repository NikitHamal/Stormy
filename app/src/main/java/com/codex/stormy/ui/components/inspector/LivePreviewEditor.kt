package com.codex.stormy.ui.components.inspector

import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The WebView half of inspector editing: evaluate JS against the page to show a
 * change instantly, and to check what a selector actually matches.
 *
 * DirectEditService and PreviewEditService each carried their own copy of these
 * three functions, identical down to the escaping, so every fix had to be made
 * twice and the two drifted anyway (one logged errors, the other swallowed
 * them). They are also where the inspector's worst bug lived: each one did
 * `querySelectorAll(selector).forEach(...)`, applying the edit to *every*
 * match, so editing one card restyled all of them.
 *
 * Here they target a single element by construction -- `querySelector`, not
 * `querySelectorAll` -- and [countMatches] lets callers refuse ambiguous
 * selectors before writing anything to disk.
 */
object LivePreviewEditor {

    /**
     * Escape a value for embedding in a single-quoted JS string literal.
     *
     * The backslash has to be escaped first, otherwise escaping the quote
     * introduces a backslash that the quote-escaping pass then doubles. The
     * previous implementations only escaped quotes, so any selector containing
     * a backslash -- which, now that Tailwind classes are escaped rather than
     * discarded, is most of them -- produced invalid JS and silently did
     * nothing.
     */
    fun escapeJsString(value: String): String = value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "")

    /**
     * Number of nodes [selector] matches right now, or -1 if that cannot be
     * determined (invalid selector, JS unavailable).
     */
    suspend fun countMatches(webView: WebView?, selector: String): Int {
        if (webView == null) return -1
        val script = """
            (function() {
                try { return document.querySelectorAll('${escapeJsString(selector)}').length; }
                catch (e) { return -1; }
            })();
        """.trimIndent()
        return evaluate(webView, script)?.trim()?.toIntOrNull() ?: -1
    }

    /** Set one inline style property on the single element [selector] matches. */
    suspend fun applyStyle(webView: WebView, selector: String, property: String, value: String) {
        val script = """
            (function() {
                try {
                    var el = document.querySelector('${escapeJsString(selector)}');
                    if (!el) return 0;
                    el.style.setProperty('${escapeJsString(property)}', '${escapeJsString(value)}');
                    return 1;
                } catch (e) { return 0; }
            })();
        """.trimIndent()
        evaluate(webView, script)
    }

    /**
     * Replace the element's text.
     *
     * Assigns textContent rather than innerHTML: the inspector's text field
     * holds plain text, and routing it through innerHTML means any `<` the user
     * types silently restructures their document.
     */
    suspend fun applyText(webView: WebView, selector: String, text: String) {
        val script = """
            (function() {
                try {
                    var el = document.querySelector('${escapeJsString(selector)}');
                    if (!el) return 0;
                    el.textContent = '${escapeJsString(text)}';
                    return 1;
                } catch (e) { return 0; }
            })();
        """.trimIndent()
        evaluate(webView, script)
    }

    /** Point an `<img>` (or any element with a src) at a new source. */
    suspend fun applyImageSrc(webView: WebView, selector: String, src: String) {
        val script = """
            (function() {
                try {
                    var el = document.querySelector('${escapeJsString(selector)}');
                    if (!el) return 0;
                    el.setAttribute('src', '${escapeJsString(src)}');
                    return 1;
                } catch (e) { return 0; }
            })();
        """.trimIndent()
        evaluate(webView, script)
    }

    /**
     * evaluateJavascript must be called on the main thread and answers through
     * a callback; this bridges it to a suspend function so callers are not
     * forced into nested lambdas.
     */
    private suspend fun evaluate(webView: WebView, script: String): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                webView.evaluateJavascript(script) { result ->
                    if (cont.isActive) cont.resume(result) {}
                }
            }
        }
}
