package com.oro.app.data.ai.community

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * The browser identity chat.qwen.ai's risk SDK expects from a guest: a
 * device fingerprint, the `ssxmod_itna` / `ssxmod_itna2` cookies derived
 * from it (LZW-compressed with the SDK's own alphabet) and the `bx-ua`
 * header (an AES-CBC envelope keyed by the fingerprint's SHA-256).
 *
 * A faithful port of NEB's qwen_proxy fingerprint code. Each call mints a
 * new identity, which is what lets [QwenWebClient] rotate guests.
 */
internal object QwenFingerprint {

    data class Identity(val profile: Profile, val itna: String, val itna2: String, val raw: String, val bxUa: String)

    data class Profile(val platform: String, val renderer: String, val ua: String, val secChUa: String, val secChUaPlatform: String)

    private const val MAC_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

    val PROFILES = listOf(
        Profile(
            "MacIntel", "ANGLE (Apple, ANGLE Metal Renderer: Apple M4, Unspecified Version)|Google Inc. (Apple)", MAC_UA,
            "\"Chromium\";v=\"136\", \"Google Chrome\";v=\"136\", \"Not.A/Brand\";v=\"99\"", "\"macOS\""
        ),
        Profile(
            "MacIntel", "ANGLE (Apple, ANGLE Metal Renderer: Apple M1, Unspecified Version)|Google Inc. (Apple)",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "\"Chromium\";v=\"131\", \"Google Chrome\";v=\"131\", \"Not.A/Brand\";v=\"24\"", "\"macOS\""
        ),
        Profile(
            "Win32", "ANGLE (NVIDIA, NVIDIA GeForce RTX 3080 Direct3D11 vs_5_0 ps_5_0, D3D11)|Google Inc. (NVIDIA)",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36",
            "\"Chromium\";v=\"136\", \"Google Chrome\";v=\"136\", \"Not.A/Brand\";v=\"99\"", "\"Windows\""
        )
    )

    private val SCREENS = listOf(
        "1920|1080|283|1080|158|0|1920|1080|1920|922|0|0",
        "1470|956|283|797|158|0|1470|956|1470|798|0|0",
        "2560|1440|283|1440|158|0|2560|1440|2560|1282|0|0"
    )

    private const val ALPHABET = "DGi0YA7BemWnQjCl4_bR3f8SKIF9tUz/xhr2oEOgPpac=61ZqwTudLkM5vHyNXsVJ"

    private fun hash(): Long = Random.nextLong(0, 0xFFFFFFFFL + 1)

    fun newIdentity(profile: Profile = PROFILES.random(), now: Long = System.currentTimeMillis()): Identity {
        val fp = fingerprint(profile, now)
        val fields = fp.split("^").toMutableList()
        // Re-roll the hash fields the SDK refreshes per cookie.
        fields[16] = fields[16].substringBefore('|') + "|" + hash()
        for (i in listOf(17, 18, 31, 34)) fields[i] = hash().toString()
        fields[36] = Random.nextInt(10, 101).toString()
        fields[33] = now.toString()
        val raw = fields.joinToString("^")
        val itna = "1-" + encode(raw)
        val itna2Data = listOf(fields[0], fields[1], fields[23], "0", "", "0", "", "", "0", "0", fields[32], fields[33], "0", "0", "0", "0", "0")
            .joinToString("^")
        val itna2 = "1-" + encode(itna2Data)
        return Identity(profile, itna, itna2, raw, bxUa(raw, now))
    }

    private fun fingerprint(p: Profile, now: Long): String {
        val deviceId = (1..20).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")
        val fields = listOf(
            deviceId, "websdk-2.3.15d", now.toString(), "91", "1|15", "zh-CN", "-480", "16705151|12791", SCREENS.random(),
            "5", p.platform, "10", p.renderer, "30|30", "0", "28", "5|${hash()}", hash(), hash(),
            "1", "0", "1", "0", "P", "0", "0", "0", "416", "Google Inc.", "8", "-1|0|0|0|0", hash(), "11", now,
            hash(), "0", Random.nextInt(10, 101)
        )
        return fields.joinToString("^")
    }

    /** bx-ua: "231!" + base64(AES-CBC(json payload)), key/iv from SHA-256(fingerprint). */
    fun bxUa(fp: String, now: Long = System.currentTimeMillis()): String {
        val f = fp.split("^")
        val rnd = Random.nextInt(1000, 10000)
        val cs = md5("$fp$now$rnd").take(8)
        val payload = buildJsonObject {
            put("v", "231")
            put("ts", now)
            put("fp", fp)
            putJsonObject("d") {
                put("deviceId", f.getOrElse(0) { "" })
                put("sdkVer", f.getOrElse(1) { "" })
                put("lang", f.getOrElse(5) { "" })
                put("tz", f.getOrElse(6) { "" })
                put("platform", f.getOrElse(10) { "" })
                put("renderer", f.getOrElse(12) { "" })
                put("mode", f.getOrElse(23) { "" })
                put("vendor", f.getOrElse(28) { "" })
            }
            put("rnd", rnd)
            put("seq", 1)
            put("cs", cs)
        }.toString()
        val seed = MessageDigest.getInstance("SHA-256").digest(fp.toByteArray())
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(seed.copyOfRange(0, 16), "AES"), IvParameterSpec(seed.copyOfRange(16, 32)))
        return "231!" + android.util.Base64.encodeToString(cipher.doFinal(payload.toByteArray()), android.util.Base64.NO_WRAP)
    }

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun encode(data: String): String = lzw(data, 6) { ALPHABET[it] }

    /** lz-string's compress loop with a pluggable alphabet (the SDK's variant). */
    internal fun lzw(data: String, bits: Int, charFor: (Int) -> Char): String {
        val dictionary = HashMap<String, Int>()
        val toCreate = HashSet<String>()
        var w = ""
        var enlargeIn = 2
        var dictSize = 3
        var numBits = 2
        val out = StringBuilder()
        var value = 0
        var position = 0

        fun pushBit(bit: Int) {
            value = (value shl 1) or bit
            if (position == bits - 1) {
                position = 0; out.append(charFor(value)); value = 0
            } else position++
        }
        fun pushBits(count: Int, v0: Int) {
            var v = v0
            repeat(count) { pushBit(v and 1); v = v shr 1 }
        }
        fun emitW() {
            if (w in toCreate) {
                val code = w[0].code
                if (code < 256) {
                    repeat(numBits) { pushBit(0) }
                    pushBits(8, code)
                } else {
                    pushBit(1); repeat(numBits - 1) { pushBit(0) }
                    pushBits(16, code)
                }
                enlargeIn--
                if (enlargeIn == 0) { enlargeIn = 1 shl numBits; numBits++ }
                toCreate.remove(w)
            } else {
                pushBits(numBits, dictionary.getValue(w))
            }
            enlargeIn--
            if (enlargeIn == 0) { enlargeIn = 1 shl numBits; numBits++ }
        }

        for (ch in data) {
            val c = ch.toString()
            if (c !in dictionary) { dictionary[c] = dictSize++; toCreate += c }
            val wc = w + c
            if (wc in dictionary) {
                w = wc
            } else {
                emitW()
                dictionary[wc] = dictSize++
                w = c
            }
        }
        if (w.isNotEmpty()) emitW()
        pushBits(numBits, 2)
        while (true) {
            value = value shl 1
            if (position == bits - 1) { out.append(charFor(value)); break }
            position++
        }
        return out.toString()
    }
}
