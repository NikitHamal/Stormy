package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ElementPromptTest {

    private val button = PickedElement(
        tag = "BUTTON", selector = "main > button.btn:nth-of-type(2)", id = "buy", classes = "btn primary big wide",
        text = "  Buy   now \n today ", width = 120, height = 44,
        styles = mapOf("color" to "rgb(255, 255, 255)", "font-size" to "16px", "zzz" to "ignored"), page = "index.html"
    )

    @Test
    fun `label shows tag id and the first classes`() {
        assertThat(button.label).isEqualTo("button#buy.btn.primary.big")
        assertThat(PickedElement("", "").label).isEqualTo("element")
    }

    @Test
    fun `prompt carries tag, size, selector, text and styles`() {
        val text = ElementPrompt.build(listOf(button), "Make it green")
        assertThat(text).startsWith("Make it green")
        assertThat(text).contains("<button#buy.btn.primary.big> 120 × 44 px")
        assertThat(text).contains("page: index.html")
        assertThat(text).contains("selector: main > button.btn:nth-of-type(2)")
        assertThat(text).contains("text: \"Buy now today\"")
        assertThat(text).contains("styles: color: rgb(255, 255, 255); font-size: 16px")
        assertThat(text).doesNotContain("zzz")
    }

    @Test
    fun `empty instruction still asks for a change and many elements are capped`() {
        val many = List(10) { button.copy(id = "b$it") }
        val text = ElementPrompt.build(many, "")
        assertThat(text).startsWith("Change the selected elements")
        assertThat(text).contains("(+2 more)")
    }

    @Test
    fun `bridge hands a prompt over once and joins it to existing text`() {
        ChatPromptBridge.offer("p", "Fix it")
        val first = ChatPromptBridge.pending.value!!
        assertThat(first.projectId).isEqualTo("p")
        ChatPromptBridge.consume(first)
        assertThat(ChatPromptBridge.pending.value).isNull()
        ChatPromptBridge.offer("p", " ")
        assertThat(ChatPromptBridge.pending.value).isNull()
        assertThat(ChatPromptBridge.compose("", "new")).isEqualTo("new")
        assertThat(ChatPromptBridge.compose("old  ", "new")).isEqualTo("old\n\nnew")
    }
}
