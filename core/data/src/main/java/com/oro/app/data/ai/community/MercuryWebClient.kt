package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.TimeZone

/**
 * Runs chat.inceptionlabs.ai calls inside the real site (a WebView the app
 * provides), for when Vercel's security checkpoint refuses plain HTTP. The
 * page solves the checkpoint like any browser, then makes the same two
 * same-origin calls. Each emitted string is one response line; the first is
 * `HTTP <code> <content-type>`.
 */
interface MercuryBrowserBridge {
    /** POSTs [body] to `/api/chat` with a guest session minted in the page. */
    fun chat(body: String): Flow<String>
}

object MercuryBrowser {
    @Volatile var bridge: MercuryBrowserBridge? = null
}

/**
 * Inception's guest chat (Mercury 3, a diffusion LLM), as the user's NEB
 * proxy speaks it since 2026-10-19:
 *
 *  1. `GET /api/session` mints a guest token (no login, no key).
 *  2. `POST /api/chat` with the token as `x-session-token` and as the
 *     `session` cookie; the body is a Vercel AI SDK v5 `useChat` request with
 *     no model field (the site serves one model) and a `reasoningEffort`.
 *  3. The answer streams as AI SDK frames ([AiSdkStreamParser]).
 *
 * Tokens are cached for [TOKEN_TTL_MS]; a 403 drops the token and the call is
 * retried once with a fresh one. The site sits behind Vercel's security
 * checkpoint, which challenges some networks (datacenters always): a
 * challenged request switches this client to [MercuryBrowser.bridge] for the
 * rest of the process, the way the Qwen client does.
 *
 * Each request carries the whole transcript under a fresh chat id, so the
 * conversation lives in Oro, not on the site.
 */
class MercuryWebClient(private val base: String = "https://chat.inceptionlabs.ai") : CommunityChatClient {

    @Volatile private var token: String? = null
    @Volatile private var tokenExpiresAt = 0L
    /** Set once plain HTTP met the checkpoint; later requests go straight to the browser. */
    @Volatile private var preferBrowser = false

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent> =
        stream(model, messages, null)

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>, reasoningLevel: String?): Flow<StreamEvent> =
        CommunityHttp.guarded(SITE) {
            val effort = reasoningLevel?.takeIf { it in EFFORTS } ?: effortFor(CommunityClients.upstreamId(model))
            val body = buildBody(effort, messages, TimeZone.getDefault().id)
                ?: run { emit(StreamEvent.Error("$SITE: nothing to send")); return@guarded }
            val payload = body.toString()

            val bridge = MercuryBrowser.bridge
            if (!preferBrowser || bridge == null) {
                for (attempt in 1..2) {
                    when (val outcome = viaHttp(payload)) {
                        Outcome.Done -> return@guarded
                        Outcome.Challenged -> { preferBrowser = true; break }
                        is Outcome.Retry -> if (attempt == 2) {
                            emit(StreamEvent.Error("$SITE: ${outcome.reason}", retryable = true))
                            return@guarded
                        }
                    }
                }
            }
            if (bridge == null) {
                emit(
                    StreamEvent.Error(
                        "$SITE asked for a browser check and no in-app browser is available. Try again in a minute.",
                        retryable = true
                    )
                )
                return@guarded
            }
            viaBrowser(bridge, payload)
        }

    private sealed interface Outcome {
        data object Done : Outcome
        data object Challenged : Outcome
        data class Retry(val reason: String) : Outcome
    }

    // ------------------------------------------------------------------ HTTP

    private suspend fun FlowCollector<StreamEvent>.viaHttp(payload: String): Outcome {
        val guest = when (val s = session()) {
            is Session.Token -> s.value
            Session.Challenged -> return Outcome.Challenged
            is Session.Failed -> return Outcome.Retry(s.reason)
        }
        val request = browserHeaders(Request.Builder().url("$base/api/chat"))
            .header("Accept", "text/event-stream, */*")
            .header("x-session-token", guest)
            .header("Cookie", "session=$guest")
            .post(payload.toRequestBody(JSON))
            .build()
        return CommunityHttp.execute(request) { response ->
            when {
                isChallenge(response) -> Outcome.Challenged
                response.code == 403 -> {
                    // The guest token expired or was revoked: mint another.
                    token = null
                    Outcome.Retry("the guest session expired")
                }
                !response.isSuccessful -> {
                    emit(CommunityHttp.httpError(SITE, response))
                    Outcome.Done
                }
                response.header("Content-Type").orEmpty().contains("text/html") -> Outcome.Challenged
                else -> {
                    val source = response.body?.source() ?: return@execute Outcome.Retry("empty response")
                    val parser = AiSdkStreamParser()
                    emit(StreamEvent.Started)
                    while (!parser.finished) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        emit(StreamEvent.RawSSE(data))
                        parser.onData(data).forEach { emit(it) }
                    }
                    finishStream(parser)
                    Outcome.Done
                }
            }
        }
    }

    private sealed interface Session {
        data class Token(val value: String) : Session
        data object Challenged : Session
        data class Failed(val reason: String) : Session
    }

    private suspend fun session(): Session {
        val now = System.currentTimeMillis()
        token?.let { if (tokenExpiresAt - now > REFRESH_MARGIN_MS) return Session.Token(it) }
        val request = browserHeaders(Request.Builder().url("$base/api/session")).get().build()
        return CommunityHttp.execute(request, AiHttp.client) { response ->
            when {
                isChallenge(response) -> Session.Challenged
                !response.isSuccessful -> Session.Failed("could not start a guest session (HTTP ${response.code})")
                else -> {
                    val text = response.body?.string().orEmpty()
                    if (text.trimStart().startsWith("<")) return@execute Session.Challenged
                    val value = parseToken(text)
                        ?: return@execute Session.Failed("the guest session had no token")
                    token = value
                    tokenExpiresAt = System.currentTimeMillis() + TOKEN_TTL_MS
                    Session.Token(value)
                }
            }
        }
    }

    private fun browserHeaders(builder: Request.Builder): Request.Builder = builder
        .header("User-Agent", USER_AGENT)
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Origin", base)
        .header("Referer", "$base/")
        .header("sec-ch-ua", SEC_CH_UA)
        .header("sec-ch-ua-mobile", "?0")
        .header("sec-ch-ua-platform", "\"Windows\"")
        .header("sec-fetch-dest", "empty")
        .header("sec-fetch-mode", "cors")
        .header("sec-fetch-site", "same-origin")

    // --------------------------------------------------------------- browser

    private suspend fun FlowCollector<StreamEvent>.viaBrowser(bridge: MercuryBrowserBridge, payload: String) {
        val parser = AiSdkStreamParser()
        var head = ""
        val other = StringBuilder()
        bridge.chat(payload).collect { line ->
            if (head.isEmpty()) {
                head = line
                if (head.contains("event-stream")) emit(StreamEvent.Started)
                return@collect
            }
            if (!head.contains("event-stream")) { if (other.length < 2_000) other.append(line).append('\n'); return@collect }
            if (parser.finished || !line.startsWith("data:")) return@collect
            val data = line.removePrefix("data:").trim()
            emit(StreamEvent.RawSSE(data))
            parser.onData(data).forEach { emit(it) }
        }
        if (!head.contains("event-stream")) {
            val code = head.removePrefix("HTTP ").substringBefore(' ').toIntOrNull()
            emit(
                StreamEvent.Error(
                    "$SITE: " + describeBrowserFailure(code, other.toString()),
                    httpCode = code?.takeIf { it > 0 },
                    retryable = true
                )
            )
            return
        }
        finishStream(parser)
    }

    private suspend fun FlowCollector<StreamEvent>.finishStream(parser: AiSdkStreamParser) {
        // A reply that errored halfway is not an answer: the loop discards the
        // partial attempt and retries.
        if (parser.failed || !parser.sawOutput) {
            emit(StreamEvent.Error("$SITE: ${parser.error ?: "the reply was empty"}", retryable = true))
            return
        }
        parser.finish().forEach { emit(it) }
    }

    companion object {
        const val SITE = "Mercury"
        private val JSON = "application/json".toMediaType()
        private const val TOKEN_TTL_MS = 50 * 60_000L
        private const val REFRESH_MARGIN_MS = 60_000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
        private const val SEC_CH_UA = "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\""

        /** The site's effort scale. */
        val EFFORTS = setOf("instant", "low", "medium", "high")

        /** Effort when none is chosen: High, or what a retired seed id named ("mercury-3-instant"). */
        fun effortFor(upstreamModel: String): String = when {
            upstreamModel.endsWith("-instant") -> "instant"
            upstreamModel.endsWith("-low") -> "low"
            upstreamModel.endsWith("-medium") -> "medium"
            else -> "high"
        }

        /** Vercel's security checkpoint: 429 marked as a mitigation, not a rate limit. */
        fun isChallenge(response: Response): Boolean =
            response.header("x-vercel-mitigated").equals("challenge", ignoreCase = true) ||
                (response.code == 429 && response.header("Content-Type").orEmpty().contains("text/html"))

        fun parseToken(json: String): String? = runCatching {
            ((AiHttp.json.parseToJsonElement(json) as JsonObject)["token"] as? JsonPrimitive)?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }

        fun buildBody(effort: String, messages: List<ChatRequestMessage>, timezone: String = "UTC"): JsonObject? {
            val turns = Transcript.turns(messages)
            if (turns.none { it.role == "user" }) return null
            return buildJsonObject {
                put("reasoningEffort", effort)
                put("webSearchEnabled", false)
                put("voiceMode", false)
                put("timezone", timezone)
                put("id", AiSdkMessages.id())
                put("messages", AiSdkMessages.build(turns))
                put("trigger", "submit-message")
            }
        }

        fun describeBrowserFailure(code: Int?, body: String): String = when {
            code == null || code == 0 -> body.lineSequence().firstOrNull { it.isNotBlank() }?.take(200)
                ?: "the in-app browser could not reach the site"
            code == 429 && body.contains("Vercel", ignoreCase = true) -> "the site's browser check has not passed yet; try again in a minute"
            code == 403 -> "the guest session was refused; try again"
            else -> (com.oro.app.data.ai.ChatStreamParser.describeErrorBody(body) ?: "request failed") + " ($code)"
        }
    }
}
