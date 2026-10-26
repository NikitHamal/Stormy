package com.oro.app.data.ai.tools

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.Tool

/**
 * The coding agent's door to the web: hand a task to Oro's browser agent
 * mid-run and get back what it did and found.
 *
 * Unlike the old device delegation, this needs no permission and no service:
 * the browser runs inside Oro (an in-app WebView engine), so the tool is
 * always on offer. The browser agent runs the task with its own page-reading
 * loop and the user's browser settings (approvals for paying/sending,
 * Pause/Stop on screen, secrets that never reach the model), while the user
 * watches live, minimises it, or switches chats -- the run continues in the
 * background either way.
 */
object BrowserDelegationTools {

    const val USE_BROWSER_NAME = "use_browser"

    val USE_BROWSER = Tool(
        type = "function",
        function = FunctionDefinition(
            name = USE_BROWSER_NAME,
            description = """
                Hand a task to Oro's browser agent, a real in-app browser it drives like a person (opens sites,
                clicks, fills whole forms in one call, reads pages, searches the web, downloads files) and returns
                what it did and found. Use it for work on the live web: test the site you built in a real browser
                like a visitor would (open_preview=true), sign into a service, fill or submit a form, check a page
                or a price, scrape data or copy from a website, or research anything that needs more than one
                fetch. The user can watch it live or minimise it; the task keeps running in the background and
                they can stop it. Logins and payments still ask them first, human checks (captchas) are always
                theirs to pass, and passwords never travel through you -- ask the browser agent to use saved
                credentials or to let the user type a secret in. Describe the goal and exactly what to report back.
            """.trimIndent(),
            parameters = AiHttp.json.parseToJsonElement(
                """
                {"type":"object","properties":{
                  "task":{"type":"string","description":"What to do in the browser, with every detail needed (site, pages, form values that are not secret, what counts as done)."},
                  "report":{"type":"string","description":"What to bring back, e.g. \"the price and delivery date\", \"every row of the table\", or \"any layout or console problems you see\"."},
                  "url":{"type":"string","description":"Start at this http(s) URL instead of a blank tab."},
                  "open_preview":{"type":"boolean","description":"Start by opening this project's live preview (the dev server is started for you). Default false."}
                },"required":["task"]}
                """.trimIndent()
            )
        )
    )
}
