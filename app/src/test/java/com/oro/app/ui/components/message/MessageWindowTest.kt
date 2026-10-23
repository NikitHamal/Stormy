package com.oro.app.ui.components.message

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageWindowTest {

    @Test
    fun `a short message shows everything`() {
        assertThat(MessageWindow.start(total = 12, anchor = MessageWindow.UNSET, streaming = false)).isEqualTo(0)
    }

    @Test
    fun `nothing parsed yet leaves the anchor unset`() {
        assertThat(MessageWindow.start(total = 0, anchor = MessageWindow.UNSET, streaming = true)).isEqualTo(MessageWindow.UNSET)
    }

    @Test
    fun `a long message opens at its newest blocks`() {
        assertThat(MessageWindow.start(total = 200, anchor = MessageWindow.UNSET, streaming = false))
            .isEqualTo(200 - MESSAGE_WINDOW)
    }

    @Test
    fun `blocks a live run appends extend the window instead of shifting it`() {
        val first = MessageWindow.start(total = 50, anchor = MessageWindow.UNSET, streaming = true)
        assertThat(first).isEqualTo(10)
        for (total in 51..(first + 2 * MESSAGE_WINDOW)) {
            assertThat(MessageWindow.start(total, anchor = first, streaming = true)).isEqualTo(first)
        }
    }

    @Test
    fun `a live run moves the anchor in one step once the window doubles`() {
        val anchor = 10
        val total = anchor + 2 * MESSAGE_WINDOW + 1
        assertThat(MessageWindow.start(total, anchor, streaming = true)).isEqualTo(total - MESSAGE_WINDOW)
        // A finished message never moves on its own: the user may have opened earlier steps.
        assertThat(MessageWindow.start(total, anchor, streaming = false)).isEqualTo(anchor)
    }

    @Test
    fun `show earlier moves the anchor back, never below zero`() {
        assertThat(MessageWindow.earlier(100)).isEqualTo(100 - MESSAGE_WINDOW_STEP)
        assertThat(MessageWindow.earlier(10)).isEqualTo(0)
    }

    @Test
    fun `a message that got shorter shows its end`() {
        assertThat(MessageWindow.start(total = 30, anchor = 120, streaming = false)).isEqualTo(0)
        assertThat(MessageWindow.start(total = 90, anchor = 120, streaming = false)).isEqualTo(90 - MESSAGE_WINDOW)
    }

    @Test
    fun `the stand-in grows with the transcript, within bounds`() {
        assertThat(MessageWindow.estimatedRows("plain reply")).isEqualTo(3)
        val many = (1..300).joinToString("") { "🔧 **Read File**\n✅ ok\n\n" }
        assertThat(MessageWindow.estimatedRows(many)).isEqualTo(MESSAGE_WINDOW)
        val some = (1..20).joinToString("") { "🔧 **Edit File**\n✅ ok\n\n" }
        assertThat(MessageWindow.estimatedRows(some)).isEqualTo(12)
    }
}
