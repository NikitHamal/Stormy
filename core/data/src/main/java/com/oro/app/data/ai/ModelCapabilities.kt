package com.oro.app.data.ai

/**
 * Context window sizes, derived from the model id.
 *
 * ### Why this table exists
 *
 * The OpenAI-compatible `/models` response carries no context length for most
 * providers -- it returns ids and little else. The previous fallback only
 * matched ids that *literally contained* a size ("…-128k"), which almost none
 * do, so essentially every discovered model fell back to 8192 tokens.
 *
 * That was not a cosmetic problem. The context meter read 49% on a model with
 * a 1M window, compaction fired on conversations that had used a fraction of
 * what was available, and the optimiser truncated history that did not need
 * truncating. A wrong context size silently degrades every long conversation.
 *
 * ### How to read the ordering
 *
 * Matching is longest-prefix-first, so `gemini-3.1-flash-lite` is tested
 * before `gemini-3`. Families are matched on the part of the id that is
 * stable across providers -- the same model appears as `openai/gpt-oss-120b`
 * on one gateway and `gpt-oss-120b` on another.
 *
 * Figures are conservative where sources disagree: under-reporting a window
 * costs an unnecessary compaction, over-reporting costs a hard provider error
 * mid-conversation, which is much worse.
 */
object ModelCapabilities {

    private const val K = 1_024

    /** id fragment to context window, most specific first. */
    private val CONTEXT_WINDOWS: List<Pair<String, Int>> = listOf(
        // --- Google Gemini: 1M from 1.5 onward, Flash-Lite included --------
        "gemini-3" to 1_048_576,
        "gemini-2.5" to 1_048_576,
        "gemini-2.0" to 1_048_576,
        "gemini-1.5-flash-8b" to 1_000_000,
        "gemini-1.5" to 1_048_576,
        "gemini-exp" to 1_048_576,
        "gemini" to 32 * K,

        // --- OpenAI / GPT ---------------------------------------------------
        "gpt-6" to 400_000,
        "gpt-5" to 400_000,
        "codex" to 400_000,
        "gpt-oss-120b" to 131_072,
        "gpt-oss-20b" to 131_072,
        "gpt-4.1" to 1_047_576,
        "gpt-4o" to 128_000,
        "o3" to 200_000,
        "o1" to 200_000,

        // --- Anthropic ------------------------------------------------------
        "claude-opus" to 200_000,
        "claude-sonnet" to 200_000,
        "claude-haiku" to 200_000,
        "claude-fable" to 200_000,

        // --- Qwen -----------------------------------------------------------
        "qwen3-coder" to 262_144,
        "qwen3.8" to 262_144,
        "qwen3.6" to 262_144,
        "qwen3.5" to 262_144,
        "qwen3" to 131_072,
        "qwen2.5" to 131_072,
        "qwen" to 32 * K,

        // --- DeepSeek -------------------------------------------------------
        "deepseek-v4" to 163_840,
        "deepseek-v3" to 163_840,
        "deepseek-r1" to 163_840,
        "deepseek" to 65_536,

        // --- Meta Llama -----------------------------------------------------
        "llama-4" to 1_048_576,
        "llama-3.3" to 131_072,
        "llama-3.1" to 131_072,
        "llama-3" to 8 * K,

        // --- NVIDIA Nemotron ------------------------------------------------
        "nemotron-3" to 131_072,
        "nemotron" to 131_072,

        // --- Others ---------------------------------------------------------
        "kimi-k2" to 131_072,
        "kimi-k3" to 262_144,
        "glm-5" to 200_000,
        "glm-4" to 131_072,
        "minimax-m" to 1_000_000,
        "grok-4" to 256_000,
        "grok" to 131_072,
        "mistral-large" to 131_072,
        "devstral" to 131_072,
        "mixtral" to 65_536,
        "mistral" to 32 * K,
        "agnes-2.5" to 524_288,
        "agnes" to 131_072,
        "phi-4" to 16 * K,
        "gemma-3" to 131_072,
        "gemma" to 8 * K
    ).sortedByDescending { it.first.length }

    /** Used when nothing matches: a modern small model, not a 2023 one. */
    const val DEFAULT_CONTEXT = 32_768

    /**
     * Best-known context window for [modelId].
     *
     * An explicit size in the id always wins -- a provider serving a
     * "…-64k" variant of a 1M model means it.
     */
    fun contextWindowFor(modelId: String): Int {
        val id = modelId.lowercase()

        explicitSizeIn(id)?.let { return it }
        CONTEXT_WINDOWS.firstOrNull { (fragment, _) -> id.contains(fragment) }
            ?.let { return it.second }

        return DEFAULT_CONTEXT
    }

    /** Reads "-128k", "1m", "32k" out of an id when the provider states one. */
    private fun explicitSizeIn(id: String): Int? {
        Regex("""(\d+)\s*m\b""").find(id)?.let { match ->
            return match.groupValues[1].toIntOrNull()?.times(1_000_000)
        }
        Regex("""(\d+)\s*k\b""").find(id)?.let { match ->
            return match.groupValues[1].toIntOrNull()?.times(K)
        }
        return null
    }
}

/**
 * Free-tier request budgets, as published by each provider.
 *
 * ### Why the app enforces these itself
 *
 * Every provider here is free, and free tiers are rate limited rather than
 * billed. Hitting the limit does not degrade gracefully -- it returns a 429
 * mid-conversation, usually part-way through an agent run, which looks to the
 * user like the app broke. Pacing requests locally turns a hard failure into a
 * short wait.
 *
 * ### On the numbers
 *
 * Published limits move, and several providers no longer publish fixed figures
 * at all. These are deliberately set *below* the documented values, because the
 * cost of being slightly conservative is a brief delay, and the cost of being
 * slightly optimistic is the failure this exists to prevent. They are also the
 * organisation-level limits: additional API keys do not raise them, so there is
 * no point tracking per-key.
 */
data class RateLimit(
    /** Requests per minute. */
    val requestsPerMinute: Int,
    /** Tokens per minute, usually the binding constraint rather than RPM. */
    val tokensPerMinute: Int,
    /** Requests per day, 0 when the provider publishes none. */
    val requestsPerDay: Int = 0
) {
    /** Minimum spacing between requests to stay under [requestsPerMinute]. */
    val minIntervalMs: Long
        get() = if (requestsPerMinute <= 0) 0L else 60_000L / requestsPerMinute
}

object RateLimits {

    /**
     * Known free-tier budgets.
     *
     * Groq's tokens-per-minute is the one that bites first: 6k TPM is roughly
     * 4,500 words of combined prompt and reply, which a single agent turn with
     * tool definitions can exceed on its own.
     */
    private val limits: Map<AiProvider, RateLimit> = mapOf(
        AiProvider.GROQ to RateLimit(requestsPerMinute = 28, tokensPerMinute = 6_000, requestsPerDay = 14_000),
        AiProvider.CEREBRAS to RateLimit(requestsPerMinute = 5, tokensPerMinute = 30_000),
        AiProvider.GEMINI to RateLimit(requestsPerMinute = 10, tokensPerMinute = 250_000, requestsPerDay = 1_400),
        AiProvider.AGNES to RateLimit(requestsPerMinute = 18, tokensPerMinute = 100_000),
        AiProvider.MISTRAL to RateLimit(requestsPerMinute = 10, tokensPerMinute = 50_000),
        AiProvider.NVIDIA to RateLimit(requestsPerMinute = 38, tokensPerMinute = 100_000),
        AiProvider.OPENROUTER to RateLimit(requestsPerMinute = 18, tokensPerMinute = 50_000, requestsPerDay = 950),
        // Anonymous tier; no published figures, so paced gently.
        AiProvider.POLLINATIONS to RateLimit(requestsPerMinute = 12, tokensPerMinute = 40_000)
    )

    /** Budget for a provider, or null when it is not rate limited by us. */
    operator fun get(provider: AiProvider): RateLimit? = limits[provider]
}
