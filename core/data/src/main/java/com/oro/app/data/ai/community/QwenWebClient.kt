package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs chat.qwen.ai requests inside a real browser page (a WebView the app
 * provides). Used when the site's anti-bot layer refuses plain HTTP: the page
 * has a genuine browser's TLS, cookies and risk SDK. Each emitted string is
 * one response line; the first is `HTTP <code> <content-type>`.
 */
interface QwenBrowserBridge {
    fun post(path: String, body: String): Flow<String>
}

object QwenBrowser {
    @Volatile var bridge: QwenBrowserBridge? = null
}

/**
 * chat.qwen.ai guest chat (Qwen3.8 Max and friends), ported from NEB's
 * qwen_proxy.
 *
 * ### Identities and rotation
 *
 * The site rate-limits each guest identity (about 20 messages), so the client
 * keeps a small pool of identities -- fingerprint, risk cookies, `bx-ua`,
 * `bx-umidtoken` -- and uses the least-used one. An identity retires after
 * [IDENTITY_MESSAGES] messages, [IDENTITY_TTL_MS], or as soon as the site
 * reports a quota or challenge, and the request is retried once with a fresh
 * one.
 *
 * ### Transport
 *
 * Plain HTTP first. When the anti-bot layer answers with its "punish" page
 * instead of a stream (it does for datacenter IPs and some networks), the same
 * two calls go through [QwenBrowser.bridge], a WebView on the real site.
 *
 * Each request starts a fresh server-side chat holding the whole transcript,
 * so the conversation lives in Oro, not on the site.
 */
class QwenWebClient(private val base: String = "https://chat.qwen.ai") : CommunityChatClient {

    private class Identity(val id: QwenFingerprint.Identity, val http: OkHttpClient, var umid: String?, val createdAt: Long) {
        var uses = 0
        var warmed = false
    }

    private val pool = ArrayList<Identity>()
    /** Set once the plain-HTTP route has been refused, so later requests go straight to the browser. */
    @Volatile private var preferBrowser = false

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent> =
        CommunityHttp.guarded(SITE) {
            val upstream = CommunityClients.upstreamId(model)
            val prompt = flatten(messages)
            if (prompt.isBlank()) { emit(StreamEvent.Error("$SITE: nothing to send")); return@guarded }

            val bridge = QwenBrowser.bridge
            if (!preferBrowser || bridge == null) {
                for (attempt in 1..2) {
                    when (val r = viaHttp(upstream, prompt)) {
                        Outcome.Done -> return@guarded
                        Outcome.Refused -> { preferBrowser = true; break }
                        is Outcome.Retry -> if (attempt == 2) { emit(StreamEvent.Error("$SITE: ${r.reason}", retryable = true)); return@guarded }
                    }
                }
            }
            if (bridge == null) {
                emit(StreamEvent.Error("$SITE asked for a browser check and no in-app browser is available. Try again in a minute.", retryable = true))
                return@guarded
            }
            viaBrowser(bridge, upstream, prompt)
        }

    private sealed interface Outcome {
        data object Done : Outcome
        data object Refused : Outcome
        data class Retry(val reason: String) : Outcome
    }

    // ------------------------------------------------------------------ HTTP

    private suspend fun FlowCollector<StreamEvent>.viaHttp(model: String, prompt: String): Outcome {
        val ident = identity()
        if (!ident.warmed) {
            ident.warmed = true
            runCatching {
                CommunityHttp.execute(get(ident, "$base/"), ident.http) { r ->
                    val text = r.body?.string().orEmpty()
                    if (isChallenge(text)) throw ChallengeException()
                }
            }.onFailure { if (it is ChallengeException) { retire(ident); return Outcome.Refused } }
        }
        val chatId = CommunityHttp.execute(post(ident, "$base/api/v2/chats/new", newChatBody(model)), ident.http) { r ->
            val text = r.body?.string().orEmpty()
            if (isChallenge(text) || r.code == 403) return@execute null
            runCatching {
                ((AiHttp.json.parseToJsonElement(text) as JsonObject)["data"] as JsonObject)["id"].let { (it as JsonPrimitive).content }
            }.getOrNull()
        } ?: run { retire(ident); return Outcome.Refused }

        val request = post(ident, "$base/api/v2/chat/completions?chat_id=$chatId", completionBody(chatId, model, prompt))
            .newBuilder().header("Accept", "text/event-stream").header("x-request-id", UUID.randomUUID().toString()).build()
        return CommunityHttp.execute(request, AiHttp.streaming.newBuilder().cookieJar(ident.http.cookieJar).build()) { r ->
            val type = r.header("Content-Type").orEmpty()
            if (!r.isSuccessful) {
                retire(ident)
                return@execute if (r.code == 403) Outcome.Refused else Outcome.Retry("HTTP ${r.code}")
            }
            if (!type.contains("event-stream")) {
                val text = r.body?.string().orEmpty()
                retire(ident)
                return@execute if (isChallenge(text)) Outcome.Refused else Outcome.Retry(describe(text))
            }
            val source = r.body?.source() ?: return@execute Outcome.Retry("empty response")
            val parser = QwenStreamParser()
            emit(StreamEvent.Started)
            while (true) {
                val line = source.readUtf8Line() ?: break
                parser.onLine(line).forEach { emit(it) }
                if (parser.finished) break
            }
            ident.uses++
            if (parser.quota) retire(ident)
            if (!parser.sawOutput) return@execute Outcome.Retry(parser.error ?: "the reply was empty")
            parser.finish().forEach { emit(it) }
            Outcome.Done
        }
    }

    private class ChallengeException : RuntimeException()

    private fun get(i: Identity, url: String) = headers(Request.Builder().url(url).get(), i).header("Accept", "text/html,*/*").build()

    private fun post(i: Identity, url: String, body: JsonObject) =
        headers(Request.Builder().url(url).post(body.toString().toRequestBody(JSON)), i).build()

    private fun headers(b: Request.Builder, i: Identity): Request.Builder {
        val p = i.id.profile
        b.header("User-Agent", p.ua)
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Origin", base).header("Referer", "$base/")
            .header("sec-ch-ua", p.secChUa).header("sec-ch-ua-mobile", "?0").header("sec-ch-ua-platform", p.secChUaPlatform)
            .header("sec-fetch-dest", "empty").header("sec-fetch-mode", "cors").header("sec-fetch-site", "same-origin")
            .header("x-requested-with", "XMLHttpRequest")
            .header("source", "web").header("version", WEB_VERSION)
            .header("bx-ua", i.id.bxUa)
        i.umid?.let { b.header("bx-umidtoken", it).header("bx-v", "2.5.36") }
        return b
    }

    @Synchronized
    private fun prune() {
        val now = System.currentTimeMillis()
        pool.removeAll { it.uses >= IDENTITY_MESSAGES || now - it.createdAt > IDENTITY_TTL_MS }
    }

    @Synchronized
    private fun retire(i: Identity) { pool.remove(i) }

    private suspend fun identity(): Identity {
        prune()
        synchronized(this) { pool.minByOrNull { it.uses }?.takeIf { pool.size >= POOL_SIZE || it.uses == 0 }?.let { return it } }
        val id = QwenFingerprint.newIdentity()
        val jar = MemoryCookieJar().apply {
            seed("chat.qwen.ai", "ssxmod_itna", id.itna)
            seed("chat.qwen.ai", "ssxmod_itna2", id.itna2)
        }
        val http = AiHttp.client.newBuilder().cookieJar(jar).build()
        val umid = runCatching {
            CommunityHttp.execute(Request.Builder().url(UMID_URL).header("User-Agent", id.profile.ua).build(), http) { r ->
                UMID.find(r.body?.string().orEmpty())?.groupValues?.get(1)
            }
        }.getOrNull()
        return Identity(id, http, umid, System.currentTimeMillis()).also { synchronized(this) { pool += it } }
    }

    // --------------------------------------------------------------- browser

    private suspend fun FlowCollector<StreamEvent>.viaBrowser(bridge: QwenBrowserBridge, model: String, prompt: String) {
        var chatId: String? = null
        var status = ""
        val created = StringBuilder()
        bridge.post("/api/v2/chats/new", newChatBody(model).toString()).collect { line ->
            if (status.isEmpty()) status = line else created.append(line)
        }
        chatId = runCatching {
            ((AiHttp.json.parseToJsonElement(created.toString()) as JsonObject)["data"] as JsonObject)["id"].let { (it as JsonPrimitive).content }
        }.getOrNull()
        if (chatId == null) {
            emit(StreamEvent.Error("$SITE: could not start a chat (${status.ifBlank { "no response" }}: ${describe(created.toString())})", retryable = true))
            return
        }
        val parser = QwenStreamParser()
        var head = ""
        val nonStream = StringBuilder()
        emit(StreamEvent.Started)
        bridge.post("/api/v2/chat/completions?chat_id=$chatId", completionBody(chatId, model, prompt).toString()).collect { line ->
            if (head.isEmpty()) { head = line; return@collect }
            if (!head.contains("event-stream")) { nonStream.append(line); return@collect }
            if (!parser.finished) parser.onLine(line).forEach { emit(it) }
        }
        if (!parser.sawOutput) {
            val why = parser.error ?: describe(nonStream.toString()).ifBlank { head }
            emit(StreamEvent.Error("$SITE: $why", retryable = true))
            return
        }
        parser.finish().forEach { emit(it) }
    }

    // ---------------------------------------------------------------- bodies

    private fun newChatBody(model: String) = buildJsonObject {
        put("title", "New Chat")
        putJsonArray("models") { add(JsonPrimitive(model)) }
        put("chat_mode", "normal")
        put("chat_type", "t2t")
        put("timestamp", System.currentTimeMillis())
        put("project_id", "")
    }

    private fun completionBody(chatId: String, model: String, prompt: String): JsonObject {
        val now = System.currentTimeMillis()
        // Qwen3.8 only answers with thinking on; the others are asked to answer directly (faster, cheaper quota).
        val thinking = model.startsWith("qwen3.8")
        return buildJsonObject {
            put("stream", true)
            put("incremental_output", true)
            put("version", "2.1")
            put("timestamp", now)
            put("chat_id", chatId)
            put("chat_mode", "normal")
            put("model", model)
            put("parent_id", JsonNull)
            putJsonArray("messages") {
                addJsonObject {
                    put("fid", UUID.randomUUID().toString())
                    put("parentId", JsonNull)
                    putJsonArray("childrenIds") {}
                    put("role", "user")
                    put("content", prompt)
                    put("user_action", "chat")
                    putJsonArray("files") {}
                    putJsonArray("models") { add(JsonPrimitive(model)) }
                    put("chat_type", "t2t")
                    putJsonObject("feature_config") {
                        if (thinking) {
                            put("thinking_enabled", true); put("thinking_mode", "Auto"); put("auto_thinking", true)
                        } else {
                            put("thinking_enabled", false); put("thinking_budget", 81920)
                        }
                        put("output_schema", "phase")
                    }
                    put("sub_chat_type", "t2t")
                    put("timestamp", now)
                    putJsonObject("extra") { putJsonObject("meta") { put("subChatType", "t2t") } }
                }
            }
        }
    }

    companion object {
        const val SITE = "Qwen"
        private const val WEB_VERSION = "0.2.89"
        private const val POOL_SIZE = 3
        private const val IDENTITY_MESSAGES = 14
        private const val IDENTITY_TTL_MS = 15 * 60_000L
        private const val UMID_URL = "https://sg-wum.alibaba.com/w/wu.json"
        private val UMID = Regex("""(?:umx\.wu|__fycb)\('([^']+)'\)""")
        private val JSON = "application/json".toMediaType()

        fun isChallenge(text: String) =
            listOf("RGV587_ERROR", "_____tmd_____", "x5secdata", "aliyun_waf_aa", "acw_sc__v2").any { text.contains(it) }

        fun describe(text: String): String = when {
            isChallenge(text) -> "the site asked for a human check"
            text.contains("quota", true) || text.contains("RateLimited", true) -> "guest quota reached; switching identity"
            else -> text.take(200).ifBlank { "no reply" }
        }

        /**
         * The site takes one user message per chat, so the conversation is
         * flattened: instructions first, then the earlier turns, then the
         * current request.
         */
        fun flatten(messages: List<ChatRequestMessage>): String {
            val turns = Transcript.turns(messages)
            val system = turns.filter { it.role == "system" }.joinToString("\n\n") { it.text }
            val convo = turns.filter { it.role != "system" }
            val last = convo.lastOrNull { it.role == "user" } ?: return ""
            val earlier = convo.take(convo.lastIndexOf(last))
            return buildString {
                if (system.isNotBlank()) append("<instructions>\n").append(system).append("\n</instructions>\n\n")
                if (earlier.isNotEmpty()) {
                    append("<conversation>\n")
                    earlier.forEach { t -> append(if (t.role == "user") "User: " else "Assistant: ").append(t.text).append("\n\n") }
                    append("</conversation>\n\n")
                }
                append(last.text)
            }
        }
    }
}

/**
 * chat.qwen.ai SSE: `choices[0].delta` carries `content` tagged with a
 * `phase` (`think`/`thinking_summary` is reasoning, `answer` the reply);
 * `{"error": ...}` frames report quota and moderation problems.
 */
class QwenStreamParser {
    var finished = false; private set
    var sawOutput = false; private set
    var quota = false; private set
    var error: String? = null; private set

    fun onLine(raw: String): List<StreamEvent> {
        val line = raw.trim()
        if (!line.startsWith("data:")) return emptyList()
        val data = line.removePrefix("data:").trim()
        if (data == "[DONE]") { finished = true; return emptyList() }
        val obj = runCatching { AiHttp.json.parseToJsonElement(data) as? JsonObject }.getOrNull() ?: return emptyList()
        obj["error"]?.let { e ->
            val text = (e as? JsonObject)?.let { (it["message"] as? JsonPrimitive)?.contentOrNull ?: (it["code"] as? JsonPrimitive)?.contentOrNull } ?: e.toString()
            if (text.contains("quota", true) || text.contains("RateLimit", true)) quota = true
            error = QwenWebClient.describe(text)
            if (!sawOutput) finished = true
            return emptyList()
        }
        val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return emptyList()
        val delta = choice["delta"] as? JsonObject
        val out = mutableListOf<StreamEvent>()
        val phase = (delta?.get("phase") as? JsonPrimitive)?.contentOrNull
        val content = (delta?.get("content") as? JsonPrimitive)?.contentOrNull
        val reasoning = (delta?.get("reasoning_content") as? JsonPrimitive)?.contentOrNull
        if (!reasoning.isNullOrEmpty()) out += StreamEvent.ReasoningDelta(reasoning)
        if (!content.isNullOrEmpty()) {
            if (phase == "think" || phase == "thinking_summary" || phase == "web_search") {
                out += StreamEvent.ReasoningDelta(content)
            } else {
                sawOutput = true
                out += StreamEvent.ContentDelta(content)
            }
        }
        val status = (delta?.get("status") as? JsonPrimitive)?.contentOrNull
        val finish = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
        if (finish != null || (status == "finished" && phase == "answer")) {
            finished = true
            out += StreamEvent.FinishReason(finish ?: "stop")
        }
        return out
    }

    fun finish(): List<StreamEvent> = listOf(StreamEvent.Completed)
}

/** Cookies per guest identity, in memory: an identity's cookies die with it. */
private class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    fun seed(host: String, name: String, value: String) {
        val url = "https://$host/".toHttpUrl()
        Cookie.parse(url, "$name=$value; Domain=$host; Path=/")?.let { store.getOrPut(host) { ConcurrentHashMap() }[name] = it }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val m = store.getOrPut(url.host) { ConcurrentHashMap() }
        cookies.forEach { m[it.name] = it }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store.entries.filter { (host, _) -> url.host == host || url.host.endsWith(".$host") }.flatMap { it.value.values }
            .filter { it.matches(url) || url.host.endsWith(it.domain) }
}
