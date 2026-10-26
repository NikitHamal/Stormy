package com.oro.app.browser

/**
 * Decides which browser actions need the user's explicit Allow before they
 * happen: anything that spends money, sends something to other people,
 * deletes an account's data, posts publicly, or hands over a password. A
 * false positive costs one tap on Allow; a false negative can buy something
 * the user never asked for, so this errs wide -- the device agent's rule,
 * carried over.
 *
 * It judges the resolved [PageElement] the engine is about to act on, never
 * the raw arguments, so guard and action cannot disagree. The web port adds
 * what only the web has: payment-page context (a Stripe/checkout origin makes
 * a plain "Submit" sensitive) and secret entry, which is sensitive unless it
 * travels through the vault or the user's own hands.
 */
object WebGuard {

    private val SENSITIVE = Regex(
        "\\b(pay|payment|payments|purchase|buy|order now|place order|checkout|check out|subscribe|subscription|" +
            "unsubscribe|donate|donation|transfer|send money|remit|bid|bet|stake|" +
            "send|post|publish|tweet|share|reply|comment|delete|remove|erase|" +
            "reset password|change password|sign out|log ?out|deactivate|close account|install)\\b",
        RegexOption.IGNORE_CASE
    )
    // Deliberately NOT sensitive on their own: "submit", "confirm", "cancel",
    // "accept", "agree", "allow". A plain form submit is the bread and butter
    // of a browser run -- gating every one would drown the user in Allow
    // taps. They are caught instead by their context: a payment page, a
    // dialog that says what it destroys (GENERIC_YES + DESTRUCTIVE), or a
    // form that sends (messagingContext).

    /** A button that only says yes: what it agrees to is written around it. */
    private val GENERIC_YES = Regex("^(ok|okay|yes|y|continue|proceed|done|apply|sure|got it|i understand|i agree|confirm|allow|accept)$", RegexOption.IGNORE_CASE)

    /** Nearby text that makes a yes-button destructive or spending, question or not. */
    private val DESTRUCTIVE = Regex(
        "\\b(delet\\w*|remov\\w*|eras\\w*|cancel\\w*|unsubscrib\\w*|deactivat\\w*|close (your )?account|" +
            "wip\\w*|permanent\\w*|cannot be undone|can.?t be undone|will be lost|charg\\w*|\\$\\s?\\d|usd|eur|gbp|" +
            "pay\\w*|purchas\\w*|order|total due|amount due)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Labels longer than this are content (a post, a product blurb), not a button's verb. */
    private const val MAX_LABEL = 60

    /** Origins and paths where any submit-like click spends money or ends accounts. */
    private val PAYMENT_CONTEXT = Regex(
        "(checkout\\.stripe\\.com|pay\\.google\\.com|paypal\\.com/(checkout|signin)|buy\\.stripe\\.com|" +
            "/checkout\\b|/cart\\b|/payment\\b|/billing\\b|/order/confirm|/purchase\\b)",
        RegexOption.IGNORE_CASE
    )

    /** A click on [target] on [page]: the sensitive phrase to confirm, or null. */
    fun click(target: PageElement?, page: PageState, verb: String = "click"): String? {
        if (target == null) return null
        val label = target.label.trim().take(MAX_LABEL)
        if (label.isNotBlank() && SENSITIVE.containsMatchIn(label)) {
            return "$verb “$label” on ${hostOf(page)}"
        }
        // A plain "Submit" on a payment page spends money like a "Pay" button.
        if (target.role == "button" || target.inputType == "submit") {
            if (PAYMENT_CONTEXT.containsMatchIn(page.url) && label.isNotBlank() && label.length <= 30) {
                return "$verb “$label” on a payment page (${hostOf(page)})"
            }
        }
        confirmation(target, page)?.let { (yes, what) ->
            return "$verb “$yes” on “$what” on ${hostOf(page)}"
        }
        return null
    }

    /**
     * A plain "OK"/"Confirm" whose surroundings say what it agrees to: the
     * modal's own text (elements near it, above or around), not the page.
     * Returns (button, that text) or null.
     */
    internal fun confirmation(button: PageElement, page: PageState): Pair<String, String>? {
        val label = button.label.trim()
        if (!GENERIC_YES.matches(label)) return null
        val context = page.elements
            .filter { it !== button && it.label.isNotBlank() && it.role != "button" && it.role != "link" }
            .filter { it.bounds.bottom <= button.bounds.top + 8 && button.bounds.top - it.bounds.bottom < 700 &&
                it.bounds.left < button.bounds.right && it.bounds.right > button.bounds.left }
            .sortedByDescending { it.bounds.bottom }
            .take(4)
        // The page's own text counts too when the page is dialog-like.
        val hitEl = context.firstOrNull { DESTRUCTIVE.containsMatchIn(it.label) }
        if (hitEl != null) return label to hitEl.label.take(60)
        val nearText = page.text.take(400)
        if (isLikelyModal(page) && DESTRUCTIVE.containsMatchIn(nearText)) return label to nearText.take(60)
        return null
    }

    /** A dialog-ish page: few words visible and a generic yes-button present. */
    private fun isLikelyModal(page: PageState): Boolean =
        page.text.length < 600 || page.elements.count { it.role == "button" } <= 4

    /**
     * Typing into a field. Passwords are the user's business: the model may
     * not type a plaintext one it was given -- that is what the vault and
     * ask_user_secret are for. [viaVaultOrUser] suppresses the flag when the
     * value came from either.
     */
    fun type(field: PageElement?, page: PageState, submit: Boolean, viaVaultOrUser: Boolean = false): String? = when {
        field?.password == true && !viaVaultOrUser ->
            "type into a password field on ${hostOf(page)} (use a saved credential or ask the user to type it)"
        submit && paymentForm(page) -> "submit a payment form on ${hostOf(page)}"
        submit && messagingContext(page) -> "send a message on ${hostOf(page)}"
        else -> null
    }

    /** fill_form with submit=true: sensitive when the form pays, sends or deletes. */
    fun fillForm(page: PageState, submit: Boolean, hasPasswordField: Boolean, viaVaultOrUser: Boolean): String? {
        if (hasPasswordField && !viaVaultOrUser) return "fill a form containing a password field on ${hostOf(page)}"
        if (!submit) return null
        return when {
            paymentForm(page) -> "fill and submit a payment form on ${hostOf(page)}"
            messagingContext(page) -> "fill and submit a form that sends a message on ${hostOf(page)}"
            else -> null
        }
    }

    /** Links and navigations that leave the web: tel:, sms:, mailto:, intent:. */
    fun url(url: String?): String? {
        val u = url.orEmpty().trim()
        val lower = u.lowercase()
        return when {
            lower.startsWith("tel:") || lower.startsWith("sms:") || lower.startsWith("smsto:") -> "open $u (leaves the browser)"
            lower.startsWith("mailto:") -> "open $u (drafts an email outside Oro)"
            lower.startsWith("intent:") || lower.startsWith("market:") -> "open $u (launches another app)"
            else -> null
        }
    }

    private fun paymentForm(page: PageState): Boolean =
        PAYMENT_CONTEXT.containsMatchIn(page.url) ||
            page.elements.any {
                it.editable && (it.label.contains("card", true) || it.label.contains("cvv", true) ||
                    it.label.contains("expiry", true) || it.label.contains("cvc", true))
            }

    private fun messagingContext(page: PageState): Boolean {
        val url = page.url.lowercase()
        return url.contains("/messages") || url.contains("/chat") || url.contains("gmail.com") ||
            url.contains("linkedin.com/messaging") || url.contains("twitter.com") || url.contains("x.com") ||
            url.contains("facebook.com") || url.contains("whatsapp") || url.contains("telegram")
    }

    fun hostOf(page: PageState): String = runCatching {
        java.net.URI(page.url).host ?: page.url
    }.getOrDefault(page.url)
}
