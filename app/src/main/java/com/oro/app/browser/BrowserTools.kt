package com.oro.app.browser

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.Tool

/**
 * The browser agent's tool set.
 *
 * Ordered by how often a task needs them, because weak models favour the
 * first verbs they read. Elements are addressed by the number in the page
 * listing, or by their visible words (click_text) -- small models copy words
 * more reliably than numbers. CSS selectors and raw JS (evaluate) are the
 * power tools for pages that need precision.
 *
 * Every action's result carries the fresh page listing plus an explicit "the
 * page did not change" when it didn't, so a click that did nothing is caught
 * on the spot instead of being repeated forever.
 */
object BrowserTools {

    private fun tool(name: String, description: String, schema: String) = Tool(
        function = FunctionDefinition(name, description.trimIndent(), AiHttp.json.parseToJsonElement(schema.trimIndent()))
    )

    private const val TARGET = """
          "index":{"type":"integer","description":"Element number from the page listing (preferred)."},
          "text":{"type":"string","description":"The element's visible words instead of its number (close matches allowed)."},
          "selector":{"type":"string","description":"CSS selector instead of index/text, for precision."}
    """

    val OPEN_URL = tool(
        "open_url",
        "Open a URL in the current tab (or a new one). A bare word that is not a URL opens a search. Wait for the page and return its listing.",
        """{"type":"object","properties":{
          "url":{"type":"string","description":"Absolute http(s) URL, a domain, or search words."},
          "new_tab":{"type":"boolean","description":"Open in a new tab instead of the current one. Default false."}
        },"required":["url"]}"""
    )

    val CLICK = tool(
        "click",
        "Click an element: by index, by its visible text, or by CSS selector. Clicking a link or button returns the new page.",
        """{"type":"object","properties":{$TARGET},"required":[]}"""
    )

    val CLICK_TEXT = tool(
        "click_text",
        "Click the element showing this text (case-insensitive, close matches allowed; 'Sign in' also finds 'Log in'). If several match, you get their numbers to choose from.",
        """{"type":"object","properties":{
          "text":{"type":"string","description":"The words shown on the link, button or row."},
          "nth":{"type":"integer","description":"Which match, in page order, when several show the same text. Default: refuse and list them."}
        },"required":["text"]}"""
    )

    val TYPE_TEXT = tool(
        "type_text",
        "Type into a field: the field at index/text/selector, else the focused one. Replaces its text unless append=true. submit=true presses Enter afterwards. Never type a password the task gave you in plaintext -- use use_credential or ask_user_secret instead.",
        """{"type":"object","properties":{
          "text":{"type":"string","description":"The text to type."},
          "index":{"type":"integer","description":"Field element number. Omit to use the focused field."},
          "selector":{"type":"string","description":"CSS selector of the field instead of index."},
          "append":{"type":"boolean","description":"Add to existing text instead of replacing. Default false."},
          "submit":{"type":"boolean","description":"Press Enter after typing (submits the enclosing form). Default false."}
        },"required":["text"]}"""
    )

    val FILL_FORM = tool(
        "fill_form",
        "Fill a whole form in one call: text fields, checkboxes, switches, radio groups and dropdowns, found by their label, placeholder or name. Fields further down are scrolled to. Every field is reported (filled / ambiguous / not found); nothing is guessed. submit=true then presses the form's own submit button.",
        """{"type":"object","properties":{
          "fields":{"type":"array","description":"One entry per field, in form order.","items":{"type":"object","properties":{
            "field":{"type":"string","description":"The field's label or placeholder as shown, e.g. Email."},
            "index":{"type":"integer","description":"Element number instead of field, when the label is unclear."},
            "value":{"type":"string","description":"Text to type; true/false for a checkbox or switch; the option to pick for a dropdown or radio group."}
          },"required":["value"]}},
          "submit":{"type":"boolean","description":"Press the form's submit button after filling. Default false."}
        },"required":["fields"]}"""
    )

    val SELECT = tool(
        "select",
        "Pick an option in a <select> dropdown by its visible text (or value). For custom (non-native) dropdowns, click to open and then click the option.",
        """{"type":"object","properties":{
          "index":{"type":"integer","description":"The dropdown's element number."},
          "option":{"type":"string","description":"The option to choose, as shown."}
        },"required":["index","option"]}"""
    )

    val CHECK = tool(
        "check",
        "Set a checkbox, switch or radio to a state.",
        """{"type":"object","properties":{
          "index":{"type":"integer","description":"The element's number."},
          "value":{"type":"boolean","description":"true to check, false to uncheck."}
        },"required":["index","value"]}"""
    )

    val SCROLL = tool(
        "scroll",
        "Scroll the page (or one inner list, by index). direction=down shows content further down.",
        """{"type":"object","properties":{
          "direction":{"type":"string","enum":["down","up","left","right"]},
          "index":{"type":"integer","description":"Inner scrollable element to scroll. Omit for the page."},
          "amount":{"type":"number","description":"Fraction of a viewport to move, 0.2-1.2. Default 0.7."}
        },"required":["direction"]}"""
    )

    val SCROLL_TO = tool(
        "scroll_to",
        "Scroll until the element with this text is in view (whole page searched).",
        """{"type":"object","properties":{
          "text":{"type":"string","description":"The words to find."}
        },"required":["text"]}"""
    )

    val PRESS_KEY = tool(
        "press_key",
        "Press a key on the focused element: enter, tab, escape, backspace, up, down, left, right, home, end, pageup, pagedown, or a single character.",
        """{"type":"object","properties":{
          "key":{"type":"string","description":"The key name."},
          "index":{"type":"integer","description":"Element to press it on. Default: the focused element."}
        },"required":["key"]}"""
    )

    val HOVER = tool(
        "hover",
        "Hover an element (menus and tooltips that open on mouseover).",
        """{"type":"object","properties":{"index":{"type":"integer","description":"The element's number."}},"required":["index"]}"""
    )

    val BACK = tool("back", "Go back in the tab's history.", """{"type":"object","properties":{}}""")
    val FORWARD = tool("forward", "Go forward in the tab's history.", """{"type":"object","properties":{}}""")
    val RELOAD = tool("reload", "Reload the current page.", """{"type":"object","properties":{}}""")

    val READ_PAGE = tool(
        "read_page",
        "Read the page's content, not just its controls: markdown (default, headings/links/tables kept), text, or links (every link with its URL). Use it to scrape copy, data or documentation; use selector to read one section.",
        """{"type":"object","properties":{
          "mode":{"type":"string","enum":["markdown","text","links"],"description":"Default markdown."},
          "selector":{"type":"string","description":"Read only this CSS selector's subtree, e.g. \"main\" or \".pricing-table\"."},
          "max_chars":{"type":"integer","description":"Maximum characters. Default 12000, max 40000."}
        }}"""
    )

    val FIND_TEXT = tool(
        "find_text",
        "Where these words are on the page right now: the matching elements with their numbers. No scroll.",
        """{"type":"object","properties":{"text":{"type":"string","description":"The words to find."}},"required":["text"]}"""
    )

    val WEB_SEARCH = tool(
        "web_search",
        "Search the web without any API key; returns titles, URLs and snippets. Use it before wandering: one search usually beats five clicks. Open a result with open_url, or read it with web_fetch when you only need its text.",
        """{"type":"object","properties":{
          "query":{"type":"string","description":"The search query, as words."},
          "limit":{"type":"integer","description":"Maximum results. Default 6."}
        },"required":["query"]}"""
    )

    val WAIT = tool(
        "wait",
        "Wait a moment for slow content (0.5-10 s). Prefer wait_for when you know what you are waiting for.",
        """{"type":"object","properties":{"seconds":{"type":"number","description":"How long. Default 2."}}}"""
    )

    val WAIT_FOR = tool(
        "wait_for",
        "Wait until something is true: text on the page (or gone), a CSS selector present (or gone), or a URL fragment. Returns as soon as it happens.",
        """{"type":"object","properties":{
          "text":{"type":"string","description":"Text to wait for."},
          "selector":{"type":"string","description":"CSS selector to wait for (instead of text)."},
          "url_contains":{"type":"string","description":"URL fragment to wait for (instead of text)."},
          "gone":{"type":"boolean","description":"Wait for it to disappear instead of appear. Default false."},
          "seconds":{"type":"number","description":"Timeout, 1-30. Default 10."}
        }}"""
    )

    val SCREENSHOT = tool(
        "screenshot",
        "Look at the page as an image (vision models only): the rendered viewport, or the whole page. The element listing stays the source of truth for indexes.",
        """{"type":"object","properties":{
          "full_page":{"type":"boolean","description":"Capture the whole scrollable page, not just the viewport. Default false."}
        }}"""
    )

    val EVALUATE = tool(
        "evaluate",
        "Run JavaScript in the page and get the result back (a power tool: data extraction, odd widgets, checking state). Return a JSON-serialisable value or a string; keep it small.",
        """{"type":"object","properties":{
          "js":{"type":"string","description":"JavaScript expression or IIFE. No 'javascript:' prefix."}
        },"required":["js"]}"""
    )

    val NEW_TAB = tool("new_tab", "Open a new tab (optionally at a URL) and switch to it.",
        """{"type":"object","properties":{"url":{"type":"string","description":"URL to open. Omit for a blank tab."}}}""")
    val LIST_TABS = tool("list_tabs", "List the open tabs with their ids, titles and URLs.", """{"type":"object","properties":{}}""")
    val SWITCH_TAB = tool("switch_tab", "Switch to another tab by id.",
        """{"type":"object","properties":{"id":{"type":"integer","description":"The tab's id from list_tabs."}},"required":["id"]}""")
    val CLOSE_TAB = tool("close_tab", "Close a tab by id.",
        """{"type":"object","properties":{"id":{"type":"integer","description":"The tab's id."}},"required":["id"]}""")

    val UPLOAD_FILE = tool(
        "upload_file",
        "Give a project file to a file input (<input type=file>): click it with the file ready, e.g. path \"images/logo.png\".",
        """{"type":"object","properties":{
          "index":{"type":"integer","description":"The file input's element number."},
          "path":{"type":"string","description":"File path inside the project."}
        },"required":["index","path"]}"""
    )

    val LIST_DOWNLOADS = tool("list_downloads", "List the files this browser downloaded during the run (csv, json, pdf...).",
        """{"type":"object","properties":{}}""")
    val READ_DOWNLOAD = tool(
        "read_download",
        "Read a downloaded text file (csv/json/txt/xml) back as text -- scraping data a page exports.",
        """{"type":"object","properties":{
          "name":{"type":"string","description":"The download's file name from list_downloads."},
          "max_chars":{"type":"integer","description":"Maximum characters. Default 20000."}
        },"required":["name"]}"""
    )

    val LIST_CREDENTIALS = tool(
        "list_credentials",
        "List the login credentials the user saved in Oro (id, site, username -- never passwords). Empty when the user saved none.",
        """{"type":"object","properties":{}}"""
    )

    val USE_CREDENTIAL = tool(
        "use_credential",
        "Fill a saved login into the current page's username+password fields inside the engine: the password never passes through this conversation, and the user approves first. For a site with no saved credential, use ask_user_secret.",
        """{"type":"object","properties":{
          "id":{"type":"string","description":"The credential's id from list_credentials."},
          "user_index":{"type":"integer","description":"Username field's element number, when auto-detect should not guess."},
          "pass_index":{"type":"integer","description":"Password field's element number, when auto-detect should not guess."}
        },"required":["id"]}"""
    )

    val ASK_USER_SECRET = tool(
        "ask_user_secret",
        "Ask the user to enter a secret (password, OTP, card number) THEMSELVES: Oro shows them a masked card and/or focuses the field in the live page, they type it in, and you only learn that it was entered. Use for any credential not in the vault, one-time codes, and payment details. Never ask for a secret in plaintext via ask_user.",
        """{"type":"object","properties":{
          "purpose":{"type":"string","description":"What the secret is for, shown to the user, e.g. \"your GitHub password\" or \"the 6-code from your authenticator\"."},
          "index":{"type":"integer","description":"Element number of the field to fill. Omit to let the user tap the field in the live page."}
        },"required":["purpose"]}"""
    )

    val ASK_USER = tool(
        "ask_user",
        "Ask the user a question and wait for the answer (shown on the browser screen and the floating pill). Use it when genuinely blocked: which of several identical items, an account to use, a choice the task left open. Never for permission the guard already asks, never for secrets (ask_user_secret).",
        """{"type":"object","properties":{
          "question":{"type":"string","description":"The question, short."},
          "options":{"type":"array","items":{"type":"string"},"description":"Up to 4 tap-to-answer options."}
        },"required":["question"]}"""
    )

    val TAKE_NOTE = tool(
        "take_note",
        "Remember a fact for later in this run (a price, an id, something found). Notes survive page changes and come back with the final report.",
        """{"type":"object","properties":{"note":{"type":"string","description":"The fact, one line."}},"required":["note"]}"""
    )

    val BROWSER_STATUS = tool(
        "browser_status",
        "The browser's state: tabs, current URL/title, loading, downloads, desktop mode.",
        """{"type":"object","properties":{}}"""
    )

    val FINISH_TASK = tool(
        "finish_task",
        "End the task with its outcome. success=true only when the task's goal is really met (the form is submitted, the data is in hand); otherwise success=false with what is missing. The summary is what the user (or the coding agent that sent you) reads: put the answer in it -- every value the task asked to report, in full.",
        """{"type":"object","properties":{
          "summary":{"type":"string","description":"What was done and found: the answer, the data, what is left."},
          "success":{"type":"boolean","description":"True only when the goal is met."}
        },"required":["summary","success"]}"""
    )

    val all: List<Tool> = listOf(
        OPEN_URL, CLICK, CLICK_TEXT, TYPE_TEXT, FILL_FORM, SELECT, CHECK,
        SCROLL, SCROLL_TO, PRESS_KEY, HOVER, BACK, FORWARD, RELOAD,
        READ_PAGE, FIND_TEXT, WEB_SEARCH, WAIT, WAIT_FOR, SCREENSHOT, EVALUATE,
        NEW_TAB, LIST_TABS, SWITCH_TAB, CLOSE_TAB,
        UPLOAD_FILE, LIST_DOWNLOADS, READ_DOWNLOAD,
        LIST_CREDENTIALS, USE_CREDENTIAL, ASK_USER_SECRET,
        ASK_USER, TAKE_NOTE, BROWSER_STATUS, FINISH_TASK,
    )

    val names: Set<String> = all.map { it.function.name }.toSet()

    /** Tools that count as no action: no target, no guard, no action counter. */
    val passive: Set<String> = setOf(
        "read_page", "find_text", "web_search", "wait", "wait_for", "screenshot", "browser_status",
        "list_tabs", "list_downloads", "read_download", "list_credentials",
        "take_note", "ask_user", "ask_user_secret", "finish_task",
    )

    /** What a run may still do while the user has it paused. */
    val allowedWhilePaused: Set<String> = setOf("ask_user", "take_note", "finish_task", "browser_status", "read_page")

    /** The tool set for one run: [vision] models also get the screenshot. */
    fun forRun(vision: Boolean): List<Tool> =
        if (vision) all else all.filter { it.function.name != "screenshot" }
}
