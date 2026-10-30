package com.oro.app.data.ai

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps keyless use working when one free model is busy.
 *
 * Free, anonymous models fail in ordinary ways all the time: a daily quota
 * runs out, an upstream is overloaded, a guest endpoint asks for a human
 * check, a model id is retired. With no key there is nothing for the user to
 * fix, so instead of ending the run the request moves to the next free model
 * that can serve it (one with native tools when the request carries tools),
 * and the busy one is skipped for a while. Only failures that arrive before
 * any output switch models, so a reply is never stitched from two models.
 *
 * The order is by measurement (2026-10-22, every keyless model probed from
 * two networks with a streamed tool call and a ~50k-token prompt; the live
 * evals of 2026-10-23): strong native-tool models with 1-3 s to the first
 * token first. The community entries (UniKey, Mercury, K2, Ptero, Qwen,
 * Cloudflare's Gemma) take no native tools, so they only ever stand in for
 * text-mode requests. Routes too limited to carry an agent run (Pollinations,
 * VLM Run, the anonymous OVHcloud and LLM7 tiers, MiniCPM's demo) were
 * removed on 2026-10-23 (docs/research/keyless-providers.md).
 */
object KeylessFallback {

    private val ORDER = listOf(
        // Tencent's Hy3 demo: native tool calls, 256K, ~2 s to the first token (2026-10-23). In the
        // live eval (docs/research/live-eval.md) it built and self-checked a landing page, an app and
        // a game in 5 to 10 minutes each.
        "hy3:hy3",
        // Kilo's free pool, all native tools: 550B, 1M context -- but on agent-sized prompts its
        // upstream often times out before the first token ("Upstream idle timeout exceeded").
        "nvidia/nemotron-3-ultra-550b-a55b:free",
        "kilo-auto/free",
        "cohere/north-mini-code:free",
        "dots-studio/dots-3-note-preview:free",
        "nvidia/nemotron-3.5-super-vl:free",
        "openrouter/free",
        // Claude Opus 4.8 answering in ~3 s (2026-10-22): the strongest free stand-in for text-mode runs.
        "unikey:claude-opus-4-8",
        // Mercury 3 (the default): over 1,000 tokens/s when its site lets the phone through.
        "mercury:mercury-3",
        "poolside/laguna-s-2.1:free",
        "nvidia/nemotron-3.5-lightning:free",
        // Poolside's own Laguna demo: a second route to Laguna S 2.1, on the demo's key (tools via GLM's template).
        "laguna:laguna-s-2.1",
        "unikey:minimax/minimax-m3",
        "reflection/beam-free",
        "recursive/flint-v1.0:free",
        // Text-mode stand-in found 2026-10-23: Cloudflare's playground (per-minute
        // caps shared by all its visitors).
        "cfai:@cf/google/gemma-4-26b-a4b-it",
        // Gemini's anonymous web chat: cookie-rotating, text-only, a good
        // stand-in when the other text routes are rate limited.
        "geminiweb:gemini-flash-lite",
        "k2:IFM/K2-Horizon-375B-A23B",
        "ptero:deepseek-v4.1-flash:free",
        "qwen:qwen3.7-plus"
    )

    /**
     * Ids of the models the user has switched on, or null before the first
     * read (then nothing is filtered). A stand-in the user switched off is
     * never used: it may be a host they chose to avoid. Kept current by
     * AiModelRepository whenever the enabled list is read or observed.
     */
    @Volatile
    var enabledIds: Set<String>? = null

    /** Model id -> time until which it is skipped. */
    private val busyUntil = ConcurrentHashMap<String, Long>()

    /** Guest site -> time until which all its models are skipped (a browser check is site-wide). */
    private val siteBusyUntil = ConcurrentHashMap<String, Long>()

    fun isBusy(id: String, now: Long = System.currentTimeMillis()) =
        (busyUntil[id] ?: 0L) > now || (siteOf(id)?.let { siteBusyUntil[it] } ?: 0L) > now

    /** The guest site behind a namespaced seed id ("qwen:qwen3.8-max" -> "qwen"), for sites behind a browser check. */
    private fun siteOf(id: String): String? = id.substringBefore(':', "").takeIf { it in BROWSER_CHECK_SITES }

    private val BROWSER_CHECK_SITES = setOf("qwen", "mercury")

    fun markBusy(id: String, error: StreamEvent.Error, now: Long = System.currentTimeMillis()) {
        val hint = error.retryAfterMs?.coerceIn(5_000, 30 * 60_000L)
        // A site's browser check (Mercury's Vercel checkpoint) that the in-app
        // browser could not pass will not clear in seconds; skip the model for a
        // while so every request does not wait on it first.
        val browserCheck = Regex("browser check|security checkpoint", RegexOption.IGNORE_CASE).containsMatchIn(error.message)
        val ms = when {
            browserCheck -> BROWSER_CHECK_MS
            // A retired id stays gone; check again after a few hours.
            isRetiredModel(error) -> RETIRED_SKIP_MS
            hint != null -> hint
            error.message.startsWith(SILENT_PREFIX) -> SILENT_SKIP_MS
            // A gateway whose upstream timed out before answering stays slow for a while.
            UPSTREAM_TIMEOUT.containsMatchIn(error.message) -> SILENT_SKIP_MS
            else -> when (error.httpCode) {
                401, 402, 403 -> 30 * 60_000L // needs a key or a check: not coming back soon
                429 -> 90_000L
                else -> 45_000L
            }
        }
        busyUntil[id] = now + ms
        // A human check is about the site and the network, not one model: the
        // site's other models would wait on the same check first.
        if (browserCheck) siteOf(id)?.let { siteBusyUntil[it] = now + ms }
    }

    fun markHealthy(id: String) {
        busyUntil.remove(id)
        siteOf(id)?.let { siteBusyUntil.remove(it) }
    }

    private const val BROWSER_CHECK_MS = 10 * 60_000L

    /**
     * Whether [error] is the kind another model would not have: quota, auth
     * for a key-only model, overload, a guest check, a model the gateway no
     * longer serves. A 400 about tools is left to the harness, which retries
     * the same model with text tool calls.
     */
    fun shouldFallBack(error: StreamEvent.Error): Boolean {
        val code = error.httpCode
        val msg = error.message
        if (code == 400 && Regex("tool|function", RegexOption.IGNORE_CASE).containsMatchIn(msg)) return false
        if (isRetiredModel(error)) return true
        if (code != null) return code in setOf(401, 402, 403, 404, 408, 409, 413, 429) || code >= 500
        return error.retryable ||
            Regex("rate limit|quota|overload|busy|unavailable|human check|empty|timed? ?out", RegexOption.IGNORE_CASE).containsMatchIn(msg)
    }

    /**
     * "Model 'X' is currently unavailable" (LLM7), "model_not_found", "No
     * endpoints found for ..." (OpenRouter-style gateways): the id is gone,
     * not busy. Answered with a 400 or 404 rather than a 5xx, so without this
     * check a retired seed failed the whole run instead of handing over.
     */
    fun isRetiredModel(error: StreamEvent.Error): Boolean =
        error.httpCode in setOf(400, 404, 410, 422, null) && RETIRED.containsMatchIn(error.message)

    private val RETIRED = Regex(
        "model[_ ]?(not[_ ]found|unavailable|does not exist|is not available|not available)|" +
            "model\\b[^\\n]{0,80}?\\b(is (currently )?unavailable|not found|does not exist|no longer (available|supported|served)|has been (removed|retired|deprecated))|" +
            "no endpoints found|unknown model|no such model|invalid model",
        RegexOption.IGNORE_CASE
    )

    /**
     * Models to try after [model], in order: free, healthy, switched on
     * ([enabled]), able to take [needsNativeTools], and with a window of at
     * least [minContextTokens] (a stand-in must hold the request the selected
     * model's window let through). Stand-ins from [model]'s own family come
     * first, so a Qwen stays a Qwen when its usual host is down; the global
     * order follows.
     */
    fun candidates(
        model: AiModel,
        needsNativeTools: Boolean,
        minContextTokens: Int = 0,
        enabled: Set<String>? = enabledIds,
        lookup: (String) -> AiModel?
    ): List<AiModel> {
        val family = sameFamily(model)
        return (family + ORDER.filter { it !in family }).asSequence()
            .filter { it != model.id && !isBusy(it) && (enabled == null || it in enabled) }
            .mapNotNull(lookup)
            .filter {
                !needsNativeTools || (it.supportsToolCalls &&
                    (!it.provider.isCommunity || com.oro.app.data.ai.community.CommunityClients.takesTools(it.provider)))
            }
            .filter { minContextTokens <= 0 || it.contextLength >= minContextTokens }
            // chat.qwen.ai refused this request: the site's other models would be refused too.
            .filter { !(model.provider == AiProvider.QWEN_WEB && it.provider == AiProvider.QWEN_WEB) }
            .toList()
    }

    /**
     * Keyless hosts of the same model family, best first. Qwen: Ptero's Qwen
     * 3.7 Flash (text only, verified keyless from two networks on 2026-10-23).
     * VLM Run's and OVHcloud's anonymous Qwen routes (30 requests an hour, 2 a
     * minute) were removed as too limited for an agent run; the global order
     * follows with stronger stand-ins.
     */
    internal fun sameFamily(model: AiModel): List<String> {
        val qwen = model.provider == AiProvider.QWEN_WEB ||
            com.oro.app.data.ai.providers.ModelIds.upstream(model.id).contains("qwen", ignoreCase = true)
        return if (qwen) QWEN_FAMILY else emptyList()
    }

    private val QWEN_FAMILY = listOf("ptero:qwen3.7-flash:free")

    /** Ids in fallback order, for tests and diagnostics. */
    internal val order: List<String> get() = ORDER

    /**
     * Gives up on a candidate that sends nothing at all -- not even response
     * headers -- for [timeoutMs], so a dead or hanging site hands over in
     * seconds instead of holding the run until the stream's idle watchdog
     * (minutes). Any first event (Started included) disarms it; after that
     * the stream is passed through untouched. Providers that answer in one
     * piece (Ptero) are left alone: their headers come with the whole reply.
     */
    fun guardSilence(candidate: AiModel, stream: Flow<StreamEvent>): Flow<StreamEvent> {
        val timeoutMs = silenceTimeoutMs(candidate.provider) ?: return stream
        return stream.firstEventWithin(timeoutMs) {
            StreamEvent.Error(
                "$SILENT_PREFIX ${candidate.name} sent nothing for ${timeoutMs / 1000} s",
                retryable = true
            )
        }
    }

    /**
     * How long [provider] may stay silent before its first event, or null for
     * no limit. Ptero answers in one piece; the Mercury and Qwen clients may
     * first open the site in a hidden browser to pass its check, which takes
     * a while on a slow phone.
     */
    internal fun silenceTimeoutMs(provider: AiProvider): Long? = when (provider.protocol) {
        ProviderProtocol.PTERO_GUEST -> null
        // A Space may be waking up or queueing; the in-app browser bridges load a site first.
        ProviderProtocol.MERCURY_WEB, ProviderProtocol.QWEN_WEB, ProviderProtocol.HF_SPACE -> BROWSER_SILENT_TIMEOUT_MS
        else -> SILENT_TIMEOUT_MS
    }

    const val SILENT_TIMEOUT_MS = 35_000L
    private const val BROWSER_SILENT_TIMEOUT_MS = 75_000L
    private const val SILENT_PREFIX = "No answer:"
    private val UPSTREAM_TIMEOUT = Regex("upstream (idle )?time ?out|gateway time-?out|upstream request timeout", RegexOption.IGNORE_CASE)
    private const val SILENT_SKIP_MS = 5 * 60_000L
    private const val RETIRED_SKIP_MS = 6 * 60 * 60_000L
}

/**
 * Passes [this] through, unless no event at all arrives within [timeoutMs]:
 * then the upstream is cancelled and [onSilent]'s error is the only event.
 */
internal fun Flow<StreamEvent>.firstEventWithin(
    timeoutMs: Long,
    onSilent: () -> StreamEvent.Error
): Flow<StreamEvent> {
    val upstream = this
    return channelFlow {
        val events = upstream.buffer(Channel.UNLIMITED).produceIn(this)
        val first = withTimeoutOrNull(timeoutMs) { events.receiveCatching() }
        if (first == null) {
            events.cancel()
            send(onSilent())
            return@channelFlow
        }
        if (first.isClosed) {
            first.exceptionOrNull()?.let { throw it }
            return@channelFlow
        }
        send(first.getOrThrow())
        for (event in events) send(event)
    }
}
