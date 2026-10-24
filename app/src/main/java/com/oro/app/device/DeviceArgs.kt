package com.oro.app.device

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Lenient readers for tool arguments: models send numbers as strings
 * (" 2"), integers as floats (7.0) and booleans as "true".
 *
 * One set of readers for everything that looks at a call -- the safety guard,
 * the target resolver and the executor -- so they can never disagree about
 * which element a call names. (They did: the guard read `7.0` as no index and
 * waved the call through, the executor read it as 7 and tapped.)
 */
internal object DeviceArgs {

    fun str(a: JsonObject, k: String): String? = (a[k] as? JsonPrimitive)?.contentOrNull

    /** An integer, or null; a non-integral number (7.5) is not an index. */
    fun int(a: JsonObject, k: String): Int? {
        val p = a[k] as? JsonPrimitive ?: return null
        val raw = p.contentOrNull?.trim() ?: return null
        p.intOrNull?.let { return it }
        raw.toIntOrNull()?.let { return it }
        val d = p.doubleOrNull ?: raw.toDoubleOrNull() ?: return null
        return if (d % 1.0 == 0.0 && d >= Int.MIN_VALUE && d <= Int.MAX_VALUE) d.toInt() else null
    }

    fun num(a: JsonObject, k: String): Double? {
        val p = a[k] as? JsonPrimitive ?: return null
        return p.doubleOrNull ?: p.contentOrNull?.trim()?.toDoubleOrNull()
    }

    fun bool(a: JsonObject, k: String): Boolean {
        val p = a[k] as? JsonPrimitive ?: return false
        return p.booleanOrNull ?: (p.contentOrNull?.trim()?.lowercase() == "true")
    }

    /** True when the key is present at all, even with a value that does not parse. */
    fun has(a: JsonObject, k: String): Boolean = a[k] != null && a[k] !is kotlinx.serialization.json.JsonNull
}
