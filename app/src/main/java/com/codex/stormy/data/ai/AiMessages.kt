package com.codex.stormy.data.ai

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
    val toolChoice: String? = null
)

@Serializable
data class ChatRequestMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallRequest>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null
)

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
