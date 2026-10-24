package com.oro.app.device

/**
 * Whether a field kept what was typed into it, judged from what it shows
 * afterwards. Pure, for tests.
 */
internal object TypedText {

    enum class Readback { KEPT, LOST, UNKNOWN }

    /**
     * [actual] (null: the field does not say) against [expected]. Letters and
     * digits only, case aside, and anywhere in the field: a field that formats
     * ("5551234567" shown as "(555) 123-4567") or completes inline ("you" →
     * "youtube.com") kept the text. Emptying a field means it is empty.
     */
    fun check(expected: String, actual: String?): Readback {
        if (actual == null) return Readback.UNKNOWN
        val want = core(expected)
        val kept = if (want.isEmpty()) actual.trim() == expected.trim() else want in core(actual)
        return if (kept) Readback.KEPT else Readback.LOST
    }

    private fun core(s: String): String = s.filter { it.isLetterOrDigit() }.lowercase()
}
