package com.oro.app.data.ai.agents

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Executes the three delegation tools and keeps the orchestrator and plan in
 * step with what the model asked for.
 *
 * This sits between the raw tool call and the orchestrator so that the parsing
 * and validation live in one testable place rather than inside the view model's
 * streaming loop. The loop only needs to ask "is this one of mine?" and hand it
 * over.
 *
 * Every handler returns a [ToolResult] whose `output` is written for the *model*
 * to read on the next turn, not for the user. That distinction matters: the
 * model needs to know the delegation was accepted and that the summary will
 * arrive later, otherwise it either waits for a result that never comes in this
 * turn or re-delegates the same work.
 */
class DelegationHandler(
    private val orchestrator: SubagentOrchestrator,
    /**
     * Drives a subagent to completion. Supplied by the caller because the
     * model loop lives in the view model; this class deliberately knows nothing
     * about providers.
     */
    private val runner: SubagentRunner
) {
    /** What a caller must provide to actually run delegated work. */
    fun interface SubagentRunner {
        /**
         * Start [session]. Implementations report progress through the
         * orchestrator and finish by calling `complete` or `fail`.
         */
        fun start(session: AgentSession)
    }

    private val _plan = MutableStateFlow(AgentPlan())
    val plan: StateFlow<AgentPlan> = _plan.asStateFlow()

    /** Tool names this handler owns. */
    val handledTools: Set<String> = setOf("spawn_subagent", "update_plan", "request_approval")

    fun handles(toolName: String): Boolean = toolName in handledTools

    /**
     * Run a delegation tool call.
     *
     * @param parentSessionId set when the call came from inside a subagent, so
     *   nesting depth and delegate permission are evaluated against the right
     *   parent.
     */
    fun execute(
        toolName: String,
        args: JsonObject,
        parentSessionId: String? = null
    ): ToolResult = when (toolName) {
        "spawn_subagent" -> spawnSubagent(args, parentSessionId)
        "update_plan" -> updatePlan(args)
        "request_approval" -> requestApproval(args, parentSessionId)
        else -> ToolResult(false, "", "Unknown delegation tool: $toolName")
    }

    private fun spawnSubagent(args: JsonObject, parentSessionId: String?): ToolResult {
        val typeId = args.str("agent_type")
            ?: return ToolResult(false, "", "agent_type is required")
        val description = args.str("description")
            ?: return ToolResult(false, "", "description is required")
        val prompt = args.str("prompt")
            ?: return ToolResult(false, "", "prompt is required")

        val definition = Subagents[typeId]
            ?: return ToolResult(
                false, "",
                "Unknown agent_type '$typeId'. Available: " +
                    Subagents.all.joinToString(", ") { it.id }
            )

        val session = orchestrator.spawn(
            definition = definition,
            description = description,
            prompt = prompt,
            parentId = parentSessionId
        ) ?: return ToolResult(
            false, "",
            // Explains the refusal rather than just denying, so the model does
            // the work itself instead of retrying the same call.
            "Cannot delegate from here: the nesting limit of ${Subagents.MAX_DEPTH} " +
                "levels is reached, or this agent type may not delegate. Do the work directly."
        )

        runner.start(session)

        return ToolResult(
            success = true,
            output = buildString {
                append("Started ${definition.label} agent \"$description\" (id ${session.id}). ")
                append("It runs in its own context and will report a summary when it finishes. ")
                append("Do not wait for it or repeat its work; continue with what you can do now.")
            }
        )
    }

    private fun updatePlan(args: JsonObject): ToolResult {
        val raw = args["tasks"]?.jsonArray
            ?: return ToolResult(false, "", "tasks is required and must be an array")

        if (raw.size > AgentPlan.MAX_TASKS) {
            return ToolResult(
                false, "",
                "Too many tasks (${raw.size}). Keep the plan to at most " +
                    "${AgentPlan.MAX_TASKS} items -- a longer checklist is one nobody reads. " +
                    "Group the detail into fewer steps."
            )
        }

        val tasks = raw.mapIndexedNotNull { index, element ->
            val obj = element.jsonObject
            val title = obj.str("title")?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            PlanTask(
                id = "task-$index",
                title = title,
                state = parseState(obj.str("state")),
                note = obj.str("note")?.takeIf { it.isNotBlank() }
            )
        }

        if (tasks.isEmpty()) return ToolResult(false, "", "No valid tasks in the list")

        // Exactly one active task, whatever the model sent. Two highlighted
        // rows tell the user the agent has lost track of itself.
        val activeIds = tasks.filter { it.state == TaskState.ACTIVE }.map { it.id }
        val normalised = if (activeIds.size > 1) {
            val keep = activeIds.first()
            tasks.map {
                if (it.state == TaskState.ACTIVE && it.id != keep) it.copy(state = TaskState.PENDING)
                else it
            }
        } else {
            tasks
        }

        _plan.value = AgentPlan(normalised)

        val done = normalised.count { it.state == TaskState.DONE }
        return ToolResult(
            success = true,
            output = "Plan updated: $done of ${normalised.size} done" +
                (normalised.firstOrNull { it.state == TaskState.ACTIVE }
                    ?.let { ", now on \"${it.title}\"" } ?: "")
        )
    }

    private fun requestApproval(args: JsonObject, sessionId: String?): ToolResult {
        val action = args.str("action")
            ?: return ToolResult(false, "", "action is required")
        val detail = args.str("detail")

        if (sessionId == null) {
            // Approval for the main conversation is surfaced by the caller, not
            // through a session card. Reported as a failure so the model does
            // not sit waiting for a card that will never appear.
            return ToolResult(
                false, "",
                "request_approval is only available to subagents. In the main " +
                    "conversation, ask the user directly in your reply instead."
            )
        }

        orchestrator.requestApproval(
            sessionId,
            ApprovalRequest(
                id = "approval-${System.nanoTime()}",
                toolName = "request_approval",
                description = action,
                detail = detail,
                capability = ToolCapability.WRITE
            )
        )
        return ToolResult(
            success = true,
            output = "Waiting for the user to decide. You will be resumed with their answer."
        )
    }

    /** Clear the plan, e.g. when a new request starts. */
    fun resetPlan() {
        _plan.value = AgentPlan()
    }

    private fun parseState(raw: String?): TaskState = when (raw?.lowercase()) {
        "active", "in_progress", "running" -> TaskState.ACTIVE
        "done", "complete", "completed" -> TaskState.DONE
        "blocked" -> TaskState.BLOCKED
        "skipped" -> TaskState.SKIPPED
        else -> TaskState.PENDING
    }

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()

    companion object {
        /** Parse a tool-call argument blob, tolerating the usual model slop. */
        fun parseArgs(raw: String): JsonObject? =
            runCatching { AiHttp.json.parseToJsonElement(raw).jsonObject }.getOrNull()
    }
}
