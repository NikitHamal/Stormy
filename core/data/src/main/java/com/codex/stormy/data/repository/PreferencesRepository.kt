package com.codex.stormy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.codex.stormy.data.ai.AiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "codex_preferences")

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

        // AI Provider and Model settings
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
        val HAS_SET_DEFAULT_MODEL = booleanPreferencesKey("has_set_default_model")

        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        // Beta Features
        val DEBUG_LOGS_ENABLED = booleanPreferencesKey("debug_logs_enabled")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        try {
            ThemeMode.valueOf(preferences[PreferenceKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
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
        preferences[PreferenceKeys.apiKey(provider)]
            ?: preferences[PreferenceKeys.API_KEY].takeIf { provider == AiProvider.DEEPINFRA }
            ?: ""
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
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.apiKey(provider)] = key.trim()
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

    /** Keys for every provider at once, for building an AiProviderManager. */
    val apiKeys: Flow<Map<AiProvider, String>> = context.dataStore.data.map { preferences ->
        AiProvider.entries.associateWith { provider ->
            preferences[PreferenceKeys.apiKey(provider)]
                // The original build stored DeepInfra's key under a bare
                // "api_key" before per-provider keys existed.
                ?: preferences[PreferenceKeys.API_KEY].takeIf { provider == AiProvider.DEEPINFRA }
                ?: ""
        }
    }

    val customBaseUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.CUSTOM_BASE_URL] ?: ""
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
