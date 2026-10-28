package com.oro.app.data.ai.agents

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.tools.ToolResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The coordinator half of delegation: background spawns that return at once,
 * live status, follow-ups that reach a running session, and waiting that
 * collects a finish without ever blocking the rounds before it. This is what
 * lets the main coding agent keep working while its delegates work.
 */
class DelegationCoordinatorTest {

    /** A runner whose sessions finish when the test says so. */
    private class GateRunner : DelegationHandler.SubagentRunner {
        val started = mutableListOf<AgentSession>()
        lateinit var orchestrator: SubagentOrchestrator

        override fun start(session: AgentSession) {
            started += session
        }

        /** Completes [session] as the loop would, with a summary. */
        fun finish(session: AgentSession, summary: String) = orchestrator.complete(session.id, summary)

        fun fail(session: AgentSession, error: String) = orchestrator.fail(session.id, error)
    }

    private class Fixture {
        val orchestrator = SubagentOrchestrator(
            kotlinx.coroutines.test.TestScope(kotlinx.coroutines.test.StandardTestDispatcher())
        )
        val runner = GateRunner().also { it.orchestrator = orchestrator }
        val handler: DelegationHandler

        constructor(followUpSink: ((String, String) -> Boolean)? = null) {
            handler = if (followUpSink == null) {
                DelegationHandler(orchestrator, runner)
            } else {
                DelegationHandler(orchestrator, runner, followUpSink)
            }
        }
    }

    private fun args(json: String) = DelegationHandler.parseArgs(json)!!

    @Test
    fun `background spawn returns at once with the id and the orchestration hints`() = runTest {
        val f = Fixture(); val handler = f.handler; val runner = f.runner; val o = f.orchestrator
        val result = handler.execute(
            "spawn_subagent",
            args("""{"agent_type":"explore","description":"find the router","prompt":"look","background":true}""")
        )
        assertThat(result.success).isTrue()
        assertThat(result.output).contains("background")
        assertThat(result.output).contains("agents_status")
        assertThat(result.output).contains("wait_for_agent")
        assertThat(runner.started).hasSize(1)
        // Nothing awaited: the session is still live and the call already returned.
        assertThat(o.session(runner.started[0].id)!!.isLive).isTrue()
    }

    @Test
    fun `a foreground spawn's message still promises the summary`() = runTest {
        val f = Fixture(); val handler = f.handler; val o = f.orchestrator
        val result = handler.execute(
            "spawn_subagent",
            args("""{"agent_type":"explore","description":"find the router","prompt":"look"}""")
        )
        assertThat(result.output).contains("summary is returned when it finishes")
        assertThat(result.output).doesNotContain("background")
    }

    @Test
    fun `agents_status lists live sessions with phase, rounds and last step`() = runTest {
        val f = Fixture(); val handler = f.handler; val runner = f.runner; val o = f.orchestrator
        handler.execute("spawn_subagent", args("""{"agent_type":"build","description":"write the gallery","prompt":"go","background":true}"""))
        val session = runner.started[0]
        o.advance(
            session.id, AgentPhase.EDITING,
            AgentStep(index = 0, phase = AgentPhase.EDITING, label = "Wrote gallery.js")
        )
        o.advance(session.id, AgentPhase.EDITING, turnsUsed = 4)

        val all = handler.execute("agents_status", args("{}"))
        assertThat(all.success).isTrue()
        assertThat(all.output).contains("write the gallery")
        assertThat(all.output).contains("Editing")
        assertThat(all.output).contains("round 4")
        assertThat(all.output).contains("Wrote gallery.js")

        val one = handler.execute("agents_status", args("""{"id":"${session.id}"}"""))
        assertThat(one.output).contains("write the gallery")

        val missing = handler.execute("agents_status", args("""{"id":"nope"}"""))
        assertThat(missing.success).isFalse()
    }

    @Test
    fun `with nothing delegated, agents_status says so plainly`() = runTest {
        val f = Fixture(); val handler = f.handler; val o = f.orchestrator
        val result = handler.execute("agents_status", args("{}"))
        assertThat(result.success).isTrue()
        assertThat(result.output).contains("No delegated work running")
    }

    @Test
    fun `follow_up queues on a live session and the loop drains it`() = runTest {
        val f = Fixture(); val handler = f.handler; val runner = f.runner; val o = f.orchestrator
        handler.execute("spawn_subagent", args("""{"agent_type":"explore","description":"prices","prompt":"go","background":true}"""))
        val session = runner.started[0]

        val queued = handler.execute("follow_up", args("""{"id":"${session.id}","message":"only prices in EUR"}"""))
        assertThat(queued.success).isTrue()
        assertThat(o.session(session.id)!!.followUps).containsExactly("only prices in EUR")

        // The run loop's roundNote drains exactly once.
        val drained = o.drainFollowUps(session.id)
        assertThat(drained).containsExactly("only prices in EUR")
        assertThat(o.drainFollowUps(session.id)).isEmpty()
    }

    @Test
    fun `follow_up to a finished session is refused with the reason`() = runTest {
        val f = Fixture(); val handler = f.handler; val runner = f.runner; val o = f.orchestrator
        handler.execute("spawn_subagent", args("""{"agent_type":"explore","description":"prices","prompt":"go","background":true}"""))
        val session = runner.started[0]
        runner.finish(session, "Done: three prices.")

        val result = handler.execute("follow_up", args("""{"id":"${session.id}","message":"again"}"""))
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("already finished")
    }

    @Test
    fun `a custom sink receives follow-ups for sessions the handler does not queue`() = runTest {
        val sunk = mutableListOf<Pair<String, String>>()
        val f = Fixture(followUpSink = { id, message -> sunk += id to message; true })
        val handler = f.handler
        val runner = f.runner
        val orchestrator = f.orchestrator
        handler.execute("spawn_subagent", args("""{"agent_type":"explore","description":"x","prompt":"go","background":true}"""))
        val id = runner.started[0].id

        handler.execute("follow_up", args("""{"id":"$id","message":"steer"}"""))
        assertThat(sunk).containsExactly(id to "steer")
        // The session's own queue stayed empty: the sink replaced it.
        assertThat(orchestrator.session(id)!!.followUps).isEmpty()
    }

    @Test
    fun `wait_for_agent returns the summary once the session finishes`() = runTest {
        val f = Fixture(); val handler = f.handler; val runner = f.runner; val o = f.orchestrator
        handler.execute("spawn_subagent", args("""{"agent_type":"build","description":"gallery","prompt":"go","background":true}"""))
        val session = runner.started[0]

        val waiting = launch {
            val result = handler.execute("wait_for_agent", args("""{"id":"${session.id}","timeout_seconds":30}"""))
            assertThat(result.success).isTrue()
            assertThat(result.output).contains("the gallery works on phones")
        }
        runner.finish(session, "the gallery works on phones")
        waiting.join()
    }

    @Test
    fun `wait_for_agent on a finished session answers at once`() = runTest {
        val f = Fixture(); val handler = f.handler; val runner = f.runner; val o = f.orchestrator
        handler.execute("spawn_subagent", args("""{"agent_type":"build","description":"gallery","prompt":"go","background":true}"""))
        val session = runner.started[0]
        runner.finish(session, "done already")

        val result = handler.execute("wait_for_agent", args("""{"id":"${session.id}"}"""))
        assertThat(result.success).isTrue()
        assertThat(result.output).contains("done already")
    }

    @Test
    fun `a failed session reports its failure through wait_for_agent`() = runTest {
        val f = Fixture(); val handler = f.handler; val runner = f.runner; val o = f.orchestrator
        handler.execute("spawn_subagent", args("""{"agent_type":"build","description":"gallery","prompt":"go","background":true}"""))
        val session = runner.started[0]
        runner.fail(session, "the model kept re-reading the same file")

        val result = handler.execute("wait_for_agent", args("""{"id":"${session.id}"}"""))
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("re-reading")
    }
}
