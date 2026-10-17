package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.StreamEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/**
 * chat.ifm.ai guest frames. Each `{"content": ...}` frame carries the
 * *cumulative* text so far: a `<details type="reasoning">` block (lines
 * `> `-quoted) followed by the answer. Deltas are computed against the
 * previous frame. `{"done": true}` ends the stream; `{"detail": "..."}` is an
 * error; `task_id`, `choices` and `usage`-only frames carry nothing new.
 */
class K2FrameParser {
    private var prevReasoning = ""
    private var prevAnswer = ""
    var finished = false
        private set
    var sawOutput = false
        private set

    fun onData(data: String): List<StreamEvent> {
        if (finished) return emptyList()
        if (data == "[DONE]") return finish()
        val obj = runCatching { AiHttp.json.parseToJsonElement(data) as? JsonObject }.getOrNull() ?: return emptyList()
        val out = mutableListOf<StreamEvent>()
        obj.str("detail")?.takeIf { !obj.containsKey("content") }?.let { detail ->
            finished = true
            return listOf(StreamEvent.Error("K2 Horizon: ${detail.take(300)}", retryable = detail.contains("busy", true)))
        }
        (obj["usage"] as? JsonObject)?.let { u ->
            val p = (u["prompt_tokens"] as? JsonPrimitive)?.intOrNull
            val c = (u["completion_tokens"] as? JsonPrimitive)?.intOrNull
            val cached = ((u["prompt_tokens_details"] as? JsonObject)?.get("cached_tokens") as? JsonPrimitive)?.intOrNull ?: 0
            if (p != null || c != null) out += StreamEvent.Usage(p ?: 0, c ?: 0, cached)
        }
        obj.str("content")?.let { content ->
            val (reasoning, answer) = split(content)
            if (reasoning.length > prevReasoning.length && reasoning.startsWith(prevReasoning)) {
                out += StreamEvent.ReasoningDelta(reasoning.substring(prevReasoning.length))
            }
            prevReasoning = reasoning
            val delta = if (answer.startsWith(prevAnswer)) answer.substring(prevAnswer.length) else ""
            if (delta.isNotEmpty()) {
                sawOutput = true
                out += StreamEvent.ContentDelta(delta)
            }
            if (answer.startsWith(prevAnswer)) prevAnswer = answer
        }
        if ((obj["done"] as? JsonPrimitive)?.contentOrNull == "true") out += finish()
        return out
    }

    fun finish(): List<StreamEvent> {
        if (finished) return emptyList()
        finished = true
        return listOf(StreamEvent.Completed)
    }

    companion object {
        private val DETAILS_OPEN = Regex("<details\\b[^>]*>")
        private val SUMMARY = Regex("(?s)<summary\\b[^>]*>.*?</summary>")

        /**
         * (reasoning, answer) from cumulative content.
         *
         * The reasoning block is the leading `<details type="reasoning">`; it
         * ends at the first `</details>` on a line of its own (reasoning lines
         * are `> `-quoted, so a tag the model *thinks about* never starts a
         * line). This used to split at the LAST `</details>` in the content,
         * so any answer whose file contained a `<details>` element (FAQ
         * sections, accordions) was filed as reasoning, tool call and all, and
         * the call silently vanished.
         */
        fun split(content: String): Pair<String, String> {
            val open = DETAILS_OPEN.find(content)
            if (open == null || content.substring(0, open.range.first).isNotBlank()) return "" to content
            val close = Regex("(?m)^</details>").find(content, open.range.last + 1)
                ?: return strip(content) to "" // still inside the reasoning block
            return strip(content.substring(0, close.range.first)) to
                content.substring(close.range.last + 1).trimStart('\n')
        }

        private fun strip(text: String): String = text
            .replace(DETAILS_OPEN, "")
            .replace(SUMMARY, "")
            .lines().joinToString("\n") { if (it.startsWith("> ")) it.substring(2) else if (it == ">") "" else it }
            .trim()
    }
}
