package com.oro.app.data.ai

/**
 * Keyless models found in the 2026-10-22 survey (docs/research/keyless-providers.md),
 * kept apart from [SeedModels] so that list's owners and this one can change
 * independently. [AiModels] reads both.
 *
 * Every entry was checked live on 2026-10-22 from two networks (a datacenter
 * and molab) with a streamed native tool call (`write_file` with the right
 * arguments) and a ~50k-token prompt. Windows are the ones the serving
 * gateway reports for these ids.
 */
object KeylessSeeds {

    private fun model(
        id: String,
        name: String,
        provider: AiProvider,
        context: Int,
        thinking: Boolean = false,
        tools: Boolean = true
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
     * Kilo's free pool, newer and stronger than the 2026-10-17 seeds: all
     * reasoning models with native tool calls, 1-3 s to the first token.
     * OpenRouter-style `reasoning: {enabled: false}` turns their thinking off;
     * `reasoning_effort` is accepted. Stealth ids (`stealth/...`) rotate too
     * fast to seed; a catalogue refresh still lists them.
     */
    val kilo = listOf(
        model("nvidia/nemotron-3-ultra-550b-a55b:free", "Nemotron 3 Ultra 550B (free, no key)", AiProvider.KILOCODE, 1_000_000, thinking = true),
        model("cohere/north-mini-code:free", "North Mini Code (free, no key)", AiProvider.KILOCODE, 256_000, thinking = true),
        model("dots-studio/dots-3-note-preview:free", "Dots3-Note Preview (free, no key)", AiProvider.KILOCODE, 512_000, thinking = true),
        // Takes images: VisionSupport matches its "-vl" id.
        model("nvidia/nemotron-3.5-super-vl:free", "Nemotron 3.5 Super VL (free, no key)", AiProvider.KILOCODE, 1_000_000, thinking = true),
        model("nvidia/nemotron-3.5-lightning:free", "Nemotron 3.5 Lightning (free, no key)", AiProvider.KILOCODE, 1_000_000, thinking = true),
        model("recursive/flint-v1.0:free", "Flint 1.0 (free, no key)", AiProvider.KILOCODE, 262_144)
    )

    /**
     * LLM7's anonymous tier (`usage_based_only: false`, tier "turbo").
     * Its anonymous token budget is small (about 100k tokens a day per IP),
     * so it sits low in [KeylessFallback]'s order.
     */
    val llm7 = listOf(
        model("minimax-m2.7", "MiniMax M2.7 (LLM7, no key)", AiProvider.LLM7, 204_800)
    )

    /**
     * VLM Run's gateway takes anonymous callers (no Authorization header at
     * all). `qwen3.8-27b` is the gateway's alias of `qwen/qwen3.8-27b`: the
     * alias is seeded because the canonical id is also a paid model in Kilo's
     * catalogue, and model ids are the table's primary key. 0.5-0.8 s to the
     * first token; 10/min, 30/hour, 100/day per IP.
     */
    val vlmRun = listOf(
        model("qwen3.8-27b", "Qwen3.8 27B (VLM Run, no key)", AiProvider.VLM_RUN, 168_000)
    )

    /**
     * New keyless providers from the 2026-10-23 survey (docs/research/keyless-providers.md),
     * each checked live with a streamed reply and a long prompt.
     *
     * Hy3: Tencent's own Hugging Face demo relays to Tencent's API -- 295B MoE,
     * 256K context, native tool calls (a round trip verified), think level.
     */
    val hy3 = listOf(
        model("hy3:hy3", "Hy3 (Tencent, no key)", AiProvider.HY3, 262_144, thinking = true)
    )

    /**
     * Cloudflare's AI Playground: Workers AI models over the Agents SDK
     * socket. Windows are the ones its own `getModels` reports. Only models
     * that stream cleanly and finish long answers are seeded (gpt-oss-120b
     * stops after a few hundred characters there; Llama 4 Scout and Qwen3
     * repeat every chunk). The text protocol carries tools.
     */
    val cloudflare = listOf(
        // Follows Oro's text tool protocol exactly (verified live).
        model("cfai:@cf/google/gemma-4-26b-a4b-it", "Gemma 4 26B (Cloudflare, no key)", AiProvider.CLOUDFLARE_PLAYGROUND, 256_000, thinking = true, tools = false),
        // Good for chat; its text tool calls are unreliable (unnamed parameters, escaped HTML), so
        // it is not a fallback stand-in.
        model("cfai:@cf/zai-org/glm-4.7-flash", "GLM-4.7 Flash (Cloudflare, no key)", AiProvider.CLOUDFLARE_PLAYGROUND, 131_072, thinking = true, tools = false)
        // GLM-5.3 Flash is listed too but answered "rate limited" (a cap shared by all the
        // playground's visitors) on every try over two hours, so it is not seeded.
    )

    /**
     * OpenBMB's own demo of MiniCPM-V 4.7 (35B-A3B MoE): native tool calls
     * (a call, its result and a follow-up turn verified live). The model
     * reads 262K tokens; the relay's limit is unpublished, so a safe window
     * is set (a ~32k-token prompt answered correctly).
     */
    val minicpm = listOf(
        model("minicpm:MiniCPM-V-4.7-35B-A3B", "MiniCPM-V 4.7 35B-A3B (OpenBMB, no key)", AiProvider.MINICPM, 131_072, thinking = true)
    )

    /**
     * Poolside's own demo of Laguna S 2.1 (OpenRouter's free route: 262K
     * context, 32K output). The demo forwards no tools; the adapter carries
     * them in GLM's template, which the model follows (see HfSpaces.Laguna).
     */
    val laguna = listOf(
        model("laguna:laguna-s-2.1", "Laguna S 2.1 (Poolside, no key)", AiProvider.LAGUNA, 262_144, thinking = true)
    )

    private val byProvider: Map<AiProvider, List<AiModel>> = mapOf(
        AiProvider.KILOCODE to kilo,
        AiProvider.LLM7 to llm7,
        AiProvider.VLM_RUN to vlmRun,
        AiProvider.HY3 to hy3,
        AiProvider.CLOUDFLARE_PLAYGROUND to cloudflare,
        AiProvider.MINICPM to minicpm,
        AiProvider.LAGUNA to laguna
    )

    operator fun get(provider: AiProvider): List<AiModel> = byProvider[provider].orEmpty()

    val all: List<AiModel> = byProvider.values.flatten()
}
