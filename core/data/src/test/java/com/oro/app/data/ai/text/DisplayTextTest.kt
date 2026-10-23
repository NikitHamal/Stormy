package com.oro.app.data.ai.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DisplayTextTest {
    @Test
    fun `escaped prose is decoded but inline code is kept`() {
        assertThat(DisplayText.clean("Line one\\nLine two\\n\\nLine three")).isEqualTo("Line one\nLine two\n\nLine three")
        assertThat(DisplayText.clean("AI \\u2014 the field")).isEqualTo("AI — the field")
        assertThat(DisplayText.clean("It&#39;s fast &amp; small &rarr; done")).isEqualTo("It's fast & small → done")
        assertThat(DisplayText.clean("Use `\"a\\nb\"` here\nand there")).isEqualTo("Use `\"a\\nb\"` here\nand there")
        assertThat(DisplayText.clean("Plain text")).isEqualTo("Plain text")
    }
}
