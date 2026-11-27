package com.oro.app.data.preview

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** An element picked in the preview, reduced to what a chat prompt needs. */
data class PickedElement(
    val tag: String,
    val selector: String,
    val id: String = "",
    val classes: String = "",
    val text: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val styles: Map<String, String> = emptyMap(),
    val page: String = ""
) {
    /** `<button#buy.btn.primary>` */
    val label: String
        get() = buildString {
            append(tag.lowercase().ifBlank { "element" })
            if (id.isNotBlank()) append('#').append(id.trim())
            classes.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(3).forEach { append('.').append(it) }
        }
}

/** Builds the chat text for "Send to chat" from the elements the user pointed at. */
object ElementPrompt {

    private val STYLE_KEYS = listOf(
        "color", "background-color", "font-size", "font-weight", "font-family",
        "display", "position", "margin", "padding", "border-radius"
    )

    /** Computed styles worth showing, in a stable order, as `key: value`. */
    fun styleSummary(styles: Map<String, String>): String =
        STYLE_KEYS.mapNotNull { key -> styles[key]?.takeIf { it.isNotBlank() }?.let { "$key: $it" } }
            .joinToString("; ")

    fun build(elements: List<PickedElement>, instruction: String): String {
        val list = elements.take(8)
        return buildString {
            val ask = instruction.trim()
            append(if (ask.isEmpty()) "Change the selected element${if (list.size == 1) "" else "s"} in the live preview:" else ask)
            append("\n\n")
            append(if (list.size == 1) "Selected element" else "Selected elements").append(":\n")
            list.forEach { e ->
                append("- <").append(e.label).append('>')
                if (e.width > 0 && e.height > 0) append(" ").append(e.width).append(" × ").append(e.height).append(" px")
                append('\n')
                if (e.page.isNotBlank()) append("  page: ").append(e.page).append('\n')
                if (e.selector.isNotBlank()) append("  selector: ").append(e.selector).append('\n')
                val text = e.text.trim().replace(Regex("\\s+"), " ").take(120)
                if (text.isNotEmpty()) append("  text: \"").append(text).append("\"\n")
                val styles = styleSummary(e.styles)
                if (styles.isNotEmpty()) append("  styles: ").append(styles).append('\n')
            }
            if (elements.size > list.size) append("(+").append(elements.size - list.size).append(" more)\n")
        }.trimEnd()
    }
}

/**
 * Hands a prompt from the preview (its own screen) to the editor's chat
 * composer: "Fix with Stormy" and "Send to chat" put their text here, and
 * the editor for that project drops it into the message box.
 */
object ChatPromptBridge {

    data class Pending(val projectId: String, val text: String, val serial: Long)

    private val state = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = state

    private var serial = 0L

    @Synchronized
    fun offer(projectId: String, text: String) {
        if (projectId.isBlank() || text.isBlank()) return
        serial += 1
        state.value = Pending(projectId, text, serial)
    }

    /** Clears [item] if it is still the waiting one. */
    fun consume(item: Pending) {
        if (state.value == item) state.value = null
    }

    /** What to put in a composer that already holds [existing]: both, the new text last. */
    fun compose(existing: String, incoming: String): String =
        if (existing.isBlank()) incoming else existing.trimEnd() + "\n\n" + incoming
}
