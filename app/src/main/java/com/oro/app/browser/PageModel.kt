package com.oro.app.browser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * A rectangle in page pixels (CSS px, viewport coordinates for on-screen
 * elements). Plain Kotlin rather than android.graphics.Rect so the whole page
 * model runs in JVM unit tests.
 */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val area: Long get() = width.toLong().coerceAtLeast(0) * height.toLong().coerceAtLeast(0)

    fun overlaps(o: Bounds): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
}

/**
 * The page as the agent sees it: plain Kotlin, parsed from the engine's
 * snapshot JSON, so everything about how a page is described to a model --
 * rendering, matching, change detection -- runs in JVM unit tests.
 *
 * The design that made the device agent work with weak models carries over:
 * a short numbered list of what can be acted on, not a DOM dump or an image.
 * Every element keeps the `data-oid` tag the snapshot script gave it, so an
 * action resolves the exact element the model saw, and a stable CSS selector
 * as the fallback when an SPA re-rendered between the snapshot and the act.
 */
data class PageElement(
    val index: Int,
    /** link, button, textbox, checkbox, radio, combobox, switch, tab, menuitem, slider, file, area... */
    val role: String,
    /** The words a person would use for it: text, aria-label, placeholder, name. */
    val label: String,
    val bounds: Bounds,
    val oid: String = "",
    val selector: String = "",
    /** Frame index (0 = main document). */
    val frame: Int = 0,
    val editable: Boolean = false,
    val password: Boolean = false,
    val disabled: Boolean = false,
    val checked: Boolean? = null,
    /** Current value of a field or slider (never a password's). */
    val value: String? = null,
    /** A dropdown's open state, when the page reports one. */
    val expanded: Boolean? = null,
    /** A link's destination, when cheap to have. */
    val href: String? = null,
    /** select/combobox option count. */
    val options: Int = 0,
    /** Selected option text of a <select>. */
    val selected: String? = null,
    /** Scroll container: can still move back/forward, and horizontally. */
    val canScrollBack: Boolean = false,
    val canScrollForward: Boolean = false,
    val horizontal: Boolean = false,
    /** Input types the engine should treat with care (date, time, color...). */
    val inputType: String? = null,
    val multiline: Boolean = false,
) {
    /** One compact line, as the model reads it. */
    fun render(): String = buildString {
        append('[').append(index).append("] ").append(role)
        if (label.isNotBlank()) append(" \"").append(label).append('"')
        if (password) {
            append(" (password")
            append(if ((value?.isNotEmpty() == true)) ", filled" else ", empty")
            append(')')
        } else if (value != null && value.isNotBlank() && value.length <= 40) {
            append(" = \"").append(value).append('"')
        }
        checked?.let { append(if (it) " (checked)" else " (unchecked)") }
        expanded?.let { append(if (it) " (open)" else " (closed)") }
        if (options > 0) {
            append(" (").append(options).append(" options")
            selected?.takeIf { it.isNotBlank() }?.let { append(", selected \"").append(it).append('"') }
            append(')')
        }
        if (disabled) append(" (disabled)")
        if (multiline) append(" (multiline)")
        if (canScrollForward || canScrollBack) {
            append(" (scrolls").append(if (horizontal) " sideways" else "")
            if (!canScrollBack) append(", at start")
            if (!canScrollForward) append(", at end")
            append(')')
        }
    }
}

/** A frame the snapshot found. Cross-origin frames are read-only for actions. */
data class FrameInfo(
    val id: Int,
    val src: String,
    val crossOrigin: Boolean,
    /** The engine accepted commands for it (not a captcha or a dead frame). */
    val controllable: Boolean,
    /** A human check lives in this frame. */
    val humanCheck: Boolean = false,
)

/** What kind of human check is on the page, if any. */
enum class HumanCheckKind { NONE, RECAPTCHA, HCAPTCHA, TURNSTILE, ARKOSE, TEXT }

/**
 * One snapshot of one page. [signature] is the rolling hash the run compares
 * before/after an action: same signature means "the page did not change", the
 * single most useful signal for noticing a click that did nothing.
 */
data class PageState(
    val url: String,
    val title: String,
    val elements: List<PageElement>,
    val text: String,
    val signature: Int,
    val scrollY: Int,
    val pageHeight: Int,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val loading: Boolean,
    val humanCheck: HumanCheckKind = HumanCheckKind.NONE,
    val humanCheckPassed: Boolean = false,
    val frames: List<FrameInfo> = emptyList(),
    val consoleErrors: Int = 0,
    val forms: Int = 0,
    val capturedAt: Long = System.currentTimeMillis(),
) {
    /** A human check the user still has to pass (a ticked box is passed). */
    val needsHuman: Boolean get() = humanCheck != HumanCheckKind.NONE && !humanCheckPassed

    val canScrollDown: Boolean get() = scrollY + viewportHeight < pageHeight - 4
    val canScrollUp: Boolean get() = scrollY > 4

    val editable: List<PageElement> get() = elements.filter { it.editable }
    val passwordFields: List<PageElement> get() = elements.filter { it.password }

    fun element(index: Int): PageElement? = elements.firstOrNull { it.index == index }

    /**
     * The observation the model reads after every action. Kept short: only the
     * newest page stays in context (BrowserAgent.compact folds older ones).
     */
    fun render(maxChars: Int = 6_000): String = buildString {
        append(PAGE_MARKER).append('\n')
        append("Page: ").append(title.ifBlank { "(no title)" }).append(" — ").append(url).append('\n')
        if (loading) append("(still loading)\n")
        if (needsHuman) {
            append("⚠ HUMAN CHECK (").append(humanCheck.name.lowercase()).append(") on the page: the user must pass it; you cannot and must not try.\n")
        }
        append("Scroll: ").append(scrollY).append('/').append(pageHeight)
        append(" (viewport ").append(viewportWidth).append('x').append(viewportHeight).append(')')
        if (canScrollDown) append(" — more below")
        if (canScrollUp) append(" — more above")
        append('\n')
        if (frames.any { it.crossOrigin }) {
            append("Frames: ").append(frames.filter { it.crossOrigin }.joinToString("; ") {
                it.src + if (it.humanCheck) " (human check)" else if (!it.controllable) " (not controllable)" else ""
            }).append('\n')
        }
        if (consoleErrors > 0) append("Console errors: ").append(consoleErrors).append('\n')
        val elementsPart = elements.joinToString("\n") { it.render() }
        val budget = (maxChars * 0.55).toInt().coerceAtLeast(800)
        if (elementsPart.length <= budget) {
            append(elementsPart)
        } else {
            // Keep the interactive head and as much of the tail as fits; the
            // model can read_page or scroll for the rest.
            var used = 0
            val kept = ArrayList<String>()
            for (line in elementsPart.lineSequence()) {
                if (used + line.length + 1 > budget) break
                kept += line
                used += line.length + 1
            }
            append(kept.joinToString("\n"))
            append("\n(... ").append(elements.size - kept.size).append(" more elements; use read_page or scroll)")
        }
        val textBudget = maxChars - length
        if (textBudget > 300 && text.isNotBlank()) {
            append("\n\nText on page:\n")
            append(if (text.length <= textBudget) text else text.take(textBudget) + "\n[... truncated; read_page for more]")
        }
    }

    companion object {
        /** Marks the observation inside a tool result, so the run can fold it later. */
        const val PAGE_MARKER = "=== PAGE ==="
    }
}

/** Parses the engine's snapshot JSON and renders nothing but truth. */
object PageModel {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parse(raw: String): PageState? = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        fromJson(root)
    }.getOrNull()

    fun fromJson(root: JsonObject): PageState {
        val url = root.str("url").orEmpty()
        val title = root.str("title").orEmpty()
        val viewportW = root.int("viewportW") ?: 412
        val viewportH = root.int("viewportH") ?: 915
        val scrollY = root.int("scrollY") ?: 0
        val pageHeight = root.int("pageHeight") ?: viewportH
        val loading = root.bool("loading") ?: false
        val text = root.str("text").orEmpty()
        val consoleErrors = root.int("consoleErrors") ?: 0
        val forms = root.int("forms") ?: 0

        val frames = (root["frames"] as? JsonArray).items().mapIndexed { i, f ->
            val o = f.jsonObject
            FrameInfo(
                id = o.int("id") ?: i,
                src = o.str("src").orEmpty(),
                crossOrigin = o.bool("crossOrigin") ?: false,
                controllable = o.bool("control") ?: !(o.bool("crossOrigin") ?: false),
                humanCheck = o.bool("humanCheck") ?: false,
            )
        }

        val checkObj = root["humanCheck"] as? JsonObject
        var checkKind = HumanCheckKind.NONE
        var checkPassed = false
        if (checkObj != null) {
            checkPassed = checkObj.bool("passed") ?: false
            checkKind = when (checkObj.str("kind")?.lowercase()) {
                "recaptcha" -> HumanCheckKind.RECAPTCHA
                "hcaptcha" -> HumanCheckKind.HCAPTCHA
                "turnstile" -> HumanCheckKind.TURNSTILE
                "arkose" -> HumanCheckKind.ARKOSE
                null -> HumanCheckKind.NONE
                else -> HumanCheckKind.TEXT
            }
        }

        val elements = (root["elements"] as? JsonArray).items().mapIndexedNotNull { i, e ->
            (e as? JsonObject)?.let { parseElement(it, i + 1) }
        }

        return PageState(
            url = url, title = title, elements = elements, text = text,
            signature = signatureOf(url, elements, text, scrollY, viewportH, pageHeight, checkKind, checkPassed),
            scrollY = scrollY, pageHeight = pageHeight,
            viewportWidth = viewportW, viewportHeight = viewportH,
            loading = loading, humanCheck = checkKind, humanCheckPassed = checkPassed,
            frames = frames, consoleErrors = consoleErrors, forms = forms,
        )
    }

    private fun parseElement(o: JsonObject, fallbackIndex: Int): PageElement {
        val x = o.int("x") ?: 0
        val y = o.int("y") ?: 0
        val w = o.int("w") ?: 0
        val h = o.int("h") ?: 0
        return PageElement(
            index = o.int("i") ?: fallbackIndex,
            role = o.str("r") ?: "element",
            label = (o.str("t") ?: "").trim(),
            bounds = Bounds(x, y, x + w, y + h),
            oid = o.str("oid").orEmpty(),
            selector = o.str("sel").orEmpty(),
            frame = o.int("f") ?: 0,
            editable = o.bool("ed") ?: false,
            password = o.bool("pw") ?: false,
            disabled = o.bool("dis") ?: false,
            checked = o.bool("chk"),
            value = when {
                o.bool("pw") == true ->
                    // A password's value is never in the snapshot; only filled/empty.
                    if (o.bool("hasValue") == true) "•" else null
                // A select shows its chosen option, not a raw value.
                (o.int("opt") ?: 0) > 0 -> null
                else -> o.str("val")
            },
            expanded = o.bool("exp"),
            href = o.str("href"),
            options = o.int("opt") ?: 0,
            selected = o.str("selected") ?: o.str("val")?.takeIf { (o.int("opt") ?: 0) > 0 },
            canScrollBack = o.bool("scrBack") ?: false,
            canScrollForward = o.bool("scrFwd") ?: false,
            horizontal = o.bool("hScroll") ?: false,
            inputType = o.str("itype"),
            multiline = o.bool("multi") ?: false,
        )
    }

    /**
     * The change-detection hash: what the page *is* (url, interactive
     * structure, scroll position, check state), not every repaint. Value-only
     * changes (a typed character, a clock) are folded in only through checked
     * states and password filled/empty, so "did my click do anything" stays a
     * reliable question -- the same trade the device agent's ScreenModel made.
     */
    fun signatureOf(
        url: String,
        elements: List<PageElement>,
        text: String,
        scrollY: Int,
        viewportH: Int,
        pageHeight: Int,
        check: HumanCheckKind,
        checkPassed: Boolean,
    ): Int {
        var h = url.hashCode()
        h = 31 * h + (text.length / 64)          // coarse text mass
        h = 31 * h + (scrollY / (viewportH / 2).coerceAtLeast(1))
        h = 31 * h + pageHeight
        h = 31 * h + check.ordinal
        h = 31 * h + (if (checkPassed) 1 else 0)
        for (e in elements) {
            h = 31 * h + e.index
            h = 31 * h + e.role.hashCode()
            h = 31 * h + e.label.hashCode()
            h = 31 * h + (e.checked?.let { if (it) 1 else 0 } ?: -1)
            h = 31 * h + (if (e.disabled) 1 else 0)
            h = 31 * h + (if (e.expanded == true) 1 else 0)
            h = 31 * h + (if (e.password && e.value != null) 1 else 0)
            h = 31 * h + (e.value?.takeIf { !e.password }?.hashCode() ?: 0)
            // Position, coarse: a list that reordered is a different page, a
            // pixel that shifted is not.
            h = 31 * h + (e.bounds.centerY / 24)
        }
        return h
    }

    // JSON helpers that tolerate the usual slop.
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.strContent()

    private fun JsonPrimitive.strContent(): String? = runCatching { content }.getOrNull()?.takeIf { it != "null" }

    private fun JsonObject.int(key: String): Int? {
        val p = this[key] as? JsonPrimitive ?: return null
        return p.intOrNull ?: p.doubleOrNull?.toInt() ?: runCatching { p.content.trim('"').toInt() }.getOrNull()
    }

    private fun JsonObject.bool(key: String): Boolean? {
        val p = this[key] as? JsonPrimitive ?: return null
        return p.booleanOrNull ?: when (p.content.trim('"').lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun JsonArray?.items(): List<kotlinx.serialization.json.JsonElement> = this?.toList() ?: emptyList()
}
