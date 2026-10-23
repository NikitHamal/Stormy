package com.oro.app.device

import com.oro.app.data.ai.Tool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * System prompt for the device agent.
 *
 * Written to be followed by small models: short imperative rules, the fast
 * paths named explicitly (most failed runs were a model hunting through a
 * home screen for something one intent would have done), and two worked
 * examples of what a good turn looks like. Nothing in it changes during a run
 * (the actions left are reported with each screen), so providers can cache it.
 */
object DevicePrompt {

    fun system(
        textTools: Boolean,
        settings: DeviceAgent.Settings,
        tools: List<Tool>,
        vision: Boolean,
        fromChat: Boolean,
    ): String = buildString {
        append(
            """
            You are Orobi, an agent that operates the user's Android phone to complete their task. You see the
            screen as a numbered list of elements and act with tools. The user watches and can pause or stop you.

            # Each turn
            1. Read the screen: which app, what is on it, did your last action work ("Screen changed" or
               "⚠ The screen did not change")?
            2. Write ONE short sentence: what you see, what you do next and why.
            3. Call the tool. Several calls in one turn only when they are a fixed sequence on THIS screen
               (e.g. type_text then tap "Search"); the app skips a call whose target disappeared.

            # Fast paths first
            - Apps: open_app("YouTube"). Websites and searches: open_url("https://www.google.com/search?q=...").
            - YouTube: open_url("https://www.youtube.com/results?search_query=...") then tap the video. Ads are
              skipped automatically when skippable. media("next"/"pause"/"forward") controls playback; in Shorts
              swipe up for the next video.
            - Alarms, timers, directions, calls, texts, emails, calendar events, settings pages: quick_action.
              WhatsApp someone by name: open_app("WhatsApp"), open their chat (search if needed), type, tap Send.
              quick_action(whatsapp, to="+<country code> number", text=...) only when you have their number.
              Find a place: maps. An app in the store: store. Wi-Fi/data/volume toggles over the current app:
              panel. One app's notifications or permissions: app_info. Flashlight and volume: their own actions.
            - Notifications: press_key("notifications") opens the shade; read them there.
            - Tap by words with tap_text("Add to cart") when the label is clear; by number otherwise.
            - To type: type_text(index=<input>, text=..., submit=true) searches or sends in one step.
            - Something not visible: scroll_to("text"). Reading a long page or comparing items: read_page.
            - Loading: wait_for("expected text") instead of guessing with wait.

            # Reading the screen
            - A list says "more below" while there is more to see; without it you are at its end.
            - Icons with no name show where they sit, e.g. (top right): menus are usually top right, back top left.
            - "under keyboard" means the open keyboard covers it: press_key("back") closes the keyboard first.
            - "Hint:" lines name what to handle first (sign-in, captcha, error, popup, consent banner).

            # When stuck
            - If an action changed nothing, do not repeat it: try the row or button around it, tap_text, scroll it
              into view, wait_for loading, or press_key("back"). Three failures in a row: rethink the route.
            - "Skipped" means nothing was done because the screen changed first: decide again from the screen shown.
            - Popups, cookie banners, "rate us", sign-in nags: dismiss or decline unless the task needs them.
            - Logins, payments, personal choices (which contact, which card, which account): ask_user.
            - Never type passwords, card numbers or one-time codes the user did not give you.
            - Stay on task: no unrelated apps, purchases or settings changes.

            # Examples
            Task: "Play lofi music on YouTube"
            Turn 1: Home screen; I'll search YouTube directly. → open_url("https://www.youtube.com/results?search_query=lofi+music")
            Turn 2: Results are showing; the first video fits. → tap_text("lofi hip hop radio")
            Turn 3: The video is playing. → finish_task("Playing lofi hip hop radio on YouTube.", success=true)

            Task: "What's the cheapest flight to Delhi next Friday on Google?"
            Turn 1: → open_url("https://www.google.com/search?q=flights+to+Delhi+next+Friday")
            Turn 2: Results loaded with prices. → read_page(pages=2)
            Turn 3: Cheapest is ₹4,120 on IndiGo 6:05. → finish_task("Cheapest: IndiGo 6:05, ₹4,120 (Google Flights).", success=true)

            # Finishing
            Verify on screen that the goal really happened (message visible in the chat, alarm in the list,
            video playing), then call finish_task(summary, success=true). If the task asked for information,
            put the answer in the summary. If it is impossible (app missing, login required, blocked), call
            finish_task with success=false, say why and what the user can do.
            """.trimIndent()
        )
        if (vision) {
            append("\n\n# Seeing\nlook() returns a screenshot with the element numbers drawn on it. Use it for images, ")
            append("videos, games, maps and anything the listing does not describe. The floating Orobi pill in it is yours; ignore it.")
        }
        if (fromChat) {
            append("\n\n# Context\nThe coding agent in Oro handed you this task mid-conversation. Finish with a summary ")
            append("that gives it exactly what it asked for (facts, values, what you saw); take_note anything worth returning.")
        }
        if (settings.confirmSensitive) {
            append("\n\nSending, paying, deleting and similar actions ask the user for approval automatically; just call the tool.")
        }
        if (textTools) {
            append("\n\n# Calling tools\n")
            append("Write your sentence, then one block per tool call, and stop:\n")
            append("<tool_call>\n<function=tap>\n<parameter=index>3</parameter>\n</function>\n</tool_call>\n")
            append("Values are raw text: no quotes, no escaping. Results come back in a <tool_result> message. ")
            append("Never write a <tool_result> yourself.\n\nTools (`?` = optional):\n")
            tools.forEach { t -> append("- ").append(signature(t)).append(": ").append(firstSentence(t.function.description)).append('\n') }
        }
    }

    private val COMPARING = Regex(
        """\b(cheapest|lowest|highest|least|most|best|top[- ]rated|compare|comparing|every|all of|all the|all my|largest|smallest|newest|oldest|fastest|longest|shortest)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Whether [task] picks among items (cheapest, best, all…), so a list must be read to its end. Pure. */
    fun comparesItems(task: String): Boolean = COMPARING.containsMatchIn(task)

    /**
     * Names from [task] (outside quoted text, which is a message to type) that
     * start tappable labels with two or more different surnames on [screen]:
     * "Priya" with "Priya Sharma" and "Priya Rao" both in a chat list. The
     * live eval saw a model message the first match instead of asking which
     * one. Only a name standing alone counts: "Blinding" in "Blinding Lights"
     * is part of a title, and "Blinding Lights (Lyrics)" and "Blinding Lights |
     * Piano Cover" are results to choose from, not people to ask about. Pure.
     */
    fun ambiguousNames(task: String, screen: ScreenState): List<Pair<String, List<String>>> {
        val outsideQuotes = task.replace(Regex("""["“”'‘’][^"“”'‘’]*["“”'‘’]"""), " ")
        val words = Regex("""[\p{L}\p{N}]+""").findAll(outsideQuotes).toList()
        // Capitalised mid-sentence; the first word of a sentence ("Message Priya…") is capitalised anyway.
        fun capital(i: Int): Boolean {
            val w = words.getOrNull(i) ?: return false
            if (w.value.length < 2 || !w.value[0].isUpperCase()) return false
            val before = outsideQuotes.substring(0, w.range.first).trimEnd()
            return before.isNotEmpty() && before.last() !in ".!?:"
        }
        val names = words.indices.filter { i ->
            val w = words[i].value
            w.length >= 3 && w[0].isUpperCase() && w.drop(1).all { it.isLowerCase() } && !capital(i - 1) && !capital(i + 1)
        }.map { words[it].value }.toSet()
        if (names.isEmpty()) return emptyList()
        val labels = screen.elements.filter { it.clickable && it.label.isNotBlank() }.map { it.label.substringBefore(" · ").trim() }.distinct()
        return names.mapNotNull { name ->
            if (labels.any { it.equals(name, true) }) return@mapNotNull null
            val hits = labels.filter { l -> l.startsWith("$name ", ignoreCase = true) && l.getOrNull(name.length + 1)?.isUpperCase() == true }
            val surnames = hits.map { it.substring(name.length + 1).substringBefore(' ') }.toSet()
            // Named in full in the task ("Priya Rao"): nothing to ask.
            val named = hits.any { outsideQuotes.contains(it, ignoreCase = true) }
            if (surnames.size >= 2 && !named) name to hits.take(4) else null
        }
    }

    /**
     * Whether two questions to the user ask the same thing: most of the
     * shorter one's words (four letters or more) are in the other. Pure.
     */
    fun sameQuestion(a: String, b: String): Boolean {
        fun words(s: String) = Regex("""\p{L}{4,}""").findAll(s.lowercase()).map { it.value }.toSet()
        val x = words(a)
        val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return a.trim().equals(b.trim(), ignoreCase = true)
        return (x intersect y).size >= 0.6 * minOf(x.size, y.size)
    }

    /** `tap(index?: int, x?: int, y?: int)`, from the schema. */
    internal fun signature(tool: Tool): String {
        val schema = tool.function.parameters as? JsonObject
        val props = schema?.get("properties") as? JsonObject ?: return tool.function.name + "()"
        val required = (schema["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty().toSet()
        val fields = props.entries.joinToString(", ") { (name, spec) ->
            val obj = spec as? JsonObject
            val enum = (obj?.get("enum") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
            val typeName = (obj?.get("type") as? JsonPrimitive)?.content
            val type = when {
                enum != null -> enum.joinToString("|") { "\"$it\"" }
                typeName == "integer" -> "int"
                typeName == "array" -> "[string]"
                else -> typeName ?: "any"
            }
            name + (if (name in required) "" else "?") + ": " + type
        }
        return tool.function.name + "(" + fields + ")"
    }

    private fun firstSentence(description: String): String {
        val flat = description.replace(Regex("\\s+"), " ").trim()
        val end = Regex("""(?<!\be\.g)(?<!\bi\.e)\.\s+(?=[A-Z])""").find(flat)?.range?.first ?: flat.length
        return flat.substring(0, end).trim().removeSuffix(".") + "."
    }
}
