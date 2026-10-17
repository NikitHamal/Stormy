package com.oro.app.data.ai.agents

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Semaphore
import java.util.UUID

/**
 * Tracks delegated work: which subagents exist, what each is doing, and what
 * the user is allowed to do about it.
 *
 * This is the state half of delegation only -- it owns sessions, phases,
 * approvals and cancellation. It does not drive the model loop; the caller
 * does that and reports progress back through [advance]. Keeping the two apart
 * means the whole UI can be exercised without a provider, which is the
 * difference between being able to test this and not.
 *
 * ### Why there is a concurrency gate
 *
 * Nothing stops a model from requesting six subagents in one turn. On a phone
 * that is six concurrent streams, six sets of file reads, and a token bill the
 * user did not ask for, arriving all at once. [maxConcurrent] admits them in
 * order and leaves the rest visibly [AgentPhase.QUEUED], which is also more
 * honest than silently serialising them.
 */
class SubagentOrchestrator(
    private val scope: CoroutineScope,
    private val maxConcurrent: Int = 2
) {
    private val gate = Semaphore(maxConcurrent)
    private val jobs = mutableMapOf<String, Job>()

    private val _sessions = MutableStateFlow<List<AgentSession>>(emptyList())
    val sessions: StateFlow<List<AgentSession>> = _sessions.asStateFlow()

    /** Live sessions, newest first -- what the activity sheet shows. */
    val liveSessions: StateFlow<List<AgentSession>> = _sessions
        .map { list -> list.filter { it.isLive }.sortedByDescending { it.startedAt } }
        .derived(emptyList())

    val hasLiveWork: StateFlow<Boolean> = _sessions
        .map { list -> list.any { it.isLive } }
        .derived(false)

    /** Sessions blocked on the user. */
    val awaitingApproval: StateFlow<List<AgentSession>> = _sessions
        .map { list -> list.filter { it.phase == AgentPhase.AWAITING_APPROVAL } }
        .derived(emptyList())

    /**
     * Register a delegation.
     *
     * @return the new session, or null when the request is refused -- either
     *   the nesting limit is reached or the parent's type is not permitted to
     *   delegate. Refusing here rather than in the prompt means a model that
     *   ignores the instruction still cannot recurse without bound.
     */
    fun spawn(
        definition: SubagentDefinition,
        description: String,
        prompt: String,
        parentId: String? = null
    ): AgentSession? {
        val parent = parentId?.let { id -> _sessions.value.find { it.id == id } }
        val depth = (parent?.depth ?: 0) + 1

        if (depth > Subagents.MAX_DEPTH) return null
        if (parent != null && !parent.definition.allows(ToolCapability.DELEGATE)) return null

        val session = AgentSession(
            id = UUID.randomUUID().toString(),
            definition = definition,
            description = description.trim().ifEmpty { definition.label },
            prompt = prompt,
            parentId = parentId,
            depth = depth
        )
        _sessions.value = _sessions.value + session
        return session
    }

    /**
     * Move a session to a new phase, optionally appending a step.
     *
     * Terminal phases are sticky: once a session has completed, failed or been
     * cancelled, a late progress report from an in-flight coroutine must not
     * revive it. Cancellation in particular races with whatever the agent was
     * already doing, and without this the card would flicker back to
     * "Reading files" after the user stopped it.
     */
    fun advance(
        sessionId: String,
        phase: AgentPhase,
        step: AgentStep? = null,
        turnsUsed: Int? = null
    ) = update(sessionId) { current ->
        if (current.phase.isTerminal) return@update current
        current.copy(
            phase = phase,
            steps = step?.let { current.steps + it } ?: current.steps,
            turnsUsed = turnsUsed ?: current.turnsUsed,
            finishedAt = if (phase.isTerminal) System.currentTimeMillis() else null
        )
    }

    /** Record the final summary and complete the session. */
    fun complete(sessionId: String, summary: String) = update(sessionId) { current ->
        if (current.phase.isTerminal) return@update current
        current.copy(
            phase = AgentPhase.COMPLETED,
            summary = summary,
            pendingApproval = null,
            finishedAt = System.currentTimeMillis()
        )
    }

    fun fail(sessionId: String, error: String) = update(sessionId) { current ->
        if (current.phase.isTerminal) return@update current
        current.copy(
            phase = AgentPhase.FAILED,
            error = error,
            pendingApproval = null,
            finishedAt = System.currentTimeMillis()
        )
    }

    /**
     * Stop a session at the user's request.
     *
     * Cancels the coroutine first, then marks the state, so no further step can
     * be appended after the card reads "Stopped".
     */
    fun cancel(sessionId: String) {
        jobs.remove(sessionId)?.cancel()
        update(sessionId) { current ->
            if (current.phase.isTerminal) return@update current
            current.copy(
                phase = AgentPhase.CANCELLED,
                pendingApproval = null,
                finishedAt = System.currentTimeMillis()
            )
        }
    }

    /** Stop everything still running. Used by the stop-all control. */
    fun cancelAll() {
        _sessions.value.filter { it.isLive }.forEach { cancel(it.id) }
    }

    /** Park a session until the user answers. */
    fun requestApproval(sessionId: String, request: ApprovalRequest) = update(sessionId) { current ->
        if (current.phase.isTerminal) return@update current
        current.copy(phase = AgentPhase.AWAITING_APPROVAL, pendingApproval = request)
    }

    /**
     * Answer a pending approval.
     *
     * A denial does not fail the session: the agent is told no and continues,
     * which is usually recoverable. The caller resumes it by reporting the next
     * phase through [advance].
     */
    fun resolveApproval(sessionId: String, approved: Boolean) = update(sessionId) { current ->
        val pending = current.pendingApproval ?: return@update current
        current.copy(
            phase = AgentPhase.THINKING,
            pendingApproval = null,
            steps = current.steps + AgentStep(
                index = current.steps.size,
                phase = AgentPhase.AWAITING_APPROVAL,
                label = if (approved) "You approved: ${pending.description}"
                        else "You declined: ${pending.description}",
                finishedAt = System.currentTimeMillis(),
                isError = !approved
            )
        )
    }

    /** Associate the driving coroutine so cancellation can reach it. */
    fun attachJob(sessionId: String, job: Job) {
        jobs[sessionId] = job
        job.invokeOnCompletion { jobs.remove(sessionId) }
    }

    /** Admission gate; suspends while [maxConcurrent] others are running. */
    suspend fun <T> withSlot(sessionId: String, block: suspend () -> T): T {
        gate.acquire()
        return try {
            advance(sessionId, AgentPhase.PREPARING)
            block()
        } finally {
            gate.release()
        }
    }

    /** Drop finished sessions. The live ones are untouched. */
    fun clearCompleted() {
        _sessions.value = _sessions.value.filter { it.isLive }
    }

    fun session(id: String): AgentSession? = _sessions.value.find { it.id == id }

    private inline fun update(sessionId: String, transform: (AgentSession) -> AgentSession) {
        _sessions.value = _sessions.value.map { if (it.id == sessionId) transform(it) else it }
    }

    private fun <T> Flow<T>.derived(initial: T): StateFlow<T> =
        stateIn(scope, SharingStarted.Eagerly, initial)
}
