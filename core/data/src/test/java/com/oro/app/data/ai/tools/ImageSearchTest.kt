package com.oro.app.data.ai.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageSearchTest {

    // Trimmed from a live api.openverse.org response (2026-10-23).
    private val sample = """
        {"result_count":240,"page_count":80,"page_size":3,"page":1,"results":[
         {"id":"a1","title":"Coffee Shop Sign","url":"https://live.staticflickr.com/18/24171123_685e298fcd_b.jpg",
          "creator":"Jane Doe","license":"by-sa","license_version":"2.0","width":1024,"height":683,"source":"flickr"},
         {"id":"a2","title":"Coffee Shop in Vienna","url":"https://live.staticflickr.com/24/36155672_23b9878251_b.jpg",
          "creator":"","license":"by","license_version":"2.0","width":800,"height":600}]}
    """.trimIndent()

    @Test
    fun `openverse results come back as image urls with credits`() {
        val text = ImageSearch.format(sample)!!
        assertThat(text).contains("1. https://live.staticflickr.com/18/24171123_685e298fcd_b.jpg (1024x683)")
        assertThat(text).contains("\"Coffee Shop Sign\" by Jane Doe, CC BY-SA 2.0")
        assertThat(text).contains("2. https://live.staticflickr.com/24/36155672_23b9878251_b.jpg (800x600)")
        assertThat(text).doesNotContain(" by , ")
    }

    @Test
    fun `no results and non-openverse bodies are handled`() {
        assertThat(ImageSearch.format("""{"result_count":0,"results":[]}""")).startsWith("No photos found")
        assertThat(ImageSearch.format("<html>not json</html>")).isNull()
        assertThat(ImageSearch.isOpenverse("https://api.openverse.org/v1/images/?q=latte")).isTrue()
        assertThat(ImageSearch.isOpenverse("https://example.com/v1/images")).isFalse()
    }
}
