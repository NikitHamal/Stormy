package com.oro.app.data.ai

/**
 * Keyless models ported from ZeusDesigns (`src-tauri/src/providers.rs`) and the
 * 2026-10-22/23 survey, kept apart from [SeedModels] so that list's owners and
 * this one can change independently. [AiModels] reads both.
 *
 * Includes the complete Google Gemini 4 family (Hydrogen, Neon, Carbon, Argon,
 * Pro Orchestrator, 3.8 Flash, Worker AGY), Claude Opus/Sonnet/Haiku 5.5,
 * GPT-6 Luna/GPT-5/o3/o4-mini, NVIDIA Nemotron 3.5 Super VL/Ultra 550B,
 * Mistral Large 4 1T/Devstral/Magistral, Qwen 4 Max/3.8 2.4T/3.6 35B,
 * DeepSeek V4 Pro/V4.1 Flash, GLM 5.3/4.5, Kimi K3.1 Pro/K2.7 Code,
 * StepFun Step 5, LongCat 2.5, Agnes 3.0 Pro, SenseNova 6.8, Doubao Seed 2.0,
 * Gemma 4 31B/12B/E4B VL, and more.
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

    val kilo = listOf(
        model("nvidia/nemotron-3-ultra-550b-a55b:free", "Nemotron 3 Ultra 550B (free, no key)", AiProvider.KILOCODE, 1_000_000, thinking = true),
        model("cohere/north-mini-code:free", "North Mini Code (free, no key)", AiProvider.KILOCODE, 256_000, thinking = true),
        model("dots-studio/dots-3-note-preview:free", "Dots3-Note Preview (free, no key)", AiProvider.KILOCODE, 512_000, thinking = true),
        model("nvidia/nemotron-3.5-super-vl:free", "Nemotron 3.5 Super VL (free, no key)", AiProvider.KILOCODE, 1_000_000, thinking = true),
        model("nvidia/nemotron-3.5-lightning:free", "Nemotron 3.5 Lightning (free, no key)", AiProvider.KILOCODE, 1_000_000, thinking = true),
        model("recursive/flint-v1.0:free", "Flint 1.0 (free, no key)", AiProvider.KILOCODE, 262_144),
        model("nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", "Nemotron 3 Nano Omni VL (free, no key)", AiProvider.KILOCODE, 256_000, thinking = true),
        model("inclusionai/ling-3.2-flash:free", "Ling 3.2 Flash VL (free, no key)", AiProvider.KILOCODE, 1_000_000, thinking = true),
        model("baidu/ernie-5.5-flash-preview:free", "ERNIE 5.5 Flash (free, no key)", AiProvider.KILOCODE, 1_000_000, thinking = true),
        model("particle-ai/particle-1:free", "Particle-1 VL (free, no key)", AiProvider.KILOCODE, 1_000_000, thinking = true),
        model("liquid/lfm-3-1.7b:free", "Liquid LFM 3 1.7B (free, no key)", AiProvider.KILOCODE, 128_000),
        model("stealth/glyph-cluster", "Stealth Glyph Cluster (free, no key)", AiProvider.KILOCODE, 256_000, thinking = true)
    )

    val hy3 = listOf(
        model("hy3:hy3", "Hy3 (Tencent, no key)", AiProvider.HY3, 262_144, thinking = true)
    )

    val cloudflare = listOf(
        model("cfai:@cf/google/gemma-4-26b-a4b-it", "Gemma 4 26B (Cloudflare, no key)", AiProvider.CLOUDFLARE_PLAYGROUND, 256_000, thinking = true, tools = false)
    )

    val laguna = listOf(
        model("laguna:laguna-s-2.1", "Laguna S 2.1 (Poolside, no key)", AiProvider.LAGUNA, 262_144, thinking = true)
    )

    /** Google Antigravity & Cloud Code Hub: full Gemini 4 family + Claude 5.5 with native tools & vision. */
    val antigravity = listOf(
        model("ag:gemini-4-hydrogen-xhigh", "Gemini 4 Hydrogen XHigh", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-hydrogen-high", "Gemini 4 Hydrogen High", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-hydrogen-medium", "Gemini 4 Hydrogen Medium", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-hydrogen-low", "Gemini 4 Hydrogen Low", AiProvider.ANTIGRAVITY, 1_048_576),
        model("ag:gemini-4-hydrogen-sonar", "Gemini 4 Hydrogen Sonar", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-hydrogen-tiered-xhigh", "Gemini 4 Hydrogen Tiered XHigh", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-hydrogen-tiered-high", "Gemini 4 Hydrogen Tiered High", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-neon-xhigh", "Gemini 4 Neon XHigh", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-neon-high", "Gemini 4 Neon High", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-neon-medium", "Gemini 4 Neon Medium", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-neon-low", "Gemini 4 Neon Low", AiProvider.ANTIGRAVITY, 1_048_576),
        model("ag:gemini-4-carbon-high", "Gemini 4 Carbon High", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-carbon-medium", "Gemini 4 Carbon Medium", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-carbon-low", "Gemini 4 Carbon Low", AiProvider.ANTIGRAVITY, 1_048_576),
        model("ag:gemini-4-argon-high", "Gemini 4 Argon High", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-argon-medium", "Gemini 4 Argon Medium", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-4-argon-low", "Gemini 4 Argon Low", AiProvider.ANTIGRAVITY, 1_048_576),
        model("ag:gemini-pro-orchestrator", "Gemini 4 Pro Orchestrator", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-pro-orchestrator-argon", "Gemini 4 Pro Orchestrator Argon", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-pro-orchestrator-neon", "Gemini 4 Pro Orchestrator Neon", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-3.8-flash-high", "Gemini 3.8 Flash High", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-3.8-flash-medium", "Gemini 3.8 Flash Medium", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-3.8-flash-low", "Gemini 3.8 Flash Low", AiProvider.ANTIGRAVITY, 1_048_576),
        model("ag:gemini-3.8-flash-tiered", "Gemini 3.8 Flash Tiered", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-flash-worker-agy", "Gemini 4 Flash Worker AGY", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:claude-opus-5-5-high", "Claude Opus 5.5 High (Antigravity)", AiProvider.ANTIGRAVITY, 200_000, thinking = true),
        model("ag:claude-opus-5-5-medium", "Claude Opus 5.5 Medium (Antigravity)", AiProvider.ANTIGRAVITY, 200_000, thinking = true),
        model("ag:claude-opus-5-5-low", "Claude Opus 5.5 Low (Antigravity)", AiProvider.ANTIGRAVITY, 200_000),
        model("ag:claude-sonnet-5-5-high", "Claude Sonnet 5.5 High (Antigravity)", AiProvider.ANTIGRAVITY, 200_000, thinking = true),
        model("ag:claude-sonnet-5-5-medium", "Claude Sonnet 5.5 Medium (Antigravity)", AiProvider.ANTIGRAVITY, 200_000, thinking = true),
        model("ag:claude-sonnet-5-5-low", "Claude Sonnet 5.5 Low (Antigravity)", AiProvider.ANTIGRAVITY, 200_000),
        model("ag:gemini-3-flash", "Gemini 3 Flash (Antigravity)", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite (Antigravity)", AiProvider.ANTIGRAVITY, 1_048_576),
        model("ag:gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite (Antigravity)", AiProvider.ANTIGRAVITY, 1_048_576),
        model("ag:gemini-2.5-flash-thinking", "Gemini 2.5 Flash Thinking (Antigravity)", AiProvider.ANTIGRAVITY, 1_048_576, thinking = true),
        model("ag:gemini-2.5-flash", "Gemini 2.5 Flash (Antigravity)", AiProvider.ANTIGRAVITY, 1_048_576),
        model("ag:gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite (Antigravity)", AiProvider.ANTIGRAVITY, 1_048_576),
        model("ag:gpt-oss-120b-medium", "GPT-OSS 120B Medium (Antigravity)", AiProvider.ANTIGRAVITY, 131_072, thinking = true)
    )

    /** Gemini 4 Hydrogen & Family Ultra Mesh. */
    val gemini4Hydrogen = listOf(
        model("g4h:gemini-4-hydrogen", "Gemini 4 Hydrogen (Ultra Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-hydrogen-xhigh", "Gemini 4 Hydrogen XHigh (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-hydrogen-high", "Gemini 4 Hydrogen High (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-hydrogen-medium", "Gemini 4 Hydrogen Medium (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-hydrogen-low", "Gemini 4 Hydrogen Low (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576),
        model("g4h:gemini-4-hydrogen-sonar", "Gemini 4 Hydrogen Sonar (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-hydrogen-tiered-xhigh", "Gemini 4 Hydrogen Tiered XHigh (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-hydrogen-tiered-high", "Gemini 4 Hydrogen Tiered High (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-hydrogen-thinking", "Gemini 4 Hydrogen Thinking (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-hydrogen", "Gemini Hydrogen Fast Alias (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576),
        model("g4h:gemini-4-neon-xhigh", "Gemini 4 Neon XHigh (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-neon-high", "Gemini 4 Neon High (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-neon-medium", "Gemini 4 Neon Medium (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-neon-low", "Gemini 4 Neon Low (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576),
        model("g4h:gemini-4-neon-thinking", "Gemini 4 Neon Thinking (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-carbon-high", "Gemini 4 Carbon High (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-carbon-medium", "Gemini 4 Carbon Medium (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-carbon-low", "Gemini 4 Carbon Low (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576),
        model("g4h:gemini-4-argon-high", "Gemini 4 Argon High (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-argon-medium", "Gemini 4 Argon Medium (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-4-argon-low", "Gemini 4 Argon Low (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576),
        model("g4h:gemini-pro-orchestrator", "Gemini 4 Pro Orchestrator (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-pro-orchestrator-argon", "Gemini 4 Pro Orchestrator Argon (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-pro-orchestrator-neon", "Gemini 4 Pro Orchestrator Neon (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-3.8-flash-high", "Gemini 3.8 Flash High (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true),
        model("g4h:gemini-flash-worker-agy", "Gemini 4 Flash Worker AGY (Mesh)", AiProvider.GEMINI4_HYDROGEN, 1_048_576, thinking = true)
    )

    /** Google Gemini 4 Direct & 5-Node Cluster. */
    val gemini4Direct = listOf(
        model("g4:gemini-4-hydrogen", "Gemini 4 Hydrogen Direct", AiProvider.GEMINI4_DIRECT, 1_048_576),
        model("g4:gemini-4-hydrogen-thinking", "Gemini 4 Hydrogen Thinking Direct", AiProvider.GEMINI4_DIRECT, 1_048_576, thinking = true),
        model("g4:gemini-4-neon", "Gemini 4 Neon Direct", AiProvider.GEMINI4_DIRECT, 1_048_576, thinking = true),
        model("g4:gemini-4-neon-thinking", "Gemini 4 Neon Thinking Direct", AiProvider.GEMINI4_DIRECT, 1_048_576, thinking = true),
        model("g4:gemini-4-carbon", "Gemini 4 Carbon Direct", AiProvider.GEMINI4_DIRECT, 1_048_576, thinking = true),
        model("g4:gemini-4-argon", "Gemini 4 Argon Direct", AiProvider.GEMINI4_DIRECT, 1_048_576, thinking = true),
        model("g4:gemini-hydrogen", "Gemini Hydrogen Short Alias", AiProvider.GEMINI4_DIRECT, 1_048_576),
        model("g4:gemini-neon", "Gemini Neon Short Alias", AiProvider.GEMINI4_DIRECT, 1_048_576, thinking = true),
        model("g4:gemini-auto", "Gemini 4 Auto Router", AiProvider.GEMINI4_DIRECT, 1_048_576, thinking = true),
        model("g4:gemini-3.7-flash", "Gemini 3.7 Flash (Hydrogen Route)", AiProvider.GEMINI4_DIRECT, 1_048_576),
        model("g4:gemini-3.6-flash", "Gemini 3.6 Flash (Hydrogen Route)", AiProvider.GEMINI4_DIRECT, 1_048_576),
        model("g4:gemini-3.5-flash", "Gemini 3.5 Flash (Hydrogen Route)", AiProvider.GEMINI4_DIRECT, 1_048_576),
        model("g4:gemini-3.5-flash-thinking", "Gemini 3.5 Flash Thinking (Neon Route)", AiProvider.GEMINI4_DIRECT, 1_048_576, thinking = true),
        model("g4:gemini-3.1-pro", "Gemini 3.1 Pro (Carbon Route)", AiProvider.GEMINI4_DIRECT, 1_048_576, thinking = true),
        model("g4:gemini-3.5-flash-thinking-lite", "Gemini 4 Dynamic Thinking", AiProvider.GEMINI4_DIRECT, 1_048_576, thinking = true),
        model("g4:gemini-flash-lite", "Gemini 4 Flash Lite", AiProvider.GEMINI4_DIRECT, 1_048_576)
    )

    /** Gemini Web Gateway. */
    val geminiWeb = listOf(
        model("gemweb:gemini-4-hydrogen", "Gemini 4 Hydrogen (Web Gateway)", AiProvider.GEMINI_WEB, 1_048_576),
        model("gemweb:gemini-4-hydrogen-thinking", "Gemini 4 Hydrogen Thinking (Web Gateway)", AiProvider.GEMINI_WEB, 1_048_576, thinking = true),
        model("gemweb:gemini-4-neon", "Gemini 4 Neon (Web Gateway)", AiProvider.GEMINI_WEB, 1_048_576, thinking = true),
        model("gemweb:gemini-4-neon-thinking", "Gemini 4 Neon Thinking (Web Gateway)", AiProvider.GEMINI_WEB, 1_048_576, thinking = true),
        model("gemweb:gemini-4-carbon", "Gemini 4 Carbon (Web Gateway)", AiProvider.GEMINI_WEB, 1_048_576, thinking = true),
        model("gemweb:gemini-3.7-flash", "Gemini 3.7 Flash (Web Gateway)", AiProvider.GEMINI_WEB, 1_048_576),
        model("gemweb:gemini-3.6-flash", "Gemini 3.6 Flash (Web Gateway)", AiProvider.GEMINI_WEB, 1_048_576),
        model("gemweb:gemini-3.5-flash", "Gemini 3.5 Flash (Web Gateway)", AiProvider.GEMINI_WEB, 1_048_576),
        model("gemweb:gemini-3.5-flash-thinking", "Gemini 3.5 Flash Thinking (Web Gateway)", AiProvider.GEMINI_WEB, 1_048_576, thinking = true),
        model("gemweb:gemini-3.1-pro", "Gemini 3.1 Pro (Web Gateway)", AiProvider.GEMINI_WEB, 1_048_576, thinking = true),
        model("gemweb:gemini-flash-lite", "Gemini Flash-Lite (Web Gateway)", AiProvider.GEMINI_WEB, 1_048_576)
    )

    /** MyChat Native Direct Cloud. */
    val myChatCloud = listOf(
        model("mychat:gpt-6-luna", "GPT-6 Luna Direct", AiProvider.MYCHAT_CLOUD, 400_000),
        model("mychat:gpt-5", "GPT-5 Direct", AiProvider.MYCHAT_CLOUD, 400_000, thinking = true),
        model("mychat:gpt-5-mini", "GPT-5 Mini Direct", AiProvider.MYCHAT_CLOUD, 400_000, thinking = true),
        model("mychat:gpt-4.1", "GPT-4.1 Direct", AiProvider.MYCHAT_CLOUD, 1_047_576),
        model("mychat:gpt-4.1-mini", "GPT-4.1 Mini Direct", AiProvider.MYCHAT_CLOUD, 1_047_576),
        model("mychat:gpt-4o", "GPT-4o Direct", AiProvider.MYCHAT_CLOUD, 128_000),
        model("mychat:o3", "OpenAI o3 Direct", AiProvider.MYCHAT_CLOUD, 200_000, thinking = true),
        model("mychat:o4-mini", "OpenAI o4-mini Direct", AiProvider.MYCHAT_CLOUD, 200_000, thinking = true)
    )

    /** Claude 5.5 & FreeClaude Direct Hub. */
    val claude5Proxy = listOf(
        model("claude5:claude-sonnet-5-5", "Claude Sonnet 5.5 Direct", AiProvider.CLAUDE5_PROXY, 1_000_000, thinking = true),
        model("claude5:claude-haiku-5-5", "Claude Haiku 5.5 Direct", AiProvider.CLAUDE5_PROXY, 1_000_000, thinking = true),
        model("claude5:claude-opus-5-5", "Claude Opus 5.5 Direct", AiProvider.CLAUDE5_PROXY, 1_000_000, thinking = true),
        model("claude5:kimi-k3.1-pro", "Kimi K3.1 Pro (FreeClaude)", AiProvider.CLAUDE5_PROXY, 262_144, thinking = true),
        model("claude5:step-5-preview", "StepFun Step 5 Preview (FreeClaude)", AiProvider.CLAUDE5_PROXY, 262_144, thinking = true),
        model("claude5:glm-4.5-flash", "GLM 4.5 Flash Direct", AiProvider.CLAUDE5_PROXY, 131_072, thinking = true),
        model("claude5:glm-z1-flash", "GLM Z1 Flash Thinking Direct", AiProvider.CLAUDE5_PROXY, 131_072, thinking = true),
        model("claude5:glm-4.6v-flash", "GLM 4.6V Flash VL Direct", AiProvider.CLAUDE5_PROXY, 200_000, thinking = true)
    )

    /** NVIDIA NIM OpenAI Cloud (7-key direct rotation + proxy cluster). */
    val nimCloud = listOf(
        model("nim:nvidia/nemotron-3.5-super-vl-120b-a12b", "Nemotron 3.5 Super VL 120B (NIM Direct)", AiProvider.NIM_CLOUD, 262_144, thinking = true),
        model("nim:nvidia/nemotron-3-ultra-550b-a55b", "Nemotron 3 Ultra 550B (NIM Direct)", AiProvider.NIM_CLOUD, 262_144, thinking = true),
        model("nim:nvidia/nemotron-3-nano-omni-30b-a3b-reasoning", "Nemotron 3 Nano Omni 30B VL (NIM Direct)", AiProvider.NIM_CLOUD, 256_000, thinking = true),
        model("nim:nvidia/nemotron-3.5-lightning-30b-a3b", "Nemotron 3.5 Lightning 30B (NIM Direct)", AiProvider.NIM_CLOUD, 262_144, thinking = true),
        model("nim:nvidia/nemotron-3.5-bolt-e4b", "Nemotron 3.5 Bolt E4B (NIM Direct)", AiProvider.NIM_CLOUD, 262_144),
        model("nim:mistralai/mistral-large-4-1t-a52b", "Mistral Large 4 1T A52B (NIM Direct)", AiProvider.NIM_CLOUD, 262_144, thinking = true),
        model("nim:stepfun-ai/step-5-preview", "StepFun Step 5 Preview (NIM Direct)", AiProvider.NIM_CLOUD, 262_144, thinking = true),
        model("nim:z-ai/glm-5.3-flash", "GLM 5.3 Flash (NIM Direct)", AiProvider.NIM_CLOUD, 262_144, thinking = true),
        model("nim:minimaxai/minimax-m3.1-flash", "MiniMax M3.1 Flash (NIM Direct)", AiProvider.NIM_CLOUD, 262_144, thinking = true),
        model("nim:poolside/laguna-xs-2.1", "Poolside Laguna XS 2.1 (NIM Direct)", AiProvider.NIM_CLOUD, 128_000)
    )

    /** BlockRun Gateway. */
    val blockRun = listOf(
        model("blockrun:nvidia/nemotron-3-super-120b", "Nemotron 3.5 Super 120B (BlockRun)", AiProvider.BLOCKRUN, 262_144, thinking = true),
        model("blockrun:nvidia/nemotron-3-nano-omni-30b-a3b-reasoning", "Nemotron 3 Nano Omni VL (BlockRun)", AiProvider.BLOCKRUN, 256_000, thinking = true),
        model("blockrun:nvidia/nemotron-nano-12b-v2-vl", "Nemotron Nano 12B V2 VL (BlockRun)", AiProvider.BLOCKRUN, 131_072),
        model("blockrun:nvidia/gpt-oss-120b", "GPT-OSS 120B (BlockRun)", AiProvider.BLOCKRUN, 131_072, thinking = true),
        model("blockrun:nvidia/step-3.7-flash", "StepFun Step 3.7 Flash (BlockRun)", AiProvider.BLOCKRUN, 131_072, thinking = true),
        model("blockrun:nvidia/minimax-m3.1-flash", "MiniMax M3.1 Flash (BlockRun)", AiProvider.BLOCKRUN, 1_000_000, thinking = true),
        model("blockrun:nvidia/nemotron-3-ultra-550b", "Nemotron 3 Ultra 550B (BlockRun)", AiProvider.BLOCKRUN, 1_000_000, thinking = true),
        model("blockrun:nvidia/nemotron-3.5-lightning", "Nemotron 3.5 Lightning (BlockRun)", AiProvider.BLOCKRUN, 262_144, thinking = true),
        model("blockrun:nvidia/nemotron-3.5-bolt", "Nemotron 3.5 Bolt (BlockRun)", AiProvider.BLOCKRUN, 262_144, thinking = true),
        model("blockrun:nvidia/muse-glimmer-30b", "Muse Glimmer 30B (BlockRun)", AiProvider.BLOCKRUN, 131_072, thinking = true),
        model("blockrun:poolside/laguna-xs-2.1", "Poolside Laguna XS 2.1 (BlockRun)", AiProvider.BLOCKRUN, 128_000, thinking = true),
        model("blockrun:cohere/north-mini-code", "Cohere North Mini Code (BlockRun)", AiProvider.BLOCKRUN, 256_000, thinking = true)
    )

    /** FreeLLM & Logfare Frontier Hub. */
    val freeLlmHub = listOf(
        model("freellm:glm-5.3", "GLM 5.3 (FreeLLM Hub)", AiProvider.FREELLM_HUB, 1_000_000, thinking = true),
        model("freellm:glm-5.2", "GLM 5.2 (FreeLLM Hub)", AiProvider.FREELLM_HUB, 1_000_000, thinking = true),
        model("freellm:glm-5.3-flash", "GLM 5.3 Flash (FreeLLM Hub)", AiProvider.FREELLM_HUB, 1_000_000, thinking = true),
        model("freellm:kimi-k2.7-code", "Kimi K2.7 Code (FreeLLM Hub)", AiProvider.FREELLM_HUB, 262_144, thinking = true),
        model("freellm:kimi-k2.6", "Kimi K2.6 (FreeLLM Hub)", AiProvider.FREELLM_HUB, 262_144, thinking = true),
        model("freellm:deepseek-v4.1-flash-1017", "DeepSeek V4.1 Flash 1017 (FreeLLM Hub)", AiProvider.FREELLM_HUB, 1_048_576, thinking = true),
        model("freellm:DeepSeek-V3.2", "DeepSeek V3.2 (FreeLLM Hub)", AiProvider.FREELLM_HUB, 163_840, thinking = true),
        model("freellm:qwen3-235b", "Qwen3 235B (FreeLLM Hub)", AiProvider.FREELLM_HUB, 131_072, thinking = true),
        model("freellm:Qwen3.8-27B", "Qwen 3.8 27B (FreeLLM Hub)", AiProvider.FREELLM_HUB, 262_144, thinking = true),
        model("freellm:Qwen3.8-Flash-Next", "Qwen 3.8 Flash Next (FreeLLM Hub)", AiProvider.FREELLM_HUB, 262_144),
        model("freellm:mistral-large-latest", "Mistral Large 3 (FreeLLM Hub)", AiProvider.FREELLM_HUB, 262_144),
        model("freellm:mistral-medium-3-5", "Mistral Medium 3.5 (FreeLLM Hub)", AiProvider.FREELLM_HUB, 262_144, thinking = true),
        model("freellm:devstral-latest", "Mistral Devstral Latest (Logfare Hub)", AiProvider.FREELLM_HUB, 131_072, thinking = true),
        model("freellm:magistral-medium-latest", "Mistral Magistral Medium (Logfare Hub)", AiProvider.FREELLM_HUB, 131_072, thinking = true),
        model("freellm:codestral-latest", "Mistral Codestral Latest (Logfare Hub)", AiProvider.FREELLM_HUB, 262_144),
        model("freellm:reka-flash-3", "Reka Flash 3 (Logfare Hub)", AiProvider.FREELLM_HUB, 131_072, thinking = true),
        model("freellm:zai-glm-4.7", "Z.ai GLM 4.7 Cerebras WSE (Logfare Hub)", AiProvider.FREELLM_HUB, 131_072, thinking = true),
        model("freellm:gemma-4-31b-it", "Gemma 4 31B IT (FreeLLM Hub)", AiProvider.FREELLM_HUB, 131_072, thinking = true),
        model("freellm:gpt-oss-120b", "GPT-OSS 120B (FreeLLM Hub)", AiProvider.FREELLM_HUB, 131_072, thinking = true),
        model("freellm:Llama-4-Maverick-17B-128E-Instruct", "Llama 4 Maverick (FreeLLM Hub)", AiProvider.FREELLM_HUB, 1_048_576)
    )

    /** AI Bridge Gateway. */
    val aiBridge = listOf(
        model("aibridge:gpt-6-luna", "GPT-6 Luna (AI Bridge)", AiProvider.AI_BRIDGE, 400_000),
        model("aibridge:claude-haiku-5-5", "Claude Haiku 5.5 (AI Bridge)", AiProvider.AI_BRIDGE, 1_000_000),
        model("aibridge:gpt-4.1", "GPT-4.1 (AI Bridge)", AiProvider.AI_BRIDGE, 1_047_576),
        model("aibridge:gpt-4o", "GPT-4o (AI Bridge)", AiProvider.AI_BRIDGE, 128_000),
        model("aibridge:deepseek-v4-flash", "DeepSeek V4 Flash (AI Bridge)", AiProvider.AI_BRIDGE, 1_048_576),
        model("aibridge:grok-4.20", "Grok 4.20 (AI Bridge)", AiProvider.AI_BRIDGE, 256_000),
        model("aibridge:deepseek-v4-flash-vision-exp", "DeepSeek V4 Flash Vision (AI Bridge)", AiProvider.AI_BRIDGE, 1_048_576, thinking = true),
        model("aibridge:qwen3.5-flash", "Qwen 3.5 Flash (AI Bridge)", AiProvider.AI_BRIDGE, 262_144, thinking = true),
        model("aibridge:doubao-seed-2-0-mini-260428", "Doubao Seed 2.0 Mini (AI Bridge)", AiProvider.AI_BRIDGE, 131_072, thinking = true),
        model("aibridge:supabase-qwen3.7-flash", "Qwen 3.7 Flash (AI Bridge)", AiProvider.AI_BRIDGE, 1_000_000, thinking = true)
    )

    /** HF Router Hub. */
    val hfRouter = listOf(
        model("hfrouter:qwen3.8", "Qwen 3.8 2.4T A95B (HF Router)", AiProvider.HF_ROUTER, 262_144, thinking = true),
        model("hfrouter:deepseek-v4-pro", "DeepSeek V4 Pro (HF Router)", AiProvider.HF_ROUTER, 1_048_576, thinking = true),
        model("hfrouter:deepseek-v4-flash", "DeepSeek V4 Flash (HF Router)", AiProvider.HF_ROUTER, 1_048_576, thinking = true),
        model("hfrouter:glm-5.3", "GLM 5.3 (HF Router)", AiProvider.HF_ROUTER, 1_000_000, thinking = true),
        model("hfrouter:minimax-m2.7", "MiniMax M2.7 (HF Router)", AiProvider.HF_ROUTER, 1_000_000, thinking = true),
        model("hfrouter:kimi-k2", "Kimi K2 (HF Router)", AiProvider.HF_ROUTER, 262_144),
        model("hfrouter:gpt-oss-120b", "GPT-OSS 120B (HF Router)", AiProvider.HF_ROUTER, 131_072, thinking = true),
        model("hfrouter:qwen3-coder-480b", "Qwen3 Coder 480B (HF Router)", AiProvider.HF_ROUTER, 262_144),
        model("hfrouter:qwen3-coder-next", "Qwen3 Coder Next (HF Router)", AiProvider.HF_ROUTER, 262_144)
    )

    /** LiteLLM & Groq LPU Frontier Hub. */
    val liteLlmHub = listOf(
        model("litellm:groq/gpt-oss-120b", "GPT-OSS 120B (Groq LPU)", AiProvider.LITELLM_HUB, 131_072, thinking = true),
        model("litellm:groq/qwen3.8-27b", "Qwen 3.8 27B (Groq LPU)", AiProvider.LITELLM_HUB, 131_072),
        model("litellm:nvidia/nemotron-3-ultra-550b-a55b:free", "Nemotron 3 Ultra 550B (LiteLLM)", AiProvider.LITELLM_HUB, 1_000_000, thinking = true),
        model("litellm:deepseek-v4.1-flash-1017", "DeepSeek V4.1 Flash (LiteLLM)", AiProvider.LITELLM_HUB, 1_048_576, thinking = true),
        model("litellm:gemini-2.5-flash", "Gemini 2.5 Flash (LiteLLM)", AiProvider.LITELLM_HUB, 1_048_576, thinking = true)
    )

    /** Supabase Tongyi VIP Cloud. */
    val supaVip = listOf(
        model("supavip:qwen4-max", "Qwen 4 Max VIP", AiProvider.SUPA_VIP, 1_000_000, thinking = true),
        model("supavip:qwen3.8-max", "Qwen 3.8 Max VIP", AiProvider.SUPA_VIP, 983_616, thinking = true),
        model("supavip:qwen3.7-flash", "Qwen 3.7 Flash VIP", AiProvider.SUPA_VIP, 1_000_000, thinking = true),
        model("supavip:deepseek-v4.1-flash", "DeepSeek V4.1 Flash VIP", AiProvider.SUPA_VIP, 1_048_576, thinking = true),
        model("supavip:deepseek-r1", "DeepSeek R1 VIP", AiProvider.SUPA_VIP, 163_840, thinking = true)
    )

    /** ShanNian, Volcengine Ark & SenseNova VIP. */
    val shanNianVip = listOf(
        model("shannian:deepseek-v4-flash-vision-exp", "DeepSeek V4 Flash Vision VIP", AiProvider.SHANNIAN_VIP, 1_048_576, thinking = true),
        model("shannian:qwen3.5-flash", "Qwen 3.5 Flash VIP", AiProvider.SHANNIAN_VIP, 262_144, thinking = true),
        model("shannian:doubao-seed-2-0-lite-260428", "Doubao Seed 2.0 Lite (Volcengine Ark)", AiProvider.SHANNIAN_VIP, 131_072, thinking = true),
        model("shannian:doubao-seed-2-0-mini-260428", "Doubao Seed 2.0 Mini VIP", AiProvider.SHANNIAN_VIP, 131_072, thinking = true),
        model("shannian:sensenova-6.8-flash", "SenseNova 6.8 Flash Direct", AiProvider.SHANNIAN_VIP, 131_072, thinking = true),
        model("shannian:sensenova-6.8-flash-lite", "SenseNova 6.8 Flash Lite Direct", AiProvider.SHANNIAN_VIP, 131_072)
    )

    /** Agnes AI Direct Hub. */
    val agnesCloud = listOf(
        model("agnescloud:agnes-3.0-pro", "Agnes 3.0 Pro Direct", AiProvider.AGNES_CLOUD, 524_288, thinking = true),
        model("agnescloud:agnes-3.0-flash", "Agnes 3.0 Flash Direct", AiProvider.AGNES_CLOUD, 524_288, thinking = true),
        model("agnescloud:agnes-2.5-pro", "Agnes 2.5 Pro Direct", AiProvider.AGNES_CLOUD, 524_288, thinking = true),
        model("agnescloud:agnes-2.5-pro-beta", "Agnes 2.5 Pro Beta Thinking Direct", AiProvider.AGNES_CLOUD, 524_288, thinking = true),
        model("agnescloud:agnes-2.5-pro-theta", "Agnes 2.5 Pro Theta Direct", AiProvider.AGNES_CLOUD, 524_288, thinking = true),
        model("agnescloud:agnes-2.5-flash", "Agnes 2.5 Flash VL Direct", AiProvider.AGNES_CLOUD, 524_288, thinking = true)
    )

    /** Z.ai BigModel v4 & LongCat 2.5 Official Cloud. */
    val zhipuLongCat = listOf(
        model("zhipu:LongCat-2.5", "LongCat 2.5 1.6T MoE Direct", AiProvider.ZHIPU_LONGCAT, 131_072, thinking = true),
        model("zhipu:LongCat-2.5-Preview", "LongCat 2.5 Preview Direct", AiProvider.ZHIPU_LONGCAT, 131_072, thinking = true),
        model("zhipu:LongCat-2.0", "LongCat 2.0 Direct", AiProvider.ZHIPU_LONGCAT, 131_072),
        model("zhipu:glm-4.5-flash", "GLM 4.5 Flash (Z.ai Direct)", AiProvider.ZHIPU_LONGCAT, 131_072, thinking = true),
        model("zhipu:glm-z1-flash", "GLM Z1 Flash Thinking (Z.ai Direct)", AiProvider.ZHIPU_LONGCAT, 131_072, thinking = true),
        model("zhipu:glm-4v-flash", "GLM 4V Flash VL (Z.ai Direct)", AiProvider.ZHIPU_LONGCAT, 131_072),
        model("zhipu:glm-4-flash-250414", "GLM 4 Flash 250414 (Z.ai Direct)", AiProvider.ZHIPU_LONGCAT, 131_072)
    )

    /** OpenCode Zen Free. */
    val openCodeZenFree = listOf(
        model("zenfree:big-pickle", "Big Pickle (OpenCode Zen Free)", AiProvider.OPENCODE_ZEN_FREE, 200_000, thinking = true),
        model("zenfree:deepseek-v4.1-flash-free", "DeepSeek V4.1 Flash (OpenCode Zen Free)", AiProvider.OPENCODE_ZEN_FREE, 1_048_576, thinking = true),
        model("zenfree:mimo-v2.6-flash-free", "MiMo V2.6 Flash (OpenCode Zen Free)", AiProvider.OPENCODE_ZEN_FREE, 256_000, thinking = true),
        model("zenfree:nemotron-3.5-super-vl-free", "Nemotron 3.5 Super VL (OpenCode Zen Free)", AiProvider.OPENCODE_ZEN_FREE, 262_144, thinking = true),
        model("zenfree:nemotron-3.5-lightning-free", "Nemotron 3.5 Lightning (OpenCode Zen Free)", AiProvider.OPENCODE_ZEN_FREE, 262_144, thinking = true),
        model("zenfree:beam-free", "Reflection Beam (OpenCode Zen Free)", AiProvider.OPENCODE_ZEN_FREE, 1_048_576, thinking = true),
        model("zenfree:flint-40b-free", "Flint 40B (OpenCode Zen Free)", AiProvider.OPENCODE_ZEN_FREE, 262_144, thinking = true),
        model("zenfree:iceman-free", "Iceman (OpenCode Zen Free)", AiProvider.OPENCODE_ZEN_FREE, 200_000, thinking = true),
        model("zenfree:hy4-flash-free", "Hy4 Flash (OpenCode Zen Free)", AiProvider.OPENCODE_ZEN_FREE, 256_000, thinking = true),
        model("zenfree:tidepool-alpha-free", "Tidepool Alpha (OpenCode Zen Free)", AiProvider.OPENCODE_ZEN_FREE, 200_000, thinking = true)
    )

    /** Vireonix Auto. */
    val vireonix = listOf(
        model("vireonix:auto", "Vireonix Auto Router", AiProvider.VIREONIX, 1_000_000)
    )

    /** Colibri Qwen 3.6 & EU Keyless Cloud. */
    val colibriQwen = listOf(
        model("colibri:Qwen3.6-35B-A3B", "Qwen 3.6 35B A3B MoE (Colibri)", AiProvider.COLIBRI_QWEN, 262_144, thinking = true),
        model("colibri:DeepSeek-V4.1-Flash-1017", "DeepSeek V4.1 Flash (EU Keyless)", AiProvider.COLIBRI_QWEN, 262_144, thinking = true),
        model("colibri:Qwen3.8-27B", "Qwen 3.8 27B VL (EU Keyless)", AiProvider.COLIBRI_QWEN, 262_144, thinking = true),
        model("colibri:Qwen3.5-397B-A17B", "Qwen 3.5 397B MoE (EU Keyless)", AiProvider.COLIBRI_QWEN, 262_144, thinking = true),
        model("colibri:gemma-4-26b-a4b", "Gemma 4 26B A4B MoE (EU Keyless)", AiProvider.COLIBRI_QWEN, 131_072, thinking = true)
    )

    /** Gemma 4 Flagship & Vision Cluster. */
    val gemma4Cluster = listOf(
        model("gemma4:gemma-4-31b-it", "Gemma 4 31B IT Flagship", AiProvider.GEMMA4_CLUSTER, 131_072, thinking = true),
        model("gemma4:gemma-4-12b-it", "Gemma 4 12B IT", AiProvider.GEMMA4_CLUSTER, 131_072, thinking = true),
        model("gemma4:gemma-4-12b-coding", "Gemma 4 12B Coding", AiProvider.GEMMA4_CLUSTER, 131_072, thinking = true),
        model("gemma4:gemma-4-e4b-vision-q4", "Gemma 4 E4B Vision UI", AiProvider.GEMMA4_CLUSTER, 131_072),
        model("gemma4:gemma-4-e4b-ud-q5", "Gemma 4 E4B VL (UD-Q5_K_XL)", AiProvider.GEMMA4_CLUSTER, 131_072),
        model("gemma4:gemma-4-e4b-qat", "Gemma 4 E4B QAT", AiProvider.GEMMA4_CLUSTER, 131_072),
        model("gemma4:gemma-4-ollama", "Gemma 4 Latest", AiProvider.GEMMA4_CLUSTER, 131_072)
    )

    /** Frontier Open-Weights & Liquid LFM Hub. */
    val frontierLab = listOf(
        model("frontier:qwen3.8-flash-next", "Qwen 3.8 Flash Next", AiProvider.FRONTIER_LAB, 131_072, thinking = true),
        model("frontier:muse-glimmer-30b", "Meta Muse Glimmer 30B", AiProvider.FRONTIER_LAB, 131_072, thinking = true),
        model("frontier:inkling-2-mini", "Thinking Machines Inkling 2 Mini", AiProvider.FRONTIER_LAB, 131_072, thinking = true),
        model("frontier:nanbeige4.3-33b", "Nanbeige 4.3 33B", AiProvider.FRONTIER_LAB, 131_072, thinking = true),
        model("frontier:xing4.0-29b-a4b", "Xing 4.0 29B A4B MoE", AiProvider.FRONTIER_LAB, 131_072, thinking = true),
        model("frontier:mellum2-12b-thinking", "JetBrains Mellum 2 12B Thinking", AiProvider.FRONTIER_LAB, 131_072, thinking = true),
        model("frontier:nanbeige4.2-3b", "Nanbeige 4.2 3B", AiProvider.FRONTIER_LAB, 131_072, thinking = true)
    )

    /** DeepAI. */
    val deepAi = listOf(
        model("deepai:gpt-6-luna", "GPT-6 Luna (DeepAI)", AiProvider.DEEPAI, 128_000),
        model("deepai:claude-haiku-5-5", "Claude Haiku 5.5 (DeepAI)", AiProvider.DEEPAI, 200_000),
        model("deepai:glm-5.3-flash", "GLM 5.3 Flash (DeepAI)", AiProvider.DEEPAI, 128_000, thinking = true),
        model("deepai:deepseek-v4.1-flash", "DeepSeek V4.1 Flash (DeepAI)", AiProvider.DEEPAI, 128_000),
        model("deepai:qwen3.8-flash", "Qwen 3.8 Flash (DeepAI)", AiProvider.DEEPAI, 128_000),
        model("deepai:gemma-4", "Gemma 4 (DeepAI)", AiProvider.DEEPAI, 128_000),
        model("deepai:standard", "DeepAI Standard", AiProvider.DEEPAI, 128_000)
    )

    /** Motif. */
    val motif = listOf(
        model("motif:motif-3-102b", "Motif 3 102B", AiProvider.MOTIF, 131_072, thinking = true, tools = false),
        model("motif:motif-3-12.7b", "Motif 3 12.7B", AiProvider.MOTIF, 131_072, thinking = true, tools = false)
    )

    /** ChatJimmy 20,000+ tok/s. */
    val chatJimmy = listOf(
        model("chatjimmy:llama3.1-8b", "Llama 3.1 8B Taalas Silicon (20,000+ tok/s)", AiProvider.CHATJIMMY, 131_072, thinking = false, tools = false)
    )

    /** Volcengine Ark & SenseNova Direct Cloud. */
    val volcArk = listOf(
        model("volcark:doubao-seed-2-0-lite-260428", "Doubao Seed 2.0 Lite Direct", AiProvider.VOLC_ARK, 131_072, thinking = true),
        model("volcark:doubao-seed-2-0-mini-260428", "Doubao Seed 2.0 Mini Direct", AiProvider.VOLC_ARK, 131_072, thinking = true),
        model("volcark:sensenova-6.8-flash", "SenseNova 6.8 Flash Direct", AiProvider.VOLC_ARK, 131_072, thinking = true),
        model("volcark:sensenova-6.8-flash-lite", "SenseNova 6.8 Flash Lite Direct", AiProvider.VOLC_ARK, 131_072)
    )

    /** Logfare, Mistral & Reka Frontier Hub. */
    val logfareHub = listOf(
        model("logfare:devstral-latest", "Mistral Devstral Latest", AiProvider.LOGFARE_HUB, 131_072, thinking = true),
        model("logfare:magistral-medium-latest", "Mistral Magistral Medium", AiProvider.LOGFARE_HUB, 131_072, thinking = true),
        model("logfare:codestral-latest", "Mistral Codestral Latest", AiProvider.LOGFARE_HUB, 262_144),
        model("logfare:reka-flash-3", "Reka Flash 3", AiProvider.LOGFARE_HUB, 131_072, thinking = true),
        model("logfare:reka-edge-2603", "Reka Edge 2603", AiProvider.LOGFARE_HUB, 131_072),
        model("logfare:zai-glm-4.7", "Z.ai GLM 4.7 (Cerebras WSE)", AiProvider.LOGFARE_HUB, 131_072, thinking = true),
        model("logfare:DeepSeek-V3.1-cb", "DeepSeek V3.1 Reasoning (SambaNova)", AiProvider.LOGFARE_HUB, 163_840, thinking = true),
        model("logfare:meta-llama/llama-4-scout-17b-16e-instruct", "Llama 4 Scout 17B 16E (Groq LPU)", AiProvider.LOGFARE_HUB, 131_072),
        model("logfare:phoenix-1.0", "Logfare Phoenix 1.0", AiProvider.LOGFARE_HUB, 131_072, thinking = true),
        model("logfare:clef", "Logfare Clef", AiProvider.LOGFARE_HUB, 131_072, thinking = true),
        model("logfare:clef-flash", "Logfare Clef Flash", AiProvider.LOGFARE_HUB, 131_072, thinking = true),
        model("logfare:gemma-4-26b", "Google Gemma 4 26B A4B", AiProvider.LOGFARE_HUB, 131_072, thinking = true),
        model("logfare:MinerU2.5-Pro", "MinerU 2.5 Pro", AiProvider.LOGFARE_HUB, 131_072, thinking = true),
        model("logfare:DeepSeek-V4-Flash-Vision-Exp", "DeepSeek V4 Flash Vision", AiProvider.LOGFARE_HUB, 1_048_576, thinking = true)
    )

    /** FreeClaude & Gemini 4 Hub. */
    val freeCc = listOf(
        model("freecc:claude-3-freecc-no-thinking/gemini/models/gemini-4-hydrogen", "Gemini 4 Hydrogen (FreeClaude)", AiProvider.FREECC, 1_048_576, thinking = true),
        model("freecc:claude-3-freecc-no-thinking/gemini/models/gemini-3-flash-preview", "Gemini 3 Flash VL (FreeClaude)", AiProvider.FREECC, 1_048_576, thinking = true),
        model("freecc:claude-3-freecc-no-thinking/gemini/models/gemini-3.5-flash", "Gemini 3.5 Flash VL (FreeClaude)", AiProvider.FREECC, 1_048_576, thinking = true),
        model("freecc:claude-3-freecc-no-thinking/gemini/models/gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite VL (FreeClaude)", AiProvider.FREECC, 1_048_576),
        model("freecc:claude-3-freecc-no-thinking/nvidia_nim/moonshotai/kimi-k3.1-pro", "Kimi K3.1 Pro (FreeClaude)", AiProvider.FREECC, 262_144, thinking = true),
        model("freecc:claude-3-freecc-no-thinking/nvidia_nim/stepfun-ai/step-5-preview", "StepFun Step 5 Preview (FreeClaude)", AiProvider.FREECC, 262_144, thinking = true),
        model("freecc:claude-3-freecc-no-thinking/nvidia_nim/nvidia/nemotron-3.5-super-vl-120b-a12b", "Nemotron 3.5 Super VL 120B (FreeClaude)", AiProvider.FREECC, 262_144, thinking = true),
        model("freecc:claude-3-freecc-no-thinking/nvidia_nim/nvidia/nemotron-3-nano-omni-30b-a3b-reasoning", "Nemotron 3 Nano Omni 30B VL (FreeClaude)", AiProvider.FREECC, 256_000, thinking = true),
        model("freecc:claude-3-freecc-no-thinking/nvidia_nim/nvidia/nemotron-3-ultra-550b-a55b", "Nemotron 3 Ultra 550B (FreeClaude)", AiProvider.FREECC, 262_144, thinking = true),
        model("freecc:claude-3-freecc-no-thinking/nvidia_nim/nvidia/nemotron-3.5-lightning-30b-a3b", "Nemotron 3.5 Lightning 30B (FreeClaude)", AiProvider.FREECC, 262_144, thinking = true),
        model("freecc:claude-3-freecc-no-thinking/nvidia_nim/nvidia/nemotron-3.5-bolt-e4b", "Nemotron 3.5 Bolt E4B (FreeClaude)", AiProvider.FREECC, 262_144, thinking = true),
        model("freecc:claude-3-freecc-no-thinking/nvidia_nim/poolside/laguna-xs-2.1", "Poolside Laguna XS 2.1 (FreeClaude)", AiProvider.FREECC, 131_072)
    )

    /** Together Frontier. */
    val togetherFrontier = listOf(
        model("together2api:glm-5.3", "GLM 5.3 (Together Frontier)", AiProvider.TOGETHER_FRONTIER, 1_000_000, thinking = true),
        model("together2api:glm-5.3-flash", "GLM 5.3 Flash (Together Frontier)", AiProvider.TOGETHER_FRONTIER, 1_000_000, thinking = true),
        model("together2api:kimi-k3", "Kimi K3 2.8T (Together Frontier)", AiProvider.TOGETHER_FRONTIER, 1_048_576, thinking = true),
        model("together2api:muse-glimmer-30b", "Muse Glimmer 30B (Together Frontier)", AiProvider.TOGETHER_FRONTIER, 131_072, thinking = true),
        model("together2api:gpt-oss-120b", "GPT-OSS 120B (Together Frontier)", AiProvider.TOGETHER_FRONTIER, 131_072, thinking = true),
        model("together2api:glm-5.2", "GLM 5.2 (Together Frontier)", AiProvider.TOGETHER_FRONTIER, 1_000_000, thinking = true),
        model("together2api:qwen3.5-9b", "Qwen 3.5 9B (Together Frontier)", AiProvider.TOGETHER_FRONTIER, 262_144, thinking = true)
    )

    /** Liquid AI LFM Gateway. */
    val liquidAi = listOf(
        model("liquidai:lfm2.5-24b-a2b", "Liquid LFM 2.5 24B A2B (Q8_0)", AiProvider.LIQUID_AI, 131_072, thinking = true),
        model("liquidai:lfm2.5-8b-a1b", "Liquid LFM 2.5 8B A1B (Q4_K_M)", AiProvider.LIQUID_AI, 131_072, thinking = true),
        model("liquidai:lfm3-1.7b", "Liquid LFM 3 1.7B", AiProvider.LIQUID_AI, 131_072),
        model("liquidai:lfm2.5-2.6b", "Liquid LFM 2.5 2.6B", AiProvider.LIQUID_AI, 131_072)
    )

    /** Gemma 4 Vision & QAT Studio. */
    val gemma4Vl = listOf(
        model("gemma4vl:gemma-4-e4b-vision-q4", "Gemma 4 E4B Vision UI (Q4_K_M)", AiProvider.GEMMA4_VL, 131_072),
        model("gemma4vl:gemma-4-e2b-q6k-aggressive", "Gemma 4 E2B Uncensored (Q6_K_P)", AiProvider.GEMMA4_VL, 131_072, thinking = true),
        model("gemma4vl:gemma-4-e2b-qat-q4", "Gemma 4 E2B QAT (Q4_0)", AiProvider.GEMMA4_VL, 131_072, thinking = true),
        model("gemma4vl:gemma-4-e2b-qat-mobile", "Gemma 4 E2B QAT Mobile (UD-Q2_K_XL)", AiProvider.GEMMA4_VL, 131_072),
        model("gemma4vl:gemma-4-e2b-it-fast", "Gemma 4 E2B IT Fast", AiProvider.GEMMA4_VL, 131_072)
    )

    /** Bonsai & Ternary Frontier. */
    val bonsaiCloud = listOf(
        model("bonsaicloud:ternary-bonsai-2-27b", "Ternary Bonsai 2 27B (PTQ1_0)", AiProvider.BONSAI_CLOUD, 131_072, thinking = true),
        model("bonsaicloud:bonsai-8b", "Ternary Bonsai 8B", AiProvider.BONSAI_CLOUD, 131_072),
        model("bonsaicloud:vibethinker-3b-q4", "VibeThinker 3B Reasoning", AiProvider.BONSAI_CLOUD, 131_072, thinking = true)
    )

    /** Unsloth Qwen 3.5 & Gemma 4 Cloud. */
    val unslothCloud = listOf(
        model("unslothcloud:qwen3.5-4b-q6", "Qwen 3.5 4B (Q6_K)", AiProvider.UNSLOTH_CLOUD, 262_144, thinking = true),
        model("unslothcloud:gemma-4-e2b-ud-q4", "Gemma 4 E2B IT (UD-Q4_K_XL)", AiProvider.UNSLOTH_CLOUD, 131_072, thinking = true),
        model("unslothcloud:gemma-4-e2b-qat-ud", "Gemma 4 E2B QAT (UD-Q4_K_XL)", AiProvider.UNSLOTH_CLOUD, 131_072, thinking = true),
        model("unslothcloud:gemma-4-e2b-q4ks", "Gemma 4 E2B IT (Q4_K_S)", AiProvider.UNSLOTH_CLOUD, 131_072)
    )

    /** KoboldAI Multi-Node Cloud. */
    val koboldCloud = listOf(
        model("koboldcloud:koboldcpp/Gemma4-E2B", "Gemma 4 E2B (Official KoboldAI)", AiProvider.KOBOLD_CLOUD, 131_072),
        model("koboldcloud:koboldcpp/gemma-2-9b-it-Q5_K_M", "Gemma 2 9B IT (Q5_K_M)", AiProvider.KOBOLD_CLOUD, 131_072),
        model("koboldcloud:koboldcpp/gemma-3n-E2B-it", "Gemma 3n E2B IT", AiProvider.KOBOLD_CLOUD, 131_072)
    )

    /** HF Frontier Cluster. */
    val hfCluster = listOf(
        model("hfcluster:qwen3.8-27b-q8", "Qwen 3.8 27B (UD-Q8_K_XL)", AiProvider.HF_CLUSTER, 262_144, thinking = true),
        model("hfcluster:gemma-4-e4b-q5", "Gemma 4 E4B IT (Q5_K_P)", AiProvider.HF_CLUSTER, 131_072),
        model("hfcluster:gemma-4-e2b-q8", "Gemma 4 E2B IT (Q8_0)", AiProvider.HF_CLUSTER, 131_072)
    )

    /** Phi-4 & Llama 3.1 Abliterated Cloud. */
    val phi4Cloud = listOf(
        model("phi4cloud:phi-4-14b-gguf", "Microsoft Phi-4 14B", AiProvider.PHI4_CLOUD, 131_072, thinking = true),
        model("phi4cloud:llama-3.1-8b-abliterated", "Llama 3.1 8B Abliterated (Q4_K_M)", AiProvider.PHI4_CLOUD, 131_072),
        model("phi4cloud:llama-cpp-fast", "Llama.cpp Fast GGUF", AiProvider.PHI4_CLOUD, 131_072)
    )

    /** Llama.cpp LFM & SmolVLM Cloud. */
    val llamaCppCloud = listOf(
        model("llamacppcloud:lfm2.5-2.6b-uncensored", "Liquid LFM 2.5 2.6B Uncensored", AiProvider.LLAMACPP_CLOUD, 131_072),
        model("llamacppcloud:lfm3-1.7b-gguf", "Liquid LFM 3 1.7B GGUF", AiProvider.LLAMACPP_CLOUD, 131_072, thinking = true),
        model("llamacppcloud:smolvlm-500m-instruct", "SmolVLM 500M Vision (Q8_0)", AiProvider.LLAMACPP_CLOUD, 131_072),
        model("llamacppcloud:lfm2.5-230m-instant", "Liquid LFM 2.5 230M Instant", AiProvider.LLAMACPP_CLOUD, 131_072)
    )

    /** Ollama Multi-Model Cloud. */
    val ollamaCloud = listOf(
        model("ollamacloud:deepseek-r1:1.5b", "DeepSeek R1 1.5B Reasoning", AiProvider.OLLAMA_CLOUD, 163_840, thinking = true),
        model("ollamacloud:gemma:7b-instruct", "Google Gemma 7B Instruct", AiProvider.OLLAMA_CLOUD, 131_072),
        model("ollamacloud:qwen2.5:1.5b", "Qwen 2.5 1.5B Instruct", AiProvider.OLLAMA_CLOUD, 131_072),
        model("ollamacloud:qwen-coder-0.5b", "Qwen 2.5 Coder Fast", AiProvider.OLLAMA_CLOUD, 131_072),
        model("ollamacloud:moondream:latest", "Moondream 2 Vision", AiProvider.OLLAMA_CLOUD, 131_072)
    )

    /** AI Horde. */
    val aiHorde = listOf(
        model("aihorde:Qwen/Qwen3.8-Flash-Next", "Qwen 3.8 Flash Next (AI Horde)", AiProvider.AI_HORDE, 262_144),
        model("aihorde:koboldcpp/Qwen/Qwen3.8-27B", "Qwen 3.8 27B (AI Horde)", AiProvider.AI_HORDE, 262_144),
        model("aihorde:llamacpp/Qwen3.6-35B-A3B-Uncensored-HauhauCS-Aggressive", "Qwen 3.6 35B MoE (AI Horde)", AiProvider.AI_HORDE, 262_144),
        model("aihorde:koboldcpp/Gemma-4-E4B-Uncensored-HauhauCS-Aggressive-Q2_K_P", "Gemma 4 E4B (AI Horde)", AiProvider.AI_HORDE, 131_072),
        model("aihorde:koboldcpp/Cydonia-24B-v4zg-Q4_K_M", "Cydonia 24B (AI Horde)", AiProvider.AI_HORDE, 131_072),
        model("aihorde:koboldcpp/Llama-3.2-3B-Instruct", "Llama 3.2 3B Instruct (AI Horde)", AiProvider.AI_HORDE, 131_072)
    )

    /** APIsFreeLLM. */
    val apisFreeLlm = listOf(
        model("apisfreellm:free-ai", "Free-AI 9B Uncensored", AiProvider.APIS_FREE_LLM, 131_072)
    )

    /** YQCloud Stream. */
    val yqCloud = listOf(
        model("yqcloud:yqcloud-default", "YQCloud GPT Stream", AiProvider.YQ_CLOUD, 131_072)
    )

    /** LongCat Oversea V2. */
    val longCatWeb = listOf(
        model("longcat:longcat-2.5", "LongCat 2.5 (Oversea V2)", AiProvider.LONGCAT_WEB, 131_072, thinking = true)
    )

    private val byProvider: Map<AiProvider, List<AiModel>> = mapOf(
        AiProvider.KILOCODE to kilo,
        AiProvider.HY3 to hy3,
        AiProvider.CLOUDFLARE_PLAYGROUND to cloudflare,
        AiProvider.LAGUNA to laguna,
        AiProvider.ANTIGRAVITY to antigravity,
        AiProvider.GEMINI4_HYDROGEN to gemini4Hydrogen,
        AiProvider.GEMINI4_DIRECT to gemini4Direct,
        AiProvider.GEMINI_WEB to geminiWeb,
        AiProvider.MYCHAT_CLOUD to myChatCloud,
        AiProvider.CLAUDE5_PROXY to claude5Proxy,
        AiProvider.NIM_CLOUD to nimCloud,
        AiProvider.BLOCKRUN to blockRun,
        AiProvider.FREELLM_HUB to freeLlmHub,
        AiProvider.AI_BRIDGE to aiBridge,
        AiProvider.HF_ROUTER to hfRouter,
        AiProvider.LITELLM_HUB to liteLlmHub,
        AiProvider.SUPA_VIP to supaVip,
        AiProvider.SHANNIAN_VIP to shanNianVip,
        AiProvider.AGNES_CLOUD to agnesCloud,
        AiProvider.ZHIPU_LONGCAT to zhipuLongCat,
        AiProvider.OPENCODE_ZEN_FREE to openCodeZenFree,
        AiProvider.VIREONIX to vireonix,
        AiProvider.COLIBRI_QWEN to colibriQwen,
        AiProvider.GEMMA4_CLUSTER to gemma4Cluster,
        AiProvider.FRONTIER_LAB to frontierLab,
        AiProvider.DEEPAI to deepAi,
        AiProvider.MOTIF to motif,
        AiProvider.CHATJIMMY to chatJimmy,
        AiProvider.VOLC_ARK to volcArk,
        AiProvider.LOGFARE_HUB to logfareHub,
        AiProvider.FREECC to freeCc,
        AiProvider.TOGETHER_FRONTIER to togetherFrontier,
        AiProvider.LIQUID_AI to liquidAi,
        AiProvider.GEMMA4_VL to gemma4Vl,
        AiProvider.BONSAI_CLOUD to bonsaiCloud,
        AiProvider.UNSLOTH_CLOUD to unslothCloud,
        AiProvider.KOBOLD_CLOUD to koboldCloud,
        AiProvider.HF_CLUSTER to hfCluster,
        AiProvider.PHI4_CLOUD to phi4Cloud,
        AiProvider.LLAMACPP_CLOUD to llamaCppCloud,
        AiProvider.OLLAMA_CLOUD to ollamaCloud,
        AiProvider.AI_HORDE to aiHorde,
        AiProvider.APIS_FREE_LLM to apisFreeLlm,
        AiProvider.YQ_CLOUD to yqCloud,
        AiProvider.LONGCAT_WEB to longCatWeb
    )

    operator fun get(provider: AiProvider): List<AiModel> = byProvider[provider].orEmpty()

    val all: List<AiModel> = byProvider.values.flatten()
}
