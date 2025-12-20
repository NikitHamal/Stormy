package com.codex.stormy.data.git

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.gitCredentialsStore: DataStore<Preferences> by preferencesDataStore(
    name = "git_credentials"
)

/**
 * Manages Git credentials with secure storage using Android Keystore
 * Credentials are encrypted before storage
 */
class GitCredentialsManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        // Global Git identity
        val GIT_USER_NAME = stringPreferencesKey("git_user_name")
        val GIT_USER_EMAIL = stringPreferencesKey("git_user_email")

        // Default credentials (encrypted)
        val DEFAULT_CREDENTIALS = stringPreferencesKey("default_credentials")

        // Host-specific credentials prefix
        fun hostCredentials(host: String) = stringPreferencesKey("host_${host.hashCode()}")

        // Git settings
        val AUTO_FETCH = stringPreferencesKey("auto_fetch") // "true" or "false"
        val FETCH_INTERVAL_MINUTES = stringPreferencesKey("fetch_interval")
        val PUSH_DEFAULT = stringPreferencesKey("push_default") // "current", "matching", "simple"
        val PULL_REBASE = stringPreferencesKey("pull_rebase") // "true" or "false"
    }

    companion object {
        private const val KEYSTORE_ALIAS = "codex_git_credentials_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    // Git identity
    val gitUserName: Flow<String> = context.gitCredentialsStore.data.map { preferences ->
        preferences[Keys.GIT_USER_NAME] ?: ""
    }

    val gitUserEmail: Flow<String> = context.gitCredentialsStore.data.map { preferences ->
        preferences[Keys.GIT_USER_EMAIL] ?: ""
    }

    // Git settings
    val autoFetch: Flow<Boolean> = context.gitCredentialsStore.data.map { preferences ->
        preferences[Keys.AUTO_FETCH]?.toBooleanStrictOrNull() ?: true
    }

    val fetchIntervalMinutes: Flow<Int> = context.gitCredentialsStore.data.map { preferences ->
        preferences[Keys.FETCH_INTERVAL_MINUTES]?.toIntOrNull() ?: 15
    }

    val pushDefault: Flow<String> = context.gitCredentialsStore.data.map { preferences ->
        preferences[Keys.PUSH_DEFAULT] ?: "current"
    }

    val pullRebase: Flow<Boolean> = context.gitCredentialsStore.data.map { preferences ->
        preferences[Keys.PULL_REBASE]?.toBooleanStrictOrNull() ?: false
    }

    /**
     * Set Git user identity
     */
    suspend fun setGitIdentity(name: String, email: String) {
        context.gitCredentialsStore.edit { preferences ->
            preferences[Keys.GIT_USER_NAME] = name
            preferences[Keys.GIT_USER_EMAIL] = email
        }
    }

    /**
     * Get Git user identity
     */
    suspend fun getGitIdentity(): Pair<String, String> {
        val prefs = context.gitCredentialsStore.data.first()
        return Pair(
            prefs[Keys.GIT_USER_NAME] ?: "",
            prefs[Keys.GIT_USER_EMAIL] ?: ""
        )
    }

    /**
     * Save default credentials (used when no host-specific credentials found)
     */
    suspend fun saveDefaultCredentials(credentials: GitCredentials) {
        val encrypted = encryptCredentials(credentials)
        context.gitCredentialsStore.edit { preferences ->
            preferences[Keys.DEFAULT_CREDENTIALS] = encrypted
        }
    }

    /**
     * Get default credentials
     */
    suspend fun getDefaultCredentials(): GitCredentials? {
        val prefs = context.gitCredentialsStore.data.first()
        val encrypted = prefs[Keys.DEFAULT_CREDENTIALS] ?: return null
        return decryptCredentials(encrypted)
    }

    /**
     * Save credentials for a specific host (e.g., github.com, gitlab.com)
     */
    suspend fun saveHostCredentials(host: String, credentials: GitCredentials) {
        val encrypted = encryptCredentials(credentials)
        context.gitCredentialsStore.edit { preferences ->
            preferences[Keys.hostCredentials(host)] = encrypted
        }
    }

    /**
     * Get credentials for a specific host
     * Falls back to default credentials if no host-specific credentials found
     */
    suspend fun getCredentialsForHost(host: String): GitCredentials? {
        val prefs = context.gitCredentialsStore.data.first()

        // Try host-specific first
        val hostEncrypted = prefs[Keys.hostCredentials(host)]
        if (hostEncrypted != null) {
            return decryptCredentials(hostEncrypted)
        }

        // Fall back to default
        val defaultEncrypted = prefs[Keys.DEFAULT_CREDENTIALS]
        return defaultEncrypted?.let { decryptCredentials(it) }
    }

    /**
     * Get credentials for a remote URL
     * Extracts host from URL and looks up credentials
     */
    suspend fun getCredentialsForUrl(remoteUrl: String): GitCredentials? {
        val host = extractHostFromUrl(remoteUrl) ?: return getDefaultCredentials()
        return getCredentialsForHost(host)
    }

    /**
     * Remove credentials for a specific host
     */
    suspend fun removeHostCredentials(host: String) {
        context.gitCredentialsStore.edit { preferences ->
            preferences.remove(Keys.hostCredentials(host))
        }
    }

    /**
     * Remove default credentials
     */
    suspend fun removeDefaultCredentials() {
        context.gitCredentialsStore.edit { preferences ->
            preferences.remove(Keys.DEFAULT_CREDENTIALS)
        }
    }

    /**
     * Check if credentials exist for a host
     */
    suspend fun hasCredentialsForHost(host: String): Boolean {
        val prefs = context.gitCredentialsStore.data.first()
        return prefs[Keys.hostCredentials(host)] != null
    }

    /**
     * Check if default credentials exist
     */
    suspend fun hasDefaultCredentials(): Boolean {
        val prefs = context.gitCredentialsStore.data.first()
        return prefs[Keys.DEFAULT_CREDENTIALS] != null
    }

    // Git Settings

    suspend fun setAutoFetch(enabled: Boolean) {
        context.gitCredentialsStore.edit { preferences ->
            preferences[Keys.AUTO_FETCH] = enabled.toString()
        }
    }

    suspend fun setFetchInterval(minutes: Int) {
        context.gitCredentialsStore.edit { preferences ->
            preferences[Keys.FETCH_INTERVAL_MINUTES] = minutes.coerceIn(5, 60).toString()
        }
    }

    suspend fun setPushDefault(mode: String) {
        context.gitCredentialsStore.edit { preferences ->
            preferences[Keys.PUSH_DEFAULT] = mode
        }
    }

    suspend fun setPullRebase(enabled: Boolean) {
        context.gitCredentialsStore.edit { preferences ->
            preferences[Keys.PULL_REBASE] = enabled.toString()
        }
    }

    // Encryption helpers

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Check if key already exists
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        // Create new key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    private fun encryptCredentials(credentials: GitCredentials): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val jsonString = json.encodeToString(credentials)
        val encryptedBytes = cipher.doFinal(jsonString.toByteArray(Charsets.UTF_8))

        // Combine IV and encrypted data
        val iv = cipher.iv
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    private fun decryptCredentials(encrypted: String): GitCredentials? {
        return try {
            val combined = Base64.decode(encrypted, Base64.DEFAULT)

            // Extract IV (first 12 bytes for GCM)
            val iv = combined.copyOfRange(0, 12)
            val encryptedBytes = combined.copyOfRange(12, combined.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val jsonString = String(decryptedBytes, Charsets.UTF_8)

            json.decodeFromString<GitCredentials>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractHostFromUrl(url: String): String? {
        return try {
            when {
                url.startsWith("git@") -> {
                    // SSH format: git@github.com:user/repo.git
                    url.substringAfter("git@").substringBefore(":")
                }
                url.startsWith("https://") || url.startsWith("http://") -> {
                    // HTTPS format: https://github.com/user/repo.git
                    val withoutProtocol = url.substringAfter("://")
                    withoutProtocol.substringBefore("/")
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
