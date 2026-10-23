package com.oro.app.ui.screens.preview

/**
 * What the preview lays the page out as. Widths are CSS pixels; a null width
 * keeps the phone's own, so the page sees exactly what a visitor's phone does.
 */
enum class PreviewDevice(val label: String, private val portraitWidth: Int?, private val landscapeWidth: Int?) {
    PHONE("Phone", null, 844),
    TABLET("Tablet", 820, 1180),
    DESKTOP("Desktop", 1280, null);

    /** The layout width, turned to [landscape] where the device turns; null for the phone's own. */
    fun width(landscape: Boolean): Int? =
        if (landscape && landscapeWidth != null) landscapeWidth else portraitWidth

    val canRotate: Boolean get() = landscapeWidth != null

    /** The user agent to send, made from WebView's own [defaultUa]; null keeps that one. */
    fun userAgent(defaultUa: String): String? = when (this) {
        PHONE -> null
        TABLET -> defaultUa.replace(" Mobile", "")
        DESKTOP -> desktopUserAgent(defaultUa)
    }

    companion object {
        private val ANDROID_PLATFORM = Regex("""\(Linux; Android[^)]*\)""")
        private val WEBVIEW_VERSION = Regex("""\s?Version/\d+(\.\d+)*""")

        /**
         * Desktop Chrome on Linux, at the same Chrome version as this WebView,
         * so pages that sniff the user agent serve their desktop layout.
         */
        fun desktopUserAgent(mobileUa: String): String = mobileUa
            .replace(ANDROID_PLATFORM, "(X11; Linux x86_64)")
            .replace(" Mobile", "")
            .replace(WEBVIEW_VERSION, "")

        /**
         * Document-start script that lays the page out [width] CSS px wide:
         * it rewrites the page's viewport meta as the parser inserts it, or
         * adds one, before the first layout, so overview mode zooms the page
         * to fit the screen the way a real device of that width shows it.
         */
        fun viewportScript(width: Int): String = """
            (function () {
              var W = 'width=$width';
              function scan() {
                var list = document.querySelectorAll('meta[name="viewport" i]');
                for (var i = 0; i < list.length; i++) {
                  if (list[i].getAttribute('content') !== W) list[i].setAttribute('content', W);
                }
                return list.length;
              }
              var watch = new MutationObserver(scan);
              watch.observe(document, { childList: true, subtree: true });
              document.addEventListener('DOMContentLoaded', function () {
                watch.disconnect();
                if (!scan()) {
                  var m = document.createElement('meta');
                  m.name = 'viewport';
                  m.setAttribute('content', W);
                  (document.head || document.documentElement).appendChild(m);
                }
              });
            })();
        """.trimIndent()

        /** The same, for a page that has already loaded (WebViews without document-start scripts). */
        fun viewportNowScript(width: Int): String = """
            (function () {
              var W = 'width=$width', list = document.querySelectorAll('meta[name="viewport" i]');
              for (var i = 0; i < list.length; i++) list[i].setAttribute('content', W);
              if (!list.length && document.head) {
                var m = document.createElement('meta');
                m.name = 'viewport';
                m.setAttribute('content', W);
                document.head.appendChild(m);
              }
            })();
        """.trimIndent()
    }
}
