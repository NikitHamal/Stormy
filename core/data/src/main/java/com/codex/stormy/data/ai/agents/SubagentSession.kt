package com.codex.stormy.data.ai.agents

/**
 * What a running subagent is doing right now.
 *
 * Deliberately finer-grained than "running / done / failed". The user is
 * watching a card on a phone screen while something spends their tokens; the
 * difference between "reading files" and "waiting on the model" is the
 * difference between a progress report and a spinner. Each state maps to one
 * short phrase in the UI.
 *
 * Terminal states are separated from live ones so that cleanup and recovery
 * logic can branch on [isTerminal] without enumerating the live set.
 */
enum class AgentPhase(val display: String, val isTerminal: Boolean = false) {
    /** Accepted, not yet started -- the concurrency gate is holding it. */
    QUEUED("Queued"),

    /** Building the prompt and context. */
    PREPARING("Preparing"),

    /** Waiting on the provider. */
    THINKING("Thinking"),

    /** Tokens arriving. */
    STREAMING("Responding"),

    /** A read-only tool is running. */
    READING("Reading files"),

    /** A mutating tool is running. */
    EDITING("Editing files"),

    /** A shell command is running. */
    EXECUTING("Running command"),

    /** Blocked on the user: an action needs approval. */
    AWAITING_APPROVAL("Needs your approval"),

    /** Writing its final summary. */
    SUMMARISING("Wrapping up"),

    /** Finished, summary available. */
    COMPLETED("Done", isTerminal = true),

    /** Stopped by the user. */
    CANCELLED("Stopped", isTerminal = true),

    /** Hit maxTurns without finishing -- almost always a loop. */
    EXHAUSTED("Gave up", isTerminal = true),

    /** Ended on an error. */
    FAILED("Failed", isTerminal = true);

    val isLive: Boolean get() = !isTerminal
}

/** One step a subagent took, for the expandable timeline on its card. */
data class AgentStep(
    val index: Int,
    val phase: AgentPhase,
    /** "Read index.html", "Wrote 3 files" -- already user-facing. */
    val label: String,
    val detail: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val isError: Boolean = false
) {
    val durationMs: Long? get() = finishedAt?.let { it - startedAt }
}

/**
 * A subagent run, as the UI sees it.
 *
 * Immutable: the orchestrator replaces the whole value on each change so that
 * Compose can diff it, rather than mutating fields a snapshot cannot observe.
 */
data class AgentSession(
    val id: String,
    val definition: SubagentDefinition,
    /** The one-line task label. This is what the user reads on the card. */
    val description: String,
    /** The full brief handed to the subagent. Shown when expanded. */
    val prompt: String,
    val phase: AgentPhase = AgentPhase.QUEUED,
    val depth: Int = 1,
    val parentId: String? = null,
    val steps: List<AgentStep> = emptyList(),
    val turnsUsed: Int = 0,
    /** Final summary; the only part that reaches the parent conversation. */
    val summary: String? = null,
    val error: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    /** Set while [AgentPhase.AWAITING_APPROVAL]. */
    val pendingApproval: ApprovalRequest? = null
) {
    val isLive: Boolean get() = phase.isLive
    val elapsedMs: Long get() = (finishedAt ?: System.currentTimeMillis()) - startedAt

    /**
     * Progress as a fraction of the turn budget, or null when the budget is
     * not a meaningful denominator.
     *
     * Honest about being an estimate: turns used against maxTurns is a budget
     * bar, not a measure of how much of the task is done. A completed agent
     * reports 1f regardless, because showing "60%" next to "Done" is worse
     * than showing nothing.
     */
    val budgetFraction: Float?
        get() = when {
            phase == AgentPhase.COMPLETED -> 1f
            definition.maxTurns <= 0 -> null
            else -> (turnsUsed.toFloat() / definition.maxTurns).coerceIn(0f, 1f)
        }
}

/** An action a subagent wants to take that the user must agree to first. */
data class ApprovalRequest(
    val id: String,
    val toolName: String,
    /** "Delete 4 files in src/" -- plain language, not a JSON dump. */
    val description: String,
    val detail: String? = null,
    val capability: ToolCapability
)
