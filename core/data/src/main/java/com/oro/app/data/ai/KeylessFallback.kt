package com.oro.app.data.ai

import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps keyless use working when one free model is busy.
 *
 * Free, anonymous models fail in ordinary ways all the time: a daily quota
 * runs out, an upstream is overloaded, a guest endpoint asks for a human
 * check. With no key there is nothing for the user to fix, so instead of
 * ending the run the request moves to the next free model that can serve it
 * (one with native tools when the request carries tools), and the busy one is
 * skipped for a while. Only failures that arrive before any output switch
 * models, so a reply is never stitched from two models.
 *
 * The order is by measurement (2026-10-18, every keyless model probed with a
 * streamed tool call): fast and reliable first, rate-paced ones last.
 */
object KeylessFallback {

    private val ORDER = listOf(
        "kilo-auto/free",
        "openrouter/free",
        "Qwen3.8-27B",
        "DeepSeek-V4-Flash-0731",
        "poolside/laguna-s-2.1:free",
        "Qwen3.5-397B-A17B",
        "codestral-latest",
        "reflection/beam-free",
        "k2:IFM/K2-Horizon-375B-A23B",
        "ptero:deepseek-v4.1-flash:free",
        "qwen:qwen3.7-plus",
        "openai-fast"
    )

    /** Model id -> time until which it is skipped. */
    private val busyUntil = ConcurrentHashMap<String, Long>()

    fun isBusy(id: String, now: Long = System.currentTimeMillis()) = (busyUntil[id] ?: 0L) > now

    fun markBusy(id: String, error: StreamEvent.Error, now: Long = System.currentTimeMillis()) {
        val hint = error.retryAfterMs?.coerceIn(5_000, 30 * 60_000L)
        val ms = hint ?: when (error.httpCode) {
            401, 402, 403 -> 30 * 60_000L // needs a key or a check: not coming back soon
            429 -> 90_000L
            else -> 45_000L
        }
        busyUntil[id] = now + ms
    }

    fun markHealthy(id: String) { busyUntil.remove(id) }

    /**
     * Whether [error] is the kind another model would not have: quota, auth
     * for a key-only model, overload, a guest check. A 400 about tools is left
     * to the harness, which retries the same model with text tool calls.
     */
    fun shouldFallBack(error: StreamEvent.Error): Boolean {
        val code = error.httpCode
        val msg = error.message
        if (code == 400 && Regex("tool|function", RegexOption.IGNORE_CASE).containsMatchIn(msg)) return false
        if (code != null) return code in setOf(401, 402, 403, 404, 408, 409, 413, 429) || code >= 500
        return error.retryable ||
            Regex("rate limit|quota|overload|busy|unavailable|human check|empty|timed? ?out", RegexOption.IGNORE_CASE).containsMatchIn(msg)
    }

    /** Models to try after [model], in order: free, healthy, and able to take [needsNativeTools]. */
    fun candidates(model: AiModel, needsNativeTools: Boolean, lookup: (String) -> AiModel?): List<AiModel> =
        ORDER.asSequence()
            .filter { it != model.id && !isBusy(it) }
            .mapNotNull(lookup)
            .filter { !needsNativeTools || (it.supportsToolCalls && !it.provider.isCommunity) }
            .toList()
}
