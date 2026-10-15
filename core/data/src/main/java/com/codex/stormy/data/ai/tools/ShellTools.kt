package com.codex.stormy.data.ai.tools

import com.codex.stormy.data.ai.AiHttp
import com.codex.stormy.data.ai.FunctionDefinition
import com.codex.stormy.data.ai.Tool
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
                Run a shell command in the project directory.

                Use it for things the file tools cannot do: installing
                dependencies, running a build or a test suite, inspecting git
                state, checking whether a tool is available.

                Refused, by design: anything that deletes (rm, rmdir), changes
                privileges (sudo, su), touches devices or partitions, kills
                processes, or opens a network shell (ssh, nc, telnet). Delete
                files with delete_file instead, which is undoable. Commands run
                against an allowlist of programs -- git, npm, node, python,
                gradle, the usual text utilities -- so an unrecognised program
                is refused rather than attempted.

                The working directory is always the project root and cannot be
                changed; a leading `cd` will not do what you expect. Output is
                truncated, so prefer a command that prints what you need over
                one that prints everything.

                Commands are not interactive. Anything that waits for input
                will hit the timeout and be killed.
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
