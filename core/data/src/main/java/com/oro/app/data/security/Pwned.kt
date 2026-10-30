package com.oro.app.data.security

import com.oro.app.data.ai.AiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Have-I-Been-Pwned lookups, the privacy-preserving way.
 *
 * A password never leaves the device: only the first five hex chars of its
 * SHA-1 go out (the k-anonymity range query), and the answer comes back as a
 * list of suffixes the device matches locally. An email is never sent at all
 * without the user's own HIBP key -- breach watching for addresses happens
 * through the council reading public breach notices instead.
 */
object Pwned {

    fun sha1Hex(input: String): String =
        MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }

    /** How many times [suffix] (upper-case hex) appears in a range [body]; 0 when absent. */
    fun parseRange(body: String, suffix: String): Int =
        body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstOrNull { line -> line.substringBefore(':').equals(suffix, ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0

    /**
     * Times this exact password has appeared in known breaches, null when the
     * service could not be reached (an unreachable oracle says nothing, it
     * never says "safe").
     */
    suspend fun passwordSeenCount(password: String): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val hash = sha1Hex(password)
            val prefix = hash.take(5)
            val suffix = hash.substring(5)
            val client = AiHttp.client.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url("https://api.pwnedpasswords.com/range/$prefix")
                .header("User-Agent", "Oro-Shield (k-anonymity range query)")
                .header("Add-Padding", "true")
                .get()
                .build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) null else parseRange(r.body?.string().orEmpty(), suffix)
            }
        }.getOrNull()
    }
}
