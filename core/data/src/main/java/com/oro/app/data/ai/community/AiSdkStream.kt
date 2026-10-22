package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.StreamEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.UUID

/**
 * The Vercel AI SDK v5 "UI message stream" that `useChat` backends answer
 * with (chat.inceptionlabs.ai among them): SSE `data:` frames holding
 * `{"type": ...}` objects.
 *
 *  - `text-delta` / `reasoning-delta`: `delta` is APPENDED (not a whole-text
 *    replacement, even on a diffusion model).
 *  - `error`: `errorText`, which some servers fill with a JSON string of
 *    their own (`{"error":"..."}`).
 *  - `finish`: end of the message, with an AI SDK finish reason and, on some
 *    servers, token counts in `messageMetadata`.
 *  - `[DONE]` (or the socket closing) ends the stream.
 *
 * Everything else (`start`, `*-start`/`*-end`, steps, sources, server-side
 * tool frames) is bookkeeping for the site's own UI and is skipped.
 */
class AiSdkStreamParser {
    var finished = false; private set
    /** True once any answer text arrived. Reasoning alone does not count. */
    var sawOutput = false; private set
    var error: String? = null; private set
    private var sentFinish = false

    fun onData(data: String): List<StreamEvent> {
        if (finished) return emptyList()
        val payload = data.trim()
        if (payload.isEmpty()) return emptyList()
        if (payload == "[DONE]") { finished = true; return emptyList() }
        val obj = runCatching { AiHttp.json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return emptyList()
        return when (str(obj["type"])) {
            "text-delta" -> str(obj["delta"])?.takeIf { it.isNotEmpty() }?.let {
                sawOutput = true
                listOf(StreamEvent.ContentDelta(it))
            }.orEmpty()
            "reasoning-delta" -> str(obj["delta"])?.takeIf { it.isNotEmpty() }
                ?.let { listOf(StreamEvent.ReasoningDelta(it)) }.orEmpty()
            "error" -> {
                error = errorText(obj)
                emptyList()
            }
            "abort" -> {
                if (error == null) error = "the site stopped the reply"
                finished = true
                emptyList()
            }
            "finish" -> {
                finished = true
                val out = mutableListOf<StreamEvent>()
                val meta = obj["messageMetadata"] as? JsonObject
                val input = int(meta?.get("inputTokens"))
                val output = int(meta?.get("outputTokens"))
                if (input != null || output != null) out += StreamEvent.Usage(input ?: 0, output ?: 0)
                val reason = str(obj["finishReason"]) ?: str(meta?.get("finishReason"))
                out += StreamEvent.FinishReason(finishReason(reason))
                sentFinish = true
                out
            }
            else -> emptyList()
        }
    }

    /** Closing events once the stream is over. */
    fun finish(): List<StreamEvent> =
        if (sentFinish) listOf(StreamEvent.Completed)
        else listOf(StreamEvent.FinishReason("stop"), StreamEvent.Completed)

    companion object {
        private fun str(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull
        private fun int(e: JsonElement?): Int? = (e as? JsonPrimitive)?.intOrNull

        /** AI SDK finish reasons, in the OpenAI spelling the agent loop checks. */
        fun finishReason(reason: String?): String = when (reason) {
            null, "", "unknown", "other" -> "stop"
            "content-filter" -> "content_filter"
            "tool-calls" -> "tool_calls"
            else -> reason
        }

        /** The human message in an `error` frame, unwrapping a JSON-encoded `errorText`. */
        fun errorText(obj: JsonObject): String {
            val raw = str(obj["errorText"]) ?: str(obj["error"]) ?: str(obj["message"]) ?: return "the site reported an error"
            val inner = runCatching { AiHttp.json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            val nested = inner?.let { o ->
                when (val e = o["error"]) {
                    is JsonObject -> str(e["message"])
                    is JsonPrimitive -> e.contentOrNull
                    else -> null
                } ?: str(o["message"])
            }
            return (nested ?: raw).trim().take(300)
        }
    }
}

/** UI messages (`{id, role, parts: [{type: "text", text}]}`) for an AI SDK backend. */
internal object AiSdkMessages {

    /** A random 16-hex id, the shape `useChat` generates. */
    fun id(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    /**
     * The conversation as UI messages. Instructions (system turns) ride at
     * the top of the first user message: these sites add their own system
     * prompt and may drop a client's, and an agent whose tool instructions
     * vanish cannot work. Consecutive turns of one role are merged, as the
     * backends expect user and assistant to alternate.
     */
    fun build(turns: List<Transcript.Turn>): JsonArray {
        val system = turns.filter { it.role == "system" }.joinToString("\n\n") { it.text }.trim()
        val merged = mutableListOf<Pair<String, StringBuilder>>()
        turns.filter { it.role != "system" }.forEach { t ->
            val last = merged.lastOrNull()
            if (last != null && last.first == t.role) last.second.append("\n\n").append(t.text)
            else merged += t.role to StringBuilder(t.text)
        }
        if (system.isNotEmpty()) {
            val first = merged.indexOfFirst { it.first == "user" }
            val block = "<instructions>\n$system\n</instructions>"
            if (first >= 0) merged[first].second.insert(0, "$block\n\n")
            else merged.add(0, "user" to StringBuilder(block))
        }
        return buildJsonArray {
            merged.forEach { (role, text) ->
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("type", "text")
                            put("text", text.toString())
                        }
                    }
                    put("id", id())
                    put("role", role)
                }
            }
        }
    }
}
