package com.oro.app.ui.screens.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreviewDeviceTest {

    private val webViewUa = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.004; wv) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Version/4.0 Chrome/129.0.6668.100 Mobile Safari/537.36"

    @Test
    fun `phones keep their own width and turn to a landscape one`() {
        assertThat(PreviewDevice.PHONE.width(landscape = false)).isNull()
        assertThat(PreviewDevice.PHONE.width(landscape = true)).isEqualTo(844)
        assertThat(PreviewDevice.TABLET.width(landscape = false)).isEqualTo(820)
        assertThat(PreviewDevice.TABLET.width(landscape = true)).isEqualTo(1180)
        assertThat(PreviewDevice.DESKTOP.width(landscape = true)).isEqualTo(1280)
        assertThat(PreviewDevice.DESKTOP.canRotate).isFalse()
    }

    @Test
    fun `desktop sends desktop Chrome at the same version`() {
        assertThat(PreviewDevice.DESKTOP.userAgent(webViewUa)).isEqualTo(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.6668.100 Safari/537.36"
        )
    }

    @Test
    fun `tablet drops only the mobile token, phone keeps the default`() {
        val tablet = PreviewDevice.TABLET.userAgent(webViewUa)!!
        assertThat(tablet).doesNotContain("Mobile")
        assertThat(tablet).contains("Android 14")
        assertThat(PreviewDevice.PHONE.userAgent(webViewUa)).isNull()
    }

    @Test
    fun `viewport scripts carry the width`() {
        assertThat(PreviewDevice.viewportScript(1280)).contains("'width=1280'")
        assertThat(PreviewDevice.viewportNowScript(820)).contains("'width=820'")
    }
}
