package com.oro.app.data.preview

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password hashes for the preview's mock accounts.
 *
 * PBKDF2 (HMAC-SHA1, the variant every Android version from API 24 has) with a
 * random salt: people test sign-up forms with their real passwords, so a
 * leaked hash must not give one up in seconds the way a single salted SHA-256
 * round does. Stored as `pbkdf2:<iterations>:<salt hex>:<hash hex>`; the
 * older `sha256:<salt>:<hex>` form still verifies and is upgraded on the next
 * successful login.
 */
internal object PasswordHash {

    private const val ITERATIONS = 12_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private val random = SecureRandom()

    fun create(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        return "pbkdf2:$ITERATIONS:${salt.hex()}:${pbkdf2(password, salt, ITERATIONS).hex()}"
    }

    fun verify(stored: String, password: String): Boolean {
        val parts = stored.split(':')
        return when {
            parts.size == 4 && parts[0] == "pbkdf2" -> {
                val iterations = parts[1].toIntOrNull()?.takeIf { it in 1..1_000_000 } ?: return false
                val salt = parts[2].unhex() ?: return false
                val expected = parts[3].unhex() ?: return false
                MessageDigest.isEqual(pbkdf2(password, salt, iterations, expected.size * 8), expected)
            }
            parts.size == 3 && parts[0] == "sha256" -> {
                val actual = MessageDigest.getInstance("SHA-256").digest((parts[1] + password).toByteArray()).hex()
                MessageDigest.isEqual(actual.toByteArray(), parts[2].lowercase().toByteArray())
            }
            else -> false
        }
    }

    /** True for a hash weaker than what [create] makes now. */
    fun needsUpgrade(stored: String): Boolean =
        !stored.startsWith("pbkdf2:") || (stored.split(':').getOrNull(1)?.toIntOrNull() ?: 0) < ITERATIONS

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int, bits: Int = KEY_BITS): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, bits)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private fun String.unhex(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching { ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }
}
