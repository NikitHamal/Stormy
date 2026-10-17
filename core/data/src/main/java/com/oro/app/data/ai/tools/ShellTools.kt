package com.oro.app.data.ai.tools

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.Tool
import kotlinx.serialization.json.JsonElement

/**
 * The shell tool.
 *
 * [ShellToolExecutor] already implemented the hard part -- an allowlist of
 * permitted programs, a blocklist of destructive ones, and pattern checks for
 * the shell-injection shapes that get past both -- but nothing exposed it to
 * the model, so the capability existed and was unreachable.
 *
 * The description tells the model what is *refused* as well as what is allowed.
 * Without that it spends turns discovering the boundary one rejection at a
 * time, and a refusal it cannot predict reads to the user as the agent being
 * broken rather than being careful.
 */
object ShellTools {

    val RUN_COMMAND = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "run_command",
            description = """
                Run a command in the project directory using the phone's
                Android shell (toybox). Rarely needed: the file tools cover
                reading, searching and editing.

                Available: basic text utilities (ls, cat, head, tail, wc, sort,
                find, grep, sed, diff, du, date). NOT available on the device:
                node, npm, npx, python, pip, build tools or package managers --
                do not try to install dependencies. Oro projects are plain
                HTML/CSS/JS that run directly in the live preview; load
                libraries from a CDN instead.

                Refused, by design: deleting (use delete_file, which is
                undoable), privilege changes, device access, killing processes,
                network shells. The working directory is always the project
                root. Commands are not interactive and are killed at the
                timeout.
            """.trimIndent(),
            parameters = parameters(
                """
                {
                  "type": "object",
                  "properties": {
                    "command": {
                      "type": "string",
                      "description": "The command line to run, e.g. \"npm test\" or \"git status --short\"."
                    },
                    "timeout_seconds": {
                      "type": "integer",
                      "description": "How long to allow before the command is killed. Defaults to 30, capped at 300."
                    }
                  },
                  "required": ["command"]
                }
                """
            )
        )
    )

    private fun parameters(schema: String): JsonElement =
        AiHttp.json.parseToJsonElement(schema.trimIndent())
}
