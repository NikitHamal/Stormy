package com.oro.app.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Request body for chat completions API (OpenAI-compatible format)
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatRequestMessage>,
    val stream: Boolean = true,
    val temperature: Float = 0.7f,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val tools: List<Tool>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
    /**
     * OpenAI-compatible reasoning budget. Null for models that do not expose
     * one -- `explicitNulls = false` on the shared Json means a null is omitted
     * from the body entirely rather than sent as `"reasoning_effort": null`,
     * which several providers reject outright.
     */
    @SerialName("reasoning_effort")
    val reasoningEffort: String? = null
)

@Serializable
data class ChatRequestMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallRequest>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null,
    /**
     * Base64 JPEG images attached to a user turn. Not part of the OpenAI
     * schema as such: each client splices them into its own content-part
     * format ([withImageParts], AnthropicClient).
     */
    @kotlinx.serialization.Transient
    val images: List<String> = emptyList()
)

/**
 * Encode a request, turning messages that carry [ChatRequestMessage.images]
 * into OpenAI content parts: text plus `image_url` data URLs.
 */
fun encodeChatRequest(json: kotlinx.serialization.json.Json, request: ChatCompletionRequest): String {
    val tree = json.encodeToJsonElement(ChatCompletionRequest.serializer(), request)
    if (request.messages.none { it.images.isNotEmpty() }) return tree.toString()
    val obj = tree as kotlinx.serialization.json.JsonObject
    val msgs = (obj["messages"] as kotlinx.serialization.json.JsonArray).mapIndexed { i, m ->
        val images = request.messages[i].images
        if (images.isEmpty()) m else {
            val mo = m as kotlinx.serialization.json.JsonObject
            val parts = kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                    put("text", kotlinx.serialization.json.JsonPrimitive(request.messages[i].content.orEmpty()))
                })
                images.forEach { img ->
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("type", kotlinx.serialization.json.JsonPrimitive("image_url"))
                        put("image_url", kotlinx.serialization.json.buildJsonObject {
                            put("url", kotlinx.serialization.json.JsonPrimitive("data:image/jpeg;base64,$img"))
                        })
                    })
                }
            }
            kotlinx.serialization.json.JsonObject(mo + ("content" to parts))
        }
    }
    return kotlinx.serialization.json.JsonObject(obj + ("messages" to kotlinx.serialization.json.JsonArray(msgs))).toString()
}

@Serializable
data class Tool(
    val type: String = "function",
    val function: FunctionDefinition
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonElement
)

@Serializable
data class ToolCallRequest(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)

/**
 * Response from chat completions API
 */
@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ResponseMessage? = null,
    val delta: DeltaMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class ResponseMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallResponse>? = null
)

@Serializable
data class DeltaMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallDelta>? = null
)

@Serializable
data class ToolCallResponse(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

@Serializable
data class ToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCallDelta? = null
)

@Serializable
data class FunctionCallDelta(
    val name: String? = null,
    val arguments: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)

/**
 * SSE chunk from streaming response
 */
@Serializable
data class StreamChunk(
    val id: String? = null,
    val choices: List<Choice> = emptyList()
)

/**
 * Error response from API
 */
@Serializable
data class ApiErrorResponse(
    val error: ApiError? = null
)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

/**
 * Helper data class for creating assistant messages that include tool calls
 * Used when continuing the agentic loop
 */
data class AssistantMessageWithToolCalls(
    val content: String?,
    val toolCalls: List<ToolCallResponse>
) {
    /**
     * Convert to ChatRequestMessage format for API requests
     */
    fun toChatRequestMessage(): ChatRequestMessage {
        return ChatRequestMessage(
            role = "assistant",
            content = content,
            toolCalls = toolCalls.map { toolCall ->
                ToolCallRequest(
                    id = toolCall.id,
                    type = toolCall.type,
                    function = toolCall.function
                )
            }
        )
    }
}
