package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.ChatStreamParser
import com.oro.app.data.ai.ProviderProtocol
import com.oro.app.data.ai.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response

/**
 * A community (unofficial guest web-chat) provider.
 *
 * ### Isolation
 *
 * One class per protocol, sharing nothing but the HTTP client and the
 * [StreamEvent] vocabulary. A site that changes its protocol breaks exactly one
 * of these, which then fails fast with a clear error rather than taking the
 * others (or the OpenAI-compatible path) down with it. Every client:
 *  - turns any exception into a [StreamEvent.Error] (never throws into the loop),
 *  - always ends its flow with [StreamEvent.Completed] or an error,
 *  - cancels its HTTP call when the collector is cancelled.
 *
 * None of these sites support function calling; models are flagged
 * `supportsToolCalls = false` and the agent uses the `<tool_call>` text
 * protocol, which [com.oro.app.data.ai.agent.TextToolCallParser] recovers.
 */
interface CommunityChatClient {
    fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent>
}

object CommunityClients {
    private val clients: Map<ProviderProtocol, CommunityChatClient> by lazy {
        mapOf(
            ProviderProtocol.K2_GUEST to K2GuestClient(),
            ProviderProtocol.LOCAL to com.oro.app.data.ai.local.LocalModels.client
        )
    }

    fun forProvider(provider: AiProvider): CommunityChatClient? = clients[provider.protocol]

    fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent> =
        forProvider(model.provider)?.stream(model, messages)
            ?: flowOf(StreamEvent.Error("${model.provider.displayName} has no community client"))

    /** Upstream model id: the seed id minus its "provider:" namespace. */
    fun upstreamId(model: AiModel): String = model.id.substringAfter(':', model.id)
}

/** Browser-like headers the guest endpoints expect. Nothing secret, no fingerprinting. */
internal object CommunityHeaders {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36"

    fun Request.Builder.site(origin: String, referer: String = "$origin/"): Request.Builder = this
        .header("User-Agent", USER_AGENT)
        .header("Origin", origin)
        .header("Referer", referer)
        .header("Accept-Language", "en-US,en;q=0.9")
}

/**
 * Plain-text view of a conversation, for sites without roles or tool messages.
 *
 * Native tool traffic (assistant `tool_calls`, `tool` results) is rendered in
 * the same `<tool_call>` / `<tool_result>` text the agent uses in text mode, so
 * a conversation started on another model stays intelligible here.
 */
internal object Transcript {

    data class Turn(val role: String, val text: String)

    fun turns(messages: List<ChatRequestMessage>): List<Turn> = messages.mapNotNull { m ->
        when (m.role) {
            "system", "user" -> m.content?.takeIf { it.isNotBlank() }?.let { Turn(m.role, it) }
            "assistant" -> {
                val calls = m.toolCalls.orEmpty().joinToString("\n") { c ->
                    "<tool_call>\n{\"name\": \"${c.function.name}\", \"arguments\": ${c.function.arguments.ifBlank { "{}" }}}\n</tool_call>"
                }
                listOfNotNull(m.content?.takeIf { it.isNotBlank() }, calls.takeIf { it.isNotBlank() })
                    .joinToString("\n").takeIf { it.isNotBlank() }?.let { Turn("assistant", it) }
            }
            "tool" -> Turn(
                "user",
                "<tool_result name=\"${m.name ?: "tool"}\">\n${m.content.orEmpty()}\n</tool_result>"
            )
            else -> null
        }
    }
}

/** Shared HTTP plumbing: cancellable calls, SSE `data:` lines, error mapping. */
internal object CommunityHttp {

    /** Execute [request] and stream its `data:` payloads to [onData]; maps HTTP errors. */
    suspend fun FlowCollector<StreamEvent>.sse(
        siteName: String,
        request: Request,
        onData: suspend FlowCollector<StreamEvent>.(String) -> Boolean
    ) {
        execute(request) { response ->
            if (!response.isSuccessful) {
                emit(httpError(siteName, response))
                return@execute
            }
            emit(StreamEvent.Started)
            val source = response.body?.source() ?: run {
                emit(StreamEvent.Error("$siteName sent an empty response", retryable = true)); return@execute
            }
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                emit(StreamEvent.RawSSE(data))
                if (!onData(data)) break
            }
        }
    }

    /** Run a call, cancelling it if the collecting coroutine is cancelled. */
    suspend fun <T> execute(
        request: Request,
        client: okhttp3.OkHttpClient = AiHttp.streaming,
        block: suspend (Response) -> T
    ): T {
        val call: Call = client.newCall(request)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            return call.execute().use { block(it) }
        } finally {
            handle?.dispose()
        }
    }

    fun httpError(siteName: String, response: Response): StreamEvent.Error {
        val body = runCatching { response.body?.string() }.getOrNull()
        val detail = ChatStreamParser.describeErrorBody(body)
        val code = response.code
        val message = when {
            code == 413 -> "$siteName: too much text for this site in one request" +
                (detail?.let { " ($it)" } ?: "")
            code == 429 -> "$siteName rate limit reached" + (detail?.let { ": $it" } ?: "")
            detail != null -> "$siteName: $detail ($code)"
            else -> "$siteName request failed ($code)"
        }
        return StreamEvent.Error(
            message,
            httpCode = code,
            retryAfterMs = ChatStreamParser.retryAfterMs(response.header("Retry-After"), body),
            retryable = ChatStreamParser.isRetryableStatus(code)
        )
    }

    /** Wrap a client body so no exception escapes and it runs on IO. */
    fun guarded(siteName: String, body: suspend FlowCollector<StreamEvent>.() -> Unit): Flow<StreamEvent> = flow {
        try {
            body()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(
                StreamEvent.Error(
                    "$siteName: ${e.message ?: e.javaClass.simpleName}",
                    retryable = e is java.io.IOException
                )
            )
        }
    }.flowOn(Dispatchers.IO)
}
