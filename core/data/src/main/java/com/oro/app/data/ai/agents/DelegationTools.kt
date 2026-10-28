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

                With background=true the call returns at once with the session
                id and you keep working: watch it with agents_status, steer it
                with follow_up, collect it with wait_for_agent (or let it finish
                and read its card). With background=false (the default) the call
                returns the subagent's summary when it finishes.
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
                    },
                    "background": {
                      "type": "boolean",
                      "description": "True: return at once and keep working while it runs (check agents_status / follow_up / wait_for_agent). False (default): wait for its summary."
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

    val AGENTS_STATUS = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "agents_status",
            description = """
                The live state of delegated work: every running (and recently finished) subagent and background
                browser run -- id, kind, task, phase, rounds spent, and its latest step. Call it to see how
                background work is going before deciding whether to follow up, wait, or carry on with your own
                work. With `id`, just that session, including its queued follow-ups.
            """.trimIndent(),
            parameters = buildParameters(
                """
                {
                  "type": "object",
                  "properties": {
                    "id": { "type": "string", "description": "One session id. Omit for all live and recent ones." }
                  },
                  "required": []
                }
                """
            )
        )
    )

    val FOLLOW_UP = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "follow_up",
            description = """
                Send a message to a running background session (subagent or browser run). It lands before the
                session's next round: new facts, a change of direction, "stop that and do this instead", or an
                answer to something it is stuck on. Use it instead of waiting and re-delegating: the session
                keeps its whole context, so a correction costs one message, not a fresh brief.
            """.trimIndent(),
            parameters = buildParameters(
                """
                {
                  "type": "object",
                  "properties": {
                    "id": { "type": "string", "description": "The session id from spawn_subagent or agents_status." },
                    "message": { "type": "string", "description": "What the session should know or do differently." }
                  },
                  "required": ["id", "message"]
                }
                """
            )
        )
    )

    val WAIT_FOR_AGENT = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "wait_for_agent",
            description = """
                Wait for a background session to finish and return its summary (or why it failed). The way to
                collect work you started with background=true once its result is actually needed. A session
                that already finished answers at once. Timeout caps the wait; on timeout the session keeps
                running and you can wait again or carry on.
            """.trimIndent(),
            parameters = buildParameters(
                """
                {
                  "type": "object",
                  "properties": {
                    "id": { "type": "string", "description": "The session id." },
                    "timeout_seconds": { "type": "integer", "description": "How long to wait. Default 120, max 600." }
                  },
                  "required": ["id"]
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
    val orchestratorOnly: List<Tool> = listOf(SPAWN_SUBAGENT, UPDATE_PLAN, AGENTS_STATUS, FOLLOW_UP, WAIT_FOR_AGENT)

    val all: List<Tool> = listOf(SPAWN_SUBAGENT, UPDATE_PLAN, REQUEST_APPROVAL, AGENTS_STATUS, FOLLOW_UP, WAIT_FOR_AGENT)

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
