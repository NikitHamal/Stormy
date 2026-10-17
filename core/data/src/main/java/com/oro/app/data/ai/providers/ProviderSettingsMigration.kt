package com.oro.app.data.ai.providers

/**
 * Folds the pre-multi-key preferences into [ProviderSettings] on read, so
 * nobody loses a key or their custom server on upgrade.
 *
 * Applied on every read rather than once: it is idempotent (a provider that
 * already has a key list is left alone), costs a map lookup, and means there
 * is no "migrated" flag that a crash between two writes could leave wrong.
 */
object ProviderSettingsMigration {

    /** Id of the custom provider rebuilt from the old single base URL. */
    const val LEGACY_CUSTOM_ID = "legacy"
    const val CUSTOM_PROVIDER_NAME = "CUSTOM"

    fun withLegacy(
        stored: ProviderSettings,
        legacyKeys: Map<String, String>,
        legacyCustomBaseUrl: String
    ): ProviderSettings {
        val keys = stored.builtInKeys.toMutableMap()
        legacyKeys.forEach { (provider, key) ->
            if (provider != CUSTOM_PROVIDER_NAME && key.isNotBlank() && provider !in keys) {
                keys[provider] = listOf(ApiKeyEntry(id = "legacy-${provider.lowercase()}", key = key.trim()))
            }
        }
        var custom = stored.custom
        if (legacyCustomBaseUrl.isNotBlank() && custom.none { it.id == LEGACY_CUSTOM_ID }) {
            val key = legacyKeys[CUSTOM_PROVIDER_NAME].orEmpty().trim()
            custom = listOf(
                CustomProviderConfig(
                    id = LEGACY_CUSTOM_ID,
                    name = "Custom server",
                    protocol = WireProtocol.OPENAI,
                    baseUrl = legacyCustomBaseUrl.trim(),
                    keys = if (key.isNotEmpty()) listOf(ApiKeyEntry("legacy-custom", key)) else emptyList(),
                    presetId = "custom-openai"
                )
            ) + custom
        }
        // Ship the well-known vendors as ready-made providers (no key yet), so
        // "Add provider" is only for servers Oro does not know.
        var seeded = stored.seededPresets
        if (!seeded) {
            val have = custom.mapNotNull { it.presetId }.toSet()
            custom = custom + ProviderPresets.all
                .filter { !it.id.startsWith("custom-") && it.id !in have }
                .map { p ->
                    CustomProviderConfig(
                        id = "preset-${p.id}", name = p.name, protocol = p.protocol, baseUrl = p.baseUrl,
                        maxOutputTokens = p.maxOutputTokens, presetId = p.id
                    )
                }
            seeded = true
        }
        return if (keys == stored.builtInKeys && custom === stored.custom && seeded == stored.seededPresets) stored
        else stored.copy(builtInKeys = keys, custom = custom, seededPresets = seeded)
    }
}
