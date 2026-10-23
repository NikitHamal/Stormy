package com.oro.app.data.ai

import com.oro.app.data.ai.providers.ModelIds

/** One step on a model's reasoning scale: [wire] is what the provider expects, [label] what it calls it. */
data class ReasoningLevel(val wire: String, val label: String)

/** How a chosen level reaches the provider. */
enum class ReasoningWire {
    /** Top-level `reasoning_effort` in Chat Completions (OpenAI, Gemini, xAI, Groq, Cerebras, Inception, OpenRouter). */
    REASONING_EFFORT,

    /** Anthropic Messages `output_config.effort` (custom providers speaking Anthropic's protocol). */
    ANTHROPIC_EFFORT,

    /** A community bridge maps the level onto its own request (Mercury's guest chat, Qwen Chat). */
    CLIENT
}

/**
 * The reasoning control a model exposes, exactly as its provider defines it.
 *
 * [initial] is what Oro selects before the user picks: the provider's own
 * default, except where Oro deliberately chooses another one ([pinned]).
 */
data class ReasoningControl(
    /** Picker title: "Reasoning effort", or "Thinking" for an on/off switch. */
    val title: String,
    val levels: List<ReasoningLevel>,
    val initial: String,
    val wire: ReasoningWire,
    /** Oro's [initial] differs from the provider's default on purpose; an old global choice does not override it. */
    val pinned: Boolean = false
) {
    fun has(level: String?): Boolean = level != null && levels.any { it.wire == level }

    fun label(level: String?): String = levels.firstOrNull { it.wire == level }?.label ?: level.orEmpty()
}

/**
 * Which reasoning control each model has, per provider, with each
 * provider's own level names and wire values (provider docs, October 2026;
 * sources in docs/research/models.md).
 *
 * The control depends on the host as much as the model: the same gpt-oss
 * takes `reasoning_effort` on Groq and Cerebras but nothing documented on
 * OVHcloud or LLM7, so a model only gets a control on hosts where it is
 * documented. Where a family's exact scale is not documented the model gets
 * no control at all: a wrong value is a 400 on every request, which is worse
 * than no control.
 */
object ReasoningControls {

    /** Map key that carries the old app-wide Low/Balanced/Deep choice. */
    const val LEGACY_KEY = "*"

    private const val EFFORT = "Reasoning effort"

    private val LABELS = mapOf(
        "none" to "None", "minimal" to "Minimal", "low" to "Low", "medium" to "Medium", "high" to "High",
        "xhigh" to "XHigh", "max" to "Max", "instant" to "Instant", "default" to "Default",
        "off" to "Off", "on" to "On"
    )

    private fun levels(vararg wire: String) = wire.map { ReasoningLevel(it, LABELS[it] ?: it.replaceFirstChar(Char::uppercase)) }

    private fun effort(initial: String, vararg wire: String, pinned: Boolean = false) =
        ReasoningControl(EFFORT, levels(*wire), initial, ReasoningWire.REASONING_EFFORT, pinned)

    /** The reasoning control of [model], or null when it has none. */
    fun forModel(model: AiModel): ReasoningControl? = forModel(model.provider, model.id, anthropicWire = false)

    /**
     * Same, for a model served by a custom provider that speaks Anthropic's
     * Messages protocol: Claude's effort without extended thinking, so tool
     * turns never need thinking blocks sent back.
     */
    fun forAnthropicCustom(modelId: String): ReasoningControl? = forModel(AiProvider.CUSTOM, modelId, anthropicWire = true)

    private fun forModel(provider: AiProvider, modelId: String, anthropicWire: Boolean): ReasoningControl? {
        val raw = ModelIds.upstream(modelId).lowercase()
        val id = if (provider.isCommunity) raw.substringAfter(':') else familyId(raw)
        return when (provider) {
            AiProvider.MERCURY -> if (id.startsWith("mercury-3")) {
                // The guest chat's own scale. Oro asks for High by default: Mercury
                // is fast enough that its deepest setting still answers in seconds.
                ReasoningControl(EFFORT, levels("instant", "low", "medium", "high"), "high", ReasoningWire.CLIENT, pinned = true)
            } else null
            // Qwen Chat's thinking switch; Qwen3.8 models always think there.
            AiProvider.QWEN_WEB -> if (id.startsWith("qwen3.7")) {
                ReasoningControl("Thinking", levels("off", "on"), "off", ReasoningWire.CLIENT)
            } else null
            AiProvider.INCEPTION -> mercury(id)
            // Tencent's Hy3 demo: its own think_level scale, the Space's default High.
            AiProvider.HY3 -> ReasoningControl(
                "Think level",
                listOf(ReasoningLevel("no_think", "No think"), ReasoningLevel("low", "Low"), ReasoningLevel("high", "High")),
                "high",
                ReasoningWire.CLIENT
            )
            // On/off switches the demos expose (both default to on).
            AiProvider.LAGUNA -> ReasoningControl("Reasoning", levels("off", "on"), "on", ReasoningWire.CLIENT)
            AiProvider.MINICPM -> ReasoningControl("Thinking", levels("off", "on"), "on", ReasoningWire.CLIENT)
            AiProvider.GROQ -> when {
                id.startsWith("gpt-oss") -> effort("medium", "low", "medium", "high")
                id.startsWith("qwen3.8") -> effort("default", "none", "default", "low", "medium", "high")
                else -> null
            }
            AiProvider.CEREBRAS -> if (id.startsWith("gpt-oss")) effort("medium", "low", "medium", "high") else null
            AiProvider.GEMINI -> gemini(id)
            AiProvider.OPENCODE_ZEN -> openAi(id)
            // OpenRouter-style gateways take OpenRouter's own reasoning_effort
            // and translate it for each upstream (Claude's effort included).
            AiProvider.OPENROUTER, AiProvider.KILOCODE ->
                openAi(id) ?: gemini(id) ?: grok(id) ?: claude(id, gateway = true) ?: when {
                    id.startsWith("gpt-oss") -> effort("medium", "low", "medium", "high")
                    id.startsWith("deepseek-v4") -> effort("high", "none", "low", "high")
                    else -> null
                }
            AiProvider.CUSTOM ->
                if (anthropicWire) claude(id, gateway = false)?.copy(wire = ReasoningWire.ANTHROPIC_EFFORT)
                else openAi(id) ?: gemini(id) ?: grok(id) ?: mercury(id) ?: when {
                    id.startsWith("gpt-oss") -> effort("medium", "low", "medium", "high")
                    // DeepSeek's and Z.ai's own APIs: thinking stays on, the effort sizes it.
                    id.startsWith("deepseek-v4") -> effort("high", "low", "high", "max")
                    id.startsWith("glm-5.3") -> effort("high", "low", "high", "max")
                    else -> null
                }
            else -> null
        }
    }

    /**
     * The upstream family id: lowercase, gateway owner and variant suffix
     * removed ("openai/gpt-5.4:free" -> "gpt-5.4", "models/gemini-3-flash" ->
     * "gemini-3-flash"), Claude's dotted versions as Anthropic writes them.
     */
    internal fun familyId(raw: String): String {
        var id = raw.lowercase().substringAfterLast('/').substringBefore(':')
        if (id.startsWith("claude")) id = id.replace('.', '-')
        return id
    }

    /** Inception: instant, low, medium, high on every Mercury model; Mercury 3 at High in Oro. */
    private fun mercury(id: String): ReasoningControl? = when {
        id.startsWith("mercury-3") -> effort("high", "instant", "low", "medium", "high", pinned = true)
        id.startsWith("mercury-2") -> effort("medium", "instant", "low", "medium", "high")
        else -> null
    }

    private val GPT = Regex("""^gpt-(\d+)(?:\.(\d+))?(.*)$""")

    /** OpenAI's GPT-5.x, GPT-6 and o-series, per model page. */
    private fun openAi(id: String): ReasoningControl? {
        if (Regex("""^o[34](-mini)?(-\d.*)?$""").matches(id) || Regex("""^o1(-\d.*)?$""").matches(id)) {
            return effort("medium", "low", "medium", "high")
        }
        val m = GPT.matchEntire(id) ?: return null
        val major = m.groupValues[1].toInt()
        val minor = m.groupValues[2].toIntOrNull() ?: 0
        val rest = m.groupValues[3]
        if (major < 5 || rest.contains("chat") || rest.contains("audio") || rest.contains("realtime") ||
            rest.contains("image") || rest.contains("search")
        ) return null
        return when {
            rest.contains("-pro") -> if (major == 5 && minor >= 2) effort("medium", "medium", "high", "xhigh") else null
            rest.contains("codex") -> effort("medium", "low", "medium", "high")
            major == 5 && minor == 0 -> effort("medium", "minimal", "low", "medium", "high")
            major == 5 && minor == 1 -> effort("none", "none", "low", "medium", "high")
            major == 5 && minor in 2..4 -> effort("none", "none", "low", "medium", "high", "xhigh")
            major == 5 && minor == 5 -> effort("medium", "none", "low", "medium", "high", "xhigh")
            // Newer than the documented set: the three levels every OpenAI reasoning model accepts.
            else -> effort("medium", "low", "medium", "high")
        }
    }

    private val GEMINI = Regex("""^gemini-(\d+(?:\.\d+)?)-(.*)$""")

    /** Gemini through its OpenAI-compatible endpoint; reasoning_effort maps to thinking_level / budget. */
    private fun gemini(id: String): ReasoningControl? {
        val m = GEMINI.matchEntire(id) ?: return null
        val version = m.groupValues[1].toDoubleOrNull() ?: return null
        val rest = m.groupValues[2]
        if (rest.contains("image") || rest.contains("tts") || rest.contains("live") || rest.contains("embedding")) return null
        val flash = rest.contains("flash")
        return when {
            version >= 3 && flash -> effort("high", "minimal", "low", "medium", "high")
            version >= 3 -> effort("high", "low", "medium", "high")
            version >= 2.5 && rest.contains("flash-lite") -> effort("none", "none", "low", "medium", "high")
            version >= 2.5 && flash -> effort("medium", "none", "low", "medium", "high")
            version >= 2.5 -> effort("medium", "low", "medium", "high")
            else -> null
        }
    }

    private val GROK = Regex("""^grok-(\d+)(?:\.(\d+))?(.*)$""")

    /** xAI: the scale differs per Grok release. */
    private fun grok(id: String): ReasoningControl? {
        val m = GROK.matchEntire(id) ?: return null
        val major = m.groupValues[1].toInt()
        val minor = m.groupValues[2].toIntOrNull() ?: 0
        val rest = m.groupValues[3]
        return when {
            major == 3 && rest.startsWith("-mini") -> effort("low", "low", "high")
            major == 4 && minor == 3 -> effort("low", "none", "low", "medium", "high")
            major == 4 && minor == 5 -> effort("high", "low", "medium", "high")
            major == 4 && minor >= 6 -> effort("high", "low", "medium", "high", "xhigh")
            else -> null
        }
    }

    private val CLAUDE = Regex("""^claude-(opus|sonnet|haiku|fable|mythos)-(\d+)(?:-(\d))?(?:-.*)?$""")

    /**
     * Claude's effort (low…max). Through a gateway: up to XHigh where the
     * model has it (OpenRouter's reasoning_effort has no Max). Over Anthropic's
     * own protocol Oro sends effort without extended thinking, which caps it at
     * High but keeps tool turns free of thinking blocks.
     */
    private fun claude(id: String, gateway: Boolean): ReasoningControl? {
        val m = CLAUDE.matchEntire(id) ?: return null
        val tier = m.groupValues[1]
        val major = m.groupValues[2].toInt()
        val minor = m.groupValues[3].toIntOrNull() ?: 0
        val version = major * 10 + minor
        if (version < 46) return null
        val initial = if ((tier == "opus" || tier == "haiku") && version == 55) "medium" else "high"
        val xhigh = gateway && (version >= 47) && !(tier == "sonnet" && version == 46)
        return if (xhigh) effort(initial, "low", "medium", "high", "xhigh") else effort(initial, "low", "medium", "high")
    }

    /**
     * The level to send for [model]: the user's saved choice, else an old
     * app-wide choice the model's scale has (never over a pinned default),
     * else the control's initial level. Null when the model has no control.
     */
    fun chosen(model: AiModel, saved: Map<String, String>): String? = chosen(forModel(model), model.id, saved)

    fun chosen(control: ReasoningControl?, modelId: String, saved: Map<String, String>): String? {
        control ?: return null
        saved[modelId]?.takeIf { control.has(it) }?.let { return it }
        if (!control.pinned) saved[LEGACY_KEY]?.takeIf { control.has(it) }?.let { return it }
        return control.initial
    }

    /** The old global setting (LOW / BALANCED / DEEP) as a wire value; Balanced was the default, so it carries nothing. */
    fun legacyLevel(name: String?): String? = when (name?.uppercase()) {
        "LOW" -> "low"
        "DEEP" -> "high"
        else -> null
    }

    /**
     * Whether a request must leave `temperature` out: OpenAI's reasoning
     * models reject anything but the default on OpenAI's own API, which Zen
     * and custom endpoints pass through. Gateways drop it themselves.
     */
    fun omitsTemperature(model: AiModel): Boolean {
        if (model.provider != AiProvider.OPENCODE_ZEN && model.provider != AiProvider.CUSTOM) return false
        val id = familyId(ModelIds.upstream(model.id))
        return openAi(id) != null
    }
}
