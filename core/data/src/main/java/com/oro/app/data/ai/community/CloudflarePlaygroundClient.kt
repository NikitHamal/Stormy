package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cloudflare's AI Playground (playground.ai.cloudflare.com): Workers AI
 * models behind a public demo, no account and no key.
 *
 * The playground is an Agents SDK app: one Durable Object per chat, reached
 * over a WebSocket at `/agents/playground/{name}`. On connect the agent
 * sends its identity and state; the client configures it with the `setConfig`
 * RPC (`{"type":"rpc","method":"setConfig","args":[config]}` -- a plain
 * state update is rejected) and then sends a `cf_agent_use_chat_request`
 * whose body is an AI SDK `useChat` request. The reply arrives as
 * `cf_agent_use_chat_response` frames whose `body` is one AI SDK UI-stream
 * chunk ([AiSdkStreamParser]); `done: true` ends it.
 *
 * Limits learned by probing (2026-10-23): the system prompt goes in the
 * config and may be long (44 KB checked); earlier turns may be long (200 KB
 * across turns answered correctly); the newest user message must stay under
 * 6,000 characters, so [Messages.build] moves the bulk of a long final turn
 * into earlier turns. Models are capped per minute across all of the
 * playground's visitors, and DeepSeek V4, Kimi and GLM-5.2 are reserved for
 * bring-your-own-gateway use (the agent silently answers with its default).
 * No client tools: the agent's own tools are MCP servers it runs itself, so
 * Oro uses its text tool protocol here.
 */
class CloudflarePlaygroundClient(
    private val host: String = "playground.ai.cloudflare.com"
) : CommunityChatClient {

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent> = callbackFlow {
        val upstream = CommunityClients.upstreamId(model)
        val system = messages.filter { it.role == "system" }.mapNotNull { it.content?.takeIf(String::isNotBlank) }
            .joinToString("\n\n")
        val uiMessages = Messages.build(Transcript.turns(messages))
        if (uiMessages == null) {
            trySend(StreamEvent.Error("$SITE: nothing to send"))
            close(); return@callbackFlow
        }
        val configId = "cfg_" + shortId()
        val chatId = "req_" + shortId()
        val parser = AiSdkStreamParser()
        val configured = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        val started = AtomicBoolean(false)

        fun finish(events: List<StreamEvent>) {
            if (!finished.compareAndSet(false, true)) return
            events.forEach { trySend(it) }
            close()
        }

        fun fail(message: String, code: Int? = null) =
            finish(listOf(StreamEvent.Error("$SITE: $message", httpCode = code, retryable = true)))

        val request = Request.Builder()
            .url("wss://$host/agents/playground/${agentName()}?_pk=${UUID.randomUUID()}")
            .header("Origin", "https://$host")
            .header("User-Agent", CommunityHeaders.USER_AGENT)
            .build()

        fun sendConfig(socket: WebSocket, initial: JsonObject?) {
            if (!configured.compareAndSet(false, true)) return
            socket.send(
                buildJsonObject {
                    put("type", "rpc")
                    put("id", configId)
                    put("method", "setConfig")
                    putJsonArray("args") { add(config(initial, upstream, system)) }
                }.toString()
            )
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (started.compareAndSet(false, true)) trySend(StreamEvent.Started)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (finished.get()) return
                val msg = runCatching { AiHttp.json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
                when ((msg["type"] as? JsonPrimitive)?.contentOrNull) {
                    // The agent's opening state: configure it with our model and instructions.
                    "cf_agent_state" -> sendConfig(webSocket, msg["state"] as? JsonObject)
                    "rpc" -> if ((msg["id"] as? JsonPrimitive)?.contentOrNull == configId) {
                        val ok = (msg["success"] as? JsonPrimitive)?.booleanOrNull == true
                        if (!ok) fail("the playground refused the model settings")
                        else webSocket.send(chatRequest(chatId, uiMessages).toString())
                    }
                    "cf_agent_use_chat_response" -> {
                        val body = (msg["body"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                        val isError = (msg["error"] as? JsonPrimitive)?.booleanOrNull == true
                        bodyError(body, isError)?.let { (message, code) -> fail(message, code); return }
                        parser.onData(body).forEach { trySend(it) }
                        if ((msg["done"] as? JsonPrimitive)?.booleanOrNull == true || parser.finished) {
                            when {
                                parser.failed -> fail(parser.error ?: "the reply broke off")
                                !parser.sawOutput -> fail("ended without a reply")
                                else -> finish(parser.finish())
                            }
                            webSocket.close(1000, null)
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                fail(t.message ?: "the connection failed", response?.code)
            }

            // The server closing first only reaches onClosing: OkHttp calls
            // onClosed after *we* close too, and reading has already stopped,
            // so without this a playground restart mid-reply left the run
            // waiting for the idle watchdog.
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                endAfterClose()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                endAfterClose()
            }

            private fun endAfterClose() {
                if (!finished.get()) {
                    if (parser.sawOutput && !parser.failed) finish(parser.finish()) else fail("the connection closed early")
                }
            }
        }

        // Pings notice a connection that died without a close frame (a phone
        // changing networks); a missed pong fails the socket into onFailure.
        val socket = SOCKETS.newWebSocket(request, listener)
        // Should the opening state not arrive, configure from defaults anyway.
        launch {
            delay(OPENING_STATE_WAIT_MS)
            if (!configured.get() && !finished.get()) sendConfig(socket, null)
        }
        awaitClose { socket.cancel() }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    /** Builds the AI SDK `messages` for the playground. */
    internal object Messages {
        /** The newest user message's cap (the playground's own is 6,000 characters). */
        const val LAST_TURN_LIMIT = 5_500

        /** Earlier turns are kept under this, so one oversized tool result never becomes a single huge message. */
        const val TURN_LIMIT = 60_000

        private const val BRIDGE = "Noted."

        /**
         * Alternating UI messages ending with a short user turn: system turns
         * are left out (they travel in the config), consecutive turns of one
         * role merged, and long user turns split into parts joined by brief
         * assistant acknowledgements, so the final part fits the cap.
         */
        fun build(turns: List<Transcript.Turn>): JsonArray? {
            val merged = mutableListOf<Pair<String, StringBuilder>>()
            turns.filter { it.role != "system" }.forEach { t ->
                val last = merged.lastOrNull()
                if (last != null && last.first == t.role) last.second.append("\n\n").append(t.text)
                else merged += t.role to StringBuilder(t.text)
            }
            while (merged.isNotEmpty() && merged.first().first != "user") merged.removeAt(0)
            if (merged.isEmpty()) return null
            if (merged.last().first != "user") merged += "user" to StringBuilder("Continue.")
            val out = mutableListOf<Pair<String, String>>()
            merged.forEachIndexed { i, (role, text) ->
                if (role != "user") {
                    out += role to text.toString()
                    return@forEachIndexed
                }
                val isLast = i == merged.lastIndex
                val parts = split(text.toString(), if (isLast) LAST_TURN_LIMIT else TURN_LIMIT, TURN_LIMIT)
                parts.forEachIndexed { j, part ->
                    if (j > 0) out += "assistant" to BRIDGE
                    out += "user" to part
                }
            }
            return buildJsonArray {
                out.forEach { (role, text) ->
                    addJsonObject {
                        put("id", AiSdkMessages.id())
                        put("role", role)
                        putJsonArray("parts") {
                            addJsonObject {
                                put("type", "text")
                                put("text", text)
                            }
                        }
                    }
                }
            }
        }

        /**
         * Splits [text] so its last part is at most [lastLimit] characters and
         * every earlier part at most [limit], cutting at line breaks where
         * possible.
         */
        fun split(text: String, lastLimit: Int, limit: Int): List<String> {
            if (text.length <= lastLimit) return listOf(text)
            val tailStart = cutPoint(text, text.length - lastLimit, text.length)
            val head = text.substring(0, tailStart)
            val tail = text.substring(tailStart)
            val parts = mutableListOf<String>()
            var start = 0
            while (start < head.length) {
                val end = if (head.length - start <= limit) head.length
                else cutPoint(head, start + limit / 2, start + limit)
                parts += head.substring(start, end)
                start = end
            }
            return parts.filter { it.isNotEmpty() } + tail
        }

        /** The last line break in `[from, to)`, or [to] when there is none. */
        private fun cutPoint(text: String, from: Int, to: Int): Int {
            val i = text.lastIndexOf('\n', (to - 1).coerceAtMost(text.length - 1))
            return if (i >= from.coerceAtLeast(0)) i + 1 else to.coerceIn(from.coerceAtLeast(0), text.length)
        }
    }

    companion object {
        const val SITE = "Cloudflare AI Playground"
        private const val OPENING_STATE_WAIT_MS = 4_000L

        /** The streaming client with pings: shares its pool and threads, adds a heartbeat. */
        private val SOCKETS by lazy {
            AiHttp.streaming.newBuilder().pingInterval(20, java.util.concurrent.TimeUnit.SECONDS).build()
        }

        private fun shortId() = UUID.randomUUID().toString().replace("-", "").take(8)

        private val NAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

        /** A fresh agent (chat) per request: the playground keeps no state Oro relies on. */
        fun agentName(): String = "playground-" + (1..13).map { NAME_CHARS.random() }.joinToString("")

        /** The agent's config: its own state (if it sent one) with our model and instructions. */
        fun config(initial: JsonObject?, model: String, system: String): JsonObject = buildJsonObject {
            initial?.forEach { (k, v) -> if (k !in OVERRIDDEN) put(k, v) }
            put("model", model)
            put("system", system.ifBlank { "You are a helpful assistant." })
            put("temperature", 0.6)
            put("stream", true)
            if (initial == null) {
                put("useExternalProvider", false)
                put("externalProvider", "openai")
                put("webSearchEnabled", false)
                put("generativeUIMode", "off")
            }
        }

        private val OVERRIDDEN = setOf("model", "system", "temperature", "stream")

        fun chatRequest(id: String, messages: JsonArray): JsonObject = buildJsonObject {
            put("type", "cf_agent_use_chat_request")
            put("id", id)
            putJsonObject("init") {
                put("method", "POST")
                put(
                    "body",
                    buildJsonObject {
                        put("messages", messages)
                        put("trigger", "submit-message")
                    }.toString()
                )
            }
        }

        /**
         * An error the playground sent instead of a stream chunk: a body with
         * a `message` and no chunk `type` ("The model is currently rate
         * limited ..."), or a frame flagged `error`. Null for ordinary chunks.
         */
        fun bodyError(body: String, flagged: Boolean): Pair<String, Int?>? {
            val obj = runCatching { AiHttp.json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            val type = (obj?.get("type") as? JsonPrimitive)?.contentOrNull
            if (type != null && !flagged) return null
            val message = (obj?.get("message") as? JsonPrimitive)?.contentOrNull
                ?: (obj?.get("error") as? JsonPrimitive)?.contentOrNull
                ?: if (flagged) body.take(300).ifBlank { "the playground reported an error" } else return null
            val code = when {
                message.contains("rate limit", ignoreCase = true) -> 429
                message.contains("too long", ignoreCase = true) -> 400
                // "Safety check unavailable. Please try again shortly." -- the playground's input moderation.
                message.contains("unavailable", ignoreCase = true) || message.contains("try again", ignoreCase = true) -> 503
                else -> null
            }
            return message.take(300) to code
        }
    }
}
