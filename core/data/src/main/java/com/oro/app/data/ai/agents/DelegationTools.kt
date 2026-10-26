package com.oro.app.data.ai.agents

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.Tool
import kotlinx.serialization.json.JsonElement

/**
 * Tool definitions for delegation and plan tracking.
 *
 * The descriptions are written for the model, and they are deliberately blunt
 * about cost. A fresh subagent is not free: it knows only what the prompt
 * carries, it returns only a summary, and both handoffs lose detail. Without
 * that stated, models delegate trivial lookups they could have done inline --
 * which is slower, more expensive, and less accurate than not delegating.
 */
object DelegationTools {

    val SPAWN_SUBAGENT = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "spawn_subagent",
            description = """
                Delegate a self-contained piece of work to a fresh agent with
                its own context.

                Worth it when the work means reading across many files and you
                only need the conclusion. Not worth it for a handful of tool
                calls or a lookup whose target you already know -- the subagent
                starts with nothing but your prompt, and returns only a summary,
                so both handoffs drop detail.

                Brief it like a peer: state the goal, what you have already
                ruled out, and which files are worth reading. That prompt is the
                only context it gets. If you cannot write a clear brief, you do
                not understand the task well enough to hand it off.

                The call returns the subagent's summary when it finishes.
                Explore, plan and review subagents started in one turn run side
                by side (and beside your reads); build and general ones run alone.

                Types: explore (read-only search), plan (research, no edits),
                build (implement a scoped change), review (find defects),
                general (anything else). All but review may also research on
                the web through the in-app browser (use_browser).
            """.trimIndent(),
            parameters = buildParameters(
                """
                {
                  "type": "object",
                  "properties": {
                    "agent_type": {
                      "type": "string",
                      "enum": ["explore", "plan", "build", "review", "general"],
                      "description": "Which subagent type to use."
                    },
                    "description": {
                      "type": "string",
                      "description": "Three to five words naming the task. Shown to the user as the label on the running agent's card."
                    },
                    "prompt": {
                      "type": "string",
                      "description": "The full brief. The subagent sees only this -- include the goal, relevant file paths, and what you have already established."
                    }
                  },
                  "required": ["agent_type", "description", "prompt"]
                }
                """
            )
        )
    )

    val UPDATE_PLAN = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "update_plan",
            description = """
                Publish or update your plan for the current request, so the user
                can see where you are.

                Use it for work with several distinct steps. Keep it to a few
                items -- a long checklist is one nobody reads, and steps that
                are obvious from the code are padding. Mark exactly one task
                active at a time, and close it before opening the next.

                If you skip or get blocked on a task, say so with a reason. A
                plan that silently drops a step leaves the user believing it
                happened.
            """.trimIndent(),
            parameters = buildParameters(
                """
                {
                  "type": "object",
                  "properties": {
                    "tasks": {
                      "type": "array",
                      "description": "The full task list, in order. Send the whole list each time, not a delta.",
                      "items": {
                        "type": "object",
                        "properties": {
                          "title": { "type": "string", "description": "Short imperative phrase." },
                          "state": {
                            "type": "string",
                            "enum": ["pending", "active", "done", "blocked", "skipped"]
                          },
                          "note": { "type": "string", "description": "Required when blocked or skipped: why." }
                        },
                        "required": ["title", "state"]
                      }
                    }
                  },
                  "required": ["tasks"]
                }
                """
            )
        )
    )

    val REQUEST_APPROVAL = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "request_approval",
            description = """
                Ask the user before doing something hard to reverse or
                outward-facing -- deleting files you did not create, force
                pushing, overwriting work you did not write.

                Describe the action in plain language, as a consequence rather
                than a command: "Delete 4 files under src/legacy", not a JSON
                payload. Approval for one action does not extend to the next.

                Do not use this as a checkpoint on ordinary work. A prompt the
                user did not need teaches them to approve without reading.
            """.trimIndent(),
            parameters = buildParameters(
                """
                {
                  "type": "object",
                  "properties": {
                    "action": {
                      "type": "string",
                      "description": "What will happen, in plain language."
                    },
                    "detail": {
                      "type": "string",
                      "description": "Specifics the user needs to decide: the file list, the branch name."
                    }
                  },
                  "required": ["action"]
                }
                """
            )
        )
    )

    /** Tools available to the orchestrator but never to a subagent. */
    val orchestratorOnly: List<Tool> = listOf(SPAWN_SUBAGENT, UPDATE_PLAN)

    val all: List<Tool> = listOf(SPAWN_SUBAGENT, UPDATE_PLAN, REQUEST_APPROVAL)

    /**
     * Parse a raw JSON Schema literal.
     *
     * The existing OroTools helper only builds flat string properties,
     * which cannot express the array-of-objects shape that `update_plan`
     * needs. Writing the schema out is clearer than extending the builder with
     * a nested DSL used by three tools.
     */
    private fun buildParameters(schema: String): JsonElement =
        AiHttp.json.parseToJsonElement(schema.trimIndent())
}
