package com.oro.app.device

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Decides which actions need the user's explicit Allow before they happen:
 * anything that spends money, sends something to other people, deletes, or
 * changes security. A false positive costs one tap on Allow; a false
 * negative can send a message the user never wrote, so this errs wide.
 */
object SensitiveGuard {

    private val SENSITIVE = Regex(
        "\\b(pay|payment|purchase|buy|order|checkout|place order|subscribe|donate|transfer|send money|" +
            "send|post|publish|tweet|share|reply|comment|submit|confirm|delete|remove|erase|uninstall|install|" +
            "reset|factory|sign out|log ?out|block|report|call|dial|accept|agree|grant|allow)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Description of the sensitive action, or null if it is routine. */
    fun check(tool: String, args: JsonObject, screen: ScreenState): String? {
        fun idx(k: String) = (args[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
        return when (tool) {
            "tap", "long_press", "double_tap" -> {
                val e = idx("index")?.let { screen.element(it) } ?: return null
                sensitiveTap(e, screen)
            }
            "tap_text" -> {
                val text = (args["text"] as? JsonPrimitive)?.contentOrNull ?: return null
                val nth = idx("nth")
                when (val m = TextMatch.find(text, screen, nth)) {
                    is TextMatch.Result.Found -> sensitiveTap(m.element, screen)
                    else -> null // nothing will be tapped
                }
            }
            "type_text" -> {
                val e = idx("index")?.let { screen.element(it) } ?: screen.elements.firstOrNull { it.editable && it.focused }
                val submit = (args["submit"] as? JsonPrimitive)?.booleanOrNull == true
                when {
                    e?.password == true -> "type into a password field in ${screen.appLabel}"
                    submit && isMessagingApp(screen.packageName) -> "send “${(args["text"] as? JsonPrimitive)?.contentOrNull?.take(40)}” in ${screen.appLabel}"
                    else -> null
                }
            }
            "press_key" -> {
                val key = (args["key"] as? JsonPrimitive)?.contentOrNull?.lowercase()
                if (key == "enter" && isMessagingApp(screen.packageName)) "press Enter (may send) in ${screen.appLabel}" else null
            }
            "open_url" -> {
                val url = (args["url"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (url.startsWith("tel:") || url.startsWith("sms:")) "open $url" else null
            }
            else -> null
        }
    }

    private fun sensitiveTap(e: ScreenElement, screen: ScreenState): String? {
        val label = e.label
        return if (label.isNotBlank() && SENSITIVE.containsMatchIn(label) && label.length <= 60) {
            "tap “${label.take(40)}” in ${screen.appLabel}"
        } else null
    }

    private fun isMessagingApp(pkg: String): Boolean = MESSAGING.any { pkg.contains(it, ignoreCase = true) }

    private val MESSAGING = listOf(
        "whatsapp", "telegram", "messag", "mms", "sms", "signal", "viber", "discord", "slack", "instagram",
        "facebook.orca", "twitter", "x.android", "snapchat", "gmail", "email", "outlook", "teams", "line", "wechat", "skype"
    )
}
