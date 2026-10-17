package com.codex.stormy.data.ai

import kotlinx.serialization.Serializable

/**
 * Represents an AI model available for use
 */
/**
 * How much the model should think before answering.
 *
 * Only meaningful on models that expose a reasoning budget; the request layer
 * omits the field entirely for the rest rather than sending something they will
 * reject. Three steps, not five: the difference between adjacent levels has to
 * be noticeable or the control is just a dial the user fiddles with.
 */
@Serializable
enum class ReasoningEffort(val label: String, val wireValue: String) {
    /** Answer quickly. Good for edits whose shape is already decided. */
    LOW("Low", "low"),

    /** The default trade-off. */
    BALANCED("Balanced", "medium"),

    /** Think longer. Worth it for debugging and design questions. */
    DEEP("Deep", "high");

    companion object {
        val DEFAULT = BALANCED

        fun fromName(value: String?): ReasoningEffort =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
    }
}

@Serializable
data class AiModel(
    val id: String,
    val name: String,
    val provider: AiProvider,
    val contextLength: Int = 4096,
    val supportsStreaming: Boolean = true,
    val supportsToolCalls: Boolean = true,
    val isThinkingModel: Boolean = false,
    /**
     * Whether this model accepts a reasoning-effort setting.
     *
     * Separate from [isThinkingModel]: a model can reason internally without
     * letting the caller size that budget, and sending the parameter to one
     * that does not accept it is a 400 rather than a graceful ignore on several
     * providers.
     */
    val supportsReasoningEffort: Boolean = false,
    /**
     * Whether the user has this model switched on.
     *
     * Only the model picker and the management screen read it; the request
     * path never does, because a disabled model should not reach it. Carried
     * on the domain model rather than looked up separately so a list of models
     * and their state cannot drift apart between two queries.
     */
    val isEnabled: Boolean = true
)

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
 * On key requirements, verified against the live endpoints in October 2026:
 * only Pollinations currently answers with no credentials. OpenCode Zen's free
 * tier is gated to the OpenCode client itself ("can only be used from within
 * OpenCode"), so it is not a keyless option either. The rest are free to sign
 * up for without billing details, which is a different thing from keyless and
 * is labelled as such in the UI.
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
    val extraHeaders: Map<String, String> = emptyMap()
) {
    POLLINATIONS(
        displayName = "Pollinations",
        baseUrl = "https://text.pollinations.ai/openai",
        apiKeyRequirement = ApiKeyRequirement.NONE,
        keyNote = "No account needed. Anonymous tier, tool calling supported."
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
        displayName = "Kilo Code",
        baseUrl = "https://kilocode.ai/api/openrouter",
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        apiKeyUrl = "https://app.kilocode.ai/api/profile",
        keyNote = "Routes to OpenRouter behind a Kilo account key.",
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

    companion object {
        fun fromString(value: String): AiProvider =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: DEFAULT

        /**
         * Provider used before the user configures anything. Must be one that
         * works with no key, otherwise a fresh install cannot send a message.
         */
        val DEFAULT = POLLINATIONS
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
         * Only used when the capability table has nothing for this id. Keeping
         * one source of truth stops a seed entry from disagreeing with the
         * same model once it is discovered live.
         */
        context: Int,
        tools: Boolean = true,
        thinking: Boolean = false
    ) = AiModel(
        id = id,
        name = name,
        provider = provider,
        contextLength = ModelCapabilities.contextWindowFor(id)
            .takeIf { it != ModelCapabilities.DEFAULT_CONTEXT } ?: context,
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
        model("openai-fast", "GPT-OSS 20B (free, no key)", AiProvider.POLLINATIONS, 32_768)
    )

    val nvidia = listOf(
        model("nvidia/nemotron-3-super-120b-a12b", "Nemotron 3 Super 120B", AiProvider.NVIDIA, 131_072),
        model("nvidia/nemotron-3.5-lightning-30b-a3b", "Nemotron 3.5 Lightning 30B", AiProvider.NVIDIA, 131_072),
        model("openai/gpt-oss-120b", "GPT-OSS 120B", AiProvider.NVIDIA, 131_072),
        model("deepseek-ai/deepseek-v4.1-flash", "DeepSeek V4.1 Flash", AiProvider.NVIDIA, 163_840)
    )

    val agnes = listOf(
        model("agnes-2.5-flash", "Agnes 2.5 Flash", AiProvider.AGNES, 524_288),
        model("agnes-2.0-flash", "Agnes 2.0 Flash", AiProvider.AGNES, 131_072)
    )

    val groq = listOf(
        model("moonshotai/kimi-k2-instruct", "Kimi K2 Instruct", AiProvider.GROQ, 131_072),
        model("openai/gpt-oss-120b", "GPT-OSS 120B", AiProvider.GROQ, 131_072),
        model("qwen/qwen3-32b", "Qwen3 32B", AiProvider.GROQ, 131_072)
    )

    val cerebras = listOf(
        model("qwen-3-coder-480b", "Qwen3 Coder 480B", AiProvider.CEREBRAS, 131_072),
        model("gpt-oss-120b", "GPT-OSS 120B", AiProvider.CEREBRAS, 131_072)
    )

    val mistral = listOf(
        model("devstral-medium-latest", "Devstral Medium", AiProvider.MISTRAL, 131_072),
        model("mistral-large-latest", "Mistral Large", AiProvider.MISTRAL, 131_072)
    )

    val openCodeZen = listOf(
        model("claude-sonnet-5-5", "Claude Sonnet 5.5", AiProvider.OPENCODE_ZEN, 200_000),
        model("gpt-5.4", "GPT-5.4", AiProvider.OPENCODE_ZEN, 400_000),
        model("qwen3.8-max", "Qwen3.8 Max", AiProvider.OPENCODE_ZEN, 262_144)
    )

    /**
     * Providers whose catalogue is fetched and which have no useful seed. An
     * empty list here is correct, not a gap: OpenRouter and Kilo Code both
     * front hundreds of models that change weekly, and Gemini's ids move fast
     * enough that shipping them guarantees stale entries.
     */
    private val fetchedOnly = emptyList<AiModel>()

    private val byProvider: Map<AiProvider, List<AiModel>> = mapOf(
        AiProvider.POLLINATIONS to pollinations,
        AiProvider.GEMINI to fetchedOnly,
        AiProvider.NVIDIA to nvidia,
        AiProvider.OPENROUTER to fetchedOnly,
        AiProvider.OPENCODE_ZEN to openCodeZen,
        AiProvider.AGNES to agnes,
        AiProvider.KILOCODE to fetchedOnly,
        AiProvider.GROQ to groq,
        AiProvider.CEREBRAS to cerebras,
        AiProvider.MISTRAL to mistral,
        AiProvider.CUSTOM to fetchedOnly
    )

    operator fun get(provider: AiProvider): List<AiModel> = byProvider[provider].orEmpty()

    val all: List<AiModel> = byProvider.values.flatten()

    /** The model a fresh install starts on: free, keyless, tool-capable. */
    val default: AiModel = pollinations.first()
}

/**
 * Lookup helpers over the seed catalogue.
 */
object AiModels {

    fun getModelsForProvider(provider: AiProvider): List<AiModel> = SeedModels[provider]

    /**
     * Seed default for a provider, falling back to the keyless default so a
     * caller always gets something sendable.
     */
    fun getDefaultModel(provider: AiProvider): AiModel =
        SeedModels[provider].firstOrNull() ?: SeedModels.default

    fun getAllModels(): List<AiModel> = SeedModels.all

    fun findModelById(modelId: String): AiModel? = SeedModels.all.find { it.id == modelId }

    /** Models worth suggesting for code work, best-first. */
    fun getRecommendedModels(): List<AiModel> = listOf(
        SeedModels.default,
        SeedModels.cerebras.first(),
        SeedModels.groq.first(),
        SeedModels.nvidia.first()
    )
}
