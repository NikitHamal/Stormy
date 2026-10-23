package com.oro.app.data.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Turns OpenAI-style SSE `data:` payloads into [StreamEvent]s.
 *
 * Pure and stateful per stream, so every provider quirk seen in the wild can be
 * pinned down in a unit test rather than discovered mid-run on a phone:
 *  - reasoning arrives as `reasoning_content` (DeepSeek, Agnes, LLM7) or as
 *    `reasoning` (vLLM, OVH, Pollinations, OpenRouter/Kilo);
 *  - the first tool-call delta may omit `arguments` entirely (OVH Llama);
 *  - some streams never send `[DONE]` and just close after `finish_reason`;
 *  - usage arrives in a trailing chunk with `choices: []`;
 *  - gateways interleave content-free chunks (`provider_metadata`) that must
 *    be ignored, not treated as the end of the stream;
 *  - a few providers omit `index` on tool-call deltas and distinguish calls
 *    only by `id`.
 *
 * [finish] must be called exactly once when the transport ends, whether or not
 * `[DONE]` was seen; it flushes accumulated tool calls and emits
 * [StreamEvent.Completed]. Calling it twice is harmless.
 */
class ChatStreamParser {

    private class ToolAccumulator(var id: String = "", var name: String = "") {
        val arguments = StringBuilder()
    }

    private val json get() = AiHttp.json
    private val tools = linkedMapOf<Int, ToolAccumulator>()
    private var finished = false
    private var sawAnything = false

    /** True once any content, reasoning or tool-call fragment arrived. */
    val hasOutput: Boolean get() = sawAnything

    fun onData(data: String): List<StreamEvent> {
        if (finished) return emptyList()
        val trimmed = data.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed == "[DONE]") return finish()

        val root = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() as? JsonObject
            ?: return emptyList()

        val out = mutableListOf<StreamEvent>()

        // Some providers stream an error object instead of failing the HTTP
        // request (OpenRouter-style mid-stream errors).
        errorMessageOf(root)?.let { message ->
            out += StreamEvent.Error(message, retryable = true)
            return out
        }

        (root["usage"] as? JsonObject)?.let { usage ->
            val prompt = (usage["prompt_tokens"] as? JsonPrimitive)?.intOrNull
            val completion = (usage["completion_tokens"] as? JsonPrimitive)?.intOrNull
            // OpenAI-style `prompt_tokens_details.cached_tokens`; DeepSeek's
            // `prompt_cache_hit_tokens`.
            val cached = ((usage["prompt_tokens_details"] as? JsonObject)?.get("cached_tokens") as? JsonPrimitive)?.intOrNull
                ?: (usage["prompt_cache_hit_tokens"] as? JsonPrimitive)?.intOrNull ?: 0
            if (prompt != null || completion != null) {
                out += StreamEvent.Usage(prompt ?: 0, completion ?: 0, cached)
            }
        }

        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: return out
        // Streaming chunks carry `delta`; a provider that ignored stream=true
        // and sent a whole completion carries `message`. Accept both.
        val delta = (choice["delta"] as? JsonObject) ?: (choice["message"] as? JsonObject)

        if (delta != null) {
            stringOf(delta["reasoning_content"])?.takeIf { it.isNotEmpty() }
                ?.let { sawAnything = true; out += StreamEvent.ReasoningDelta(it) }
                ?: stringOf(delta["reasoning"])?.takeIf { it.isNotEmpty() }
                    ?.let { sawAnything = true; out += StreamEvent.ReasoningDelta(it) }

            stringOf(delta["content"])?.takeIf { it.isNotEmpty() }?.let {
                sawAnything = true
                out += StreamEvent.ContentDelta(it)
            }

            (delta["tool_calls"] as? JsonArray)?.forEach { element ->
                val call = element as? JsonObject ?: return@forEach
                accumulate(call)
            }
        }

        stringOf(choice["finish_reason"])?.let { reason ->
            out += StreamEvent.FinishReason(reason)
        }
        return out
    }

    private fun accumulate(call: JsonObject) {
        sawAnything = true
        val id = stringOf(call["id"])?.takeIf { it.isNotBlank() }
        val function = call["function"] as? JsonObject
        val name = stringOf(function?.get("name"))?.takeIf { it.isNotBlank() }
        val argumentsElement = function?.get("arguments")
        // Arguments are normally a string fragment; a few providers send the
        // complete object instead.
        val argumentsFragment = when (argumentsElement) {
            null -> null
            is JsonPrimitive -> argumentsElement.contentOrNull
            else -> argumentsElement.toString()
        }

        var index = (call["index"] as? JsonPrimitive)?.intOrNull
        if (index == null) {
            // No index: a new id starts a new call, otherwise continue the last.
            index = when {
                tools.isEmpty() -> 0
                id != null && tools.values.none { it.id == id } -> tools.keys.max() + 1
                id != null -> tools.entries.first { it.value.id == id }.key
                else -> tools.keys.max()
            }
        } else if (id != null) {
            // Same index reused for a different id (seen on some gateways that
            // restart numbering per chunk): treat as a new call.
            val existing = tools[index]
            if (existing != null && existing.id.isNotEmpty() && existing.id != id &&
                existing.arguments.isNotEmpty()
            ) {
                index = tools.keys.max() + 1
            }
        }

        val acc = tools.getOrPut(index) { ToolAccumulator() }
        if (id != null) acc.id = id
        if (name != null) acc.name = name
        if (argumentsFragment != null) acc.arguments.append(argumentsFragment)
    }

    /** Flush tool calls and complete. Idempotent. */
    fun finish(): List<StreamEvent> {
        if (finished) return emptyList()
        finished = true
        val out = mutableListOf<StreamEvent>()
        val calls = tools.values
            .filter { it.name.isNotBlank() }
            .mapIndexed { i, acc ->
                ToolCallResponse(
                    id = acc.id.ifBlank { "call_${System.nanoTime()}_$i" },
                    type = "function",
                    function = FunctionCall(
                        name = acc.name,
                        arguments = acc.arguments.toString().ifBlank { "{}" }
                    )
                )
            }
        if (calls.isNotEmpty()) out += StreamEvent.ToolCalls(calls)
        out += StreamEvent.Completed
        return out
    }

    companion object {
        private fun stringOf(element: JsonElement?): String? =
            (element as? JsonPrimitive)?.takeIf { it.isString }?.content

        /** Message from any of the error shapes providers use, or null. */
        fun errorMessageOf(root: JsonObject): String? {
            return when (val error = root["error"]) {
                is JsonObject -> stringOf(error["message"]) ?: error.toString()
                is JsonPrimitive -> error.contentOrNull?.takeIf { error.isString }
                else -> null
            }
        }

        /** Best-effort human message from an HTTP error body of unknown shape. */
        fun describeErrorBody(body: String?): String? {
            if (body.isNullOrBlank()) return null
            val root = runCatching { AiHttp.json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                ?: return body.trim().take(300).takeIf { !it.startsWith("<") }
            return errorMessageOf(root)
                ?: stringOf(root["message"])
                ?: stringOf(root["detail"])
        }

        /**
         * Retry delay hinted by the server: the `Retry-After` header (seconds),
         * a `retry_after` field, or "Retry after 19s" in the message
         * (Pollinations' 402 FREE_LIMIT says it that way).
         */
        fun retryAfterMs(header: String?, body: String?): Long? {
            header?.trim()?.toLongOrNull()?.let { return it * 1_000 }
            if (body.isNullOrBlank()) return null
            Regex("""retry_after"?\s*[:=]\s*"?(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
                .find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                ?.let { return (it * 1_000).toLong() }
            Regex("""retry (?:after|in) (\d+(?:\.\d+)?)\s*s""", RegexOption.IGNORE_CASE)
                .find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                ?.let { return (it * 1_000).toLong() }
            return null
        }

        /** Whether an HTTP status is worth retrying after a pause. */
        fun isRetryableStatus(code: Int): Boolean =
            code == 408 || code == 409 || code == 425 || code == 429 ||
                code == 402 /* Pollinations FREE_LIMIT */ || code in 500..599
    }
}
