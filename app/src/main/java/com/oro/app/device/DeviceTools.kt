package com.oro.app.device

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.Tool

/**
 * The device agent's tool set.
 *
 * Ordered by how often a task needs them, because weak models favour the
 * first verbs they read. Elements are addressed by the number in the screen
 * listing, or by their visible words (tap_text) -- small models copy words
 * more reliably than numbers. Coordinates are the fallback for UIs with no
 * elements (games, maps, canvases).
 *
 * Shortcuts (quick_action, open_url, media) come before UI navigation in
 * spirit: one intent instead of eight taps is faster, cheaper and works the
 * same on every phone.
 */
object DeviceTools {

    private fun tool(name: String, description: String, schema: String) = Tool(
        function = FunctionDefinition(name, description.trimIndent(), AiHttp.json.parseToJsonElement(schema.trimIndent()))
    )

    private const val TARGET = """
          "index":{"type":"integer","description":"Element number from the screen listing (preferred)."},
          "x":{"type":"integer","description":"Screen x in pixels, only if no element fits."},
          "y":{"type":"integer","description":"Screen y in pixels, only if no element fits."}
    """

    val TAP = tool("tap", "Tap an element (by index) or a screen point (x,y). Tapping a text taps its button or row.",
        """{"type":"object","properties":{$TARGET},"required":[]}""")

    val TAP_TEXT = tool(
        "tap_text",
        "Tap the element showing this text (case-insensitive, close matches allowed). If several match, you get their numbers to choose from.",
        """{"type":"object","properties":{
          "text":{"type":"string","description":"The words shown on the button, row or link."},
          "nth":{"type":"integer","description":"Which match, in reading order, when several show the same text. Default: refuse and list them."}
        },"required":["text"]}"""
    )

    val TYPE_TEXT = tool(
        "type_text",
        "Type into a text field: the field at `index`, else the focused one. Replaces its text unless append=true. submit=true presses the keyboard's Enter/Search/Send afterwards.",
        """{"type":"object","properties":{
          "text":{"type":"string","description":"The text to type."},
          "index":{"type":"integer","description":"Input element number. Omit to use the focused field."},
          "append":{"type":"boolean","description":"Add to existing text instead of replacing. Default false."},
          "submit":{"type":"boolean","description":"Press Enter/Search/Go after typing. Default false."}
        },"required":["text"]}"""
    )

    val SCROLL = tool(
        "scroll",
        "Scroll the screen or a list. direction=down shows content further down (like swiping up with a finger).",
        """{"type":"object","properties":{
          "direction":{"type":"string","enum":["down","up","left","right"]},
          "index":{"type":"integer","description":"List/element to scroll. Omit for the main list."},
          "amount":{"type":"number","description":"Fraction of the list to move, 0.2-0.9. Default 0.6."}
        },"required":["direction"]}"""
    )

    val SCROLL_TO = tool(
        "scroll_to",
        "Scroll until an element with this text is on screen (up to 10 scrolls), then show the screen. Stops at the end of the list.",
        """{"type":"object","properties":{
          "text":{"type":"string"},
          "direction":{"type":"string","enum":["down","up","left","right"],"description":"Default down."},
          "index":{"type":"integer","description":"List to scroll. Omit for the main list."}
        },"required":["text"]}"""
    )

    val PRESS_KEY = tool(
        "press_key",
        "Press a system key: back, home, recents, notifications, quick_settings, enter, lock, screenshot.",
        """{"type":"object","properties":{
          "key":{"type":"string","enum":["back","home","recents","notifications","quick_settings","enter","lock","screenshot"]}
        },"required":["key"]}"""
    )

    val OPEN_APP = tool("open_app", "Open an installed app by name (e.g. \"YouTube\", \"Settings\", \"camera\").",
        """{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}""")

    val OPEN_URL = tool(
        "open_url",
        "Open a link: web pages in the browser, app links (YouTube, Maps, stores open in their apps), or tel:, mailto:, geo: links. Fastest way to search: https://www.google.com/search?q=... or https://www.youtube.com/results?search_query=...",
        """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}"""
    )

    val QUICK_ACTION = tool(
        "quick_action",
        "Do a common task in one step instead of navigating screens: alarm (time, text=label), timer (minutes), search (text), navigate (text=place), maps (text=place to find), settings (page), panel (page=internet|wifi|nfc|volume: toggles over the current app), app_info (text=app: its notifications, permissions, storage), dial (to), sms (to, text), whatsapp (to=number with country code, text), email (to, subject, text), share (text), event (text=title, time, minutes), music (text), store (text=app), flashlight (text=on|off), volume (text=up|down|mute|40). Nothing is sent or called without a later tap.",
        """{"type":"object","properties":{
          "action":{"type":"string","enum":["alarm","timer","search","navigate","maps","settings","panel","app_info","dial","sms","whatsapp","email","share","event","music","store","flashlight","volume"]},
          "text":{"type":"string","description":"Query, message, place, title, label, app name or value, depending on the action."},
          "to":{"type":"string","description":"Phone number (with country code for WhatsApp) or email address."},
          "subject":{"type":"string","description":"Email subject."},
          "time":{"type":"string","description":"\"07:30\", \"7:30 pm\", \"tomorrow 9am\" or \"2026-10-23 15:00\"."},
          "minutes":{"type":"number","description":"Timer length or event duration in minutes."},
          "page":{"type":"string","description":"Settings page: wifi, bluetooth, display, sound, battery, apps, location, notifications, airplane, data, date, language, storage, dnd, about…"}
        },"required":["action"]}"""
    )

    val MEDIA = tool(
        "media",
        "Control what is playing (YouTube, Spotify, any player): play, pause, play_pause, next, previous, forward (+10 s), rewind (-10 s), stop.",
        """{"type":"object","properties":{
          "action":{"type":"string","enum":["play","pause","play_pause","next","previous","forward","rewind","stop"]}
        },"required":["action"]}"""
    )

    val LONG_PRESS = tool("long_press", "Press and hold an element or point (opens context menus, selects items).",
        """{"type":"object","properties":{$TARGET},"required":[]}""")

    val DOUBLE_TAP = tool("double_tap", "Double-tap an element or point (zoom photos/maps, like posts).",
        """{"type":"object","properties":{$TARGET},"required":[]}""")

    val SWIPE = tool(
        "swipe",
        "Swipe from (x1,y1) to (x2,y2) in pixels: carousels, stories, short videos (swipe up for the next), dismissing, sliders.",
        """{"type":"object","properties":{
          "x1":{"type":"integer"},"y1":{"type":"integer"},"x2":{"type":"integer"},"y2":{"type":"integer"},
          "duration_ms":{"type":"integer","description":"Default 350. Longer is slower."}
        },"required":["x1","y1","x2","y2"]}"""
    )

    val DRAG = tool(
        "drag",
        "Press, hold and drag from one element/point to another (reorder, move icons, sliders).",
        """{"type":"object","properties":{
          "from_index":{"type":"integer"},"to_index":{"type":"integer"},
          "x1":{"type":"integer"},"y1":{"type":"integer"},"x2":{"type":"integer"},"y2":{"type":"integer"}
        },"required":[]}"""
    )

    val PINCH = tool(
        "pinch",
        "Two-finger pinch at a point: direction=out zooms in, direction=in zooms out.",
        """{"type":"object","properties":{
          "x":{"type":"integer"},"y":{"type":"integer"},
          "direction":{"type":"string","enum":["in","out"]}
        },"required":["direction"]}"""
    )

    val READ_SCREEN = tool("read_screen", "Look at the screen again (after content loaded, or to see it in full).",
        """{"type":"object","properties":{},"required":[]}""")

    val READ_PAGE = tool(
        "read_page",
        "Read the text of a long page (article, results, chat, reviews) by scrolling through it, up to `pages` screens. Use to extract or compare information.",
        """{"type":"object","properties":{
          "pages":{"type":"integer","description":"Screens to read, 1-8. Default 3."}
        },"required":[]}"""
    )

    val WAIT = tool("wait", "Wait for loading or animations, 1-10 seconds, then look at the screen.",
        """{"type":"object","properties":{"seconds":{"type":"number"}},"required":["seconds"]}""")

    val WAIT_FOR = tool(
        "wait_for",
        "Wait until a text appears on screen (or disappears with gone=true), up to `seconds` (default 10). Faster than guessing with wait.",
        """{"type":"object","properties":{
          "text":{"type":"string"},
          "gone":{"type":"boolean","description":"Wait for the text to disappear instead. Default false."},
          "seconds":{"type":"number","description":"Longest wait, 1-30. Default 10."}
        },"required":["text"]}"""
    )

    val LOOK = tool(
        "look",
        "See a screenshot of the screen with element numbers drawn on it. Use for images, videos, games, maps or anything the listing does not describe.",
        """{"type":"object","properties":{},"required":[]}"""
    )

    val DEVICE_STATUS = tool("device_status", "Current date and time, battery, network and sound mode.",
        """{"type":"object","properties":{},"required":[]}""")

    val LIST_APPS = tool("list_apps", "List installed apps, optionally filtered by a word.",
        """{"type":"object","properties":{"query":{"type":"string"}},"required":[]}""")

    val FIND_TEXT = tool(
        "find_text",
        "Find elements on the current screen whose text contains a phrase (case-insensitive).",
        """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}"""
    )

    val TAKE_NOTE = tool(
        "take_note",
        "Save a fact you found (price, code, name, address) so you still have it after the screen changes. Notes are shown with every screen and returned with the result.",
        """{"type":"object","properties":{"note":{"type":"string"}},"required":["note"]}"""
    )

    val ASK_USER = tool(
        "ask_user",
        "Ask the user something only they know (which account, which contact, a choice between options). Pauses until they answer.",
        """{"type":"object","properties":{
          "question":{"type":"string"},
          "options":{"type":"array","items":{"type":"string"},"description":"Optional short answers to choose from."}
        },"required":["question"]}"""
    )

    val FINISH = tool(
        "finish_task",
        "End the task. Call this when the goal is achieved (success=true) or truly impossible (success=false). The summary is shown to the user: say what you did and any result they asked for.",
        """{"type":"object","properties":{
          "summary":{"type":"string","description":"What was done and the answer/result, in one to three sentences."},
          "success":{"type":"boolean"}
        },"required":["summary","success"]}"""
    )

    /** Offered to every model. find_text still runs if called, but tap_text and scroll_to cover it. */
    private val core: List<Tool> = listOf(
        TAP, TAP_TEXT, TYPE_TEXT, SCROLL, SCROLL_TO, PRESS_KEY, OPEN_APP, OPEN_URL, QUICK_ACTION, MEDIA,
        LONG_PRESS, DOUBLE_TAP, SWIPE, DRAG, PINCH,
        READ_SCREEN, READ_PAGE, WAIT, WAIT_FOR, DEVICE_STATUS, LIST_APPS, TAKE_NOTE, ASK_USER, FINISH
    )

    /** The tools for a run: [vision] adds look (screenshots), for models that can see. */
    fun forRun(vision: Boolean): List<Tool> = if (vision) core.take(16) + LOOK + core.drop(16) else core

    val all: List<Tool> = forRun(vision = true)

    /** Every name the executor understands, for text-call recovery. */
    val names: Set<String> = (all + FIND_TEXT).map { it.function.name }.toSet()

    /** Tools that do not touch the device: they never count as actions. */
    val passive = setOf(
        "find_text", "read_screen", "list_apps", "take_note", "ask_user", "finish_task", "device_status", "look", "wait_for"
    )

    /**
     * The only tools that run while the user has paused the run. Pause is when
     * people type a code or a card number by hand, so nothing that reads the
     * screen (read_screen, find_text, look, wait_for) may run then either.
     */
    val allowedWhilePaused = setOf("take_note", "ask_user", "finish_task", "device_status", "list_apps")
}
