package com.oro.app.data.ai.agents

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SubagentOrchestratorTest {

    private fun orchestrator() = SubagentOrchestrator(TestScope(StandardTestDispatcher()))

    @Test
    fun `spawning registers a queued session`() {
        val o = orchestrator()
        val session = o.spawn(Subagents.EXPLORE, "find the router", "Locate routing setup")

        assertThat(session).isNotNull()
        assertThat(session!!.phase).isEqualTo(AgentPhase.QUEUED)
        assertThat(session.depth).isEqualTo(1)
        assertThat(o.sessions.value).hasSize(1)
    }

    @Test
    fun `blank description falls back to the agent label`() {
        val o = orchestrator()
        val session = o.spawn(Subagents.EXPLORE, "   ", "brief")
        // An unlabelled card is unreadable; the type name is a usable default.
        assertThat(session!!.description).isEqualTo("Explore")
    }

    @Test
    fun `nesting is refused past the depth limit`() {
        val o = orchestrator()
        val root = o.spawn(Subagents.GENERAL, "root", "do work")!!
        val child = o.spawn(Subagents.GENERAL, "child", "do work", parentId = root.id)

        assertThat(child).isNotNull()
        assertThat(child!!.depth).isEqualTo(2)

        // Depth 3 exceeds MAX_DEPTH. Enforced here rather than in the prompt,
        // so a model that ignores the instruction still cannot recurse.
        val grandchild = o.spawn(Subagents.GENERAL, "grandchild", "do work", parentId = child.id)
        assertThat(grandchild).isNull()
    }

    @Test
    fun `an agent without the delegate capability cannot spawn`() {
        val o = orchestrator()
        // Explore is read-only: no DELEGATE capability.
        val explorer = o.spawn(Subagents.EXPLORE, "look", "look around")!!
        assertThat(o.spawn(Subagents.BUILD, "build", "build it", parentId = explorer.id)).isNull()
    }

    @Test
    fun `terminal phases are sticky so a late report cannot revive a session`() {
        val o = orchestrator()
        val session = o.spawn(Subagents.BUILD, "write page", "Write index.html")!!

        o.cancel(session.id)
        assertThat(o.session(session.id)!!.phase).isEqualTo(AgentPhase.CANCELLED)

        // Cancellation races with whatever the agent was already doing. Without
        // stickiness the card flicks back to "Reading files" after the user
        // stopped it.
        o.advance(session.id, AgentPhase.READING)
        assertThat(o.session(session.id)!!.phase).isEqualTo(AgentPhase.CANCELLED)

        o.complete(session.id, "done anyway")
        assertThat(o.session(session.id)!!.phase).isEqualTo(AgentPhase.CANCELLED)
        assertThat(o.session(session.id)!!.summary).isNull()
    }

    @Test
    fun `completing records the summary and a finish time`() {
        val o = orchestrator()
        val session = o.spawn(Subagents.EXPLORE, "find it", "find the thing")!!
        o.complete(session.id, "It is in src/router.js")

        val done = o.session(session.id)!!
        assertThat(done.phase).isEqualTo(AgentPhase.COMPLETED)
        assertThat(done.summary).isEqualTo("It is in src/router.js")
        assertThat(done.finishedAt).isNotNull()
        assertThat(done.isLive).isFalse()
    }

    @Test
    fun `steps accumulate in order`() {
        val o = orchestrator()
        val session = o.spawn(Subagents.BUILD, "build", "build it")!!
        o.advance(session.id, AgentPhase.READING, step(0, "Read index.html"))
        o.advance(session.id, AgentPhase.EDITING, step(1, "Wrote index.html"))

        val steps = o.session(session.id)!!.steps
        assertThat(steps.map { it.label })
            .containsExactly("Read index.html", "Wrote index.html").inOrder()
    }

    @Test
    fun `approval parks the session and resolving resumes it`() {
        val o = orchestrator()
        val session = o.spawn(Subagents.BUILD, "clean up", "Delete old files")!!
        o.requestApproval(session.id, approval("Delete 4 files under src/legacy"))

        assertThat(o.session(session.id)!!.phase).isEqualTo(AgentPhase.AWAITING_APPROVAL)
        assertThat(o.session(session.id)!!.pendingApproval).isNotNull()

        o.resolveApproval(session.id, approved = true)
        val resumed = o.session(session.id)!!
        assertThat(resumed.phase).isEqualTo(AgentPhase.THINKING)
        assertThat(resumed.pendingApproval).isNull()
        assertThat(resumed.steps.last().label).contains("approved")
    }

    @Test
    fun `declining an approval does not fail the session`() {
        val o = orchestrator()
        val session = o.spawn(Subagents.BUILD, "clean up", "Delete old files")!!
        o.requestApproval(session.id, approval("Force push to main"))
        o.resolveApproval(session.id, approved = false)

        val resumed = o.session(session.id)!!
        // Being told no is usually recoverable; the agent continues.
        assertThat(resumed.phase).isEqualTo(AgentPhase.THINKING)
        assertThat(resumed.isLive).isTrue()
        assertThat(resumed.steps.last().isError).isTrue()
    }

    @Test
    fun `cancelAll stops every live session and leaves finished ones alone`() {
        val o = orchestrator()
        val a = o.spawn(Subagents.EXPLORE, "a", "a")!!
        val b = o.spawn(Subagents.EXPLORE, "b", "b")!!
        o.complete(a.id, "found it")

        o.cancelAll()

        assertThat(o.session(a.id)!!.phase).isEqualTo(AgentPhase.COMPLETED)
        assertThat(o.session(b.id)!!.phase).isEqualTo(AgentPhase.CANCELLED)
    }

    @Test
    fun `clearCompleted keeps live sessions`() {
        val o = orchestrator()
        val done = o.spawn(Subagents.EXPLORE, "done", "x")!!
        val live = o.spawn(Subagents.EXPLORE, "live", "y")!!
        o.complete(done.id, "ok")

        o.clearCompleted()

        assertThat(o.sessions.value.map { it.id }).containsExactly(live.id)
    }

    @Test
    fun `budget fraction tracks turns and pins to full when complete`() {
        val o = orchestrator()
        val session = o.spawn(Subagents.EXPLORE, "find", "find")!!   // maxTurns 16
        o.advance(session.id, AgentPhase.THINKING, turnsUsed = 8)
        assertThat(o.session(session.id)!!.budgetFraction).isWithin(0.01f).of(0.5f)

        o.complete(session.id, "done")
        // Showing "50%" next to "Done" is worse than showing nothing.
        assertThat(o.session(session.id)!!.budgetFraction).isEqualTo(1f)
    }

    @Test
    fun `the concurrency gate admits only maxConcurrent at once`() = runTest {
        // backgroundScope, not `this`: the orchestrator keeps eagerly-collected
        // derived flows alive for its whole lifetime, and runTest waits for
        // every child of its own scope to finish.
        val o = SubagentOrchestrator(backgroundScope, maxConcurrent = 1)
        val first = o.spawn(Subagents.EXPLORE, "first", "x")!!
        val second = o.spawn(Subagents.EXPLORE, "second", "y")!!

        var secondStarted = false
        val holder = backgroundScope.launch { o.withSlot(first.id) { delay(1_000) } }
        advanceTimeBy(10)

        val waiter = backgroundScope.launch { o.withSlot(second.id) { secondStarted = true } }
        advanceTimeBy(100)
        // Still queued: the first run holds the only slot. Without this gate a
        // model requesting six subagents gets six concurrent streams.
        assertThat(secondStarted).isFalse()

        holder.join()
        waiter.join()
        assertThat(secondStarted).isTrue()
    }

    private fun step(index: Int, label: String) = AgentStep(
        index = index,
        phase = AgentPhase.READING,
        label = label,
        finishedAt = System.currentTimeMillis()
    )

    private fun approval(description: String) = ApprovalRequest(
        id = "a1",
        toolName = "request_approval",
        description = description,
        capability = ToolCapability.WRITE
    )
}

class AgentPlanTest {

    private fun plan(vararg states: TaskState) = AgentPlan(
        states.mapIndexed { i, state -> PlanTask("t$i", "Task $i", state) }
    )

    @Test
    fun `activating one task closes the previously active one`() {
        val p = AgentPlan(
            listOf(
                PlanTask("a", "A", TaskState.ACTIVE),
                PlanTask("b", "B", TaskState.PENDING)
            )
        ).activate("b")

        // Two highlighted rows tell the user the agent has lost track of itself.
        assertThat(p.tasks.single { it.state == TaskState.ACTIVE }.id).isEqualTo("b")
        assertThat(p.tasks.first { it.id == "a" }.state).isEqualTo(TaskState.DONE)
    }

    @Test
    fun `skipped tasks count as resolved`() {
        // Otherwise a finished plan reads as 50% done forever.
        val p = plan(TaskState.DONE, TaskState.SKIPPED)
        assertThat(p.fraction).isEqualTo(1f)
        assertThat(p.isComplete).isTrue()
    }

    @Test
    fun `a blocked task leaves the plan incomplete`() {
        val p = plan(TaskState.DONE, TaskState.BLOCKED)
        assertThat(p.isComplete).isTrue()
        assertThat(p.fraction).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `an empty plan is not complete`() {
        assertThat(AgentPlan().isComplete).isFalse()
        assertThat(AgentPlan().fraction).isEqualTo(0f)
    }

    @Test
    fun `active task is exposed for the collapsed header`() {
        val p = plan(TaskState.DONE, TaskState.ACTIVE, TaskState.PENDING)
        assertThat(p.activeTask?.title).isEqualTo("Task 1")
        assertThat(p.doneCount).isEqualTo(1)
    }
}
