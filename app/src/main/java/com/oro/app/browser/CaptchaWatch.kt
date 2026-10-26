package com.oro.app.browser

/**
 * Human checks are the user's to pass, never the agent's -- the device
 * agent's hardest safety rule, carried over unchanged: Orobi does not solve
 * captchas. Detection is widget-based (the snapshot script looks for the
 * check's own iframes and markers), not badge-based, and a ticked box counts
 * as passed.
 *
 * This object is the Kotlin half: it decides what a [PageState] means for the
 * run (wait, proceed, or act) and what the user is told. The detection itself
 * runs in [BrowserScripts] inside the page, where the widgets actually live.
 */
object CaptchaWatch {

    /** What the run should do about the page's human check, if any. */
    sealed interface Verdict {
        /** No check, or the user already passed it: act normally. */
        data object Clear : Verdict
        /** A check the user must pass: wait for them, do not touch it. */
        data class WaitForUser(val kind: HumanCheckKind) : Verdict
    }

    fun verdict(page: PageState): Verdict =
        if (page.needsHuman) Verdict.WaitForUser(page.humanCheck) else Verdict.Clear

    /** The message shown while the run waits (banner on the screen, island text). */
    fun waitingMessage(kind: HumanCheckKind): String = when (kind) {
        HumanCheckKind.RECAPTCHA -> "Pass the reCAPTCHA on the page"
        HumanCheckKind.HCAPTCHA -> "Pass the hCaptcha on the page"
        HumanCheckKind.TURNSTILE -> "Pass the Cloudflare check on the page"
        HumanCheckKind.ARKOSE -> "Pass the verification on the page"
        HumanCheckKind.TEXT -> "Pass the check on the page"
        HumanCheckKind.NONE -> "Pass the check on the page"
    }

    /**
     * Whether a navigation is a bot-challenge interstitial rather than the
     * page the run wanted: Cloudflare's "Just a moment", hCaptcha's
     * challenge, Akamai/GeeTest interstitials. These usually clear
     * themselves in a few seconds or need the user; either way the run must
     * not read the interstitial as the destination.
     */
    fun isInterstitial(page: PageState): Boolean {
        val url = page.url.lowercase()
        if (url.contains("/cdn-cgi/challenge-platform")) return true
        val title = page.title.lowercase()
        if (title == "just a moment..." || title == "just a moment" ||
            title.startsWith("attention required") || title == "access denied"
        ) return true
        val head = page.text.take(200).lowercase()
        return head.contains("verifying you are human") || head.contains("checking your browser")
    }

    /** What the model is told when an action is refused because of a check. */
    fun blockedMessage(kind: HumanCheckKind): String =
        "Blocked: a human check (${kind.name.lowercase()}) is on the page. It is the user's to pass -- Oro is showing it to them now. " +
            "Wait for them (wait_for or wait), then continue. Never try to solve, click or bypass the check itself."
}
