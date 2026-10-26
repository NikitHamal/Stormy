package com.oro.app.data.ai.agent

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.tools.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToolBatchTest {

    private fun call(id: String, name: String, args: String = "{}") =
        ToolCallResponse(id, "function", FunctionCall(name, args))

    private fun write(id: String, path: String) = call(id, "write_file", """{"path":"$path","content":"$id"}""")
    private fun edit(id: String, path: String) = call(id, "edit_file", """{"path":"$path","old_string":"a","new_string":"b"}""")
    private fun read(id: String, path: String) = call(id, "read_file", """{"path":"$path"}""")

    /** When one call ran, in virtual milliseconds. */
    private data class Span(val start: Long, val end: Long) {
        fun overlaps(other: Span) = start < other.end && other.start < end
    }

    private class Ran(
        val results: List<ToolResult>,
        val spans: Map<String, Span>,
        /** Each onStarted: the ids starting together, and how many were running. */
        val starts: List<Pair<List<String>, Int>>,
        val finishOrder: List<String>,
        /** Most calls executing at the same moment. */
        val peak: Int
    ) {
        fun span(id: String) = spans.getValue(id)
    }

    /** Runs [calls]; each takes its [millis] (100 by default) of virtual time. */
    private suspend fun TestScope.runBatch(calls: List<ToolCallResponse>, millis: Map<String, Long> = emptyMap()): Ran {
        val spans = linkedMapOf<String, Span>()
        val starts = mutableListOf<Pair<List<String>, Int>>()
        val finishOrder = mutableListOf<String>()
        var running = 0
        var peak = 0
        val results = ToolBatch(calls).run(
            onStarted = { started, count -> starts += started.map { calls[it].id } to count },
            onFinished = { i, _ -> finishOrder += calls[i].id }
        ) { c ->
            running++
            peak = maxOf(peak, running)
            val start = testScheduler.currentTime
            delay(millis[c.id] ?: 100L)
            spans[c.id] = Span(start, testScheduler.currentTime)
            running--
            ToolResult(true, "out-${c.id}")
        }
        return Ran(results, spans, starts, finishOrder, peak)
    }

    @Test
    fun `calls on the same file keep their order`() = runTest {
        val ran = runBatch(listOf(write("w1", "index.html"), edit("e2", "./index.html"), write("w3", "css/../index.html")))

        assertThat(ran.span("e2").start).isAtLeast(ran.span("w1").end)
        assertThat(ran.span("w3").start).isAtLeast(ran.span("e2").end)
        assertThat(ran.peak).isEqualTo(1)
    }

    @Test
    fun `a read waits for the write of its file and for nothing else`() = runTest {
        val ran = runBatch(listOf(write("w", "app.js"), read("ra", "app.js"), read("rb", "style.css")))

        assertThat(ran.span("ra").start).isAtLeast(ran.span("w").end)
        assertThat(ran.span("rb").overlaps(ran.span("w"))).isTrue()
        // The indicator is told what runs together: two at first, then the read alone.
        assertThat(ran.starts).containsExactly(listOf("w", "rb") to 2, listOf("ra") to 1).inOrder()
    }

    @Test
    fun `a write waits for earlier reads of its file or folder`() = runTest {
        val ran = runBatch(
            listOf(
                read("r", "index.html"),
                call("l", "list_files", """{"path":"css"}"""),
                write("w1", "index.html"),
                write("w2", "css/site.css"),
                write("w3", "app.js")
            )
        )

        assertThat(ran.span("w1").start).isAtLeast(ran.span("r").end)
        assertThat(ran.span("w2").start).isAtLeast(ran.span("l").end)
        assertThat(ran.span("w3").start).isEqualTo(0L)
    }

    @Test
    fun `writes to different files run together`() = runTest {
        val ran = runBatch(listOf(write("a", "index.html"), write("b", "css/style.css"), write("c", "js/app.js")))

        assertThat(ran.spans.values.map { it.start }).containsExactly(0L, 0L, 0L)
        assertThat(ran.peak).isEqualTo(3)
        assertThat(ran.starts).containsExactly(listOf("a", "b", "c") to 3)
    }

    @Test
    fun `a barrier runs alone, in place`() = runTest {
        val ran = runBatch(
            listOf(
                read("r1", "a.html"),
                read("r2", "b.html"),
                call("q", "ask_user", """{"question":"Which colour?"}"""),
                read("r3", "c.html"),
                write("w", "d.html")
            )
        )
        val q = ran.span("q")

        assertThat(ran.span("r1").overlaps(ran.span("r2"))).isTrue()
        for (id in listOf("r1", "r2")) assertThat(ran.span(id).end).isAtMost(q.start)
        for (id in listOf("r3", "w")) assertThat(ran.span(id).start).isAtLeast(q.end)
        assertThat(ran.spans.filterKeys { it != "q" }.values.none { it.overlaps(q) }).isTrue()
        assertThat(ran.starts).contains(listOf("q") to 1)
    }

    @Test
    fun `a tool it does not know is a barrier`() = runTest {
        val ran = runBatch(
            listOf(
                write("a", "a.html"),
                call("x", "frobnicate", """{"path":"b.html"}"""),
                write("b", "b.html"),
                call("m", "mcp__github__create_issue", """{"title":"Bug"}"""),
                read("c", "c.html")
            )
        )

        assertThat(ran.span("x").start).isAtLeast(ran.span("a").end)
        assertThat(ran.span("b").start).isAtLeast(ran.span("x").end)
        assertThat(ran.span("m").start).isAtLeast(ran.span("b").end)
        assertThat(ran.span("c").start).isAtLeast(ran.span("m").end)
        assertThat(ran.peak).isEqualTo(1)
    }

    @Test
    fun `interactive, outward-facing and unpinned calls are barriers`() {
        val names = listOf(
            "ask_user", "request_approval", "run_command", "bash", "open_pull_request", "use_browser",
            "spawn_subagent", "mcp__github__create_issue", "save_memory", "add_ui_kit", "frobnicate"
        )
        for (name in names) {
            assertWithMessage(name).that(ToolBatch.accessOf(call(name, name)).barrier).isTrue()
        }
        // A write that cannot be pinned to a file in the project.
        for (args in listOf("""{"content":"x"}""", """{"path":"/sdcard/x.html","content":"x"}""", """{"path":".."}""", "{not json")) {
            assertWithMessage(args).that(ToolBatch.accessOf(call("w", "write_file", args)).barrier).isTrue()
        }
        // Reads that may look at any file wait for every earlier write.
        assertThat(ToolBatch.accessOf(call("s", "screenshot_preview"))).isEqualTo(ToolBatch.Access.READS_ALL)
        assertThat(ToolBatch.accessOf(call("c", "check_syntax", """{"path":"a.html"}"""))).isEqualTo(ToolBatch.Access.READS_ALL)
    }

    private fun spawn(id: String, type: String) =
        call(id, "spawn_subagent", """{"agent_type":"$type","description":"d","prompt":"p"}""")

    @Test
    fun `read-only subagents run side by side and a build subagent runs alone`() = runTest {
        assertThat(ToolBatch.accessOf(spawn("e", "explore")).barrier).isFalse()
        assertThat(ToolBatch.accessOf(spawn("p", "plan")).barrier).isFalse()
        assertThat(ToolBatch.accessOf(spawn("r", "review")).barrier).isFalse()
        assertThat(ToolBatch.accessOf(spawn("b", "build")).barrier).isTrue()
        assertThat(ToolBatch.accessOf(spawn("g", "general")).barrier).isTrue()
        assertThat(ToolBatch.accessOf(spawn("u", "nonsense")).barrier).isTrue()

        val ran = runBatch(listOf(spawn("e", "explore"), spawn("r", "review"), read("x", "a.html"), spawn("b", "build")))
        assertThat(ran.span("e").overlaps(ran.span("r"))).isTrue()
        assertThat(ran.span("e").overlaps(ran.span("x"))).isTrue()
        assertThat(ran.span("b").start).isAtLeast(ran.span("r").end)
        assertThat(ran.span("b").start).isAtLeast(ran.span("x").end)
    }

    @Test
    fun `a read-only subagent waits for an earlier write and holds back a later one`() = runTest {
        val ran = runBatch(listOf(write("w1", "index.html"), spawn("e", "plan"), write("w2", "app.js")))
        assertThat(ran.span("e").start).isAtLeast(ran.span("w1").end)
        assertThat(ran.span("w2").start).isAtLeast(ran.span("e").end)
    }

    @Test
    fun `results keep the calls' order whatever order they finish in`() = runTest {
        val ran = runBatch(
            listOf(read("a", "a.html"), read("b", "b.html"), read("c", "c.html")),
            millis = mapOf("a" to 300L, "b" to 100L, "c" to 200L)
        )

        assertThat(ran.finishOrder).containsExactly("b", "c", "a").inOrder()
        assertThat(ran.results.map { it.output }).containsExactly("out-a", "out-b", "out-c").inOrder()
    }

    @Test
    fun `at most four calls run at once`() = runTest {
        val ran = runBatch((1..6).map { read("r$it", "f$it.html") })

        assertThat(ran.peak).isEqualTo(ToolBatch.MAX_PARALLEL)
        assertThat((1..4).map { ran.span("r$it").start }).containsExactly(0L, 0L, 0L, 0L)
        assertThat(ran.span("r5").start).isEqualTo(100L)
        assertThat(ran.span("r6").start).isEqualTo(100L)
    }

    @Test
    fun `paths compare by file, whatever the spelling`() {
        assertThat(ToolBatch.normalisePath("./css//Site.css")).isEqualTo("css/site.css")
        assertThat(ToolBatch.normalisePath("css\\..\\index.html")).isEqualTo("index.html")
        assertThat(ToolBatch.normalisePath(".")).isEqualTo("")
        assertThat(ToolBatch.normalisePath("/data/user/0/app/index.html")).isEqualTo("")
        assertThat(ToolBatch.normalisePath("../outside.html")).isEqualTo("")

        // A folder covers its files, a batch read each file it names, a move both ends.
        val batch = ToolBatch(
            listOf(
                call("d", "delete_file", """{"path":"css"}"""),
                call("r", "read_file", """{"path":"index.html","paths":["css/site.css"]}"""),
                call("g", "grep", """{"pattern":"x","path":"js"}"""),
                call("m", "move_file", """{"source":"js/app.js","destination":"app.js"}""")
            )
        )
        assertThat(batch.waitsFor).containsExactly(emptyList<Int>(), listOf(0), emptyList<Int>(), listOf(2)).inOrder()
    }
}
