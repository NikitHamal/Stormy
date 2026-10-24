package com.oro.app.data.ai.usage

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Tokens (and, when the provider publishes prices, dollars) one chat has used. */
@Serializable
data class SessionUsage(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    /** Prompt tokens the provider served from its cache (a subset of [promptTokens]). */
    val cachedTokens: Long = 0,
    val requests: Int = 0,
    /** USD; null while every request so far was to a model without published prices. */
    val costUsd: Double? = null,
    /** True when at least one request went to a free model, so "$0" is meaningful. */
    val sawFree: Boolean = false
) {
    val totalTokens: Long get() = promptTokens + completionTokens
    val cacheHitPercent: Int get() = if (promptTokens == 0L) 0 else ((cachedTokens * 100) / promptTokens).toInt()

    /** Add one request's usage priced for [model]. */
    fun plus(model: AiModel, prompt: Int, completion: Int, cached: Int): SessionUsage {
        val inP = model.inputPricePerToken
        val outP = model.outputPricePerToken
        val free = model.provider.worksWithoutKey && (inP ?: 0.0) == 0.0 && (outP ?: 0.0) == 0.0
        val cost = when {
            inP != null && outP != null -> {
                // Cache reads are discounted by every provider that reports
                // them; 10% is Anthropic's and OpenAI's rate, the common case.
                val fresh = (prompt - cached).coerceAtLeast(0)
                fresh * inP + cached * inP * 0.1 + completion * outP
            }
            free -> 0.0
            else -> null
        }
        return copy(
            promptTokens = promptTokens + prompt,
            completionTokens = completionTokens + completion,
            cachedTokens = cachedTokens + cached,
            requests = requests + 1,
            costUsd = if (cost == null) costUsd else (costUsd ?: 0.0) + cost,
            sawFree = sawFree || free
        )
    }

    companion object {
        fun formatTokens(n: Long): String = when {
            n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
            n >= 1_000 -> "%.1fK".format(n / 1_000.0)
            else -> n.toString()
        }

        fun formatCost(usd: Double?): String? = when {
            usd == null -> null
            usd == 0.0 -> "Free"
            usd < 0.01 -> "<$0.01"
            else -> "$" + "%.2f".format(usd)
        }
    }
}

/**
 * Per-session usage persisted in the project (`.oro/usage.json`), so the
 * meter survives restarts and travels with the project. Small, rewritten
 * after each run rather than per request.
 */
class UsageStore(private val projectRoot: File) {
    private val file get() = File(projectRoot, ".oro/usage.json")
    private val serializer = MapSerializer(String.serializer(), SessionUsage.serializer())

    fun load(): MutableMap<String, SessionUsage> = runCatching {
        AiHttp.json.decodeFromString(serializer, file.readText()).toMutableMap()
    }.getOrElse { mutableMapOf() }

    fun save(all: Map<String, SessionUsage>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(AiHttp.json.encodeToString(serializer, all))
        }
    }
}
