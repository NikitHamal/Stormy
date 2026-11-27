package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.ChatStreamParser
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.community.CommunityHeaders.site
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * getunikey.ai's anonymous chat: a free trial of frontier models (Claude Opus,
 * GPT-5.x, Grok, DeepSeek V4, MiniMax M3, Kimi) with no login and no key,
 * ported from NEB's unikey_proxy.
 *
 * The wire format is OpenAI's: `{model, messages, stream}` to
 * `/api/anonymous/chat/completions`, answered with `chat.completion.chunk`
 * SSE (reasoning in `reasoning_content`), so [ChatStreamParser] reads it. The
 * trial quota (three messages) is keyed on a self-reported FingerprintJS
 * visitor id, so every request carries a fresh one. The endpoint refuses
 * `tools` and image parts ("anonymous chat does not support ..."), so these
 * models use Oro's text tool protocol and see text only.
 */
class UnikeyClient(
    private val endpoint: String = "https://www.getunikey.ai/api/anonymous/chat/completions"
) : CommunityChatClient {

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent> =
        CommunityHttp.guarded(SITE) {
            val requested = CommunityClients.upstreamId(model)
            val candidates = if (requested == "moonshotai/kimi-k2.7-code") {
                listOf("moonshotai/kimi-k2.7-code", "moonshotai/kimi-k3", "claude-opus-5-5", "gpt-6.1-sol")
            } else {
                listOf(requested, "claude-opus-5-5", "gpt-6.1-sol", "gpt-6-luna").distinct()
            }
            var lastError: StreamEvent.Error? = null
            for (cand in candidates) {
                val body = buildBody(cand, messages)
                    ?: run { emit(StreamEvent.Error("$SITE: nothing to send")); return@guarded }
                val ip = KeylessCrypto.fakeIpv4()
                val request = Request.Builder().url(endpoint)
                    .post(body.toString().toRequestBody(JSON))
                    .header("Accept", "text/event-stream")
                    .header("X-Anonymous-Fingerprint", fingerprint())
                    .header("X-Forwarded-For", ip)
                    .header("X-Real-IP", ip)
                    .site("https://www.getunikey.ai", "https://www.getunikey.ai/anonymous-chat")
                    .build()
                val succeeded = CommunityHttp.execute(request) { response ->
                    if (!response.isSuccessful) {
                        lastError = CommunityHttp.httpError(SITE, response)
                        return@execute false
                    }
                    val source = response.body?.source() ?: run {
                        lastError = StreamEvent.Error("$SITE sent an empty response", retryable = true)
                        return@execute false
                    }
                    val parser = ChatStreamParser()
                    var started = false
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isEmpty()) continue
                        if (!started) {
                            started = true
                            emit(StreamEvent.Started)
                        }
                        emit(StreamEvent.RawSSE(data))
                        val events = parser.onData(data)
                        for (event in events) {
                            if (event is StreamEvent.Error) {
                                if (!parser.hasOutput) {
                                    lastError = event.copy(message = "$SITE: ${event.message}")
                                    return@execute false
                                }
                                emit(event.copy(message = "$SITE: ${event.message}"))
                                return@execute true
                            }
                            emit(event)
                        }
                        if (events.any { it is StreamEvent.Completed }) return@execute true
                    }
                    if (!parser.hasOutput) {
                        lastError = StreamEvent.Error("$SITE ended without a reply", retryable = true)
                        return@execute false
                    }
                    parser.finish().forEach { emit(it) }
                    true
                }
                if (succeeded) return@guarded
            }
            emit(lastError ?: StreamEvent.Error("$SITE ended without a reply", retryable = true))
        }

    companion object {
        const val SITE = "UniKey"
        private val JSON = "application/json".toMediaType()

        /** A FingerprintJS-shaped visitor id; new for every request (the trial is three messages each). */
        fun fingerprint(): String = "rnd" + UUID.randomUUID().toString().replace("-", "")

        /**
         * Roles are kept (the endpoint honours system messages). Earlier tool
         * traffic arrives as text through [Transcript]; consecutive turns of
         * one role are merged so user and assistant alternate.
         */
        fun buildBody(upstreamModel: String, messages: List<ChatRequestMessage>): JsonObject? {
            val turns = Transcript.turns(messages)
            if (turns.none { it.role == "user" }) return null
            val merged = mutableListOf<Pair<String, StringBuilder>>()
            turns.forEach { t ->
                val last = merged.lastOrNull()
                if (last != null && last.first == t.role) last.second.append("\n\n").append(t.text)
                else merged += t.role to StringBuilder(t.text)
            }
            return buildJsonObject {
                put("model", upstreamModel)
                putJsonArray("messages") {
                    merged.forEach { (role, text) ->
                        addJsonObject {
                            put("role", role)
                            put("content", text.toString())
                        }
                    }
                }
                put("stream", true)
            }
        }
    }
}
