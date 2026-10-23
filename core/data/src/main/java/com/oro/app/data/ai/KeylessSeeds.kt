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
 *
 * Only routes that sustain an agent run are kept (~20-60 model calls in 5-15
 * minutes with 10-60k-token prompts): LLM7's anonymous budget (about 100k
 * tokens a day), VLM Run's 30 requests an hour and OpenBMB's MiniCPM demo
 * (four requests at a time across all visitors) were removed on 2026-10-23.
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
     * that stream cleanly, finish long answers and follow the text tool
     * protocol are seeded: Gemma 4 26B passed every live device scenario
     * (docs/research/device-live-eval.md). GLM-4.7 Flash was dropped
     * (2026-10-23): its text tool calls are unreliable, so it could not stand
     * in for anything in an agent run. gpt-oss-120b stops after a few hundred
     * characters there, Llama 4 Scout and Qwen3 repeat every chunk, and
     * GLM-5.3 Flash answered "rate limited" on every try.
     */
    val cloudflare = listOf(
        // Follows Oro's text tool protocol exactly (verified live).
        model("cfai:@cf/google/gemma-4-26b-a4b-it", "Gemma 4 26B (Cloudflare, no key)", AiProvider.CLOUDFLARE_PLAYGROUND, 256_000, thinking = true, tools = false)
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
        AiProvider.HY3 to hy3,
        AiProvider.CLOUDFLARE_PLAYGROUND to cloudflare,
        AiProvider.LAGUNA to laguna
    )

    operator fun get(provider: AiProvider): List<AiModel> = byProvider[provider].orEmpty()

    val all: List<AiModel> = byProvider.values.flatten()
}
