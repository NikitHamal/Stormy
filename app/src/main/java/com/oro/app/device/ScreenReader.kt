package com.oro.app.device

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

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
 *  - System dialogs and popups are separate windows; their nodes are
 *    included and tagged, so "Allow" in a permission dialog is visible.
 *  - The keyboard is NOT listed key by key: forty key nodes per screen were
 *    the largest single cost in every observation while typing, and the
 *    agent types with type_text anyway. It is reported as "keyboard open".
 *
 * ### Cost on low-end phones
 * One pass over each window's tree; child labels are gathered only for
 * unlabeled clickables and stop early; nothing is rendered here.
 */
object ScreenReader {

    private const val MAX_ELEMENTS = 150
    private const val MAX_DEPTH = 60

    /** Label length in normal observations; read_page asks for whole paragraphs. */
    const val DEFAULT_MAX_LABEL = 80

    /** The last capture and its live nodes, kept together so a node is never used against another screen. */
    private class Snapshot(val state: ScreenState, val nodes: Map<Int, AccessibilityNodeInfo>)

    @Volatile
    private var last: Snapshot? = null

    /**
     * The live node behind element [index] of [state], or null when [state] is
     * not the latest capture (its numbers would point at the wrong nodes).
     */
    fun nodeFor(state: ScreenState, index: Int): AccessibilityNodeInfo? {
        val snap = last ?: return null
        return if (snap.state === state) snap.nodes[index] else null
    }

    /** Whether [state] is the newest capture, i.e. its nodes are the ones actions use. */
    fun isLatest(state: ScreenState): Boolean = last?.state === state

    fun capture(service: AccessibilityService, maxLabel: Int = DEFAULT_MAX_LABEL): ScreenState {
        val metrics = service.resources.displayMetrics
        val screen = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        val keyboard = ime != null
        val keyboardTop = ime?.let { w -> Rect().also { w.getBoundsInScreen(it) }.top.takeIf { it > 0 } }

        val roots = mutableListOf<Pair<AccessibilityNodeInfo, String?>>()
        val active = service.rootInActiveWindow
        // Application windows first (lowest layer), then dialogs and system
        // windows drawn over them. Our own overlay and the keyboard are skipped.
        windows.sortedBy { it.layer }.forEach { w ->
            when (w.type) {
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
                AccessibilityWindowInfo.TYPE_INPUT_METHOD,
                AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> return@forEach
            }
            val root = w.root ?: return@forEach
            if (root.packageName?.toString() == service.packageName && w.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@forEach
            val name = when (w.type) {
                AccessibilityWindowInfo.TYPE_SYSTEM ->
                    if (isDialogLike(root)) ScreenState.WINDOW_DIALOG
                    else if (w.isActive || w.isFocused) ScreenState.WINDOW_SYSTEM
                    else return@forEach
                else -> when {
                    active != null && root == active -> null
                    w.isActive || w.isFocused -> null
                    isDialogLike(root) -> ScreenState.WINDOW_DIALOG
                    else -> ScreenState.WINDOW_POPUP
                }
            }
            roots += root to name
        }
        if (roots.isEmpty() && active != null) roots += active to null

        val pkg = active?.packageName?.toString()
            ?: roots.firstOrNull { it.second == null }?.first?.packageName?.toString()
            ?: ""
        val out = ArrayList<ScreenElement>(64)
        val nodes = ArrayList<AccessibilityNodeInfo>(64)
        var omitted = 0
        val scratch = Rect()

        fun add(node: AccessibilityNodeInfo, bounds: Bounds, label: String, role: String, window: String?) {
            if (out.size >= MAX_ELEMENTS) { omitted++; return }
            out += ScreenElement(
                index = out.size,
                role = role,
                label = clean(label, maxLabel),
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
                value = if (node.isEditable && !node.isPassword && !showsHint(node)) node.text?.toString() else null,
                viewId = node.viewIdResourceName,
                window = window,
                secretLength = if (node.isPassword && !showsHint(node)) node.text?.length ?: 0 else 0,
            ).let { e -> if (node.isScrollable) withScrollCapacity(e, node) else e }
            nodes += node
        }

        fun walk(node: AccessibilityNodeInfo, window: String?, insideLabelled: Boolean, depth: Int) {
            if (depth > MAX_DEPTH || !node.isVisibleToUser) return
            node.getBoundsInScreen(scratch)
            if (scratch.width() <= 1 || scratch.height() <= 1 || !Rect.intersects(scratch, screen)) return
            scratch.intersect(screen)
            val b = Bounds(scratch.left, scratch.top, scratch.right, scratch.bottom)

            val cls = node.className?.toString().orEmpty()
            val actionable = node.isClickable || node.isLongClickable || node.isEditable || node.isCheckable ||
                (node.isScrollable && b.height > 120)
            val own = ownText(node)
            var consumesChildren = false
            if (actionable) {
                // A field is named by its hint or description, never by what is
                // typed in it: that is its value, shown separately.
                var label = if (node.isEditable) fieldName(node) else own
                if (label.isBlank() && !node.isEditable && !node.isScrollable) {
                    label = descendantText(node)
                    consumesChildren = label.isNotBlank()
                }
                if (label.isBlank() && !node.isScrollable) label = idHint(node) ?: ""
                add(node, b, label, roleOf(node, cls), window)
            } else if (cls.contains("ProgressBar") && !insideLabelled) {
                add(node, b, own, ScreenElement.ROLE_PROGRESS, window)
            } else if (!insideLabelled && own.isNotBlank()) {
                add(node, b, own, if (cls.contains("Image")) "image" else ScreenElement.ROLE_TEXT, window)
            }
            val children = node.childCount
            for (i in 0 until children) {
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                walk(child, window, insideLabelled || consumesChildren, depth + 1)
            }
        }

        roots.forEach { (root, window) -> walk(root, window, false, 0) }

        // Dedupe renumbers; keep the live nodes aligned with the final indexes.
        val deduped = ScreenState.dedupe(out)
        val byOriginal = HashMap<Int, AccessibilityNodeInfo>(deduped.size * 2)
        if (deduped.size == out.size) {
            out.indices.forEach { byOriginal[it] = nodes[it] }
        } else {
            var j = 0
            for (i in out.indices) {
                if (j < deduped.size && sameElement(out[i], deduped[j])) {
                    byOriginal[j] = nodes[i]
                    j++
                }
            }
        }
        val label = AppCatalog.labelFor(service, pkg) ?: pkg
        val state = ScreenState(pkg, label, screen.width(), screen.height(), keyboard, deduped, omitted, keyboardTop = keyboardTop)
        last = Snapshot(state, byOriginal)
        return state
    }

    /**
     * Which ways [node] can still scroll, from the actions it offers right now
     * (a list at its end no longer offers "scroll forward"), and whether it
     * scrolls sideways. Read only for scrollables, so it costs one call each.
     */
    private fun withScrollCapacity(e: ScreenElement, node: AccessibilityNodeInfo): ScreenElement {
        val ids = runCatching { node.actionList.map { it.id } }.getOrNull() ?: return e
        val down = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        val up = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        val left = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
        val right = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
        val sideways = (left in ids || right in ids) && down !in ids && up !in ids
        val cls = node.className?.toString().orEmpty()
        val horizontal = sideways || cls.contains("HorizontalScrollView") || cls.contains("ViewPager") ||
            (e.bounds.width > e.bounds.height * 2 && e.bounds.height < 600)
        return e.copy(
            canScrollForward = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD in ids || down in ids || right in ids,
            canScrollBack = AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD in ids || up in ids || left in ids,
            horizontal = horizontal,
        )
    }

    /** Equal apart from the index: dedupe only drops and renumbers, never edits. */
    private fun sameElement(a: ScreenElement, b: ScreenElement): Boolean =
        a.role == b.role && a.label == b.label && a.bounds == b.bounds && a.viewId == b.viewId

    private fun clean(label: String, maxLabel: Int): String {
        val flat = label.replace('\n', ' ').replace(Regex("\\s{2,}"), " ").trim()
        return if (flat.length <= maxLabel) flat else flat.take(maxLabel - 1).trimEnd() + "…"
    }

    private fun ownText(node: AccessibilityNodeInfo): String {
        val text = if (node.isPassword) "" else node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val state = if (Build.VERSION.SDK_INT >= 30) node.stateDescription?.toString()?.trim().orEmpty() else ""
        return when {
            text.isEmpty() && desc.isEmpty() -> state
            text.isEmpty() -> if (state.isEmpty() || state == desc) desc else "$desc · $state"
            desc.isEmpty() || desc == text -> if (state.isEmpty() || state == text) text else "$text · $state"
            else -> "$text · $desc"
        }
    }

    /** Text of an unlabeled clickable's children, joined; stops once it is long enough to identify the row. */
    private fun descendantText(node: AccessibilityNodeInfo): String {
        val parts = ArrayList<String>(4)
        var length = 0
        fun visit(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 6 || length > DEFAULT_MAX_LABEL) return
            for (i in 0 until n.childCount) {
                if (length > DEFAULT_MAX_LABEL) return
                val c = runCatching { n.getChild(i) }.getOrNull() ?: continue
                if (!c.isVisibleToUser) continue
                val t = ownText(c)
                if (t.isNotBlank() && t !in parts) {
                    parts += t
                    length += t.length + 3
                }
                visit(c, depth + 1)
            }
        }
        visit(node, 0)
        return parts.joinToString(" · ")
    }

    @Suppress("DEPRECATION")
    private fun isChecked(node: AccessibilityNodeInfo): Boolean = node.isChecked

    private fun hintOf(node: AccessibilityNodeInfo): String? =
        if (Build.VERSION.SDK_INT >= 26) node.hintText?.toString()?.takeIf { it.isNotBlank() } else null

    /** What a text field is called: its hint, its description, or the label that names it. */
    private fun fieldName(node: AccessibilityNodeInfo): String {
        hintOf(node)?.let { return it.trim() }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        runCatching { node.labeledBy }.getOrNull()?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        // Before Android 8 an empty field reports its hint as its text.
        if (Build.VERSION.SDK_INT < 26 && !node.isPassword) node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return ""
    }

    private fun showsHint(node: AccessibilityNodeInfo): Boolean =
        Build.VERSION.SDK_INT >= 26 && node.isShowingHintText

    private fun idHint(node: AccessibilityNodeInfo): String? =
        node.viewIdResourceName?.substringAfter(":id/")?.replace('_', ' ')?.takeIf { it.isNotBlank() && it.length < 40 }

    private fun isDialogLike(root: AccessibilityNodeInfo): Boolean =
        root.className?.contains("Dialog") == true || root.packageName?.contains("permissioncontroller") == true

    private fun roleOf(node: AccessibilityNodeInfo, cls: String): String = when {
        node.isEditable -> "input"
        node.isCheckable && cls.contains("Switch") -> "switch"
        node.isCheckable && cls.contains("Radio") -> "radio"
        node.isCheckable -> "checkbox"
        cls.contains("SeekBar") -> "slider"
        cls.contains("Tab") -> "tab"
        cls.contains("Image") && node.isClickable -> "icon"
        node.isScrollable && !node.isClickable -> ScreenElement.ROLE_LIST
        node.isClickable || node.isLongClickable -> "button"
        else -> "view"
    }
}
