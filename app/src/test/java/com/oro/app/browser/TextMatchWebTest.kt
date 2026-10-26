package com.oro.app.browser

import com.google.common.truth.Truth.assertThat
import com.oro.app.browser.PageFixtures.el
import com.oro.app.browser.PageFixtures.page
import org.junit.Test

class TextMatchWebTest {

    private val p = page(
        el(1, "button", "Add to cart", top = 100),
        el(2, "link", "Sign in", top = 200),
        el(3, "link", "Sign in with SSO", top = 300),
        el(4, "button", "Delete account", top = 400),
        el(5, "textbox", "Address line 1", top = 500),
        el(6, "link", "lofi hip hop radio - beats to relax/stu…", top = 600),
        el(7, "button", "Continue", top = 700, disabled = true),
        el(8, "button", "Continue", top = 800),
        el(9, "button", "Remove", top = 900),
        el(10, "button", "Remove", top = 1_000),
    )

    @Test
    fun `an exact label is found`() {
        val m = TextMatchWeb.find("Add to cart", p)
        assertThat(m).isInstanceOf(TextMatchWeb.Result.Found::class.java)
        assertThat((m as TextMatchWeb.Result.Found).element.index).isEqualTo(1)
    }

    @Test
    fun `web synonyms match -- a model says log in, the page says sign in`() {
        val m = TextMatchWeb.find("Log in", p)
        assertThat((m as TextMatchWeb.Result.Found).element.index).isEqualTo(2)
    }

    @Test
    fun `a prefix match beats weaker ones`() {
        val m = TextMatchWeb.find("Sign in", p)
        assertThat((m as TextMatchWeb.Result.Found).element.index).isEqualTo(2)
    }

    @Test
    fun `several equally good matches are listed, never guessed`() {
        val m = TextMatchWeb.find("Remove", p)
        assertThat(m).isInstanceOf(TextMatchWeb.Result.Ambiguous::class.java)
        assertThat((m as TextMatchWeb.Result.Ambiguous).candidates.map { it.index }).containsExactly(9, 10)
    }

    @Test
    fun `nth picks among equal matches in page order`() {
        val m = TextMatchWeb.find("Remove", p, nth = 2)
        assertThat((m as TextMatchWeb.Result.Found).element.index).isEqualTo(10)
    }

    @Test
    fun `a disabled twin does not win the tie`() {
        // Both "Continue" buttons score the same; only the enabled one stays in the pool.
        val m = TextMatchWeb.find("Continue", p)
        assertThat((m as TextMatchWeb.Result.Found).element.index).isEqualTo(8)
    }

    @Test
    fun `part of a word never acts -- Go does not click Category`() {
        val weak = TextMatchWeb.score("Go", "Category")
        assertThat(weak).isLessThan(TextMatchWeb.ACT_THRESHOLD)
        val address = TextMatchWeb.score("Add", "Address line 1")
        assertThat(address).isLessThan(TextMatchWeb.ACT_THRESHOLD)
    }

    @Test
    fun `a title the listing cut short still matches the full words`() {
        val m = TextMatchWeb.find("lofi hip hop radio - beats to relax/study to", p)
        assertThat(m).isInstanceOf(TextMatchWeb.Result.Found::class.java)
        assertThat((m as TextMatchWeb.Result.Found).element.index).isEqualTo(6)
    }

    @Test
    fun `small typos are offered below the act threshold`() {
        val score = TextMatchWeb.score("Addd to cart", "Add to cart")
        assertThat(score).isGreaterThan(0)
        assertThat(score).isLessThan(TextMatchWeb.ACT_THRESHOLD)
    }

    @Test
    fun `a missing label is not found`() {
        assertThat(TextMatchWeb.find("Checkout now", p)).isInstanceOf(TextMatchWeb.Result.NotFound::class.java)
    }

    @Test
    fun `hits rank the best first and enabled above disabled`() {
        val hits = TextMatchWeb.hits("Remove", p)
        assertThat(hits.map { it.element.index }).containsExactly(9, 10).inOrder()
    }
}
