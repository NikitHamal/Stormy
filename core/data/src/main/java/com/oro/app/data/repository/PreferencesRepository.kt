package com.oro.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.ReasoningEffort
import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.providers.ApiKeyEntry
import com.oro.app.data.ai.providers.CustomProviderRegistry
import com.oro.app.data.ai.providers.ProviderSettings
import com.oro.app.data.ai.providers.ProviderSettingsMigration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "oro_preferences")

class PreferencesRepository(private val context: Context) {

    private object PreferenceKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_NUMBERS = booleanPreferencesKey("line_numbers")
        val WORD_WRAP = booleanPreferencesKey("word_wrap")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        // CODE_AUTOCOMPLETION removed - feature disabled

        // API keys. One preference per provider, derived from the enum name so
        // that adding a provider needs no change here. The derived names match
        // the previously hard-coded ones ("deepinfra_api_key", "gemini_api_key",
        // "nvidia_api_key", "openrouter_api_key"), so existing installs keep
        // their saved keys.
        val API_KEY = stringPreferencesKey("api_key")
        fun apiKey(provider: AiProvider) =
            stringPreferencesKey("${provider.name.lowercase()}_api_key")

        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        /** [ProviderSettings] as JSON: custom providers and multi-key lists. */
        val PROVIDER_SETTINGS = stringPreferencesKey("provider_settings_v1")
        /** MCP servers as JSON ([com.oro.app.data.ai.mcp.McpSettings]). */
        val MCP_SETTINGS = stringPreferencesKey("mcp_servers_v1")
        val NETLIFY_TOKEN = stringPreferencesKey("netlify_token")
        val AUTONOMY = stringPreferencesKey("autonomy_mode")
        val DICTATION_ENGINE = stringPreferencesKey("dictation_engine")
        val SELF_CHECK = booleanPreferencesKey("self_check")
        val RICH_UI = booleanPreferencesKey("rich_ui")
        val REASONING_EFFORT = stringPreferencesKey("reasoning_effort")

        // AI Provider and Model settings
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
        val HAS_SET_DEFAULT_MODEL = booleanPreferencesKey("has_set_default_model")

        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        // Beta Features
        val DEBUG_LOGS_ENABLED = booleanPreferencesKey("debug_logs_enabled")

        // Performance
        val LITE_MODE = stringPreferencesKey("lite_mode")
    }

    // distinctUntilChanged: every DataStore edit re-emits the whole
    // Preferences object, so without it an unrelated write (an API key, the
    // font size slider) would push a "new" theme through the activity root.
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        try {
            ThemeMode.valueOf(preferences[PreferenceKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }.distinctUntilChanged()

    /**
     * Lite mode: trades decorative effects (backdrop blur, idle loops, springy
     * motion) for smoothness. [LiteMode.AUTO] lets the app decide from the
     * device's RAM class; see `isLowEndDevice` in the design system.
     */
    val liteMode: Flow<LiteMode> = context.dataStore.data.map { preferences ->
        LiteMode.fromName(preferences[PreferenceKeys.LITE_MODE])
    }.distinctUntilChanged()

    suspend fun setLiteMode(mode: LiteMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.LITE_MODE] = mode.name
        }
    }

    val dynamicColors: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DYNAMIC_COLORS] ?: false
    }

    val fontSize: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.FONT_SIZE] ?: 14f
    }

    val lineNumbers: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.LINE_NUMBERS] ?: true
    }

    val wordWrap: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.WORD_WRAP] ?: true
    }

    val autoSave: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.AUTO_SAVE] ?: true
    }

    /** Pre-per-provider key, kept only so existing installs can be migrated. */
    val apiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.API_KEY] ?: ""
    }

    /** Key for a single provider; see [apiKeys] for all of them at once. */
    fun apiKeyFor(provider: AiProvider): Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.apiKey(provider)] ?: ""
    }

    // AI Provider and Model
    val aiProvider: Flow<String> = context.dataStore.data.map { preferences ->
        // Falls back to the provider that needs no key, so a fresh install can
        // send its first message without visiting Settings.
        preferences[PreferenceKeys.AI_PROVIDER] ?: AiProvider.DEFAULT.name
    }

    val aiModel: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.AI_MODEL] ?: ""
    }

    val defaultModelId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DEFAULT_MODEL_ID] ?: ""
    }

    val hasSetDefaultModel: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.HAS_SET_DEFAULT_MODEL] ?: false
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DYNAMIC_COLORS] = enabled
        }
    }

    suspend fun setFontSize(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.FONT_SIZE] = size.coerceIn(10f, 24f)
        }
    }

    suspend fun setLineNumbers(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.LINE_NUMBERS] = enabled
        }
    }

    suspend fun setWordWrap(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.WORD_WRAP] = enabled
        }
    }

    suspend fun setAutoSave(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.AUTO_SAVE] = enabled
        }
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.API_KEY] = key
        }
    }

    suspend fun setApiKeyForProvider(provider: AiProvider, key: String) {
        // Single-key callers edit the first key and leave any others alone.
        updateProviderSettings { settings ->
            val list = settings.builtInKeys[provider.name].orEmpty()
            val trimmed = key.trim()
            val next = when {
                list.isEmpty() && trimmed.isEmpty() -> list
                list.isEmpty() -> listOf(ApiKeyEntry(java.util.UUID.randomUUID().toString(), trimmed))
                trimmed.isEmpty() -> list.drop(1)
                else -> listOf(list[0].copy(key = trimmed)) + list.drop(1)
            }
            settings.copy(builtInKeys = settings.builtInKeys + (provider.name to next))
        }
    }

    suspend fun setCustomBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.CUSTOM_BASE_URL] = url.trim()
        }
    }


    suspend fun setAiProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.AI_PROVIDER] = provider
        }
    }

    suspend fun setAiModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.AI_MODEL] = model
        }
    }

    suspend fun setDefaultModelId(modelId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_MODEL_ID] = modelId
            preferences[PreferenceKeys.HAS_SET_DEFAULT_MODEL] = true
        }
    }

    suspend fun clearDefaultModel() {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_MODEL_ID] = ""
            preferences[PreferenceKeys.HAS_SET_DEFAULT_MODEL] = false
        }
    }

    /**
     * The first usable key of every provider, for code that only needs "is
     * there a key" or one key to try. Requests rotate through every key via
     * [providerSettings].
     */
    val apiKeys: Flow<Map<AiProvider, String>> = context.dataStore.data.map { preferences ->
        val settings = readProviderSettings(preferences)
        AiProvider.entries.associateWith { provider ->
            // The pre-per-provider "api_key" belonged to DeepInfra, which has
            // been removed; there is no provider left to migrate it to, and
            // handing one provider's key to another would silently 401.
            settings.builtInKeys[provider.name]?.firstOrNull { it.enabled && it.key.isNotBlank() }?.key
                ?: preferences[PreferenceKeys.apiKey(provider)] ?: ""
        }
    }

    /** Custom providers and every provider's key list, legacy settings folded in. */
    val providerSettings: Flow<ProviderSettings> = context.dataStore.data
        .map { readProviderSettings(it) }
        .distinctUntilChanged()

    private fun readProviderSettings(preferences: Preferences): ProviderSettings {
        val stored = preferences[PreferenceKeys.PROVIDER_SETTINGS]?.let { raw ->
            runCatching { AiHttp.json.decodeFromString(ProviderSettings.serializer(), raw) }.getOrNull()
        } ?: ProviderSettings()
        val merged = ProviderSettingsMigration.withLegacy(
            stored = stored,
            legacyKeys = AiProvider.entries.associate { it.name to (preferences[PreferenceKeys.apiKey(it)] ?: "") },
            legacyCustomBaseUrl = preferences[PreferenceKeys.CUSTOM_BASE_URL].orEmpty()
        )
        CustomProviderRegistry.update(merged)
        return merged
    }

    val mcpSettings: Flow<com.oro.app.data.ai.mcp.McpSettings> = context.dataStore.data.map { p ->
        p[PreferenceKeys.MCP_SETTINGS]?.let { raw ->
            runCatching { AiHttp.json.decodeFromString(com.oro.app.data.ai.mcp.McpSettings.serializer(), raw) }.getOrNull()
        } ?: com.oro.app.data.ai.mcp.McpSettings()
    }.distinctUntilChanged()

    val autonomyMode: Flow<com.oro.app.data.ai.agent.AutonomyMode> = context.dataStore.data
        .map { com.oro.app.data.ai.agent.AutonomyMode.fromName(it[PreferenceKeys.AUTONOMY]) }.distinctUntilChanged()

    suspend fun setAutonomyMode(mode: com.oro.app.data.ai.agent.AutonomyMode) {
        context.dataStore.edit { it[PreferenceKeys.AUTONOMY] = mode.name }
    }

    /** Orobi checks its own changes (render, syntax, preview errors) before finishing. */
    val selfCheck: Flow<Boolean> = context.dataStore.data.map { it[PreferenceKeys.SELF_CHECK] ?: true }.distinctUntilChanged()

    /** Whether replies may contain UI blocks (generative UI, rendered natively). */
    val richUi: Flow<Boolean> = context.dataStore.data.map { it[PreferenceKeys.RICH_UI] ?: true }.distinctUntilChanged()

    suspend fun setRichUi(on: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.RICH_UI] = on }
    }

    suspend fun setSelfCheck(on: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SELF_CHECK] = on }
    }

    /** "SYSTEM" or "WHISTLE" (the app's DictationEngine names). */
    val dictationEngineName: Flow<String> = context.dataStore.data
        .map { it[PreferenceKeys.DICTATION_ENGINE] ?: "SYSTEM" }.distinctUntilChanged()

    suspend fun setDictationEngineName(name: String) {
        context.dataStore.edit { it[PreferenceKeys.DICTATION_ENGINE] = name }
    }

    val netlifyToken: Flow<String> = context.dataStore.data.map { it[PreferenceKeys.NETLIFY_TOKEN].orEmpty() }.distinctUntilChanged()

    suspend fun setNetlifyToken(token: String) {
        context.dataStore.edit { it[PreferenceKeys.NETLIFY_TOKEN] = token.trim() }
    }

    suspend fun updateMcpSettings(transform: (com.oro.app.data.ai.mcp.McpSettings) -> com.oro.app.data.ai.mcp.McpSettings) {
        context.dataStore.edit { p ->
            val current = p[PreferenceKeys.MCP_SETTINGS]?.let { raw ->
                runCatching { AiHttp.json.decodeFromString(com.oro.app.data.ai.mcp.McpSettings.serializer(), raw) }.getOrNull()
            } ?: com.oro.app.data.ai.mcp.McpSettings()
            p[PreferenceKeys.MCP_SETTINGS] = AiHttp.json.encodeToString(com.oro.app.data.ai.mcp.McpSettings.serializer(), transform(current))
        }
    }

    /** Atomically edit provider settings. */
    suspend fun updateProviderSettings(transform: (ProviderSettings) -> ProviderSettings) {
        context.dataStore.edit { preferences ->
            val next = transform(readProviderSettings(preferences))
            preferences[PreferenceKeys.PROVIDER_SETTINGS] =
                AiHttp.json.encodeToString(ProviderSettings.serializer(), next)
            // Keep the single-key preferences in step with the lists: other
            // code (and a downgrade) still reads them.
            AiProvider.entries.forEach { provider ->
                next.builtInKeys[provider.name]?.let { list ->
                    preferences[PreferenceKeys.apiKey(provider)] =
                        list.firstOrNull { it.enabled && it.key.isNotBlank() }?.key.orEmpty()
                }
            }
            // The legacy custom server was deleted: stop re-importing it.
            if (next.customById(ProviderSettingsMigration.LEGACY_CUSTOM_ID) == null) {
                preferences.remove(PreferenceKeys.CUSTOM_BASE_URL)
            }
            CustomProviderRegistry.update(next)
        }
    }

    val customBaseUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.CUSTOM_BASE_URL] ?: ""
    }

    /** Reasoning budget, for models that expose one. */
    val reasoningEffort: Flow<ReasoningEffort> = context.dataStore.data.map { preferences ->
        ReasoningEffort.fromName(preferences[PreferenceKeys.REASONING_EFFORT])
    }

    suspend fun setReasoningEffort(effort: ReasoningEffort) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.REASONING_EFFORT] = effort.name
        }
    }

    suspend fun getApiKeyForProvider(provider: AiProvider): String = apiKeys.first()[provider] ?: ""

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ONBOARDING_COMPLETED] = completed
        }
    }

    val debugLogsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DEBUG_LOGS_ENABLED] ?: false
    }

    suspend fun setDebugLogsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEBUG_LOGS_ENABLED] = enabled
        }
    }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/** User choice for [PreferencesRepository.liteMode]. */
enum class LiteMode {
    /** On for low-RAM / low-memory-class devices, off otherwise. */
    AUTO,
    ON,
    OFF;

    /** Resolves the choice against the device classification. */
    fun isEnabled(lowEndDevice: Boolean): Boolean = when (this) {
        AUTO -> lowEndDevice
        ON -> true
        OFF -> false
    }

    companion object {
        fun fromName(value: String?): LiteMode =
            entries.find { it.name == value } ?: AUTO
    }
}
