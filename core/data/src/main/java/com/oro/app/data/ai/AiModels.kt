package com.oro.app.data.ai

import kotlinx.serialization.Serializable

/**
 * Represents an AI model available for use
 */
@Serializable
data class AiModel(
    val id: String,
    val name: String,
    val provider: AiProvider,
    /** Effective window, already resolved (see [ModelCapabilities.resolveContextLength]). */
    val contextLength: Int = ModelCapabilities.DEFAULT_CONTEXT,
    /**
     * What the provider's `/models` (or the seed, or the user) stated, kept
     * separately so a later table update can still correct models whose
     * provider said nothing.
     */
    val reportedContextLength: Int? = null,
    val supportsStreaming: Boolean = true,
    val supportsToolCalls: Boolean = true,
    val isThinkingModel: Boolean = false,
    /**
     * Whether the user has this model switched on.
     *
     * Only the model picker and the management screen read it; the request
     * path never does, because a disabled model should not reach it. Carried
     * on the domain model rather than looked up separately so a list of models
     * and their state cannot drift apart between two queries.
     */
    val isEnabled: Boolean = true,
    /** USD per token as the provider publishes it (OpenRouter/Kilo); null when unknown. */
    val inputPricePerToken: Double? = null,
    val outputPricePerToken: Double? = null
) {
    /**
     * The reasoning control this model exposes on its provider, with the
     * provider's own levels ([ReasoningControls]); null when it has none.
     *
     * Derived rather than stored, and separate from [isThinkingModel]: a model
     * can reason without letting the caller size it, and sending a level to
     * one that does not take it is a 400 on several providers.
     */
    val reasoningControl: ReasoningControl? get() = ReasoningControls.forModel(this)

    val supportsReasoningEffort: Boolean get() = reasoningControl != null
}

/** How Oro talks to a provider. */
enum class ProviderProtocol {
    /** OpenAI-compatible `/chat/completions` + `/models`. */
    OPENAI,
    /** chat.ifm.ai guest completions, cumulative `content` frames. */
    K2_GUEST,
    /** chat.qwen.ai guest chat: browser-signed requests, phase-tagged SSE. */
    QWEN_WEB,
    /** ptero.pro guest chat: one JSON reply per request, quota per guest token. */
    PTERO_GUEST,
    /** chat.inceptionlabs.ai guest chat (Mercury 3): session token + Vercel AI SDK v5 stream. */
    MERCURY_WEB,
    /** getunikey.ai anonymous trial: OpenAI-style SSE, a fresh visitor id per request, no tools. */
    UNIKEY_GUEST,
    /** A lab's official Hugging Face Space relaying to its own API, over Gradio's queue protocol. */
    HF_SPACE,
    /** Cloudflare's AI Playground: an Agents SDK WebSocket (setConfig RPC + AI SDK chat stream). */
    CF_PLAYGROUND
}

/** How a provider treats credentials. */
enum class ApiKeyRequirement {
    /** Works with no credentials at all. */
    NONE,

    /** Works anonymously, but a key raises limits or unlocks more models. */
    OPTIONAL,

    /** Will not answer without a key. */
    REQUIRED
}

/**
 * Supported AI providers.
 *
 * Every field a provider needs lives on the enum constant, so adding one is a
 * single line here rather than an edit in the manager, the builder, four
 * exhaustive `when` blocks and the settings screen. [extraHeaders] covers the
 * providers that want attribution or browser-ish headers.
 *
 * On key requirements, verified with live chat completions (streaming and
 * native tool calls) from a plain HTTP client on 2026-10-17:
 *  - Pollinations (`text.pollinations.ai/openai`, model `openai-fast`) answers
 *    anonymously; roughly one reply per 30 s per IP, over that it answers
 *    HTTP 402 `FREE_LIMIT` with a retry hint.
 *  - OVHcloud AI Endpoints answers anonymously on its documented free tier;
 *    2 requests/min per model per IP, HTTP 429 when exceeded.
 *  - Kilo's gateway (`api.kilo.ai/api/gateway`) serves its `:free` / `isFree`
 *    models without a key (about 200 requests/hour per IP); a key unlocks the
 *    rest of the catalogue.
 * OpenCode Zen's free tier is gated to the OpenCode client itself ("can only
 * be used from within OpenCode"); re-tested 2026-10-17 with the
 * `Bearer public` + `User-Agent: opencode/1.0` headers some tools use, every
 * listed free model still answered FreeTierError (or "Model is unavailable"
 * for retired ids), so it is not a keyless option -- and passing as their
 * client would be impersonation anyway.
 *
 * Community providers ([ProviderProtocol] other than OPENAI) are public guest
 * web chats bridged by their own protocol clients in `ai/community`. They are
 * unofficial: no SLA, may change or disappear, no native tool calling (Orobi
 * uses text tool calls). Only guest endpoints that answer a plain HTTPS client
 * are included -- nothing that needs a browser fingerprint, a WAF/captcha
 * token, signing SDKs or a user's cookies. The rest are
 * free to sign up for without billing details, which is a different thing
 * from keyless and is labelled as such in the UI.
 *
 * DeepInfra was removed outright: its anonymous endpoint was retired, which
 * left it as one more key to manage for models every other provider here also
 * serves.
 */
@Serializable
enum class AiProvider(
    val displayName: String,
    val baseUrl: String,
    val apiKeyRequirement: ApiKeyRequirement,
    /** Where the user goes to get a key. Empty when none is needed. */
    val apiKeyUrl: String = "",
    /** Short note shown under the key field in Settings. */
    val keyNote: String = "",
    val extraHeaders: Map<String, String> = emptyMap(),
    /** Wire protocol. Anything but OPENAI is a community web-chat bridge. */
    val protocol: ProviderProtocol = ProviderProtocol.OPENAI
) {
    POLLINATIONS(
        displayName = "Pollinations",
        baseUrl = "https://text.pollinations.ai/openai",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "No account needed. Anonymous tier (about one reply every 30 s), tool calling supported."
    ),
    OVHCLOUD(
        displayName = "OVHcloud AI Endpoints",
        baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1",
        apiKeyRequirement = ApiKeyRequirement.OPTIONAL,
        apiKeyUrl = "https://endpoints.ai.cloud.ovh.net",
        keyNote = "No account needed. Anonymous tier is 2 requests/min per model; " +
            "a key raises it. Qwen 3.5/3.8, gpt-oss, Llama 3.3 with tool calling."
    ),
    GEMINI(
        displayName = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        apiKeyUrl = "https://aistudio.google.com/apikey",
        keyNote = "Free tier, no billing details required."
    ),
    NVIDIA(
        displayName = "NVIDIA NIM",
        baseUrl = "https://integrate.api.nvidia.com/v1",
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        apiKeyUrl = "https://build.nvidia.com",
        keyNote = "Free credits on signup. Hosts Nemotron, Qwen, DeepSeek, gpt-oss."
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        apiKeyUrl = "https://openrouter.ai/keys",
        keyNote = "Has a catalogue of :free models usable on a free key.",
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://github.com/NikitHamal/Stormy",
            "X-Title" to "Oro"
        )
    ),
    OPENCODE_ZEN(
        displayName = "OpenCode Zen",
        baseUrl = "https://opencode.ai/zen/v1",
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        apiKeyUrl = "https://opencode.ai/auth",
        keyNote = "Signup needs no billing details. The *-free models are " +
            "restricted to the OpenCode client and will be rejected here."
    ),
    AGNES(
        displayName = "Agnes AI",
        baseUrl = "https://apihub.agnes-ai.com/v1",
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        apiKeyUrl = "https://platform.agnes-ai.com",
        keyNote = "Free tier with no stated end date, rate limited to ~20 RPM."
    ),
    KILOCODE(
        displayName = "Kilo Gateway",
        baseUrl = "https://api.kilo.ai/api/gateway",
        apiKeyRequirement = ApiKeyRequirement.OPTIONAL,
        apiKeyUrl = "https://app.kilo.ai/profile",
        keyNote = "Free models work with no key (about 200 requests/hour); some " +
            "free upstreams may log prompts. A Kilo key unlocks the full catalogue.",
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://github.com/NikitHamal/Stormy",
            "X-Title" to "Oro",
            "X-KiloCode-Version" to "1.0.0"
        )
    ),
    GROQ(
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        apiKeyUrl = "https://console.groq.com/keys",
        keyNote = "Free tier, no billing details. Fastest tokens/sec of the set."
    ),
    CEREBRAS(
        displayName = "Cerebras",
        baseUrl = "https://api.cerebras.ai/v1",
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        apiKeyUrl = "https://cloud.cerebras.ai",
        keyNote = "Free tier with a generous daily token allowance."
    ),
    MISTRAL(
        displayName = "Mistral",
        baseUrl = "https://api.mistral.ai/v1",
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        apiKeyUrl = "https://console.mistral.ai/api-keys",
        keyNote = "Free experiment tier on signup."
    ),
    K2_HORIZON(
        displayName = "K2 Horizon (community)",
        baseUrl = "https://chat.ifm.ai",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Unofficial bridge to MBZUAI IFM's guest chat (K2 Horizon 375B, " +
            "reasoning). Long inputs, no native tools. May break without notice.",
        protocol = ProviderProtocol.K2_GUEST
    ),
    QWEN_WEB(
        displayName = "Qwen (community)",
        baseUrl = "https://chat.qwen.ai",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Unofficial bridge to Qwen Chat's guest mode (Qwen3.8 Max, Qwen3.7 Plus, Omni Flash). Falls back to " +
            "an in-app browser when the site asks for one, and to another free Qwen when it asks for a human check. May break without notice.",
        protocol = ProviderProtocol.QWEN_WEB
    ),
    PTERO(
        displayName = "Ptero (community)",
        baseUrl = "https://ptero.pro",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Unofficial bridge to ptero.pro's free guest chat (DeepSeek V4.1 Flash, Qwen 3.7 Flash, Mercury 2.5…). " +
            "A fresh guest identity per request. No native tools.",
        protocol = ProviderProtocol.PTERO_GUEST
    ),
    LLM7(
        displayName = "LLM7",
        baseUrl = "https://api.llm7.io/v1",
        apiKeyRequirement = ApiKeyRequirement.OPTIONAL,
        apiKeyUrl = "https://token.llm7.io",
        keyNote = "No account needed: anonymous tier (about 10 requests/min), native tool calling. A free token raises the limits."
    ),
    MERCURY(
        displayName = "Mercury (community)",
        baseUrl = "https://chat.inceptionlabs.ai",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Unofficial bridge to Inception's guest chat: Mercury 3, a very fast diffusion model. " +
            "Uses an in-app browser when the site asks for a browser check. No native tools. May break without notice.",
        protocol = ProviderProtocol.MERCURY_WEB
    ),
    UNIKEY(
        displayName = "UniKey (community)",
        baseUrl = "https://www.getunikey.ai",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Unofficial bridge to getunikey.ai's anonymous trial: Claude Opus 4.8, GPT-5.6, Grok 4.3, " +
            "DeepSeek V4, MiniMax M3, Kimi K2.7 Code. A fresh trial per request. No native tools or images. " +
            "May break without notice.",
        protocol = ProviderProtocol.UNIKEY_GUEST
    ),
    INCEPTION(
        displayName = "Inception (Mercury)",
        baseUrl = "https://api.inceptionlabs.ai/v1",
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        apiKeyUrl = "https://platform.inceptionlabs.ai/dashboard/api-keys",
        keyNote = "Official API for Mercury 3, the diffusion model: very fast, 1M context, native tool calling. " +
            "Has a free tier."
    ),
    VLM_RUN(
        displayName = "VLM Run",
        baseUrl = "https://gateway.vlm.run/v1/openai",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "No account needed: Qwen3.8 27B with native tool calling, under a second to the first token. " +
            "Anonymous limit 10 requests/min, 30/hour, 100/day per IP."
    ),
    HY3(
        displayName = "Hy3 (Tencent, community)",
        baseUrl = "https://tencent-hy3.hf.space",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Tencent's Hy3 (295B MoE, 256K context) through Tencent's own Hugging Face demo: no account, " +
            "native tool calls, think level. Shared with the demo's visitors; may change without notice.",
        protocol = ProviderProtocol.HF_SPACE
    ),
    CLOUDFLARE_PLAYGROUND(
        displayName = "Cloudflare AI Playground (community)",
        baseUrl = "https://playground.ai.cloudflare.com",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Workers AI models (Gemma 4 26B, GLM-4.7 Flash) through Cloudflare's public playground: " +
            "no account. Rate limited per minute across all its visitors; no native tools.",
        protocol = ProviderProtocol.CF_PLAYGROUND
    ),
    MINICPM(
        displayName = "MiniCPM (OpenBMB, community)",
        baseUrl = "https://openbmb-minicpm-v-4-7-demo.hf.space",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "OpenBMB's MiniCPM-V 4.7 (35B-A3B) through OpenBMB's own Hugging Face demo: no account. " +
            "Four requests at a time across all visitors; the demo logs conversations.",
        protocol = ProviderProtocol.HF_SPACE
    ),
    LAGUNA(
        displayName = "Laguna (Poolside, community)",
        baseUrl = "https://poolside-laguna-s-2-1.hf.space",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Poolside's Laguna S 2.1 coding model through Poolside's own Hugging Face demo: no account, " +
            "tool calls. Answers arrive whole (the demo does not stream); shared with the demo's visitors.",
        protocol = ProviderProtocol.HF_SPACE
    ),
    CUSTOM(
        displayName = "Custom (OpenAI-compatible)",
        baseUrl = "",
        apiKeyRequirement = ApiKeyRequirement.OPTIONAL,
        keyNote = "Point at any OpenAI-compatible server, e.g. a local Ollama."
    );

    /** True when the provider will not work until the user supplies a key. */
    val requiresApiKey: Boolean get() = apiKeyRequirement == ApiKeyRequirement.REQUIRED

    /** True when the provider can be used right now with no setup. */
    val worksWithoutKey: Boolean get() = apiKeyRequirement != ApiKeyRequirement.REQUIRED

    /** Unofficial guest web-chat bridge rather than a documented API. */
    val isCommunity: Boolean get() = protocol != ProviderProtocol.OPENAI

    companion object {
        fun fromString(value: String): AiProvider =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: DEFAULT

        /**
         * Provider used before the user configures anything. Must be one that
         * works with no key, otherwise a fresh install cannot send a message.
         */
        val DEFAULT = MERCURY
    }
}

/**
 * Seed catalogue.
 *
 * Providers that expose `GET /models` are queried at runtime and those results
 * win; the entries here exist so the model picker is not empty before the first
 * successful fetch, and so an offline user still has something selectable.
 * Keeping the list short is deliberate -- a long hand-maintained catalogue goes
 * stale, which is how the previous version ended up offering Llama 3.1 models
 * that the upstream host had already end-of-lifed.
 */
object SeedModels {

    private fun model(
        id: String,
        name: String,
        provider: AiProvider,
        /**
         * The window the provider documents for this id, checked when the
         * seed was written. Treated like a provider-reported figure.
         */
        context: Int,
        tools: Boolean = true,
        thinking: Boolean = false
    ) = AiModel(
        id = id,
        name = name,
        provider = provider,
        contextLength = ModelCapabilities.resolveContextLength(id, reported = context),
        reportedContextLength = context,
        supportsStreaming = true,
        supportsToolCalls = tools,
        isThinkingModel = thinking
    )

    /**
     * Pollinations serves one model on the anonymous tier. Verified October
     * 2026: `openai-fast` (GPT-OSS 20B) and it does support tool calls, which
     * is what makes it viable as the no-setup default for an agentic editor.
     */
    val pollinations = listOf(
        model("openai-fast", "GPT-OSS 20B (free, no key)", AiProvider.POLLINATIONS, 131_072)
    )

    /**
     * Verified keyless with native tool calls, 2026-10-17. OVH also serves
     * `gpt-oss-120b`, left out of the seed only because the id collides with
     * the Cerebras seed (model ids are the table's primary key); a refresh
     * still discovers it.
     */
    val ovhcloud = listOf(
        model("Qwen3.5-397B-A17B", "Qwen3.5 397B (free, no key)", AiProvider.OVHCLOUD, 262_144),
        model("Qwen3.8-27B", "Qwen3.8 27B (free, no key)", AiProvider.OVHCLOUD, 262_144),
        model("Meta-Llama-3_3-70B-Instruct", "Llama 3.3 70B (free, no key)", AiProvider.OVHCLOUD, 131_072)
    )

    /**
     * Kilo's free pool rotates; these were live and tool-capable without a key
     * on 2026-10-17. `kilo-auto/free` is the router and the most durable id.
     */
    val kilo = listOf(
        model("kilo-auto/free", "Kilo Auto (free, no key)", AiProvider.KILOCODE, 256_000),
        model("reflection/beam-free", "Reflection Beam (free, no key)", AiProvider.KILOCODE, 1_048_576),
        model("poolside/laguna-s-2.1:free", "Laguna S 2.1 (free, no key)", AiProvider.KILOCODE, 262_144),
        // OpenRouter's own free router, served through Kilo; tool calls verified.
        model("openrouter/free", "OpenRouter Free router (free, no key)", AiProvider.KILOCODE, 200_000)
    )

    /**
     * Community providers. Ids are namespaced ("k2:...") because model
     * ids are the table's primary key and these upstream ids also exist on
     * the OpenAI-style gateways; the client strips the prefix. All verified
     * live on 2026-10-17 with a real completion.
     */
    val k2Horizon = listOf(
        model("k2:IFM/K2-Horizon-375B-A23B", "K2 Horizon 375B (community)", AiProvider.K2_HORIZON, 524_288, tools = false, thinking = true)
    )

    /** Guest models on chat.qwen.ai, listed by its /api/models without an account (2026-10-18). */
    val qwenWeb = listOf(
        model("qwen:qwen3.8-max", "Qwen3.8 Max (community)", AiProvider.QWEN_WEB, 983_616, tools = false, thinking = true),
        model("qwen:qwen3.7-plus", "Qwen3.7 Plus (community)", AiProvider.QWEN_WEB, 262_144, tools = false, thinking = true),
        model("qwen:qwen3.8-omni-flash", "Qwen3.8 Omni Flash (community)", AiProvider.QWEN_WEB, 262_144, tools = false, thinking = true)
    )

    /**
     * ptero.pro guest models. Only these two are served as themselves: the
     * site's other ids (Mercury, GPT-OSS, Nemotron, ...) were all answered by
     * qwen3.7-flash on 2026-10-18 ("model_used"), so listing them would be a lie.
     */
    val ptero = listOf(
        model("ptero:deepseek-v4.1-flash:free", "DeepSeek V4.1 Flash (Ptero)", AiProvider.PTERO, 131_072, tools = false),
        model("ptero:qwen3.7-flash:free", "Qwen 3.7 Flash (Ptero)", AiProvider.PTERO, 131_072, tools = false)
    )

    /**
     * LLM7's anonymous tier ("usage_based_only": false), each verified with a
     * native tool call on 2026-10-18. One request at a time per client.
     */
    val llm7 = listOf(
        model("codestral-latest", "Codestral (LLM7, no key)", AiProvider.LLM7, 32_000),
        model("gpt-oss:20b", "GPT-OSS 20B (LLM7, no key)", AiProvider.LLM7, 131_072),
        model("default", "LLM7 Auto (no key)", AiProvider.LLM7, 131_072)
    )

    /**
     * Inception's guest chat serves one model, Mercury 3 (a diffusion LLM,
     * 1M context). Its reasoning effort (Instant, Low, Medium, High) is the
     * model's reasoning setting, not a separate model ([ReasoningControls]).
     * The recipe is NEB's (2026-10-19). The site sits behind Vercel's browser
     * check, which a datacenter IP never passes; phones pass it in the in-app
     * browser.
     */
    val mercury = listOf(
        model("mercury:mercury-3", "Mercury 3 (community)", AiProvider.MERCURY, 1_000_000, tools = false, thinking = true)
    )

    /**
     * getunikey.ai's anonymous trial. Each id was checked on 2026-10-22 for
     * which model actually answers (the stream's `model` field); ids answered
     * by a different model (Kimi K3, GLM 5.2) are left out, and "gpt-5.5" is
     * named for what serves it. Text only: tools and images are refused.
     */
    val unikey = listOf(
        model("unikey:claude-opus-4-8", "Claude Opus 4.8 (UniKey)", AiProvider.UNIKEY, 128_000, tools = false),
        model("unikey:gpt-5.5", "GPT-5.6 Terra (UniKey)", AiProvider.UNIKEY, 128_000, tools = false),
        model("unikey:minimax/minimax-m3", "MiniMax M3 (UniKey)", AiProvider.UNIKEY, 128_000, tools = false),
        model("unikey:deepseek/deepseek-v4-pro", "DeepSeek V4 Pro (UniKey)", AiProvider.UNIKEY, 128_000, tools = false),
        model("unikey:deepseek/deepseek-v4-flash", "DeepSeek V4 Flash (UniKey)", AiProvider.UNIKEY, 128_000, tools = false, thinking = true),
        model("unikey:x-ai/grok-4.3", "Grok 4.3 (UniKey)", AiProvider.UNIKEY, 128_000, tools = false, thinking = true),
        model("unikey:moonshotai/kimi-k2.7-code", "Kimi K2.7 Code (UniKey)", AiProvider.UNIKEY, 128_000, tools = false, thinking = true),
        model("unikey:claude-opus-4-7", "Claude Opus 4.7 (UniKey)", AiProvider.UNIKEY, 128_000, tools = false)
    )

    /**
     * Inception's official API (key). Windows from its public `/v1/models`
     * (2026-10-22); Mercury 3 takes a reasoning effort.
     */
    val inception = listOf(
        model("mercury-3", "Mercury 3", AiProvider.INCEPTION, 1_000_000, thinking = true),
        model("mercury-2.5", "Mercury 2.5", AiProvider.INCEPTION, 260_000),
        model("mercury-2", "Mercury 2", AiProvider.INCEPTION, 128_000)
    )

    val nvidia = listOf(
        model("nvidia/nemotron-3-super-120b-a12b", "Nemotron 3 Super 120B", AiProvider.NVIDIA, 262_144),
        model("nvidia/nemotron-3.5-lightning-30b-a3b", "Nemotron 3.5 Lightning 30B", AiProvider.NVIDIA, 262_144),
        model("openai/gpt-oss-120b", "GPT-OSS 120B", AiProvider.NVIDIA, 131_072),
        model("deepseek-ai/deepseek-v4.1-flash", "DeepSeek V4.1 Flash", AiProvider.NVIDIA, 1_048_576)
    )

    val agnes = listOf(
        model("agnes-3.0-flash", "Agnes 3.0 Flash", AiProvider.AGNES, 524_288),
        model("agnes-2.5-flash", "Agnes 2.5 Flash", AiProvider.AGNES, 524_288),
        model("agnes-2.0-flash", "Agnes 2.0 Flash", AiProvider.AGNES, 131_072)
    )

    val groq = listOf(
        model("moonshotai/kimi-k2-instruct", "Kimi K2 Instruct", AiProvider.GROQ, 131_072),
        model("openai/gpt-oss-120b", "GPT-OSS 120B", AiProvider.GROQ, 131_072),
        model("qwen/qwen3.8-27b", "Qwen3.8 27B", AiProvider.GROQ, 131_072)
    )

    /** Cerebras deprecated qwen-3-coder-480b; its /models lists gpt-oss-120b at 131,072 (65,536 on the free tier). */
    val cerebras = listOf(
        model("gpt-oss-120b", "GPT-OSS 120B", AiProvider.CEREBRAS, 131_072)
    )

    val mistral = listOf(
        model("devstral-medium-latest", "Devstral Medium", AiProvider.MISTRAL, 131_072),
        model("mistral-large-latest", "Mistral Large", AiProvider.MISTRAL, 262_144)
    )

    val openCodeZen = listOf(
        model("claude-sonnet-5-5", "Claude Sonnet 5.5", AiProvider.OPENCODE_ZEN, 1_000_000),
        model("gpt-5.4", "GPT-5.4", AiProvider.OPENCODE_ZEN, 1_050_000),
        model("qwen3.8-max", "Qwen3.8 Max", AiProvider.OPENCODE_ZEN, 983_616)
    )

    /**
     * Providers whose catalogue is fetched and which have no useful seed. An
     * empty list here is correct, not a gap: OpenRouter fronts hundreds of models that change weekly, and Gemini's ids move fast
     * enough that shipping them guarantees stale entries.
     */
    private val fetchedOnly = emptyList<AiModel>()

    private val byProvider: Map<AiProvider, List<AiModel>> = mapOf(
        AiProvider.POLLINATIONS to pollinations,
        AiProvider.OVHCLOUD to ovhcloud,
        AiProvider.GEMINI to fetchedOnly,
        AiProvider.NVIDIA to nvidia,
        AiProvider.OPENROUTER to fetchedOnly,
        AiProvider.OPENCODE_ZEN to openCodeZen,
        AiProvider.AGNES to agnes,
        AiProvider.KILOCODE to kilo,
        AiProvider.GROQ to groq,
        AiProvider.CEREBRAS to cerebras,
        AiProvider.MISTRAL to mistral,
        AiProvider.K2_HORIZON to k2Horizon,
        AiProvider.QWEN_WEB to qwenWeb,
        AiProvider.PTERO to ptero,
        AiProvider.LLM7 to llm7,
        AiProvider.MERCURY to mercury,
        AiProvider.UNIKEY to unikey,
        AiProvider.INCEPTION to inception,
        AiProvider.CUSTOM to fetchedOnly
    )

    operator fun get(provider: AiProvider): List<AiModel> = byProvider[provider].orEmpty()

    val all: List<AiModel> = byProvider.values.flatten()

    /**
     * The model a fresh install starts on: Mercury 3, at High effort. It is
     * newer than K2 Horizon and beats it on both speed (over 1,000 tokens/s)
     * and quality. It is a community endpoint behind a browser check, so
     * [KeylessFallback] moves a blocked request to the next free model, and
     * [AiModels.getRecommendedModels] keeps K2, UniKey and Kilo one tap away.
     */
    val default: AiModel = mercury.first()
}

/**
 * Lookup helpers over the seed catalogue.
 */
object AiModels {

    fun getModelsForProvider(provider: AiProvider): List<AiModel> = SeedModels[provider] + KeylessSeeds[provider]

    /**
     * Seed default for a provider, falling back to the keyless default so a
     * caller always gets something sendable.
     */
    fun getDefaultModel(provider: AiProvider): AiModel =
        SeedModels[provider].firstOrNull() ?: SeedModels.default

    fun getAllModels(): List<AiModel> = SeedModels.all + KeylessSeeds.all

    fun findModelById(modelId: String): AiModel? =
        SeedModels.all.find { it.id == modelId } ?: KeylessSeeds.all.find { it.id == modelId }

    /** Models worth suggesting for code work, best-first. */
    fun getRecommendedModels(): List<AiModel> = listOf(
        SeedModels.default,
        SeedModels.unikey.first(),
        SeedModels.k2Horizon.first(),
        SeedModels.kilo.first(),
        SeedModels.ovhcloud.first(),
        SeedModels.pollinations.first(),
        SeedModels.cerebras.first(),
        SeedModels.groq.first(),
        SeedModels.nvidia.first()
    )
}
