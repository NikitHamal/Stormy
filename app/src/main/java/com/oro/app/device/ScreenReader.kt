package com.oro.app.device

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** One thing on screen the agent can see and refer to by [index]. */
data class ScreenElement(
    val index: Int,
    val role: String,
    val label: String,
    val bounds: Rect,
    val clickable: Boolean,
    val longClickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val selected: Boolean,
    val focused: Boolean,
    val enabled: Boolean,
    val password: Boolean,
    val value: String?,
    val viewId: String?,
    val window: String?,
) {
    val centerX: Int get() = bounds.centerX()
    val centerY: Int get() = bounds.centerY()
    val actionable: Boolean get() = clickable || longClickable || editable || scrollable || checkable

    /** Line used in the model's view of the screen. */
    fun render(): String = buildString {
        append('[').append(index).append("] ").append(role)
        if (label.isNotBlank()) append(" \"").append(label).append('"')
        if (editable) {
            when {
                password -> append(" · password field")
                !value.isNullOrEmpty() && value != label -> append(" · text: \"").append(value.take(80)).append('"')
                else -> append(" · empty")
            }
        }
        val flags = buildList {
            if (scrollable) add("scrollable")
            if (checkable) add(if (checked) "on" else "off")
            if (selected) add("selected")
            if (focused) add("focused")
            if (!enabled) add("disabled")
            if (longClickable && !clickable) add("long-press")
        }
        if (flags.isNotEmpty()) append(" · ").append(flags.joinToString(", "))
        if (window != null) append(" · in ").append(window)
    }
}

/** A snapshot of the device screen in a form any model can act on. */
data class ScreenState(
    val packageName: String,
    val appLabel: String,
    val width: Int,
    val height: Int,
    val keyboardVisible: Boolean,
    val elements: List<ScreenElement>,
    val truncated: Int,
    val capturedAt: Long = System.currentTimeMillis(),
) {
    /** Cheap fingerprint to tell the model whether an action changed anything. */
    val signature: Int by lazy {
        (packageName + elements.joinToString("|") { it.role + it.label + it.value + it.checked + it.bounds.top / 40 }).hashCode()
    }

    fun element(index: Int): ScreenElement? = elements.getOrNull(index)?.takeIf { it.index == index }
        ?: elements.firstOrNull { it.index == index }

    fun render(maxChars: Int = 7_000): String = buildString {
        append("App: ").append(appLabel)
        if (appLabel != packageName) append(" (").append(packageName).append(')')
        append(" · Screen ").append(width).append('x').append(height)
        append(" · Keyboard ").append(if (keyboardVisible) "open" else "closed").append('\n')
        if (elements.isEmpty()) {
            append("(No readable elements. The app may draw its own UI (game, video, map, WebView still loading). ")
            append("Wait, scroll, or tap by x,y coordinates.)\n")
            return@buildString
        }
        append("Elements (act on them by number):\n")
        for (e in elements) {
            val line = e.render()
            if (length + line.length > maxChars) {
                append("… ").append(elements.size - elements.indexOf(e)).append(" more elements not shown; scroll or use find_text.\n")
                break
            }
            append(line).append('\n')
        }
        if (truncated > 0) append("(").append(truncated).append(" minor elements omitted)\n")
    }
}

/**
 * Flattens the accessibility tree into a numbered list of what matters.
 *
 * ### Choices that make small models succeed
 *  - Only visible, on-screen nodes; zero-size and off-screen nodes are noise.
 *  - A clickable container takes the text of its children as its label, so a
 *    chat row reads `[7] button "Alice · hi there · 9:41"` instead of an
 *    unlabeled button followed by three loose texts -- the model taps what it
 *    reads.
 *  - Plain texts are kept (the model must be able to *read* the screen, e.g.
 *    a price or a message) but numbered like everything else, so "tap the
 *    text 'Settings'" always has a target.
 *  - The keyboard and system dialogs are separate windows; their nodes are
 *    included and labelled with the window so "Allow" in a permission
 *    dialog is visible.
 */
object ScreenReader {

    private const val MAX_ELEMENTS = 160
    private const val MAX_LABEL = 90

    /** Live nodes for the last snapshot, so actions can use node actions. */
    @Volatile
    var lastNodes: Map<Int, AccessibilityNodeInfo> = emptyMap()
        private set

    fun capture(service: AccessibilityService): ScreenState {
        val metrics = service.resources.displayMetrics
        val screen = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val keyboard = windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }

        val roots = mutableListOf<Pair<AccessibilityNodeInfo, String?>>()
        val active = service.rootInActiveWindow
        // Application windows first (top-most last in the list = drawn on top), then system dialogs, then the IME.
        windows.sortedBy { it.layer }.forEach { w ->
            if (w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return@forEach // our own overlay
            val root = w.root ?: return@forEach
            if (root.packageName?.toString() == service.packageName && w.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@forEach
            val name = when (w.type) {
                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "keyboard"
                AccessibilityWindowInfo.TYPE_SYSTEM -> if (w.isActive || isDialogLike(root)) "system" else return@forEach
                AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> return@forEach
                else -> if (active != null && root == active) null else if (w.isActive || w.isFocused) null else "window"
            }
            roots += root to name
        }
        if (roots.isEmpty() && active != null) roots += active to null

        val pkg = active?.packageName?.toString()
            ?: roots.firstOrNull { it.second == null }?.first?.packageName?.toString()
            ?: ""
        val out = mutableListOf<ScreenElement>()
        val nodes = HashMap<Int, AccessibilityNodeInfo>()
        var omitted = 0

        fun add(node: AccessibilityNodeInfo, bounds: Rect, label: String, role: String, window: String?) {
            if (out.size >= MAX_ELEMENTS) { omitted++; return }
            val idx = out.size
            out += ScreenElement(
                index = idx,
                role = role,
                label = label.take(MAX_LABEL),
                bounds = bounds,
                clickable = node.isClickable,
                longClickable = node.isLongClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                checkable = node.isCheckable,
                checked = isChecked(node),
                selected = node.isSelected,
                focused = node.isFocused,
                enabled = node.isEnabled,
                password = node.isPassword,
                value = if (node.isEditable && !node.isPassword) node.text?.toString() else null,
                viewId = node.viewIdResourceName,
                window = window,
            )
            nodes[idx] = node
        }

        fun walk(node: AccessibilityNodeInfo, window: String?, insideLabelled: Boolean, depth: Int) {
            if (depth > 60) return
            if (!node.isVisibleToUser) return
            val b = Rect().also { node.getBoundsInScreen(it) }
            if (b.width() <= 1 || b.height() <= 1 || !Rect.intersects(b, screen)) return
            b.intersect(screen)

            val actionable = node.isClickable || node.isLongClickable || node.isEditable || node.isCheckable ||
                (node.isScrollable && b.height() > 120)
            val own = ownText(node)
            var consumesChildren = false
            if (actionable) {
                var label = own
                if (label.isBlank() && !node.isEditable && !node.isScrollable) {
                    label = descendantText(node, 0).joinToString(" · ").trim()
                    consumesChildren = label.isNotBlank()
                }
                if (node.isEditable && label.isBlank()) label = hintOf(node) ?: ""
                if (label.isBlank() && !node.isScrollable) label = idHint(node) ?: ""
                add(node, b, label, roleOf(node), window)
            } else if (!insideLabelled && own.isNotBlank()) {
                add(node, b, own, if (node.className?.contains("Image") == true) "image" else "text", window)
            }
            for (i in 0 until node.childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                walk(child, window, insideLabelled || consumesChildren, depth + 1)
            }
        }

        roots.forEach { (root, window) -> walk(root, window, false, 0) }
        lastNodes = nodes

        val label = AppCatalog.labelFor(service, pkg) ?: pkg
        return ScreenState(pkg, label, screen.width(), screen.height(), keyboard, out, omitted)
    }

    private fun ownText(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val state = if (Build.VERSION.SDK_INT >= 30) node.stateDescription?.toString()?.trim().orEmpty() else ""
        return listOf(if (node.isPassword) "" else text, desc, state)
            .filter { it.isNotEmpty() }.distinct().joinToString(" · ").replace('\n', ' ')
    }

    private fun descendantText(node: AccessibilityNodeInfo, depth: Int): List<String> {
        if (depth > 6) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until node.childCount) {
            val c = runCatching { node.getChild(i) }.getOrNull() ?: continue
            if (!c.isVisibleToUser) continue
            val t = ownText(c)
            if (t.isNotBlank()) out += t
            if (out.joinToString().length > MAX_LABEL) break
            out += descendantText(c, depth + 1)
        }
        return out.distinct()
    }

    @Suppress("DEPRECATION")
    private fun isChecked(node: AccessibilityNodeInfo): Boolean = node.isChecked

    private fun hintOf(node: AccessibilityNodeInfo): String? =
        if (Build.VERSION.SDK_INT >= 26) node.hintText?.toString()?.takeIf { it.isNotBlank() } else null

    private fun idHint(node: AccessibilityNodeInfo): String? =
        node.viewIdResourceName?.substringAfter(":id/")?.replace('_', ' ')?.takeIf { it.isNotBlank() && it.length < 40 }

    private fun isDialogLike(root: AccessibilityNodeInfo): Boolean =
        root.className?.contains("Dialog") == true || root.packageName?.contains("permissioncontroller") == true

    private fun roleOf(node: AccessibilityNodeInfo): String {
        val cls = node.className?.toString().orEmpty()
        return when {
            node.isEditable -> "input"
            node.isCheckable && cls.contains("Switch") -> "switch"
            node.isCheckable && cls.contains("Radio") -> "radio"
            node.isCheckable -> "checkbox"
            cls.contains("SeekBar") -> "slider"
            cls.contains("Tab") -> "tab"
            cls.contains("Image") && node.isClickable -> "icon-button"
            node.isScrollable && !node.isClickable -> "list"
            node.isClickable || node.isLongClickable -> "button"
            else -> "view"
        }
    }
}
