package com.codex.stormy.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Request

@Serializable
private data class OpenAiModelsResponse(
    val data: List<OpenAiModelInfo> = emptyList()
)

@Serializable
private data class OpenAiModelInfo(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    @SerialName("object")
    val objectType: String? = null,
    @SerialName("owned_by")
    val ownedBy: String? = null,
    val created: Long? = null,
    @SerialName("context_length")
    val contextLength: Int? = null,
    @SerialName("max_context_length")
    val maxContextLength: Int? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null
)

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

                val parsed = json.decodeFromString<OpenAiModelsResponse>(body)
                val models = parsed.data
                    .asSequence()
                    .filter { isValidChatModel(it.id) }
                    .map { mapToAiModel(provider, it) }
                    .distinctBy { it.id }
                    .sortedBy { it.name }
                    .toList()

                Result.success(models)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isValidChatModel(modelId: String): Boolean {
        val lowerId = modelId.lowercase()
        val excluded = listOf(
            "embed", "embedding",
            "whisper", "audio", "speech", "tts", "stt", "transcription",
            "image", "vision-only", "diffusion", "sdxl", "flux", "dall-e", "midjourney",
            "rerank", "classifier", "moderation",
            "vae", "encoder", "decoder"
        )
        return excluded.none { lowerId.contains(it) }
    }

    private fun mapToAiModel(provider: AiProvider, info: OpenAiModelInfo): AiModel {
        val lowerId = info.id.lowercase()
        val isThinkingModel = lowerId.contains("r1") ||
            lowerId.contains("reasoning") ||
            lowerId.contains("think")

        val supportsToolCalls = !isThinkingModel || lowerId.contains("gemini")

        val contextLength = info.maxContextLength
            ?: info.contextLength
            ?: info.maxTokens
            ?: estimateContextLength(info.id)

        return AiModel(
            id = info.id,
            name = info.name?.takeIf { it.isNotBlank() } ?: generateDisplayName(info.id),
            provider = provider,
            contextLength = contextLength,
            supportsStreaming = true,
            supportsToolCalls = supportsToolCalls,
            isThinkingModel = isThinkingModel
        )
    }

    private fun estimateContextLength(modelId: String): Int {
        val lowerId = modelId.lowercase()
        return when {
            lowerId.contains("1m") || lowerId.contains("1000k") -> 1_000_000
            lowerId.contains("256k") -> 262_144
            lowerId.contains("200k") -> 200_000
            lowerId.contains("131k") || lowerId.contains("128k") -> 131_072
            lowerId.contains("64k") -> 65_536
            lowerId.contains("32k") -> 32_768
            lowerId.contains("16k") -> 16_384
            else -> 8_192
        }
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
