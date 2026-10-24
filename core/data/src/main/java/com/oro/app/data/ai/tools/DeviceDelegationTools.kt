package com.oro.app.data.ai.tools

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.Tool

/**
 * The coding agent's door to the phone: hand a task to Oro's device agent
 * mid-run and get back what it did and found.
 *
 * Offered only while the Oro Device Agent accessibility service is on, so a
 * model never sees a tool it cannot use. The device agent runs the task with
 * its own screen-reading loop and the user's device settings (approvals for
 * sending/paying, Pause/Stop on screen), then Oro returns to the chat and the
 * summary comes back as this tool's result.
 */
object DeviceDelegationTools {

    const val USE_PHONE_NAME = "use_phone"

    val USE_PHONE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = USE_PHONE_NAME,
            description = """
                Hand a task to Oro's device agent, which operates this Android phone like a person (opens apps
                and websites, taps, types, scrolls, reads the screen) and returns what it did and found.
                Use it for things outside the project files: test the site you built in a real browser like a
                user would (open_preview=true), check a real website or app, look up information that needs an
                app, or gather content for the project. The user sees it happen and can stop it; sending,
                paying and deleting still ask them first. Describe the goal and exactly what to report back.
            """.trimIndent(),
            parameters = AiHttp.json.parseToJsonElement(
                """
                {"type":"object","properties":{
                  "task":{"type":"string","description":"What to do on the phone, with every detail needed (names, search terms, what counts as done)."},
                  "report":{"type":"string","description":"What to bring back, e.g. \"the price and delivery date\" or \"any layout or console problems you see\"."},
                  "open_preview":{"type":"boolean","description":"Start by opening this project's live preview in the phone's browser. Default false."}
                },"required":["task"]}
                """.trimIndent()
            )
        )
    )
}
