package com.oro.app.browser

import com.google.common.truth.Truth.assertThat
import com.oro.app.browser.PageFixtures.el
import com.oro.app.browser.PageFixtures.page
import org.junit.Test

class CaptchaWatchTest {

    @Test
    fun `an unchecked widget means the user must pass it`() {
        val p = page(el(1, "button", "Search", top = 100, editable = false), humanCheck = HumanCheckKind.RECAPTCHA)
        assertThat(p.needsHuman).isTrue()
        assertThat(CaptchaWatch.verdict(p)).isInstanceOf(CaptchaWatch.Verdict.WaitForUser::class.java)
        assertThat(CaptchaWatch.blockedMessage(HumanCheckKind.RECAPTCHA)).contains("user's to pass")
    }

    @Test
    fun `a passed check is clear`() {
        val p = page(humanCheck = HumanCheckKind.TURNSTILE, checkPassed = true)
        assertThat(p.needsHuman).isFalse()
        assertThat(CaptchaWatch.verdict(p)).isEqualTo(CaptchaWatch.Verdict.Clear)
    }

    @Test
    fun `a page with no check is clear`() {
        assertThat(CaptchaWatch.verdict(page())).isEqualTo(CaptchaWatch.Verdict.Clear)
    }

    @Test
    fun `bot-challenge interstitials are recognised and are not the destination`() {
        val moment = page(url = "https://shop.example/", title = "Just a moment...", text = "Verifying you are human. This may take a few seconds.")
        assertThat(CaptchaWatch.isInterstitial(moment)).isTrue()

        val challenge = page(url = "https://shop.example/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page", title = "Shop")
        assertThat(CaptchaWatch.isInterstitial(challenge)).isTrue()

        val denied = page(title = "Attention Required! | Cloudflare")
        assertThat(CaptchaWatch.isInterstitial(denied)).isTrue()
    }

    @Test
    fun `an ordinary page is not an interstitial`() {
        val real = page(url = "https://shop.example/", title = "Shop — Home", text = "Welcome to the shop. Today's deals are below.")
        assertThat(CaptchaWatch.isInterstitial(real)).isFalse()
    }

    @Test
    fun `waiting messages name the check`() {
        assertThat(CaptchaWatch.waitingMessage(HumanCheckKind.HCAPTCHA)).contains("hCaptcha")
        assertThat(CaptchaWatch.waitingMessage(HumanCheckKind.TURNSTILE)).contains("Cloudflare")
        assertThat(CaptchaWatch.waitingMessage(HumanCheckKind.TEXT)).contains("Pass the check")
    }
}
