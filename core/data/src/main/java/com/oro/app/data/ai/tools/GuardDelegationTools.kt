package com.oro.app.data.ai.tools

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.Tool

/**
 * The bridge from the coding agent to the Shield.
 *
 * use_browser proved the shape: one tool, a task, and a specialist agent on
 * the other side that reports back. ask_guard is the same bridge into the
 * personal cyber-defence agent -- the coding agent consults it instead of
 * improvising security verdicts in chat, and the guard answers with its own
 * tools and, when the question deserves it, its council of models.
 */
object GuardDelegationTools {

    const val ASK_GUARD_NAME = "ask_guard"

    val ASK_GUARD = Tool(
        function = FunctionDefinition(
            name = ASK_GUARD_NAME,
            description = "Consult the Shield, Oro's personal cyber-defence agent, and wait for its verdict. " +
                "Use it for security questions that deserve a specialist: judge a URL or mail header, check whether a " +
                "dependency or CVE matters, audit this project for leaked secrets, weigh an incident ('the user says " +
                "they were breached'), or get a council opinion. Give it a self-contained brief with the evidence " +
                "quoted in. It reports confirmed vs suspected, and it never needs -- and must never be given -- a " +
                "plaintext password. Its screen shows the run live.",
            parameters = AiHttp.json.parseToJsonElement(
                """
                {"type":"object","properties":{
                  "brief":{"type":"string","description":"The security question plus all the evidence, self-contained: what to judge, what the user is worried about, what counts as an answer."}
                },"required":["brief"]}
                """.trimIndent()
            )
        )
    )
}
