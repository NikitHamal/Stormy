package com.oro.app.data.ai.agent

import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.agents.Subagents
import com.oro.app.data.ai.agents.ToolCapability
import com.oro.app.data.ai.mcp.McpNames
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.ToolAliases
import com.oro.app.data.ai.tools.ToolResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One round of tool calls, run in dependency order.
 *
 * Each call is classified by what it touches ([Access]). Reads and writes are
 * scoped to project paths. A call that waits on the user or acts beyond the
 * project files (ask_user, request_approval, run_command, open_pull_request,
 * use_browser, the coordinator tools (agents_status, follow_up,
 * wait_for_agent), MCP tools, a subagent that may write), and any tool not known
 * here, is a barrier. A subagent that can only read (explore, plan, review)
 * reads the whole project, so several run side by side. A call starts once
 * every earlier call it depends on has finished:
 *  - a read waits for earlier writes of an overlapping path;
 *  - a write waits for earlier reads and writes of an overlapping path, so a
 *    file is never read half-written or changed under an earlier read;
 *  - a barrier waits for everything before it and everything after it waits
 *    for the barrier, so it runs alone, in place.
 *
 * `[write a, read a, read b]` starts the write and `read b` together and
 * `read a` once the write has landed. At most [maxParallel] calls run at
 * once, and results keep the calls' order whatever order they finish in.
 *
 * Pure: no Android and no I/O of its own, so the ordering is unit-tested.
 */
class ToolBatch(
    private val calls: List<ToolCallResponse>,
    private val maxParallel: Int = MAX_PARALLEL
) {
    init {
        require(maxParallel >= 1) { "maxParallel must be at least 1, was $maxParallel" }
    }

    /** What each call touches, by index. */
    val access: List<Access> = calls.map { accessOf(it) }

    /** For each call, the earlier calls (indices, ascending) it waits for. */
    val waitsFor: List<List<Int>> = access.indices.map { j ->
        (0 until j).filter { i -> access[j].mustWaitFor(access[i]) }
    }

    /**
     * Run every call through [execute]; the results come back in call order.
     *
     * [onStarted] gets the calls starting together (indices, ascending) and
     * how many are running counting them, before any of them runs.
     * [onFinished] gets each result as it arrives. Both are called from this
     * coroutine, one at a time, never from a call's own coroutine.
     */
    suspend fun run(
        onStarted: (started: List<Int>, running: Int) -> Unit = { _, _ -> },
        onFinished: (index: Int, result: ToolResult) -> Unit = { _, _ -> },
        execute: suspend (ToolCallResponse) -> ToolResult
    ): List<ToolResult> = coroutineScope {
        val results = arrayOfNulls<ToolResult>(calls.size)
        val started = BooleanArray(calls.size)
        val finished = BooleanArray(calls.size)
        val completions = Channel<Pair<Int, ToolResult>>(Channel.UNLIMITED)
        var running = 0
        var done = 0
        while (done < calls.size) {
            // Lowest index first: when the cap bites, the model's order decides.
            val ready = ArrayList<Int>()
            for (i in calls.indices) {
                if (running + ready.size >= maxParallel) break
                if (!started[i] && waitsFor[i].all { finished[it] }) ready += i
            }
            if (ready.isNotEmpty()) {
                ready.forEach { started[it] = true }
                running += ready.size
                onStarted(ready, running)
                for (i in ready) {
                    launch {
                        try {
                            completions.send(i to execute(calls[i]))
                        } catch (e: Throwable) {
                            // A call that ends without a result (cancelled from
                            // inside, say) must not leave the loop below waiting.
                            completions.close(e)
                            throw e
                        }
                    }
                }
            }
            // Something is always running here: with nothing running, the first
            // call not yet started has every earlier call finished. Take every
            // result already in, so calls freed together start together.
            var next: Pair<Int, ToolResult>? = completions.receive()
            while (next != null) {
                val (index, result) = next
                finished[index] = true
                results[index] = result
                running--
                done++
                onFinished(index, result)
                next = completions.tryReceive().getOrNull()
            }
        }
        results.map { it ?: ToolResult(false, "", "Tool did not run") }
    }

    /**
     * What one call touches. Paths are keys from [normalisePath]; "" is the
     * whole project.
     */
    data class Access(
        val reads: Set<String> = emptySet(),
        val writes: Set<String> = emptySet(),
        /** Runs alone: after everything before it, before everything after it. */
        val barrier: Boolean = false
    ) {
        /** Whether a call with this access must wait for [earlier], a call listed before it. */
        fun mustWaitFor(earlier: Access): Boolean =
            barrier || earlier.barrier ||
                overlap(earlier.writes, reads) ||
                overlap(earlier.writes, writes) ||
                overlap(earlier.reads, writes)

        companion object {
            val BARRIER = Access(barrier = true)

            /** May read any file: previews, audits, checks, searches, fetches. */
            val READS_ALL = Access(reads = setOf(""))
        }
    }

    companion object {
        /** Most calls running at once. */
        const val MAX_PARALLEL = 4

        /** Wait on the user or act beyond the project files: always alone. */
        private val BARRIERS = setOf(
            "ask_user", "request_approval", "run_command", "open_pull_request", "use_browser",
            "agents_status", "follow_up", "wait_for_agent"
        )

        /**
         * What a subagent may do and still count as a reader: its own context,
         * no file changes. BROWSER counts: the in-app browser reads the web,
         * not the project, and two browser-capable readers still run side by
         * side (one live browser run at a time; a second use_browser is told
         * the browser is busy and works on something else).
         */
        private val READER_CAPABILITIES = setOf(ToolCapability.READ, ToolCapability.GIT, ToolCapability.BROWSER)

        /** Writes, by the arguments naming the paths they change. */
        private val WRITES = mapOf(
            "write_file" to listOf("path"),
            "edit_file" to listOf("path"),
            "multi_edit" to listOf("path"),
            "delete_file" to listOf("path"),
            "move_file" to listOf("source", "destination")
        )

        /** Reads confined to their `path` (read_file adds `paths`); no path is the whole project. */
        private val SCOPED_READS = setOf("read_file", "list_files", "glob", "grep")

        private val json = Json { isLenient = true }

        /** Classify [call]. Anything not known to read or write by path is a barrier. */
        fun accessOf(call: ToolCallResponse): Access {
            val name = ToolAliases.canonicalName(call.function.name)
            if (name in BARRIERS || McpNames.isMcp(name)) return Access.BARRIER
            val args = parseArgs(call.function.arguments)?.let { ToolAliases.normalise(call.function.name, it).second }
            if (name == "spawn_subagent") return subagent(args?.text("agent_type"))
            WRITES[name]?.let { keys -> return writing(keys.map { args?.text(it) }) }
            if (name in SCOPED_READS) return reading(args, batch = name == "read_file")
            if (name in AgentTools.readOnly) return Access.READS_ALL
            return Access.BARRIER
        }

        /**
         * [raw] as a key: project-relative, "/"-separated, "." and ".."
         * resolved, lower-cased (the file system may not tell case apart).
         * "" is the whole project, and so is an absolute path or one that
         * climbs out of the root: either may name any file in the project.
         */
        internal fun normalisePath(raw: String): String {
            val path = raw.trim().replace('\\', '/')
            if (path.startsWith("/")) return ""
            val segments = ArrayList<String>()
            for (segment in path.split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (segments.isEmpty()) return "" else segments.removeAt(segments.lastIndex)
                    else -> segments += segment.lowercase()
                }
            }
            return segments.joinToString("/")
        }

        /**
         * A subagent of [type]: one that can only read (and look at git) reads
         * the whole project; one that may write, run commands or delegate, or
         * a type not known, runs alone.
         */
        private fun subagent(type: String?): Access {
            val definition = type?.let { Subagents[it] } ?: return Access.BARRIER
            return if (READER_CAPABILITIES.containsAll(definition.capabilities)) Access.READS_ALL else Access.BARRIER
        }

        /** Writes to [paths]; one that cannot be pinned to a file in the project is a barrier. */
        private fun writing(paths: List<String?>): Access {
            val keys = paths.map { path -> path?.let { normalisePath(it) } }
            if (keys.any { it.isNullOrEmpty() }) return Access.BARRIER
            return Access(writes = keys.filterNotNull().toSet())
        }

        /** A read of `path`, plus read_file's `paths`; none given is the whole project. */
        private fun reading(args: JsonObject?, batch: Boolean): Access {
            if (args == null) return Access.READS_ALL
            val paths = listOfNotNull(args.text("path")).toMutableList()
            if (batch) {
                when (val more = args["paths"]) {
                    null, JsonNull -> Unit
                    is JsonArray -> more.forEach { item ->
                        (item as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { paths += it }
                    }
                    // One string of paths, split several ways by the tool: assume any file.
                    else -> return Access.READS_ALL
                }
            }
            return Access(reads = paths.map { normalisePath(it) }.ifEmpty { listOf("") }.toSet())
        }

        private fun parseArgs(raw: String): JsonObject? =
            if (raw.isBlank()) JsonObject(emptyMap())
            else runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}

/** Whether any path in [a] is, contains or lies inside any path in [b] ("" contains all). */
private fun overlap(a: Set<String>, b: Set<String>): Boolean =
    a.any { x -> b.any { y -> x.isEmpty() || y.isEmpty() || x == y || y.startsWith("$x/") || x.startsWith("$y/") } }
