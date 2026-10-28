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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
    private val runner: SubagentRunner,
    /**
     * Where follow_up messages go. Defaults to the session's own queue; the
     * view model overrides it so sessions driven outside this handler (a
     * background browser run) receive theirs too.
     */
    private val followUpSink: (sessionId: String, message: String) -> Boolean =
        { id, message -> orchestrator.queueFollowUp(id, message) }
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
    val handledTools: Set<String> = setOf(
        "spawn_subagent", "update_plan", "request_approval",
        "agents_status", "follow_up", "wait_for_agent"
    )

    fun handles(toolName: String): Boolean = toolName in handledTools

    /**
     * Run a delegation tool call.
     *
     * @param parentSessionId set when the call came from inside a subagent, so
     *   nesting depth and delegate permission are evaluated against the right
     *   parent.
     */
    suspend fun execute(
        toolName: String,
        args: JsonObject,
        parentSessionId: String? = null
    ): ToolResult = when (toolName) {
        "spawn_subagent" -> startSubagent(args, parentSessionId).result
        "update_plan" -> updatePlan(args)
        "request_approval" -> requestApproval(args, parentSessionId)
        "agents_status" -> agentsStatus(args)
        "follow_up" -> followUp(args)
        "wait_for_agent" -> waitForAgent(args)
        else -> ToolResult(false, "", "Unknown delegation tool: $toolName")
    }

    /**
     * Starts a subagent and returns the result with the new session's id
     * (null when it did not start), so a caller waits on exactly that one:
     * looking for "the session that appeared" picked another call's when two
     * spawns ran side by side.
     */
    fun startSubagent(args: JsonObject, parentSessionId: String? = null): SpawnOutcome {
        val typeId = args.str("agent_type")
            ?: return SpawnOutcome(ToolResult(false, "", "agent_type is required"), null, false)
        val description = args.str("description")
            ?: return SpawnOutcome(ToolResult(false, "", "description is required"), null, false)
        val prompt = args.str("prompt")
            ?: return SpawnOutcome(ToolResult(false, "", "prompt is required"), null, false)
        val background = args.str("background").equals("true", ignoreCase = true)

        val definition = Subagents[typeId]
            ?: return SpawnOutcome(
                ToolResult(
                    false, "",
                    "Unknown agent_type '$typeId'. Available: " +
                        Subagents.all.joinToString(", ") { it.id }
                ), null, false
            )

        val session = orchestrator.spawn(
            definition = definition,
            description = description,
            prompt = prompt,
            parentId = parentSessionId
        ) ?: return SpawnOutcome(
            ToolResult(
                false, "",
                // Explains the refusal rather than just denying, so the model does
                // the work itself instead of retrying the same call.
                "Cannot delegate from here: the nesting limit of ${Subagents.MAX_DEPTH} " +
                    "levels is reached, or this agent type may not delegate. Do the work directly."
            ), null, false
        )

        runner.start(session)

        return SpawnOutcome(
            ToolResult(
                success = true,
                output = buildString {
                    append("Started ${definition.label} agent \"$description\" (id ${session.id}). ")
                    if (background) {
                        append("It runs in the background while you carry on: agents_status watches it, ")
                        append("follow_up steers it, wait_for_agent collects it. Its card shows it live.")
                    } else {
                        append("It runs in its own context; its summary is returned when it finishes.")
                    }
                }
            ),
            session.id,
            background
        )
    }

    /** What a spawn produced: the result for the model, the session id, and whether it went to the background. */
    data class SpawnOutcome(val result: ToolResult, val sessionId: String?, val background: Boolean)

    /** Every live session and the recently finished ones, one compact line each. */
    private fun agentsStatus(args: JsonObject): ToolResult {
        val wanted = args.str("id")
        val now = System.currentTimeMillis()
        val sessions = orchestrator.sessions.value.filter { s ->
            (wanted == null || s.id == wanted || s.id.startsWith(wanted)) &&
                (s.isLive || now - (s.finishedAt ?: now) < 30 * 60_000L)
        }
        if (wanted != null && sessions.isEmpty()) {
            return ToolResult(false, "", "No session with id \"$wanted\". agents_status without an id lists them all.")
        }
        if (sessions.isEmpty()) return ToolResult(true, "No delegated work running or recently finished. Everything is yours to do inline.")
        return ToolResult(true, sessions.joinToString("\n") { s ->
            buildString {
                append("[").append(s.id.take(8)).append("] ").append(s.definition.label).append(" \"").append(s.description).append("\" ")
                append(s.phase.display)
                if (s.isLive) {
                    if (s.turnsUsed > 0) append(", round ").append(s.turnsUsed).append(if (s.definition.maxTurns > 0) "/${s.definition.maxTurns}" else "")
                    s.steps.lastOrNull()?.let { append(", last: ").append(it.label.take(60)) }
                    append(", ").append(((now - s.startedAt) / 1000)).append("s")
                    if (s.followUps.isNotEmpty()) append(", ").append(s.followUps.size).append(" follow-up(s) queued")
                } else {
                    s.summary?.let { append(": ").append(it.take(300)) }
                    s.error?.let { append(" — ").append(it.take(160)) }
                }
            }
        })
    }

    private fun followUp(args: JsonObject): ToolResult {
        val id = args.str("id") ?: return ToolResult(false, "", "id is required: the session from spawn_subagent or agents_status.")
        val message = args.str("message") ?: return ToolResult(false, "", "message is required.")
        val session = orchestrator.session(id)
            ?: orchestrator.sessions.value.firstOrNull { it.id.startsWith(id) }
            ?: return ToolResult(false, "", "No session with id \"$id\".")
        if (!session.isLive) {
            return ToolResult(
                false, "",
                "Session ${session.id.take(8)} already finished (${session.phase.display.lowercase()}); read its summary in agents_status instead of following up."
            )
        }
        return if (followUpSink(session.id, message)) {
            ToolResult(true, "Queued for ${session.definition.label.lowercase()} \"${session.description}\"; it sees it before its next round.")
        } else {
            ToolResult(false, "", "Could not queue the follow-up; the session may have just finished.")
        }
    }

    private suspend fun waitForAgent(args: JsonObject): ToolResult {
        val id = args.str("id") ?: return ToolResult(false, "", "id is required.")
        val session = orchestrator.session(id)
            ?: orchestrator.sessions.value.firstOrNull { it.id.startsWith(id) }
            ?: return ToolResult(false, "", "No session with id \"$id\".")
        val live = orchestrator.sessions.value.firstOrNull { it.id == session.id }
        if (live != null && live.isLive) {
            val timeoutMs = ((args.str("timeout_seconds")?.toIntOrNull()) ?: 120).coerceIn(1, 600) * 1000L
            withTimeoutOrNull(timeoutMs) {
                orchestrator.sessions.first { list -> list.firstOrNull { it.id == session.id }?.isLive != true }
            }
        }
        val ended = orchestrator.session(session.id)
        return when {
            ended == null -> ToolResult(false, "", "The session was cleared before it finished; its summary is gone.")
            ended.isLive -> ToolResult(
                true,
                "Still running after the wait (${ended.phase.display}, round ${ended.turnsUsed}). It keeps going: wait again, follow_up, or carry on without it."
            )
            ended.phase == AgentPhase.COMPLETED -> ToolResult(
                true,
                "Subagent \"${ended.description}\" finished:\n${ended.summary.orEmpty()}"
            )
            ended.error != null -> ToolResult(false, "Subagent \"${ended.description}\" failed: ${ended.error}", ended.error)
            else -> ToolResult(
                false, "",
                "Subagent \"${ended.description}\" stopped (${ended.phase.display.lowercase()}) without a summary. Do the work directly."
            )
        }
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
