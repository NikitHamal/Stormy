package com.oro.app.data.ai.providers

import kotlinx.serialization.Serializable

/** Wire protocol of a user-added provider. */
@Serializable
enum class WireProtocol(val label: String) {
    /** `/chat/completions` + `/models`, Bearer auth. */
    OPENAI("OpenAI"),

    /** Anthropic Messages API: `/v1/messages`, `x-api-key`, `anthropic-version`. */
    ANTHROPIC("Anthropic")
}

/** Which key a provider with several keys uses first. */
@Serializable
enum class KeyRotation(val label: String) {
    /** Always the first healthy key; later keys are backups. */
    FAILOVER("Failover"),

    /** Spread requests across every healthy key in turn. */
    ROUND_ROBIN("Round robin")
}

/**
 * One API key. A provider can hold several: when one is rate limited, out of
 * credits or rejected, the request moves to the next without the user seeing
 * an error (see [KeyPool]).
 */
@Serializable
data class ApiKeyEntry(
    val id: String,
    val key: String,
    val label: String = "",
    val enabled: Boolean = true
) {
    /** `sk-or-…a1B2`: enough to tell keys apart, never enough to use one. */
    val masked: String
        get() {
            val k = key.trim()
            if (k.length <= 8) return "•".repeat(k.length.coerceAtLeast(4))
            return k.take(5) + "…" + k.takeLast(4)
        }
}

/**
 * A provider the user added: any OpenAI- or Anthropic-compatible endpoint.
 *
 * Its models live in the model table like every other provider's, under
 * namespaced ids ([ModelIds]) with provider `CUSTOM`, so the picker, the
 * management screen and favourites need nothing new. Only routing (which base
 * URL, which keys, which protocol) reads this config.
 */
@Serializable
data class CustomProviderConfig(
    val id: String,
    val name: String,
    val protocol: WireProtocol = WireProtocol.OPENAI,
    val baseUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val keys: List<ApiKeyEntry> = emptyList(),
    val rotation: KeyRotation = KeyRotation.FAILOVER,
    /** Default for fetched models; manual models set their own. */
    val supportsTools: Boolean = true,
    /** Sent as max_tokens; required by the Anthropic protocol. */
    val maxOutputTokens: Int? = null,
    val enabled: Boolean = true,
    /** The preset it was created from, for the icon and the "get a key" link. */
    val presetId: String? = null
) {
    val normalizedBaseUrl: String get() = baseUrl.trim().trimEnd('/')
}

/** Everything about providers that is user configuration, persisted as one JSON document. */
@Serializable
data class ProviderSettings(
    val custom: List<CustomProviderConfig> = emptyList(),
    /** Keys for built-in providers, by `AiProvider.name`. */
    val builtInKeys: Map<String, List<ApiKeyEntry>> = emptyMap(),
    val builtInRotation: Map<String, KeyRotation> = emptyMap(),
    /** The preset providers (DeepSeek, Kimi, ...) have been added once; never re-added after the user removes one. */
    val seededPresets: Boolean = false
) {
    fun customById(id: String): CustomProviderConfig? = custom.firstOrNull { it.id == id }
}

/**
 * Model ids for custom providers: `cp:<providerId>:<upstream id>`.
 *
 * Namespaced so two providers can serve the same upstream id (the model table
 * is keyed by id alone), and so a request knows its provider from the model
 * alone. The prefix is stripped before the id goes on the wire, as the
 * community bridges already do with theirs.
 */
object ModelIds {
    private const val PREFIX = "cp:"

    fun of(providerId: String, upstream: String): String = "$PREFIX$providerId:$upstream"

    fun prefixFor(providerId: String): String = "$PREFIX$providerId:"

    fun providerId(modelId: String): String? =
        if (modelId.startsWith(PREFIX)) modelId.removePrefix(PREFIX).substringBefore(':', "").ifEmpty { null } else null

    fun upstream(modelId: String): String =
        if (modelId.startsWith(PREFIX)) modelId.removePrefix(PREFIX).substringAfter(':') else modelId
}

/**
 * The current custom providers, readable from anywhere without a coroutine:
 * model rows only carry an id, and the picker wants the provider's name for
 * the group header. Updated whenever settings are read or written.
 */
object CustomProviderRegistry {
    @Volatile
    var providers: Map<String, CustomProviderConfig> = emptyMap()
        private set

    fun update(settings: ProviderSettings) {
        providers = settings.custom.associateBy { it.id }
    }

    fun nameFor(modelId: String): String? = ModelIds.providerId(modelId)?.let { providers[it]?.name }

    /** The wire protocol of the custom provider serving [modelId], or null when it is not a known custom model. */
    fun protocolFor(modelId: String): WireProtocol? =
        providers[ModelIds.providerId(modelId) ?: ProviderSettingsMigration.LEGACY_CUSTOM_ID]?.protocol
}

/** A starting point in the Add provider sheet. Everything stays editable. */
data class ProviderPreset(
    val id: String,
    val name: String,
    val protocol: WireProtocol,
    val baseUrl: String,
    val keyUrl: String = "",
    val note: String = "",
    val needsKey: Boolean = true,
    val maxOutputTokens: Int? = null
)

object ProviderPresets {
    val all: List<ProviderPreset> = listOf(
        ProviderPreset("openai", "OpenAI", WireProtocol.OPENAI, "https://api.openai.com/v1", "https://platform.openai.com/api-keys"),
        ProviderPreset(
            "anthropic", "Anthropic", WireProtocol.ANTHROPIC, "https://api.anthropic.com/v1",
            "https://console.anthropic.com/settings/keys", "Claude models through the Messages API.", maxOutputTokens = 16_384
        ),
        ProviderPreset("deepseek", "DeepSeek", WireProtocol.OPENAI, "https://api.deepseek.com/v1", "https://platform.deepseek.com/api_keys"),
        ProviderPreset("xai", "xAI", WireProtocol.OPENAI, "https://api.x.ai/v1", "https://console.x.ai"),
        ProviderPreset("moonshot", "Moonshot (Kimi)", WireProtocol.OPENAI, "https://api.moonshot.ai/v1", "https://platform.moonshot.ai/console/api-keys"),
        ProviderPreset("zai", "Z.ai (GLM)", WireProtocol.OPENAI, "https://api.z.ai/api/paas/v4", "https://z.ai/manage-apikey/apikey-list"),
        ProviderPreset("together", "Together AI", WireProtocol.OPENAI, "https://api.together.xyz/v1", "https://api.together.xyz/settings/api-keys"),
        ProviderPreset("fireworks", "Fireworks", WireProtocol.OPENAI, "https://api.fireworks.ai/inference/v1", "https://fireworks.ai/account/api-keys"),
        ProviderPreset("deepinfra", "DeepInfra", WireProtocol.OPENAI, "https://api.deepinfra.com/v1/openai", "https://deepinfra.com/dash/api_keys"),
        ProviderPreset("ollama", "Ollama (local)", WireProtocol.OPENAI, "http://localhost:11434/v1", note = "A model server on this device or your network.", needsKey = false),
        ProviderPreset("lmstudio", "LM Studio (local)", WireProtocol.OPENAI, "http://localhost:1234/v1", note = "A model server on your network.", needsKey = false),
        ProviderPreset("custom-openai", "Custom OpenAI-compatible", WireProtocol.OPENAI, "", note = "Any server that speaks /chat/completions.", needsKey = false),
        ProviderPreset(
            "custom-anthropic", "Custom Anthropic-compatible", WireProtocol.ANTHROPIC, "",
            note = "Any server that speaks the Messages API, e.g. Anthropic-style endpoints of other vendors.",
            maxOutputTokens = 8_192
        )
    )

    fun byId(id: String?): ProviderPreset? = all.firstOrNull { it.id == id }
}
