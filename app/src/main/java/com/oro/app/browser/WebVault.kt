package com.oro.app.browser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's saved logins for the browser agent.
 *
 * Passwords are encrypted with a non-exportable Android Keystore key
 * (AES-256-GCM) and only ever decrypted inside [get], whose result the engine
 * types straight into the page: the model sees ids, labels, domains and
 * usernames -- never a password, not even a masked one. Entries are scoped to
 * a domain, and [use_credential] asks the user before signing in anywhere.
 *
 * The crypto is deliberately thin (there is nothing to unit-test in a
 * Keystore); the domain logic around it is pure and tested.
 */
object WebVault {

    private const val PREFS = "web_vault"
    private const val KEY_ALIAS = "oro_web_vault"
    private const val GCM_TAG_BITS = 128

    @Serializable
    data class Entry(
        val id: String,
        val label: String,
        val domain: String,
        val username: String,
        /** Base64(iv ++ ciphertext); plaintext only ever inside [get]. */
        val enc: String,
    )

    /** What [list] shows and what the model may see: never the secret. */
    data class Visible(val id: String, val label: String, val domain: String, val username: String)

    /** A decrypted entry; short-lived, never stored or logged. */
    data class Secret(val id: String, val label: String, val domain: String, val username: String, val password: String)

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            }
        }
    }

    fun list(): List<Visible> = entries().map { Visible(it.id, it.label, it.domain, it.username) }

    /** The full entry (password decrypted), or null when the id is unknown or undecryptable. */
    fun get(id: String): Secret? {
        val e = entries().firstOrNull { it.id == id } ?: return null
        val password = runCatching { decrypt(e.enc) }.getOrNull() ?: return null
        return Secret(e.id, e.label, e.domain, e.username, password)
    }

    /** Saves (or replaces, by id) a login; returns its id. */
    fun save(label: String, domain: String, username: String, password: String, id: String? = null): String? {
        val enc = runCatching { encrypt(password) }.getOrNull() ?: return null
        val entryId = id ?: newId()
        val list = entries().filterNot { it.id == entryId } +
            Entry(entryId, label.trim(), normalizeDomain(domain), username.trim(), enc)
        persist(list)
        return entryId
    }

    fun delete(id: String) {
        persist(entries().filterNot { it.id == id })
    }

    /** Entries whose domain covers [url], best (longest) match first. */
    fun forUrl(url: String): List<Visible> {
        val host = hostOf(url) ?: return emptyList()
        return list().filter { matches(it.domain, host) }
            .sortedByDescending { it.domain.length }
    }

    private fun entries(): List<Entry> {
        val raw = prefs?.getString("entries", null) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(Entry.serializer()), raw) }.getOrDefault(emptyList())
    }

    private fun persist(list: List<Entry>) {
        prefs?.edit()?.putString("entries", json.encodeToString(ListSerializer(Entry.serializer()), list))?.apply()
    }

    private fun newId(): String = java.util.UUID.randomUUID().toString().take(8)

    // ------------------------------------------------------------ crypto

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun secretKey(): SecretKey {
        val ks = keyStore()
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + out, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val all = Base64.decode(stored, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, 12)
        val body = all.copyOfRange(12, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    // ------------------------------------------------------ pure helpers

    /** "https://accounts.google.com/v3/signin?x=1" → "accounts.google.com". */
    fun hostOf(url: String): String? = runCatching {
        val u = url.trim()
        val withScheme = if (u.contains("://")) u else "https://$u"
        java.net.URI(withScheme).host?.lowercase()
    }.getOrNull()

    /** "example.com" → stripped, lowercased, no scheme/path/www. */
    fun normalizeDomain(raw: String): String =
        hostOf(raw) ?: raw.trim().lowercase().removePrefix("www.")

    /** Whether a stored [domain] covers [host]: exact, or a subdomain of it. */
    fun matches(domain: String, host: String): Boolean {
        val d = normalizeDomain(domain)
        val h = host.lowercase().removePrefix("www.")
        if (d.isEmpty()) return false
        return h == d || h.endsWith(".$d") || d.removePrefix("www.") == h
    }
}
