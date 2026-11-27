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
            val upstream = CommunityClients.upstreamId(model)
            val body = buildBody(upstream, messages)
                ?: run { emit(StreamEvent.Error("$SITE: nothing to send")); return@guarded }

            // Try Ptero's newer /wp-json/mlp/v1/chat-stream SSE endpoint first when using default host
            if (endpoint == "https://ptero.pro/wp-json/mlp/v1/chat") {
                val streamModel = when (upstream) {
                    "agnes-2.5-flash:agnes",
                    "agnes-3.0-flash:agnes",
                    "ising-calibration-1.5-31b:bluesminds",
                    "mimo-v2.6-flash:tokenharbor",
                    "glm-5.3-flash" -> upstream
                    "qwen3.7-flash:free" -> "agnes-3.0-flash:agnes"
                    else -> "agnes-2.5-flash:agnes"
                }
                val streamBody = buildBody(streamModel, messages)
                if (streamBody != null && tryStreamEndpoint(streamBody)) return@guarded
            }

            var attempt = 0
            while (true) {
                attempt++
                val ip = KeylessCrypto.fakeIpv4()
                val request = Request.Builder().url(endpoint)
                    .post(body.toString().toRequestBody(JSON))
                    .header("Accept", "application/json")
                    .header("X-MLP-Guest-Token", UUID.randomUUID().toString().replace("-", ""))
                    .header("X-MLP-Guest-Username", "nebian")
                    .header("X-Forwarded-For", ip)
                    .header("X-Real-IP", ip)
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

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.tryStreamEndpoint(
        body: JsonObject
    ): Boolean = runCatching {
        val ip = KeylessCrypto.fakeIpv4()
        val reqBuilder = Request.Builder()
            .url("https://ptero.pro/wp-json/mlp/v1/chat-stream")
            .post(body.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream, application/json")
            .header("X-MLP-Guest-Token", UUID.randomUUID().toString().replace("-", ""))
            .header("X-MLP-Guest-Username", "nebian")
            .header("X-Forwarded-For", ip)
            .header("X-Real-IP", ip)
            .site("https://ptero.pro")
        cachedNonce?.takeIf { it.isNotEmpty() }?.let { reqBuilder.header("X-WP-Nonce", it) }
        CommunityHttp.execute(reqBuilder.build()) { response ->
            if (!response.isSuccessful) return@execute false
            val source = response.body?.source() ?: return@execute false
            var started = false
            var sawText = false
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val raw = line.removePrefix("data:").trim()
                val ev = runCatching { AiHttp.json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: continue
                val act = ev["activity"] as? JsonObject
                if ((act?.get("type") as? JsonPrimitive)?.contentOrNull == "thinking") {
                    val lbl = (act["label"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    if (lbl.isNotEmpty()) {
                        if (!started) { started = true; emit(StreamEvent.Started) }
                        emit(StreamEvent.ReasoningDelta("$lbl\n"))
                    }
                }
                val thinkingTok = (ev["thinking_token"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (thinkingTok.isNotEmpty()) {
                    if (!started) { started = true; emit(StreamEvent.Started) }
                    emit(StreamEvent.ReasoningDelta(thinkingTok))
                }
                val tok = (ev["token"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (tok.isNotEmpty()) {
                    if (!started) { started = true; emit(StreamEvent.Started) }
                    sawText = true
                    emit(StreamEvent.ContentDelta(tok))
                }
                if ((ev["done"] as? JsonPrimitive)?.contentOrNull == "true") break
            }
            if (!sawText) return@execute false
            emit(StreamEvent.FinishReason("stop"))
            emit(StreamEvent.Completed)
            true
        }
    }.getOrDefault(false)

    companion object {
        const val SITE = "Ptero"
        private const val MAX_ATTEMPTS = 3
        private val JSON = "application/json".toMediaType()
        @Volatile private var cachedNonce: String? = null

        fun extractNonce(html: String): String? {
            for (pat in listOf("var nonce = '", "var nonce = \"", "data-referral-nonce=\"", "data-referral-nonce='")) {
                val idx = html.indexOf(pat)
                if (idx >= 0) {
                    val rest = html.substring(idx + pat.length)
                    val end = rest.indexOfFirst { !it.isDigit() && it !in 'a'..'f' && it !in 'A'..'F' }
                        .let { if (it < 0) rest.length else it }
                    if (end >= 8) return rest.substring(0, end)
                }
            }
            return null
        }

        /** System text rides on the last user message (the site has no system role); earlier turns go in history. */
        fun buildBody(upstreamModel: String, messages: List<ChatRequestMessage>): JsonObject? {
            val turns = Transcript.turns(messages)
            val systems = turns.filter { it.role == "system" }.joinToString("\n\n") { it.text }
            val convo = turns.filter { it.role != "system" }
            val lastUserIndex = convo.indexOfLast { it.role == "user" }
            if (lastUserIndex < 0) return null
            val allImages = messages.flatMap { it.images }
            val effModel = if (allImages.isNotEmpty()) "agnes-2.5-flash:agnes" else upstreamModel
            val message = (if (systems.isNotBlank()) "[System instructions: $systems]\n\n" else "") + convo[lastUserIndex].text
            return buildJsonObject {
                put("message", message)
                put("model", effModel)
                put("conversation_id", UUID.randomUUID().toString())
                putJsonArray("history") {
                    convo.take(lastUserIndex).forEach { t ->
                        addJsonObject { put("role", t.role); put("text", t.text); putJsonArray("attachments") {} }
                    }
                }
                putJsonArray("attachments") {
                    allImages.forEachIndexed { idx, dataUrl ->
                        val mime = dataUrl.removePrefix("data:").substringBefore(';', "image/png").ifBlank { "image/png" }
                        addJsonObject {
                            put("name", "image_$idx.png")
                            put("type", mime)
                            put("size", dataUrl.length * 3 / 4)
                            put("data", dataUrl)
                            put("dataUrl", dataUrl)
                            put("url", dataUrl)
                        }
                    }
                }
                put("github_repo", "")
                put("lang", "en")
                put("mode", "full")
            }
        }
    }
}
