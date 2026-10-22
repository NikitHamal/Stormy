package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import org.junit.Test

class TextMatchTest {

    @Test
    fun `scores rank exact over segment over prefix over word over substring`() {
        assertThat(TextMatch.score("Add to cart", "Add to cart")).isEqualTo(100)
        assertThat(TextMatch.score("add to cart", "  ADD TO CART. ")).isEqualTo(100)
        assertThat(TextMatch.score("Alice", "Alice · hi there · 9:41")).isEqualTo(90)
        assertThat(TextMatch.score("Settings", "Settings and privacy")).isEqualTo(75)
        assertThat(TextMatch.score("cart", "Go to cart now")).isEqualTo(60)
        assertThat(TextMatch.score("art", "Go to cart now")).isEqualTo(45)
        assertThat(TextMatch.score("cart add", "Add to cart")).isEqualTo(40)
        assertThat(TextMatch.score("Setings", "Settings")).isGreaterThan(0)
        assertThat(TextMatch.score("Delete", "Settings")).isEqualTo(0)
    }

    @Test
    fun `finds the one element named, preferring the button over its text`() {
        val s = screen(
            el(0, ScreenElement.ROLE_TEXT, "Your cart"),
            el(1, "button", "Add to cart"),
            el(2, ScreenElement.ROLE_TEXT, "Add to cart", clickable = false),
        )
        val r = TextMatch.find("add to cart", s)
        assertThat(r).isInstanceOf(TextMatch.Result.Found::class.java)
        assertThat((r as TextMatch.Result.Found).element.index).isEqualTo(1)
    }

    @Test
    fun `refuses to guess between equal matches unless nth is given`() {
        val s = screen(el(0, "button", "Delete"), el(1, "button", "Delete"), el(2, "button", "Cancel"))
        val r = TextMatch.find("Delete", s)
        assertThat(r).isInstanceOf(TextMatch.Result.Ambiguous::class.java)
        assertThat((r as TextMatch.Result.Ambiguous).candidates.map { it.index }).containsExactly(0, 1).inOrder()
        assertThat(r.weak).isFalse()

        val second = TextMatch.find("Delete", s, nth = 2)
        assertThat((second as TextMatch.Result.Found).element.index).isEqualTo(1)
    }

    @Test
    fun `weak matches are offered, not tapped`() {
        val s = screen(el(0, "button", "Continue with Google"), el(1, "button", "Sign up"))
        val r = TextMatch.find("Settngs page", s)
        assertThat(r).isEqualTo(TextMatch.Result.NotFound)
        val typo = TextMatch.find("Sign upp", s)
        assertThat(typo).isInstanceOf(TextMatch.Result.Ambiguous::class.java)
        assertThat((typo as TextMatch.Result.Ambiguous).weak).isTrue()
    }

    @Test
    fun `levenshtein basics`() {
        assertThat(TextMatch.levenshtein("kitten", "sitting")).isEqualTo(3)
        assertThat(TextMatch.levenshtein("", "abc")).isEqualTo(3)
        assertThat(TextMatch.levenshtein("same", "same")).isEqualTo(0)
    }
}
