package com.codex.stormy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
        val CODE_AUTOCOMPLETION = booleanPreferencesKey("code_autocompletion")

        // API Keys for different providers
        val API_KEY = stringPreferencesKey("api_key") // DeepInfra (legacy)
        val DEEPINFRA_API_KEY = stringPreferencesKey("deepinfra_api_key")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")

        // AI Provider and Model settings
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
        val HAS_SET_DEFAULT_MODEL = booleanPreferencesKey("has_set_default_model")

        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
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

    val codeAutocompletion: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.CODE_AUTOCOMPLETION] ?: true
    }

    // Legacy API key (DeepInfra)
    val apiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.API_KEY] ?: ""
    }

    // Provider-specific API keys
    val deepInfraApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DEEPINFRA_API_KEY]
            ?: preferences[PreferenceKeys.API_KEY] // Fallback to legacy key
            ?: ""
    }

    val openRouterApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.OPENROUTER_API_KEY] ?: ""
    }

    val geminiApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.GEMINI_API_KEY] ?: ""
    }

    val openAiApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.OPENAI_API_KEY] ?: ""
    }

    val anthropicApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.ANTHROPIC_API_KEY] ?: ""
    }

    // AI Provider and Model
    val aiProvider: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.AI_PROVIDER] ?: "DEEPINFRA"
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

    suspend fun setCodeAutocompletion(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.CODE_AUTOCOMPLETION] = enabled
        }
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.API_KEY] = key
        }
    }

    suspend fun setDeepInfraApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEEPINFRA_API_KEY] = key
        }
    }

    suspend fun setOpenRouterApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.OPENROUTER_API_KEY] = key
        }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.GEMINI_API_KEY] = key
        }
    }

    suspend fun setOpenAiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.OPENAI_API_KEY] = key
        }
    }

    suspend fun setAnthropicApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ANTHROPIC_API_KEY] = key
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
     * Get the API key for a specific provider
     */
    suspend fun getApiKeyForProvider(provider: String): String {
        val preferences = context.dataStore.data.map { it }.first()
        return when (provider.uppercase()) {
            "DEEPINFRA" -> preferences[PreferenceKeys.DEEPINFRA_API_KEY]
                ?: preferences[PreferenceKeys.API_KEY] ?: ""
            "OPENROUTER" -> preferences[PreferenceKeys.OPENROUTER_API_KEY] ?: ""
            "GEMINI" -> preferences[PreferenceKeys.GEMINI_API_KEY] ?: ""
            "OPENAI" -> preferences[PreferenceKeys.OPENAI_API_KEY] ?: ""
            "ANTHROPIC" -> preferences[PreferenceKeys.ANTHROPIC_API_KEY] ?: ""
            else -> preferences[PreferenceKeys.API_KEY] ?: ""
        }
    }

    /**
     * Set the API key for a specific provider
     */
    suspend fun setApiKeyForProvider(provider: String, key: String) {
        when (provider.uppercase()) {
            "DEEPINFRA" -> setDeepInfraApiKey(key)
            "OPENROUTER" -> setOpenRouterApiKey(key)
            "GEMINI" -> setGeminiApiKey(key)
            "OPENAI" -> setOpenAiApiKey(key)
            "ANTHROPIC" -> setAnthropicApiKey(key)
            else -> setApiKey(key)
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ONBOARDING_COMPLETED] = completed
        }
    }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
