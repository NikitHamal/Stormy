package com.oro.app.device

/**
 * Decides which actions need the user's explicit Allow before they happen:
 * anything that spends money, sends something to other people, deletes, or
 * changes security. A false positive costs one tap on Allow; a false
 * negative can send a message the user never wrote, so this errs wide.
 *
 * It judges resolved [Target]s -- the very element the executor will act on,
 * plus the control that really receives the tap -- never the raw arguments,
 * so the guard and the action cannot disagree. A tap by coordinates is judged
 * by what is under the finger.
 */
object SensitiveGuard {

    private val SENSITIVE = Regex(
        "\\b(pay|payment|purchase|buy|order|checkout|place order|subscribe|unsubscribe|donate|transfer|send money|" +
            "send|post|publish|tweet|share|reply|comment|submit|confirm|delete|remove|erase|uninstall|install|" +
            "reset|factory|sign out|log ?out|block|report|call|dial|accept|agree|grant|allow|" +
            "clear (app )?(data|storage|cache)|clear all data|force stop|disable|deactivate|wipe|" +
            "format (the )?(sd card|card|storage|device|phone))\\b",
        RegexOption.IGNORE_CASE
    )

    /** A button that only says yes: what it agrees to is written in the dialog above it. */
    private val GENERIC_YES = Regex("^(ok|okay|yes|continue|proceed|done|apply|sure|got it|i understand|i agree)$", RegexOption.IGNORE_CASE)

    /** Dialog text that makes its yes-button destructive, question or not. */
    private val DESTRUCTIVE = Regex(
        "\\b(delet\\w*|remov\\w*|eras\\w*|clear (app |all )?(data|storage|cache)|wip\\w*|uninstall\\w*|reset\\w*|" +
            "disabl\\w*|force stop|permanent\\w*|cannot be undone|can.t be undone|will be lost)\\b",
        RegexOption.IGNORE_CASE
    )

    /** An action a dialog asks about ("Send this message?"); a notice that it happened ("Message sent") is not. */
    private val ACTION = Regex(
        "\\b(pay\\w*|purchas\\w*|buy|charg\\w*|subscri\\w*|send|post|publish|transfer|share|sign out|log ?out|call)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Labels longer than this are content (a post, a chat row), not a button's verb. */
    private const val MAX_LABEL = 60

    /** A tap, double tap or long press on [target]. */
    fun tap(target: Target?, screen: ScreenState, verb: String = "tap"): String? = when (target) {
        is Target.OnElement -> sensitiveLabel(target.element, target.receiver)?.let { "$verb “$it” in ${appOf(screen)}" }
            ?: confirmation(target.receiver.takeIf { it.label.isNotBlank() } ?: target.element, screen)
                ?.let { (yes, what) -> "$verb “$yes” on “$what” in ${appOf(screen)}" }
        is Target.AtPoint -> sensitiveLabel(target.element, target.text)?.let { "$verb “$it” in ${appOf(screen)}" }
            ?: (target.element ?: target.text)?.let { confirmation(it, screen) }?.let { (yes, what) -> "$verb “$yes” on “$what” in ${appOf(screen)}" }
        else -> null
    }

    /**
     * A plain "OK"/"Yes" in a dialog that says what it confirms: "Clear app
     * data? … will be deleted permanently" with OK. The button's own word is
     * harmless; the dialog's text above it is what the tap agrees to. Returns
     * (button, that text) or null.
     */
    internal fun confirmation(button: ScreenElement, screen: ScreenState): Pair<String, String>? {
        val label = button.label.trim()
        if (!GENERIC_YES.matches(label)) return null
        // The few texts just above the button, in its own window: the dialog's
        // title and message, not the page around it.
        val context = screen.elements
            .filter { it !== button && it.window == button.window && it.label.isNotBlank() && !it.takesTaps }
            .filter { it.bounds.bottom <= button.bounds.top + 8 && button.bounds.top - it.bounds.bottom < 700 }
            .sortedByDescending { it.bounds.bottom }
            .take(4)
        // Reported by the top-most matching line: the dialog's title when it says it.
        val hit = context.sortedBy { it.bounds.top }.firstOrNull {
            DESTRUCTIVE.containsMatchIn(it.label) || ('?' in it.label && ACTION.containsMatchIn(it.label))
        } ?: return null
        return label to hit.label.take(60)
    }

    /** type_text into [field] (null: the focused field), optionally pressing Enter. */
    fun type(field: ScreenElement?, text: String?, submit: Boolean, screen: ScreenState): String? = when {
        field?.password == true -> "type into a password field in ${appOf(screen)}"
        submit && isMessagingApp(screen.packageName) -> "send “${text.orEmpty().take(40)}” in ${appOf(screen)}"
        else -> null
    }

    /** press_key: Enter in a messaging app may send. */
    fun key(key: String?, screen: ScreenState): String? =
        if (key?.lowercase() == "enter" && isMessagingApp(screen.packageName)) "press Enter (may send) in ${appOf(screen)}" else null

    /** open_url: links that start a call or a text. */
    fun url(url: String?): String? {
        val u = url.orEmpty().trim()
        return if (u.startsWith("tel:", true) || u.startsWith("sms:", true) || u.startsWith("smsto:", true)) "open $u" else null
    }

    /** A swipe or drag that starts on a "Slide to pay" / "Swipe to confirm" control. */
    fun slide(start: Target?, screen: ScreenState): String? = when (start) {
        is Target.OnElement -> sensitiveLabel(start.element, start.receiver)?.let { "slide “$it” in ${appOf(screen)}" }
        is Target.AtPoint -> sensitiveLabel(start.element, start.text)?.let { "slide “$it” in ${appOf(screen)}" }
        else -> null
    }

    /** The first of [elements]' labels that reads as a sensitive verb, shortened for the prompt. */
    private fun sensitiveLabel(vararg elements: ScreenElement?): String? {
        for (e in elements) {
            val label = e?.label ?: continue
            if (label.isNotBlank() && label.length <= MAX_LABEL && SENSITIVE.containsMatchIn(label)) return label.take(40)
        }
        return null
    }

    private fun appOf(screen: ScreenState): String = screen.appLabel.ifBlank { screen.packageName }

    private fun isMessagingApp(pkg: String): Boolean = MESSAGING.any { pkg.contains(it, ignoreCase = true) }

    private val MESSAGING = listOf(
        "whatsapp", "telegram", "messag", "mms", "sms", "signal", "viber", "discord", "slack", "instagram",
        "facebook.orca", "twitter", "x.android", "snapchat", "gmail", "email", "outlook", "teams", "line", "wechat", "skype"
    )
}
