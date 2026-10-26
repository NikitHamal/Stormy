package com.oro.app.browser

import com.google.common.truth.Truth.assertThat
import com.oro.app.browser.PageFixtures.el
import com.oro.app.browser.PageFixtures.page
import org.junit.Test

class WebGuardTest {

    private val shop = page(
        el(1, "button", "Place order", top = 100, editable = false),
        el(2, "button", "Add to cart", top = 200, editable = false),
        el(3, "button", "Delete account", top = 300, editable = false),
        el(4, "textbox", "Password", top = 400, password = true),
        url = "https://shop.example/product/42",
    )

    private val checkout = page(
        el(1, "button", "Submit", top = 100, editable = false),
        el(2, "textbox", "Card number", top = 200),
        url = "https://pay.example/checkout",
    )

    @Test
    fun `spending, sending and deleting ask, browsing does not`() {
        assertThat(WebGuard.click(shop.element(1), shop)).isNotNull()   // Place order
        assertThat(WebGuard.click(shop.element(3), shop)).isNotNull()   // Delete account
        assertThat(WebGuard.click(shop.element(2), shop)).isNull()      // Add to cart is not sensitive
    }

    @Test
    fun `a plain submit on a payment page spends money like a Pay button`() {
        assertThat(WebGuard.click(checkout.element(1), checkout)).isNotNull()
        // The same button on an ordinary page is just a form.
        val ordinary = page(el(1, "button", "Submit", top = 100, editable = false), url = "https://blog.example/contact")
        assertThat(WebGuard.click(ordinary.element(1), ordinary)).isNull()
    }

    @Test
    fun `typing a plaintext password is sensitive unless it came from the vault or the user`() {
        assertThat(WebGuard.type(shop.element(4), shop, submit = false)).isNotNull()
        assertThat(WebGuard.type(shop.element(4), shop, submit = false, viaVaultOrUser = true)).isNull()
    }

    @Test
    fun `submitting a form with card fields is sensitive`() {
        assertThat(WebGuard.type(checkout.element(2), checkout, submit = true)).isNotNull()
    }

    @Test
    fun `a generic yes-button in a destructive dialog is caught by the dialog's text`() {
        val dialog = page(
            el(1, "text", "Delete your account? This cannot be undone.", top = 100, editable = false),
            el(2, "button", "OK", top = 400, editable = false),
            url = "https://app.example/settings",
            text = "Delete your account? This cannot be undone.",
        )
        assertThat(WebGuard.click(dialog.element(2), dialog)).isNotNull()
    }

    @Test
    fun `a generic yes-button on an ordinary page is not`() {
        val ordinary = page(
            el(1, "button", "OK", top = 100, editable = false),
            url = "https://blog.example/post",
            text = "A long article about gardening with many paragraphs of harmless text. ".repeat(20),
        )
        assertThat(WebGuard.click(ordinary.element(1), ordinary)).isNull()
    }

    @Test
    fun `leaving the web asks, http does not`() {
        assertThat(WebGuard.url("tel:+15551234567")).isNotNull()
        assertThat(WebGuard.url("mailto:hi@example.com")).isNotNull()
        assertThat(WebGuard.url("intent://scan/#Intent;scheme=zxing;end")).isNotNull()
        assertThat(WebGuard.url("https://example.com")).isNull()
        assertThat(WebGuard.url(null)).isNull()
    }

    @Test
    fun `form-level guard -- submit on a messaging page sends`() {
        val messaging = page(
            el(1, "textbox", "Message", top = 100),
            el(2, "button", "Send", top = 200, editable = false),
            url = "https://app.example/messages/12",
        )
        // "Send" is itself a sensitive label:
        assertThat(WebGuard.click(messaging.element(2), messaging)).isNotNull()
        assertThat(WebGuard.fillForm(messaging, submit = true, hasPasswordField = false, viaVaultOrUser = true)).isNotNull()
        assertThat(WebGuard.fillForm(messaging, submit = false, hasPasswordField = false, viaVaultOrUser = true)).isNull()
    }

    @Test
    fun `a form with a password field asks unless the secret route is used`() {
        assertThat(WebGuard.fillForm(shop, submit = false, hasPasswordField = true, viaVaultOrUser = false)).isNotNull()
        assertThat(WebGuard.fillForm(shop, submit = false, hasPasswordField = true, viaVaultOrUser = true)).isNull()
    }
}
