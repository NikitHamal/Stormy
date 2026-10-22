package com.oro.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request

@Serializable
private data class OpenAiModelsResponse(
    val data: List<OpenAiModelInfo> = emptyList()
)

/**
 * One `/models` entry. Every context field seen in the wild is accepted,
 * because providers disagree on the name and shape:
 *  - `context_length` (OpenRouter, Kilo, OVH, Pollinations gen),
 *  - `top_provider.context_length` (OpenRouter-style gateways),
 *  - `context_window` as a number (Groq) or `{ "tokens": N }` (LLM7),
 *  - `max_model_len` (vLLM), `max_context_length`, `input_token_limit`.
 * `max_tokens` is deliberately *not* read as a window: on most gateways it is
 * the output cap, and treating it as the window is how a 1M model ends up
 * metered as 4k.
 */
@Serializable
internal data class OpenAiModelInfo(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    @SerialName("object")
    val objectType: String? = null,
    @SerialName("owned_by")
    val ownedBy: String? = null,
    val created: Long? = null,
    @SerialName("context_length")
    val contextLength: Long? = null,
    @SerialName("max_context_length")
    val maxContextLength: Long? = null,
    @SerialName("max_model_len")
    val maxModelLen: Long? = null,
    @SerialName("input_token_limit")
    val inputTokenLimit: Long? = null,
    @SerialName("context_window")
    val contextWindow: JsonElement? = null,
    @SerialName("top_provider")
    val topProvider: TopProvider? = null,
    @SerialName("supported_parameters")
    val supportedParameters: List<String>? = null,
    val pricing: Pricing? = null,
    val isFree: Boolean? = null,
    /** Pollinations: `"tools": true`. */
    val tools: Boolean? = null,
    /** LLM7: true for models that need a token (paid usage); the rest work anonymously. */
    @SerialName("usage_based_only")
    val usageBasedOnly: Boolean? = null,
    /** LLM7: "chat", "image", "video", ... */
    @SerialName("model_type")
    val modelType: String? = null,
    @SerialName("tools_calling")
    val toolsCalling: Boolean? = null,
    /** LLM7: "pro" models need a balance even with a token; "turbo" ones are free. */
    val tier: String? = null,
    /** Inception: `["chat.completions"]`, or `["decisions"]` for a model chat cannot use. */
    @SerialName("supported_endpoints")
    val supportedEndpoints: List<String>? = null
) {
    @Serializable
    internal data class TopProvider(
        @SerialName("context_length")
        val contextLength: Long? = null
    )

    @Serializable
    internal data class Pricing(
        val prompt: String? = null,
        val completion: String? = null
    )

    /** Largest plausible window the provider states, or null. */
    fun reportedContext(): Int? {
        val windowField = when (val cw = contextWindow) {
            is JsonPrimitive -> cw.longOrNull
            is JsonObject -> (cw["tokens"] as? JsonPrimitive)?.longOrNull
            else -> null
        }
        return listOfNotNull(
            contextLength, maxContextLength, maxModelLen, inputTokenLimit,
            topProvider?.contextLength, windowField
        )
            .filter { it >= ModelCapabilities.MIN_PLAUSIBLE_REPORTED }
            .maxOrNull()
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt()
    }

    /** Free on a gateway that also sells paid models. */
    val isFreeModel: Boolean
        get() = isFree == true ||
            id.endsWith(":free") || id.endsWith("-free") || id.endsWith("/free") ||
            (pricing?.prompt?.toDoubleOrNull() == 0.0 &&
                pricing.completion?.toDoubleOrNull() == 0.0)

    /** Null when the provider does not say. */
    val reportsToolSupport: Boolean?
        get() = supportedParameters?.contains("tools") ?: tools ?: toolsCalling
}

/**
 * Which model families accept a reasoning-effort parameter.
 *
 * Matched on the id rather than asked for, because the OpenAI-compatible
 * `/models` response carries no capability field -- it returns ids and little
 * else. Being wrong in the permissive direction costs a 400 on every request,
 * so the list is deliberately restricted to families known to accept it.
 */
object ReasoningEffortSupport {

    private val patterns = listOf(
        Regex("""^o[1-9]"""),                 // OpenAI o-series
        Regex("""gpt-5"""),
        Regex("""gpt-6"""),
        Regex("""codex"""),
        Regex("""gemini-[3-9]"""),            // Gemini 3+ exposes a budget
        Regex("""gemini-2\.5"""),
        Regex("""claude-(opus|sonnet)-[4-9]"""),
        Regex("""grok-[4-9]"""),
        Regex("""deepseek-(r|v[4-9])"""),
        Regex("""qwen3"""),
        Regex("""nemotron"""),
        Regex("""glm-[5-9]"""),
        Regex("""minimax-m"""),
        Regex("""^mercury-[3-9]""")            // Inception: instant/low/medium/high
    )

    fun supports(modelId: String): Boolean {
        val id = modelId.lowercase()
        // Lite/mini/flash-lite tiers in these families generally drop the
        // budget even though the family name matches.
        if (id.contains("flash-lite") || id.contains("-nano")) return false
        return patterns.any { it.containsMatchIn(id) }
    }
}

/**
 * Generic model discovery for providers exposing OpenAI-compatible `/models`.
 */
class OpenAiCompatibleModelService {

    private val json get() = AiHttp.json

    suspend fun fetchAvailableModels(
        provider: AiProvider,
        apiKey: String,
        extraHeaders: Map<String, String> = emptyMap(),
        /** Only used by [AiProvider.CUSTOM]. */
        baseUrlOverride: String = ""
    ): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        if (provider.requiresApiKey && apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("${provider.displayName} API key is required")
            )
        }

        try {
            val requestBuilder = Request.Builder()
                .url(
                    "${(baseUrlOverride.takeIf { it.isNotBlank() } ?: provider.baseUrl)
                        .trimEnd('/')}/models"
                )
                .get()

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            for ((header, value) in provider.extraHeaders + extraHeaders) {
                requestBuilder.addHeader(header, value)
            }

            AiHttp.client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(
                            "Failed to fetch ${provider.displayName} models: " +
                                "${response.code} ${response.message}"
                        )
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                Result.success(parseModels(provider, body, hasKey = apiKey.isNotBlank()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parse a `/models` body. Pure, so the provider quirks are unit-testable.
     *
     * On a gateway that also sells paid models (Kilo, OpenRouter), calling
     * without a key only lists the free ones: the others would just 401.
     */
    internal fun parseModels(provider: AiProvider, body: String, hasKey: Boolean): List<AiModel> {
        val parsed = json.decodeFromString<OpenAiModelsResponse>(body)
        val keylessGateway = !hasKey && provider == AiProvider.KILOCODE
        // Without a token LLM7 serves only its anonymous tier; the rest answer 401.
        val llm7Anonymous = !hasKey && provider == AiProvider.LLM7
        return parsed.data
            .asSequence()
            .filter { isValidChatModel(it.id) }
            .filter { it.modelType == null || it.modelType == "chat" }
            .filter { it.supportedEndpoints == null || "chat.completions" in it.supportedEndpoints }
            .filter { !keylessGateway || it.isFreeModel }
            .filter { !llm7Anonymous || (it.usageBasedOnly != true && it.tier != "pro") }
            .map { mapToAiModel(provider, it) }
            .distinctBy { it.id }
            .sortedBy { it.name }
            .toList()
    }

    private fun isValidChatModel(modelId: String): Boolean {
        val lowerId = modelId.lowercase()
        val excluded = listOf(
            "embed", "embedding", "bge-",
            "whisper", "audio", "speech", "tts", "stt", "transcription", "lyria",
            "image", "vision-only", "diffusion", "sdxl", "flux", "dall-e", "midjourney",
            "rerank", "classifier", "moderation", "guard", "content-safety",
            "vae", "encoder", "decoder"
        )
        return excluded.none { lowerId.contains(it) }
    }

    private fun mapToAiModel(provider: AiProvider, info: OpenAiModelInfo): AiModel {
        val lowerId = info.id.lowercase()
        val isThinkingModel = lowerId.contains("r1") ||
            lowerId.contains("reasoning") ||
            lowerId.contains("think")

        // Trust the provider when it says; otherwise assume tools work. The
        // harness falls back to text-based tool calls when they do not, which
        // is far better than hiding the tools from a model that has them.
        val supportsToolCalls = info.reportsToolSupport ?: true

        val reported = info.reportedContext()

        return AiModel(
            id = info.id,
            name = info.name?.takeIf { it.isNotBlank() } ?: generateDisplayName(info.id),
            provider = provider,
            contextLength = ModelCapabilities.resolveContextLength(info.id, reported),
            reportedContextLength = reported,
            supportsStreaming = true,
            supportsToolCalls = supportsToolCalls,
            supportsReasoningEffort = ReasoningEffortSupport.supports(info.id),
            isThinkingModel = isThinkingModel,
            inputPricePerToken = info.pricing?.prompt?.toDoubleOrNull()?.takeIf { it >= 0 },
            outputPricePerToken = info.pricing?.completion?.toDoubleOrNull()?.takeIf { it >= 0 }
        )
    }

    private fun generateDisplayName(modelId: String): String {
        val cleaned = modelId
            .substringAfterLast("/")
            .substringBefore(":")
            .replace("_", " ")
            .replace("-", " ")

        return cleaned
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                if (word.length <= 3) {
                    word.uppercase()
                } else {
                    word.replaceFirstChar { it.uppercase() }
                }
            }
    }
}
