package com.oro.app.guard

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The guard's persisted settings, additive to DataStore like every other
 * store: which models hold council seats (ids; the user's own three work
 * best, any further keyed/custom/community model can be seated), whether the
 * daily guard pass is on, what KEV entries the user has already been told
 * about, and the findings board.
 */
object GuardSettingsStore {

    private val Context.guardPrefs by preferencesDataStore(name = "oro_guard")

    private val MODEL_IDS = stringPreferencesKey("council_model_ids")
    private val CHAIR_ID = stringPreferencesKey("chair_model_id")
    private val DAILY_ON = stringPreferencesKey("daily_pass_on")
    private val KEV_SEEN = stringPreferencesKey("kev_seen")
    private val FINDINGS = stringPreferencesKey("findings")
    private val RUNNER_URL = stringPreferencesKey("sandbox_runner_url")
    private val RUNNER_TOKEN = stringPreferencesKey("sandbox_runner_token")

    private val json = Json { ignoreUnknownKeys = true }
    private val listJson = Json { ignoreUnknownKeys = true }

    /** The owner's default council: the three that answer, at maximum effort. */
    val DEFAULT_MEMBERS = listOf(
        "mercury:mercury-3",
        "k2:IFM/K2-Horizon-375B-A23B",
        "hy3:hy3",
    )

    suspend fun memberIds(context: Context): List<String> {
        val raw = context.guardPrefs.data.first()[MODEL_IDS] ?: return DEFAULT_MEMBERS
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw).ifEmpty { DEFAULT_MEMBERS }
        }.getOrDefault(DEFAULT_MEMBERS)
    }

    suspend fun setMemberIds(context: Context, ids: List<String>) {
        context.guardPrefs.edit { it[MODEL_IDS] = json.encodeToString(ListSerializer(String.serializer()), ids) }
    }

    /** The model that drives the guard loop; the council seats are separate. */
    suspend fun chairId(context: Context): String? = context.guardPrefs.data.first()[CHAIR_ID]?.takeIf { it.isNotBlank() }

    suspend fun setChairId(context: Context, id: String) {
        context.guardPrefs.edit { it[CHAIR_ID] = id }
    }

    suspend fun sandboxRunnerUrl(context: Context): String? =
        context.guardPrefs.data.first()[RUNNER_URL]?.takeIf { it.isNotBlank() }

    suspend fun setSandboxRunnerUrl(context: Context, url: String) {
        context.guardPrefs.edit { it[RUNNER_URL] = url.trim() }
    }

    suspend fun sandboxRunnerToken(context: Context): String? =
        context.guardPrefs.data.first()[RUNNER_TOKEN]?.takeIf { it.isNotBlank() }

    suspend fun setSandboxRunnerToken(context: Context, token: String) {
        context.guardPrefs.edit { it[RUNNER_TOKEN] = token.trim() }
    }

    suspend fun dailyPassOn(context: Context): Boolean =
        context.guardPrefs.data.first()[DAILY_ON]?.toBooleanStrictOrNull() ?: false

    suspend fun setDailyPassOn(context: Context, on: Boolean) {
        context.guardPrefs.edit { it[DAILY_ON] = on.toString() }
    }

    suspend fun kevSeen(context: Context): Set<String> {
        val raw = context.guardPrefs.data.first()[KEV_SEEN] ?: return emptySet()
        return runCatching { listJson.decodeFromString(ListSerializer(String.serializer()), raw).toSet() }.getOrDefault(emptySet())
    }

    suspend fun markKevSeen(context: Context, cves: Set<String>) {
        context.guardPrefs.edit {
            val merged = (kevSeen(context) + cves).toList().takeLast(4000)
            it[KEV_SEEN] = listJson.encodeToString(ListSerializer(String.serializer()), merged)
        }
    }

    // NOTE: findings stored as JSON array of {severity,title,action,source,at}
    suspend fun findings(context: Context): List<Map<String, String>> {
        val raw = context.guardPrefs.data.first()[FINDINGS] ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(MapSerializer(String.serializer(), String.serializer())), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun addFinding(context: Context, severity: String, title: String, action: String, source: String) {
        val merged = (findings(context).filterNot { it["title"] == title } +
            mapOf("severity" to severity, "title" to title, "action" to action, "source" to source,
                "at" to System.currentTimeMillis().toString())).takeLast(200)
        context.guardPrefs.edit {
            it[FINDINGS] = json.encodeToString(ListSerializer(MapSerializer(String.serializer(), String.serializer())), merged)
        }
    }

    suspend fun clearFindings(context: Context) {
        context.guardPrefs.edit { it[FINDINGS] = "" }
    }
}
