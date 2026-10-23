package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.Tool
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.community.CommunityHeaders.site
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Labs' official demos on Hugging Face Spaces that expose a programmatic
 * Gradio endpoint and relay to the lab's own API.
 *
 * Only CPU-hosted Spaces qualify: the model runs at the lab, so there is no
 * ZeroGPU quota, and the endpoint is the one the Space publishes for
 * `gradio_client` callers. The client speaks Gradio's queue protocol
 * (`sse_v3`), the one Gradio's own clients use, because it streams *diffs*:
 * the REST `/call` route re-sends every output whole on each update, and an
 * endpoint that echoes the conversation back (Hy3) would then re-send a
 * long agent history once per streamed chunk -- megabytes per turn.
 *
 *  - `GET {host}/config`: the endpoint's `fn_index` (cached per Space);
 *  - `POST {host}/gradio_api/queue/join` with `{data, fn_index, session_hash}`;
 *  - `GET {host}/gradio_api/queue/data?session_hash=...`: SSE `data:` frames
 *    `{"msg": ...}`. The first `process_generating` carries the outputs
 *    whole, later ones a list of edits per output (`replace`, `append`,
 *    `add`, `delete` at a path), and `process_completed` the final outputs.
 *
 * Each Space has an [HfSpaces.Adapter] for its own inputs and outputs.
 */
class HfSpaceClient(
    private val spaces: Map<AiProvider, HfSpaces.Space> = HfSpaces.ALL
) : CommunityChatClient {

    override fun takesTools(provider: AiProvider): Boolean = spaces[provider]?.adapter?.takesTools == true

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent> =
        stream(model, messages, null, null)

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>, reasoningLevel: String?): Flow<StreamEvent> =
        stream(model, messages, reasoningLevel, null)

    override fun stream(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        reasoningLevel: String?,
        tools: List<Tool>?
    ): Flow<StreamEvent> {
        val space = spaces[model.provider]
            ?: return flowOf(StreamEvent.Error("${model.provider.displayName} has no Space"))
        return CommunityHttp.guarded(space.site) {
            val data = space.adapter.inputs(
                CommunityClients.upstreamId(model), messages, tools?.takeIf { space.adapter.takesTools }, reasoningLevel
            ) ?: run { emit(StreamEvent.Error("${space.site}: nothing to send")); return@guarded }
            val fnIndex = fnIndex(space) ?: run {
                emit(StreamEvent.Error("${space.site}: the Space did not answer (it may be restarting)", retryable = true))
                return@guarded
            }
            val session = UUID.randomUUID().toString().replace("-", "").take(11)
            val join = Request.Builder().url("${space.host}/gradio_api/queue/join")
                .post(
                    buildJsonObject {
                        put("data", data)
                        put("fn_index", fnIndex)
                        put("session_hash", session)
                        put("event_data", JsonNull)
                        put("trigger_id", JsonNull)
                    }.toString().toRequestBody(JSON)
                )
                .header("Accept", "application/json")
                .site(space.host)
                .build()
            val joined = CommunityHttp.execute(join, AiHttp.client) { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404 || response.code == 422) fnIndexes.remove(space.host)
                    emit(CommunityHttp.httpError(space.site, response)); false
                } else true
            }
            if (!joined) return@guarded
            val listen = Request.Builder().url("${space.host}/gradio_api/queue/data?session_hash=$session")
                .get()
                .header("Accept", "text/event-stream")
                .site(space.host)
                .build()
            CommunityHttp.execute(listen, AiHttp.streaming) { response ->
                if (!response.isSuccessful) {
                    emit(CommunityHttp.httpError(space.site, response)); return@execute
                }
                val source = response.body?.source() ?: run {
                    emit(StreamEvent.Error("${space.site} sent an empty response", retryable = true)); return@execute
                }
                emit(StreamEvent.Started)
                val parser = QueueParser(space.adapter)
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    for (e in parser.onMessage(line.removePrefix("data:").trim())) {
                        emit(if (e is StreamEvent.Error) e.copy(message = "${space.site}: ${e.message}") else e)
                    }
                    if (parser.done) return@execute
                }
                // The stream closed without a completion: keep what arrived, or report the drop.
                parser.finish().forEach { e ->
                    emit(if (e is StreamEvent.Error) e.copy(message = "${space.site}: ${e.message}") else e)
                }
            }
        }
    }

    /** The Space's `fn_index` for the adapter's endpoint, from its `/config` (cached). */
    private suspend fun fnIndex(space: HfSpaces.Space): Int? {
        fnIndexes[space.host]?.let { return it }
        val request = Request.Builder().url("${space.host}/config").get()
            .header("Accept", "application/json")
            .site(space.host)
            .build()
        val found = runCatching {
            CommunityHttp.execute(request, AiHttp.client) { response ->
                if (!response.isSuccessful) null else fnIndexIn(response.body?.string().orEmpty(), space.endpoint)
            }
        }.getOrNull() ?: return null
        fnIndexes[space.host] = found
        return found
    }

    /**
     * Gradio's queue frames turned into Oro's stream: text and reasoning
     * deltas as the outputs grow, tool calls once the reply is complete
     * (partial arguments are never emitted).
     */
    class QueueParser(private val adapter: HfSpaces.Adapter) {
        private var outputs: List<JsonElement?>? = null
        private var content = ""
        private var reasoning = ""
        private var calls: List<ToolCallResponse> = emptyList()
        private var finishReason: String? = null
        var done = false
            private set

        /**
         * Stream events for one queue message. A message that changes nothing
         * visible -- a queue estimate, a heartbeat, a progress line, a Space
         * that answers in one piece (Laguna) still working -- comes back as a
         * [StreamEvent.RawSSE] keep-alive: the Space is alive, and a long queue
         * wait or a long silent reply must not trip the agent loop's idle
         * watchdog.
         */
        fun onMessage(data: String): List<StreamEvent> {
            if (done) return emptyList()
            val msg = runCatching { AiHttp.json.parseToJsonElement(data) as? JsonObject }.getOrNull() ?: return emptyList()
            val type = (msg["msg"] as? JsonPrimitive)?.contentOrNull
            val events = parse(msg, type)
            return if (events.isEmpty() && !done) listOf(StreamEvent.RawSSE(type ?: "message")) else events
        }

        private fun parse(msg: JsonObject, type: String?): List<StreamEvent> {
            return when (type) {
                "process_generating" -> {
                    val output = msg["output"] as? JsonObject ?: return emptyList()
                    if (msg.isFailure()) return fail(errorOf(output))
                    val raw = output["data"] as? JsonArray ?: return emptyList()
                    val current = outputs
                    outputs = if (current == null) raw.toList()
                    else raw.mapIndexed { i, edits ->
                        val ops = edits as? JsonArray ?: return@mapIndexed current.getOrNull(i)
                        GradioDiff.apply(current.getOrNull(i), ops)
                    }
                    read(complete = false)
                }
                "process_completed" -> {
                    val output = msg["output"] as? JsonObject
                    if (output == null || msg.isFailure()) return fail(output?.let { errorOf(it) })
                    (output["data"] as? JsonArray)?.let { outputs = it.toList() }
                    read(complete = true)
                }
                "unexpected_error" -> fail((msg["message"] as? JsonPrimitive)?.contentOrNull)
                "close_stream" -> finish()
                else -> emptyList() // estimation, process_starts, heartbeat, progress, log
            }
        }

        private fun read(complete: Boolean): List<StreamEvent> {
            val values = outputs ?: return emptyList()
            val frame = adapter.frame(JsonArray(values.map { it ?: JsonNull })) ?: return if (complete) finish() else emptyList()
            if (frame.error != null) return fail(frame.error)
            val out = mutableListOf<StreamEvent>()
            grow(reasoning, frame.reasoning)?.let { out += StreamEvent.ReasoningDelta(it) }
            if (frame.reasoning.startsWith(reasoning)) reasoning = frame.reasoning
            grow(content, frame.content)?.let { out += StreamEvent.ContentDelta(it) }
            if (frame.content.startsWith(content)) content = frame.content
            if (frame.toolCalls.isNotEmpty()) calls = frame.toolCalls
            frame.finishReason?.takeIf { it.isNotBlank() }?.let { finishReason = it }
            if (complete) out += finish()
            return out
        }

        /**
         * End of stream: tool calls, finish reason, completion -- or an error if
         * nothing came. The Space's own finish reason wins, so a reply cut off
         * by its output cap ("length") reaches the agent loop, which continues
         * it and never runs a cut-off call.
         */
        fun finish(): List<StreamEvent> {
            if (done) return emptyList()
            done = true
            if (content.isEmpty() && calls.isEmpty()) {
                return listOf(StreamEvent.Error("ended without a reply", retryable = true))
            }
            return buildList {
                if (calls.isNotEmpty()) add(StreamEvent.ToolCalls(calls))
                add(StreamEvent.FinishReason(finishReason ?: if (calls.isNotEmpty()) "tool_calls" else "stop"))
                add(StreamEvent.Completed)
            }
        }

        private fun fail(message: String?): List<StreamEvent> {
            done = true
            return listOf(
                StreamEvent.Error(
                    message?.takeIf { it.isNotBlank() && it != "null" } ?: "the Space reported an error (busy or out of quota)",
                    retryable = true
                )
            )
        }

        private fun JsonObject.isFailure(): Boolean = (this["success"] as? JsonPrimitive)?.contentOrNull == "false"

        private fun errorOf(output: JsonObject): String? =
            (output["error"] as? JsonPrimitive)?.contentOrNull ?: (output["title"] as? JsonPrimitive)?.contentOrNull

        /** The new tail of a cumulative value; null when it did not grow (or was rewritten). */
        private fun grow(sent: String, now: String): String? =
            if (now.length > sent.length && now.startsWith(sent)) now.substring(sent.length) else null
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private val fnIndexes = ConcurrentHashMap<String, Int>()

        /** The `id` of the dependency whose `api_name` is [endpoint] in a Space's `/config`. */
        fun fnIndexIn(config: String, endpoint: String): Int? {
            val root = runCatching { AiHttp.json.parseToJsonElement(config) as? JsonObject }.getOrNull() ?: return null
            val deps = root["dependencies"] as? JsonArray ?: return null
            deps.forEachIndexed { i, dep ->
                val obj = dep as? JsonObject ?: return@forEachIndexed
                if ((obj["api_name"] as? JsonPrimitive)?.contentOrNull == endpoint) {
                    return (obj["id"] as? JsonPrimitive)?.intOrNull ?: i
                }
            }
            return null
        }
    }
}

/**
 * Gradio's streaming diffs (`gradio.utils.diff`): a list of
 * `[action, path, value]` edits. `append` extends a string, `add` inserts a
 * list item or an object key, `replace` swaps a value, `delete` removes one.
 */
object GradioDiff {
    fun apply(value: JsonElement?, ops: JsonArray): JsonElement? {
        var current = value
        for (op in ops) {
            val edit = op as? JsonArray ?: continue
            val action = (edit.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: continue
            val path = (edit.getOrNull(1) as? JsonArray)?.toList().orEmpty()
            current = at(current, action, path, edit.getOrNull(2))
        }
        return current
    }

    private fun at(node: JsonElement?, action: String, path: List<JsonElement>, value: JsonElement?): JsonElement? {
        if (path.isEmpty()) return leaf(node, action, value)
        val key = path.first() as? JsonPrimitive ?: return node
        val rest = path.drop(1)
        return when (node) {
            is JsonArray -> {
                val i = key.intOrNull ?: return node
                val list = node.toMutableList()
                if (rest.isEmpty()) {
                    when (action) {
                        "add" -> if (i in 0..list.size) list.add(i, value ?: JsonNull)
                        "delete" -> if (i in list.indices) list.removeAt(i)
                        else -> if (i in list.indices) list[i] = leaf(list[i], action, value) ?: JsonNull
                    }
                } else if (i in list.indices) {
                    list[i] = at(list[i], action, rest, value) ?: JsonNull
                }
                JsonArray(list)
            }
            is JsonObject -> {
                val k = key.contentOrNull ?: return node
                val map = node.toMutableMap()
                if (rest.isEmpty()) {
                    when (action) {
                        "delete" -> map.remove(k)
                        "add" -> map[k] = value ?: JsonNull
                        else -> map[k] = leaf(map[k], action, value) ?: JsonNull
                    }
                } else {
                    map[k] = at(map[k], action, rest, value) ?: JsonNull
                }
                JsonObject(map)
            }
            else -> node
        }
    }

    private fun leaf(node: JsonElement?, action: String, value: JsonElement?): JsonElement? = when (action) {
        "replace", "add" -> value
        "delete" -> null
        "append" -> {
            val tail = (value as? JsonPrimitive)?.contentOrNull
            val head = (node as? JsonPrimitive)?.contentOrNull
            when {
                tail == null -> node
                node == null || node is JsonNull -> JsonPrimitive(tail)
                head != null && (node as JsonPrimitive).isString -> JsonPrimitive(head + tail)
                else -> node
            }
        }
        else -> node
    }
}

/** The Spaces Oro talks to, and how each one's endpoint is called. */
object HfSpaces {

    data class Space(val site: String, val host: String, val endpoint: String, val adapter: Adapter)

    /** One frame of a Space's reply: cumulative text, reasoning and tool calls, or an error the Space returned as output. */
    data class Frame(
        val content: String,
        val reasoning: String = "",
        val toolCalls: List<ToolCallResponse> = emptyList(),
        val error: String? = null,
        /** The Space's finish reason when it reports one ("stop", "length", "tool_calls"). */
        val finishReason: String? = null
    )

    interface Adapter {
        /** Whether the endpoint takes OpenAI tool definitions (native function calling). */
        val takesTools: Boolean

        /** The endpoint's `data` array for this request, or null when there is nothing to send. */
        fun inputs(upstreamModel: String, messages: List<ChatRequestMessage>, tools: List<Tool>?, level: String?): JsonArray?

        /** Reads the outputs of one frame. */
        fun frame(outputs: JsonArray): Frame?
    }

    /**
     * Tencent's Hy3 (295B MoE, 21B active, 256K context) on its official
     * Space `tencent/Hy3`, whose `/chat` endpoint is the stateless API the
     * Space documents for programmatic callers: OpenAI-style history (tool
     * turns included) in, cumulative `(content, reasoning_content,
     * tool_calls, updated_history)` out, OpenAI tool definitions as a JSON
     * string, and `think_level` one of `no_think | low | high` (the Space's
     * default is high). The new turn must be a non-empty user message, so a
     * turn that follows tool results is sent as "Continue." after them.
     */
    object Hy3 : Adapter {
        override val takesTools: Boolean = true

        const val CONTINUE = "Continue."

        val LEVELS = listOf("no_think", "low", "high")

        override fun inputs(upstreamModel: String, messages: List<ChatRequestMessage>, tools: List<Tool>?, level: String?): JsonArray? {
            val system = messages.filter { it.role == "system" }.mapNotNull { it.content?.takeIf(String::isNotBlank) }
                .joinToString("\n\n")
            val turns = messages.filter { it.role != "system" }
            if (turns.isEmpty()) return null
            val last = turns.last()
            val lastUserText = last.content?.trim().orEmpty()
            val (history, message) = if (last.role == "user" && lastUserText.isNotEmpty()) {
                turns.dropLast(1) to lastUserText
            } else {
                turns to CONTINUE
            }
            return buildJsonArray {
                add(message)
                add(system)
                add(openAiHistory(history))
                add(level?.takeIf { it in LEVELS } ?: "high")
                add(JsonNull)  // temperature: the server's default
                add(0)         // max_tokens: the server's default
                add(0)         // top_p: the server's default
                add(JsonNull)  // preserved_thinking: the server's default
                add(tools?.takeIf { it.isNotEmpty() }?.let { toolsJson(it).toString() }.orEmpty())
            }
        }

        override fun frame(outputs: JsonArray): Frame? {
            val out = outputs.firstOrNull() as? JsonArray ?: return null
            fun text(i: Int) = ((out.getOrNull(i) as? JsonPrimitive)?.contentOrNull).orEmpty()
            return Frame(content = text(0), reasoning = text(1), toolCalls = toolCalls(out.getOrNull(2) as? JsonArray, "hy3"))
        }
    }

    /**
     * OpenBMB's MiniCPM-V 4.7 (35B-A3B MoE, images) on its official Space
     * `openbmb/MiniCPM-V-4.7-Demo`, whose `/predict` relays to OpenBMB's own
     * API with native tool calling: OpenAI `tools` in, cumulative
     * `{content, reasoning, finish_reason, tool_calls}` out. (Tool-call text
     * written by the model is swallowed by the upstream parser, so this site
     * cannot use the text protocol -- native tools it is.)
     *
     * History is `[user, assistant, files, tool_messages]` turns, the tool
     * exchange of a turn sitting between its user text and its final answer,
     * and the exchange after the newest user message goes in `tool_messages`.
     * There is no system field: instructions ride at the top of the first
     * user turn.
     */
    object MiniCpm : Adapter {
        override val takesTools: Boolean = true

        /** The Space's own rule for function names; any other name fails the whole request. */
        private val NAME = Regex("[A-Za-z0-9_-]{1,64}")

        override fun inputs(upstreamModel: String, messages: List<ChatRequestMessage>, tools: List<Tool>?, level: String?): JsonArray? {
            val plan = plan(messages) ?: return null
            val offered = tools?.filter { NAME.matches(it.function.name) }?.distinctBy { it.function.name }
            return buildJsonArray {
                add(plan.message)
                addJsonArray {
                    plan.history.forEach { turn ->
                        addJsonArray {
                            add(turn.user)
                            add(turn.answer)
                            addJsonArray { }
                            add(openAiHistory(turn.exchange))
                        }
                    }
                }
                addJsonArray { }                    // files
                add(level != "off")                 // thinking_mode (the Space's default is on)
                add(16_384)                         // max_new_tokens (the Space's cap)
                add(0.7); add(0.8); add(100)        // temperature, top_p, top_k: the Space's defaults
                add(64)                             // max_frames
                add("Sampling")                     // generation_mode
                add(true)                           // streaming_mode
                add(2)                              // beam_width
                add("4x")                           // downsample_mode
                add(JsonNull)                       // max_slice_nums
                if (offered.isNullOrEmpty()) add(JsonNull) else add(toolsJson(offered))
                add("auto")                         // tool_choice
                if (plan.exchange.isEmpty()) add(JsonNull) else add(openAiHistory(plan.exchange))
                add(upstreamModel)                  // model_name
            }
        }

        override fun frame(outputs: JsonArray): Frame? {
            val out = outputs.firstOrNull() as? JsonObject ?: return null
            return Frame(
                content = (out["content"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                reasoning = (out["reasoning"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                toolCalls = toolCalls(out["tool_calls"] as? JsonArray, "minicpm"),
                finishReason = (out["finish_reason"] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
            )
        }

        /** One finished turn: the user's text, the tool exchange it caused, and the final answer. */
        data class Turn(val user: String, val exchange: List<ChatRequestMessage>, val answer: String)

        /** The request: earlier turns, the newest user text, and the tool exchange since it. */
        data class Plan(val history: List<Turn>, val message: String, val exchange: List<ChatRequestMessage>)

        /**
         * Splits Oro's conversation into MiniCPM's turns. A tool call without
         * its result (an interrupted run) is dropped with it, since the Space
         * refuses unanswered calls; consecutive user turns merge.
         */
        fun plan(messages: List<ChatRequestMessage>): Plan? {
            val system = messages.filter { it.role == "system" }.mapNotNull { it.content?.takeIf(String::isNotBlank) }
                .joinToString("\n\n")
            val turns = mutableListOf<Turn>()
            var user: StringBuilder? = null
            var exchange = mutableListOf<ChatRequestMessage>()
            var answer = StringBuilder()

            fun close() {
                val u = user ?: return
                turns += Turn(u.toString(), complete(exchange), answer.toString())
                user = null
                exchange = mutableListOf()
                answer = StringBuilder()
            }

            for (m in messages) {
                when (m.role) {
                    "user" -> {
                        val text = m.content.orEmpty()
                        val open = user
                        if (open != null && exchange.isEmpty() && answer.isEmpty()) open.append("\n\n").append(text)
                        else { close(); user = StringBuilder(text) }
                    }
                    "assistant" -> {
                        if (user == null) continue
                        if (!m.toolCalls.isNullOrEmpty()) {
                            if (answer.isNotEmpty()) {
                                // Text before calls in one turn: keep it with the calls.
                                exchange += m.copy(content = listOfNotNull(answer.toString(), m.content).joinToString("\n\n"))
                                answer = StringBuilder()
                            } else exchange += m
                        } else m.content?.takeIf { it.isNotBlank() }?.let {
                            if (answer.isNotEmpty()) answer.append("\n\n")
                            answer.append(it)
                        }
                    }
                    "tool" -> if (user != null) exchange += m
                }
            }
            val last = user ?: return null
            val message = last.toString().ifBlank { Hy3.CONTINUE }
            val pending = complete(exchange)
            val history = turns.toMutableList()
            // A trailing answer means the model spoke last: ask it to go on.
            val (finalMessage, finalExchange) = if (answer.isNotEmpty()) {
                history += Turn(message, pending, answer.toString())
                Hy3.CONTINUE to emptyList<ChatRequestMessage>()
            } else message to pending
            val withInstructions = if (system.isEmpty()) history to finalMessage else {
                val block = "<instructions>\n$system\n</instructions>\n\n"
                if (history.isEmpty()) history to block + finalMessage
                else (listOf(history.first().copy(user = block + history.first().user)) + history.drop(1)) to finalMessage
            }
            return Plan(withInstructions.first, withInstructions.second, finalExchange)
        }

        /** The exchange with only calls that have results (and results whose call is present). */
        private fun complete(exchange: List<ChatRequestMessage>): List<ChatRequestMessage> {
            val answered = exchange.filter { it.role == "tool" }.mapNotNull { it.toolCallId }.toSet()
            val out = mutableListOf<ChatRequestMessage>()
            var kept = emptySet<String>()
            for (m in exchange) {
                if (m.role == "assistant") {
                    val calls = m.toolCalls.orEmpty().filter { it.id.isNotBlank() && it.id in answered }
                    kept = calls.map { it.id }.toSet()
                    if (calls.isNotEmpty() && calls.size == m.toolCalls.orEmpty().size) out += m.copy(toolCalls = calls)
                    else kept = emptySet()
                } else if (m.toolCallId in kept) out += m
            }
            return out
        }
    }

    /**
     * Poolside's Laguna S 2.1 (agentic coding) on its official Space
     * `poolside/Laguna-S-2.1`, whose `/chat` takes OpenAI `messages` and a
     * `reasoning` switch and forwards them to OpenRouter -- without `tools`.
     *
     * The model knows GLM's tool template, though: given the tools in that
     * template it writes clean `<tool_call>name<arg_key>k</arg_key><arg_value>v`
     * `</arg_value></tool_call>` blocks (5 of 5 live, against 1 of 5 for Oro's
     * text format, which it mixes with GLM's). So the adapter takes Oro's tools,
     * puts them in the system message in that template, writes earlier calls
     * the same way, and leaves the reply's blocks to the agent loop, which reads
     * them -- and checks them for cut-off calls -- like any text-protocol reply.
     *
     * The Space calls the model in one piece and streams a decorated terminal
     * transcript; the final `done` event carries the clean assistant message,
     * which is what Oro reads (so the answer arrives whole). A failure shows as
     * a `done` with no message, after an "error »" line.
     */
    object Laguna : Adapter {
        override val takesTools: Boolean = true

        private val ANSI = Regex("\u001B\\[[0-9;]*m")

        override fun inputs(upstreamModel: String, messages: List<ChatRequestMessage>, tools: List<Tool>?, level: String?): JsonArray? {
            val turns = Transcript.turns(messages, ::formatCall)
            val system = listOfNotNull(
                turns.filter { it.role == "system" }.joinToString("\n\n") { it.text }.takeIf { it.isNotBlank() },
                tools?.takeIf { it.isNotEmpty() }?.let { toolsPrompt(it) }
            ).joinToString("\n\n")
            val merged = mutableListOf<Pair<String, StringBuilder>>()
            turns.filter { it.role != "system" }.forEach { t ->
                val last = merged.lastOrNull()
                if (last != null && last.first == t.role) last.second.append("\n\n").append(t.text)
                else merged += t.role to StringBuilder(t.text)
            }
            while (merged.isNotEmpty() && merged.first().first != "user") merged.removeAt(0)
            if (merged.isEmpty()) return null
            if (merged.last().first != "user") merged += "user" to StringBuilder(Hy3.CONTINUE)
            return buildJsonArray {
                addJsonArray {
                    if (system.isNotEmpty()) addJsonObject {
                        put("role", "system")
                        put("content", system)
                    }
                    merged.forEach { (role, text) ->
                        addJsonObject {
                            put("role", role)
                            put("content", text.toString())
                        }
                    }
                }
                add(level != "off")
            }
        }

        /** The tools in GLM's template, the one Laguna follows. */
        fun toolsPrompt(tools: List<Tool>): String = buildString {
            append("# Tools\n\nYou may call one or more functions to assist with the user query.\n\n")
            append("You are provided with function signatures within <tools></tools> XML tags:\n<tools>\n")
            toolsJson(tools).forEach { append(it.toString()).append('\n') }
            append("</tools>\n\n")
            append("For each function call, output the function name and arguments within the following XML format:\n")
            append("<tool_call>{function-name}\n<arg_key>{arg-key-1}</arg_key>\n<arg_value>{arg-value-1}</arg_value>\n")
            append("<arg_key>{arg-key-2}</arg_key>\n<arg_value>{arg-value-2}</arg_value>\n...\n</tool_call>")
        }

        /** An earlier call in the same template: strings raw, anything else as JSON. */
        fun formatCall(name: String, argumentsJson: String): String = buildString {
            append("<tool_call>").append(name).append('\n')
            val args = runCatching { AiHttp.json.parseToJsonElement(argumentsJson.ifBlank { "{}" }) as? JsonObject }.getOrNull()
            args?.forEach { (key, value) ->
                val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: value.toString()
                append("<arg_key>").append(key).append("</arg_key>\n<arg_value>").append(text).append("</arg_value>\n")
            }
            append("</tool_call>")
        }

        override fun frame(outputs: JsonArray): Frame? {
            val out = outputs.firstOrNull() as? JsonObject ?: return null
            val event = (out["event"] as? JsonPrimitive)?.contentOrNull
            if (event != "done") {
                val line = (out["text"] as? JsonPrimitive)?.contentOrNull?.replace(ANSI, "").orEmpty()
                return if (line.trimStart().startsWith("error")) Frame("", error = line.substringAfter("»").trim().take(300))
                else Frame("")
            }
            val assistant = out["assistant"] as? JsonObject
                ?: return Frame("", error = "the Space could not reach the model")
            return Frame(
                content = (assistant["content"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                reasoning = reasoningText(assistant["reasoning_details"]),
                // OpenRouter's choice-level reason, when the Space passes it on.
                finishReason = listOf(out["finish_reason"], assistant["finish_reason"]).firstNotNullOfOrNull {
                    (it as? JsonPrimitive)?.contentOrNull?.takeIf { r -> r.isNotBlank() && r != "null" }
                }
            )
        }

        /** Text out of OpenRouter `reasoning_details` (a list of parts, an object, or a string). */
        fun reasoningText(details: JsonElement?): String = when (details) {
            is JsonPrimitive -> details.contentOrNull.orEmpty()
            is JsonArray -> details.joinToString("") { reasoningText(it) }
            is JsonObject -> listOf("text", "summary", "content", "reasoning").firstNotNullOfOrNull { k ->
                (details[k] as? JsonPrimitive)?.contentOrNull
            } ?: ""
            else -> ""
        }
    }

    val ALL: Map<AiProvider, Space> = mapOf(
        AiProvider.HY3 to Space("Hy3", "https://tencent-hy3.hf.space", "chat", Hy3),
        AiProvider.MINICPM to Space("MiniCPM", "https://openbmb-minicpm-v-4-7-demo.hf.space", "predict", MiniCpm),
        AiProvider.LAGUNA to Space("Laguna", "https://poolside-laguna-s-2-1.hf.space", "chat", Laguna)
    )

    /** OpenAI-style tool calls in a Space's output, with stable ids. */
    fun toolCalls(list: JsonArray?, prefix: String): List<ToolCallResponse> = list.orEmpty().mapIndexedNotNull { i, el ->
        val call = el as? JsonObject ?: return@mapIndexedNotNull null
        val fn = call["function"] as? JsonObject ?: return@mapIndexedNotNull null
        val name = (fn["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
        val args = when (val a = fn["arguments"]) {
            is JsonPrimitive -> a.contentOrNull.orEmpty()
            null, JsonNull -> ""
            else -> a.toString()
        }.ifBlank { "{}" }
        val id = (call["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "call_${prefix}_$i"
        ToolCallResponse(id = id, function = FunctionCall(name, args))
    }

    /** Oro's history as OpenAI messages (tool turns included), for Spaces that forward them upstream. */
    fun openAiHistory(turns: List<ChatRequestMessage>): JsonArray = buildJsonArray {
        for (m in turns) {
            when (m.role) {
                "user" -> addJsonObject {
                    put("role", "user")
                    put("content", m.content.orEmpty())
                }
                "assistant" -> addJsonObject {
                    put("role", "assistant")
                    put("content", m.content.orEmpty())
                    val calls = m.toolCalls.orEmpty()
                    if (calls.isNotEmpty()) putJsonArray("tool_calls") {
                        calls.forEach { c ->
                            addJsonObject {
                                put("id", c.id)
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", c.function.name)
                                    put("arguments", c.function.arguments.ifBlank { "{}" })
                                }
                            }
                        }
                    }
                }
                "tool" -> addJsonObject {
                    put("role", "tool")
                    put("tool_call_id", m.toolCallId.orEmpty())
                    put("content", m.content.orEmpty())
                }
            }
        }
    }

    /** OpenAI `tools` as a JSON array. */
    fun toolsJson(tools: List<Tool>): JsonArray = buildJsonArray {
        tools.forEach { t ->
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", t.function.name)
                    put("description", t.function.description)
                    put("parameters", t.function.parameters)
                }
            }
        }
    }
}
