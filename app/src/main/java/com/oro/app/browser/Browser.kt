package com.oro.app.browser

/** One tab of the engine, as the agent and the tab strip see it. */
data class TabInfo(val id: Int, val url: String, val title: String, val active: Boolean)

/** A file the engine downloaded during a run. */
data class DownloadInfo(val name: String, val mime: String, val bytes: Long, val at: Long, val path: String)

/**
 * Everything one browser run needs from the web.
 *
 * [BrowserEngine] is the real browser (WebViews, JS bridge, main thread). The
 * run loop talks only to this interface, so the whole decision path --
 * observe, resolve, guard, act, report -- runs in JVM tests against a
 * scripted fake browser (see the scenario bench in app/src/test), exactly
 * like the device agent's Phone seam did.
 *
 * Every action returns an outcome sentence the model reads: what happened, or
 * why it did not. Sentences starting with the [FAILED_SENTINEL] words ("No ",
 * "Could not", "Refused", ...) mark an action that did not happen, so the run
 * can stop the rest of the turn (the page the remaining calls were planned
 * for never came).
 */
internal interface Browser {

    /** Millisecond clock the engine's change stamps are on. */
    fun uptime(): Long

    /**
     * Uptime of the last thing that changed the page (navigation finished, DOM
     * structure moved, scroll ended). Value-only repaints (a caret, a clock)
     * do not count, so a fresh capture can be reused across them.
     */
    val lastChangeAt: Long

    /** Whether [state] is still the newest snapshot: no navigation since. */
    fun isLatest(state: PageState): Boolean

    /** The page as it is now: tagged, extracted, parsed. */
    suspend fun snapshot(): PageState

    /** Waits until the DOM has been quiet for [quietMs], at least [minMs], at most [maxMs]. */
    suspend fun settle(quietMs: Long = 250, maxMs: Long = 2_500, minMs: Long = 120)

    /**
     * Loads [url] in the active tab and waits for the page to arrive
     * (finished + settled). Returns the outcome sentence; on failure says why
     * (DNS, TLS refused, timeout) without Chromium's error page nonsense.
     */
    suspend fun navigate(url: String): String

    suspend fun back(): String
    suspend fun forward(): String
    suspend fun reload(): String

    /** Clicks the element by its oid (selector fallback). Scrolls it into view first. */
    suspend fun click(e: PageElement): String

    /**
     * Resolves a CSS selector against the live page: tags the first match and
     * returns it as an element (index -1: it was not in the listing), or null.
     */
    suspend fun bySelector(selector: String): PageElement?

    /**
     * Types [text] into [e] (null: the focused field). React-safe: the native
     * value setter plus input/change events. [append] adds to what is there.
     */
    suspend fun type(e: PageElement?, text: String, append: Boolean): String

    /** Enter on the focused element / the enclosing form's submit. */
    suspend fun submit(): String

    /** A named key on the focused element: enter, tab, escape, backspace, arrows. */
    suspend fun pressKey(key: String): String

    /** Picks the option showing [option] in a <select>; says what it chose. */
    suspend fun select(e: PageElement, option: String): String

    /** Sets a checkbox/radio/switch to [checked]; a no-op reports "already". */
    suspend fun setChecked(e: PageElement, checked: Boolean): String

    /** Scrolls the page (or [e], a scroll container) by [amount] of a viewport. */
    suspend fun scroll(direction: String, e: PageElement?, amount: Float = 0.7f): String

    /** Scrolls until [text] is in the viewport (or the end of the page). */
    suspend fun scrollToText(text: String): String

    /** Hover (menus that open on mouseover). */
    suspend fun hover(e: PageElement): String

    /**
     * Runs [js] in the page and returns its result as text. Guarded: no
     * javascript:-URL tricks, result capped, exceptions reported.
     */
    suspend fun evaluate(js: String): String

    /** Extracts the page (or [selector]'s subtree) as markdown/text/links. */
    suspend fun readPage(mode: String, selector: String?, maxChars: Int): String

    /** A viewport (or full-page) screenshot as base64 JPEG; null when it fails. */
    suspend fun screenshot(fullPage: Boolean): String?

    /** Waits for [text] to appear (or, with [gone], disappear); reports which. */
    suspend fun waitForText(text: String, gone: Boolean, timeoutMs: Long): String

    /** Waits for a CSS selector to exist (or with [gone], to vanish). */
    suspend fun waitForSelector(selector: String, gone: Boolean, timeoutMs: Long): String

    /** Waits until the URL contains [fragment]. */
    suspend fun waitForUrl(fragment: String, timeoutMs: Long): String

    /** Cheap text presence check while waiting (no full snapshot). */
    suspend fun textVisible(text: String): Boolean

    fun url(): String
    fun title(): String
    fun tabs(): List<TabInfo>
    suspend fun switchTab(id: Int): String
    suspend fun closeTab(id: Int): String
    suspend fun newTab(url: String?): String

    /** Sets up [absolutePath] (a project file) for the file input [e], then clicks it. */
    suspend fun uploadFile(e: PageElement, absolutePath: String): String

    fun downloads(): List<DownloadInfo>

    /** Reads a text download (csv/json/txt...) back, capped at [maxChars]. */
    fun readDownload(name: String, maxChars: Int): String

    /**
     * Brings the page to the front and focuses [e] (or the page) so the user
     * can type a secret straight into it. The model never sees what is typed.
     */
    suspend fun focusForUser(e: PageElement?)

    /** Types a secret into [e] without it ever entering the model's context. */
    suspend fun typeSecret(e: PageElement, secret: String): String

    /** Answers an HTTP-auth prompt (or cancels it). */
    fun answerHttpAuth(proceed: Boolean, user: String?, password: String?)

    /** Engine health as one line: tabs, url, cookies on, downloads pending. */
    fun status(): String

    companion object {
        /** Outcome sentences that mean the action did not happen (see interface doc). */
        val FAILED_SENTINEL = Regex(
            "^(No |Unknown |Could not|Refused|Blocked|Skipped|Not |Failed)|could not|failed|^\\w+ needs |not supported",
            RegexOption.IGNORE_CASE
        )
    }
}
