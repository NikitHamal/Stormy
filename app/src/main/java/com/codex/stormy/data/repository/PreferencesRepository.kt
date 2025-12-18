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
        val API_KEY = stringPreferencesKey("api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
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

    val apiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.API_KEY] ?: ""
    }

    val aiModel: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.AI_MODEL] ?: "gpt-4"
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

    suspend fun setAiModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.AI_MODEL] = model
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
