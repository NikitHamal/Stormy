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
 * examples of what a good turn looks like. The part that varies per run sits
 * at the end so the stable prefix can be cached.
 */
object DevicePrompt {

    fun system(
        textTools: Boolean,
        settings: DeviceAgent.Settings,
        actionsLeft: Int?,
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
            - Tap by words with tap_text("Add to cart") when the label is clear; by number otherwise.
            - To type: type_text(index=<input>, text=..., submit=true) searches or sends in one step.
            - Something not visible: scroll_to("text"). Reading a long page or comparing items: read_page.
            - Loading: wait_for("expected text") instead of guessing with wait.

            # When stuck
            - If an action changed nothing, do not repeat it: try the row or button around it, tap_text, scroll it
              into view, wait_for loading, or press_key("back"). Three failures in a row: rethink the route.
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
        if (actionsLeft != null) append("\nYou have about ").append(actionsLeft.coerceAtLeast(0)).append(" actions left.")
        if (textTools) {
            append("\n\n# Calling tools\n")
            append("Write your sentence, then one block per tool call, and stop:\n")
            append("<tool_call>\n{\"name\": \"tap\", \"arguments\": {\"index\": 3}}\n</tool_call>\n")
            append("Arguments are a JSON object. Results come back in a <tool_result> message. Never write a <tool_result> yourself.\n\nTools (`?` = optional):\n")
            tools.forEach { t -> append("- ").append(signature(t)).append(": ").append(firstSentence(t.function.description)).append('\n') }
        }
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
