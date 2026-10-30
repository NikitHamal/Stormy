package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Google's anonymous Gemini web chat, bridged without a key or an account.
 *
 * The site lets an unsigned visitor talk to its default Flash-Lite model. The
 * flow (ported from the owner's NEB proxy, 2026-10-26):
 *
 *  1. `GET /app` collects the session cookies and parses `FdrFJe` (f.sid),
 *     the current `bl` build id and `SNlM0e` from WIZ_global_data.
 *  2. `POST /_/BardChatUi/data/.../StreamGenerate` with form-encoded
 *     `f.req=[null,"<inner-json>"]`; the inner JSON carries the prompt,
 *     locale and an empty conversation token (anonymous chats need none).
 *  3. The reply is a `)]}'`-prefixed stream of length-prefixed JSON chunks;
 *     answer text lives in nested `rc_*` arrays, ids in `c_<id>` / `r_<id>` pairs.
 *
 * Each identity is a fresh cookie jar and a fresh `_reqid`; a 429, 5xx or a
 * rejected request drops the identity and retries with the next one, so a
 * rate-limited cookie does not take the route offline. Proxies can be set in
 * [GeminiWebConfig.proxies] (the Android analogue of NEB's
 * `GEMINIWEB_PROXIES`), and the request applies a Chrome user agent because
 * the site serves a reduced page to obvious non-browser clients.
 *
 * It is text-only: no native tools, no images. The agent uses its text tool
 * protocol on this route like any community bridge.
 */
class GeminiWebClient : CommunityChatClient {

    @Volatile private var warm: Identity? = null

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent> =
        stream(model, messages, null)

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>, reasoningLevel: String?): Flow<StreamEvent> =
        CommunityHttp.guarded(SITE) {
            val prompt = buildPrompt(messages)
            if (prompt.isBlank()) {
                emit(StreamEvent.Error("$SITE: nothing to send"))
                return@guarded
            }
            var lastError: StreamEvent.Error? = null
            for (attempt in 1..MAX_ATTEMPTS) {
                val identity = identityFor(attempt)
                try {
                    if (!identity.warm()) {
                        lastError = StreamEvent.Error("$SITE: the page did not load (session warm-up failed)", retryable = true)
                        identity.close()
                        continue
                    }
                    val outcome = identity.send(prompt) { event -> emit(event) }
                    when (outcome) {
                        SendOutcome.Done -> {
                            adopt(identity)
                            return@guarded
                        }
                        is SendOutcome.Failed -> {
                            lastError = outcome.error ?: StreamEvent.Error("$SITE: no answer", retryable = true)
                            identity.close()
                            if (outcome.fatal) break
                            delay(900L * attempt)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    identity.close()
                    throw e
                } catch (e: Exception) {
                    identity.close()
                    lastError = StreamEvent.Error(
                        "$SITE: ${e.message ?: e.javaClass.simpleName}",
                        retryable = e is java.io.IOException
                    )
                    delay(900L * attempt)
                }
            }
            emit(lastError ?: StreamEvent.Error("$SITE: every identity failed", retryable = true))
        }

    /** Reuse a healthy identity; on any retry take a fresh one (that is the rotation). */
    private fun identityFor(attempt: Int): Identity {
        if (attempt == 1) {
            warm?.let { if (!it.expired()) return it else { it.close(); warm = null } }
        }
        val proxy = GeminiWebConfig.proxies.takeIf { it.isNotEmpty() }?.let { list ->
            val index = (GeminiWebConfig.rotation.getAndIncrement() + attempt) % list.size
            parseProxy(list[index])
        }
        return Identity(proxy)
    }

    private fun adopt(identity: Identity) {
        val old = warm
        warm = identity
        if (old != null && old !== identity) old.close()
    }

    private fun parseProxy(raw: String): Proxy? = runCatching {
        val clean = raw.substringAfter("://")
        val host = clean.substringBefore(':')
        val port = clean.substringAfter(':', "8080").substringBefore('/').toIntOrNull() ?: 8080
        Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
    }.getOrNull()

    /** Flattens a chat into the one prompt the anonymous page understands. */
    private fun buildPrompt(messages: List<ChatRequestMessage>): String {
        val system = messages.firstOrNull { it.role == "system" }?.content.orEmpty().take(8_000)
        val turns = messages.filter { it.role == "user" || it.role == "assistant" }
            .mapNotNull { m -> m.content?.takeIf { it.isNotBlank() }?.let { m.role to it } }
        val lastUser = turns.lastOrNull { it.first == "user" }?.second.orEmpty()
        if (lastUser.isBlank()) return ""
        val transcript = if (turns.size > 1) {
            turns.dropLast(1).takeLast(10).joinToString("\n") { (role, text) ->
                val label = if (role == "user") "User" else "Assistant"
                "$label: ${text.take(2_000)}"
            }
        } else ""
        return buildString {
            if (system.isNotBlank()) append(system).append("\n\n")
            if (transcript.isNotBlank()) append("Previous conversation:\n").append(transcript).append("\n\n")
            append("User: ").append(lastUser.take(12_000))
        }.take(26_000)
    }

    private sealed interface SendOutcome {
        data object Done : SendOutcome
        data class Failed(val error: StreamEvent.Error?, val fatal: Boolean = false) : SendOutcome
    }

    private inner class Identity(private val proxy: Proxy?) {
        private val client: OkHttpClient = AiHttp.client.newBuilder()
            .cookieJar(MemoryCookieJar())
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(2, 2L, TimeUnit.MINUTES))
            .dispatcher(Dispatcher().apply {
                maxRequests = 4
                maxRequestsPerHost = 2
            })
            .apply { proxy?.let { proxy(it) } }
            .build()
        private var fsid = ""
        private var bl = "boq_assistant-bard-web-server_20260827.05_p0"
        private var snlm0e = ""
        private var reqid = 100_000 + (Math.random() * 800_000).toInt()
        private var warmedAt = 0L

        fun expired(): Boolean = warmedAt == 0L || System.currentTimeMillis() - warmedAt > GeminiWebConfig.IDENTITY_TTL_MS

        fun warm(): Boolean {
            if (warmedAt > 0L && !expired()) return true
            val request = Request.Builder().url("$BASE/app")
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .get()
                .build()
            val html = runCatching {
                client.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    r.body?.string()
                }
            }.getOrNull() ?: return false
            Regex(""""FdrFJe":"([0-9\-]+)"""").find(html)?.let { fsid = it.groupValues[1] }
            Regex("""(boq_assistant-bard-web-server_[0-9._p]+)""").find(html)?.let { bl = it.groupValues[1] }
            Regex(""""SNlM0e":"([^"]+)"""").find(html)?.let { snlm0e = it.groupValues[1] }
            warmedAt = System.currentTimeMillis()
            return fsid.isNotBlank()
        }

        suspend fun send(prompt: String, emit: suspend (StreamEvent) -> Unit): SendOutcome {
            reqid += 100_000
            val inner = buildInner(prompt)
            val url = buildString {
                append(BASE).append(STREAM_ENDPOINT)
                append("?bl=").append(bl)
                append("&f.sid=").append(fsid)
                append("&hl=en-US&_reqid=").append(reqid).append("&rt=c")
                if (snlm0e.isNotBlank()) append("&at=").append(URLEncoder.encode(snlm0e, "UTF-8"))
            }
            val body = FormBody.Builder().add("f.req", "[null,$inner]").build()
            val request = Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Origin", BASE)
                .header("Referer", "$BASE/")
                .header("X-Same-Domain", "1")
                .post(body)
                .build()
            val response = runCatching { client.newCall(request).execute() }.getOrElse {
                return SendOutcome.Failed(StreamEvent.Error("$SITE: ${it.message ?: "request failed"}", retryable = true))
            }
            response.use { r ->
                if (!r.isSuccessful) {
                    val detail = runCatching { r.body?.string()?.take(300) }.getOrNull().orEmpty()
                    val message = when (r.code) {
                        429 -> "$SITE rate limit reached (rotating identity)"
                        else -> "$SITE request failed (${r.code})" + detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                    }
                    return SendOutcome.Failed(
                        StreamEvent.Error(message, httpCode = r.code, retryable = r.code == 429 || r.code >= 500),
                        fatal = r.code == 401 || r.code == 403 || r.code == 404
                    )
                }
                emit(StreamEvent.Started)
                val source = r.body?.source()
                    ?: return SendOutcome.Failed(StreamEvent.Error("$SITE sent an empty response", retryable = true))
                var prevText = ""
                var saw = false
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || line.startsWith(")]}'") || line.all { it.isDigit() }) continue
                    if (!line.startsWith("[")) continue
                    for (text in parseLine(line)) {
                        val delta = when {
                            text.startsWith(prevText) -> text.substring(prevText.length)
                            prevText.isEmpty() -> text
                            else -> ""
                        }
                        if (text.length > prevText.length) prevText = text
                        if (delta.isNotEmpty()) {
                            saw = true
                            emit(StreamEvent.ContentDelta(delta))
                        }
                    }
                }
                if (!saw) return SendOutcome.Failed(StreamEvent.Error("$SITE stream ended without text", retryable = true))
                emit(StreamEvent.Completed)
                return SendOutcome.Done
            }
        }

        private fun buildInner(prompt: String): String = buildJsonArray {
            add(buildJsonArray {
                add(JsonPrimitive(prompt))
                add(JsonPrimitive(0))
                add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull); add(JsonPrimitive(0))
            })
            add(buildJsonArray { add(JsonPrimitive("en-US")) })
            add(buildJsonArray {
                add(JsonPrimitive("")); add(JsonPrimitive("")); add(JsonPrimitive(""))
                add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull)
                add(JsonPrimitive(""))
            })
            add(JsonPrimitive(""))
            add(JsonPrimitive(UUID.randomUUID().toString().replace("-", "")))
            add(JsonNull)
            add(buildJsonArray { add(JsonPrimitive(0)) })
            add(JsonPrimitive(1))
            add(JsonNull)
            add(JsonNull)
            add(JsonPrimitive(1))
            add(JsonPrimitive(0))
        }.toString()

        /** Text snapshots in one response line, across every `wrb.fr` row and `rc_*` part. */
        private fun parseLine(line: String): List<String> = GeminiWebParser.parse(line)

        fun close() {
            // Its own pool and dispatcher: evicting here never touches the
            // shared client used by the rest of the app.
            runCatching { client.connectionPool.evictAll() }
            runCatching { client.dispatcher.cancelAll() }
        }
    }

    private class MemoryCookieJar : CookieJar {
        private val store = HashMap<String, MutableList<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(store) {
                val current = store.getOrPut(url.host) { ArrayList() }
                cookies.forEach { fresh ->
                    current.removeAll { it.name == fresh.name && it.path == fresh.path }
                    current += fresh
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(store) {
            val now = System.currentTimeMillis()
            store[url.host].orEmpty().filter { it.expiresAt > now }.toMutableList().also { store[url.host] = it }
        }
    }

    companion object {
        private const val BASE = "https://gemini.google.com"
        private const val STREAM_ENDPOINT = "/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate"
        private const val SITE = "Gemini web"
        private const val MAX_ATTEMPTS = 3
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
    }
}

/**
 * Parses one line of Gemini's `StreamGenerate` reply. Kept pure and internal so
 * fixture tests can prove the nesting without a network call.
 */
internal object GeminiWebParser {
    fun parse(line: String): List<String> {
        val out = ArrayList<String>()
        val arr = runCatching { AiHttp.json.parseToJsonElement(line) as? JsonArray }.getOrNull() ?: return out
        arr.forEach { entry ->
            val row = entry as? JsonArray ?: return@forEach
            if ((row.getOrNull(0) as? JsonPrimitive)?.contentOrNull != "wrb.fr") return@forEach
            val innerRaw = (row.getOrNull(2) as? JsonPrimitive)?.contentOrNull ?: return@forEach
            val inner = runCatching { AiHttp.json.parseToJsonElement(innerRaw) as? JsonArray }.getOrNull()
                ?: return@forEach
            if (inner.size < 5) return@forEach
            val body = inner[4] as? JsonArray ?: return@forEach
            body.forEach { partEl ->
                val part = partEl as? JsonArray ?: return@forEach
                val tag = (part.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return@forEach
                if (!tag.startsWith("rc_")) return@forEach
                val texts = part.getOrNull(1) as? JsonArray ?: return@forEach
                val best = texts.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .maxByOrNull { it.length } ?: return@forEach
                if (best.isNotBlank()) out += best
            }
        }
        return out
    }
}

/**
 * The knobs the NEB proxy exposed as environment variables, kept alive on
 * Android: optional HTTP proxies (one identity per proxy, round-robin) and the
 * warm-identity TTL.
 */
object GeminiWebConfig {
    const val IDENTITY_TTL_MS = 8 * 60_000L

    /** `host:port` or `http://host:port` entries. Empty means direct. */
    @Volatile
    var proxies: List<String> = emptyList()

    internal val rotation = java.util.concurrent.atomic.AtomicInteger(0)
}
