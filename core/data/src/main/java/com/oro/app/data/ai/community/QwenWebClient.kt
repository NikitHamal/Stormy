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
 * has a genuine browser's TLS, cookies and the site's own risk SDK (Baxia),
 * which signs every request itself.
 *
 * [chat] creates a chat from [newChatBody] and posts [completionBody] with the
 * new chat's id filled in, all from page context. Emitted lines, in order:
 *  - `CHAT <id>` once the chat exists, or `CHALLENGE <detail>` when the site
 *    puts up its human check (a slider no one can see in a hidden page), or
 *    `FAIL <detail>` for anything else;
 *  - `HTTP <code> <content-type>` for the completion, then the response
 *    lines (SSE `data:` frames, or a JSON body).
 */
interface QwenBrowserBridge {
    fun chat(newChatBody: String, completionBody: String): Flow<String>
}

object QwenBrowser {
    @Volatile var bridge: QwenBrowserBridge? = null
}

/**
 * chat.qwen.ai guest chat (Qwen3.8 Max, Qwen3.7 Plus, Qwen3.8 Omni Flash).
 *
 * ### What the site wants from a guest (checked 2026-10-23, web 0.3.22)
 *
 * No account and no token: a guest is an `x-umid` header (the device id the
 * page keeps in localStorage, any UUID) plus the site's cookies. Requests also
 * carry `source`, `version` and the risk SDK's `bx-ua` / `bx-umidtoken` /
 * `bx-v`. Creating a chat works with just `x-umid`; the completion is where
 * Alibaba's WAF decides, mostly on the network: from datacenter IPs even a
 * real browser gets its "punish" captcha, from phone networks guests chat.
 *
 * ### Identities and rotation
 *
 * The site rate-limits each guest identity, so the client keeps a small pool
 * of identities -- device id, fingerprint, risk cookies, `bx-ua`,
 * `bx-umidtoken` -- and uses the least-used one. An identity retires after
 * [IDENTITY_MESSAGES] messages, [IDENTITY_TTL_MS], or as soon as the site
 * reports a quota or challenge, and the request is retried once with a fresh
 * one.
 *
 * ### Transport
 *
 * Plain HTTP first. When the WAF answers with its punish page instead of a
 * stream, the same chat goes through [QwenBrowser.bridge], a hidden WebView on
 * the real site whose own scripts sign the request. When that page is shown
 * the captcha too, the request fails fast with a "browser check" error, so
 * [com.oro.app.data.ai.KeylessFallback] parks the site for a while and another
 * free Qwen (VLM Run, OVHcloud, Ptero) answers instead.
 *
 * Each request starts a fresh server-side chat holding the whole transcript,
 * so the conversation lives in Oro, not on the site.
 */
class QwenWebClient(private val base: String = "https://chat.qwen.ai") : CommunityChatClient {

    private class Identity(
        val id: QwenFingerprint.Identity,
        val http: OkHttpClient,
        /** bx-umidtoken from Alibaba's UMID service, when it answered. */
        var umidToken: String?,
        val createdAt: Long,
        /** The guest: the site keys a guest's quota on this `x-umid` device id. */
        val deviceId: String = UUID.randomUUID().toString()
    ) {
        var uses = 0
        var warmed = false
    }

    private val pool = ArrayList<Identity>()
    /**
     * When the plain-HTTP route was last refused: for [PREFER_BROWSER_MS]
     * after that, requests go straight to the in-app browser. Plain HTTP is
     * cheaper, so it gets another chance once that has passed (a phone moves
     * between networks; a refusal on one is not a verdict on the next).
     */
    @Volatile private var refusedAt = 0L
    private val preferBrowser: Boolean get() = System.currentTimeMillis() - refusedAt < PREFER_BROWSER_MS

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent> =
        stream(model, messages, null)

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>, reasoningLevel: String?): Flow<StreamEvent> =
        CommunityHttp.guarded(SITE) {
            val upstream = CommunityClients.upstreamId(model)
            val thinking = thinkingFor(upstream, reasoningLevel)
            val prompt = flatten(messages)
            if (prompt.isBlank()) { emit(StreamEvent.Error("$SITE: nothing to send")); return@guarded }

            val bridge = QwenBrowser.bridge
            var challenged = false
            if (!preferBrowser || bridge == null) {
                for (attempt in 1..2) {
                    when (val r = viaHttp(upstream, prompt, thinking)) {
                        Outcome.Done -> return@guarded
                        Outcome.Refused -> { refusedAt = System.currentTimeMillis(); challenged = true; break }
                        is Outcome.Retry -> if (attempt == 2) { emit(StreamEvent.Error("$SITE: ${r.reason}", retryable = true)); return@guarded }
                    }
                }
            }
            if (bridge == null) {
                emit(challengeError(if (challenged) "plain requests were refused" else "no in-app browser"))
                return@guarded
            }
            viaBrowser(bridge, upstream, prompt, thinking)
        }

    private sealed interface Outcome {
        data object Done : Outcome
        /** The WAF wants a human check: try the in-app browser, or hand over. */
        data object Refused : Outcome
        data class Retry(val reason: String) : Outcome
    }

    // ------------------------------------------------------------------ HTTP

    private suspend fun FlowCollector<StreamEvent>.viaHttp(model: String, prompt: String, thinking: Boolean): Outcome {
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
            if (text.contains("RateLimited", true) || text.contains("quota", true)) return@execute ""
            runCatching {
                ((AiHttp.json.parseToJsonElement(text) as JsonObject)["data"] as JsonObject)["id"].let { (it as JsonPrimitive).content }
            }.getOrNull()
        } ?: run { retire(ident); return Outcome.Refused }
        if (chatId.isEmpty()) { retire(ident); return Outcome.Retry("guest quota reached; switching identity") }

        val request = post(ident, "$base/api/v2/chat/completions?chat_id=$chatId", completionBody(chatId, model, prompt, thinking))
            .newBuilder().header("Accept", "text/event-stream").header("x-request-id", UUID.randomUUID().toString()).build()
        return CommunityHttp.execute(request, AiHttp.streaming.newBuilder().cookieJar(ident.http.cookieJar).build()) { r ->
            val type = r.header("Content-Type").orEmpty()
            if (!r.isSuccessful) {
                retire(ident)
                return@execute if (r.code == 403 || r.code == 405) Outcome.Refused else Outcome.Retry("HTTP ${r.code}")
            }
            if (!type.contains("event-stream")) {
                val text = r.body?.string().orEmpty()
                retire(ident)
                return@execute if (isChallenge(text)) Outcome.Refused else Outcome.Retry(describe(text))
            }
            val source = r.body?.source() ?: return@execute Outcome.Retry("empty response")
            val parser = QwenStreamParser()
            var started = false
            while (true) {
                val line = source.readUtf8Line() ?: break
                val events = parser.onLine(line)
                if (!started && events.isNotEmpty()) { started = true; emit(StreamEvent.Started) }
                events.forEach { emit(it) }
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
            .header("x-request-id", UUID.randomUUID().toString())
            .header("bx-ua", i.id.bxUa)
            .header("x-umid", i.deviceId)
            .header("bx-v", BX_VERSION)
            .header("Timezone", timezone())
        i.umidToken?.let { b.header("bx-umidtoken", it) }
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
        val umidToken = runCatching {
            CommunityHttp.execute(Request.Builder().url(UMID_URL).header("User-Agent", id.profile.ua).build(), http) { r ->
                UMID.find(r.body?.string().orEmpty())?.groupValues?.get(1)
            }
        }.getOrNull()
        return Identity(id, http, umidToken, System.currentTimeMillis()).also { synchronized(this) { pool += it } }
    }

    // --------------------------------------------------------------- browser

    private suspend fun FlowCollector<StreamEvent>.viaBrowser(bridge: QwenBrowserBridge, model: String, prompt: String, thinking: Boolean) {
        val parser = QwenStreamParser()
        val reader = BrowserReply(parser)
        bridge.chat(newChatBody(model).toString(), completionBody("", model, prompt, thinking).toString()).collect { line ->
            reader.onLine(line).forEach { emit(it) }
        }
        when {
            reader.challenge != null -> emit(challengeError(reader.challenge!!))
            !parser.sawOutput -> emit(StreamEvent.Error("$SITE: ${reader.failure()}", retryable = true))
            else -> parser.finish().forEach { emit(it) }
        }
    }

    /**
     * Folds the bridge's lines (see [QwenBrowserBridge]) into stream events.
     * Pure, so the browser path's edge cases run in JVM tests.
     */
    internal class BrowserReply(private val parser: QwenStreamParser) {
        var chatId: String? = null; private set
        var challenge: String? = null; private set
        private var failure: String? = null
        private var head = ""
        private val body = StringBuilder()
        private var started = false

        fun onLine(line: String): List<StreamEvent> {
            when {
                challenge != null || parser.finished -> return emptyList()
                chatId == null && head.isEmpty() -> when {
                    line.startsWith("CHAT ") -> chatId = line.removePrefix("CHAT ").trim()
                    line.startsWith("CHALLENGE") -> challenge = line.removePrefix("CHALLENGE").trim().ifBlank { "the site asked for a human check" }
                    line.startsWith("FAIL") -> failure = line.removePrefix("FAIL").trim()
                    else -> failure = line
                }
                head.isEmpty() -> when {
                    line.startsWith("CHALLENGE") -> challenge = line.removePrefix("CHALLENGE").trim().ifBlank { "the site asked for a human check" }
                    line.startsWith("HTTP ") -> head = line
                    line.startsWith("FAIL") -> failure = line.removePrefix("FAIL").trim()
                }
                line.startsWith("CHALLENGE") -> challenge = line.removePrefix("CHALLENGE").trim().ifBlank { "the site asked for a human check" }
                !head.contains("event-stream") -> {
                    body.append(line)
                    if (isChallenge(body.toString())) challenge = "the site asked for a human check"
                }
                else -> {
                    val events = parser.onLine(line)
                    if (events.isEmpty()) return emptyList()
                    if (!started) { started = true; return listOf(StreamEvent.Started) + events }
                    return events
                }
            }
            return emptyList()
        }

        fun failure(): String = parser.error ?: failure?.takeIf { it.isNotBlank() } ?: when {
            chatId == null -> "could not start a chat"
            body.isNotEmpty() -> describe(body.toString())
            head.isNotEmpty() -> "no reply ($head)"
            else -> "no reply"
        }
    }

    // ---------------------------------------------------------------- bodies

    internal fun newChatBody(model: String) = buildJsonObject {
        put("title", "New Chat")
        putJsonArray("models") { add(JsonPrimitive(model)) }
        put("chat_mode", "normal")
        put("chat_type", "t2t")
        put("timestamp", System.currentTimeMillis())
        put("project_id", "")
    }

    /** The completion request; the browser path leaves [chatId] empty and the page fills it in. */
    internal fun completionBody(chatId: String, model: String, prompt: String, thinking: Boolean): JsonObject {
        val now = System.currentTimeMillis()
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
        /** chat.qwen.ai's web client version (main.js 0.3.22, 2026-10-23); sent as `version`. */
        internal const val WEB_VERSION = "0.3.22"
        /** The risk SDK's own version header. */
        internal const val BX_VERSION = "2.5.37"
        private const val POOL_SIZE = 3
        private const val PREFER_BROWSER_MS = 30 * 60_000L
        private const val IDENTITY_MESSAGES = 14
        private const val IDENTITY_TTL_MS = 15 * 60_000L
        private const val UMID_URL = "https://sg-wum.alibaba.com/w/wu.json"
        private val UMID = Regex("""(?:umx\.wu|__fycb)\('([^']+)'\)""")
        private val JSON = "application/json".toMediaType()

        /**
         * The site's human check, worded so KeylessFallback both hands the
         * request to another free model and parks Qwen Chat for a while
         * ("browser check"): a captcha nobody can see will not clear in seconds.
         */
        internal fun challengeError(detail: String) = StreamEvent.Error(
            "$SITE Chat asked for a browser check ($detail)",
            retryable = true
        )

        /** `Timezone` the way the page sends it: `Thu Oct 23 2026 06:00:00 GMT+0545`. */
        internal fun timezone(now: java.util.Date = java.util.Date()): String =
            java.text.SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", java.util.Locale.US).format(now)

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
        /**
         * Qwen Chat's thinking switch: Qwen3.8 models only answer with it on;
         * the others answer directly unless the user turned it on.
         */
        fun thinkingFor(model: String, level: String?): Boolean = model.startsWith("qwen3.8") || level == "on"

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
        // Some frames carry the whole message instead of a delta.
        val content = (delta?.get("content") as? JsonPrimitive)?.contentOrNull
            ?: ((choice["message"] as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull
        val reasoning = ((delta?.get("reasoning_content") ?: delta?.get("reasoning")) as? JsonPrimitive)?.contentOrNull
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
