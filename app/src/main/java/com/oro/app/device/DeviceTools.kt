package com.oro.app.device

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.Tool

/**
 * The device agent's tool set. Small and orthogonal on purpose: weak models
 * choose well between twelve clear verbs and badly between forty. Elements
 * are addressed by the number shown in the screen listing; coordinates are
 * the fallback for UIs that expose no elements (games, maps, canvases).
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

    val TAP = tool("tap", "Tap an element (by index) or a screen point (x,y). Tapping a text taps its button/row.",
        """{"type":"object","properties":{$TARGET},"required":[]}""")

    val LONG_PRESS = tool("long_press", "Press and hold an element or point (opens context menus, selects items).",
        """{"type":"object","properties":{$TARGET},"required":[]}""")

    val DOUBLE_TAP = tool("double_tap", "Double-tap an element or point (zoom photos/maps, like posts).",
        """{"type":"object","properties":{$TARGET},"required":[]}""")

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

    val PRESS_KEY = tool(
        "press_key",
        "Press a system key: back, home, recents, notifications, quick_settings, enter, lock, screenshot.",
        """{"type":"object","properties":{
          "key":{"type":"string","enum":["back","home","recents","notifications","quick_settings","enter","lock","screenshot"]}
        },"required":["key"]}"""
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

    val SWIPE = tool(
        "swipe",
        "Swipe from (x1,y1) to (x2,y2) in pixels: carousels, stories, dismissing, unlocking sliders.",
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

    val OPEN_APP = tool("open_app", "Open an installed app by name (e.g. \"YouTube\", \"Settings\", \"camera\").",
        """{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}""")

    val OPEN_URL = tool(
        "open_url",
        "Open a link: web pages in the browser, or deep links (tel:, mailto:, geo:, market:, app links). Fastest way to search the web: open_url(\"https://www.google.com/search?q=...\").",
        """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}"""
    )

    val LIST_APPS = tool("list_apps", "List installed apps, optionally filtered by a word.",
        """{"type":"object","properties":{"query":{"type":"string"}},"required":[]}""")

    val FIND_TEXT = tool(
        "find_text",
        "Find elements on the current screen whose text contains a phrase (case-insensitive). Use before scrolling to check if something is already visible.",
        """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}"""
    )

    val READ_SCREEN = tool("read_screen", "Look at the screen again (after waiting for content to load, or to see it in full).",
        """{"type":"object","properties":{},"required":[]}""")

    val WAIT = tool("wait", "Wait for loading/animations, 1-10 seconds, then look at the screen.",
        """{"type":"object","properties":{"seconds":{"type":"number"}},"required":["seconds"]}""")

    val TAKE_NOTE = tool(
        "take_note",
        "Save a fact you found (price, code, name, address) so you still have it after the screen changes. Notes are shown with every screen.",
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

    val all: List<Tool> = listOf(
        TAP, TYPE_TEXT, SCROLL, PRESS_KEY, OPEN_APP, OPEN_URL, LONG_PRESS, DOUBLE_TAP, SWIPE, DRAG, PINCH,
        FIND_TEXT, READ_SCREEN, WAIT, LIST_APPS, TAKE_NOTE, ASK_USER, FINISH
    )

    val names: Set<String> = all.map { it.function.name }.toSet()

    /** Tools that do not touch the device; they may run while paused. */
    val passive = setOf("find_text", "read_screen", "list_apps", "take_note", "ask_user", "finish_task")
}
