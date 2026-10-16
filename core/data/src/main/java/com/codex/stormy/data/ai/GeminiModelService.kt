package com.codex.stormy.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

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
    private val json get() = AiHttp.json


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

            AiHttp.client.newCall(request).execute().use { response ->
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
                    // Newest first. This was sortedBy { it.name }, i.e.
                    // alphabetical, which puts "Gemini 1.5 Flash" above
                    // "Gemini 3.8 Flash" -- so the top of the picker was always
                    // the oldest models and the current ones were buried far
                    // enough down to look absent.
                    .sortedWith(GeminiModelOrdering)
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

        // Non-conversational variants on the same family. The old filter caught
        // only embedding/image/veo, so text-to-speech, the Live audio models,
        // and the attributed-QA endpoint all appeared in a chat model picker.
        val excluded = listOf(
            "embedding", "image", "veo", "tts", "-live", "audio",
            "aqa", "learnlm", "gemma", "computer-use"
        )
        if (excluded.any { id.contains(it) }) return false

        // Accept either method. Requiring generateContent exactly dropped any
        // model that advertises only streamGenerateContent -- which newer
        // releases do, and which this app streams from anyway. That is the
        // other half of why current models looked missing.
        return model.supportedGenerationMethods.any { method ->
            method.equals("generateContent", ignoreCase = true) ||
                method.equals("streamGenerateContent", ignoreCase = true)
        }
    }

    /**
     * Newest-first ordering.
     *
     * Sorts on the numeric version parsed out of the id, descending, then puts
     * stable ids above dated snapshots and previews within a version -- a
     * picker whose first entry is "gemini-2.5-flash-preview-04-17" reads as
     * broken even when the list is complete.
     */
    private object GeminiModelOrdering : Comparator<AiModel> {
        override fun compare(a: AiModel, b: AiModel): Int {
            val versionComparison = versionOf(b.id).compareTo(versionOf(a.id))
            if (versionComparison != 0) return versionComparison

            val stabilityComparison = stabilityRank(a.id).compareTo(stabilityRank(b.id))
            if (stabilityComparison != 0) return stabilityComparison

            return a.id.compareTo(b.id)
        }

        /** Major.minor as a single number, so 3.8 ranks above 3.5 and above 2.5. */
        private fun versionOf(id: String): Double =
            Regex("""(\d+)(?:\.(\d+))?""").find(id)?.let { match ->
                val major = match.groupValues[1].toDoubleOrNull() ?: 0.0
                val minor = match.groupValues[2].toDoubleOrNull() ?: 0.0
                major + minor / 100.0
            } ?: 0.0

        private fun stabilityRank(id: String): Int = when {
            id.contains("preview") -> 3
            id.contains("exp") -> 2
            Regex("""-\d{3,}$""").containsMatchIn(id) -> 1  // dated snapshot
            else -> 0
        }
    }

    private fun mapToAiModel(model: GeminiModelInfo): AiModel {
        val id = model.name.removePrefix("models/")
        val lowerId = id.lowercase()
        val isThinkingModel = lowerId.contains("thinking") ||
            lowerId.contains("reasoning") ||
            // Gemini 2.5 and later reason by default without saying so in the
            // id, so matching on the word alone under-reports it.
            (versionNumber(lowerId) >= 2.5 && !lowerId.contains("flash-lite"))

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
            isThinkingModel = isThinkingModel,
            // Gemini exposes a thinking budget from 2.5 onwards, except on the
            // lite tiers.
            supportsReasoningEffort = versionNumber(lowerId) >= 2.5 &&
                !lowerId.contains("flash-lite")
        )
    }

    private fun versionNumber(id: String): Double =
        Regex("""(\d+)(?:\.(\d+))?""").find(id)?.let { match ->
            val major = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val minor = match.groupValues[2].toDoubleOrNull() ?: 0.0
            major + minor / 10.0
        } ?: 0.0
}
