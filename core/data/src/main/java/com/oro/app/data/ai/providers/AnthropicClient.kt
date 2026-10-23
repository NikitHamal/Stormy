package com.oro.app.data.ai.providers

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.ChatStreamParser
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.Tool
import com.oro.app.data.ai.ToolCallResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Anthropic Messages API (`POST /v1/messages`, streaming), for Anthropic and
 * the many vendors that expose an Anthropic-compatible endpoint.
 *
 * Maps the app's OpenAI-shaped conversation onto Messages: system turns move
 * to the top-level `system` field, assistant `tool_calls` become `tool_use`
 * blocks, `tool` results become `tool_result` blocks in a user turn, and
 * consecutive same-role turns are merged (the API wants them alternating).
 * Streamed `text_delta` / `thinking_delta` / `input_json_delta` events come
 * back as the same [StreamEvent]s the OpenAI path emits, so the agent loop
 * cannot tell the protocols apart.
 */
class AnthropicClient(
    private val name: String,
    baseUrl: String,
    private val apiKey: String,
    private val headers: Map<String, String> = emptyMap()
) {
    private val root = baseUrl.trim().trimEnd('/').let { if (it.endsWith("/v1")) it else "$it/v1" }

    fun stream(
        model: String,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>?,
        temperature: Float?,
        maxTokens: Int,
        /** Claude's `output_config.effort` (low/medium/high), or null to leave it to the model. */
        effort: String? = null
    ): Flow<StreamEvent> = flow {
        val body = buildBody(model, messages, tools, temperature, maxTokens, effort)
        val request = requestBuilder("$root/messages")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(AiHttp.jsonMediaType))
            .build()
        val call = AiHttp.streaming.newCall(request)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(errorFor(response.code, response.header("retry-after"), runCatching { response.body?.string() }.getOrNull()))
                    return@use
                }
                emit(StreamEvent.Started)
                val source = response.body?.source() ?: run {
                    emit(StreamEvent.Error("$name sent an empty response", retryable = true)); return@use
                }
                val parser = AnthropicStreamParser(name)
                while (!parser.finished) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    emit(StreamEvent.RawSSE(data))
                    parser.onData(data).forEach { emit(it) }
                }
                parser.finish().forEach { emit(it) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(StreamEvent.Error("$name: ${e.message ?: e.javaClass.simpleName}", retryable = e is java.io.IOException))
        } finally {
            handle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    /** One entry of `GET /v1/models`: [maxInputTokens] is the context window, [maxOutputTokens] the output cap. */
    data class ModelInfo(val id: String, val name: String, val maxInputTokens: Int?, val maxOutputTokens: Int?)

    /** `GET /v1/models`, following pagination. */
    fun listModels(): Result<List<ModelInfo>> = runCatching {
        val out = mutableListOf<ModelInfo>()
        var after: String? = null
        repeat(10) {
            val url = "$root/models?limit=100" + (after?.let { "&after_id=$it" } ?: "")
            val response = AiHttp.client.newCall(requestBuilder(url).get().build()).execute()
            val text = response.use { r ->
                val b = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw java.io.IOException(errorFor(r.code, null, b).message)
                b
            }
            val page = parseModelPage(text)
            out += page.models
            after = page.nextAfter ?: return@runCatching out
        }
        out
    }

    private fun requestBuilder(url: String): Request.Builder {
        val b = Request.Builder().url(url)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Content-Type", "application/json")
        if (apiKey.isNotBlank()) b.header("x-api-key", apiKey)
        headers.forEach { (k, v) -> b.header(k, v) }
        return b
    }

    private fun errorFor(code: Int, retryAfter: String?, body: String?): StreamEvent.Error {
        val detail = ChatStreamParser.describeErrorBody(body)
        val message = when (code) {
            401 -> "$name rejected the API key (401)"
            403 -> "$name refused the request (403)" + (detail?.let { ": $it" } ?: "")
            429 -> "$name rate limit reached" + (detail?.let { ": $it" } ?: "")
            529 -> "$name is overloaded (529)"
            else -> detail?.let { "$name: $it ($code)" } ?: "$name request failed ($code)"
        }
        return StreamEvent.Error(
            message,
            httpCode = code,
            retryAfterMs = ChatStreamParser.retryAfterMs(retryAfter, body),
            retryable = code == 529 || ChatStreamParser.isRetryableStatus(code)
        )
    }

    companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"

        /** A page of the model list and the cursor for the next one (null on the last page). */
        data class ModelPage(val models: List<ModelInfo>, val nextAfter: String?)

        /**
         * Reads one model-list page. Anthropic's list has carried each model's
         * `max_input_tokens` (context window) and `max_tokens` (output cap)
         * since March 2026; other Messages-API servers may leave them out.
         */
        fun parseModelPage(text: String): ModelPage {
            val root = AiHttp.json.parseToJsonElement(text) as JsonObject
            val models = (root["data"] as? JsonArray).orEmpty().mapNotNull { m ->
                val o = m as? JsonObject ?: return@mapNotNull null
                val id = o.str("id") ?: return@mapNotNull null
                ModelInfo(
                    id = id,
                    name = o.str("display_name") ?: id,
                    maxInputTokens = (o["max_input_tokens"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 },
                    maxOutputTokens = (o["max_tokens"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
                )
            }
            val more = (root["has_more"] as? JsonPrimitive)?.contentOrNull == "true"
            return ModelPage(models, if (more) root.str("last_id") else null)
        }
        private val EPHEMERAL = buildJsonObject { put("type", "ephemeral") }

        /** Mark the last block of the last user turn as a cache breakpoint. */
        private fun withCacheBreakpoint(turns: JsonArray): JsonArray {
            val lastUser = turns.indexOfLast { (it as? JsonObject)?.get("role")?.let { r -> (r as? JsonPrimitive)?.content } == "user" }
            if (lastUser < 0) return turns
            return JsonArray(turns.mapIndexed { i, turn ->
                if (i != lastUser) return@mapIndexed turn
                val obj = turn as JsonObject
                val blocks = obj["content"] as? JsonArray ?: return@mapIndexed turn
                val marked = blocks.mapIndexed { j, b ->
                    if (j == blocks.lastIndex && b is JsonObject) JsonObject(b + ("cache_control" to EPHEMERAL)) else b
                }
                JsonObject(obj + ("content" to JsonArray(marked)))
            })
        }

        fun buildBody(
            model: String,
            messages: List<ChatRequestMessage>,
            tools: List<Tool>?,
            temperature: Float?,
            maxTokens: Int,
            effort: String? = null
        ): JsonObject = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("stream", true)
            temperature?.let { put("temperature", it.coerceIn(0f, 1f)) }
            // Effort without extended thinking: no thinking blocks to send back on tool turns.
            effort?.let { level -> putJsonObject("output_config") { put("effort", level) } }
            val system = messages.filter { it.role == "system" }.mapNotNull { it.content }.joinToString("\n\n")
            // Prompt caching: breakpoints after the tools, the system prompt
            // and the newest user turn (3 of the 4 allowed). Tools + system are
            // the stable prefix of every agent turn; the last-turn breakpoint
            // lets the next turn reuse the whole conversation so far. Cache
            // reads are billed at a tenth of input price.
            if (system.isNotBlank()) {
                putJsonArray("system") {
                    addJsonObject {
                        put("type", "text")
                        put("text", system)
                        put("cache_control", EPHEMERAL)
                    }
                }
            }
            put("messages", withCacheBreakpoint(mapMessages(messages)))
            if (!tools.isNullOrEmpty()) {
                putJsonArray("tools") {
                    tools.forEachIndexed { i, t ->
                        addJsonObject {
                            put("name", t.function.name)
                            put("description", t.function.description)
                            put("input_schema", t.function.parameters)
                            if (i == tools.lastIndex) put("cache_control", EPHEMERAL)
                        }
                    }
                }
            }
        }

        /** OpenAI-shaped turns -> alternating Messages turns with content blocks. */
        fun mapMessages(messages: List<ChatRequestMessage>): JsonArray {
            data class Turn(val role: String, val blocks: MutableList<JsonObject>)
            val turns = mutableListOf<Turn>()
            fun add(role: String, block: JsonObject) {
                val last = turns.lastOrNull()
                if (last != null && last.role == role) last.blocks += block else turns += Turn(role, mutableListOf(block))
            }
            fun text(t: String) = buildJsonObject { put("type", "text"); put("text", t) }

            for (m in messages) {
                when (m.role) {
                    "system" -> Unit
                    "user" -> {
                        m.content?.takeIf { it.isNotBlank() }?.let { add("user", text(it)) }
                        m.images.forEach { img ->
                            add("user", buildJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64"); put("media_type", "image/jpeg"); put("data", img)
                                }
                            })
                        }
                    }
                    "assistant" -> {
                        m.content?.takeIf { it.isNotBlank() }?.let { add("assistant", text(it)) }
                        m.toolCalls.orEmpty().forEach { c ->
                            add("assistant", buildJsonObject {
                                put("type", "tool_use")
                                put("id", c.id)
                                put("name", c.function.name)
                                put("input", parseInput(c.function.arguments))
                            })
                        }
                    }
                    "tool" -> add("user", buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", m.toolCallId.orEmpty())
                        put("content", m.content.orEmpty().ifEmpty { "(no output)" })
                    })
                }
            }
            // The first turn must be the user's.
            if (turns.firstOrNull()?.role == "assistant") turns.add(0, Turn("user", mutableListOf(text("Continue."))))
            return buildJsonArray {
                turns.forEach { t ->
                    addJsonObject {
                        put("role", t.role)
                        putJsonArray("content") { t.blocks.forEach { add(it) } }
                    }
                }
            }
        }

        private fun parseInput(arguments: String): JsonElement =
            runCatching { AiHttp.json.parseToJsonElement(arguments.ifBlank { "{}" }) }.getOrNull()
                ?.takeIf { it is JsonObject } ?: buildJsonObject { }
    }
}

/**
 * Messages SSE -> [StreamEvent]s. Tool-use blocks accumulate their
 * `input_json_delta` fragments and are emitted together at `message_stop`,
 * which is what the agent loop expects from the OpenAI path too.
 */
class AnthropicStreamParser(private val name: String = "Anthropic") {
    private class PendingTool(val id: String, val name: String, val json: StringBuilder = StringBuilder())

    private val tools = sortedMapOf<Int, PendingTool>()
    private var inputTokens = 0
    private var cachedTokens = 0
    private var outputTokens = 0
    private var stopReason: String? = null
    var finished = false
        private set

    fun onData(data: String): List<StreamEvent> {
        if (finished) return emptyList()
        val obj = runCatching { AiHttp.json.parseToJsonElement(data) as? JsonObject }.getOrNull() ?: return emptyList()
        val out = mutableListOf<StreamEvent>()
        when (obj.str("type")) {
            "message_start" -> {
                val usage = (obj["message"] as? JsonObject)?.get("usage") as? JsonObject
                inputTokens = usage.int("input_tokens") + usage.int("cache_read_input_tokens") + usage.int("cache_creation_input_tokens")
                cachedTokens = usage.int("cache_read_input_tokens")
            }
            "content_block_start" -> {
                val index = obj.int("index")
                val block = obj["content_block"] as? JsonObject
                if (block?.str("type") == "tool_use") {
                    tools[index] = PendingTool(block.str("id").orEmpty(), block.str("name").orEmpty())
                } else if (block?.str("type") == "text") {
                    block.str("text")?.takeIf { it.isNotEmpty() }?.let { out += StreamEvent.ContentDelta(it) }
                }
            }
            "content_block_delta" -> {
                val delta = obj["delta"] as? JsonObject
                when (delta?.str("type")) {
                    "text_delta" -> delta.str("text")?.takeIf { it.isNotEmpty() }?.let { out += StreamEvent.ContentDelta(it) }
                    "thinking_delta" -> delta.str("thinking")?.takeIf { it.isNotEmpty() }?.let { out += StreamEvent.ReasoningDelta(it) }
                    "input_json_delta" -> tools[obj.int("index")]?.json?.append(delta.str("partial_json").orEmpty())
                }
            }
            "message_delta" -> {
                (obj["delta"] as? JsonObject)?.str("stop_reason")?.let { stopReason = it }
                (obj["usage"] as? JsonObject)?.let { outputTokens = it.int("output_tokens") }
            }
            "message_stop" -> out += finish()
            "error" -> {
                val err = obj["error"] as? JsonObject
                val type = err?.str("type").orEmpty()
                finished = true
                out += StreamEvent.Error(
                    "$name: ${err?.str("message") ?: type.ifEmpty { "stream error" }}",
                    httpCode = if (type == "rate_limit_error") 429 else null,
                    retryable = type in setOf("overloaded_error", "api_error", "rate_limit_error")
                )
            }
        }
        return out
    }

    fun finish(): List<StreamEvent> {
        if (finished) return emptyList()
        finished = true
        val out = mutableListOf<StreamEvent>()
        if (tools.isNotEmpty()) {
            out += StreamEvent.ToolCalls(tools.values.map { t ->
                ToolCallResponse(id = t.id, type = "function", function = FunctionCall(t.name, t.json.toString().ifBlank { "{}" }))
            })
        }
        stopReason?.let {
            out += StreamEvent.FinishReason(
                when (it) {
                    "tool_use" -> "tool_calls"
                    "max_tokens" -> "length"
                    else -> "stop"
                }
            )
        }
        if (inputTokens > 0 || outputTokens > 0) out += StreamEvent.Usage(inputTokens, outputTokens, cachedTokens)
        out += StreamEvent.Completed
        return out
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject?.int(key: String): Int = (this?.get(key) as? JsonPrimitive)?.intOrNull ?: 0
