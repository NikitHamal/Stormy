package com.codex.stormy.data.ai.learning

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Types of user preferences that can be learned
 */
enum class PreferenceCategory {
    CODING_STYLE,        // Naming conventions, formatting, patterns
    FRAMEWORK_CHOICES,   // Preferred libraries, frameworks
    COMMUNICATION,       // How verbose to be, emoji usage
    FILE_STRUCTURE,      // Project organization preferences
    CORRECTIONS          // Corrections made to AI suggestions
}

/**
 * A single learned preference
 */
@Serializable
data class LearnedPreference(
    val category: PreferenceCategory,
    val key: String,
    val value: String,
    val confidence: Float = 1.0f,
    val occurrences: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val examples: List<String> = emptyList()
)

/**
 * Container for all user preferences for a project
 */
@Serializable
data class ProjectPreferences(
    val projectId: String,
    val preferences: MutableMap<String, LearnedPreference> = mutableMapOf(),
    val corrections: MutableList<CorrectionEntry> = mutableListOf()
)

/**
 * A correction entry when user fixes AI output
 */
@Serializable
data class CorrectionEntry(
    val original: String,
    val corrected: String,
    val context: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * System for learning user preferences from interactions.
 * Learns from:
 * - User corrections to AI output
 * - Explicit preferences stated in chat
 * - Patterns in user code
 */
class UserPreferencesLearner(private val context: Context) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private val preferencesDir by lazy {
        File(context.filesDir, "user_preferences").apply { mkdirs() }
    }

    /**
     * Learn a preference from user interaction
     */
    suspend fun learnPreference(
        projectId: String,
        category: PreferenceCategory,
        key: String,
        value: String,
        example: String? = null
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = loadPreferences(projectId)
            val prefKey = "${category.name}:$key"

            val existing = prefs.preferences[prefKey]
            if (existing != null) {
                // Reinforce existing preference
                prefs.preferences[prefKey] = existing.copy(
                    occurrences = existing.occurrences + 1,
                    confidence = (existing.confidence + 0.1f).coerceAtMost(1.0f),
                    timestamp = System.currentTimeMillis(),
                    examples = if (example != null) {
                        (existing.examples + example).takeLast(5)
                    } else existing.examples
                )
            } else {
                // New preference
                prefs.preferences[prefKey] = LearnedPreference(
                    category = category,
                    key = key,
                    value = value,
                    examples = if (example != null) listOf(example) else emptyList()
                )
            }

            savePreferences(projectId, prefs)
        }
    }

    /**
     * Record a correction (when user edits AI output)
     */
    suspend fun recordCorrection(
        projectId: String,
        original: String,
        corrected: String,
        context: String
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = loadPreferences(projectId)

            prefs.corrections.add(
                CorrectionEntry(
                    original = original,
                    corrected = corrected,
                    context = context
                )
            )

            // Keep only recent corrections (limit to 50)
            while (prefs.corrections.size > 50) {
                prefs.corrections.removeAt(0)
            }

            // Analyze the correction for patterns
            analyzeCorrection(prefs, original, corrected)

            savePreferences(projectId, prefs)
        }
    }

    /**
     * Analyze a correction to extract learnable patterns
     */
    private fun analyzeCorrection(prefs: ProjectPreferences, original: String, corrected: String) {
        // Detect naming convention changes
        detectNamingPatterns(prefs, original, corrected)

        // Detect formatting preferences
        detectFormattingPatterns(prefs, original, corrected)
    }

    private fun detectNamingPatterns(prefs: ProjectPreferences, original: String, corrected: String) {
        // Check for camelCase to snake_case or vice versa
        val camelCasePattern = Regex("[a-z][a-zA-Z0-9]*[A-Z][a-zA-Z0-9]*")
        val snakeCasePattern = Regex("[a-z][a-z0-9]*_[a-z][a-z0-9_]*")

        val originalHasCamel = camelCasePattern.containsMatchIn(original)
        val correctedHasSnake = snakeCasePattern.containsMatchIn(corrected)

        if (originalHasCamel && correctedHasSnake) {
            val prefKey = "${PreferenceCategory.CODING_STYLE.name}:naming_convention"
            val existing = prefs.preferences[prefKey]
            prefs.preferences[prefKey] = LearnedPreference(
                category = PreferenceCategory.CODING_STYLE,
                key = "naming_convention",
                value = "snake_case",
                occurrences = (existing?.occurrences ?: 0) + 1,
                confidence = ((existing?.confidence ?: 0.5f) + 0.1f).coerceAtMost(1.0f)
            )
        }
    }

    private fun detectFormattingPatterns(prefs: ProjectPreferences, original: String, corrected: String) {
        // Detect indentation preference (spaces vs tabs)
        val originalUsesSpaces = original.contains("    ") && !original.contains("\t")
        val correctedUsesTabs = corrected.contains("\t") && !corrected.contains("    ")

        if (originalUsesSpaces && correctedUsesTabs) {
            val prefKey = "${PreferenceCategory.CODING_STYLE.name}:indentation"
            prefs.preferences[prefKey] = LearnedPreference(
                category = PreferenceCategory.CODING_STYLE,
                key = "indentation",
                value = "tabs"
            )
        }

        // Detect bracket style preference
        if (original.contains("function() {") && corrected.contains("function()\n{")) {
            val prefKey = "${PreferenceCategory.CODING_STYLE.name}:bracket_style"
            prefs.preferences[prefKey] = LearnedPreference(
                category = PreferenceCategory.CODING_STYLE,
                key = "bracket_style",
                value = "newline"
            )
        }
    }

    /**
     * Get a preference value
     */
    suspend fun getPreference(projectId: String, category: PreferenceCategory, key: String): String? {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val prefs = loadPreferences(projectId)
                val prefKey = "${category.name}:$key"
                prefs.preferences[prefKey]?.value
            }
        }
    }

    /**
     * Get all preferences for a category
     */
    suspend fun getPreferencesByCategory(
        projectId: String,
        category: PreferenceCategory
    ): List<LearnedPreference> {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val prefs = loadPreferences(projectId)
                prefs.preferences.values.filter { it.category == category }
            }
        }
    }

    /**
     * Get all learned preferences as context for AI
     */
    suspend fun getPreferencesContext(projectId: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = loadPreferences(projectId)
            if (prefs.preferences.isEmpty() && prefs.corrections.isEmpty()) {
                return@withContext ""
            }

            buildString {
                appendLine("\n## User Preferences")
                appendLine("Learned preferences for this user/project:")

                // Group by category
                val grouped = prefs.preferences.values.groupBy { it.category }

                for ((category, categoryPrefs) in grouped) {
                    appendLine("\n### ${formatCategory(category)}")
                    for (pref in categoryPrefs.sortedByDescending { it.confidence }) {
                        val confidence = if (pref.confidence >= 0.8f) "high" else if (pref.confidence >= 0.5f) "medium" else "low"
                        appendLine("- ${pref.key}: ${pref.value} ($confidence confidence)")
                    }
                }

                // Include recent corrections as examples
                if (prefs.corrections.isNotEmpty()) {
                    appendLine("\n### Recent Corrections")
                    appendLine("User has corrected AI output in these ways:")
                    prefs.corrections.takeLast(3).forEach { correction ->
                        appendLine("- Changed: \"${correction.original.take(50)}...\" → \"${correction.corrected.take(50)}...\"")
                    }
                }
            }
        }
    }

    private fun formatCategory(category: PreferenceCategory): String {
        return category.name.split("_").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
    }

    /**
     * Clear all preferences for a project
     */
    suspend fun clearPreferences(projectId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            getPreferencesFile(projectId).delete()
        }
    }

    /**
     * Export preferences as JSON
     */
    suspend fun exportPreferences(projectId: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = loadPreferences(projectId)
            json.encodeToString(prefs)
        }
    }

    private fun getPreferencesFile(projectId: String): File {
        return File(preferencesDir, "${projectId}_preferences.json")
    }

    private fun loadPreferences(projectId: String): ProjectPreferences {
        val file = getPreferencesFile(projectId)
        return if (file.exists()) {
            try {
                json.decodeFromString(file.readText())
            } catch (e: Exception) {
                ProjectPreferences(projectId)
            }
        } else {
            ProjectPreferences(projectId)
        }
    }

    private fun savePreferences(projectId: String, preferences: ProjectPreferences) {
        val file = getPreferencesFile(projectId)
        file.writeText(json.encodeToString(preferences))
    }
}
