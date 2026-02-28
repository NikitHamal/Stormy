package com.codex.stormy.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
private data class GeminiModelsResponse(
    val models: List<GeminiModelInfo> = emptyList()
)

@Serializable
private data class GeminiModelInfo(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val supportedGenerationMethods: List<String> = emptyList()
)

/**
 * Dynamic model discovery for the official Gemini API.
 */
class GeminiModelService {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAvailableModels(apiKey: String): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Gemini API key is required")
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("key", apiKey)
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch Gemini models: ${response.code} ${response.message}")
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val parsed = json.decodeFromString<GeminiModelsResponse>(body)
                val models = parsed.models
                    .asSequence()
                    .filter { isChatCapableGeminiModel(it) }
                    .map { mapToAiModel(it) }
                    .distinctBy { it.id }
                    .sortedBy { it.name }
                    .toList()

                Result.success(models)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isChatCapableGeminiModel(model: GeminiModelInfo): Boolean {
        val id = model.name.removePrefix("models/").lowercase()
        if (!id.startsWith("gemini")) return false
        if (id.contains("embedding")) return false
        if (id.contains("image") || id.contains("veo")) return false

        return model.supportedGenerationMethods.any {
            it.equals("generateContent", ignoreCase = true)
        }
    }

    private fun mapToAiModel(model: GeminiModelInfo): AiModel {
        val id = model.name.removePrefix("models/")
        val lowerId = id.lowercase()
        val isThinkingModel = lowerId.contains("thinking") || lowerId.contains("reasoning")

        val contextLength = model.inputTokenLimit ?: when {
            lowerId.contains("1.5") -> 1_000_000
            lowerId.contains("2.5") -> 1_000_000
            lowerId.contains("128k") -> 131_072
            else -> 32_768
        }

        return AiModel(
            id = id,
            name = model.displayName?.takeIf { it.isNotBlank() } ?: id,
            provider = AiProvider.GEMINI,
            contextLength = contextLength,
            supportsStreaming = true,
            supportsToolCalls = true,
            isThinkingModel = isThinkingModel
        )
    }
}
