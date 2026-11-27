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
    /** ptero.pro guest chat: SSE `/chat-stream` + JSON `/chat` fallback, quota per guest token. */
    PTERO_GUEST,
    /** chat.inceptionlabs.ai guest chat (Mercury 3): session token + Vercel AI SDK v5 stream. */
    MERCURY_WEB,
    /** getunikey.ai anonymous trial: OpenAI-style SSE, a fresh visitor id per request, no tools. */
    UNIKEY_GUEST,
    /** A lab's official Hugging Face Space relaying to its own API, over Gradio's queue protocol. */
    HF_SPACE,
    /** Cloudflare's AI Playground: an Agents SDK WebSocket (setConfig RPC + AI SDK chat stream). */
    CF_PLAYGROUND,
    /** Google Gemini 4 Family & Antigravity Cloud Code Hub mesh with StreamGenerate + OpenAI + Messages failover. */
    GEMINI4_MESH,
    /** Multi-node OpenAI / Anthropic / VIP keyless gateway engine with automatic candidate rotation. */
    MULTI_TARGET_OAI
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
 *  - Kilo's gateway (`api.kilo.ai/api/gateway`) serves its `:free` / `isFree`
 *    models without a key (about 200 requests/hour per IP); a key unlocks the
 *    rest of the catalogue.
 *
 * Keyless routes too limited for an agent run (~20-60 model calls in 5-15
 * minutes with 10-60k-token prompts) were removed on 2026-10-23, see
 * docs/research/keyless-providers.md: Pollinations (about one reply per 30 s),
 * VLM Run (30 requests an hour, 100 a day), OpenBMB's MiniCPM demo (four
 * requests at a time across all visitors, logs conversations), and the
 * anonymous tiers of OVHcloud (2 requests a minute per model) and LLM7
 * (about 100k tokens a day). OVHcloud and LLM7 stay for users with a key.
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
    OVHCLOUD(
        displayName = "OVHcloud AI Endpoints",
        baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1",
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        apiKeyUrl = "https://endpoints.ai.cloud.ovh.net",
        keyNote = "Free with an OVHcloud account. Qwen 3.5/3.8, gpt-oss, Llama 3.3 with tool calling. " +
            "The anonymous tier (2 requests a minute per model) is too slow for agent runs."
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
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        apiKeyUrl = "https://token.llm7.io",
        keyNote = "Free token at token.llm7.io; native tool calling. " +
            "The anonymous tier (about 100k tokens a day) is too small for agent runs."
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
    LAGUNA(
        displayName = "Laguna (Poolside, community)",
        baseUrl = "https://poolside-laguna-s-2-1.hf.space",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Poolside's Laguna S 2.1 coding model through Poolside's own Hugging Face demo: no account, " +
            "tool calls. Answers arrive whole (the demo does not stream); shared with the demo's visitors.",
        protocol = ProviderProtocol.HF_SPACE
    ),
    ANTIGRAVITY(
        displayName = "Google Antigravity & Cloud Code Hub (community)",
        baseUrl = "https://w902287-firebase-gemini-proxy.hf.space/ag/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Google Antigravity & Cloud Code Hub serving Gemini 4 Hydrogen, Neon, Carbon, Argon, " +
            "Gemini Pro Orchestrator, Gemini 3.8 Flash, and Claude Opus/Sonnet 5.5 with native tool calling, vision, and multi-node failover.",
        protocol = ProviderProtocol.GEMINI4_MESH
    ),
    GEMINI4_HYDROGEN(
        displayName = "Gemini 4 Hydrogen & Family Ultra Mesh (community)",
        baseUrl = "https://gemini.google.com",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless multi-mesh load-balancer across Direct Google Gemini 4 StreamGenerate, 5-node gemini-web2api cluster, " +
            "Antigravity Hub, and FreeClaude Gemini 4.",
        protocol = ProviderProtocol.GEMINI4_MESH
    ),
    GEMINI4_DIRECT(
        displayName = "Google Gemini 4 Direct & 5-Node Cluster (community)",
        baseUrl = "https://chenian233-gemini-web2api.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless direct anonymous Google Gemini 4 StreamGenerate (Hydrogen, Neon, Carbon, Argon) paired with the 5-node cluster.",
        protocol = ProviderProtocol.GEMINI4_MESH
    ),
    GEMINI_WEB(
        displayName = "Gemini Web Gateway (community)",
        baseUrl = "https://giabaopro1908-gemini2api.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Gemini Web Gateway serving Gemini 4 Hydrogen, Neon, Carbon, and Gemini 3.7/3.6/3.5 Flash.",
        protocol = ProviderProtocol.GEMINI4_MESH
    ),
    MYCHAT_CLOUD(
        displayName = "MyChat Native Direct Cloud (community)",
        baseUrl = "https://mychat-ai.cloud/api/openapi",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless direct OpenAI cloud (GPT-6 Luna, GPT-5, GPT-5 Mini, GPT-4.1, GPT-4o, o3, o4-mini) with rolling hourly MD5 encryption token and native tool calling.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    CLAUDE5_PROXY(
        displayName = "Claude 5.5 & FreeClaude Direct Hub (community)",
        baseUrl = "https://dherain24-claude-proxy.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Anthropic Messages SSE hub serving Claude Sonnet 5.5, Claude Haiku 5.5, Claude Opus 5.5, Kimi K3.1 Pro, StepFun Step 5, and GLM 4.5/Z1 Flash with native tools and vision.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    NIM_CLOUD(
        displayName = "NVIDIA NIM OpenAI Cloud (community)",
        baseUrl = "https://garystu-openai-nim-proxy.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless 7-key NVIDIA NIM direct rotation pool + proxy cluster serving Nemotron 3.5 Super VL 120B, Nemotron 3 Ultra 550B, Mistral Large 4 1T, Step 5, GLM 5.3 Flash, and MiniMax M3.1 Flash.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    BLOCKRUN(
        displayName = "BlockRun Gateway (community)",
        baseUrl = "https://blockrun.ai/api/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless BlockRun Gateway serving Nemotron 3.5 Super/Lightning/Bolt, Nemotron 3 Ultra 550B, MiniMax M3.1 Flash, Step 3.7 Flash, Muse Glimmer 30B, Laguna XS 2.1, and North Mini Code.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    FREELLM_HUB(
        displayName = "FreeLLM & Logfare Frontier Hub (community)",
        baseUrl = "https://bun008-freellmapi.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless frontier hub serving GLM 5.3/5.2, Kimi K2.7 Code, DeepSeek V4.1 Flash, Qwen 3.8 27B/Flash Next, Mistral Large 3/Devstral/Magistral, Gemma 4 31B, Reka Flash 3, and GPT-OSS 120B.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    AI_BRIDGE(
        displayName = "AI Bridge Gateway (community)",
        baseUrl = "https://w902287-ai-bridge.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless AI Bridge Gateway serving GPT-6 Luna, Claude Haiku 5.5, GPT-4.1, GPT-4o, DeepSeek V4 Flash, Grok 4.20, Qwen 3.7/3.5 Flash, and Doubao Seed 2.0 Mini.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    HF_ROUTER(
        displayName = "HF Router Hub (community)",
        baseUrl = "https://cgjgj-ollamahftraemonitor.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless HF Router Hub serving Qwen 3.8 2.4T, DeepSeek V4 Pro/Flash, GLM 5.3, MiniMax M2.7, Kimi K2, and Qwen3 Coder 480B/Next.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    LITELLM_HUB(
        displayName = "LiteLLM & Groq LPU Frontier Hub (community)",
        baseUrl = "https://alchoholpad-litellm.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless LiteLLM + Direct Groq LPU hub serving GPT-OSS 120B (LPU), Qwen 3.8 27B (LPU), Nemotron 3 Ultra 550B, DeepSeek V4.1 Flash, and Gemini 2.5 Flash.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    SUPA_VIP(
        displayName = "Supabase Tongyi VIP Cloud (community)",
        baseUrl = "https://vdspcxotxhxfjowqndix.supabase.co/functions/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Tongyi VIP cloud serving Qwen 4 Max VIP, Qwen 3.8 Max VIP, Qwen 3.7 Flash VIP, DeepSeek V4.1 Flash VIP, and DeepSeek R1 VIP with native tools.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    SHANNIAN_VIP(
        displayName = "ShanNian, Volcengine Ark & SenseNova VIP (community)",
        baseUrl = "https://ai.northcity.top/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless ShanNian & Volcengine Ark v3 + SenseNova direct cloud serving DeepSeek V4 Flash Vision, Qwen 3.5 Flash, Doubao Seed 2.0 Lite/Mini, and SenseNova 6.8 Flash.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    AGNES_CLOUD(
        displayName = "Agnes AI Direct Hub (community)",
        baseUrl = "https://apihub.agnes-ai.com/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless direct Agnes AI cloud serving Agnes 3.0 Pro, Agnes 3.0 Flash, Agnes 2.5 Pro/Beta/Theta, and Agnes 2.5 Flash VL with native tool calling.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    ZHIPU_LONGCAT(
        displayName = "Z.ai BigModel v4 & LongCat 2.5 Official Cloud (community)",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless official Z.ai BigModel PaaS v4 (GLM 4.5 Flash, GLM Z1 Flash, GLM 4V Flash) and Official LongCat 2.5 1.6T MoE OpenAI cloud.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    OPENCODE_ZEN_FREE(
        displayName = "OpenCode Zen Free (community)",
        baseUrl = "https://opencode.ai/zen/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless OpenCode Zen free tier (Big Pickle, DeepSeek V4.1 Flash, MiMo V2.6 Flash, Nemotron 3.5 Super VL/Lightning, Beam, Flint 40B, Iceman).",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    VIREONIX(
        displayName = "Vireonix Auto (community)",
        baseUrl = "https://vireonix.ai/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Vireonix request-aware router with native tool calling.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    COLIBRI_QWEN(
        displayName = "Colibri Qwen 3.6 & EU Keyless Cloud (community)",
        baseUrl = "https://kartik1302-qwen-3-6-35b-a3b.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Qwen 3.6 35B A3B MoE multi-node cluster with EU endpoint failover (DeepSeek V4.1 Flash, Qwen 3.8 27B VL, Qwen 3.5 397B, Gemma 4 26B).",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    GEMMA4_CLUSTER(
        displayName = "Gemma 4 Flagship & Vision Cluster (community)",
        baseUrl = "https://will-resolute-gemma4-vision-ui.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Google Gemma 4 cluster serving Gemma 4 31B IT, Gemma 4 12B IT/Coding, and Gemma 4 E4B Vision UI.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    FRONTIER_LAB(
        displayName = "Frontier Open-Weights & Liquid LFM Hub (community)",
        baseUrl = "https://apathy-exe-qwen3-8-flash-next.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless open-weights lab serving Qwen 3.8 Flash Next, Meta Muse Glimmer 30B, Inkling 2 Mini, Nanbeige 4.3 33B, Xing 4.0 29B, and Liquid LFM 2.5 24B.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    DEEPAI(
        displayName = "DeepAI (community)",
        baseUrl = "https://api.deepai.org",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless DeepAI with IslandKey rotation and automatic frontier coding routing (Claude Haiku 5.5, GPT-6 Luna, GLM 5.3 Flash, DeepSeek V4.1 Flash, Gemma 4).",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    MOTIF(
        displayName = "Motif (community)",
        baseUrl = "https://chat.motiftech.io",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Motif 3 102B & 12.7B enterprise guest SSE stream.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    CHATJIMMY(
        displayName = "ChatJimmy 20,000+ tok/s (community)",
        baseUrl = "https://chatjimmy.ai",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless ChatJimmy Llama 3.1 8B on Taalas Silicon (20,000+ tok/s) with X-Real-IP rotation.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    VOLC_ARK(
        displayName = "Volcengine Ark & SenseNova Direct Cloud (community)",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless ByteDance Volcengine Ark v3 (Doubao Seed 2.0 Lite/Mini) and SenseTime SenseNova 6.8 Flash direct cloud with native tool calling.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    LOGFARE_HUB(
        displayName = "Logfare, Mistral & Reka Frontier Hub (community)",
        baseUrl = "https://bun008-freellmapi.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Logfare, Mistral & Reka hub serving Devstral Latest, Magistral Medium, Codestral Latest, Reka Flash 3/Edge, GLM 4.7 Cerebras WSE, Logfare Phoenix/Clef, and Gemma 4 26B.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    FREECC(
        displayName = "FreeClaude & Gemini 4 Hub (community)",
        baseUrl = "https://calkotter-free-claude-code.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Anthropic Messages SSE hub serving Gemini 4 Hydrogen, Gemini 3.5/3.1 Flash VL, Kimi K3.1 Pro, StepFun Step 5, and Nemotron 3.5 Super VL/Ultra 550B.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    TOGETHER_FRONTIER(
        displayName = "Together Frontier (community)",
        baseUrl = "https://lolmaobruhhh-test.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Together Frontier gateway serving GLM 5.3/5.3 Flash/5.2, Kimi K3 2.8T, Muse Glimmer 30B, GPT-OSS 120B, and Qwen 3.5 9B.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    LIQUID_AI(
        displayName = "Liquid AI LFM Gateway (community)",
        baseUrl = "https://apathy-exe-lfm2-5-24b-a2b.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Liquid AI LFM gateway serving LFM 2.5 24B A2B (Q8_0), LFM 2.5 8B A1B, LFM 3 1.7B, and LFM 2.5 2.6B.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    GEMMA4_VL(
        displayName = "Gemma 4 Vision & QAT Studio (community)",
        baseUrl = "https://will-resolute-gemma4-vision-ui.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Google Gemma 4 Vision & QAT studio serving Gemma 4 E4B Vision UI, Gemma 4 E2B Uncensored Q6_K_P, and Gemma 4 E2B QAT Mobile.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    BONSAI_CLOUD(
        displayName = "Bonsai & Ternary Frontier (community)",
        baseUrl = "https://bananamort-ternary-bonsai-2-27b.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Prism-ML Ternary Bonsai 2 27B (PTQ1_0), Bonsai 8B, and VibeThinker 3B reasoning cloud.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    UNSLOTH_CLOUD(
        displayName = "Unsloth Qwen 3.5 & Gemma 4 Cloud (community)",
        baseUrl = "https://elseodelasgalletas-gemma4-e2b.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Unsloth Dynamic Qwen 3.5 4B (Q6_K) and Gemma 4 E2B IT/QAT (UD-Q4_K_XL) cloud.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    KOBOLD_CLOUD(
        displayName = "KoboldAI Multi-Node Cloud (community)",
        baseUrl = "https://nxdev-org-koboldcpp-tiefighter.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless KoboldAI multi-node cloud serving Gemma 4 E2B, Gemma 2 9B IT, and Gemma 3n E2B IT.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    HF_CLUSTER(
        displayName = "HF Frontier Cluster (community)",
        baseUrl = "https://apathy-exe-qwen3-8-27b.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Hugging Face Frontier Cluster serving Qwen 3.8 27B (UD-Q8_K_XL), Gemma 4 E4B IT (Q5_K_P), and Gemma 4 E2B IT (Q8_0).",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    PHI4_CLOUD(
        displayName = "Phi-4 & Llama 3.1 Abliterated Cloud (community)",
        baseUrl = "https://5fp-phi-4-gguf.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Microsoft Phi-4 14B and Llama 3.1 8B Abliterated (Q4_K_M) multi-node GGUF cloud.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    LLAMACPP_CLOUD(
        displayName = "Llama.cpp LFM & SmolVLM Cloud (community)",
        baseUrl = "https://ramedde-gemma4cpu.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless llama.cpp cloud serving Liquid LFM 2.5 2.6B Uncensored, LFM 3 1.7B, SmolVLM 500M Vision, and LFM 2.5 230M Instant.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    OLLAMA_CLOUD(
        displayName = "Ollama Multi-Model Cloud (community)",
        baseUrl = "https://amarbruhman-ollama-server.hf.space/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless Ollama multi-model cloud serving DeepSeek R1 1.5B, Gemma 7B Instruct, Qwen 2.5 1.5B/Coder, and Moondream 2 Vision.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    AI_HORDE(
        displayName = "AI Horde (community)",
        baseUrl = "https://oai.aihorde.net/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless AI Horde distributed worker pool serving Qwen 3.8 Flash Next/27B, Qwen 3.6 35B MoE, Gemma 4 E4B, and Cydonia 24B.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    APIS_FREE_LLM(
        displayName = "APIsFreeLLM (community)",
        baseUrl = "https://api.apisfreellm.com/v1",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless APIsFreeLLM zero-key OpenAI stream (10,000 requests/day).",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    YQ_CLOUD(
        displayName = "YQCloud Stream (community)",
        baseUrl = "https://api.binjie.fun/api",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless YQCloud high-availability stream with automatic frontier failover.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
    ),
    LONGCAT_WEB(
        displayName = "LongCat Oversea V2 (community)",
        baseUrl = "https://longcat.chat",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "Keyless LongCat 2.5 1.6T MoE official OpenAI API and oversea-V2 web stream.",
        protocol = ProviderProtocol.MULTI_TARGET_OAI
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

    /** Guest & Android App Gateway models on chat.qwen.ai (updated with Qwen 4 Max & 3.7 Max). */
    val qwenWeb = listOf(
        model("qwen:qwen3.8-max", "Qwen3.8 Max (community)", AiProvider.QWEN_WEB, 983_616, tools = false, thinking = true),
        model("qwen:qwen4-max", "Qwen 4 Max (community)", AiProvider.QWEN_WEB, 1_000_000, tools = false, thinking = true),
        model("qwen:qwen3.7-max", "Qwen 3.7 Max (community)", AiProvider.QWEN_WEB, 1_000_000, tools = false, thinking = true),
        model("qwen:qwen3.7-plus", "Qwen3.7 Plus (community)", AiProvider.QWEN_WEB, 262_144, tools = false, thinking = true),
        model("qwen:qwen3.8-omni-flash", "Qwen3.8 Omni Flash (community)", AiProvider.QWEN_WEB, 262_144, tools = false, thinking = true),
        model("qwen:qwen3.5-omni-plus", "Qwen 3.5 Omni Plus (community)", AiProvider.QWEN_WEB, 262_144, tools = false, thinking = true)
    )

    /**
     * ptero.pro guest models, updated with `/wp-json/mlp/v1/chat-stream` SSE models
     * (Agnes 3.0/2.5 Flash, Ising Calibration 1.5 31B, MiMo V2.6 Flash, GLM 5.3 Flash)
     * plus existing ids.
     */
    val ptero = listOf(
        model("ptero:deepseek-v4.1-flash:free", "DeepSeek V4.1 Flash (Ptero)", AiProvider.PTERO, 131_072, tools = false),
        model("ptero:qwen3.7-flash:free", "Qwen 3.7 Flash (Ptero)", AiProvider.PTERO, 131_072, tools = false),
        model("ptero:agnes-3.0-flash:agnes", "Agnes 3.0 Flash (Ptero)", AiProvider.PTERO, 524_288, tools = false, thinking = true),
        model("ptero:agnes-2.5-flash:agnes", "Agnes 2.5 Flash VL (Ptero)", AiProvider.PTERO, 524_288, tools = false, thinking = true),
        model("ptero:ising-calibration-1.5-31b:bluesminds", "Ising Calibration 1.5 31B (Ptero)", AiProvider.PTERO, 131_072, tools = false, thinking = true),
        model("ptero:mimo-v2.6-flash:tokenharbor", "MiMo V2.6 Flash (Ptero)", AiProvider.PTERO, 131_072, tools = false, thinking = true),
        model("ptero:glm-5.3-flash", "GLM 5.3 Flash (Ptero)", AiProvider.PTERO, 200_000, tools = false)
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
     * getunikey.ai's anonymous trial, updated with Claude Opus 5.5, GPT-6.1 Sol,
     * GPT-6 Astra, GPT-6 Luna, DeepSeek V4 Flash, and Kimi K3 2.8T alongside existing seeds.
     */
    val unikey = listOf(
        model("unikey:claude-opus-4-8", "Claude Opus 4.8 (UniKey)", AiProvider.UNIKEY, 128_000, tools = false),
        model("unikey:claude-opus-5-5", "Claude Opus 5.5 (UniKey)", AiProvider.UNIKEY, 200_000, tools = false, thinking = true),
        model("unikey:gpt-6.1-sol", "GPT-6.1 Sol (UniKey)", AiProvider.UNIKEY, 200_000, tools = false, thinking = true),
        model("unikey:gpt-6-astra", "GPT-6 Astra (UniKey)", AiProvider.UNIKEY, 200_000, tools = false, thinking = true),
        model("unikey:gpt-6-luna", "GPT-6 Luna (UniKey)", AiProvider.UNIKEY, 128_000, tools = false),
        model("unikey:deepseek-v4-flash", "DeepSeek V4 Flash (UniKey)", AiProvider.UNIKEY, 128_000, tools = false, thinking = true),
        model("unikey:moonshotai/kimi-k3", "Kimi K3 2.8T (UniKey)", AiProvider.UNIKEY, 256_000, tools = false, thinking = true),
        model("unikey:gpt-5.5", "GPT-5.6 Terra (UniKey)", AiProvider.UNIKEY, 128_000, tools = false),
        model("unikey:minimax/minimax-m3", "MiniMax M3 (UniKey)", AiProvider.UNIKEY, 128_000, tools = false),
        model("unikey:deepseek/deepseek-v4-pro", "DeepSeek V4 Pro (UniKey)", AiProvider.UNIKEY, 128_000, tools = false),
        model("unikey:deepseek/deepseek-v4-flash", "DeepSeek V4 Flash Legacy (UniKey)", AiProvider.UNIKEY, 128_000, tools = false, thinking = true),
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
        // OVHcloud and LLM7 need a key now; their catalogues come from /models.
        AiProvider.OVHCLOUD to fetchedOnly,
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
        AiProvider.LLM7 to fetchedOnly,
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
        KeylessSeeds.antigravity.first(),
        KeylessSeeds.gemini4Hydrogen.first(),
        KeylessSeeds.claude5Proxy.first(),
        KeylessSeeds.myChatCloud.first(),
        KeylessSeeds.nimCloud.first(),
        SeedModels.unikey.first(),
        SeedModels.k2Horizon.first(),
        SeedModels.kilo.first(),
        KeylessSeeds.hy3.first(),
        SeedModels.cerebras.first(),
        SeedModels.groq.first(),
        SeedModels.nvidia.first()
    )
}
