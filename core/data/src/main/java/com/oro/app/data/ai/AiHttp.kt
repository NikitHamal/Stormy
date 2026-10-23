package com.oro.app.data.ai

import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One HTTP client and one JSON codec for everything that talks to an AI
 * provider.
 *
 * Every provider and model service used to build its own `OkHttpClient`. Each
 * of those carries a connection pool and an executor, so six clients meant six
 * thread pools and no connection reuse between, say, listing models and then
 * streaming from the same host. They also could not share a single TLS
 * handshake. Sharing one client fixes all of that, and it is the configuration
 * OkHttp is designed for -- per-request settings that genuinely differ (the
 * long read timeout a stream needs) are applied per call instead.
 */
object AiHttp {

    const val STREAM_READ_TIMEOUT_SECONDS = 300L

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
    }

    val jsonMediaType = "application/json".toMediaType()

    /**
     * Shared client. Timeouts here are the *short* ones suitable for ordinary
     * request/response calls; streaming uses [streaming] below.
     */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(maxIdleConnections = 8, keepAliveDuration = 5, TimeUnit.MINUTES))
            .dispatcher(
                Dispatcher().apply {
                    // A phone has no business opening 64 sockets to one host.
                    maxRequests = 24
                    maxRequestsPerHost = 8
                }
            )
            .build()
    }

    /**
     * Client for SSE streams. Shares the connection pool and dispatcher with
     * [client] (that is what `newBuilder()` preserves) but uses a long read
     * timeout, because a model that thinks for ninety seconds before its first
     * token is normal and must not be treated as a dead connection.
     *
     * Not infinite, though: it used to be 0 (no timeout), and a stream that
     * stalled after the headers then hung the agent run forever with the
     * "Thinking" indicator gone. Five minutes of silence on one socket read is
     * a dead connection; the agent loop also has its own idle watchdog.
     */
    val streaming: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Events emitted while a completion streams.
 *
 * Lives here rather than beside one provider so that every provider can emit
 * them without depending on another provider's file.
 */
sealed class StreamEvent {
    data object Started : StreamEvent()
    data class ContentDelta(val content: String) : StreamEvent()
    data class ReasoningDelta(val reasoning: String) : StreamEvent()
    data class ToolCalls(val toolCalls: List<ToolCallResponse>) : StreamEvent()
    data class FinishReason(val reason: String) : StreamEvent()

    /**
     * The request failed. [retryable] marks transient failures (rate limits,
     * 5xx, dropped connections) that the agent loop should retry with backoff;
     * [retryAfterMs] is the server's own hint when it gave one.
     */
    data class Error(
        val message: String,
        val httpCode: Int? = null,
        val retryAfterMs: Long? = null,
        val retryable: Boolean = false
    ) : StreamEvent()

    /** Token usage reported by the provider, when it reports any. */
    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        /** Prompt tokens served from the provider's prompt cache (already included in [promptTokens]). */
        val cachedTokens: Int = 0
    ) : StreamEvent()
    data object Completed : StreamEvent()

    /** Unparsed SSE payload, forwarded for the debug log. */
    data class RawSSE(val data: String) : StreamEvent()
}
