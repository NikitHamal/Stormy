package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The one-step shortcuts added for real tasks: chats, the store, toggles. */
class QuickActionsShortcutsTest {

    @Test
    fun `whatsapp links take international digits and the message`() {
        assertThat(QuickActions.whatsappLink("+977 980-000-0000", "Running 10 min late"))
            .isEqualTo("https://wa.me/9779800000000?text=Running%2010%20min%20late")
        // No number: WhatsApp asks who to send it to.
        assertThat(QuickActions.whatsappLink(null, "hi & bye")).isEqualTo("https://wa.me/?text=hi%20%26%20bye")
        assertThat(QuickActions.whatsappLink("+1 415 555 0100", null)).isEqualTo("https://wa.me/14155550100")
        // Too short to be a phone number, or nothing at all.
        assertThat(QuickActions.whatsappLink("123", "hi")).isNull()
        assertThat(QuickActions.whatsappLink("", "")).isNull()
    }

    @Test
    fun `store links open an app page for a package id, else a search`() {
        assertThat(QuickActions.storeLink("com.whatsapp")).isEqualTo("market://details?id=com.whatsapp")
        assertThat(QuickActions.storeLink("Google Maps")).isEqualTo("market://search?q=Google%20Maps&c=apps")
        assertThat(QuickActions.storeLink("  ")).isNull()
        assertThat(QuickActions.storeWebLink("market://details?id=com.whatsapp"))
            .isEqualTo("https://play.google.com/store/apps/details?id=com.whatsapp")
        assertThat(QuickActions.storeWebLink("market://search?q=maps&c=apps"))
            .isEqualTo("https://play.google.com/store/search?q=maps&c=apps")
    }

    @Test
    fun `switches and volume read the words people use`() {
        assertThat(QuickActions.parseSwitch("on")).isTrue()
        assertThat(QuickActions.parseSwitch("Off")).isFalse()
        assertThat(QuickActions.parseSwitch("toggle")).isNull()
        assertThat(QuickActions.parseSwitch(null)).isNull()

        assertThat(QuickActions.parseVolume("up")).isEqualTo(QuickActions.Volume.Up)
        assertThat(QuickActions.parseVolume("mute")).isEqualTo(QuickActions.Volume.Mute)
        assertThat(QuickActions.parseVolume("40%")).isEqualTo(QuickActions.Volume.Percent(40))
        assertThat(QuickActions.parseVolume("0.4")).isEqualTo(QuickActions.Volume.Percent(40))
        assertThat(QuickActions.parseVolume("max")).isEqualTo(QuickActions.Volume.Percent(100))
        assertThat(QuickActions.parseVolume("150")).isEqualTo(QuickActions.Volume.Percent(100))
        assertThat(QuickActions.parseVolume("loud-ish")).isNull()
    }

    @Test
    fun `panels exist for connectivity, wifi, nfc and volume only`() {
        assertThat(QuickActions.panelAction("internet")).isEqualTo("android.settings.panel.action.INTERNET_CONNECTIVITY")
        assertThat(QuickActions.panelAction("Wi-Fi")).isEqualTo("android.settings.panel.action.WIFI")
        assertThat(QuickActions.panelAction("volume panel")).isEqualTo("android.settings.panel.action.VOLUME")
        assertThat(QuickActions.panelAction("bluetooth")).isNull()
    }

    @Test
    fun `new shortcuts have readable step titles and are offered to the model`() {
        assertThat(QuickActions.title("whatsapp", "hi", null, null, null, "+977 980")).isEqualTo("Open WhatsApp for +977 980")
        assertThat(QuickActions.title("flashlight", "on", null, null, null, null)).isEqualTo("Turn the flashlight on")
        assertThat(QuickActions.title("app_info", "Instagram", null, null, null, null)).isEqualTo("Open Instagram's app settings")
        val schema = DeviceTools.QUICK_ACTION.function.parameters.toString()
        for (a in listOf("whatsapp", "store", "maps", "panel", "app_info", "flashlight", "volume")) {
            assertThat(QuickActions.ACTIONS).contains(a)
            assertThat(schema).contains("\"$a\"")
        }
    }
}
