package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.community.CommunityHeaders.site
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * ptero.pro guest chat (WordPress REST, no login, no key). One POST of
 * `{message, model, history}` answered with `{reply, model_used}`; no
 * streaming. The quota (about 30 requests/min) is keyed on a guest token the
 * client mints itself, so every request gets a fresh identity and a 429 is
 * retried at once under another one. Ported from NEB's ptero_proxy.
 */
class PteroClient(
    private val endpoint: String = "https://ptero.pro/wp-json/mlp/v1/chat"
) : CommunityChatClient {

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent> =
        CommunityHttp.guarded(SITE) {
            val body = buildBody(CommunityClients.upstreamId(model), messages)
                ?: run { emit(StreamEvent.Error("$SITE: nothing to send")); return@guarded }
            var attempt = 0
            while (true) {
                attempt++
                val request = Request.Builder().url(endpoint)
                    .post(body.toString().toRequestBody(JSON))
                    .header("Accept", "application/json")
                    .header("X-MLP-Guest-Token", UUID.randomUUID().toString().replace("-", ""))
                    .header("X-MLP-Guest-Username", "guest")
                    .site("https://ptero.pro")
                    .build()
                val done = CommunityHttp.execute(request) { response ->
                    if (response.code == 429 && attempt < MAX_ATTEMPTS) return@execute false
                    if (!response.isSuccessful) {
                        emit(CommunityHttp.httpError(SITE, response)); return@execute true
                    }
                    val obj = runCatching { AiHttp.json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject }.getOrNull()
                    val reply = (obj?.get("reply") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                    if (reply.isEmpty()) {
                        val why = (obj?.get("message") as? JsonPrimitive)?.contentOrNull ?: (obj?.get("error") as? JsonPrimitive)?.contentOrNull
                        emit(StreamEvent.Error("$SITE sent an empty reply" + (why?.let { ": $it" } ?: ""), retryable = true))
                    } else {
                        emit(StreamEvent.Started)
                        emit(StreamEvent.ContentDelta(reply))
                        emit(StreamEvent.FinishReason("stop"))
                        emit(StreamEvent.Completed)
                    }
                    true
                }
                if (done) break
                delay(400L * attempt)
            }
        }

    companion object {
        const val SITE = "Ptero"
        private const val MAX_ATTEMPTS = 3
        private val JSON = "application/json".toMediaType()

        /** System text rides on the last user message (the site has no system role); earlier turns go in history. */
        fun buildBody(upstreamModel: String, messages: List<ChatRequestMessage>): JsonObject? {
            val turns = Transcript.turns(messages)
            val systems = turns.filter { it.role == "system" }.joinToString("\n\n") { it.text }
            val convo = turns.filter { it.role != "system" }
            val lastUserIndex = convo.indexOfLast { it.role == "user" }
            if (lastUserIndex < 0) return null
            val message = (if (systems.isNotBlank()) "[System instructions: $systems]\n\n" else "") + convo[lastUserIndex].text
            return buildJsonObject {
                put("message", message)
                put("model", upstreamModel)
                putJsonArray("history") {
                    convo.take(lastUserIndex).forEach { t ->
                        addJsonObject { put("role", t.role); put("text", t.text); putJsonArray("attachments") {} }
                    }
                }
                putJsonArray("attachments") {}
                put("lang", "en")
                put("mode", "full")
            }
        }
    }
}
