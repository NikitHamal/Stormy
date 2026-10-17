package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.community.CommunityHeaders.site
import com.oro.app.data.ai.community.CommunityHttp.sse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON = "application/json".toMediaType()

/**
 * chat.ifm.ai guest completions (MBZUAI IFM K2 Horizon): an OpenAI-style
 * message list POSTed to `/api/guest/chat/completions`, answered with
 * cumulative-content SSE frames ([K2FrameParser]). System messages are
 * accepted. Assistant turns must carry a thinking field or the backend rejects
 * the request, so an empty `reasoning` is added to each.
 */
class K2GuestClient(
    private val endpoint: String = "https://chat.ifm.ai/api/guest/chat/completions"
) : CommunityChatClient {

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent> =
        CommunityHttp.guarded(SITE) {
            val request = Request.Builder().url(endpoint)
                .post(buildBody(CommunityClients.upstreamId(model), messages).toString().toRequestBody(JSON))
                .header("Accept", "text/event-stream, application/json")
                .site("https://chat.ifm.ai", "https://chat.ifm.ai/guest")
                .build()
            val parser = K2FrameParser()
            sse(SITE, request) { data ->
                parser.onData(data).forEach { emit(it) }
                !parser.finished
            }
            // The backend sometimes drops the socket after the last frame.
            if (!parser.sawOutput && !parser.finished) {
                emit(StreamEvent.Error("$SITE ended without a reply", retryable = true))
            } else {
                parser.finish().forEach { emit(it) }
            }
        }

    companion object {
        const val SITE = "K2 Horizon"

        fun buildBody(upstreamModel: String, messages: List<ChatRequestMessage>): JsonObject = buildJsonObject {
            put("stream", true)
            put("model", upstreamModel)
            putJsonArray("messages") {
                Transcript.turns(messages).forEach { turn ->
                    addJsonObject {
                        put("role", turn.role)
                        put("content", turn.text)
                        if (turn.role == "assistant") put("reasoning", "")
                    }
                }
            }
            putJsonObject("params") {}
            putJsonObject("features") { put("web_search", false) }
        }
    }
}

