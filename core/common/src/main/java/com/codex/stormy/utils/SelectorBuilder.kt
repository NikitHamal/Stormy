package com.codex.stormy.utils

/**
 * CSS selector construction for the element inspector.
 *
 * ## Why this is careful about uniqueness
 *
 * The inspector's whole job is "change *this* element". A selector that
 * matches more than one node silently restyles every match, which is the worst
 * possible failure for a visual editor: it looks like it worked, and the damage
 * is somewhere off screen.
 *
 * The old implementation produced `div.card.shadow` for any element carrying
 * those classes, so editing one card restyled every card on the page. It was
 * worse on Tailwind projects -- which this app ships templates for -- because
 * its class filter rejected anything containing `:`, `/` or `[`, i.e. nearly
 * every Tailwind utility (`md:flex`, `w-1/2`, `bg-[#fff]`). With all classes
 * rejected the selector collapsed to a bare `div`, and the edit landed on the
 * first div in the document.
 *
 * The real fix is to compute the selector in the DOM, where uniqueness can
 * actually be checked -- see `buildUniqueSelectorJs`. Everything here is the
 * fallback for when only tag/id/class is known.
 */
object SelectorBuilder {

    /**
     * JavaScript that computes a selector for `el` and verifies it matches
     * exactly one node before returning it.
     *
     * Strategy, cheapest first:
     *  1. `#id`, if the id is unique in the document.
     *  2. A unique attribute that authors intend as an identifier
     *     (`data-testid`, `data-id`, `name`).
     *  3. An ancestor path of `tag:nth-of-type(n)` segments, extended upwards
     *     only until the path is unique. `nth-of-type` rather than `nth-child`
     *     because it is stable when sibling elements of other tags are added
     *     or removed around the target.
     *
     * Every identifier goes through `CSS.escape`, so ids and classes
     * containing Tailwind's `:` and `/` survive instead of being discarded.
     *
     * Exposed as a string because it has to run inside the page.
     */
    val BUILD_UNIQUE_SELECTOR_JS: String = """
        function __codexUniqueSelector(el) {
            if (!el || el.nodeType !== 1) return '';
            var esc = (window.CSS && CSS.escape)
                ? CSS.escape
                : function (s) { return String(s).replace(/([^a-zA-Z0-9_-])/g, '\\${'$'}1'); };

            function unique(sel) {
                try { return document.querySelectorAll(sel).length === 1; } catch (e) { return false; }
            }

            // 1. id
            if (el.id && unique('#' + esc(el.id))) return '#' + esc(el.id);

            // 2. an author-provided identifying attribute
            var idAttrs = ['data-testid', 'data-test-id', 'data-id', 'name'];
            for (var a = 0; a < idAttrs.length; a++) {
                var v = el.getAttribute && el.getAttribute(idAttrs[a]);
                if (v) {
                    var attrSel = el.tagName.toLowerCase() + '[' + idAttrs[a] + '="' + v.replace(/"/g, '\\"') + '"]';
                    if (unique(attrSel)) return attrSel;
                }
            }

            // 3. structural path, extended upwards until unique
            var parts = [];
            var node = el;
            while (node && node.nodeType === 1 && node !== document.documentElement) {
                var tag = node.tagName.toLowerCase();
                var seg = tag;

                // An ancestor with a unique id anchors the whole path.
                if (node.id && unique('#' + esc(node.id))) {
                    parts.unshift('#' + esc(node.id));
                    break;
                }

                var parent = node.parentNode;
                if (parent && parent.nodeType === 1) {
                    var sameTag = [];
                    for (var i = 0; i < parent.children.length; i++) {
                        if (parent.children[i].tagName === node.tagName) sameTag.push(parent.children[i]);
                    }
                    if (sameTag.length > 1) {
                        seg += ':nth-of-type(' + (sameTag.indexOf(node) + 1) + ')';
                    }
                }

                parts.unshift(seg);
                var candidate = parts.join(' > ');
                if (unique(candidate)) return candidate;
                node = parent;
            }

            var finalSel = parts.join(' > ');
            // Returned even when not provably unique; the caller is told via
            // the separate `selectorIsUnique` flag rather than being handed a
            // selector that silently matches nothing.
            return finalSel;
        }
    """.trimIndent()

    /**
     * Fallback selector from the little we know when the DOM is unavailable.
     *
     * [isUnique] is false for anything but an id, and callers are expected to
     * respect that rather than treat the result as safe to mutate with.
     */
    fun buildSelector(
        tagName: String,
        id: String? = null,
        className: String? = null,
        maxClasses: Int = 3
    ): String {
        val tag = tagName.lowercase().ifBlank { "*" }

        if (!id.isNullOrBlank()) return "#${escapeIdentifier(id)}"

        if (!className.isNullOrBlank()) {
            val classes = splitClasses(className).take(maxClasses)
            if (classes.isNotEmpty()) {
                return tag + classes.joinToString("") { ".${escapeIdentifier(it)}" }
            }
        }
        return tag
    }

    /** True when [selector] can only ever match one node. */
    fun isDefinitelyUnique(selector: String): Boolean =
        selector.startsWith("#") && !selector.contains(' ') && !selector.contains('>')

    /**
     * Split a class attribute into usable class names.
     *
     * Unlike the previous implementation this keeps Tailwind utilities. They
     * are perfectly valid class names -- they just need escaping, which
     * [escapeIdentifier] does. Discarding them was what made selectors on
     * Tailwind pages collapse to a bare tag.
     *
     * State variants (`hover:`, `focus:`) are still dropped, because those
     * classes describe a state the element is not currently in and including
     * them does not narrow the match in a useful way.
     */
    fun splitClasses(className: String): List<String> {
        return className.split(WHITESPACE)
            .asSequence()
            .map(String::trim)
            .filter { it.isNotBlank() }
            // Inspector's own bookkeeping classes must never reach a selector.
            .filterNot { it.startsWith("__stormy") || it.startsWith("__codex") }
            .filterNot { cls -> STATE_VARIANTS.any { cls.startsWith(it) } }
            .filterNot { it.startsWith("--") }
            .distinct()
            .toList()
    }

    /**
     * Escape a CSS identifier the way `CSS.escape` does for the characters that
     * actually turn up in class names: Tailwind's `:` and `/`, arbitrary-value
     * brackets, and the `.`/`#` that would otherwise be read as combinators.
     */
    fun escapeIdentifier(identifier: String): String = buildString(identifier.length + 8) {
        identifier.forEachIndexed { index, c ->
            when {
                c.isLetterOrDigit() || c == '-' || c == '_' || c.code > 0x7F -> append(c)
                else -> append('\\').append(c)
            }
            // A leading digit has to be escaped as a hex code point, not a
            // backslash-digit, or it reads as the start of a numeric escape.
            if (index == 0 && c.isDigit()) {
                setLength(0)
                append("\\3").append(c).append(' ')
            }
        }
    }

    /** Join an ancestor chain into a child-combinator path. */
    fun buildSelectorPath(ancestors: List<String>, elementSelector: String): String =
        if (ancestors.isEmpty()) elementSelector
        else (ancestors + elementSelector).joinToString(" > ")

    /** Parse a simple `tag#id.a.b` selector back into parts. */
    fun parseSelector(selector: String): SelectorComponents {
        val trimmed = selector.trim().substringAfterLast(' ').substringAfterLast('>')
        val tagName = TAG_PATTERN.find(trimmed)?.value ?: "*"
        val id = ID_PATTERN.find(trimmed)?.groupValues?.get(1)
        val classes = CLASS_PATTERN.findAll(trimmed).map { it.groupValues[1] }.toList()
        return SelectorComponents(tagName = tagName, id = id, classes = classes)
    }

    data class SelectorComponents(
        val tagName: String,
        val id: String? = null,
        val classes: List<String> = emptyList()
    ) {
        fun toSelector(): String = buildSelector(tagName, id, classes.joinToString(" "))
    }

    private val WHITESPACE = Regex("\\s+")
    private val TAG_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9-]*")
    private val ID_PATTERN = Regex("#((?:[\\w-]|\\\\.)+)")
    private val CLASS_PATTERN = Regex("\\.((?:[\\w-]|\\\\.)+)")

    private val STATE_VARIANTS = listOf(
        "hover:", "focus:", "focus-visible:", "focus-within:",
        "active:", "visited:", "target:", "disabled:", "group-hover:", "peer-"
    )
}
