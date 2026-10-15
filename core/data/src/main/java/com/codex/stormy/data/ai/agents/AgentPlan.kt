package com.codex.stormy.data.ai.agents

import kotlinx.serialization.Serializable

/** State of one step in the agent's plan. */
@Serializable
enum class TaskState { PENDING, ACTIVE, DONE, BLOCKED, SKIPPED }

/**
 * One item in the agent's working plan.
 *
 * [note] carries the reason when a task ends up [TaskState.BLOCKED] or
 * [TaskState.SKIPPED]. A plan that silently drops a step is worse than no
 * plan -- the user believes it happened.
 */
@Serializable
data class PlanTask(
    val id: String,
    val title: String,
    val state: TaskState = TaskState.PENDING,
    val note: String? = null,
    /** Set when this task was handed to a subagent. */
    val delegatedTo: String? = null
)

/**
 * The agent's plan for the current request.
 *
 * Exists so that long multi-step work is legible while it happens. On a phone
 * the user cannot scroll back through a wall of tool calls to work out where
 * the agent is; a short checklist that updates in place answers "what is it
 * doing and how much is left" at a glance.
 *
 * Kept deliberately small. A plan with fifteen items is a plan nobody reads,
 * and encourages the model to pad it with steps that are obvious from the code.
 */
@Serializable
data class AgentPlan(
    val tasks: List<PlanTask> = emptyList()
) {
    val isEmpty: Boolean get() = tasks.isEmpty()
    val activeTask: PlanTask? get() = tasks.firstOrNull { it.state == TaskState.ACTIVE }
    val doneCount: Int get() = tasks.count { it.state == TaskState.DONE }

    /**
     * Fraction complete, counting skipped tasks as resolved.
     *
     * Skipped items are deliberately counted: they are decided, not pending,
     * and leaving them out makes a finished plan read as 80% done forever.
     */
    val fraction: Float
        get() = if (tasks.isEmpty()) 0f
        else tasks.count { it.state == TaskState.DONE || it.state == TaskState.SKIPPED }
            .toFloat() / tasks.size

    val isComplete: Boolean
        get() = tasks.isNotEmpty() && tasks.none {
            it.state == TaskState.PENDING || it.state == TaskState.ACTIVE
        }

    fun withTask(id: String, transform: (PlanTask) -> PlanTask): AgentPlan =
        copy(tasks = tasks.map { if (it.id == id) transform(it) else it })

    /**
     * Mark one task active, clearing any other active task.
     *
     * Exactly one task is active at a time by construction. Two highlighted
     * rows tell the user the agent has lost track of itself, and the model does
     * sometimes forget to close the previous one.
     */
    fun activate(id: String): AgentPlan = copy(
        tasks = tasks.map {
            when {
                it.id == id -> it.copy(state = TaskState.ACTIVE)
                it.state == TaskState.ACTIVE -> it.copy(state = TaskState.DONE)
                else -> it
            }
        }
    )

    companion object {
        /** Beyond this the plan stops being a glanceable summary. */
        const val MAX_TASKS = 10
    }
}
