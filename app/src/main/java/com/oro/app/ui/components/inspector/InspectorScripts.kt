package com.oro.app.ui.components.inspector

/**
 * Page-side helpers the preview's inspector script builds on. Plain
 * JavaScript with no Kotlin templates in it, so tests can load the same text
 * into a browser.
 */
object InspectorScripts {

    /**
     * `__oroTextTarget(el)` reports the element's own text and where it sits
     * among the page's text -- the [SourceTextEdit.Target] fields -- so an
     * edit can be written back to that exact place in the source. Its
     * whitespace rules and its tag path match [SourceTextEdit]'s.
     *
     * `__oroFirstChild(el)` is the first child worth selecting when walking
     * down the tree: not a script or style, and not Oro's own overlay.
     */
    val TEXT_TARGET_JS: String = """
        var __oroSkipText = { SCRIPT: 1, STYLE: 1, TEXTAREA: 1, TITLE: 1, NOSCRIPT: 1, TEMPLATE: 1,
            IFRAME: 1, NOEMBED: 1, NOFRAMES: 1, XMP: 1 };

        function __oroNorm(s) {
            return String(s || '').replace(/[ \t\n\r\f]+/g, ' ').trim();
        }

        function __oroPath(el) {
            var parts = [];
            for (var n = el; n !== document.body; n = n.parentElement) {
                if (!n) return null;
                var t = n.tagName.toLowerCase();
                if (t !== 'tbody') parts.unshift(t);
            }
            return parts.join('>');
        }

        function __oroIsUi(el) {
            return !!(el.closest && el.closest('#__oro_inspector_overlay, .__oro_selected_marker'));
        }

        function __oroTextTarget(el) {
            var node = null, count = 0, n;
            for (n = el.firstChild; n; n = n.nextSibling) {
                if (n.nodeType === 3 && __oroNorm(n.nodeValue)) { count++; if (!node) node = n; }
            }
            var out = { ownText: '', ownTextNodes: count, textPath: '', textOccurrence: -1, textMatches: 0, textTotal: 0 };
            if (count !== 1 || __oroSkipText[el.tagName.toUpperCase()]) return out;
            var text = __oroNorm(node.nodeValue), path = __oroPath(el);
            if (path === null) return out;
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
            var same = 0, index = -1, total = 0;
            for (n = walker.nextNode(); n; n = walker.nextNode()) {
                var p = n.parentNode;
                if (!p || p.nodeType !== 1 || __oroSkipText[p.tagName.toUpperCase()]) continue;
                if (__oroNorm(n.nodeValue) !== text || __oroIsUi(p)) continue;
                total++;
                if (__oroPath(p) !== path) continue;
                if (n === node) index = same;
                same++;
            }
            out.ownText = text;
            out.textPath = path;
            out.textOccurrence = index;
            out.textMatches = same;
            out.textTotal = total;
            return out;
        }

        function __oroFirstChild(el) {
            for (var c = el.firstElementChild; c; c = c.nextElementSibling) {
                var t = c.tagName.toUpperCase();
                if (__oroSkipText[t] || t === 'BR' || __oroIsUi(c)) continue;
                return c;
            }
            return null;
        }
    """.trimIndent()
}
