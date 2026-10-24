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
            val body = buildBody(CommunityClients.upstreamId(model), messages)
                ?: run { emit(StreamEvent.Error("$SITE: nothing to send")); return@guarded }
            val request = Request.Builder().url(endpoint)
                .post(body.toString().toRequestBody(JSON))
                .header("Accept", "text/event-stream")
                .header("X-Anonymous-Fingerprint", fingerprint())
                .site("https://www.getunikey.ai", "https://www.getunikey.ai/anonymous-chat")
                .build()
            CommunityHttp.execute(request) { response ->
                if (!response.isSuccessful) {
                    emit(CommunityHttp.httpError(SITE, response))
                    return@execute
                }
                val source = response.body?.source() ?: run {
                    emit(StreamEvent.Error("$SITE sent an empty response", retryable = true))
                    return@execute
                }
                emit(StreamEvent.Started)
                val parser = ChatStreamParser()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    emit(StreamEvent.RawSSE(data))
                    val events = parser.onData(data)
                    for (event in events) {
                        if (event is StreamEvent.Error) {
                            emit(event.copy(message = "$SITE: ${event.message}"))
                            return@execute
                        }
                        emit(event)
                    }
                    if (events.any { it is StreamEvent.Completed }) return@execute
                }
                if (!parser.hasOutput) {
                    emit(StreamEvent.Error("$SITE ended without a reply", retryable = true))
                    return@execute
                }
                // The socket closed without `[DONE]`: flush what arrived.
                parser.finish().forEach { emit(it) }
            }
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
